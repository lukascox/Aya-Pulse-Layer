#!/bin/sh
# pulse_lite v3.3 -- AutoTDP daemon, KONKR Pocket FIT / Snapdragon G3 Gen 3
# 4-policy CPU coverage: A520(cpu0-1) + A720-mid(cpu2-4) + A720-high(cpu5-6) + X4(cpu7)

# CHANGELOG v3.3 (vs v3.2):
# - Fixed feedback deadlock: if GPU is CPU-bound in medium tier (game runs at
#   low FPS but never pushes GPU_BUSY above HEAVY_UP), the daemon would get
#   stuck in medium forever, denying the CPU/GPU the clocks needed to actually
#   relieve the bottleneck. Observed live: gpu=58%->12% oscillating in medium
#   for 90+ seconds while game ran at 15fps.
# - Added MEDIUM_STUCK_TICKS safety valve: if TIER stays in medium for N
#   consecutive ticks without resolving to idle or heavy naturally, force
#   escalate to heavy once. This is a safety net, not a primary trigger --
#   GPU_BUSY hysteresis remains the main driver.
# - CPU_HOT/GPU_HOT etc. remain PLACEHOLDERS -- see note in v3.2. Partial
#   validation from first soak test below; heavy_low and GPU-driven
#   transitions still unverified (see analysis).

# CHANGELOG v3.2 (vs v3.0):
# - Dropped single TEMP_ZONE (skin-msm-therm proved to be ~16C off vs overlay SKN --
#   it is a vendor-calculated composite, not a raw sysfs zone. Do not use it.)
# - Two independent raw signals now drive the heavy sub-tier decision:
#     CPU_TEMP = max of cpuss-0..cpuss-3
#     GPU_TEMP = max of gpuss-0..gpuss-7
# - Sub-tier downgrades if EITHER signal crosses its threshold (worst-case wins).
# - Sub-tier upgrades only once BOTH signals drop below their cool thresholds.
# - Zone paths are resolved dynamically at startup (zone numbering is not
#   guaranteed stable across reboots/firmware updates).

# Deploy: adb push pulse_lite.sh /sdcard/pulse_lite.sh
# Start:  Root Script -> paste start.sh
# Stop:   adb shell 'touch /sdcard/pulse_lite.stop'
# Status: adb shell sh /sdcard/pulse_lite.sh status
# Force tier (testing): adb shell 'echo heavy > /sdcard/pulse_lite.force'

SENTINEL=/sdcard/pulse_lite.stop
FORCE=/sdcard/pulse_lite.force

# -- sysfs nodes --------------------------------------------------------------
CPU0=/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq   # A520  cpu0-1
CPU2=/sys/devices/system/cpu/cpufreq/policy2/scaling_max_freq   # A720m cpu2-4
CPU5=/sys/devices/system/cpu/cpufreq/policy5/scaling_max_freq   # A720h cpu5-6
CPU7=/sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq   # X4    cpu7
GPU_CAP=/sys/class/kgsl/kgsl-3d0/max_pwrlevel
GPU_BUSY=/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage

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

# -- hysteresis thresholds (%) -- GPU busy% drives idle/medium/heavy ----------
HEAVY_UP=70
HEAVY_DOWN=45
MEDIUM_UP=20
MEDIUM_DOWN=10

# -- medium deadlock safety valve (v3.3) --------------------------------------
# If GPU is CPU-bound while capped in medium, GPU_BUSY% may never cross
# HEAVY_UP even though the game is starved. Force one escalation to heavy
# after N consecutive ticks stuck in medium (not resolving to idle either).
MEDIUM_STUCK_TICKS=30   # 30 * INTERVAL(2s) = 60s

# -- heavy sub-tier thresholds (millidegrees C) -- PARTIALLY VALIDATED, see
# analysis notes below. CPU_HOT confirmed in right ballpark by first soak
# test (transition observed at cpuT=78300-78700). CPU_COOL_HOT, GPU_HOT,
# GPU_COOL_HOT, and both HOTTER (heavy_low) thresholds remain UNVALIDATED --
# no data point ever approached them in the first session.
CPU_HOT=78000          # cpuss max above this -> heavy_mid
CPU_HOTTER=85000        # cpuss max above this -> heavy_low
CPU_COOL_HOT=74000      # cpuss max below this -> allowed back to heavy_high
CPU_COOL_HOTTER=81000   # cpuss max below this -> allowed back to heavy_mid

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

# -- apply tier caps ---------------------------------------------------------
apply_heavy_high() {
  lock_write "$CPU0" $CPU0_STOCK
  lock_write "$CPU2" $CPU2_STOCK
  lock_write "$CPU5" $CPU5_STOCK
  lock_write "$CPU7" $CPU7_STOCK
  lock_write "$GPU_CAP" $GPU_HIGH
}

apply_heavy_mid() {
  lock_write "$CPU0" $CPU0_HMID
  lock_write "$CPU2" $CPU2_HMID
  lock_write "$CPU5" $CPU5_HMID
  lock_write "$CPU7" $CPU7_HMID
  lock_write "$GPU_CAP" $GPU_MID
}

apply_heavy_low() {
  lock_write "$CPU0" $CPU0_HLOW
  lock_write "$CPU2" $CPU2_HLOW
  lock_write "$CPU5" $CPU5_HLOW
  lock_write "$CPU7" $CPU7_HLOW
  lock_write "$GPU_CAP" $GPU_LOW
}

