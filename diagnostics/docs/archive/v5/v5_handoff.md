# pulse_lite_diag_v5.sh - Handoff (retrospective, English)

Source: actual script content (pulse_lite_diag_v5.sh, verified) + 7 full diagnostic
logs collected with this exact script (eco, balanced, gaming, max, streaming,
retroarch_balanced, heavy_gaming).

## What v5 does

Single, non-interactive diagnostic run. One suffix argument selects the output log
name; every invocation runs the full sequence (sections 0-9) unconditionally, then
exits. No branching logic based on suffix (unlike v4 — see below).

Section breakdown:
- 0. Identity/run info (uid, date, reminder to note active AYASpace mode manually)
- 1. SoC fingerprint (getprop dump, filtered for soc/hardware/board/chip/gpu)
- 2. CPU topology: all 4 cpufreq policies, full available-frequency tables, governor,
  min/max/cur freq — one-time snapshot at script start
- 3. GPU (kgsl): pwrlevel range, gpuclk, raw gpubusy, gpubusypercentage (explicitly
  commented as "known broken on this kernel, kept for reference"), devfreq governor
  and available frequencies — one-time snapshot
- 4. Full thermal zone enumeration (type + temp for every /sys/class/thermal/thermal_zone*)
- 5. Installed emulator/gaming package list (grep against a fixed keyword list)
- 6. SurfaceFlinger --list dump, explicitly labeled "fallback FPS method reference" —
  captured but not parsed/used automatically in this version
- 7. 90-second sampling loop (30 samples x 3s sleep): per sample, resolves foreground
  package via `dumpsys activity activities` (topResumedActivity, falling back to
  mResumedActivity), runs `dumpsys gfxinfo <pkg> framestats | head -40`, computes GPU
  busy% as a raw delta between consecutive gpubusy reads (busy_cycles/total_cycles,
  no bounds checking), and logs CPU scaling_cur_freq for all 4 policies
- 8. Reversible CPU write test (policy0 scaling_max_freq: chmod 644, write test value,
  read back, restore original, chmod 444)
- 9. Reversible GPU write test (kgsl max_pwrlevel: same pattern)

## Confirmed issues found by analyzing v5 output (root causes, verified against code)

**FPS source (section 7) is unreliable for emulators.** The script calls
`dumpsys gfxinfo "$PKG" framestats`, which is blind to apps that render via a native
SurfaceView (RetroArch, Eden/Switch emulator, Dolphin, etc.) rather than the standard
Android View/Skia hierarchy. Confirmed in logs:
- retroarch_balanced.log: "Total frames rendered: 0" in every sample despite active
  RetroArch gameplay.
- heavy_gaming.log: identical, frozen frame stats (117 frames, same histogram) across
  5 samples spanning ~15 seconds of real Eden/Super Mario Odyssey gameplay — the API
  was returning a stale cached snapshot, not live data.

**GPU busy% computation (section 7) has no bounds checking.** The delta calculation
`BUSY_PCT=$((DBUSY * 100 / DTOTAL))` can go outside 0-100 (observed values as extreme
as -2718% in heavy_gaming.log) when the underlying kgsl cycle counters wrap/reset
between reads. The script has no guard against this — it prints whatever the raw
arithmetic produces, including negative/nonsensical values.

**CPU/GPU state is only captured once per run (sections 2-3), not per sample.**
Section 7's per-sample logging only captures `scaling_cur_freq` for the 4 CPU
policies — governor, GPU freq, and GPU busy% baseline snapshot come only from
sections 2-3 at script start, so correlating CPU/GPU behavior with FPS/load changes
across the 90s window is not fully possible from a single run's data.

**get_resumed_pkg() has a fragile sed-based parser.** It depends on a specific
`ActivityRecord{<hash> <user> <pkg>/<activity>...}` string format from
`dumpsys activity activities`. Works for standard Android UI apps and was confirmed
functional against RetroArch/Eden's outer activity, but does not attempt to
distinguish the actual rendering surface (SurfaceView) from the enclosing Activity —
this is the root design gap that section 6 (SurfaceFlinger --list) was captured for
but never wired into an actual FPS calculation in this version.

## What v5 got right (confirmed via 7-log comparison)

- CPU governor + full freq table capture (section 2) correctly distinguished all 5
  AYASpace modes (Eco=powersave, Balanced=schedutil, Streaming/Gaming/Max=performance
  with different scaling_max_freq caps) — this is solid, reusable data.
- Reversible write tests (sections 8-9) correctly confirmed sysfs write access is
  available under `xsu` and that chmod 644/444 toggling works as a safety pattern.
- Full thermal zone dump (section 4) captured real temperature spikes during
  heavy_gaming (multiple CPU cores 87-90.7 C) — useful raw data despite the FPS
  measurement issue in the same run.

## Relationship to v6

v6 keeps sections 0-6 and 8-9 essentially unchanged in intent, and fully replaces
section 7's FPS method (gfxinfo -> SurfaceFlinger focus/layer/latency pipeline) and
adds bounds-checking to the GPU busy% calculation, plus per-sample CPU governor/freq
and GPU freq/busy capture (not just once at script start).
