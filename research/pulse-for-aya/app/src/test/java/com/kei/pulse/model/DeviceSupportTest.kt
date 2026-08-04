package com.kei.pulse.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Truth table for [DeviceSupport] — the gate that stops a published APK writing to a SoC family this was
 * never run on. The positive case is ground truth from the real unit (`ro.soc.manufacturer = QTI`,
 * `diagnostics/docs/archive/v6/pulse_lite_diag_eden_v6.log`); the negatives are AYANEO's own MediaTek
 * handhelds, which are the actual risk — same brand, same community, entirely different silicon.
 */
class DeviceSupportTest {

    @Test fun theRealUnitIsSupported() {
        // Exactly what the Pocket FIT reports.
        assertTrue(DeviceSupport.isSupportedSoc("QTI", "qcom"))
    }

    @Test fun theVendorStringIsProseSoMatchingIsFuzzy() {
        // Real-world spellings. An equality test here would be a false-negative generator.
        assertTrue(DeviceSupport.isSupportedSoc("Qualcomm", null))
        assertTrue(DeviceSupport.isSupportedSoc("QUALCOMM", null))
        assertTrue(DeviceSupport.isSupportedSoc("Qualcomm Technologies, Inc.", null))
        assertTrue(DeviceSupport.isSupportedSoc("qti", null))
    }

    @Test fun hardwareAloneIsEnoughWhenTheSocFieldIsEmpty() {
        // Some OEMs never set ro.soc.manufacturer but still report qcom in ro.hardware.
        assertTrue(DeviceSupport.isSupportedSoc(null, "qcom"))
        assertTrue(DeviceSupport.isSupportedSoc("", "qcom"))
        assertTrue(DeviceSupport.isSupportedSoc("unknown", "qcom"))
    }

    @Test fun mediatekIsBlocked() {
        // The case this gate exists for: AYANEO's Helio handhelds. Generic cpufreq nodes are present
        // there, so without this PULSE would cap clocks and park cores on unvalidated silicon.
        assertFalse(DeviceSupport.isSupportedSoc("Mediatek", "mt6789"))
        assertFalse(DeviceSupport.isSupportedSoc("MTK", null))
    }

    @Test fun otherVendorsAreBlocked() {
        assertFalse(DeviceSupport.isSupportedSoc("Samsung", "exynos"))
        assertFalse(DeviceSupport.isSupportedSoc("Google", "gs101"))
        assertFalse(DeviceSupport.isSupportedSoc("Unisoc", "ums512"))
    }

    @Test fun unknownFailsClosed() {
        // "I cannot tell what I am standing on" is nearer to risk than to safety. Android's own default
        // for an unset property is the literal string "unknown".
        assertFalse(DeviceSupport.isSupportedSoc(null, null))
        assertFalse(DeviceSupport.isSupportedSoc("", ""))
        assertFalse(DeviceSupport.isSupportedSoc("unknown", "unknown"))
        assertFalse(DeviceSupport.isSupportedSoc("   ", "   "))
    }

    @Test fun describeNamesTheDeviceAndTheChipVendor() {
        assertEquals(
            "AYANEO Pocket FIT (SoC vendor: QTI)",
            DeviceSupport.describe("AYANEO", "Pocket FIT", "QTI"),
        )
        assertEquals(
            "unknown device (SoC vendor: unknown)",
            DeviceSupport.describe(null, null, null),
        )
        assertEquals(
            "Pocket Micro (SoC vendor: Mediatek)",
            DeviceSupport.describe(null, "Pocket Micro", "Mediatek"),
        )
    }
}
