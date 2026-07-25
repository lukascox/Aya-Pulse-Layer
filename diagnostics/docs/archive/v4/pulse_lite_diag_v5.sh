#!/bin/sh
# pulse_lite_diag.sh (v5) - diagnostic session before the FPS + busy% PoC
#
# Fixes vs v4:
#   - Section 11 (interactive AYASpace mode survey via sentinel files) is
#     removed entirely. It did not survive inside an `xsu` invocation in
#     practice (the session appears to exit before the wait-loop can pick
#     up the sentinel file - likely `xsu` does not keep a long-running
#     background loop alive the way a persistent root shell would).
#   - New workflow instead: run this script once per AYASpace mode with a
#     matching suffix, right after switching modes manually. Every run does
#     the full sequence (SoC fingerprint, CPU/GPU tables, thermal zones,
#     90s FPS+busy% sampling, safe write tests). This is simpler, more
#     robust, and gives more usable data per run than the interactive
#     survey did (since sections 7-10 run every time too).
#
# Usage:
#   adb push pulse_lite_diag.sh /sdcard/pulse_lite_diag.sh
#   adb shell xsu sh /sdcard/pulse_lite_diag.sh <suffix>
#
# Recommended suffixes for the mode comparison pass:
#   eco       -> switch AYASpace to Eco, then run with suffix "eco"
#   balanced  -> switch AYASpace to Balanced, then run with suffix "balanced"
#   gaming    -> switch AYASpace to Gaming, then run with suffix "gaming"
#   max       -> switch AYASpace to Max, then run with suffix "max"
#
# Recommended suffixes for the gameplay pass (run AFTER the mode comparison):
#   retroarch_balanced -> RetroArch running, AYASpace on Balanced
#   heavy_gaming        -> heavier emulator running, AYASpace on Gaming
#
# Each invocation creates /sdcard/pulse_lite_diag_<suffix>.log
# Pull it with: adb pull /sdcard/pulse_lite_diag_<suffix>.log

SUFFIX="${1:-run}"
LOG="/sdcard/pulse_lite_diag_${SUFFIX}.log"
: > "$LOG"

sec() { echo "" >> "$LOG"; echo "===== $1 =====" >> "$LOG"; }

get_resumed_pkg() {
  pkg=$(dumpsys activity activities 2>/dev/null | grep -m1 'topResumedActivity=' \
        | sed -n 's/.*ActivityRecord{[^ ]* [^ ]* \([a-zA-Z0-9_.]*\)\/.*/\1/p')
  if [ -z "$pkg" ]; then
    pkg=$(dumpsys activity activities 2>/dev/null | grep -m1 'mResumedActivity' \
          | sed -n 's/.*ActivityRecord{[^ ]* [^ ]* \([a-zA-Z0-9_.]*\)\/.*/\1/p')
  fi
  echo "$pkg"
}

read_gpu_busy_raw() {
  cat /sys/class/kgsl/kgsl-3d0/gpubusy 2>/dev/null
}

sec "0. IDENTITY / RUN INFO"
{
  echo "suffix: $SUFFIX"
  echo "id: $(id 2>/dev/null)"
  echo "date: $(date 2>/dev/null)"
  echo ">>> REMINDER: note which AYASpace mode was active for this run when reporting back <<<"
} >> "$LOG"

sec "1. DEVICE / SOC FINGERPRINT"
{
  getprop ro.product.model 2>/dev/null | sed 's/^/ro.product.model=/'
  getprop ro.product.name 2>/dev/null | sed 's/^/ro.product.name=/'
  getprop ro.product.device 2>/dev/null | sed 's/^/ro.product.device=/'
  getprop ro.build.flavor 2>/dev/null | sed 's/^/ro.build.flavor=/'
  getprop ro.vendor.qti.soc_model 2>/dev/null | sed 's/^/ro.vendor.qti.soc_model=/'
  getprop ro.board.platform 2>/dev/null | sed 's/^/ro.board.platform=/'
  getprop ro.build.version.release 2>/dev/null | sed 's/^/android.version=/'
  getprop ro.build.version.sdk 2>/dev/null | sed 's/^/android.sdk=/'
  echo "--- full getprop grep soc/hw/board/gpu ---"
  getprop 2>/dev/null | grep -iE 'soc|hardware|board|chip|gpu'
} >> "$LOG" 2>&1

