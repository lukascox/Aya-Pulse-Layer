package com.kei.pulse.model

/**
 * apl glue gate (2026-08-03): is this a SoC family the glue patch can safely touch?
 *
 * Nothing in this codebase used to ask. That was fine while the only copies lived on the author's two
 * devices; it stopped being fine the moment a signed APK went on a public Releases page, because
 * AYANEO also ships Android handhelds on MediaTek silicon and the people who find this project do not
 * sort themselves by chip.
 *
 * What would happen without the gate is readable in the code, not hypothetical:
 *  - The Qualcomm-only nodes (paths under /sys/module/msm_performance and /sys/class/kgsl) do not
 *    exist elsewhere, so those writes fail harmlessly. Not the problem.
 *  - The generic cpufreq nodes ARE the problem: scaling_max_freq and online, under
 *    /sys/devices/system/cpu/cpuN/, are plain Linux and exist on EVERY SoC. `CpuPolicyDetector` finds
 *    them dynamically, so on unvalidated silicon PULSE would cap clocks and park cores against a
 *    topology nobody checked -- reboot-recoverable, but indistinguishable from a broken device to its
 *    owner.
 *  - Every privileged write goes out as `chmod 666 <path>; echo <v> > <path>; chmod <mode> <path>`
 *    (`PerformanceCommandBuilder`), loosening permissions on whatever path it is handed, as root,
 *    until the next reboot.
 *
 * **The test is the SoC family, deliberately NOT the model name.** A model allowlist would reject
 * AYANEO's other Snapdragon handhelds (Pocket S2, the Elite variant of this same chassis) which are
 * the natural next thing anyone would try, and a false negative here leaves someone holding a dead
 * app. Blocking a whole architecture we have never run a single instruction on is the claim actually
 * worth making.
 *
 * Ground truth for the check: `ro.soc.manufacturer = QTI` on the real unit
 * (`diagnostics/docs/archive/v6/pulse_lite_diag_eden_v6.log`). Android exposes that property as
 * [android.os.Build.SOC_MANUFACTURER] from API 31, and this module's `minSdk` is 31 -- so it is an
 * in-process constant, with none of the process-spawn cost `SocDetector` pays for `getprop`.
 *
 * ### What this does and does not cover
 * The sysfs path is fully covered: generic kernel interfaces are the risk, and "is it Qualcomm" is a
 * sound test for them.
 *
 * The AIDL path in [com.kei.pulse.aidl.AyaAidlClient] is a weaker fit, and the honest reason is that
 * those opcodes are specific to AYANEO's *firmware*, not to the silicon. Non-AYANEO hardware was never
 * at risk (`bind()` targets `com.ayaneo.gamewindow` by name and simply fails to bind where the package
 * is absent), so the real exposure is other AYANEO models -- of which this gate stops the MediaTek ones
 * and admits the Snapdragon ones. That is a deliberate bet, not an oversight. If it ever needs
 * splitting, the shape is: sysfs stays broad, AIDL narrows to confirmed models, and an unknown AYANEO
 * degrades to sysfs-only rather than being blocked outright.
 */
object DeviceSupport {

    /**
     * True when the SoC identifies as Qualcomm.
     *
     * Fails CLOSED: an unset or unrecognised manufacturer (Android's default is the literal string
     * `"unknown"`) is treated as unsupported, because "I cannot tell what I am standing on" is nearer
     * to risk than to safety. `Build.HARDWARE` is folded in as a second opinion -- some OEMs leave
     * `ro.soc.manufacturer` empty while still reporting `qcom` there.
     *
     * Matching is substring-based on purpose: the field is vendor-written prose ("QTI",
     * "Qualcomm Technologies, Inc.", "QUALCOMM"), so an exact-equality test would be a false-negative
     * generator.
     */
    fun isSupportedSoc(socManufacturer: String?, hardware: String?): Boolean {
        val haystack = "${socManufacturer.orEmpty()} ${hardware.orEmpty()}".lowercase()
        return QUALCOMM_MARKERS.any { it in haystack }
    }

    /** Human-readable identity for the log line that explains a refusal. */
    fun describe(manufacturer: String?, model: String?, socManufacturer: String?): String {
        val device = listOfNotNull(
            manufacturer?.takeIf { it.isNotBlank() },
            model?.takeIf { it.isNotBlank() },
        ).joinToString(" ").ifBlank { "unknown device" }
        val soc = socManufacturer?.takeIf { it.isNotBlank() } ?: "unknown"
        return "$device (SoC vendor: $soc)"
    }

    private val QUALCOMM_MARKERS = listOf("qti", "qualcomm", "qcom")
}
