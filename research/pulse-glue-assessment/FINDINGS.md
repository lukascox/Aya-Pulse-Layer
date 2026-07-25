# FINDINGS — is "glue, not rewrite" viable for `pulse`? (2026-07-24, follow-up 2026-07-25)

**Verdict: yes, confirmed — the root abstraction is exactly as clean as we
hoped, and CPU/GPU detection is already fully dynamic/generic (not
hardcoded per-SoC). The one place with real per-device gating is narrow,
optional, and already degrades gracefully for an unrecognized SoC — which
is exactly what our device is today.** This reframes `apl/app/` from "write
AutoTDP/fan-curve/overlay/profiles from scratch" to "write one small root
shim + one small device-profile entry, reuse the rest."

The 2026-07-24 pass was a reconnaissance sample (8 files read closely, not
the whole ~80-file `app/` module). The 2026-07-25 follow-up (see "Follow-up
pass" section below) closed out all four open items from that pass via a
full-module `grep` and targeted reads — the glue strategy is now confirmed
at the whole-module level, not just on the sampled files. One new risk
surfaced in this pass (fan-reassert loop cadence vs `xsu` overhead) — see
below. Writing the actual patch is still deferred to a later session.

## 1. The root mechanism is a single, narrow choke point

Package `com.kei.pulse.root/` (5 files, all read in full, curated in
`evidence/`):

- **`RootExec.kt`** — the ONLY file that touches `PServerBinder`. Looks it
  up via `ServiceManager.getService("PServerBinder")` reflection, gets an
  `IBinder`, and does `binder.transact(0, data, reply, 0)` with a simple
  `[command, "1"]` string-array payload, decoding the reply as a byte-array
  string. **This is the entire surface that needs replacing.**
- **`RootSupport.kt`** — a singleton wrapping `RootExec` with a
  `ReentrantLock` (PServer can't handle overlapping transacts) and
  `runGeneratedScript()`: writes a script to app-private storage, marks it
  world-readable+executable (`chmod`-equivalent via `File.setReadable/
  setExecutable`), then runs `sh <path>` through `runRootCommand()`. This
  world-readable dance exists specifically because PServer runs the script
  from a *different UID* and needs to be able to read it — **this whole
  mechanism becomes unnecessary with `xsu`**, since `xsu -c "<script>"` can
  just take the script text directly as its argument, no intermediate file
  needed (same pattern already used throughout this project's own probes).
- **`RootCommandRunner.kt`** — the class actually injected into the rest of
  the app; `executeScript(script)` on `Dispatchers.IO`, delegates to
  `RootSupport.runGeneratedScript()`. `isAvailable` just checks
  `rootExec.pServerAvailable`.
- **`PServerSysfsReader.kt`** — implements a generic `PrivilegedSysfsReader`
  interface (`readText(path): String?`) as `RootSupport.runRootCommand("cat
  '$escapedPath' 2>/dev/null")`. **Reads are abstracted behind an interface
  too**, not just writes.
- **`PerformanceCommandBuilder.kt`** — generates plain POSIX shell text
  (`chmod 666 $path; echo $value > $path; chmod $mode $path`) from generic
  `CpuPolicyInfo` objects (path, value, mode) — **zero AYN/Odin/Retroid-
  specific hardcoding in the command text itself.** Contains real, valuable
  domain logic worth preserving as-is (vendor-HAL fights over `scaling_min`
  reasserting the prime cluster's floor, GPU `min_pwrlevel` needing to widen
  before `max_pwrlevel` will actually bite) — this is exactly the kind of
  hard-won empirical knowledge we do NOT want to rediscover from scratch.

**What "glue" concretely means here**: replace `RootExec.kt`'s ~25 lines of
`PServerBinder` reflection with an `xsu -c` `ProcessBuilder` call (we
already have this exact code, tested and proven, in every probe in this
repo — `XsuShell.kt`). `RootSupport`/`RootCommandRunner`/
`PServerSysfsReader`/`PerformanceCommandBuilder` likely need zero changes —
they only know about `RootExec`'s `executeAsRoot(cmd): Result<String?>`
signature, not about `PServerBinder` specifically. (Not yet verified this
signature is `RootExec`'s only public surface used elsewhere — worth a
quick `grep -rn "RootExec("` on the next pass before assuming zero
call-site changes.)

## 2. CPU/GPU detection is already fully dynamic — no per-SoC hardcoding

- **`CpuPolicyDetector.kt`** — reads `/sys/devices/system/cpu/cpufreq/
  policy*` (generic Linux cpufreq), parses `scaling_available_frequencies`,
  `affected_cpus`/`related_cpus`, `cpuinfo_max_freq`, `scaling_min_freq`,
  builds a `CpuPolicyInfo` per policy dynamically. Zero hardcoded
  frequency tables or policy counts. This is functionally identical to
  what `apl-diag/docs/HARDWARE_PROFILE.md`'s Test 6 already confirmed for
  our exact device — it will detect our 4 policies (18/32/29/31 OPP steps)
  correctly with no changes.
- **`GpuFreqDetector.kt`** — reads `kgsl-3d0/gpu_available_frequencies`
  (falling back to `devfreq/available_frequencies`), computes the
  power-level-index math generically (ascending-frequency-table ↔
  descending-power-level mapping) — same `kgsl-3d0/max_pwrlevel` mechanism
  this project has used since `pulse_lite` v3.2. Zero hardcoding beyond the
  two candidate root paths tried in order.

Both would work on our AYANEO Pocket FIT with **no code changes** — they
already do exactly what our own `HwProfile.kt`/`ThermalZones.kt` do, just
independently arrived at from a different codebase for different (but
Snapdragon/Adreno) hardware.

## 3. The one real per-device gate — narrow and already degrades gracefully

**`model/DeviceProfiles.kt`** keys a `DeviceProfile` (fan-release mode,
whether the prime cluster is "vendor-floored", whether Game Mode fps cap is
honored, fps target list, Odin-specific power tuning flag) off
`ro.soc.model`:

```kotlin
fun forSoc(socModel: String?): DeviceProfile = when (socModel?.trim()?.uppercase()) {
    "CQ8725S" -> ODIN3
    "QCS8550" -> SD8GEN2
    else -> UNKNOWN
}
```

Our SoC (`SG8350P`, confirmed in `apl-diag/docs/HARDWARE_PROFILE.md`) isn't
in this table — **it already falls through to `UNKNOWN`**, a real,
functioning profile (Smart fan release, no Odin power tuning, standard
60/90/120 fps targets), not a crash or a block. Adding a proper `SG8350P`
entry is a small, low-risk, well-isolated addition once we've empirically
confirmed the few open questions the profile fields encode (is our prime
cluster vendor-floored like the Odin, or does it genuinely scale like the
SD 8 Gen 2? does our firmware honor `cmd game mode --fps`?) — not a
blocker to bring the app up at all, just a quality refinement.

`data/SocDetector.kt` (the thing that produces the `ro.soc.model` string
`DeviceProfiles.forSoc` consumes) is itself generic — reads
`ro.soc.model`/`ro.vendor.qti.soc_model`/`Build.SOC_MODEL`/`Build.HARDWARE`/
`Build.BOARD` in order, falls back gracefully. `FRIENDLY_NAMES` is purely
cosmetic display-name mapping, not a gate.

## 4. Bonus, unrelated to the glue question: their FPS method is better than ours

**`data/FpsReader.kt`** uses `dumpsys SurfaceFlinger --timestats`, not
`--latency` — a fundamentally different (and more robust) approach than
`pulse_lite_diag_v8.sh`'s (and our probes') layer-name-matching heuristic:

- Reads the **global `presentToPresent` histogram** (the display's actual
  present cadence) rather than one specific layer's stats — this
  sidesteps the ENTIRE "which layer is the real one" problem that took our
  own diagnostic script four buggy iterations (v4-v7) to solve, and that
  still needed a 4-tier priority-match heuristic even after fixing it.
- Falls back to the busiest layer's `averageFPS` only if the global block
  is empty — same safety net, cheaper primary path.
- Reduces the entire `dumpsys` dump to ONE line via an in-shell `awk`
  script before it ever leaves the root shell — much less data over the
  `xsu`/`PServerBinder` round trip than our raw `--latency` dumps.
- Also derives frame-pacing stability metrics (worst frame time, jank
  count) that our own pipeline doesn't currently compute at all.

**This is worth adopting regardless of whether "glue pulse wholesale"
happens** — it's a strictly better FPS-reading method than what
`apl-diag`'s validated script and this project's probes currently use, and
it doesn't require any of the layer-matching heuristic work we've already
sunk effort into. Flagged here for `apl-diag`/`xsu-capability-probe`'s
FINDINGS.md too, independent of the glue decision.

## License

GPL-2.0 (confirmed, `LICENSE` file at repo root). Personal fork/modification
for own-device use carries no copyleft obligation — GPL's share-source
requirement triggers on *distribution*, not personal use. If `apl` (or a
`pulse`-derived fork) is ever published for other AYANEO/handheld owners,
the fork would need to stay GPL-2.0-licensed with source available — a
known, simple constraint, not a blocker for a personal hobby project.

## Follow-up pass (2026-07-25): all four open items checked

- **`RootExec`'s public surface, confirmed module-wide.**
  `grep -rln "RootExec(\|RootCommandRunner(\|RootSupport\.\|PServerBinder\|ServiceManager" app/src`
  (whole tree, not just `root/`) returns exactly: `AppContainer.kt`,
  `appwatch/ForegroundAppMonitorService.kt`, `data/RgbController.kt`,
  `data/FanCurveController.kt`, `data/FrameLimiter.kt`, `data/FpsReader.kt`,
  `data/DisplayController.kt`, `data/SystemTuning.kt`,
  `data/FanController.kt`, `data/AutoTuneController.kt` — every hit is a
  call to `RootSupport.runRootCommand(cmd)` or `runGeneratedScript(...)`,
  never `RootExec`/`ServiceManager`/`PServerBinder` directly. Confirms the
  choke-point theory holds for the whole module, not just the 8 sampled
  files. Concretely, "glue" = rewrite `RootExec.executeAsRoot()`'s ~15-line
  body (currently `ServiceManager.getService("PServerBinder")` +
  `binder.transact(...)`) to shell out via `ProcessBuilder("xsu", "-c",
  cmd)` instead — same pattern already proven in this project's own
  `XsuShell.kt` (used in `xsu-capability-probe`, `autotdp-ab-harness`,
  `aidl-bind-spike`) — keeping the same `Result<String?>` signature.
  Nothing else in the ~80-file module needs to change for that swap alone.
- **Control-loop logic read.** `data/AutoTuneController.kt` and
  `appwatch/ForegroundAppMonitorService.kt` (the fan-reassert and per-app
  binding loops) were read in the relevant sections. Both only ever call
  `RootSupport.runRootCommand`/`runGeneratedScript` — never touch
  `PServerBinder` — consistent with the root-layer read. Notably,
  `model/PowerModel.kt`, `model/PowerTargetMath.kt`, `model/FanCurve.kt`,
  and `model/FanTempController.kt` do **not** appear in the grep hit list
  at all — they're pure math/state models with no I/O of their own, a
  clean separation that makes the glue even lower-risk (the domain logic
  worth preserving doesn't touch the root layer even indirectly).
- **`ui/`/`overlay`/`tile` packages** — not read in full, but the
  whole-tree grep above confirms none of them reference
  `RootExec`/`PServerBinder`/`ServiceManager` directly either. The
  root-mechanism question is answered; UI-level review (if ever needed) is
  a separate, lower-stakes task.
- **`minSdk`/manifest, confirmed no conflict.** This clone's
  `app/build.gradle.kts`: `compileSdk 34`, `minSdk 31`, `targetSdk 34`.
  Not a real constraint — our AYANEO Pocket FIT runs Android 13/14, well
  above 31; `apl/app/`'s own placeholder `minSdk 26` is irrelevant here
  since the glue plan is to fork `pulse-upstream`'s own module, not merge
  into the existing skeleton. `AndroidManifest.xml` requests only ordinary
  permissions (`RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`(`_SPECIAL_USE`),
  `POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW` for the overlay,
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) — nothing that assumes a
  rooted/system context, and no manifest entry is needed for `xsu` itself
  (it's invoked as an external binary via `ProcessBuilder`, same as every
  probe in this repo).

## New risk found this pass: fan-reassert loop cadence vs `xsu` overhead

`ForegroundAppMonitorService.kt`'s fan-reassert loop
(`ensureFanReassertLoop()`) re-checks the live fan duty node every
`FAN_RECHECK_MS = 120L` milliseconds while a Custom fan profile is active —
each tick calls `FanCurveController.readDutyFromDevice()`, which is a
`RootSupport.runRootCommand("cat ...")` call, i.e. one root round-trip
every 120ms, continuously, for as long as the profile is active. The
in-code comment explains why: the Odin vendor daemon re-pins fan duty to
~50% on foreground-app transitions even in manual passthrough, and this
loop exists specifically to catch and override that within one cheap tick
— "cheap" on a native Binder call (`PServerBinder`), which is what this
loop was designed and tuned against.

Over `xsu`, each call is a fork+exec of a subprocess, not a Binder
transact. This project's own findings (`STATUS.md`) document a ~100ms
per-call floor for `xsu` and one observed 126-second stall under heavy
device load — both uncomfortably close to a 120ms budget if the loop tries
to keep pace continuously. This is the single biggest open technical
question for the glue, surfaced by actually reading the control loop
rather than just the root layer:

- Not yet known whether this device's firmware even re-pins fan duty on
  foreground transitions the way the Odin's does — the same comment notes
  the reconcile is already a no-op on RP6/Thor where nothing re-pins; if
  ours is also a no-op, the 120ms tick still costs one `xsu` round-trip
  every tick even though the write itself never fires, which is still an
  overhead question, just a cheaper one (read-only, no write contention).
- Needs an on-device measurement pass before committing to porting this
  loop as-is: does a 120ms-cadence `xsu` `cat` loop keep up without
  drifting/backing up, and does sustained fork+exec at that rate cause any
  side effects (CPU overhead, the known stall risk recurring more often
  under load)?
- Mitigations if it doesn't keep up, in rough preference order: (1) confirm
  the reconcile is a no-op on our SoC and skip the read entirely when so;
  (2) widen `FAN_RECHECK_MS` (trades a slightly more audible rev-catch
  window for headroom); (3) explore whether fan duty could be read via the
  already-proven AIDL bind to `com.ayaneo.gamewindow` instead of `xsu` —
  unconfirmed whether that surface exposes a continuous/pollable duty read
  at all (the aidl-bind-spike only exercised discrete whole-mode "set"
  commands, not polling), so likely not a drop-in fix, but worth a quick
  check before assuming it's ruled out.

## On-device confirmation (2026-07-25): fan control doesn't port at all, not just a cadence risk

Ran directly on the AYANEO Pocket FIT:

```
PocketFIT:/ $ xsu -c "cat /sys/class/gpio5_pwm2/duty"
PocketFIT:/ $
```

Empty output. Tracing what that means in `pulse`'s own code:
`FanController.customFanAvailable()` ([`FanController.kt:119-122`](../pulse-upstream/app/src/main/java/com/kei/pulse/data/FanController.kt:119))
does exactly this `cat` and returns `false` on empty/unparseable output. That
return value gates `isCustomFanSupported()` in
`ForegroundAppMonitorService.kt`, whose caller returns early at
[line 873](../pulse-upstream/app/src/main/java/com/kei/pulse/appwatch/ForegroundAppMonitorService.kt:873)
(`if (!isCustomFanSupported()) return false`) — `customFanRunning` is never
set to `true`, so `ensureFanReassertLoop()` (the 120ms loop from the
previous risk section) **never starts on this device**. The cadence-vs-`xsu`
question above is moot for this specific mechanism — not because it was
solved, but because the feature gates itself off before that loop ever
runs, exactly as `DeviceProfiles.forSoc()` gates off gracefully elsewhere.

**Bigger finding from reading the rest of `FanController.kt`**: the
*discrete* fan modes (Silent/Smart/Sport, `fan_mode` values 1/4/5) go
through `settings put system fan_mode $mode` — a `Settings.System` key read
by `com.odin.settings` (the AYN vendor fan-control app), not a generic
Android mechanism. That's Odin-specific in the same way `gpio5_pwm2` is.
**Both of `pulse`'s fan-control paths (discrete stock modes AND the custom
PWM duty curve) are very likely vendor-specific to AYN Odin/RP6/Thor and
inert on AYANEO hardware** — this isn't a transport-layer "glue" problem
like `RootExec`, it's a missing feature. Fan control on our device would
need to be routed entirely differently: through the already-confirmed AIDL
bind to `com.ayaneo.gamewindow` (which returns fan mode as part of its
whole-profile callback JSON per `aidl-bind-spike/FINDINGS.md`), as a
separate, scoped piece of work — not a drop-in replacement inside
`FanController.kt`/`FanCurveController.kt`. Not yet confirmed: whether
`settings get system fan_mode` also returns nothing on this device (same
test, one command, not yet run) — worth doing to fully close this out, but
the PWM result alone is enough to downgrade "port pulse's fan control" from
"small glue" to "separate feature, deferred."

### Important gap: the discrete `fan_mode` writes are NOT gated by `customFanAvailable()`

`isCustomFanSupported()` only gates the PWM-duty reassert loop. Reading the
rest of `ForegroundAppMonitorService.kt` finds several **unconditional**
calls to `fanController.setMode(...)` (i.e. `settings put system fan_mode
$mode`) that run regardless of whether Custom/PWM fan is available at all:

- [`ForegroundAppMonitorService.kt:1453`](../pulse-upstream/app/src/main/java/com/kei/pulse/appwatch/ForegroundAppMonitorService.kt:1453)
  — starting AutoTDP for a foregrounded app forces `SMART` unless the user
  has Custom fan active.
- [`ForegroundAppMonitorService.kt:1887`](../pulse-upstream/app/src/main/java/com/kei/pulse/appwatch/ForegroundAppMonitorService.kt:1887)
  — applying a per-app profile that has a `fanMode` assigned.
- [`ForegroundAppMonitorService.kt:1913`](../pulse-upstream/app/src/main/java/com/kei/pulse/appwatch/ForegroundAppMonitorService.kt:1913)
  — master-OFF / revert-to-stock forces `SMART`.
- [`ForegroundAppMonitorService.kt:1935`](../pulse-upstream/app/src/main/java/com/kei/pulse/appwatch/ForegroundAppMonitorService.kt:1935)
  — restoring a saved snapshot.

So even with the PWM loop confirmed dead, a ported-as-is `pulse` would
still issue `settings put system fan_mode <1|4|5|6>` writes through `xsu`
at these moments. **Whether that's harmless depends entirely on whether
anything on AYANEO reads that `Settings.System` key.** Neither
`ayaspace-teardown/FINDINGS.md` nor `aya-gamewindows-teardown/FINDINGS.md`
mentions any `Settings.System` key for fan control at all — AYANEO's own
fan control appears to live entirely behind AIDL commands
(`com_set_performance_fan` / whole-profile config), not a Settings
provider — which suggests this key is orphaned/unread on this device and
these writes are no-ops. **Not directly verified.** Cheap on-device check
that would confirm it: `settings get system fan_mode` before/after
toggling fan mode in the native AyaSettings UI — if the value never
changes and AyaSettings' own fan behavior is unaffected by writing to it
manually, the key is confirmed dead.

**Decision for the glue patch, given the stated intent to keep relying on
native AyaSettings' fan control for now and do AIDL-based fan work as a
separate later project**: don't rely on the "probably orphaned key"
assumption — explicitly strip or no-op every `fanController.setMode()`/
`ensureManualMode()` call site in the fork (the four listed above, plus
whatever else `grep -n "fanController\."` turns up at patch time), not
just leave the existing `customFanAvailable()` gate as the only protection.
That guarantees the ported app makes zero fan-touching root calls at all,
independent of whether the Settings key turns out to be wired to
something — a guarantee instead of an assumption.

## Risk assessment (2026-07-25) — what could actually go wrong on real hardware

Asked explicitly: does porting `pulse` this way risk the device itself? Not
a rewrite-from-scratch risk (this only replaces one root-transport layer),
but real risks worth naming before letting any of this run unattended:

1. **Fan/thermal safety (the highest-stakes category, though narrowed by
   the finding above).** Once real fan control is wired up via AIDL rather
   than `pulse`'s Odin-specific paths, the relevant risk moves from "cadence
   vs `xsu`" to whatever timing constraints the AIDL path has — a separate
   question to re-assess once that work starts. Independent of mechanism:
   this project's own known `xsu` stall (~126s once observed under heavy
   load, see `STATUS.md`) is worst-case exactly when a device is thermally
   stressed. Any actuation path (AIDL or `xsu`) needs to treat "the last
   command might not have landed" as a real, recurring condition, not a
   theoretical one — mitigated in practice by the SoC's own independent
   hardware thermal throttle/shutdown, which doesn't depend on any
   userspace app, so the realistic worst case is "runs hotter/louder than
   intended for a while," not hardware damage.
2. **CPU/GPU frequency writes — low risk.** `pulse` reads real, detected
   `scaling_available_frequencies`/`gpu_available_frequencies` at runtime
   rather than hardcoding numbers (confirmed earlier in this document), so
   there's no path to writing a frequency outside what the hardware itself
   already advertises as valid.
3. **Fighting the AYANEO vendor daemon over the same sysfs nodes.**
   `pulse`'s re-assert logic (`reassertBoundCaps` etc.) was tuned against
   the *Odin's* vendor daemon behavior (how fast and how it re-pins
   `scaling_min`/duty). AYANEO's own daemon (`com.ayaneo.gamewindow`, see
   the teardown findings) may re-pin differently or not at all — realistic
   worst case is oscillating/unstable clocks under contention, not damage,
   but worth watching for during first real test runs.
