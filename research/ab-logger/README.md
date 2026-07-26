# ab-logger

A minimal, single-purpose telemetry recorder for the AYANEO Pocket FIT:
two buttons, "Start log" / "Stop log", nothing else.

## Why this exists instead of reusing `research/autotdp-ab-harness`

That harness already had a proven sampling pipeline (FPS via
foreground-app detection + SurfaceFlinger layer match, CPU/GPU/thermal/
fan/battery snapshot, one CSV row every 2s) — but it was built to compare
its own "Start Baseline" / "Start AutoTDP" buttons, where "Start AutoTDP"
launches `pulse_lite_v3.7.sh` (an old, unrelated bash controller) as a
background daemon. Reusing that button as-is for testing
`research/pulse-for-aya` would launch that old script **alongside**
`pulse-for-aya`, both writing to the same CPU/GPU/fan sysfs nodes at
once — exactly the kind of vendor/controller write-contention risk this
project already flags elsewhere, self-inflicted this time. It also
carried "Run Capability Tests (1-9)" UI that's irrelevant to just
recording a session.

Given the proven pieces (`XsuShell.kt`, `FpsPipeline.kt`,
`ThermalZones.kt`, `PowerFanProbe.kt` — none of it hardware-specific
logic that needed rediscovering) are small and self-contained, a fresh
minimal app was cheaper and safer than trying to carve the daemon-launch
and Test 1-9 UI out of the existing one: less surface area to trust when
handing someone a console to run in the background during an actual play
session.

## What's reused vs new

- **Reused unchanged** (just repackaged, `pl.autotdpharness.app` →
  `pl.ablogger.app`): `XsuShell.kt`, `FpsPipeline.kt`, `ThermalZones.kt`,
  `PowerFanProbe.kt`.
- **New**: `LoggerService.kt` — a foreground service (not an
  Activity-scoped coroutine like the harness had) so the sampling loop
  survives being backgrounded for a whole game session; `LoggerSession.kt`
  — adapted from the harness's `AbSession`, same CSV columns minus the
  `mode` column (no Baseline/AutoTDP distinction here — see
  `research/pulse-for-aya/TESTING.md` for how the tester's own notes carry
  that instead); `MainActivity.kt` — two buttons, no mode picker, no
  Test 1-9.
- **Dropped entirely**: `AbHarness.kt`'s `PulseLiteV37` object (the old
  daemon launch/stop commands) and `SessionMode` enum, `MainActivity`'s
  Test 1-9 UI and logcat-dump helpers.

## Permissions

