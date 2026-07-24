# STATUS

Living document — update this in place at the end of a working session,
commit with a descriptive message. Do not create a new dated/versioned copy
of this file; `git log` is the history.

Remote: `git.internal.example/cox/AyaPulseLite` (Forgejo, self-hosted). Sibling
repo: `apl-diag` (`git.internal.example/cox/AyaPulseDiag`) — the diagnostics/
research half of this project, see its own STATUS.md.

## PLAN FOR NEXT SESSION (2026-07-24 end of day)

**Headline result this session: `research/aidl-bind-spike` CONFIRMED on
real hardware.** A plain, non-system `apl`-style app can flip AYASpace's
performance profile (Gaming ↔ Eco, verified via governor + `scaling_cur_freq`
read-back, repeated 3x reliably) over a bare Binder connection to
`com.ayaneo.gamewindow` — no `xsu`, no root, no per-call ~100ms floor. Full
per-mode config (CPU per-core caps, fan mode, GPU frequency range) also
came back live in the callback JSON — resolved the long-standing
"Gaming vs Max identical?" mystery (answer: GPU cap only, 834MHz vs
1050MHz/uncapped) — see `research/aidl-bind-spike/FINDINGS.md` and the
updated table in `apl-diag/docs/HARDWARE_PROFILE.md`.

This changes the architecture question from "should `apl` try the AIDL
route" to "how much of `apl` should be built on it." Next session, in
priority order:

1. **Decide `apl/app/`'s actuation architecture**: AIDL-bind (now proven)
   vs `xsu` sysfs writes (also proven, but slower/riskier) — likely AIDL
   for whole-profile switches (proven), `xsu` still needed for anything
   NOT covered by the AIDL command catalog (live FPS/temp/busy% reads for
   `AutoTdpController` — AIDL only exposes "set" commands, no monitoring).
2. **Scope and build the per-app profile-mimic feature** (assign Eco/
   Balanced/Streaming/Gaming/Max to specific apps, auto-applied via AIDL on
   foreground-app detection) — this is now `apl/app/`'s first real feature,
   and the hard part (does the actuation mechanism work) is already answered.
   Needs: a `ForegroundService` + `BootReceiver` (still just placeholders in
   `app/`), reuse of `FpsPipeline.parseForegroundPkg`-style detection (via
   `xsu`, since polling foreground app isn't in the AIDL surface either),
   and `AidlProtocol.kt`'s bind/register/send logic ported from the spike
   into production-quality code.
3. **Optional follow-up spike** (not blocking, only if step 2 needs it):
   test `com_set_performance_fan`/`com_set_performance_cpu` (fine-grained,
   not whole-mode) and the controller/key-mapping commands
   (`com_set_abxy_mode` etc.) — the aidl-bind-spike only exercised whole-mode
   switching so far.
4. Everything from before this session remains queued behind the above:
   A/B comparison sessions (`research/autotdp-ab-harness`), then
   `AutoTdpController` design informed by both that data and the ~100ms
   `xsu` floor (now less critical for actuation, still relevant for reads).

## Where things stand (this repo's first commit)

This repo was assembled by migrating scattered pre-git research into a
single structure — see `docs/archive/` for everything that predates this
commit, and `research/xsu-capability-probe/FINDINGS.md` for the specific
technical conclusions summarized below.

## Confirmed, don't re-litigate

- `xsu` is callable via `Runtime.exec()`/`ProcessBuilder("xsu", "-c", cmd)`
  from a normal installed app (debug and release both), giving `uid=0` /
  `context=u:r:xsud:s0`. This was the single biggest open risk carried from
  `docs/archive/xsu_handoff_2026-07-21.md` — now closed.
- CPU sysfs (`cpufreq/policy*/scaling_max_freq`) and GPU sysfs
  (`kgsl-3d0/max_pwrlevel`) writes both confirmed working through that
  channel, with read-back verification.
- The FPS pipeline (foreground app → SurfaceFlinger layer match →
  `--latency` FPS calc), originally built and validated in the sibling
  `apl-diag` repo's shell script, is confirmed reachable the same way,
  across RetroArch/Eden/Dolphin (three distinct layer-naming conventions).
- The "stdin" `xsu` invocation method is broken (silent false positive,
  never diagnosed) — do not use it. Use the `args` method only.
- `pulse_lite`'s old tier/hysteresis/floor controller architecture (see
  `docs/archive/pulse_lite/v3.7/pulse_lite_v3.7_handoff.md`) already
  achieved the target outcome once (same FPS, meaningfully lower
  CPU/GPU/skin temps and fan RPM than stock) — but was driven by raw
  busy% signals, which turned out to have a structural ambiguity (can't
  tell "needs a higher clock" from "fenced waiting on GPU"). The plan is to
  port the tier/hysteresis/floor *architecture* into `AutoTdpController.kt`
  but drive it from measured FPS-vs-target delta instead of busy%.

