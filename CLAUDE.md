# CLAUDE.md

## Project
Research repo for AYANEO Pocket FIT performance controls (root, AIDL, sysfs).
Deliverable is a glue patch on the existing `pulse` app
(`research/pulse-for-aya/`), not a rewrite.
`STATUS.md` is the single living state doc (open/active threads only) — read
it first, before anything else. Resolved threads move to `STATUS_ARCHIVE.md`
(same "living doc, update in place" rule) — read only if you need history
beyond the pointer left in `STATUS.md`.

## Session scope (token discipline)
- Work inside ONE `research/<project>/` at a time. Never scan sibling projects.
- Before touching a project, read only its `FINDINGS.md` / `README.md`.
- `research/pulse-upstream/` is a gitignored, read-only reference clone of
  upstream `pulse` — consult for diffing only; never build or modify it.
- Never read `docs/archive/` (frozen history) or `app/` (dead placeholder).
- For any exploration beyond the current project, use the `scout` subagent.
- For mechanical, fully-specified work (boilerplate, renames, test stubs,
  docstrings, format conversions), delegate to the `grunt` subagent.
- After code changes are finalized (diff + commit message decided), delegate
  the build-verify-commit-push cycle (`./gradlew compileDebugKotlin
  testDebugUnitTest lintDebug`, then `git add/commit/push`) to the `grunt`
  subagent — mechanical once the change itself is decided; keeps gradle/git
  output out of the main session's context.
- For grepping/trimming/reorganizing large raw log files once the exact
  pattern or script is already decided, delegate execution to `grunt` too —
  report back only the extracted facts, not the raw file contents.
- PULSE session logs (`pulse_<timestamp>[.log|_cap_poll.log|_dmesg.log|
  _logcat.log]`, pulled from `/sdcard/apl_pulse_logs/`) are grouped/trimmed/
  summarized by `research/pulse-for-aya/scripts/analyze-pulse-logs.py`
  (no model involved, deterministic) — it's a step in the user's own pull
  procedure (`adb pull ... ./pulled/ && analyze-pulse-logs.py ./pulled/`),
  producing a `SUMMARY.md` alongside the raw files. Read that `SUMMARY.md`
  first; only open a raw log file when the summary flags something (a
  crash-keyword hit, a missing clean-stop marker, an interesting session)
  worth a closer look. The full end-to-end routine for a two-device session
  (serials, per-unit summarising, triage table for the known-false-positive
  flags, redaction check, what to delete) is
  `research/ab-logger/results/PULL_AND_TRIM.md` — follow it rather than
  improvising, and update it in place when a step turns out wrong.

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
- **Commits, `STATUS.md` and `NOTES.md` record facts, not who did what.**
  Write "Sleep was switched off and the kills stopped", not "the user
  turned Sleep off" or "the user reported that…". No "user", no "I", no
  "we" attributing an action to a person — the repo is a research log, not
  a support ticket queue, and third-person narration about its own author
  reads as though it has customers. State what was tested, what held, what
  was disproved. From 2026-08-04 onward; two July commits (`44f0c05`,
  `500fb09`) predate this and are deliberately NOT being rewritten —
  they are already on the public mirror, a rewrite would touch 118
  commits and orphan the release tag, and a force push hides rather than
  unpublishes.
- Everything written to files in this repo is English-only, regardless of
  what language the conversation is in, unless the user explicitly asks
  for a different language for that specific file. **There is currently no
  such exception.** `pulse-for-aya/TESTING.md` used to be one — a Polish
  procedure for a non-technical tester — and was rewritten in English on
  2026-08-02 once the author became the only tester and the project drew
  an outside one.

## Hard rules
- Update `STATUS.md` in place; never create dated/versioned handoff docs
  (`git log` is the history).
- **Never `git push github` without an explicit go-ahead in this session.**
  `git push` (Forgejo, private) is free and expected after each chunk of
  work. The GitHub mirror is public and effectively irreversible — a force
  push hides, it does not unpublish. Once GitHub is ~10 commits behind, ask
  whether to keep batching locally or do the review-and-mirror pass now;
  don't decide it unilaterally, and don't let the gap grow silently.
  Before any mirror push, review `git diff github/main..main` against ALL of:
  hardware addresses and network names; account identifiers and tokens; local
  filesystem paths; the author e-mail (must be the private one — the repo has
  no global default it can rely on, see the local `user.email`); and **prose
  stating intent toward AYANEO or describing how their protections are
  worked around**. That last item is not greppable and is the one that has
  actually been caught in review (2026-07-31) — every earlier check only
  looked for technical data inside device logs.
- Never re-enable the `xsu` stdin invocation method.
- Never write fan-control sysfs/Settings paths in pulse-for-aya
  (`FanController.kt` is deliberately stubbed) without first reading
  `pulse-glue-assessment/FINDINGS.md`.
- Never run pulse-for-aya + ab-logger together during real gameplay
  unsupervised — correlated twice with device reboots
  (`STATUS.md` INCIDENT entries).
- Before any command that touches the physical device (any `adb`
  invocation — installs, pulls, shell commands, `xsu` calls, reboots,
  anything), first give the user a plain ELI5 explanation of what you're
  about to do and why, and wait for explicit acceptance. This device has a
  real history of crashes/reboots from exactly this kind of hands-on
  session work (`STATUS.md` INCIDENT entries) — never assume standing
  permission from earlier in the conversation covers a new round of
  device commands.
