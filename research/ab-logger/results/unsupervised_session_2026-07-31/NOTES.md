# Unsupervised session, 2026-07-31 — `B` unit only

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
