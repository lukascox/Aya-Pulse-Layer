#!/bin/sh
# pulse_lite v3.6 -- AutoTDP daemon, KONKR Pocket FIT / Snapdragon G3 Gen 3
# 4-policy CPU coverage: A520(cpu0-1) + A720-mid(cpu2-4) + A720-high(cpu5-6) + X4(cpu7)

# CHANGELOG v3.6 (vs v3.5):
# - CRITICAL FIX: v3.5's aggregate /proc/stat "cpu " line (average busy% across
#   all 8 cores) failed to detect single-thread-bound workloads. Confirmed via
#   side-by-side screenshots: stock caps (no daemon) gave FPS=60 CPU=23% avg;
#   v3.5 running gave FPS=35 CPU=36% avg, with CPU_TIER stuck at idle/medium
#   the entire session (cpu_busy=14-31%) while GPU_TIER correctly climbed to
#   heavy (gpu_busy=66-78%). Root cause: a game bottlenecked on 1-2 hot threads
#   (main/render thread near 100% on their core) gets averaged down to ~20-30%
#   across 8 cores, so CPU_HEAVY_UP=70 (avg-based) never trips, and pulse_lite
#   holds CPU clocks at ECO/medium levels while the actual bottleneck thread is
#   starved. This directly caused the FPS=60->35 regression from v3.4 to v3.5.
# - Replaced read_cpu_busy() (aggregate) with read_cpu_busy_max() (per-core,
#   max-of-cores). Parses each individual "cpuN" line in /proc/stat (not just
#   the aggregate "cpu " line), computes delta-based busy% per core, and
#   returns the MAXIMUM across all cores -- mirroring the existing
#   read_max_temp() pattern (worst-case/hottest-core wins), which is the same
#   design already proven for thermal zones since v3.2.
# - NEW: GPU_TIER safe floor lock on CPU_TIER. If GPU_TIER=heavy, CPU_TIER is
#   never allowed to sit at idle -- it is floored to at least medium,
#   regardless of what the per-core CPU busy% signal says. Rationale: no game
#   that is genuinely pushing the GPU into heavy (gpu_busy 66-96% observed) is
#   realistically fine running its CPU-side logic at ECO clocks; the risk of
#   under-clocking CPU in a GPU-heavy scene is judged worse than the risk of
#   slightly over-provisioning CPU clocks. This is a safety net layered ON TOP
#   of the per-core fix above, not a replacement for it -- per-core detection
#   is expected to be the primary fix; the floor catches any case per-core
#   detection still misses (e.g. per-core sampling noise, or genuinely bursty
#   single-frame CPU spikes shorter than one INTERVAL tick).
# - The floor is applied AFTER CPU_TIER's own hysteresis resolves each tick,
#   as a clamp: `[ "$GPU_TIER" = "heavy" ] && [ "$CPU_TIER" = "idle" ] &&
#   CPU_TIER=medium`. It does not reset CPU_MEDIUM_TICK/CPU_HEAVY_DOWN_TICK
#   counters, so the CPU domain's own hysteresis timers keep running normally
#   underneath the clamp.
# - No change to GPU_TIER logic, GPU thresholds, or thermal sub-tier logic --
#   this version is scoped strictly to fixing the CPU load signal and adding
#   the GPU-driven floor.

# CHANGELOG v3.5 (vs v3.4):
# - MAJOR: decoupled CPU and GPU tier decisions into two independent state
#   machines (CPU_TIER / CPU_HTIER and GPU_TIER / GPU_HTIER).
# - Added a new CPU load signal read from /proc/stat aggregate line (later
#   found insufficient -- see v3.6 changelog above).
# - GPU_TIER retains the exact v3.4 GPU_BUSY-driven hysteresis, renamed
#   MEDIUM_STUCK_TICKS -> GPU_MEDIUM_STUCK_TICKS for clarity.
# - CPU_TIER got its own mirrored hysteresis (CPU_HEAVY_UP/DOWN,
#   CPU_MEDIUM_UP/DOWN) and its own heavy->medium descent debounce
#   (CPU_HEAVY_DOWN_TICKS).
# - Thermal sub-tier logic split: CPU_HTIER reacts only to CTEMP, GPU_HTIER
#   reacts only to GTEMP (was worst-case-wins across both domains in v3.2-v3.4).
# - Force-tier testing hooks: /sdcard/pulse_lite.force (both domains, legacy),
#   /sdcard/pulse_lite.force_cpu, /sdcard/pulse_lite.force_gpu (new).

