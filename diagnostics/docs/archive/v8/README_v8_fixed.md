# Pulse Lite Diagnostics - Project README (v8, VALIDATED)

## Project goal

Diagnose AYA Pocket FIT (Qualcomm SG8350P / Adreno 750, Android 14) CPU/GPU/
thermal behavior across AYASpace power modes, as groundwork for an eventual
AutoTDP-style controller app. This is a read-only/reversible diagnostic phase
- no persistent system modification has been made or is planned before the
diagnostic data is considered complete and trustworthy.

## Current status: v8, CONFIRMED WORKING end-to-end (this session)

v8's SurfaceFlinger layer-selection fix has now been validated with two
fresh on-device test runs (RetroArch + Eden/yuzu), both showing correct
layer matching, correct non-zero frame counts, and correct FPS numbers in
the large majority of samples. See v8_handoff.md for full validation
results. This closes the bug chain that ran from v4 (broken app detection)
through v6/v7 (broken layer selection) to v8 (fix + confirmation). The
script file itself is unchanged from its original v8 release - only the
handoff doc was updated with validation results.

## Known technical facts (confirmed, current as of v8 validation)

- **Foreground-app detection: CONFIRMED WORKING since v7, re-confirmed in
  v8 runs.** Use `dumpsys activity activities | grep topResumedActivity=`
  (primary), falling back to `mFocusedApp=`. Do NOT use
  `dumpsys window windows | grep mCurrentFocus` or `grep mResumedActivity`
  - both confirmed to return nothing on this Android 14 build.
- **SurfaceFlinger layer selection: CONFIRMED WORKING in v8 for both tested
  apps.** `dumpsys SurfaceFlinger --list` lists helper/wrapper layers (e.g.
  "Background for SurfaceView[...]", "ActivityRecordInputSink ...") before
  or instead of the real frame-producing layer for both RetroArch and Eden
  on this device. v8's priority search (BLAST layer first, then plain
  SurfaceView excluding wrapper prefixes, then last non-helper package
  match, then old first-match as final fallback) correctly selected:
  - `SurfaceView[com.miHoYo.Yuanshen/...](BLAST)#515` for Eden (tier 1),
    30/30 samples correct.
  - `com.retroarch.aarch64/...RetroActivityFuture#457` for RetroArch
    (tier 3), 30/30 samples correct.
- **FPS measurement method (the `--latency` pipeline, given the correct
  layer): CONFIRMED WORKING, with one known minor edge case.**
  `frame_count_in_window` was correct (126-127 per 3s window) in 100% of
  samples for both apps. `computed_fps` was correct in 100% of RetroArch
  samples (59.8-60.7fps) and ~83% of Eden samples (58.8-60.0fps); the
  remaining ~17% of Eden samples hit a "zero span" edge case (first/last
  timestamp in the window identical) and safely printed "n/a" instead of a
  wrong number - see v8_handoff.md for detail, flagged as a v8.1 candidate,
  not blocking. `dumpsys gfxinfo` (used in v3-v5) remains CONFIRMED
  UNRELIABLE for native SurfaceView renderers - do not reuse it.
- **GPU busy% via /sys/class/kgsl/kgsl-3d0/gpubusy: usable, with a known
  counter-reset edge case handled by an overflow guard** - unchanged since
  v6. Re-confirmed sane in both v8 validation runs (RetroArch ~9%, Eden
  65-86%, both matching expected load levels). `gpubusypercentage` (the
  sysfs node) remains confirmed broken on this kernel.
- **CPU cap behavior across AYASpace modes is fully mapped** (see
  HARDWARE_PROFILE_v6_en.md / HARDWARE_PROFILE_v6_updates.md): Eco uses
  governor=powersave, Balanced uses schedutil, Streaming/Gaming/Max all use
  governor=performance with different scaling_max_freq ceilings. Both v8
  validation runs showed governor=performance with CPU frequencies pinned
  at their ceiling throughout, consistent with Gaming/Max mode.
- **Thermal ceiling behavior: CPU cores have been directly observed
  exceeding the HOTTER threshold (87-96.1 C in earlier v6 runs; this
  session's Eden v8 baseline snapshot showed cpu-1-2-1 at 93800 / ~93.8 C
  and cpu-1-0-1 at 86100 / ~86.1 C before sampling even began) with no
  observable throttling response.** Do not assume AYASpace provides thermal
  protection - evidence so far continues to suggest it does not intervene
  at these temperatures.
- **Module 3 (fan curve control) priority note:** given the thermal finding
  above, fan curve control may need to be moved up in priority versus
  originally planned, since we cannot assume AYASpace is already managing
  thermals safely on our behalf.
- **AYASpace write-conflict probe (originally v4 Section 10) has STILL never
  been executed in any version through v8.** This remains the single
  still-untested item in the full test matrix (see TEST_PROCEDURE_v8.md,
  Series 4).
- **v4's interactive mode-survey feature (sentinel-file polling loop) is
  CONFIRMED NON-FUNCTIONAL under `xsu`** and was correctly removed in v5 -
  do not attempt to revive this exact mechanism.

## File map

- `pulse_lite_diag_v8.sh` - current diagnostic script, CONFIRMED WORKING,
  unchanged since original release (only its handoff doc was updated with
  validation results)
- `v8_handoff.md` - full root-cause writeup, diff vs v7, AND on-device
  validation results for both RetroArch and Eden/yuzu
- `HARDWARE_PROFILE_v6_en.md` + `HARDWARE_PROFILE_v6_updates.md` - full
  hardware/firmware findings, CPU/GPU/thermal tables across AYASpace modes
  (unaffected by the v7/v8 fixes, still current)
- `TEST_PROCEDURE_v8.md` - ordered test plan; Series 2/3 are now considered
  VALIDATED (re-run confirmed both apps produce correct data) and can move
  to real data-collection mode; Series 4 (AYASpace conflict probe) remains
  not yet executed
- `v7_handoff.md` / `v6_handoff.md` / earlier handoffs - retrospective
  root-cause writeups for earlier script versions, kept for project history
  and to avoid re-introducing already-diagnosed bugs

## Open items going into the next session

1. Series 4 (AYASpace write-conflict probe) - never executed, needs to be
   re-implemented as a script branch (was dropped in the v4->v5 transition
   along with the broken mode-survey feature, but is conceptually distinct
   and still valid).
2. The "zero span" FPS edge case seen in ~17% of Eden samples - narrow,
   fails safe, not blocking, but worth a closer look in a v8.1 pass if a
   pattern with GPU frequency transitions is confirmed across more runs.
3. Now that the measurement pipeline is validated, actual comparative data
   collection across AYASpace modes (Eco/Balanced/Gaming/Max/Streaming) for
   both RetroArch and Eden can proceed for real analysis - this was blocked
   until this session's validation.

## What a new instance/session needs to pick up this project without re-hitting solved problems

Provide all of the following together, not piecemeal, at the start of a new
session: this README, v8_handoff.md, v7_handoff.md (for the foreground-
detection fix history), HARDWARE_PROFILE_v6_en.md (+ updates),
TEST_PROCEDURE_v8.md, and pulse_lite_diag_v8.sh. That combination contains
the full current state, the full list of confirmed-fixed and
confirmed-still-open issues, and the exact script to run next - no piece of
this history lives only in conversation memory.
