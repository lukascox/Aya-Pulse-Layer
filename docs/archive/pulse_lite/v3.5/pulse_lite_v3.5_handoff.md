---
# pulse_lite AutoTDP Daemon — Handoff Substrate

## Session: v3.5 — decoupling CPU and GPU tier logic via /proc/stat-based CPU load signal
**Date:** 2026-07-05
**Status:** v3.5 script drafted and delivered as file; not yet deployed, run, or soak-tested on device. All new CPU-domain thresholds are untested working values (PLACEHOLDER).

---

## Context

`pulse_lite.sh` is a shell daemon (v3.4 baseline) that manages CPU frequency caps (`scaling_max_freq` per cpufreq policy) and GPU power level caps (`kgsl-3d0/max_pwrlevel`) on a Snapdragon 8 Gen 3-class SoC in the KONKR Pocket FIT handheld. The problem driving this session: a single shared `TIER` state machine drove both CPU and GPU caps together, which broke down in two observed scenarios — Switch emulation (GPU 80-90% busy, CPU ~20% busy, but all CPU cores forced to max because GPU was in heavy) and launcher/menu/GBA content (brief GPU busy% spikes triggering unwanted CPU escalation via the `MEDIUM_STUCK_TICKS` safety valve at `gpu=12-13%`, confirmed in `pulse_lite.log`). User explicitly scoped this session to "krok 1 - cpu-load-based tier" plus splitting CPU/GPU independence, deferring full FPS-based detection to a future step. Outcome: v3.5 was produced with two fully independent tier state machines (CPU_TIER/CPU_HTIER and GPU_TIER/GPU_HTIER), driven by a new aggregate `/proc/stat`-derived CPU busy% signal.

---

## Decisions Made

- **Scoped this session to CPU-load-based tier + CPU/GPU decoupling only, deferring FPS detection** — user explicitly said "nie, na razie krok 1 - cpu-load-based tier" and named "przykręcanie CPU niezależnie od GPU i jeszcze bardziej niż teraz" as the immediate next step, not FPS detection via `dumpsys SurfaceFlinger`/`gfxinfo` (discussed as an option in prior turn, not selected for this session).
- **Split the single `TIER` state machine into two fully independent state machines, `CPU_TIER`/`CPU_HTIER` and `GPU_TIER`/`GPU_HTIER`** — because the CPU-bound-Switch-emulation and GPU-spike-in-launcher cases both stem from the same root cause: one shared tier variable forcing correlated behavior between two independently-loaded subsystems.
- **Used the aggregate `cpu ` line in `/proc/stat` (all cores combined) as the CPU load signal, not per-policy/per-cluster** — chosen as a deliberately coarse first cut matching the explicit "krok 1" scope; per-cluster granularity (e.g. capping policy0 low while keeping policy7 high) was explicitly deferred as the next step, not solved in v3.5.
- **Mirrored GPU's existing hysteresis structure (busy thresholds, heavy-down debounce) onto the new CPU domain with separate constant names** (`CPU_HEAVY_UP`, `CPU_HEAVY_DOWN`, `CPU_MEDIUM_UP`, `CPU_MEDIUM_DOWN`, `CPU_HEAVY_DOWN_TICKS`) rather than inventing a different algorithm — justification: reuse a structure already validated in production logs for GPU, reducing net-new untested logic to just the signal source itself.
- **Split thermal sub-tier reactions so CTEMP only affects CPU_HTIER and GTEMP only affects GPU_HTIER** — this is a behavior change from v3.2-v3.4's "worst-case-wins across both domains" rule; decided to match the same CPU/GPU independence principle as the tier split, on the reasoning that a hot GPU should not force CPU clocks down if CPU itself is cool, and vice versa. This is an [inferred] design consistency choice, not something the user explicitly requested for the thermal path — flag for review.
- **Kept the legacy `/sdcard/pulse_lite.force` file working as a "force both domains" shortcut, and added two new independent force files** (`/sdcard/pulse_lite.force_cpu`, `/sdcard/pulse_lite.force_gpu`) — decision made to preserve backward-compatible quick testing while adding the primary tool needed to validate the decoupling itself.
- **Did not implement per-cluster CPU load, FPS detection, or lowering ECO floor values below current thresholds in this version** — all three were discussed as future directions in the prior turn but explicitly deferred; only CPU-load-based tier + CPU/GPU decoupling was in scope for v3.5.

---

## Findings / Facts Established

