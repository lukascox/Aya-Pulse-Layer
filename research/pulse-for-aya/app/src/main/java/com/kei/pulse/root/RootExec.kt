package com.kei.pulse.root

import android.os.Build
import android.util.Log
import com.kei.pulse.model.DeviceSupport
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

    // apl glue gate (2026-08-03): the single chokepoint for everything privileged -- this is the only
    // ProcessBuilder("xsu", ...) in the whole tree, so refusing here means no cap write, no chmod 666,
    // no core parking and no daemon launch can reach a SoC family this was never run on. See
    // [DeviceSupport] for the reasoning and for what specifically would happen without it.
    //
    // On the Pocket FIT this is always true, so the gate is transparent: identical behaviour, one
    // extra read of an in-process constant, once per process.
    private val socSupported: Boolean by lazy {
        DeviceSupport.isSupportedSoc(Build.SOC_MANUFACTURER, Build.HARDWARE).also { ok ->
            if (!ok) {
                Log.w(
                    TAG,
                    "Refusing every root command: " +
                        DeviceSupport.describe(Build.MANUFACTURER, Build.MODEL, Build.SOC_MANUFACTURER) +
                        " is not a Qualcomm SoC. This build was only ever run on one. See DeviceSupport.kt.",
                )
            }
        }
    }

    // apl glue fix (2026-07-25): only latch a TRUE result. A single transient probe
    // failure (this device's xsud is known to crash-and-refork on connection close,
    // see pulse-glue-assessment/FINDINGS.md and STATUS.md's system_server incident --
    // catching xsud mid-crash on the one lazy probe call is entirely plausible) used
    // to poison this flag to false for the rest of the process's life, showing "Your
    // device is not compatible with this app" even while executeAsRoot() kept
    // working fine underneath (it doesn't consult this cache at all). Same lesson
    // RgbController.available() already applies elsewhere in this codebase ("DON'T
    // latch a false here... re-probe on the next call") -- missed here originally.
    //
    // The SoC gate is consulted FIRST and is deliberately exempt from that rule: it is a fact about the
    // hardware rather than a flaky probe, so latching a false is correct. Reusing this flag also means
    // the existing "Your device is not compatible with this app" screen covers the wrong-SoC case with
    // no change to TunerScreen.kt -- which is byte-identical to upstream and worth keeping that way.
    // The distinction between "no root" and "wrong SoC" lives in the Log.w above, not in the UI.
    val pServerAvailable: Boolean
        get() = if (!socSupported) false else cachedAvailable ?: probe().also { ok -> if (ok) cachedAvailable = true }

    fun executeAsRoot(cmd: String): Result<String?> {
        if (!socSupported) return Result.failure(IllegalStateException(UNSUPPORTED_SOC_MESSAGE))
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

        private const val TAG = "PulseRoot"

        const val UNSUPPORTED_SOC_MESSAGE =
            "PULSE for AYANEO was only ever run on Qualcomm hardware; refusing to touch this device."
    }
}
