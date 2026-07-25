# TEST_PROCEDURE.md (v7)

## Purpose

Ordered test plan for the pulse_lite_diag project. Series 2 and 3 must be
RE-RUN with v7 - all prior runs (v5, v6) of these series have unreliable or
completely missing FPS data due to the foreground-detection bug fixed in v7
(see v7_handoff.md for root cause). Series 1 and 4 are unaffected by that bug.

## Series 1: AYASpace mode comparison baseline (STILL CURRENT, no re-run needed)

Status: COMPLETE, valid. Collected with v5, unaffected by the v7 fix since
this series does not depend on Section 7's foreground/FPS detection.

Modes to capture (all already collected): eco, balanced, gaming, max,
streaming. For each: switch AYASpace to that mode, run the script with a
matching suffix, no app running (idle home screen).

Purpose: map governor + scaling_max_freq + GPU pwrlevel/devfreq caps per mode.
Already fully mapped in HARDWARE_PROFILE_v6_en.md - no action needed.

## Series 2: Light emulator load (RE-RUN REQUIRED with v7)

Status: PARTIALLY INVALID. Previous runs (retroarch_balanced.log on v5,
ra_v6.log on v6) both show frozen/zero or NoMatch FPS data due to the now-
fixed foreground-detection bug. GPU busy%, CPU freq, and thermal data from
those runs remain usable as reference, but should ideally be re-collected
alongside valid FPS data for a clean, consistent dataset per run.

Steps:
1. Switch AYASpace to Balanced.
2. Launch RetroArch, load any GBA/SNES-era core+game, start active gameplay.
3. Run: `sh pulse_lite_diag_v7.sh ra_balanced_v7`
4. Confirm in the resulting log: `matched_layer` is NOT "NoMatch" in the
   majority of samples, and `computed_fps` shows a real, non-frozen number
   (expect ~50-60fps range based on prior manual verification).

## Series 3: Heavy emulator load (RE-RUN REQUIRED with v7)

Status: PARTIALLY INVALID for the same reason as Series 2. Previous run
(heavy_gaming.log on v5, eden_v6.log on v6) showed frozen frame counts /
NoMatch throughout, despite real, confirmed gameplay (Eden running Super
Mario Odyssey / a Yuanshen-labeled activity) and clearly live SurfaceFlinger
layers visible in Section 6 the whole time.

Steps:
1. Switch AYASpace to Gaming.
2. Launch Eden (or equivalent heavy emulator), start active 3D gameplay.
3. Run: `sh pulse_lite_diag_v7.sh eden_gaming_v7`
4. Confirm: `matched_layer` shows the real SurfaceView layer, `computed_fps`
   varies between samples (not frozen), and `thermal_skin`/CPU core temps
   show a rising trend under sustained load (expect the previously-observed
   87-96 C range on some cores - this is a known, unresolved thermal finding,
   not a new bug to chase).

## Series 4: AYASpace write-conflict probe (STILL NOT YET EXECUTED)

Status: NEVER RUN in any version from v4 through v7. This remains the single
open item in the full test matrix.

Original design (from v4 Section 10, not currently re-implemented in v7):
hold policy0 scaling_max_freq at a fixed Eco-equivalent value under
chmod 444 for 20 seconds while manually switching AYASpace modes (Gaming ->
Eco -> Max) in the UI, logging scaling_max_freq + governor every 2 seconds to
see whether AYASpace's own mode-switch logic overwrites a read-only-locked
sysfs value.

Action needed before this can be run: re-implement this probe as a new
suffix branch in a v7.x script (was present in v4, was NOT carried into v5/
v6/v7 - it was deliberately dropped along with the broken mode_survey feature
in the v4->v5 transition, but the write-conflict probe itself was never
confirmed broken and is conceptually still valid). Flag this as the next
concrete script change needed if this test is prioritized.

## Recommended order for the next testing session

1. Re-run Series 2 with v7 (RetroArch, Balanced mode) - quick, validates the
   fix in a lighter-load scenario first.
2. Re-run Series 3 with v7 (Eden, Gaming mode) - validates the fix under
   heavier, more thermally significant load.
3. If both come back clean (real FPS numbers, matched layers), the FPS/GPU
   busy%/CPU/thermal pipeline can be considered fully validated end-to-end.
4. Only after that: decide whether to invest in re-implementing Series 4
   (write-conflict probe) as a new script branch, since it is unrelated to
   the FPS fix and can be scheduled independently.