**Root-cause evidence for the decoupling problem (from `pulse_lite.log`, prior session)**
- Confirmed live log entries: `18:07:23 tier=medium gpu=13% ... medium_tick=8` followed by `18:08:11 WARN: forced heavy after 30 stuck ticks in medium (gpu=12%, likely CPU-bound)` and `18:08:12 tier=heavy/heavy_high gpu=12%` — i.e. the v3.3/v3.4 safety valve escalated to heavy while GPU busy% was only 12% [confirmed, directly quoted from `pulse_lite.log`].
- A second occurrence: `18:14:47 tier=medium gpu=13% ... medium_tick=26` -> `18:14:56 WARN: forced heavy after 30 stuck ticks in medium (gpu=13%...)` [confirmed, directly quoted from `pulse_lite.log`].
- These entries used `MEDIUM_STUCK_TICKS` values of 30 (pre-v3.4 tuning) and 8 (post-v3.4 tuning) in different sessions of the same log file, both showing the same false-positive escalation pattern at low GPU busy% [confirmed, per log inspection].
- Switch emulation scenario (GPU 80-90%, CPU ~20%) was reported by the user directly in conversation, not captured in the attached log excerpts reviewed this session [confirmed as user-reported, not independently log-verified this session].

**New CPU load signal design (v3.5)**
- `read_cpu_busy()` parses the aggregate `cpu ` line from `/proc/stat`: fields `user nice system idle iowait irq softirq steal`, computing `TOTAL = user+nice+system+idle+iowait+irq+softirq+steal` and `IDLE = idle+iowait`, then `busy% = (DELTA_TOTAL - DELTA_IDLE) * 100 / DELTA_TOTAL` between ticks [assumed -- standard `/proc/stat` delta-busy formula, not empirically validated against this specific device's `/proc/stat` field layout].
- This is an **aggregate all-core** signal, not per-policy/per-cluster -- explicitly flagged as a known limitation in the v3.5 changelog, matching the user's own scoping of "krok 1" [confirmed as intentional scope limit, per user's own message: "Następny krok - przykręcanie CPU niezależnie od GPU i jeszcze bardziej niż teraz"].
- The function requires a priming call before the main loop (`read_cpu_busy >/dev/null` at startup) to establish a `/proc/stat` baseline, since the first invocation of a delta-based counter has no prior sample [inferred, standard pattern for delta-based /proc/stat readers, not verified on-device this session].

**CPU domain thresholds (all new in v3.5, unvalidated)**
- `CPU_HEAVY_UP=70`, `CPU_HEAVY_DOWN=45`, `CPU_MEDIUM_UP=20`, `CPU_MEDIUM_DOWN=10` -- copied numerically from the existing GPU thresholds as placeholder starting values [assumed, explicitly not tuned to CPU-specific behavior].
- `CPU_HEAVY_DOWN_TICKS=5` (10s at `INTERVAL=2`) -- mirrors GPU's `HEAVY_DOWN_TICKS` value structurally [assumed].

**GPU domain (retained from v3.4, renamed for clarity)**
- `MEDIUM_STUCK_TICKS` renamed to `GPU_MEDIUM_STUCK_TICKS`, value unchanged at `8` (16s) [confirmed, direct carry-over from v3.4].
- All GPU busy%-driven thresholds (`HEAVY_UP=70`, `HEAVY_DOWN=45`, `MEDIUM_UP=20`, `MEDIUM_DOWN=10`, `HEAVY_DOWN_TICKS=5`) unchanged from v3.4 [confirmed].

