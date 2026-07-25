package pl.ablogger.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Logging still works without it -- the notification just won't show while
            // backgrounded, which defeats the point of "hand off the console and walk
            // away", so nudge but don't block on it.
            if (!granted) {
                txtStatus.text = "Notification permission denied -- logging will still run, but you won't see progress while backgrounded."
            }
            beginLogging()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txtStatus)
        btnStart = findViewById(R.id.btnStartLog)
        btnStop = findViewById(R.id.btnStopLog)

        btnStart.setOnClickListener { onStartTapped() }
        btnStop.setOnClickListener { onStopTapped() }
    }

    override fun onResume() {
        super.onResume()
        // Reflect real state in case the service is already running from an earlier
        // launch of this Activity (e.g. user reopened the app while a session logs
        // in the background).
        setRunningUiState(LoggerService.isRunning)
    }

    private fun onStartTapped() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        beginLogging()
    }

    private fun beginLogging() {
        LoggerService.start(this)
        setRunningUiState(true)
        txtStatus.text = "Logging started."
    }

    private fun onStopTapped() {
        LoggerService.stop(this)
        setRunningUiState(false)
        txtStatus.text = "Stopped. Last CSV: ${LoggerService.lastCsvPath ?: "(none)"}"
    }

    private fun setRunningUiState(running: Boolean) {
        btnStart.isEnabled = !running
        btnStop.isEnabled = running
        if (running) txtStatus.text = "Logging..."
    }
}
