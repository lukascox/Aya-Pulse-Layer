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

One thing observed during this smoke test, worth being aware of rather
than alarmed by: `logcat` showed several `Fatal signal 6 (SIGABRT)` crash
traces from `xsud` (the on-device root daemon, a vendor system binary,
not anything in this repo) during rapid app-switching moments — each
crash was immediately followed by a fresh `xsud` worker handling the next
command successfully, and a 12-second idle window with steady polling
showed zero crashes. Not reproduced from a single isolated `xsu -c "id"`
call either. Most consistent explanation so far: `xsud` forks a worker
per connection and something about its cleanup path aborts under rapid
concurrent access (this session had both `pulse-for-aya`'s background
service and `ab-logger` hitting `xsu` around the same moments) — not
confirmed to drop or corrupt any actual command result, but also not
something this project had documented before. Also incidentally
confirmed during the same session: the device's real native fan PWM node
is `/sys/devices/platform/soc/soc:pwm-fan/hwmon/hwmon0/pwm1` (standard
Linux hwmon, actively written by the system, not the Odin-style
`gpio5_pwm2` `pulse` assumes), and SELinux is running in permissive mode
on this ROM. None of this blocks using the tool, but worth folding into
`diagnostics/docs/HARDWARE_PROFILE.md` and keeping an eye on the SIGABRT
pattern during real, longer test sessions.
