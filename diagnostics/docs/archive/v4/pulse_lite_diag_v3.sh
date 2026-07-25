#!/bin/sh
# pulse_lite_diag.sh (v3) - diagnostic session BEFORE writing the FPS+busy% PoC
#
# Fixes vs v2 (found from baseline/gba_eco/gba_gaming logs):
#   - resumed activity regex was wrong (mResumedActivity does not exist on
#     this Android build). Correct field is "ResumedActivity" / "topResumedActivity".
#     Confirmed live: com.retrohrai.launcher/.MainActivity
#   - /sys/class/kgsl/kgsl-3d0/gpubusypercentage returns empty on this kernel.
#     Falling back to /sys/class/kgsl/kgsl-3d0/gpubusy which returns two raw
#     counters ("busy_cycles total_cycles"). busy% is computed manually as a
#     delta between two samples.
#   - Added explicit scaling_governor readback + a governor sanity note,
#     since AYASpace ECO mode switches governor to "powersave" (not just
#     scaling_max_freq) - relevant for the future controller.
#
# Usage:
#   adb shell xsu sh /sdcard/pulse_lite_diag.sh baseline
#   adb shell xsu sh /sdcard/pulse_lite_diag.sh gba_retroarch
#   adb shell xsu sh /sdcard/pulse_lite_diag.sh heavy_emulator
#   adb shell xsu sh /sdcard/pulse_lite_diag.sh ayaspace_conflict
#
# Each invocation creates /sdcard/pulse_lite_diag_<suffix>.log

SUFFIX="${1:-run}"
LOG="/sdcard/pulse_lite_diag_${SUFFIX}.log"
: > "$LOG"

sec() { echo "" >> "$LOG"; echo "===== $1 =====" >> "$LOG"; }

get_resumed_pkg() {
  # Primary: modern Android field name.
  pkg=$(dumpsys activity activities 2>/dev/null | grep -m1 'topResumedActivity=' \
        | sed -n 's/.*ActivityRecord{[^ ]* [^ ]* \([a-zA-Z0-9_.]*\)\/.*/\1/p')
  if [ -z "$pkg" ]; then
    # Fallback: older field name, kept for portability across firmware versions.
    pkg=$(dumpsys activity activities 2>/dev/null | grep -m1 'mResumedActivity' \
          | sed -n 's/.*ActivityRecord{[^ ]* [^ ]* \([a-zA-Z0-9_.]*\)\/.*/\1/p')
  fi
  echo "$pkg"
}

read_gpu_busy_raw() {
  # Returns "busy_cycles total_cycles" as reported by the kernel.
  cat /sys/class/kgsl/kgsl-3d0/gpubusy 2>/dev/null
}

sec "0. IDENTITY / RUN INFO"
{
  echo "suffix: $SUFFIX"
  echo "id: $(id 2>/dev/null)"
  echo "date: $(date 2>/dev/null)"
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

sec "10. AYASPACE WRITE-CONFLICT PROBE (only runs if suffix=ayaspace_conflict)"
{
  if [ "$SUFFIX" = "ayaspace_conflict" ]; then
    echo "Setting policy0 to ECO (1344000) and holding it read-only for 20s."
    echo "During this window, manually switch AYASpace mode: Gaming -> Eco -> Max."
    P0=/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
    chmod 644 "$P0" 2>/dev/null
    echo 1344000 > "$P0" 2>/dev/null
    chmod 444 "$P0" 2>/dev/null
    t=0
    while [ "$t" -lt 20 ]; do
      v=$(cat "$P0" 2>/dev/null)
      g=$(cat /sys/devices/system/cpu/cpufreq/policy0/scaling_governor 2>/dev/null)
      echo "t=${t}s scaling_max_freq=$v governor=$g"
      t=$((t + 2))
      sleep 2
    done
    chmod 644 "$P0" 2>/dev/null
  else
    echo "SKIPPED (run with suffix=ayaspace_conflict to execute this test)"
  fi
} >> "$LOG" 2>&1

sec "DONE"
echo "Diagnostics finished. Log: $LOG" >> "$LOG"
