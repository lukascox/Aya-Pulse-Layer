# STATUS

Living document — update this in place at the end of a working session,
commit with a descriptive message. Do not create a new dated/versioned copy
of this file; `git log` is the history.

Remote: `git.internal.example/cox/AyaPulseLite` (Forgejo, self-hosted).

## INCIDENT #3 (2026-07-26): empty-CSV bug recurs, then device powers off entirely, `BatteryService` left stuck

Two `ab-logger`-only sessions run back-to-back for the native-vs-`pulse-for-aya`
A/B comparison (`pl.ablogger.app`, no game actually reached foreground):
`session_1785093262984.csv` (PULSE not installed) and
`session_1785093666795.csv` (PULSE installed) — both saved raw to
`diagnostics/logs/ab-comparison/`. **Both are 100% empty** — 48/48 samples
each, evenly spaced (~5.1s), but every single column (`foreground_pkg`, `fps`,
all CPU/GPU freq+governor, GPU busy%, all temps, fan, battery current/voltage)
is `?`/`n/a`/`n/a (no layer matched)` throughout. This is the exact symptom
already documented in INCIDENT #2 ("100% empty" CSVs) — **recurring
unchanged**, which means that incident's mitigation (combining `xsu` calls,
`LoggerSession.sampleOnce()`) did not fix the underlying cause. Both sessions
also ran only ~240s (4 min), short of `TESTING.md`'s 10-minute protocol — no
A/B conclusion about `pulse-for-aya` can be drawn from this data; the failure
is entirely in the telemetry-capture layer, before any real comparison could
happen.

**Immediately after, the device powered off entirely and came back with a
new symptom**: `com.android.settings`/system UI reported no battery data at
all (matches the user's report — "nie raportuje nawet baterii"). Diagnosed
live over `adb` right after it rebooted (uptime ~26 min at diagnosis time):

- `persist.sys.boot.reason.history`'s newest entry: `shutdown,userrequested`
  at unix time `1785093175` (2026-07-26 21:12:55 CEST) — same suspiciously
  "user requested" label already flagged as unconfirmed in INCIDENT #2's
  `reboot,userrequested`; nobody actually requested a shutdown.
- `/sys/fs/pstore/` is empty — **no kernel panic was recorded**, so this
  wasn't a hard kernel crash, consistent with a triggered/graceful shutdown
  path instead.
- `dumpsys battery` showed `present: false`, `level: 0`, `voltage: 0` —
  looked like a dead/disconnected battery.
- **But the raw kernel driver disagrees**: `cat
  /sys/class/power_supply/battery/uevent` (via `xsu`, needed since
  `/sys/class/power_supply/battery/<individual-attr>` reads gave "Permission
  denied" even as root — SELinux is permissive so this wasn't a MAC block,
  cause not fully explained) returned a fully healthy live reading:
  `PRESENT=1`, `CAPACITY=43`, `STATUS=Discharging`, `HEALTH=Good`,
  `VOLTAGE_NOW=7591153`. **The battery itself is fine.** `dmesg` shows
  `healthd` logging a transient `battery none chg=` right around the
  shutdown window, then recovering — the working theory is `system_server`'s
  `BatteryService` got stuck on that transient empty read and never
  resynced, independent of the (healthy) hardware state underneath.
- `dumpsys battery reset` (standard adb debug command, clears a forced
  override) did **not** fix the stuck `present: false` — so this isn't a
  simple debug-override artifact, it's a real stuck framework/HAL state.
  Not yet resolved on-device; likely fixable by briefly plugging in USB
  power (forces a fresh `uevent`) or a full manual power-cycle — not
  confirmed this session.

**This is the third incident in the same family** (INCIDENT #1: `xsud`
crash → `BatteryService$Led` HAL call → `system_server` crash; INCIDENT #2:
`xsu`-heavy dual-app session → full device reboot + empty CSVs; this one:
empty CSVs recur + a different system service, `BatteryService`, left
stuck post-reboot) — all three correlate with test sessions putting heavy
concurrent load on `xsu`/`xsud`. Treat this as a real, recurring reliability
ceiling of the current `xsu`-polling approach under sustained/repeated use,
not three unrelated one-off flukes.

**Decision, picked up next session**: do not resume real A/B test sessions
until the empty-CSV recurrence itself is root-caused — the isolation step
already planned in INCIDENT #2 (verify `xsu` survives sustained polling over
minutes, not just a single manual `id`/`cat` check) still hasn't been done
and is now confirmed necessary, not optional. Raw CSVs from this incident
kept as evidence in `diagnostics/logs/ab-comparison/` (not renamed into the
usual `native_runN`/`pulse_runN` layout — they contain no usable comparison
data, they're incident evidence).

## INCIDENT #2 (2026-07-25, later same day): full device reboot during PULSE + ab-logger + Eden

After the mitigation below, ran PULSE + `ab-logger` + Eden (Mario, ~1-2 min
play) — the device fully rebooted this time (not just `system_server`,
kernel uptime reset). `persist.sys.boot.reason.history` shows two reasons
in sequence: `reboot,rollback_staged_install` (12:20:40 — looks like a
routine Android staged-update rollback, plausibly unrelated) then
`reboot,userrequested` (12:24:47 — the one right before the test data).
Root cause of the second one **not confirmed** — logcat rotates on
reboot, and tombstones/pstore were not readable over adb (permission
denied) to check what preceded it.

**Separately, and more concerning**: the resulting CSVs from this session
and the one before it are **100% empty** (no foreground pkg, no CPU/GPU
values, everything blank) — three empty rows-worth across ~2+ minutes
each. Investigated whether this is a bug in the call-combining mitigation
below: **it isn't** — manually ran the exact same combined `xsu` command
by hand and it returned real data (422 lines, correct activity/layer/CPU
dump) on the first try. Ran it again moments later and got **"Permission
denied" on every single CPU/GPU sysfs read**, despite `xsu -c "id"`
reporting `uid=0` around the same time. Conclusion: root access itself is
intermittently unreliable on this device right now, independent of
anything in this repo's code — consistent with (and a more severe
manifestation of) the already-documented `xsud` crash-and-refork
behavior. Not yet root-caused.

