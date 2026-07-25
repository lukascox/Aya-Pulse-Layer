#!/system/bin/sh
# pulse_lite_diag_v7.sh
# AYA Pocket FIT / AYASpace pre-AutoTDP diagnostic script
# v7: fixes foreground-app detection, which was silently broken in v4/v5/v6.
#
# Root cause found and fixed (confirmed manually via adb on this exact device,
# Android 14 / AYANEO_PocketS2-user build):
#   - "dumpsys window windows | grep mCurrentFocus" returns NOTHING on this
#     build. That field does not exist in this dumpsys output on Android 14
#     here. v4/v5/v6 all silently relied on it as the primary focus source.
#   - "dumpsys activity activities | grep mResumedActivity" also returns
#     nothing on this build - the correct field name here is
#     "topResumedActivity=", not "mResumedActivity".
#   - Because both v6 lookups failed, PKG was always "unknown", which meant
#     MATCHED_LAYER could never be found even though SurfaceFlinger --list
#     clearly showed live, correctly named layers for RetroArch and Eden the
#     whole time. The layer-matching logic itself was NEVER broken - it just
#     never received a usable package name to search for.
#   - Confirmed fix commands (manually run over adb, both returned correct,
#     parseable data):
#       adb shell dumpsys activity activities | grep -E 'topResumedActivity|mFocusedApp'
#       adb shell dumpsys SurfaceFlinger --list | grep <pkg>
#       adb shell dumpsys SurfaceFlinger --latency "<full_layer_name>"
#   - Also confirmed: the FPS pipeline itself is refresh-rate-independent.
#     Tested twice on the same RetroArch session, once with the panel set to
#     120Hz (refresh_period_ns=8333333) and once at 144Hz
#     (refresh_period_ns=6944444) - both times computed_fps came back at
#     ~60fps, matching the emulated console's native framerate, not the
#     panel's refresh rate. This confirms the --latency method measures real
#     content framerate correctly, independent of display Hz.
#
# What changed vs v6 (this is the ONLY functional change in this version):
#   - Section 7 foreground-detection step now parses "topResumedActivity="
#     first, falling back to "mFocusedApp=" if that's empty (both confirmed
#     present in this build's dumpsys activity activities output). The old
#     "dumpsys window windows | grep mCurrentFocus" call has been REMOVED
#     entirely - confirmed dead end on this build, kept it would only waste
#     a dumpsys call every sample.
#   - PKG extraction regex updated to match the real string format seen on
#     this device: "topResumedActivity=ActivityRecord{<hash> u0 <pkg>/<activity> t<task>}"
#     (previous versions' regex did not account for the trailing " t<N>}" and
#     could fail even if the right field had been queried).
#   - Everything else (sections 0-6, 8, layer-matching logic, --latency
#     parsing/sentinel-filtering, GPU busy% overflow guard, per-sample CPU/GPU
#     capture) is UNCHANGED from v6 - those parts were already confirmed
#     working correctly in v6's own logs (gpu_busy_pct, gpu_freq_hz, thermal
#     trends were all sane in the eden_v6 and ra_v6 runs; only the
#     focus-detection step was the blocker).
#
# Usage: sh pulse_lite_diag_v7.sh <suffix>
# Output: /sdcard/pulse_lite_diag_<suffix>.log

SUFFIX="$1"
if [ -z "$SUFFIX" ]; then SUFFIX="run"; fi
LOG="/sdcard/pulse_lite_diag_${SUFFIX}.log"
> "$LOG"

log() { echo "$1" >> "$LOG"; }

log "===== 0. IDENTITY / RUN INFO ====="
log "suffix: $SUFFIX"
log "id: $(id)"
log "date: $(date)"
log ">>> REMINDER: note which AYASpace mode was active for this run when reporting back <<<"
log ""

log "===== 1. DEVICE / SOC FINGERPRINT ====="
getprop ro.product.model >> "$LOG"
getprop ro.product.name >> "$LOG"
getprop ro.product.device >> "$LOG"
getprop ro.build.flavor >> "$LOG"
getprop ro.vendor.qti.soc_model >> "$LOG"
getprop ro.board.platform >> "$LOG"
log "android.version=$(getprop ro.build.version.release)"
log "android.sdk=$(getprop ro.build.version.sdk)"
log "--- full getprop grep soc/hw/board/gpu ---"
getprop | grep -iE "soc|board|gpu" >> "$LOG"
log ""

log "===== 2. CPU TOPOLOGY - all policies, full frequency tables + governor (baseline snapshot) ====="
for p in /sys/devices/system/cpu/cpufreq/policy*; do
  pn=$(basename "$p")
  log "--- $pn ---"
  log "affected_cpus: $(cat $p/affected_cpus 2>/dev/null)"
  log "scaling_governor: $(cat $p/scaling_governor 2>/dev/null)"
  log "scaling_available_governors: $(cat $p/scaling_available_governors 2>/dev/null)"
  log "cpuinfo_min_freq: $(cat $p/cpuinfo_min_freq 2>/dev/null)"
  log "cpuinfo_max_freq: $(cat $p/cpuinfo_max_freq 2>/dev/null)"
  log "scaling_min_freq: $(cat $p/scaling_min_freq 2>/dev/null)"
  log "scaling_max_freq: $(cat $p/scaling_max_freq 2>/dev/null)"
  log "scaling_cur_freq: $(cat $p/scaling_cur_freq 2>/dev/null)"
  log "scaling_available_frequencies: $(cat $p/scaling_available_frequencies 2>/dev/null)"
