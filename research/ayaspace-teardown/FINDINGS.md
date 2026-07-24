# FINDINGS — AYA Settings (`com.ayaneo.settings`) teardown, pass 1

Target: `com.ayaneo.settings` v1.1.112 (versionCode 147), `app_QCOMRelease` build
variant, pulled from an AYANEO Pocket FIT (Snapdragon). See `WORKLOG.md` in this
directory for exactly how the APK was extracted and the full methodology.

Both open questions from `README.md` got real answers. Short version first,
evidence and detail below.

## TL;DR

1. **Does the profile switch (Eco/Balanced/Streaming/Gaming/Max) do more than
   CPU governor+freq and GPU pwrlevel?**
   **Yes — fan mode is part of every profile's config.** Each profile carries
   a `FAN_MODE` (`OFF`/`MUTE`/`BALANCE`/`TURBO`/`CUSTOM`) alongside a
   `CPUSchedulerMode` (`POWER_SAVING`/`BALANCED`/`HIGH_PERFORMANCE`), a
   per-core CPU frequency cap list, and a GPU max-frequency+fixed flag. So
   the fan curve genuinely changes with the profile, not just CPU/GPU clocks
   — this is a real, previously-unconfirmed lever for `apl`'s profile-mimicking
   feature.

2. **Does `xsud` expose a Binder/AIDL interface instead of shell-spawn?**
   **Not `xsud` specifically, but yes — a Binder/AIDL interface exists**, and
   it's the *primary* path for performance-mode changes. It's not a system
   service, though: AYA Settings binds to a **separate app**,
   `com.ayaneo.gamewindow` (service class
   `com.ayaneo.gamewindow.utils.aidl.AyaAidlService`), and talks to it over a
   trivially thin, hand-rolled AIDL interface (3 methods: `send(String)`,
   register callback, unregister callback) carrying colon-delimited text
   commands. **AYA Settings itself never touches sysfs for the profile
   switch** — it just sends `"<clientId>:msg_type_performance:com_set_performance_mode:<0-4>"`
   over this Binder connection and updates its own UI state. The actual
   privileged sysfs/shell writes happen inside `com.ayaneo.gamewindow`,
   which we have **not** decompiled yet (see "Open question / next step"
   below).

## Why this app doesn't need `xsu` at all

`AndroidManifest.xml` declares `android:sharedUserId="android.uid.system"`
(see `resources/AndroidManifest.xml:3`). AYA Settings is a priv-app signed
with the platform key and shares the `system` UID directly — it already runs
with system-level privileges in-process. That's why its own shell helper
(`RootShell.a()`, see `evidence/shell/RootShell.java`) mostly just calls
`Runtime.exec()` / `ProcessBuilder` **with no `su`/`xsu` prefix at all**
(`evidence/shell/CmdUtilKt.java`, function `f()`/`b()`) — no elevation is
needed, the process is already root-equivalent. This is fundamentally
different from `apl`, which is a normal, non-system app and has no path to
this except `xsu -c`.

Two device families do use an external root helper instead of raw
`Runtime.exec`, confirmed in the shell-wrapper dispatch table
(`RootShell.a()`):
- **`YtRootShell`** (AR04/AR05/AR05S devices) execs a binary called `ytsu`
  and writes the command to its **stdin**, then `exit\n`
  (`evidence/shell/YtRootShell.java`). Notably this is the same
  stdin-piping pattern that failed for our own `xsu` binary
  (`xsu-capability-probe/FINDINGS.md`) — but it works here because `ytsu`
  is a different, vendor-specific binary with (presumably) different stdin
  handling than `xsu`. Doesn't change our own findings about `xsu`, just
  confirms the failure is `xsu`-specific, not a generic Android/root
  limitation.
- **`KtRootShell`** (AR03-compatible devices) goes through a **native JNI
  shim** (`com.kingtop.shellcmd.ShellCmd`, method `shellJni`), and directly
  writes MediaTek-specific sysfs nodes for a "GPU freq lock" toggle:
  `/proc/gpufreq/gpufreq_opp_freq` and `/proc/ppm/policy/ut_fix_freq_idx`
  (`evidence/shell/KtRootShell.java`). `ppm` = MediaTek's Power/Performance
  Management sysfs tree — confirms these device variants are MTK-based, not
  Qualcomm, and their sysfs surface is entirely different from ours
  (irrelevant to our Snapdragon-based hardware, noted here only because it
  was on the direct path while tracing `RootShell`).

`RootShell` itself is **not** used for the profile switch (that's all AIDL,
see below) — it's used for other privileged one-offs: closing wifi
(`CloseWifiFragment`), external-display resolution and color mode
(`ExternalResolutionFragment`, `ColorModeFragment`), OTA package install
(`SoftwareOTAFragment`), and a literal user-facing "run a root script" debug
screen (`RootScriptFragment`).

## The AIDL mechanism, in full

**Bind target** (from `evidence/aidl/LauncherApp_excerpt.java`, used by
`AyaAidlManager.h()` in `evidence/aidl/AyaAidlManager.java`):
```
Intent intent = new Intent();
intent.setClassName("com.ayaneo.gamewindow", "com.ayaneo.gamewindow.utils.aidl.AyaAidlService");
bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
```

**Interface** (`evidence/aidl/AyaAidlInterface.java`, descriptor string
`"com.ayaneo.gamewindow.AyaAidlInterface"`) has exactly three transactions:
- `send(String msg)` — transact code 1, the actual command channel
- `k(AyaAidlCallback cb)` — transact code 2, register a callback (receives
  async messages back, e.g. the client ID on connect, or unsolicited
  `com_set_performance_mode` notifications — see `PerformanceFragment`'s
  `aidlCallBack`, which listens for `COM_SET_PERFORMANCE_MODE` coming *back*
  from gamewindow, e.g. if the mode was changed by a hardware button/overlay
  rather than from within Settings)
