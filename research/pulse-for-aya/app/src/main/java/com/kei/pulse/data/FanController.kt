package com.kei.pulse.data

import com.kei.pulse.aidl.AyaAidlClient
import com.kei.pulse.root.PulseDaemon
import com.kei.pulse.root.RootSupport

/**
 * Fan control for the AYANEO Pocket FIT. Two independent mechanisms, deliberately kept separate:
 *
 * 1. **Discrete vendor mode** (Silent/Smart/Sport) — upstream wrote this via the AYN Odin's
 *    `Settings.System fan_mode` key (`com.odin.settings` reads it). That key does not exist on
 *    AYANEO hardware (`pulse-glue-assessment/FINDINGS.md`), so [readMode] still targets it as-is
 *    (dead, harmless read) — but [setMode] now drives the real AYANEO mechanism instead: the
 *    proven-working AIDL command `com_set_performance_fan` (`research/aidl-fan-spike/FINDINGS.md`
 *    Step 1), via an injected [AyaAidlClient] (see [aidlModeFor] for the mode-int → AIDL-string
 *    mapping). Implemented 2026-07-30 — `research/pulse-for-aya/README.md`'s "Discrete fan mode
 *    implementation plan" section has the full design.
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
     * The live vendor fan mode, preferring [aidlClient]'s callback-cached value -- the ONLY real readback
     * this device has. The Odin `Settings.System fan_mode` key below it is dead here and always reads
     * `null`, confirmed live: every `fan_mode=null` in the drift-log excerpt quoted in
     * `research/pulse-for-aya/README.md` is this read. It is kept as the fallback rather than deleted so
     * the fork stays diffable against upstream and still works on hardware where that key IS live.
     *
     * **Returns `null` until gamewindow has pushed at least one callback** (nothing arrives on connect --
     * `AyaAidlClient.parseFanModeFromCallback`'s doc). Callers that need a value at startup should fall
     * back to PULSE's own persisted `managedFanMode`; a `null` here means "unknown", never "off".
     *
     * `pulseDaemon` routes the fallback read through the daemon's `GETSETTING` (zero `xsu` calls) when
     * available -- same all-or-nothing-per-call contract as [PulseDaemon.readBatch]'s callers.
     */
    fun readMode(pulseDaemon: PulseDaemon? = null, aidlClient: AyaAidlClient? = null): Int? {
        modeForAidl(aidlClient?.lastKnownFanMode())?.let { return it }
        return readModeFromSettings(pulseDaemon)
    }

    /**
     * Drift-detection counterpart to [readMode] for `FanArbiter`'s `readLiveMode` — see
     * [arbitrationModeFor] for why the two must differ. Falls back to the (AYANEO-dead) Settings key
     * only when the vendor has told us nothing at all yet.
     */
    fun readModeForArbitration(pulseDaemon: PulseDaemon? = null, aidlClient: AyaAidlClient? = null): Int? =
        arbitrationModeFor(aidlClient?.lastKnownFanMode()) { readModeFromSettings(pulseDaemon) }

    private fun readModeFromSettings(pulseDaemon: PulseDaemon?): Int? =
        (pulseDaemon?.readSetting("fan_mode") ?: RootSupport.runRootCommand("settings get system fan_mode"))
            ?.trim()?.toIntOrNull()

    /**
     * Prerequisite for the AYANEO duty node to respond: writes [FAN_POWER_PATH] = `1` (unlocking it first,
     * same `chmod 666` pattern as every other sysfs write in this app). Unlike the Odin -- whose
     * `fan_mode=6` write this replaces also reset the duty to a ~50% default, so the caller had to pin its
     * intended duty in the same command -- AYANEO has no "enter manual mode" handshake to negotiate at
     * all: confirmed live that sending `FAN_MODE_CUSTOM` via AIDL first makes no measurable difference to
     * the vendor daemon's reassert behavior (`research/aidl-fan-spike/FINDINGS.md`). So this is only the
     * power-state prerequisite, safe and cheap to call every tick (idempotent), and takes no duty
     * argument. Routes through [pulseDaemon] when available, same fallback contract as [readMode].
     * Always returns `true` -- there's no real "entry can fail" concept here.
     */
    fun ensureManualMode(pulseDaemon: PulseDaemon? = null): Boolean {
        if (pulseDaemon?.setCap(FAN_POWER_PATH, "666", "1") != true) {
            RootSupport.runRootCommand("chmod 666 $FAN_POWER_PATH 2>/dev/null; echo 1 > $FAN_POWER_PATH")
        }
        return true
    }

    /**
     * Drives the real AYANEO discrete-mode mechanism (see class doc): [aidlModeFor] maps [mode] to an
     * AIDL `FAN_MODE_*` string, sent via [aidlClient]. Returns `false` (no write attempted) when [mode]
     * has no AIDL equivalent (CUSTOM -- the service drives that directly, never through here) or when
     * [aidlClient] is `null`/not yet bound (the bind handshake is async; a call landing before it
     * completes just reports "didn't happen", same as any other transient AIDL failure).
     */
    fun setMode(mode: Int, aidlClient: AyaAidlClient? = null): Boolean {
        val aidlMode = aidlModeFor(mode) ?: return false
        val client = aidlClient ?: return false
        return client.sendFanMode(aidlMode).isSuccess
    }

    companion object {
        /** Smart is the confirmed stock default and the safe fallback. */
        const val SMART = 4
        const val SPORT = 5
        /** Silent (low fan) — the quiet bounce mode when re-applying Smart so the fan dips instead of revving. */
        const val SILENT = 1

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
         * [SILENT]/[SMART]/[SPORT] → the AIDL `FAN_MODE_*` string `com_set_performance_fan` expects
         * (`AyaAidlClient.sendFanMode`, confirmed working `research/aidl-fan-spike/FINDINGS.md` Step 1).
         * [CUSTOM] and any unrecognized mode return `null` -- Custom never goes through AIDL (the service
         * drives the PWM curve directly). `FAN_MODE_MUTE`/`BALANCE`/`TURBO` are named for loudness, not
         * 1:1 confirmed against our Silent/Smart/Sport labels beyond OFF/MUTE's clean duty readback (0/76)
         * -- BALANCE/TURBO tracked noisier in testing (real thermal load, not a mapping error) -- worth a
         * quick listen-test per mode once live (`README.md`'s implementation plan, test procedure).
         */
        fun aidlModeFor(mode: Int): String? = when (mode) {
            SILENT -> "FAN_MODE_MUTE"
            SMART -> "FAN_MODE_BALANCE"
            SPORT -> "FAN_MODE_TURBO"
            else -> null
        }

        /**
         * Inverse of [aidlModeFor], for reading gamewindow's callback state back into our mode ints.
         * `FAN_MODE_OFF` and `FAN_MODE_CUSTOM` are real vendor states we can RECEIVE but deliberately
         * never send (OFF has no slot in [MODES]; CUSTOM is the vendor's own curve mode, unrelated to
         * PULSE's [CUSTOM] which drives the PWM node directly) -- they return `null` here, meaning
         * "not one of ours". Callers deciding whether the fan has DRIFTED must not treat that `null`
         * as "unknown" -- see [arbitrationModeFor], which exists precisely because conflating the two
         * was a real bug (2026-07-31).
         */
        fun modeForAidl(aidlMode: String?): Int? = when (aidlMode) {
            "FAN_MODE_MUTE" -> SILENT
            "FAN_MODE_BALANCE" -> SMART
            "FAN_MODE_TURBO" -> SPORT
            else -> null
        }

        /**
         * Stands for "the vendor is in a state PULSE has no mode for" (`FAN_MODE_OFF` /
         * `FAN_MODE_CUSTOM`). Deliberately outside the 1/4/5/6 range so it can never equal a mode the
         * arbiter might want, i.e. it always compares as drift. Internal to arbitration -- never
         * persisted, never sent, and never surfaced ([readMode] still reports `null` for these, so the
         * Tuner falls back to PULSE's own persisted selection instead of showing "Mode -1").
         */
        const val VENDOR_UNMANAGED = -1

        /**
         * The arbiter's view of the live mode, kept separate from [readMode] because the two need
         * OPPOSITE handling of a vendor state PULSE doesn't manage.
         *
         * A `null` from `readLiveMode` makes `FanArbiter.decide` bail out of the whole tick
         * (`readLiveMode() ?: return FanAction.None`). That is right for "we genuinely don't know
         * yet", and wrong for "the vendor is in a mode we don't manage": before this existed, setting
         * the fan to OFF in native AyaSettings left PULSE permanently hands-off, so a fan someone had
         * switched off was never corrected even with PULSE actively managing Smart. Found on-device
         * 2026-07-31 by exactly that experiment. OFF is the one vendor state that leaves the device
         * with no active cooling at all, which is what made this worth fixing rather than documenting.
         */
        fun arbitrationModeFor(aidlMode: String?, settingsFallback: () -> Int?): Int? =
            if (aidlMode == null) settingsFallback() else (modeForAidl(aidlMode) ?: VENDOR_UNMANAGED)

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
