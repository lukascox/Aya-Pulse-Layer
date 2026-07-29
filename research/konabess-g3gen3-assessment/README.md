# KonaBess-Next (G3 Gen 3 fork) assessment — static analysis, reference clone

Static analysis of a fresh `git clone` of
`https://github.com/thefiqs/KonaBess-Next-G3Gen3.git`, kept locally at
`research/konabess-g3gen3-upstream/` (gitignored, read-only reference clone —
same pattern as `research/pulse-upstream/`: never build it, never modify it,
consult it for diffing/reading only). This is a fork of the well-known
"KonaBess" GPU frequency/voltage table editor, explicitly branded for the
"G3 Gen 3" chipset — a name close enough to our own device's marketing name
("Snapdragon G3 Gen 3", per AYANEO/Qualcomm, though not confirmed by
`getprop` — see `diagnostics/docs/HARDWARE_PROFILE.md`) to be worth a look.

## Why this, why now

Two live questions this could resolve, both with direct bearing on
`pulse-for-aya`'s GPU/CPU control design (currently plain `xsu -c` sysfs
writes to `kgsl`/`cpufreq` nodes, no reboot, no boot-partition changes — see
`research/pulse-glue-assessment/FINDINGS.md` for why that design was chosen):

1. **Does this tool offer a live, no-reboot sysfs-write path** we don't
   already have, or is it — like the classic KonaBess — strictly a
   DTB-patch-and-reboot mechanism? If the latter, it's a fundamentally
   different risk class than `pulse-for-aya`'s approach, worth stating
   plainly rather than stretching for relevance.
2. **Does it carry any concrete GPU frequency/voltage numbers specific to
   this chip family** that we could cross-check against
   `diagnostics/docs/HARDWARE_PROFILE.md`'s own empirically-measured GPU
   power-level table?

A negative result on either question is still a useful answer — see
`FINDINGS.md` for the verdict.

## Scope of this assessment

Read-only static analysis only: source, manifest, and repo docs read from
disk, no build, no install, no device involved. See `FINDINGS.md` for the
numbered Q&A with `file:line` evidence pointers into
`research/konabess-g3gen3-upstream/`.
