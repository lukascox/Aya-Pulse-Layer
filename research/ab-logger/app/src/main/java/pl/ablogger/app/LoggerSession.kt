package pl.ablogger.app

import java.io.File

/**
 * One logging session: resolves thermal zones + fan node once (done by the caller,
 * [LoggerService]), then samples on a fixed cadence, writing CSV rows. Ported from
 * research/autotdp-ab-harness's AbSession -- same sampling pipeline and CSV columns
 * (minus the `mode` column, which had no equivalent once this app dropped the
 * Baseline/AutoTDP distinction -- see this folder's README.md for why), so existing
 * expectations about the data shape carry over unchanged.
 */
class LoggerSession(
    private val zones: ZoneMap,
    private val fanNode: FanNode?,
    filesDir: File,
    startedAtMs: Long,
    private val pulseInstalled: Boolean,
    private val pulseServiceRunning: Boolean,
) {
    private val csvFileName = "session_$startedAtMs.csv"
    private val localCsvFile: File = File(filesDir, csvFileName)

    val sdcardCsvPath: String = "$SDCARD_LOG_DIR/$csvFileName"

    private var sampleIdx = 0

    init {
        localCsvFile.writeText(CSV_HEADER + "\n")
    }

    data class SampleTiming(val pipelineMs: Long, val snapshotMs: Long)

    /**
     * apl glue fix (2026-07-25, revised 2026-07-26): a single sample used to be 3-4
     * separate `xsu` invocations, then was combined into ONE call to cut down
     * process-spawn count -- that combined call turned out to be the actual root
     * cause of the "100% empty CSV" bug: on this device, `xsud` segfaults (or
     * silently drops output) once a single `-c` argument crosses roughly 1000-1200
     * characters, and the fully-combined command ran ~3150 chars (19 CPU + 8 GPU
     * thermal zones). Confirmed by direct on-device bisection, see
     * research/xsu-capability-probe/FINDINGS.md's "Root cause found" section --
     * do not recombine the snapshot statements into one call.
     *
     * Current shape: the ACT+LIST call stays combined (its command text is short --
     * only the *output* of `dumpsys activity`/`SurfaceFlinger --list` is large, and
     * output size was never the problem, `-c` argument length was). The CPU/GPU/
     * thermal/fan/battery snapshot -- the part whose statement count scales with
     * this device's thermal-zone count -- goes through [XsuShell.execChunked],
     * which packs statements into multiple calls safely under that threshold.
     */
    fun sampleOnce(): SampleTiming {
        val t0 = System.nanoTime()
        val actListRes = XsuShell.exec(buildActListCommand(), timeoutSec = 8)
        val blocks = PowerFanProbe.parseBlockTags(actListRes.stdout)

        val (_, pkgShort) = FpsPipeline.parseForegroundPkg(blocks["ACT"].orEmpty())
        val matchedLayer = FpsPipeline.matchLayer(pkgShort, blocks["LIST"].orEmpty())
        val pipelineMs = (System.nanoTime() - t0) / 1_000_000

        val (_, frameCount, fps) = if (matchedLayer != "NoMatch") {
            val latRes = XsuShell.exec("dumpsys SurfaceFlinger --latency \"$matchedLayer\"")
            FpsPipeline.parseFps(latRes.stdout)
        } else {
            Triple("n/a", 0, "n/a (no layer matched)")
        }

        val snapRes = XsuShell.execChunked(buildSnapshotStatements())
        val tagged = FpsPipeline.parseTaggedLines(snapRes.stdout)

        val row = buildCsvRow(pkgShort, fps, frameCount, tagged)
        localCsvFile.appendText(row + "\n")
        sampleIdx++

        return SampleTiming(pipelineMs, actListRes.elapsedMs + snapRes.elapsedMs)
    }

    private fun buildActListCommand(): String =
        "echo ===ACT===; dumpsys activity activities; echo ===LIST===; dumpsys SurfaceFlinger --list; "

    /** Copies the local CSV to /sdcard via the root shell (uid=0 can read our
     * app-private file), same mechanism every probe in this repo uses -- the app
     * process itself never touches /sdcard through its own file APIs. */
    fun syncToSdcard() {
        XsuShell.exec("mkdir -p $SDCARD_LOG_DIR; cat '${localCsvFile.absolutePath}' > $sdcardCsvPath")
    }

    /** One statement per value, packed into safely-sized `xsu` calls by
     * [XsuShell.execChunked] -- see that function's doc comment for why this can't
     * go back to being one combined string. */
    private fun buildSnapshotStatements(): List<String> = buildList {
        for (p in listOf(0, 2, 5, 7)) {
            add("echo P${p}_GOV=\$(cat /sys/devices/system/cpu/cpufreq/policy$p/scaling_governor); ")
            add("echo P${p}_FREQ=\$(cat /sys/devices/system/cpu/cpufreq/policy$p/scaling_cur_freq); ")
        }
        add("echo GPU_CLK=\$(cat /sys/class/kgsl/kgsl-3d0/gpuclk); ")
        add("echo GPU_BUSY=\$(cat /sys/class/kgsl/kgsl-3d0/gpubusy); ")
        zones.cpuZones.forEachIndexed { i, z -> add("echo CPUZ_$i=\$(cat $z 2>/dev/null); ") }
        zones.gpuZones.forEachIndexed { i, z -> add("echo GPUZ_$i=\$(cat $z 2>/dev/null); ") }
        zones.skinZone?.let { add("echo SKIN=\$(cat $it 2>/dev/null); ") }
        zones.batteryZone?.let { add("echo BATTZONE=\$(cat $it 2>/dev/null); ") }
        fanNode?.let { add("echo FAN=\$(cat ${it.curStatePath} 2>/dev/null); ") }
        add("echo BATT_CURRENT=\$(cat /sys/class/power_supply/battery/current_now 2>/dev/null); ")
        add("echo BATT_VOLTAGE=\$(cat /sys/class/power_supply/battery/voltage_now 2>/dev/null); ")
    }

    private fun buildCsvRow(pkg: String, fps: String, frameCount: Int, tagged: Map<String, String>): String {
        fun g(k: String) = tagged[k] ?: "?"
        val cpuTemps = zones.cpuZones.indices.map { tagged["CPUZ_$it"] }
        val gpuTemps = zones.gpuZones.indices.map { tagged["GPUZ_$it"] }
        val fanSignal = fanNode?.let { parseFanSignal(g("FAN")) } ?: "n/a"
        val fanUnit = fanNode?.label ?: "not_found"

        val cols = listOf(
            System.currentTimeMillis().toString(),
            sampleIdx.toString(),
            pkg,
            fps,
            frameCount.toString(),
            g("P0_FREQ"), g("P0_GOV"),
            g("P2_FREQ"), g("P2_GOV"),
            g("P5_FREQ"), g("P5_GOV"),
            g("P7_FREQ"), g("P7_GOV"),
            g("GPU_CLK"),
            computeGpuBusyPct(tagged["GPU_BUSY"]),
            ThermalZones.formatMaxTempC(cpuTemps),
            ThermalZones.formatMaxTempC(gpuTemps),
            ThermalZones.formatTempC(tagged["SKIN"]),
            ThermalZones.formatTempC(tagged["BATTZONE"]),
            fanSignal,
            fanUnit,
            g("BATT_CURRENT"),
            g("BATT_VOLTAGE"),
            pulseInstalled.toString(),
            pulseServiceRunning.toString(),
        )
        return cols.joinToString(",") { csvEscape(it) }
    }

    /** `fan_rpm_state` reads as free text ("Current RPM 2815"); the old
     * cooling_device fallback reads as a bare step-index integer. Extract the
     * trailing number either way so the CSV column is plain numeric, falling back
     * to the raw string if the format is ever unexpected rather than dropping it. */
    private fun parseFanSignal(raw: String): String =
        Regex("(\\d+)\\s*$").find(raw.trim())?.groupValues?.get(1) ?: raw

    private fun csvEscape(v: String): String =
        if (v.contains(",") || v.contains("\"")) "\"${v.replace("\"", "\"\"")}\"" else v

    private fun computeGpuBusyPct(raw: String?): String {
        if (raw == null) return "n/a"
        val parts = raw.trim().split(Regex("\\s+"))
        if (parts.size < 2) return "n/a"
        val busy = parts[0].toLongOrNull() ?: return "n/a"
        val total = parts[1].toLongOrNull() ?: return "n/a"
        if (total <= 0) return "n/a"
        val pct = (busy * 100) / total
        return if (pct < 0 || pct > 100) "n/a(counter_reset)" else pct.toString()
    }

    companion object {
        const val SDCARD_LOG_DIR = "/sdcard/apl_ab_logs"

        const val CSV_HEADER =
            "timestamp,sample_idx,foreground_pkg,fps,frame_count," +
                "p0_freq,p0_governor,p2_freq,p2_governor,p5_freq,p5_governor,p7_freq,p7_governor," +
                "gpu_freq_hz,gpu_busy_pct," +
                "temp_cpu_max_c,temp_gpu_max_c,temp_skin_c,temp_battery_c," +
                "fan_signal,fan_signal_unit," +
                "battery_current_ma,battery_voltage_mv," +
                // apl glue addition (2026-07-26): recorded once at session start (see
                // LoggerService.resolvePulseStatus()) and repeated on every row, so a
                // pulled CSV is self-describing -- no separate notes needed to tell
                // which A/B arm a file belongs to. pulse_installed uses PackageManager
                // (no root); pulse_service_running checks whether pulse-for-aya's
                // ForegroundAppMonitorService is actually alive (i.e. AUTOTDP is on),
                // via one xsu call at session start only, not per-sample.
                "pulse_installed,pulse_service_running"
    }
}
