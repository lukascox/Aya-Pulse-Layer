package com.kei.pulse.root

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * One-xsu-connection-per-session daemon for CPU/GPU cap writes -- replaces the old "one xsu
 * connection per write" pattern with the architecture the original bash `pulse_lite`
 * (`docs/archive/pulse_lite/`) already used, before this repo had `xsu`/`xsud` at all: launch a
 * script once via a single `xsu -c "... &"` call, then apply every subsequent write as a plain
 * shell builtin already running as root, with zero further `xsu`/`xsud` connections. Validated
 * on-device three times (`research/pulse-for-aya/scripts/daemon-persistence-test.sh`) plus once
 * more with the named-pipe bridge used here (`fifo-daemon-test.sh`) -- see `STATUS.md`'s
 * Minecraft-crash investigation, 2026-07-27, "New direction found and validated" / the FIFO
 * update, for the full evidence trail.
 *
 * The pipe lives under this app's own private storage (`filesDir`) -- `/data/local/tmp` was tried
 * first but this app's own process gets `EACCES` there (confirmed on-device); `filesDir` works for
 * both the app and the root daemon (also confirmed on-device, same session).
 *
 * NOT thread-safe for concurrent [start]/[stop] calls; callers must serialize through their own
 * lock (`ForegroundAppMonitorService`'s `transitionMutex` already owns this class's lifecycle).
 * [setCap] itself is safe to call from any thread but does blocking file I/O -- call it off the
 * main thread.
 */
class PulseDaemon(context: Context) {

    private val scriptFile = File(context.filesDir, "pulse_daemon.sh")
    private val fifoInPath = File(context.filesDir, "pulse_fifo_in").absolutePath
    private val logPath = File(context.filesDir, "pulse_daemon.log").absolutePath
    private val assets = context.assets

    /**
     * One human-readable session log per daemon launch, under `/sdcard` (not this app's own `filesDir`) so it
     * can be pulled directly (`adb pull`, no root/`run-as` needed) even if the device crashes before a host
     * `logcat` capture can be taken -- logcat itself has proven unreliable under real gameplay load (its
     * 256 KiB ring buffer overflows under `xsu`'s own chatty protocol logging, STATUS.md 2026-07-27). Written
     * by the SAME already-root daemon process via the existing FIFO ([log]), so this adds zero further `xsu`
     * connections -- unlike the app's own sandboxed process, which per this repo's own established finding
     * (`MainActivity.kt`'s AIDL probes) can't reliably reach `/sdcard` directly under scoped storage.
     */
    private val sdcardLogPath = "/sdcard/apl_pulse_logs/pulse_" +
        java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date()) + ".log"

    @Volatile private var running = false

    /**
     * Copies the daemon script from assets (first run / if missing) and launches it via one
     * `xsu` call. Safe to call repeatedly -- a no-op while already running.
     */
    fun start() {
        if (running) return
        if (!scriptFile.exists()) {
            assets.open(ASSET_NAME).use { input ->
                scriptFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        RootSupport.runRootCommand(
            "mkdir -p /sdcard/apl_pulse_logs; " +
                "sh '${scriptFile.absolutePath}' '$fifoInPath' '$logPath' '$sdcardLogPath' > /dev/null 2>&1 < /dev/null &",
        )
        running = true
    }

    /**
     * Sends one write (`echo <value> > <path>`, then `chmod <mode> <path>` -- e.g. `"644"` for
     * governor writes, `"444"` for CPU/GPU frequency caps, matching each control's own existing
     * convention so the vendor perf daemon can't silently stomp them) to the already-running
     * daemon -- zero `xsu` calls, just a blocking write into the pipe (the daemon's own read loop
     * wakes up within its current cycle, observed sub-millisecond in testing). `value` is a plain
     * string so this covers both numeric caps (frequencies) and names (governors) with one
     * protocol. Returns `false` (never throws) if the daemon isn't running or the pipe write
     * fails, so callers can fall back to a direct root-shell write.
     *
     * Opening a FIFO for write blocks until a reader is on the other end -- if the daemon script
     * never actually started (the launch `xsu` call silently failed) or died mid-session (e.g.
     * killed by `xsud`'s own documented instability under load), NOTHING will ever open the read
     * end again, so a bare `FileOutputStream` open here would block forever. [start] optimistically
     * marks `running = true` without confirming the script is actually alive, so that dead-daemon
     * case is real, not hypothetical. The write runs on a throwaway daemon thread with a bounded
     * [SET_CAP_TIMEOUT_MS] join instead of directly on the caller's thread/coroutine, so a wedged
     * daemon can never freeze the caller -- `ForegroundAppMonitorService.tick()`'s single poll-loop
     * coroutine calls this synchronously from `startAutoTdp()`, and a hang there would silently
     * stall the whole watcher (overlay/fan/RGB/AutoTDP) with no exception and no log to explain why
     * (STATUS.md's AutoTDP-tick-loop investigation, 2026-07-27 -- root-caused to exactly this).
     * On timeout the writer thread is abandoned (never interrupted -- a blocked FIFO open doesn't
     * reliably respond to `Thread.interrupt()`), a bounded, rare leak that matches the existing
     * documented lifecycle gap in [start]'s doc rather than introducing a new one.
     */
    fun setCap(path: String, mode: String, value: String): Boolean {
        if (!running) return false
        return sendLine("$path $mode $value")
    }

    /**
     * Appends one line (timestamped by the daemon script) to this session's `/sdcard` log file --
     * same zero-`xsu`, bounded-timeout FIFO write as [setCap], just a different protocol verb
     * (`"LOG <message>"` instead of a sysfs write). `message` must not contain a newline (any found
     * are replaced with a space, so one call always produces exactly one log line). Returns `false`
     * (never throws) on a dead/not-yet-started daemon -- callers should treat this as best-effort:
     * this is a diagnostic aid, not a control-path write, so there's no xsu fallback to drop back to.
     */
    fun log(message: String): Boolean {
        if (!running) return false
        return sendLine("LOG " + message.replace('\n', ' '))
    }

    /** Tells the daemon to exit and clean up its FIFO. Safe to call even if never started. */
    fun stop() {
        if (!running) return
        try {
            FileOutputStream(fifoInPath).use { it.write("STOP\n".toByteArray()) }
        } catch (_: Exception) {
            // daemon already gone -- fine, nothing left to signal
        }
        running = false
    }

    /** Shared bounded-timeout FIFO line write backing [setCap] and [log] -- see [setCap]'s doc for why the
     * timeout/daemon-thread pattern is required (a dead daemon must never block the caller). */
    private fun sendLine(line: String): Boolean {
        val succeeded = java.util.concurrent.atomic.AtomicBoolean(false)
        val writer = Thread {
            try {
                FileOutputStream(fifoInPath).use { it.write("$line\n".toByteArray()) }
                succeeded.set(true)
            } catch (e: Exception) {
                // leave succeeded = false
            }
        }
        writer.isDaemon = true
        writer.start()
        writer.join(SET_CAP_TIMEOUT_MS)
        return succeeded.get()
    }

    companion object {
        private const val ASSET_NAME = "pulse_daemon.sh"

        // Healthy round trips are sub-millisecond (fifo-daemon-test.sh); this is generous slack for
        // a live daemon while still cutting a dead one's stall down from "forever" to "unnoticeable."
        private const val SET_CAP_TIMEOUT_MS = 500L
    }
}
