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
`com_set_fan_speed_strategy:FAN_MODE_CUSTOM-30,100|50,100|70,100|85,100|95,100`
(flat 100% duty at every temp point — unambiguous regardless of actual SoC
temp, since real temps during testing are always above the lowest point).
**Runs 1-3 (see `FINDINGS.md`) confirmed this exact format does not work**
— 6 attempts, 0 with duty anywhere near 255.

**Step 3 — alternate string-format guesses (added for run4+).** Since the
original format is now a confirmed dead end, three more Buttons try
different guesses at the `mode-pairs` string, each still using the same
flat-100% shape so a hit is just as unambiguous:
- **Guess A** — swap pair order to `duty,temp` instead of `temp,duty`.
- **Guess B** — `;` as the pair separator instead of `|`.
- **Guess C** — drop the `FAN_MODE_CUSTOM-` prefix entirely (mode is
  already set by the preceding `com_set_performance_fan` call — maybe the
  prefix itself breaks the parser).

Each guess button does the same mode→CUSTOM, wait, send-strategy,
read-back sequence as Step 2, then **automatically resets to BALANCE
mode** afterward — so leftover duty from one guess can't bleed into the
next guess's reading, and the device is never left mid-experiment even if
you stop after just one tap.

**Beyond the three built-in guesses**, any string can be tried without a
rebuild via an Intent extra — see "Quick test loop" below.

**Every send is followed by an objective read-back**, not just trusting
that the Binder `transact()` didn't throw: `XsuShell` reads the confirmed
`pwm-fan` hwmon node (`fan_rpm_state` + `hwmon*/pwm1`, both readable
without root even — `xsu` is used here only for consistency with the rest
of this repo's probes) 1.5s after each send, plus real SoC temp (max
across all `thermal_zone*` CPU zones). A "Read fan state" button is always
enabled (even before connecting) for manual checks, and one baseline read
fires automatically on launch, before anything is touched.

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

**Recommended order (run4)**: since step 1 and the original step-2 format
are already confirmed (working / not working, respectively), you can go
straight for the new material:
1. Tap **Guess A** (swap order), read the log line, note the duty.
2. Tap **Guess B** (semicolon), read, note the duty.
3. Tap **Guess C** (no prefix), read, note the duty.
4. If none of the three show duty near 255, try a couple of the ad hoc
   strings from "Quick test loop" below (via adb, no rebuild needed) —
   e.g. dropping the `50,` middle points, or trying `is_linear` first.

Every guess already resets to BALANCE on its own — no manual cleanup
needed between taps. All results land in the same log file/session, so
one pull at the end covers everything tried.

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

**Trying an ad hoc string without tapping any button**: pass it as the
`custom_command` extra — the app auto-sends it (through the same
mode→CUSTOM → strategy → read → reset-to-BALANCE flow as the guess
buttons) as soon as it connects, no source edit or reinstall needed:

```bash
adb shell am start -n pl.aidlfanspike.app/.MainActivity \
  --es custom_command "com_set_fan_speed_strategy:FAN_MODE_CUSTOM-30,100:50,100:70,100:85,100:95,100"
adb logcat -c && adb logcat -s AIDL_FAN_SPIKE:D
```

The extra's value is everything that goes after `msg_type_performance:` —
so it can test a completely different command too, e.g.
`--es custom_command "com_set_fan_speed_is_linear:true"` to finally
exercise the third fan command that's never been tried in any run.

If you change the Kotlin source itself (e.g. to hardcode a fourth guess
button, or change `TEST_CURVE`), only then do you need the full
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

For step 2/3, a successful curve write should show duty jump to ~255
(100%) — the flat-everywhere test curve makes this unambiguous regardless
of the device's actual temperature at test time, unlike run1's original
moderate curve.

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
- **Step 1 works but Step 2/3 (`com_set_fan_speed_strategy`) doesn't, for
  every guess tried** — already the confirmed state as of run3 for the
  original format; if Guesses A/B/C in run4 all fail too, that's strong
  evidence the problem isn't the string format at all, and worth
  re-checking `research/ayaspace-teardown/ayasettings_decompiled/sources/
  com/ayaneo/settings/ui/device/fan/FanSpeedConfig.java` directly for
  something structurally different (e.g. maybe the curve needs to be sent
  as a different AIDL message type entirely, not `msg_type_performance`).
- **One of the run4 guesses reports success and the read-back genuinely
  jumps to ~255** — the fan-control path for `pulse-for-aya` is real;
  next step is porting `FanTempController.kt`/`FanCurve.kt`'s actual logic
  to drive this channel (using whichever guess worked) instead of a
  one-shot test curve.

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
