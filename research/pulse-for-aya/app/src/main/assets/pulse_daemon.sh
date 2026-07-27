#!/system/bin/sh
# One-xsu-connection-per-session cap-write daemon. Launched ONCE via
# `xsu -c "sh pulse_daemon.sh <fifo_in> <log> > /dev/null 2>&1 < /dev/null &"`
# (research/pulse-for-aya/root/PulseDaemon.kt); everything after that is a plain
# shell builtin already running as root -- zero further xsu/xsud connections.
# Protocol on the input FIFO, one line per command: "<sysfs_path> <chmod_mode> <value>\n"
# (mode matches each control's own existing convention -- e.g. 644 for governor writes,
# 444 for CPU/GPU frequency caps, so the vendor perf daemon can't silently stomp them).
# The literal line "STOP" (no mode/value) ends the daemon.
#
# Validated architecture: research/pulse-for-aya/scripts/daemon-persistence-test.sh
# and fifo-daemon-test.sh (see STATUS.md's Minecraft-crash investigation,
# 2026-07-27, "New direction found and validated" + the FIFO update).

FIFO_IN=$1
LOG=$2

rm -f "$FIFO_IN"
mkfifo "$FIFO_IN"
chmod 666 "$FIFO_IN"
echo "start $(date +%s) pid=$$" > "$LOG"

while true; do
  IFS=' ' read -r path mode value < "$FIFO_IN" || continue
  if [ "$path" = "STOP" ]; then
    echo "stop $(date +%s)" >> "$LOG"
    break
  fi
  chmod 666 "$path" 2>/dev/null
  echo "$value" > "$path" 2>/dev/null
  chmod "$mode" "$path" 2>/dev/null
done

rm -f "$FIFO_IN"
