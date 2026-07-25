# pulse-for-aya

A glue patch of upstream `pulse` (`github.com/keiretrogaming/pulse`, GPL-2.0)
onto this AYANEO Pocket FIT, built after `research/pulse-glue-assessment`
concluded the port was a narrow root-transport swap, not a rewrite. Forked
from upstream commit `0d2893e67cee0497e3fe624237679d104dd9c472` (2026-07-05),
copied in (no shared git history — see that folder's README for why the
upstream clone itself stays a separate, gitignored reference). Upstream's
own README/docs/licenses are preserved unchanged alongside this file; see
`research/pulse-upstream/README.md` for the pristine original.

**Status (2026-07-25): builds clean, installs, launches, live CPU/GPU/thermal
telemetry confirmed working over `xsu` on-device.** Actuation (AutoTDP
actually writing frequencies) not yet exercised — needs the one-time
"Usage access" grant (Settings → Apps → Special app access → Usage access →
Pulse → Allow), which the app's own onboarding flow correctly prompts for.

## What's patched vs upstream (see `pulse-glue-assessment/FINDINGS.md` for the
full reasoning behind each)

- **`root/RootExec.kt`** — rewritten to shell out via `ProcessBuilder("xsu",
  "-c", cmd)` instead of `ServiceManager.getService("PServerBinder")` +
  `binder.transact(...)` (PServer doesn't exist on this device). Same
  `executeAsRoot(cmd): Result<String?>` signature and `pServerAvailable`
  property upstream's ~80 other files already depend on — nothing else
  changed. `pServerAvailable` is cached at the class level (a `xsu -c "id"`
  probe) rather than re-probed on every `RootExec()` construction, since
  `RootSupport.runRootCommand` constructs a fresh instance per call and a
  process-spawn probe on every one of those would double the cost of every
  root command.
- **`root/RootSupport.kt`** — `runGeneratedScript` simplified to call
  `runRootCommand(scriptContents)` directly instead of writing a
  world-readable/-executable file to app storage first. That dance existed
  because PServer ran scripts as root from a different UID and had to read
  the file off disk; `xsu -c` takes the script text directly as its
  argument, so the file (and the local-exposure risk that came with making
  it world-readable) is gone.
- **`data/FanController.kt`** — `setMode()`, `ensureManualMode()`, and
  `customFanAvailable()` stubbed to no-ops/`false`. Confirmed on-device
  (2026-07-25) that neither of upstream's two fan mechanisms (the
  `gpio5_pwm2` PWM path, and the `Settings.System fan_mode` key read by
  `com.odin.settings`) exist here — native AyaSettings owns fan control on
  this device and does it well; the plan is a dedicated AIDL-based fan loop
  later (`research/aidl-bind-spike`), not this port. Stubbed at this single
  choke point rather than editing every call site, since several call sites
  wrote `fan_mode` unconditionally (not gated behind `customFanAvailable()`)
  — see `pulse-glue-assessment/FINDINGS.md`'s "Important gap" section.
- **RGB (`data/RgbController.kt`) — NOT patched, left as upstream.**
  Confirmed on-device (`xsu -c "settings get system
  joystick_led_light_picker_color"` → `null`) that its vendor key doesn't
  exist here either, and unlike fan, every RGB write site already checks
  `available()` first — it self-gates off safely with no changes needed.
- **`SG8350P` `DeviceProfile` entry — NOT added.** This SoC still falls
  through `DeviceProfiles.forSoc()` to `UNKNOWN` (Smart fan-release
  semantics unused here anyway, no Odin power tuning, standard 60/90/120
  fps targets) — a real, working, conservative profile. Adding a precise
  entry is deferred until the open empirical questions (is the prime
  cluster vendor-floored here? does the firmware honor the Game Mode fps
  cap?) are answered, per `pulse-glue-assessment/FINDINGS.md`.

## Not yet exercised / open

- AutoTDP's actual write path (CPU/GPU frequency actuation) — needs Usage
  Access granted once, then a supervised first run per the risk assessment
  in `pulse-glue-assessment/FINDINGS.md` (watch logs + temperature, don't
  background it unattended yet).
- The `sleep/SleepProfileMonitorService` package — not read during the
  glue assessment, unknown whether it needs any patching.
- No A/B comparison against native AyaSettings run yet.

## Build / install

```bash
cd research/pulse-for-aya
echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > local.properties  # gitignored, per-machine
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.kei.pulse/.MainActivity
```
