package com.kei.pulse.model

import com.kei.pulse.data.FanController
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Truth table for the fan arbitration — the pure resolver behind the service's `reassertManagedFan`.
 * This decision used to be imperative service code whose release path silently assumed "the fan is already
 * at the vendor default" (a no-op unless a Custom loop was running), so turning the Fan card OFF left the
 * last managed mode stuck forever. The table pins today's verified behavior AND the release-normalize rule.
 *
 * Read-cost contract: rows where the decision must NOT need a fan_mode read pass a throwing [readLiveMode] —
 * the fan reconcile shares the PServer lock with AutoTDP re-asserts, so needless per-tick reads are a
 * documented regression class.
 */
class FanArbiterTest {

    private val mustNotRead: () -> Int? = { error("this decision must not read fan_mode") }

    private fun decide(
        autoTdpActive: Boolean = false,
        boundFanMode: Int? = null,
        managedFanMode: Int? = null,
        customFanSupported: Boolean = true,
        releaseLatched: Boolean = false,
        releaseMode: Int = FanController.SMART,
        readLiveMode: () -> Int?,
    ) = FanArbiter.decide(
        autoTdpActive, boundFanMode, managedFanMode, customFanSupported, releaseLatched, releaseMode, readLiveMode,
    )

    // ── AutoTDP owns the fan ────────────────────────────────────────────────────────────────────────

    @Test
    fun `autotdp with user custom fan cascades the custom loop, no mode read`() {
        assertEquals(
            FanAction.RunCustomLoop,
            decide(autoTdpActive = true, managedFanMode = FanController.CUSTOM, readLiveMode = mustNotRead),
        )
    }

    @Test
    fun `autotdp with custom unsupported stands down, no mode read`() {
        assertEquals(
            FanAction.None,
            decide(
                autoTdpActive = true, managedFanMode = FanController.CUSTOM,
                customFanSupported = false, readLiveMode = mustNotRead,
            ),
        )
    }

    @Test
    fun `autotdp with non-custom fan stands down (vendor smart was set at session start)`() {
        assertEquals(
            FanAction.None,
            decide(autoTdpActive = true, managedFanMode = FanController.SPORT, readLiveMode = mustNotRead),
        )
        assertEquals(
            FanAction.None,
            decide(autoTdpActive = true, managedFanMode = null, readLiveMode = mustNotRead),
        )
    }

    // ── Managed vendor modes: write only on drift ───────────────────────────────────────────────────

    @Test
    fun `held managed mode does nothing`() {
        assertEquals(
            FanAction.None,
            decide(managedFanMode = FanController.SPORT, readLiveMode = { FanController.SPORT }),
        )
    }

    @Test
    fun `drifted managed mode is rewritten (qs tile fight)`() {
        assertEquals(
            FanAction.SetVendorMode(FanController.SPORT),
            decide(managedFanMode = FanController.SPORT, readLiveMode = { FanController.SMART }),
        )
    }

    /**
     * The vendor sitting in a mode PULSE has no equivalent for — on AYANEO, `FAN_MODE_OFF` set from
     * native AyaSettings — must be corrected like any other drift. Regression cover for a real
     * on-device bug (2026-07-31): that state used to reach here as `null` (indistinguishable from "mode
     * unreadable"), which made this whole decision bail out and left the fan switched OFF indefinitely
     * while PULSE believed it was managing Smart. `FanController.arbitrationModeFor` now maps it to
     * [FanController.VENDOR_UNMANAGED] so it lands in the drift branch below instead.
     */
    @Test
    fun `vendor state PULSE does not manage (eg fan OFF) is corrected, not ignored`() {
        assertEquals(
            FanAction.SetVendorMode(FanController.SMART),
            decide(
                managedFanMode = FanController.SMART,
                readLiveMode = { FanController.VENDOR_UNMANAGED },
            ),
        )
    }

    @Test
    fun `bound per-app fan wins over the global managed mode`() {
        assertEquals(
            FanAction.SetVendorMode(FanController.SPORT),
            decide(
                boundFanMode = FanController.SPORT, managedFanMode = FanController.SILENT,
                readLiveMode = { FanController.SILENT },
            ),
        )
    }

    @Test
    fun `unreadable mode skips the tick (same as today)`() {
        assertEquals(
            FanAction.None,
            decide(managedFanMode = FanController.SPORT, readLiveMode = { null }),
        )
    }

    // ── Custom fan ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `custom supported runs the loop, no mode read`() {
        assertEquals(
            FanAction.RunCustomLoop,
            decide(managedFanMode = FanController.CUSTOM, readLiveMode = mustNotRead),
        )
    }

    @Test
    fun `custom unsupported falls back to smart (never sit on a phantom mode)`() {
        assertEquals(
            FanAction.SetVendorMode(FanController.SMART),
            decide(
                managedFanMode = FanController.CUSTOM, customFanSupported = false,
                readLiveMode = { FanController.SPORT },
            ),
        )
        assertEquals(
            FanAction.None,
            decide(
                managedFanMode = FanController.CUSTOM, customFanSupported = false,
                readLiveMode = { FanController.SMART },
            ),
        )
    }

    // ── Release (managed → unmanaged edge): normalize ONCE, never fight the user afterwards ─────────

    @Test
    fun `release edge with the fan stuck on a managed mode normalizes to the release mode`() {
        // THE hole this resolver closes: Fan card turned OFF after managing Sport used to leave Sport forever
        // (the old restoreVendor was a no-op unless a Custom loop had been running).
        assertEquals(
            FanAction.ReleaseToVendor(FanController.SMART),
            decide(releaseLatched = false, readLiveMode = { FanController.SPORT }),
        )
    }

    @Test
    fun `release edge already at the release mode does nothing`() {
        assertEquals(
            FanAction.None,
            decide(releaseLatched = false, readLiveMode = { FanController.SMART }),
        )
    }

    @Test
    fun `release edge with manual passthrough live normalizes (custom loop was driving)`() {
        assertEquals(
            FanAction.ReleaseToVendor(FanController.SMART),
            decide(releaseLatched = false, readLiveMode = { FanController.CUSTOM }),
        )
    }

    @Test
    fun `latched release does nothing and must not read (a deliberate tile choice is respected)`() {
        assertEquals(
            FanAction.None,
            decide(releaseLatched = true, readLiveMode = mustNotRead),
        )
    }

    @Test
    fun `release edge with unreadable mode does nothing`() {
        assertEquals(FanAction.None, decide(releaseLatched = false, readLiveMode = { null }))
    }
}
