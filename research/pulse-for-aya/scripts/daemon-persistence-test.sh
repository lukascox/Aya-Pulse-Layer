#!/system/bin/sh
# Extended background-daemon connection + group-control test (2026-07-27).
#
# Two things under test:
# 1) Connection-persistence check (same as the first run): a script
#    backgrounded via a SINGLE `xsu -c "... &"` call should keep applying
#    real sysfs writes with zero further xsu/xsud connections after launch.
#    First run confirmed zero NEW connections from the write loop itself,
#    but also showed the LAUNCHING connection stayed open for the whole
#    test instead of closing right away -- likely because the backgrounded
#    process inherited xsu's own stdout/stderr pipes. This run's launch
#    command should redirect all three standard streams explicitly (see
#    the procedure) to test whether that lets the connection close
#    immediately instead.
# 2) Group/shared-policy control: policy0 (cpu0+cpu1, the A520 efficiency
#    cluster per HARDWARE_PROFILE.md) alongside the already-proven lone-core
#    policy7. Writes go to the POLICY-level node (scaling_max_freq), the
#    same pattern GovernorController/AutoTuneController have always used --
#    NOT the AIDL com_set_performance_cpu path, which is where the earlier
#    cap-up-doesn't-take-effect bug was found (STATUS.md, 2026-07-27). This
#    checks whether that bug was specific to AIDL's own handling rather
#    than a hardware limit that would also affect direct sysfs writes.
#    Logs BOTH the policy-level and per-cpu-level readback for policy0/
#    cpu0/cpu1, since policy7/cpu7 were found to disagree on this kernel
#    (not a plain symlink pair) -- worth re-checking for a shared policy.
#
# 787200 reused as the test value for both policies (already confirmed
# valid for policy0 in an earlier AIDL round-trip test, and for policy7 in
# this script's first run).
#
# Self-restoring: always writes the ORIGINAL values back, both on normal
# completion and on early stop via the sentinel file.

LOG=/sdcard/apl_daemon_test.log
STOP=/sdcard/apl_daemon_test.stop

P7=/sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq
P0=/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
C0=/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq
C1=/sys/devices/system/cpu/cpu1/cpufreq/scaling_max_freq

ORIG7=$(cat "$P7")
ORIG0=$(cat "$P0")

rm -f "$STOP"
echo "start $(date +%s) orig7=$ORIG7 orig0=$ORIG0 pid=$$" > "$LOG"

i=0
while [ ! -f "$STOP" ]; do
  i=$((i + 1))
  if [ $((i % 2)) -eq 0 ]; then
    t7=$ORIG7
    t0=$ORIG0
  else
    t7=787200
    t0=787200
  fi

  chmod 666 "$P7"
  echo "$t7" > "$P7"
  chmod 444 "$P7"

  chmod 666 "$P0"
  echo "$t0" > "$P0"
  chmod 444 "$P0"

  rb7=$(cat "$P7" 2>/dev/null || echo denied)
  rb_p0=$(cat "$P0" 2>/dev/null || echo denied)
  rb_c0=$(cat "$C0" 2>/dev/null || echo denied)
  rb_c1=$(cat "$C1" 2>/dev/null || echo denied)
  echo "$i $(date +%s) t7=$t7 rb7=$rb7 t0=$t0 rb_p0=$rb_p0 rb_c0=$rb_c0 rb_c1=$rb_c1" >> "$LOG"
  sleep 2
done

chmod 666 "$P7"; echo "$ORIG7" > "$P7"; chmod 444 "$P7"
chmod 666 "$P0"; echo "$ORIG0" > "$P0"; chmod 444 "$P0"
echo "stop $(date +%s) restored7=$(cat "$P7") restored_p0=$(cat "$P0") restored_c0=$(cat "$C0") restored_c1=$(cat "$C1")" >> "$LOG"
