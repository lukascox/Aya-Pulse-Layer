# Two-device unsupervised session, 2026-08-01

Both units, ~2h10m, started within two minutes of each other
(`B` 14:01:00→16:12:15, `W` 14:02:26→16:16:32). Fully unsupervised: PULSE ran
in the background, nobody watched anything live. Build `1.19.6 (303)`, the
same shipping APK on both.

**Read this before comparing the two columns: the workloads were not
matched.** `B` was on low battery and spent roughly 50 minutes mid-session on
the charger, idle, screen off. `W` played throughout. Both ran Minecraft; `W`
additionally touched Dolphin, RetroArch, Eden and Chrome briefly. So the
performance numbers below describe two different real sessions, not a
controlled A/B.

## Confounder eliminated: vendor software versions are identical

Checked explicitly because a version skew would have invalidated every
comparison from here on. `dumpsys package` on both units:

| package | system | updated |
|---|---|---|
| `com.ayaneo.settings` | 1.1.100 | **1.1.112** |
| `com.ayaneo.gamewindow` | 1.5.78 | **1.5.84** |

Same on `B` and `W`. The dumps themselves were not kept (80 KB each of
mostly-irrelevant permission tables); this table is the finding.

## Headline numbers

| | `B` | `W` |
|---|---|---|
| mean fps | 78.2 | 89.1 |
| mean CPU temp | **75.2 °C** (max 84) | **46.8 °C** (max 67) |
| mean draw | **9.99 W** (max 16.68) | **4.56 W** (max 12.83) |
| regulation | 296 TRIM / 312 RAISE / **73 HOLD** | 112 TRIM / 69 RAISE / **982 HOLD** |
| `xsu` fallback | 180 / 2143 (**7.7 %**) | 71 / 3278 (**2.1 %**) |
| AutoTDP sessions | 47 | 79 |

The regulation split is the interesting column. `B` spent the session
oscillating — 608 cap changes against 73 holds — which is what AutoTDP does
when it is pinned against its thermal and power ceiling and cannot reach the
90 fps target. `W`, on a lighter load, settled: 982 holds, and it sat at
`caps%[0:35,2:55,5:35,7:35,-100:40]` holding a flat 90.0 fps at 11 ms tail.
That is AutoTDP working exactly as intended — find the cheapest settings that
still hit target, then stop touching anything.

Given the unmatched workloads this says nothing yet about the open "`W` feels
slower" question. It does show both units regulate correctly.

## Nothing broke

- **`dmesg` crash-keyword hits: the usual two boot-time false positives on
  each** (driver names containing "panic"). Nothing else on `B`.
- **Every filtered-logcat hit pre-dates its session start** — the same
  `init`/`nvkeeper`/`qcrosvm` boot aborts the logcat filter replays on attach.
- **The daemon never restarted on either unit** (`session start` appears once
  per log).

### `B`'s 51-minute log silence is the charger, not a failure

`B`'s app log jumps 15:01:28 → 15:52:50 with no elision marker, i.e. genuinely
no output. Throughout that window `cap_poll` keeps running at ~1 Hz, caps are
**fully released** (`p0_max=2265600`, `gov=schedutil`), `batt_online=1`, temps
42-48 °C and the fan is at its idle duty. That is the charger + screen-off
case, and it matches the signature already documented on `B` for a 3h15m
suspend on 2026-07-31. Expected, not an incident.

### Minecraft's SIGSEGV on `B` happened while PULSE was idle

`08-01 15:15:17` — `Fatal signal 11 (SIGSEGV) in tid 3018 (MINECRAFT MAIN),
pid 2670 (ang.minecraftpe)`. That timestamp falls inside the silence window
above, when AutoTDP was not running and the caps were released. PULSE was
demonstrably not regulating anything when the game died.

### One kernel WARNING on `W`, unexplained, benign

Once, at ~14:18:31 local: a scheduler assertion inside Qualcomm's WALT
(`rq->balance_callback && rq->balance_callback != &balance_push_callback`,
`android_rvh_try_to_wake_up`). Excerpt kept in
`W/evidence/kernel_walt_warning.txt`.

A `WARN_ON`, not an oops — the device ran another two hours without trouble.
Checked against PULSE's core parking, since offlining CPUs is the one thing it
does that could plausibly upset a scheduler: **parking does not line up**.
`prk=1` ran 14:17:04→14:17:40, ending ~50 s before the warning. Worth noting
that `W` parked 22 times to `B`'s 4, and only `W` produced the warning — but
that is a weak association, not a link. Not seen on `B`.

## Fan: both units on SMART, so nothing to learn here

Four `PulseFan` lines each, all `arbiter=None`, `managed=4` (SMART) — correct
and uneventful (`FanArbiter` returns `None` both when AutoTDP owns a
non-Custom mode and when the vendor mode has not drifted). Custom is
deliberately not being used: this fan has bad coil whine at its working point.

Duty distributions differ with the load, as expected: `B` sat mostly at 76
(the vendor idle point) with excursions to 124-137, `W` hovered 81-91.

**Third session in a row with no fan-curve data.** Nothing about the Custom
curve can be concluded from any of these pulls.

## Capture problems worth fixing next time

- **`B/fan_test.log` came back 0 bytes** — the `adb logcat -s PulseFan:D`
  capture produced nothing at all. Not kept.
- **Neither `ayasettings_run.log` recorded a UI launch.** On `W`,
  `com.ayaneo.settings` only ever appears being started for a
  `RescheduleReceiver` broadcast, never `Displayed`. So the AYA Settings
  question from 2026-07-31 is still open and still has no evidence either way.

## What was kept

Per unit: the app log and the `cap_poll` ground-truth poll. Everything else
deleted after review — `_dmesg.log` and the filtered `_logcat.log` (23 MB
combined, no findings), the auto-generated `SUMMARY.md`, and the `dumpsys`
package dumps.

**The two manual full-logcat dumps per unit (`logcat.log`,
`ayasettings_run.log`, ~11 MB) were deleted and must never be committed.**
They carried hundreds of matches for the home network name, hardware
addresses and a personal e-mail. `dmesg` masks that class of data; `logcat`
does not.
