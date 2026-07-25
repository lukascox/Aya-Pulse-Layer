# PROJECT HANDOFF - Pulse Lite / AYASpace AutoTDP prep
Device: AYANEO Pocket FIT (SG8350P, Snapdragon-class, Adreno 750-branded GPU, Android 14/SDK34)
Goal: build a root-capable diagnostic + eventually an AutoTDP-style controller that manages
CPU governor/freq caps and GPU freq based on real workload signals (FPS, GPU busy%, thermals),
similar in spirit to Steam Deck/ROG Ally power profile tools, but reverse-engineering AYASpace's
own profile behavior first.

## Why this exists
AYASpace ships 5 power profiles (Eco/Balanced/Gaming/Streaming/Max) that silently set CPU
governor + freq caps and (presumably) GPU limits under the hood, with no visibility into what
they actually do. Before building any custom controller (AutoTDP-like), we need to:
1. Reverse-engineer exactly what each AYASpace mode does at the kernel level (governor, freq
   caps, thermal behavior).
2. Build a reliable way to measure real-time performance signals (FPS, GPU busy%, CPU freq,
   temps) that works across ALL app types on this device - system UI, Android-native apps,
   AND emulators (RetroArch, Eden/Yuzu-class, Dolphin) which render via native SurfaceView and
   are invisible to standard `dumpsys gfxinfo`.
3. Only once both of the above are solid, start writing an actual controller that dynamically
   adjusts CPU/GPU limits based on measured load (the "AutoTDP" script).

## Where we are (as of v6)
- v1-v3.7: iterative diagnostic script builds, mostly abandoned/superseded, kept in thread for
  history only. No separate handoffs were written per version early on (acknowledged gap).
- v5: first script that produced usable 90s sampling data across 4 AYASpace modes (eco/balanced/
  gaming/max) + 2 workload runs (retroarch_balanced, heavy_gaming) + 1 extra (streaming).
  Confirmed: Eco/Balanced/Gaming/Max differ clearly in CPU governor + freq caps (powersave vs
  schedutil vs performance, min vs max frequencies). Streaming = performance governor but with
  mid-range freq caps (distinct profile, not just Gaming-lite).
  BUG FOUND: gpu_busy_pct computation could go negative/garbage due to raw cycle counter
  wraparound between samples (unhandled).
  BUG FOUND: dumpsys gfxinfo (used for frame stats) returns 0 frames for RetroArch (renders via
  native SurfaceView, invisible to gfxinfo) and returned FROZEN/cached data for the heavy_gaming
  run (identical stats across 5 samples spanning 15s) - confirmed by user's manual FPS
  observation (~60fps or less in Eden running Super Mario Odyssey, not the 117 frames gfxinfo
  reported).
  CORRECTION: heavy_gaming workload was Eden (Switch emulator) running Super Mario Odyssey
  (legally owned cartridge), NOT Yuzu/Genshin as earlier assumed mid-thread.
- v6 (current): replaces gfxinfo-based FPS section with a dynamic pipeline: detect foreground
  app via window focus -> match SurfaceFlinger layer -> compute FPS from --latency timestamps.
  This works uniformly across UI apps, RetroArch, and Eden without hardcoding package names.
  Also fixes the gpu_busy_pct overflow bug and adds per-sample CPU governor+freq / GPU freq+busy
  capture (previously only captured once at script start, not per-sample).

## Known remaining corner cases / open questions
- Static/idle screens (e.g. RetroHrai launcher sitting still) produce very few SurfaceFlinger
  present events per sampling window -> FPS calc is statistically unreliable at low frame counts.
  v6 flags this as "low_sample_count" rather than reporting a misleadingly low FPS number.
- SurfaceView layer names are not perfectly stable across app launches (suffix numbers change),
  matching is done via fuzzy grep on package name, not exact layer string - could occasionally
  mismatch if multiple layers share substrings.
- GPU freq/busy still read from raw kgsl sysfs (no vendor-provided clean busy% API on this
  kernel) - the overflow guard in v6 treats any resulting % outside 0-100 as counter reset,
  but root cause of the wraparound itself is not fixed (not fixable from userspace).
- No AutoTDP control logic exists yet - v6 is still pure diagnostics/telemetry. Next major step
  after validating v6 data quality is designing the actual control loop (what triggers a freq
  cap/governor change, hysteresis, thermal ceiling behavior, etc.).

## Testing protocol in use
Six log runs pulled per iteration cycle via adb push/shell xsu/pull:
1. eco, balanced, gaming, max (AYASpace mode comparison, idle foreground app)
2. retroarch_balanced (real RetroArch gameplay, Balanced mode)
3. heavy_gaming (real Eden/Switch emulator gameplay, Gaming mode)
Occasionally a streaming run is added as a 7th distinct AYASpace profile check.

## Handoff hygiene note
Per-version handoffs were not written for v1-v5 (iteration moved faster than documentation).
Going forward (starting v6), every version should get its own short handoff file alongside the
script artifact, even if brief, to preserve context across sessions/thread resets.
