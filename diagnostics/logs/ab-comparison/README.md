# A/B comparison logs — Baseline vs AutoTDP v0

CSV output from `apl` repo's `research/autotdp-ab-harness` app. The tool
lives in `apl` (needs its Gradle/Kotlin scaffolding); the data it produces
belongs here, next to the rest of this repo's empirical reference material.

Suggested layout per session (one pair per game/scene/ordering):

```
ab-comparison/
└── <game>_<scene>/
    ├── pulsefit_baseline_run1.csv
    ├── pulsefit_autotdp_run1.csv
    ├── pulsefit_baseline_run2.csv   -- swapped order (AutoTDP went first this time)
    └── pulsefit_autotdp_run2.csv
```

Rename away from the app's generic `pulsefit_<mode>_<timestamp>.csv` before
filing here — the timestamp-only name gets confusing fast across several
games x two modes x two orderings.
