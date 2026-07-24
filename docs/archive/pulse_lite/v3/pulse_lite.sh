#!/bin/sh
# pulse_lite v2.2 — AutoTDP daemon, AYANEO Pocket S2 / Snapdragon G3 Gen 3
# 4-policy CPU coverage: A520(cpu0-1) + A720-mid(cpu2-4) + A720-high(cpu5-6) + X4(cpu7)
# Deploy:  adb push pulse_lite.sh /sdcard/pulse_lite.sh
# Start:   Root Script → paste start.sh
# Stop:    adb shell 'touch /sdcard/pulse_lite.stop'
# Status:  adb shell sh /sdcard/pulse_lite.sh status

SENTINEL=/sdcard/pulse_lite.stop

# ── sysfs nodes ──────────────────────────────────────────────────────────────
CPU0=/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq   # A520  cpu0-1
CPU2=/sys/devices/system/cpu/cpufreq/policy2/scaling_max_freq   # A720m cpu2-4
CPU5=/sys/devices/system/cpu/cpufreq/policy5/scaling_max_freq   # A720h cpu5-6
CPU7=/sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq   # X4    cpu7
GPU_CAP=/sys/class/kgsl/kgsl-3d0/max_pwrlevel
GPU_BUSY=/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage

# ── CPU caps (Hz) — all values verified in OPP tables ────────────────────────
# STOCK: full performance (heavy tier), skip turbo bins
CPU0_STOCK=2265600   # A520  true max
CPU2_STOCK=3148800   # A720m true max (includes boost OPPs)
CPU5_STOCK=2956800   # A720h true max (includes boost OPPs)
CPU7_STOCK=3052800   # X4    max without 3302400 turbo bin

# ECO: ~60% of non-boost max (idle + medium tier)
CPU0_ECO=1344000     # A520  OPP ✓ (1363200 snap-down → 1344000, use directly)
CPU2_ECO=1708800     # A720m OPP ✓
CPU5_ECO=1708800     # A720h OPP ✓
CPU7_ECO=1824000     # X4    OPP ✓

# ── GPU pwrlevel (INVERTED: HIGHER index = SLOWER) ───────────────────────────
# 0=1050 1=1000 2=903 3=834 4=770 5=720 6=680 7=629 8=578 9=500 10=422
GPU_UNCAP=0    # 1050 MHz — stock / restore target
GPU_MED=6      #  680 MHz — heavy + medium
GPU_ECO=9      #  500 MHz — idle

# ── hysteresis thresholds (%) ─────────────────────────────────────────────────
HEAVY_UP=70      # → heavy above this
HEAVY_DOWN=45    # heavy → medium only below this
MEDIUM_UP=20     # → medium above this
MEDIUM_DOWN=10   # medium → idle only below this

# ── misc ─────────────────────────────────────────────────────────────────────
INTERVAL=2
LOG=/sdcard/pulse_lite.log
LOG_MAX=400
LOG_KEEP=50

# ── status mode ──────────────────────────────────────────────────────────────
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

# ── helpers ──────────────────────────────────────────────────────────────────
lock_write() {
    chmod 644 "$1" 2>/dev/null
    echo "$2" > "$1"
    chmod 444 "$1" 2>/dev/null
}

unlock_restore() {
    chmod 644 "$1" 2>/dev/null
    echo "$2" > "$1"
    # leave 644 — AYASpace can take over
}

log() {
    echo "$(date '+%H:%M:%S') $*" >> "$LOG"
}

# ── apply tier caps ───────────────────────────────────────────────────────────
apply_heavy() {
    lock_write "$CPU0"    $CPU0_STOCK
    lock_write "$CPU2"    $CPU2_STOCK
    lock_write "$CPU5"    $CPU5_STOCK
    lock_write "$CPU7"    $CPU7_STOCK
    lock_write "$GPU_CAP" $GPU_MED
}

