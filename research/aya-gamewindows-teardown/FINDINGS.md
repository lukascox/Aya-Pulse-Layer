# FINDINGS — AYA GameWindow (`com.ayaneo.gamewindow`) teardown

Target: `com.ayaneo.gamewindow` v1.5.84 (versionCode 204),
`gamewindow_QCOMRelease` build variant, pulled from the same AYANEO Pocket
FIT (Snapdragon) as the `ayaspace-teardown/` pass. Extracted the same way
(`pm path` + `adb pull` + `jadx`) — see `research/ayaspace-teardown/WORKLOG.md`
for the methodology this repeats; `com.ayaneo.gamewindow` was already flagged
there as the clear next target, since `com.ayaneo.settings`
(`research/ayaspace-teardown/FINDINGS.md`) turned out to only be a thin AIDL
remote control for this app, not the component that actually touches sysfs.

Three passes so far: pass 1 (sections 1–3) resolved the "Open question / next
step" left in `research/ayaspace-teardown/FINDINGS.md` about performance-mode
control. Pass 2 (section 4) was prompted by a side observation in another
session about `apl`'s deferred key-binding feature and found a second,
independent unprotected component with its own major implication. Pass 3
(sections 5–6) was prompted by `research/pulse-for-aya`'s glue patch needing
real RGB/fan mechanisms for AYANEO — found a live, on-device-confirmed sysfs
PWM fan path that overturns pass 1's "probably serial" guess, plus a
standard-`Settings.System`-based RGB mechanism.

## TL;DR — two headline findings

**1. `AyaAidlService` is `exported="true"` with no `android:permission`, and its
code does zero caller-identity verification.** Any app installed on the
device — no `system` UID, no signature permission, no root — can bind to it
and drive the exact same performance-mode / fan / RGB / controller commands
AYA Settings does. This is a real, currently-untaken option for `apl`:
**bind directly to AYA's own privileged service and skip `xsu` entirely**
for everything this AIDL surface covers.

**2. A second, independent unprotected component — `SharedPrefsProvider`
(section 4) — could let `apl` implement arbitrary keyboard-key remapping
for pad buttons (its long-deferred "Module 2") without writing an input
remapper at all.** Same pattern: `exported="true"`, no permission, no
caller check, and it drives a macro engine that can send *any* Android
keycode (Escape, Enter, letters, key combos) via `input keyevent`/
`input keycombination` when a physical button fires — reachable via a
plain `ContentResolver` call, no service bind needed.

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

## 4. Bonus: arbitrary-key remapping (Module 2) may be reachable the same way

Prompted by a side observation in another session: the AIDL command catalog
(`research/ayaspace-teardown/FINDINGS.md`, "command catalog") includes
`com_set_abxy_mode`, `com_set_l1l2r1r2_mode`, `com_set_direction_dpad_mode`,
`com_set_single_key_mapping` — raising the question of whether `apl`'s
long-deferred key-binding feature ("Module 2") could ride on this same
mechanism, and specifically whether an arbitrary keyboard key (Escape,
Enter, any letter) could be bound to a pad button, not just another
gamepad function.

**Short answer: yes for the extra/back buttons, unconfirmed but plausible
for the main face/shoulder buttons — and the write path is even simpler
than the AIDL service, no binding required at all.**

### 4a. What `com_set_single_key_mapping` actually is — a dead end

Traced where AYA Settings actually sends it
(`com/ayaneo/settings/ui/controller/LcRcSetKeysFragment.java`, the "LC/RC"
back-paddle settings screen): the message format is
`"com_set_single_key_mapping:<CustomButtonIndex>|<JoystickFunctionType>|<bool>"`.
`JoystickFunctionType` (`evidence/customkey/JoystickFunctionType.java`) is a
closed enum of **gamepad functions only** — `A,B,X,Y,SELECT,START,DPAD_*,
LB,RB,LEFT_THUMB,RIGHT_THUMB,LT,RT,LEFTSTICK_*,RIGHTSTICK_*` — no keyboard
keycodes exist in this enum. `CustomButtonIndex`
(`evidence/customkey/CustomButtonIndex.java`) only names the 4 back paddles
(`LC_SHOULDER, RC_SHOULDER, LC_BACK, RC_BACK`). So this specific AIDL
command is gamepad-function-to-gamepad-function remapping only — not what
we're after. Dead end, but worth ruling out explicitly.

