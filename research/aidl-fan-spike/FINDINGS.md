# FINDINGS — AIDL fan spike, runs 1-3 (2026-07-29 / 2026-07-30)

**Confirmed on-device: `com_set_performance_fan` genuinely drives the real
fan, no root, over the same plain Binder connection `aidl-bind-spike`
already proved for performance-mode.** The discrete-mode half of this
spike (step 1) is a clean, repeatable success. The curve-write half
(step 2, `com_set_fan_speed_strategy`) is now a **strong negative
result** after three runs — the command does not appear to actually
apply the curve content sent. Raw logs: `results/run1/`, `results/run2/`,
`results/run3/` (each with `aidl_fan_spike_result.txt` +
`aidl_fan_spike_logcat_dump.txt`, redundant content, no errors in any of
the three).

## Step 1 — discrete fan mode: confirmed, strong evidence, not just plausible

Two independent lines of evidence agree, not just one:

**1. Real PWM duty changed, tracking the requested mode** (via the
confirmed `pwm-fan` hwmon read-back):
- `FAN_MODE_OFF` → duty **0** (run1, both times sent — perfectly consistent).
- `FAN_MODE_MUTE` → duty **76** (all runs, every time sent — perfectly
  consistent).
- `FAN_MODE_TURBO` / `FAN_MODE_BALANCE` → duty noisier across runs (toggles
  between values rather than one fixed value each — see run3's TURBO read
  showing duty=25 right after a CUSTOM-mode send, most likely still
  settling). Read as fan/PWM mechanical settling lag against this spike's
  fixed 1.5s post-send delay (RPM visibly still ramping in several reads),
  not evidence the command didn't work — OFF/MUTE's perfect repeatability
  across all three runs rules out "read timing is just noise" as a general
  explanation.

**2. The vendor's own unsolicited state callback confirms it independently
of our sysfs read.** Every `send()` triggers gamewindow to hand back a
full JSON dump of all 5 modes' `ModeConfiguration` (same bonus finding as
`aidl-bind-spike/FINDINGS.md`). `currentMode` was `3` (Gaming) throughout
run1 — extracting mode `"3"`'s own `fanMode` field from every single
callback in run1, in order:

```
TURBO, BALANCE, MUTE, OFF, TURBO, BALANCE, TURBO, BALANCE, MUTE, OFF, CUSTOM, BALANCE, TURBO, BALANCE
```

— a **perfect, 1:1, zero-miss match** against the exact sequence of
buttons pressed. This isn't our own code reporting success; it's
`com.ayaneo.gamewindow` itself confirming, unprompted, that the active
profile's fan mode changed to exactly what was requested, every time.
Runs 2 and 3 show the same pattern on spot-check (every mode send's
callback `fanMode` matches what was sent). As strong a confirmation as
this project has produced for any AIDL command.

## Step 2 — custom curve write (`com_set_fan_speed_strategy`): strong negative after 3 runs

The mode-switch half of step 2 reliably works in every run (`fanMode` for
mode 3 correctly shows `CUSTOM` in the callback right after sending
`com_set_performance_fan:FAN_MODE_CUSTOM`). The curve-*content* write does
not show any evidence of taking effect.

### Run1 (moderate curve, temp not logged) — inconclusive

`com_set_fan_speed_strategy:FAN_MODE_CUSTOM-50,12|65,32|78,68|85,95|95,100`
produced `duty=0` immediately, `duty=25` on manual re-check — `25` being
suspiciously identical to run1's own pre-test baseline duty. No SoC temp
was logged this run, so "the SoC was below the curve's lowest point
(50°C)" couldn't be ruled out. Verdict at the time: not proven either way.

### Run2 + Run3 (flat 100%-everywhere curve, temp logged every read) — strong negative

Runs 2 and 3 switched to a deliberately unambiguous test curve —
`30,100|50,100|70,100|85,100|95,100`, i.e. **100% duty at every defined
temperature point, including the lowest (30°C)** — and added real SoC
temp logging (max across all `thermal_zone*` CPU zones) to every read, so
"was the SoC too cold" is no longer a blind spot.

Every curve-write attempt across both runs, with the CPU temp logged at
that exact read:

| Run | Attempt | CPU temp (max) | Duty read back |
|---|---|---|---|
| run2 | 1 | 39.6°C | 76 |
| run2 | 2 | 38.0°C | 25 |
| run3 | 1 | 42.2°C | 76 |
| run3 | 2 | 42.2°C | 25 |
| run3 | 3 | 39.5°C | 25 |
| run3 | 4 | 40.3°C | 76 |

**0 of 6 attempts produced anything near duty=255**, despite every single
read showing CPU temp comfortably above the curve's lowest point (30°C) —
if the curve had been applied, every one of these reads should show
duty≈255, unconditionally. Instead, results only ever land on **25 or
76** — the same two values already established elsewhere in these runs as
belonging to *other* states (76 = confirmed exact `FAN_MODE_MUTE` duty;
25 = this session's pre-test idle value) — and show no correlation with
the (mildly rising, 38→48°C) temp trend across the runs, which a real
temperature-responsive curve (ours or a pre-existing saved one) should
show. Most consistent explanation: these are settling-lag leftovers from
whichever discrete-mode command preceded the curve send, not a curve
being evaluated at all.

**Verdict: `com_set_fan_speed_strategy`, as constructed here, does not
appear to change real fan behavior.** This doesn't yet prove the *reason*
(malformed string format vs. a code path that isn't wired to PWM output
on this firmware vs. something else) — see "Not yet done" — but the
"we just happened to test at the wrong temperature" explanation (run1's
open hypothesis #1) is now firmly ruled out by runs 2-3, and "coincidence
with a legitimately temp-driven pre-existing curve" (hypothesis #3) is
much weaker too, since 6 attempts across a real temp range never showed
temp-correlated variation.

## What this means for `pulse-for-aya`

**Step 1 alone is already a usable, real feature** — a discrete
OFF/MUTE/BALANCE/TURBO fan-mode toggle via this AIDL channel could be
wired into `FanController.kt` today with high confidence, independent of
whether the curve-write mechanism ever pans out. This closes a meaningful
slice of the fan-control gap in
`research/pulse-for-aya/README.md`'s "Feature parity vs upstream" section
by itself.

**The curve write (needed for a real PI-controller/spline-curve port) is
not currently usable.** Porting `FanTempController.kt`/`FanCurve.kt`'s
actual temperature-responsive logic into `pulse-for-aya` via this AIDL
channel isn't viable until `com_set_fan_speed_strategy` either starts
showing an effect or a different invocation (different string format,
different command name entirely) is found.

## Not yet done

- Root-causing *why* the curve write doesn't take effect: try a different
  string format (e.g. swap `duty,temp` order, try `;` or `,` as the
  pair-list separator instead of `|`, or drop the `FAN_MODE_CUSTOM-`
  prefix in case the mode is implied by the preceding
  `com_set_performance_fan` call and the prefix itself breaks parsing).
- Testing `com_set_fan_speed_is_linear` at all (not exercised in any run).
- Re-checking `FanSpeedConfig.java`'s exact serialization
  (`research/ayaspace-teardown/ayasettings_decompiled/sources/com/ayaneo/
  settings/ui/device/fan/FanSpeedConfig.java`) against what run2/3 sent,
  in case the point-list format was misread the first time.
- Confirming whether a `com_set_fan_speed_strategy` write is transient or
  persists into the user's saved settings (flagged as a real risk in the
  original ELI5 before run1 — worth checking AYA's native Fan Settings
  screen to see whether the CUSTOM curve there still shows the user's own
  values, now that we know the write likely isn't landing anyway).
