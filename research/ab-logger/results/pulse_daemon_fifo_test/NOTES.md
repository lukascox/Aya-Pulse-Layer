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
