# Fan curve controller — first live confirmation (2026-07-30)

Pulled `pulse-for-aya` session logs from the first two on-device tests of
the newly-ported Custom fan curve controller (`research/pulse-for-aya/
README.md`'s "Fan curve implementation plan"). `SUMMARY.md` (auto-generated
by `scripts/analyze-pulse-logs.py`) covers 3 session groups — read that
first.

**All three sessions show "clean session end: NO -- crash suspected" —
verified NOT a real crash.** Each log's tail cuts off mid-idle-tick with
no error/exception, and the corresponding `dmesg` pull (not kept here,
see below) had zero crash-keyword hits. The mundane explanation fits the
timeline exactly: `20260730_180855`/`_181003` are two daemon restarts
within one `adb logcat`-monitored session (an early reconnect, then the
real test); `20260730_183203` is the follow-up session started after
`adb install -r`-ing the toast-message fix — installing over a running
app kills the process abruptly, which looks identical to a crash to the
"missing STOP marker" heuristic but isn't one.

**What each session was:**
- `20260730_180855` (38s, idle) — brief pre-test connection, nothing of
  interest.
- `20260730_181003` (~10 min, the main test) — this is the session with
  the **first live proof the daemon-routed reassert loop works**:
  `fast-loop drift: node=... re-pinned=20%` lines (the vendor daemon
  trying to reclaim the duty node, caught and corrected, repeatedly, live)
  and `CUSTOM fan running (...)`. Also where the Custom→Smart mode-switch
  toast bug was first hit (`arbiter=None ... managed=4`, fan handed off
  with no explicit vendor write since `setMode()` is a stub) — fixed same
  day, see `pulse-for-aya/README.md`.
- `20260730_183203` (41s) — quick re-check after the toast-message fix
  and after adding temp/fan fields to `cap_poll`; confirms the new
  `cpu_temp_mc`/`gpu_temp_mc`/`fan_duty`/`fan_rpm` columns are populated
  correctly (e.g. `cpu_temp_mc=39500 gpu_temp_mc=37900 fan_duty=76
  fan_rpm=2900`).

**Only the small, information-dense files were kept** (`pulse_<ts>.log` +
`_cap_poll.log`, all well under 10KB except `181003`'s, trimmed by
`analyze-pulse-logs.py` from 2208 to 1333 lines) — the large raw
`_dmesg.log`/`_logcat.log` pulls (up to ~740KB) were dropped after
confirming zero crash-keyword hits in them; not worth keeping in git for
sessions with nothing anomalous to explain.
