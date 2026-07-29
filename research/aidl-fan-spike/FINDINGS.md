# FINDINGS — AIDL fan spike, run1 (2026-07-29)

**Confirmed on-device: `com_set_performance_fan` genuinely drives the real
fan, no root, over the same plain Binder connection `aidl-bind-spike`
already proved for performance-mode.** The discrete-mode half of this
spike (step 1) is a clean, repeatable success — the curve-write half
(step 2, `com_set_fan_speed_strategy`) is inconclusive, not confirmed
either way. Raw log: `results/aidl_fan_spike_result.txt` (+ a redundant
logcat-tag dump, `results/logs`, same content, no extra errors).

## Step 1 — discrete fan mode: confirmed, strong evidence, not just plausible

Two independent lines of evidence agree, not just one:

**1. Real PWM duty changed, tracking the requested mode** (via the
confirmed `pwm-fan` hwmon read-back):
- `FAN_MODE_OFF` → duty **0** (both times sent — perfectly consistent).
- `FAN_MODE_MUTE` → duty **76** (both times sent — perfectly consistent).
- `FAN_MODE_TURBO` / `FAN_MODE_BALANCE` → duty toggled between **76/255**
  across repeats rather than one fixed value each. Read as fan/PWM
  mechanical settling lag against this spike's fixed 1.5s post-send delay
  (RPM visibly still ramping in several reads, e.g. `RPM=2900`→`RPM=7038`
  across consecutive reads of the same mode), not evidence the command
  didn't work — OFF/MUTE's perfect repeatability rules out "read timing is
  just noise" as the explanation for TURBO/BALANCE's variance.

**2. The vendor's own unsolicited state callback confirms it independently
of our sysfs read.** Every `send()` triggers gamewindow to hand back a
full JSON dump of all 5 modes' `ModeConfiguration` (same bonus finding as
`aidl-bind-spike/FINDINGS.md`). `currentMode` was `3` (Gaming) throughout
this run — extracting mode `"3"`'s own `fanMode` field from every single
callback, in order:

```
TURBO, BALANCE, MUTE, OFF, TURBO, BALANCE, TURBO, BALANCE, MUTE, OFF, CUSTOM, BALANCE, TURBO, BALANCE
```

— a **perfect, 1:1, zero-miss match** against the exact sequence of
buttons pressed. This isn't our own code reporting success; it's
`com.ayaneo.gamewindow` itself confirming, unprompted, that the active
profile's fan mode changed to exactly what was requested, every time. As
strong a confirmation as this project has produced for any AIDL command.

## Step 2 — custom curve write (`com_set_fan_speed_strategy`): inconclusive

The mode-switch half of step 2 worked (`fanMode` for mode 3 correctly
shows `CUSTOM` in the callback right after sending `com_set_performance_fan:
FAN_MODE_CUSTOM`). But the follow-up
`com_set_fan_speed_strategy:FAN_MODE_CUSTOM-50,12|65,32|78,68|85,95|95,100`
send did **not** produce a duty reading that's clearly explained by that
curve:

- Immediate post-send read: `duty=0`.
- Manual re-check shortly after: `duty=25` — which is suspicious because
  **25 is the exact same duty value this run's very first baseline read
  showed, before this app ever sent anything.**

Three explanations remain open, not distinguished by this run:
1. The device's actual SoC temp at test time was below our curve's lowest
   point (50°C) and AYA's real curve-application logic doesn't hold flat
   at the first point's duty (12%) the way upstream `pulse`'s own
   `FanCurve.kt` does — it may fall back to some other idle default
   instead. Plausible, not confirmed (temp wasn't logged this run — gap to
   fix in a follow-up).
2. The `mode-pairs` string format guess (`FAN_MODE_CUSTOM-50,12|65,32|...`)
   is subtly wrong and `FanSpeedConfig`'s real parser silently rejected it,
   falling back to whatever the existing saved CUSTOM curve already was.
3. `duty=25` genuinely *is* what the pre-existing default CUSTOM curve
   gives at the room-temperature reading this run happened to be at —
   i.e. the write worked and this is a coincidence, not evidence against
   it.

**Verdict: not proven either way.** Unlike step 1, there's no independent
server-side confirmation available for the curve *contents* specifically
(the callback JSON only ever carries the `fanMode` enum, never the point
list — `aidl-bind-spike/FINDINGS.md` already established the callback
schema doesn't include a curve).

## What this means for `pulse-for-aya`

**Step 1 alone is already a usable, real feature** — a discrete
OFF/MUTE/BALANCE/TURBO fan-mode toggle via this AIDL channel could be
wired into `FanController.kt` today with high confidence, independent of
whether the curve-write mechanism ever pans out. This closes a meaningful
slice of the fan-control gap in
`research/pulse-for-aya/README.md`'s "Feature parity vs upstream" section
by itself, even before the harder PI-controller/curve work.

The curve write needs a better-instrumented follow-up before treating it
as confirmed or ruled out — logging the actual SoC temp at read time (to
test explanation 1) and/or testing an unambiguous curve (e.g. a flat 100%
at every point, impossible to confuse with an idle default) would settle
which of the three explanations above is correct.

## Not yet done

- Temp-aware verification of the curve write (see above).
- Testing `com_set_fan_speed_is_linear` at all (not exercised this run).
- Confirming whether a `com_set_fan_speed_strategy` write is transient or
  persists into the user's saved settings (flagged as a real risk in the
  original ELI5 before this test — worth checking AYA's native Fan
  Settings screen to see whether the CUSTOM curve there still shows the
  user's own values or now shows this test's numbers).
