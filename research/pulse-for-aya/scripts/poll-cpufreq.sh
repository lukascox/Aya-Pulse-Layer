#!/usr/bin/env sh
# Polls, once a second, BOTH the live clock (scaling_cur_freq) and the AutoTDP cap
# (scaling_max_freq) for all 4 CPU policies (diagnostics/docs/HARDWARE_PROFILE.md) plus the GPU's
# live clock (kgsl-3d0/gpuclk, falling back to devfreq/cur_freq on kernels that don't expose the
# former) -- all via plain `adb shell cat` (no root, no `xsu`), one combined `adb shell` call per
# tick so it adds no load to the xsu/xsud channel under investigation elsewhere in this repo.
#
# The cur/max split matters: `scaling_cur_freq` legitimately bounces under `schedutil` load
# regardless of whether AutoTDP is doing anything -- that's normal governor behavior, not evidence
# of AutoTDP activity. Only `scaling_max_freq` (the CAP) moving means AutoTDP itself is trimming/
# raising; a flat max with a bouncing cur just means AutoTDP converged and is holding, which is
# also correct behavior once the target is met. Ground truth independent of logcat -- useful when
# logcat itself is dropping lines under a heavy game/emulator session (STATUS.md's AutoTDP-tick-
# loop investigation, 2026-07-27).
#
# Usage: ./poll-cpufreq.sh > cap_poll.log   (Ctrl+C to stop)

REMOTE_SCRIPT='
CPU=/sys/devices/system/cpu/cpufreq
GPU=/sys/class/kgsl/kgsl-3d0
p0c=$(cat $CPU/policy0/scaling_cur_freq 2>/dev/null); p0m=$(cat $CPU/policy0/scaling_max_freq 2>/dev/null)
p2c=$(cat $CPU/policy2/scaling_cur_freq 2>/dev/null); p2m=$(cat $CPU/policy2/scaling_max_freq 2>/dev/null)
p5c=$(cat $CPU/policy5/scaling_cur_freq 2>/dev/null); p5m=$(cat $CPU/policy5/scaling_max_freq 2>/dev/null)
p7c=$(cat $CPU/policy7/scaling_cur_freq 2>/dev/null); p7m=$(cat $CPU/policy7/scaling_max_freq 2>/dev/null)
gov=$(cat $CPU/policy0/scaling_governor 2>/dev/null)
gpu=$(cat $GPU/gpuclk 2>/dev/null)
[ -z "$gpu" ] && gpu=$(cat $GPU/devfreq/cur_freq 2>/dev/null)
echo "p0_cur=$p0c p0_max=$p0m p2_cur=$p2c p2_max=$p2m p5_cur=$p5c p5_max=$p5m p7_cur=$p7c p7_max=$p7m gov=$gov gpu_cur=$gpu"
'

set -eu

while true; do
    out=$(adb shell "$REMOTE_SCRIPT")
    echo "$(date +%H:%M:%S.%N) $out"
    sleep 1
done
