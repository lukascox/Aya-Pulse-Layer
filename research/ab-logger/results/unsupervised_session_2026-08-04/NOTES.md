# Unsupervised session, 2026-08-04 — the Sleep hypothesis holds, and the gate is invisible

First session on the **gated debug build** (`1.19.6-aya.1`, versionCode 30301), and the first with
**Sleep deliberately off on both units** — the one-setting test the 2026-08-03 notes asked for.

Setup was deliberate this time and it shows in the logs: app killed, reinstalled, device rebooted,
PULSE opened and checked that it saw the CPU clusters, then handed over. The short session files
(`B/154600`, `W/153610`, `W/153737`, 10-45 seconds each) are that procedure, not failures.

| | B | W |
|---|---|---|
| real session | 15:47:14 → 17:40:38 (1h53m) | 15:39:07 → 17:41:49 (2h02m) |
| workload | Eden (reports itself as `com.miHoYo.Yuanshen`) | Eden **+ Artemis** (`com.limelight.noir`, PC streaming) |
| `com.kei.pulse` kills | **0** | **0** |
| Sleep | off | off |
| chip temp p50 / max | 54 / 60 °C | 51 / 74 °C |
| fan duty | 22-27 % | mostly 20 %, up to 31 % |
| AutoTDP | engaged, 44 decisions | never engaged |
| read fallback to raw `xsu` | 697/10287 (6.8 %) | 321/2870 (11.2 %) |
| write fallback | 20/205 (9.8 %) | 63/831 (7.6 %) |

---

## 1. Sleep off, zero kills — the hypothesis survives its first real test

`B` ran 1 hour 53 minutes without a single `com.kei.pulse` death. The string
`SleepProfileMonitorService` does not appear anywhere in either unit's logcat dump.

The tally across four sessions on the unit that has ever misbehaved:

| date | Sleep on `B` | kills on `B` |
|---|---|---|
| 2026-08-02 | on | 6 |
| 2026-08-03 | on | 7 |
| **2026-08-04** | **off** | **0** |

`W` has had Sleep off throughout and has never recorded a kill.

This is now three sessions consistent with the reading in `STATUS.md`, and the first where the
variable was changed on purpose rather than observed after the fact. It is still **correlation, not
mechanism** — nobody has shown *why* that service's foreground-start gets the process reaped, and one
clean session does not rule out the kills simply being intermittent. But the cheap test was run and
it came back the way the hypothesis predicted, which is worth more than the previous two sessions
combined.

## 2. The SoC gate ran on real hardware and did nothing at all

Which is exactly the requirement. The version stamp confirms the gated build was what ran; the
`PulseRoot` warning that fires **only** on refusal appears zero times; and 1036 cap writes plus 13157
telemetry reads across the two units went through normally — a false negative would have blocked
every one of them.

So the gate is confirmed transparent on supported hardware. **It is still untested in the direction
that matters** — no MediaTek device was available to be rejected, so the negative cases rest entirely
on `DeviceSupportTest`.

Evidence: [`B/evidence/soc_gate_transparent.txt`](B/evidence/soc_gate_transparent.txt).

## 3. AutoTDP gave clocks back — first RAISE ever recorded, on an unidentified workload

Every previous session logged 0 RAISE against dozens of TRIM, which is what made the 2026-08-01
"vsync blind spot" reading look incomplete. `B` logged **3 RAISE**, and they fire where they should:
at 28.4, 29.8 and 62.8 fps against a 90 fps target.

**CORRECTION (same day).** The first version of this section said AutoTDP ran for an hour and
regulated Eden. Both halves are wrong.

Eden was on a **fixed tier (balanced)**, not AutoTDP — the logs say so directly:
`autoTdpPackage=null boundPackage=com.miHoYo.Yuanshen`. And AutoTDP did not run for an hour; it
started **five separate times** (16:38:19, 16:53:50, 17:38:26, 17:39:12, 17:39:56), each lasting
seconds to a couple of minutes. The error came from taking the first and last `PulseAutoTdp`
timestamp as one span, and from assuming the workload was Eden because Eden dominated the
foreground tally.

**What was AutoTDP actually regulating? Unresolved.** The `AUTOTDP-SESSION` line does not name a
package, and the `TICK-SKIP` line that would is not emitted while AutoTDP is active — so the logs
as they stand cannot answer it. That gap is worth closing in the log format itself.

