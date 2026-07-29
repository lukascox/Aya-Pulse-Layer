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
standard-`Settings.System`-based RGB mechanism. Pass 4 (section 7) traced a
direct hardware complaint (over-sensitive analog sticks) as far as static
analysis and on-device probing allow — found the control is genuinely
crude (3-step gain, binary deadzone), and that a proper `apl`-side fix is
blocked by input arriving via privileged event injection rather than a
normal kernel joystick device, not a small follow-up task.

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

**Short answer: yes for the extra/back buttons, and — as of this pass —
confirmed NO for the main face/shoulder buttons, not just unconfirmed. The
write path is even simpler than the AIDL service, no binding required at
all.**

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
`evidence/customkey/FunInfo.java`) has 11 categories (`pCode` 0–10:
open-app, controller/performance shortcuts, input, nav, sound/DND, media,
brightness, screen-rotation/power, clipboard/screenshot/URL,
keyboard/macro, connectivity — full catalog in **4e** below). Two
categories are exactly what we want, confirmed in
`evidence/customkey/CustomKeyFunExecutor.java`:

- **`pCode=2` ("input"), `funCode=1`** ("input_inputKeyCode",
  `CustomKeyFunExecutor.java:279-281`):
  `CmdUtilKt.e("input keyevent " + executePar[0].value)` — sends **any**
  string as an Android `input keyevent` argument. Accepts both numeric
  keycodes and `KEYCODE_*` names — `KEYCODE_ESCAPE`, `KEYCODE_ENTER`,
  `KEYCODE_A`..`KEYCODE_Z`, anything `input keyevent` understands.
- **`pCode=9` ("keyb"), `funCode=4`** ("keyb_inputSpecifyKey",
  `CustomKeyFunExecutor.java:608-620`): takes a comma-separated list in
  `executePar[0].value` and fires `input keyevent` for each one in
  sequence — a genuine multi-key macro.
- **`pCode=9`, `funCode=2`** (`CustomKeyFunExecutor.java:585-586`) is a
  hard-coded example
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
  detector on the spot (`evidence/customkey/CustomKeyDispatch.java:111-140`).
  No service bind, no AIDL message framing — a plain
  `ContentResolver.query()`/`update()` call from any app is enough.

**Important safety note, not just a style preference**: `update()`
replaces the *entire* array, not one item. `CustomKeyDispatch.c()` clears
all existing detectors (`GlobalKeyInterceptKt.f4920d.clear()`,
`CustomKeyDispatch.java:116`) and rebuilds them from whatever you write.
**Always `query()` first, parse the existing array, append/modify the
relevant `CustomKeyItem`, and write the full array back** — a naive blind
`update()` would silently delete the user's existing LC/RC/paddle
bindings.

### 4d. Example payload (for manual, read-only-first testing — not yet tried)

Read the current config first (safe, no side effects):
```bash
adb shell content query --uri content://com.ayaneo.gamewindow.provider.sharedprefs/shared_prefs
```
This returns the live JSON array, including the real `KeyInfo.value`
keycodes this specific device uses for its LC/RC/etc. physical buttons —
needed before constructing a real write, since those trigger keycodes are
device-specific and weren't pinned down from static analysis alone (same
open item as the fan mechanism in part 3; see **4h** below for why static
analysis alone can't fully resolve this).

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

### 4e. Complete `pCode`/`funCode` catalog (all 11 categories, `pCode` 0–10)

Full read of `evidence/customkey/CustomKeyFunExecutor.java` (the `a(CustomKeyItem)`
dispatch method, `CustomKeyFunExecutor.java:100-682`, one `switch` on
`funInfo.getPCode()`). Behavior below is read directly from the code (shell
command issued / `Settings` key written / method called), not guessed.
Where a friendly label from the class's Kotlin reflection metadata
(`CustomKeyFunExecutor.java:67`, the huge `@Metadata` `d2` string array —
JADX preserves Kotlin property/function names there even where it can't
fully decompile the body) plausibly lines up with a branch, it's noted in
parens — but that metadata list is alphabetically sorted, not in
declaration order, so those labels are corroborating color, not proof of
exact `funCode` alignment; the code-read behavior is the actual evidence.

**`pCode=0` — "openApp"** (`:114-124`)
Two `executePar` values = `<packageName>|<className>`; builds
`Intent().setClassName(pkg, cls)` and starts it. Generic "launch this
activity" action.

**`pCode=1` — "hc" (controller/performance shortcuts)** (`:125-194`)
- `funCode=0` (`:128`): `new OtherControllerSerialManager().j()` — sends a
  command over the controller serial link; body of `.j()` not traced
  further this pass, uncertain exact effect.
- `funCode=1` (`:129-143`): shows/refreshes the in-overlay performance-mode
  panel (`PerformanceHolderKt.a`/`MainPanelManager.a`) — UI-only, doesn't
  itself change mode.
