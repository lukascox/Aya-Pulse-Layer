# HARDWARE_PROFILE.md — AYANEO Pocket FIT / Pocket S2 (consolidated, English, v6-updated)

Source: pulse_lite v2.2 -> v3.7 handoffs/scripts, plus v5/v6 diagnostic runs (7 full
logs: eco, balanced, gaming, max, streaming, retroarch_balanced, heavy_gaming). All
values below have been empirically verified on-device (not guessed) unless explicitly
marked as unverified/pending.

## Device identification

- ro.product.model = Pocket FIT
- ro.product.name = AYANEO_Pocket_FIT
- ro.product.device = PocketFIT
- ro.build.flavor = AYANEO_PocketS2-user
- ro.vendor.qti.soc_model = SG8350P (Qualcomm platform codename "pineapple")
- ro.board.platform = pineapple
- GPU reported as "Adreno (TM) 750" via persist.sys.fake.gpu (spoofed identifier;
  actual silicon is SG8350P-integrated Adreno, exact real model not confirmed from
  userspace)
- Android 14, SDK 34, AYASpace firmware layer

CORRECTION vs earlier draft: the previously-used marketing name "Snapdragon G3 Gen 3"
and GPU designation "A33" are NOT confirmed by getprop output. Confirmed values above
come directly from `getprop | grep -iE "soc|board|gpu"` on-device.

## CPU — 4 independent policies (clusters)

| Policy | Cores  | Cluster (inferred) | cpuinfo_max_freq | Notes |
|--------|--------|---------------------|-------------------|-------|
| policy0 | cpu0-1 | small/efficiency    | 2265600 Hz        | AYASpace never raises above 1248000 in any of the 5 modes observed |
| policy2 | cpu2-4 | mid                  | 3148800 Hz        | Full range used across modes |
| policy5 | cpu5-6 | mid-high             | 2956800 Hz        | Full range used across modes |
| policy7 | cpu7   | prime                | 3302400 Hz        | "Turbo bin" — see correction below |

### Confirmed scaling_max_freq per AYASpace mode (Hz), governor included

| AYASpace Mode | Governor    | policy0 max | policy2 max | policy5 max | policy7 max |
|---|---|---|---|---|---|
| Eco       | powersave   | 1248000 | 729600  | 729600  | 480000  |
| Balanced  | schedutil   | 1248000 | 3148800 | 2956800 | 3302400 |
| Streaming | performance | 1248000 | 2131200 | 2035200 | 2112000 |
| Gaming    | performance | 1248000 | 3148800 | 2956800 | 3302400 |
| Max       | performance | 1248000 | 3148800 | 2956800 | 3302400 |

KEY FINDING: Gaming and Max are functionally identical at the CPU governor/freq level
across all four policies. Whatever difference exists between them (if any) lives
outside CPU frequency caps — likely GPU power-level limits, thermal budget, or a power
draw ceiling not visible in these sysfs nodes.

CORRECTION vs earlier draft: policy7's 3302400 Hz "turbo bin" was previously assumed
to be "deliberately skipped as unstable/too hot." This is CONTRADICTED by observed
AYASpace behavior — three of five stock modes (Balanced, Gaming, Max) all set
policy7 scaling_max_freq to exactly 3302400 Hz. The vendor itself uses this bin in
its default profiles, including the non-extreme Balanced mode. Treat the earlier
"unstable, must skip" assumption as unverified/likely outdated until proven otherwise
with dedicated thermal/stability testing at that specific frequency.

Sysfs paths:
```
/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq
/sys/devices/system/cpu/cpufreq/policy2/scaling_max_freq
/sys/devices/system/cpu/cpufreq/policy5/scaling_max_freq
/sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq
/sys/devices/system/cpu/cpufreq/policyN/scaling_governor
/sys/devices/system/cpu/cpufreq/policyN/scaling_cur_freq
```

### Still missing: full available-frequency table per policy

Confirmed available frequency lists exist (captured in v5/v6 baseline section) but a
clean consolidated table (all OPP steps per policy, not just the 5 AYASpace-mode
snapshots) has not yet been extracted into this document. This is needed before
designing a fine-grained FPS-driven step-controller — currently we only have 5 discrete
operating points per cluster (one per AYASpace mode), not the full OPP granularity.