What survives, and it is still the first of its kind: **the regulator demonstrably moves in both
directions.** What does not survive is any claim about how it behaves in Eden, or on emulators
generally. This session tested neither.

Two caveats on the surviving finding:

**The sample is six decisions.** Across all five bursts AutoTDP made 44 decisions and **38 had no
framerate reading at all**. HOLD with no data is correct behaviour, but the loop had something to
act on only six times.

**One decision does not follow.** At 17:38:41 it TRIMmed with `fps=30.0` against `tgt=90`, while cool
(`cT=35`) and well under power (`draw=2.39W`), so no thermal or power ceiling explains it. In context
it is part of a three-second oscillation — TRIM → RAISE → TRIM — with the controller chasing a very
noisy signal. Worth one look before anyone calls AutoTDP fixed.

**Five starts in an hour is itself a finding.** Whatever engaged AutoTDP kept engaging and
disengaging. Nobody has looked at why.

Evidence: [`B/evidence/autotdp_first_raise.txt`](B/evidence/autotdp_first_raise.txt).

## 4. Fan: correct but uninformative, because nothing got hot

Both units sat near the curve's floor because neither exceeded 60 °C (`B`) or 74 °C (`W`). At those
temperatures 22-27 % is what the curve says. Nothing new about curve behaviour — the 2026-08-03
session remains the only one that pushed it.

`W` streaming through Artemis is the reason its load stayed low: the PC does the rendering, so the
handheld is decoding video. That also makes `W`'s two workloads non-comparable with each other, let
alone with `B`.

## 5. The per-app fan override is nearly gone

`W` hit it 6 times out of 822 ticks (`bound=1` SILENT, `bound=4` SMART against a global `managed=6`),
against a whole lost session on 2026-08-03. `B` never hit it. Effectively fixed by the procedure
change, not by code — which is the preferred order.

## Flags that were false positives

- `dmesg` hits (3 on `B`, 2 on `W`): boot-time driver names containing "panic"
  (`gh_panic_notifier`, `sde_dbg_init … panic:1`) plus a module list. Every session has these.
- `logcat` hits (14 on each): `init` / `nvkeeper` / `qcrosvm` aborts, all pre-dating the session
  start — the ring buffer replayed when the filter attached. Identical set on both units.
- `clean session end: NO` on all five: the pull cut them live.
- Neither unit rebooted during its session.

## What was deleted, and one new check

`pulse_*_dmesg.log`, `pulse_*_logcat.log`, the generated `SUMMARY.md`, and both manual
`new_Session_logcat.log` dumps. 31 MB in, 5.3 MB out.

**Artemis made this pull a new redaction case.** PC streaming means a host name, a local IP and
possibly a GPU model could land in a log. The standard sweep from `PULL_AND_TRIM.md` does not look
for any of those, so a second sweep was run over the kept files for IPv4 addresses, `.local`
hostnames, and `moonlight` / `sunshine` / `GeForce` / `nvidia`. **Both sweeps came back with zero
matches** — the streaming client's networking never reaches PULSE's own logs. Worth keeping in the
procedure anyway, since the next session may not be so tidy.

## For the next session

1. **Leave Sleep off on `B` and run it again.** Two clean sessions would move this from "consistent
   with" to "established"; one more kill would kill the hypothesis outright. Either is progress.
2. **Give AutoTDP a workload deliberately, and know which one.** Six usable decisions is not a test
   of the regulator. Assign AutoTDP to one specific title that reports framerate reliably and let it
   run — that would say more in ten minutes than this session did in two hours.
3. **Log the package on the `AUTOTDP-SESSION` line.** Not knowing what the regulator was regulating
   is what made this section wrong the first time, and it is a one-field fix.
4. The `fps=30 → TRIM` decision deserves a read of `AutoTuneController`'s trim condition.
5. **Wanted comparison, no date: the same `pulse` version on a Retroid Pocket 6 (8 Gen 2), same
   emulator, same game.** AutoTDP has never been good with Eden here, which leaves two very
   different possibilities open — that this is a Pocket FIT / G3x Gen 3 problem, or that AutoTDP is
   simply poor against emulator framerate reporting on any hardware. One afternoon with an RP6 would
   separate them; nothing else in the repo can.
