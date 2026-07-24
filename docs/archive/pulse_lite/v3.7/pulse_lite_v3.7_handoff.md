pulse_lite AutoTDP Daemon — Handoff Substrate
Session: v3.7 — full tier-rank floor (CPU_TIER >= GPU_TIER) replacing v3.6's idle-only floor
Date: 2026-07-05
Status: v3.7 script drafted and delivered as file (code_file:546); not yet deployed, run, or soak-tested on device.

Context
v3.6 replaced v3.5's aggregate CPU busy% signal with a per-core max signal and added a GPU-driven floor that prevented CPU_TIER from sitting at idle when GPU_TIER was heavy. A live soak test showed this was insufficient: CPU_TIER settled into medium almost immediately (cpu_busy_max=57% on tick 1) and never moved for the entire session, even while GPU_TIER repeatedly reached heavy (gpu_busy=51-70%), because the floor's condition (idle→medium only) was never triggered — CPU was never at idle, it was stuck at medium. This produced essentially the same FPS regression as v3.5 (FPS=39 on overlay), just manifesting one tier lower. Root cause identified: per-core CPU busy% cannot distinguish "CPU needs a higher clock" from "CPU is fenced/waiting on GPU at a lower clock" in a tightly-coupled render pipeline — both look like non-saturating busy% in /proc/stat. Outcome: v3.7 generalizes the floor into a full tier-rank clamp, ensuring CPU_TIER can never rank lower than GPU_TIER, while still allowing CPU to independently escalate above GPU on its own signal.

Decisions Made
Replaced the v3.6 idle-only floor with a full rank-based floor (idle=0, medium=1, heavy=2), clamping CPU_TIER up to GPU_TIER's rank whenever GPU_RANK > CPU_RANK — justification: the live test proved the idle-only floor never fires once CPU has already left idle, so the fix must generalize across all tier pairs (idle→medium, medium→heavy, idle→heavy), not just the bottom one.

Kept CPU's ability to independently escalate above GPU's rank on its own per-core busy signal untouched — justification: this preserves the Switch-emulation case (GPU light, CPU heavy) that motivated the original CPU/GPU decoupling in v3.5; the floor only raises, never caps or lowers CPU.

Did not abandon the per-core CPU busy% signal (read_cpu_busy_max) despite it proving insufficient on its own — justification: it remains valid as CPU's primary upward-escalation driver for CPU-bound-but-GPU-light scenarios; the floor is layered on top specifically for GPU-fenced/lockstep pipelines where the busy% signal structurally cannot help.

Did not reset CPU's own hysteresis counters (CPU_MEDIUM_TICK, CPU_HEAVY_DOWN_TICK) when the floor clamps the tier — justification: consistent with v3.6's design, the floor only overrides the final resolved value, leaving CPU's internal timers to keep running underneath the clamp.

Renamed the log field from floor=N (v3.6, boolean) to floor_rank=N (v3.7, the GPU rank clamped to, or blank) — justification: needed finer diagnostic visibility into which tier pairs actually trigger the floor in practice, since v3.6's binary floor flag gave no insight into severity.

Left all CPU domain threshold values (CPU_HEAVY_UP=70, etc.) numerically unchanged from v3.5/v3.6 — justification: isolating the floor-mechanism fix from threshold retuning, consistent with the same reasoning applied in v3.6; these thresholds have still never been observed driving an independent CPU escalation in any test so far, only the floor has been observed raising CPU_TIER.

Findings / Facts Established
v3.6 test failure (root cause for this session), from user-supplied log

Confirmed log: 20:33:23 cpu_tier=medium/heavy_high cpu_busy_max=57%... floor=0 through 20:35:46 cpu_tier=medium/heavy_high cpu_busy_max=39%... floor=0 | gpu_tier=heavy/heavy_high gpu_busy=67% — CPU_TIER stayed at medium for the entire ~4 minute session, floor never fired (floor=0 throughout), despite GPU_TIER reaching heavy multiple times (gpu_busy 51-70%) [confirmed, directly quoted from user-supplied v3.6 session log].

