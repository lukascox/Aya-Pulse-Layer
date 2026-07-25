# Pulse Lite Diagnostics - Project README (v7)

## Project goal

Diagnose AYA Pocket FIT (Qualcomm SG8350P / Adreno 750, Android 14) CPU/GPU/
thermal behavior across AYASpace power modes, as groundwork for an eventual
AutoTDP-style controller app. This is a read-only/reversible diagnostic phase
- no persistent system modification has been made or is planned before the
diagnostic data is considered complete and trustworthy.

## Current status: v7 (this version)

v7 fixes the foreground-app / FPS measurement pipeline, which was silently
broken from v4 through v6. The bug was isolated and confirmed by manually
running individual adb commands against the live device (not by guessing from
documentation) - see v7_handoff.md for the full root-cause writeup.

## Known technical facts (confirmed, current as of v7)

- **FPS measurement method: CONFIRMED WORKING as of v7.** Pipeline is:
  detect foreground pkg via `dumpsys activity activities` (topResumedActivity=
  primary, mFocusedApp= fallback) -> find matching layer via
  `dumpsys SurfaceFlinger --list` -> compute real content framerate from
  `dumpsys SurfaceFlinger --latency "<layer>"` actualPresentTime deltas,
  filtering the INT64_MAX sentinel row. Confirmed refresh-rate-independent by
  testing the same RetroArch session at both 120Hz and 144Hz panel settings -
  both times measured ~60fps content framerate, matching the emulated
  console's native rate. `dumpsys gfxinfo` (used in v3-v5) is CONFIRMED
  UNRELIABLE for native SurfaceView renderers (RetroArch, Eden/yuzu, Dolphin,
  Azahar) - do not reuse it.
- **GPU busy% via /sys/class/kgsl/kgsl-3d0/gpubusy: usable but has a known
  counter-reset edge case.** v6 added an overflow guard (result outside 0-100%
  is now reported as `n/a (counter_reset)` instead of a garbage negative
  number) - this fix is unchanged and carried into v7. `gpubusypercentage`
  (the sysfs node) is confirmed broken on this kernel and should not be read
  directly.
- **CPU cap behavior across AYASpace modes is fully mapped** (see
  HARDWARE_PROFILE_v6_en.md / HARDWARE_PROFILE_v6_updates.md): Eco uses
  governor=powersave, Balanced uses schedutil, Streaming/Gaming/Max all use
  governor=performance with different scaling_max_freq ceilings. Gaming and
  Max are identical at the CPU level; Max only differs from Gaming at the GPU
  level.
- **Thermal ceiling behavior: CPU cores have been directly observed exceeding
  the HOTTER threshold (87-90.7 C, one core hit 96.1 C during the eden_v6
  run) with no observable throttling response.** Do not assume AYASpace
  provides thermal protection - this has NOT been confirmed and evidence so
  far suggests it does not intervene at these temperatures.
- **Module 3 (fan curve control) priority note:** given the thermal finding
  above, fan curve control may need to be moved up in priority versus
  originally planned, since we cannot assume AYASpace is already managing
  thermals safely on our behalf.
- **AYASpace write-conflict probe (originally v4 Section 10) has STILL never
  been executed in any version through v7.** This remains the single
  still-untested item in the full test matrix (see TEST_PROCEDURE.md, Series
  4).
- **v4's interactive mode-survey feature (sentinel-file polling loop) is
  CONFIRMED NON-FUNCTIONAL under `xsu`** and was correctly removed in v5 - do
  not attempt to revive this exact mechanism; any future interactive
  long-running root-shell workflow needs a different approach (see
  v4_handoff.md for details from the earlier retrospective).

## File map

- `pulse_lite_diag_v7.sh` - current diagnostic script (single functional fix
  vs v6, see v7_handoff.md)
- `v7_handoff.md` - full root-cause writeup and diff vs v6 for this version
- `HARDWARE_PROFILE_v6_en.md` + `HARDWARE_PROFILE_v6_updates.md` - full
  hardware/firmware findings, CPU/GPU/thermal tables across AYASpace modes
  (unaffected by the v7 fix, still current)
- `TEST_PROCEDURE.md` - ordered test plan; Series 2/3 should be RE-RUN with
  v7 to get valid FPS data (previous v5/v6 runs of these series have
  unreliable or missing FPS columns); Series 4 (AYASpace conflict probe)
  remains not yet executed
- `v4_handoff.md` / `v5_handoff.md` - retrospective root-cause writeups for
  earlier script versions, kept for project history and to avoid
  re-introducing already-diagnosed bugs

## What a new instance/session needs to pick up this project without re-hitting solved problems

Provide all of the following together, not piecemeal, at the start of a new
session: this README, v7_handoff.md, HARDWARE_PROFILE_v6_en.md (+ updates),
TEST_PROCEDURE.md, and pulse_lite_diag_v7.sh. That combination contains the
full current state, the full list of confirmed-fixed and confirmed-still-open
issues, and the exact script to run next - no piece of this history lives
only in conversation memory.
