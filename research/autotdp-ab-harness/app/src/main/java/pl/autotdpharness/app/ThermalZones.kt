package pl.autotdpharness.app

/**
 * Thermal zone resolution for the A/B harness's sampling loop. Resolved dynamically
 * by zone `type` prefix at session start (matching pulse_lite_diag_v8.sh's own
 * "resolve dynamically, don't hardcode zone numbers" principle -- zone numbers can
 * shift across firmware revisions, per HARDWARE_PROFILE.md).
 */
data class ZoneMap(
    val cpuZones: List<String>,
    val gpuZones: List<String>,
    val skinZone: String?,
    val batteryZone: String?,
)

object ThermalZones {

    fun buildResolveCommand(): String =
        "for f in /sys/class/thermal/thermal_zone*/type; do d=\$(dirname \$f); echo \"ZONE:\$d:\$(cat \$f 2>/dev/null)\"; done"

    fun parseResolveOutput(stdout: String): ZoneMap {
        val cpuZones = mutableListOf<String>()
        val gpuZones = mutableListOf<String>()
        var skinZone: String? = null
        var batteryZone: String? = null

        for (line in stdout.lines()) {
            if (!line.startsWith("ZONE:")) continue
            val rest = line.removePrefix("ZONE:")
            val parts = rest.split(":", limit = 2)
            if (parts.size < 2) continue
            val dir = parts[0]
            val type = parts[1]
            val tempPath = "$dir/temp"
            when {
                type.startsWith("cpu", ignoreCase = true) -> cpuZones.add(tempPath)
                type.startsWith("gpu", ignoreCase = true) -> gpuZones.add(tempPath)
                type.contains("skin", ignoreCase = true) -> skinZone = tempPath
                type.startsWith("battery", ignoreCase = true) -> batteryZone = tempPath
            }
        }
        return ZoneMap(cpuZones, gpuZones, skinZone, batteryZone)
    }

    /** Millidegrees C -> a plain "%.1f" Celsius string, or "n/a" if unparseable. */
    fun formatMaxTempC(rawValues: List<String?>): String {
        val maxMilli = rawValues.mapNotNull { it?.trim()?.toLongOrNull() }.maxOrNull() ?: return "n/a"
        return "%.1f".format(maxMilli / 1000.0)
    }

    fun formatTempC(rawValue: String?): String {
        val milli = rawValue?.trim()?.toLongOrNull() ?: return "n/a"
        return "%.1f".format(milli / 1000.0)
    }
}
