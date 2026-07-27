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
