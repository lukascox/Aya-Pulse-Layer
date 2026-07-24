package pl.autotdpharness.app

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * xsu exec wrapper, carried over from research/xsu-capability-probe/ (same lessons
 * apply here). Only the confirmed-working "args" invocation
 * (ProcessBuilder("xsu", "-c", cmd)) is implemented -- the "stdin" method is
 * confirmed broken (silent false positive) and intentionally not reimplemented.
 *
 * Also carried over: java.lang.Process on Android does NOT expose
 * toHandle()/ProcessHandle (confirmed by a failed compile attempt while building the
 * prior probe) -- so on timeout, only destroyForcibly() on the immediate process is
 * possible, not a full descendant-process kill. A batched sysfs-read call stalled
 * ~126s once under heavy Dolphin load in that probe; short explicit timeouts on
 * expensive/batched calls plus per-call elapsed logging are the mitigation, not a fix.
 */
object XsuShell {

    data class ExecResult(
        val command: String,
        val exitCode: Int?,
        val stdout: String,
        val stderr: String,
        val error: String?,
        val elapsedMs: Long,
    ) {
        val looksLikeRoot: Boolean get() = stdout.contains("uid=0")
    }

    fun exec(command: String, timeoutSec: Long = 6): ExecResult {
        val start = System.nanoTime()
        return try {
            val proc = ProcessBuilder("xsu", "-c", command)
                .redirectErrorStream(false)
                .start()
            val outBuf = StringBuilder()
            val errBuf = StringBuilder()
            val tOut = Thread { drain(proc.inputStream, outBuf) }
            val tErr = Thread { drain(proc.errorStream, errBuf) }
            tOut.start()
            tErr.start()
            val finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) proc.destroyForcibly()
            tOut.join(1000)
            tErr.join(1000)
            val elapsed = (System.nanoTime() - start) / 1_000_000
            ExecResult(
                command = command,
                exitCode = if (finished) proc.exitValue() else null,
                stdout = outBuf.toString().trim(),
                stderr = errBuf.toString().trim(),
                error = if (!finished) "TIMEOUT after ${timeoutSec}s" else null,
                elapsedMs = elapsed,
            )
        } catch (e: Exception) {
            val elapsed = (System.nanoTime() - start) / 1_000_000
            ExecResult(command, null, "", "", "${e.javaClass.simpleName}: ${e.message}", elapsed)
        }
    }

    private fun drain(stream: InputStream, into: StringBuilder) {
        try {
            BufferedReader(InputStreamReader(stream)).forEachLine { into.appendLine(it) }
        } catch (_: Exception) {
            // stream closed after a forced kill on timeout -- ignore
        }
    }
}
