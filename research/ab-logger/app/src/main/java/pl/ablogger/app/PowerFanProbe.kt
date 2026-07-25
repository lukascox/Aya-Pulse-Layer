package pl.ablogger.app

/**
 * Test 8: fan RPM / power-draw node discovery. Nothing here has been confirmed to
 * exist before -- ro.build.flavor is "AYANEO_PocketS2-user" (a "user" build), where
 * debugfs (where per-rail power monitors often live on Qualcomm platforms) is
 * frequently unmounted/restricted even under full root. Discover first, log exactly
 * what's found (including "not found") -- do not hardcode any path as if confirmed.
 *
 * Uses a block-tagged output format (===TAG===) rather than the single-line
 * KEY=value convention used elsewhere, because several of these commands
 * (`find`, `ls`) naturally produce multi-line output.
 */
data class DiscoveryResult(
    val hwmonEntries: List<String>,
    val coolingDevices: List<Pair<String, String>>, // name to type
    val fanSearchHits: List<String>,
    val powerSupplyEntries: List<String>,
    val batteryCurrentNow: String?,
    val batteryVoltageNow: String?,
    val batteryPowerNow: String?,
    val debugfsMounted: Boolean,
) {
    val fanLikeCoolingDevices: List<Pair<String, String>>
        get() = coolingDevices.filter { it.second.contains("fan", ignoreCase = true) }
}

object PowerFanProbe {

    fun buildDiscoveryCommand(): String = buildString {
        appendLine("echo ===HWMON===")
        appendLine("find /sys/class/hwmon -maxdepth 2 2>/dev/null")
        appendLine("echo ===COOLDEV===")
        appendLine("for f in /sys/class/thermal/cooling_device*; do echo \"\$(basename \$f):\$(cat \$f/type 2>/dev/null)\"; done")
        appendLine("echo ===FANSEARCH===")
        appendLine("find /sys -iname '*fan*' 2>/dev/null | head -50")
        appendLine("echo ===POWERSUPPLY===")
        appendLine("ls /sys/class/power_supply/ 2>/dev/null")
        appendLine("echo ===BATT_CURRENT===")
        appendLine("cat /sys/class/power_supply/battery/current_now 2>/dev/null")
        appendLine("echo ===BATT_VOLTAGE===")
        appendLine("cat /sys/class/power_supply/battery/voltage_now 2>/dev/null")
        appendLine("echo ===BATT_POWER===")
        appendLine("cat /sys/class/power_supply/battery/power_now 2>/dev/null")
        appendLine("echo ===DEBUGFS_MOUNT===")
        append("mount | grep debugfs 2>/dev/null")
    }

    fun parseBlockTags(stdout: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var currentTag: String? = null
        val buffer = StringBuilder()
        val tagRegex = Regex("^===(.+)===$")
        fun flush() {
            currentTag?.let { result[it] = buffer.toString().trim() }
            buffer.clear()
        }
        for (line in stdout.lines()) {
            val m = tagRegex.find(line.trim())
            if (m != null) {
                flush()
                currentTag = m.groupValues[1]
            } else {
                buffer.appendLine(line)
            }
        }
        flush()
        return result
    }

    fun parseDiscovery(blocks: Map<String, String>): DiscoveryResult {
        fun linesOf(tag: String) = blocks[tag]?.lines()?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        val cooldevs = linesOf("COOLDEV").mapNotNull { line ->
            val idx = line.indexOf(":")
            if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
        }
        return DiscoveryResult(
            hwmonEntries = linesOf("HWMON"),
            coolingDevices = cooldevs,
            fanSearchHits = linesOf("FANSEARCH"),
            powerSupplyEntries = linesOf("POWERSUPPLY"),
            batteryCurrentNow = blocks["BATT_CURRENT"]?.trim()?.takeIf { it.isNotBlank() },
            batteryVoltageNow = blocks["BATT_VOLTAGE"]?.trim()?.takeIf { it.isNotBlank() },
            batteryPowerNow = blocks["BATT_POWER"]?.trim()?.takeIf { it.isNotBlank() },
            debugfsMounted = !blocks["DEBUGFS_MOUNT"]?.trim().isNullOrBlank(),
        )
    }

    /** Follow-up read for cur_state/max_state of any fan-like cooling device found. */
    fun buildFanReadCommand(fanDeviceNames: List<String>): String = buildString {
        for (name in fanDeviceNames) {
            append("echo ${name}_CUR=\$(cat /sys/class/thermal/$name/cur_state 2>/dev/null); ")
            append("echo ${name}_MAX=\$(cat /sys/class/thermal/$name/max_state 2>/dev/null); ")
        }
    }
}

/** A resolved fan signal source for the A/B harness's sampling loop, or null if
 * Test 8 found nothing usable. cur_state is a step index, NOT RPM -- label clearly. */
data class FanNode(val name: String, val curStatePath: String, val label: String)
