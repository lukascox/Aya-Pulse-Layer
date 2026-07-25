# TEST_PROCEDURE.md (v8)

## Purpose

Ordered test plan for the pulse_lite_diag project. Series 2 and 3 must be
RE-RUN with v8 - all prior runs (v5, v6, v7) of these series have unreliable
or completely missing FPS data. v5/v6 failed due to the foreground-detection
bug (fixed in v7); v7 correctly detected the foreground app but failed due to
the SurfaceFlinger layer-selection bug (fixed in v8, not yet validated).
Series 1 and 4 are unaffected by either bug.

## Series 1: AYASpace mode comparison baseline (STILL CURRENT, no re-run needed)

Status: COMPLETE, valid. Collected with v5, unaffected by the v7/v8 fixes
since this series does not depend on Section 7's foreground/FPS detection.

Modes to capture (all already collected): eco, balanced, gaming, max,
streaming. For each: switch AYASpace to that mode, run the script with a
matching suffix, no app running (idle home screen).

Purpose: map governor + scaling_max_freq + GPU pwrlevel/devfreq caps per mode.
Already fully mapped in HARDWARE_PROFILE_v6_en.md - no action needed.

## Series 2: Light emulator load (RE-RUN REQUIRED with v8)

Status: STILL INVALID. v7 runs (ra_v7.log, ra_balanced_v7.log) correctly
showed `topResumedActivity=com.retroarch.aarch64/...` and a non-"NoMatch"
`matched_layer` in every sample, but that matched layer was
`ActivityRecordInputSink com.retroarch.aarch64/...#288` - an input-handling
layer, not the render surface - so `frame_count_in_window` was 0 throughout.
v8 should now select the correct, unprefixed
`com.retroarch.aarch64/...RetroActivityFuture#<N>` layer instead.

Steps:
1. Switch AYASpace to Balanced.
2. Launch RetroArch, load any GBA/SNES-era core+game, start active gameplay.
3. Run: `sh pulse_lite_diag_v8.sh ra_balanced_v8`
4. Confirm in the resulting log: `matched_layer` does NOT contain
   "ActivityRecordInputSink", and `computed_fps` shows a real, non-frozen
   number (expect ~50-60fps range based on prior manual verification of the
   underlying --latency pipeline).
5. If `frame_count_in_window` is still 0, manually run
   `adb shell dumpsys SurfaceFlinger --latency "<exact matched_layer string
   from the log>"` and inspect the raw output by hand before assuming a new
   bug - see v8_handoff.md's "what to expect" section for the debugging
   order.

## Series 3: Heavy emulator load (RE-RUN REQUIRED with v8)

Status: STILL INVALID for the same reason as Series 2. v7 runs (eden_v7.log,
eden_balanced_v7.log) correctly detected `com.miHoYo.Yuanshen` as foreground
and matched a layer, but that layer was
`Background for SurfaceView[com.miHoYo.Yuanshen/...]#381` - a backing/
placeholder layer - not the real `(BLAST)` render layer, despite
`gpu_busy_pct` clearly showing 60-88% GPU load throughout both runs. v8
should now select the `(BLAST)` layer instead.

Steps:
1. Switch AYASpace to Gaming.
2. Launch Eden (or equivalent heavy emulator), start active 3D gameplay.
3. Run: `sh pulse_lite_diag_v8.sh eden_gaming_v8`
4. Confirm: `matched_layer` contains "(BLAST)", `computed_fps` varies between
   samples (not frozen, not n/a), and `thermal_skin`/CPU core temps show a
   rising trend under sustained load (expect the previously-observed 87-96 C
   range on some cores - this is a known, unresolved thermal finding, not a
   new bug to chase).

## Series 4: AYASpace write-conflict probe (STILL NOT YET EXECUTED)

Status: NEVER RUN in any version from v4 through v8. This remains the single
open item in the full test matrix, unrelated to the FPS/layer-selection work.

Original design (from v4 Section 10, not currently re-implemented in v8):
hold policy0 scaling_max_freq at a fixed Eco-equivalent value under
chmod 444 for 20 seconds while manually switching AYASpace modes (Gaming ->
Eco -> Max) in the UI, logging scaling_max_freq + governor every 2 seconds to
see whether AYASpace's own mode-switch logic overwrites a read-only-locked
sysfs value.

Action needed before this can be run: re-implement this probe as a new
suffix branch in a v8.x script (was present in v4, was NOT carried into v5/
v6/v7/v8 - it was deliberately dropped along with the broken mode_survey
feature in the v4->v5 transition, but the write-conflict probe itself was
never confirmed broken and is conceptually still valid). Flag this as the
next concrete script change needed if this test is prioritized.

## Recommended order for the next testing session

1. Re-run Series 2 with v8 (RetroArch, Balanced mode) - quick, validates the
   layer-selection fix in a simpler case (no BLAST layer, tier-3 heuristic)
   first.
2. Re-run Series 3 with v8 (Eden, Gaming mode) - validates the BLAST-layer
   priority tier under heavier, more thermally significant load.
3. If both come back clean (real FPS numbers, correct matched layers), the
   full FPS/GPU busy%/CPU/thermal pipeline can finally be considered fully
   validated end-to-end, closing out a bug chain that has spanned v4 through
   v8.
4. Only after that: decide whether to invest in re-implementing Series 4
   (write-conflict probe) as a new script branch, since it is unrelated to
   the FPS fix and can be scheduled independently.
5. If either re-run still shows `frame_count_in_window: 0`, do NOT assume
   v8's priority logic needs another blind iteration - first manually query
   the exact `matched_layer` string from the log via
   `adb shell dumpsys SurfaceFlinger --latency "<layer>"` to see the raw
   timestamp rows (or lack of them) before making further code changes.
