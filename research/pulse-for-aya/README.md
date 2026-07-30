# pulse-for-aya

A glue patch of upstream `pulse` (`github.com/keiretrogaming/pulse`, GPL-2.0)
onto this AYANEO Pocket FIT, built after `research/pulse-glue-assessment`
concluded the port was a narrow root-transport swap, not a rewrite. Forked
from upstream commit `0d2893e67cee0497e3fe624237679d104dd9c472` (2026-07-05),
copied in (no shared git history — see that folder's README for why the
upstream clone itself stays a separate, gitignored reference). Upstream's
own README/docs/licenses are preserved unchanged alongside this file; see
`research/pulse-upstream/README.md` for the pristine original.

**Status (2026-07-29): AutoTDP, manual tiers, per-app profiles, and live
telemetry all confirmed working on real hardware** — see "Feature parity vs
upstream" below for the full checklist. **Fan control is the one remaining
functional gap for full 1:1 parity**, and now has a validated, no-root path
forward (see that section) instead of being a dead end.

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

## AIDL migration, step 1 (2026-07-27) — mitigation for the `xsud` crash, not a fix

Follow-up to `STATUS.md`'s Minecraft-crash investigation (governor choice
and `aggressivePark` both ruled out; root cause is a real stack-overflow
bug in the vendor's `xsud` binary, most likely triggered by bursts of
*concurrent* `xsu` connections from multiple actors — `pulse-for-aya`
itself, `ab-logger`, and confirmed-not-ours processes, most likely
AYASpace's own native "game launch" hooks — all hitting `xsu` in the same
narrow window when a game comes to foreground). Since `xsud` and
AYASpace's own footprint are both outside this repo's control, the only
lever we have is reducing `pulse-for-aya`'s *own* contribution to that
connection burst.

`research/aidl-bind-spike` already proved (on this exact hardware, from a
different app) that `com.ayaneo.gamewindow`'s `AyaAidlService` can be
driven by any app with a plain Binder call — no `xsu`, no ~100ms-per-call
floor. Reading `com.ayaneo.settings`'s own decompiled source
(`research/ayaspace-teardown/evidence/performance/PerformanceViewModel.java`)
turned up more than the whole-profile switch already known about — the
native "Custom" profile editor (per-core frequency, GPU cap, CPU
scheduler, fan mode) is *also* pure AIDL, no sysfs writes on the
`ayasettings` side at all:

```
com_set_performance_mode:<0-4>            # Eco/Balanced/Streaming/Gaming/Max
com_set_performance_scheduler:<mode>      # POWER_SAVING / BALANCED / HIGH_PERFORMANCE
com_set_performance_cpu:<cpuId>_<freqKHz> # one physical core (0-7) at a time
com_set_performance_gpu:<freqKHz>
com_set_performance_gpu_is_fixed:<bool>
com_set_performance_fan:<FAN_MODE_*>
com_set_performance_reset:<0-4>
```

If these hold up on-device, most of what `PerformanceCommandBuilder`/
`GovernorController` do today via `xsu`'s `chmod`+`echo` dance could go
through this instead — leaving `xsu` only for the things AIDL can't do
(continuous FPS/thermal/frequency *reads* for AutoTDP's live loop; AIDL is
set-only). Reduces `pulse-for-aya`'s own connection-burst contribution,
doesn't touch `xsud`'s actual bug.

**What landed this session** (`app/src/main/java/com/kei/pulse/aidl/AyaAidlClient.kt`,
new file): a clean port of `aidl-bind-spike`'s proven wire protocol, plus
typed `send*()` methods for every command above. **Not wired into any live
control path** — `ForegroundAppMonitorService.kt` (2048 lines, carefully
tuned, already has one documented governor-related bug fixed in it) is
deliberately untouched this step. Two debug-build-only hooks in
`MainActivity.kt`:

1. `verifyAyaAidlBindOnDebugBuild()` — binds + registers on every
   debug-build launch, Toasts/logs the `clientId` or the failure reason.
   Never changes device state, safe to run automatically.
