package pl.aidlspike.app

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Same xsu exec wrapper carried over from every other probe in this repo -- used
 * here ONLY to read back sysfs state after an AIDL command, as an objective
 * "did it actually work" check independent of whether the Binder transaction itself
 * threw an exception. This spike's actual point (the AIDL bind) does not use xsu at
 * all -- that's the whole finding being tested.
 */
object XsuShell {

    data class ExecResult(
        val command: String,
        val exitCode: Int?,
        val stdout: String,
        val stderr: String,
        val error: String?,
        val elapsedMs: Long,
    )

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