4. **Display/settings changes (`wm size`/`wm density`, brightness, RGB via
   `settings put`) are reversible but not crash-safe.** If the process dies
   mid-sequence (e.g. before a `wm size reset`), the device can be left with
   a wrong resolution/density/brightness until manually corrected via `adb`
   — annoying, recoverable, not damaging.
5. **The world-readable/-executable script file in `runGeneratedScript`**
   is a minor local-attacker consideration (another app could in principle
   race to read/tamper with the script between write and exec) — moot once
   the `xsu`-glue drops the file-based approach in favor of passing script
   text directly to `xsu -c` (already noted as a simplification opportunity
   earlier in this document), so no action needed beyond doing that
   simplification when the patch is written.

**Practical takeaway**: nothing here is a hardware-destructive risk by
design (frequency scaling and fan control are both bounded by hardware/OS
safety floors independent of `pulse`), but several items are "silent
misbehavior for a while before anyone notices" risks, most of them tied to
`xsu`'s known stall behavior and to code that was tuned against a different
vendor's firmware quirks. Recommended before letting any ported code run
as an unattended background service: (a) do the one remaining fan-mode
settings check noted above, (b) run first sessions under active
observation (logs + eyes on temperature) rather than silently backgrounded,
(c) confirm real sysfs behavior under contention with AYANEO's own daemon
before trusting the re-assert logic unattended.

## Recommendation

Reconnaissance is now closed out at the whole-module level — both
load-bearing assumptions (clean root abstraction, generic CPU/GPU
detection) are confirmed by a full-tree grep, not just the original 8-file
sample, and the domain-logic control loops were read directly rather than
inferred. The glue strategy stands for CPU/GPU/display/RGB/AutoTDP: this
looks like the right call over rewriting from scratch there. **Fan control
is the one exception**, confirmed on-device (2026-07-25): both of `pulse`'s
fan mechanisms are vendor-specific to AYN hardware and inert on AYANEO —
that piece isn't "glue a transport layer," it's "build fan control
separately," most likely on top of the already-proven AIDL bind to
`com.ayaneo.gamewindow`. The 120ms cadence-vs-`xsu` question that motivated
this pass no longer applies to `pulse`'s own fan code (it self-gates off),
but the same class of question (timing/reliability of whatever actuation
path drives fan control) will resurface once that separate AIDL-based fan
work is scoped. Actual patch-writing (fork `pulse-upstream`, replace
`RootExec.kt`, add the `SG8350P` `DeviceProfile` entry, and scope fan
control as a distinct follow-up) is deferred to a later session.
