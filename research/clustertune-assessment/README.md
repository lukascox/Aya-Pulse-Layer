# ClusterTune assessment — static analysis, reference clone

Static analysis of a fresh `git clone` of `https://github.com/AurelioB/ClusterTune`,
kept locally at `research/clustertune-upstream/` (gitignored, read-only
reference clone — same pattern as `research/pulse-upstream/`: never build it,
never modify it, consult it for diffing/reading only). ClusterTune is a CPU
frequency-cap tuner for AYN Odin/Retroid-class handhelds. Upstream `pulse`'s
own README credits ClusterTune as the pioneer of its no-root technique:
obtaining the vendor's privileged `PServerBinder` system service via
reflection and calling `binder.transact(...)` directly, no root needed, on
devices that ship that service. Our AYANEO Pocket FIT does **not** ship
`PServerBinder` — that absence is exactly why `pulse-for-aya`
(`research/pulse-for-aya/`) had to fork upstream `pulse`'s root-transport
layer onto `xsu -c` instead (see `research/pulse-glue-assessment/FINDINGS.md`).

## Why this, why now

A specific tip: ClusterTune is suspected to carry a **fallback path using
`su` directly**, distinct from its `PServerBinder` mechanism — i.e. it may
already have solved the "no PServerBinder on this device" problem that
`pulse-for-aya` had to solve by forking. If ClusterTune's `su` fallback is
per-call (spawn a fresh root-shell process for every write/read), it would
face the same ~100ms-per-call floor and crash-risk profile documented in
`STATUS.md`'s `xsud` investigation — worth knowing whether ClusterTune
solved that differently (some persistent-process/daemon technique, like
`pulse-for-aya`'s own FIFO daemon) or simply accepts the cost. A negative
result — a naive, rarely-exercised fallback with no special handling — is
still a useful, honest answer.

## Scope of this assessment

Read-only static analysis only: source and repo docs read from disk, no
build, no install, no device involved. See `FINDINGS.md` for the numbered
Q&A with `file:line` evidence pointers into `research/clustertune-upstream/`.
