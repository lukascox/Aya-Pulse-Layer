# Unsupervised session, 2026-08-03 -- both units, Custom fan curve on both

Two devices, both left to run without supervision, both with fan control set
to **Custom / curve** (not "hold target temp" -- that mix-up is what cost the
2026-08-02 session its fan data).

**Workloads are comparable this time**, which is unusual and worth saying up
front: both units spent essentially the whole session in Genshin Impact
(`com.miHoYo.Yuanshen`) -- B 3058 of 3502 sampled ticks, W 1948 of 4178. W
also passed through `com.retrohrai.launcher`. So the two temperature curves
below can be compared directly.

| | B | W |
|---|---|---|
| session files | 4 (3 are startup churn, see below) | 2 |
| useful span | 14:50:53 -> 17:30:03 (2h39m) | 14:50:06 -> 17:28:12, split at 15:39 |
| chip temp p50 / max | 55 / 78 °C | 77 / 83 °C |
| fan duty commanded | 20-30 % | 20-45 % |
| `com.kei.pulse` kills | 7, all inside 78 s at start | 0 |
| read fallback to raw `xsu` | 2233/19548 (11.4 %) | 424/7444 (5.7 %) |
| write fallback to raw `xsu` | 182/1324 (13.7 %) | 16/290 (5.5 %) |

---

## 1. The fan curve finally ran, and it tracks

This is the first session in five that produced real curve data. Both units
were on `arbiter=RunCustomLoop` for the whole useful span (B: 3466/3466 ticks;
W: 1917/1925).

W is the interesting one because it actually got hot. Averaged in ten-minute
buckets over its second session:

```
15:3  temp_avg=61  applied_avg=20  n=28
15:4  temp_avg=72  applied_avg=29  n=393
15:5  temp_avg=76  applied_avg=36  n=342
16:0  temp_avg=76  applied_avg=35  n=402
16:1  temp_avg=78  applied_avg=38  n=388
16:2  temp_avg=75  applied_avg=33  n=272
16:3  temp_avg=57  applied_avg=20  n=19
```

Duty follows temperature up and back down, and settles into an equilibrium at
roughly 76-78 °C with the fan at 35-38 %. Nothing runs away, nothing
oscillates, and the floor (20 %) and the top of the observed range (45 %) are
both reached. The auto-calibrated curve behaves.

B looks pinned at 27 % only because B never got hot -- p50 55 °C, and the
curve's answer at 55 °C is about 27 %. Same controller, different input.

**`applied` tracks `target` closely.** Out of B's 3165 samples the top three
buckets are `applied=27 target=27` (2168), `applied=26 target=26` (427),
`applied=28 target=28` (227); the mismatched pairs are all one step apart, in
transit. The slew limiter is doing its job and is not a bottleneck.

**Open question about the equilibrium.** Holding 76-78 °C for two and a half
hours with the fan at ~36 % is stable but warm. The curve says 50 % at 80 °C,
so it never had to commit. Whether to pull the mid-curve up is a comfort
judgement, not a correctness one -- there is no throttling in the logs.

## 2. B's process deaths have a named suspect now: the Sleep service

B was killed 7 times, all between 14:49:34 and 14:50:52 -- 78 seconds -- and
then ran 2h39m without a single further death. Yesterday's six kills on the
same unit looked random; this session says they are not.

**Every one of the 8 `Start proc` lines for `com.kei.pulse` in the whole dump
names the same trigger: `com.kei.pulse.sleep.SleepProfileMonitorService`.**
The restart loop is 36 reschedules of that service and 38 of
`ForegroundAppMonitorService` dragged along with it, and Android complains
three times that the sleep service is a foreground service started from the
background -- which is exactly the condition `prcp FGS` describes.

W, same day, same app build, same game, has **zero** of all of it: no deaths,
no reschedules, and the string `SleepProfileMonitorService` does not appear in
its dump at all. The difference between the units is that **Sleep is enabled
on B and disabled on W**.

The 2026-08-02 evidence file for B carries the same two service names
(`unsupervised_session_2026-08-02/B/evidence/pulse_process_deaths.txt`), so
this is two independent sessions on the unit with Sleep on, and two clean
sessions on the unit with it off.