2. `maybeRunSchedulerSendTest()` — the one thing that DOES change device
   state (`com_set_performance_scheduler:BALANCED`, then reads back
   `policy0/scaling_governor` via `xsu` to confirm). Gated behind a marker
   file (`/sdcard/apl_test_aidl_scheduler.txt`) the tester creates
   deliberately over their existing root shell — never fires just because
   the app launched.

Builds clean (`./gradlew assembleDebug`), unit tests pass, **not yet run
on-device**. Expected result if this works: readback shows `schedutil`
(this SoC's native `BALANCED` governor, already confirmed in
`diagnostics/docs/HARDWARE_PROFILE.md`).

**Confirmed on-device (2026-07-27)**: it works, first try —

```
D AidlVerify: AIDL bind OK, clientId=84156219
D AidlVerify: sendScheduler(BALANCED) result=Success(kotlin.Unit)
D AidlVerify: scheduler test: send=Success(kotlin.Unit) readback(policy0 governor)=schedutil
```

`com_set_performance_scheduler` genuinely works from `pulse-for-aya`'s own
signed/packaged context, not just from `aidl-bind-spike`'s throwaway app —
the governor actually changed, confirmed by an independent `xsu` readback,
zero `xsu` calls needed to *set* it. This is the first real evidence the
mitigation direction is sound, not just plausible on paper.

**Extended (2026-07-27, same day): `sendCpuFrequency`/`sendGpuFrequency`
verify hooks added**, same self-restoring pattern as the scheduler test
(read original → apply test value → readback → restore original →
readback again, no reliance on `com_set_performance_reset`):

- `maybeRunCpuFrequencyTest()`, marker `/sdcard/apl_test_aidl_cpu.txt` —
  sends `sendCpuFrequency(0, 787200)`, reads back `policy0/scaling_max_freq`.
- `maybeRunGpuFrequencyTest()`, marker `/sdcard/apl_test_aidl_gpu.txt` —
  sends `sendGpuFrequency(366000000)`, reads back `kgsl-3d0/devfreq/max_freq`
  (the node `AyaDevicesUtil$applyGPUFrequency$1` is confirmed to write, per
  `aya-gamewindows-teardown/FINDINGS.md` section 2 — **not** `max_pwrlevel`,
  a different, index-based node).

Both test values are raw units already observed live in real `ab-logger`
captures on this device (known-valid, not guessed) — CPU in `scaling_max_freq`'s
KHz convention, GPU in `kgsl`'s Hz convention (the GPU AIDL unit itself is
**unconfirmed** — the "GPU Limit" slider in AYASpace's own UI displays
truncated MHz like `231`-`1050`, unclear whether that's cosmetic display
truncation or the actual wire value; this test is exactly what will answer
that). Builds clean, **not yet run on-device**.

**Confirmed on-device (2026-07-27)**: `sendScheduler` and `sendGpuFrequency`
both work cleanly. `sendCpuFrequency` is more nuanced — first test
(`cpuId=0`, shares `policy0` with `cpu1`) sent successfully (no exception)
but the value never actually changed, read back from both the
policy-level and per-cpu nodes. Hypothesis: cores that share a real
hardware frequency domain (a cpufreq policy) can't be set independently
via this AIDL command, one at a time. **Confirmed correct** — a second
test against `cpuId=7` (the *sole* member of `policy7`, a genuinely
independent domain) worked perfectly:

```
cpu7 freq test: original=3052800 before=[policy7=3052800 cpu7=3302400]
  send(787200)=Success after=[policy7=787200 cpu7=787200]
  restore(3052800)=Success after=[policy7=3052800 cpu7=3052800]
```

(Also note `policy7`/`cpu7` disagreed *before* the test even ran —
confirms these two sysfs paths genuinely aren't a plain symlink pair on
this kernel, not just a theoretical concern.)

**So: `com_set_performance_cpu` works for `policy7` (single-core cluster)
today; `policy0`/`policy2`/`policy5` (multi-core clusters) are no-ops when
sent one `cpuId` at a time.**

**Update, same day: sending all constituent `cpuId`s of a shared policy
together DOES unlock the write — but only in one direction.** Tested
`cpu0`+`cpu1` (both `policy0`) back-to-back with the same target:

```
cpu group freq test: original=2265600 before=[policy0=2265600 cpu0=2265600 cpu1=2265600]
  send(787200)=[Success,Success] after=[policy0=787200 cpu0=787200 cpu1=787200]   -- worked
  restore(2265600)=[Success,Success] after=[policy0=787200 cpu0=787200 cpu1=787200] -- did NOT take effect
```

Lowering (2265600→787200) worked cleanly for both cores at once. Raising
back (787200→2265600) with the exact same call pattern did **not** —
`cpu0`/`cpu1` stayed capped at 787200, live on the device, until manually
fixed with a plain `xsu` write (`chmod 666` → `echo 2265600` to all three
paths → `chmod 644`, confirmed restored via readback). Not yet understood
whether this is a real asymmetry in the command (down safer/easier than
up, receiver-side) or an unlucky race between two near-simultaneous Binder
calls that happened to land badly only on the second attempt — the
lowering test used the identical two-calls-back-to-back pattern and
worked fine. **Not investigated further this session** — the device was
left in a degraded-but-safe state (787MHz is a normal, safe operating
point, just a real performance limitation, not a risk) until the user
manually restored it via `xsu`.

**Practical conclusion for step 2**: even where `com_set_performance_cpu`
technically works (single-core policies directly, multi-core policies via
a synchronized full-group send), it's demonstrably less reliable than
`sendScheduler`/`sendGpuFrequency` (both of which round-tripped cleanly in
both directions, no exceptions). Don't lean on it for anything
safety-relevant without a confirmed, reliable restore path — worth
revisiting with more care (staggered timing, confirm-then-retry logic) if
step 2 ever wants to lean on it, but not blocking: the crash-mitigation
goal only needs `sendScheduler`/`sendGpuFrequency` at the foreground-change
moment, not per-core control.

`sendGpuFixed`/`sendFanMode` still unverified — fan control specifically
needs the same care as any fan-control work per `CLAUDE.md`'s hard rule,
see `STATUS.md`'s fan-control discussion before pursuing that one. Once
CPU/GPU frequency are confirmed, step 2 (replacing
`PerformanceCommandBuilder`'s `xsu`-based writes with these AIDL sends in
the live control path) can be scoped for real.

## AIDL migration, step 2 (2026-07-27) — tried, confirmed not to help, REVERTED

**Reverted (2026-07-27), same day.** After the on-device evidence below
confirmed `com.ayaneo.gamewindow`'s AIDL receiver shells out through
`xsu` itself (and does so with MORE connections than our own code would
for the same write), the user chose to revert to the simplest correct
implementation (KISS) rather than keep unproven complexity in the live
control path. `ForegroundAppMonitorService` is back to calling
`GovernorController.setGovernor(policies, BALANCED)` directly via `xsu`
on AutoTDP engage, exactly as before this step — no AIDL bind, no
fallback branch, no `PulseAidl` logging in this file. `AyaAidlClient.kt`
and `MainActivity`'s debug-only verification hooks are left in place as
validated research tooling (they're what proved this migration doesn't
help), just no longer wired into the live path. History below kept for
the record — this is *why* the strategy was abandoned, not a currently-
active design.

Replaces the one `xsu`-based governor write that fires on the exact
foreground-change moment `xsud`'s connection-burst crashes correlate with:
`ForegroundAppMonitorService.startAutoTdp()`'s "lock the Balanced governor"
step (previously `GovernorController.setGovernor(policies, BALANCED)`,
straight to `xsu`) now goes through a new helper,
`setAutoTdpGovernorBalanced()`: try `AyaAidlClient.sendScheduler("BALANCED")`
first (zero `xsu` calls); if the AIDL bind isn't ready yet or the send
throws/fails, fall back to the exact same `xsu` write as before. AutoTDP
can never end up without Balanced set — the fallback is unconditional, not
best-effort.

`ForegroundAppMonitorService` now owns and binds its own `AyaAidlClient`
for its whole lifetime (`onCreate`/`onDestroy`), separate from
`MainActivity`'s debug-only verification hooks (left as-is; each bind gets
its own `clientId` from `AyaAidlService`, so the two don't conflict, just
duplicate a small amount of setup on debug builds). A `@Volatile aidlReady`
flag flips once `bind()`'s async callback actually reports
`BindResult.Ready` — until then (and on any `Failed`), every foreground
change transparently uses the old `xsu` path, so there's no window where
AutoTDP's engage step silently does nothing.

