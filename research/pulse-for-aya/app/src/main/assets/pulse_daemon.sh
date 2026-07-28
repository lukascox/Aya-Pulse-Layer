#!/system/bin/sh
# One-xsu-connection-per-session cap-write daemon. Launched ONCE via
# `xsu -c "sh pulse_daemon.sh <fifo_in> <log> <sdcard_log> <cap_poll_log> <version_label> <fifo_out> > /dev/null 2>&1 < /dev/null &"`
# (research/pulse-for-aya/root/PulseDaemon.kt); everything after that is a plain
# shell builtin already running as root -- zero further xsu/xsud connections.
# Protocol on the input FIFO, one line per command:
#   "<sysfs_path> <chmod_mode> <value>" -- unlock/write/relock a sysfs node (mode matches each
#     control's own existing convention -- e.g. 644 for governor writes, 444 for CPU/GPU
#     frequency caps, so the vendor perf daemon can't silently stomp them).
#   "LOG <message>" -- append a timestamped line to $SDCARD_LOG (app-side diagnostic logging,
#     independent of logcat -- see PulseDaemon.kt's `log()` doc).
#   "READ <path1> <path2> ..." -- `cat` each path and write ONE `|`-delimited response line to
#     $FIFO_OUT, in the same order (empty for a path that doesn't exist/fails) -- see
#     PulseDaemon.kt's `readBatch()` doc. Added 2026-07-28 to replace TelemetryReader's ~13-15
#     separate xsu connections per tick (the dominant xsud-crash contributor, STATUS.md) with one.
#   "STOP" -- ends the daemon.
#
# Validated architecture: research/pulse-for-aya/scripts/daemon-persistence-test.sh
# and fifo-daemon-test.sh (see STATUS.md's Minecraft-crash investigation,
# 2026-07-27, "New direction found and validated" + the FIFO update).
#
# STATUS.md, 2026-07-28: also runs the same 1 Hz cap/cur sysfs poll `scripts/poll-cpufreq.sh` did
# by hand from the host, as a background loop below -- same fields, same file format, same
# timestamp prefix as $SDCARD_LOG (both come from PulseDaemon.kt's single `sessionTimestamp`), so
# a test session now produces its ground-truth cap_poll file automatically instead of needing a
# separate `adb`-connected script running on a host machine for the whole session. Still reads
# raw sysfs directly (not through the app's own telemetry/decision code), so it stays a real
# cross-check of "did the value actually land on the device" independent of AutoTuneController's
# own internal state -- just no longer independent of this daemon script itself.
#
# STATUS.md, 2026-07-28: both log files now open with a version line ($VERSION_LABEL, built from
# BuildConfig.VERSION_NAME/VERSION_CODE/BUILD_TIMESTAMP in PulseDaemon.kt) -- passed straight in as
# a launch argument, not sent over the FIFO afterwards, so there's no startup race with the reader
# not being ready yet. Added after a suspected regression turned out to need "is this actually the
# patched build" ruled out first -- now every pulled log answers that on its own.

FIFO_IN=$1
LOG=$2
SDCARD_LOG=$3
CAP_POLL_LOG=$4
VERSION_LABEL=$5
FIFO_OUT=$6

rm -f "$FIFO_IN"
mkfifo "$FIFO_IN"
chmod 666 "$FIFO_IN"
rm -f "$FIFO_OUT"
mkfifo "$FIFO_OUT"
chmod 666 "$FIFO_OUT"
echo "start $(date +%s) pid=$$" > "$LOG"
[ -n "$SDCARD_LOG" ] && echo "$(date '+%Y-%m-%d %H:%M:%S') === pulse_daemon session start === version=$VERSION_LABEL" > "$SDCARD_LOG"
[ -n "$CAP_POLL_LOG" ] && echo "$(date '+%Y-%m-%d %H:%M:%S') === cap_poll session start === version=$VERSION_LABEL" > "$CAP_POLL_LOG"

