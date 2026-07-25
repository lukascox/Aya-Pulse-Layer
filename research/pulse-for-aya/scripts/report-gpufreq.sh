#!/usr/bin/env sh
# Reports the Adreno GPU devfreq nodes PULSE reads/writes. Run with a rooted adb
# shell (or via the device's PServer) to confirm detection on Odin 3 / Thor / RP6.
set -eu
adb_args=""
while [ "$#" -gt 0 ]; do
    case "$1" in
        --) shift; break ;;
        *) adb_args="$adb_args $1"; shift ;;
    esac
done

# shellcheck disable=SC2086
adb $adb_args shell 'su -c '"'"'
for root in /sys/class/kgsl/kgsl-3d0/devfreq /sys/devices/platform/soc@0/3d00000.gpu/devfreq/3d00000.gpu
do
    [ -d "$root" ] || continue
    printf "=== %s ===\n" "$root"
    printf "max_freq (Hz):           "; cat "$root/max_freq" 2>/dev/null || printf "unavailable\n"
    printf "min_freq (Hz):           "; cat "$root/min_freq" 2>/dev/null || printf "unavailable\n"
    printf "cur_freq (Hz):           "; cat "$root/cur_freq" 2>/dev/null || printf "unavailable\n"
    printf "governor:                "; cat "$root/governor" 2>/dev/null || printf "unavailable\n"
    printf "available_frequencies:   "; cat "$root/available_frequencies" 2>/dev/null || printf "unavailable\n"
    printf "\n"
    exit 0
done
printf "No Adreno devfreq node found.\n"
'"'"''