# CHANGELOG v3.4 (vs v3.3):
# - Added HEAVY_DOWN_TICKS debounce on heavy->medium GPU descent.
# - Reduced MEDIUM_STUCK_TICKS 30 -> 8 (60s -> 16s).

# CHANGELOG v3.3 (vs v3.2):
# - Fixed feedback deadlock: CPU-bound game stuck in medium tier forever.
#   Added MEDIUM_STUCK_TICKS safety valve.

# CHANGELOG v3.2 (vs v3.0):
# - Dropped single TEMP_ZONE (skin-msm-therm ~16C off vs overlay SKN).
#   Independent CPU_TEMP / GPU_TEMP raw signals now drive thermal sub-tiers.

# Deploy: adb push pulse_lite_v3.6.sh /sdcard/pulse_lite.sh
# Start:  Root Script -> paste start.sh
# Stop:   adb shell 'touch /sdcard/pulse_lite.stop'
# Status: adb shell sh /sdcard/pulse_lite.sh status
# Force BOTH domains (legacy, testing): adb shell 'echo heavy > /sdcard/pulse_lite.force'
# Force CPU only (testing):             adb shell 'echo heavy > /sdcard/pulse_lite.force_cpu'
# Force GPU only (testing):             adb shell 'echo heavy > /sdcard/pulse_lite.force_gpu'

SENTINEL=/sdcard/pulse_lite.stop
FORCE=/sdcard/pulse_lite.force
FORCE_CPU=/sdcard/pulse_lite.force_cpu
FORCE_GPU=/sdcard/pulse_lite.force_gpu

# -- sysfs nodes --------------------------------------------------------------
CPU0=/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq   # A520 cpu0-1
CPU2=/sys/devices/system/cpu/cpufreq/policy2/scaling_max_freq   # A720m cpu2-4
CPU5=/sys/devices/system/cpu/cpufreq/policy5/scaling_max_freq   # A720h cpu5-6
CPU7=/sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq   # X4 cpu7
GPU_CAP=/sys/class/kgsl/kgsl-3d0/max_pwrlevel
GPU_BUSY=/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage
PROC_STAT=/proc/stat

# -- CPU caps (Hz) -- all values verified against scaling_available_frequencies -
CPU0_STOCK=2265600
CPU2_STOCK=3148800
CPU5_STOCK=2956800
CPU7_STOCK=3052800

CPU0_HMID=1804800
CPU2_HMID=2438400
CPU5_HMID=2438400
CPU7_HMID=2438400

CPU0_HLOW=1459200
CPU2_HLOW=2035200
CPU5_HLOW=2035200
CPU7_HLOW=2035200

CPU0_ECO=1344000
CPU2_ECO=1708800
CPU5_ECO=1708800
CPU7_ECO=1824000

# -- GPU pwrlevel (INVERTED: HIGHER index = SLOWER) ---------------------------
# 0=1050 1=1000 2=903 3=834 4=770 5=720 6=680 7=629 8=578 9=500 10=422
GPU_UNCAP=0
GPU_HIGH=6
GPU_MID=7
GPU_LOW=8
GPU_ECO=9

# -- GPU hysteresis thresholds (%) -- unchanged from v3.4/v3.5 ----------------
HEAVY_UP=70
HEAVY_DOWN=45
MEDIUM_UP=20
MEDIUM_DOWN=10

# -- GPU heavy->medium descent debounce (v3.4, unchanged) ---------------------
HEAVY_DOWN_TICKS=5      # 5 * INTERVAL(2s) = 10s sustained low-busy required

# -- GPU medium deadlock safety valve (v3.3, renamed in v3.5, unchanged) ------
GPU_MEDIUM_STUCK_TICKS=8   # 8 * INTERVAL(2s) = 16s

# -- CPU hysteresis thresholds (%) -- now driven by PER-CORE MAX busy% (v3.6),
# not aggregate avg (v3.5). Values kept as placeholders from v3.5 for now --
# per-core max busy% behaves very differently from avg busy%, so these will
# very likely need re-tuning after a soak test (see handoff Open Questions).
CPU_HEAVY_UP=70
CPU_HEAVY_DOWN=45
CPU_MEDIUM_UP=20
CPU_MEDIUM_DOWN=10

