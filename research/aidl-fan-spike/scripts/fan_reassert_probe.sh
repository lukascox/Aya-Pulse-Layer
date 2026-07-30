#!/system/bin/sh
# fan_reassert_probe.sh -- measures exactly how long a manual fan-duty
# write survives before AYANEO's own vendor daemon reasserts its value,
# at 1s resolution (adb-shell-by-hand timing was too imprecise for this).
#
# Logs to a file under /sdcard/apl_pulse_logs/ (the same directory
# pulse-for-aya's own session logs already use, confirmed accessible
# without extra setup) instead of relying on the launching xsu
# invocation's stdout redirect, which did not reliably produce a
# non-empty log when backgrounded. Constants are hardcoded (no
# positional-parameter defaulting) to avoid relying on `${1:-default}`
# expansion, which this device's minimal `sh` may not support -- the
# first run produced zero output even in the foreground, suggesting
# it failed at or near the top of the script.
#
# Run as root (via xsu), backgrounded so the launching connection
# closes immediately (same pattern as
# research/pulse-for-aya/scripts/daemon-persistence-test.sh):
#   xsu -c "sh /data/local/tmp/fan_reassert_probe.sh > /data/local/tmp/fan_reassert_probe_stderr.log 2>&1 < /dev/null &"
# (that stderr log is just a debug net in case something still fails
# silently -- the real data is the timestamped file under
# /sdcard/apl_pulse_logs/.)

SLEEP_BEFORE=60
POLL_SECONDS=180
DUTY=150

PWR=/sys/devices/platform/soc/soc:pwm-fan/fan_power_state
PWM=/sys/devices/platform/soc/soc:pwm-fan/hwmon/hwmon0/pwm1
RPM=/sys/devices/platform/soc/soc:pwm-fan/fan_rpm_state

mkdir -p /sdcard/apl_pulse_logs
LOG=/sdcard/apl_pulse_logs/fan_reassert_probe_$(date +%s).log

echo "probe start: waiting ${SLEEP_BEFORE}s before writing duty=${DUTY}" >> "$LOG"
sleep "$SLEEP_BEFORE"

chmod 666 "$PWR" "$PWM"
echo 1 > "$PWR"
echo "$DUTY" > "$PWM"
echo "wrote duty=${DUTY} at t=0" >> "$LOG"

i=0
while [ "$i" -lt "$POLL_SECONDS" ]; do
  ts=$(date +%s)
  rpm=$(cat "$RPM")
  duty=$(cat "$PWM")
  echo "t=${i}s ts=${ts} rpm=${rpm} duty=${duty}" >> "$LOG"
  sleep 1
  i=$((i + 1))
done
echo "probe done" >> "$LOG"
