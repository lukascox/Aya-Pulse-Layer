# pulse_lite

Minimal AutoTDP daemon for AYANEO Pocket S2 (Snapdragon G3 Gen 3).  
Runs on **stock firmware, no root, no Magisk** — uses a vendor-provided privileged execution channel in AYASpace.

---

## What it does

Adjusts CPU frequency caps and GPU power level dynamically based on GPU utilization, keeping the SoC cooler and quieter during light/medium workloads while releasing full performance when gaming demands it.

```
idle  (GPU < 20%) → CPU ~60% cap, GPU 500 MHz
medium(GPU 20–70%) → CPU ~60% cap, GPU 680 MHz
heavy (GPU > 70%) → CPU full,     GPU 680 MHz
```

Transitions use hysteresis: upgrades immediately, downgrades only past a lower threshold. Prevents the tier thrashing common with bursty GPU workloads (emulators, Switch games).

On exit (sentinel or signal) — restores all nodes to stock values and releases locks so AYASpace can take over cleanly.

---

## Hardware

| | |
|---|---|
| Device | AYANEO Pocket S2 |
| SoC | Snapdragon G3 Gen 3 |
| GPU | Adreno A33 |
| OS | Android 14 (stock, AYASpace firmware) |

---

## CPU topology

| Policy | CPUs | Cluster | ECO cap | STOCK cap |
|--------|------|---------|---------|-----------|
| policy0 | 0–1 | Cortex-A520 | 1344000 | 2265600 |
| policy2 | 2–4 | Cortex-A720 mid | 1708800 | 3148800 |
| policy5 | 5–6 | Cortex-A720 high | 1708800 | 2956800 |
| policy7 | 7 | Cortex-X4 prime | 1824000 | 3052800 |

All cap values are verified OPP entries. policy7 stock cap is 3052800 — the 3302400 turbo bin is intentionally skipped.

## GPU power levels

`/sys/class/kgsl/kgsl-3d0/max_pwrlevel` uses **inverted** indexing: higher index = lower frequency.

| Index | Frequency | Used for |
|-------|-----------|---------|
| 0 | 1050 MHz | stock / restore |
| 6 | 680 MHz | heavy + medium tier |
| 9 | 500 MHz | idle tier |

---

## How it runs without root

AYASpace (the AYANEO system app) includes a "Root Script" text field that executes arbitrary shell as `u:r:xsuds:s0` — a vendor SELinux domain with sysfs write rights. Externally inaccessible (no exported Android components), but usable interactively.

`setsid sh /sdcard/pulse_lite.sh &` from this runner reparents the daemon to `ppid=1` immediately, making it independent of AYASpace's lifecycle. The daemon survives AYASpace being force-stopped, minimized, or killed.

Since `uid=2000` (ADB shell) cannot signal `uid=0` processes, stopping uses a **sentinel file**: the daemon polls `/sdcard/pulse_lite.stop` every tick and exits cleanly if found. Creating that file requires no root.

---

## Deploy

```bash
adb push pulse_lite.sh /sdcard/pulse_lite.sh
```

---

## Usage

### Start
Open AYASpace → Performance → Root Script, paste and run:
```sh
chmod 755 /sdcard/pulse_lite.sh
setsid sh /sdcard/pulse_lite.sh >> /sdcard/pulse_lite.log 2>&1 &
```

### Stop
```bash
adb shell 'touch /sdcard/pulse_lite.stop'
# daemon exits within 2s, restores stock caps
```

Or via Root Script:
```sh
touch /sdcard/pulse_lite.stop; echo done
```

### Status
```bash
adb shell sh /sdcard/pulse_lite.sh status
```

Or via Root Script:
```sh
sh /sdcard/pulse_lite.sh status
```

### Monitor
```bash
watch -n 3 'adb shell cat /sdcard/pulse_lite.log | tail -10'
```

Sample log:
```
23:07:20 START: pid=12400 ppid=1
23:07:20 tier=idle   gpu=19% caps: p0=1344000 p2=1708800 p5=1708800 p7=1824000
23:08:22 tier=medium gpu=34% caps: p0=1344000 p2=1708800 p5=1708800 p7=1824000
23:08:52 tier=heavy  gpu=80% caps: p0=2265600 p2=3148800 p5=2956800 p7=3052800
23:09:48 tier=medium gpu=17% caps: p0=1344000 p2=1708800 p5=1708800 p7=1824000
```

---

## Tuning

All thresholds are constants at the top of the script:

```sh
HEAVY_UP=70      # GPU% to enter heavy tier
HEAVY_DOWN=45    # GPU% to leave heavy tier (hysteresis)
MEDIUM_UP=20     # GPU% to enter medium tier
MEDIUM_DOWN=10   # GPU% to leave medium tier (hysteresis)
INTERVAL=2       # seconds per tick
```

GPU pwrlevel table for reference:
`0=1050 1=1000 2=903 3=834 4=770 5=720 6=680 7=629 8=578 9=500 10=422 11=366 12=310 13=231 (MHz)`

---

## Persistence on reboot

There is no clean autostart mechanism on stock firmware — the vendor Root Script runner has no exported Android component reachable via `am`. After each reboot, paste the two start lines into Root Script manually (~10 seconds).

With Magisk: create `/data/adb/service.d/pulse_lite_boot.sh`:
```sh
#!/bin/sh
sleep 15
setsid sh /sdcard/pulse_lite.sh >> /sdcard/pulse_lite.log 2>&1 &
```

---

## Files

```
pulse_lite.sh   daemon (deploy to /sdcard/)
start.sh        Root Script paste — start daemon
stop.sh         Root Script paste — stop via sentinel
status.sh       Root Script paste — show running state + log tail
```

---

## What was explored and ruled out

| Approach | Verdict |
|----------|---------|
| PServerBinder (ClusterTune method) | No AYANEO namespace in service list — AYN-only |
| QTI perf HAL | Platform-signed + LSM-hardened, unreachable from shell |
| Direct sysfs write (uid=2000) | Permission denied — SELinux label blocks shell |
| `ytsu` binary via ADB | Not in PATH for uid=2000, presumably app-private |
| Exported Activity/Service in com.ayaneo.settings | None — all components exported=false except framework boilerplate |
| Settings provider (ayaneo/setting/switch_performance_mode) | Dead UI mirror — writes land but AYASpace ignores them |
| Fan control via pulse_lite | Redundant — AYASpace fan curve reacts to lower temps naturally |

---

## Reference

- [ClusterTune](https://github.com/AurelioB/ClusterTune) — binder approach, Retroid/AYN only
- [PULSE](https://github.com/keiretrogaming/pulse) — full AutoTDP reference, same concepts with FPS-based closed loop
