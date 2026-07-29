package pl.aidlfanspike.app

/**
 * Thermal zone resolution, ported verbatim (package renamed) from
 * research/ab-logger's ThermalZones.kt -- same already-proven "resolve dynamically by
 * zone `type` prefix, don't hardcode zone numbers" approach (zone numbers can shift
 * across firmware revisions, per diagnostics/docs/HARDWARE_PROFILE.md).
 */
data class ZoneMap(
    val cpuZones: List<String>,
    val gpuZones: List<String>,
)

object ThermalZones {

    fun buildResolveCommand(): String =
        "for f in /sys/class/thermal/thermal_zone*/type; do d=\$(dirname \$f); echo \"ZONE:\$d:\$(cat \$f 2>/dev/null)\"; done"

    fun parseResolveOutput(stdout: String): ZoneMap {
        val cpuZones = mutableListOf<String>()
        val gpuZones = mutableListOf<String>()

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
            }
        }
        return ZoneMap(cpuZones, gpuZones)
    }

    /** Millidegrees C -> a plain "%.1f" Celsius string, or "n/a" if unparseable. */
    fun formatMaxTempC(rawValues: List<String?>): String {
        val maxMilli = rawValues.mapNotNull { it?.trim()?.toLongOrNull() }.maxOrNull() ?: return "n/a"
        return "%.1f".format(maxMilli / 1000.0)
    }
}
