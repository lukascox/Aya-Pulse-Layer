# FINDINGS — AYA GameWindow (`com.ayaneo.gamewindow`) teardown, pass 1

Target: `com.ayaneo.gamewindow` v1.5.84 (versionCode 204),
`gamewindow_QCOMRelease` build variant, pulled from the same AYANEO Pocket
FIT (Snapdragon) as the `ayaspace-teardown/` pass. Extracted the same way
(`pm path` + `adb pull` + `jadx`) — see `research/ayaspace-teardown/WORKLOG.md`
for the methodology this repeats; `com.ayaneo.gamewindow` was already flagged
there as the clear next target, since `com.ayaneo.settings`
(`research/ayaspace-teardown/FINDINGS.md`) turned out to only be a thin AIDL
remote control for this app, not the component that actually touches sysfs.

This directly resolves the "Open question / next step" left in
`research/ayaspace-teardown/FINDINGS.md`. Both things flagged there are now
answered.

## TL;DR — this is the headline finding of the whole two-app teardown

**`AyaAidlService` is `exported="true"` with no `android:permission`, and its
code does zero caller-identity verification.** Any app installed on the
device — no `system` UID, no signature permission, no root — can bind to it
and drive the exact same performance-mode / fan / RGB / controller commands
AYA Settings does. This is a real, currently-untaken option for `apl`:
**bind directly to AYA's own privileged service and skip `xsu` entirely**
for everything this AIDL surface covers.

## 1. The exported-service confirmation

`resources/AndroidManifest.xml` (`evidence/AndroidManifest_excerpt.xml`):
```xml
<service
    android:name="com.ayaneo.gamewindow.utils.aidl.AyaAidlService"
    android:exported="true"/>
```
No `android:permission=`. Compare to the two `<provider>` entries in the
same manifest area, which *are* exported but for a completely different,
narrower purpose (shared prefs sync) — the AIDL service specifically has no
gate at all.

**Code-level confirmation there's no hidden check**: `onBind()`
(`evidence/aidl/AyaAidlService.java`) just inspects `intent.getAction()` to
decide which of two binders to hand back (a plain `LocalBinder` for
same-process access under action `"com.ayaneo.aidl.server"`, or the real
`AyaAidlInterface.Stub` implementation otherwise) — no permission check, no
UID check. The receive path,
`AyaAidlService$mAidlBinder$1.i0(String)`
(`evidence/aidl/AyaAidlService$mAidlBinder$1.java`), just splits the
incoming string on `:` into `clientId:tag:msg` and dispatches — again, no
`Binder.getCallingUid()`, no `getCallingPackage()`, nothing. Whoever sends
the message is trusted implicitly.

**What `apl` would need to do to use this directly**, mirroring what
`com.ayaneo.settings` does (`research/ayaspace-teardown/evidence/aidl/`):
```kotlin
val intent = Intent()
intent.setClassName("com.ayaneo.gamewindow", "com.ayaneo.gamewindow.utils.aidl.AyaAidlService")
context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
// in connection.onServiceConnected:
val aidl = AyaAidlInterface.Stub.asInterface(binder)
aidl.send("apl-client-1:msg_type_performance:com_set_performance_mode:3")  // -> Gaming
```
No `xsu`, no root, no ~100ms/call floor, no per-call process spawn — just a
regular bound-service call. This is strictly better than the current
`xsu -c` model for anything this command surface covers (see the command
catalog in `research/ayaspace-teardown/FINDINGS.md`, section "The AIDL
mechanism, in full" — `com_set_performance_mode`, `_fan`, `_cpu`,
`_scheduler`, `_gpu`, plus RGB/controller commands).