# -- CPU heavy->medium descent debounce -- unchanged from v3.5 ----------------
CPU_HEAVY_DOWN_TICKS=5   # 5 * INTERVAL(2s) = 10s sustained low-cpu required

# -- heavy sub-tier thresholds (millidegrees C) -- unchanged from v3.5 --------
CPU_HOT=78000
CPU_HOTTER=85000
CPU_COOL_HOT=74000
CPU_COOL_HOTTER=81000

GPU_HOT=75000
GPU_HOTTER=82000
GPU_COOL_HOT=71000
GPU_COOL_HOTTER=78000

# -- misc ----------------------------------------------------------------------
INTERVAL=2
LOG=/sdcard/pulse_lite.log
LOG_MAX=400
LOG_KEEP=50

# -- status mode ----------------------------------------------------------------
if [ "$1" = "status" ]; then
  PID=$(pgrep -f pulse_lite.sh 2>/dev/null | grep -v $$)
  if [ -n "$PID" ]; then
    echo "RUNNING pid=$PID"
    tail -5 "$LOG" 2>/dev/null
  else
    echo "NOT RUNNING"
  fi
  exit 0
fi

# -- resolve thermal zones dynamically at startup ------------------------------
resolve_zones() {
  PREFIX=$1
  for f in /sys/class/thermal/thermal_zone*/type; do
    t=$(cat "$f" 2>/dev/null)
    case "$t" in
      ${PREFIX}*) echo "${f%/type}/temp" ;;
    esac
  done
}

CPU_ZONES=$(resolve_zones "cpuss-")
GPU_ZONES=$(resolve_zones "gpuss-")

read_max_temp() {
  MAX=0
  for z in $1; do
    v=$(cat "$z" 2>/dev/null | tr -cd '0-9')
    v=${v:-0}
    [ "$v" -gt "$MAX" ] && MAX=$v
  done
  echo "$MAX"
}

# -- v3.6: per-core CPU busy%, MAX across cores (replaces v3.5 aggregate avg) -
# Parses every "cpuN" line in /proc/stat individually (NOT the aggregate
# "cpu " summary line). Computes delta-based busy% per core since the last
# tick, and returns the highest value across all cores -- same worst-case/
# hottest-wins pattern as read_max_temp(). This detects single-thread-bound
# workloads that a global average would mask (e.g. one hot render thread on
# an otherwise idle 8-core SoC).
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

# -- helpers ---------------------------------------------------------------------
lock_write() {
  chmod 644 "$1" 2>/dev/null
  echo "$2" > "$1"
  chmod 444 "$1" 2>/dev/null
}

unlock_restore() {
  chmod 644 "$1" 2>/dev/null
  echo "$2" > "$1"
}

log() {
  echo "$(date '+%H:%M:%S') $*" >> "$LOG"
}

# -- apply CPU caps (independent of GPU) --------------------------------------
apply_cpu_heavy_high() {
  lock_write "$CPU0" $CPU0_STOCK
  lock_write "$CPU2" $CPU2_STOCK
  lock_write "$CPU5" $CPU5_STOCK
  lock_write "$CPU7" $CPU7_STOCK
}

apply_cpu_heavy_mid() {
  lock_write "$CPU0" $CPU0_HMID
  lock_write "$CPU2" $CPU2_HMID
  lock_write "$CPU5" $CPU5_HMID
  lock_write "$CPU7" $CPU7_HMID
}

apply_cpu_heavy_low() {
  lock_write "$CPU0" $CPU0_HLOW
  lock_write "$CPU2" $CPU2_HLOW
  lock_write "$CPU5" $CPU5_HLOW
  lock_write "$CPU7" $CPU7_HLOW
}

apply_cpu_medium() {
  lock_write "$CPU0" $CPU0_ECO
  lock_write "$CPU2" $CPU2_ECO
  lock_write "$CPU5" $CPU5_ECO
  lock_write "$CPU7" $CPU7_ECO
}

apply_cpu_idle() {
  lock_write "$CPU0" $CPU0_ECO
  lock_write "$CPU2" $CPU2_ECO
  lock_write "$CPU5" $CPU5_ECO
  lock_write "$CPU7" $CPU7_ECO
}

