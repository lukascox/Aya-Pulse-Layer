package com.kei.pulse.aidl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [AyaAidlClient.parseFanModeFromCallback] is this device's ONLY fan-mode readback (there is no
 * `com_get_*` query command, and registering delivers no state dump), so it has to survive whatever
 * gamewindow actually sends. The payload below is the real captured one, trimmed to two profiles but
 * structurally verbatim -- `research/aidl-fan-spike/results/run1/aidl_fan_spike_result.txt:16`.
 *
 * The two things most worth pinning: it must select via `currentMode` rather than taking the first
 * profile it finds (profile "0" and "1" deliberately carry DIFFERENT fan modes here, so an
 * index-ignoring parser passes the naive case and fails this one), and it must never throw -- it runs
 * on a Binder thread where an exception would propagate into the vendor's own transact.
 */
class AyaAidlClientParseFanModeTest {

    private fun payload(currentMode: Int) =
        "msg_type_performance:com_set_performance_mode:" +
            """{"currentMode":$currentMode,"modeConfigurations":{""" +
            """"0":{"cpuSchedulerMode":"POWER_SAVING","fanMode":"FAN_MODE_OFF",""" +
            """"gpuFrequency":{"isFixed":false,"maxFrequency":310000000},"lastFanMode":"FAN_MODE_MUTE"},""" +
            """"1":{"cpuSchedulerMode":"BALANCED","fanMode":"FAN_MODE_TURBO",""" +
            """"gpuFrequency":{"isFixed":false,"maxFrequency":903000000},"lastFanMode":"FAN_MODE_CUSTOM"}}}"""

    @Test
    fun readsTheActiveProfilesFanMode() {
        assertEquals("FAN_MODE_OFF", AyaAidlClient.parseFanModeFromCallback(payload(currentMode = 0)))
        assertEquals("FAN_MODE_TURBO", AyaAidlClient.parseFanModeFromCallback(payload(currentMode = 1)))
    }

    /** `lastFanMode` sits right next to `fanMode` in every profile and is NOT the live value. */
    @Test
    fun doesNotConfuseLastFanModeForTheLiveOne() {
        assertEquals("FAN_MODE_OFF", AyaAidlClient.parseFanModeFromCallback(payload(currentMode = 0)))
    }

    /**
     * `currentMode` arriving quoted (`"3"` instead of `3`) still resolves -- kotlinx's `int` accessor
     * parses the primitive's content either way. Pinned deliberately: it means a vendor update that
     * starts quoting the number degrades to "still works" rather than to a silently blind readback.
     */
    @Test
    fun toleratesAQuotedCurrentModeIndex() {
        assertEquals(
            "FAN_MODE_MUTE",
            AyaAidlClient.parseFanModeFromCallback(
                "msg_type_performance:com_set_performance_mode:" +
                    """{"currentMode":"3","modeConfigurations":{"3":{"fanMode":"FAN_MODE_MUTE"}}}""",
            ),
        )
    }

    @Test
    fun ignoresTheRegistrationHandshakeAndOtherMessages() {
        assertNull(AyaAidlClient.parseFanModeFromCallback("msg_type_register:175316599"))
        assertNull(AyaAidlClient.parseFanModeFromCallback(""))
        assertNull(AyaAidlClient.parseFanModeFromCallback("msg_type_performance:com_set_performance_cpu:0_787200"))
    }

    @Test
    fun malformedOrUnexpectedPayloadsReturnNullInsteadOfThrowing() {
        val prefix = "msg_type_performance:com_set_performance_mode:"
        assertNull(AyaAidlClient.parseFanModeFromCallback("$prefix{not json at all"))
        assertNull(AyaAidlClient.parseFanModeFromCallback("$prefix{}"))
        // currentMode points at a profile that isn't in the dump
        assertNull(AyaAidlClient.parseFanModeFromCallback(payload(currentMode = 9)))
        // profile present but carries no fanMode key
        assertNull(
            AyaAidlClient.parseFanModeFromCallback(
                """${prefix}{"currentMode":0,"modeConfigurations":{"0":{"cpuSchedulerMode":"BALANCED"}}}""",
            ),
        )
    }
}
