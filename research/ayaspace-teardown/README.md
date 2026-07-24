# AYA Settings teardown — static analysis, not another Android probe

Unlike `xsu-capability-probe/` and `autotdp-ab-harness/` (throwaway Android
apps we build and run on-device), this is **static analysis of a different,
already-installed app** — the vendor's own `com.ayaneo.settings` (AYA
Settings). No Gradle project here, no on-device execution — just: pull the
APK, decompile with `jadx`, read the source, write down what's found.

## Why this, why now

Two concrete open questions this could resolve, both with direct
architectural impact on `apl/app/`'s eventual `XsuShell.kt` and
`AutoTdpController.kt`:

1. **Does AYA Settings' profile switch (Eco/Balanced/Streaming/Gaming/Max)
   do anything beyond the CPU governor+freq and GPU pwrlevel writes we
   already know about (see `apl-diag/docs/HARDWARE_PROFILE.md`)?** If
   Gaming/Max also touch a fan curve or thermal trip points we haven't
   directly observed, that's a real, currently-missing lever for our own
   profile-mimicking feature (the "assign a profile per app" idea being
   scoped as `apl`'s first real feature, ahead of full AutoTDP).
2. **Does `xsud` expose a Binder/AIDL interface** (analogous to `pulse`'s
   `PServerBinder`), rather than only the `xsu -c` shell-spawn mechanism we
   currently use? This has been an open question since the very first
   `xsu_handoff` document and was never checked. If such an interface
   exists and AYA Settings uses it, it could explain why the `xsu` "stdin"
   invocation method silently failed (see
   `xsu-capability-probe/FINDINGS.md`), and — more importantly — could be a
   much better foundation for `AutoTdpController`'s polling loop than
   spawning a new `xsu` process per call, which has a confirmed ~100ms
   floor per call regardless of what the command does (same FINDINGS.md).

A negative result (nothing more than what we already know) is still a real,
useful answer — it confirms our current sysfs-replication approach already
covers everything replicable, and that the `xsu -c` exec model is the best
available option, not something we settled for out of ignorance.

## What NOT to re-derive (already confirmed elsewhere, don't re-litigate)

- `xsu` works from an installed app, `args` method only, `stdin` method
  confirmed broken — `apl/research/xsu-capability-probe/FINDINGS.md`.
- Full CPU OPP table + governor list — `apl-diag/docs/HARDWARE_PROFILE.md`.
- The ~100ms-per-`xsu`-call floor and the ~126s stall failure mode — same
  FINDINGS.md.
- pulse_lite_v3.7.sh's tier/hysteresis/floor controller architecture —
  `apl/docs/archive/pulse_lite/v3.7/`.

## Process (see also: the parent conversation's step-by-step teaching
writeup, if available — this is the condensed version for a fresh session)

1. **Get the APK off the device.**
   ```bash
   adb shell pm list packages | grep -i ayaneo
   adb shell pm path com.ayaneo.settings
   adb pull <path-from-above> ayasettings.apk
   ```
   If `pull` is denied (common for priv-app system APKs), fall back to the
   now-confirmed root channel:
   ```bash
   adb shell "xsu -c 'cat <path> > /sdcard/ayasettings.apk'"
   adb pull /sdcard/ayasettings.apk
   ```
   `pm path` may list multiple split APKs (base + config splits) — `base.apk`
   has the actual business logic.

2. **Decompile.**
   ```bash
   jadx -d ayasettings_decompiled ayasettings.apk
   ```
   (`jadx` is already installed on this machine, confirmed v1.5.6.)

3. **Orient first: read `ayasettings_decompiled/resources/AndroidManifest.xml`**
   before touching any Java — full component map (Activities/Services/
   Receivers/Providers), permissions, what's exported.

4. **Grep for the mechanism, don't read linearly.** Known starting search
   terms (from earlier handoffs, not yet actually checked against real
   decompiled source):
   ```bash
   grep -rl "SwitchPerformanceModeFragment\|CpuFragment\|PServerBinder\|xsud\|RootShell\|scaling_max_freq\|kgsl\|cooling_device\|fan" ayasettings_decompiled/sources/
   ```
   Follow whatever those files call into (ViewModel / Repository / a Shell
   wrapper class) until reaching the actual privileged action (a shell
   command string, a Binder call, a direct sysfs write).

5. **Write findings into `FINDINGS.md`** in this directory as you go (create
   it fresh — none exists yet). For anything that changes what `apl`'s
   `XsuShell.kt` should look like, or adds a new sysfs node to
   `apl-diag/docs/HARDWARE_PROFILE.md`, update those documents too, same as
   every other research thread in this project.

6. **Keep only curated excerpts in git**, not the full decompiled tree or
   the APK itself (`.gitignore` already excludes both) — copy in just the
   specific `.java` file(s) that reveal the mechanism, as evidence,
   mirroring how `xsu-capability-probe/results/` keeps curated raw data
   rather than entire build outputs.

## Known risk / dead end to expect

If the app is obfuscated (ProGuard/R8), tracing gets much harder (a/b/c
class names). If the real mechanism lives in native code (a `.so`), `jadx`
won't show it at all — would need a different tool (Ghidra/IDA), out of
scope for a first pass. Given everything else on this device has turned out
to be simple shell/sysfs based, this is considered unlikely but not
confirmed.
