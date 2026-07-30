#!/system/bin/sh
# One-xsu-connection-per-session cap-write daemon. Launched ONCE via
# `xsu -c "sh pulse_daemon.sh <fifo_in> <log> <sdcard_log> <cap_poll_log> <version_label> <fifo_out>
#   <dmesg_log> <logcat_log> > /dev/null 2>&1 < /dev/null &"`
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
# research/pulse-for-aya/README.md, 2026-07-30: cap_poll now also logs cpu_temp_mc/gpu_temp_mc (raw
# millidegree thermal-zone reads, zones resolved once at startup the same way SystemTuning.kt's
# resolveZones() picks them) and fan_duty/fan_rpm -- added for a multi-day unattended soak test of the
# newly-ported Custom fan curve, so a low-noise, always-present time series exists independent of
# whether the app's own fanLog() happened to fire that tick (it only logs on a decision CHANGE).
#
# STATUS.md, 2026-07-28: both log files now open with a version line ($VERSION_LABEL, built from
# BuildConfig.VERSION_NAME/VERSION_CODE/BUILD_TIMESTAMP in PulseDaemon.kt) -- passed straight in as
# a launch argument, not sent over the FIFO afterwards, so there's no startup race with the reader
# not being ready yet. Added after a suspected regression turned out to need "is this actually the
# patched build" ruled out first -- now every pulled log answers that on its own.
#
# STATUS.md, 2026-07-28 (correlation gap): none of the above ever captured what the rest of the
# DEVICE was doing right before a crash -- xsu-capability-probe/FINDINGS.md already proved `dmesg`
# catches `xsud`'s own SIGSEGV/SIGABRT directly (kernel ring buffer survives a userspace crash), and
# a filtered `logcat` for just the known crash tags avoids the ring-buffer-overflow problem a full
# `logcat` has under xsu's own chatty protocol logging. Both added below: `dmesg -c` polled in the
# same loop as cap_poll (cheap, clears after each read so nothing duplicates), and one filtered
# `logcat` spawned ONCE as a fully detached process (same "survives xsud dying" property as this
# daemon itself) instead of polled. `cap_poll` also now reads `battery/online` -- the still-
# unidentified process seen writing that value right before a crash in the 2026-07-27 investigation
# (see STATUS_ARCHIVE.md) was never checked against a PULSE-side crash before.

FIFO_IN=$1
LOG=$2
SDCARD_LOG=$3
CAP_POLL_LOG=$4
VERSION_LABEL=$5
FIFO_OUT=$6
DMESG_LOG=$7
LOGCAT_LOG=$8

rm -f "$FIFO_IN"
mkfifo "$FIFO_IN"
chmod 666 "$FIFO_IN"
rm -f "$FIFO_OUT"
mkfifo "$FIFO_OUT"
chmod 666 "$FIFO_OUT"
echo "start $(date +%s) pid=$$" > "$LOG"
[ -n "$SDCARD_LOG" ] && echo "$(date '+%Y-%m-%d %H:%M:%S') === pulse_daemon session start === version=$VERSION_LABEL" > "$SDCARD_LOG"
[ -n "$CAP_POLL_LOG" ] && echo "$(date '+%Y-%m-%d %H:%M:%S') === cap_poll session start === version=$VERSION_LABEL" > "$CAP_POLL_LOG"

# Detached, filtered logcat -- only the tags known to matter for this crash's signature (AndroidRuntime's
# FATAL EXCEPTION, libc's Fatal signal, DEBUG's crash backtrace, ActivityManager/BatteryService errors).
# *:S silences everything else, so xsu's own chatty protocol logging can't overflow the ring buffer
# ahead of these. Backgrounded with redirected stdio (same "close immediately, keep running" pattern
# validated for this daemon itself) so it survives independently even if THIS script's process dies.
LOGCAT_PID=""
if [ -n "$LOGCAT_LOG" ]; then
  logcat -v threadtime -s AndroidRuntime:E libc:F DEBUG:F ActivityManager:E BatteryService:E '*:S' \
    > "$LOGCAT_LOG" 2>&1 < /dev/null &
  LOGCAT_PID=$!
fi

