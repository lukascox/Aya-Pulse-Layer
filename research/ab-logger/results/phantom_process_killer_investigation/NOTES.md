# Phantom Process Killer investigation — raw `ab-logger`/logcat pulls

Index only — the running narrative and conclusions live in `STATUS.md`'s
Phantom Process Killer entry (don't duplicate it here, that copy will drift).

## round1_2026-07-28_1811_diagservices_anr_correlation/

Three `logcat -d` greps taken during one ~10.5min PULSE+Minecraft session
(18:11:12 → 18:21:55), read-only, no device state changed.

- `diagservices.log` — every `com.qti.diagservices` ANR-restart cycle (32
  total, trimmed from 346 raw lines to 34 — every cycle is byte-identical
  except timestamp/pid). Confirms a **chronic, pre-existing device
  condition**: this vendor persistent service times out and gets
  killed+restarted by ActivityManager every ~20s, continuously, regardless
  of whether PULSE/AutoTDP is even running. Same signature already sat
  unnoticed in yesterday's `minecraft_crash_investigation/round4.../
  minecraft_crash_20260727_103555.log` (6 occurrences, same ~20s cadence).
- `phantom.log` — every Phantom Process Killer kill in the same window (21
  total). Two parents show up: `com.kei.pulse` (our own `xsu` children) and
  `com.ayaneo.gamewindow` (spawns `top` periodically) — confirms the killer
  sweeps ALL apps' untracked children, not something PULSE-specific.
- `activity.log` — **dropped, was 0 bytes**. `dumpsys activity processes |
  grep -i phantom` surfaced nothing; that dumpsys section just doesn't
  expose phantom-kill counters under that query. Not a real negative result
  — `phantom.log`'s direct logcat evidence already proves the killer is
  active. Worth trying a different `dumpsys` query next time if the kill
  counts/thresholds themselves are ever needed.

**The correlation, checked by hand**: every single `com.kei.pulse`/`xsu`
phantom-kill timestamp in `phantom.log` lines up within <30ms of a
`com.qti.diagservices` ANR timestamp in `diagservices.log` (e.g. `18:12:32.716`
vs `18:12:32.717`, `18:13:12.938` vs `18:13:12.939`, `18:20:15.188` vs
`18:20:15.189`). This is not loose timing correlation — it's the same
ActivityManager cleanup pass. The mechanism: `com.qti.diagservices`
(persistent, exempt from freezer) ANRs and gets restarted roughly every 20s
forever on this device; as part of that same cleanup cycle, ActivityManager
also sweeps and kills whatever "phantom" (untracked, `ProcessBuilder`-spawned)
child processes happen to still be alive from ANY app — ours included, if an
`xsu` invocation (or the daemon's own long-lived `sh pulse_daemon.sh` child)
is unlucky enough to still be running at that exact moment.

**Not yet confirmed**: whether the specific PID killed is ever the long-lived
`pulse_daemon.sh` script itself (vs. just the short-lived `xsu` spawner that
exits in milliseconds either way) — `phantom.log`'s process names show `xsu`
or `com.kei.pulse`, not `sh`/`pulse_daemon.sh` by name, so this is still open.
Also open: whether disabling `com.qti.diagservices` (`pm disable-user`,
reversible) stops the ~20s sweep cadence entirely, which would be the cleanest
way to test whether it's really the trigger.
