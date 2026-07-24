package pl.autotdpharness.app

import java.io.File

/** Stock values pulse_lite_v3.7.sh's own EXIT/INT/TERM trap restores -- used here
 * only to VERIFY clean shutdown after sending the sentinel, never written directly
 * by this app. Source: apl-diag repo, pulse_lite_v3.7.sh `restore()`. */
object PulseLiteV37 {
    const val CPU0_STOCK = 2265600L
    const val CPU2_STOCK = 3148800L
    const val CPU5_STOCK = 2956800L
    const val CPU7_STOCK = 3052800L
    const val GPU_UNCAP = 0L

    const val SCRIPT_PATH = "/sdcard/pulse_lite.sh"
    const val STOP_SENTINEL = "/sdcard/pulse_lite.stop"

    /** Fire-and-forget background launch, per Test 9's confirmed-viable pattern
     * (nested `sh -c '... &'`). Do NOT wait for this to return the script's own
     * exit -- it runs an infinite loop until the sentinel is dropped. */
    fun buildLaunchCommand(): String =
        "sh -c 'sh $SCRIPT_PATH &'"

    fun buildStopCommand(): String =
        "touch $STOP_SENTINEL"
}

enum class SessionMode(val label: String) {
    BASELINE("baseline"),
    AUTOTDP("autotdp"),
}

/** One A/B session: resolves thermal zones + fan node once, then samples on a fixed
 * cadence (2s, matching pulse_lite_v3.7.sh's own loop interval), writing CSV rows. */
class AbSession(
    val mode: SessionMode,
    private val zones: ZoneMap,
    private val fanNode: FanNode?,
    filesDir: File,
) {
    private val csvFileName = "pulsefit_${mode.label}_${System.currentTimeMillis()}.csv"
    val localCsvFile: File = File(filesDir, csvFileName)
    val sdcardCsvPath: String = "/sdcard/$csvFileName"

    private var sampleIdx = 0

    init {
        localCsvFile.writeText(CSV_HEADER + "\n")
    }

    /** One full sample: 3 chained dumpsys calls (pipeline) + 1 batched sysfs/thermal/
     * power snapshot call. Returns the elapsed ms of each leg for logging, plus the
     * CSV row already appended to the local file. */
    data class SampleTiming(val pipelineMs: Long, val snapshotMs: Long)

    fun sampleOnce(): SampleTiming {
        val t0 = System.nanoTime()
        val actRes = XsuShell.exec("dumpsys activity activities")
        val (_, pkgShort) = FpsPipeline.parseForegroundPkg(actRes.stdout)
        val listRes = XsuShell.exec("dumpsys SurfaceFlinger --list")
        val matchedLayer = FpsPipeline.matchLayer(pkgShort, listRes.stdout)
        val (_, frameCount, fps) = if (matchedLayer != "NoMatch") {
            val latRes = XsuShell.exec("dumpsys SurfaceFlinger --latency \"$matchedLayer\"")
            FpsPipeline.parseFps(latRes.stdout)
        } else {
            Triple("n/a", 0, "n/a (no layer matched)")
        }
        val pipelineMs = (System.nanoTime() - t0) / 1_000_000

        val snapCmd = buildFullSnapshotCommand()
        val snapRes = XsuShell.exec(snapCmd, timeoutSec = 4)
        val tagged = FpsPipeline.parseTaggedLines(snapRes.stdout)

        val row = buildCsvRow(pkgShort, fps, frameCount, tagged)
        localCsvFile.appendText(row + "\n")
        sampleIdx++

        return SampleTiming(pipelineMs, snapRes.elapsedMs)
    }

    fun syncToSdcard() {
        XsuShell.exec("cat '${localCsvFile.absolutePath}' > $sdcardCsvPath")
    }

    private fun buildFullSnapshotCommand(): String = buildString {
        for (p in listOf(0, 2, 5, 7)) {
            append("echo P${p}_GOV=\$(cat /sys/devices/system/cpu/cpufreq/policy$p/scaling_governor); ")
            append("echo P${p}_FREQ=\$(cat /sys/devices/system/cpu/cpufreq/policy$p/scaling_cur_freq); ")
        }
        append("echo GPU_CLK=\$(cat /sys/class/kgsl/kgsl-3d0/gpuclk); ")
        append("echo GPU_BUSY=\$(cat /sys/class/kgsl/kgsl-3d0/gpubusy); ")
        zones.cpuZones.forEachIndexed { i, z -> append("echo CPUZ_$i=\$(cat $z 2>/dev/null); ") }
        zones.gpuZones.forEachIndexed { i, z -> append("echo GPUZ_$i=\$(cat $z 2>/dev/null); ") }
        zones.skinZone?.let { append("echo SKIN=\$(cat $it 2>/dev/null); ") }
        zones.batteryZone?.let { append("echo BATTZONE=\$(cat $it 2>/dev/null); ") }
        fanNode?.let { append("echo FAN=\$(cat ${it.curStatePath} 2>/dev/null); ") }
        append("echo BATT_CURRENT=\$(cat /sys/class/power_supply/battery/current_now 2>/dev/null); ")
        append("echo BATT_VOLTAGE=\$(cat /sys/class/power_supply/battery/voltage_now 2>/dev/null)")
    }

    private fun buildCsvRow(pkg: String, fps: String, frameCount: Int, tagged: Map<String, String>): String {
        fun g(k: String) = tagged[k] ?: "?"
        val cpuTemps = zones.cpuZones.indices.map { tagged["CPUZ_$it"] }
        val gpuTemps = zones.gpuZones.indices.map { tagged["GPUZ_$it"] }
        val fanSignal = fanNode?.let { g("FAN") } ?: "n/a"
        val fanUnit = fanNode?.label ?: "not_found"

        val cols = listOf(
            System.currentTimeMillis().toString(),
            mode.label,
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
        )
        return cols.joinToString(",") { csvEscape(it) }
    }

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
        const val CSV_HEADER =
            "timestamp,mode,sample_idx,foreground_pkg,fps,frame_count," +
                "p0_freq,p0_governor,p2_freq,p2_governor,p5_freq,p5_governor,p7_freq,p7_governor," +
                "gpu_freq_hz,gpu_busy_pct," +
                "temp_cpu_max_c,temp_gpu_max_c,temp_skin_c,temp_battery_c," +
                "fan_signal,fan_signal_unit," +
                "battery_current_ma,battery_voltage_mv"
    }
}