- `funCode=2` (`:144-176`): cycles fan mode within the *current* performance
  profile's `ModeConfiguration` (mute→balance→turbo→custom→off) and
  persists it via `PerformanceManager.f(configDataB, true)`.
- `funCode=3` (`:179-185`): toggles the AYA magic-window overlay
  open/closed (`AyaWindow.k()`/`AyaWindow.w()`).
- `funCode=4` (`:186-190`): toggles RGB on/off (`RgbManager.c(...)`, ties
  into section 5's RGB mechanism).
- `funCode=5` (`:191-192`): `MagicFunctioinActionKt.k()` →
  `switchDirectionDpad$1` coroutine (confirmed by name match,
  `MagicFunctioinActionKt.java:125-127`) — toggles D-pad direction-mode.

**`pCode=2` — "input"** (`:195-288`) — see 4b for the two keyboard-relevant
branches (`funCode=1`, main keycode injection; the general macro is under
`pCode=9`). Full set:
- `funCode=0` (`:282-287`): `input text "<value>"` (shell-level text
  injection, no focus check).
- `funCode=1` (`:279-281`): `input keyevent <value>` — **the main
  single-keycode remap action**, documented in 4b.
- `funCode=3` (`:273-278`): `input tap <x> <y>`.
- `funCode=4` (`:199-255`): `input swipe <x1> <y1> <x2> <y2> <duration>` —
  builds the swipe from the first/last point of a `"x,y|x,y|..."` path
  parameter.
- `funCode=5` (`:256-272`): injects text directly into the currently
  *focused editable field* via `AccessibilityNodeInfo.performAction`
  (`ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE`) — distinct from `funCode=0`'s
  shell-level `input text`; requires `WindowKeyEventService` (the
  accessibility service) to be running and a focused editable view to
  exist.

**`pCode=3` — "nav"** (`:289-344`)
- `funCode=0` and `funCode=1` (`:292-295`, identical branches): both call
  `MagicFunctioinActionKt.d(83)` = `input keyevent 83`
  (`KEYCODE_NOTIFICATION`) — opens/toggles the notification shade. Notable:
  two distinct `funCode`s dispatch to the literal same call — either a
  genuine duplicate in AYA's own code or two UI-facing options
  (open/expand vs. something else) that happen to collapse to the same
  keycode.
- `funCode=4` (`:296-297`): `input keyevent KEYCODE_BACK`.
- `funCode=5` (`:304-343`): go to home screen — has extra logic for a
  "double-confirm before leaving current app" setting
  (`AyaShareProvider.f6263c.c("home_double_confirm", false)`) and a
  special-case broadcast (`"ayaGoHomeBroadcast"`) when `com.ayaneo.home`
  is already foreground.
- `funCode=6` (`:299-300`): `MagicFunctioinActionKt.e()` →
  `goPreviousApp$1` coroutine (name-matched) — switches to the previously
  used app/recents entry.
- `funCode=7` (`:301-302`): `input keyevent KEYCODE_APP_SWITCH` (recents/
  task-switcher overlay).

**`pCode=4` — "sound" (volume/DND)** (`:345-429`)
- `funCode=0`/`funCode=1` (`:349-381`): volume up/down — **not** via
  `AudioManager`, via `SystemUtilKt` directly plus the on-screen HUD
  (`BrightnessFloat.setProgress`).
- `funCode=2` (`:382-395`): re-reads and re-displays current volume on the
  HUD (no change, just a "show volume" refresh).
- `funCode=4` (`:396-405`): toggles DND by checking
  `NotificationManager.getCurrentInterruptionFilter()` and calling
  `MagicFunctioinActionKt.c()`/`h()`.
- `funCode=5` (`:406-408`): `MagicFunctioinActionKt.h()` — opens DND
  (requests `ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` if not yet
  granted, `MagicFunctioinActionKt.java:87-100`).
- `funCode=6` (`:409-411`): `MagicFunctioinActionKt.c()` — closes DND
  (`MagicFunctioinActionKt.java:50-56`).
- `funCode=7` (`:412-421`): toggles mute (checks volume==0).
- `funCode=8` (`:422-424`): `MagicFunctioinActionKt.f()` — mutes (saves
  current volume to `aya_last_volume.conf`, sets volume to 0,
  `MagicFunctioinActionKt.java:66-72`).
- `funCode=9` (`:425-427`): `MagicFunctioinActionKt.i()` — un-mutes
  (restores the saved volume, `MagicFunctioinActionKt.java:102-119`).

**`pCode=5` — "midea" (media, sic — typo for "media" in AYA's own code)**
(`:430-454`) — straightforward `input keyevent KEYCODE_MEDIA_*`: `funCode`
0=pause, 1=play, 2=play/pause toggle, 3=previous, 4=next,
5=fast-forward, 6=rewind. High confidence — every branch is a literal
`input keyevent` string.

**`pCode=6` — "display" (screen brightness)** (`:455-521`)
- `funCode=0`/`1` (`:457-462`): `Settings.System.putInt(..., "screen_brightness_mode", 1/0)`
  — auto-brightness on/off.
- `funCode=2` (`:514-520`): toggles auto-brightness mode.
- `funCode=3`/`4` (`:465-513`): increase/decrease brightness (device-aware
  step size, plus HUD).

**`pCode=7` — "screen" (rotation/power)** (`:522-557`)
- `funCode=0` (`:524-529`): sets `accelerometer_rotation=1` **and** calls
  `iAyaDevices.J0(false)` — forces portrait (locks orientation while
  technically leaving the auto-rotation flag on; exact interaction of the
  two calls not fully traced past the `IAyaDevices` interface boundary).
- `funCode=1` (`:530-531`): `MagicFunctioinActionKt.a()` — disables
  auto-rotate (`accelerometer_rotation=0`, `MagicFunctioinActionKt.java:30-35`).
- `funCode=2` (`:532-548`): reads current rotation-lock state and either
  forces landscape-lock-off (calls the `funCode=0`-style force-portrait
  path) or disables auto-rotate, depending on state — a "smart toggle".
- `funCode=3` (`:549-551`): `MagicFunctioinActionKt.a()` **and**
  `iAyaDevices.J0(true)` — forces landscape.
- `funCode=5` (`:552-553`): `input keyevent KEYCODE_POWER` — screen
  sleep/wake (short press).
- `funCode=6` (`:554-555`): `input keyevent --longpress POWER` — power
  menu.
- **No `funCode=4` branch exists in the `switch`.** The class's reflection
  metadata (`:67`) lists 7 "screen_*" names for what is only 6 wired
  `funCode`s (0,1,2,3,5,6) — one name (plausibly
  `screen_openAutoRotateScreen`) has no corresponding case, likely dead
  code left over from a removed/merged branch. Worth knowing if `apl` ever
  tries to *write* `funCode=4` for this `pCode` — it would silently no-op.

**`pCode=8` — "content" (clipboard/screenshot/URL)** (`:558-578`)
- `funCode=0` (`:560-566`): opens a URL from `executePar[0]` via
  `Intent.ACTION_VIEW` (prepends `https://` if the string doesn't start
  with `http`).
- `funCode=1`/`2`/`3` (`:567-572`): `MagicFunctioinActionKt.d(277)` /
  `d(278)` / `d(279)` = `input keyevent 277/278/279` = standard Android
  `KEYCODE_CUT`/`KEYCODE_COPY`/`KEYCODE_PASTE` — confirmed by the literal
  Android `KeyEvent` constant values, not just name-matching.
- `funCode=4` (`:573-574`): `MagicFunctioinActionKt.j()` →
  `screenshotFull$1` coroutine (name-matched) — takes a full screenshot.
- `funCode=5` (`:575-576`): `ScreenRecordHelperKt.a()` — starts/stops
  screen recording.

**`pCode=9` — "keyb" (keyboard)** (`:579-621`) — see 4b for `funCode=2` and
`funCode=4` (the keyboard-macro branches already documented). Full set:
- `funCode=0` (`:581-582`): `input keyevent 123` — Android `KEYCODE_MOVE_END`
  (literal constant value 123) — moves cursor to end of field.
- `funCode=1` (`:583-584`): `MagicFunctioinActionKt.d(111)` =
  `input keyevent 111` — Android `KEYCODE_ESCAPE` (literal constant
  value). Note the reflection metadata's alphabetical name list includes a
  `keyb_switchKeyBoard` entry that would intuitively fit this slot better
  than "Escape" does — flagging the mismatch rather than asserting a label
  that doesn't match the code's actual behavior.
- `funCode=2` (`:585-586`): hardcoded `input keycombination -t 500
  KEYCODE_CTRL_LEFT KEYCODE_A` example (documented in 4b).
- `funCode=3` (`:587-607`): cycles to the next enabled IME — reads
  `Settings.Secure` `default_input_method`/`enabled_input_methods`,
  advances the index, writes the new `default_input_method`.
- `funCode=4` (`:608-620`): comma-separated multi-`input keyevent` macro
  (documented in 4b — the general-purpose macro action).

**`pCode=10` — "connect" (connectivity)** (`:622-679`)
- `funCode=0`/`1`/`2` (`:624-642`): Wi-Fi toggle/on/off via
  `WifiManager.setWifiEnabled`.
- `funCode=3` (`:643-649`): toggles Bluetooth (checks
  `BluetoothAdapter.isEnabled()`).
- `funCode=4`/`5` (`:650-655`): `MagicFunctioinActionKt.g()`/`b()` —
  Bluetooth on/off (with a user-facing toast if already in that state,
  `MagicFunctioinActionKt.java:38-48,74-85`).
- `funCode=6`/`7`/`8` (`:656-677`): airplane-mode toggle/on/off via
  `Settings.Global.putInt("airplane_mode_on", ...)` plus the standard
  `ACTION_AIRPLANE_MODE` broadcast.

### 4f. Trigger side: which physical buttons can actually fire a `CustomKeyItem` — confirmed, not just "extra/back buttons, untested for ABXY"

This closes the open question previously left as "confirmed for the
extra/back buttons; whether it extends to the main face/shoulder buttons
... untested."

**At the data layer, there is no restriction at all.** `KeyInfo`
(`evidence/customkey/KeyInfo.java:13-23`) is a plain `{keyName: String,
value: Int}` data class — `value` is never validated against any enum or
range. `CustomButtonIndex` (`evidence/customkey/CustomButtonIndex.java`,
package `com.ayaneo.gamewindow.ui.window.controller.protocol`, "compiled
from: `BehindButManager.kt`", `:21,24-29`) is a *separate* enum belonging
to a different subsystem (the back-paddle-specific `BehindButManager`
UI/protocol layer) that just happens to also define 4 named values
(`LC_SHOULDER=16, RC_SHOULDER=17, LC_BACK=18, RC_BACK=19`) — it is never
referenced by `CustomKeyItem`/`KeyInfo`/`CustomKeyDispatch` at all, so it
cannot be the gate. Parsing confirms this: `CustomKeyDispatch.c(String)`
(`evidence/customkey/CustomKeyDispatch.java:111-140`) builds a
`CustomSingleKeyDetector(customKeyItem.getKeyInfoList().get(0).getValue(), ...)`
straight from the raw JSON int — any value parses and gets a live detector
registered (`CustomKeyDispatch.java:124`).

**The real gate is downstream, in event routing, and it *is* restrictive.**
`WindowKeyEventService` (an `AccessibilityService`,
`aya-gamewindow-decompiled/sources/com/ayaneo/gamewindow/service/WindowKeyEventService.java:28,157-181`)
implements `onKeyEvent(KeyEvent)` — Android's global, system-wide key
interception callback for accessibility services — and forwards every key
event to `OnKeyInterceptKt.b(event)`
(`WindowKeyEventService.java:180`). That function
(`aya-gamewindow-decompiled/sources/com/ayaneo/gamewindow/custom/keydetector/OnKeyInterceptKt.java:40-154`)
is a long chain of `if (keyCode == iAyaDevices.get<X>())` checks against a
**fixed, small set of getters on `IAyaDeviceKey`** — and only when the
incoming keycode matches one of those does it call `onCheckCustomKey`
(`OnKeyInterceptKt.a`, `:24-37`), which is the function that actually
iterates the `CustomKeyDetector` list and invokes `dispatchKeyEvent`
(`OnKeyInterceptKt.java:51-73`, each branch guarding a call to `a(keyEvent)`).
**Any keycode that doesn't match one of those specific getters falls
through to the generic branch at the bottom of `b()`
(`OnKeyInterceptKt.java:125-153`)** — which only forwards to
`MainPanelManager` if the in-game overlay panel is currently open, or
passes the event through untouched otherwise. It never reaches
`onCheckCustomKey`.

The specific `IAyaDeviceKey` getters checked in `OnKeyInterceptKt.b()`
(`:51,54,58,62,66,70,74,80,86,108`) correspond, by cross-referencing
`IAyaDeviceKey`'s own property list
(`aya-gamewindow-decompiled/sources/com/ayaneo/devices/IAyaDeviceKey.java:7,15-66`),
to: `ayaLCCode`, `ayaRCCode`, `ayaModeCode`, `ayaHomeKeyCode`,
`ayaSlideRCode`, `ayaRollerIncrease`/`ayaRollerDecrease`/`ayaRollerPress`,
`ayaKeyCode`/`ayaKeyCode2`, `magicTouchCode`, `ayaVolumeUp`/`ayaVolumeDown`
— i.e. **exactly the AYA-specific extra hardware** (LC/RC back paddles,
Mode button, Home button, a roller/slide control, one or two other
AYA-specific buttons, a "magic touch" pad, hardware volume keys). **ABXY,
D-pad, L1/R1/L2/R2 shoulder triggers, and Start/Select are not among
these getters and are not checked anywhere in `OnKeyInterceptKt.b()`.**

**Conclusion: writing a `CustomKeyItem` whose `KeyInfo.value` is an ABXY/
DPAD/shoulder/Start-Select keycode via `SharedPrefsProvider` will parse
successfully and register a live `CustomSingleKeyDetector` — but that
detector will never fire**, because the `KeyEvent` for that button never
reaches `onCheckCustomKey` in the first place. This is confirmed
architecturally, independent of the separate, already-documented fact
(section 7 below) that the analog sticks and — per this same routing
logic — likely the main face buttons too may not even generate standard
`KeyEvent`s reaching this accessibility callback at all (they may be
injected directly into the focused app instead). Either way, the
mechanism is a firm **no** for remapping ABXY-class buttons through this
subsystem as currently wired. Only the extra/back/roller/mode/home/
volume-class buttons are viable triggers.

Corroborating, weaker evidence from the UI side: AYA Settings' actual
editor for this feature, `CustomKeyDetailFragment`
(`research/ayaspace-teardown/ayasettings_decompiled/sources/com/ayaneo/settings/ui/controller/customkey/CustomKeyDetailFragment.java`),
navigates to two separate key-picker screens for choosing a trigger
(`rlBut`/`rlButMode` rows at `:485-500`, navigating to `R.id.z7`/`R.id.J7`)
— not traced further this pass, but consistent with the UI steering users
toward a constrained picker rather than a raw keycode field, matching the
code-level restriction found above.

### 4g. `appWhite` confirmed: a per-foreground-package allowlist, not something else

`CustomKeyItem.appWhite` (`evidence/customkey/CustomKeyItem.java:24,102-104`)
is a `List<AppInfo>`; `hasAppWhiteList` (`:29,230`) is simply
`!appWhite.isEmpty()`. The check happens in
`CustomSingleKeyDetector.dispatchKeyEvent`
(`evidence/customkey/CustomSingleKeyDetector.java:63-78`): on every key
event, before any click/hold logic runs, it reads the current foreground
task's package/activity name (`TaskStackObserverKt.f5350a.f5327a`) and, if
`customKeyItem.getHasAppWhiteList()` is true, calls
`customKeyItem.isInAppWhite(str, str2)`
(`evidence/customkey/CustomKeyItem.java:138-154`) — which loops
`appWhite` and returns true only if `AppInfo.getPackageName()` matches the
current foreground package (the `activityName` parameter is accepted but
never actually compared — dead parameter, worth flagging as a minor bug in
AYA's own code). If there's no match, the detector calls `c()` (resets
click-tracking state) and returns `0` (event not consumed / binding does
not fire) — `CustomSingleKeyDetector.java:75-78`.

**Confirmed answer**: `appWhite` is exactly what it looks like — a binding
with a non-empty `appWhite` list is **scoped to fire only while one of the
listed packages is in the foreground**; an empty list (the default) means
the binding is always active regardless of foreground app. This is a
real, usable "only remap this key while playing Game X" mechanism, not
some other kind of allowlist (e.g. it does not gate config *visibility* or
*write access* — only whether the trigger fires at runtime).

### 4h. Trigger keycode enumeration — names exist, literal values are device-specific

`IAyaDeviceKey`
(`aya-gamewindow-decompiled/sources/com/ayaneo/devices/IAyaDeviceKey.java:7,15-66`)
enumerates the **names** of every AYA-specific extra-button keycode the
whole device abstraction layer knows about:
`ayaHomeKeyCode, ayaKeyCode, ayaKeyCode2, ayaLBRes, ayaLBWhiteRes,
ayaLCCode, ayaLTRes, ayaLTWhiteRes, ayaModeCode, ayaRBRes, ayaRBWhiteRes,
ayaRCCode, ayaRTRes, ayaRTWhiteRes, ayaRollerDecrease, ayaRollerIncrease,
ayaRollerPress, ayaSelectRes, ayaSelectWhiteRes, ayaSlideRCode,
ayaStartRes, ayaStartWhiteRes, ayaVolumeDown, ayaVolumeUp, magicTouchCode,
needSwitchLR`. This is the authoritative "what extra buttons does this
device family have" list, and (per 4f) the authoritative list of what can
ever be a working `CustomKeyItem` trigger.

**However, the literal integer keycode value for each of these, on this
specific Pocket FIT unit, is not resolved by this pass.** The interface
only declares the properties; the actual values live in a concrete
`IAyaDevices` implementation class (candidate: `AR13`, per section 3/6's
finding that `AR13`/`AR03` are the leading match for this hardware's fan
path — not confirmed as the *key-code* implementation too), and JADX
renders those overridden getters with single-letter obfuscated names
(e.g. the `CustomKeyDispatch.b()` helper at
`evidence/customkey/CustomKeyDispatch.java:59-85` resolves `"LC"` to
`AyaDevicesKt.f4814a.getF4854d()`, `"RC"` to `.getM()`, etc.) that can't be
mapped back to the semantic `IAyaDeviceKey` names by static text search
alone — doing so would require either deeper cross-referencing of the
compiled interface vtable order (not attempted this pass) or just reading
the values live, which is exactly what the `adb shell content query`
already recommended in **4d** gives you directly and unambiguously. Left
as-is rather than guessed at.

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

## 7. Joystick sensitivity — confirmed crude, and confirmed hard to fix ourselves

Triggered by a direct complaint: the analog sticks feel over-sensitive to
small deflections, and the suspicion that AYA Settings' "sensitivity"
control is really just widening the reported range rather than shaping a
real response curve. Two separate questions got answered: what the
existing control actually does (crude, confirmed), and whether `apl` could
build a better one itself (much harder than hoped, confirmed on-device).

### 7a. What "sensitivity" and "deadzone" actually are in AYA's own UI

`JoystickSensitivityView` (the in-game overlay panel) exposes **two
independent axes (L/R stick), each with exactly 3 discrete steps**: 50%,
100%, 150% — not a continuous slider, confirmed by the tick labels
(`AYASeekbar.setTickTexts("50%","100%","150%")`) and by
`ControllerHolder$bindJoystickSensitivity$2`
(`evidence/joystick-sensitivity/`), which maps raw values 0/1/2 straight to
those three label strings with no interpolation. Changing it calls
`OtherControllerViewModel.configJoystickSensitivity`
(`evidence/joystick-sensitivity/OtherControllerViewModel$configJoystickSensitivity$1.java`),
which sends the level+1 (i.e. 1/2/3) as a **single raw byte** over the
controller's serial link via `OtherControllerSerialManager`/
`NewControllerSerialManagerKt.b(buffer, commandIndex, byteValue)` —
command index `4` for the left stick, `5` for the right, `6` for a
**separate, binary DeadZone on/off toggle**
(`OtherControllerViewModel$configJoystickDeadZone$1.java` — not a radius,
just `'0'`/`'1'`).

**What that byte actually does inside the stick's firmware is invisible to
us** — it's consumed entirely inside the embedded controller MCU on the
other end of the serial link, not in any Android-side code we can read.
So the user's specific technical theory (range-widening vs. true curve
reshaping) can't be confirmed or refuted from this decompilation — but the
control surface itself is now confirmed to be exactly as coarse as it
feels: a 3-step gain multiplier plus a binary deadzone flag, not a tunable
curve/deadzone-radius editor. This mechanism is also **not exposed on the
external AIDL command bus** (no `com_set_joystick_sensitivity` in the
`AidlConstants` catalog from section 1/`ayaspace-teardown`) — only reachable
from GameWindow's own in-process UI.

### 7b. Why a `apl`-side fix is much harder than it first looked

The natural "build our own real curve" plan was: read the standard Android
joystick `MotionEvent` axes (works from any app, no root), apply a proper
deadzone+response curve, and re-emit corrected values system-wide by
grabbing the physical evdev device exclusively (`EVIOCGRAB`) and replaying
through a virtual `uinput` gamepad — the same technique desktop Linux tools
like `oversteer`/Steam Input use. Checked the prerequisites for this
directly on the AYANEO Pocket FIT (2026-07-25):

- **`/dev/uinput` exists** (`crw-rw---- uhid uhid`) — the kernel has uinput
  support, confirmed.
- **But there is no joystick evdev device to grab at all.** `cat
  /proc/bus/input/devices` and `dumpsys input` both list only 8 fixed
  system devices (haptics, `gpio-keys`, power/volume keys, touchscreen,
  headset/button jacks, one virtual keyboard) — no `ABS_X`/`ABS_Y`, no
  device that appears or changes while physically moving the stick. No
  `xbox`/`controller`-named process is running either.

Traced why in the decompiled source: `com.input.source.InjectInputDispatcher`
and `com.ayaneo.gamewindow.KeyInputInject`
(`evidence/joystick-sensitivity/`) both call
`InputManager.getInstance().injectInputEvent(event, ...)` — the same
privileged, normally-hidden API `adb shell input tap/swipe/keyevent` itself
uses (gated to apps holding `INJECT_EVENTS`, i.e. system-signed apps, or to
the `shell`/root UID). **The analog sticks never exist as a kernel input
device at all** — GameWindow reads the serial link in-process and
synthesizes `MotionEvent`/`KeyEvent` objects directly into whichever app
currently has input focus, bypassing evdev entirely. The actual
read-and-decide logic behind that (`addInjectParams`, `startInjectTouch`,
etc.) is declared `native` — a compiled `.so`, invisible to `jadx`, a hard
wall for any further static analysis of the exact math.

**This closes off the tidy uinput/evdev-grab plan completely — there is
nothing to grab.** A real fix would instead require reading the raw serial
UART ourselves (same class of link as `AR03`'s `"dev/ttyHS4"` from the
earlier fan/RGB passes, though not confirmed to be the same port on
whichever device class this Pocket FIT actually is) — either racing
GameWindow for the same port (uncertain if it even allows a second reader)
or somehow stopping GameWindow's own controller reader first, reverse
engineering AYA's undocumented framing well enough to decode real stick
position, then re-injecting corrected events ourselves via the same
`injectInputEvent` mechanism (reachable from a rooted/`xsu`'d context, same
privilege tier as `shell`). That's a substantially larger, standalone
reverse-engineering project — deferred, not pursued further this session.

### Implication

No `apl`-side action taken or recommended from this pass. The concrete,
useful output is the write-up itself: precise, evidenced documentation of
what the "sensitivity"/"deadzone" controls currently do (3 fixed gain
steps, binary deadzone, both opaque past the serial boundary) — worth
compiling into feedback for AYA directly, independent of anything `apl`
does. If AYA ever exposes a real curve/deadzone-radius control (or better,
raw pre-processed stick data) this whole limitation disappears without any
work on our end.

## 8. Gyroscope access — real hardware, but not a real Android `Sensor`

Nothing in this repo had looked at motion-sensor access before this pass.
Investigated both decompiled trees end-to-end
(`aya-gamewindow-decompiled/` and
`research/ayaspace-teardown/ayasettings_decompiled/`) for
`gyro|Gyroscope|SensorManager|TYPE_GYROSCOPE|MotionSensor|SensorEventListener`.

### 8a. The device has a gyro capability flag, and GameWindow does use it — for controller gyro-aim, in its own in-game overlay

`IAyaDevices` declares a `hasGyro: Boolean` capability property
(`aya-gamewindow-decompiled/sources/com/ayaneo/devices/IAyaDevices.java:41`,
in the class's Kotlin reflection metadata — `getHasGyro`/`hasGyro` appears
alongside `hasEqualizer`, `hasBypassPowerSupply`, etc.). Both `AR13`
(`aya-gamewindow-decompiled/sources/com/ayaneo/devices/ar13/AR13.java:22`)
and `AR14`
(`aya-gamewindow-decompiled/sources/com/ayaneo/devices/ar14/AR14.java:34`)
redeclare `hasGyro` in their own class metadata — i.e. these device
families override the interface default rather than inheriting it as-is.
**Not resolved this pass**: the literal `true`/`false` value for our
specific Pocket FIT unit — same obfuscated-getter problem as section 4h,
the override body isn't textually present, only the property name in
metadata.

Gyro is a real, user-facing feature in GameWindow's in-game controller
settings panel ("other" controller variant — `ControllerHolder`/
`OtherControllerViewModel`, the same subsystem section 7 already covered
for joystick sensitivity). The UI binding
(`aya-gamewindow-decompiled/sources/com/ayaneo/gamewindow/ui/window/controller/other/ControllerHolder$bindGyro$4.java:74-94`)
shows: an on/off checkbox, a sensitivity seekbar (shown only when gyro is
on), and a radio button choosing which shoulder button (LB or LT,
`R.id.civ_lb`/`R.id.civ_lt`) acts as the gyro-activation "hold to aim"
trigger — a standard "gyro-while-holding-a-button" scheme, same concept as
many third-party controllers.

### 8b. Gyro is driven over the same proprietary controller serial link as joystick sensitivity — not a standard Android `Sensor`

`OtherControllerViewModel.switchGyro` / `setGyroSensitivity` are the entry
points. Full trace of the "turn gyro on" path
(`aya-gamewindow-decompiled/sources/com/ayaneo/gamewindow/ui/window/controller/other/OtherControllerViewModel$switchGyro$1.java`,
full 188-line file):
1. Reads persisted config from a plain **file**, `aya_gyro.conf`, via
   `AyaShareConfUtilKt.d(AyaShareConfUtilKt.c("aya_gyro.conf"), "10")`
   (`switchGyro$1.java:73`).
2. Updates in-memory `UiState.gyroSensitivity`
   (`OtherControllerViewModel.java:1487`, the `GyroSensitivity` data class
   at `:580-635` — fields `isOn: Boolean`, `which: Int` [LB=0/LT=1
   trigger], `level: Int` [sensitivity step]).
3. After a 100ms UI-settle delay, calls
   `otherControllerSerialManager.g(iD, i3, this)`
   (`switchGyro$1.java:97-99`) — a suspend call into
   `com.ayaneo.gamewindow.utils.newserial.other.OtherControllerSerialManager`,
   the **same serial-link manager class** section 7a already traced for
   joystick sensitivity gain/deadzone bytes. Turning gyro off
   (`switchGyro$1.java:102-142`) similarly writes the file, updates
   `UiState`, then calls
   `otherControllerSerialManager2.c(this)` after mutating
   `otherControllerSerialManager2.f6017a` via
   `NewControllerSerialManagerKt.b(...)` — the same low-level
   buffer-byte-patching helper (`NewControllerSerialManagerKt.b(buffer,
   commandIndex, byteValue)`) already documented in section 7a for the
   stick-sensitivity command bytes.
4. `ConfigGyroSensitivity`/`SwitchGyro` are modeled as first-class
   `UiAction`s (`OtherControllerViewModel.java:1001-1032` and
   `:1368-1391`), wired at `:1837-1849` — confirming this is a deliberate,
   maintained feature, not incidental code.

**Config persistence is a third mechanism, distinct from both this repo's
previously-documented ones.** `AyaShareConfUtilKt.a(String)`
(`aya-gamewindow-decompiled/sources/com/ayaneo/gamewindow/utils/system/AyaShareConfUtilKt.java:23-26`)
builds a plain **file path** —
`(AyaDevicesUtilKt.r ? "/sdcard/.aya/" : "/data/system/aya/") + name` —
guarded by a `ReentrantLock`-protected file observer
(`AyaShareObserver`, referenced at `AyaShareConfUtilKt.java:37-38`). This
is neither the `SharedPrefsProvider` `ContentProvider` (section 4) nor the
`Settings.System ayaneo/share/*` mechanism (section 5's RGB) — it's a
third, plain-file-based shared-config channel, apparently reserved for
this class of controller/serial-adjacent settings (`AYA_DEVICES_GYRO_CONFIG`
is one of ~50 named constants in this same file's reflection metadata,
`AyaShareConfUtilKt.java:19`). Not investigated further — out of scope for
this pass, flagged for whoever next touches serial-controller config.

### 8c. Confirmed: no standard `SensorManager`/`Sensor.TYPE_GYROSCOPE` usage anywhere in either app's own code

Grepped both full decompiled trees for any `SensorManager`/
`TYPE_GYROSCOPE`/`android.hardware.Sensor` usage:
```
aya-gamewindow-decompiled: only hits are ContextCompat.java (AndroidX
  library, generic), SphericalGLSurfaceView.java + OrientationListener.java
  (bundled ExoPlayer library), and ContextUtilKt.java.
ayasettings_decompiled: only ContextCompat.java (AndroidX library).
```
- The **only** place a real `Sensor` is actually registered anywhere in
  either app is `com.google.android.exoplayer2.video.spherical.SphericalGLSurfaceView`
  (`aya-gamewindow-decompiled/sources/com/google/android/exoplayer2/video/spherical/SphericalGLSurfaceView.java:270-273,204,206`)
  — bundled ExoPlayer library code for 360°/VR video playback, using
  `Sensor.TYPE_GAME_ROTATION_VECTOR` (15) / `TYPE_ROTATION_VECTOR` (11),
  **completely unrelated to controller gyro** and not written by AYANEO.
- `ContextUtilKt.java`
  (`aya-gamewindow-decompiled/sources/com/ayaneo/gamewindow/utils/ContextUtilKt.java:28`)
  declares a generic `Context.sensorManager` extension property
  (`android.hardware.SensorManager`, alongside ~25 other similar
  `getSystemService(...)` convenience wrappers for `WifiManager`,
  `PowerManager`, etc.) — but grepping the whole tree for any caller
  (`getSensorManager(`/`ContextUtilKt.getSensorManager`) found **zero
  invocations**. It's declared but dead — never actually used to read a
  real sensor anywhere in AYA's own code.
- No `gyro`/`Gyro` hit anywhere at all in the entire AYA Settings
  decompiled tree (`grep -rli gyro` returned empty) — the feature is
  GameWindow-only, not surfaced in the standalone settings app.
- No gyro-related entry in either app's AIDL command catalog:
  `aya-gamewindow-decompiled/sources/com/ayaneo/gamewindow/utils/aidl/AidlConstants.java`
  and
  `research/ayaspace-teardown/ayasettings_decompiled/sources/com/ayaneo/settings/utils/aidl/AidlConstants.java`
  both have zero `gyro` hits — gyro calibration/sensitivity/enable is not
  reachable over the cross-app AIDL bus documented in section 1, only from
  GameWindow's own in-process controller UI.

### 8d. Implication — same "AYA hogs the resource" shape as the analog sticks, worse

Section 7 already established the analog sticks are read out-of-band over
serial and synthesized into the focused app via privileged
`injectInputEvent`, bypassing the normal Android input stack entirely —
meaning a third-party app (e.g. a Moonlight/Artemis/Sunshine-style
streaming client) cannot read raw stick position itself; it can only
receive whatever GameWindow chooses to inject.

**Gyro is the same story, and arguably a harder wall**: there is no
evidence anywhere in either decompiled tree of gyro data being
synthesized into standard Android input events (`MotionEvent`/`KeyEvent`)
at all — the gyro pipeline traced in 8b only ever configures the physical
sensor's *behavior* (on/off, sensitivity, which trigger button arms it)
over the serial link; where the resulting motion *data* actually goes
(consumed natively inside GameWindow's own process for its own gyro-aim
overlay feature, most likely, given the `.so`-boundary precedent from
section 7b) was not traced this pass. Either way: **there is no standard
`SensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)` path available to
any third-party app on this device today.** A game-streaming client
wanting gyro input would face the same fundamental problem section 7
already documented for the sticks — the real sensor is not exposed as a
normal Android input source, and GameWindow's own consumption of it is
opaque native code, not a resource a second reader could straightforwardly
share or intercept. This is a "for later" flag, per the task that
prompted this pass, not something resolved or actioned here.

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
- **Gap noted (2026-07-27), not yet closed**: the in-game FPS counter
  overlay (shown when AyaSettings' HUD is on — FPS number, per-core CPU
  clocks, colored mode-name label) has not been located in this teardown's
  decompiled code, in either this package or `ayaspace-teardown`. Whether
  its mode-name label reads `AyaAidlService`'s live `currentMode` (see
  section 2 above) or something else is unconfirmed — relevant because
  `pulse-for-aya` changes governor/frequency via `xsu`/sysfs, never through
  `com_set_performance_mode`, so if the label does read gamewindow's AIDL
  state it would go stale under pulse and keep showing whatever AYASpace
  itself last set. See `pulse-for-aya/README.md`'s "Open question
  (2026-07-27)" section for the full writeup and the on-device check that
  would confirm or rule this out.
