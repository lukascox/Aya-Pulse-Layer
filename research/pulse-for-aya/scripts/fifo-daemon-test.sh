#!/system/bin/sh
# Named-pipe (FIFO) feasibility test (2026-07-27) -- can the daemon pattern
# from daemon-persistence-test.sh use blocking pipes instead of polled files
# for near-zero-latency, zero-storage communication with the (future) Kotlin
# side? Placed under /data/local/tmp (real ext4/f2fs storage, root/shell-
# writable) rather than /sdcard, which is often a FUSE passthrough that
# historically doesn't support mkfifo reliably -- this sidesteps that
# question for the shell-side plumbing test. A separate check is still
# needed later for whether pulse-for-aya's own app process (a different
# SELinux domain/UID) can actually open files under /data/local/tmp.
#
# Two FIFOs, since a pipe is one-directional:
#   apl_fifo_in  -- test driver (standing in for Kotlin) writes a target
#                   frequency value; the daemon's main loop BLOCKS reading
#                   this (no polling, no sleep) and reacts the instant a
#                   line arrives.
#   apl_fifo_out -- the daemon writes a fake telemetry line back after each
#                   applied value, simulating what real telemetry push
#                   would look like.
# Sending the literal line "STOP" through apl_fifo_in ends the test and
# restores policy7's original value (same safe, self-restoring pattern as
# daemon-persistence-test.sh; reuses policy7 since it's already confirmed
# reliable in both directions via direct writes).

FIFO_IN=/data/local/tmp/apl_fifo_in
FIFO_OUT=/data/local/tmp/apl_fifo_out
LOG=/sdcard/apl_fifo_test.log
P7=/sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq
ORIG7=$(cat "$P7")

rm -f "$FIFO_IN" "$FIFO_OUT"
mkfifo "$FIFO_IN" "$FIFO_OUT"
chmod 666 "$FIFO_IN" "$FIFO_OUT"
# Hold our own read-write fd on the OUT fifo open for the daemon's whole
# life so our own writes into it never block waiting for a reader to show
# up (a reader can attach/detach freely without us caring).
exec 9<>"$FIFO_OUT"

echo "start $(date +%s.%N) orig7=$ORIG7 pid=$$" > "$LOG"

i=0
while true; do
  IFS= read -r line < "$FIFO_IN" || continue
  recvtime=$(date +%s.%N)
  if [ "$line" = "STOP" ]; then
    echo "stop $recvtime" >> "$LOG"
    break
  fi
  i=$((i + 1))
  echo "$i recv_at=$recvtime line=$line" >> "$LOG"
  chmod 666 "$P7"
  echo "$line" > "$P7"
  chmod 444 "$P7"
  rb=$(cat "$P7" 2>/dev/null || echo denied)
  applytime=$(date +%s.%N)
  echo "$i applied_at=$applytime readback=$rb" >> "$LOG"
  echo "tick=$i target=$line readback=$rb ts=$applytime" >&9
done

chmod 666 "$P7"
echo "$ORIG7" > "$P7"
chmod 444 "$P7"
exec 9>&-
rm -f "$FIFO_IN" "$FIFO_OUT"
echo "restored7=$(cat "$P7")" >> "$LOG"
