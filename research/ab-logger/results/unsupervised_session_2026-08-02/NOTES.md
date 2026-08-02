# Two-device unsupervised session, 2026-08-02

First session with **AutoTDP and the Custom fan curve both enabled**, on both
units. Build `1.19.6 (303)`, `script_crc32=ebbd831f`, same APK as the previous
three sessions.

**Workloads were not matched and were heavier than any previous pull.** Both
units ran Genshin (`com.miHoYo.Yuanshen`) and Eden. Peak CPU temperature
83.7 C on `B` and 80.5 C on `W`, against 84 / 67 C the day before.

Two things came out of this pull, one good and one bad, and they are
independent of each other.

## 1. The fan ran on "hold target temp", not the curve — and did so correctly

**CORRECTED after the user checked the app.** The first version of this
section concluded the *curve* was configured almost flat. It was not: the Fan
card was on CUSTOM with **"hold target temp"** selected, so the controller in
play was `FanTempController` (a PI loop, `DEFAULT_TARGET_C = 78`), not
`FanCurve`. Evidence: `B/evidence/fan_hold_target_temp_at_floor.txt`.

**The curve has still never been exercised on device**, across every session
in this directory. That remains the open gap.

What the logs show is a PI loop behaving properly. Across 528 decisions on
`B` and 530 on `W`, spanning 38-76 C, output was 20 % — `FanCurve.MIN_PERCENT`,
the floor. Asked to hold 78 C with the chip at 55-67 C, a PI controller
commands its minimum. The one sample that escaped the floor confirms it rather
than contradicting it: `temp=76 applied=20% target=24`, two degrees under
target, output just lifting off the floor. Nothing else in the pull got close
enough to move it.

The plumbing around it is also correct: `arbiter=RunCustomLoop` fires,
`managed=6`, and the idle handoff to vendor Smart works.

**What survives, at its proper weight.** Holding 78 C means that while the
chip is below target, PULSE runs the fan at 20 % (duty 51) while the vendor's
own idle point is duty 76 (30 %). So on this setting the device is quieter and
warmer than with PULSE's fan control off. That is what asking for 78 C means —
a legitimate choice, worth knowing it is being made. Lowering the target or
switching to the curve both change it.

## 2. `com.kei.pulse` was killed six times in twelve minutes on `B`

Evidence: `B/evidence/pulse_process_deaths.txt`. This is why `B`'s pull holds
**five session files** where `W` holds one.

`B` ran one clean 44-minute session (14:58:17 -> 15:42:04), then collapsed:
four more sessions between 15:42 and 15:54, two of which lasted 10 and 68
seconds. Each death is `ActivityManager: Process com.kei.pulse ... has died:
prcp FGS`, and each is followed by ActivityManager restarting the foreground
services, which starts a fresh daemon session.

**No cause is attributable from any log.** There is no `FATAL EXCEPTION` and
no native `F libc` crash for `com.kei.pulse` anywhere in the full `logcat -b
all` dump; no `lowmemorykiller` line; no kernel OOM in `dmesg`. `prcp FGS`
describes the process *state* at death, not the killer.

The Phantom Process Killer is active and demonstrably reaches PULSE: 30
`PhantomProcessRecord` deaths, of which several are `xsu` children, and one
(`20883:4357:xsu/u0a183`) is parented to `com.kei.pulse` itself. **This is
suggestive but not established** — most reaps are parented to a uid-1000
process, and several app deaths have no nearby reap. Recorded as the leading
hypothesis, not a conclusion.

Note what this is *not*: the device did not reboot. All five of `B`'s
filtered logcats replay the same boot-time aborts from ~14:57, so this is one
boot throughout. That distinguishes it from the earlier INCIDENT entries.

`W` did not do this once in 1 h 35 m under the same build and a comparable
load.

## Evening follow-up on `B`: revised curve, and why it still proves nothing

Added after the main pull. `B/pulse_20260802_201343*`, 20:13:44 -> 20:32:01
(~18 min), Eden on a fixed **Balanced** tier with **AutoTDP off** (Eden
regulates badly, see the open thread), Custom fan with the curve revised to
ramp above ~60 C. Reported subjectively as near-silent at a near-steady
60 fps. Evidence: `B/evidence/fan_sensor_lag_and_vendor_fight.txt`.

**Stability: clean.** One `session start`, no `com.kei.pulse` kills at all in
the manual dump, against 41 Phantom Process reaps in the same window. That
last number is worth noting — it *weakens* the phantom-killer hypothesis for
the afternoon collapse, since 41 reaps here produced zero deaths. The
`clean session end: NO` flag is just the pull cutting a live session. Caveat:
18 minutes is short, and the afternoon's collapse only began after 44.

**Still on "hold target temp", so still no curve data.** All 180 decisions
returned 20 %, for the same correct reason as above: the target is 78 C and
the chip never got there.