- `q(AyaAidlCallback cb)` — transact code 3, unregister

**Wire format** for `send()`, confirmed in
`evidence/aidl/AyaAidlManager$sendAidlMsg$1.java`:
```
"<clientId>:<msg_type>:<command>:<args...>"
```
e.g. `"7:msg_type_performance:com_set_performance_mode:3"` to switch to Game
mode. `clientId` is assigned by gamewindow on registration (delivered via the
callback as `"msg_type_register:<id>"`, parsed in `AyaAidlManager.j()`).
If gamewindow isn't bound yet, outgoing messages queue up
(`AyaAidlManager.messageQueue`) and get flushed after bind — see
`AyaAidlManager.h()`/`m()`.

**Command catalog**, the full `AidlConstants` list
(`evidence/aidl/AidlConstants.java`) — this is effectively the entire
remote-control surface AYA Settings has over gamewindow:
```
com_set_performance_mode          -- the profile switch (0=Eco,1=Balanced,2=Streaming,3=Gaming,4=Max)
com_set_performance_reset
com_set_performance_fan
com_set_performance_cpu           -- "<cpuId>_<freq>"
com_set_performance_scheduler
com_set_performance_gpu
com_set_performance_gpu_is_fixed
com_set_controller_state
com_set_controller_style
com_set_key_mouse_quick_key
com_get_single_key_mapping / com_set_single_key_mapping
com_init_local_firmware_version
com_set_rgb_is_open
com_get_abxy_mode / com_set_abxy_mode
com_get_l1l2r1r2_mode / com_set_l1l2r1r2_mode
com_get_direction_dpad_mode / com_set_direction_dpad_mode
com_wifi_close
com_set_fan_speed_strategy
com_set_fan_speed_is_linear
com_apk_install_done
com_show_magic_touch
```

**Confirmed profile-mode values**: `PerformanceFragment.u3(mode)`
(`evidence/performance/PerformanceFragment.java`, the actual click handler
for the 5 mode tabs) maps `0=Eco, 1=Balanced, 2=Streaming, 3=Gaming, 4=Max`
and is the only place that sends `com_set_performance_mode`. Note:
`SwitchPerformanceModeFragment` (confusingly named — it's a *settings*
screen, not the switcher) does **not** change the active profile; it only
toggles a bitmask of which of the 5 profile buttons are *visible* in the
UI, persisted via a `ContentProvider` (`AyaSettingProvider`,
`AyaSetting.SWITCH_PERFORMANCE_MODE`) — unrelated to the AIDL mechanism.

## Per-profile config schema (answers question 1)

`ModeConfiguration` (`evidence/performance/ModeConfiguration.java`), one
instance per mode, keyed by mode int in `IAyaDevice.N()` (device-specific
map, not yet extracted per-device in this pass):

```kotlin
data class ModeConfiguration(
    val fanMode: FAN_MODE,                    // OFF / MUTE / BALANCE / TURBO / CUSTOM
    val cpuSchedulerMode: CPUSchedulerMode,    // POWER_SAVING / BALANCED / HIGH_PERFORMANCE
    val cpuFrequencies: List<CPUFrequency>,    // per-core cap
    val gpuFrequency: GPUFrequency,            // max freq + isFixed flag
)
```

This whole struct gets serialized to JSON and both (a) kept as local UI
state and (b) sent piecemeal to gamewindow as individual
`com_set_performance_*` AIDL messages when the user tweaks a sub-setting.
When switching modes wholesale (`u3()`), only `com_set_performance_mode:<N>`
is sent — gamewindow presumably has its own copy of the per-mode config
table and applies fan+scheduler+CPU+GPU together server-side; AYA Settings'
local `ModeConfiguration` JSON is for display/editing, not something it
pushes over on every mode switch.

## Resolved: `com.ayaneo.gamewindow` teardown, pass 2

`com.ayaneo.gamewindow` has since been decompiled and analyzed — see
`research/aya-gamewindows-teardown/FINDINGS.md`. Headline result:
**`AyaAidlService` is `exported="true"` with no `android:permission`, and
its code does zero caller-identity verification** — any app, including a
normal non-`system` `apl` build, can bind to it directly and drive
performance-mode/fan/GPU/RGB/controller commands with no `xsu` involved at
all. That teardown also confirms the concrete CPU-governor and
`kgsl`/Adreno GPU sysfs commands AYA itself issues (matching what `apl`
already knew) and traces the fan-mode path far enough to show it's *not* a
plain sysfs write — likely serial/EC-based, mechanism not yet pinned down.
Full detail, evidence, and implications for `apl` in that directory.

## Implications for `apl`

- Nothing here suggests `apl` should try to reach `xsud` over AIDL — that
  service doesn't exist under that name; the AIDL surface belongs to
  `gamewindow`, a different vendor app, for a different purpose (it's AYA's
  own internal settings-to-overlay channel, not a generic root-command bus).
- The fan-mode-per-profile finding is real and actionable: `apl`'s
  profile-mimicking feature should account for fan behavior differing by
  profile, not just clocks — assuming `apl` replicates via sysfs, the fan
  sysfs node(s) need to be part of the per-profile write set.
- The `gamewindow` AIDL service turned out to be the strongest lead in the
  whole teardown — see `research/aya-gamewindows-teardown/FINDINGS.md` for
  why binding to it directly could let `apl` skip `xsu` entirely for
  performance-mode changes.
