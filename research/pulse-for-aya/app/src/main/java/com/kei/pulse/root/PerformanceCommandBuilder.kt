package com.kei.pulse.root

import com.kei.pulse.model.CpuPolicyInfo

/** One sysfs node write: unlock (666) -> echo [value] -> lock ([mode]) -- the unit [PulseDaemon.setCap] and the
 * xsu fallback script both perform, so a caller can build the list once and dispatch it either way. */
data class CapWrite(val path: String, val mode: String, val value: Long)

class PerformanceCommandBuilder {

    /**
     * @param lowerMinPolicyIds CPU clusters whose `scaling_min_freq` should ALSO be written down to their
     *   floor (before the max). This is **per-cluster on purpose**. The vendor perf HAL pins the *prime*
     *   cluster's min high during gaming (~3 GHz), so without lowering its min the kernel rejects any lower
     *   `scaling_max` (max < min) and the prime never drops. BUT writing a cluster's `scaling_min` wakes the
     *   HAL, which re-asserts that cluster's OPP — harmless for the prime (we want it pinned low) but it
     *   stomps the *perf* cluster's `scaling_max` back up. So AutoTDP passes ONLY the prime here: prime min
     *   drops (prime can be capped) while the perf cluster's min is left alone (its cap keeps biting). On
     *   reset, pass every CPU id (writable `644`) to hand min control back to the HAL and clear stale locks.
     */
    fun buildApplyWrites(
        policies: List<CpuPolicyInfo>,
        selectedValues: Map<Int, Int>,
        isReset: Boolean,
        lowerMinPolicyIds: Set<Int> = emptySet(),
    ): List<CapWrite> {
        val writes = mutableListOf<CapWrite>()
        val targetMode = if (isReset) "644" else "444"

        policies.forEach { policy ->
            val value = selectedValues[policy.id] ?: return@forEach
            if (policy.isGpu) {
                appendGpuLevel(writes, policy, value)
            } else {
                // Never echo a non-positive frequency to a CPU freq node — the kernel rejects it / the result
                // is undefined. A 0/negative value here means malformed detection (or a reset/uninstall edge);
                // skip the cluster entirely rather than write garbage.
                if (value <= 0) return@forEach
                if (policy.id in lowerMinPolicyIds) {
                    val floor = policy.supportedFrequencies.minOrNull() ?: policy.minFreq
                    if (floor > 0) writes += CapWrite("${policy.policyPath}/scaling_min_freq", targetMode, floor.toLong())
                }
                writes += CapWrite(policy.scalingMaxPath, targetMode, value.toLong())
            }
        }
        return writes
    }

    /** Same writes as [buildApplyWrites], flattened into one `xsu`-runnable shell script. Only used by
     * [buildApplyScript] now (kept for that + test compatibility) -- the live xsu-fallback path
     * ([com.kei.pulse.data.AutoTuneController]'s `dispatch()`) uses [statementsFor] instead so it can chunk
     * under the length this device's `xsud` crashes past (STATUS.md, 2026-07-28; the confirmed threshold is
     * `research/xsu-capability-probe/FINDINGS.md`'s bisection: safe under ~800 chars, a fuzzy failure band
     * ~1000-1200, consistently fails above that -- a single combined script for even a modest write batch
     * (7-10 nodes) lands well inside that failing range). */
    fun scriptFor(writes: List<CapWrite>): String = buildString {
        appendLine("#!/system/bin/sh")
        writes.forEach { w ->
            appendLine("chmod 666 ${w.path}")
            appendLine("echo ${w.value} > ${w.path}")
            appendLine("chmod ${w.mode} ${w.path}")
        }
    }

    /** One semicolon-joinable statement per write (`chmod 666 X; echo V > X; chmod M X`) -- the unit
     * [statementsFor]'s caller chunks by, so a single write's unlock/echo/relock triple never gets split
     * across two separate `xsu` calls (which could leave a node stuck mid-unlock if the first call's
     * connection is the one that fails). */
    fun statementsFor(writes: List<CapWrite>): List<String> = writes.map { w ->
        "chmod 666 ${w.path}; echo ${w.value} > ${w.path}; chmod ${w.mode} ${w.path}"
    }

    fun buildApplyScript(
        policies: List<CpuPolicyInfo>,
        selectedValues: Map<Int, Int>,
        isReset: Boolean,
        lowerMinPolicyIds: Set<Int> = emptySet(),
    ): String = scriptFor(buildApplyWrites(policies, selectedValues, isReset, lowerMinPolicyIds))

    /**
     * Adreno is capped by power-level INDEX (fastest = 0). The kernel clamps `max_pwrlevel`
     * so it can never be a higher index (slower level) than `min_pwrlevel`. If the device's
     * default `min_pwrlevel` sits above our target the cap silently snaps back — which is
     * why only level 0 (uncapped) "stuck" before. So we widen `min_pwrlevel` to the slowest
     * level first (full downscale headroom), then set the ceiling.
     */
    private fun appendGpuLevel(
        writes: MutableList<CapWrite>,
        policy: CpuPolicyInfo,
        valueKHz: Int,
    ) {
        val maxLevel = gpuPowerLevelFor(policy, valueKHz)
        val slowestLevel = (policy.supportedFrequencies.size - 1).coerceAtLeast(maxLevel)
        val minPath = "${policy.policyPath}/min_pwrlevel"
        // Always lock the GPU bounds read-only (444). On the Max tier (isReset) the CPU nodes
        // are intentionally left writable for stock behaviour, but doing the same for the GPU
        // lets the vendor performance daemon stomp min_pwrlevel back up and floor the GPU
        // mid-range (~900MHz). Locking min=slowest / max=ceiling keeps it free to idle down
        // and scale up to its cap.
        writes += CapWrite(minPath, "444", slowestLevel.toLong())
        writes += CapWrite(policy.scalingMaxPath, "444", maxLevel.toLong())
    }

    /** ascending kHz table -> power level (fastest = 0). */
    private fun gpuPowerLevelFor(policy: CpuPolicyInfo, valueKHz: Int): Int {
        val asc = policy.supportedFrequencies
        if (asc.isEmpty()) return 0
        val pos = asc.indexOf(valueKHz).let { exact ->
            if (exact >= 0) exact else asc.indices.minByOrNull { kotlin.math.abs(asc[it] - valueKHz) } ?: asc.lastIndex
        }
        return (asc.size - 1 - pos).coerceIn(0, asc.lastIndex)
    }
}
