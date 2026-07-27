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
            "sh '${scriptFile.absolutePath}' '$fifoInPath' '$logPath' > /dev/null 2>&1 < /dev/null &",
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
     */
    fun setCap(path: String, mode: String, value: String): Boolean {
        if (!running) return false
        return try {
            FileOutputStream(fifoInPath).use { it.write("$path $mode $value\n".toByteArray()) }
            true
        } catch (e: Exception) {
            false
        }
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

    companion object {
        private const val ASSET_NAME = "pulse_daemon.sh"
    }
}