**Deliberately NOT touched**: the exit-side restore calls
(`governorController.setGovernorRaw(...)`, three call sites — the
GOVERNOR LEAK fix, `restoreSnapshot`, and the scope-commit path) all
restore an arbitrary *raw* kernel governor name captured before the game
started (could be anything present on the device, not just this app's own
three options) — `com_set_performance_scheduler` only accepts the fixed
`POWER_SAVING`/`BALANCED`/`HIGH_PERFORMANCE` enum, so mapping a captured
raw name onto it isn't safe without more work. Scoped out for this step;
`startAutoTdp`'s engage write is the one that actually collides with the
foreground-change burst this mitigation targets, so it's the meaningful
win on its own.

Builds clean (`./gradlew assembleDebug testDebugUnitTest`).

`setAutoTdpGovernorBalanced()` logs which path fired on every engage
(`Log.d("PulseAidl", "engage governor via AIDL")` or `"...via xsu
fallback (aidlReady=...)"`) — added specifically so a `logcat` capture
from the reproduction test can confirm which path actually ran at each
foreground change, not just infer it from the crash timing alone.

**Major finding (2026-07-27), from decompiled `aya-gamewindows-teardown`
source: AYA's own AIDL receiver likely does NOT bypass `xsu` on this SoC
either — it may just relocate which process opens the connection.**
Traced the full call chain for our device family (`AyaDevicesKt`'s device
selector falls through to the default `AR03` class for any codename not
in its explicit list — `PocketFIT` isn't one of the 16 named ones, so
`AR03` is the very likely branch in effect, not yet confirmed live):

```
AyaDevicesUtil$applyCPUFrequencies$1 / applyCPUSchedulerMode$1 / applyGPUFrequency$1
  → AyaDevicesKt.f4814a.b(str)          ("echo ... > /sys/...")
  → AR03.b(str)   (research/aya-gamewindows-teardown/.../ar03/AR03.java:583-586)
  → TcRootShell.a(str)   (.../ar03/TcRootShell.java) → CmdUtilKt.e("xsu " + str)
  → Runtime.getRuntime().exec(...)   (.../utils/shell/CmdUtilKt.java:128-155)
```

Confirmed this exact chain for all three commands we care about
(scheduler, CPU freq, GPU freq) — every one ends in a fresh
`Runtime.exec("xsu ...")` per call, same one-process-per-call pattern as
our own `RootExec.kt`. No caching, no persistent shell, no JNI shortcut
(that only exists on the unrelated MediaTek `AR01`/`KtRootShell` branch,
which uses `com.kingtop.shellcmd.ShellCmd`'s JNI `hsInvokeJni` instead of
IPC to `xsud` — not available to us on this Snapdragon device).

**CONFIRMED live (2026-07-27), same day**: log
`research/ab-logger/results/aidl_xsu_check.log`, captured via the existing
debug-only `maybeRunSchedulerSendTest()` hook (marker
`/sdcard/apl_test_aidl_scheduler.txt`). `sendScheduler("BALANCED")` fires
at 15:32:17.317; **192-225ms later**, four separate, freshly-spawned `xsu`
processes each run a BARE `echo schedutil > .../policyN/scaling_governor`
(N = 0, 2, 5, 7 — one connection per policy, no `chmod` wrapping at all):

```
15:32:17.509  xsu: echo schedutil > .../policy0/scaling_governor
15:32:17.522  xsu: echo schedutil > .../policy2/scaling_governor
15:32:17.532  xsu: echo schedutil > .../policy5/scaling_governor
15:32:17.542  xsu: echo schedutil > .../policy7/scaling_governor
```

This cannot be our own code: `maybeRunSchedulerSendTest()` only ever
issues 3 `xsu` calls in this whole test (marker check, marker delete, one
`cat .../policy0/scaling_governor` readback — logged 1.5s later at
15:32:18.975, clearly separate) and `GovernorController` always wraps
writes in `chmod 666; echo; chmod 644` combined into ONE call across all
policies, never 4 separate bare-echo calls. The shape (bare echo, one
policy per connection, zero chmod) is an exact match for
`AyaDevicesUtil$applyCPUSchedulerMode$1.java`'s own code, confirming
`com.ayaneo.gamewindow` genuinely opens its own fresh `xsu` connections in
direct response to our AIDL send.

