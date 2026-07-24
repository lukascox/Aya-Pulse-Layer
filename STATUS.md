# STATUS

Living document — update this in place at the end of a working session,
commit with a descriptive message. Do not create a new dated/versioned copy
of this file; `git log` is the history.

Remote: `git.internal.example/cox/AyaPulseLite` (Forgejo, self-hosted). Sibling
repo: `apl-diag` (`git.internal.example/cox/AyaPulseDiag`) — the diagnostics/
research half of this project, see its own STATUS.md.

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
  `--latency` FPS calc), originally built and validated in the sibling
  `apl-diag` repo's shell script, is confirmed reachable the same way,
  across RetroArch/Eden/Dolphin (three distinct layer-naming conventions).
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
  as missing in `apl-diag`'s hardware profile, still needed before a
  precise step-controller can be designed (currently only 4 discrete
  points per cluster are known).
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
Built and compiling; **not yet run on-device** (Test 9's pass/fail
determines whether the daemon-launch approach in AutoTDP mode is even
viable — check its own README before trusting the A/B numbers). Resulting
CSVs, once collected, should be copied into `apl-diag/logs/` — the code
lives here for the Gradle/Kotlin scaffolding, the data belongs there.

## Next steps (rough priority order)

1. Push `pulse_lite_v3.7.sh` to the device and run
   `research/autotdp-ab-harness`'s Tests 1-9, especially Test 9 — it's a
   hard gate before any A/B session data can be trusted.
2. Run actual Baseline vs AutoTDP sessions per game (see that app's
   README's test procedure) — this is the "realistic input" needed before
   designing the FPS-delta-augmented v1 controller.
3. Decide `AutoTdpController`'s actual control signal and loop cadence,
   informed by the ~100ms-per-`xsu`-call floor documented in FINDINGS.md
   and by the A/B comparison data from step 2.
4. Write the real `XsuShell.kt` (production quality, not probe quality) —
   the `args` method only, informed by the timeout/reliability findings.
5. First real increment per the KISS plan in README.md: a service that
   starts on boot and writes one confirmable log line.
