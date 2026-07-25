# pulse_lite_diag_v7.sh - Handoff

## What v7 fixes (single, precisely diagnosed bug)

v7 fixes the foreground-app detection step in Section 7, which was silently
broken in v4, v5, AND v6. This was NOT a layer-matching problem - the
SurfaceFlinger layer search logic was correct all along. The actual bug was one
step earlier: the script could never get a usable package name to search for.

## Root cause (confirmed manually against the live device, not guessed)

Manually running the exact commands v6 uses, over a plain `adb shell` (outside
the diagnostic script), gave these results on this device (Android 14,
AYANEO_PocketS2-user build):

- `dumpsys window windows | grep mCurrentFocus` -> returned NOTHING. This
  field does not exist in this dumpsys output on this Android 14 build. v6
  used this as its primary/only fallback source for window focus.
- `dumpsys activity activities | grep mResumedActivity` -> also returned
  NOTHING. The correct field name here is `topResumedActivity=`, not
  `mResumedActivity`.
- `dumpsys activity activities | grep -E 'topResumedActivity|mFocusedApp'` ->
  returned CORRECT, parseable data:
  `topResumedActivity=ActivityRecord{a020b44 u0 com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture t11}`
  and a matching `mFocusedApp=` line.

Because v6's PKG variable always resolved to empty/"unknown" (both of its
lookups failed), MATCHED_LAYER could never be found - even though
`dumpsys SurfaceFlinger --list` clearly showed live, correctly named layers
for both RetroArch and Eden throughout the v6 test runs (confirmed by manually
reading Section 6 output in both eden_v6.log and ra_v6.log). The layer search
itself never got a chance to run correctly because its input was always wrong.

## What changed in the code (exact diff vs v6)

Section 7, step (a) only:
- Removed: `dumpsys window windows | grep mCurrentFocus` (confirmed dead end
  on this build - kept it would only waste a dumpsys call per sample).
- Removed: `dumpsys activity activities | grep mResumedActivity` (wrong field
  name for this build).
- Added: `dumpsys activity activities | grep topResumedActivity=` as primary
  source, falling back to `grep mFocusedApp=` if that line is empty. Both
  confirmed present and correctly formatted on this device.
- PKG extraction regex is unchanged (`[a-zA-Z0-9_.]+/[a-zA-Z0-9_.]+`) - it
  already worked correctly once given a real `topResumedActivity=` line to
  search; it just never received one before.

Everything else - sections 0-6, the layer-matching logic in step (b), the
`--latency` parsing and sentinel-filtering in step (c), the GPU busy% overflow
guard, per-sample CPU/GPU capture in step (d), and section 8's write safety
test - is BYTE-FOR-BYTE UNCHANGED from v6. None of that code was found to be
broken; only the focus-detection lookup needed fixing.

## Independent confirmation that the FPS pipeline itself works correctly

Before writing v7, the underlying `--latency` method was manually verified
twice over adb, at two different panel refresh rates, using a live RetroArch
session and the confirmed real layer name:

- At 120Hz panel setting: `refresh_period_ns=8333333`, computed content
  framerate from `actualPresentTime` deltas ≈ 60fps.
- At 144Hz panel setting: `refresh_period_ns=6944444`, computed content
  framerate from `actualPresentTime` deltas ≈ 60fps (same result).

This confirms the `--latency` pipeline measures real emulated-content
framerate correctly and independently of the physical panel's refresh rate -
RetroArch renders the emulated console at its native rate (60fps here) and the
panel simply repeats frames to keep up with its own Hz, which is expected,
documented behavior in RetroArch/libretro forums, not a bug in our
measurement method. The `9223372036854775807` (INT64_MAX) sentinel row was
present at the end of the buffer in both manual tests and is filtered out by
the existing v6 parsing logic (carried into v7 unchanged).

## What to expect in v7 test logs

- `focus_raw` and `resumed_raw` should now contain a real, non-empty
  `topResumedActivity=...` string in every sample, for any foreground app.
- `matched_layer` should show a real SurfaceFlinger layer name (e.g.
  `SurfaceView[com.retroarch.aarch64/...]` or the plain
  `com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture#<N>`
  layer), not `NoMatch`, whenever RetroArch or Eden is in the foreground.
- `computed_fps` should show a real, non-frozen, non-zero number (expect
  ~50-60fps range for most emulated content on this device, matching the
  manually-verified pipeline behavior above), or a clearly labeled
  `n/a (low_sample_count=...)` if the sampled window genuinely had too few
  frames (e.g. a static menu screen).
- `gpu_busy_pct`, `gpu_freq_hz`, `cpu_snapshot`, and `thermal_skin`/
  `thermal_battery` are expected to behave exactly as they did in the v6 test
  runs (already confirmed sane there) - no change expected in this data.

## Known remaining limitations (carried over from v6, NOT addressed in v7)

- Section 7's per-sample thermal capture is still limited to skin + battery +
  one hardcoded hottest-zone slot (thermal_zone55/thermal_zone72) - it does
  not track all thermal zones per sample, only at the one-time Section 4
  snapshot at script start.
- The AYASpace write-conflict probe (originally Section 10 in v4) has still
  never been re-implemented or executed in any version from v5 onward.
- No change was made to the reversible write-safety tests in Section 8.

## Confidence level

HIGH. Every claim in this handoff is backed by a command that was manually
run against the live device and whose raw output was read directly, not
inferred from documentation or assumed from prior script behavior.
