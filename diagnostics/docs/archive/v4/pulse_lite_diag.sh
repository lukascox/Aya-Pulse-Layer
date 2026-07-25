#!/bin/sh
# pulse_lite_diag.sh — sesja diagnostyczna PRZED napisaniem PoC
# Cel: zebrac WSZYSTKIE dane potrzebne do zaprojektowania FPS-driven AutoTDP.
# Prawie wylacznie read-only (jeden bezpieczny write-test na koniec, odwracalny).
#
# Uzycie:
#   adb push pulse_lite_diag.sh /sdcard/pulse_lite_diag.sh
#   adb shell xsu sh /sdcard/pulse_lite_diag.sh
#
# W trakcie dzialania (sekcja FPS SAMPLING), na osobnym terminalu odpalaj po kolei
# emulatory/gry ktore chcesz przetestowac - skrypt bedzie co 3s probkowal FPS
# aktywnego pakietu przez 90s (30 probek), wypisujac surowy output do logu.
#
# Wynik: /sdcard/pulse_lite_diag.log — caly output do przyniesienia do analizy.

LOG=/sdcard/pulse_lite_diag.log
: > "$LOG"

sec() { echo "" >> "$LOG"; echo "===== $1 =====" >> "$LOG"; }

sec "0. IDENTITY CHECK"
{
  echo "id: $(id 2>/dev/null)"
  echo "date: $(date 2>/dev/null)"
} >> "$LOG"

sec "1. DEVICE / SOC FINGERPRINT"
{
  getprop ro.product.model 2>/dev/null | sed 's/^/ro.product.model=/'
  getprop ro.product.name 2>/dev/null | sed 's/^/ro.product.name=/'
  getprop ro.product.device 2>/dev/null | sed 's/^/ro.product.device=/'
  getprop ro.build.flavor 2>/dev/null | sed 's/^/ro.build.flavor=/'
  getprop ro.soc.model 2>/dev/null | sed 's/^/ro.soc.model=/'
  getprop ro.soc.manufacturer 2>/dev/null | sed 's/^/ro.soc.manufacturer=/'
  getprop ro.hardware 2>/dev/null | sed 's/^/ro.hardware=/'
  getprop ro.hardware.chipname 2>/dev/null | sed 's/^/ro.hardware.chipname=/'
  getprop ro.board.platform 2>/dev/null | sed 's/^/ro.board.platform=/'
  getprop ro.build.version.release 2>/dev/null | sed 's/^/android.version=/'
  getprop ro.build.version.sdk 2>/dev/null | sed 's/^/android.sdk=/'
  echo "--- full getprop grep soc/hw/board ---"
  getprop 2>/dev/null | grep -iE 'soc|hardware|board|chip|gpu' 
} >> "$LOG" 2>&1

sec "2. CPU TOPOLOGY - all policies, full frequency tables"
for p in /sys/devices/system/cpu/cpufreq/policy*; do
  [ -d "$p" ] || continue
  name=$(basename "$p")
  {
    echo "--- $name ---"
    echo "affected_cpus: $(cat "$p/affected_cpus" 2>/dev/null)"
    echo "scaling_governor: $(cat "$p/scaling_governor" 2>/dev/null)"
    echo "scaling_available_governors: $(cat "$p/scaling_available_governors" 2>/dev/null)"
    echo "cpuinfo_min_freq: $(cat "$p/cpuinfo_min_freq" 2>/dev/null)"
    echo "cpuinfo_max_freq: $(cat "$p/cpuinfo_max_freq" 2>/dev/null)"
    echo "scaling_min_freq: $(cat "$p/scaling_min_freq" 2>/dev/null)"
    echo "scaling_max_freq: $(cat "$p/scaling_max_freq" 2>/dev/null)"
    echo "scaling_cur_freq: $(cat "$p/scaling_cur_freq" 2>/dev/null)"
    echo "scaling_available_frequencies: $(cat "$p/scaling_available_frequencies" 2>/dev/null)"
  } >> "$LOG" 2>&1
done

sec "3. GPU (kgsl) - full state"
{
  KGSL=/sys/class/kgsl/kgsl-3d0
  echo "max_pwrlevel: $(cat $KGSL/max_pwrlevel 2>/dev/null)"
  echo "min_pwrlevel: $(cat $KGSL/min_pwrlevel 2>/dev/null)"
  echo "num_pwrlevels: $(cat $KGSL/num_pwrlevels 2>/dev/null)"
  echo "gpuclk: $(cat $KGSL/gpuclk 2>/dev/null)"
  echo "gpubusy: $(cat $KGSL/gpubusy 2>/dev/null)"
  echo "gpubusypercentage: $(cat $KGSL/gpubusypercentage 2>/dev/null)"
  echo "devfreq/governor: $(cat $KGSL/devfreq/governor 2>/dev/null)"
  echo "devfreq/available_governors: $(cat $KGSL/devfreq/available_frequencies 2>/dev/null)"
  echo "--- pwrlevel table (index freq) if exposed ---"
  cat $KGSL/pwrscale/gpu_available_frequencies 2>/dev/null
  cat $KGSL/pwrscale/trace 2>/dev/null | head -20
} >> "$LOG" 2>&1

