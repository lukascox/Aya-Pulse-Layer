# xsu Capability Probe — Consolidated Findings

Three throwaway Android probe apps were built and run on-device across two
research sessions to answer one question before writing the real
`XsuShell.kt`/`AutoTdpController.kt`: **can `xsu` be invoked from inside a
normal, installed Android app (not just `adb shell`), and if so, what are
its real-world characteristics (reliability, latency, failure modes)?**

This document is the synthesis. The probe app kept in this directory is the
last (most refined) iteration; raw result files from all runs are preserved
under `results/`, grounding every claim below in actual on-device data
rather than assumption.

## Bottom line

**Yes — `xsu` works from `Runtime.exec()`/`ProcessBuilder` inside an
installed app**, confirmed across debug and release builds, across the
`args` invocation method (`ProcessBuilder("xsu", "-c", cmd)`), and across
four different foreground apps (RetroArch, Eden/yuzu, Dolphin, and the
probe app's own idle UI). This closes the single biggest open question
carried since the original `xsu_handoff/handoff_pulse_android_app.md`.

## Confirmed capabilities (regression-checked across multiple runs)

| Capability | Status | Evidence |
|---|---|---|
| `xsu -c "id"` returns `uid=0` from an installed app | CONFIRMED | `results/test_app_run0/xsu_result_debug.txt`, `xsu_result_release.txt` — both debug and release builds |
| CPU sysfs write+verify+restore (`cpufreq/policy0/scaling_max_freq`) | CONFIRMED | All `results/xsu_benchmark_run1/*/xsu_benchmark_result.txt` — TEST 2 PASS in every run |
| **GPU sysfs write+verify+restore (`kgsl-3d0/max_pwrlevel`) — NEW, not proven via the old Root-Script channel** | CONFIRMED | TEST 3 PASS in every run 1 result file (e.g. `other/xsu_benchmark_result.txt`: wrote pwrlevel 6, read back 6, restored to original 0) |
| GPU busy signal (`kgsl-3d0/gpubusy`) readable via the app channel | CONFIRMED | TEST 4 in all runs; values changed between reads during active load (e.g. `eden` run), were identical during idle (`clean`, `other` before app launch) |
| FPS pipeline (foreground-app detect → SurfaceFlinger layer match → `--latency` FPS calc) reachable via `xsu` despite `dumpsys` normally requiring `android.permission.DUMP` | CONFIRMED | TEST 5 in all runs — see layer-matching section below |
| `scaling_governor` itself writable (not just frequency caps) | Only tested in v2 spec, not yet run at time of this migration — see "Not yet executed" below |

## Layer-matching heuristic: confirmed across THREE distinct naming conventions

The 4-tier priority search ported from `pulse_lite_diag_v8.sh` was validated
against three different apps during these runs, each exercising a different
tier:

- **RetroArch** (`com.retroarch.aarch64`): no `SurfaceView`-named layer at
  all — tier 3 (last non-helper match) required.
- **Eden/yuzu** (`com.miHoYo.Yuanshen`): `SurfaceView[...](BLAST)` — tier 1.
- **Dolphin** (`org.dolphinemu.dolphinemu`): also `SurfaceView[...](BLAST)`
  — tier 1, confirmed in `results/xsu_benchmark_run1/other/xsu_benchmark_result.txt`
  sample 3-5 (`SurfaceView[org.dolphinemu.dolphinemu/...EmulationActivity](BLAST)#1162`).

No further layer-naming validation is needed before writing the production
`FpsReader.kt` — three independent naming conventions across three real
emulators is a solid empirical base.

**Update (2026-07-24, via the sibling `research/autotdp-ab-harness` probe,
not this one):** Azahar (`org.azahar_emu.azahar`, a Citra 3DS fork) is now
also confirmed — tier 1 (BLAST), matched as
`SurfaceView[org.azahar_emu.azahar/org.citra.citra_emu.activities.EmulationActivity](BLAST)`.
Note the Activity class is still named `org.citra.citra_emu...` even though
the package was rebranded — the fork kept Citra's original class names.
Four distinct apps now confirmed, no further layer-naming validation needed.

## New edge case: stale-buffer FPS decay on an idle/static foreground layer

Found in `research/autotdp-ab-harness` run2 (2026-07-24), not this probe's
own runs, but belongs here since it's a property of the shared FPS pipeline.
When the foreground layer stops producing new frames (e.g. the user leaves
the harness app itself in foreground, not touching anything), 14
consecutive samples showed `frame_count` staying near its ceiling (127 —
NOT triggering the existing `low_sample_count` guard, which only fires
below 5) while the computed FPS smoothly decayed every sample: 114.3 → 71.7
→ 35.8 → 20.9 → 13.2 → 9.7 → 7.7 → 6.3 → 5.4 → 4.7 → 4.1 → 3.7 → 3.3 → 3.1.

Interpretation (not independently verified beyond this one observation):
SurfaceFlinger's `--latency` buffer for a layer that has stopped rendering
appears to keep returning the same stale, historical present-time entries
rather than emptying out. `frame_count` (just a count of buffer entries)
stays high, but `span_ns` (first-to-last timestamp delta in that same
frozen buffer) is measured against a real "now" that keeps advancing every
sample, so the computed FPS keeps shrinking even though nothing new is
actually happening. Confirmed via `xsu_bench_logcat_dump.txt` timestamps
that this was NOT an actual pipeline stall — each sample completed on
schedule (~3.5s apart) with normal `pipeline_ms`/`snapshot_ms` values
throughout; the anomaly is in the FPS *number's meaning*, not in the
pipeline's execution.

**Implication for `AutoTdpController`:** a persistently non-trivial
`frame_count` is NOT sufficient evidence that the computed FPS is
meaningful — an idle/static screen can produce this exact decaying-FPS
signature without the existing `low_sample_count` or `zero span` guards
catching it. Whoever designs the FPS-delta signal needs a way to detect
"this layer's present-time buffer isn't advancing" (e.g. comparing the
newest timestamp across consecutive samples), not just count entries.
Not fixed here — same "not this app's job" boundary as `zero span`.

## Confirmed broken / must NOT be used

- **The "stdin" invocation method** (`ProcessBuilder("xsu")` + writing the
  command to stdin, the pattern from `YtRootShell.java`) returns `exit=0`
  with **empty stdout/stderr — a silent false positive**. Root cause never
  diagnosed. The production `XsuShell.kt` must use the `args` method
  (`ProcessBuilder("xsu", "-c", cmd)`) exclusively, or this needs a
  dedicated diagnostic session before ever being reconsidered.
- **`gpubusypercentage`** (as opposed to raw `gpubusy`) — confirmed broken
  on this kernel across the whole project (v6 onward), reconfirmed here via
  the `ls kgsl-3d0/ | grep -i busy` sanity check, which lists both nodes but
  only `gpubusy` gives usable data.
- **Raw `gpubusy` on its own, read once, is NOT a trustworthy tier-decision
  signal.** Per `HARDWARE_PROFILE_v6_en.md` (7 diagnostic runs), the cycle
  counter is confirmed to wrap/reset between reads, producing values as
  extreme as -2718%. `AutoTdpController` needs a smoothing strategy (e.g.
  rolling median) or a secondary signal — deciding this is controller
  design work, not something either probe app attempted to fix.

## Performance characteristics (the numbers that decide XsuShell.kt's architecture)

From `results/xsu_benchmark_run1/retroarch/xsu_benchmark_result.txt` (most
complete run, 25-30 iterations per category):

```
simple_read_cat:              avg=101.7ms  min=101ms  max=102ms  p95=102ms
write_verify_combined:        avg=101.5ms  min=101ms  max=102ms  p95=102ms
single_dumpsys_call:          avg=101.4ms  min=101ms  max=102ms  p95=102ms
full_fps_pipeline_per_sample: avg=319.3ms  min=313ms  max=341ms  p95=330ms
```

**The ~100-102ms per-call floor is real, command-independent, and confirmed
across every single result file in this directory** — a bare `cat`, a full
`dumpsys activity activities`, and `id` all cost essentially the same. This
is almost certainly the fixed cost of spawning `xsu` itself (process
creation + root elevation handshake), not the cost of whatever command runs
inside it. The 3-call FPS pipeline costing ~3.2x the single-call floor is
exactly consistent with that model (3 sequential spawns, not 3x the work of
one command).

**Implication for `AutoTdpController`'s design:** a naive polling loop that
issues one `xsu -c` call per signal per tick will hit this ~100ms floor
*per call*. If the controller needs CPU governor + CPU freq (x4 policies) +
GPU freq + GPU busy + FPS pipeline every tick, that is many separate calls
at ~100ms each — batching multiple reads into one call (as both the Test 5
snapshot and Test 6 hardware-profile dump already do, via
`echo TAG=$(cat path); echo TAG2=$(cat path2); ...`) is not an optimization,
it is a requirement once the tick rate gets fast. Whether this floor is
low enough for a real-time control loop, or whether `AutoTdpController`
needs a persistent-shell architecture instead of one-shot `xsu -c` calls,
is an open design question for controller-design time — resolving *why*
the stdin (persistent-shell) method fails silently is the prerequisite for
that path, and was NOT diagnosed by either probe.

## Real failure mode found: ~126 second stall under heavy Dolphin load

`results/xsu_benchmark_run1/other/xsu_bench_logcat_dump.txt`, lines 51-54:
sample 4 logged at `01:17:43.289`, sample 5 logged at `01:19:49.938` — a gap
of **126 seconds** between two supposedly-3-second-apart samples, both
during active Dolphin (`org.dolphinemu.dolphinemu`) gameplay. The FPS/
`dumpsys` leg of the recovering sample reported a normal-looking
`pipeline_ms=318` — the stall was isolated to the batched CPU/GPU sysfs
snapshot call, which came back with every field as `?` once it finally
returned:

```
[sample 5] ... pipeline_ms=318
  p0:gov=,freq=? p2:gov=?,freq=? p5:gov=?,freq=? p7:gov=?,freq=? | gpu_freq_hz=? gpu_busy_raw=?
```

**Root cause not confirmed.** Two hypotheses, neither verified:
1. A grandchild process (e.g. a `cat` shelled out from the xsu-spawned
   shell) blocked in an uninterruptible kernel wait, unreachable by
   `Process.destroyForcibly()` on the immediate child.
2. Heavy Dolphin CPU/GPU load caused a system-wide scheduling stall
   affecting the probe app's own threads (including whatever thread would
   have enforced a timeout), not a blocked syscall specifically.

