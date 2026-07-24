package pl.autotdpharness.app

/**
 * Test 6 support: full CPU frequency table dump + static GPU reference table.
 * GPU side already fully documented in apl-diag's HARDWARE_PROFILE.md (7 diagnostic
 * runs) -- hardcoded here as reference text, not queried. What's missing is the full
 * scaling_available_frequencies per CPU policy (only 4-5 discrete AYASpace-mode
 * points were ever captured before).
 */
object HwProfile {

    const val GPU_REFERENCE_TABLE = """CONFIRMED GPU pwrlevels (kgsl devfreq/available_frequencies, Hz), index 0 = highest:
1050000000, 1000000000, 903000000, 834000000, 770000000, 720000000, 680000000,
629000000, 578000000, 500000000, 422000000, 366000000, 310000000, 231000000
(14 entries total; pulse_lite v3.7 only ever used 4 of these)
Source: apl-diag repo, HARDWARE_PROFILE.md. Not re-queried by this app."""

    val POLICIES = listOf(0, 2, 5, 7)

    /**
     * v2 fix: one command PER POLICY, not one giant 24-line batched call. Run 1
     * (autotdp-ab-harness) saw this test come back with EVERY field as "?" -- with
     * no raw exitCode/error logged for the call at the time (a separate bug, also
     * fixed), so the exact cause wasn't confirmed, but the leading hypothesis is a
     * timeout on the batched call combined with block-buffered stdout (a killed
     * process loses its ENTIRE buffer, not just what came after the kill point) --
     * the same class of stall documented in xsu-capability-probe/FINDINGS.md.
     * Splitting into 4 smaller calls costs ~4x the ~100ms floor (still trivial for a
     * one-shot test) in exchange for: a stall in one policy's query no longer zeroes
     * out all four, and each call's own exitCode/error is now directly attributable.
     */
    fun buildCpuProfileCommandForPolicy(p: Int): String = buildString {
        append("echo P${p}_AFFECTED_CPUS=\$(cat /sys/devices/system/cpu/cpufreq/policy$p/affected_cpus); ")
        append("echo P${p}_GOVERNOR=\$(cat /sys/devices/system/cpu/cpufreq/policy$p/scaling_governor); ")
        append("echo P${p}_AVAIL_GOVERNORS=\$(cat /sys/devices/system/cpu/cpufreq/policy$p/scaling_available_governors); ")
        append("echo P${p}_MIN_FREQ=\$(cat /sys/devices/system/cpu/cpufreq/policy$p/cpuinfo_min_freq); ")
        append("echo P${p}_MAX_FREQ=\$(cat /sys/devices/system/cpu/cpufreq/policy$p/cpuinfo_max_freq); ")
        append("echo P${p}_AVAIL_FREQS=\$(cat /sys/devices/system/cpu/cpufreq/policy$p/scaling_available_frequencies)")
    }

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

    /** For Test 7: policy0's available governors, reused directly from Test 6's
     * result -- Test 7 must not re-query this itself. */
    fun policy0AvailableGovernors(tagged: Map<String, String>): List<String> =
        (tagged["P0_AVAIL_GOVERNORS"] ?: "").trim().split(Regex("\\s+")).filter { it.isNotBlank() }
}
