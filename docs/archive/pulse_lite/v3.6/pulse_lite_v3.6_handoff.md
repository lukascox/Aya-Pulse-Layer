---
# pulse_lite AutoTDP Daemon -- Handoff Substrate

## Session: v3.6 -- per-core max CPU busy% signal + GPU-driven CPU safe floor
**Date:** 2026-07-05
**Status:** v3.6 script drafted and delivered as file; not yet deployed, run, or soak-tested on device. Per-core CPU thresholds are carried over unvalidated placeholders from v3.5; behavior under real per-core signal is untested.

---

## Context

`pulse_lite.sh` v3.5 decoupled CPU and GPU tier decisions into two independent state machines, using an aggregate `/proc/stat` "cpu " line (average busy% across all 8 cores) as the new CPU load signal. Live testing showed this caused a regression: a game bottlenecked on 1-2 hot threads got averaged down to ~20-30% CPU busy across 8 cores, so `CPU_TIER` never escalated past idle/medium even while `GPU_TIER` correctly climbed to heavy (`gpu_busy=66-78%`), leaving CPU clocks pinned at ECO levels during real gameplay. Side-by-side screenshots confirmed the regression directly: stock caps (daemon off) produced FPS=60 at CPU=23% avg, while v3.5 running produced FPS=35 at CPU=36% avg with CPU_TIER stuck at idle/medium the whole session. This session's outcome: v3.6 replaces the aggregate CPU signal with a per-core max busy% signal (mirroring the existing `read_max_temp()` worst-case-wins pattern) and adds a GPU-driven safety floor that prevents CPU_TIER from sitting at idle whenever GPU_TIER is heavy.

---

## Decisions Made

- **Replaced the aggregate `/proc/stat` "cpu " line with per-core parsing, taking the MAX busy% across all cores as the CPU load signal** -- justification: the aggregate average masked single-thread-bound workloads (confirmed root cause of the v3.5 FPS regression, evidenced by screenshots and log showing `cpu_busy=14-31%` while GPU sat in heavy at `gpu_busy=66-78%`); per-core max mirrors the already-proven `read_max_temp()` worst-case pattern used since v3.2 for thermal zones.
- **Added a GPU_TIER-driven floor that forces CPU_TIER out of idle (up to medium minimum) whenever GPU_TIER=heavy** -- justification: user explicitly requested this as "GPU_TIER safe lock" in this session; reasoning is that no game genuinely pushing GPU into heavy is realistically fine running CPU-side logic at ECO clocks, so the risk of under-clocking CPU in a GPU-heavy scene is judged worse than slightly over-provisioning CPU.
- **Scoped the floor as a safety net layered on top of the per-core fix, not a replacement for it** -- per-core detection is intended to be the primary fix; the floor is meant to catch residual cases per-core detection might still miss (e.g. sampling noise, sub-tick CPU bursts).
- **Left CPU domain hysteresis threshold values (`CPU_HEAVY_UP=70`, `CPU_HEAVY_DOWN=45`, `CPU_MEDIUM_UP=20`, `CPU_MEDIUM_DOWN=10`) numerically unchanged from v3.5**, despite switching from an average signal to a max signal -- decision made to isolate one variable at a time (signal source vs threshold values) so a soak test can attribute behavior changes correctly; these values are flagged as likely needing re-tuning, not assumed correct.
- **Did not touch GPU_TIER logic, GPU thresholds, or thermal sub-tier logic in this version** -- session was explicitly scoped by the user to the CPU signal fix plus the GPU floor only.
- **Floor implementation does not reset CPU_MEDIUM_TICK / CPU_HEAVY_DOWN_TICK counters** -- it only clamps the resolved `CPU_TIER` value after CPU's own hysteresis has run, so CPU's internal timers keep progressing normally underneath the clamp rather than being repeatedly reset by the floor.

---

## Findings / Facts Established

