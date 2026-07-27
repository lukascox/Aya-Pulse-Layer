# Minecraft crash investigation — raw `ab-logger` pulls

Index only — the actual analysis and running narrative live in `STATUS.md`'s
"native Minecraft fails to launch while PULSE is running" section (don't
duplicate it here, that copy will drift).

## round1_2026-07-27_0915_old_ablogger/

Pulled before the crash-capture/per-sample-sync changes landed — no
`logcat_*.log` exists for any of these, and `/sdcard` sync still lagged up
to 10 samples behind. Six sessions, `_550309` is the only one that ran
clean (PULSE inactive); `_675652`/`_744486`/`_816264` all truncate abruptly
partway through with PULSE's governor at `walt`.

## round2_2026-07-27_0933_new_ablogger/

Pulled after that build. Four sessions:
- `_589999` — 1 sample, immediate stop.
- `_597194` — 26 samples/144s, Minecraft under stock `performance` governor
  (PULSE not actively tuning), hit 90-96°C repeatedly, **no crash**, ended
  cleanly (last row shows the user back in `ab-logger`'s own UI to hit
  Stop).
- `_752907` — 7 samples, PULSE settings navigation + a brief 2-frame
  Minecraft launch attempt.
- `_879294` — 27 samples, PULSE active (`walt`) from row 0, Minecraft plays
  ~130s then `frame_count` drops to 0 and the foreground app becomes the
  launcher/home screen — looks like Minecraft itself died, but **the
  sampling loop never truncates this time** (unlike round 1) — a different
  failure shape, app-level rather than system-level.

**Still no `logcat_*.log` in this round either** — check on-device at
`/sdcard/apl_ab_logs/` for a file matching that pattern before assuming the
capture code is broken; it may just not have been pulled.

## round3_2026-07-27_1019_schedutil_test/

Pulled after `pulse-for-aya` was rebuilt with `schedutil` instead of `walt`
for Balanced/AutoTDP (commit `64dcb18`). Three genuinely new sessions (the
pull also re-grabbed 4 already-known round-2 files, byte-identical,
discarded rather than duplicated here):
- `_371966` — 1 sample, immediate stop.
- `_375013` — 3 samples, governor confirmed `schedutil` from row 1, brief.
- `_454633` — 14 samples: Minecraft launches under `performance` (row 5),
  AutoTDP engages `schedutil` at row 6 with a temp spike to 93.0°C (same
  shape as round 2's 93.8°C spike right as PULSE engaged, different
  governor) — plays fine for ~47s under `schedutil` (rows 6-13, temps back
  down to 55-65°C, FPS steady), **then the file truncates** — no further
  rows, matching round 1's abrupt-cutoff shape more than round 2's
  captured-recovery-window shape. Still no `logcat_*.log` anywhere in this
  pull either.

**Headline result: the crash reproduces under `schedutil`, not just
`walt`.** Rules out governor choice as the root cause — see STATUS.md for
the revised theory and next steps.

## round5_2026-07-27_1053_unplugged_new_ablogger/

Reproduced unplugged (no charger), with the fixed `ab-logger` build (the
2026-07-27 `startCrashCapture()` rewrite) — this is the first round where
`ab-logger`'s own in-app `logcat` capture actually produced output.
`session_1785142405772.csv`/`logcat_1785142405772.log` is a short false
start (empty CSV, logcat just shows a normal thermal-zone scan getting cut
off by a clean `pkill`, no crash signature — not the real repro).
`session_1785142413182.csv`/`logcat_1785142413182.log` is the real one: 3
CSV rows, then a genuine crash caught in the logcat. **Different crash
mechanism than round 4** — not `BatteryService`/`system_server` this time,
see STATUS.md for the full analysis (short version: Minecraft's own
`XNNPACK`/`cpuinfo` library aborts trying to read CPU topology, `Fatal
signal 6 (SIGABRT)` in Minecraft's own process, not system-wide). Confirms
this is not a single narrow bug — PULSE's activity is destabilizing more
than one subsystem.

## round6_2026-07-27_1225_unplugged_aggressiveparkoff/

Reproduced unplugged, `schedutil`, `aggressivePark` explicitly OFF — crash
still happens (rules out `aggressivePark` as the sole/necessary trigger).
`logcat_1785147913343.log` stops ~75s before the CSV's actual last
sample — the backgrounded capture connection itself got killed by one of
the `xsud` `xsu_conn_handler` stack-overflow crashes (see STATUS.md), so
this file doesn't cover the true end of the session. What it DOES show:
every `xsud` crash captured across this whole investigation shares the
identical `__stack_chk_fail` / `xsu_conn_handler.cfi+856` backtrace — a
real, reproducible bug in the vendor `xsud` binary itself, not in this
repo's code. Full theory (concurrent `xsu` connection bursts at
app-launch time, not a specific PULSE option) in STATUS.md.

## round7_2026-07-27_1244_cable_host_capture/

Cable reconnected, full host-side `adb logcat` capture
(`minecraft_crash.log`, 8MB) of another `system_server` crash, run
simultaneously with `ab-logger`'s own capture
(`session_1785149052936.csv`/`logcat_1785149052936.log` — the real pair;
`session_1785148979469.csv`/`logcat_1785148979469.log` is a short false
start). Used to answer "is there a timing pattern" — full timeline and
answer in STATUS.md. Short version: no fixed timer, but a consistent
shape (long quiet gap, then `xsud` crashes clustering tighter and tighter,
~10s apart, in the final ~20-30s before `system_server` goes down) matches
round 4 almost exactly. Also caught direct evidence of a system-privileged
`Performance-Timer` thread (almost certainly AYASpace's own native
perf-monitor) reading `gpuclk` concurrently — independent confirmation of
the "other actors sharing `xsu`" theory. `ab-logger`'s own capture died at
the very first `xsud` crash again, same as round 6 — confirmed reproducible
limitation, not a one-off.

`minecraft_crash_no_ablogger.log` (also in this folder, 9.5MB): same test
with `ab-logger` NOT running at all, to check whether its own polling is
a necessary ingredient. It isn't — same crash, same timing shape (69s
Game-Mode-to-crash, same accelerating `xsud`-crash gaps). See STATUS.md's
three-capture comparison table.

## round4_2026-07-27_1035_schedutil_logcat_capture/

Host-side `adb logcat -v threadtime` capture (not `ab-logger`'s own
in-app capture, which still doesn't produce a file — that's still
unexplained, see STATUS.md) spanning a real crash reproduction.
`minecraft_crash_20260727_103532.log` is a short (~17s) lead-in;
`minecraft_crash_20260727_103555.log` is the real capture and contains
the actual crash. **This is the first real crash signature this
investigation has captured** — full analysis in STATUS.md, don't
duplicate it here. Short version: same `BatteryService$Led` →
`ILights.setLightState()` → uncaught exception → `system_server` crash as
the original 2026-07-25 INCIDENT, plus a newly-found candidate trigger
(repeated `echo 1 > /sys/class/power_supply/battery/online` from an
unidentified source, not this repo's code, while the device was on the
charger).
