#!/usr/bin/env sh
# Polls, once a second, BOTH the live clock (scaling_cur_freq) and the AutoTDP cap
# (scaling_max_freq) for all 4 CPU policies (diagnostics/docs/HARDWARE_PROFILE.md), plus the GPU's
# live clock (kgsl-3d0/gpuclk, falling back to devfreq/cur_freq on kernels that don't expose the
# former) AND its actual cap (min_pwrlevel/max_pwrlevel -- the power-level INDEX AutoTDP's GPU trim
# writes, per PerformanceCommandBuilder.appendGpuLevel/GpuFreqDetector; lower index = faster/less
# capped) -- all via plain `adb shell cat` (no root, no `xsu`), one combined `adb shell` call per
# tick so it adds no load to the xsu/xsud channel under investigation elsewhere in this repo.
#
# The cur/max split matters: `scaling_cur_freq`/`gpu_cur` legitimately bounce under `schedutil`/GPU
# DVFS load regardless of whether AutoTDP is doing anything -- that's normal governor behavior, not
# evidence of AutoTDP activity. Only `scaling_max_freq`/`gpu_max_pwrlevel` (the CAPS) moving means
# AutoTDP itself is trimming/raising; a flat max/cap with a bouncing cur just means AutoTDP
# converged and is holding, which is also correct behavior once the target is met. Ground truth
# independent of logcat -- useful when logcat itself is dropping lines under a heavy game/emulator
# session (STATUS.md's AutoTDP-tick-loop investigation, 2026-07-27).
#
# STATUS.md, 2026-07-28: earlier versions of this script polled ONLY gpu_cur, never the GPU's own
# cap -- a session whose entire TRIM/RAISE arc happened on the GPU (confirmed via the app's own
# /sdcard log, see PulseDaemon.kt/logPulse) looked completely flat here, wrongly suggesting nothing
# was happening. gpu_min_pwrlevel/gpu_max_pwrlevel below close that gap.
#
# Usage: ./poll-cpufreq.sh > cap_poll.log   (Ctrl+C to stop)

REMOTE_SCRIPT='
CPU=/sys/devices/system/cpu/cpufreq
GPU=/sys/class/kgsl/kgsl-3d0
[ -d "$GPU" ] || GPU=/sys/devices/platform/soc@0/3d00000.gpu/kgsl/kgsl-3d0
p0c=$(cat $CPU/policy0/scaling_cur_freq 2>/dev/null); p0m=$(cat $CPU/policy0/scaling_max_freq 2>/dev/null)
p2c=$(cat $CPU/policy2/scaling_cur_freq 2>/dev/null); p2m=$(cat $CPU/policy2/scaling_max_freq 2>/dev/null)
p5c=$(cat $CPU/policy5/scaling_cur_freq 2>/dev/null); p5m=$(cat $CPU/policy5/scaling_max_freq 2>/dev/null)
p7c=$(cat $CPU/policy7/scaling_cur_freq 2>/dev/null); p7m=$(cat $CPU/policy7/scaling_max_freq 2>/dev/null)
gov=$(cat $CPU/policy0/scaling_governor 2>/dev/null)
gpu=$(cat $GPU/gpuclk 2>/dev/null)
[ -z "$gpu" ] && gpu=$(cat $GPU/devfreq/cur_freq 2>/dev/null)
gpu_min=$(cat $GPU/min_pwrlevel 2>/dev/null)
gpu_max=$(cat $GPU/max_pwrlevel 2>/dev/null)
echo "p0_cur=$p0c p0_max=$p0m p2_cur=$p2c p2_max=$p2m p5_cur=$p5c p5_max=$p5m p7_cur=$p7c p7_max=$p7m gov=$gov gpu_cur=$gpu gpu_min_pwrlevel=$gpu_min gpu_max_pwrlevel=$gpu_max"
'

set -eu

while true; do
    out=$(adb shell "$REMOTE_SCRIPT")
    echo "$(date +%H:%M:%S.%N) $out"
    sleep 1
done