apply_medium() {
    lock_write "$CPU0"    $CPU0_ECO
    lock_write "$CPU2"    $CPU2_ECO
    lock_write "$CPU5"    $CPU5_ECO
    lock_write "$CPU7"    $CPU7_ECO
    lock_write "$GPU_CAP" $GPU_MED
}

apply_idle() {
    lock_write "$CPU0"    $CPU0_ECO
    lock_write "$CPU2"    $CPU2_ECO
    lock_write "$CPU5"    $CPU5_ECO
    lock_write "$CPU7"    $CPU7_ECO
    lock_write "$GPU_CAP" $GPU_ECO
}

# ── restore stock on exit (trap) ──────────────────────────────────────────────
restore() {
    unlock_restore "$CPU0"    $CPU0_STOCK
    unlock_restore "$CPU2"    $CPU2_STOCK
    unlock_restore "$CPU5"    $CPU5_STOCK
    unlock_restore "$CPU7"    $CPU7_STOCK
    unlock_restore "$GPU_CAP" $GPU_UNCAP
    log "STOP: stock restored pid=$$"
}

trap restore EXIT INT TERM

# ── startup ───────────────────────────────────────────────────────────────────
rm -f "$SENTINEL"
log "START: pid=$$ ppid=$(cut -d' ' -f4 /proc/$$/stat 2>/dev/null)"

TIER=idle
PREV_TIER=""
TICK=0

# ── main loop ─────────────────────────────────────────────────────────────────
while true; do
    TICK=$((TICK + 1))

    # sentinel stop — no root needed: adb shell 'touch /sdcard/pulse_lite.stop'
    if [ -f "$SENTINEL" ]; then
        rm -f "$SENTINEL"
        log "STOP: sentinel, exiting cleanly"
        exit 0
    fi

    # GPU busy — strip everything except digits
    BUSY=$(cat "$GPU_BUSY" 2>/dev/null | tr -cd '0-9')
    BUSY=${BUSY:-0}

    # ── hysteresis state machine ──────────────────────────────────────────────
    case $TIER in
        idle)
            [ "$BUSY" -gt "$MEDIUM_UP" ] && TIER=medium
            ;;
        medium)
            if   [ "$BUSY" -gt "$HEAVY_UP" ];   then TIER=heavy
            elif [ "$BUSY" -lt "$MEDIUM_DOWN" ]; then TIER=idle
            fi
            ;;
        heavy)
            [ "$BUSY" -lt "$HEAVY_DOWN" ] && TIER=medium
            ;;
    esac

    # ── apply ────────────────────────────────────────────────────────────────
    case $TIER in
        heavy)  apply_heavy  ;;
        medium) apply_medium ;;
        idle)   apply_idle   ;;
    esac

    # ── log on tier change or heartbeat (120s) ────────────────────────────────
    if [ "$TIER" != "$PREV_TIER" ] || [ $((TICK % 60)) -eq 0 ]; then
        # read caps back for verification
        C0=$(cat "$CPU0" 2>/dev/null)
        C2=$(cat "$CPU2" 2>/dev/null)
        C5=$(cat "$CPU5" 2>/dev/null)
        C7=$(cat "$CPU7" 2>/dev/null)
        log "tier=$TIER gpu=${BUSY}% caps: p0=$C0 p2=$C2 p5=$C5 p7=$C7"
        PREV_TIER=$TIER
    fi

    # ── log rotation (every 120s) ─────────────────────────────────────────────
    if [ $((TICK % 60)) -eq 0 ]; then
        LINES=$(wc -l < "$LOG" 2>/dev/null || echo 0)
        if [ "$LINES" -gt "$LOG_MAX" ]; then
            tail -$LOG_KEEP "$LOG" > "${LOG}.tmp" 2>/dev/null \
                && mv "${LOG}.tmp" "$LOG"
        fi
    fi

    sleep $INTERVAL
done