**The sensors agree — an earlier claim here was wrong.** This section first
reported that `cap_poll` peaked at 78.7 C while the fan loop read 58-63 C, and
concluded the fan controller reads a different, useless sensor. That is not
what happened. Both sides resolve the same thermal zone by the same rule
(first `thermal_zoneN` whose `type` contains "cpu"; `pulse_daemon.sh` says so
in a comment deliberately matched to `SystemTuning.kt#resolveZones()`). The
app side applies an EMA (0.6 old + 0.4 new); `cap_poll` does not.

Matching the two logs on identical timestamps across the session, 140 pairs:
**median difference -0.6 C**, p10/p90 -1.5/+0.4 C, and exactly **one pair out
of 140** differs by more than 5 C. At steady state the fan controller's
temperature *is* the raw sysfs temperature. The 15 C gap existed only during a
five-second ramp — which is what an EMA is for.

**PULSE did undo the vendor's ramp, and that part stands.** At 20:15:55 the
vendor went to duty 163 (~64 %); three seconds later PULSE reasserted duty 51.
That is PULSE winning arbitration as designed (`FAN_RECHECK_MS = 120`). But it
is not a blind controller: at 1 Hz with alpha 0.4 the smoothed value converges
on a sustained change in roughly ten seconds. It ignores five-second spikes
and responds to real heat, which for a fan is the wanted behaviour.

**The earlier conclusion — "check which sensor `FanTempController` reads
before tuning the curve" — is withdrawn.** It reads the right one.

**Fallback: cap writes 15/98 (15.3 %)**, the highest rate recorded, though on
a small sample. Reads 180/1338 (13.5 %).

## Regulation and fallback

| | `B` (5 sessions) | `W` (1 long session) |
|---|---|---|
| span | 14:58:17 -> 16:19:38, fragmented | 14:42:17 -> 16:17:43, continuous |
| peak CPU temp | 83.7 C | 80.5 C |
| regulation | 2 TRIM / 5 RAISE / 6 HOLD | 46 TRIM / **0 RAISE** / 220 HOLD |
| cap-write fallback | 25 / 1230 (2.0 %) | 93 / 1878 (5.0 %) |
| **telemetry-read fallback** | **159 / 2190 (7.3 %)** | **468 / 2690 (17.4 %)** |

Two things to carry forward:

- **`W` logged 0 RAISE against 46 TRIM.** AutoTDP trimmed and then never gave
  anything back across a 95-minute session. That is the same one-way
  behaviour as the Stardew case on 2026-08-01, now seen under a heavy 3D load
  rather than a vsync-locked 2D one, which weakens the "vsync-only" reading
  of that finding. Worth a proper look before the week-long run.
- **Read fallback is far higher than write fallback** and has never been
  tracked separately before. 17.4 % of telemetry reads on `W` went through
  raw `xsu` rather than the daemon FIFO. Since raw `xsu` is the historical
  prime suspect for instability, and `B` is the unit that died six times,
  this is worth watching — though note `B`'s read fallback was the *lower*
  of the two, which argues against a simple link.

## Not findings

- **Eden's `FATAL EXCEPTION` on `W`** (15:29:15) is Eden failing its own
  initialisation, `lateinit property emulationState has not been
  initialized`. Same self-inflicted class already recorded on 2026-07-31.
  Excerpt in `W/evidence/eden_fatal_exception.txt`.
- **`fan_duty=0` for the last five minutes of `B`'s final session** looks
  alarming and is not. It runs 16:14:35 -> 16:19:38 at 41-54 C with rpm
  decaying to zero and the caps fully released; PULSE never commanded 0
  (`applied` is never below 20 %). This is the vendor idling the fan after
  PULSE stood down.
- **The usual dmesg and logcat crash-keyword hits** were all the known false
  positives: driver names containing "panic", and `init`/`nvkeeper`/`qcrosvm`
  boot aborts replayed from the ring buffer, every one pre-dating its
  session.
- **`W`'s 21-second first session** (14:41:55 -> 14:42:16) ended cleanly and
  is just a false start before the real one.

## Capture problems

- **`W/pulse_fan.log` came back 0 bytes again** — second session running for
  the `adb logcat -s PulseFan:D` capture. It did not matter this time, because
  the app now writes `PulseFan` lines into `pulse_<ts>.log` itself, which is
  where all the fan analysis above came from. **The live capture step in
  `PULL_AND_TRIM.md` is redundant and should be dropped** rather than fixed.

## What was kept

Per unit: the app log and the `cap_poll` ground-truth poll, plus the three
evidence excerpts. 37 MB in, 4.3 MB out.

Deleted: all `_dmesg.log` and filtered `_logcat.log` (no findings beyond the
excerpts), the auto-generated `SUMMARY.md`, the empty `pulse_fan.log`, and
**both manual `logcat -b all` dumps, which carried 151 (`B`) and 156 (`W`)
matches for network names, hardware addresses and account e-mails**. Those
are never committed; the lines worth keeping were extracted into `evidence/`
first.
