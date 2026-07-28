#!/system/bin/sh
# One-xsu-connection-per-session cap-write daemon. Launched ONCE via
# `xsu -c "sh pulse_daemon.sh <fifo_in> <log> <sdcard_log> > /dev/null 2>&1 < /dev/null &"`
# (research/pulse-for-aya/root/PulseDaemon.kt); everything after that is a plain
# shell builtin already running as root -- zero further xsu/xsud connections.
# Protocol on the input FIFO, one line per command:
#   "<sysfs_path> <chmod_mode> <value>" -- unlock/write/relock a sysfs node (mode matches each
#     control's own existing convention -- e.g. 644 for governor writes, 444 for CPU/GPU
#     frequency caps, so the vendor perf daemon can't silently stomp them).
#   "LOG <message>" -- append a timestamped line to $SDCARD_LOG (app-side diagnostic logging,
#     independent of logcat -- see PulseDaemon.kt's `log()` doc).
#   "STOP" -- ends the daemon.
#
# Validated architecture: research/pulse-for-aya/scripts/daemon-persistence-test.sh
# and fifo-daemon-test.sh (see STATUS.md's Minecraft-crash investigation,
# 2026-07-27, "New direction found and validated" + the FIFO update).

FIFO_IN=$1
LOG=$2
SDCARD_LOG=$3

rm -f "$FIFO_IN"
mkfifo "$FIFO_IN"
chmod 666 "$FIFO_IN"
echo "start $(date +%s) pid=$$" > "$LOG"
[ -n "$SDCARD_LOG" ] && echo "$(date '+%Y-%m-%d %H:%M:%S') === pulse_daemon session start ===" > "$SDCARD_LOG"

while true; do
  IFS= read -r line < "$FIFO_IN" || continue
  case "$line" in
    STOP)
      echo "stop $(date +%s)" >> "$LOG"
      break
      ;;
    "LOG "*)
      [ -n "$SDCARD_LOG" ] && echo "$(date '+%Y-%m-%d %H:%M:%S') ${line#LOG }" >> "$SDCARD_LOG"
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
