package com.kei.pulse.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The stock fan controller caches the active mode and won't re-apply the same one, so [FanController.setMode]
 * bounces through a different real mode first to force a reload. Reaching SMART must bounce through SILENT
 * (low fan), NOT SPORT (high fan) — otherwise handing the fan back to Smart (e.g. AutoTDP restoring it on
 * game-exit) revs the fan for a moment. This locks that down.
 */
class FanControllerTest {

    @Test
    fun reachingSmartBouncesThroughSilentNotSport() {
        assertEquals(FanController.SILENT, FanController.bounceModeFor(FanController.SMART))
        assertNotEquals(
            "reaching Smart must never route through the high Sport fan (that's the rev)",
            FanController.SPORT,
            FanController.bounceModeFor(FanController.SMART),
        )
    }

    @Test
    fun reachingSportBouncesThroughSmart() {
        assertEquals(FanController.SMART, FanController.bounceModeFor(FanController.SPORT))
    }

    @Test
    fun reachingSilentBouncesThroughSmart() {
        assertEquals(FanController.SMART, FanController.bounceModeFor(FanController.SILENT))
    }

    @Test
    fun bounceModeAlwaysDiffersFromTarget() {
        for (target in listOf(FanController.SILENT, FanController.SMART, FanController.SPORT)) {
            assertNotEquals(target, FanController.bounceModeFor(target))
        }
    }
}

/**
 * [FanController.aidlModeFor] maps Silent/Smart/Sport to the AIDL `FAN_MODE_*` strings
 * `com_set_performance_fan` expects (`research/aidl-fan-spike/FINDINGS.md` Step 1). Custom and any
 * unrecognized mode must return `null` -- Custom never goes through AIDL, [FanController.setMode]
 * relies on that to skip the AIDL call entirely for it.
 */
class FanControllerAidlModeForTest {

    @Test
    fun mapsTheThreeStockModes() {
        assertEquals("FAN_MODE_MUTE", FanController.aidlModeFor(FanController.SILENT))
        assertEquals("FAN_MODE_BALANCE", FanController.aidlModeFor(FanController.SMART))
        assertEquals("FAN_MODE_TURBO", FanController.aidlModeFor(FanController.SPORT))
    }

    @Test
    fun customAndUnknownModesReturnNull() {
        assertNull(FanController.aidlModeFor(FanController.CUSTOM))
        assertNull(FanController.aidlModeFor(999))
    }

    @Test
    fun modeForAidlRoundTripsEveryModeWeCanSend() {
        for (mode in listOf(FanController.SILENT, FanController.SMART, FanController.SPORT)) {
            assertEquals(mode, FanController.modeForAidl(FanController.aidlModeFor(mode)))
        }
    }

    /** OFF/CUSTOM are real vendor states we can receive but never send -- both mean "not a mode PULSE
     *  manages", which the arbiter must see as drift rather than as one of our own modes. */
    @Test
    fun vendorOnlyStatesAndGarbageMapToNull() {
        assertNull(FanController.modeForAidl("FAN_MODE_OFF"))
        assertNull(FanController.modeForAidl("FAN_MODE_CUSTOM"))
        assertNull(FanController.modeForAidl(null))
        assertNull(FanController.modeForAidl(""))
    }
}

/**
 * AYANEO's `fan_rpm_state` node reads back `"Current RPM 2666"`, not a bare number like the Odin's tach --
 * confirmed live 2026-07-30 (`research/aidl-fan-spike/results/run5/`). Locks down the parse.
 */
class FanControllerParseRpmTest {

    @Test
    fun parsesTheRealDeviceFormat() {
        assertEquals(2666, FanController.parseRpm("Current RPM 2666"))
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertEquals(4780, FanController.parseRpm("  Current RPM 4780\n"))
    }

    @Test
    fun nullEmptyAndUnparseableInputAllReturnNull() {
        assertNull(FanController.parseRpm(null))
        assertNull(FanController.parseRpm(""))
        assertNull(FanController.parseRpm("Current RPM"))
    }
}
