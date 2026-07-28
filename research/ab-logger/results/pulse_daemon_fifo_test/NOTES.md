# PulseDaemon FIFO migration — test pulls

Index only — the actual analysis and running narrative live in `STATUS.md`
(search for these filenames; don't duplicate the analysis here, that copy
will drift).

## round1_2026-07-28_0954_first_engage_confirmed/

First real test after migrating `AutoTuneController`'s cap writes onto the
`PulseDaemon` FIFO. `pulse_20260728_095642.log` is the useful one: a full
engage → HOLD → repeated TRIM (GPU cap 100→85→80→70→65→55%) → RAISE arc,
every write logged "via daemon", zero `xsu` fallback, no crash.

## round2_2026-07-28_1024_post_install_crash_not_fifo_related/

`cap_poll9/10_new_fifo.log` (host-side `poll-cpufreq.sh`, this round is what
prompted adding `gpu_min_pwrlevel`/`gpu_max_pwrlevel` to that script) plus
the paired `pulse_2026072810[24]*.log` app logs. `autoActive` never went
true in any of these — the crash here is the separate, already-documented
"Minecraft needs a reboot after fresh install" issue, not the
`xsud`/`BatteryService` bug the FIFO migration targets.

## round3_2026-07-28_1044_telemetry_culprit_found/

The "it crashed again" regression report. `pulse_20260728_104418.log`
(trimmed) shows a real ~60s AutoTDP session (engage → HOLD → a second fresh
engage) that then crashes in the same ~54-70s-after-engage window as the
original bug — despite the cap-write FIFO migration already being in
place. Led to finding `TelemetryReader`'s ~13-15 xsu connections per call
(never migrated) as the real dominant contributor, now fixed via
`PulseDaemon.readBatch()`. The four short `pulse_1052xx/1054xx/1056xx/1057xx`
sessions are the crash-loop aftermath (each dies almost immediately).

## round4_2026-07-28_1233_retroarch_crash_before_engage/

First test on the build with `TelemetryReader` also migrated (version line
confirms `built 2026-07-28 11:47:25`). User reported RetroArch showing the
same frozen-CPU-freq-then-crash symptom as Minecraft. **Inconclusive for
the telemetry fix specifically**: all four daemon sessions (12:33-12:38)
show `autoActive` never going true and `lastForeground` never showing
RetroArch's package at all — the crash-restart loop here happens before
PULSE's foreground monitor ever tracks the emulator, same shape as
round2's "needs a reboot after install" issue, not evidence either way for
whether the telemetry-read migration prevents the `xsud` crash during
actual regulation. Still need a session where AutoTDP visibly engages on
the patched build to test that.

## round5_2026-07-28_1245_minecraft_crash_after_write_fallback/

The session that finally got a real regulation run on the fully-patched
build. `pulse_20260728_124555.log`: engage 12:46:25, HOLD, then a real TRIM
sequence under heavy load (Minecraft 120fps target, temps up to 83°C
CPU/73°C GPU, multiple domains capped at once) with rapid successive
writes — all "via daemon" until 12:47:07, where the **first-ever "cap
write via xsu fallback"** appears, then the file stops. The paired
`_cap_poll.log` (independent sysfs poll) confirms real cap movement
(`p7_max`, `p2_max`, `gpu_max_pwrlevel` all move) up through 12:47:06, one
second before the crash. Investigation paused here at the user's request —
see STATUS.md's entry for the honest state and the concrete gap found (no
daemon-vs-fallback logging on the READ side, unlike writes, so we can't
yet tell whether telemetry reads were quietly falling back to `xsu`
throughout this session).
