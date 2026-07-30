package com.kei.pulse.data

import com.kei.pulse.model.FanCurve
import com.kei.pulse.root.PulseDaemon
import com.kei.pulse.root.RootSupport
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Drives the AYANEO Pocket FIT fan from a [FanCurve] as a CONTINUOUS slew so the fan ramps smoothly instead
 * of audibly stepping. Two cadences:
 *  - [setTarget] (slow, ~1s telemetry tick) picks the curve's % for the live SoC temp — the goal.
 *  - [slew] (fast, ~120ms loop, see [ForegroundAppMonitorService.FAN_RECHECK_MS]) eases the applied %
 *    toward that goal by [slewPerSecond] × dt and WRITES the duty every pass. That write is also the
 *    re-assert that beats the vendor fan daemon (measured 2026-07-30, `research/aidl-fan-spike/
 *    FINDINGS.md`: an irregular 1-112s re-pin cycle, comfortably out-paced by this loop's 120ms cadence).
 *
 * Safety: at/above [FanCurve.THERMAL_OVERRIDE_C] the slew SNAPS straight to 100% (never ramps into an
 * overheat). The applied % can never write below the vendor-safe floor ([FanCurve.percentToDuty] clamps).
 * Device I/O is injected so the slew logic is unit-testable; the companion supplies the real sysfs impls
 * (plain no-daemon versions, used as the constructor defaults and by [ui.TunerViewModel]'s one-shot
 * autocalibrate sweep, plus daemon-routed overloads the service wires in for the continuous reassert loop
 * — see those functions' docs). The caller assigns the *effective* curve (any Cooler/Quieter bias already
 * folded in) and decides when to drive — see [FanController.customFanAvailable]; stopping (just stop
 * calling [slew]) hands the fan back.
 */
class FanCurveController(
    private val writeDuty: (Int) -> Unit = Companion::writeDutyToDevice,
    private val readRpm: () -> Int? = Companion::readRpmFromDevice,
) {
    var curve: FanCurve = FanCurve.DEFAULT
    var slewPerSecond: Int = FanCurve.DEFAULT_SLEW
    var period: Int = DEFAULT_PERIOD

    private var appliedF: Float = FanCurve.MIN_PERCENT.toFloat()
    private var targetPercent: Int = FanCurve.MIN_PERCENT
    private var forceFull: Boolean = false
    @Volatile private var lastWrittenDuty: Int = -1 // -1 = nothing written yet → force the first write

    val appliedPercent: Int get() = appliedF.roundToInt()

    /** Pick the goal % for [tempC] from the curve. At/above the thermal trip, latch a snap-to-full. */
    fun setTarget(tempC: Int) {
        forceFull = tempC >= FanCurve.THERMAL_OVERRIDE_C
        targetPercent = if (forceFull) 100 else curve.percentFor(tempC)
    }

    /** Set the goal duty % directly (the closed-loop [FanTempController] path, which bypasses the curve). The
     *  caller already applied its own thermal-trip safety, so [percent] == 100 just slews to full normally. */
    fun setTargetPercent(percent: Int) {
        forceFull = false
        targetPercent = percent.coerceIn(FanCurve.MIN_PERCENT, 100)
    }

    /**
     * Ease the applied % toward the target by at most [slewPerSecond] × [dtMillis], then write the duty —
     * but ONLY when it actually changed. Re-writing the same value every tick is needless and risks the RPM
     * tach reading a transient mid-write glitch. Writing on change keeps the ramp smooth while letting the
     * tach settle so RPM reads cleanly when the fan holds.
     */
    fun slew(dtMillis: Long) {
        if (forceFull) {
            appliedF = 100f // safety: jump to full at the thermal trip, don't ramp into an overheat
        } else {
            val maxStep = max(1f, slewPerSecond * dtMillis / 1000f)
            val target = targetPercent.toFloat()
            appliedF = when {
                target > appliedF -> (appliedF + maxStep).coerceAtMost(target)
                target < appliedF -> (appliedF - maxStep).coerceAtLeast(target)
                else -> appliedF
            }
        }
        val duty = FanCurve.percentToDuty(appliedF.roundToInt(), period)
        if (duty != lastWrittenDuty) {
            writeDuty(duty)
            lastWrittenDuty = duty
        }
    }

    /**
     * Reconcile against the live duty node. AYANEO's own vendor fan daemon RE-PINS the duty on an irregular
     * cycle even while PULSE is actively driving it (measured 2026-07-30: 1-112s between our write and its
     * correction, always back to its own idle value, `research/aidl-fan-spike/FINDINGS.md`) — observed live
     * the same way the Odin's did: [slew]'s write-on-change never corrected it on its own (`lastWrittenDuty`
     * still matched our intended value, so it skipped the write while the vendor's value sat on the
     * hardware). If [actualDuty] no longer matches what we last wrote, force the next [slew] to re-assert
     * our duty and return true. A null read (unreadable) or a matching value is a no-op returning false.
     */
    fun reconcileActualDuty(actualDuty: Int?): Boolean {
        if (actualDuty != null && actualDuty != lastWrittenDuty) {
            lastWrittenDuty = -1 // something changed the node underneath us → re-write our value next slew
            return true
        }
        return false
    }

    /**
     * Immediately re-write the CURRENT applied duty (no ramp advance). Used right after [reconcileActualDuty]
     * flags that something stole the node, so we re-pin our value within the fast re-check cadence instead of
     * waiting for the next (slower) [slew] — the difference between an inaudible correction and a brief rev.
     */
    fun reassertCurrentDuty() {
        val duty = FanCurve.percentToDuty(appliedF.roundToInt(), period)
        writeDuty(duty)
        lastWrittenDuty = duty
    }

    /** Live fan RPM from the tach node, or null if unreadable. */
    fun rpm(): Int? = readRpm()

    /** Forget the eased state so a new session re-slews from the floor (and forces the next write). */
    fun reset() {
        appliedF = FanCurve.MIN_PERCENT.toFloat()
        targetPercent = FanCurve.MIN_PERCENT
        forceFull = false
        lastWrittenDuty = -1
    }

    companion object {
        /** AYANEO's `pwm1` is a fixed 0-255 duty scale, unlike the Odin's device-read period register --
         *  see [readPeriodFromDevice]. */
        const val DEFAULT_PERIOD = 255

        /**
         * Set true while a one-shot external driver (the autocalibrate sweep) is writing the fan PWM duty
         * directly. The foreground service's fan paths no-op while it's set so the two don't fight over the
         * duty node. Same-process @Volatile (the service runs in the app process). The sweep MUST clear it in
         * a `finally` so a crashed/cancelled sweep can never strand the fan out of PULSE's control.
         */
        @Volatile var externalControlActive: Boolean = false

        /** No-daemon raw-`xsu` write (unlock + write every call -- AYANEO's node isn't world-writable like
         *  the Odin's was, see [FanController.FAN_DUTY_PATH]'s doc). Used as the constructor default and by
         *  [ui.TunerViewModel]'s one-shot autocalibrate sweep, where a fresh `xsu` connection per sample is
         *  fine (not a sustained high-frequency loop). */
        fun writeDutyToDevice(duty: Int) {
            val path = FanController.FAN_DUTY_PATH
            RootSupport.runRootCommand("chmod 666 $path 2>/dev/null; echo $duty > $path")
        }

        /** Daemon-routed write for the continuous reassert loop (~120ms cadence) -- a raw `xsu` connection
         *  every tick at that frequency is exactly the pattern this app's `PulseDaemon` FIFO exists to
         *  replace (`STATUS.md`'s `xsud`-crash investigation). Falls back to [writeDutyToDevice] (raw `xsu`)
         *  if the daemon isn't running -- same all-or-nothing-per-call contract as [PulseDaemon.setCap]'s
         *  other callers. `"666"` as the chmod-back mode is intentional, not a placeholder: unlike CPU/GPU
         *  nodes, this one can't actually be relocked (confirmed, `research/aidl-fan-spike/FINDINGS.md`) --
         *  [PulseDaemon.setCap]'s relock attempt just harmlessly fails every call. */
        fun writeDutyToDevice(duty: Int, pulseDaemon: PulseDaemon?) {
            if (pulseDaemon?.setCap(FanController.FAN_DUTY_PATH, "666", duty.toString()) == true) return
            writeDutyToDevice(duty)
        }

        fun readRpmFromDevice(): Int? =
            FanController.parseRpm(RootSupport.runRootCommand("cat ${FanController.FAN_SPEED_PATH} 2>/dev/null"))

        /** Daemon-routed read, same rationale as [writeDutyToDevice]'s daemon overload. [PulseDaemon.readBatch]
         *  returning `null` means the batch itself failed (daemon dead/timeout) -- fall back to raw `xsu`. A
         *  non-null batch with a `null` element means a legitimately empty read (node absent) -- pass that
         *  through as-is, not a reason to also try the raw fallback (would double up the "not available"
         *  answer, not correct it). */
        fun readRpmFromDevice(pulseDaemon: PulseDaemon?): Int? {
            val batch = pulseDaemon?.readBatch(listOf(FanController.FAN_SPEED_PATH))
            val raw = if (batch != null) batch.firstOrNull()
                else RootSupport.runRootCommand("cat ${FanController.FAN_SPEED_PATH} 2>/dev/null")
            return FanController.parseRpm(raw)
        }

        /** The live PWM duty value (0..period) — reliable, unlike the [readRpmFromDevice] tach. */
        fun readDutyFromDevice(): Int? =
            RootSupport.runRootCommand("cat ${FanController.FAN_DUTY_PATH} 2>/dev/null")?.trim()?.toIntOrNull()

        /** Daemon-routed read, same rationale/contract as [readRpmFromDevice]'s daemon overload -- this is
         *  the one called every ~120ms by the reassert loop's drift check, so it's the most important of the
         *  three to keep off raw `xsu`. */
        fun readDutyFromDevice(pulseDaemon: PulseDaemon?): Int? {
            val batch = pulseDaemon?.readBatch(listOf(FanController.FAN_DUTY_PATH))
            val raw = if (batch != null) batch.firstOrNull()
                else RootSupport.runRootCommand("cat ${FanController.FAN_DUTY_PATH} 2>/dev/null")
            return raw?.trim()?.toIntOrNull()
        }

        /** AYANEO has no period/frequency register to read (unlike the Odin's MAX31760) -- the duty scale
         *  is a fixed [DEFAULT_PERIOD]. Always `null` so [isCustomFanSupported]'s fallback fires; kept as a
         *  function (not deleted) so the call site reads the same for both devices this code was written
         *  to support. */
        fun readPeriodFromDevice(): Int? = null
    }
}
