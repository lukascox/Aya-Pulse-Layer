# xsu Capability Probe (formerly "XsuBench v2") — throwaway probe (NOT the real app)

This is the surviving source of a two-round research probe run before
writing `AutoTdpController.kt`/`XsuShell.kt` for real (`../../app/`). It was
originally the second of three throwaway apps (`test_app`, `xsu_benchmark`
v1, `xsu_benchmark_v2`); the other two were not kept as separate projects
after this repo migration — **see `FINDINGS.md` in this directory for the
full consolidated conclusions from all three**, and `results/` for the
preserved raw on-device output that backs those conclusions.

Same "args"-only `xsu` invocation throughout (`ProcessBuilder("xsu", "-c",
cmd)`) — the "stdin" method stays confirmed broken and unused, see
`FINDINGS.md`.

## What changed vs v1, and why

- **Test 5 step (d) fix (the main point of v2).** Run 1 found the batched
  CPU/GPU snapshot call stalled ~126s once under heavy Dolphin load, and the
  stall was invisible in the result file (only `pipeline_ms=318`, which only
  ever measured the `dumpsys` leg). Fixed two ways: a short explicit timeout
  (`SNAPSHOT_TIMEOUT_SEC = 4`) just for this call, and its own logged
  duration (`snapshot_ms=`) on every single sample, separate from
  `pipeline_ms`. Also switched this call's parsing from positional line
  indexing to tagged `KEY=value` output (`FpsPipeline.parseTaggedLines`) --
  more robust, fails visibly on a specific missing key instead of silently
  shifting every subsequent field.
- **Test 4 now carries an explicit warning** in its own output: the raw
  `gpubusy` counter is confirmed (per `HARDWARE_PROFILE_v6_en.md`, 7 runs) to
  wrap/reset between reads, not just a theoretical overflow edge case. This
  app only confirms the node is reachable through the app channel -- fixing
  the signal itself (smoothing / secondary signal) is explicitly deferred to
  `AutoTdpController` design time.
- **Test 6 (new):** full `scaling_available_frequencies` OPP table for all 4
  CPU policies, batched into one `xsu` call, written to its own persistent
  reference file `/sdcard/pulsefit_hw_profile.txt` (not just appended to the
  benchmark log) with the already-confirmed GPU pwrlevel table prepended.
- **Test 7 (new):** confirms `scaling_governor` itself is writable (every
  prior write test was a frequency cap, never the governor). Runs on
  policy0 only, picks its test governor from Test 6's own
  `scaling_available_governors` result -- does not re-query or guess.
- **5th latency category (new):** `batched_snapshot_call`, tracked
  separately per the same reasoning as Test 5 step (d) above -- this is the
  call that stalled, so it gets its own stats instead of being folded into
  an aggregate.
- Attempted (and reverted): killing descendant processes on timeout via
  `Process.toHandle()`/`ProcessHandle`. Confirmed at compile time that this
  desktop-JDK-9+ API is not present on Android's `java.lang.Process` at all.
  `XsuShell.exec()` still only calls `destroyForcibly()` on the immediate
  process -- the underlying stall's root cause remains undiagnosed, out of
  scope for this probe either way.

## Install and run

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Second terminal, leave running during the test:

```bash
adb logcat | grep -i avc
```

On the phone: open **XsuBench2**, tap **"Run Benchmark v2"**. Same ~2-3
minute total runtime as v1 (Test 5 is still the fixed 90s window, 30
samples x 3s, unchanged parameters). For a real stress test of the Test 5
step (d) timeout fix, run it once with something heavy on screen (Dolphin,
if you want to try to reproduce the run-1 conditions) and once with
something light, to compare `snapshot_ms` distributions.

## Pull results afterward

```bash
adb pull /sdcard/xsu_benchmark_result.txt
adb pull /sdcard/pulsefit_hw_profile.txt
adb logcat -d | grep XSU_BENCH > xsu_bench_v2_logcat_dump.txt
```

`xsu_benchmark_result.txt` is the same rolling benchmark log as v1 (refreshed
after every section). `pulsefit_hw_profile.txt` is new in v2 -- a standalone
reference document (GPU table + live CPU OPP table), not a pass/fail log.

## Reading the results

- If Test 5 samples show `snapshot_error=TIMEOUT after 4s` anywhere, that's
  the v2 fix catching a real anomaly, not a false failure -- it's the same
  class of event that stalled ~126s in run 1, now bounded to 4s and clearly
  flagged with a `**` marker line instead of silently vanishing.
- `pulsefit_hw_profile.txt`'s CPU section is genuinely new reference data --
  the full OPP table per policy was never captured before v2 (prior
  versions only ever used 4 discrete points per cluster).
- Test 7 tells you whether `AutoTdpController` can change governor
  dynamically (e.g. `schedutil` for normal play, `performance` when the FPS
  controller needs to escalate) or must stick to capping frequency within
  whatever governor AYASpace already set.
