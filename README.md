# APL — AyaPulseLite

Native Android GUI app for the KONKR/AYANEO Pocket FIT (Snapdragon 8 Gen
3-class SoC, Adreno GPU, Android 14), replacing the manual "Root Script"
workflow in AYA Settings with a real background service.

Architecture/inspiration: [`pulse`](https://github.com/keiretrogaming/pulse)
(AutoTDP logic), but with a root layer built on the device's native `xsu`
binary (`Runtime.exec("xsu", "-c", cmd)`, confirmed working from an
installed app — see `research/xsu-capability-probe/FINDINGS.md`) instead of
reflection into `PServerBinder` (not available on this device).

The author is not a professional programmer. This project is built
iteratively with AI assistance (Claude Code) plus manual builds/tests on
real hardware. See `STATUS.md` for the current state — that file is kept up
to date in place rather than accumulating a new handoff document per
session (the pattern used before this repo existed, see `docs/archive/`).

## Scope (priority order)

1. **Module 1 — AutoTDP (in progress).** Background service, boots
   automatically, reads active app FPS, a hysteresis-based step controller
   picks CPU/GPU limits to hold a target FPS (default 60) at minimum power.
   Writes to sysfs via `xsu`.
2. **Module 2 — Key remapping (deferred).** Arbitrary physical button
   rebinding. Likely Accessibility Service + InputManager, or root-level
   `getevent`/`sendevent` if buttons are firmware-reserved. Early research
   for this lives outside this repo for now (see project root's `external
   buttons/` folder) — will become its own separate project, not a module
   of this one, once it's actually started.
3. **Module 3 — Fan curve (deferred, priority may rise).** PI controller
   holding minimum RPM against temperature. `HARDWARE_PROFILE.md` (in the
   sibling `apl-diag` repo) found CPU cores hitting ~93.8°C with no observed
   throttling response from AYASpace — do not assume the vendor firmware is
   already handling thermals safely.

## Design principles

- **KISS.** Every module is its own small, testable class. No god objects.
- **Modularity.** Transport layer (`xsu`), data-reading layer (FPS/temp),
  logic layer (controllers), and UI are separated and don't know each
  other's implementation details (`root/`, `fps/`, `tdp/`, `ui/`).
- **Small steps.** Each iteration is one small, verifiable increment.
- **No vendor magic copy-pasting.** `pulse`'s architecture is a reference,
  not a source to fork 1:1 — the root mechanism differs.

## Repo layout

```
apl/
├── app/                        -- the real source (seeded from the original
│                                   pulsefit_skeleton.zip, package renamed
│                                   pl.pulsefit.app -> pl.ayapulselite.app)
├── research/
│   └── xsu-capability-probe/    -- throwaway probe that validated the xsu-from-app
│                                   channel; FINDINGS.md has the conclusions that
│                                   inform app/'s XsuShell.kt
├── docs/
│   └── archive/                 -- frozen pre-git history (do not extend further,
│                                   see "Handoff workflow" below)
├── STATUS.md                    -- current state, living document
└── build.gradle.kts / settings.gradle.kts / gradlew / gradle/wrapper/
```

## What's already confirmed (see `research/xsu-capability-probe/FINDINGS.md` for detail)

- `xsu` works via `Runtime.exec()`/`ProcessBuilder` from an installed app
  (debug AND release builds) — the single biggest open question this
  project carried is now closed.
- CPU (`cpufreq/policy*/scaling_max_freq`) and GPU
  (`kgsl-3d0/max_pwrlevel`) sysfs writes both confirmed working through
  this channel.
- The FPS measurement pipeline (foreground-app detect → SurfaceFlinger
  layer match → `--latency` FPS calc), ported from the sibling `apl-diag`
  project's validated shell script, is confirmed reachable through the
  same channel, across three different emulator layer-naming conventions.
- A real, recurring failure mode exists under heavy device load (a batched
  sysfs-read call stalled ~126s once during Dolphin gameplay) — see
  FINDINGS.md's "Real failure mode" section before designing the actual
  polling loop.
- The "stdin" `xsu` invocation method (piping commands into an interactive
  process) is confirmed broken (silent false positive) — the production
  code must use the `args` method (`ProcessBuilder("xsu", "-c", cmd)`).

## Handoff workflow

Historically (see `docs/archive/`), this project wrote a new handoff `.md`
file per script/session. Going forward, in git, **that pattern is
replaced**: `STATUS.md` is a single living document updated in place, and
`git log` is the version history. Do not create `HANDOFF_v2.md`-style files
— update `STATUS.md` and write a good commit message instead.