Screenshot (IMG_0212.jpg) confirmed FPS=39, CPU=30% avg (overlay's own aggregate metric, distinct from the script's per-core max), CPU caps frozen at ECO values (p0=1344000 p2=1708800 p5=1708800 p7=1824000) [confirmed].

Root cause interpretation: the game's CPU/GPU pipeline is tightly coupled (render thread waits on GPU fence/vsync), so CPU threads on the critical path register as idle/moderate busy% even when the actual limiting factor is clock speed, not thread saturation [inferred from the pattern of low-but-not-saturating per-core busy% (27-39%) co-occurring with GPU heavy and low FPS; not independently confirmed via a dedicated per-thread CPU trace].

General conclusion: no CPU load signal alone (aggregate average, v3.5, or per-core max, v3.6) can safely resolve tightly-coupled CPU/GPU render pipelines, because both approaches measure busy% which cannot distinguish "starved for clock" from "waiting on GPU" [inferred, based on two consecutive failed signal-only approaches across v3.5 and v3.6].

v3.7 floor design

tier_rank() maps idle=0, medium=1, heavy=2; rank_to_tier() is the inverse mapping [confirmed, this session's code].

Floor logic: CPU_RANK=$(tier_rank "$CPU_TIER"); GPU_RANK=$(tier_rank "$GPU_TIER"); if [ "$GPU_RANK" -gt "$CPU_RANK" ]; then CPU_TIER=$(rank_to_tier "$GPU_RANK"); FLOOR_RANK=$GPU_RANK; fi — applied once per tick after both CPU_TIER and GPU_TIER have independently resolved via their own hysteresis [confirmed, this session's code].

The floor is strictly one-directional: it can only raise CPU_TIER to match or has no effect; it never lowers CPU_TIER even if CPU_RANK > GPU_RANK (preserves independent CPU escalation) [confirmed, this session's code].

FLOOR_RANK is logged per-tick as floor_rank= (blank if not applied, or the numeric GPU rank 0/1/2 that CPU was clamped to) [confirmed, this session's code].

Unchanged from v3.6 (carried forward as-is)

read_cpu_busy_max() per-core signal, CORE_COUNT dynamic detection, all GPU domain thresholds/hysteresis, thermal sub-tier CTEMP/GTEMP scoping, force-tier hooks (.force, .force_cpu, .force_gpu) [confirmed, unchanged].

Operational commands (path updated for v3.7)

Deploy: adb push pulse_lite_v3.7.sh /sdcard/pulse_lite.sh

Stop: adb shell 'touch /sdcard/pulse_lite.stop'

Status: adb shell sh /sdcard/pulse_lite.sh status

Force both domains (legacy): adb shell 'echo heavy > /sdcard/pulse_lite.force'

Force CPU only: adb shell 'echo heavy > /sdcard/pulse_lite.force_cpu'

Force GPU only: adb shell 'echo heavy > /sdcard/pulse_lite.force_gpu'

Open Questions / Unresolved
Whether floor_rank will fire on nearly every tick once GPU reaches heavy (making the floor the de-facto primary CPU driver rather than a safety net) is unknown and is explicitly flagged as an important observation for the next soak test — if so, the per-core CPU signal's practical relevance for this class of tightly-coupled game is questionable and the architecture may need rethinking (e.g. simplifying back toward a single shared tier for GPU-heavy cases specifically).

CPU domain threshold values (CPU_HEAVY_UP/DOWN, CPU_MEDIUM_UP/DOWN) remain unvalidated placeholders and have never been observed driving an independent upward escalation in any test conducted so far (v3.6 or v3.7) — the Switch-emulation scenario that would exercise this path has not yet been tested with either version.

No test plan for v3.7 has been executed yet — needs a repeat of the same scene/workload from IMG_0212.jpg to confirm CPU_TIER now reaches heavy alongside GPU_TIER, and that FPS recovers closer to the ~60 stock baseline.

The Switch-emulation and launcher/menu scenarios validated conceptually in v3.5 have not been re-confirmed under v3.6 or v3.7 — needs re-testing to ensure the floor mechanism (now full-rank) does not inadvertently force CPU to heavy in the launcher/menu case (it should not, since GPU_TIER should stay idle/medium there, but this needs empirical confirmation, not just logical inference).

Proposed Scope
Objective: Validate that v3.7's full tier-rank floor resolves the FPS regression observed in the v3.6 test (same game/scene as IMG_0212.jpg), restoring FPS closer to the stock ~60 baseline, while confirming the floor's practical firing frequency to judge whether the per-core signal is still doing meaningful independent work.

Definition of Done:

Re-run the exact scene from IMG_0212.jpg with pulse_lite_v3.7.sh active; confirm cpu_tier reaches heavy whenever gpu_tier=heavy in the log, and FPS on the overlay recovers substantially above the 39 observed with v3.6.

Record how often floor_rank is non-blank during that session to assess whether the floor is firing on nearly every heavy-GPU tick (suggesting the per-core signal is largely redundant for this workload) or only occasionally (suggesting per-core detection is doing real work and the floor is a true safety net).

Re-run the Switch-emulation scenario (GPU light, CPU heavy) to confirm CPU can still independently reach heavy without the floor interfering, and re-run the launcher/menu scenario to confirm the floor does not force CPU to heavy when GPU stays idle/medium.

Based on floor-firing frequency data, decide whether to keep the current architecture (per-core signal + floor) or simplify toward a GPU-rank-driven CPU tier for this class of GPU-fenced game, reserving independent per-core escalation only for GPU-light/CPU-heavy cases.

Candidate Relations
none

Raw Artifacts
v3.7 floor implementation (new, replaces v3.6's idle-only floor):

text
tier_rank() {
  case $1 in
    idle) echo 0 ;;
    medium) echo 1 ;;
    heavy) echo 2 ;;
    *) echo 0 ;;
  esac
}

rank_to_tier() {
  case $1 in
    0) echo idle ;;
    1) echo medium ;;
    2) echo heavy ;;
    *) echo idle ;;
  esac
}