### 4b. The real mechanism: `CustomKeyDispatch` / `CustomKeyFunExecutor`

A completely separate subsystem, `com.ayaneo.gamewindow.custom.*`, is a
full macro engine: "when physical button with raw Android keycode X fires,
run function Y." The trigger side (`KeyInfo.value`,
`evidence/customkey/KeyInfo.java`) is a raw `KeyEvent.getKeyCode()` value —
not a closed enum. The action side (`FunInfo.pCode`/`funCode`,
`evidence/customkey/FunInfo.java`) has ~11 categories (`pCode` 0–10:
open-app, controller/performance shortcuts, **input**, nav, media, display,
screen, content, **keyboard**, connectivity...). Two categories are exactly
what we want, confirmed in `evidence/customkey/CustomKeyFunExecutor.java`:

- **`pCode=2` ("input"), `funCode=1`** ("input_inputKeyCode"):
  `CmdUtilKt.e("input keyevent " + executePar[0].value)` — sends **any**
  string as an Android `input keyevent` argument. Accepts both numeric
  keycodes and `KEYCODE_*` names — `KEYCODE_ESCAPE`, `KEYCODE_ENTER`,
  `KEYCODE_A`..`KEYCODE_Z`, anything `input keyevent` understands.
- **`pCode=9` ("keyb"), `funCode=4`** ("keyb_inputSpecifyKey"): takes a
  comma-separated list in `executePar[0].value` and fires `input keyevent`
  for each one in sequence — a genuine multi-key macro.
- **`pCode=9`, `funCode=2`** is a hard-coded example
  (`"input keycombination -t 500 KEYCODE_CTRL_LEFT KEYCODE_A"`) proving
  `input keycombination` (real modifier+key combos, e.g. Ctrl+A) is also
  reachable through this engine, not just single keypresses.

Both `input keyevent` and `input keycombination` are plain shell commands
(`CmdUtilKt.e/f` → `Runtime.exec`/`ProcessBuilder`, same pattern as
`research/ayaspace-teardown/FINDINGS.md` section on `RootShell`) — they
work here because `com.ayaneo.gamewindow` is `sharedUserId=system` and
already has `android.permission.INJECT_EVENTS`-equivalent privilege, same
root cause as everywhere else in this teardown.

### 4c. How to set it — an exported `ContentProvider`, no binding needed

The full `List<CustomKeyItem>` config is read and written through
`SharedPrefsProvider` (`evidence/customkey/SharedPrefsProvider.java`),
declared in the manifest as (`evidence/AndroidManifest_excerpt.xml`):
```xml
<provider
    android:name="com.ayaneo.provider.SharedPrefsProvider"
    android:exported="true"
    android:authorities="com.ayaneo.gamewindow.provider.sharedprefs"/>
```
No `android:permission`, and — same story as `AyaAidlService` — the
`query()`/`update()` implementations do **zero caller-identity checks**.
Concretely:
- `query(content://com.ayaneo.gamewindow.provider.sharedprefs/shared_prefs)`
  → single-row cursor, column `value`, containing the current config as a
  JSON array of `CustomKeyItem`.
- `update(...)` with `ContentValues{"value": "<new JSON array>"}` →
  persists it to `SharedPreferences` **and immediately calls
  `CustomKeyDispatch.c(json)`**, which re-parses and re-arms every key
  detector on the spot. No service bind, no AIDL message framing — a plain
  `ContentResolver.query()`/`update()` call from any app is enough.

**Important safety note, not just a style preference**: `update()`
replaces the *entire* array, not one item. `CustomKeyDispatch.c()` clears
all existing detectors (`GlobalKeyInterceptKt.f4920d.clear()`) and rebuilds
them from whatever you write. **Always `query()` first, parse the existing
array, append/modify the relevant `CustomKeyItem`, and write the full
array back** — a naive blind `update()` would silently delete the user's
existing LC/RC/paddle bindings.

