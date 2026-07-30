#!/system/bin/sh
# fan_reassert_probe2.sh -- follow-up to fan_reassert_probe.sh. That run
# showed a single reassert event ~18s after a manual duty write, then
# rock-stable for 99+s afterward -- not a continuous fight. Open
# question: does a SECOND write (right after the first reassert) also
# get reasserted ~18s later (a per-write reactive correction), or does
# it stay stable indefinitely (a one-time-only correction)? Detects the
# first reassert automatically (polls until duty != target) instead of
# guessing a fixed wait, then re-writes and watches again.

DUTY=150
MAX_WAIT_FOR_REASSERT=60
SETTLE_POLL_SECONDS=120

PWR=/sys/devices/platform/soc/soc:pwm-fan/fan_power_state
PWM=/sys/devices/platform/soc/soc:pwm-fan/hwmon/hwmon0/pwm1
RPM=/sys/devices/platform/soc/soc:pwm-fan/fan_rpm_state

mkdir -p /sdcard/apl_pulse_logs
LOG=/sdcard/apl_pulse_logs/fan_reassert_probe2_$(date +%s).log

chmod 666 "$PWR" "$PWM"
echo 1 > "$PWR"
echo "$DUTY" > "$PWM"
echo "write #1: duty=${DUTY} at t=0" >> "$LOG"

i=0
reasserted=0
while [ "$i" -lt "$MAX_WAIT_FOR_REASSERT" ]; do
  ts=$(date +%s)
  rpm=$(cat "$RPM")
  duty=$(cat "$PWM")
  echo "t=${i}s ts=${ts} rpm=${rpm} duty=${duty}" >> "$LOG"
  if [ "$duty" != "$DUTY" ]; then
    echo "REASSERT #1 detected at t=${i}s (duty now ${duty})" >> "$LOG"
    reasserted=1
    break
  fi
  sleep 1
  i=$((i + 1))
done

if [ "$reasserted" = "0" ]; then
  echo "no reassert seen within ${MAX_WAIT_FOR_REASSERT}s, stopping probe" >> "$LOG"
  exit 0
fi

echo 1 > "$PWR"
echo "$DUTY" > "$PWM"
echo "write #2: duty=${DUTY} at t=${i}s (right after reassert #1)" >> "$LOG"

j=0
while [ "$j" -lt "$SETTLE_POLL_SECONDS" ]; do
  ts=$(date +%s)
  rpm=$(cat "$RPM")
  duty=$(cat "$PWM")
  echo "post2 t=${j}s ts=${ts} rpm=${rpm} duty=${duty}" >> "$LOG"
  if [ "$duty" != "$DUTY" ] && [ "$j" -gt "0" ]; then
    echo "REASSERT #2 detected at post2 t=${j}s (duty now ${duty})" >> "$LOG"
  fi
  sleep 1
  j=$((j + 1))
done
echo "probe2 done" >> "$LOG"
