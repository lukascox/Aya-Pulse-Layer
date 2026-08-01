# Sessions, 2026-07-31 (`B`) and 2026-08-01 (`W`)

Folder name keeps the `B` session's date. `W`'s first session ran just past
midnight, so its files carry `20260801`.

## `W` first run: the clean-install test PASSES

`W` is the white unit, untouched until this install, so this doubles as
"does the port work on a device nobody prepared". Six-minute session,
`pulse_20260801_000730*`. Every prerequisite in `STATUS.md`'s test order
cleared, in order:

1. **`xsu` is present and works on a stock unit.** The daemon started and
   carried the session: 83 cap writes and 183 telemetry reads through the
   FIFO, 1 `xsu` fallback, 0 fallback reads. This was the whole unknown, and
   it is closed.
2. **The fan duty node exists and reads.** `cap_poll` logs `fan_duty` and
   `fan_rpm` exactly as on `B`.
3. **The AIDL client bound, confirmed two independent ways.** Directly, in
   the ring buffer: `PulseFan: AIDL callback: fanMode=FAN_MODE_BALANCE` (the
   vendor's own state, not an echo of ours). And objectively, via PWM
   readback of a discrete mode change:

```
00:07:34  PulseFan: managed=5  (SPORT)
00:07:39  fan_duty 12 -> 255,  fan_rpm 322 -> 6718
00:07:41  PulseFan: managed=4  (SMART)
00:07:44  fan_duty 255 -> 76,  fan_rpm settles ~2750
```

   ~5 s vendor latency, both directions. Duty 255 is unreachable unless the
   AIDL command landed.

AutoTDP also ran (governor toggling `performance`/`schedutil`, caps moving).
`dmesg` crash-keyword hits: **0**. Every logcat hit pre-dated the session and
was Eden (`org.yuzu.yuzu_emu`, running as `com.miHoYo.Yuanshen`) crashing on
its own, not vendor or PULSE code.

### Second `W` session the same night: Stardew, and AutoTDP starving a vsync-locked game

`pulse_20260801_002757*`, 00:27:57→00:36:23, after the reboot. Also here:
the `000730` files were re-pulled longer and replace the earlier copy.

**PULSE did not crash** — the log runs to the moment of the pull, and caps
were released cleanly when the game exited (`00:31:00`: `p0_max=2265600`,
`gpu_max_pwrlevel=0`, everything back to 100 %).

**The frozen CPU readout in AYA Settings was PULSE doing as told.** A per-app
profile bound to `com.retrohrai.launcher` (applied at `00:31:39`) caps all
four clusters near 55 %, and with `gov=performance` the frequency is pinned
*at* the cap — `p2_cur` has exactly one value across 100 consecutive samples.
Nothing is broken; the number cannot move. Changing or dropping that profile,
or not pairing it with the performance governor, is the fix.

**The real finding: AutoTDP starves vsync-locked 2D games.** Over 45 s it
trimmed the GPU 1050→903→834→770→720→680→629→578→500→422 MHz (level 0→10 of
13), `act=TRIM` every 3 s, and never stopped — because its stop condition is
an fps drop and Stardew is pinned at exactly `fps=60.0 jank=0 tail=16ms` in
every single line. A vsync-capped game reports the cap until it falls off a
cliff, so the signal AutoTDP regulates on is uninformative. Meanwhile
`bn=CPU` and `cpuPk=100` throughout: the real bottleneck was one pegged core
(SMAPI/Mono is single-threaded), CPU caps stayed at 100 %, and AutoTDP trimmed
the GPU, which was never the limiter. SMAPI's pid changed (14151→22288), so
the game did restart — strong correlation, not proof.

Same family as the open Eden thread, from the other side: there fps never
reaches target, here fps is artificially perfect. **Workaround: do not use
AutoTDP for vsync-locked 2D titles; use a fixed tier.**

### `W` idles with the fan nearly stopped, and that is probably a vendor fault

Before PULSE touched anything, `W` sat at `fan_duty=12` / 322 rpm — the fan
effectively off. `B` idles at 76 / ~2750. After PULSE sent SMART, `W` moved to
76 and stayed.

This lines up with the user's report that **AYA Settings' UI did not work on
this unit**. If the vendor stack had not initialised, its fan daemon would not
have been regulating, which is exactly what duty=12 looks like. On that
reading PULSE restored cooling on a device whose vendor software had quietly
stopped doing it. Unconfirmed, but testable: if `W` idles at 76 after the
reboot, the vendor stack was simply not initialised.

**The AYA Settings failure left no trace at all.** A full `logcat -b all`
covering ~2 weeks has no launch attempt, no exception, no `avc: denied`, no
process death for `com.ayaneo.settings`. The package reads `installed=true`,
`stopped=false`, `enabled=0` (default, not disabled) and the process is alive
at 0 % CPU. So it is a UI/init-level failure, not a process failure, and
nothing implicates PULSE.

Vendor versions on `W`, recorded because differing versions would confound
the two-device comparison: `com.ayaneo.settings` **1.1.112** over system
1.1.100, `com.ayaneo.gamewindow` **1.5.84** over 1.5.78. `B` not checked (not
to hand); the user expects them identical.

### Not kept, and one file that must never be committed

Same trim as `B`: `_dmesg.log` and `_logcat.log` deleted (0 and 0 relevant
crash hits respectively), plus the auto-generated `SUMMARY.md`, whose
crash-hit list would dangle after those deletions.

The pre-reboot `logcat -b all` dump was pulled to the user's home directory
and **deliberately kept out of this repo**. It contains the home network name,
a BSSID, four unmasked hardware addresses and a personal e-mail address.
`dmesg` masks these; `logcat` does not. If anything from it is ever needed,
extract the specific verified lines — never the file.

## `B`, 2026-07-31 — the earlier session

Real, unsupervised play on the black (`B`) unit. `W` had not been installed
yet, so this is single-device data, not the two-device comparison.

Build: `1.19.6 (303)`, built 2026-07-31 00:45:36, `script_crc32=ebbd831f` —
the shipping APK, i.e. everything through the fan-OFF arbitration fix.

## What is kept, and why the rest was deleted

Two files from the long session, in `B/`:

- `pulse_20260731_155031.log` — the daemon/AutoTDP log (15:50:32 → 21:32:11)
- `pulse_20260731_155031_cap_poll.log` — the 1 Hz sysfs ground-truth poll

Deleted after review, deliberately: both `_dmesg.log` files (10.5 MB
combined), both `_logcat.log` files, and the whole short 12:06:50 session.
Every crash-keyword hit the analyzer flagged in them was verified noise (see
below), and none of it carried evidence for anything open. The dmesg files
were also the only place in this pull carrying partially-masked router
BSSIDs — deleting them removes that concern rather than deferring it.

Raw pull originally landed in `research/aidl-fan-spike/results/run8/B_unit/`.
Moved here because the session contains no fan-curve activity at all (see
"The fan did nothing" below) — the directory name promised something the
data did not hold.

## All flagged "crashes" were noise

- **dmesg, 2 hits per session**: boot-time lines at `[1.24s]`/`[1.35s]` whose
  *driver names* contain the word "panic" (`gh_panic_notifier`,
  `sde_dbg_init … panic:1`). No content.
- **logcat, 14-15 hits per session**: every one **pre-dates its session's
  start** — `init`/`nvkeeper`/`qcrosvm` aborting during boot (15:50:14-21 vs.
  session start 15:50:32). The filtered logcat replays what is already in the
  ring buffer when it attaches; same artifact documented on 2026-07-28.
- One `SIGABRT` in `binder:1450_2` at 12:12:54, one second after the short
  session's last line. Low PID (boot-time system process), not
  `com.kei.pulse`, and it never recurs in the 5h41m session. Coincidence with
  the user leaving the game.
- **`clean session end: NO` on both** is explained, not a crash: the long
  session was cut by the `adb pull` at 21:33; the short one by the user
  exiting the game.

## Three findings worth keeping

**1. The daemon survived ~3h15m of suspend and resumed cleanly.**
The main log has a hole from 18:16:09 to 21:31:10, but `cap_poll` keeps
producing lines throughout (24 lines in hour 19, 37 in 20, 77 in 21) — the
root shell stayed alive through doze, just polled slowly, and AutoTDP resumed
regulating on wake with **no daemon restart** (`session start` appears exactly
once in the whole log). Nobody set out to test this; it is the strongest
evidence for the FIFO daemon's stability so far.

**2. `xsu` fallback at 4.9%, in a window, with no error path.**
296 fallbacks vs. 5770 daemon cap writes. All of them fall between 16:11:41
and 18:15:51; zero afterwards. Crucially this is **not** the daemon being
down — successful daemon writes interleave in the same second:

```
16:11:41 PulseDaemon: cap write via daemon (2 node(s))
16:11:41 PulseDaemon: cap write via xsu fallback (2 node(s), ~289 chars, chunked)
```

The log contains zero lines matching `error|fail|timeout|denied`. So specific
write batches take the fallback path for a reason not visible in the log.
Harmless here, but raw `xsu` is the historical prime suspect for crashes, and
4.9% is up from the ~2% seen in the first clean FIFO session (2026-07-28).
Unexplained; not chased.

**3. Zero Phantom Process Killer kills, despite 449 `diagservices` ANRs.**
The chronic `com.qti.diagservices` ANR loop is unchanged (449 in 5h41m, ~every
45s). But `PhantomProcessRecord` appears **zero** times, against 21 kills in a
10-minute window on 2026-07-28, each within 30 ms of an ANR. Not a filter
artifact — those lines are `ActivityManager:E`, which this logcat filter
captures (15084 such lines present). Evidence checked before the logcat files
were deleted.

## The fan did nothing — by design, not by failure

Only 6 `PulseFan` lines in 5h41m, all `arbiter=None`, `latched=false`, and all
at app-switch moments. Reading them: `managed=4` and `bound=4`, and
`FanController.SMART = 4` while `CUSTOM = 6`. The Fan card was on **Smart**,
so `FanArbiter.decide` correctly returns `None` on both reachable paths —
AutoTDP-active with a non-Custom mode stands down, and an undrifted vendor
mode needs no write. `fanLog` also de-duplicates, so 6 lines means six state
changes with steady state between them.

**Consequence: this session is not evidence about the fan curve either way.**
To get that, the Fan card has to be set to Custom. And note that the
interesting lines (`fast-loop drift`, `fan_mode drifted`, `AIDL callback`) go
to `android.util.Log` only, never through `pulseDaemon.log` — `pulse_daemon.sh`
filters logcat to crash tags, so they will never appear in a pulled log.
Capture them live with `adb logcat -s PulseFan:D`.

**Done immediately afterwards, same evening**, with the Fan card set to
Custom — that capture confirmed the fan↔clock cascade end-to-end AND
superseded the run5 reassert-cadence figure. See `STATUS.md`, "Fan↔clock
cascade CONFIRMED END-TO-END", and the boxed SUPERSEDED note in
`research/aidl-fan-spike/FINDINGS.md`.