### 4d. Example payload (for manual, read-only-first testing — not yet tried)

Read the current config first (safe, no side effects):
```bash
adb shell content query --uri content://com.ayaneo.gamewindow.provider.sharedprefs/shared_prefs
```
This returns the live JSON array, including the real `KeyInfo.value`
keycodes this specific device uses for its LC/RC/etc. physical buttons —
needed before constructing a real write, since those trigger keycodes are
device-specific and weren't pinned down from static analysis alone (same
open item as the fan mechanism in part 3).

Shape of one `CustomKeyItem` entry that binds a trigger key to send
`KEYCODE_ESCAPE` (values illustrative — `keyInfoList[0].value` must be
replaced with a real trigger keycode read from the query above, and `id`
should be a fresh unique value, e.g. current epoch millis):
```json
{
  "id": 1732400000000,
  "isShortClick": true,
  "isTogether": true,
  "keyInfoList": [
    { "keyName": "LC", "value": 0 }
  ],
  "funInfoList": [
    {
      "name": "",
      "pCode": 2,
      "funCode": 1,
      "executePar": [
        { "type": "keycode", "value": "KEYCODE_ESCAPE" }
      ]
    }
  ],
  "appWhite": [],
  "enable": true,
  "updateTime": 1732400000000
}
```
To apply: take the array from the `query` above, append (or replace) this
item, serialize back to a single JSON array, and:
```bash
adb shell content update --uri content://com.ayaneo.gamewindow.provider.sharedprefs/shared_prefs --bind value:s:'<full JSON array here>'
```
Not yet executed against a real device — this is the next concrete,
low-risk experiment (read-first, single-item change, easily reverted by
writing the original queried array back).

## 5. RGB control — sticks confirmed real and controllable, no fan-RGB found

Prompted by a question about whether this teardown surfaces anything useful
for `pulse-for-aya`'s glue patch (`research/pulse-glue-assessment/FINDINGS.md`
confirmed `pulse`'s own RGB — the `joystick_led_light_picker_color` Settings
key — is AYN/Retroid-specific and dead on AYANEO, though it already
self-gates off safely). This is the AYANEO-native replacement, if `apl` or a
`pulse` fork ever wants real RGB control on this hardware.

**`RgbManager`/`RgbUtil`** (`com.ayaneo.gamewindow.utils.rgb.*`,
`evidence/rgb/RgbManager.java`) is a real, working 8-mode controller:
Default, Breath Single, Breath RGB Cycle, Breath Google, Scan, Wave, Single
Color, Reactive/Follow-controller — each with its own persisted color
(`"R,G,B"` string) and brightness (0–100). Confirmed specifically tied to
**joystick** RGB via the `isDeviceHasJoystick`/`isRgbEnable` capability
flags in `IAyaDeviceHardware`/`IAyaDevices` — no equivalent flag or code
path found for fan/vent lighting anywhere in the module (see section 6 for
what *does* exist for fans — plain PWM speed, no lighting). The
`followModeFrontColor`/`followModeBackColor` pair (two colors for Reactive
mode) is most likely two slots in a gradient effect, not confirmed to be
distinct physical zones (e.g. "sticks vs. something else") — not resolved
either way from static analysis, and not important for the RGB-is-real
conclusion either way.

**The mechanism is not another exported component — it's plain
`Settings.System`.** `RgbManager`'s state is entirely backed by
`SystemProvider`/`AyaShareProvider` (`evidence/rgb/SystemProvider.java`,
`evidence/rgb/AyaShareProvider.java`), which wraps
`Settings.System.getString/putString/getInt/putInt(...)` under a fixed key
prefix `"ayaneo/share/<name>"` — e.g. `ayaneo/share/aya_rgb_mode.conf`,
`ayaneo/share/aya_rgb_single_mode_color.conf`. This is meaningfully
different from every other mechanism found in this whole teardown series:
no `bindService`, no `ContentResolver` authority to discover, just the
standard `android.permission.WRITE_SETTINGS` permission — an ordinary,
user-grantable permission (`Settings.ACTION_MANAGE_WRITE_SETTINGS`), not
root, not an unprotected-but-still-nonstandard component.

