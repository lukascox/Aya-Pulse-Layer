package pl.aidlfanspike.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.util.Date

private const val TAG = "AIDL_FAN_SPIKE"
private const val RESULT_FILE_NAME = "aidl_fan_spike_result.txt"
private const val SDCARD_RESULT_PATH = "/sdcard/aidl_fan_spike_result.txt"

// Reconstructed enum-string format, NOT independently confirmed -- FanSpeedConfig.java's
// WhenMappings block (research/ayaspace-teardown, evidence read via the scout pass that
// found this whole mechanism) shows the underlying Kotlin enum constants are named
// FAN_MODE_OFF/FAN_MODE_MUTE/FAN_MODE_BALANCE/FAN_MODE_TURBO/FAN_MODE_CUSTOM, and
// FanViewModel.java builds the AIDL message with plain string concatenation
// ("...:" + fanMode), which for an unmodified Kotlin enum calls its default toString()
// -- i.e. the constant name verbatim. This spike is the first live test of whether that
// assumption holds.
private const val MODE_OFF = "FAN_MODE_OFF"
private const val MODE_MUTE = "FAN_MODE_MUTE"
private const val MODE_BALANCE = "FAN_MODE_BALANCE"
private const val MODE_TURBO = "FAN_MODE_TURBO"
private const val MODE_CUSTOM = "FAN_MODE_CUSTOM"

// Run1's moderate "ramp harder, sooner" curve (50,12|65,32|78,68|85,95|95,100) produced
// an inconclusive read: duty=25 after send, which is suspiciously identical to this run's
// own pre-test baseline -- can't tell "curve didn't apply" from "SoC was below the curve's
// lowest point (50C) and it coincidentally matches the pre-existing default". Run2's curve
// is deliberately flat at 100% duty for every temp point, chosen so the result is
// unambiguous regardless of actual SoC temp at test time: if the curve applies at all, the
// read-back duty must jump to ~255 (100%); if it stays near run1's ~25% baseline (or
// anything else well below 255), that's a clean signal the write was rejected/ignored
// rather than "we happened to test at a temp below the curve's range." temp:duty pairs,
// format confirmed from FanSpeedConfig.h() (evidence: joins pairs with '|', each pair
// "temp,duty").
private const val TEST_CURVE = "30,100|50,100|70,100|85,100|95,100"

// Runs 2-3 confirmed the above "FAN_MODE_CUSTOM-temp,duty|temp,duty|..." guess does not
// change real fan behavior (6/6 attempts stayed well below the flat-100% curve's expected
// duty=255, at temps comfortably inside its range -- see FINDINGS.md). These are the next
// concrete format guesses to try, each still built around the same flat-100% shape so a
// hit is just as unambiguous as before.
private const val GUESS_SWAP_ORDER = "FAN_MODE_CUSTOM-100,30|100,50|100,70|100,85|100,95"
private const val GUESS_SEMICOLON = "FAN_MODE_CUSTOM-30,100;50,100;70,100;85,100;95,100"
private const val GUESS_NO_PREFIX = "30,100|50,100|70,100|85,100|95,100"

// Lets any string be tried from adb without a rebuild: this is the exact suffix appended
// after "msg_type_performance:" -- e.g.
// `adb shell am start -n pl.aidlfanspike.app/.MainActivity --es custom_command
// "com_set_fan_speed_strategy:FAN_MODE_CUSTOM-30,100:50,100:70,100:85,100:95,100"`
private const val EXTRA_CUSTOM_COMMAND = "custom_command"

// Confirmed live and real (research/aya-gamewindows-teardown/FINDINGS.md section 6):
// standard Linux pwm-fan hwmon node, readable even without root. hwmon index globbed
// rather than hardcoded to hwmon0, in case a firmware update ever shifts it.
private const val FAN_RPM_PATH = "/sys/devices/platform/soc/soc:pwm-fan/fan_rpm_state"
private const val FAN_PWM_GLOB = "/sys/devices/platform/soc/soc:pwm-fan/hwmon/hwmon*/pwm1"

class MainActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView
    private lateinit var txtLog: TextView
    private lateinit var btnOff: Button
    private lateinit var btnMute: Button
    private lateinit var btnBalance: Button
    private lateinit var btnTurbo: Button
    private lateinit var btnSendCurve: Button
    private lateinit var btnGuessSwapOrder: Button
    private lateinit var btnGuessSemicolon: Button
    private lateinit var btnGuessNoPrefix: Button
    private lateinit var btnReadFan: Button
    private val log = StringBuilder()
    private lateinit var localLogFile: File
    private var cpuTempPaths: List<String> = emptyList()
    private var zonesResolved = false
    private var pendingCustomCommand: String? = null

    private var serviceBinder: IBinder? = null
    private var clientId: String? = null
    private val callbackStub = AidlCallbackStub { msg -> onCallbackMessage(msg) }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            appendLog("onServiceConnected: name=$name binder=${binder?.javaClass?.name ?: "null"}")
            serviceBinder = binder
            if (binder == null) {
                appendLog("BIND RESULT: FAIL -- connected but no binder returned")
                runOnUiThread { txtStatus.text = "Bind FAILED (null binder) -- see log" }
                syncToSdcard()
                return
            }
            runOnUiThread { txtStatus.text = "Bound. Registering callback..." }
            Thread {
                try {
                    AidlProtocol.registerCallback(binder, callbackStub)
                    appendLog("registerCallback() transact completed without exception -- waiting for msg_type_register callback...")
                } catch (e: Exception) {
                    appendLog("registerCallback() FAILED: ${e.javaClass.simpleName}: ${e.message}")
                    runOnUiThread { txtStatus.text = "registerCallback FAILED -- see log" }
                }
                syncToSdcard()
            }.start()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            appendLog("onServiceDisconnected: name=$name")
            serviceBinder = null
            runOnUiThread {
                txtStatus.text = "Disconnected"
                setModeButtonsEnabled(false)
            }
            syncToSdcard()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txtStatus)
        txtLog = findViewById(R.id.txtLog)
        btnOff = findViewById(R.id.btnOff)
        btnMute = findViewById(R.id.btnMute)
        btnBalance = findViewById(R.id.btnBalance)
        btnTurbo = findViewById(R.id.btnTurbo)
        btnSendCurve = findViewById(R.id.btnSendCurve)
        btnGuessSwapOrder = findViewById(R.id.btnGuessSwapOrder)
        btnGuessSemicolon = findViewById(R.id.btnGuessSemicolon)
        btnGuessNoPrefix = findViewById(R.id.btnGuessNoPrefix)
        btnReadFan = findViewById(R.id.btnReadFan)
        localLogFile = File(filesDir, RESULT_FILE_NAME)
        localLogFile.writeText("")

        appendLog("===== AIDL FAN SPIKE =====")
        appendLog("time=${Date()} applicationId=${BuildConfig.APPLICATION_ID}")
        appendLog("Target: ${AidlProtocol.SERVICE_PACKAGE}/${AidlProtocol.SERVICE_CLASS}")
        appendLog("Test curve (temp,duty pairs): $TEST_CURVE")

        pendingCustomCommand = intent?.getStringExtra(EXTRA_CUSTOM_COMMAND)
        pendingCustomCommand?.let { appendLog("custom_command extra present: \"$it\" -- will auto-send once connected") }
        appendLog("")

        btnOff.setOnClickListener { sendFanMode(MODE_OFF, "OFF") }
        btnMute.setOnClickListener { sendFanMode(MODE_MUTE, "MUTE") }
        btnBalance.setOnClickListener { sendFanMode(MODE_BALANCE, "BALANCE") }
        btnTurbo.setOnClickListener { sendFanMode(MODE_TURBO, "TURBO") }
        btnSendCurve.setOnClickListener { sendCurveGuess("original (temp,duty | pipe, CUSTOM- prefix)", MODE_CUSTOM + "-" + TEST_CURVE) }
        btnGuessSwapOrder.setOnClickListener { sendCurveGuess("guessA swap-order", GUESS_SWAP_ORDER) }
        btnGuessSemicolon.setOnClickListener { sendCurveGuess("guessB semicolon", GUESS_SEMICOLON) }
        btnGuessNoPrefix.setOnClickListener { sendCurveGuess("guessC no-prefix", GUESS_NO_PREFIX) }
        btnReadFan.setOnClickListener { Thread { readFanState(tag = "manual") }.start() }

        // Baseline read on launch, before anything is touched -- so the very first log
        // lines always show what the fan was doing before this app sent anything.
        Thread { readFanState(tag = "baseline, before any AIDL command") }.start()

        bindToGameWindow()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unbindService(connection)
        } catch (_: Exception) {
            // not bound / already unbound -- fine to ignore for this throwaway probe
        }
    }

    private fun bindToGameWindow() {
        // No action set on the Intent -- same deliberate choice as aidl-bind-spike
        // (see that project's MainActivity.kt for the full reasoning, traced to
        // AyaAidlService.onBind() in the decompiled evidence).
        val intent = Intent().apply {
            setClassName(AidlProtocol.SERVICE_PACKAGE, AidlProtocol.SERVICE_CLASS)
        }
        val bound = try {
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            appendLog("bindService() threw: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
        appendLog("bindService() returned: $bound")
        txtStatus.text = if (bound) "bindService() returned true, waiting for onServiceConnected..." else "bindService() returned FALSE -- see log"
        syncToSdcard()
    }

    private fun onCallbackMessage(msg: String) {
        appendLog("callback received: \"$msg\"")
        if (msg.startsWith("msg_type_register:")) {
            clientId = msg.substringAfter("msg_type_register:")
            appendLog("clientId assigned: $clientId")
            runOnUiThread {
                txtStatus.text = "READY -- connected, clientId=$clientId"
                setModeButtonsEnabled(true)
            }
            pendingCustomCommand?.let { cmd ->
                pendingCustomCommand = null
                sendCustomCommand(cmd)
            }
        }
        syncToSdcard()
    }

    private fun setModeButtonsEnabled(enabled: Boolean) {
        btnOff.isEnabled = enabled
        btnMute.isEnabled = enabled
        btnBalance.isEnabled = enabled
        btnTurbo.isEnabled = enabled
        btnSendCurve.isEnabled = enabled
        btnGuessSwapOrder.isEnabled = enabled
        btnGuessSemicolon.isEnabled = enabled
        btnGuessNoPrefix.isEnabled = enabled
        // btnReadFan intentionally NOT gated -- it's a pure xsu read, safe and useful
        // even while disconnected (e.g. to capture a true pre-connection baseline).
    }

    /** Step 1 test: discrete mode only, no curve write -- isolates whether
     *  `com_set_performance_fan` alone does anything before risking the curve write. */
    private fun sendFanMode(mode: String, label: String) {
        val binder = serviceBinder
        val id = clientId
        if (binder == null || id == null) {
            appendLog("Cannot send $label: not connected/registered yet")
            return
        }
        setModeButtonsEnabled(false)
        Thread {
            val message = "$id:msg_type_performance:com_set_performance_fan:$mode"
            appendLog("--- sending fan mode $label ---")
            appendLog("send(\"$message\")")
            sendAndVerify(binder, message)
            runOnUiThread { setModeButtonsEnabled(true) }
        }.start()
    }

    /** Step 2/3 test: the actual curve write -- switches to CUSTOM, then pushes
     *  [strategySuffix] (everything after "com_set_fan_speed_strategy:") via
     *  `com_set_fan_speed_strategy`. [label] is just for the log, to tell guesses apart.
     *  Always ends with [resetToKnownState] so one guess's leftover duty can't bleed into
     *  the read for the next one, and the device isn't left mid-experiment. */
    private fun sendCurveGuess(label: String, strategySuffix: String) {
        val binder = serviceBinder
        val id = clientId
        if (binder == null || id == null) {
            appendLog("Cannot send guess [$label]: not connected/registered yet")
            return
        }
        setModeButtonsEnabled(false)
        Thread {
            appendLog("--- guess [$label]: mode->CUSTOM then strategy ---")

            val modeMsg = "$id:msg_type_performance:com_set_performance_fan:$MODE_CUSTOM"
            appendLog("send(\"$modeMsg\")")
            var modeOk = false
            try {
                AidlProtocol.send(binder, modeMsg)
                modeOk = true
                appendLog("send() [mode->CUSTOM] transact completed without exception")
            } catch (e: Exception) {
                appendLog("send() [mode->CUSTOM] FAILED: ${e.javaClass.simpleName}: ${e.message}")
            }

            Thread.sleep(500)

            val strategyMsg = "$id:msg_type_performance:com_set_fan_speed_strategy:$strategySuffix"
            appendLog("send(\"$strategyMsg\")")
            sendAndVerify(binder, strategyMsg, extraNote = "guess=$label mode-switch send_ok=$modeOk")

            resetToKnownState(binder, id)
            runOnUiThread { setModeButtonsEnabled(true) }
        }.start()
    }

    /** Sends the exact command suffix passed via the `custom_command` intent extra --
     *  the adb-drivable escape hatch for trying a format guess without a rebuild. Also
     *  resets to BALANCE afterward, same as [sendCurveGuess]. */
    private fun sendCustomCommand(command: String) {
        val binder = serviceBinder
        val id = clientId
        if (binder == null || id == null) {
            appendLog("Cannot send custom command: not connected/registered yet")
            return
        }
        setModeButtonsEnabled(false)
        Thread {
            appendLog("--- custom command (from intent extra) ---")
            val message = "$id:msg_type_performance:$command"
            appendLog("send(\"$message\")")
            sendAndVerify(binder, message, extraNote = "source=intent_extra")
            resetToKnownState(binder, id)
            runOnUiThread { setModeButtonsEnabled(true) }
        }.start()
    }

    /** Leaves the fan in a known, sane state after any curve-write attempt -- BALANCE
     *  rather than OFF, so a test session never ends with no active cooling. Also gives
     *  every guess in a multi-guess session a clean, comparable starting point instead of
     *  letting the previous guess's leftover duty bleed into the next one's read. */
    private fun resetToKnownState(binder: IBinder, id: String) {
        Thread.sleep(300)
        appendLog("--- reset to BALANCE after guess ---")
        val resetMsg = "$id:msg_type_performance:com_set_performance_fan:$MODE_BALANCE"
        appendLog("send(\"$resetMsg\")")
        sendAndVerify(binder, resetMsg)
    }

    /** Sends [message], then waits and reads back real fan state via xsu -- the
     *  objective "did it actually work" check, independent of whether the Binder
     *  transaction itself reported success (same empirical standard as
     *  aidl-bind-spike's cpufreq read-back). */
    private fun sendAndVerify(binder: IBinder, message: String, extraNote: String? = null) {
        var sendOk = false
        try {
            AidlProtocol.send(binder, message)
            sendOk = true
            appendLog("send() transact completed without exception")
        } catch (e: Exception) {
            appendLog("send() FAILED: ${e.javaClass.simpleName}: ${e.message}")
        }
        Thread.sleep(1500)
        val note = extraNote?.let { " ($it)" } ?: ""
        readFanState(tag = "post-send, send_ok=$sendOk$note")
    }

    /** Pure xsu read, no AIDL involved -- safe to call anytime, including before
     *  connecting. RPM comes from the vendor's own reporting node; PWM duty comes
     *  from the raw hwmon node underneath it (both confirmed readable in
     *  research/aya-gamewindows-teardown/FINDINGS.md section 6). Also reads real SoC
     *  temp (run2 addition -- run1 had no temp logged, which is why its curve-write
     *  result was inconclusive: no way to tell whether the SoC was simply below the
     *  test curve's lowest defined point).
     */
    private fun readFanState(tag: String) {
        ensureThermalZonesResolved()
        val tempCmd = if (cpuTempPaths.isNotEmpty()) {
            "echo CPU_TEMP_RAW=\$(cat ${cpuTempPaths.joinToString(" ")} 2>/dev/null | tr '\\n' ',')"
        } else {
            "echo CPU_TEMP_RAW=unresolved"
        }
        val result = XsuShell.exec(
            "echo RPM=\$(cat $FAN_RPM_PATH 2>/dev/null); " +
                "echo PWM_DUTY=\$(cat $FAN_PWM_GLOB 2>/dev/null); " +
                tempCmd
        )
        val rawTemps = result.stdout.lineSequence()
            .firstOrNull { it.startsWith("CPU_TEMP_RAW=") }
            ?.removePrefix("CPU_TEMP_RAW=")
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val tempC = ThermalZones.formatMaxTempC(rawTemps)
        appendLog("fan state [$tag] (${result.elapsedMs}ms): ${result.stdout.replace("\n", " | ")} | CPU_TEMP_MAX=${tempC}C")
        if (result.stderr.isNotBlank()) appendLog("  stderr: ${result.stderr}")
        if (result.error != null) appendLog("  error: ${result.error}")
        appendLog("")
        syncToSdcard()
    }

    /** Resolves CPU thermal zone paths once (dynamic, zone numbers can shift across
     *  firmware revisions -- same principle as ThermalZones.kt's other callers) and
     *  caches for the rest of this session. */
    private fun ensureThermalZonesResolved() {
        if (zonesResolved) return
        val result = XsuShell.exec(ThermalZones.buildResolveCommand())
        cpuTempPaths = ThermalZones.parseResolveOutput(result.stdout).cpuZones
        zonesResolved = true
        appendLog("resolved CPU thermal zones: $cpuTempPaths")
    }

    private fun appendLog(line: String) {
        Log.d(TAG, line)
        log.appendLine(line)
        localLogFile.appendText(line + "\n")
        runOnUiThread { txtLog.text = log.toString() }
    }

    private fun syncToSdcard() {
        XsuShell.exec("cat '${localLogFile.absolutePath}' > $SDCARD_RESULT_PATH")
    }
}
