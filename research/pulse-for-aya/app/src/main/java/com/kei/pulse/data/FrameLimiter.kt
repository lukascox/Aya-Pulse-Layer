package com.kei.pulse.data

import com.kei.pulse.root.RootSupport

/**
 * A real frame‑rate cap for AutoTDP targets via **Android Game Mode interventions** (`cmd game`).
 *
 * Setting a custom game mode (mode 4) with an `--fps` override caps the app's frame rate **without
 * touching the panel refresh** — so AutoTDP can hold the panel at 120 Hz (latency) yet limit a light
 * game to e.g. 60 fps, which clock‑trimming alone can't do. Confirmed working on the Odin 3 (Android 15):
 * `cmd game set --mode 4 --fps 60 <pkg>` → "fps-override: 60". On some Android 13 firmwares the
 * intervention isn't honored — calls are harmless there (best‑effort; AutoTDP's clock loop still runs).
 *
 * Per‑app and persistent until cleared, so AutoTDP sets it on engage and clears it on release. All shell
 * goes through [RootSupport] (PServer root).
 */
object FrameLimiter {

    /**
     * Cap [pkg] to [fps] (a no‑op cap when [fps] ≤ 0 — clears instead). Returns the `cmd game` output
     * (e.g. "fps-override: 40") so callers can confirm the firmware honored the value vs floored it.
     */
    fun setCap(pkg: String, fps: Int): String? {
        if (pkg.isBlank()) return null
        if (fps <= 0) {
            clear(pkg)
            return null
        }
        return RootSupport.runRootCommand("cmd game set --mode 4 --fps $fps ${pkg.shellSafe()}")
    }

    /** Remove the cap (back to standard mode, dropping the fps override at runtime). */
    fun clear(pkg: String) {
        if (pkg.isBlank()) return
        RootSupport.runRootCommand("cmd game set --mode 1 ${pkg.shellSafe()}")
    }

    private fun String.shellSafe(): String = filter { it.isLetterOrDigit() || it == '.' || it == '_' }
}