done
log ""

log "===== 3. GPU (kgsl) - full state (baseline snapshot) ====="
KGSL=/sys/class/kgsl/kgsl-3d0
log "max_pwrlevel: $(cat $KGSL/max_pwrlevel 2>/dev/null)"
log "min_pwrlevel: $(cat $KGSL/min_pwrlevel 2>/dev/null)"
log "num_pwrlevels: $(cat $KGSL/num_pwrlevels 2>/dev/null)"
log "gpuclk: $(cat $KGSL/gpuclk 2>/dev/null)"
log "gpubusy (raw busy_cycles total_cycles): $(cat $KGSL/gpubusy 2>/dev/null)"
log "devfreq/governor: $(cat $KGSL/devfreq/governor 2>/dev/null)"
log "devfreq/available_frequencies: $(cat $KGSL/devfreq/available_frequencies 2>/dev/null)"
log ""

log "===== 4. THERMAL ZONES - full list + current temps ====="
for tz in /sys/class/thermal/thermal_zone*; do
  n=$(basename "$tz")
  type=$(cat "$tz/type" 2>/dev/null)
  temp=$(cat "$tz/temp" 2>/dev/null)
  log "$n: type=$type temp=$temp"
done
log ""

log "===== 5. INSTALLED EMULATOR/GAMING PACKAGES ====="
pm list packages | grep -iE "retroarch|dolphin|yuzu|eden|citra|ppsspp|emu|retrohrai|ayaneo" >> "$LOG"
log ""

log "===== 6. SURFACEFLINGER WINDOW LIST (reference dump) ====="
dumpsys SurfaceFlinger --list >> "$LOG" 2>/dev/null
log ""

# ---------------------------------------------------------------------------
# 7. DYNAMIC FPS + CPU/GPU SAMPLING - 90s window
# Pipeline per sample (v7 fix applied at step a only - see header comment):
#   a) detect foreground pkg/activity via topResumedActivity= (primary),
#      falling back to mFocusedApp= if empty. Both confirmed present in this
#      build's "dumpsys activity activities" output.
#   b) find matching SurfaceFlinger layer (prefer SurfaceView[pkg] if present,
#      fallback to generic pkg/Activity layer) - UNCHANGED from v6
#   c) dumpsys SurfaceFlinger --latency "<layer>" -> parse refresh period +
#      compute real FPS + frame count from actualPresentTime deltas,
#      filtering out the INT64_MAX sentinel row - UNCHANGED from v6
#   d) capture CPU governor+cur_freq per policy, GPU freq+busy (with overflow
#      sanity check), thermal zone snapshot (subset) - UNCHANGED from v6
# ---------------------------------------------------------------------------

log "===== 7. DYNAMIC FPS + CPU/GPU SAMPLING - 90s window, autodetect foreground app ====="
log "Sampling starts NOW on whatever is currently on screen."
log "Focus/layer/FPS are re-detected every sample - no app name hardcoded."
log ""

SAMPLE_INTERVAL=3
SAMPLE_COUNT=30   # 30 * 3s = 90s

