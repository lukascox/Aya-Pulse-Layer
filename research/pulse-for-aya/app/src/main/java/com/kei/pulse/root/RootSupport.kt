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

    // apl glue patch (2026-07-25): upstream wrote the script to a world-readable/-executable file
    // because the stock PServer service ran it as root from a DIFFERENT uid and had to read it off
    // disk. xsu takes the command text directly as its "-c" argument (same mechanism this project's
    // own probes use for multi-line scripts), so that file-based indirection -- and the world-
    // readable exposure that came with it -- is no longer needed at all.
    fun runGeneratedScript(
        context: Context,
        scriptName: String,
        scriptContents: String,
    ): String? {
        return runRootCommand(scriptContents)
    }
}
