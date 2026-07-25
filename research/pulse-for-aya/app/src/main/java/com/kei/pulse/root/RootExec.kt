package com.kei.pulse.root

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * apl glue patch (2026-07-25): upstream `pulse` talks to `PServerBinder` (a root broker present on
 * AYN Odin/Thor/Retroid Pocket 6) via raw Binder transact. That service doesn't exist on this
 * AYANEO Snapdragon device; `xsu` is this device's confirmed root-shell equivalent (see apl's
 * xsu-capability-probe/FINDINGS.md), invoked the same way as every other probe in that repo (the
 * "args" method: ProcessBuilder("xsu", "-c", cmd) -- the "stdin" method is confirmed broken).
 * Every caller in the rest of this module only depends on [executeAsRoot]'s Result<String?>
 * signature and [pServerAvailable], never on PServerBinder itself -- confirmed by a whole-tree grep
 * before this patch was written (see pulse-glue-assessment/FINDINGS.md).
 *
 * `RootSupport.runRootCommand` constructs a fresh `RootExec()` on every single call, so probing
 * `xsu` availability in the constructor (a real process spawn, unlike the cheap reflection lookup
 * this replaces) would double the process-spawn cost of every root command. [pServerAvailable] is
 * cached at the companion/class level instead, so the probe only runs once per process lifetime.
 */
class RootExec {

    val pServerAvailable: Boolean
        get() = cachedAvailable ?: probe().also { cachedAvailable = it }

    fun executeAsRoot(cmd: String): Result<String?> {
        val result = exec(cmd)
        if (result.error != null) return Result.failure(IllegalStateException(result.error))
        return Result.success(result.stdout.trim().takeIf { it.isNotEmpty() && it != "null" })
    }

    private fun probe(): Boolean {
        val result = exec("id", timeoutSec = 5)
        return result.exitCode == 0 && result.stdout.contains("uid=0")
    }

    private data class ExecResult(val exitCode: Int?, val stdout: String, val stderr: String, val error: String?)

    private fun exec(command: String, timeoutSec: Long = 8): ExecResult {
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
            ExecResult(
                exitCode = if (finished) proc.exitValue() else null,
                stdout = outBuf.toString(),
                stderr = errBuf.toString(),
                error = if (!finished) "xsu timeout after ${timeoutSec}s: $command" else null,
            )
        } catch (t: Throwable) {
            ExecResult(null, "", "", "${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun drain(stream: InputStream, into: StringBuilder) {
        try {
            BufferedReader(InputStreamReader(stream)).forEachLine { into.appendLine(it) }
        } catch (_: Exception) {
            // stream closed after a forced kill on timeout -- ignore, same as XsuShell in the probes
        }
    }

    companion object {
        @Volatile private var cachedAvailable: Boolean? = null
    }
}
