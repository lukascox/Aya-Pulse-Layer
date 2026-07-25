# TEST_PROCEDURE.md (v8, VALIDATED)

## Purpose

Ordered test plan for the pulse_lite_diag project. Series 2 and 3 have now
been RE-RUN with v8 and are CONFIRMED VALID - both RetroArch and Eden/yuzu
produced correct layer matches, non-zero frame counts, and correct FPS
numbers in the large majority of samples. Series 1 and 4 status unchanged
from before.

## Series 1: AYASpace mode comparison baseline (STILL CURRENT, no re-run needed)

Status: COMPLETE, valid. Collected with v5, unaffected by the v7/v8 fixes
since this series does not depend on Section 7's foreground/FPS detection.
Already fully mapped in HARDWARE_PROFILE_v6_en.md - no action needed.

## Series 2: Light emulator load (RE-RUN COMPLETE - CONFIRMED VALID)

Status: VALIDATED this session (pulse_lite_diag_ra_v8.log). `matched_layer`
correctly showed
`com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture#457`
in all 30 samples (never the `ActivityRecordInputSink` line that v7
incorrectly matched). `frame_count_in_window` was 126-127 in all 30 samples,
and `computed_fps` was correct (59.8-60.7) in all 30 samples - 100% success
rate. GPU busy% (8.6-10.0%) and thermal trend (36.2C->38.9C over 90s) were
both consistent with expected light-emulator load. No further action needed
for this series beyond additional mode/game combinations if desired for
broader data collection.

## Series 3: Heavy emulator load (RE-RUN COMPLETE - CONFIRMED VALID, one minor open item)

Status: VALIDATED this session (pulse_lite_diag_eden_v8.log). `matched_layer`
correctly showed
`SurfaceView[com.miHoYo.Yuanshen/...](BLAST)#515` in all 30 samples (never
the "Background for SurfaceView[...]#516" wrapper that v6/v7 incorrectly
matched). `frame_count_in_window` was 126-127 in all 30 samples - a complete
reversal from the constant 0 seen in every prior version's Eden run.
`computed_fps` was correct (58.8-60.0) in 25/30 samples (~83%); the
remaining 5 samples hit a "zero span" edge case and safely printed "n/a"
rather than a wrong number (see v8_handoff.md for detail - candidate for a
v8.1 investigation, not blocking). GPU busy% (65-86%) and dynamic GPU
frequency scaling (231-422MHz) were both consistent with sustained active 3D
rendering. Thermal trend (49.9C->53.0C over 90s) showed no throttling
signal, consistent with the concerning "no thermal protection observed"
finding from earlier versions.

## Series 4: AYASpace write-conflict probe (STILL NOT YET EXECUTED)

Status: NEVER RUN in any version from v4 through v8. This remains the single
open item in the full test matrix, unrelated to the FPS/layer-selection work
that was just validated.

Original design (from v4 Section 10, not currently re-implemented in v8):
hold policy0 scaling_max_freq at a fixed Eco-equivalent value under
chmod 444 for 20 seconds while manually switching AYASpace modes (Gaming ->
Eco -> Max) in the UI, logging scaling_max_freq + governor every 2 seconds to
see whether AYASpace's own mode-switch logic overwrites a read-only-locked
sysfs value.

Action needed before this can be run: re-implement this probe as a new
suffix branch in a v8.x or v9 script (was present in v4, was NOT carried
into v5/v6/v7/v8 - it was deliberately dropped along with the broken
mode_survey feature in the v4->v5 transition, but the write-conflict probe
itself was never confirmed broken and is conceptually still valid). Flag
this as the next concrete script change needed if this test is prioritized.

## Recommended order for the next testing session

1. With the FPS/layer measurement pipeline now fully validated, prioritize
   real comparative data collection: run v8 across AYASpace modes
   (Eco/Balanced/Gaming/Max/Streaming) for both RetroArch and Eden to build
   out a genuine FPS/thermal/power comparison table - this was blocked until
   this session, and is now the main unblocked opportunity.
2. Optionally investigate the "zero span" FPS edge case in Eden samples if
   a few more runs show the same ~15-20% occurrence rate correlated with GPU
   frequency transitions (noted pattern, not yet statistically confirmed) -
   this is a nice-to-have precision improvement, not a correctness blocker.
3. Decide whether to invest in re-implementing Series 4 (write-conflict
   probe) as a new script branch, since it is unrelated to the FPS fix and
   can be scheduled independently.
4. If a future run of either app shows `frame_count_in_window: 0` again
   despite these confirmed-working results, do not assume a regression in
   the layer-selection logic itself - first check whether a new/different
   app or emulator with an unrecognized layer-naming convention is involved,
   since v8's priority tiers were built and validated specifically against
   RetroArch's and Eden's confirmed layer names.