**Root cause of v3.5 regression (from user-supplied log and screenshots)**
- Log evidence: `20:14:38 cpu_tier=idle/heavy_high cpu_busy=13% ... | gpu_tier=heavy/heavy_high gpu_busy=49%` through `20:16:12 cpu_tier=medium/heavy_high cpu_busy=27% ... | gpu_tier=heavy/heavy_high gpu_busy=78%` -- CPU_TIER stayed at idle/medium (cpu_busy 13-31%) for the entire session while GPU_TIER independently reached heavy multiple times [confirmed, directly quoted from user-supplied v3.5 session log].
- Screenshot comparison: stock caps (`p0=2265600 p2=3148800 p5=2956800 p7=3052800`, daemon off) showed `FPS=60`, `CPU=23%` [confirmed, IMG_0210/IMG_0211 screenshots]; v3.5 running with CPU capped to ECO (`p0=1344000... p7=1824000`) showed `FPS=35`, `CPU=36%` [confirmed, same screenshots].
- Interpretation: higher CPU busy% (36%) at lower clock combined with lower FPS (35 vs 60) is consistent with CPU becoming the bottleneck after clock reduction -- the same workload takes proportionally longer in CPU time at lower frequency [inferred from the confirmed screenshot data, not independently profiled via a dedicated CPU-thread trace].
- Attributed root cause: the game likely concentrates load on 1-2 threads (main/render thread) rather than spreading evenly across all 8 cores; averaging across all cores in v3.5's `read_cpu_busy()` diluted this to ~20-30%, never crossing `CPU_HEAVY_UP=70` [inferred from the pattern of the data; not confirmed via direct per-thread CPU profiling this session].

