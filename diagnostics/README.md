# diagnostics/

Raw hardware measurement/diagnostic tooling for the KONKR/AYANEO Pocket FIT
(Snapdragon 8 Gen 3-class SoC, Adreno GPU, Android 14). Folded in from the
formerly-separate `apl-diag` repo (2026-07-25) — see the root
[README.md](../README.md) for why: once `apl` itself became a device-
capability research project rather than "the app", the two-repo split
("`apl-diag` = research, `apl` = the app it feeds") no longer matched
reality, so this is now one repo instead of two kept manually in sync.
`apl-diag`'s own git history/Forgejo repo is being archived separately, not
deleted — this folder is a plain copy, not a submodule/subtree link.

## Why this exists

AYASpace ships 5 power profiles (Eco/Balanced/Gaming/Streaming/Max) that
silently set CPU governor/freq caps and GPU limits with no visibility into
what they actually do. Before writing any custom controller logic (or,
now, before trusting a `pulse` glue patch's assumptions), this answers:

1. What does each AYASpace mode actually do at the kernel level?
2. How do we reliably measure FPS/GPU busy%/CPU freq/temps across ALL app
   types — system UI, native Android apps, AND emulators (RetroArch,
   Eden/yuzu-class, Dolphin), which render via native SurfaceView and are
   invisible to standard `dumpsys gfxinfo`?

## Current state

`scripts/pulse_lite_diag.sh` is the current script (content = the final,
on-device-validated v8 iteration from pre-git history — see `docs/archive/`
for the full v3-v8 debugging trail that got it there). **Confirmed working
end-to-end** for both RetroArch and Eden/yuzu — see `logs/` for the
validated sample runs.

This script has no version number in its filename — `git log` is the
version history, not a new `_vN.sh` copy per session.

## Layout

```
diagnostics/
├── scripts/pulse_lite_diag.sh   -- current script, no version suffix
├── logs/                         -- validated sample runs + A/B comparison CSVs
├── docs/
│   ├── HARDWARE_PROFILE.md       -- living reference, update in place
│   └── archive/                  -- frozen pre-git history (v3-v8 handoffs, older
│                                     script versions, superseded doc drafts)
```

(No separate `STATUS.md` here anymore — its content was folded into the
root [`STATUS.md`](../STATUS.md), which is now the single living document
for the whole repo, diagnostics included.)

## What's confirmed (see `docs/HARDWARE_PROFILE.md` for full detail)

- Foreground-app detection: `dumpsys activity activities | grep
  topResumedActivity=` (NOT `mCurrentFocus`/`mResumedActivity`, confirmed
  to return nothing on this Android 14 build).
- SurfaceFlinger layer selection: 4-tier priority search (BLAST > plain
  SurfaceView > last non-helper match > old fallback) — the part that took
  four buggy iterations (v4-v7) to get right.
- `dumpsys gfxinfo` is confirmed UNRELIABLE for native SurfaceView
  renderers (returns 0 frames or frozen/cached stats) — do not reuse it.
- GPU busy signal: use raw `kgsl-3d0/gpubusy` (NOT `gpubusypercentage`,
  confirmed broken on this kernel) — but the raw counter itself wraps/resets
  between reads (confirmed, not theoretical), so no tier-decision logic
  should trust a single raw read.
- CPU cores have been directly observed exceeding the HOTTER thermal
  threshold (87-96°C) with no observable throttling response from AYASpace
  — do not assume the vendor firmware is protecting the device thermally.
- `xsu` callable from an installed app's `Runtime.exec()` — confirmed in
  `research/xsu-capability-probe/FINDINGS.md`.

## Handoff workflow

Historically (`docs/archive/`), every script version got its own
`vN_handoff.md`. That pattern is retired: the root `STATUS.md` is the one
living document for this whole repo, `git log` is the history. Do not
create `handoff_vN.md`-style files.