An attempt to add descendant-process killing on timeout (via
`Process.toHandle()`/`ProcessHandle`) was made and reverted — **that API
does not exist on Android's `java.lang.Process` at all**, confirmed by a
failed compile, not a runtime check. The only mitigation implemented is a
short, explicit timeout (a few seconds) specifically on the batched
snapshot call, plus logging that call's own duration separately from the
FPS pipeline's — bounding the damage and making the anomaly visible in the
result file directly, rather than requiring manual logcat timestamp math to
even notice it happened. **This is not a fix — `AutoTdpController` needs to
treat "this call might just not return" as a real, recurring condition
under heavy load, not an edge case.**

## Root cause found: `xsud` crashes (SIGSEGV) on long `-c` commands — this is the "100% empty CSV" bug, and likely the source of several `apl`-side reboot incidents (2026-07-26)

Traced from `research/ab-logger`'s empty-CSV bug (`apl/STATUS.md` INCIDENT #2
and #3 — two full A/B sessions where every sample came back
`?`/`n/a`/`no layer matched` across the board). `LoggerSession.sampleOnce()`
combines the foreground-app check, SurfaceFlinger layer list, and the full
CPU/GPU/thermal/fan/battery snapshot into one `xsu -c "<big multi-statement
string>"` call (`buildCombinedCommand()` + `buildFullSnapshotCommand()`) —
on the AYANEO Pocket FIT, with its 19 CPU + 8 GPU thermal zones, this string
is **~3150 characters**. Reproduced live on-device, outside any app, by
pushing that exact command text to `/data/local/tmp/` and running
`xsu -c "$(cat ...)"` directly over `adb shell` repeatedly:

- Result was inconsistent per call: sometimes an explicit protocol-level
  rejection (`a obsolete xsu found, suggest close socket! / receive an
  invalid package, exit!`, exit code 165), sometimes **zero bytes of output
  with no error at all** — this second mode is the exact, direct mechanism
  behind the "100% empty" CSV rows: `PowerFanProbe.parseBlockTags()` finds
  no `===TAG===` markers because the underlying `xsu` call produced nothing,
  so every field in `LoggerSession.buildCsvRow()` falls back to its `"?"`/
  `"n/a"` default.
- `dmesg`, captured live during this: `init: Service 'xsud' (pid 9642)
  received signal 11` / `init: process with updatable components 'xsud'
  exited 4 times in 4 minutes` — **`xsud` (the root broker daemon) is
  segfaulting** on this input and being respawned by `init`. Calls that land
  during the crash/respawn window get either the "invalid package" error
  (hitting the dying socket) or silent emptiness (hitting the gap before the
  new `xsud` is listening again).
- Both mechanisms were caught in the same short session where a genuine
  **full device reboot** (`reboot,userrequested`, not just an `xsud`
  respawn) also occurred — though that specific reboot was later confirmed
  by the `apl` maintainer to have been a deliberate manual power-cycle (to
  regain a frozen GUI), not something triggered by this test. Whether
  *repeated* `xsud` segfaults can, on their own, cascade into the kind of
  `system_server`/`BatteryService` instability documented in `apl/STATUS.md`
  INCIDENT #1 and #3 remains a plausible but **unconfirmed** connection —
  flagged here, not proven.

