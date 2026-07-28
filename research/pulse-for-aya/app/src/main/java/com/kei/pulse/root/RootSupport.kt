package com.kei.pulse.root

import android.content.Context
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object RootSupport {

    // Process-wide serialization of root access. Several callers (per-app watcher apply, telemetry
    // poll, tile, sleep monitor) can hit this concurrently, and xsu invocations racing on the same
    // sysfs node is exactly the kind of contention this project's own probes serialize against
    // (see xsu-capability-probe/FINDINGS.md) — kept from upstream's PServer-specific reasoning
    // since the underlying concern (overlapping privileged writes) applies just as much to xsu.
    private val pServerLock = ReentrantLock()

    fun runRootCommand(command: String): String? {
        return pServerLock.withLock {
            RootExec().executeAsRoot(command).getOrNull()
        }
    }

    /**
     * Same call as [runRootCommand], but returns the full [Result] instead of collapsing it to `String?` via
     * `getOrNull()` -- that collapse makes "the `xsu` process threw/timed out" and "it ran fine but printed
     * nothing" indistinguishable. STATUS.md, 2026-07-28: `PulseDaemon.start()`'s launch call started failing
     * with `result=null` and no way to tell which case it was -- `run-as com.kei.pulse xsu -c "id"` proved
     * root itself, and even the app's own UID/SELinux context via `run-as`, both work instantly, so the
     * failure has to be something specific to how the call happens from inside the app's own live process
     * (e.g. a `PATH` difference between a Zygote-forked app process and a `run-as`-spawned shell) -- only the
     * real exception message (or confirmed timeout) can settle that, not another `null`.
     */
    fun runRootCommandResult(command: String): Result<String?> {
        return pServerLock.withLock {
            RootExec().executeAsRoot(command)
        }
    }

    // apl glue patch (2026-07-25): upstream wrote the script to a world-readable/-executable file
    // because the stock PServer service ran it as root from a DIFFERENT uid and had to read it off
    // disk. This was simplified to pass scriptContents directly as xsu's "-c" argument -- correct
    // for short commands, but WRONG for anything long: this device's xsud crashes (SIGSEGV, or
    // silently drops output) once a single `-c` argument crosses roughly 1000-1200 characters, a
    // fuzzy race-like threshold with a safe margin around ~800 (research/xsu-capability-probe/
    // FINDINGS.md's on-device bisection). FpsReader's TimeStats-reduction script alone is 874
    // characters and fires every ~1-2s during real gameplay -- squarely in that danger band, and
    // never audited against this finding until STATUS.md's 2026-07-28 investigation. Reverted
    // (2026-07-28) to writing the script to this app's own private filesDir (root can already read
    // there fine -- the same mechanism PulseDaemon.kt's own script uses) and running it via a short
    // `sh '<path>'` command instead, WITHOUT reintroducing the world-readable exposure the original
    // upstream approach had (filesDir is private to this app + root, nothing else).
    fun runGeneratedScript(
        context: Context,
        scriptName: String,
        scriptContents: String,
    ): String? {
        val file = java.io.File(context.filesDir, scriptName)
        return try {
            file.writeText(scriptContents)
            runRootCommand("sh '${file.absolutePath}'")
        } catch (e: Exception) {
            null
        }
    }
}