**Decision, picked up next session**: isolate variables before resuming
full A/B testing — run `ab-logger` alone (no `pulse-for-aya`) during real
gameplay first, to check whether the reboot/root-flakiness happens
without PULSE's added concurrent `xsu` polling, or needs both. All test
apps (`pulse-for-aya`, `ab-logger`, `aidl-bind-spike`) uninstalled from
the device at the end of this session for a clean restart next time —
reinstall from each folder's own build/install instructions
(`README.md`/`TESTING.md`) when resuming.

## Bug (2026-07-25, same incident): `pulse-for-aya` falsely claimed "device not compatible"

Downstream of the `system_server` crash below: after Android auto-killed
and relaunched `com.kei.pulse`, its main screen showed a red "PSERVER
UNAVAILABLE" badge and upstream's stock fallback text, "Your device is
not compatible with this app" — while the HUD right next to it kept
showing live, correct CPU/GPU/fan/battery numbers. Not a real
incompatibility: `RootExec.pServerAvailable`'s one-time cached `xsu`
probe latched `false` after a single failed attempt (almost certainly
landing during the post-crash `xsud` instability) and never retried —
`executeAsRoot()` itself doesn't consult that cache, so real functionality
kept working throughout. **Fixed**: only latch `true`, never latch
`false` (matches `RgbController.available()`'s existing pattern in the
same codebase — that lesson was already learned once, just not applied
here). See `research/pulse-for-aya/README.md`'s "Bug found" section.
Builds clean, not yet re-verified on-device.

## INCIDENT (2026-07-25): first real ab-logger session crashed `system_server`, device rebooted

Not a cosmetic bug — read this before running `ab-logger` again. First
real test session (logging started, app backgrounded, playing normally)
made the device appear to freeze for ~25-30s. `logcat` timeline:

- `xsud` (root daemon) `Fatal signal 6 (SIGABRT)` crash traces right as
  the app started/backgrounded (same pattern first noticed in this app's
  original smoke test, then under-weighted as "not confirmed harmful").
- ~10-13s later: `BLASTSyncEngine: ... Application ANR likely to follow`,
  then a confirmed **ANR in `com.android.systemui`**.
- ~18s after that: **`FATAL EXCEPTION IN SYSTEM PROCESS`** —
  `BatteryService$Led`'s charging-LED animation called into the `ILights`
  HAL, got back error `-13`, uncaught — **this crashes `system_server`
  itself**.