**Conclusion, no longer just a hypothesis**: sending
`com_set_performance_scheduler`/`_cpu`/`_gpu` over AIDL does NOT remove
`xsu` load from the system — it relocates which process opens the
connection (`com.kei.pulse` → `com.ayaneo.gamewindow`), and even ADDS a
4-way fan-out (one `xsu` connection per cpufreq policy) where our own
code would have used a single combined connection. **The "migrate more
writes to AIDL to reduce `xsu` load" strategy is abandoned for this
device** — it was never going to reduce the crash-prone broker's total
connection count, and for the scheduler command specifically, the AIDL
path may create MORE `xsu` connections than doing it ourselves would
have. GPU cap and any further `com_set_performance_cpu` work should not
be pursued for this reason.

Also checked (2026-07-27) whether AYA has some hidden persistent-root-
channel trick we're missing, per the user's alternative architecture
question — **no evidence of one anywhere in either teardown**. Even
`TcRootShell`/`CmdUtilKt.e` (Qualcomm path) and `KtRootShell`/`ShellCmd`
(MediaTek path) both spawn a fresh process/JNI call per invocation, no
caching or reuse. AYA's own code doesn't solve this problem either — it
just has a cheaper primitive on MediaTek hardware we don't have here.

**On-device result (2026-07-27): AIDL path confirmed working, crash still
happens on baseline timing — this migration alone does not help.** Real
Minecraft-crash reproduction, host-side `adb logcat`, log filed to
`research/ab-logger/results/minecraft_crash_step2_test_restart.log`.
`PulseAidl: engage governor via AIDL` fired as designed (zero `xsu` for
this write), but the same `system_server`/`BatteryService$Led` crash
happened anyway, ~60.2s after Game Mode activation — squarely inside the
pre-existing 59s/73s/69s baseline band from `STATUS.md`, with the same
"one long gap then tightening" `xsud`-crash shape (3 crashes this time,
gaps 38.8s/10.5s, `system_server` 5.0s after the last one). Full detail
and the timing table: `STATUS.md`'s Minecraft-crash entry, "step 2's real
on-device test" update. **Confirms the risk flagged when step 2 was
scoped**: AYASpace's own foreground-change hooks keep piling onto the
same `xsu` channel regardless of what this app does, so shaving off one
of our own calls doesn't reduce the pile-up enough to matter. The AIDL
plumbing itself is still validated and reusable (e.g. for the GPU cap
write next), but this specific mitigation goal — stopping the crash by
reducing our own footprint — is not confirmed to work and shouldn't be
assumed to, going forward.

## Per-app profile testing session (2026-07-28, night) — four findings

User exercised the per-app binding feature for real: RetroArch (GBA), Mario
Odyssey on Eden (Switch emulation), Minecraft, and `retrohrai` (the
frontend/launcher). Raw evidence:
`research/ab-logger/results/per_app_profile_test/round1_2026-07-28_2028_.../`
and `round2_2026-07-28_2035_.../`.

**1. RESOLVED — `com.miHoYo.Yuanshen` in the log was actually Eden, not
Genshin Impact.** Confirmed on-device: `dumpsys package com.miHoYo.Yuanshen`
showed `versionName=1f6734c` (a git-commit-hash style version, not a real
Genshin release number), `installerPackageName=com.google.android.
packageinstaller` (sideloaded, not Play Store), and the version string
matched exactly what Android's own Settings > Apps shows for the user's
installed Eden build. Eden (Switch emulator) ships under the
`com.miHoYo.Yuanshen` `applicationId` — plausibly deliberate camouflage,
a known pattern among post-Yuzu-lawsuit Switch emulator distributions
avoiding an easily-filterable package name. Not a PULSE bug: foreground
detection was correct the whole time, just reporting Eden's real (borrowed)
package identity. No further action needed.