## GPU — Adreno (kgsl), 14 power-levels (index 0-13, inverse: higher index = lower freq)

Confirmed via kgsl devfreq/available_frequencies (Hz): 1050000000, 1000000000,
903000000, 834000000, 770000000, 720000000, 680000000, 629000000, 578000000,
500000000, 422000000, 366000000, 310000000, 231000000 (14 entries, index 0 = highest).

Observed gpuclk during active load (heavy_gaming run, Eden/Switch emulator): jumped
from idle 231000000 Hz baseline up to 422000000 Hz under real rendering load,
confirming devfreq governor (msm-adreno-tz) is actively scaling in response to demand.

Sysfs paths:
```
/sys/class/kgsl/kgsl-3d0/max_pwrlevel        <- write: cap (index)
/sys/class/kgsl/kgsl-3d0/min_pwrlevel
/sys/class/kgsl/kgsl-3d0/gpuclk              <- read: current freq, Hz
/sys/class/kgsl/kgsl-3d0/gpubusy             <- read: raw "busy_cycles total_cycles"
/sys/class/kgsl/kgsl-3d0/gpubusypercentage   <- read: marked broken on this kernel
/sys/class/kgsl/kgsl-3d0/devfreq/governor
/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies
```

### GPU busy% reliability — CORRECTION, neither available path is fully trustworthy

`gpubusypercentage` is marked "known broken on this kernel" in our own diagnostic
output. The raw `gpubusy` (busy_cycles/total_cycles) path has its OWN confirmed
problem: the cycle counters wrap/reset between reads on this kernel, producing garbage
percentages (observed values as extreme as -2718%). v6 adds a sanity check (any
computed % outside 0-100 is reported as "n/a, counter_reset" instead of the raw
number), but the underlying wraparound is not fixable from userspace. Any future
AutoTDP controller must NOT rely solely on either GPU busy% source for decision-making
— treat both as advisory/noisy signals, not ground truth.

Prior draft only exercised 4 of 14 available power-levels (0=uncapped, 6=high,
7/8=mid, 9=eco) — the remaining 9 levels (1,2,3,4,5,10,11,12,13) remain untested and
represent real headroom for a finer-grained step-controller.

## Thermal zones

Confirmed via full thermal_zone enumeration (72 zones total on this device, types
prefixed cpuss/cpu/gpuss/aoss/mdmss/battery/skin/etc — full list captured in v5/v6
baseline section 4). Relevant subset used for live monitoring:
- thermal_zone55 (skin-msm-therm)
- thermal_zone72 (battery)
- cpuss/cpu-N-N-N zones for per-core CPU temps
- gpuss-N zones for GPU temps

### Thermal thresholds (from prior design draft, values in millidegrees C)

| Signal | HOT (step down) | HOTTER (step down further) | COOL_HOT (recover) | COOL_HOTTER (recover) |
|--------|------------------|------------------------------|----------------------|--------------------------|
| CPU temp | 78000 (78.0 C) | 85000 (85.0 C) | 74000 (74.0 C) | 81000 (81.0 C) |
| GPU temp | 75000 (75.0 C) | 82000 (82.0 C) | 71000 (71.0 C) | 78000 (78.0 C) |

CORRECTION — MAJOR: earlier draft stated these thresholds "have never actually been
reached in testing." This is now FALSE. The heavy_gaming run (Eden emulator, Super
Mario Odyssey, AYASpace Gaming mode) recorded:
- cpu-1-2-1 = 90700 (90.7 C) — above HOTTER (85.0 C)
- cpu-1-2-0 = 88000 (88.0 C) — above HOTTER
- cpu-1-2-2 = 87200 (87.0 C) — above HOTTER
- cpu-2-1-0 / cpu-2-1-1 = 80600-81400 (80.6-81.4 C) — above HOT (78.0 C)
- skin-msm-therm = 50089 (50.1 C) — elevated real skin temperature under load

