# A/B comparison logs — native AyaSettings vs `pulse-for-aya`

CSV output from `research/ab-logger` (this repo). Formerly fed by
`research/autotdp-ab-harness`'s Baseline/AutoTDP modes — that comparison
was against the old `pulse_lite_v3.7.sh` bash controller, which is no
longer the thing being tested. `ab-logger` is a pure telemetry recorder
(no mode concept, no daemon launching) used to compare native AyaSettings
against `research/pulse-for-aya` instead — see
`research/pulse-for-aya/TESTING.md` for the actual test procedure and
`research/ab-logger/TESTING.md` for how to run/pull/clean the logger app
itself.

Suggested layout per session (one pair per game/scene/ordering) — the
filename no longer encodes which arm was active (there's no mode column
anymore), so rename on the way in:

```
ab-comparison/
└── <game>_<scene>/
    ├── native_run1.csv     -- was session_<timestamp>.csv off the device
    ├── pulse_run1.csv
    ├── native_run2.csv     -- swapped order (pulse went first this time)
    └── pulse_run2.csv
```

Rename away from the app's generic `session_<timestamp>.csv` before filing
here — the timestamp-only name doesn't say which arm it was, and that
info only exists in whatever notes were taken during the session (see
`pulse-for-aya/TESTING.md`'s "what to record" step).