**2. Custom tier has no per-app slider editor — confirmed, not a missing
UI hookup.** `PerAppScreen.kt`'s binding dialog lists `PowerTier.CUSTOM` as a
selectable chip (line ~349), but `PowerTier.CUSTOM`'s `cpuFactor`/
`gpuFactor` are unused placeholders (`PowerTier.kt`) — applying "Custom"
per-app actually calls `PerformanceRepository.restoreCustomValues()`, which
re-applies whatever the **one global** Custom frequency map was last saved
(`profileStorage.customValues`), edited only via the sliders on the main
Tuner screen (`TunerScreen.kt`, gated on `activeTier == PowerTier.CUSTOM`).
So today, every app bound to "Custom" shares the same single manual
frequency curve — there's no way to give two different games their own
distinct Custom setup. Real limitation, not a bug; worth deciding whether
per-app Custom maps are worth building before relying on this for more than
one game.

**3. Power Saving tier unstable in RetroArch (Super Wario Land 4, ~55-60fps
instead of locked 60).** `PowerTier.POWER_SAVING` (`cpuFactor=0.55`,
`gpuFactor=0.45`, see `PowerTier.kt`) is deliberately the most restrictive
tier — this looks like expected trade-off behavior (some real GBA titles are
light but not zero-cost to emulate at 0.55x clock ceiling) rather than a
bug. Noted for future tuning if Power Saving needs to stay usable for
emulation, not just idle/menu use.

