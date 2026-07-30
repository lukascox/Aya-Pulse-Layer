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
