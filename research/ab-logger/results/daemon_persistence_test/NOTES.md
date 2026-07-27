# daemon-persistence-test — file index

Evidence for `research/pulse-for-aya/scripts/daemon-persistence-test.sh`:
does a script backgrounded via a single `xsu -c "... &"` call avoid
further `xsu`/`xsud` connections for its whole run? Full writeup and
conclusions: `STATUS.md`'s Minecraft-crash entry, "New direction found and
validated" update (2026-07-27, evening).

Each `runN_.../` has:
- `logcat.log` — host-side `adb logcat` capture spanning the run.
- `script.log` — the daemon script's own log (`target`/`readback` per
  cycle), pulled from `/sdcard/apl_daemon_test.log`. Missing for run 1
  (deleted on-device before it was pulled).

| run | launch command | launch→close time | new xsu conns from the write loop? | crash? |
|---|---|---|---|---|
| `run1_..._no_redirect` | `xsu -c "sh script.sh &"` (no stdio redirect) | ~2m18s (stayed open the whole run) | zero | no |
| `run2_..._redirected` | `xsu -c "sh script.sh > out 2>&1 < /dev/null &"` | 11ms | zero | no |
| `run3_..._post_reboot` | same as run 2, after a clean device reboot | 5ms | zero | no |

All three runs: `policy7` (lone-core) cap toggled cleanly in both
directions every cycle, readback always matched the target. `policy0`
(`cpu0`+`cpu1`, shared) came up already at `787200` (the Eco-tier value)
in every run — including run 3, run fresh after a reboot that followed a
manual fix to `2265600` — so the script's own before/after alternation
was an unintentional no-op for `policy0` in all three runs (never
actually validated shared-policy up/down toggling via direct writes).
This means `787200` isn't a "stuck"/broken state from an incomplete
restore as first suspected — something (most likely AYASpace re-applying
whatever mode was last active) re-asserts it on every boot, independent
of anything this repo's tooling did. Not yet root-caused further.