KEY FINDING: AYASpace's Gaming mode did NOT visibly throttle CPU frequency in this
90-second sampling window despite multiple cores exceeding the HOTTER threshold. This
means either (a) AYASpace has no active thermal protection at the OS-visible governor/
freq level for this profile, or (b) throttling occurs at a level/timescale not captured
by our 90s window (e.g. deeper firmware/PMIC-level power limiting). This is directly
relevant to Module 3 (fan curve) — do not assume the vendor is protecting the device
for you in Gaming mode; treat thermal safety as our own responsibility in the
controller design, not something to defer.

Thermal zone type detection should remain dynamic (match by "cpu"/"gpu" prefix in
/sys/class/thermal/thermal_zone*/type at controller startup), not hardcoded zone
numbers, since these may shift across firmware revisions.

## Root channel (confirmed, two historical variants)

**Historical (v2.2-v3.7, AYASpace Root Script):** no direct root from `adb shell`
(uid=2000). Channel: AYASpace -> "Performance -> Root Script" -> text field executed
as `u:r:xsud:s0` (dedicated SELinux domain with sysfs write permission). Requires
manual paste after every reboot — no autostart without Magisk.

**Current (confirmed):** `xsu` acts as a full, operational `su`-equivalent binary,
granting `uid=0` directly from `adb shell` without going through the AYASpace UI. All
7 diagnostic runs (v5, v6) confirm `uid=0(root)` in the IDENTITY section of every log,
using `adb shell xsu sh /sdcard/pulse_lite_diag.sh <suffix>`. Invocation pattern from
an Android app is presumed identical to `YtRootShell.java` from AYA Settings
(`Runtime.exec("xsu")`, stdin/stdout pipe) — see companion PulseFit README.

STILL UNVERIFIED: whether `xsu` is callable from inside a normal Android app via
`Runtime.exec()`, rather than only from an adb shell session. This remains the first
untested assumption blocking Module 1 of the PulseFit app — see
handoff_pulse_android_app.md.

## FPS measurement — CORRECTION, method fully replaced

Prior recommendation (`dumpsys gfxinfo <package> framestats`) is CONFIRMED UNRELIABLE
for emulator-class apps:
- RetroArch: gfxinfo returns "Total frames rendered: 0" across all samples despite
  active gameplay — root cause: RetroArch renders via a native SurfaceView invisible
  to this API.
- Eden (Switch emulator, Super Mario Odyssey): gfxinfo returned IDENTICAL, frozen
  frame stats (117 frames, same histogram) across 5 samples spanning 15 seconds of
  real gameplay — the API was returning a cached/stale snapshot from the Android UI
  overlay layer, not live game content. User's manual observation confirmed real FPS
  was ~60 or lower, not the reported 117-frame snapshot.

CONFIRMED WORKING METHOD (v6): dynamic three-step pipeline, works uniformly across UI
apps, RetroArch, and Eden without hardcoding any package name:
1. Detect foreground pkg/activity: `dumpsys window windows | grep mCurrentFocus` +
   `dumpsys activity activities | grep mResumedActivity`.
2. Match SurfaceFlinger layer: `dumpsys SurfaceFlinger --list`, prefer
   `SurfaceView[pkg]` (native game/emulator render surface) over generic
   `pkg/Activity` (Android UI chrome layer). Layer name suffix numbers are unstable
   across launches — match via fuzzy grep on package name substring, not exact string.
3. Compute FPS: `dumpsys SurfaceFlinger --latency "<layer>"` — first line is refresh
   period in ns, following lines are timestamp triplets; FPS derived from
   actualPresentTime deltas within the sampling window.

Known limitation: static/idle screens produce very few present events per window,
making FPS statistically unreliable at low frame counts — v6 flags this explicitly
as "low_sample_count" rather than reporting a misleadingly low FPS value. `gfxinfo`
remains usable only as a SECONDARY source of jank/percentile data for native Android
UI apps, never as the primary FPS signal for emulator workloads.

## Confirmed per-sample CPU/GPU telemetry (new in v6)

v6 captures CPU governor + scaling_cur_freq AND GPU freq/busy per sample (every ~3s
across a 90s window), not just once at script start. Confirmed behavior: Balanced mode
(schedutil) shows continuous, real-time frequency bouncing (e.g. policy2 observed
swinging between 960000-3148800 Hz across a handful of samples during RetroArch play),
consistent with expected schedutil behavior but not previously documented empirically
in this profile.
