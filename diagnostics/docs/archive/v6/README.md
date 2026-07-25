# PulseFit — AutoTDP + key remap + fan curve for AYANEO Pocket FIT

## Project goal

Build a native Android GUI app for AYANEO Pocket FIT (SG8350P / "Snapdragon"-class
SoC, Android 14), replacing the manual "Root Script" workflow from AYA Settings.
Fork/inspired by the AutoTDP logic from the `pulse` project
(https://github.com/keiretrogaming/pulse), but with a root layer based on the native
`xsu`/`xsud` binary, not on reflection into `PServerBinder` (which is not available on
this device).

Author is not a professional programmer. Project built iteratively with AI assistance
(Claude Code / other agents) plus manual builds/tests on hardware. See
`docs/HANDOFF_TEMPLATE.md` at the end of each session.

## Scope (in priority order)

1. **Module 1 — AutoTDP (in progress)**
   Background service, autostart on boot, reads FPS of the foreground app, runs a
   step-controller with hysteresis to pick CPU/GPU limits that hold a target FPS
   (default 60) at the lowest possible power draw. Writes to sysfs via `xsu`.

2. **Module 2 — Key remapping (deferred)**
   Arbitrary physical button binding (e.g. back button -> ESCAPE/H). Likely an
   Accessibility Service + InputManager, or root-level via `getevent`/`sendevent` if
   buttons are reserved by firmware.

3. **Module 3 — Fan curve (deferred, priority reconsideration below)**
   PI-controller holding minimum RPM relative to temperature, modeled on AutoTDP from
   `pulse`. Intended to eventually share a control loop with Module 1.

4. **Out of scope for now:** the "streaming mode" toggle in AYA Settings (to block
   accidental entry into streaming mode) — requires decompiling AYA Settings, separate
   analysis session.

## Design principles

- **KISS.** Every module is a small, separately testable class. No "god objects."
- **Modularity.** Transport layer (`xsu`), data-reading layer (FPS/temp), control
  logic layer, and UI layer are separated and unaware of each other's internals (see
  `root/`, `fps/`, `tdp/`, `ui/`).
- **Small steps.** Every iteration is one small, verifiable increment (e.g. "service
  starts on boot and logs," then "reads FPS and logs it," then "writes to sysfs").
- **No vendor magic copy-paste.** We do not copy `pulse` code 1:1 (different root
  mechanism) — only its controller architecture/logic is used as a reference.

## Repo structure

```
pulsefit/
├── app/
│   ├── src/main/java/pl/pulsefit/app/
│   │   ├── root/      -> XsuShell.kt (root channel, exec("xsu"))
│   │   ├── fps/        -> FpsReader.kt (dumpsys SurfaceFlinger --latency pipeline)
│   │   ├── tdp/        -> AutoTdpController.kt (logic), SysfsWriter.kt (I/O)
│   │   ├── service/    -> TuningService.kt (foreground service, main loop)
│   │   ├── boot/        -> BootReceiver.kt (autostart)
│   │   └── ui/          -> MainActivity.kt (minimal UI, status/on-off toggle)
│   ├── src/main/res/    -> resources (layout, values)
│   ├── src/main/AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/wrapper/       -> gradle wrapper (to be regenerated locally)
├── docs/
│   └── HANDOFF_TEMPLATE.md  -> session-closing template
├── build.gradle.kts       -> root project config
├── settings.gradle.kts
└── README.md              -> this file
```

## Known technical facts (from prior sessions, updated after v5/v6 diagnostics)

- `xsu` works as full root from `adb shell` (confirmed across 7 diagnostic runs:
  uid=0, protected file reads, root-level writes). STILL UNVERIFIED: invocation from
  inside a normal app via `Runtime.exec()` — this remains the FIRST test to perform
  for Module 1.
- Root communication pattern: `Runtime.getRuntime().exec("xsu")`, commands written to
  stdin, `waitFor()`, stdout read back (see decompiled `YtRootShell.java` from AYA
  Settings — analogous mechanism, different binary name).
- `pulse` uses reflection into the `PServerBinder` service, only available on AYN
  Odin 3, AYN Thor, and Retroid Pocket 6 — AYANEO Pocket FIT does NOT have this
  service, hence the need for our own transport layer via `xsu`.
- Example sysfs nodes used by `pulse` (analogous targets for us):
  `/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq` (CPU),
  `/sys/class/kgsl/kgsl-3d0/min_pwrlevel` (GPU, power-level index, not frequency).
  Confirmed on our device (see HARDWARE_PROFILE.md): 4 independent CPU policies
  (policy0/2/5/7), 14 GPU power-levels (index 0=1050MHz down to index 13=231MHz).

- **FPS measurement — METHOD CONFIRMED AND FINALIZED.** Do NOT use `dumpsys gfxinfo`
  as the primary FPS source for `FpsReader.kt` — confirmed unreliable for emulator
  workloads (returns 0 frames for RetroArch; returns frozen/cached stats for
  Eden/Switch emulator gameplay). Use the confirmed working pipeline instead:
  1. Detect foreground pkg via `dumpsys window windows | grep mCurrentFocus` +
     `dumpsys activity activities | grep mResumedActivity`.
  2. Match SurfaceFlinger layer via `dumpsys SurfaceFlinger --list`, preferring
     `SurfaceView[pkg]` over generic `pkg/Activity` layer (layer suffix numbers are
     unstable across launches — match by package name substring, not exact string).
  3. Compute FPS from `dumpsys SurfaceFlinger --latency "<layer>"` timestamp deltas.
  This works uniformly across native Android UI apps, RetroArch, and Switch emulators
  without hardcoding any package name. `gfxinfo` remains usable only as a secondary
  jank/percentile source for native Android UI apps, never as the primary FPS signal.

- **GPU busy% — neither available sysfs path is fully trustworthy.**
  `/sys/class/kgsl/kgsl-3d0/gpubusypercentage` is marked broken on this kernel. The
  raw `/sys/class/kgsl/kgsl-3d0/gpubusy` (busy_cycles/total_cycles) path has a
  confirmed cycle-counter wraparound issue, producing garbage percentages (observed as
  extreme as -2718%) between reads. `AutoTdpController.kt` must include a sanity check
  (reject any computed % outside 0-100 as "counter_reset/no data") and should not treat
  either GPU busy% source as fully reliable ground truth on its own.

- **Thermal protection cannot be assumed from AYASpace.** Under full CPU+GPU load
  (Gaming mode, Eden emulator, Super Mario Odyssey), individual CPU cores reached
  87-90.7 C in a 90-second window with no visible frequency throttling from AYASpace
  at the OS-observable governor/freq level. This means Module 3 (fan curve / thermal
  ceiling) may need to be reprioritized earlier than "deferred" — our own controller
  cannot assume the vendor is protecting the device for us in high-performance modes.

## Iteration workflow (no live Claude Code agent)

1. Code changes generated in-session (Perplexity / other assistant) as files or a
   .zip archive.
2. Unpack on a machine with JDK + Android SDK cmdline-tools installed.
3. `./gradlew assembleDebug` -> APK in `app/build/outputs/apk/debug/`.
4. `adb install -r <apk>` on the Pocket FIT, manual test.
5. Collect logs (`adb logcat`) and observations, report back in next session.
6. At session end: fill out `docs/HANDOFF_TEMPLATE.md` and commit to repo.

## Status

Repo is a skeleton (placeholder files with TODO comments). No module has a working
implementation yet. First goal: Module 1, step 1 — a service that starts on boot and
writes a log to logcat, to verify `xsu` is callable from inside the app.
