# Pulse glue assessment — should `apl` fork+patch upstream `pulse` instead of writing its own app?

Static analysis of `github.com/keiretrogaming/pulse` (GPL-2.0), cloned locally
to `research/pulse-upstream/` (gitignored — separate git history, not forked
into this repo directly, read-only reference). This folder holds our
*assessment* of it — curated evidence + findings — same pattern as the
`ayaspace-teardown`/`aya-gamewindows-teardown` folders.

## Why this question came up

`pulse` already implements almost everything `apl` wants to build:
closed-loop AutoTDP, custom fan curve, HUD/OSD overlay, RGB control,
per-app profiles, Quick Settings tile — mature, maintained, tested on real
hardware. Its only known blocker for this device is its root mechanism
(`PServerBinder`, available on AYN Odin 3 / Thor / Retroid Pocket 6, not on
this Snapdragon-based AYANEO Pocket FIT). The question: is that blocker a
clean, narrow substitution (glue), or is `PServerBinder` woven too deeply
through the codebase to swap out cheaply?

## To re-clone / re-inspect the actual source

```bash
git clone https://github.com/keiretrogaming/pulse.git research/pulse-upstream
```

See `FINDINGS.md` for the verdict and exact next steps.
