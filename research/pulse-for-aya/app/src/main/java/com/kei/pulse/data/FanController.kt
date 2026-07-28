package com.kei.pulse.data

import com.kei.pulse.root.PulseDaemon
import com.kei.pulse.root.RootSupport

/**
 * Fan control for the AYN Odin family via the stock `Settings.System` keys that the
 * device's own fan controller (com.odin.settings) reads. We do NOT write raw PWM — we set
 * the same fan *mode* the stock app sets, so the stock thermal-safety curve stays in
 * charge. Confirmed on Odin 3: `fan_mode=4` is Smart (the stock default).
 *
 * Values are written through PServer as root via `settings put`.
 *
 * apl glue patch (2026-07-25): fan control is explicitly OUT OF SCOPE for this device. Neither of
 * upstream's two mechanisms (this Settings.System `fan_mode` key, read by `com.odin.settings` --
 * AYN-vendor-specific -- and the `gpio5_pwm2` PWM path below) exist on AYANEO hardware; native
 * AyaSettings owns fan control here and does it well, and the plan is a dedicated AIDL-based fan
 * loop later (see pulse-glue-assessment/FINDINGS.md), not this. [ensureManualMode], [setMode], and
 * [customFanAvailable] are stubbed to no-ops rather than left to rely on the upstream self-gating
 * (which only covers the PWM path, not the ungated `fan_mode` writes several call sites made
 * unconditionally) -- this guarantees zero fan-touching root calls regardless of what any caller
 * does, instead of assuming an unverified Settings key is dead everywhere it's touched.
 */
class FanController {

    /**
     * `pulseDaemon` optional and defaults to `null` (raw `xsu`, the original behavior) so every existing
     * call site keeps working unchanged. STATUS.md, 2026-07-28: this is the one live, unmigrated fan-related
     * `xsu` call found on this device -- [ensureManualMode]/[setMode]/[customFanAvailable] are all stubbed
     * no-ops here (class doc), and the 120ms PWM-duty reassert loop never runs since [customFanAvailable]
     * is always false -- but the discrete `fan_mode` key IS read live, roughly once a second, from the fan
     * arbiter's tick. Routes through the daemon's `GETSETTING` (zero `xsu` calls) when available, falling
     * back to the original raw `xsu` read otherwise -- same all-or-nothing-per-call contract as
     * [PulseDaemon.readBatch]'s callers.
     */
    fun readMode(pulseDaemon: PulseDaemon? = null): Int? =
        (pulseDaemon?.readSetting("fan_mode") ?: RootSupport.runRootCommand("settings get system fan_mode"))
            ?.trim()?.toIntOrNull()

    /** Stubbed no-op (see class doc) -- never touches `fan_mode` or the PWM duty node on this device. */
    fun ensureManualMode(reassertDuty: Int? = null): Boolean = false

    /** Stubbed no-op (see class doc) -- native AyaSettings remains the sole fan-mode writer. */
    fun setMode(mode: Int): Boolean = false

    companion object {
        /** Smart is the confirmed stock default and the safe fallback. */
        const val SMART = 4
        const val SPORT = 5
        /** Silent (low fan) — the quiet bounce mode when re-applying Smart so the fan dips instead of revving. */
        const val SILENT = 1

        /**
         * The intermediate mode [setMode] bounces through to force the stock controller to reload [target]
         * (it caches the active mode and won't re-apply the same one). Must differ from [target]. Reaching
         * SMART routes through SILENT (low fan) rather than SPORT (high fan), so handing the fan back to Smart
         * — e.g. when AutoTDP restores it on game-exit — dips quietly instead of audibly revving the fan.
         */
        fun bounceModeFor(target: Int): Int = if (target == SMART) SILENT else SMART

        /**
         * PULSE-driven custom fan curve (Odin 3 only). Not a stock fan_mode — when selected, the service
         * drives the fan via [FanCurveController] (re-asserting the gpio5_pwm2/duty PWM node) instead of
         * writing fan_mode. Picked a value outside the stock 1/4/5 range so it can't collide.
         */
        const val CUSTOM = 6

        // The Odin 3's fan is a MAX31760 driven via this vendor PWM node (NOT the stock Settings keys):
        //   duty (0..period, world-writable) = fan speed; period (=50000); speed = RPM tach (read).
        // Writable on the Odin; absent on Thor/RP6 (different fan path) — gate on customFanAvailable.
        const val FAN_DUTY_PATH = "/sys/class/gpio5_pwm2/duty"
        const val FAN_PERIOD_PATH = "/sys/class/gpio5_pwm2/period"
        const val FAN_SPEED_PATH = "/sys/class/gpio5_pwm2/speed"

        // Confirmed on Odin 3 via the device's own fan toggle: Silent=1, Smart=4, Sport=5; Custom is ours.
        // The fan card shows the live fan_mode number, so any mismatch is self-revealing.
        val MODES: List<FanMode> = listOf(
            FanMode(SILENT, "Silent"),
            FanMode(SMART, "Smart"),
            FanMode(SPORT, "Sport"),
            FanMode(CUSTOM, "Custom"),
        )

        fun labelFor(mode: Int?): String =
            MODES.firstOrNull { it.value == mode }?.label ?: mode?.let { "Mode $it" } ?: "—"

        /**
         * Confirmed on-device (2026-07-25, `xsu -c "cat /sys/class/gpio5_pwm2/duty"` -> empty) that
         * this node doesn't exist on AYANEO -- stubbed to always false rather than left to the
         * upstream self-gating probe, matching [setMode]/[ensureManualMode] above (see class doc).
         */
        fun customFanAvailable(): Boolean = false
    }
}

data class FanMode(val value: Int, val label: String)