## Known open risk, not yet resolved

A batched sysfs-read call stalled ~126 seconds once during heavy Dolphin
load (see FINDINGS.md's "Real failure mode" section) — root cause not
confirmed. `AutoTdpController`'s polling loop needs to treat "a call might
just not return for a long time under heavy load" as a real, recurring
condition, not a theoretical edge case.

## Not yet done

- `app/` is still the original placeholder skeleton (TODO-comment stubs,
  package renamed to `pl.ayapulselite.app`, nothing implemented).
- No `XsuShell.kt`/`AutoTdpController.kt` real implementation exists yet.
- Full `scaling_available_frequencies` OPP table per CPU policy — flagged
  as missing in `apl-diag`'s hardware profile, still needed before a
  precise step-controller can be designed (currently only 4 discrete
  points per cluster are known).
- Test 6/7 from the probe's v2 spec (full CPU frequency table dump,
  governor write+verify) were coded but not yet run on-device before this
  migration — see FINDINGS.md.

## New: `research/autotdp-ab-harness/`

Second probe app, sibling to `xsu-capability-probe/`. Adds Test 8 (fan/
power node discovery — nothing hardcoded, logs what's found) and Test 9
(backgrounded-process persistence via `xsu` — a hard gate for the harness
below). Its actual point: an A/B comparison harness with two modes
(Baseline / AutoTDP) sharing one sampling loop (FPS + CPU/GPU/thermal/fan/
battery, one CSV row every 2s), where AutoTDP launches this repo's own
already-validated `docs/archive/pulse_lite/v3.7/pulse_lite_v3.7.sh` as a
background daemon and just observes — no controller logic is ported into
Kotlin here.
Resulting CSVs, once collected, should be copied into `apl-diag/logs/
ab-comparison/` — the code lives here for the Gradle/Kotlin scaffolding,
the data belongs there.

**run1 (2026-07-24):** Test 9 PASSED (backgrounded process persists), with
a quirk to expect again (launch call itself hits its timeout rather than
returning fast — daemon still starts regardless). Test 6 (CPU frequency
table) came back completely empty — fixed (split into one call per policy,
explicit raw-exec logging).

**run2 (2026-07-24):** Test 6 fix confirmed working — full CPU OPP table
captured and folded into `apl-diag`'s `HARDWARE_PROFILE.md` (this closes
the "still missing" item open since v6). Azahar (Citra 3DS fork) confirmed
as a fourth working app for the layer-matching heuristic. A new FPS
pipeline edge case found (idle-screen stale-buffer FPS decay, not a stall)
— see `xsu-capability-probe/FINDINGS.md`. **Phase 1 (Tests 1-9) is now
considered complete and validated** — both probe apps' capability
questions are answered; next work is Phase 2 (actual A/B comparison data).

## RESOLVED + major finding: `research/ayaspace-teardown/` + `research/aya-gamewindows-teardown/`

Both static-analysis passes complete (see each folder's own `FINDINGS.md`
for full evidence). Headline result, likely the most architecturally
consequential finding in the project so far:

**`com.ayaneo.gamewindow`'s `AyaAidlService` is `exported="true"` with no
`android:permission` and does zero caller-identity verification.** Any
installed app — no root, no `system` UID, no `xsu` — can bind to it
directly and drive `com_set_performance_mode` (Eco/Balanced/Streaming/
Gaming/Max), fan mode, GPU-fixed-frequency, RGB, and controller/key-mapping
commands, all through a plain Binder connection. `com.ayaneo.settings`
itself doesn't touch sysfs for the profile switch at all — it only relays
this same AIDL message; it doesn't need `xsu` because it runs
`sharedUserId=system`, a different app can't reuse that specific shortcut
but doesn't need to, since the AIDL service itself has no gate.

**CONFIRMED on-device, 2026-07-24** (see `research/aidl-bind-spike/FINDINGS.md`):
`apl` can skip `xsu` entirely for profile/fan/GPU-cap changes: no
~100ms-per-call floor, no risk of fighting AYASpace over the same sysfs
node (we ask gamewindow's own code to apply the change, not writing in
parallel), and fan control (previously an unconfirmable lever, likely
serial/EC-based) comes along for free — the AIDL callback delivers the
full per-mode config (fan mode, GPU frequency range, per-core CPU caps),
which also resolved the old "Gaming vs Max identical?" question (answer:
GPU max frequency cap only, 834MHz vs 1050MHz/uncapped — folded into
`apl-diag/docs/HARDWARE_PROFILE.md`). Bonus: the same command surface
covers controller/key remapping (`com_set_abxy_mode`, `com_set_l1l2r1r2_mode`,
`com_set_single_key_mapping`) — Module 2, deferred since the project's very
first README, might turn out to be nearly free, though not itself tested yet.

CPU/GPU sysfs mechanics (governor, per-core freq, `kgsl` max/idle-timer)
were independently reconfirmed at the command level in
`aya-gamewindows-teardown` and match what `apl` already knew — no changes
needed there regardless of which path (AIDL vs. own `xsu` writes) wins.

**`research/aidl-bind-spike/`** hand-rolls the undocumented Binder wire
protocol (no `.aidl` file exists, reconstructed from decompiled `Stub`/
`Proxy` classes) — two buttons, Gaming/Eco, each verified via the
already-proven `xsu` read-back. **Confirmed working, repeatably (3
mode-switches in one session, consistent each time).** Only whole-mode
switching tested so far — fine-grained `com_set_performance_fan`/`_cpu` and
the controller/key-mapping commands remain untested, not blocking.

## Next steps (rough priority order)

1. Run `research/aidl-bind-spike` on-device — see that project's own README
   for exact expected output for success and each distinct failure mode.
   This decides whether `apl`'s profile-mimic feature (and later
   `AutoTdpController`'s actuation side) uses AIDL-bind or `xsu` sysfs
   writes.
2. Scope and build the per-app profile-mimic feature (assign Eco/Balanced/
   Streaming/Gaming/Max to specific apps, auto-applied on foreground-app
   detection) — likely `apl/app/`'s first real (non-throwaway)
   functionality, architecture now depends on step 1's outcome.
3. Run actual Baseline vs AutoTDP sessions per game (see
   `research/autotdp-ab-harness`'s README test procedure — paired,
   order-swapped per game, NOT all
   baseline sessions then all autotdp sessions, which would confound mode
   with elapsed time/thermal carry-over). This is the "realistic input"
   needed before designing the FPS-delta-augmented v1 controller.
4. Decide `AutoTdpController`'s actual control signal and loop cadence,
   informed by the ~100ms-per-`xsu`-call floor documented in FINDINGS.md
   and by the A/B comparison data from step 3. Planned: selectable FPS
   target thresholds (60/120/144) once the core loop is stable — noted for
   later, not blocking current work.
4. Write the real `XsuShell.kt` (production quality, not probe quality) —
   the `args` method only, informed by the timeout/reliability findings.
5. First real increment per the KISS plan in README.md: a service that
   starts on boot and writes one confirmable log line.