None beyond `FOREGROUND_SERVICE`/`FOREGROUND_SERVICE_SPECIAL_USE` (so the
logging loop can run continuously while backgrounded) and
`POST_NOTIFICATIONS` (Android 13+ requires this for the foreground
service's persistent notification — the app still logs fine if this is
denied, it just won't show progress in the notification shade). No
storage permission needed: the CSV sync to `/sdcard` is done by the root
shell itself (`xsu`), the same mechanism every other probe in this repo
uses — the app process never touches `/sdcard` through its own file APIs.

## Multiple sessions, no overwriting

Each "Start log" while not already running begins a brand-new file,
`/sdcard/apl_ab_logs/session_<timestamp>.csv` — safe to start/stop
repeatedly across a testing session without colliding with earlier runs.
Reopening the app while a session is already logging in the background
correctly shows "Stop log" enabled, "Start log" disabled (checked via
`LoggerService.isRunning`).

## Build / install

```bash
cd research/ab-logger
echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > local.properties  # gitignored, per-machine
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

See `TESTING.md` for how to run a session and pull/clean up the resulting
logs, and `research/pulse-for-aya/TESTING.md` for the actual native-vs-
`pulse-for-aya` A/B procedure this tool is used inside of.

## Real fan RPM reading (2026-07-25 update)

The original fan-signal column relied on `PowerFanProbe`'s generic
`cooling_device` discovery, inherited from `autotdp-ab-harness` — on this
device that search finds nothing, so `fan_signal`/`fan_signal_unit` always
came back `n/a`/`not_found` (confirmed in this app's own first smoke
test, see "Status" below). `research/aya-gamewindows-teardown/FINDINGS.md`
(pass 3, section 6) found and confirmed-live the device's *actual* fan
mechanism: a plain Linux `pwm-fan` platform driver at
`/sys/devices/platform/soc/soc:pwm-fan/fan_rpm_state`, readable even
without root, returning e.g. `Current RPM 2815`.

**What changed**: `LoggerService.resolveFanNode()` now tries that
confirmed path first (`LoggerService.kt`'s `FAN_RPM_PATH`), parses out the
plain RPM number for the CSV (`LoggerSession.parseFanSignal()`), and only
falls back to the old generic `cooling_device` search if that specific
node isn't present (kept as a safety net for a different device/firmware,
not deleted). **Read-only** — this app never writes to the fan node, and
neither does `pulse-for-aya`'s still-deliberately-stubbed
`FanController.kt`; write access to this path is a separate, unconfirmed,
not-yet-exercised question (see `pulse-glue-assessment/FINDINGS.md`). This
change only makes the A/B test's CSV data complete (real fan RPM
alongside CPU/GPU/FPS/temp/battery) — it doesn't add any new actuation or
risk to the device.

## Status (2026-07-25)

Builds clean, installs, launches. Smoke-tested on-device: a session was
started, ran for several minutes producing a well-formed, real-valued CSV
(live CPU governor/freq per policy, battery current/voltage — thermal
zones came back `n/a` this run, worth checking on a longer/real session
whether that's a one-off or needs a fix), and multiple xsu commands
completed successfully throughout. Not yet exercised as an actual A/B
comparison tool (that's the next step, per `TESTING.md`).

One thing observed during this smoke test, later found to be a leading
indicator of something more serious (see "Incident" below): `logcat`
showed several `Fatal signal 6 (SIGABRT)` crash traces from `xsud` (the
on-device root daemon, a vendor system binary, not anything in this repo)
during rapid app-switching moments — each crash was immediately followed
by a fresh `xsud` worker handling the next command successfully, and a
12-second idle window with steady polling showed zero crashes. Also
incidentally confirmed during the same session: the device's real native
fan PWM node is `/sys/devices/platform/soc/soc:pwm-fan/hwmon/hwmon0/pwm1`
(standard Linux hwmon, actively written by the system, not the Odin-style
`gpio5_pwm2` `pulse` assumes), and SELinux is running in permissive mode
on this ROM — folded into `diagnostics/docs/HARDWARE_PROFILE.md`.

## Incident (2026-07-25): full `system_server` crash during the first real test session

The first real (not smoke-test) session — logging started, app
backgrounded, user playing normally — ended with the device appearing to
freeze for ~25-30 seconds. `logcat` showed the real cause was more severe
than an app-level hang: **`system_server` itself crashed and fully
restarted** (a `FATAL EXCEPTION IN SYSTEM PROCESS` inside
`BatteryService$Led`'s charging-LED animation, calling into the `ILights`
HAL and getting back error `-13`, uncaught). Full timeline in
`STATUS.md`. That specific crash site is vendor/AOSP code with nothing to
do with this app directly, but the timing is not treated as coincidence:
`com.android.systemui` had already ANR'd and `BLASTSyncEngine` was
reporting missed transaction commits for ~25 seconds *before* the crash,
starting right around when this app's logging loop began and the app was
backgrounded — with `pulse-for-aya`'s own background service also
polling via `xsu` at the same time. The leading theory: sustained,
frequent `xsu` process-spawning from two apps at once, compounded by the
already-known `xsud` crash-on-cleanup-per-connection quirk above, created
enough system load/binder contention to starve a timing-sensitive HAL
call that would otherwise have succeeded.

**Not proven beyond circumstantial evidence** (no kernel-level profiling
was done), but treated as confirmed-serious rather than a cosmetic
quirk — a full `system_server` restart is a real, visible device
disruption, not a benign log line.

**Mitigation applied**: `LoggerSession.sampleOnce()` used to make 3-4
separate `xsu` calls per sample (foreground-app detect, layer list, FPS
latency, full CPU/GPU/thermal/battery snapshot — each one a full process
spawn, each triggering the `xsud` fork-per-connection behavior above).
Combined the three that don't need a Kotlin-side decision in between
(activity dump, layer list, and the snapshot) into a single `xsu` call
split by `===TAG===` markers (same convention `PowerFanProbe` already
uses) — only the FPS `--latency` query still needs its own call, since
its argument depends on parsing the combined call's output first. Net:
4 spawns/sample down to 1-2. Also raised the sampling interval
(`LoggerService.SESSION_INTERVAL_MS`) from 2000ms to 5000ms. Builds
clean; **not yet re-verified on-device** (the device was being restarted
by the user at the time this fix landed) — the next real session is the
actual test of whether this mitigation holds.

**Update (2026-07-26): that mitigation was itself the bug.** Two real A/B
sessions came back 100% empty (every column `?`/`n/a` across 48/48 samples
each — see `STATUS.md` INCIDENT #2/#3). Root-caused live on-device, outside
this app entirely: `xsud` segfaults (or silently drops output) once a
single `xsu -c` argument crosses roughly 1000-1200 characters — a fuzzy,
race-like threshold, not a hard cutoff — and the combined snapshot command
above runs ~3150 characters on this device (19 CPU + 8 GPU thermal zones).
Full bisection methodology and evidence in
`research/xsu-capability-probe/FINDINGS.md`'s "Root cause found" section.
**Fixed**: `LoggerSession.sampleOnce()` now keeps the short ACT+LIST call
combined (its *command text* is short — output size was never the
problem) but runs the CPU/GPU/thermal/fan/battery snapshot through
`XsuShell.execChunked()`, which packs those statements into multiple calls
each safely under ~700 characters. Validated with the same on-device
bisection method before being applied here (0/5 failures per chunk, 15/15
over a sustained 5s-interval run) — see the FINDINGS.md section for the
raw numbers. Do not re-combine the snapshot statements into one call.

Also added this session: two new CSV columns, `pulse_installed` and
`pulse_service_running`, recorded once at session start (`LoggerService`)
and repeated on every row — so a pulled CSV is self-describing about which
A/B arm it's from, without needing to match it up against separately-taken
session notes/timestamps.
