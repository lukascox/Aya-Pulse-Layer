# run5 — sysfs unlock + reassert-cadence measurement (2026-07-30)

Not an `aidl-fan-spike` app run like run1-4 — this is the manual/scripted
sysfs follow-up (chmod-unlock discovery, then precise reassert-cadence
measurement). Full narrative and conclusions:
`research/aidl-fan-spike/FINDINGS.md` ("Raw sysfs write CONFIRMED WORKING"
and "Vendor daemon reassert cadence measured" sections).

Scripts that produced these logs: `../../scripts/fan_reassert_probe.sh`
(single write, just watch) and `../../scripts/fan_reassert_probe2.sh`
(write, wait for the correction, write again, watch again) — both push to
`/data/local/tmp/` and log to `/sdcard/apl_pulse_logs/`, pulled here.

- `fan_reassert_probe_1785423971.log` — first precise (1s-resolution)
  measurement. `fan_reassert_probe.sh` run. Reassert at t=18s, then stable
  through the full 180s run.
- `fan_reassert_probe2_*.log` (8 files) — `fan_reassert_probe2.sh` runs,
  each with two write→reassert measurements (first write, and a second
  write sent immediately after the first correction). Timestamps in the
  filenames are the epoch seconds the script started, no other meaning.

All corresponding `*_stderr.log` debug-net files (from the launching
`xsu -c "... > stderr.log 2>&1 &"` wrapper) were empty across all 9 runs —
confirms no script errors — and were deleted rather than kept as 9
zero-byte files.