**Writes apply live, confirmed by code path**: `RgbManager.g()`
(`setRgbObserver()`) registers a `ContentObserver`
(`evidence/rgb/RgbManager$setRgbObserver$1.java`, via
`Settings.System.getUriFor("ayaneo/share")`, `notifyForDescendants=true`)
that re-applies the correct `RgbUtil` call the instant any
`ayaneo/share/aya_rgb_*` key changes — no separate "apply" signal needed,
same live-reload pattern as `SharedPrefsProvider` in section 4.

Full worked example (not yet tried from a real non-system app — see caveat
below):
```bash
adb shell settings put system ayaneo/share/aya_rgb_mode.conf 6
adb shell settings put system ayaneo/share/aya_rgb_single_mode_color.conf "255,0,0"
adb shell settings put system ayaneo/share/aya_rgb_single_mode_bright.conf 100
adb shell settings put system ayaneo/share/aya_rgb_is_open.conf true
```

**Caveat, unresolved**: `adb shell settings put` runs as the `shell` UID,
which historically gets broader `Settings.System` write access than a
genuine third-party app process does — Android's `SettingsProvider`
restricts *non-privileged* apps' `Settings.System` writes to a known-key
allowlist on some versions/OEMs, silently no-op'ing unrecognized custom
keys like `ayaneo/share/*`. Whether a real `apl` process (holding
`WRITE_SETTINGS`, not shell/root) can actually write these specific custom
keys is **not yet confirmed** — the `adb shell` route proves the plumbing
exists and works, not that a normal app can reach it. Worth testing from an
actual installed app before relying on this design.

## 6. Fan speed control — confirmed live and real, plain Linux `pwm-fan` hwmon, not serial

Section 3 (pass 1) speculated fan control was likely serial/EC-based,
reasoning from the presence of `IAyaDeviceSerial`/`newserial` packages and
the absence of any generic fan sysfs pattern in `gamewindow`'s top-level
`AyaDevicesUtil`. That speculation was **wrong** — or at least incomplete.
Reading two concrete per-device implementations,
`com/ayaneo/devices/ar03/AR03.java` and `com/ayaneo/devices/ar13/AR13.java`
(which extends it), turned up a plain, generic Linux **`pwm-fan` platform
driver / hwmon interface** — the same class of mechanism already confirmed
for CPU (`cpufreq`) and GPU (`kgsl`), not a proprietary protocol:

- **Read RPM** (`AR13.c1()`, `evidence/fan/AR13_fan_excerpt.java`):
  `cat /sys/devices/platform/soc/soc:pwm-fan/fan_rpm_state`
- **Read/write enable state**: `/sys/devices/platform/soc/soc:pwm-fan/fan_power_state`
  (`AR13.n1()` writes `echo 1` or `echo 0`)
- **Write duty** (`AR03.t1(int)`, 0–255 range,
  `evidence/fan/AR03_fan_excerpt.java`): resolves the actual PWM file via
  `AR03.r1()`, which probes `/sys/devices/platform/soc/soc:pwm-fan/hwmon/
  hwmon<N>/pwm1` for `N` in `0..8` and uses the first one that exists, then
  writes the value as plain file I/O (falling back to `echo ... > path`
  through the device's own root-shell helper only if the direct file write
  throws). `AR13.n1(percent)` (setFanSpeed as a 0–100 percent) converts via
  `(percent * 255) / 100` before calling `t1`.

**Confirmed live on our actual AYANEO Pocket FIT (2026-07-25)**, read-only,
exactly matching what the code above predicts:
```
$ adb shell su -c "cat .../soc:pwm-fan/fan_rpm_state"
Current RPM 2815
$ adb shell xsu -c "cat .../soc:pwm-fan/fan_power_state; cat .../hwmon/hwmon0/pwm1"
pwm_en=1
76
```
`hwmon1` through `hwmon8` confirmed **absent** (only `hwmon0` exists) —
matches `AR03.r1()`'s probe loop exactly. Notably, `hwmon0/pwm1` read
successfully even from a plain unprivileged shell (no `su`/`xsu` needed for
the *read* — confirmed by accident when a malformed test command ran the
`for` loop outside of `su` and it still returned `76`). Write access is
**not yet tested** — the natural next step, but a real one (changes actual
fan behavior on a live device, and may fight whatever AYANEO's own vendor
process does if it re-asserts the value), deferred to when it's
deliberately exercised under observation, not rushed.