# -- apply GPU caps (independent of CPU) --------------------------------------
apply_gpu_heavy_high() { lock_write "$GPU_CAP" $GPU_HIGH; }
apply_gpu_heavy_mid()  { lock_write "$GPU_CAP" $GPU_MID; }
apply_gpu_heavy_low()  { lock_write "$GPU_CAP" $GPU_LOW; }
apply_gpu_medium()     { lock_write "$GPU_CAP" $GPU_MID; }
apply_gpu_idle()       { lock_write "$GPU_CAP" $GPU_ECO; }

# -- restore stock on exit (trap) ---------------------------------------------
restore() {
  unlock_restore "$CPU0" $CPU0_STOCK
  unlock_restore "$CPU2" $CPU2_STOCK
  unlock_restore "$CPU5" $CPU5_STOCK
  unlock_restore "$CPU7" $CPU7_STOCK
  unlock_restore "$GPU_CAP" $GPU_UNCAP
  log "STOP: stock restored pid=$$"
}

trap restore EXIT INT TERM

# -- startup --------------------------------------------------------------------
rm -f "$SENTINEL"
init_core_arrays
log "START: pid=$$ ppid=$(cut -d' ' -f4 /proc/$$/stat 2>/dev/null) v3.6"
log "id=$(id 2>/dev/null)"
log "core_count=$CORE_COUNT"
log "cpu_zones=$(echo $CPU_ZONES | tr '\n' ' ')"
log "gpu_zones=$(echo $GPU_ZONES | tr '\n' ' ')"

CPU_TIER=idle
CPU_HTIER=heavy_high
GPU_TIER=idle
GPU_HTIER=heavy_high
PREV_CPU_TIER=""
PREV_CPU_HTIER=""
PREV_GPU_TIER=""
PREV_GPU_HTIER=""
TICK=0
CPU_MEDIUM_TICK=0
CPU_HEAVY_DOWN_TICK=0
GPU_MEDIUM_TICK=0
GPU_HEAVY_DOWN_TICK=0
FLOOR_APPLIED=0

# prime the per-core /proc/stat baseline before the loop so tick 1 has a real delta
read_cpu_busy_max >/dev/null