### Bisection: it's raw command length, not the number of `$(...)` subshells

Two hypotheses were tested head-to-head at matched total length (~3000
chars): **(A)** few (6) subshells each with a long inline argument, vs
**(B)** many (73) small subshells, matching the real command's shape. Both
failed 5/5 identically — ruling out subshell/fork count as the driver and
pointing at raw byte length of the single `-c` argument as the relevant
variable.

Bisecting length alone (`echo A=$(cat /proc/version 2>/dev/null); ` repeated
N times, 3-5 trials per size), on a device where `xsud` had already
segfaulted and respawned several times that session:

| length (chars) | failure rate |
|---|---|
| 800 | 0/3 |
| 900 | 0/3 |
| 1000 | 0/3 |
| 1050 | 3/5 |
| 1100 | 5/5 (was 1/3 on an earlier, fresher pass) |
| 1200 | 3/3 |
| 1500-3000 | consistently fails |

**The transition is a fuzzy band (~1000-1200 chars), not a sharp cutoff** —
consistent with a genuine race (buffer contents/size depending on how the
underlying socket read is chunked) rather than a fixed, deterministic buffer
size. Note also the caveat in the table: the exact failure rate at a given
length was not stable across the session — early passes tolerated lengths
that failed reliably later, suggesting `xsud`'s crash history may itself
degrade its short-term reliability (consistent with the project's other
reports of `xsud` being harder to trust the longer/heavier a session runs).

### Validated practical fix: split any combined command into chunks under ~800 chars

- A synthetic 697-char command survived **15/15** consecutive calls spaced
  5 seconds apart (matching `LoggerService.SESSION_INTERVAL_MS`) — i.e. a
  75-second window with zero failures.
- The real `LoggerSession` combined command (the actual ~3150-char string,
  not synthetic filler) was split on statement boundaries (`"; "`) into 5
  chunks of ≤790 chars each and each chunk run 5x: **0/5 failures on every
  chunk**, and each chunk returned real, correctly-tagged data (269
  `dumpsys`-derived lines in the first chunk, 10-11 sysfs key=value lines in
  each of the rest).
- **Recommendation for `ab-logger`/any future `xsu`-based batching code
  (including `pulse-for-aya`'s `RootExec` if it is ever changed to batch
  multiple reads into one call)**: keep any single `xsu -c` argument under
  roughly **800 characters** as a practical safety margin (the measured
  fuzzy-failure band starts around 1000). This directly conflicts with
  `apl/STATUS.md` INCIDENT #2's mitigation of combining *all* per-sample
  reads into one call to reduce process-spawn count — the fix is a middle
  ground (e.g. 4-5 medium calls instead of 1 giant one or 4 fully separate
  ones), not a reversion to the original per-attribute call pattern.

### Method note

