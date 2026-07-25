# Pulse Lite Diagnostics - Project README (v8)

## Project goal

Diagnose AYA Pocket FIT (Qualcomm SG8350P / Adreno 750, Android 14) CPU/GPU/
thermal behavior across AYASpace power modes, as groundwork for an eventual
AutoTDP-style controller app. This is a read-only/reversible diagnostic phase
- no persistent system modification has been made or is planned before the
diagnostic data is considered complete and trustworthy.

## Current status: v8 (this version, not yet validated on-device)

v8 fixes the SurfaceFlinger layer-selection logic, which was silently
selecting the wrong (non-frame-producing) layer in both v6 and v7, causing
`frame_count_in_window: 0` / `computed_fps: n/a` in every sample of all four
v7 test runs despite confirmed active rendering (GPU busy% 60-88% during the
Eden run). See v8_handoff.md for the full root-cause writeup, based directly
on reading the actual v7 log contents. v8 has NOT yet been run on the device
- the next session should run it and confirm real FPS numbers appear before
considering this issue closed.

## Known technical facts (confirmed, current as of v8)

- **Foreground-app detection: CONFIRMED WORKING since v7.** Use
  `dumpsys activity activities | grep topResumedActivity=` (primary),
  falling back to `mFocusedApp=` - both confirmed present and correctly
  formatted on this device. Do NOT use `dumpsys window windows |
  grep mCurrentFocus` or `grep mResumedActivity` - both confirmed to return
  nothing on this Android 14 build.
- **SurfaceFlinger layer selection: FIX APPLIED IN v8, NOT YET VALIDATED.**
  `dumpsys SurfaceFlinger --list` lists helper/wrapper layers (e.g.
  "Background for SurfaceView[...]", "ActivityRecordInputSink ...") before or
  instead of the real frame-producing layer for both RetroArch and Eden on
  this device. A naive first-match grep will silently pick the wrong layer
  and always return frame_count_in_window=0. v8 uses a priority search:
  BLAST layer first, then plain SurfaceView (excluding wrapper prefixes),
  then last non-helper package match, then old first-match as final
  fallback. This needs on-device confirmation in the next test session.
- **FPS measurement method (the `--latency` pipeline itself, once given the
  correct layer): CONFIRMED WORKING and refresh-rate-independent.** Verified
  by testing the same RetroArch session at both 120Hz and 144Hz panel
  settings - both times measured ~60fps content framerate, matching the
  emulated console's native rate, not the panel's Hz. `dumpsys gfxinfo`
  (used in v3-v5) remains CONFIRMED UNRELIABLE for native SurfaceView
  renderers - do not reuse it.
- **GPU busy% via /sys/class/kgsl/kgsl-3d0/gpubusy: usable, with a known
  counter-reset edge case handled by an overflow guard** (result outside
  0-100% reported as `n/a (counter_reset)`) - unchanged since v6.
  `gpubusypercentage` (the sysfs node) remains confirmed broken on this
  kernel.
- **CPU cap behavior across AYASpace modes is fully mapped** (see
  HARDWARE_PROFILE_v6_en.md / HARDWARE_PROFILE_v6_updates.md): Eco uses
  governor=powersave, Balanced uses schedutil, Streaming/Gaming/Max all use
  governor=performance with different scaling_max_freq ceilings. Gaming and
  Max are identical at the CPU level; Max only differs from Gaming at the GPU
  level.
- **Thermal ceiling behavior: CPU cores have been directly observed exceeding
  the HOTTER threshold (87-90.7 C, one core hit 96.1 C during the eden_v6
  run) with no observable throttling response.** Do not assume AYASpace
  provides thermal protection - evidence so far suggests it does not
  intervene at these temperatures. The v7 Eden runs also showed a steady
  thermal_skin climb from ~48000 to ~54000 (raw millidegree units) over 90
  seconds under sustained 3D load, consistent with this earlier finding.
- **Module 3 (fan curve control) priority note:** given the thermal finding
  above, fan curve control may need to be moved up in priority versus
  originally planned, since we cannot assume AYASpace is already managing
  thermals safely on our behalf.
- **AYASpace write-conflict probe (originally v4 Section 10) has STILL never
  been executed in any version through v8.** This remains the single
  still-untested item in the full test matrix (see TEST_PROCEDURE.md, Series
  4).
- **v4's interactive mode-survey feature (sentinel-file polling loop) is
  CONFIRMED NON-FUNCTIONAL under `xsu`** and was correctly removed in v5 - do
  not attempt to revive this exact mechanism.

## File map

- `pulse_lite_diag_v8.sh` - current diagnostic script (single functional fix
  vs v7, see v8_handoff.md)
- `v8_handoff.md` - full root-cause writeup and diff vs v7 for this version,
  based on direct log analysis of the four v7 test runs
- `HARDWARE_PROFILE_v6_en.md` + `HARDWARE_PROFILE_v6_updates.md` - full
  hardware/firmware findings, CPU/GPU/thermal tables across AYASpace modes
  (unaffected by the v7/v8 fixes, still current)
- `TEST_PROCEDURE_v8.md` - ordered test plan; Series 2/3 must be RE-RUN with
  v8 to get valid FPS data (v7 runs of these series had correct app detection
  but zero frame counts due to the layer-selection bug); Series 4 (AYASpace
  conflict probe) remains not yet executed
- `v7_handoff.md` / `v6_handoff.md` / earlier handoffs - retrospective
  root-cause writeups for earlier script versions, kept for project history
  and to avoid re-introducing already-diagnosed bugs

## What a new instance/session needs to pick up this project without re-hitting solved problems

Provide all of the following together, not piecemeal, at the start of a new
session: this README, v8_handoff.md, v7_handoff.md (for the foreground-
detection fix history), HARDWARE_PROFILE_v6_en.md (+ updates),
TEST_PROCEDURE_v8.md, and pulse_lite_diag_v8.sh. That combination contains
the full current state, the full list of confirmed-fixed and
confirmed-still-open issues, and the exact script to run next - no piece of
this history lives only in conversation memory.
