# pulse_lite — Handoff Substrate
## Session: AYANEO Pocket S2 AutoTDP Research → Implementation
**Date:** 2026-07-04  
**Status:** Working daemon, v2.2, deployed and validated on device

---

## Context

Started as a meta-question: can Android CPU/GPU tuning techniques (ClusterTune, PULSE) be ported to closed Android TV firmware for power reduction in Samba's own TV apps? Pivoted to AYANEO Pocket S2 (personal device) as a live research target — same recon methodology, lower stakes.

---

## Decisions Made

- **No Magisk** — daemon achieves ppid=1 via `setsid` + vendor Root Script runner; Magisk would only add autostart, not new capabilities. Deferred until actual pain.
- **GPU busy% as AutoTDP proxy** — simpler and sufficient vs. SurfaceFlinger FPS reader (too complex for pure shell). GPU load correlates well with gaming demand on this SoC.
- **Hysteresis state machine** — upgrade immediately, downgrade only past lower threshold. Prevents tier thrashing on bursty GPU workloads (emulators, Switch games).
- **Sentinel file stop** (`/sdcard/pulse_lite.stop`) — `pkill` from uid=2000 cannot signal uid=0 process. `touch` on world-writable `/sdcard` works without root; daemon polls per tick.
- **Skip turbo bin** — policy7 (X4 prime) has OPP 3302400 (turbo) above the normal table top 3052800. STOCK cap = 3052800, not 3302400.
- **Skip fan control** — AYASpace has its own thermal→fan curve. Capping CPU/GPU reduces heat, AYASpace naturally reduces fan. No race condition to manage.
- **Four CPU policies, not two** — initial assumption (policy0 + policy7 only) was wrong. Device has policy0 (A520 cpu0-1), policy2 (A720m cpu2-4), policy5 (A720h cpu5-6), policy7 (X4 cpu7). policy2 and policy5 were running uncapped.

---

## Findings / Facts Established

### Device [confirmed]
- AYANEO Pocket S2, Snapdragon G3 Gen 3, Adreno A33, Android 14
- `ro.build.flavor=AYANEO_PocketS2-user` / `ro.product.name=AYANEO_Pocket_FIT`
- ADB over USB enabled, `adb shell id` → `uid=2000(shell) u:r:shell:s0`

### CPU topology [confirmed from device]
| Policy | CPUs | Cluster | Max freq | Boost OPPs |
|--------|------|---------|----------|------------|
| policy0 | 0–1 | Cortex-A520 | 2265600 | — |
| policy2 | 2–4 | Cortex-A720 mid | 3148800 | 2899200–3148800 |
| policy5 | 5–6 | Cortex-A720 high | 2956800 | 2899200–2956800 |
| policy7 | 7 | Cortex-X4 prime | 3302400 | 3302400 (skip) |

- `boost=1` in `/sys/devices/system/cpu/cpufreq/boost`
- Governor: `performance` (AYASpace Gaming Mode) — DVFS disabled, but `scaling_max_freq` still acts as hard ceiling

### GPU [confirmed from device]
- `/sys/class/kgsl/kgsl-3d0/max_pwrlevel` — index, **inverted**: higher = slower
- `num_pwrlevels=14`, index 0=1050MHz … index 13=231MHz
- To CAP: write higher index. Default/stock: `max_pwrlevel=0` (uncapped)
- `gpu_busy_percentage` — world-readable, no root needed

### Privilege escalation path [confirmed empirically]
- External channels (binder, socket, Settings provider, QTI perf HAL) — all closed [confirmed]
- `com.ayaneo.settings` AndroidManifest — zero exported components in AYANEO namespace [confirmed via jadx]
- Vendor provides in-UI Root Script runner executing as `u:r:xsuds:s0` (dedicated SELinux domain with sysfs write rights)
- Actual exec path: `YtRootShell.java` → `Runtime.exec("ytsu")` — vendor binary, not in PATH for uid=2000
- `setsid sh -c '...' &` from Root Script → process reparents to `ppid=1` **immediately**, before first tick [confirmed]
- uid=2000 shell **cannot** `pkill` uid=0 daemon — "Operation not permitted" [confirmed]
- Sentinel file pattern resolves this without root

