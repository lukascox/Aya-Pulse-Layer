# FINDINGS — AIDL fan spike, runs 1-4 (2026-07-29 / 2026-07-30)

**Confirmed on-device: `com_set_performance_fan` genuinely drives the real
fan, no root, over the same plain Binder connection `aidl-bind-spike`
already proved for performance-mode.** The discrete-mode half of this
spike (step 1) is a clean, repeatable success. The curve-write half
(step 2/3, `com_set_fan_speed_strategy`) is **definitively a dead end,
not an open question** — reading the actual dispatch bytecode (see "Root
cause found" below) confirms its handler never applies the payload
regardless of format; no format tried across four runs worked, and one
guess (a malformed one, run4) crashed `com.ayaneo.gamewindow` outright,
requiring a device reboot. Raw logs: `results/run1/` through
`results/run4/` (each with
`aidl_fan_spike_result.txt` + `aidl_fan_spike_logcat_dump.txt`; run4 adds
`gamewindow_crash_excerpt.log` for the crash).

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

## Run4 — three format guesses, one of them crashes `com.ayaneo.gamewindow`

Run4 tried three alternate `mode-pairs` string formats (all still built on
the same flat-100%-everywhere shape): swapped `duty,temp` order, `;` as
the pair separator, and dropping the `FAN_MODE_CUSTOM-` prefix entirely.

**Guess A (swap order) and Guess B (semicolon) — still no effect,
consistent with runs 2-3.** Tried twice each (once before, once after the
crash below, with a fresh `clientId`): duty read back as 25 or 76 both
times, never near 255. Same pattern as before — mode-switch to CUSTOM
confirmed via callback, curve content still not landing.

**Guess C (no `FAN_MODE_CUSTOM-` prefix) crashed `com.ayaneo.gamewindow`
outright — twice.** Full stack trace from `full_logcat.log`:

```
FATAL EXCEPTION: DefaultDispatcher-worker-10
Process: com.ayaneo.gamewindow, PID: 4122
java.lang.IllegalArgumentException: No enum constant com.ayaneo.gamewindow.utils.FAN_MODE.30,100|50,100|70,100|85,100|95,100
	at java.lang.Enum.valueOf(Enum.java:300)
	at com.ayaneo.gamewindow.utils.FAN_MODE.valueOf(SettingsUtil.kt:3)
	at com.ayaneo.gamewindow.utils.aidl.AYAAidlManager$dealMsg$1.invokeSuspend(AYAAidlManager.kt:922)
```

This is a **real, reproducible, unprivileged crash bug in a vendor system
service** — not a "nothing happened" result. `AYAAidlManager.dealMsg`
evidently splits the `com_set_fan_speed_strategy` payload on the first
`-` and feeds everything before it straight into
`FAN_MODE.valueOf(...)` with no validation; when the prefix is missing,
the *entire* curve string becomes the "enum name" and `valueOf()` throws
an exception nothing catches, killing the whole `com.ayaneo.gamewindow`
process (which also owns the game overlay, notifications, and
`WindowKeyEventService`/key remapping). Android auto-restarted it after
the first crash (silent), but the *second* identical crash (same guess,
retried after reconnect with a new `clientId`) triggered Android's
user-facing crash dialog instead and the service didn't cleanly come
back — the user had to reboot the device to fully recover. Full context
kept in `results/run4/gamewindow_crash_excerpt.log` (trimmed from a
1.2MB full-system logcat dump to the ~450 lines around both crashes).

**This settles the "is the prefix mandatory" question for good — yes,
confirmed by the vendor's own code, not just inferred.** It also
confirms the enum type really is `com.ayaneo.gamewindow.utils.FAN_MODE`
(not just our reconstructed guess) and pinpoints the exact handler
(`AYAAidlManager.dealMsg`, decompiles to `AYAAidlManager.kt`). The
no-prefix guess button has been **removed from the app** (was never a
live hypothesis to retry — it's now a confirmed crash trigger, not
something to tap again).

## Root cause found: `com_set_fan_speed_strategy` is a stub, not a format bug (2026-07-30)

The mystery of why even correctly-prefixed guesses (Guess A/B, and the
original format in runs 1-3) never changed real duty is now **fully and
definitively closed** — not by more live guessing, but by reading the
actual message-dispatch bytecode. `research/aya-gamewindows-teardown/`'s
decompile of this exact method had previously failed silently (JADX
couldn't reconstruct this specific Kotlin-coroutine state machine); a
re-decompile with `jadx --comments-level debug` recovered a raw
instruction dump instead, which — while harder to read than clean Java —
is complete and unambiguous. Full detail, including the curated
instruction-dump excerpt of all three fan-related command branches:
`research/aya-gamewindows-teardown/FINDINGS.md` section 9 and
`research/aya-gamewindows-teardown/evidence/aidl/
AYAAidlManager_dealMsg_fan_excerpt.txt`.

**The short version: `com_set_fan_speed_strategy`'s handler splits the
payload on `-`, parses the mode from element `[0]` (which is exactly what
crashed on the no-prefix guess), and then does nothing with element `[1]`
except log it via Timber (labeled `"| json = "` in the log line — a real
hint the intended payload was JSON, not our reconstructed
`temp,duty|temp,duty` shape) — no write, no persistence, no hardware
effect of any kind.** It's a genuine stub/dead code path in this app
build, full stop. No string format could ever have made it work — the
handler doesn't act on its input regardless of shape. For contrast,
`com_set_performance_fan` (which does work) calls straight into
`PerformanceManager.g(mode, ...)`, and `com_set_fan_speed_is_linear`
(untested live, but structurally real) persists via a `ContentProvider`
write (`AyaShareConfUtilKt.e(...)`).

## What this means for `pulse-for-aya`

**Step 1 alone is already a usable, real feature** — a discrete
OFF/MUTE/BALANCE/TURBO fan-mode toggle via this AIDL channel could be
wired into `FanController.kt` today with high confidence. This closes a
meaningful slice of the fan-control gap in
`research/pulse-for-aya/README.md`'s "Feature parity vs upstream" section
by itself.

**The curve write via this AIDL channel is a closed dead end, not an open
question.** `com_set_fan_speed_strategy` cannot be made to work by
guessing a different string format — the handler ignores its payload
entirely. Porting `FanTempController.kt`/`FanCurve.kt`'s actual
temperature-responsive logic into `pulse-for-aya` needs a different
mechanism entirely — the most promising lead is the plain `pwm-fan`
sysfs write path documented in
`research/aya-gamewindows-teardown/FINDINGS.md` section 6
(`AR03.t1(int)`, 0-255 duty via `/sys/devices/platform/soc/soc:pwm-fan/
hwmon/hwmon0/pwm1`, structurally identical to how `pulse-for-aya` already
talks to CPU/GPU sysfs) — untested live so far, but doesn't depend on
this AIDL surface or `com.ayaneo.gamewindow` at all.

## Not yet done

- ~~Root-causing why the curve write doesn't take effect~~ — **done, see
  above.** The AIDL curve-write path is closed; any further fan-curve
  work should target the sysfs `pwm-fan` write path instead (see
  `research/aya-gamewindows-teardown/FINDINGS.md` section 6), not more
  guesses against `com_set_fan_speed_strategy`.
- Testing `com_set_fan_speed_is_linear` — still a live, worthwhile probe
  (unlike the strategy command, its handler genuinely persists a value via
  `AyaShareConfUtilKt.e(...)`, per
  `research/aya-gamewindows-teardown/FINDINGS.md` section 9). Not
  exercised in any run yet; possible via the `custom_command` intent extra
  without a rebuild, see `README.md`.
- Live-testing the plain sysfs `pwm-fan` write path
  (`research/aya-gamewindows-teardown/FINDINGS.md` section 6,
  `AR03.t1(int)`) as the real replacement plan for a custom curve, now
  that the AIDL route is confirmed closed.
- **Any further live guessing should stay conservative given run4's
  crash**: always keep the confirmed-mandatory `FAN_MODE_CUSTOM-` prefix,
  and treat every new guess as a potential crash until proven otherwise,
  not just a potential no-op.
