# Unsupervised real-world session — 2026-07-29

Index only — the actual analysis and running narrative live in `STATUS.md`
(search for "Unsupervised session"; don't duplicate the analysis here, that
copy will drift). `SUMMARY.md` in this folder is the deterministic output
of `research/pulse-for-aya/scripts/analyze-pulse-logs.py`, generated
automatically as part of the user's own pull procedure, not written by hand.

Pulled after a normal, unsupervised day of play — no one watching logs
live, no test protocol, just real usage. 4 sessions in `apl_pulse_logs/`
today (a 5th+6th+7th+8th from 2026-07-28 already covered by the
per-app-profile-test entries elsewhere in `results/`):

- `pulse_20260729_151428*` — ~61min, Eden (`com.miHoYo.Yuanshen`
  applicationId) + a brief RetroArch stint.
- `pulse_20260729_161522*` — instant false start (<1s, 124 bytes).
- `pulse_20260729_161552*` — 32s idle false start (launcher foreground,
  AutoTDP never engaged).
- `pulse_20260729_161822*` — ~3h20m, Eden, the real continuation after the
  two false starts above.

## Headline finding

A previously-undocumented kernel-level **haptic-driver + GPU AHB-bus-error
storm** (`kgsl kgsl-3d0: CP: AHB bus error` /
`hid_aya_haptic_play`/`aya_haptic_hid_report_work`, plus a
`gen7_err_callback: N callbacks suppressed` line proving the real rate was
higher than what got logged) spikes sharply in the ~150-kernel-second gap
between the two false-start sessions (300 AHB errors + 6360 haptic events,
vs. a background rate of well under 1/min the rest of the day) — purely
kernel/dmesg-level, invisible to logcat (confirmed byte-identical logcat
dumps for both false-start sessions via `diff -q`). Not yet root-caused.
See `STATUS.md` for full detail and the rest of the findings (self-kill
bug still fixed, no crashes, `com.qti.diagservices` ANR-loop unchanged and
chronic as already documented).