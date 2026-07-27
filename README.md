# apl — AYANEO Pocket FIT performance research + a `pulse` glue patch

Device-capability research for the KONKR/AYANEO Pocket FIT (Snapdragon 8
Gen 3-class SoC / SG8350P, Adreno GPU, Android 14) — root-access
mechanisms, sysfs/thermal behavior, and the undocumented AIDL surface
behind AYASpace's own performance profiles. The findings feed a **glue
patch onto the existing, mature [`pulse`](https://github.com/keiretrogaming/pulse)
app** (AutoTDP, fan curve, HUD overlay, RGB, per-app profiles — GPL-2.0,
tested on real AYN/Retroid hardware) rather than a ground-up rewrite of
the same logic.

The author is not a professional programmer. This project is built
iteratively with AI assistance (Claude Code) plus manual builds/tests on
real hardware. See `STATUS.md` for the current state — that file is kept
up to date in place rather than accumulating a new handoff document per
session (the pattern used before this repo existed, see `docs/archive/`).

## Why this exists

AYASpace ships 5 closed power profiles (Eco/Balanced/Gaming/Streaming/Max)
with no visibility into what they actually do at the kernel level, and no
way to define a custom one, an FPS-target-driven controller, a custom fan
curve, or per-app automatic profile switching. `pulse` already implements
all of that — closed-loop AutoTDP, custom fan curve, HUD/OSD overlay, RGB,
per-app profiles, Quick Settings tile, tested and maintained on real
AYN/Retroid hardware. Its only blocker for this device was its root
mechanism (`PServerBinder`, an AYN/Retroid-specific broker, not present
here). This repo exists to answer, empirically, whether that blocker is a
narrow substitution or something deeper — and, having confirmed the
former, to build and validate that substitution.

## Current state (2026-07-27)

- **`research/pulse-for-aya/`** — the actual glue patch: a fork of
  upstream `pulse` with its root-transport layer swapped from
  `PServerBinder` to `xsu` (this device's confirmed root-shell
  equivalent), fan control stubbed out (native AyaSettings keeps owning
  it — see below), RGB left untouched (self-gates off safely on its own).
  **Builds clean, installs, launches, reads live CPU/GPU/thermal
  telemetry, and its AutoTDP write path is built, tested, and confirmed
  regulating live on real hardware** — verified independently of the
  app's own logs by polling `scaling_cur_freq`/`scaling_max_freq`
  directly (`research/pulse-for-aya/scripts/poll-cpufreq.sh`), not just by
  trusting the app said so. Despite the amount of investigation this took
  (see below), a direct diff against `research/pulse-upstream/` confirms
  it's still a small, clean glue patch — 6 modified files with modest,
  targeted line diffs plus 2 new AYANEO-specific files, not a de-facto
  rewrite. Two threads are still open: a not-yet-explained "Minecraft
  needs a reboot after every reinstall" ritual, and persistently low FPS
  in one emulator (Eden) despite confirmed-live regulation. See
  `STATUS.md` for all of the above in detail.
- A real, reproducible **vendor bug** was found along the way: this
  device's root-shell daemon (`xsud`, closed-source) has a stack-overflow
  bug (`xsu_conn_handler`) that a real gameplay session's cumulative `xsu`
  connection load can trigger, eventually crashing `system_server`. Not
  fixable from this repo (closed vendor binary) — mitigated instead by
  cutting `pulse-for-aya`'s own contribution to that connection load: a
  root daemon launched once per session via a single `xsu` call, driven
  over a named pipe for every subsequent write, instead of one `xsu`
  connection per write (previously the app's single largest contributor
  to that load). An AIDL-based mitigation attempt (driving AYASpace's own
  Binder interface instead of `xsu` for the same writes) was tried first,
  confirmed to not help — and confirmed to be net-*negative*, since
  AYASpace's own AIDL receiver shells out through `xsu` internally too,
  with *more* connections than `pulse-for-aya`'s own code — and was
  reverted. Full timeline in `STATUS.md`.
- **`research/pulse-glue-assessment/`** — the analysis behind the patch:
  why "glue, not rewrite" is the right call, exactly what needed patching
  (root layer, fan control) and what didn't (CPU/GPU detection, RGB,
  display/refresh-rate, autostart, the Quick Settings tile — all already
  generic or self-gating), plus a device-risk assessment and the A/B
  testing protocol.