**4. AutoTDP regulates Minecraft correctly but loses the target on Eden —
extends the still-open Eden thread in `STATUS.md`.** User confirms AAA/Max
(static, no AutoTDP) runs the same Eden/Mario session "super smoothly" —
useful data point: it's specifically the *AutoTDP loop* that mishandles
this workload, not the SoC/hardware. In the captured log
(`round1.../pulse_20260728_202800.log`), a ~90s AutoTDP stretch pinned
`fps≈30` against `tgt=90` despite continuous `RAISE` decisions pushing caps
toward 100% — target never reached. Can't yet tell if this is Eden being
genuinely GPU/emulation-bound (AutoTDP correctly maxed out and still
couldn't hit 90) or a **package-attribution gap**: the regulation log line
(`ForegroundAppMonitorService.kt:1682`, the `tgt=...` telemetry line) never
prints which package it's regulating — only the separate `TICK-SKIP`/
`AUTOTDP-SESSION` lines show `boundPackage`, and those go silent for the
whole time AutoTDP is actively running. **Cheap fix, do first**: add the
bound package name to every `tgt=` line, so the next Eden session shows
definitively whether AutoTDP stayed attached to Eden the whole time or drifted.

**5. AAA/Max ran ~2min at max CPU clock, hit ~70°C, fan never ramped —
expected, not a red flag.** `PowerTier.MAX` pairs `cpuFactor=1.0` with the
real `performance` cpufreq governor (`SystemTuning.kt` `OPTIONS`,
`GovernorOption("Performance", listOf("performance"))`) — this pins cores at
their max allowed frequency continuously, it does not itself decide how hot
the SoC gets. Actual power draw still tracks real switching activity
(workload), not just the frequency ceiling — a lightly-threaded 2D title
sitting at max clock on 1-2 active cores draws far less than the same clock
ceiling under full multi-core load, so 70°C sustained for a light game is
unsurprising and well under typical Snapdragon throttle thresholds.
Separately: PULSE's own fan control is a confirmed no-op on this device
(`customFanAvailable(): Boolean = false`, `pulse-glue-assessment/
FINDINGS.md`) — whatever the fan does (or doesn't do) is entirely the
vendor firmware's own curve, uninfluenced by which PULSE tier is active.
**Caveat**: this isn't a thermal guarantee for heavier titles — `performance`
never backs off preemptively, so a sustained CPU/GPU-bound 3D game at
AAA/Max would rely purely on the SoC's own hardware throttling, not
anything PULSE-side, to avoid overheating.

## Feature parity vs upstream `pulse` (2026-07-29)

Honest checklist against upstream's own feature list (its `README.md`,
"Features" section) — not a claim of "done," a record of what's actually
been confirmed working on **this** hardware vs. what's still open, so the
gap to a real 1:1 port is visible at a glance.

**Confirmed working, live on real hardware:**
- **AutoTDP** — closed-loop CPU/GPU regulation, real TRIM/RAISE cycles
  observed under genuine load, zero `xsu` fallback for a full clean
  session (`STATUS.md`, "VERIFIED ON-DEVICE" entries).
- **Manual tiers / per-cluster CPU caps / GPU cap** — the same sysfs
  mechanics AutoTDP already proves out; no separate doubt here.
- **Per-app profiles** — real session testing across RetroArch, Eden,
  Minecraft, `retrohrai` (`STATUS.md`, "Per-app profile testing").
- **Live telemetry HUD/OSD** — CPU/GPU/thermal readings confirmed over
  `xsu`/the FIFO daemon.
- **Quick Settings tile, autostart, themes** — inherited byte-identical
  from upstream (see "What's patched" above — none of these files were
  touched), no device-specific dependency in any of them, no reason to
  doubt they work; not yet independently exercised in a dedicated test
  pass.

**Confirmed a real, currently unaddressed gap:**
- **Fan control — the big one, discrete mode done, curve control path
  found and confirmed working (2026-07-30), implementation not yet
  built.** `FanController.kt` is still fully stubbed in code
  (`setMode()` always `false`, `customFanAvailable()` always `false`) —
  upstream's two fan mechanisms (`gpio5_pwm2` PWM, `Settings.System
  fan_mode`) are confirmed dead on this device
  (`pulse-glue-assessment/FINDINGS.md`). **Discrete fan-mode control
  (OFF/MUTE/BALANCE/TURBO) is confirmed working live, no root** —
  `com_set_performance_fan` over the same no-root AIDL channel already
  proven for performance-mode switching, verified two independent ways
  (real PWM duty changes, AND the vendor's own unsolicited state callback
  echoing the exact mode back) — see `research/aidl-fan-spike/FINDINGS.md`.
  **The full curve write: AIDL is a confirmed dead end, but raw sysfs is
  confirmed working.** `com_set_fan_speed_strategy` is genuine dead code
  (proven by reading the dispatch bytecode,
  `research/aya-gamewindows-teardown/FINDINGS.md` section 9; one
  crash-triggering guess required a device reboot, INCIDENT #4,
  `STATUS.md`), but the plain `hwmon0/pwm1`/`fan_power_state` sysfs write
  works once unlocked with the same `chmod 666` pattern this fork already
  uses for CPU/GPU (`PerformanceCommandBuilder.kt`) — live, audible,
  RPM-confirmed (2961→4780). **This means a real
  PI-controller/spline-curve port (`FanCurve.kt`/`FanTempController.kt`,
  which are pure math models with no I/O of their own and are portable
  as-is) is achievable on this device.** The vendor daemon's reassert
  cadence (does it fight a sustained controller?) was measured precisely
  (17 samples, 1-112s range, ~10s reassert loop preempts it 94% of the
  time) — no open questions remain. **Ready to build**, not yet started.
  See `research/aidl-fan-spike/FINDINGS.md` for the full trail.
- **RGB — smaller, same shape of gap.** Upstream's own RGB mechanism
  (`joystick_led_light_picker_color`) is confirmed dead here too, but
  self-gates off safely (no crash, just inert — `RgbController.kt` is
  untouched, byte-identical to upstream, no patch was even needed to stay
  safe). A real AYANEO-native RGB mechanism exists and was already found
  (`RgbManager`/`RgbUtil`, `Settings.System` keys under `ayaneo/share/*`,
  `research/aya-gamewindows-teardown/FINDINGS.md` section 5) but isn't
  wired into `pulse-for-aya` — smaller scope than fan (RGB is cosmetic,
  not safety-relevant), deferred behind fan.

**Genuinely unknown, never checked:**
- The `sleep/SleepProfileMonitorService` package — not read during the
  glue assessment, unknown whether it needs any patching to work
  correctly on this device.
- No formal A/B comparison against native AyaSettings run yet (informal
  comparisons exist scattered through `STATUS.md`'s per-app testing
  entries, but nothing structured).

**Bottom line**: fan control is the last *big* piece — closing it gets
AutoTDP + manual control + profiles + telemetry + fan all genuinely
working, which is the overwhelming majority of what a user actually
touches day to day. RGB and the sleep-monitor unknown are real but small
remaining items, worth closing before calling this a true 1:1 port, not
before calling it *usable*.

## Fan control — discrete mode CLOSED (works), curve control UNBLOCKED (2026-07-29/30): AIDL is dead, raw sysfs works

`research/aidl-fan-spike/` (same shape as `research/aidl-bind-spike/`) has
now been run on-device four times by the user, followed by a manual sysfs
write investigation that was initially wrong and corrected the same day.
Full detail in that project's `FINDINGS.md`:

- **Discrete mode (`com_set_performance_fan:<mode>`) — confirmed working,
  strong evidence.** Real PWM duty tracked the requested mode (OFF→0,
  MUTE→76 perfectly repeatable across all runs), AND — independently —
  gamewindow's own unsolicited state callback echoed the exact mode back,
  every single send, zero misses. Not a fluke. **Ship this in
  `FanController.kt` today.**
- **The real curve write — AIDL is dead, raw sysfs works.**
  - **AIDL** (`com_set_fan_speed_strategy`): 8 attempts across runs 2-4
    with 4 different string-format guesses never once moved duty near the
    expected value, and one guess (dropping the mandatory
    `FAN_MODE_CUSTOM-` prefix) crashed `com.ayaneo.gamewindow` twice via
    an unvalidated `FAN_MODE.valueOf(...)` call, requiring a device
    reboot (INCIDENT #4, `STATUS.md`). The *why* is settled for good:
    `research/aya-gamewindows-teardown/FINDINGS.md` section 9 shows the
    handler splits the payload, parses the mode, and **only logs** the
    rest — no write, no persistence, no hardware effect, for any format.
    `FanViewModel.java` (AYA Settings' own native curve editor) sends the
    identical command through the identical channel — meaning AYA's own
    UI likely doesn't apply the curve either. **This channel is closed,
    not worth revisiting.**
  - **Raw sysfs** (`hwmon0/pwm1` + `fan_power_state`,
    `research/aya-gamewindows-teardown/FINDINGS.md` section 6): an
    initial by-hand test looked blocked (`Permission denied` even under
    confirmed genuine root with SELinux Permissive) — but that
    investigation missed a step already established elsewhere in this
    codebase. `PerformanceCommandBuilder.kt` already unlocks CPU/GPU
    sysfs nodes with `chmod 666` before writing; the same trick, never
    tried on the fan nodes, fixed it immediately. **Confirmed live, same
    day**: `chmod 666` + write moved RPM from ~2960 to 4780, user
    independently heard the fan spin up. The vendor's own fan daemon
    reasserted the old value on its own, confirming a reassert loop
    exists here too, same class of behavior `pulse`'s own fan-reassert
    logic was built to fight on the Odin — **precisely measured
    afterward** (see below), not just observed once.

**Reassert cadence measured (2026-07-30, later the same day)**: two small
on-device scripts (`research/aidl-fan-spike/scripts/fan_reassert_probe*.sh`)
logged duty at 1s resolution across 9 runs, 17 write→reassert
measurements: **range 1-112s, mean ≈50s, median 54s, no fixed period.**
Every reassert corrected to exactly duty=76. Sending `FAN_MODE_CUSTOM` via
AIDL first made no measurable difference. Only 1 of 17 measurements fell
under 10 seconds — a **~10s periodic reassert loop** in the real
controller would preempt the vendor's correction 94% of the time, far
lighter than upstream `pulse`'s 120ms Odin-reassert loop.

**Practical upshot**: discrete fan-mode toggle for `FanController.kt` is
ready to ship. **A real PI-controller/spline-curve replacement
(`FanTempController.kt`/`FanCurve.kt`) is achievable, not closed** — those
files are pure math/state models with no I/O of their own
(`pulse-glue-assessment/FINDINGS.md`), so they're portable as-is; the
remaining work is a device-specific I/O layer (chmod-unlock + ~10s
reassert loop writing to `hwmon0/pwm1`/`fan_power_state`). **No open
questions remain — ready to design and build.** Not yet started, deferred
to a later session per the user's request. See
`research/aidl-fan-spike/FINDINGS.md` for the complete trail.

## Build / install

```bash
cd research/pulse-for-aya
echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > local.properties  # gitignored, per-machine
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.kei.pulse/.MainActivity
```