sec "4. THERMAL ZONES - full list + baseline temps"
{
  for z in /sys/class/thermal/thermal_zone*; do
    [ -d "$z" ] || continue
    t=$(cat "$z/type" 2>/dev/null)
    v=$(cat "$z/temp" 2>/dev/null)
    echo "$(basename "$z"): type=$t temp=$v"
  done
} >> "$LOG" 2>&1

sec "5. INSTALLED EMULATOR/GAMING PACKAGES"
{
  pm list packages 2>/dev/null | grep -iE 'retro|emul|dolphin|citra|ppsspp|yuzu|ryujinx|duckstation|flycast|aetherx|winlator|exagear|mupen|drastic|redream|vita3k'
} >> "$LOG" 2>&1

sec "6. SURFACEFLINGER WINDOW LIST (fallback FPS method reference)"
{
  dumpsys SurfaceFlinger --list 2>/dev/null
} >> "$LOG" 2>&1

sec "7. FPS SAMPLING - manual per-app test window"
{
  echo "Instrukcja: odpal teraz gre/emulator na urzadzeniu. Skrypt bedzie"
  echo "probkowal FPS aktywnego pakietu co 3s, 30 probek (90s). Zmien gre"
  echo "i uruchom ten skrypt ponownie dla kazdej gry / kazdego emulatora."
} >> "$LOG"

i=0
while [ "$i" -lt 30 ]; do
  PKG=$(dumpsys activity 2>/dev/null | grep -m1 'mResumedActivity' | sed -n 's/.* \([a-zA-Z0-9_.]*\)\/.*/\1/p')
  {
    echo "--- sample $i pkg=$PKG $(date +%H:%M:%S) ---"
    if [ -n "$PKG" ]; then
      dumpsys gfxinfo "$PKG" framestats 2>/dev/null | head -40
    else
      echo "no resumed activity detected"
    fi
    echo "gpu_busy: $(cat /sys/class/kgsl/kgsl-3d0/gpubusypercentage 2>/dev/null)"
  } >> "$LOG" 2>&1
  i=$((i + 1))
  sleep 3
done

sec "8. SAFE WRITE TEST - CPU (odwracalny, restore natychmiastowy)"
{
  P0=/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
  ORIG=$(cat "$P0" 2>/dev/null)
  echo "policy0 original scaling_max_freq: $ORIG"
  chmod 644 "$P0" 2>/dev/null
  TEST_VAL=1344000
  echo "$TEST_VAL" > "$P0" 2>/dev/null
  sleep 1
  AFTER=$(cat "$P0" 2>/dev/null)
  echo "after writing $TEST_VAL, scaling_max_freq reads: $AFTER"
  if [ -n "$ORIG" ]; then
    echo "$ORIG" > "$P0" 2>/dev/null
    RESTORED=$(cat "$P0" 2>/dev/null)
    echo "restored to: $RESTORED"
  fi
  chmod 444 "$P0" 2>/dev/null
} >> "$LOG" 2>&1

sec "9. SAFE WRITE TEST - GPU (odwracalny, restore natychmiastowy)"
{
  GC=/sys/class/kgsl/kgsl-3d0/max_pwrlevel
  ORIG=$(cat "$GC" 2>/dev/null)
  echo "gpu original max_pwrlevel: $ORIG"
  chmod 644 "$GC" 2>/dev/null
  echo "9" > "$GC" 2>/dev/null
  sleep 1
  AFTER=$(cat "$GC" 2>/dev/null)
  GPUCLK_AFTER=$(cat /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null)
  echo "after writing 9, max_pwrlevel reads: $AFTER gpuclk=$GPUCLK_AFTER"
  if [ -n "$ORIG" ]; then
    echo "$ORIG" > "$GC" 2>/dev/null
    RESTORED=$(cat "$GC" 2>/dev/null)
    echo "restored to: $RESTORED"
  fi
  chmod 444 "$GC" 2>/dev/null
} >> "$LOG" 2>&1

sec "DONE"
echo "Diagnostyka zakonczona. Log: $LOG" >> "$LOG"
echo "Przynies caly plik $LOG do analizy."
