package pl.ablogger.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service so the sampling loop survives being backgrounded for a whole
 * game session -- research/autotdp-ab-harness's equivalent loop lived in
 * lifecycleScope on the Activity, which is NOT safe to background for 10+ minutes.
 * Each [ACTION_START] while not already running begins a brand-new timestamped CSV
 * (see [LoggerSession]) -- supports being started/stopped repeatedly across a
 * session without overwriting or colliding with earlier runs.
 */
class LoggerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var loopJob: Job? = null
    private var session: LoggerSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "AB Logger", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        serviceScope.launch { recoverOrphanedSessions() }
    }

    /**
     * Best-effort recovery net, run once whenever this service process (re)starts --
     * including after the device reboots, the only way to recover from a full UI
     * crash (see STATUS.md's INCIDENT entries). The CSV itself is never actually
     * lost -- every sample is appended straight to an app-private file that survives
     * a reboot like any other file -- but nothing exposes it outside private storage
     * except [LoggerSession.syncToSdcard], which a crash-then-reboot skips entirely
     * (no clean "Stop log" to trigger it). This re-syncs any `session_*.csv` still
     * sitting in [filesDir] from an earlier run so a crashed session's data lands on
     * /sdcard automatically, without a manual root pull. Runs on [serviceScope]
     * (IO dispatcher), not inline in `onCreate()`, so a slow/blocked `xsu` call here
     * can't stall the service's main-thread startup.
     */
    private fun recoverOrphanedSessions() {
        val orphans = filesDir.listFiles { f -> f.name.startsWith("session_") && f.name.endsWith(".csv") }
        if (orphans.isNullOrEmpty()) return
        val cmd = buildString {
            append("mkdir -p ${LoggerSession.SDCARD_LOG_DIR}; ")
            orphans.forEach { f -> append("cat '${f.absolutePath}' > '${LoggerSession.SDCARD_LOG_DIR}/${f.name}'; ") }
        }
        XsuShell.exec(cmd, timeoutSec = 10)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startLogging()
            ACTION_STOP -> stopLogging()
        }
        return START_NOT_STICKY
    }

    private fun startLogging() {
        if (loopJob?.isActive == true) return // already running -- ignore, don't start a second overlapping session
        isRunning = true
        startForeground(NOTIF_ID, buildNotification("Starting..."))
        loopJob = serviceScope.launch {
            val zoneRes = XsuShell.exec(ThermalZones.buildResolveCommand(), timeoutSec = 8)
            val zones = ThermalZones.parseResolveOutput(zoneRes.stdout)

            val fanNode = resolveFanNode()
            val pulseInstalled = isPulseInstalled()
            val pulseServiceRunning = isPulseServiceRunning()

            val s = LoggerSession(zones, fanNode, filesDir, System.currentTimeMillis(), pulseInstalled, pulseServiceRunning)
            session = s
            lastCsvPath = s.sdcardCsvPath
            lastLogcatPath = s.logcatSdcardPath
            s.startCrashCapture()

            var idx = 0
            while (isActive) {
                s.sampleOnce()
                idx++
                // Synced every sample, not periodically -- see syncToSdcard()'s doc comment:
                // a crash/reboot skips both the old periodic sync and the clean-stop flush.
                s.syncToSdcard()
                updateNotification("Logging... $idx samples")
                delay(SESSION_INTERVAL_MS)
            }
        }
    }

    /**
     * apl glue improvement (2026-07-25): the generic cooling_device-based fan
     * discovery this app inherited from autotdp-ab-harness never finds a real node
     * on this device -- the AYANEO Pocket FIT's fan is a plain `pwm-fan` platform
     * driver / hwmon interface, a different sysfs subtree entirely, confirmed live
     * and readable (even unprivileged) by aya-gamewindows-teardown's pass 3 (see
     * that folder's FINDINGS.md section 6, and AR03/AR13's own read logic). Try that
     * confirmed path first; fall back to the old generic cooling_device search
     * (kept, not deleted) in case a future firmware/device lacks it.
     *
     * Read-only: this app never writes to the fan node. Write access to this path
     * is separately unconfirmed and deliberately not exercised here or in
     * pulse-for-aya yet -- see pulse-glue-assessment/FINDINGS.md.
     */
    private fun resolveFanNode(): FanNode? {
        val rpmRes = XsuShell.exec("cat $FAN_RPM_PATH 2>/dev/null", timeoutSec = 4)
        if (rpmRes.stdout.isNotBlank()) {
            return FanNode("pwm-fan", FAN_RPM_PATH, "fan_rpm_state")
        }
        val discRes = XsuShell.exec(PowerFanProbe.buildDiscoveryCommand(), timeoutSec = 8)
        val disc = PowerFanProbe.parseDiscovery(PowerFanProbe.parseBlockTags(discRes.stdout))
        return disc.fanLikeCoolingDevices.firstOrNull()?.let {
            FanNode(it.first, "/sys/class/thermal/${it.first}/cur_state", "cooling_device_step(${it.first})")
        }
    }

    /** No root needed -- plain `PackageManager` lookup. Recorded once per session
     * (see [LoggerSession]'s `pulse_installed` column) so a pulled CSV says on its
     * own which A/B arm it's from, instead of relying on separately-taken notes. */
    private fun isPulseInstalled(): Boolean =
        try {
            packageManager.getPackageInfo(PULSE_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }

    /** One `xsu` call, at session start only (not per-sample -- this is a status
     * check, not part of the hot sampling loop). `ForegroundAppMonitorService` is
     * pulse-for-aya's persistent foreground service; it's only alive while AUTOTDP
     * is actually toggled on, so this distinguishes "installed" from "actively
     * running" -- installed-but-off would otherwise look identical to native-only
     * in a pulled CSV. */
    private fun isPulseServiceRunning(): Boolean {
        val res = XsuShell.exec("dumpsys activity services $PULSE_PACKAGE", timeoutSec = 6)
        return res.stdout.contains("ForegroundAppMonitorService")
    }

    private fun stopLogging() {
        loopJob?.cancel()
        loopJob = null
        session?.syncToSdcard() // final flush so a stop right after a sample isn't lost
        session?.stopCrashCapture()
        session = null
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AB Logger")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true)
            .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_START = "pl.ablogger.app.START"
        const val ACTION_STOP = "pl.ablogger.app.STOP"
        private const val CHANNEL_ID = "ab_logger"
        private const val NOTIF_ID = 1
        // apl glue fix (2026-07-25): was 2000L (matching autotdp-ab-harness's own
        // cadence), raised to 5000L as part of the same mitigation as
        // LoggerSession.sampleOnce()'s call-combining -- see that function's doc
        // comment and STATUS.md's incident writeup. Sustained high-frequency xsu
        // process spawning is the leading suspect in a full system_server crash
        // observed during this app's first real test session.
        private const val SESSION_INTERVAL_MS = 5000L

        /** pulse-for-aya keeps upstream `pulse`'s package id for diffability against
         * upstream, per apl/CLAUDE.md's Conventions section -- not `pl.*`. */
        private const val PULSE_PACKAGE = "com.kei.pulse"

        // Confirmed live 2026-07-25 (aya-gamewindows-teardown pass 3, FINDINGS.md
        // section 6): AR03/AR13's plain pwm-fan/hwmon interface, NOT a cooling_device.
        private const val FAN_RPM_PATH = "/sys/devices/platform/soc/soc:pwm-fan/fan_rpm_state"

        /** Read by MainActivity on resume to set correct button state if the app UI
         * was reopened while a background session is already running. */
        @Volatile var isRunning: Boolean = false
            private set

        @Volatile var lastCsvPath: String? = null
            private set

        @Volatile var lastLogcatPath: String? = null
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LoggerService::class.java).setAction(ACTION_START))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, LoggerService::class.java).setAction(ACTION_STOP))
        }
    }
}