POLL_PID=""
if [ -n "$CAP_POLL_LOG" ]; then
  (
    CPU=/sys/devices/system/cpu/cpufreq
    GPU=/sys/class/kgsl/kgsl-3d0
    [ -d "$GPU" ] || GPU=/sys/devices/platform/soc@0/3d00000.gpu/kgsl/kgsl-3d0
    FAN_PWM=/sys/devices/platform/soc/soc:pwm-fan/hwmon/hwmon0/pwm1
    FAN_RPM=/sys/devices/platform/soc/soc:pwm-fan/fan_rpm_state
    # CPU/GPU thermal zones (2026-07-30, for a multi-day Custom-fan-curve soak-test log): resolved ONCE
    # here, same selection rule as the Kotlin side's SystemTuning.kt#resolveZones() -- first
    # /sys/class/thermal/thermal_zoneN whose `type` contains "cpu" / "gpu"|"kgsl"|"gfx" -- so a human
    # reviewing a pulled log doesn't have to cross-reference which zone number means what on this device.
    CPU_TZ=""
    GPU_TZ=""
    zi=0
    while [ $zi -le 90 ]; do
      tz="/sys/class/thermal/thermal_zone$zi/type"
      t=$(cat "$tz" 2>/dev/null)
      case "$t" in
        *cpu*|*CPU*) [ -z "$CPU_TZ" ] && CPU_TZ="/sys/class/thermal/thermal_zone$zi/temp" ;;
      esac
      case "$t" in
        *gpu*|*GPU*|*kgsl*|*KGSL*|*gfx*|*GFX*) [ -z "$GPU_TZ" ] && GPU_TZ="/sys/class/thermal/thermal_zone$zi/temp" ;;
      esac
      zi=$((zi + 1))
    done
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
      batt_online=$(cat /sys/class/power_supply/battery/online 2>/dev/null)
      # Raw sysfs millidegree values (same convention as every other raw field here) -- divide by 1000 for
      # °C. Empty if the zone wasn't found above (device/kernel variance), not a script error.
      cpu_temp=""
      [ -n "$CPU_TZ" ] && cpu_temp=$(cat "$CPU_TZ" 2>/dev/null)
      gpu_temp=""
      [ -n "$GPU_TZ" ] && gpu_temp=$(cat "$GPU_TZ" 2>/dev/null)
      fan_duty=$(cat "$FAN_PWM" 2>/dev/null)
      # fan_rpm_state reads back "Current RPM 2666", not a bare number -- take the last field so this stays
      # a plain space-delimited key=value line like every other field here (matches FanController.parseRpm's
      # equivalent Kotlin-side parse of the same format).
      fan_rpm=$(cat "$FAN_RPM" 2>/dev/null | awk '{print $NF}')
      echo "$(date '+%Y-%m-%d %H:%M:%S') p0_cur=$p0c p0_max=$p0m p2_cur=$p2c p2_max=$p2m p5_cur=$p5c p5_max=$p5m p7_cur=$p7c p7_max=$p7m gov=$gov gpu_cur=$gpu gpu_min_pwrlevel=$gpu_min gpu_max_pwrlevel=$gpu_max batt_online=$batt_online cpu_temp_mc=$cpu_temp gpu_temp_mc=$gpu_temp fan_duty=$fan_duty fan_rpm=$fan_rpm" >> "$CAP_POLL_LOG"
      if [ -n "$DMESG_LOG" ]; then
        dmesg -c >> "$DMESG_LOG" 2>/dev/null
      fi
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
      [ -n "$LOGCAT_PID" ] && kill "$LOGCAT_PID" 2>/dev/null
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
    "GETSETTING "*)
      # Same one-round-trip idea as READ, but for a `Settings.System` key instead of a sysfs path --
      # FanController.readMode()'s `settings get system fan_mode` is the first caller (STATUS.md,
      # 2026-07-28: found firing every ~1s from the live discrete-fan-mode arbiter tick, never migrated
      # off raw xsu when cap-writes/telemetry-reads were). Single key only, no `|`-batching like READ --
      # add more verbs here the same way if another Settings-key reader shows up later.
      key=${line#GETSETTING }
      settings get system "$key" > "$FIFO_OUT" 2>/dev/null
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
