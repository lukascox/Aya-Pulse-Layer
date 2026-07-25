package pl.ablogger.app

/**
 * FPS measurement pipeline, ported faithfully from pulse_lite_diag_v8.sh (apl-diag
 * repo) via research/xsu-capability-probe/ (validated end-to-end on-device across
 * RetroArch, Eden/yuzu, and Dolphin -- three distinct layer-naming conventions). Do
 * not simplify or re-derive the layer-matching heuristic -- it took four buggy
 * iterations (v4-v7) to get right in the original bash diagnostic.
 *
 * Pure parsers over already-fetched dumpsys output -- the actual
 * `xsu -c "dumpsys ..."` calls happen in the caller, so each call's latency can be
 * timed and attributed correctly.
 */
object FpsPipeline {

    /** Step a: foreground app detection. topResumedActivity= primary, mFocusedApp=
     * fallback. Do NOT use mCurrentFocus/mResumedActivity (dumpsys window windows). */
    fun parseForegroundPkg(activitiesDump: String): Pair<String, String> {
        var line = activitiesDump.lineSequence().firstOrNull { it.contains("topResumedActivity=") } ?: ""
        if (line.isEmpty()) {
            line = activitiesDump.lineSequence().firstOrNull { it.contains("mFocusedApp=") } ?: ""
        }
        val pkgFull = Regex("[a-zA-Z0-9_.]+/[a-zA-Z0-9_.]+").find(line)?.value ?: ""
        return line to pkgFull.substringBefore("/")
    }

    /** Step b: SurfaceFlinger layer selection, 4-tier priority search. */
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

    /** Step c: FPS from --latency output. Returns (refreshPeriodNs, frameCount, fps). */
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

    /** Splits "KEY=value" lines into a map. Shared tagging convention used by the
     * CPU/GPU snapshot, the hardware profile dump, and the harness's full sample. */
    fun parseTaggedLines(stdout: String): Map<String, String> =
        stdout.lines()
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
            .toMap()
}