# -- main loop --------------------------------------------------------------------
while true; do
  TICK=$((TICK + 1))

  if [ -f "$SENTINEL" ]; then
    rm -f "$SENTINEL"
    log "STOP: sentinel, exiting cleanly"
    exit 0
  fi

  GPU_BUSY_VAL=$(cat "$GPU_BUSY" 2>/dev/null | tr -cd '0-9')
  GPU_BUSY_VAL=${GPU_BUSY_VAL:-0}

  CPU_BUSY_VAL=$(read_cpu_busy_max)
  CPU_BUSY_VAL=${CPU_BUSY_VAL:-0}

  FORCED_BOTH=""
  FORCED_CPU=""
  FORCED_GPU=""
  [ -f "$FORCE" ]     && FORCED_BOTH=$(cat "$FORCE" 2>/dev/null | tr -d '[:space:]')
  [ -f "$FORCE_CPU" ] && FORCED_CPU=$(cat "$FORCE_CPU" 2>/dev/null | tr -d '[:space:]')
  [ -f "$FORCE_GPU" ] && FORCED_GPU=$(cat "$FORCE_GPU" 2>/dev/null | tr -d '[:space:]')

  # ---- CPU tier resolution ----
  if [ -n "$FORCED_BOTH" ]; then
    CPU_TIER=$FORCED_BOTH
    CPU_MEDIUM_TICK=0
    CPU_HEAVY_DOWN_TICK=0
  elif [ -n "$FORCED_CPU" ]; then
    CPU_TIER=$FORCED_CPU
    CPU_MEDIUM_TICK=0
    CPU_HEAVY_DOWN_TICK=0
  else
    case $CPU_TIER in
      idle)
        [ "$CPU_BUSY_VAL" -gt "$CPU_MEDIUM_UP" ] && CPU_TIER=medium
        ;;
      medium)
        if [ "$CPU_BUSY_VAL" -gt "$CPU_HEAVY_UP" ]; then
          CPU_TIER=heavy
          CPU_MEDIUM_TICK=0
          CPU_HEAVY_DOWN_TICK=0
        elif [ "$CPU_BUSY_VAL" -lt "$CPU_MEDIUM_DOWN" ]; then
          CPU_TIER=idle
          CPU_MEDIUM_TICK=0
        else
          CPU_MEDIUM_TICK=$((CPU_MEDIUM_TICK + 1))
        fi
        ;;
      heavy)
        if [ "$CPU_BUSY_VAL" -lt "$CPU_HEAVY_DOWN" ]; then
          CPU_HEAVY_DOWN_TICK=$((CPU_HEAVY_DOWN_TICK + 1))
          if [ "$CPU_HEAVY_DOWN_TICK" -ge "$CPU_HEAVY_DOWN_TICKS" ]; then
            CPU_TIER=medium
            CPU_HEAVY_DOWN_TICK=0
            CPU_MEDIUM_TICK=0
          fi
        else
          CPU_HEAVY_DOWN_TICK=0
        fi
        ;;
    esac
  fi

  # ---- GPU tier resolution ----
  if [ -n "$FORCED_BOTH" ]; then
    GPU_TIER=$FORCED_BOTH
    GPU_MEDIUM_TICK=0
    GPU_HEAVY_DOWN_TICK=0
  elif [ -n "$FORCED_GPU" ]; then
    GPU_TIER=$FORCED_GPU
    GPU_MEDIUM_TICK=0
    GPU_HEAVY_DOWN_TICK=0
  else
    case $GPU_TIER in
      idle)
        [ "$GPU_BUSY_VAL" -gt "$MEDIUM_UP" ] && GPU_TIER=medium
        ;;
      medium)
        if [ "$GPU_BUSY_VAL" -gt "$HEAVY_UP" ]; then
          GPU_TIER=heavy
          GPU_MEDIUM_TICK=0
          GPU_HEAVY_DOWN_TICK=0
        elif [ "$GPU_BUSY_VAL" -lt "$MEDIUM_DOWN" ]; then
          GPU_TIER=idle
          GPU_MEDIUM_TICK=0
        else
          GPU_MEDIUM_TICK=$((GPU_MEDIUM_TICK + 1))
          if [ "$GPU_MEDIUM_TICK" -ge "$GPU_MEDIUM_STUCK_TICKS" ]; then
            GPU_TIER=heavy
            GPU_MEDIUM_TICK=0
            GPU_HEAVY_DOWN_TICK=0
            log "WARN: forced GPU heavy after ${GPU_MEDIUM_STUCK_TICKS} stuck ticks in medium (gpu=${GPU_BUSY_VAL}%)"
          fi
        fi
        ;;
      heavy)
        if [ "$GPU_BUSY_VAL" -lt "$HEAVY_DOWN" ]; then
          GPU_HEAVY_DOWN_TICK=$((GPU_HEAVY_DOWN_TICK + 1))
          if [ "$GPU_HEAVY_DOWN_TICK" -ge "$HEAVY_DOWN_TICKS" ]; then
            GPU_TIER=medium
            GPU_HEAVY_DOWN_TICK=0
            GPU_MEDIUM_TICK=0
          fi
        else
          GPU_HEAVY_DOWN_TICK=0
        fi
        ;;
    esac
  fi

  # ---- v3.6: GPU-driven safe floor on CPU_TIER ----
  # If GPU is in heavy, never let CPU sit at idle -- clamp to medium minimum.
  # Does not touch CPU's own hysteresis counters, only the resolved tier value.
  FLOOR_APPLIED=0
  if [ "$GPU_TIER" = "heavy" ] && [ "$CPU_TIER" = "idle" ]; then
    CPU_TIER=medium
    FLOOR_APPLIED=1
  fi

  # ---- CPU thermal sub-tier (only reacts to CTEMP) ----
  if [ "$CPU_TIER" = "heavy" ]; then
    CTEMP=$(read_max_temp "$CPU_ZONES")
    case $CPU_HTIER in
      heavy_high)
        [ "$CTEMP" -gt "$CPU_HOT" ] && CPU_HTIER=heavy_mid
        ;;
      heavy_mid)
        if [ "$CTEMP" -gt "$CPU_HOTTER" ]; then
          CPU_HTIER=heavy_low
        elif [ "$CTEMP" -lt "$CPU_COOL_HOT" ]; then
          CPU_HTIER=heavy_high
        fi
        ;;
      heavy_low)
        [ "$CTEMP" -lt "$CPU_COOL_HOTTER" ] && CPU_HTIER=heavy_mid
        ;;
    esac
  else
    CPU_HTIER=heavy_high
    CTEMP=""
  fi

  # ---- GPU thermal sub-tier (only reacts to GTEMP) ----
  if [ "$GPU_TIER" = "heavy" ]; then
    GTEMP=$(read_max_temp "$GPU_ZONES")
    case $GPU_HTIER in
      heavy_high)
        [ "$GTEMP" -gt "$GPU_HOT" ] && GPU_HTIER=heavy_mid
        ;;
      heavy_mid)
        if [ "$GTEMP" -gt "$GPU_HOTTER" ]; then
          GPU_HTIER=heavy_low
        elif [ "$GTEMP" -lt "$GPU_COOL_HOT" ]; then
          GPU_HTIER=heavy_high
        fi
        ;;
      heavy_low)
        [ "$GTEMP" -lt "$GPU_COOL_HOTTER" ] && GPU_HTIER=heavy_mid
        ;;
    esac
  else
    GPU_HTIER=heavy_high
    GTEMP=""
  fi

  # ---- apply CPU caps ----
  case $CPU_TIER in
    heavy)
      case $CPU_HTIER in
        heavy_high) apply_cpu_heavy_high ;;
        heavy_mid)  apply_cpu_heavy_mid ;;
        heavy_low)  apply_cpu_heavy_low ;;
      esac
      ;;
    medium) apply_cpu_medium ;;
    idle)   apply_cpu_idle ;;
    *)
      log "WARN: unknown CPU tier '$CPU_TIER', falling back to idle"
      CPU_TIER=idle
      apply_cpu_idle
      ;;
  esac

  # ---- apply GPU caps ----
  case $GPU_TIER in
    heavy)
      case $GPU_HTIER in
        heavy_high) apply_gpu_heavy_high ;;
        heavy_mid)  apply_gpu_heavy_mid ;;
        heavy_low)  apply_gpu_heavy_low ;;
      esac
      ;;
    medium) apply_gpu_medium ;;
    idle)   apply_gpu_idle ;;
    *)
      log "WARN: unknown GPU tier '$GPU_TIER', falling back to idle"
      GPU_TIER=idle
      apply_gpu_idle
      ;;
  esac

  if [ "$CPU_TIER" != "$PREV_CPU_TIER" ] || [ "$CPU_HTIER" != "$PREV_CPU_HTIER" ] \
     || [ "$GPU_TIER" != "$PREV_GPU_TIER" ] || [ "$GPU_HTIER" != "$PREV_GPU_HTIER" ] \
     || [ $((TICK % 60)) -eq 0 ]; then
    C0=$(cat "$CPU0" 2>/dev/null)
    C2=$(cat "$CPU2" 2>/dev/null)
    C5=$(cat "$CPU5" 2>/dev/null)
    C7=$(cat "$CPU7" 2>/dev/null)
    GC=$(cat "$GPU_CAP" 2>/dev/null)
    log "cpu_tier=$CPU_TIER/$CPU_HTIER cpu_busy_max=${CPU_BUSY_VAL}% cpuT=${CTEMP} floor=${FLOOR_APPLIED} caps: p0=$C0 p2=$C2 p5=$C5 p7=$C7 | gpu_tier=$GPU_TIER/$GPU_HTIER gpu_busy=${GPU_BUSY_VAL}% gpuT=${GTEMP} gpu_cap=$GC"
    PREV_CPU_TIER=$CPU_TIER
    PREV_CPU_HTIER=$CPU_HTIER
    PREV_GPU_TIER=$GPU_TIER
    PREV_GPU_HTIER=$GPU_HTIER
  fi

  if [ $((TICK % 60)) -eq 0 ]; then
    LINES=$(wc -l < "$LOG" 2>/dev/null || echo 0)
    if [ "$LINES" -gt "$LOG_MAX" ]; then
      tail -$LOG_KEEP "$LOG" > "${LOG}.tmp" 2>/dev/null \
        && mv "${LOG}.tmp" "$LOG"
    fi
  fi

  sleep $INTERVAL
done