CPU_RANK=$(tier_rank "$CPU_TIER")
GPU_RANK=$(tier_rank "$GPU_TIER")
FLOOR_RANK=""
if [ "$GPU_RANK" -gt "$CPU_RANK" ]; then
  CPU_TIER=$(rank_to_tier "$GPU_RANK")
  FLOOR_RANK=$GPU_RANK
fi
v3.6 failure log evidence (root cause for this session):

text
20:33:23 cpu_tier=medium/heavy_high cpu_busy_max=57% cpuT= floor=0 caps: p0=1344000 p2=1708800 p5=1708800 p7=1824000 | gpu_tier=idle/heavy_high gpu_busy=7% gpuT= gpu_cap=9
20:34:11 WARN: forced GPU heavy after 8 stuck ticks in medium (gpu=70%)
20:34:11 cpu_tier=medium/heavy_high cpu_busy_max=39% cpuT= floor=0 caps: p0=1344000 p2=1708800 p5=1708800 p7=1824000 | gpu_tier=heavy/heavy_high gpu_busy=70% gpuT=45000 gpu_cap=6
20:35:46 cpu_tier=medium/heavy_high cpu_busy_max=39% cpuT= floor=0 caps: p0=1344000 p2=1708800 p5=1708800 p7=1824000 | gpu_tier=heavy/heavy_high gpu_busy=67% gpuT=50400 gpu_cap=6
20:37:05 STOP: sentinel, exiting cleanly
Screenshot data (IMG_0212.jpg):

text
v3.6 running: FPS=39, CPU=30% avg (overlay metric), GPU=66% 59C, caps frozen at ECO (p0=1344000 p2=1708800 p5=1708800 p7=1824000)
Operational commands:

text
adb push pulse_lite_v3.7.sh /sdcard/pulse_lite.sh
adb shell 'touch /sdcard/pulse_lite.stop'
adb shell sh /sdcard/pulse_lite.sh status
adb shell 'echo heavy > /sdcard/pulse_lite.force'
adb shell 'echo heavy > /sdcard/pulse_lite.force_cpu'
adb shell 'echo heavy > /sdcard/pulse_lite.force_gpu'