**Why this matters for `pulse-for-aya`**: `pulse-glue-assessment/FINDINGS.md`
concluded fan control needed a *separate* project built on the AIDL bind
(`com.ayaneo.gamewindow`'s `AyaAidlService`, `com_set_performance_fan`),
since `pulse`'s own two mechanisms (`gpio5_pwm2`, `Settings.System
fan_mode`) are AYN-specific and dead here. This finding changes that:
**a plain, confirmed-live sysfs PWM path exists**, structurally identical
to how `pulse`'s own `FanCurveController.kt`/`CpuPolicyDetector.kt` already
talk to CPU/GPU sysfs — meaning fan control could plug into
`pulse-for-aya`'s already-stubbed `FanController.kt` the *same way* as
CPU/GPU, through the existing `xsu`-glued `RootExec`, with **no AIDL work
needed at all**. Not a certainty until the write side is confirmed, but a
meaningfully smaller, more familiar task than the AIDL-based plan it
replaces.

## Implications for `apl`

- **The AIDL-bind approach (part 1) is now the strongest lead for
  performance-mode control** in the whole two-app teardown. It would let
  `apl` set performance mode *and* fan mode *and* GPU-fixed-frequency *and*
  RGB/controller settings through one mechanism, without needing xsu,
  without needing to solve the fan/serial question in part 3, and without
  the confirmed ~100ms/call `xsu` floor. Recommend a quick, cheap spike:
  try binding to `AyaAidlService` from a throwaway non-system `apl` build
  and see if `send()` actually works end-to-end (mode visibly changes)
  before committing design decisions around it.
- CPU/GPU sysfs mechanics are now fully confirmed at the command level and
  match what `apl` already does — no changes needed there regardless of
  which path (AIDL vs. own `xsu` sysfs writes) `apl` ultimately uses.
- **Fan control is no longer an unconfirmed lever — section 6 found and
  confirmed a plain sysfs PWM path** (`soc:pwm-fan`/hwmon,
  read-confirmed live on our actual Pocket FIT). `apl` (and
  `pulse-for-aya`) can very likely replicate real fan-speed control the
  same way it already does CPU/GPU — via `xsu`/`RootExec`, no AIDL needed
  for this specific lever — once the write side is confirmed. This
  supersedes the "serial/EC, needs AIDL" conclusion from part 3/pass 1.
- **RGB control (section 5) has a real AYANEO-native mechanism too** — not
  needed to replace `pulse`'s own dead-but-safely-gated RGB code, but
  available if `apl` or a future `pulse` fork ever wants real joystick RGB
  on this hardware: plain `Settings.System` keys under `ayaneo/share/`,
  standard `WRITE_SETTINGS` permission, no root, live-applies via a
  `ContentObserver`. Whether a genuine non-system app can actually write
  these specific custom keys (vs. `adb shell`'s more privileged path) is
  the one open question, same shape as the AIDL/`SharedPrefsProvider`
  caveats elsewhere in this document.
- **`apl`'s deferred "Module 2" (key binding) may not need to be built from
  scratch at all.** If `SharedPrefsProvider` really is reachable from a
  normal app (same unverified-in-practice caveat as the AIDL service in
  part 1 — static analysis says yes, hasn't been tried on-device), `apl`
  could implement arbitrary-key-to-button remapping — including binding
  literal keyboard keys like Escape/Enter/letters, not just other gamepad
  functions — by writing a JSON config to this provider, with **zero**
  input-injection code of its own and no accessibility-service plumbing.
  This would shrink Module 2 from "build an input remapper" to "learn one
  JSON schema and write to one ContentProvider" — a much smaller task than
  originally scoped. Confirmed for the extra/back buttons; whether it
  extends to the main face/shoulder buttons depends on whether GameWindow's
  key interceptor sees those `KeyEvent`s before the foreground app does —
  untested.
