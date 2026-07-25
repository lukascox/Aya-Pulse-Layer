#!/usr/bin/env sh

set -eu

adb_args=""

while [ "$#" -gt 0 ]; do
    case "$1" in
        --)
            shift
            break
            ;;
        *)
            adb_args="$adb_args $1"
            shift
            ;;
    esac
done

# shellcheck disable=SC2086
adb $adb_args shell 'su -c '"'"'
for policy in /sys/devices/system/cpu/cpufreq/policy*
do
    [ -d "$policy" ] || continue

    printf "=== %s ===\n" "$(basename "$policy")"

    printf "related_cpus: "
    cat "$policy/related_cpus" 2>/dev/null || printf "unavailable\n"

    printf "available_frequencies: "
    cat "$policy/scaling_available_frequencies" 2>/dev/null || printf "unavailable\n"

    printf "available_max: "
    awk "{print \$NF}" "$policy/scaling_available_frequencies" 2>/dev/null || printf "unavailable\n"

    printf "scaling_max_freq: "
    cat "$policy/scaling_max_freq" 2>/dev/null || printf "unavailable\n"

    printf "cpuinfo_max_freq: "
    cat "$policy/cpuinfo_max_freq" 2>/dev/null || printf "unavailable\n"

    printf "time_in_state_max: "
    awk "NF {max=\$1} END {if (max) print max; else print \"unavailable\"}" \
        "$policy/stats/time_in_state" 2>/dev/null || printf "unavailable\n"

    printf "\n"
done
'"'"''