i=0
while [ $i -lt $SAMPLE_COUNT ]; do
  TS=$(date +%H:%M:%S)

  # a) detect foreground pkg/activity (v7 FIX: correct field names for this build)
  ACTIVITY_DUMP=$(dumpsys activity activities 2>/dev/null)
  FOCUS_LINE=$(echo "$ACTIVITY_DUMP" | grep -E "topResumedActivity=" | head -1)
  if [ -z "$FOCUS_LINE" ]; then
    FOCUS_LINE=$(echo "$ACTIVITY_DUMP" | grep -E "mFocusedApp=" | head -1)
  fi
  RESUMED_LINE="$FOCUS_LINE"
  # Extract pkg/activity from "...ActivityRecord{<hash> u0 <pkg>/<activity> t<N>}"
  PKG=$(echo "$FOCUS_LINE" | grep -oE "[a-zA-Z0-9_.]+/[a-zA-Z0-9_.]+" | head -1)
  if [ -z "$PKG" ]; then PKG="unknown"; fi
  PKG_SHORT=$(echo "$PKG" | cut -d'/' -f1)

  # b) find matching SurfaceFlinger layer (unchanged from v6)
  LAYER_LIST=$(dumpsys SurfaceFlinger --list 2>/dev/null)
  MATCHED_LAYER=$(echo "$LAYER_LIST" | grep -i "SurfaceView.*$PKG_SHORT" | head -1)
  if [ -z "$MATCHED_LAYER" ]; then
    MATCHED_LAYER=$(echo "$LAYER_LIST" | grep -i "$PKG_SHORT" | head -1)
  fi
  if [ -z "$MATCHED_LAYER" ]; then
    MATCHED_LAYER="NoMatch"
  fi

  log "--- sample $i pkg=$PKG_SHORT ts=$TS ---"
  log "focus_raw: $FOCUS_LINE"
  log "resumed_raw: $RESUMED_LINE"
  log "matched_layer: $MATCHED_LAYER"

  # c) SurfaceFlinger --latency FPS calc (unchanged from v6)
  if [ "$MATCHED_LAYER" != "NoMatch" ]; then
    LAT=$(dumpsys SurfaceFlinger --latency "$MATCHED_LAYER" 2>/dev/null)
    REFRESH_NS=$(echo "$LAT" | head -1 | tr -d '\r')
    PRESENT_TIMES=$(echo "$LAT" | tail -n +2 | awk '{print $2}' | grep -vE "^0$|^9223372036854775807$")
    FRAME_COUNT=$(echo "$PRESENT_TIMES" | grep -c "^[0-9]")
    if [ "$FRAME_COUNT" -ge 5 ]; then
      FIRST=$(echo "$PRESENT_TIMES" | head -1)
      LAST=$(echo "$PRESENT_TIMES" | tail -1)
      SPAN_NS=$((LAST - FIRST))
      if [ "$SPAN_NS" -gt 0 ]; then
        FPS=$(awk -v f="$FRAME_COUNT" -v s="$SPAN_NS" 'BEGIN{printf "%.1f", (f-1)*1000000000/s}')
      else
        FPS="n/a (zero span)"
      fi
    else
      FPS="n/a (low_sample_count=$FRAME_COUNT, likely idle/static frame)"
    fi
    log "refresh_period_ns: $REFRESH_NS"
    log "frame_count_in_window: $FRAME_COUNT"
    log "computed_fps: $FPS"
  else
    log "refresh_period_ns: n/a (no layer matched)"
    log "frame_count_in_window: 0"
    log "computed_fps: n/a (no layer matched)"
  fi

  # d) CPU governor + cur_freq per policy (unchanged from v6)
  CPU_SNAP=""
  for p in /sys/devices/system/cpu/cpufreq/policy*; do
    pn=$(basename "$p" | sed 's/policy//')
    gov=$(cat "$p/scaling_governor" 2>/dev/null)
    freq=$(cat "$p/scaling_cur_freq" 2>/dev/null)
    CPU_SNAP="$CPU_SNAP p${pn}:gov=${gov},freq=${freq}"
  done
  log "cpu_snapshot:$CPU_SNAP"

  # GPU freq + busy with overflow sanity check (unchanged from v6)
  GPU_CLK=$(cat $KGSL/gpuclk 2>/dev/null)
  GPU_BUSY_RAW=$(cat $KGSL/gpubusy 2>/dev/null)
  BUSY_CYCLES=$(echo "$GPU_BUSY_RAW" | awk '{print $1}')
  TOTAL_CYCLES=$(echo "$GPU_BUSY_RAW" | awk '{print $2}')
  if [ -n "$BUSY_CYCLES" ] && [ -n "$TOTAL_CYCLES" ] && [ "$TOTAL_CYCLES" -gt 0 ] 2>/dev/null; then
    GPU_PCT=$(awk -v b="$BUSY_CYCLES" -v t="$TOTAL_CYCLES" 'BEGIN{v=(b*100)/t; if (v<0 || v>100) print "n/a (counter_reset)"; else printf "%.1f", v}')
  else
    GPU_PCT="n/a"
  fi
  log "gpu_freq_hz: $GPU_CLK"
  log "gpu_busy_raw: $GPU_BUSY_RAW"
  log "gpu_busy_pct: $GPU_PCT"

  # thermal subset (skin + battery + hottest cpu zone for quick trend) - unchanged from v6
  SKIN=$(cat /sys/class/thermal/thermal_zone55/temp 2>/dev/null)
  BATT=$(cat /sys/class/thermal/thermal_zone72/temp 2>/dev/null)
  log "thermal_skin: $SKIN thermal_battery: $BATT"
  log ""

  i=$((i+1))
  sleep $SAMPLE_INTERVAL
done

log "===== 8. WRITE SAFETY TEST (unchanged from v5/v6) ====="
TESTFILE="/sdcard/pulse_lite_write_test.tmp"
if echo "test" > "$TESTFILE" 2>/dev/null; then
  log "write_test: OK (/sdcard writable)"
  rm -f "$TESTFILE"
else
  log "write_test: FAILED"
fi
for p in /sys/devices/system/cpu/cpufreq/policy0; do
  if echo "$(cat $p/scaling_max_freq)" > "$p/scaling_max_freq" 2>/dev/null; then
    log "cpufreq_write_test: OK (policy0 scaling_max_freq writable)"
  else
    log "cpufreq_write_test: FAILED (policy0 scaling_max_freq NOT writable - relevant for AutoTDP)"
  fi
done
log ""

log "===== DONE. Log written to $LOG ====="