**Thermal sub-tier threshold values (unchanged numerically from v3.3/v3.4, but scope changed)**
- `CPU_HOT=78000`, `CPU_HOTTER=85000`, `CPU_COOL_HOT=74000`, `CPU_COOL_HOTTER=81000`, `GPU_HOT=75000`, `GPU_HOTTER=82000`, `GPU_COOL_HOT=71000`, `GPU_COOL_HOTTER=78000` -- same numeric values as v3.4, still labeled PARTIALLY VALIDATED per the original v3.3 soak test note (`CPU_HOT` confirmed in right ballpark at `cpuT=78300-78700`; all others unvalidated) [confirmed re: original validation status, carried forward unchanged].
- Scope change: in v3.2-v3.4, either CTEMP or GTEMP crossing threshold downgraded BOTH CPU and GPU sub-tiers (worst-case-wins across domains); in v3.5, CTEMP only drives `CPU_HTIER` and GTEMP only drives `GPU_HTIER` [confirmed, this session's code change].

**Operational commands (new and carried over)**
- Deploy: `adb push pulse_lite_v3.5.sh /sdcard/pulse_lite.sh`
- Stop: `adb shell 'touch /sdcard/pulse_lite.stop'`
- Status: `adb shell sh /sdcard/pulse_lite.sh status`
- Force both domains (legacy): `adb shell 'echo heavy > /sdcard/pulse_lite.force'`
- Force CPU only (new): `adb shell 'echo heavy > /sdcard/pulse_lite.force_cpu'`
- Force GPU only (new): `adb shell 'echo heavy > /sdcard/pulse_lite.force_gpu'`

---

## Open Questions / Unresolved

- The `/proc/stat` delta-busy formula and field ordering (`user nice system idle iowait irq softirq steal`) has not been empirically verified against this specific device's actual `/proc/stat` output -- needs a direct `adb shell cat /proc/stat` check before trusting `read_cpu_busy()` output.
- All CPU domain threshold values (`CPU_HEAVY_UP/DOWN`, `CPU_MEDIUM_UP/DOWN`, `CPU_HEAVY_DOWN_TICKS`) are copied numerically from GPU as placeholders -- needs a dedicated soak test with real CPU-bound and CPU-light workloads (e.g. the Switch emulation case) to determine whether 70%/45%/20%/10% are appropriate for aggregate all-core CPU busy%, which likely behaves very differently from single-GPU-pipeline busy%.
- The hypothesis that `GPU_MEDIUM_STUCK_TICKS` will fire less often now that CPU is decoupled from GPU's medium tier is stated in the changelog as "untested hypothesis" -- needs log evidence from a real session (ideally the same launcher/menu/GBA scenario that originally exposed the problem) to confirm.
- Splitting thermal sub-tier reactions (CTEMP only affects CPU, GTEMP only affects GPU) was an [inferred] design-consistency decision made by the assistant, not something the user explicitly requested for the thermal path specifically -- user should confirm this is desired behavior, since it changes safety margins (e.g. previously a hot GPU would also throttle CPU as an extra thermal margin; now it won't).
- Per-cluster/per-policy CPU load granularity (distinguishing "only the X4 core is loaded" from "all cores are loaded") is explicitly out of scope for v3.5 and deferred to a future step, per user's own framing ("Następny krok - przykręcanie CPU niezależnie od GPU i jeszcze bardziej niż teraz").
- FPS-based detection (via `dumpsys SurfaceFlinger --latency` or `dumpsys gfxinfo framestats`, discussed as options in the prior turn) remains fully deferred -- not started, not scoped for this version.
- No validation instructions/test plan for v3.5 have been executed yet -- this was requested as a deliverable alongside the handoff and script, and needs to be run through before deployment confidence can be established.

---

## Proposed Scope

- **Objective:** Empirically validate that v3.5's independent CPU-load-driven tier correctly decouples CPU frequency capping from GPU load, resolving both the Switch-emulation (GPU-heavy/CPU-light) and launcher/menu (GPU-spike/CPU-idle) false-positive escalation cases, before tuning any threshold values further.
- **Definition of Done:**
  - `adb shell cat /proc/stat` output format confirmed to match the field assumptions in `read_cpu_busy()`.
  - `pulse_lite_v3.5.sh` deployed via `adb push` and run through a Switch emulation session, with `pulse_lite.log` showing `gpu_tier=heavy` while `cpu_tier` stays at `medium` or `idle` (not forced to heavy just because GPU is heavy).
  - Log evidence from a launcher/menu/GBA session showing `cpu_tier` staying in `idle`/`medium` without a `WARN: forced GPU heavy` false-positive at low `gpu_busy` values (the specific pattern seen at `gpu=12-13%` in the prior log).
  - `/sdcard/pulse_lite.force_cpu` and `/sdcard/pulse_lite.force_gpu` each individually tested to confirm one domain can be forced while the other continues resolving independently from its own signal.
  - A decision recorded on whether the CTEMP/GTEMP thermal-sub-tier scope split (CPU-only vs GPU-only reaction) is the desired behavior, or whether cross-domain worst-case-wins thermal safety should be restored.

---

## Candidate Relations

none

---

## Raw Artifacts

**v3.5 changelog block (as delivered in script header):**
```sh
# CHANGELOG v3.5 (vs v3.4):
# - MAJOR: decoupled CPU and GPU tier decisions into two independent state
#   machines (CPU_TIER / CPU_HTIER and GPU_TIER / GPU_HTIER).
# - Added a new CPU load signal read directly from /proc/stat (aggregate all-
#   core busy%, NOT per-policy/per-cluster).
# - GPU_TIER retains the exact v3.4 GPU_BUSY-driven hysteresis, renamed
#   MEDIUM_STUCK_TICKS -> GPU_MEDIUM_STUCK_TICKS for clarity.
# - CPU_TIER gets its own mirrored hysteresis (CPU_HEAVY_UP/DOWN,
#   CPU_MEDIUM_UP/DOWN) and its own heavy->medium descent debounce
#   (CPU_HEAVY_DOWN_TICKS), currently UNVALIDATED PLACEHOLDER values.
# - Thermal sub-tier logic now split: CPU_HTIER reacts only to CTEMP,
#   GPU_HTIER reacts only to GTEMP (was worst-case-wins across both domains).
# - Force-tier testing hooks: /sdcard/pulse_lite.force (both domains, legacy),
#   /sdcard/pulse_lite.force_cpu, /sdcard/pulse_lite.force_gpu (new).
# - KNOWN LIMITATION: CPU_BUSY is aggregate "cpu " line in /proc/stat, not
#   per-policy/per-cluster. Per-cluster throttling is the NEXT step.
```

**New CPU load reader function (`read_cpu_busy`):**
```sh
PREV_CPU_TOTAL=0
PREV_CPU_IDLE=0
CPU_STAT_INIT=0

read_cpu_busy() {
  set -- $(grep '^cpu ' "$PROC_STAT" 2>/dev/null)
  U=${2:-0}; N=${3:-0}; S=${4:-0}; I=${5:-0}; IO=${6:-0}; IRQ=${7:-0}; SIRQ=${8:-0}; ST=${9:-0}
  TOTAL=$((U + N + S + I + IO + IRQ + SIRQ + ST))
  IDLE=$((I + IO))

  if [ "$CPU_STAT_INIT" -eq 0 ]; then
    PREV_CPU_TOTAL=$TOTAL
    PREV_CPU_IDLE=$IDLE
    CPU_STAT_INIT=1
    echo 0
    return
  fi

  DTOTAL=$((TOTAL - PREV_CPU_TOTAL))
  DIDLE=$((IDLE - PREV_CPU_IDLE))
  PREV_CPU_TOTAL=$TOTAL
  PREV_CPU_IDLE=$IDLE

  if [ "$DTOTAL" -le 0 ]; then
    echo 0
    return
  fi

  echo $(( (DTOTAL - DIDLE) * 100 / DTOTAL ))
}
```

**New CPU domain threshold declarations:**
```sh
CPU_HEAVY_UP=70
CPU_HEAVY_DOWN=45
CPU_MEDIUM_UP=20
CPU_MEDIUM_DOWN=10
CPU_HEAVY_DOWN_TICKS=5
```

**Force-tier testing hooks (new file paths):**
```sh
FORCE=/sdcard/pulse_lite.force
FORCE_CPU=/sdcard/pulse_lite.force_cpu
FORCE_GPU=/sdcard/pulse_lite.force_gpu
```

**Prior-session log evidence of the false-positive escalation bug (from `pulse_lite.log`):**
```
18:07:23 tier=medium gpu=13% caps: p0=1344000 p2=1708800 p5=1708800 p7=1824000 medium_tick=8
18:08:11 WARN: forced heavy after 30 stuck ticks in medium (gpu=12%, likely CPU-bound)
18:08:12 tier=heavy/heavy_high gpu=12% cpuT=51900 gpuT=50400 caps: p0=2265600 p2=3148800 p5=2956800 p7=3052800
18:08:14 tier=medium gpu=12% caps: p0=1344000 p2=1708800 p5=1708800 p7=1824000 medium_tick=0
```
```
18:14:47 tier=medium gpu=13% caps: p0=1344000 p2=1708800 p5=1708800 p7=1824000 medium_tick=26
18:14:56 WARN: forced heavy after 30 stuck ticks in medium (gpu=13%, likely CPU-bound)
18:14:56 tier=heavy/heavy_high gpu=13% cpuT=44900 gpuT=43400 caps: p0=2265600 p2=3148800 p5=2956800 p7=3052800
18:14:58 tier=medium gpu=13% caps: p0=1344000 p2=1708800 p5=1708800 p7=1824000 medium_tick=0
```

**Operational commands:**
```sh
adb push pulse_lite_v3.5.sh /sdcard/pulse_lite.sh
adb shell 'touch /sdcard/pulse_lite.stop'
adb shell sh /sdcard/pulse_lite.sh status
adb shell 'echo heavy > /sdcard/pulse_lite.force'
adb shell 'echo heavy > /sdcard/pulse_lite.force_cpu'
adb shell 'echo heavy > /sdcard/pulse_lite.force_gpu'
```
---