sec "2. CPU TOPOLOGY - all policies, full frequency tables + governor"
for p in /sys/devices/system/cpu/cpufreq/policy*; do
  [ -d "$p" ] || continue
  name=$(basename "$p")
  {
    echo "--- $name ---"
    echo "affected_cpus: $(cat "$p/affected_cpus" 2>/dev/null)"
    echo "scaling_governor: $(cat "$p/scaling_governor" 2>/dev/null)"
    echo "scaling_available_governors: $(cat "$p/scaling_available_governors" 2>/dev/null)"
    echo "cpuinfo_min_freq: $(cat "$p/cpuinfo_min_freq" 2>/dev/null)"
    echo "cpuinfo_max_freq: $(cat "$p/cpuinfo_max_freq" 2>/dev/null)"
    echo "scaling_min_freq: $(cat "$p/scaling_min_freq" 2>/dev/null)"
    echo "scaling_max_freq: $(cat "$p/scaling_max_freq" 2>/dev/null)"
    echo "scaling_cur_freq: $(cat "$p/scaling_cur_freq" 2>/dev/null)"
    echo "scaling_available_frequencies: $(cat "$p/scaling_available_frequencies" 2>/dev/null)"
  } >> "$LOG" 2>&1
done

sec "3. GPU (kgsl) - full state"
{
  KGSL=/sys/class/kgsl/kgsl-3d0
  echo "max_pwrlevel: $(cat $KGSL/max_pwrlevel 2>/dev/null)"
  echo "min_pwrlevel: $(cat $KGSL/min_pwrlevel 2>/dev/null)"
  echo "num_pwrlevels: $(cat $KGSL/num_pwrlevels 2>/dev/null)"
  echo "gpuclk: $(cat $KGSL/gpuclk 2>/dev/null)"
  echo "gpubusy (raw busy_cycles total_cycles): $(cat $KGSL/gpubusy 2>/dev/null)"
  echo "gpubusypercentage (known broken on this kernel, kept for reference): $(cat $KGSL/gpubusypercentage 2>/dev/null)"
  echo "devfreq/governor: $(cat $KGSL/devfreq/governor 2>/dev/null)"
  echo "devfreq/available_frequencies: $(cat $KGSL/devfreq/available_frequencies 2>/dev/null)"
} >> "$LOG" 2>&1

sec "4. THERMAL ZONES - full list + current temps"
{
  for z in /sys/class/thermal/thermal_zone*; do
    [ -d "$z" ] || continue
    t=$(cat "$z/type" 2>/dev/null)
    v=$(cat "$z/temp" 2>/dev/null)
    echo "$(basename "$z"): type=$t temp=$v"
  done
} >> "$LOG" 2>&1

sec "5. INSTALLED EMULATOR/GAMING PACKAGES"
{
  pm list packages 2>/dev/null | grep -iE 'retro|emul|dolphin|citra|ppsspp|yuzu|ryujinx|duckstation|flycast|aetherx|winlator|exagear|mupen|drastic|redream|vita3k'
} >> "$LOG" 2>&1

sec "6. SURFACEFLINGER WINDOW LIST (fallback FPS method reference)"
{
  dumpsys SurfaceFlinger --list 2>/dev/null
} >> "$LOG" 2>&1

sec "7. FPS + GPU BUSY% SAMPLING - 90s window, current foreground app"
{
  echo "Sampling starts NOW on whatever is currently on screen."
  echo "If you want to test a specific game, launch it BEFORE running this script."
} >> "$LOG"

