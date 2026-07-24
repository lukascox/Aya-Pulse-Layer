package pl.xsubench2.app

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Minimal xsu exec wrapper for this benchmark probe (v2).
 *
 * Only the confirmed-working invocation ("args": ProcessBuilder("xsu", "-c", cmd)) is
 * implemented here. The "stdin" (interactive pipe) method remains confirmed broken
 * (silent false positive: exit=0, empty stdout/stderr) and is intentionally NOT
 * reimplemented -- do not add it back without a dedicated diagnostic session first.
 *
 * v2 note: run 1 saw a ~126s stall on a batched sysfs-read call during heavy Dolphin
 * load. The obvious follow-up -- also killing descendant processes on timeout, not
 * just the immediate xsu process -- is NOT possible here: java.lang.Process on
 * Android does not expose toHandle()/ProcessHandle (that's a desktop-JDK-9+ API, not
 * present on Android's runtime, confirmed by a failed compile attempt while building
 * this probe). So the only lever available from the app side is destroyForcibly() on
 * the immediate process plus a short, explicit timeout on the specific call that
 * stalled (see MainActivity Test 5, SNAPSHOT_TIMEOUT_SEC) -- bounding the damage and
 * logging it clearly, not actually guaranteeing the grandchild process is killed.
 * The underlying root cause of the stall itself was NOT diagnosed and is out of scope
 * for this probe.
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

    /** Default timeout is intentionally short -- run 1's finding is that a "hang" is a
     * real, recurring risk under heavy device load, not a theoretical concern. Callers
     * that expect a call to be cheap (most of them) should not need to override this;
     * callers of the batched CPU/GPU snapshot pass an explicit, even shorter timeout
     * (see MainActivity) since that specific call is the one that stalled in run 1. */
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
            if (!finished) killHard(proc)
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

    private fun killHard(proc: Process) {
        // Best-effort only: destroyForcibly() reaches the immediate xsu process, but
        // NOT necessarily a grandchild (e.g. a `cat` shelled out from the xsu-spawned
        // shell) that is blocked in an uninterruptible kernel wait -- see class doc.
        proc.destroyForcibly()
    }

    private fun drain(stream: InputStream, into: StringBuilder) {
        try {
            BufferedReader(InputStreamReader(stream)).forEachLine { into.appendLine(it) }
        } catch (_: Exception) {
            // stream closed after a forced kill on timeout -- ignore
        }
    }
}
