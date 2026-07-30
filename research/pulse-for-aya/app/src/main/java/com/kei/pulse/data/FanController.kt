package com.kei.pulse.data

import com.kei.pulse.root.PulseDaemon
import com.kei.pulse.root.RootSupport

/**
 * Fan control for the AYANEO Pocket FIT. Two independent mechanisms, deliberately kept separate:
 *
 * 1. **Discrete vendor mode** (Silent/Smart/Sport) — upstream wrote this via the AYN Odin's
 *    `Settings.System fan_mode` key (`com.odin.settings` reads it). That key does not exist on
 *    AYANEO hardware (`pulse-glue-assessment/FINDINGS.md`) — [setMode]/[readMode] are left
 *    stubbed/inert here (they still target the dead key rather than being rewired) since the real
 *    AYANEO discrete-mode mechanism is a proven-working AIDL command
 *    (`com_set_performance_fan`, `research/aidl-fan-spike/FINDINGS.md` Step 1) that is a separate,
 *    smaller follow-up, not bundled into the curve work below.
 * 2. **Custom fan curve** (the actual upstream-parity goal, [FanCurveController]) — upstream wrote
 *    raw PWM duty to the Odin's `gpio5_pwm2` node (also absent on AYANEO). AYANEO's real duty node
 *    is [FAN_DUTY_PATH] below, confirmed live 2026-07-30 (`research/aidl-fan-spike/FINDINGS.md`):
 *    a plain 0-255 PWM duty at `hwmon0/pwm1`, gated by [FAN_POWER_PATH] needing to be `1` first
 *    (`AR13.n1()`, `research/aya-gamewindows-teardown/FINDINGS.md` section 6), writable only after
 *    a `chmod 666` unlock (the vendor's own fan daemon then re-asserts its own idle value on an
 *    irregular 1-112s cycle — measured, not guessed, 17 samples in
 *    `research/aidl-fan-spike/results/run5/` — which [FanCurveController]'s existing 120ms
 *    reassert loop, built for the Odin's faster re-pinning, already comfortably out-paces).
 */
class FanController {

    /**
     * `pulseDaemon` optional and defaults to `null` (raw `xsu`, the original behavior) so every existing
     * call site keeps working unchanged. Routes through the daemon's `GETSETTING` (zero `xsu` calls) when
     * available, falling back to the original raw `xsu` read otherwise -- same all-or-nothing-per-call
     * contract as [PulseDaemon.readBatch]'s callers. Reads the (AYANEO-dead, see class doc) vendor
     * `fan_mode` key -- kept as-is, not rewired to AIDL (out of scope for the curve work, see class doc).
     */
    fun readMode(pulseDaemon: PulseDaemon? = null): Int? =
        (pulseDaemon?.readSetting("fan_mode") ?: RootSupport.runRootCommand("settings get system fan_mode"))
            ?.trim()?.toIntOrNull()

    /**
     * Prerequisite for the AYANEO duty node to respond: writes [FAN_POWER_PATH] = `1` (unlocking it first,
     * same `chmod 666` pattern as every other sysfs write in this app). Unlike the Odin (whose
     * `fan_mode=6` write this replaces also reset the duty to a ~50% default, hence the now-unused
     * [reassertDuty] param this signature keeps for call-site compatibility), AYANEO has no "enter manual
     * mode" handshake to negotiate -- confirmed live that sending `FAN_MODE_CUSTOM` via AIDL first makes no
     * measurable difference to the vendor daemon's reassert behavior
     * (`research/aidl-fan-spike/FINDINGS.md`) -- so this is just the power-state prerequisite, safe and
     * cheap to call every tick (idempotent). Routes through [pulseDaemon] when available, same fallback
     * contract as [readMode]. Always returns `true` -- there's no real "entry can fail" concept here.
     */
    fun ensureManualMode(reassertDuty: Int? = null, pulseDaemon: PulseDaemon? = null): Boolean {
        if (pulseDaemon?.setCap(FAN_POWER_PATH, "666", "1") != true) {
            RootSupport.runRootCommand("chmod 666 $FAN_POWER_PATH 2>/dev/null; echo 1 > $FAN_POWER_PATH")
        }
        return true
    }

    /** Stubbed no-op -- the vendor `fan_mode` key this would write is dead on AYANEO (see class doc);
     *  the real discrete-mode mechanism (AIDL) is a separate, not-yet-wired follow-up. */
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
         * PULSE-driven custom fan curve. Not a stock fan_mode — when selected, the service drives the fan
         * via [FanCurveController] (re-asserting [FAN_DUTY_PATH]) instead of writing fan_mode. Picked a
         * value outside the stock 1/4/5 range so it can't collide.
         */
        const val CUSTOM = 6

        // AYANEO Pocket FIT's fan is a plain Linux pwm-fan driver, confirmed live 2026-07-30
        // (research/aidl-fan-spike/FINDINGS.md): a fixed 0-255 PWM duty (no period register to read --
        // see FanCurveController.DEFAULT_PERIOD), gated by FAN_POWER_PATH needing to be "1" first
        // (AR13.n1(), aya-gamewindows-teardown/FINDINGS.md section 6), both writable only after a
        // chmod 666 unlock (not world-writable like the Odin's node was).
        const val FAN_DUTY_PATH = "/sys/devices/platform/soc/soc:pwm-fan/hwmon/hwmon0/pwm1"
        const val FAN_POWER_PATH = "/sys/devices/platform/soc/soc:pwm-fan/fan_power_state"
        const val FAN_SPEED_PATH = "/sys/devices/platform/soc/soc:pwm-fan/fan_rpm_state"

        // Confirmed on Odin 3 via the device's own fan toggle: Silent=1, Smart=4, Sport=5; Custom is ours.
        // The fan card shows the live fan_mode number, so any mismatch is self-revealing. These discrete
        // modes are currently inert on AYANEO (see class doc) -- kept as-is, not AYANEO-specific.
        val MODES: List<FanMode> = listOf(
            FanMode(SILENT, "Silent"),
            FanMode(SMART, "Smart"),
            FanMode(SPORT, "Sport"),
            FanMode(CUSTOM, "Custom"),
        )

        fun labelFor(mode: Int?): String =
            MODES.firstOrNull { it.value == mode }?.label ?: mode?.let { "Mode $it" } ?: "—"

        /**
         * Real probe (2026-07-30): available iff [FAN_DUTY_PATH] exists and reads as a plain integer.
         * One-shot and cached by the caller ([ForegroundAppMonitorService.isCustomFanSupported]), so a raw
         * `xsu` call here (not routed through the daemon) is fine -- matches this app's existing convention
         * for one-time probes (e.g. `RootExec`'s own root-availability check).
         */
        fun customFanAvailable(): Boolean =
            RootSupport.runRootCommand("cat $FAN_DUTY_PATH 2>/dev/null")?.trim()?.toIntOrNull() != null

        /**
         * Parses AYANEO's `fan_rpm_state` read format (`"Current RPM 2666"`) down to the bare integer --
         * unlike the Odin's tach node, which returned a bare number directly. Returns `null` for any
         * unparseable/empty input (node absent, read failed).
         */
        fun parseRpm(raw: String?): Int? = raw?.trim()?.substringAfterLast(' ')?.toIntOrNull()
    }
}

data class FanMode(val value: Int, val label: String)
