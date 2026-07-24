# FINDINGS — is "glue, not rewrite" viable for `pulse`? (2026-07-24)

**Verdict: yes, very likely — the root abstraction is exactly as clean as
we hoped, and CPU/GPU detection is already fully dynamic/generic (not
hardcoded per-SoC). The one place with real per-device gating is narrow,
optional, and already degrades gracefully for an unrecognized SoC — which
is exactly what our device is today.** This reframes `apl/app/` from "write
AutoTDP/fan-curve/overlay/profiles from scratch" to "write one small root
shim + one small device-profile entry, reuse the rest."

This is a reconnaissance pass (8 files read closely, not the whole ~80-file
`app/` module) — solid enough to commit to the strategy, not exhaustive
enough to start cutting code without reading the control-loop logic
(`AutoTuneController.kt`, `PowerModel.kt`, `FanCurveController.kt`) first.

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

## What's NOT yet checked (next pass, before writing any glue code)

- `grep -rn "RootExec(\|RootCommandRunner(\|RootSupport\."` across the
  whole `app/` module — confirm `RootExec`'s public surface really is only
  `executeAsRoot()`/`pServerAvailable`, and that nothing else references
  `PServerBinder`/`ServiceManager` directly outside this one file.
- The actual control-loop logic: `data/AutoTuneController.kt`,
  `model/PowerModel.kt`, `model/PowerTargetMath.kt`,
  `data/FanCurveController.kt`/`model/FanCurve.kt`/`model/FanTempController.kt`
  — none of this was read yet. This is where the real AutoTDP/fan-curve
  domain logic lives; the glue question is about whether it's reachable
  without touching `PServerBinder`, which looks true from the root-layer
  read, but hasn't been confirmed by reading the control loop itself.
- `ui/`/`overlay/`/`tile/` packages (Compose UI, HUD overlay, Quick
  Settings tile) — not read at all this pass. Likely fine as-is (UI
  shouldn't care about the root mechanism) but unverified.
- Whether `minSdk 31` (mentioned in the original `xsu_handoff` notes,
  should be double-checked against this clone's actual `build.gradle.kts`)
  conflicts with anything `apl` currently targets (`minSdk 26` in the
  existing skeleton) — would need reconciling either direction.
- `AndroidManifest.xml` — permissions requested (their no-root path used
  Usage Access + overlay permission; unclear if those are still needed once
  a root-shell path is available, or if pulse conditionally skips them when
  `RootExec.pServerAvailable`).

## Recommendation

Worth a deeper follow-up pass (the four items above) before committing
engineering time to the glue — but nothing found in this pass contradicts
the strategy, and the two load-bearing assumptions (clean root abstraction,
generic CPU/GPU detection) are both confirmed, not just hoped for. This
looks like the right call over rewriting from scratch, pending that deeper
pass.