This is a hypothesis with a clean test, not a proven cause: turn Sleep off on
B and see whether the startup kills disappear. The Phantom Process Killer idea
from 2026-08-02 is now the weaker of the two -- it never explained why only
one unit was ever affected.

Evidence: [`B/evidence/pulse_deaths_sleep_service.txt`](B/evidence/pulse_deaths_sleep_service.txt).

The three short session files on B (`144823`, `144937`, `145051`) are the
debris of this loop, not separate sessions. `145051` is one line long.

## 3. A per-app fan mode silently beats the global Custom card

W's first session logged 45 `PulseFan` lines in 49 minutes against 1852 in the
second, and produced no curve data. The cause is in the arbiter:
`FanArbiter.decide()` resolves `boundFanMode ?: managedFanMode`, so a
foreground app's per-app fan setting wins over the global Fan card. During
that window `bound` was 1 (SILENT) and 4 (SMART), the live mode already
matched, and `decide()` correctly returned `None` -- no write, no curve, and
nothing in the UI saying so, because the Fan card still read Custom.

This is a procedure trap more than a bug, but it is invisible: a session can
be set up correctly at the global level and still yield nothing.

Evidence: [`W/evidence/per_app_fan_override.txt`](W/evidence/per_app_fan_override.txt).

## 4. AutoTDP did almost nothing, and that is expected

B: 1 TRIM, 0 RAISE, 2 HOLD across 2h39m. W: 0/0/0 in both sessions, with
AutoTDP never engaging. Every `PulseFan` line on both units reads
`autoTdp=false`. So this session says nothing new about the 0-RAISE asymmetry
noted on 2026-08-02 -- it did not exercise AutoTDP at all.

## 5. Genshin crashed once on W, and it is not ours

`08-03 15:04:04` -- `SIGABRT` in `Thread-10` of `com.miHoYo.Yuanshen`, 841 s
into the process. At that moment `cap_poll` shows `cpu_temp_mc=57200`,
`gpu_temp_mc=49700`, `fan_duty=79`, `fan_rpm=2958`: cool, well-ventilated, and
PULSE was not driving the fan at all (this is inside the `arbiter=None` window
from section 3). Single occurrence, no repeat in the second session. Recorded
and dropped.

## 6. Fallback rates

B's read fallback is up (11.4 % vs W's 5.7 %), and for the first time its
**write** fallback is comparably high (13.7 %). B is also the unit with the
restart loop, so the two may share a cause -- a freshly restarted daemon has
no FIFO yet. Not investigated further this session; worth watching whether it
drops once Sleep is off.

---

## Flags that were false positives

- `dmesg` crash hits (1 on B, 3 on W): driver names containing "panic"
  (`gh_panic_notifier`) and `sde_dbg_init … panic:1`, plus a module list.
  Boot-time, every session.
- `logcat` crash hits (14 on B, 16 on W): `init` / `nvkeeper` / `qcrosvm`
  aborts. All pre-date the session start; they are the ring buffer replayed
  when the filter attached. Both units show the identical set. The `qcrosvm`
  one aborts with `set fw name ioctl failed … Bad file descriptor` -- vendor
  boot noise.
- `clean session end: NO` on all six sessions: the pull cut them live.
- The only logcat hit that was **not** noise is the Genshin abort, section 5.

## What was deleted

`pulse_*_dmesg.log`, `pulse_*_logcat.log`, the auto-generated `SUMMARY.md`,
and both manual `new_Session_logcat.log` dumps (per procedure, manual full
logcats are never committed -- verified lines were extracted into `evidence/`
first). 59 MB in, 8.7 MB out. The redaction sweep from `PULL_AND_TRIM.md` ran
clean over what remains, with no matches at all.

## For the next session

1. **Turn Sleep off on B** and repeat. This is the one test that would settle
   section 2, and it costs nothing.
2. **Check the per-app fan setting**, not just the Fan card, before starting.
3. If the fan comfort question in section 1 matters, raise the 65-80 °C part
   of the curve and compare the equilibrium.