apply_medium() {
  lock_write "$CPU0" $CPU0_ECO
  lock_write "$CPU2" $CPU2_ECO
  lock_write "$CPU5" $CPU5_ECO
  lock_write "$CPU7" $CPU7_ECO
  lock_write "$GPU_CAP" $GPU_MID
}

apply_idle() {
  lock_write "$CPU0" $CPU0_ECO
  lock_write "$CPU2" $CPU2_ECO
  lock_write "$CPU5" $CPU5_ECO
  lock_write "$CPU7" $CPU7_ECO
  lock_write "$GPU_CAP" $GPU_ECO
}

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
log "START: pid=$$ ppid=$(cut -d' ' -f4 /proc/$$/stat 2>/dev/null)"
log "id=$(id 2>/dev/null)"
log "cpu_zones=$(echo $CPU_ZONES | tr '\n' ' ')"
log "gpu_zones=$(echo $GPU_ZONES | tr '\n' ' ')"

TIER=idle
HTIER=heavy_high
PREV_TIER=""
PREV_HTIER=""
TICK=0
MEDIUM_TICK=0

# -- main loop --------------------------------------------------------------------
while true; do
  TICK=$((TICK + 1))

  if [ -f "$SENTINEL" ]; then
    rm -f "$SENTINEL"
    log "STOP: sentinel, exiting cleanly"
    exit 0
  fi

  BUSY=$(cat "$GPU_BUSY" 2>/dev/null | tr -cd '0-9')
  BUSY=${BUSY:-0}

  FORCED=""
  if [ -f "$FORCE" ]; then
    FORCED=$(cat "$FORCE" 2>/dev/null | tr -d '[:space:]')
  fi

  if [ -n "$FORCED" ]; then
    TIER=$FORCED
    MEDIUM_TICK=0
  else
    case $TIER in
      idle)
        [ "$BUSY" -gt "$MEDIUM_UP" ] && TIER=medium
        ;;
      medium)
        if [ "$BUSY" -gt "$HEAVY_UP" ]; then
          TIER=heavy
          MEDIUM_TICK=0
        elif [ "$BUSY" -lt "$MEDIUM_DOWN" ]; then
          TIER=idle
          MEDIUM_TICK=0
        else
          MEDIUM_TICK=$((MEDIUM_TICK + 1))
          if [ "$MEDIUM_TICK" -ge "$MEDIUM_STUCK_TICKS" ]; then
            TIER=heavy
            MEDIUM_TICK=0
            log "WARN: forced heavy after ${MEDIUM_STUCK_TICKS} stuck ticks in medium (gpu=${BUSY}%, likely CPU-bound)"
          fi
        fi
        ;;
      heavy)
        [ "$BUSY" -lt "$HEAVY_DOWN" ] && TIER=medium
        ;;
    esac
  fi

  # -- heavy sub-tier state machine (independent CPU/GPU temp signals) ---------
  if [ "$TIER" = "heavy" ]; then
    CTEMP=$(read_max_temp "$CPU_ZONES")
    GTEMP=$(read_max_temp "$GPU_ZONES")

    case $HTIER in
      heavy_high)
        if [ "$CTEMP" -gt "$CPU_HOT" ] || [ "$GTEMP" -gt "$GPU_HOT" ]; then
          HTIER=heavy_mid
        fi
        ;;
      heavy_mid)
        if [ "$CTEMP" -gt "$CPU_HOTTER" ] || [ "$GTEMP" -gt "$GPU_HOTTER" ]; then
          HTIER=heavy_low
        elif [ "$CTEMP" -lt "$CPU_COOL_HOT" ] && [ "$GTEMP" -lt "$GPU_COOL_HOT" ]; then
          HTIER=heavy_high
        fi
        ;;
      heavy_low)
        if [ "$CTEMP" -lt "$CPU_COOL_HOTTER" ] && [ "$GTEMP" -lt "$GPU_COOL_HOTTER" ]; then
          HTIER=heavy_mid
        fi
        ;;
    esac
  else
    HTIER=heavy_high
    CTEMP=""
    GTEMP=""
  fi

  case $TIER in
    heavy)
      case $HTIER in
        heavy_high) apply_heavy_high ;;
        heavy_mid)  apply_heavy_mid ;;
        heavy_low)  apply_heavy_low ;;
      esac
      ;;
    medium) apply_medium ;;
    idle)   apply_idle ;;
    *)
      log "WARN: unknown forced tier '$TIER', falling back to idle"
      TIER=idle
      apply_idle
      ;;
  esac

  if [ "$TIER" != "$PREV_TIER" ] || [ "$HTIER" != "$PREV_HTIER" ] || [ $((TICK % 60)) -eq 0 ]; then
    C0=$(cat "$CPU0" 2>/dev/null)
    C2=$(cat "$CPU2" 2>/dev/null)
    C5=$(cat "$CPU5" 2>/dev/null)
    C7=$(cat "$CPU7" 2>/dev/null)
    if [ "$TIER" = "heavy" ]; then
      log "tier=$TIER/$HTIER gpu=${BUSY}% cpuT=${CTEMP} gpuT=${GTEMP} caps: p0=$C0 p2=$C2 p5=$C5 p7=$C7"
    else
      log "tier=$TIER gpu=${BUSY}% caps: p0=$C0 p2=$C2 p5=$C5 p7=$C7 medium_tick=${MEDIUM_TICK}"
    fi
    PREV_TIER=$TIER
    PREV_HTIER=$HTIER
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