PREV_BUSY=""
PREV_TOTAL=""
i=0
while [ "$i" -lt 30 ]; do
  PKG=$(get_resumed_pkg)
  RAW=$(read_gpu_busy_raw)
  BUSY_CYCLES=$(echo "$RAW" | awk '{print $1}')
  TOTAL_CYCLES=$(echo "$RAW" | awk '{print $2}')

  BUSY_PCT="n/a"
  if [ -n "$PREV_BUSY" ] && [ -n "$BUSY_CYCLES" ] && [ -n "$TOTAL_CYCLES" ]; then
    DBUSY=$((BUSY_CYCLES - PREV_BUSY))
    DTOTAL=$((TOTAL_CYCLES - PREV_TOTAL))
    if [ "$DTOTAL" -gt 0 ] 2>/dev/null; then
      BUSY_PCT=$((DBUSY * 100 / DTOTAL))
    fi
  fi
  PREV_BUSY=$BUSY_CYCLES
  PREV_TOTAL=$TOTAL_CYCLES

  {
    echo "--- sample $i pkg=$PKG $(date +%H:%M:%S) ---"
    if [ -n "$PKG" ]; then
      dumpsys gfxinfo "$PKG" framestats 2>/dev/null | head -40
    else
      echo "no resumed activity detected"
    fi
    echo "gpu_busy_raw: $RAW  gpu_busy_pct(computed): $BUSY_PCT"
    echo "cpu_cur_freqs: p0=$(cat /sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq 2>/dev/null) p2=$(cat /sys/devices/system/cpu/cpufreq/policy2/scaling_cur_freq 2>/dev/null) p5=$(cat /sys/devices/system/cpu/cpufreq/policy5/scaling_cur_freq 2>/dev/null) p7=$(cat /sys/devices/system/cpu/cpufreq/policy7/scaling_cur_freq 2>/dev/null)"
  } >> "$LOG" 2>&1
  i=$((i + 1))
  sleep 3
done

sec "8. SAFE WRITE TEST - CPU (reversible, restores immediately)"
{
  P0=/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
  GOV0=$(cat /sys/devices/system/cpu/cpufreq/policy0/scaling_governor 2>/dev/null)
  echo "policy0 current governor: $GOV0 (note: AYASpace ECO mode sets this to 'powersave', which ignores scaling_max_freq for practical purposes)"
  ORIG=$(cat "$P0" 2>/dev/null)
  echo "policy0 original scaling_max_freq: $ORIG"
  chmod 644 "$P0" 2>/dev/null
  TEST_VAL=1344000
  echo "$TEST_VAL" > "$P0" 2>/dev/null
  sleep 1
  AFTER=$(cat "$P0" 2>/dev/null)
  echo "after writing $TEST_VAL, scaling_max_freq reads: $AFTER"
  if [ -n "$ORIG" ]; then
    echo "$ORIG" > "$P0" 2>/dev/null
    RESTORED=$(cat "$P0" 2>/dev/null)
    echo "restored to: $RESTORED"
  fi
  chmod 444 "$P0" 2>/dev/null
} >> "$LOG" 2>&1

sec "9. SAFE WRITE TEST - GPU (reversible, restores immediately)"
{
  GC=/sys/class/kgsl/kgsl-3d0/max_pwrlevel
  ORIG=$(cat "$GC" 2>/dev/null)
  echo "gpu original max_pwrlevel: $ORIG"
  chmod 644 "$GC" 2>/dev/null
  echo "9" > "$GC" 2>/dev/null
  sleep 1
  AFTER=$(cat "$GC" 2>/dev/null)
  GPUCLK_AFTER=$(cat /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null)
  echo "after writing 9, max_pwrlevel reads: $AFTER gpuclk=$GPUCLK_AFTER"
  if [ -n "$ORIG" ]; then
    echo "$ORIG" > "$GC" 2>/dev/null
    RESTORED=$(cat "$GC" 2>/dev/null)
    echo "restored to: $RESTORED"
  fi
  chmod 444 "$GC" 2>/dev/null
} >> "$LOG" 2>&1

sec "DONE"
echo "Diagnostics finished. Log: $LOG" >> "$LOG"