- Android auto-restarts `system_server` from scratch (full re-init of
  every system service, visible in logcat as dozens of "PackageWatchdog:
  ... INACTIVE -> PASSED" lines) — this restart, not an app hang, is what
  looked like the device freezing. Confirmed no actual reboot (kernel
  uptime continuous throughout); `com.kei.pulse`'s (`pulse-for-aya`)
  foreground service auto-restarted afterward along with everything else,
  confirming it was ALSO running/polling via `xsu` throughout the
  incident.

**Root cause not proven** (the crash site is vendor/AOSP `BatteryService`
code, unrelated to anything in this repo directly) — but the timing
isn't treated as coincidence: `ab-logger` was making 3-4 separate `xsu`
process spawns every 2 seconds, `pulse-for-aya` was doing its own
concurrent polling, and this device's `xsud` daemon is already known to
crash-and-refork on every connection close. Leading theory: sustained
dual-app `xsu` process-spawn churn created enough system load/binder
contention to starve a fragile, timing-sensitive HAL call that would
otherwise succeed. Elevates the earlier "xsud SIGABRT, not confirmed
harmful" note (see `pulse-glue-assessment/FINDINGS.md` risk section) —
that framing was too reassuring; treat repeated `xsud` crashes as a real
warning sign going forward, not a benign log line.

**Mitigation applied** (see `research/ab-logger/README.md`'s "Incident"
section for the full writeup): combined 3 of `LoggerSession`'s 4 `xsu`
calls per sample into one (down to 1-2 spawns/sample), raised the
sampling interval from 2s to 5s. Builds clean, **not yet re-verified
on-device** — the device was mid-restart by the user when this landed.
**Do not resume the A/B test series until a cautious, closely-observed
retest confirms this holds** — and consider testing `ab-logger` alone
first (without `pulse-for-aya` running) to isolate whether dual-app
concurrent `xsu` load was actually necessary to trigger this, or whether
`ab-logger` alone is enough.

## `research/ab-logger/` built (2026-07-25) — minimal A/B telemetry recorder

New, purpose-built app: two buttons ("Start log"/"Stop log"), reusing
`autotdp-ab-harness`'s proven sampling pipeline
(`XsuShell`/`FpsPipeline`/`ThermalZones`/`PowerFanProbe`, unchanged) inside
a foreground service (survives backgrounding for a whole game session,
unlike the harness's Activity-scoped coroutine). Built instead of reusing
`autotdp-ab-harness` directly because that app's "Start AutoTDP" button
launches the old `pulse_lite_v3.7.sh` bash daemon — using it as-is to test
`pulse-for-aya` would run both controllers at once, fighting over the same
sysfs nodes. See `research/ab-logger/README.md` for the full reasoning and
`TESTING.md` for how to run/pull/clean up logs.

**Builds clean, installs, launches, smoke-tested on-device**: a session
ran for several minutes and produced a well-formed CSV (real CPU
governor/freq per policy, battery current/voltage — thermal zones came
back `n/a` this run, worth checking whether that's a one-off next real
session). Device cleaned up afterward (test files/CSV removed, service
stopped) — not yet used for an actual native-vs-`pulse-for-aya` A/B
comparison, that's the next concrete step and is the user's to run
(see `pulse-for-aya/TESTING.md`).

**Update (2026-07-25, same day): real fan RPM reading added.** The
smoke test's `fan_signal` column came back `not_found` — the generic
`cooling_device` discovery this app inherited doesn't find anything on
this device. `aya-gamewindows-teardown`'s pass 3 (a second session's
work, see its `FINDINGS.md` section 6) found and confirmed live the
actual mechanism: a plain `pwm-fan` hwmon node,
`/sys/devices/platform/soc/soc:pwm-fan/fan_rpm_state`, readable even
without root. `LoggerService`/`LoggerSession` now try that confirmed
path first (falling back to the old generic search if absent), so the
CSV's fan column will carry a real RPM number instead of `n/a` on the
next session. Read-only — doesn't touch `pulse-for-aya`'s still-
deliberately-stubbed `FanController.kt` or add any new actuation; write
access to this fan node remains a separate, unconfirmed question. Now
builds clean; not yet re-verified on-device (left for the user's own
test run rather than another smoke test).

**Two things noticed during that smoke test, folded into
`diagnostics/docs/HARDWARE_PROFILE.md`, not blocking**:
- Repeated `xsud` (the on-device root daemon, a vendor binary) `Fatal
  signal 6 (SIGABRT)` crash traces during rapid app-switching moments
  (opening `ab-logger`, its permission dialog opening) — not reproduced
  during a steady 12s polling window or a single isolated `xsu -c "id"`
  call, and every individual command's result still came through
  correctly (a fresh `xsud` worker handled the next call each time). Most
  likely a per-connection worker crashing on cleanup under rapid/
  concurrent access (both `pulse-for-aya`'s background service and
  `ab-logger` were hitting `xsu` around the same moments this run) — not
  confirmed harmful, but new and undocumented before this session. Worth
  watching for during real, longer A/B sessions.
- Also briefly hit a transient `/sdcard` "Transport endpoint is not
  connected" error (a few seconds, self-resolved, not reproduced since) —
  noted in case it recurs, not treated as a real problem on one occurrence.
- Confirmed, incidentally: the device's real fan PWM node is
  `/sys/devices/platform/soc/soc:pwm-fan/hwmon/hwmon0/pwm1` (standard
  Linux hwmon, not Odin's `gpio5_pwm2`), and this ROM runs SELinux in
  permissive mode (`setenforce 0` observed, `avc: denied ...
  permissive=1` throughout logs). Both purely observational — nothing in
  this repo writes to that fan node or touches SELinux state.

## Repo merge

**Merged in (2026-07-25)**: the formerly-separate `apl-diag`
(`AyaPulseDiag`) repo is now `diagnostics/` in this repo, not a sibling —
see "Repo merge" section below and `diagnostics/README.md` for why. That
repo's own STATUS.md is retired; its content is folded into this section.

## `diagnostics/` (folded in from `apl-diag`, 2026-07-25)

Raw hardware facts and the validated FPS/telemetry measurement script —
see `diagnostics/README.md` and `diagnostics/docs/HARDWARE_PROFILE.md` for
full detail. Carried over, still true, don't re-litigate:

- Foreground-app detection: `dumpsys activity activities | grep
  topResumedActivity=` (not `mCurrentFocus`/`mResumedActivity` — confirmed
  to return nothing on this Android 14 build).
- SurfaceFlinger layer selection needs the 4-tier priority search (BLAST >
  plain SurfaceView > last non-helper match > old fallback) — this took
  four buggy iterations (v4-v7) to get right, already solved, don't redo it.
- `dumpsys gfxinfo` is unreliable for native SurfaceView renderers
  (RetroArch/Eden/Dolphin) — returns 0 frames or frozen/cached stats.
- GPU busy signal: raw `kgsl-3d0/gpubusy`, not `gpubusypercentage`
  (confirmed broken on this kernel) — but the raw counter itself
  wraps/resets between reads, so no controller should trust a single read.
- CPU cores directly observed at 87-96°C with no throttling response from
  AYASpace's Gaming mode — thermal safety is this project's own
  responsibility to design for, not something the vendor firmware is
  confirmed to handle.
- Full CPU OPP tables (per-policy `scaling_available_frequencies`, 18-32
  steps per policy) and the AIDL-sourced per-mode config (fan mode, GPU
  min/max, per-core CPU caps for all 5 AYASpace modes) are both captured in
  `diagnostics/docs/HARDWARE_PROFILE.md` — this is the reference to check
  before assuming a frequency/fan-mode fact needs re-measuring.

**Known open items carried over, still open**: the AYASpace
write-conflict question (what happens if a controller and AYASpace write
the same sysfs node simultaneously — relevant now to the vendor-daemon-
contention risk noted in `pulse-glue-assessment/FINDINGS.md`) was never
directly tested; the "zero span" FPS edge case (~17% of Eden samples) is
low-priority and fails safe.

## `research/pulse-for-aya/` exists now (2026-07-25) — first buildable glue port

Acted on the assessment below: forked `pulse-upstream` (commit
`0d2893e67c`) into `research/pulse-for-aya/`, patched `RootExec.kt`/
`RootSupport.kt` to run through `xsu` instead of `PServerBinder`, stubbed
`FanController`'s writes to no-ops (fan stays fully native-AyaSettings-
owned), left RGB untouched (confirmed self-gating). **Builds clean
(`./gradlew assembleDebug`), installs, launches on the AYANEO Pocket
FIT, and is already reading live CPU/GPU/thermal telemetry over `xsu`
with no crashes** — screenshot showed real values (CPU 3302MHz, GPU
231MHz, 36°C/33°C, 3.3W draw) and a green "PSERVER · LINKED · NO-ROOT"
status badge (cosmetic label from upstream, semantically now means "xsu
probe succeeded"). See `research/pulse-for-aya/README.md` for the exact
patch list and reasoning.

**Not yet exercised**: AutoTDP's actual actuation path (writing CPU/GPU
frequencies) — toggling it on in the UI correctly triggered the app's own
onboarding flow (redirected to Android's Usage Access settings screen,
since that permission isn't granted yet) rather than crashing or silently
failing. Granting that permission and doing a supervised first
AutoTDP run is the next concrete step, followed by an A/B comparison vs
native AyaSettings (see the pulse-glue-assessment FINDINGS.md follow-up
for the proposed protocol, adapted from `research/autotdp-ab-harness`'s
existing paired-session design).

## PLAN FOR NEXT SESSION (2026-07-25 end of day)

**`research/pulse-glue-assessment` reconnaissance is now closed out** (see
that folder's `FINDINGS.md`, "Follow-up pass (2026-07-25)" section) — all
four items from the previous session's "not yet checked" list were
resolved via a full-module `grep` (not just the original 8-file sample)
plus targeted reads of the actual control-loop files
(`AutoTuneController.kt`, `ForegroundAppMonitorService.kt`'s fan-reassert
loop):

- `RootExec`'s choke point is confirmed at the whole-module level — every
  caller across all ~80 files goes through `RootSupport.runRootCommand`/
  `runGeneratedScript`, nothing else touches `PServerBinder`/
  `ServiceManager`. The glue patch really is just rewriting
  `RootExec.executeAsRoot()`'s ~15-line body to shell out via `xsu`
  instead, reusing the already-proven `XsuShell.kt` pattern.
- `minSdk 31`/`targetSdk 34`/manifest permissions — no conflict, ordinary
  app permissions, nothing assumes root/system context.
- **Risk found this pass, then resolved on-device the same day**: the
  fan-reassert loop re-checks live fan duty every `FAN_RECHECK_MS = 120L`ms
  via a root `cat` call — flagged as a possible problem for `xsu`'s
  fork+exec overhead (vs the cheap native Binder call it was tuned
  against). Tested directly on the AYANEO Pocket FIT: `xsu -c "cat
  /sys/class/gpio5_pwm2/duty"` returns empty. Tracing `pulse`'s own gating
  logic (`FanController.customFanAvailable()` →
  `ForegroundAppMonitorService.isCustomFanSupported()`), empty output means
  this loop **never starts** on this device — the cadence question is moot
  for this mechanism specifically.
- **Bigger finding that surfaced from this**: both of `pulse`'s fan-control
  mechanisms (the custom PWM duty curve AND the discrete Silent/Smart/Sport
  modes via `settings put system fan_mode`, read by the AYN vendor app
  `com.odin.settings`) are vendor-specific to AYN Odin/RP6/Thor and almost
  certainly inert on AYANEO hardware. Fan control isn't a "glue the root
  transport" problem like CPU/GPU/display/RGB/AutoTDP — it needs to be
  built separately, most likely on top of the already-proven AIDL bind to
  `com.ayaneo.gamewindow` (which already returns fan mode in its
  whole-profile callback JSON, per `aidl-bind-spike/FINDINGS.md`). See
  `pulse-glue-assessment/FINDINGS.md`'s "On-device confirmation" and "Risk
  assessment" sections for the full writeup, including the other
  (non-fan) risk categories assessed this session (CPU/GPU write safety,
  vendor-daemon contention, display/settings recoverability, the
  world-readable script file).
- **Follow-up, same session: the discrete `fan_mode` writes are NOT gated
  the way the PWM loop is.** Several call sites in
  `ForegroundAppMonitorService.kt` (AutoTDP start, per-app profile apply,
  master-OFF/revert-to-stock, snapshot restore) call
  `fanController.setMode(...)` unconditionally — i.e. `settings put system
  fan_mode <N>` fires regardless of whether Custom/PWM fan is supported.
  Neither AYANEO teardown (`ayaspace-teardown`, `aya-gamewindows-teardown`)
  found any `Settings.System` key involved in AYANEO's own fan control
  (it's all AIDL-based there), suggesting this key is orphaned/unread on
  our device and these writes are no-ops — **not directly verified**.
  Stated intent: keep relying on native AyaSettings' fan control for now,
  do AIDL-based fan control as separate later work — so the plan is to
  explicitly strip/no-op every `fanController.setMode()`/
  `ensureManualMode()` call site in the fork (not just rely on the
  existing `customFanAvailable()` gate), guaranteeing zero fan-touching
  root calls from the ported app instead of assuming the Settings key is
  dead. See FINDINGS.md's "Important gap" subsection for the full call-site
  list and the cheap on-device check (`settings get system fan_mode`
  before/after toggling fan mode in native AyaSettings) that would confirm
  the key really is orphaned, if we want certainty instead of inference.
- **Follow-up, same session: RGB, autostart, and the Quick Settings tile
  all checked clean.** Unlike fan, `RgbController`'s writes ARE properly
  gated everywhere (`if (!available()) return` before every write) and its
  vendor key is `com.ro.*`-specific (AYN/Retroid), almost certainly absent
  on AYANEO — should self-disable safely, no stripping needed (one
  on-device sanity check recommended, not required).
  `BootCompletedReceiver`/`MainActivity`'s permission onboarding
  (`PACKAGE_USAGE_STATS`, overlay) and the `PerformanceTileService` QS tile
  are all stock Android APIs with no AYN-specific assumptions found. The
  glue scope for a first build is confirmed as: CPU/GPU/AutoTDP/display/
  refresh-rate/per-app-profiles/HUD-overlay/QS-tile/boot-autostart — with
  fan control stripped/deferred (see above) as the only carve-out. One
  gap: the `sleep/SleepProfileMonitorService` package hasn't been read yet
  — see `pulse-glue-assessment/FINDINGS.md`'s latest follow-up section for
  the full writeup.

Writing the actual patch (fork `pulse-upstream`, replace `RootExec.kt`, add
the `SG8350P` `DeviceProfile` entry, strip the fan-mode call sites, and
separately scope AIDL-based fan control) is intentionally deferred to a
later session — this session was findings-only.

## PLAN FROM PREVIOUS SESSION (2026-07-24 end of day)

**Two major findings that session, in order of how much they change the plan:**

### 1. `research/pulse-glue-assessment` — maybe don't write `apl/app/` from scratch at all

Upstream `pulse` (`github.com/keiretrogaming/pulse`, GPL-2.0, cloned to
`research/pulse-upstream/` — gitignored, re-clone with `git clone
https://github.com/keiretrogaming/pulse.git research/pulse-upstream` if
needed) already implements AutoTDP, fan curve, HUD overlay, RGB, per-app
profiles, Quick Settings tile — mature, tested on real AYN/Retroid hardware.
Its only blocker for our device is its root mechanism (`PServerBinder`, not
available here). Reconnaissance (8 files read, see
`research/pulse-glue-assessment/FINDINGS.md` + curated `evidence/`)
strongly suggests **this is a narrow, clean substitution, not a rewrite**:

- The ENTIRE `PServerBinder` dependency is one ~25-line file
  (`root/RootExec.kt`) behind a single choke point
  (`RootSupport.runRootCommand`/`runGeneratedScript`) — everything else
  (CPU/GPU tuning, FPS reading, fan curve) only knows `RootExec`'s
  `executeAsRoot(cmd): Result<String?>` signature, not `PServerBinder`
  itself. Swapping this file for an `xsu -c` call (we already have this
  exact code, proven, in every probe's `XsuShell.kt`) is the glue.
- CPU/GPU detection (`CpuPolicyDetector.kt`, `GpuFreqDetector.kt`) is
  already fully dynamic/generic (reads `scaling_available_frequencies`,
  `kgsl-3d0/gpu_available_frequencies`, etc. at runtime) — zero per-SoC
  hardcoding, will work on our device unchanged.
- The one per-device gate (`model/DeviceProfiles.kt`, keyed by
  `ro.soc.model`) already falls back gracefully to a working `UNKNOWN`
  profile for our SG8350P (not in its table yet) — adding a proper entry
  is a small quality refinement, not a blocker.
- **Bonus, independent of the glue decision**: `data/FpsReader.kt` uses
  `dumpsys SurfaceFlinger --timestats` (global present-cadence histogram),
  NOT `--latency` + layer-matching — sidesteps the whole "which layer is
  real" problem that took `pulse_lite_diag_v8.sh` four iterations (v4-v7)
  to solve. Worth adopting regardless of the glue decision.

**Before writing any glue code**, `FINDINGS.md`'s "What's NOT yet checked"
list is the next session's first task: confirm `RootExec`'s public surface
is really only used the one way assumed, read the actual control-loop
files (`AutoTuneController.kt`, `PowerModel.kt`, `FanCurveController.kt`),
check the manifest/permissions and `minSdk` (may conflict with `apl/app/`'s
current placeholder `minSdk 26`).

### 2. `research/aidl-bind-spike` — CONFIRMED on real hardware

A plain, non-system app can flip AYASpace's performance profile (Gaming ↔
Eco, verified via governor + `scaling_cur_freq` read-back, repeated 3x
reliably) over a bare Binder connection to `com.ayaneo.gamewindow` — no
`xsu`, no root, no per-call ~100ms floor. Full per-mode config (CPU
per-core caps, fan mode, GPU frequency range) also came back live in the
callback JSON — resolved the long-standing "Gaming vs Max identical?"
mystery (answer: GPU cap only, 834MHz vs 1050MHz/uncapped) — see
`research/aidl-bind-spike/FINDINGS.md` and the updated table in
`diagnostics/docs/HARDWARE_PROFILE.md`. This remains relevant regardless of
the glue decision above — it's a faster/safer path than `xsu` specifically
for whole-profile switches, whether that code lives in a `pulse` fork or a
from-scratch `apl/app/`.

## Next session, in priority order

1. **Write the glue patch**: reconnaissance is fully closed out
   (module-wide, not just sampled) — fork `pulse-upstream`, replace
   `RootExec.executeAsRoot()` with an `xsu`-backed implementation (reuse
   `XsuShell.kt`'s pattern), add the `SG8350P` `DeviceProfile` entry. Fan
   control is explicitly OUT of scope for this patch — confirmed on-device
   inert on AYANEO (see `pulse-glue-assessment/FINDINGS.md`'s "On-device
   confirmation" section) — track it as a separate follow-up (item 2
   below), don't try to glue it alongside the rest.
2. **Decide `apl/app/`'s actuation architecture**: AIDL-bind (now proven)
   vs `xsu` sysfs writes (also proven, but slower/riskier) — likely AIDL
   for whole-profile switches, `xsu` still needed for anything NOT covered
   by the AIDL command catalog (live FPS/temp/busy% reads — AIDL only
   exposes "set" commands, no monitoring). Applies whether this ends up
   inside a `pulse` fork or a from-scratch app.
3. **Scope and build the per-app profile-mimic feature** (assign Eco/
   Balanced/Streaming/Gaming/Max to specific apps, auto-applied via AIDL on
   foreground-app detection) — either as `apl/app/`'s first real feature
   from scratch, or as the first patch on top of a glued `pulse` fork,
   depending on step 1. Needs: a `ForegroundService` + `BootReceiver`
   (still just placeholders in `app/`), reuse of
   `FpsPipeline.parseForegroundPkg`-style detection (via `xsu`), and
   `AidlProtocol.kt`'s bind/register/send logic ported from the spike
   into production-quality code.
4. **Optional follow-up spike** (not blocking, only if step 3 needs it):
   test `com_set_performance_fan`/`com_set_performance_cpu` (fine-grained,
   not whole-mode) and the controller/key-mapping commands
   (`com_set_abxy_mode` etc.) — the aidl-bind-spike only exercised whole-mode
   switching so far.
5. Everything from before this session remains queued behind the above:
   A/B comparison sessions (`research/autotdp-ab-harness`), then
   `AutoTdpController` design informed by both that data and the ~100ms
   `xsu` floor (now less critical for actuation, still relevant for reads).

## Where things stand (this repo's first commit)

This repo was assembled by migrating scattered pre-git research into a
single structure — see `docs/archive/` for everything that predates this
commit, and `research/xsu-capability-probe/FINDINGS.md` for the specific
technical conclusions summarized below.

## Confirmed, don't re-litigate

- `xsu` is callable via `Runtime.exec()`/`ProcessBuilder("xsu", "-c", cmd)`
  from a normal installed app (debug and release both), giving `uid=0` /
  `context=u:r:xsud:s0`. This was the single biggest open risk carried from
  `docs/archive/xsu_handoff_2026-07-21.md` — now closed.
- CPU sysfs (`cpufreq/policy*/scaling_max_freq`) and GPU sysfs
  (`kgsl-3d0/max_pwrlevel`) writes both confirmed working through that
  channel, with read-back verification.
- The FPS pipeline (foreground app → SurfaceFlinger layer match →
  `--latency` FPS calc), originally built and validated in what's now the
  `diagnostics/` folder's shell script (formerly the separate `apl-diag`
  repo), is confirmed reachable the same way, across RetroArch/Eden/Dolphin
  (three distinct layer-naming conventions).
- The "stdin" `xsu` invocation method is broken (silent false positive,
  never diagnosed) — do not use it. Use the `args` method only.
- `pulse_lite`'s old tier/hysteresis/floor controller architecture (see
  `docs/archive/pulse_lite/v3.7/pulse_lite_v3.7_handoff.md`) already
  achieved the target outcome once (same FPS, meaningfully lower
  CPU/GPU/skin temps and fan RPM than stock) — but was driven by raw
  busy% signals, which turned out to have a structural ambiguity (can't
  tell "needs a higher clock" from "fenced waiting on GPU"). The plan is to
  port the tier/hysteresis/floor *architecture* into `AutoTdpController.kt`
  but drive it from measured FPS-vs-target delta instead of busy%.

## Known open risk, not yet resolved

A batched sysfs-read call stalled ~126 seconds once during heavy Dolphin
load (see FINDINGS.md's "Real failure mode" section) — root cause not
confirmed. `AutoTdpController`'s polling loop needs to treat "a call might
just not return for a long time under heavy load" as a real, recurring
condition, not a theoretical edge case.

## Not yet done

- `app/` is still the original placeholder skeleton (TODO-comment stubs,
  package renamed to `pl.ayapulselite.app`, nothing implemented).
- No `XsuShell.kt`/`AutoTdpController.kt` real implementation exists yet.
- Full `scaling_available_frequencies` OPP table per CPU policy — flagged
  as missing in `diagnostics`'s hardware profile at the time (later
  resolved, see "run2" below), still needed before a precise
  step-controller can be designed (currently only 4 discrete points per
  cluster are known).
- Test 6/7 from the probe's v2 spec (full CPU frequency table dump,
  governor write+verify) were coded but not yet run on-device before this
  migration — see FINDINGS.md.

## New: `research/autotdp-ab-harness/`

Second probe app, sibling to `xsu-capability-probe/`. Adds Test 8 (fan/
power node discovery — nothing hardcoded, logs what's found) and Test 9
(backgrounded-process persistence via `xsu` — a hard gate for the harness
below). Its actual point: an A/B comparison harness with two modes
(Baseline / AutoTDP) sharing one sampling loop (FPS + CPU/GPU/thermal/fan/
battery, one CSV row every 2s), where AutoTDP launches this repo's own
already-validated `docs/archive/pulse_lite/v3.7/pulse_lite_v3.7.sh` as a
background daemon and just observes — no controller logic is ported into
Kotlin here.
Resulting CSVs, once collected, should be copied into `diagnostics/logs/
ab-comparison/` — the code lives here for the Gradle/Kotlin scaffolding,
the data belongs there.

**run1 (2026-07-24):** Test 9 PASSED (backgrounded process persists), with
a quirk to expect again (launch call itself hits its timeout rather than
returning fast — daemon still starts regardless). Test 6 (CPU frequency
table) came back completely empty — fixed (split into one call per policy,
explicit raw-exec logging).

**run2 (2026-07-24):** Test 6 fix confirmed working — full CPU OPP table
captured and folded into `diagnostics`'s `HARDWARE_PROFILE.md` (this closes
the "still missing" item open since v6). Azahar (Citra 3DS fork) confirmed
as a fourth working app for the layer-matching heuristic. A new FPS
pipeline edge case found (idle-screen stale-buffer FPS decay, not a stall)
— see `xsu-capability-probe/FINDINGS.md`. **Phase 1 (Tests 1-9) is now
considered complete and validated** — both probe apps' capability
questions are answered; next work is Phase 2 (actual A/B comparison data).

## RESOLVED + major finding: `research/ayaspace-teardown/` + `research/aya-gamewindows-teardown/`

Both static-analysis passes complete (see each folder's own `FINDINGS.md`
for full evidence). Headline result, likely the most architecturally
consequential finding in the project so far:

**`com.ayaneo.gamewindow`'s `AyaAidlService` is `exported="true"` with no
`android:permission` and does zero caller-identity verification.** Any
installed app — no root, no `system` UID, no `xsu` — can bind to it
directly and drive `com_set_performance_mode` (Eco/Balanced/Streaming/
Gaming/Max), fan mode, GPU-fixed-frequency, RGB, and controller/key-mapping
commands, all through a plain Binder connection. `com.ayaneo.settings`
itself doesn't touch sysfs for the profile switch at all — it only relays
this same AIDL message; it doesn't need `xsu` because it runs
`sharedUserId=system`, a different app can't reuse that specific shortcut
but doesn't need to, since the AIDL service itself has no gate.

**CONFIRMED on-device, 2026-07-24** (see `research/aidl-bind-spike/FINDINGS.md`):
`apl` can skip `xsu` entirely for profile/fan/GPU-cap changes: no
~100ms-per-call floor, no risk of fighting AYASpace over the same sysfs
node (we ask gamewindow's own code to apply the change, not writing in
parallel), and fan control (previously an unconfirmable lever, likely
serial/EC-based) comes along for free — the AIDL callback delivers the
full per-mode config (fan mode, GPU frequency range, per-core CPU caps),
which also resolved the old "Gaming vs Max identical?" question (answer:
GPU max frequency cap only, 834MHz vs 1050MHz/uncapped — folded into
`diagnostics/docs/HARDWARE_PROFILE.md`). Bonus: the same command surface
covers controller/key remapping (`com_set_abxy_mode`, `com_set_l1l2r1r2_mode`,
`com_set_single_key_mapping`) — Module 2, deferred since the project's very
first README, might turn out to be nearly free, though not itself tested yet.

CPU/GPU sysfs mechanics (governor, per-core freq, `kgsl` max/idle-timer)
were independently reconfirmed at the command level in
`aya-gamewindows-teardown` and match what `apl` already knew — no changes
needed there regardless of which path (AIDL vs. own `xsu` writes) wins.

**`research/aidl-bind-spike/`** hand-rolls the undocumented Binder wire
protocol (no `.aidl` file exists, reconstructed from decompiled `Stub`/
`Proxy` classes) — two buttons, Gaming/Eco, each verified via the
already-proven `xsu` read-back. **Confirmed working, repeatably (3
mode-switches in one session, consistent each time).** Only whole-mode
switching tested so far — fine-grained `com_set_performance_fan`/`_cpu` and
the controller/key-mapping commands remain untested, not blocking.

## Next steps (rough priority order)

1. Run `research/aidl-bind-spike` on-device — see that project's own README
   for exact expected output for success and each distinct failure mode.
   This decides whether `apl`'s profile-mimic feature (and later
   `AutoTdpController`'s actuation side) uses AIDL-bind or `xsu` sysfs
   writes.
2. Scope and build the per-app profile-mimic feature (assign Eco/Balanced/
   Streaming/Gaming/Max to specific apps, auto-applied on foreground-app
   detection) — likely `apl/app/`'s first real (non-throwaway)
   functionality, architecture now depends on step 1's outcome.
3. Run actual Baseline vs AutoTDP sessions per game (see
   `research/autotdp-ab-harness`'s README test procedure — paired,
   order-swapped per game, NOT all
   baseline sessions then all autotdp sessions, which would confound mode
   with elapsed time/thermal carry-over). This is the "realistic input"
   needed before designing the FPS-delta-augmented v1 controller.
4. Decide `AutoTdpController`'s actual control signal and loop cadence,
   informed by the ~100ms-per-`xsu`-call floor documented in FINDINGS.md
   and by the A/B comparison data from step 3. Planned: selectable FPS
   target thresholds (60/120/144) once the core loop is stable — noted for
   later, not blocking current work.
4. Write the real `XsuShell.kt` (production quality, not probe quality) —
   the `args` method only, informed by the timeout/reliability findings.
5. First real increment per the KISS plan in README.md: a service that
   starts on boot and writes one confirmable log line.