**v3.6 per-core signal design**
- `read_cpu_busy_max()` iterates every `cpuN` line in `/proc/stat` (0 to `CORE_COUNT-1`), computing delta-based busy% per core the same formula as v3.5's aggregate version (`(DTOTAL - DIDLE) * 100 / DTOTAL`), and returns the maximum value across all cores [confirmed, this session's code].
- `CORE_COUNT` is resolved dynamically at startup by probing `/sys/devices/system/cpu/cpuN` directory existence, not hardcoded to 8 [confirmed, this session's code; not verified against actual on-device core enumeration].
- Per-core previous-tick state is stored via `eval`-based dynamic variable names (`PREV_CORE_TOTAL_$idx`, `PREV_CORE_IDLE_$idx`) since POSIX `sh` (the shell this script targets) has no native arrays [assumed -- functionally equivalent to arrays but not benchmarked for performance overhead with 8 cores at a 2s tick interval].
- Requires the same priming-call pattern as v3.5 (`read_cpu_busy_max >/dev/null` before the main loop) to establish per-core baselines before the first real reading [confirmed, carried over design pattern from v3.5].

**v3.6 GPU-driven floor design**
- Floor logic: `if [ "$GPU_TIER" = "heavy" ] && [ "$CPU_TIER" = "idle" ]; then CPU_TIER=medium; FLOOR_APPLIED=1; fi`, applied once per tick after both CPU_TIER and GPU_TIER have independently resolved [confirmed, this session's code].
- The floor only prevents idle while GPU is heavy; it does not force CPU into heavy, and does not affect CPU_TIER when GPU_TIER is idle or medium [confirmed, this session's code, matches user's explicit request scope].
- A new `floor=${FLOOR_APPLIED}` field was added to the per-tick log line for observability, so soak-test logs will show exactly when the floor fired [confirmed, this session's code].

**Unchanged from v3.5 (carried forward as-is)**
- All GPU domain thresholds and hysteresis (`HEAVY_UP=70`, `HEAVY_DOWN=45`, `MEDIUM_UP=20`, `MEDIUM_DOWN=10`, `HEAVY_DOWN_TICKS=5`, `GPU_MEDIUM_STUCK_TICKS=8`) [confirmed, unchanged].
- All thermal sub-tier thresholds and the CTEMP-only-affects-CPU / GTEMP-only-affects-GPU scoping introduced in v3.5 [confirmed, unchanged].
- Force-tier testing hooks (`/sdcard/pulse_lite.force`, `.force_cpu`, `.force_gpu`) [confirmed, unchanged].

**Operational commands (carried over, path updated for v3.6)**
- Deploy: `adb push pulse_lite_v3.6.sh /sdcard/pulse_lite.sh`
- Stop: `adb shell 'touch /sdcard/pulse_lite.stop'`
- Status: `adb shell sh /sdcard/pulse_lite.sh status`
- Force both domains (legacy): `adb shell 'echo heavy > /sdcard/pulse_lite.force'`
- Force CPU only: `adb shell 'echo heavy > /sdcard/pulse_lite.force_cpu'`
- Force GPU only: `adb shell 'echo heavy > /sdcard/pulse_lite.force_gpu'`

---

## Open Questions / Unresolved

- CPU domain threshold values (`CPU_HEAVY_UP=70`, `CPU_HEAVY_DOWN=45`, `CPU_MEDIUM_UP=20`, `CPU_MEDIUM_DOWN=10`) were carried over numerically unchanged from v3.5's average-based signal to v3.6's max-based signal -- these are very likely miscalibrated for the new signal (a single hot core hitting 70% is a much lower bar than 8 cores averaging 70%) and need dedicated re-tuning via soak test.
- `CORE_COUNT` detection via probing `/sys/devices/system/cpu/cpuN` directories has not been verified against this specific device's actual core count/enumeration (expected 8 based on the 4-policy layout referenced throughout prior versions, but not directly confirmed this session).
- The `eval`-based per-core state storage pattern (`PREV_CORE_TOTAL_$idx` etc.) has not been tested for correctness or performance on-device -- needs verification that it behaves correctly across ticks and doesn't introduce meaningful CPU overhead of its own at a 2s interval.
- Whether the GPU-driven floor will trigger often or rarely in practice is unknown -- depends on how well the per-core fix alone resolves the original problem; if per-core detection alone is sufficient, `FLOOR_APPLIED=1` should appear rarely in logs, and its frequency is a useful diagnostic the user should watch for in the first soak test.
- No test plan for v3.6 has been executed yet -- needs a repeat of the same workload/scenario that exposed the v3.5 regression (the game shown in IMG_0210/IMG_0211) to confirm FPS returns to ~60 with the daemon active.
- It remains unconfirmed whether per-core max busy% alone (without the floor) would have been sufficient to fix the regression, or whether the floor is doing meaningful additional work -- user requested both fixes together in this session, so no isolated A/B test of "per-core only" was performed.

---

## Proposed Scope

- **Objective:** Empirically validate that v3.6's per-core max CPU busy% signal, combined with the GPU-driven CPU floor, restores FPS/CPU-clock behavior on GPU-heavy/CPU-bottlenecked workloads to parity with the stock (daemon-off) baseline, without regressing the CPU/GPU decoupling benefits already validated for v3.5's Switch-emulation and launcher/menu scenarios.
- **Definition of Done:**
  - Re-run the exact scenario from IMG_0210/IMG_0211 (same game/scene) with `pulse_lite_v3.6.sh` active; confirm FPS returns to at or near 60 (vs the 35 observed with v3.5), with `pulse_lite.log` showing `cpu_tier` reaching `medium` or `heavy` (not stuck at `idle`) during the GPU-heavy window.
  - Confirm in the log whether `floor=1` appears during that session, and how often, to assess whether the per-core signal alone is carrying the fix or the floor is doing significant work.
  - Re-run the original v3.5 validation scenarios (Switch emulation GPU-heavy/CPU-light, launcher/menu GPU-spike/CPU-idle) to confirm the decoupling benefits from v3.5 are not lost -- specifically that CPU does not get forced to heavy just because GPU is heavy in the Switch emulation case (the floor only clamps up to medium, not heavy, so this should hold, but needs empirical confirmation).
  - Based on real per-core busy% data collected during the above tests, propose re-tuned `CPU_HEAVY_UP/DOWN` and `CPU_MEDIUM_UP/DOWN` values if the current placeholders prove poorly calibrated.
  - Verify `CORE_COUNT` resolves correctly on-device via the log line `core_count=$CORE_COUNT` emitted at startup.

---

## Candidate Relations

none

---

## Raw Artifacts

**v3.6 changelog block (as delivered in script header):**
```sh
# CHANGELOG v3.6 (vs v3.5):
# - CRITICAL FIX: v3.5's aggregate /proc/stat "cpu " line (average busy% across
#   all 8 cores) failed to detect single-thread-bound workloads.
# - Replaced read_cpu_busy() (aggregate) with read_cpu_busy_max() (per-core,
#   max-of-cores), mirroring the existing read_max_temp() pattern.
# - NEW: GPU_TIER safe floor lock on CPU_TIER. If GPU_TIER=heavy, CPU_TIER is
#   never allowed to sit at idle -- floored to at least medium.
# - The floor is applied AFTER CPU_TIER's own hysteresis resolves each tick,
#   as a clamp. Does not reset CPU_MEDIUM_TICK/CPU_HEAVY_DOWN_TICK counters.
# - No change to GPU_TIER logic, GPU thresholds, or thermal sub-tier logic.
```

**Per-core CPU busy% reader (`read_cpu_busy_max`, new in v3.6):**
```sh
CPU_STAT_INIT=0
PREV_CORE_TOTAL=""
PREV_CORE_IDLE=""
CORE_COUNT=0

init_core_arrays() {
  i=0
  while [ -f "/sys/devices/system/cpu/cpu${i}/online" ] || [ "$i" -eq 0 ]; do
    [ -d "/sys/devices/system/cpu/cpu${i}" ] || break
    i=$((i + 1))
  done
  CORE_COUNT=$i
}

read_cpu_busy_max() {
  MAXBUSY=0
  idx=0
  while [ "$idx" -lt "$CORE_COUNT" ]; do
    LINE=$(grep "^cpu${idx} " "$PROC_STAT" 2>/dev/null)
    if [ -z "$LINE" ]; then
      idx=$((idx + 1))
      continue
    fi
    set -- $LINE
    U=${2:-0}; N=${3:-0}; S=${4:-0}; I=${5:-0}; IO=${6:-0}; IRQ=${7:-0}; SIRQ=${8:-0}; ST=${9:-0}
    TOTAL=$((U + N + S + I + IO + IRQ + SIRQ + ST))
    IDLE=$((I + IO))

    PT=$(eval echo \$PREV_CORE_TOTAL_${idx})
    PI=$(eval echo \$PREV_CORE_IDLE_${idx})
    PT=${PT:-0}
    PI=${PI:-0}

    eval PREV_CORE_TOTAL_${idx}=$TOTAL
    eval PREV_CORE_IDLE_${idx}=$IDLE

    if [ "$CPU_STAT_INIT" -eq 1 ]; then
      DTOTAL=$((TOTAL - PT))
      DIDLE=$((IDLE - PI))
      if [ "$DTOTAL" -gt 0 ]; then
        BUSY=$(( (DTOTAL - DIDLE) * 100 / DTOTAL ))
        [ "$BUSY" -gt "$MAXBUSY" ] && MAXBUSY=$BUSY
      fi
    fi

    idx=$((idx + 1))
  done
  CPU_STAT_INIT=1
  echo "$MAXBUSY"
}
```

**GPU-driven CPU floor (new in v3.6):**
```sh
FLOOR_APPLIED=0
if [ "$GPU_TIER" = "heavy" ] && [ "$CPU_TIER" = "idle" ]; then
  CPU_TIER=medium
  FLOOR_APPLIED=1
fi
```

**v3.5 regression log evidence (from user-supplied session log, root cause of this session):**
```
20:13:55 cpu_tier=idle/heavy_high cpu_busy=14% cpuT= caps: p0=1344000 p2=1708800 p5=1708800 p7=1824000 | gpu_tier=idle/heavy_high gpu_busy=7% gpuT= gpu_cap=9
20:14:20 cpu_tier=idle/heavy_high cpu_busy=7% cpuT= caps: p0=1344000 p2=1708800 p5=1708800 p7=1824000 | gpu_tier=medium/heavy_high gpu_busy=48% gpuT= gpu_cap=7
20:14:37 WARN: forced GPU heavy after 8 stuck ticks in medium (gpu=49%)
20:14:38 cpu_tier=idle/heavy_high cpu_busy=13% cpuT= caps: p0=1344000 p2=1708800 p5=1708800 p7=1824000 | gpu_tier=heavy/heavy_high gpu_busy=49% gpuT=37100 gpu_cap=6
20:15:11 cpu_tier=medium/heavy_high cpu_busy=21% cpuT= caps: p0=1344000 p2=1708800 p5=1708800 p7=1824000 | gpu_tier=heavy/heavy_high gpu_busy=34% gpuT=43400 gpu_cap=6
20:16:12 cpu_tier=medium/heavy_high cpu_busy=27% cpuT= caps: p0=1344000 p2=1708800 p5=1708800 p7=1824000 | gpu_tier=heavy/heavy_high gpu_busy=78% gpuT=50000 gpu_cap=6
```

**Screenshot data summary (from IMG_0210.jpg, IMG_0211-2.jpg):**
```
Daemon OFF (stock caps): FPS=60, CPU=23% avg, GPU=78% 61C, caps p0-p7 at STOCK values (2265600/3148800/2956800/3052800)
Daemon ON (v3.5, ECO caps): FPS=35, CPU=36% avg, GPU=63% 54C, caps p0-p7 at ECO values (1344000/1708800/1708800/1824000)
```

**Operational commands:**
```sh
adb push pulse_lite_v3.6.sh /sdcard/pulse_lite.sh
adb shell 'touch /sdcard/pulse_lite.stop'
adb shell sh /sdcard/pulse_lite.sh status
adb shell 'echo heavy > /sdcard/pulse_lite.force'
adb shell 'echo heavy > /sdcard/pulse_lite.force_cpu'
adb shell 'echo heavy > /sdcard/pulse_lite.force_gpu'
```
---
