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
adb $adb_args shell '
for prop in \
    ro.soc.model \
    ro.vendor.qti.soc_model \
    ro.fota.platform \
    ro.hardware \
    ro.product.board
do
    value="$(getprop "$prop" 2>/dev/null | tr -d "\r")"
    value="$(printf "%s" "$value" | sed "s/^[[:space:]]*//;s/[[:space:]]*$//")"
    if [ -n "$value" ]; then
        printf "%s\n" "$value"
        exit 0
    fi
done

exit 1
'
