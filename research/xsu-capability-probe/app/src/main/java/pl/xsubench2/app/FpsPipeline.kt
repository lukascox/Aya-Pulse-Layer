package pl.xsubench2.app

/**
 * FPS measurement pipeline, ported faithfully from pulse_lite_diag_v8.sh (validated
 * end-to-end on-device: RetroArch and Eden/yuzu, 30/30 correct layer matches each).
 * Run 1 of this benchmark additionally confirmed the layer-matching heuristic across
 * Dolphin too (BLAST layer, same pattern as Eden) -- three different layer-naming
 * conventions now empirically confirmed through the app-invoked xsu channel. Do not
 * simplify or re-derive this heuristic -- it took four buggy iterations (v4-v7) to
 * get right in the original bash diagnostic.
 *
 * These are pure parsers over already-fetched dumpsys output. The actual
 * `xsu -c "dumpsys ..."` calls happen in the caller (MainActivity), so each call's
 * latency can be timed and attributed correctly by the benchmark harness.
 */
object FpsPipeline {

    /** Step a: foreground app detection. Confirmed on this device/Android 14:
     * topResumedActivity= works, falling back to mFocusedApp=. Do NOT use
     * mCurrentFocus / mResumedActivity (dumpsys window windows) -- both confirmed to
     * return nothing here. */
    fun parseForegroundPkg(activitiesDump: String): Pair<String, String> {
        var line = activitiesDump.lineSequence().firstOrNull { it.contains("topResumedActivity=") } ?: ""
        if (line.isEmpty()) {
            line = activitiesDump.lineSequence().firstOrNull { it.contains("mFocusedApp=") } ?: ""
        }
        val pkgFull = Regex("[a-zA-Z0-9_.]+/[a-zA-Z0-9_.]+").find(line)?.value ?: ""
        return line to pkgFull.substringBefore("/")
    }

    /** Step b: SurfaceFlinger layer selection, 4-tier priority search (the part broken
     * through v7). Tier 1 (BLAST) confirmed correct for Eden and Dolphin. Tier 3 (last
     * non-helper match) confirmed correct for RetroArch (no SurfaceView-named layer
     * exists there at all; the real layer is the last unprefixed line, not the first). */
    fun matchLayer(pkgShort: String, layerList: String): String {
        if (pkgShort.isEmpty()) return "NoMatch"
        val lines = layerList.lines()

        lines.firstOrNull {
            it.contains(pkgShort, ignoreCase = true) && it.contains("(BLAST)", ignoreCase = true)
        }?.let { return it }

        lines.firstOrNull {
            it.contains("SurfaceView", ignoreCase = true) &&
                it.contains(pkgShort, ignoreCase = true) &&
                !it.contains("Background for", ignoreCase = true) &&
                !it.contains("Bounds for -", ignoreCase = true)
        }?.let { return it }

        lines.lastOrNull {
            it.contains(pkgShort, ignoreCase = true) &&
                !it.contains("ActivityRecordInputSink", ignoreCase = true) &&
                !it.contains("Background for", ignoreCase = true) &&
                !it.contains("Bounds for -", ignoreCase = true) &&
                !it.contains("Dim layer", ignoreCase = true)
        }?.let { return it }

        lines.firstOrNull {
            it.contains("SurfaceView", ignoreCase = true) && it.contains(pkgShort, ignoreCase = true)
        }?.let { return it }

        lines.firstOrNull { it.contains(pkgShort, ignoreCase = true) }?.let { return it }

        return "NoMatch"
    }

    /** Step c: FPS from --latency output. present_times excludes "0" and the INT64_MAX
     * sentinel row. frame_count < 5 -> "low_sample_count" (likely idle/static frame).
     * span_ns == 0 -> "zero span", a confirmed real edge case (~17% of Eden samples in
     * v8 validation) -- fails safe, harmless, NOT this app's job to fix, just log it.
     * Returns (refreshPeriodNs, frameCount, fps). */
    fun parseFps(latencyDump: String): Triple<String, Int, String> {
        val lines = latencyDump.lines()
        val refreshPeriodNs = lines.firstOrNull()?.trim() ?: ""
        val presentTimes = lines.drop(1)
            .mapNotNull { it.trim().split(Regex("\\s+")).getOrNull(1) }
            .filter { it != "0" && it != "9223372036854775807" }
            .mapNotNull { it.toLongOrNull() }
        val frameCount = presentTimes.size
        val fps = when {
            frameCount < 5 -> "n/a (low_sample_count=$frameCount)"
            else -> {
                val span = presentTimes.last() - presentTimes.first()
                if (span > 0) {
                    "%.1f".format((frameCount - 1) * 1_000_000_000.0 / span)
                } else {
                    "n/a (zero span)"
                }
            }
        }
        return Triple(refreshPeriodNs, frameCount, fps)
    }

    /**
     * Step d: CPU governor+cur_freq (4 policies) + GPU freq/busy, batched into ONE xsu
     * call using tagged `echo KEY=$(cat path)` lines -- NOT positional line indexing.
     *
     * v2 change vs run 1: run 1 batched this already, but (a) never logged this call's
     * own elapsed time separately from the per-sample pipeline_ms, which is why a ~126s
     * stall on this exact call during heavy Dolphin load was invisible in the result
     * file and had to be root-caused via manual logcat timestamp math instead, and
     * (b) used fixed line-index parsing, which silently produces "?" for every field
     * if the line count is ever off by one for any reason -- tagged key=value parsing
     * fails more visibly (a specific missing key) instead of silently shifting every
     * subsequent field.
     */
    fun buildSnapshotCommand(): String = buildString {
        for (p in listOf(0, 2, 5, 7)) {
            append("echo P${p}_GOV=\$(cat /sys/devices/system/cpu/cpufreq/policy$p/scaling_governor); ")
            append("echo P${p}_FREQ=\$(cat /sys/devices/system/cpu/cpufreq/policy$p/scaling_cur_freq); ")
        }
        append("echo GPU_CLK=\$(cat /sys/class/kgsl/kgsl-3d0/gpuclk); ")
        append("echo GPU_BUSY=\$(cat /sys/class/kgsl/kgsl-3d0/gpubusy)")
    }

    fun parseSnapshot(stdout: String): String {
        val map = parseTaggedLines(stdout)
        val sb = StringBuilder()
        for (p in listOf(0, 2, 5, 7)) {
            sb.append("p$p:gov=${map["P${p}_GOV"] ?: "?"},freq=${map["P${p}_FREQ"] ?: "?"} ")
        }
        sb.append("| gpu_freq_hz=${map["GPU_CLK"] ?: "?"} gpu_busy_raw=${map["GPU_BUSY"] ?: "?"}")
        return sb.toString()
    }

    /** Splits "KEY=value" lines into a map. Used by both the Test 5 snapshot and
     * Test 6's hardware profile dump -- same tagging convention throughout. */
    fun parseTaggedLines(stdout: String): Map<String, String> =
        stdout.lines()
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
            .toMap()
}
