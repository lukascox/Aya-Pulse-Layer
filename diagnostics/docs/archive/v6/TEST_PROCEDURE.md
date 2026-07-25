# TEST_PROCEDURE.md — pre-PoC diagnostic session (English, v6-updated)

Goal: assemble a complete set of logs covering hardware ranges, FPS data quality per
game type, and any conflict with AYASpace, so a FPS+busy% PoC script can be written
without further guessing. This version supersedes the original procedure — Series 2
and 3 were already executed once with a flawed FPS measurement method (see
correction below) and must be REPEATED with the v6 script, not the original one.

## Prerequisites

- `pulse_lite_diag_v6.sh` pushed: `adb push pulse_lite_diag_v6.sh /sdcard/`
- USB cable connected for the entire session (this is diagnostics, not a long-running
  daemon)
- ~20-25 minutes, across 4 short series

## CRITICAL CORRECTION — why this procedure changed from its original version

The original Series 2 and Series 3 both relied on `dumpsys gfxinfo framestats` as the
FPS measurement method, with the explicit goal of finding out "does gfxinfo actually
return data for RetroArch/heavy emulators." That question has now been ANSWERED, and
the answer is no:
- RetroArch run: gfxinfo returned "Total frames rendered: 0" in every sample despite
  active gameplay (renders via native SurfaceView, invisible to this API).
- Heavy emulator run (Eden, Super Mario Odyssey): gfxinfo returned IDENTICAL, frozen
  frame stats (117 frames, same histogram) across 5 samples spanning 15 seconds of
  real play — a stale cached snapshot, not live data.

Both series must be re-run using `pulse_lite_diag_v6.sh`, which replaces gfxinfo with
a SurfaceFlinger-based pipeline (focus detection -> layer match -> --latency FPS calc)
confirmed to work correctly across UI apps, RetroArch, and Switch emulators.

## Series 1 — baseline (system idle, launcher/home screen)

1. Return to the home screen (do not launch anything).
2. `adb shell xsu sh /sdcard/pulse_lite_diag_v6.sh baseline`
3. The script self-samples for 90s (section 7) — do not interact with the device
   during this window.
4. When done: `adb pull /sdcard/pulse_lite_diag_baseline.log`

This log gives: full SoC fingerprint, complete CPU/GPU frequency tables, idle
baseline temperatures, and confirmation that sysfs writes work (sections 8-9).
Note: at idle, SurfaceFlinger will show very few present events per sampling window
(low frame count) — v6 correctly flags this as "low_sample_count" rather than
reporting a misleadingly low FPS number. This is expected, not a bug.

## Series 2 — light emulator (e.g. GBA/SNES on RetroArch) [v6, repeat of prior test]

1. Launch RetroArch, load a GBA/SNES game, play for a few seconds until it's actually
   running smoothly (not just sitting in a menu).
2. While the game is actively running: `adb shell xsu sh /sdcard/pulse_lite_diag_v6.sh gba_retroarch_v6`
3. The script samples for 90s — PLAY actively during this window (do not pause or sit
   in a menu).
4. `adb pull /sdcard/pulse_lite_diag_gba_retroarch_v6.log`

This gives (v6): a real FPS reading from RetroArch's actual SurfaceView layer (not the
0 reported by gfxinfo previously), real CPU busy%/freq per policy, GPU freq/busy (with
overflow sanity-check), and a `matched_layer` field per sample confirming the pipeline
actually found the game's render layer rather than returning "NoMatch."

## Series 3 — heavy emulator (e.g. Switch/PS2/GameCube, heaviest title owned)
## [v6, repeat of prior test]

1. Launch the most demanding legally-owned title available (whatever previously
   caused noticeable slowdown and prompted interest in TDP control).
2. Similarly: `adb shell xsu sh /sdcard/pulse_lite_diag_v6.sh heavy_emulator_v6`
3. Play actively for 90s, ideally during an action scene, not a loading/menu screen.
4. `adb pull /sdcard/pulse_lite_diag_heavy_emulator_v6.log`

This gives (v6): real, per-sample varying FPS (not the frozen 117-frame snapshot seen
with gfxinfo previously), correlation with per-sample cpu_snapshot/gpu_busy_pct, and
confirmation of thermal behavior. NOTE: thermal thresholds (HOT/HOTTER from
HARDWARE_PROFILE.md) have ALREADY been confirmed exceeded in a prior heavy-load test —
CPU cores reached 87-90.7 C with no visible throttling from AYASpace's Gaming mode.
This series should now also be used to check whether that pattern repeats consistently,
not just to "discover" whether thresholds can be reached at all.

## Series 4 — AYASpace conflict test (optional, but important) [status: NOT YET RUN]

This series has NOT been executed in any log collected so far (baseline, balanced,
gaming, max, streaming, retroarch_balanced, heavy_gaming all measured ONE stable
AYASpace mode throughout their entire run; none tested switching modes mid-write).
Remains fully valid and still pending:

1. Return to the home screen, or leave anything running in the background.
2. `adb shell xsu sh /sdcard/pulse_lite_diag_v6.sh ayaspace_conflict`
3. The script will set policy0 to Eco-equivalent freq and hold it for 20s, printing
   the current value every 2s. DURING this 20-second window: manually switch in
   AYASpace Performance -> Gaming mode, then Eco, then Max (quickly, one after
   another).
4. `adb pull /sdcard/pulse_lite_diag_ayaspace_conflict.log`

This answers: does AYASpace overwrite our value despite `chmod 444` (log will show a
mid-window jump), or does our write hold independent of UI toggles?

## What to bring to the next session

- `pulse_lite_diag_baseline.log` (already collected, still valid)
- `pulse_lite_diag_gba_retroarch_v6.log` (NEW — re-run with v6, supersedes any prior
  gfxinfo-based RetroArch log)
- `pulse_lite_diag_heavy_emulator_v6.log` (NEW — re-run with v6, supersedes any prior
  gfxinfo-based heavy-emulator log)
- `pulse_lite_diag_ayaspace_conflict.log` (if Series 4 is completed — still pending as
  of this writing)

Four files, each from a different scenario, using the confirmed-working v6 FPS
pipeline — this is the complete data set needed to write the FPS+busy% PoC without
guessing anything further along the way.
