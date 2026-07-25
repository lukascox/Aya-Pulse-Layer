# pulse_lite_diag_v6.sh - Handoff

## What changed vs v5

| Area | v5 | v6 | Why |
|---|---|---|---|
| FPS source | `dumpsys gfxinfo <pkg>` (frame stats section) | `dumpsys SurfaceFlinger --latency <layer>` on a dynamically detected layer | gfxinfo is blind to native SurfaceView content (RetroArch = 0 frames) and returned frozen/cached stats for Eden (identical across 5 samples spanning 15s) |
| App detection | Hardcoded to whatever was foreground at script start (pid captured once) | Re-detected every sample via `dumpsys window windows \| grep mCurrentFocus` + `dumpsys activity activities \| grep mResumedActivity` | Allows one script run to correctly track app switches, and removes reliance on a single early pid lookup |
| Layer matching | N/A (didn't exist) | Fuzzy grep against `dumpsys SurfaceFlinger --list`, prefers `SurfaceView[pkg]` over generic `pkg/Activity` layer | SurfaceView layer = actual game/emulator render surface; generic layer = Android UI chrome only |
| CPU governor/freq | Captured once at script start (section 2), static for whole run | Captured every sample (new field `cpu_snapshot` per sample) in addition to the static baseline snapshot | Needed to correlate freq/governor changes with FPS/GPU load in real time, especially under schedutil (Balanced) which changes freq constantly |
| GPU freq/busy | Captured once at script start, gpu_busy_pct sometimes negative/garbage | Captured every sample, with sanity check: any result outside 0-100% is reported as `n/a (counter_reset)` instead of a raw (often negative) number | Raw kgsl cycle counters wrap/reset between reads on this kernel; v5 had no guard against this |
| Idle/static handling | None - would just report whatever gfxinfo cached | Frame count per window is checked; if `< 5` frames present, FPS is reported as `n/a (low_sample_count=N, likely idle/static frame)` | Prevents misreading "nothing changed on screen" as "app is running at 2 FPS" |
| Output structure | Sections 1-7 + write test | Same overall structure (sections 1-8), section 7 fully rewritten, section 8 (write safety test) unchanged, added a cpufreq write-test specifically for policy0 scaling_max_freq (relevant for future AutoTDP control) |

## What stayed the same (unchanged from v5)
- Sections 1-6 (SoC fingerprint, CPU topology baseline table, GPU kgsl baseline, thermal zone
  full dump, installed emulator package list, SurfaceFlinger window list reference dump) are
  logically identical to v5, only lightly reformatted.
- Overall run pattern: one suffix argument, one log file per run, no interactive/sentinel-file
  survey mode (that dead-end from pre-v5 iterations stays removed).
- 90-second sampling window, still the default duration (30 samples x 3s interval).
- Write-safety test at the end (confirms /sdcard and sysfs cpufreq write permissions under xsu).

## How to run
```sh
adb push pulse_lite_diag_v6.sh /sdcard/pulse_lite_diag.sh
adb shell xsu sh /sdcard/pulse_lite_diag.sh <suffix>
adb pull /sdcard/pulse_lite_diag_<suffix>.log
```
Same six-run protocol as before (eco/balanced/gaming/max/retroarch_balanced/heavy_gaming, plus
optional streaming) still applies - only the internal FPS/CPU/GPU sampling logic changed.

## What to check when reading v6 logs
- `matched_layer` field per sample - confirm it's actually matching a sensible SurfaceView or
  Activity layer, not "NoMatch". If NoMatch keeps appearing for a known-running app, the fuzzy
  grep pattern likely needs adjustment for that app's layer naming.
- `computed_fps` vs `frame_count_in_window` - always check frame count before trusting the FPS
  number. Low frame count = don't trust the FPS value, it's flagged as such.
- `cpu_snapshot` per sample - for Balanced (schedutil) expect visible bouncing; for
  Gaming/Max/Eco expect it pinned near governor's fixed target.
- `gpu_busy_pct` - if you see `n/a (counter_reset)` repeatedly during heavy load, that's the
  known kgsl counter wraparound, not a new bug - documented, not yet fixable from userspace.

## Not yet done (still open after v6)
- No actual control logic (no writes to scaling_max_freq/governor based on measured load) -
  v6 is still observation-only, plus one write-permission smoke test.
- No aggregated summary/statistics output (CSV of per-sample FPS/freq/GPU over the run) - logs
  are still raw text, would need a separate parser pass to turn into a clean table/chart.
- Layer-matching heuristic is not yet tested against Dolphin or PPSSPP specifically, only
  RetroArch and Eden so far.