Two caveats, both worth a quick empirical check before relying on this in
`apl`, not yet confirmed by static analysis alone:
- Whether `bindService` from a genuinely separate, non-AYA-signed app
  succeeds at the OS level the same way it does for `com.ayaneo.settings`
  (which is itself `sharedUserId=system` — its bind isn't a fully
  representative test of a third-party app's bind). `exported="true"` with
  no permission *should* mean yes, but this is worth confirming by literally
  trying it from a throwaway `apl` build.
- Longevity/stability of this interface across AYA firmware updates — it's
  clearly an internal, undocumented mechanism (note the class is even
  inconsistently named `AyaAidlManager`/`AYAAidlManager`, `AidlConstants`
  duplicated near-identically in both apps), not a published API. Fine for
  `apl`'s own use, but not something to treat as a stable contract.

## 2. What actually happens on `com_set_performance_mode:<N>`

Confirmed the full chain from AIDL receive to hardware writes:

```
AyaAidlService$mAidlBinder$1.i0()          -- parses "clientId:tag:msg"
  -> AYAAidlManager.dealMsg (not fully decompiled, JADX SSA-var error --
     the tag/command switch itself; behavior below reconstructed from the
     methods it calls into, which flow through the (decompiled) modules
     below)
  -> PerformanceManager.a()/f()             -- evidence/performance/PerformanceManager.java
       iAyaDevices.t(fanMode, force)        -- fan: device-specific, see part 3
       AyaDevicesUtil.b(cpuSchedulerMode)   -- governor
       AyaDevicesUtil.a(cpuFrequencies,...) -- per-core max freq
       AyaDevicesUtil.c(gpuFrequency)       -- GPU freq
```

**CPU governor** (`AyaDevicesUtil$applyCPUSchedulerMode$1`, Snapdragon
branch — confirmed this is the branch that applies to our Pocket FIT, it's
the fallback `else` branch not gated by any of the vendor-specific flags
used for AR04/MTK/etc.):
```
echo <governor> > /sys/devices/system/cpu/cpufreq/policy0/scaling_governor
echo <governor> > /sys/devices/system/cpu/cpufreq/policy7/scaling_governor
echo <governor> > /sys/devices/system/cpu/cpufreq/policy<N>/scaling_governor   # one more, N picked by device sub-flags
```
where `<governor>` = `"powersave"` (POWER_SAVING), `"schedutil"` or
`"walt"` (BALANCED — `walt` picked for one specific device flag combo, not
ours), `"performance"` (HIGH_PERFORMANCE). Plus per-core
`scaling_min_freq` writes for all 8 cores. This is exactly the CPU
governor/freq mechanism `apl` already knew about
(`apl-diag/docs/HARDWARE_PROFILE.md`) — good confirmation, no new lever
here.

**Per-core max frequency** (`AyaDevicesUtil$applyCPUFrequencies$1`,
Snapdragon fallback branch):
```
echo <selectedFrequency> > /sys/devices/system/cpu/cpu<cpuId>/cpufreq/scaling_max_freq
```
Also already known.

**GPU frequency** (`AyaDevicesUtil$applyGPUFrequency$1`, the
`AyaDevicesUtilKt.r || AyaDevicesUtilKt.t` branch — Adreno/`kgsl`, i.e. the
Snapdragon path):
```
echo <idle_timer>     > /sys/class/kgsl/kgsl-3d0/idle_timer
echo <maxFrequency>   > /sys/class/kgsl/kgsl-3d0/max_gpuclk
echo <maxFrequency>   > /sys/class/kgsl/kgsl-3d0/devfreq/max_freq
echo <min-or-max>     > /sys/class/kgsl/kgsl-3d0/devfreq/min_freq   # min if not fixed, max if fixed
```
`idle_timer` gets set very low (effectively disabling GPU idle downclocking)
when the frequency is fixed, and a normal ~80ms value otherwise. Also
matches `apl`'s existing GPU pwrlevel understanding — confirms it's
complete, no additional GPU node being missed.

## 3. Fan mode is *not* a plain sysfs write — likely serial/EC, not confirmed

This is the one piece of question 1 (from `ayaspace-teardown/README.md`)
that's still genuinely open. Unlike CPU/GPU, grepping the entire
`gamewindow` source tree for fan-adjacent sysfs writes (`pwm`,
`fan_speed`, `cooling_device`, `echo ... fan`) outside of device-specific
`com/ayaneo/devices/*` implementation classes turns up **nothing** — no
generic `echo <val> > /sys/.../pwm*` pattern the way CPU/GPU have one. Fan
control goes through `iAyaDevices.t(fanMode, force)`, an abstract method on
the `IAyaDevices` interface implemented per-device (same pattern as
`com.ayaneo.settings`'s `AR01`..`AR16`/`BW01`/`BW02`/`KR02` device classes
from the other teardown). This app also bundles
`IAyaDeviceSerial`/`com.ayaneo.gamewindow.utils.newserial.*` packages,
suggesting fan (and possibly RGB/controller) control on at least some
devices goes over a **serial link to an embedded controller**, not a sysfs
node — consistent with `com.ayaneo.settings`'s own `com.vi.vioserial`
package spotted in the first teardown pass.

**Not confirmed which mechanism applies to this specific Pocket FIT unit**
— would need to identify the exact `IAyaDevices` implementation class this
device resolves to (same open item noted in
`research/ayaspace-teardown/FINDINGS.md`) and read its `t()` override. Left
for a future pass if `apl` ends up needing fan control specifically via raw
sysfs; if `apl` instead goes the AIDL route (part 1 above), this doesn't
need to be solved at all — `com_set_performance_fan:<mode>` over AIDL
reaches the same code path regardless of whether the underlying mechanism
is sysfs or serial.

## Implications for `apl`

- **The AIDL-bind approach (part 1) is now the strongest lead in the whole
  two-app teardown.** It would let `apl` set performance mode *and* fan
  mode *and* GPU-fixed-frequency *and* RGB/controller settings through one
  mechanism, without needing xsu, without needing to solve the fan/serial
  question in part 3, and without the confirmed ~100ms/call `xsu` floor.
  Recommend a quick, cheap spike: try binding to `AyaAidlService` from a
  throwaway non-system `apl` build and see if `send()` actually works
  end-to-end (mode visibly changes) before committing design decisions
  around it.
- CPU/GPU sysfs mechanics are now fully confirmed at the command level and
  match what `apl` already does — no changes needed there regardless of
  which path (AIDL vs. own `xsu` sysfs writes) `apl` ultimately uses.
- Fan control remains the one lever `apl` cannot currently replicate via
  its own sysfs writes with confidence (mechanism unconfirmed, possibly
  serial/EC-based) — another point in favor of the AIDL approach, which
  sidesteps needing to know the mechanism at all.