All of the above was reproduced directly over `adb shell "xsu -c
\"$(cat /data/local/tmp/<file>)\""` (command text pushed to a file first,
then substituted as a single shell argument) — not through the app itself.
Passing the same multi-statement string as a literal, nested-quoted `adb
shell xsu -c '...'` argument does **not** reproduce this faithfully: `adb
shell` joins all of its own argv elements with a single space before
sending them to the device's default shell for a second round of parsing,
which silently mangles embedded quotes/`$()` and produces a *different*,
unrelated shell syntax error rather than exercising `xsu`'s own handling of
a long argument. Use the file + command-substitution approach (or a real
`ProcessBuilder("xsu", "-c", command)` call, which never has this
double-parsing problem) for any future test in this area.

## Follow-up: call frequency / concurrency is a much weaker trigger than command length (2026-07-26, same day)

Natural complementary question to the section above: `pulse-for-aya`'s
`RootExec.executeAsRoot()` (`research/pulse-for-aya/app/src/main/java/com/kei/pulse/root/RootExec.kt`)
is a plain one-command-per-call pass-through — every caller in that module
sends its own short single-attribute command (a `cat` of one sysfs node, a
single `settings put`, etc.), never a combined multi-statement string. So
the long-command bug above almost certainly does **not** apply to
`pulse-for-aya` directly. The original suspected mechanism behind
`ab-logger`'s first incident (`STATUS.md` INCIDENT #1, before the
call-combining mitigation) was instead **call frequency/concurrency** —
"3-4 separate `xsu` process spawns every 2 seconds", `ab-logger` and
`pulse-for-aya` polling concurrently. Tested that variable directly, same
method as above (short real command: `cat
/sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq`, well under the
length danger zone):

- **Sequential calls, one at a time, at decreasing intervals** (2000ms down
  to 0ms/back-to-back, 15 calls per interval): **0/15 failures at every
  interval tested**, including 0ms. Pure sequential spawn rate, on its own,
  did not reproduce any instability in this test.
- **Concurrent (parallel) calls fired at the same instant** (2, 3, 4, 6, 8
  parallel processes, 3 rounds each): **0/69 calls failed**, but `dmesg`
  caught one real `xsud` crash (`received signal 6` — `SIGABRT` this time,
  not the `SIGSEGV` from the length bug) somewhere during this set, with no
  corresponding visible failure on the caller side (crash+respawn was fast
  enough that no in-flight call was actually dropped this time).
- **Sustained realistic load** (4 parallel calls every 2s, 20 rounds, 80
  calls over ~40s — deliberately matching the original incident's described
  pattern): **0/80 failures**, no additional crash caught in that specific
  window.

**Conclusion: concurrent/frequent short `xsu` calls are a real but much
rarer trigger than long commands** — one crash surfaced across ~254 total
calls in this session, none of which caused a visible caller-side failure
except the one already known long-command mode. This does **not** clear
concurrent polling as safe, it just means synthetic load alone (no game,
no competing GPU/thermal/binder traffic) rarely reproduces it — the
original incidents all happened with a real game running concurrently.
**Reproducing the original incident's crash reliably likely needs real
competing system load, not just concurrent `xsu` spawns in isolation** —
this remains an open question, not resolved here. Practical implication for
`pulse-for-aya`: this session found no evidence that its existing
one-command-per-call pattern needs to change, but it also doesn't
positively clear frequent/concurrent polling (e.g. a tight `AutoTdpController`
loop) as safe under real gameplay load — treat that as still open, not
confirmed either way.

## Not yet executed at the time of this migration

- Test 6 (full CPU `scaling_available_frequencies` OPP table dump) and
  Test 7 (`scaling_governor` write+verify+restore) were specified for the
  v2 probe but no on-device run results were captured before this repo
  migration — re-run these against the probe kept in this directory
  (`app/`) if that data is still needed; the code exists, it just wasn't
  exercised yet in the runs archived under `results/`.

## Where the code that produced this data lives

`app/` in this directory is the last (v2) iteration's source — the one
with the timeout fix and tagged-parsing described above. Earlier iterations
(`test_app`, `xsu_benchmark` v1) are not kept as separate projects; their
only lasting value was the empirical results now preserved under
`results/` and synthesized into this document.
