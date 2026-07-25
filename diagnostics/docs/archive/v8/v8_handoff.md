# pulse_lite_diag_v8.sh - Handoff

## What v8 fixes (single, precisely diagnosed bug)

v8 fixes the SurfaceFlinger layer-selection step in Section 7, step (b),
which was silently wrong in both v6 and v7. This was NOT a foreground-
detection problem - v7's fix for that (topResumedActivity= parsing) worked
perfectly. The bug was one step later: even with the correct package name in
hand, the script was picking the WRONG layer out of SurfaceFlinger's list for
that package.

## Root cause (confirmed by reading actual v7 test log data, not guessed)

All four v7 test runs (ra_v7.log, ra_balanced_v7.log, eden_v7.log,
eden_balanced_v7.log) showed `frame_count_in_window: 0` and
`computed_fps: n/a (low_sample_count=0)` for every single one of the 30
samples in each 90-second run - including during the Eden run where
`gpu_busy_pct` was simultaneously showing 60-88% GPU load the entire time,
which is only possible if real 3D frames were being rendered continuously.
This proved the "no frames" result was a measurement bug, not a real
absence of frames.

Reading Section 6 (`dumpsys SurfaceFlinger --list`) in the same logs showed
why:

- For Eden (`com.miHoYo.Yuanshen`), the dump contains, in this order:
  `Background for SurfaceView[com.miHoYo.Yuanshen/...]#381`, then
  `SurfaceView[com.miHoYo.Yuanshen/...]#379`, then
  `SurfaceView[com.miHoYo.Yuanshen/...](BLAST)#380`. v7's
  `grep -i "SurfaceView.*$PKG_SHORT" | head -1` always matched the FIRST of
  these - the "Background for" wrapper layer, which is a backing/placeholder
  layer that never receives presentation timestamps. The actual frame-
  producing layer is the `(BLAST)` one, listed third.
- For RetroArch (`com.retroarch.aarch64`), the dump has no line containing
  "SurfaceView" at all - RetroArch's renderer does not use that naming on
  this build. v7's fallback `grep -i "$PKG_SHORT" | head -1` therefore
  matched the FIRST line containing the package name, which was
  `bc18457 ActivityRecordInputSink com.retroarch.aarch64/...#288` - an
  input-handling layer, not a render surface. The real render layer, plain
  `com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture#290`
  (no prefix), is listed later in the same block.

In both cases, the layer-matching regex itself was fine - it was the "take
the first match" strategy that was wrong, because SurfaceFlinger consistently
lists helper/wrapper layers before (or instead of) the real render layer for
these two apps on this device/build.

## What changed in the code (exact diff vs v7)

Section 7, step (b) only. Replaced the single `head -1` grep (with one
fallback) with a 4-tier priority search:

1. **BLAST layer first**: `grep -i "$PKG_SHORT" | grep -i "(BLAST)" | head -1`
   - BLAST (Buffered Layer Async State Transaction) is the modern Android
     buffer-queue layer that actually receives presentation timestamps.
     Confirmed present and correctly named for Eden in all Eden logs.
2. **Plain SurfaceView, excluding wrappers**: adds
   `grep -viE "Background for|Bounds for -"` to the old SurfaceView grep, so
   the backing/placeholder layers are explicitly skipped even if no BLAST
   layer exists.
3. **Last non-helper package match**: for apps with no SurfaceView-named
   layer at all (confirmed case: RetroArch), search all lines containing the
   package name, explicitly exclude
   `ActivityRecordInputSink|Background for|Bounds for -|Dim layer`, and take
   the LAST remaining match with `tail -1` instead of the first - confirmed
   correct against the RetroArch Section 6 dump, where the real render layer
   is the last, unprefixed line in its block.
4. **Old v6/v7 fallback**: kept as a final safety net in case tiers 1-3 all
   return nothing for some other app not yet tested (not expected to trigger
   for RetroArch/Eden based on the confirmed v7 log data).

Everything else - sections 0-6, step (a)'s topResumedActivity= detection
(kept from v7, confirmed working), step (c)'s `--latency` parsing/sentinel-
filtering, step (d)'s CPU/GPU/thermal capture, and section 8's write safety
test - is BYTE-FOR-BYTE UNCHANGED from v7.

## What to expect in v8 test logs

- `matched_layer` for Eden should now show a line containing `(BLAST)`, not
  `Background for SurfaceView[...]`.
- `matched_layer` for RetroArch should now show the plain, unprefixed
  `com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture#<N>`
  layer, not the `ActivityRecordInputSink` line.
- `frame_count_in_window` should now be a real, non-zero number (roughly
  `SAMPLE_INTERVAL * expected_fps`, so somewhere around 45-180 for a 3-second
  window at 15-60fps) in the large majority of samples, for both apps.
- `computed_fps` should show a real number instead of
  `n/a (low_sample_count=0)`, expected in the ~50-60fps range for RetroArch
  content (consistent with the manually-verified 120Hz/144Hz panel tests
  from before v7) and a plausible, possibly more variable number for Eden's
  heavier 3D content.
- If `frame_count_in_window` is STILL 0 after this fix for either app, the
  next debugging step would be to manually dump
  `dumpsys SurfaceFlinger --latency "<the exact matched_layer string>"` over
  adb for that specific layer name and confirm by hand whether it returns
  real timestamp rows - do not assume another silent layer-selection bug
  without checking this first, since v8's search logic was built directly
  from the confirmed-correct layer names in the v7 log data.

## Known remaining limitations (carried over from v6/v7, NOT addressed in v8)

- Section 7's per-sample thermal capture is still limited to skin + battery +
  no full-zone tracking per sample (only at the one-time Section 4 snapshot).
- The AYASpace write-conflict probe (originally Section 10 in v4) has still
  never been re-implemented or executed in any version from v5 onward.
- No change was made to the reversible write-safety tests in Section 8.
- v8's tier-3 "last match" heuristic is based on confirmed behavior for
  exactly two apps (RetroArch, Eden/yuzu) on this exact device/build. Other
  emulators (Dolphin, Azahar/Citra, PPSSPP) have not yet been tested against
  this logic and may need their own tier if they turn out to use a different
  layer-naming convention.

## Confidence level

HIGH for the root-cause diagnosis (backed directly by reading the actual v7
log contents, not inferred). MEDIUM-HIGH for the fix itself - the priority
order was designed to match the exact confirmed-correct layer names for
RetroArch and Eden, but has not yet been run against the live device, so it
should be validated with a fresh v8 test run before being considered fully
closed.
