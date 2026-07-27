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