POLL_PID=""
if [ -n "$CAP_POLL_LOG" ]; then
  (
    CPU=/sys/devices/system/cpu/cpufreq
    GPU=/sys/class/kgsl/kgsl-3d0
    [ -d "$GPU" ] || GPU=/sys/devices/platform/soc@0/3d00000.gpu/kgsl/kgsl-3d0
    while true; do
      p0c=$(cat $CPU/policy0/scaling_cur_freq 2>/dev/null); p0m=$(cat $CPU/policy0/scaling_max_freq 2>/dev/null)
      p2c=$(cat $CPU/policy2/scaling_cur_freq 2>/dev/null); p2m=$(cat $CPU/policy2/scaling_max_freq 2>/dev/null)
      p5c=$(cat $CPU/policy5/scaling_cur_freq 2>/dev/null); p5m=$(cat $CPU/policy5/scaling_max_freq 2>/dev/null)
      p7c=$(cat $CPU/policy7/scaling_cur_freq 2>/dev/null); p7m=$(cat $CPU/policy7/scaling_max_freq 2>/dev/null)
      gov=$(cat $CPU/policy0/scaling_governor 2>/dev/null)
      gpu=$(cat $GPU/gpuclk 2>/dev/null)
      [ -z "$gpu" ] && gpu=$(cat $GPU/devfreq/cur_freq 2>/dev/null)
      gpu_min=$(cat $GPU/min_pwrlevel 2>/dev/null)
      gpu_max=$(cat $GPU/max_pwrlevel 2>/dev/null)
      echo "$(date '+%Y-%m-%d %H:%M:%S') p0_cur=$p0c p0_max=$p0m p2_cur=$p2c p2_max=$p2m p5_cur=$p5c p5_max=$p5m p7_cur=$p7c p7_max=$p7m gov=$gov gpu_cur=$gpu gpu_min_pwrlevel=$gpu_min gpu_max_pwrlevel=$gpu_max" >> "$CAP_POLL_LOG"
      sleep 1
    done
  ) &
  POLL_PID=$!
fi

while true; do
  IFS= read -r line < "$FIFO_IN" || continue
  case "$line" in
    STOP)
      [ -n "$POLL_PID" ] && kill "$POLL_PID" 2>/dev/null
      echo "stop $(date +%s)" >> "$LOG"
      # Marks a CLEAN stop in both pulled files -- a log that just cuts off with no matching "session
      # end" line means the process was killed/crashed instead of stopped normally (STATUS.md,
      # 2026-07-28: needed this distinction after a suspected regression where the app's own log
      # couldn't tell "PULSE stopped itself" apart from "something crashed it").
      [ -n "$SDCARD_LOG" ] && echo "$(date '+%Y-%m-%d %H:%M:%S') === pulse_daemon session end (clean stop) ===" >> "$SDCARD_LOG"
      [ -n "$CAP_POLL_LOG" ] && echo "$(date '+%Y-%m-%d %H:%M:%S') === cap_poll session end (clean stop) ===" >> "$CAP_POLL_LOG"
      break
      ;;
    "LOG "*)
      [ -n "$SDCARD_LOG" ] && echo "$(date '+%Y-%m-%d %H:%M:%S') ${line#LOG }" >> "$SDCARD_LOG"
      ;;
    "READ "*)
      set -- ${line#READ }
      out=""
      first=1
      for p in "$@"; do
        v=$(cat "$p" 2>/dev/null)
        if [ "$first" = "1" ]; then out="$v"; first=0; else out="$out|$v"; fi
      done
      echo "$out" > "$FIFO_OUT"
      ;;
    *)
      set -- $line
      path=$1
      mode=$2
      value=$3
      chmod 666 "$path" 2>/dev/null
      echo "$value" > "$path" 2>/dev/null
      chmod "$mode" "$path" 2>/dev/null
      ;;
  esac
done

rm -f "$FIFO_IN"
rm -f "$FIFO_OUT"
