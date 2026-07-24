package pl.xsubench2.app

/**
 * Test 6 support: full CPU frequency table dump + static GPU reference table.
 *
 * The GPU side is already fully documented in HARDWARE_PROFILE_v6_en.md (7 full
 * diagnostic runs) -- no need to re-derive it here, so it is hardcoded as reference
 * text, not queried. What's actually missing (per that same document's own "still
 * missing" note) is the full scaling_available_frequencies per CPU policy -- only 4
 * discrete AYASpace-mode operating points were ever captured before, not the complete
 * OPP table each policy actually exposes. That's this test's real job.
 */
object HwProfile {

    const val GPU_REFERENCE_TABLE = """CONFIRMED GPU pwrlevels (kgsl devfreq/available_frequencies, Hz), index 0 = highest:
1050000000, 1000000000, 903000000, 834000000, 770000000, 720000000, 680000000,
629000000, 578000000, 500000000, 422000000, 366000000, 310000000, 231000000
(14 entries total; pulse_lite v3.7 only ever used 4 of these)
Source: HARDWARE_PROFILE_v6_en.md, 7 full diagnostic runs. Not re-queried by this app."""

    private val POLICIES = listOf(0, 2, 5, 7)

    /** Batched into ONE xsu call, tagged key=value output -- same convention as the
     * Test 5 snapshot (see FpsPipeline.parseTaggedLines). This is a one-shot reference
     * dump, not a benchmarked/timed operation. */
    fun buildCpuProfileCommand(): String = buildString {
        for (p in POLICIES) {
            append("echo P${p}_AFFECTED_CPUS=\$(cat /sys/devices/system/cpu/cpufreq/policy$p/affected_cpus); ")
            append("echo P${p}_GOVERNOR=\$(cat /sys/devices/system/cpu/cpufreq/policy$p/scaling_governor); ")
            append("echo P${p}_AVAIL_GOVERNORS=\$(cat /sys/devices/system/cpu/cpufreq/policy$p/scaling_available_governors); ")
            append("echo P${p}_MIN_FREQ=\$(cat /sys/devices/system/cpu/cpufreq/policy$p/cpuinfo_min_freq); ")
            append("echo P${p}_MAX_FREQ=\$(cat /sys/devices/system/cpu/cpufreq/policy$p/cpuinfo_max_freq); ")
            append("echo P${p}_AVAIL_FREQS=\$(cat /sys/devices/system/cpu/cpufreq/policy$p/scaling_available_frequencies); ")
        }
    }

    /** Formats the parsed tag map into human-readable reference text. */
    fun formatCpuProfile(tagged: Map<String, String>): String {
        val sb = StringBuilder()
        for (p in POLICIES) {
            sb.appendLine("--- policy$p ---")
            sb.appendLine("affected_cpus: ${tagged["P${p}_AFFECTED_CPUS"] ?: "?"}")
            sb.appendLine("scaling_governor: ${tagged["P${p}_GOVERNOR"] ?: "?"}")
            sb.appendLine("scaling_available_governors: ${tagged["P${p}_AVAIL_GOVERNORS"] ?: "?"}")
            sb.appendLine("cpuinfo_min_freq: ${tagged["P${p}_MIN_FREQ"] ?: "?"}")
            sb.appendLine("cpuinfo_max_freq: ${tagged["P${p}_MAX_FREQ"] ?: "?"}")
            sb.appendLine("scaling_available_frequencies: ${tagged["P${p}_AVAIL_FREQS"] ?: "?"}")
        }
        return sb.toString()
    }

    /** For Test 7: policy0's available governors, split into a list. Reused directly
     * from Test 6's result -- Test 7 must not re-query this itself. */
    fun policy0AvailableGovernors(tagged: Map<String, String>): List<String> =
        (tagged["P0_AVAIL_GOVERNORS"] ?: "").trim().split(Regex("\\s+")).filter { it.isNotBlank() }
}
