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
  root command. **Fixed (2026-07-25): only a `true` probe result gets
  latched** — a single transient probe failure used to poison this flag
  to `false` for the rest of the process's life (see "Bug found" below),
  even though `executeAsRoot()` itself doesn't consult this cache and kept
  working fine underneath. Same fix `RgbController.available()` already
  applies elsewhere in this codebase; missed here originally.
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

## Bug found (2026-07-25): false "Your device is not compatible with this app"

Observed on-device during A/B test prep: the main screen showed a red
"PSERVER UNAVAILABLE" badge and `TunerScreen.kt`'s stock upstream fallback
text, "Your device is not compatible with this app" — while the live HUD
right next to it kept showing real CPU/GPU/fan/battery numbers, correctly
updating. Contradictory on its face, and it was: `executeAsRoot()` (what
the HUD actually uses) doesn't read `pServerAvailable` at all, so it kept
working regardless.

Root cause: `pServerAvailable`'s one-time cached probe (see above) latched
`false` after a single failed attempt and never retried for the rest of
the process's life. This specific process incarnation of `com.kei.pulse`
had just been force-killed and relaunched by Android's own automatic
`system_server` restart (see `STATUS.md`'s "INCIDENT" entry) — landing
its first probe call during exactly the kind of moment this device's
`xsud` is known to crash-and-refork on a connection close. One bad
probe, latched forever, cosmetic-but-alarming message displayed
indefinitely even after the underlying device was completely fine again.
**Fixed**: only latch `true`; a failed probe just means "ask again next
time" (see `RootExec.kt`'s doc comment). Builds clean, not yet
re-verified on-device.

## Open question (2026-07-27): native FPS counter shows stale "Gaming Mode" label + `walt` governor mismatch

Raised by the user ahead of a new on-device test series, investigated via
code/docs only (no device access this session). Two related observations
about AYA's own FPS counter overlay (launched from AyaSettings, not part
of this app) while `pulse-for-aya` is active:

**1. Mode label stuck on "Gaming Mode" (blue) regardless of actual governor.**
Not yet directly confirmed — the overlay's own code hasn't been located in
either teardown (`aya-gamewindows-teardown`/`ayaspace-teardown`), that's a
real gap, not an oversight to re-derive from memory. But a plausible
mechanism follows from confirmed facts: `com.ayaneo.gamewindow`'s
`AyaAidlService` only updates its live `currentMode` state (and fires the
callback that presumably feeds this kind of UI) when something calls
`com_set_performance_mode` over AIDL (`aidl-bind-spike/FINDINGS.md`).
`pulse-for-aya` never calls that — it writes governor/frequency straight to
sysfs via `xsu` (`RootExec`/`RootSupport.runRootCommand`, see
`pulse-glue-assessment/FINDINGS.md`). If the FPS counter's mode label reads
`gamewindow`'s cached state rather than sysfs live, it would never learn
pulse changed anything — staying on whatever AYASpace itself last set
(explains "always shows Gaming Mode" exactly). **Not confirmed** — needs
either finding the overlay's actual code, or an on-device check: watch the
label while forcing a mode change through AYASpace itself vs. through pulse.

**2. `walt` governor: real, but not what native AYASpace uses on this device.**
Confirmed real cpufreq governor, present in `scaling_available_governors` on
all 4 policies (`diagnostics/docs/HARDWARE_PROFILE.md`). But
`aya-gamewindows-teardown/FINDINGS.md` (section 2) shows native AYASpace's
own Balanced-mode governor choice on *this* SoC is `schedutil` — its code
only picks `walt` for a different device-flag combination, not the Pocket
FIT. `SystemTuning.kt`'s `OPTIONS` list here picks `walt` first for
"Balanced" (comment: inherited from upstream Pulse's Odin 3 convention,
falls back to `schedutil`/`sched_pixel` if unavailable) — it happens to
also be present on this device's governor list, so pulse actually lands on
`walt`, not `schedutil`. **Not a bug** — both are real, valid governors —
but it means pulse's "Balanced" and AYASpace's own "Balanced" are
deliberately-different-by-inheritance, not equivalent, on this hardware.
Worth knowing when comparing behavior/telemetry between the two.

**Update (2026-07-27): changed as a diagnostic step for the Minecraft-crash
investigation.** Every crash reproduction in `STATUS.md`'s Minecraft thread
so far happened with PULSE's governor at `walt` — never confirmed as the
actual cause (still no `logcat` capture from a crash), but `walt` being a
governor AYASpace itself never exercises on this SoC in any of its 5 modes
makes it a reasonable first thing to rule out cheaply. `SystemTuning.kt`'s
`OPTIONS.Balanced` narrowed from `["walt", "schedutil", "sched_pixel"]` to
just `["schedutil"]` — matches AYASpace's own native Balanced-mode choice
on this device exactly. `Performance`/`Power Save` unchanged (already
matched AYASpace's `performance`/`powersave`). Builds clean, unit tests
pass, **not yet verified on-device** — next Minecraft repro attempt should
use this build and note whether the crash still happens with `schedutil`
active instead of `walt`.

**3. Disappearing per-core frequency readout — leading hypothesis: `aggressivePark`.**
Ruled out `PerformanceCommandBuilder`'s `chmod` locking (`444`) as the
cause — it only locks `scaling_max_freq`/`min_pwrlevel`, not
`scaling_cur_freq`, so that shouldn't block a read. The more likely
mechanism: `AutoTuneController`'s opt-in `aggressivePark` (default `off`)
offlines prime cores via `cpuN/online` — an offline core has no valid
`scaling_cur_freq` to report, which a HUD would plausibly render as blank.
This is the **same lever already flagged in `STATUS.md`'s open Minecraft
native-launch-failure investigation** — worth checking, in the same
upcoming session, whether `aggressivePark` was on during both symptoms; if
so it's likely one root cause, not two.

**Next-session check (cheap, no code change needed)**: while testing,
note (a) whether `aggressivePark` was enabled when core speeds vanished,
and (b) `cat .../scaling_governor` at a moment the FPS counter shows
"Gaming Mode", to confirm what governor is actually live vs. what the
label claims.

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