### OPP caps used in pulse_lite [all verified in OPP tables]
| Policy | ECO cap | STOCK cap |
|--------|---------|-----------|
| policy0 | 1344000 | 2265600 |
| policy2 | 1708800 | 3148800 |
| policy5 | 1708800 | 2956800 |
| policy7 | 1824000 | 3052800 |

- policy0 ECO: 1363200 was initial value — not in OPP table, snaps to 1344000. Fixed to write 1344000 directly.

### GPU tier caps [confirmed]
| Tier | max_pwrlevel | Freq |
|------|-------------|------|
| heavy + medium | 6 | 680 MHz |
| idle | 9 | 500 MHz |

---

## Open Questions / Unresolved

- **AYASpace Custom fan curve** — not yet configured. Need thermal zone readings under gaming load (which zones AYASpace tracks, what temps look like with pulse_lite active). `adb shell 'for f in /sys/class/thermal/thermal_zone*/type; do z=${f%/type}; echo "$(cat $f): $(cat ${z}/temp)"; done'` is the starting point.
- **policy0 ECO effectiveness** — little cores barely register in gaming load (emulator Switch, RetroArch). Capping them is correct but the power saving is marginal. Not worth revisiting.
- **GPU `gpu_busy_percentage` format** — `tr -cd '0-9'` strips any format variant. No issue observed but not stress-tested across all game states.
- **Autostart on reboot** — deferred. Requires Magisk (`/data/adb/service.d/`). Manual Root Script paste is the current workflow (~10s after reboot).
- **`ytsu` location** — not found in PATH or standard locations. Presumed app-private or dead code for this model (ar04 legacy?). Not relevant — sentinel pattern makes it moot.

---

## Proposed Scope (for follow-on)

**Objective:** Tune AYASpace Custom fan curve for G3 Gen 3 with pulse_lite active.

**Definition of Done:**
- Identify which thermal zone(s) AYASpace Custom Mode tracks
- Collect gaming temp baseline with pulse_lite running (10+ min session)
- Define fan curve with verified knee points
- Document RPM→% mapping (calibrated: pwm=76→~2600RPM, pwm=200→~6035RPM)

---

## Candidate Relations

- ClusterTune: github.com/AurelioB/ClusterTune (binder approach, AYN/Retroid only)
- PULSE: github.com/keiretrogaming/pulse (full AutoTDP reference implementation)

---

## Raw Artifacts

### pulse_lite.sh ECO/STOCK constants (v2.2 final)
```sh
CPU0_ECO=1344000   CPU0_STOCK=2265600
CPU2_ECO=1708800   CPU2_STOCK=3148800
CPU5_ECO=1708800   CPU5_STOCK=2956800
CPU7_ECO=1824000   CPU7_STOCK=3052800
GPU_UNCAP=0  GPU_MED=6  GPU_ECO=9
HEAVY_UP=70  HEAVY_DOWN=45  MEDIUM_UP=20  MEDIUM_DOWN=10
INTERVAL=2
```

### Sentinel stop (no root needed)
```bash
adb shell 'touch /sdcard/pulse_lite.stop'
```

### Start (Root Script)
```sh
chmod 755 /sdcard/pulse_lite.sh
setsid sh /sdcard/pulse_lite.sh >> /sdcard/pulse_lite.log 2>&1 &
```

### Live log monitoring
```bash
watch -n 3 'adb shell cat /sdcard/pulse_lite.log | tail -10'
```

### Full policy snapshot
```bash
adb shell 'for p in /sys/devices/system/cpu/cpufreq/policy*/; do
  echo "$(basename $p): cpus=$(cat ${p}affected_cpus) cur=$(cat ${p}scaling_cur_freq) max=$(cat ${p}scaling_max_freq)"
done'
```
