# Per-app profile binding — real-world test sessions

Index only — the actual analysis and running narrative live in `STATUS.md`
(search for these filenames; don't duplicate the analysis here, that copy
will drift).

## round1_2026-07-28_2028_retroarch_eden_minecraft_frontend/

Real-world session testing per-app profile binding across multiple game
frontends: RetroArch (N64 emulation), Eden project (Mario clone), Minecraft
Pocket Edition, and retrohrai launcher frontend. `pulse_20260728_202800.log`:
span 20:28:01–20:34:39 (~6m 38s), AutoTDP engaged yes, 154 cap writes via
daemon + 14 xsu fallback, 148 telemetry reads via daemon + 0 fallback, 28 TRIM
/ 16 RAISE / 28 HOLD actions. `cap_poll.log` confirms real sysfs movement on
all five monitored fields (p0_max, p2_max, p5_max, p7_max, gpu_max_pwrlevel).
Crash suspected (no clean end marker), logcat pre-session noise trimmed from
1890 to 624 lines (nvkeeper/qcrosvm crashes 20:27:28–20:27:33, before session
start). Dmesg 2 boot-time hits (gh_panic_notifier, sde_dbg_init at kernel
uptime 1.28/1.48s), trimmed from 5682 to 68 lines.

## round2_2026-07-28_2035_genshin_anomaly_retroarch_retrohrai/

Real-world session testing per-app profile binding on different game mix:
Genshin Impact (with anomaly detection enabled), RetroArch (emulation),
retrohrai frontend. `pulse_20260728_203503.log`: span 20:35:03–20:45:37
(~10m 34s), AutoTDP engaged yes, 92 cap writes via daemon + 7 xsu fallback,
48 telemetry reads via daemon + 0 fallback, 0 TRIM / 20 RAISE / 3 HOLD actions
(different regulation pattern — anomaly detection + frontend behavior). Crash
suspected (no clean end marker), logcat pre-session noise trimmed from 1838 to
1101 lines (pre-session crashes at 20:27:28–20:27:33, before 20:35:03 start).
Dmesg 0 crash-keyword hits, trimmed from 4136 to 68 lines.
