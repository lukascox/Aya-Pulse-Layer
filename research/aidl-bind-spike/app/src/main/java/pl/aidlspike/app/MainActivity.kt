package pl.aidlspike.app

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

private const val TAG = "AIDL_SPIKE"
private const val RESULT_FILE_NAME = "aidl_spike_result.txt"
private const val SDCARD_RESULT_PATH = "/sdcard/aidl_spike_result.txt"

class MainActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView
    private lateinit var txtLog: TextView
    private lateinit var btnGaming: Button
    private lateinit var btnEco: Button
    private val log = StringBuilder()
    private lateinit var localLogFile: File

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
                setButtonsEnabled(false)
            }
            syncToSdcard()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txtStatus)
        txtLog = findViewById(R.id.txtLog)
        btnGaming = findViewById(R.id.btnGaming)
        btnEco = findViewById(R.id.btnEco)
        localLogFile = File(filesDir, RESULT_FILE_NAME)
        localLogFile.writeText("")

        appendLog("===== AIDL BIND SPIKE =====")
        appendLog("time=${Date()} applicationId=${BuildConfig.APPLICATION_ID}")
        appendLog("Target: ${AidlProtocol.SERVICE_PACKAGE}/${AidlProtocol.SERVICE_CLASS}")
        appendLog("")

        btnGaming.setOnClickListener { sendModeCommand(3, "Gaming") }
        btnEco.setOnClickListener { sendModeCommand(0, "Eco") }

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
        // No action set on the Intent -- AyaAidlService.onBind() returns the useless
        // plain LocalBinder if action == "com.ayaneo.aidl.server", and the real AIDL
        // binder otherwise (confirmed in aya-gamewindows-teardown/evidence/aidl/
        // AyaAidlService.java). Leaving action unset is deliberate, not an omission.
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
                setButtonsEnabled(true)
            }
        }
        syncToSdcard()
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        btnGaming.isEnabled = enabled
        btnEco.isEnabled = enabled
    }

    private fun sendModeCommand(modeIndex: Int, modeName: String) {
        val binder = serviceBinder
        val id = clientId
        if (binder == null || id == null) {
            appendLog("Cannot send $modeName: not connected/registered yet")
            return
        }
        setButtonsEnabled(false)
        Thread {
            val message = "$id:msg_type_performance:com_set_performance_mode:$modeIndex"
            appendLog("--- sending $modeName (mode index $modeIndex) ---")
            appendLog("send(\"$message\")")
            var sendOk = false
            try {
                AidlProtocol.send(binder, message)
                sendOk = true
                appendLog("send() transact completed without exception")
            } catch (e: Exception) {
                appendLog("send() FAILED: ${e.javaClass.simpleName}: ${e.message}")
            }

            // Objective verification via the already-proven xsu channel, independent of
            // whether the Binder transaction itself reported success -- this is the
            // real "did it work" check, matching this project's usual empirical standard.
            Thread.sleep(1500)
            val verify = XsuShell.exec(
                "echo P0_GOV=\$(cat /sys/devices/system/cpu/cpufreq/policy0/scaling_governor); " +
                    "echo P0_FREQ=\$(cat /sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq); " +
                    "echo P2_GOV=\$(cat /sys/devices/system/cpu/cpufreq/policy2/scaling_governor); " +
                    "echo P2_FREQ=\$(cat /sys/devices/system/cpu/cpufreq/policy2/scaling_cur_freq)"
            )
            appendLog("verify (via xsu, ${verify.elapsedMs}ms, send_ok=$sendOk): ${verify.stdout.replace("\n", " | ")}")
            appendLog(
                "Expected for $modeName per apl-diag/docs/HARDWARE_PROFILE.md: " +
                    if (modeName == "Gaming") "P2_GOV=performance, P2_FREQ near 3148800 (max)"
                    else "P2_GOV=powersave, P2_FREQ near 729600 (low)"
            )
            appendLog("")

            syncToSdcard()
            runOnUiThread { setButtonsEnabled(true) }
        }.start()
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