- **`research/aidl-bind-spike/`** — confirmed, on-device, that a plain
  installed app (no root) can drive AYANEO's own performance-profile
  switching directly over Binder to `com.ayaneo.gamewindow` — a second,
  faster actuation path for whole-profile switches, independent of the
  `pulse` glue, and the likely route for fan control later (see below).
- **`research/ayaspace-teardown/`** and **`research/aya-gamewindows-teardown/`**
  — static analysis of the AYANEO vendor apps that found the AIDL service
  above is exported with no caller-identity verification at all — the
  finding that made the spike above worth trying.
- **`research/xsu-capability-probe/`** and **`research/autotdp-ab-harness/`**
  — the probes that first confirmed `xsu` works from an installed app and
  validated the sysfs read/write mechanics and the FPS-measurement
  pipeline.
- **`research/ab-logger/`** — a minimal, purpose-built telemetry recorder
  (two buttons: start/stop log) reusing that proven sampling pipeline,
  built to compare `pulse-for-aya` against native AyaSettings without the
  older harness's now-irrelevant daemon-launch/mode-picker baggage. See
  that folder's `README.md` and `TESTING.md`.
- **`diagnostics/`** — raw hardware facts (full CPU OPP tables, per-mode
  fan/GPU config sourced from AYASpace's own AIDL callback, thermal
  thresholds) and the validated FPS-measurement shell script. Folded in
  from the formerly-separate `apl-diag` repo (2026-07-25) — see
  `diagnostics/README.md` for why: once this repo itself became research
  rather than "the app", the two-repo split no longer matched reality.
- **`app/`** — the original from-scratch skeleton this repo started as
  (TODO-comment stubs, never implemented). Superseded by the glue-patch
  approach above; not actively developed. Kept as the historical starting
  point, not a target to keep building out.

## Why glue, not rewrite

`pulse`'s root abstraction turned out to be a single, narrow choke point
(one ~15-line method), its CPU/GPU frequency detection is already fully
dynamic (reads the hardware's own advertised OPP tables at runtime, no
per-SoC hardcoding), and its one real per-device gate degrades gracefully
for an unrecognized SoC instead of crashing. That reconnaissance — and the
decision it led to — is fully written up in
`research/pulse-glue-assessment/FINDINGS.md`. Fan control is the one
confirmed exception: both of `pulse`'s fan mechanisms are AYN-vendor-
specific and inert on AYANEO hardware, so it's explicitly out of scope for
this patch (native AyaSettings keeps doing it, and does it well) — the
plan is a dedicated fan-control loop later, on top of the AIDL bind
confirmed above, not a `pulse` patch.

## Repo layout

```
apl/
├── research/
│   ├── pulse-for-aya/             -- the actual glue patch, working prototype
│   ├── pulse-glue-assessment/     -- analysis + risk assessment behind the patch
│   ├── pulse-upstream/            -- gitignored, read-only clone of upstream pulse
│   ├── aidl-bind-spike/           -- confirmed no-root AYASpace profile-switch path
│   ├── ayaspace-teardown/
│   ├── aya-gamewindows-teardown/  -- vendor app static analysis (found the AIDL gap)
│   ├── xsu-capability-probe/
│   ├── autotdp-ab-harness/        -- root-channel probes + superseded A/B harness
│   └── ab-logger/                 -- current A/B telemetry recorder (start/stop log)
├── diagnostics/                   -- raw hardware facts + FPS script (formerly apl-diag)
├── app/                           -- original from-scratch skeleton, superseded, not active
├── docs/archive/                  -- frozen pre-git history (do not extend further)
└── STATUS.md                      -- current state, living document
```

## Design principles

- **Glue over rewrite, where a mature implementation already exists.**
  Reuse hard-won domain knowledge (fan-curve math, CPU/GPU detection
  quirks, thermal behavior) instead of rediscovering it from scratch —
  this reframed the whole project after `pulse-glue-assessment`.
- **Findings before code.** Each `research/` subfolder's `FINDINGS.md` is
  the record of what was actually checked and confirmed, so nothing needs
  re-discovering next session. `STATUS.md` is the one living cross-cutting
  document — write there, don't create a new handoff doc.
- **Small, verifiable increments**, validated on real hardware, not
  assumed from reading code alone.

## Handoff workflow

Historically (see `docs/archive/`), this project wrote a new handoff `.md`
file per script/session. Going forward, in git, **that pattern is
replaced**: `STATUS.md` is a single living document updated in place, and
`git log` is the version history. Do not create `HANDOFF_v2.md`-style
files — update `STATUS.md` and write a good commit message instead.
