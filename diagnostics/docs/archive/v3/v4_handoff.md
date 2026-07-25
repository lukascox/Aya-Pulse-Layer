# pulse_lite_diag_v4.sh - Handoff (retrospective, English, partial)

Source: actual script content (pulse_lite_diag_v4.sh, verified). NOTE: unlike v5, we
do NOT have full 90s-sampling logs collected specifically with v4 — only 3 shorter
logs (baseline, gba_eco_mode, gba_gaming_mode) that appear to predate even v4's
section numbering (they use "IDENTITY CHECK" instead of "IDENTITY / RUN INFO" and lack
the AYASpace-mode reminder line), so they likely come from an earlier v3-class script,
not v4 itself. This handoff is based primarily on reading v4's actual source code, with
those 3 logs used only as loose corroborating evidence, not as v4-specific proof.
Confidence level: MEDIUM (source code is exact; behavioral confirmation via matching
logs is limited/indirect).

## What v4 does differently from v5 (both confirmed by diffing actual source)

**v4 introduces branching logic based on suffix — this is the core difference.**
Sections 7-10 (FPS/GPU sampling, CPU write test, GPU write test, AYASpace conflict
probe) are wrapped in `if [ "$SUFFIX" != "mode_survey" ]; then ... fi` — they are
skipped entirely when suffix is "mode_survey", to keep that specific run fast and
focused on section 11 only.

**v4 adds Section 10 — AYASpace write-conflict probe.** Only runs if
suffix="ayaspace_conflict": sets policy0 scaling_max_freq to 1344000 (Eco-equivalent),
holds it read-only (chmod 444) for 20 seconds, printing scaling_max_freq + governor
every 2s. Designed to answer whether AYASpace's own mode switches overwrite this value
mid-window. This section is CARRIED FORWARD conceptually into TEST_PROCEDURE.md's
"Series 4" — confirmed still pending/not yet executed in any log collected through v6.

**v4 adds Section 11 — interactive AYASpace mode survey via sentinel files.** Only
runs if suffix="mode_survey": the script enters an infinite polling loop (1s sleep),
watching for two sentinel files on /sdcard:
- `/sdcard/pulse_lite_diag.snap` — triggers one snapshot (CPU governor/max/cur freq
  for all 4 policies + GPU max_pwrlevel/devfreq_governor/gpuclk), then deletes the
  sentinel and resumes waiting.
- `/sdcard/pulse_lite_diag.snapdone` — breaks the loop and ends the script.
Design intent: let the user switch AYASpace modes manually and trigger a snapshot via
a second `adb shell touch` call from another terminal, avoiding the need for a real
TTY (the docstring explains this replaces an even earlier `read`-based approach from
v3, which did not work reliably because stdin is not a real TTY inside a non-
interactive `adb shell xsu` invocation).

## CONFIRMED FAILURE (documented directly in v5's own changelog comment, verified
## against v4 source)

v4's Section 11 sentinel-file survey DID NOT WORK IN PRACTICE. v5's source code
contains this exact comment, which we can treat as a first-party confirmed post-mortem:
"Section 11 (interactive AYASpace mode survey via sentinel files) is removed
entirely. It did not survive inside an `xsu` invocation in practice (the session
appears to exit before the wait-loop can pick up the sentinel file - likely `xsu`
does not keep a long-running background loop alive the way a persistent root shell
would)."

Root cause (as understood at the time): `xsu` likely does not sustain a long-running
background shell session the way a persistent `su` root shell would — the invoking
session appears to terminate (or stop processing) before the polling loop can detect
the sentinel file being created. This is DISTINCT from the TTY/stdin issue that v4's
own docstring says it fixed vs v3 (that earlier problem was about `read` not working
non-interactively; the sentinel-file approach fixed THAT specific problem, but ran
into this NEW, different problem with `xsu` session lifetime).

## What this means for anyone reviving mode_survey or ayaspace_conflict logic

- Do NOT reuse the Section 11 sentinel-polling pattern as-is under `xsu` — it is
  confirmed non-functional based on direct project history, not a mere hypothesis.
  Any future interactive/long-running root-shell workflow needs a different mechanism
  (e.g. a persistent foreground service invoking xsu per-command rather than one
  long xsu session held open with a polling loop, or writing state via a file that a
  SEPARATE short-lived xsu call checks periodically from the Android side instead of
  from within the shell script itself).
- Section 10 (ayaspace_conflict probe) does NOT depend on a long-running xsu session
  in the same way — it's a single continuous 20-second loop within one script
  execution, not a wait-for-external-sentinel pattern. This section's design is likely
  still viable and is exactly what TEST_PROCEDURE.md's Series 4 describes. It has
  simply never been executed yet in any log collected through v5/v6.
- Sections 0-6 in v4 are byte-for-byte identical to v5's sections 0-6 (confirmed by
  direct comparison) — no functional difference to document there.
- Sections 7-9 (when not skipped for mode_survey) are also byte-for-byte identical to
  v5's sections 7-9 — meaning v4 has the EXACT SAME gfxinfo-based FPS unreliability
  and unbounded GPU busy% calculation issues later confirmed in v5's logs and
  documented in the v5 handoff. These are not new v5 bugs — they were already present
  in v4, just not yet diagnosed as bugs at the time (the mode_survey failure was the
  known issue being fixed in the v4->v5 transition, not the FPS/GPU busy% issues).

## Confidence caveats for this retrospective

- No log was found that was definitively generated by v4 running "ayaspace_conflict"
  or "mode_survey" with an actual completed session — cannot confirm what a successful
  vs failed Section 11 log looked like beyond the comment in v5's changelog.
  If such a log exists but wasn't provided in this thread, this handoff should be
  revised once it's available.
  The 3 available logs (baseline, gba_eco_mode, gba_gaming_mode) most likely predate
  v4 (different header format: "IDENTITY CHECK" vs v4/v5's "IDENTITY / RUN INFO", no
  AYASpace-mode reminder line) — treat any inference from those 3 logs about v4
  specifically as weak/indirect, not confirmed.
