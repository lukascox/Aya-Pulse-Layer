# AIDL Fan Spike — throwaway probe (NOT the real app)

**What this tests:** whether the fan-curve AIDL commands found in
`research/ayaspace-teardown/FINDINGS.md` ("Addendum: the native Custom
fan-curve editor is a full AIDL surface") — `com_set_performance_fan`,
`com_set_fan_speed_strategy`, `com_set_fan_speed_is_linear` — actually work
when sent live, the same way `research/aidl-bind-spike/` proved
`com_set_performance_mode` does. Those three commands were only ever
traced statically (decompiled source, never exercised); this app is the
empirical confirmation step.

If this works, `pulse-for-aya`'s stubbed `FanController.kt`
(`setMode()`/`customFanAvailable()` currently hardcoded `false`) has a real
implementation path: drive the fan through this same no-root AIDL channel
instead of raw sysfs, closing the single biggest remaining feature-parity
gap against upstream `pulse` (see `research/pulse-for-aya/README.md`,
"Feature parity vs upstream").

## What it does, concretely

Same bind/register mechanism as `aidl-bind-spike` (see that project's
`AidlProtocol.kt`/`MainActivity.kt` for the full reasoning — copied here,
not shared, since these are one-shot probes not a library). Once connected
(status line reads `READY -- connected, clientId=...`), the buttons are
laid out as **two separate, deliberately staged tests**:

**Step 1 — discrete mode only, no curve write.** Four buttons (OFF / MUTE
/ BALANCE / TURBO) send just `com_set_performance_fan:<mode>` — the
already-known-to-exist command from the original AIDL catalog. This
isolates whether the simplest possible fan command does anything at all,
before risking the riskier curve write in step 2. If step 1 alone visibly
changes the fan, that's already a usable result even if step 2 doesn't
pan out.

**Step 2 — the real question.** "Send test curve (CUSTOM)" sends
`com_set_performance_fan:FAN_MODE_CUSTOM`, waits 500ms, then
`com_set_fan_speed_strategy:FAN_MODE_CUSTOM-50,12|65,32|78,68|85,95|95,100`
— a moderate, deliberately-chosen-not-extreme test curve (the same
"ramp harder, sooner" shape discussed as a sensible improvement earlier in
this project's fan-safety conversation, not a random or aggressive value).

**Every send is followed by an objective read-back**, not just trusting
that the Binder `transact()` didn't throw: `XsuShell` reads the confirmed
`pwm-fan` hwmon node (`fan_rpm_state` + `hwmon*/pwm1`, both readable
without root even — `xsu` is used here only for consistency with the rest
of this repo's probes) 1.5s after each send. A "Read fan state" button is
always enabled (even before connecting) for manual checks, and one
baseline read fires automatically on launch, before anything is touched.

## Enum-string format — reconstructed, not confirmed, flagged honestly

`FAN_MODE`'s underlying Kotlin enum constants are named `FAN_MODE_OFF`/
`FAN_MODE_MUTE`/`FAN_MODE_BALANCE`/`FAN_MODE_TURBO`/`FAN_MODE_CUSTOM`
(confirmed from `FanSpeedConfig.java`'s `WhenMappings` block, per the
scout pass that found this whole mechanism). The AIDL message is built by
plain string concatenation (`"...:" + fanMode`) in AYA's own decompiled
code, which for an unmodified enum calls its default `toString()` — the
constant name verbatim. **This app is the first live test of whether that
assumption holds.** If every send in this app fails verification, a
wrong enum-string guess (rather than a dead command) is the first thing to
suspect — see "What a FAILURE looks like" below.

## Install and run

```bash
cd research/aidl-fan-spike
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n pl.aidlfanspike.app/.MainActivity
```

You should see, within a second or two: `bindService() returned: true`,
then `onServiceConnected`, then a `callback received:
"msg_type_register:<id>"` line, and the status line reading **"READY --
connected, clientId=..."** with all buttons enabled. This alone already
confirms the bind mechanism still works (expected — `aidl-bind-spike`
already proved this part; if it suddenly fails here, something changed on
the device side since then, worth noting).

**Recommended order**: tap one Step 1 button first (e.g. TURBO — should be
audibly/RPM-obviously different from idle if it works at all), read the
log, then try Step 2. Don't jump straight to the curve write untested.

## Quick test loop (for iterating without reinstalling each time)

Once the APK is installed once, you don't need to reinstall for most
iteration — just relaunch and watch logcat live:

```bash
# One-time: build + install
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk

# Each iteration: relaunch + tail the app's own log tag live
adb shell am start -n pl.aidlfanspike.app/.MainActivity
adb logcat -c && adb logcat -s AIDL_FAN_SPIKE:D
# (tap buttons on-device while this runs; Ctrl-C to stop watching)
```

If you change the Kotlin source (e.g. to try a different test curve or a
different enum-string guess), only then do you need the full
`assembleDebug && adb install -r ...` step again.

## What a SUCCESS looks like

```
--- sending fan mode TURBO ---
send("7:msg_type_performance:com_set_performance_fan:FAN_MODE_TURBO")
send() transact completed without exception
fan state [post-send, send_ok=true] (94ms): RPM=Current RPM 4200 | PWM_DUTY=210
```
— a visibly higher RPM/duty than the baseline read at launch, with no
`xsu` write of our own anywhere in the *command* path (only in the
read-back verification, exactly like `aidl-bind-spike`).

For step 2, a successful curve write should show the duty settle near
whatever `TEST_CURVE`'s value is for the device's *current* temperature
(e.g. near 12% if the SoC is cool/idle) rather than jumping to 100% —
that distinguishes "the curve was actually applied" from "CUSTOM mode
alone just pins something else."

## What a FAILURE looks like, and what each one would mean

- **Bind/register fails** — see `aidl-bind-spike/README.md`'s failure
  list, unchanged; would be surprising since that mechanism was already
  proven.
- **`send()` throws for the mode commands (Step 1)** — would mean
  `com_set_performance_fan` itself is rejected, or the enum-string
  reconstruction is wrong (try `OFF`/`MUTE`/etc. without the `FAN_MODE_`
  prefix as the next guess if so — cheap to test, just edit the constants
  at the top of `MainActivity.kt`).
- **`send()` succeeds (no exception) but the RPM/duty read-back never
  changes, for Step 1** — the message was accepted but ignored; could mean
  this specific `IAyaDevices` implementation doesn't wire
  `com_set_performance_fan` the way `FanViewModel.java` assumes, or fan
  mode changes only apply on the *next* foreground-app/profile transition
  rather than instantly.
- **Step 1 works but Step 2 (`com_set_fan_speed_strategy`) doesn't** —
  narrows the problem to the curve-write path specifically, e.g. the
  `mode-pairs` string format might need a different separator than
  inferred, or `FanSpeedConfig.h()`'s exact serialization was
  misread. Worth re-checking `research/ayaspace-teardown/
  ayasettings_decompiled/sources/com/ayaneo/settings/ui/device/fan/
  FanSpeedConfig.java` directly if this happens.
- **Everything reports success and the read-back genuinely changes** —
  the fan-control path for `pulse-for-aya` is real; next step is porting
  `FanTempController.kt`/`FanCurve.kt`'s actual logic to drive this
  channel instead of a one-shot test curve.

Any outcome is a real, useful answer — same empirical standard as every
other probe in this repo.

## After testing — putting the fan back

This app never touched sysfs directly and never disabled AYA's own fan
management — the simplest, guaranteed-correct way to restore your normal
fan behavior is to just open **AYA Settings → Performance → Fan Settings**
(the native screen from the original conversation's screenshots) and
re-select whatever mode/curve you had before, or tap a stock preset. That
UI is the real, live editor for this exact feature — it will always win
over whatever this throwaway probe last sent.

## Pull results

```bash
adb pull /sdcard/aidl_fan_spike_result.txt results/runN/
adb logcat -d | grep AIDL_FAN_SPIKE > results/runN/aidl_fan_spike_logcat_dump.txt
```
Pull immediately after testing, not later (lesson already learned the hard
way in `autotdp-ab-harness`'s run1). Each on-device test round gets its
own `results/runN/` folder (see `run1`/`run2`/`run3`) so consecutive runs
with a changed `TEST_CURVE` or protocol tweak don't overwrite each other.
Report back regardless of outcome — see `FINDINGS.md` for the full
run1-3 write-up (mode switching confirmed working; curve write confirmed
*not* working as sent, after 6 attempts across runs 2-3).
