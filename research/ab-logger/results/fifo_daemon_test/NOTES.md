# fifo-daemon-test — file index

Evidence for `research/pulse-for-aya/scripts/fifo-daemon-test.sh`: does a
named pipe (FIFO) beat the plain-file polling bridge from
`daemon_persistence_test/` for Kotlin↔daemon communication? Full writeup:
`STATUS.md`'s Minecraft-crash entry, the FIFO update following the
daemon-persistence-test one (2026-07-27, evening).

- `logcat.log` — host-side `adb logcat` capture spanning the run.
- `script.log` — the daemon's own log (`recv_at`/`applied_at`/`readback`
  per round trip), pulled from `/sdcard/apl_fifo_test.log`.

## Result: one clean run, FIFO beats file-polling on every axis that matters

Launch connection (`sh fifo-daemon-test.sh > out 2>&1 < /dev/null &`)
closed in 5ms, same as `daemon_persistence_test`'s redirected runs. Zero
new `xsu`/`xsud` connections for the whole run (one benign SELinux
audit line mentions `scaling_max_freq`, `permissive=1`, not a new
connection — same pattern as every prior run). No crash.

Four round trips sent from the host (`echo <value> > .../apl_fifo_in`),
each answered via `head -n 1 .../apl_fifo_out`:

| trip | write→daemon-notices | notice→applied+readback (real chmod/echo/chmod/cat cost) |
|---|---|---|
| 1 | 0.97ms | 32.6ms |
| 2 | 0.48ms | 35.9ms |
| 3 | 0.48ms | 33.2ms |
| 4 | 0.14ms | 38.2ms |

The pipe itself adds **under 1ms** of latency in every case — the
daemon's blocking `read` wakes up essentially the instant a line is
written, no polling delay at all. The ~33-38ms per round trip is the
real cost of the sysfs read/write cycle, not the IPC mechanism — same
ballpark as `daemon_persistence_test`'s cycle cost, just without that
run's ~2s poll interval stacked on top. `policy7` restored to its
original value cleanly on `STOP`.

**Conclusion**: FIFOs work cleanly in this device's shell environment
(`mkfifo` under `/data/local/tmp`, not `/sdcard`'s FUSE layer) and are a
clear upgrade over file-polling for the Kotlin↔daemon bridge — same
"one `xsu` connection ever" property, near-zero added latency instead of
a multi-second poll interval. **Not yet checked**: whether `pulse-for-
aya`'s own app process (a different SELinux domain/UID than this `adb
shell` test) can actually open/read/write files under `/data/local/tmp`
— a separate question from whether the shell-side FIFO plumbing works,
which this test answers definitively (yes).
