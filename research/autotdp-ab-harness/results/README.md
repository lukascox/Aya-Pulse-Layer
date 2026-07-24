# Results — Tests 1-9 raw output

Same convention as `xsu-capability-probe/results/`: one subfolder per
on-device run, containing the raw pulled files, not summarized/edited.

Suggested layout per run:

```
results/
└── run1_<date>/
    ├── xsu_benchmark_result.txt
    ├── pulsefit_hw_profile.txt
    └── xsu_bench_logcat_dump.txt
```

After a run, update `../FINDINGS.md`-equivalent notes (or `apl`'s top-level
`STATUS.md`) with what changed vs the prior run — don't just drop files
here without a pointer to what they show.
