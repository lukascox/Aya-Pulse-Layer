# AutoTDP A/B Harness — throwaway probe (NOT the real app)

Extends `research/xsu-capability-probe/` with Tests 8-9 (fan/power discovery,
backgrounded-process persistence) and adds the actual point of this app: an
A/B comparison harness that runs the existing, already-validated
`pulse_lite_v3.7.sh` (lives in the sibling `apl-diag` repo) as a background
daemon and logs CSV data comparing it against an untouched baseline.

Already built and verified compiling in this session:
`app/build/outputs/apk/debug/app-debug.apk`

## Precondition: push the script once (not automated by this app)

```bash
adb push <path-to-apl-diag-checkout>/docs/archive/pulse_lite/v3.7/pulse_lite_v3.7.sh /sdcard/pulse_lite.sh
```

This is a manual, one-time step per device/session — the app only ever
launches whatever is already at `/sdcard/pulse_lite.sh`, it does not push it
itself (see `AbHarness.kt`'s `PulseLiteV37` object).

## Install and run

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Second terminal, leave running during any test that touches sysfs writes:

```bash
adb logcat | grep -i avc
```

## What's in this app

- **"Run Capability Tests (1-9)"** — same prose-log format as
  `xsu-capability-probe` (screen + `/sdcard/xsu_benchmark_result.txt` +
  logcat tag `XSU_BENCH`). Tests 1-7 are carried over unchanged. New:
  - **Test 8** — fan RPM / power-draw node discovery. Nothing hardcoded;
    logs exactly what's found (hwmon, cooling_device types, fan-name
    search, power_supply entries, battery current/voltage/power, debugfs
    mount state) using a block-tagged (`===TAG===`) output format since
    several of these commands produce multi-line results.
  - **Test 9** — confirms a `sh -c '... &'` backgrounded loop launched via
    `xsu` keeps running and ticking after the launching call has already
    returned, and is still visible via `pgrep`. **This is a hard gate for
    the A/B harness** — if it fails, the "AutoTDP" button's daemon-launch
    approach needs rethinking (a Kotlin polling loop reapplying caps every
    tick instead of a true background daemon).
- **"Start Baseline" / "Start AutoTDP" / "Stop"** — the actual A/B harness.
  Both modes run the identical sampling loop (FPS pipeline + CPU/GPU/
  thermal/fan/battery snapshot, one CSV row every 2s, matching
  `pulse_lite_v3.7.sh`'s own loop cadence). AutoTDP additionally launches
  the script as a background daemon at start and sends its stop sentinel
  (`touch /sdcard/pulse_lite.stop`) at stop, then explicitly verifies stock
  values were restored — does not trust the script's own exit trap blindly.
  CSVs: `pulsefit_baseline_<timestamp>.csv` / `pulsefit_autotdp_<timestamp>.csv`,
  synced to `/sdcard/` periodically during the session (every 10 samples),
  not just at the end.

## Pull results

```bash
adb pull /sdcard/xsu_benchmark_result.txt
adb pull /sdcard/pulsefit_hw_profile.txt
# after each A/B session, filename shown in the app's log:
adb pull /sdcard/pulsefit_baseline_<timestamp>.csv
adb pull /sdcard/pulsefit_autotdp_<timestamp>.csv
```

Per the test procedure agreed for this app: rename pulled CSVs to include
the game/scene identifier before they pile up (e.g.
`pulsefit_baseline_retroarch_gba_run1.csv`) — the generic timestamp-only
name gets confusing fast across several games x two modes x two orderings.
**Representative/interesting CSVs from completed sessions should end up in
the sibling `apl-diag` repo's `logs/` directory** — the harness's code lives
here (it needs this repo's Kotlin/Gradle scaffolding), but the empirical
data it produces is exactly the kind of reference material `apl-diag`
already collects.

## Design notes carried over from the prior probe (still apply here)

- Only the `args` xsu invocation method is used (`ProcessBuilder("xsu", "-c",
  cmd)`) — the `stdin` method is confirmed broken, not reimplemented.
- Every batched sysfs/thermal/power snapshot call gets its own short timeout
  (`SNAPSHOT_TIMEOUT_SEC` / explicit `timeoutSec` params) and its own logged
  elapsed time, separate from the FPS pipeline's `pipeline_ms` — this is the
  direct fix for the ~126s stall found in the prior probe's run 1 (see
  `xsu-capability-probe/FINDINGS.md`).
- Thermal zones (CPU/GPU/skin/battery) are resolved dynamically by zone
  `type` prefix at session start, not hardcoded zone numbers — these can
  shift across firmware revisions (see `apl-diag/docs/HARDWARE_PROFILE.md`).
- `java.lang.Process` on Android has no `toHandle()`/`ProcessHandle` — on a
  call timeout, only `destroyForcibly()` on the immediate process is
  possible, confirmed by a failed compile attempt in the prior probe, not
  re-attempted here.

## Non-goals (unchanged from the prior probe's own stated scope)

- No production error handling, retry policies, or UI polish.
- No investigation of the FPS "zero span" edge case — logged when it
  happens, not fixed here.
- No porting of `pulse_lite_v3.7.sh`'s tier/hysteresis/floor logic into
  Kotlin — the "AutoTDP" button launches the existing, already-validated
  script as-is. Treat whatever it does as "AutoTDP v0" (busy%-driven); the
  FPS-delta-augmented v1 design is separate work that comes AFTER this app
  produces real comparison data, not before.
- This code is not meant to be copy-pasted into the real `app/` as-is — informed
  by these results, not iterated on inside this throwaway harness.
