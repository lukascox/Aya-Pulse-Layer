# CLAUDE.md

## Project
Research repo for AYANEO Pocket FIT performance controls (root, AIDL, sysfs).
Deliverable is a glue patch on the existing `pulse` app
(`research/pulse-for-aya/`), not a rewrite.
`STATUS.md` is the single living state doc — read it first, before anything else.

## Session scope (token discipline)
- Work inside ONE `research/<project>/` at a time. Never scan sibling projects.
- Before touching a project, read only its `FINDINGS.md` / `README.md`.
- `research/pulse-upstream/` is a gitignored, read-only reference clone of
  upstream `pulse` — consult for diffing only; never build or modify it.
- Never read `docs/archive/` (frozen history) or `app/` (dead placeholder).
- For any exploration beyond the current project, use the `scout` subagent.

## Commands
No root-level build (`app/` is an unconfigured stub; no root `gradlew`).
Each `research/<project>/` is an independent Android app — `cd` in first.
- Build: `./gradlew assembleDebug`
- Test: `./gradlew testDebugUnitTest`  (JUnit; only pulse-for-aya has tests)
- Lint: `./gradlew lintDebug`  (pulse-for-aya only; deliberately curated ruleset)
- Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Structure
- `research/pulse-for-aya/` — the glue patch (fork of `pulse`); the real deliverable
- `research/pulse-glue-assessment/` — risk analysis behind the patch (`FINDINGS.md`)
- `research/ab-logger/` — telemetry recorder for A/B vs native AyaSettings
- other `research/*` — capability probes and teardowns, each self-documented
- `diagnostics/` — hardware facts (`HARDWARE_PROFILE.md`) + validated measurement script

## Conventions
- Invoke `xsu` only as `ProcessBuilder("xsu", "-c", cmd)` — the stdin method
  silently false-positives.
- Root-probe cache latches only `true`; never cache a `false`
  (`RootExec.kt`, `RgbController.kt`).
- Packages: `pl.<projectname>.app`; exception: pulse-for-aya keeps upstream's
  `com.kei.pulse` for diffability.
- Findings go in the project's own `FINDINGS.md`; `STATUS.md` gets only
  a summary + pointer.

## Hard rules
- Update `STATUS.md` in place; never create dated/versioned handoff docs
  (`git log` is the history).
- Never re-enable the `xsu` stdin invocation method.
- Never write fan-control sysfs/Settings paths in pulse-for-aya
  (`FanController.kt` is deliberately stubbed) without first reading
  `pulse-glue-assessment/FINDINGS.md`.
- Never run pulse-for-aya + ab-logger together during real gameplay
  unsupervised — correlated twice with device reboots
  (`STATUS.md` INCIDENT entries).
