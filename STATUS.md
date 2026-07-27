# STATUS

Living document — update this in place at the end of a working session,
commit with a descriptive message. Do not create a new dated/versioned copy
of this file; `git log` is the history.

Remote: `git.internal.example/cox/AyaPulseLite` (Forgejo, self-hosted).

## To investigate next session: native FPS counter shows stale mode label + disappearing per-core frequencies

Raised by the user (2026-07-27) ahead of a new test series: AYA's own FPS
counter overlay (from AyaSettings) sometimes stops showing per-core CPU
clocks after `pulse-for-aya` runs, and its colored mode-name label ("Gaming
Mode") never seems to reflect that `pulse-for-aya` has put the governor in
`walt`. Investigated via docs/code only this session (no device access) —
two plausible-but-unconfirmed mechanisms found, and one real gap (the
overlay's own code was never located in either AYA teardown). Full writeup,
evidence, and the cheap on-device check that would confirm/rule out each
hypothesis: `research/pulse-for-aya/README.md`'s "Open question
(2026-07-27)" section, with a cross-reference in
`research/aya-gamewindows-teardown/FINDINGS.md`'s "Implications for apl"
list. Leading hypothesis for the disappearing core speeds
(`AutoTuneController`'s `aggressivePark` offlining cores) is the same lever
already flagged below in the Minecraft investigation — worth checking both
in the same session, may share one root cause.

## Minecraft/PULSE crash — ROOT CAUSE CONFIRMED (2026-07-27), new trigger candidate found

Observed on-device (2026-07-26): with `pulse-for-aya` active, native Android
**Minecraft fails to launch**; after a reboot with PULSE off, it launches
fine. Native Android **Stardew Valley launches fine with PULSE active** —
so this isn't "any app fails while PULSE runs", it's specific to something
Minecraft's launch path does. Not yet root-caused. Three testable
hypotheses, in order of suspicion, none confirmed yet:

1. **`aggressivePark`** (`AutoTuneController`) offlines the prime CPU cores
   when they're not the bottleneck — if Minecraft's engine sizes a thread
   pool off the live core count at launch, launching while cores are
   offline could fail outright rather than just run slower. Stardew Valley
   (simpler 2D engine) may not do this. Test: reproduce with
   `aggressivePark` OFF but the rest of PULSE active.
2. **`RefreshRateController`** forces `peak_refresh_rate`/`min_refresh_rate`
   via `settings put system` — could conflict with however Minecraft
   negotiates its rendering surface's display mode at creation.
3. **`GpuFloorController`** locks `min_pwrlevel`/`max_pwrlevel` (`chmod
   444`) — if the GPU is capped too low exactly when Minecraft's GL/Vulkan
   context initializes, context creation could fail.

**Next session's first data-gathering step**: capture `logcat` across the
exact moment of a failed Minecraft launch (ANR vs. crash vs. silent no-op
would each point to a different one of the three above), and check whether
`aggressivePark` was actually enabled in the session where this was
observed — that's the most invasive of the three levers and the easiest to
isolate first (retry with it off, everything else unchanged).

**Update (2026-07-27), user-reported, not yet instrumented**: the failure
is **non-deterministic and survives disabling PULSE app-side**. Sequence
observed: PULSE running → Minecraft fails (red Mojang screen, bounces to
home). Toggling `aggressivePark`/other options → still fails. **Turning
PULSE off in its own settings AND disabling AutoTDP → still fails** — only
a **full device reboot** clears it. After that reboot (PULSE confirmed
running, `aggressivePark` + 120 FPS target + everything else enabled),
Minecraft launched and looked stable — **until a ~5 minute session ended
in a UI/`system_server`-style crash**, the same failure shape as the
`system_server`/`BatteryService` incidents already in this file, requiring
another reboot to recover.

This changes the leading theory: since disabling PULSE at the app level
doesn't fix it, whatever is actually broken is more likely a **stuck
low-level state that outlives the app process** — e.g. a sysfs node PULSE
locked `chmod 444` (or wrote a value to) that its own disable/revert path
fails to fully restore, or a vendor perf/thermal daemon left in a bad
state — rather than something PULSE's live control loop does moment-to-
moment. Worth specifically checking next session: does toggling PULSE
fully off actually restore every CPU/GPU node's permissions AND values to
what they were before PULSE ever touched them, or does something stay
`444`/stuck? `PerformanceCommandBuilder`'s reset path (`isReset` →
`644`) is the first place to check for a gap.

**Tooling response this session**: `ab-logger` extended with continuous
root `logcat` capture (survives a reboot, since it writes straight to
`/sdcard` instead of buffering in-process) and per-sample `/sdcard` sync
(previously every 10 samples, so a crash-then-reboot could lose up to ~50s
of the most recent data before this) — see `research/ab-logger/README.md`'s
"Crash capture + crash-proof sync (2026-07-27)" section. Not yet
verified on-device. Running `ab-logger` alongside the next Minecraft
reproduction attempt should capture the actual crash signature directly,
instead of relying on manual `adb logcat` timing like past incidents did —
**but this is exactly the "PULSE + ab-logger during real gameplay"
combination this file's hard rule already warns about** (see
`CLAUDE.md`'s Hard rules and the INCIDENT entries below): only do this
closely supervised, watching for the same early warning signs (`xsud`
SIGABRT bursts, ANRs) that preceded past incidents, not as an unattended
background session.

**Update (2026-07-27), analysis of a real repro session** (6 CSVs, now at
`research/ab-logger/results/minecraft_crash_investigation/round1_2026-07-27_0915_old_ablogger/`
— folder reorganized/renamed in a later update below, see that update for
where round 2's data landed). **Reframes the problem**: this data does NOT
look like "Minecraft's launch path specifically fails" — it looks like
**the device crashes repeatedly and quickly (within 12-30s) any time
PULSE's live tuning is actually active (governor `walt`,
`pulse_service_running=true`), regardless of which app is in the
foreground**:

| session (start time) | duration | foreground app(s) seen | pulse active? | ended how |
|---|---|---|---|---|
| `_550309` (09:15:51) | 108s, 19 samples, clean | `retrohrai.launcher` → `android.settings` → `com.kei.pulse` (UI only) | **No** — governor `performance` throughout, `pulse_service_running=false` | ran the full session, no truncation |
| `_675652` (09:17:57) | 12s, 3 samples | `com.kei.pulse` → `retrohrai.launcher` | **Yes** — governor `walt`, `pulse_service_running=true` | **truncates mid-session**, no further rows |
| `_744486` (09:19:05) | 25s, 5 samples | `retrohrai.launcher` → `com.mojang.minecraftpe` (2-frame blip) → `retrohrai.launcher` (governor `walt`, freqs collapsing to 480-1286 MHz, fan_signal drops to 0 on the last row) | **Yes** | **truncates mid-session** |
| `_816264` (09:20:17) | 30s, 6 samples | `retrohrai.launcher` → `com.mojang.minecraftpe` (steady, FPS 111-120) | **Yes** | **truncates mid-session**, last row shows CPU temp spiking to **93.8°C** (vs. 45-90°C everywhere else in this data) two samples before the cutoff, with a battery-current reading that drops to a fraction of its neighboring rows on that same spike row |

The gaps between sessions are also informative: `_550309`→`_675652` is only
18s (looks like a deliberate manual stop/restart, e.g. to flip a PULSE
setting), but `_675652`→`_744486` is 56s and `_744486`→`_816264` is 47s —
both close to the ~25-30s auto-recovery window already documented for the
`system_server` restart pattern in this file's INCIDENT #1, not a full
manual power-cycle (those take longer). Working theory: this run hit the
same *kind* of crash multiple times in a row, most consistent with a
`system_server`-style crash-and-auto-restart rather than a hard reboot each
time — separate from (or an earlier-stage version of) the harder crash
requiring a full reboot described in the update above.

**Caveat, important for next session**: none of these six pulls include a
`logcat_*.log` file, meaning the device was very likely still running the
**pre-crash-capture build** of `ab-logger` (the feature landed in this same
session, after this data was gathered) — so the exact crash signature
(`FATAL EXCEPTION` vs ANR vs something else) is still not confirmed from
this run. Also odd, not yet explained: `pulse_installed` reads `false` for
the entire first session (`_550309`) despite `com.kei.pulse` appearing as
the foreground app in that same session, then reads `true` for every
session after — either PULSE was genuinely reinstalled partway through
this test run, or the on-device `ab-logger` build predates the
`pulse_installed` package-visibility fix from the 2026-07-26 session
(worth checking which `ab-logger` version is actually installed before
trusting that column again).

**Next session**: reinstall the latest `ab-logger` build (has the crash
capture + per-sample sync from this session) before the next reproduction
attempt — that should finally pin the exact crash signature and confirm
whether it's tied to the 93.8°C thermal spike or something else entirely.

**Update (2026-07-27), round 2 with the new `ab-logger` build — different
failure shape than round 1, still no `logcat`.** Four more sessions pulled,
now organized under
`research/ab-logger/results/minecraft_crash_investigation/`
(`round1_.../` = the batch analyzed above, `round2_.../` = this batch;
`NOTES.md` in that folder indexes every session file, this entry has the
actual analysis — don't duplicate it there).

- `_597194` (26 samples, 144s): Minecraft under stock `performance`
  governor (**PULSE not actively tuning**, `pulse_service_running=false`
  throughout) — hit 90-96°C repeatedly (rows 2, 4, 17, 20-22), same
  thermal territory as round 1's crash-adjacent readings, and **did not
  crash**: ran the full session, ended cleanly (last row shows the user
  back in `ab-logger`'s own UI to hit Stop). Reinforces that raw thermal
  load alone isn't sufficient to trigger this — PULSE has to be actively
  tuning.
- `_879294` (27 samples): PULSE active (`walt`) from row 0. Minecraft plays
  normally for ~130s (rows 1-21, temps and FPS both unremarkable, nothing
  like round 1's 93.8°C spike), user played for a bit. **User-confirmed
  (2026-07-27): this is the session where exiting Minecraft triggered the
  same UI crash as before** — device slows down, boot logo appears, then
  stays unresponsive for a while — not a clean Minecraft exit as first
  guessed from the CSV alone. Revises the earlier read of rows 22-26
  (`frame_count` collapsing to 0-3, foreground bouncing between
  `retrohrai.launcher`/`com.android.launcher3`/blank): this is most likely
  **`ab-logger` capturing the crash-and-recovery window itself, in real
  time** — nothing is genuinely rendering during those ~30-40s (consistent
  with a boot-logo/recovery screen, not a real launcher), but critically
  **`ab-logger`'s own sampling loop and `xsu` polling stayed alive and kept
  producing rows throughout** — unlike every round-1 crash, which just went
  silent. First time this repo has any telemetry from *inside* one of these
  crashes instead of only before/after it.

**Still no `logcat_*.log` exists on-device** — user-confirmed via `ls
/sdcard/apl_ab_logs` on the device itself, only the 4 CSVs are there, so
this isn't a missed-pull, the file was genuinely never created. Given
`autotdp-ab-harness`'s Test 9 already proved a backgrounded `xsu`-launched
process (the old `pulse_lite_v3.7.sh` daemon) CAN persist past its
invoking call's own timeout, plain backgrounding isn't the general
problem — something specific to `startCrashCapture()`'s command is. Not
yet root-caused; candidates, in rough order of suspicion: (1) this
device's `xsud` might execute multi-statement `-c` strings through its own
lightweight interpreter rather than a real POSIX shell with full job
control, in which case trailing `&` may not mean "detach" the way it does
in `/system/bin/sh` — worth testing name-brand `nohup`/`logcat` availability
directly; (2) `xsud`'s already-documented per-connection cleanup (crashes/
reforks on connection close elsewhere in this repo) might specifically
target backgrounded children this time, unlike Test 9's daemon-launch case.
**Cheapest next diagnostic** (one manual `xsu` call, no app rebuild
needed): `xsu -c "date > /sdcard/apl_ab_logs/t1.txt; (sleep 20; date >
/sdcard/apl_ab_logs/t2.txt) &"`, then check with `ls -la
/sdcard/apl_ab_logs/t*.txt` immediately and again after 25s+ — `t1.txt`
appearing confirms basic redirect+exec works (expected), whether `t2.txt`
ever appears is the actual test of whether anything backgrounded survives
past the connection at all on this specific `xsud` build.

**Next session**: run the `t1.txt`/`t2.txt` diagnostic above before
touching the app again — it'll tell us in one call whether backgrounding
is salvageable at all on this `xsud`, before sinking more time into
`startCrashCapture()` specifically.

**Update (2026-07-27): `pulse-for-aya` rebuilt with `schedutil` instead of
`walt` for Balanced/AutoTDP** — every crash repro so far ran with `walt`
active (never confirmed as causal, but it's a governor AYASpace itself
never uses on this SoC, cheapest single variable to rule out). See
`research/pulse-for-aya/README.md`'s "Open question (2026-07-27)" section,
point 2's update. **Next Minecraft repro should use this build** and note
whether the crash still happens with `schedutil` — if it does, `walt`
itself is cleared as a suspect and the search moves elsewhere (e.g.
`aggressivePark`, the write/chmod cadence); if it doesn't, that's a strong
signal without needing the still-unresolved `logcat` capture at all.

**Update (2026-07-27): it crashed again under `schedutil` — governor choice
is cleared as the cause.** Three new sessions pulled to
`research/ab-logger/results/minecraft_crash_investigation/round3_2026-07-27_1019_schedutil_test/`
(`NOTES.md` there has the file-level index). `_454633`: Minecraft launches,
AutoTDP engages `schedutil` at row 6 with a temp spike to **93.0°C** —
notably the same shape as round 2's 93.8°C spike right as PULSE engaged,
just with a different governor underneath — plays fine for ~47s afterward
(temps back to 55-65°C, steady FPS), **then the session truncates with no
further rows**, same abrupt-cutoff shape as round 1's crashes. Not yet
confirmed by the user whether this matched the same physical symptom (UI
slowdown → boot logo → unresponsive) as before — worth confirming, but
given every truncation in this investigation so far has matched a real
observed crash, treat it as one until shown otherwise.

**This rules out `walt` as the root cause** — the crash isn't tied to
which governor is active, it reproduces under the vendor's own validated
choice too. Revised leading theory: **the trigger is PULSE's AutoTDP
control loop actually being active/writing** (the `chmod
666`→`echo`→`chmod 444/644` dance on `scaling_max_freq`/`min_pwrlevel`
every time it trims/raises a cap, at whatever cadence `AutoTuneController`
polls), not the specific governor or raw heat alone — `_597194` (round 2)
proved 96°C alone with PULSE inactive is fine. The **temp spike exactly at
the moment PULSE engages**, seen in both round 2 and round 3, is also
worth treating as a real correlate now that it's shown up twice, not a
one-off. Candidates still open: `aggressivePark` state (still not logged
by `ab-logger` — unknown whether it was on for either crash), the write/
chmod cadence itself, or something in `RootExec`/`xsu` call concurrency
under real gameplay load (echoing the older, weaker "concurrency" trigger
hypothesis from `xsu-capability-probe/FINDINGS.md`).

**Next session, in priority order**: (1) the `t1.txt`/`t2.txt` backgrounded-
`xsu` diagnostic above is now higher priority, not lower — governor-
swapping is exhausted as a cheap lever, and getting `logcat` working is the
most direct way to actually see what crashes instead of continuing to
guess from CSV shape; (2) if logcat stays unreachable, next cheapest lever
is testing with `aggressivePark` explicitly OFF (confirm/deny that
specific suspect) while everything else stays as-is.

**Update (2026-07-27): `t1.txt`/`t2.txt` diagnostic ran — backgrounding
survives fine on this `xsud`.** Both files appeared exactly as expected
(`t2.txt` present ~20s later), so the earlier "maybe `xsud` kills
everything backgrounded on connection close" theory is **ruled out** —
whatever's wrong with `ab-logger`'s own `startCrashCapture()` is a bug
specific to that command, not a fundamental platform limitation. Not yet
root-caused (still worth revisiting — try the exact `logcat -c; nohup
logcat ... &` sequence by hand next, piece by piece, since the general
mechanism now provably works). Lower priority now given what follows.

**Update (2026-07-27): real crash captured via host-side `adb logcat`,
bypassing `ab-logger`'s broken capture entirely — root cause confirmed.**
Two files pulled to
`research/ab-logger/results/minecraft_crash_investigation/round4_2026-07-27_1035_schedutil_logcat_capture/`
(`minecraft_crash_20260727_103555.log` is the one with the actual crash).
User-confirmed physical symptom matches every prior repro exactly: game
freezes, boot logo, device unresponsive until forced restart.

**The crash is confirmed to be the exact same failure as the original
2026-07-25 INCIDENT, recurring** — not a new or different bug:
`BatteryService$Led`'s charging-LED animation
(`BatteryService.java:1276`, `updateLightsLocked`/`startAlphaAnimator`)
calls `ILights.setLightState()`, the HAL returns
`ServiceSpecificException` code `-13`, **uncaught**, which crashes
`system_server` itself (`*** FATAL EXCEPTION IN SYSTEM PROCESS`,
10:36:39.790). Android kills the crashed `system_server` (`Process:
Sending signal. PID: 2262 SIG: 9`) and fully reinitializes it (the same
`PackageWatchdog: ... INACTIVE -> PASSED` cascade for every system package,
dozens of lines) — this reinit, not a true kernel-level reboot, is what
presents to the user as the device slowing down / boot-logo-then-
unresponsive. Four separate `xsud` `Fatal signal 6 (SIGABRT)` crashes
(the already-documented per-connection-cleanup crash pattern) also fire in
the ~58s leading up to the `system_server` crash, consistent with the
existing "sustained `xsu` load contributes to timing pressure on this HAL
call" theory.

**New finding — a candidate trigger, from OUTSIDE this repo's code**: the
device was **on the charger throughout this session**
(`PowerUI: ... plugged: true`, confirmed repeatedly in the log, flipping
to `plugged: false status unknown: true` only at the exact moment
`system_server` dies — consistent with that being a symptom of the crash,
not a separate event). Throughout the session, something **not part of
`ab-logger` or `pulse-for-aya`** (confirmed by grepping both apps' source
— no match) repeatedly runs `echo 1 > /sys/class/power_supply/battery/online`
through the same shared `xsu`/`xsud` channel our apps use — 12 times over
~64 seconds, in irregular bursts (not a steady poll interval), the last
one just 6 seconds before the crash. Forcing `battery/online` would make
`BatteryService` reprocess charging state, which is exactly the code path
that walks into the crash site (`updateLightsLocked` → the charging-LED
animation). **Source of this write is unidentified** — not this repo's
code, so either a pre-existing vendor/ROM daemon or another installed app,
both sharing the same system-wide `xsu` root channel. Ask the user: any
known charging-control app or ROM behavior that would explain this?

**This reframes the investigation**: `aggressivePark`, the specific
governor, and PULSE's write cadence are all still plausible *contributing*
load, but the actual crash site was never CPU/GPU-frequency code at all —
it's `BatteryService`'s charging-LED animation, and the newly-found
`battery/online` writes are a much more direct, specific match for
"repeatedly re-triggers exactly the code path that crashes" than anything
considered so far.

**Next session, cheapest and most direct test**: reproduce with the device
**unplugged from any charger** (battery power only) — if
`battery/online`-forcing is only happening while charging (very likely,
given the name), removing the charger should stop it from firing at all,
which directly tests whether this is the trigger. No code change needed
for this test. If the crash still happens unplugged, this candidate is
ruled out and the `chmod`/write-cadence or `aggressivePark` theories move
back to the top.

**Update (2026-07-27): testing unplugged means no `adb logcat` fallback —
`ab-logger`'s own `startCrashCapture()` rewritten.** Unplugging the USB
cable to properly test "not charging" also kills the host-side `adb
logcat` capture that got round 4's real signature, so `ab-logger`'s own
in-app capture (which had produced zero output across two full rounds)
needed fixing first. Root-caused via the `t1.txt`/`t2.txt` proof above:
the original command backgrounded only its tail statement with `nohup ...
&` (mirroring nothing that was ever actually tested); rewritten to
background the whole `logcat -c; logcat -v threadtime >> file` sequence
inside one `(...)  &` subshell instead, matching the diagnostic's proven
shape exactly, and dropping `pkill`/`nohup` (neither confirmed present on
this device, and the diagnostic proved `nohup` isn't needed here anyway).
See `research/ab-logger/README.md`'s "Crash capture + crash-proof sync"
section for the full before/after. Builds clean, **not yet verified
on-device** — the next unplugged repro is the real test, and this time
it's the only log source available if it doesn't work.

**Update (2026-07-27): crash reproduced unplugged too, `ab-logger`'s fixed
capture worked, and it caught a DIFFERENT crash mechanism — `battery/online`
theory is weakened, not the whole story.** Two file pairs pulled to
`research/ab-logger/results/minecraft_crash_investigation/round5_2026-07-27_1053_unplugged_new_ablogger/`
(`NOTES.md` has the file index). The real capture
(`logcat_1785142413182.log`) shows the fixed `startCrashCapture()` finally
producing real output — confirms that fix works.

**This crash is NOT the `BatteryService`/`system_server` one from round
4.** This time it's `com.mojang.minecraftpe`'s own process aborting
directly:

```
E XNNPACK : failed to parse the list of possible processors in /sys/devices/system/cpu/possible
E XNNPACK : failed to parse the list of present processors in /sys/devices/system/cpu/present
F XNNPACK : cpuinfo_get_packages_count called before cpuinfo is initialized
F libc    : Fatal signal 6 (SIGABRT) ... pid 9230 (ang.minecraftpe)
Abort message: 'cpuinfo_get_packages_count called before cpuinfo is initialized'
```

Minecraft bundles Google's `cpuinfo` library (via XNNPACK, its ML/inference
backend) to detect CPU topology at startup. It tries to read
`/sys/devices/system/cpu/possible` and `.../present` — two standard,
normally-static kernel files listing which CPU indices exist — and both
reads come back unparseable, which crashes `cpuinfo` init and, downstream,
Minecraft's own process (crash, not an ANR, not `system_server` this
time). This happened ~2 seconds into Minecraft's launch
(`Process uptime: 2s` in the tombstone), immediately after its Vulkan/
`GameActivity` init.

**This weakens (doesn't kill) the `battery/online` theory**: this crash
happened fully unplugged, no charger, and has nothing to do with
`BatteryService` at all — so `battery/online`-forcing can't be the *only*
mechanism at play. **Confirms the broader reframe from the last update
though**: PULSE's activity is destabilizing more than one subsystem, not
one narrow bug — round 4 broke `BatteryService`'s charging-LED path, round
5 breaks Minecraft's own CPU-topology detection. Both crash sites read
CPU-topology-adjacent kernel state; both happen right as PULSE/AutoTDP is
active.

**Timeline right before the crash** (from `logcat_1785142413182.log`):
in the ~600ms immediately before the `XNNPACK` failure, the only `xsu`
activity was `ab-logger`'s own routine sampling (`chmod 750 /storage;
setenforce 0` — **not this repo's code either**, grepped both apps' source,
no match; likely an AYASpace/ROM "game launch prep" hook reacting to
Minecraft coming to foreground, sharing the same `xsu` channel again, same
pattern as `battery/online`/`core_ctl/min_cpus` from round 4).
`aggressivePark`'s core-unpark (`cpu2/3/4 online=1`) and AutoTDP's
governor/freq-cap reset both fire ~350-450ms **after** the crash, not
before — consistent with `AutoTuneController` reacting to the sudden stall
(Minecraft dying), not causing this particular instance. That doesn't
clear `aggressivePark` as a suspect, though: whether cores were already
parked *before* this log's window started (3.5s before the crash) is
unknown — an earlier park cycle from earlier in the same session could
still have left `cpuN/online` state disrupted right when Minecraft's
`cpuinfo` init ran.

**Answering the user's question ("could this be a PULSE option?")**:
`aggressivePark` remains the single most mechanistically plausible PULSE
lever — it's the only thing in `pulse-for-aya`'s own code that touches
`cpuN/online`, and `possible`/`present` are exactly the kind of
CPU-topology sysfs paths a hotplug operation could transiently disrupt for
a concurrent reader. Not proven, but now the most concrete, testable
PULSE-side suspect.

**Next session, most direct test**: reproduce with `aggressivePark`
explicitly turned OFF, everything else the same (still unplugged, still
`schedutil`) — if the crash (either shape) stops, `aggressivePark` is
confirmed as at least one real trigger; if it still happens, the remaining
suspects are the write/chmod cadence itself or contention with the other
(non-PULSE) actors sharing `xsu` around app-launch moments.

**Update (2026-07-27): `aggressivePark` OFF — crash still happens. Ruled
out as sole/necessary trigger. But this session's logcat has the
`xsud` crash's own backtrace for the first time, and it points at a real
bug in the vendor binary itself, not any app.** Pulled to
`research/ab-logger/results/minecraft_crash_investigation/round6_2026-07-27_1225_unplugged_aggressiveparkoff/`.
Minecraft played fine for ~80s under `schedutil` (14 CSV samples, temps
unremarkable, nothing like the earlier spikes) then the CSV just stops —
same abrupt-cutoff shape as before, `aggressivePark` explicitly off this
entire session.

**The `xsud` `Fatal signal 6 (SIGABRT)` crashes (four of them in round 4,
one here) all share the exact same backtrace**:

```
#01 pc ... /apex/com.android.runtime/lib64/bionic/libc.so (__stack_chk_fail+24)
#02 pc ... /product/bin/xsud (xsu_conn_handler.cfi+856)
```

`__stack_chk_fail` means a stack buffer overflow was *detected* (the
stack canary tripped) inside `xsud`'s own `xsu_conn_handler` — **this is a
real bug in the vendor's root-helper binary itself**, not in
`pulse-for-aya`, `ab-logger`, or anything else in this repo. Confirmed
identical across every `xsud` crash pulled so far, both rounds, both
`walt` and `schedutil`, plugged and unplugged — this is a stable,
reproducible signature, not noise.

**Bonus finding, explains a real gap in `ab-logger`'s own capture**: this
session's `logcat` file stops at 12:25:37 (right at one of these `xsud`
crashes), but the CSV kept sampling successfully for another **~75
seconds** after that, until 12:26:52. The backgrounded `logcat` pipe is
the *one* long-lived `xsu` connection in this whole system — everything
else (CSV samples, one-off writes) is a fresh short-lived connection per
call. If `xsu_conn_handler`'s overflow is more likely to trigger on a
connection that stays open a long time, our own capture connection is
exactly the kind of connection most exposed to it — explaining why the
capture keeps cutting off before the real end of a session. Not yet
mitigated; the honest fix (making the capture connection itself survive,
or restart on its own if killed) is nontrivial given `xsud`'s behavior is
outside our control, and might not be worth chasing further given what
follows.

**This reframes "why does Minecraft specifically trigger this" — probably
not anything Minecraft's code does wrong, more likely Minecraft is just
unusually good at creating a BURST of concurrent `xsu` connections at
launch.** Every crash reproduction so far has multiple *independent*
actors hitting `xsu` in the same narrow window right as Minecraft becomes
foreground: `pulse-for-aya`'s own AutoTDP reacting to the new foreground
app (governor/freq-cap writes), `ab-logger`'s own polling, and at least
two confirmed-not-ours actors from earlier rounds (`battery/online`,
`core_ctl/min_cpus`, the `chmod 750 /storage; setenforce 0` pair) that are
most likely AYASpace's own native "game launch" hook reacting to the same
foreground-app change and *also* going through `xsu`. If `xsu_conn_handler`
has a real concurrency bug (plausible for a stack-overflow triggered
inconsistently, not on a fixed input), a pile-up of several apps'
root-shell calls landing within the same short window — which Minecraft's
heavier launch reliably produces and something like Stardew Valley
apparently doesn't (see the very first entry in this investigation) — is
a believable trigger completely independent of which specific PULSE
option is active. This would explain why swapping governors and disabling
`aggressivePark` both failed to stop it: neither actually reduces how many
*other* things hit `xsu` at the same moment.

**This is likely NOT independently fixable from this repo** — `xsud` is a
closed vendor binary (`/product/bin/xsud`), and AYASpace's own contribution
to the connection pile-up is outside our control too. The most useful
remaining question is whether `pulse-for-aya`'s own reaction to a
foreground-app change can be made to avoid piling onto that same narrow
window (e.g. a small delay before AutoTDP's first write on a fresh
foreground app, giving AYASpace's own hook room to finish first) — worth
trying, but this is now a mitigation for a vendor bug, not a fix for
anything in our own code being wrong. Getting a real crash-window
`logcat` via wireless `adb` (the user is investigating SSH-tunneled `adb`)
would help confirm this theory more directly than continuing to guess from
timing correlation alone — it would show, for the SAME test, exactly which
other processes' `xsu` connections are open at the moment `xsu_conn_handler`
actually crashes.

**Update (2026-07-27): cable back in, full host-side `adb logcat` capture
of another crash, filed to
`research/ab-logger/results/minecraft_crash_investigation/round7_2026-07-27_1244_cable_host_capture/`
(`minecraft_crash.log`, 8MB, captured simultaneously with `ab-logger`'s
own — much shorter — capture, `session_1785149052936.csv`/
`logcat_1785149052936.log`). Answers the user's question ("is there a timing pattern, like always
~60s?") directly: no fixed timer, but a real, consistent qualitative
pattern across every `system_server`-crash instance pulled so far.**

**Timeline of this session** (t=0 at `cmd game set --mode 1
com.mojang.minecraftpe`, i.e. the moment Android's Game Manager — and by
extension PULSE, which reacts to the same foreground-app change — notices
Minecraft):

| t (s) | event |
|---|---|
| 0 | Game Mode set for Minecraft; `pulse-for-aya` sets governor to `schedutil` ~0.9s later |
| +4.2 | 1st `xsud` crash (`xsu_conn_handler` stack overflow, same signature as always) |
| +20.3, +40.4, +60.5 | `ANR in com.qti.diagservices` — suspiciously regular, ~20.1s apart every time; unrelated background noise on this device, not caused by us, but a useful clock |
| +41.5 | 2nd `xsud` crash (37.3s after the 1st) |
| +52.5 | 3rd `xsud` crash (11.0s after the 2nd) |
| +62.8 | 4th `xsud` crash (10.3s after the 3rd) |
| +72.5 | 5th `xsud` crash (9.7s after the 4th) |
| **+73.4** | **`system_server` `FATAL EXCEPTION`** (same `BatteryService$Led` site as round 4) |
| +73.5-+79.4 | cascade: `Performance-Timer` thread (a **system app**, SELinux `system_app` context — almost certainly AYASpace's own native perf-monitor, seen elsewhere in this same log issuing AVC-denied reads of `gpuclk`) crashes, a binder thread crashes, a process named `init` crashes, an `AsyncTask` crashes — dependent Binder callers going down together as `system_server` restarts, not new independent bugs |

Cross-checked against round 4's four `xsud` crashes
(`10:35:40.913 → 10:36:18.336 → 10:36:28.594 → 10:36:39.540`, gaps `37.4s,
10.3s, 10.9s`) — **the exact same shape**: one long quiet gap first, then
crashes clustering tighter and tighter (~10s apart) in the final
20-30 seconds before `system_server` goes down. Total time from Game-Mode-
activation to crash: **~59s in round 4, ~73s in this session** — same
rough ballpark, not identical, and not a fixed countdown.

**Answer to "what triggers it, is it time-based"**: not a timer — a
**cumulative degradation**. Each `xsud` connection-handler crash seems to
leave the daemon a little more fragile (consistent with a resource leak or
corrupted state surviving the crash-and-refork cycle already documented
elsewhere in this repo), so crashes come faster and faster the longer
concurrent `xsu` traffic continues, until one lands badly enough to take
`system_server` down with it. The "~60-70s" the user noticed is real as a
*rough* characteristic timescale for this particular failure mode, but
it's downstream of "how long it takes concurrent `xsu` load to degrade
`xsud` enough," not a hardcoded interval — expect it to vary with how much
`xsu` traffic is actually happening (more concurrent pollers = faster;
round 5's completely different `XNNPACK`/`cpuinfo` crash happened in ~2
seconds flat, because it's not this mechanism at all).

**New concrete confirmation of the "other actors" theory**: this log
directly shows a **system-privileged app** (SELinux `system_app` context,
process tag `Performance-Tim[er]`) reading `gpuclk` and other `kgsl`
paths — independent confirmation that something AYASpace-side is actively
polling GPU state concurrently with `pulse-for-aya`/`ab-logger`, not just
inferred from `xsu`/`core_ctl` writes as before.

**`ab-logger`'s own capture died at the very first `xsud` crash again**
(`logcat_1785149052936.log` stops at the same timestamp as the 1st `xsud`
crash in the table above) — confirms this is a reliable, reproducible
limitation of the in-app approach, not a one-off; the host-side `adb
logcat` capture remains the way to get a full picture.

**Update (2026-07-27): third data point, this time WITHOUT `ab-logger`
running at all — same pattern, `ab-logger` cleared as a necessary
contributor.** One more host-side capture, user-named
`minecraft_crash_no_ablogger.log` (9.5MB, filed alongside round 7's other
files), specifically to test whether `ab-logger`'s own polling was part of
what triggers this. It wasn't running this time. Timeline: Game Mode set
for Minecraft at 12:49:36.762, four `xsud` crashes at 12:49:36.457†,
12:50:14.232, 12:50:25.799, 12:50:37.262 (†first one lands basically
simultaneously with Game Mode activation — gaps between the 4:
**37.8s, 11.6s, 11.5s** — the same long-gap-then-tightening shape as
every prior instance), then `system_server` `FATAL EXCEPTION` at
12:50:46.001 — **8.7s after the last `xsud` crash**, **69.2s after Game
Mode activation**. Same ~20.1s-spaced `ANR in com.qti.diagservices`
background noise throughout, unaffected by `ab-logger`'s absence
(confirms that one really is unrelated to anything in this repo).

Three system_server-crash instances now on record, all the same shape:

| capture | time (Game Mode → crash) | xsud crash gaps |
|---|---|---|
| round 4 | ~59s | 37.4s, 10.3s, 10.9s |
| round 7 (`minecraft_crash.log`) | ~73s | 37.3s, 11.0s, 10.3s, 9.7s |
| round 7 (`minecraft_crash_no_ablogger.log`) | ~69s | 37.8s, 11.6s, 11.5s |

Consistent within roughly a 15-second band, always the same qualitative
shape, `ab-logger` present or not — strengthens the "cumulative `xsud`
degradation under concurrent `xsu` load" theory and narrows "concurrent
actors" down to just `pulse-for-aya` itself plus whatever AYASpace-side
hooks fire on a foreground-app change (`ab-logger`'s own polling is
confirmed *not* required to reproduce this).

**Update (2026-07-27): mitigation path started — replace `pulse-for-aya`'s
own `xsu` calls with AIDL where possible, to shrink its contribution to
the connection burst.** Can't fix `xsud` itself (closed vendor binary)
or AYASpace's own footprint, but we control `pulse-for-aya`'s side of it.
Reading `com.ayaneo.settings`'s decompiled source turned up a much richer
AIDL command surface than previously known — not just the whole-profile
`com_set_performance_mode`, but per-core frequency, GPU cap, CPU
scheduler, and fan mode too, all pure AIDL with zero sysfs writes on the
`ayasettings` side. Full detail, exact command formats, and what
landed vs. what's still untested: `research/pulse-for-aya/README.md`'s
"AIDL migration, step 1" section. Short version: a new, isolated
`AyaAidlClient.kt` exists with typed senders for the whole surface, wired
to two debug-build-only verification hooks in `MainActivity.kt` (one
safe/automatic bind check, one opt-in marker-file-gated governor-set
test) — **nothing live-control-path-facing changed yet**, this is purely
additive and not yet exercised on-device. Worth noting: pursuing this
pushes `pulse-for-aya` further from upstream `pulse` (AIDL only exists on
AYANEO hardware) — the user has accepted this is now a real
AYANEO-specific fork, not a thin glue patch, going in.

**Confirmed on-device (2026-07-27), first try**: `sendScheduler("BALANCED")`
genuinely works from `pulse-for-aya`'s own signed context —
`readback(policy0 governor)=schedutil` after the send, zero `xsu` calls
used to set it. This is real evidence the mitigation direction holds, not
just a plausible theory — see `research/pulse-for-aya/README.md` for the
full log.

**Update, same day: `sendGpuFrequency` confirmed working too;
`sendCpuFrequency` works but only for single-core policies.** GPU: clean
round-trip (set 366000000, read back 366000000, restore, read back
original). CPU: first test (`cpuId=0`, shares `policy0` with `cpu1`) sent
without error but never actually changed anything — read back from both
`policy0/scaling_max_freq` and `cpu0/cpufreq/scaling_max_freq`, neither
moved. Second test against `cpuId=7` (the sole core in `policy7`, a real
independent hardware frequency domain, unlike the shared clusters)
**worked cleanly** — set, read-back-confirmed, restored, read-back-confirmed
again. So: this AIDL command can set a lone-core policy directly, but
silently no-ops for a multi-core cluster addressed one `cpuId` at a
time — not yet tested whether sending every constituent `cpuId` of a
shared policy together unlocks it. Full logs and detail:
`research/pulse-for-aya/README.md`'s "AIDL migration, step 1" section.

**Realistic scope check, answering the user directly**: this does NOT
fully replace `xsu` or give "complete AIDL control." AIDL has zero read
capability at all — AutoTDP's continuous FPS/thermal/frequency monitoring
stays on `xsu` regardless. What's confirmed usable so far (scheduler, GPU
cap, and `policy7`'s CPU cap) covers the *foreground-app-change reaction*
specifically — the exact collision window this mitigation targets — not
AutoTDP's ongoing live-tuning writes for the 3 multi-core clusters, which
still need `xsu` unless the shared-policy question above resolves
favorably. A meaningful, targeted reduction in `pulse-for-aya`'s own `xsu`
footprint at the riskiest moment, not a full replacement.

**Update, same day: the shared-policy question resolved, with a caution
attached.** Sending both `cpu0` and `cpu1` (both `policy0`) together
*does* unlock the write — lowering both to `787200` in one shot worked
cleanly. But sending them back up to the original `2265600` the same way
did **not** take effect — device was left with `policy0` genuinely capped
at `787200` (safe, just underpowered) until fixed by hand with a plain
`xsu` write. Root cause not understood (asymmetric command behavior vs. an
unlucky Binder-call race, indistinguishable from this one data point).
**Consequence for step 2**: `com_set_performance_cpu` is demonstrably less
trustworthy than `sendScheduler`/`sendGpuFrequency` (both round-tripped
cleanly every time) — don't lean on it for anything safety-relevant
without a confirmed, reliable restore path. Doesn't block the actual
mitigation goal, which only needs the scheduler/GPU commands at the
foreground-change moment. Full detail:
`research/pulse-for-aya/README.md`'s "AIDL migration, step 1" section.

**Where this whole investigation stands, end of 2026-07-27 session** (read
this first if picking the thread back up): started from "Minecraft crashes
PULSE" → traced to a real stack-overflow bug in the vendor's `xsud` binary
(`xsu_conn_handler`, confirmed via matching crash backtraces across every
capture) → governor choice and `aggressivePark` both directly ruled out as
the trigger (crash reproduces under `schedutil` and with `aggressivePark`
off) → current best theory is concurrent `xsu` connection bursts at
app-launch time, from multiple actors we don't control (AYASpace's own
native hooks) plus `pulse-for-aya`'s own traffic, which we DO control →
started an AIDL migration (`AyaAidlClient.kt`) to shrink `pulse-for-aya`'s
own contribution to that burst, confirmed `sendScheduler`/`sendGpuFrequency`
work reliably, `sendCpuFrequency` works but is unreliable (a stuck-capped
core had to be manually fixed once already) and isn't required for the
mitigation goal anyway. **Nothing has touched the live control path yet**
— `ForegroundAppMonitorService.kt` is untouched, all AIDL work so far is
isolated debug-build-only verification hooks in `MainActivity.kt`.
**Next concrete step (step 2, not started)**: replace the `xsu`-based
governor write in `ForegroundAppMonitorService`'s foreground-app-change
handling with `AyaAidlClient.sendScheduler`, then re-run the same
Minecraft-crash repro + timing methodology from this session (Game-Mode-
activation → crash, compare against the 59s/73s/69s baseline already on
record) to see whether it actually helps. GPU cap could follow the same
pattern once CPU governor is proven out.

**Update (2026-07-27): step 2 build's first on-device test hit round 5's
OTHER crash mechanism twice before AutoTDP ever engaged — inconclusive
for step 2 itself, but a new confirming data point for round 5.** Log:
`research/ab-logger/results/minecraft_crash_step2_test.log` (host-side
`adb logcat`, cable connected). Minecraft launched twice in a row
(auto-relaunch), both times crashing its own process within ~0.6s
(`Fatal signal 6 SIGABRT`, `cpuinfo_get_packages_count` — the exact
XNNPACK/`cpuinfo` signature from round 5), both times immediately
preceded by the same non-repo `chmod 750 /storage; setenforce 0` hook
already implicated there. No device reboot, matching round 5. Critically:
`PulseAidl: bind ready=true` fired at 14:11:39 (well before either
crash), but **no `engage governor via AIDL`/`...via xsu fallback` log
ever appears** — `startAutoTdp()` never ran, because the crash lands
faster (~0.6s post-launch) than `pulse-for-aya`'s foreground-poll loop
reacts. So this run says nothing about whether step 2 helps — it hit an
earlier, already-documented, pulse-independent failure mode before
AutoTDP (and therefore the governor write step 2 changed) ever got a
chance to run. **Next attempt** (user rebooting and retrying): if
Minecraft gets past this early crash and plays long enough to reach the
~60-70s AutoTDP-engaged window, check `PulseAidl` log lines to confirm
which path (AIDL vs. xsu fallback) actually fired, then compare
crash-timing against the 59s/73s/69s baseline as originally planned.

**Update (2026-07-27): step 2's real on-device test — AIDL path confirmed
working, but the crash still happens on baseline timing. The single-call
migration does not measurably help.** Second restart+retry, log
`research/ab-logger/results/minecraft_crash_step2_test_restart.log`
(host-side `adb logcat`, cable connected, no `ab-logger` running). This
time Minecraft got past the early `cpuinfo`/XNNPACK crash and played long
enough for AutoTDP to engage — `PulseAidl: engage governor via AIDL`
fires at 14:14:12.210, confirming the governor-set genuinely went through
AIDL with zero `xsu` calls, exactly as designed.

**The `system_server`/`BatteryService$Led` crash happened anyway**, same
exact site as every prior instance (`ServiceSpecificException` code -13 in
`updateLightsLocked` → `ILights.setLightState`). Timing: Game Mode ON
14:14:05.728 (t=0) → 3 `xsud` `xsu_conn_handler` crashes at t=+5.9s,
+44.7s (gap 38.8s), +55.2s (gap 10.5s) → `system_server` `FATAL EXCEPTION`
at t=+60.2s (gap 5.0s from the last `xsud` crash). Same "one long gap then
tightening" shape as every prior capture, and **60.2s sits squarely inside
the pre-existing 59s/73s/69s baseline band** — not faster, not slower,
not fewer `xsud` crashes in any way that reads as improvement. Device was
plugged in throughout (`plugged: true` until the crash, same as round
4/7); the non-repo `battery/online` write appeared only 3 times this
session (vs. round 4's 12) — session-to-session variance in that actor's
own behavior, not something this repo controls either way.

**Conclusion**: removing this one `xsu` connection (the AutoTDP-engage
governor write) is confirmed NOT sufficient to prevent, delay, or
visibly soften this crash. This matches the risk already flagged when
step 2 was scoped — AYASpace's own foreground-change hooks
(`chmod 750 /storage; setenforce 0`, `battery/online`, `core_ctl/min_cpus`
seen in earlier rounds) are still piling onto the same `xsu` channel in
the same window regardless of what `pulse-for-aya` does, and those are
outside this repo's control. One fewer call from our side doesn't reduce
the pile-up enough to matter. **Not a wasted step** — the AIDL path
itself is now proven reliable in a real crash-reproduction session, not
just a synthetic verification hook, which is still useful if a future
session wants to migrate more of `pulse-for-aya`'s own `xsu` traffic (GPU
cap is the next already-confirmed-working AIDL command) — but expectations
should reset: this is very unlikely to be the fix for the crash itself,
consistent with `research/pulse-for-aya/README.md`'s "likely NOT
independently fixable from this repo" conclusion from step 1.

**Update (2026-07-27): CONFIRMED LIVE — AYA's own AIDL receiver shells
out through `xsu` too, and for the scheduler command it uses MORE
connections than our own code would have. Step 2's AIDL migration is
confirmed not to help, and may be net-negative for connection count.**
Traced the decompiled call chain first (`aya-gamewindows-teardown`,
`AR03` branch — the default for any codename not in `AyaDevicesKt`'s
16-entry list, which `PocketFIT` isn't):
`com_set_performance_scheduler`/`_cpu`/`_gpu` all resolve to `AR03.b(str)`
→ `TcRootShell.a(str)` → `CmdUtilKt.e("xsu " + str)` →
`Runtime.getRuntime().exec(...)`. Then confirmed it live: log
`research/ab-logger/results/aidl_xsu_check.log` (via the existing
`maybeRunSchedulerSendTest()` debug hook) shows `sendScheduler(BALANCED)`
at 15:32:17.317 followed 192-225ms later by **four separate, freshly-
spawned `xsu` processes**, each a bare `echo schedutil > .../policyN/
scaling_governor` (N=0,2,5,7) with no `chmod` wrapping — a shape that
exactly matches `AyaDevicesUtil$applyCPUSchedulerMode$1.java`'s own code
and cannot be attributed to our test (which only ever issues 3 unrelated
`xsu` calls: marker check, marker delete, one readback 1.5s later). Full
evidence and reasoning: `research/pulse-for-aya/README.md`'s "Major
finding" update.

**This is worse than neutral**: our own `GovernorController` combines all
policies into ONE `xsu` connection (`chmod 666; echo; chmod 644` joined
across policies); `gamewindow`'s AIDL handler opens FOUR separate
connections for the same governor-set. So step 2's migration didn't just
fail to reduce `xsu` load — for this specific write, it likely traded 1
of our own connections for 4 of `gamewindow`'s. **This strategy
(migrating more of `pulse-for-aya`'s writes to AIDL to reduce `xsu` load)
is now abandoned for this device** — confirmed, not just suspected.
Also answers the user's persistent-root-channel question: no evidence
AYA has one either, on any SoC branch inspected (Qualcomm
`TcRootShell`/`Runtime.exec`, MediaTek `KtRootShell`/JNI `ShellCmd` — both
spawn fresh per call).

**Open decision for the user**: given this, should step 2's governor
migration (`ForegroundAppMonitorService.setAutoTdpGovernorBalanced()`) be
reverted back to the plain `xsu` path? It's not proven actively harmful
(today's crash-timing test showed no measurable difference either way),
but it's confirmed to not help and may add connections at exactly the
moment that matters — reverting would at least return to a known,
single-connection baseline instead of an unproven 4-connection one.

**Next session, open question**: is it worth migrating the GPU-cap write
too (marginal further reduction, same ceiling), or should effort instead
go toward something that isn't about reducing `pulse-for-aya`'s own `xsu`
footprint at all — e.g., revisiting whether AutoTDP can delay its very
first foreground-change write by a few hundred ms specifically to let
AYASpace's own hook finish first (untested idea from step 1, still on the
table), since the pile-up's actual size, not just our small slice of it,
is what seems to matter. **User's next research thread (2026-07-27,
picking up this evening)**: dig further into why `com_set_performance_cpu`
no-ops for multi-core policies sent one `cpuId` at a time (see this
file's earlier step-1 update and `research/pulse-for-aya/README.md`'s
matching section) — user's hypothesis is that `ayasettings` itself likely
only ever drives a whole-cluster cap through this command, never true
independent per-core control, which would make the observed behavior a
hardware/protocol constraint (one shared clock per `cpufreq` policy) NOT
an AIDL/receiver bug — worth confirming against
`ayaspace-teardown`'s decompiled `PerformanceViewModel`/`CpuFragment`
source (does the stock UI even expose per-core sliders within a shared
policy, or only one shared cap for the whole cluster?) before assuming
the current "send the whole group together" workaround is the best
achievable, since if the UI itself can't do per-core either, that's a
real answer, not a gap in this app's implementation.

**Update (2026-07-27): step 2 implemented, builds clean, not yet run
on-device.** `startAutoTdp()`'s Balanced-governor write (the exact
foreground-change collision point) now tries `AyaAidlClient.sendScheduler`
first and only falls back to the old `xsu` write if AIDL isn't bound or
the send fails — AutoTDP can never end up without Balanced set. The
exit-side restore calls (`setGovernorRaw`) stay on `xsu` deliberately
(they restore an arbitrary captured raw governor name, which doesn't map
cleanly onto AIDL's 3-value scheduler enum). Full detail:
`research/pulse-for-aya/README.md`'s "AIDL migration, step 2" section.
**Next session**: reproduce the Minecraft-crash timing methodology with
this build (Game-Mode-activation → crash, compare against the
59s/73s/69s baseline) to see whether it measurably helps.

**Concrete pointers for picking this up cold**: the call site to change is
`handleForegroundChange()` in
`research/pulse-for-aya/app/src/main/java/com/kei/pulse/appwatch/ForegroundAppMonitorService.kt`
(~line 1773, 2048-line file — read the whole surrounding `applyConfig`/
`startAutoTdp`/`snapshotCurrentState` flow before touching anything, it's
carefully tuned and already has one documented governor-related bug fixed
in it, the "GOVERNOR LEAK fix" comment around line 1803); the client to
use is already built, `research/pulse-for-aya/app/src/main/java/com/kei/pulse/aidl/AyaAidlClient.kt`.
Build/test with `cd research/pulse-for-aya && ./gradlew assembleDebug
testDebugUnitTest`. The debug-only verification hooks already in
`MainActivity.kt` (`verifyAyaAidlBindOnDebugBuild` and friends) can stay as
regression checks or be stripped once step 2 supersedes them — not
decided yet, follow-up call for whoever picks this up.

## ROOT CAUSE FOUND (2026-07-26): the empty-CSV bug is `xsud` segfaulting on long `xsu -c` commands

Follow-up to INCIDENT #3 below. Root-caused live on-device, outside any
app: `ab-logger`'s combined per-sample `xsu` call (~3150 chars on this
device, 19 CPU + 8 GPU thermal zones) reliably crashes `xsud` (`received
signal 11`, respawned by `init`) or gets silently dropped — this is the
exact mechanism behind "100% empty" CSV rows, not root/hardware flakiness.
Bisected: the failure threshold is a fuzzy band around ~1000-1200
characters (raw command length, NOT the number of `$(...)` subshells —
tested head-to-head), consistent with a real race rather than a fixed
buffer size. Validated fix: splitting a combined command into chunks under
~800 chars each survives repeatedly (0/5 failures per chunk, 15/15 on a
sustained 5s-interval run) and still returns correct data. Full
methodology, bisection table, and the recommended chunk-size fix are in
`research/xsu-capability-probe/FINDINGS.md`'s newest section — this
supersedes INCIDENT #2's "combine all calls into one" mitigation, which
traded process-spawn count for exactly this crash. **Applied
(2026-07-26)**: `LoggerSession.sampleOnce()` now runs the snapshot
statements through `XsuShell.execChunked()` (packed under ~700 chars per
call) instead of one combined string; the short ACT+LIST call stays
combined since its command *text* was never the problem. Also added two
new CSV columns this session, `pulse_installed`/`pulse_service_running`
(recorded once at session start, repeated per row), so a pulled CSV no
longer needs matching against separate session notes to know which A/B
arm it's from. **Verified on-device (2026-07-26)**: 7 real sessions
(idle, RetroArch, a longer RetroArch run, and 3 sessions with PULSE's
AUTOTDP active) all came back with complete data, zero empty rows — the
chunking fix holds under real use, not just synthetic bisection. Raw CSVs
kept as evidence in `research/ab-logger/results/apl_ab_logs/pulled_logs/`.

One bug found and fixed during this same verification: `pulse_installed`
read `false` in every one of those 7 sessions, including ones where
`pulse_service_running` correctly read `true` — a real inconsistency,
since a running service implies the app is installed. Cause: Android's
package-visibility filtering (API 30+, and `ab-logger` targets 34) blocks
`PackageManager.getPackageInfo("com.kei.pulse", ...)` without an explicit
`<queries>` declaration, so the lookup always threw and was silently
caught as `false` — `pulse_service_running` (a root/`dumpsys` check, not
subject to that filtering) was correct the whole time. Fixed by adding
`<queries><package android:name="com.kei.pulse"/></queries>` to
`AndroidManifest.xml`; a fresh one-sample verification session
(`results/apl_ab_logs/pulled_logs_verify/session_1785099163131.csv`)
confirms `pulse_installed=true` now reads correctly. `ab-logger` is
considered ready for real A/B sessions again.

## Follow-up (2026-07-26, same day): call frequency/concurrency tested, much weaker trigger than command length

Complementary to the root-cause entry above. Tested whether `xsu` call
*frequency*/*concurrency* (not length — using short, real, single-attribute
commands like `pulse-for-aya`'s actual `RootExec` call pattern) also
crashes `xsud`: sequential calls at any interval down to 0ms showed 0
failures; concurrent bursts (2-8 parallel) surfaced exactly one real
`xsud` crash across 69 calls with no visible caller-side failure; a
sustained realistic load (4 parallel every 2s, 80 calls over ~40s,
matching the original incident's described pattern) showed 0 failures.
**Concurrency is a real but much rarer trigger than long commands** — not
cleared as safe, since none of this synthetic testing had a real game
adding competing GPU/thermal/binder load, which is what all the original
incidents had. Full detail in `research/xsu-capability-probe/FINDINGS.md`'s
newest section. Practical takeaway: `pulse-for-aya`'s `RootExec.kt` already
uses short, one-command-per-call requests (not the long combined pattern
that caused the CSV bug), so no change indicated there from this session
— but a tight `AutoTdpController` polling loop under real gameplay load is
still an open risk, not confirmed either way.

## INCIDENT #3 (2026-07-26): empty-CSV bug recurs, then device powers off entirely, `BatteryService` left stuck

Two `ab-logger`-only sessions run back-to-back for the native-vs-`pulse-for-aya`
A/B comparison (`pl.ablogger.app`, no game actually reached foreground):
`session_1785093262984.csv` (PULSE not installed) and
`session_1785093666795.csv` (PULSE installed) — both saved raw to
`diagnostics/logs/ab-comparison/`. **Both are 100% empty** — 48/48 samples
each, evenly spaced (~5.1s), but every single column (`foreground_pkg`, `fps`,
all CPU/GPU freq+governor, GPU busy%, all temps, fan, battery current/voltage)
is `?`/`n/a`/`n/a (no layer matched)` throughout. This is the exact symptom
already documented in INCIDENT #2 ("100% empty" CSVs) — **recurring
unchanged**, which means that incident's mitigation (combining `xsu` calls,
`LoggerSession.sampleOnce()`) did not fix the underlying cause. Both sessions
also ran only ~240s (4 min), short of `TESTING.md`'s 10-minute protocol — no
A/B conclusion about `pulse-for-aya` can be drawn from this data; the failure
is entirely in the telemetry-capture layer, before any real comparison could
happen.

**Immediately after, the device powered off entirely and came back with a
new symptom**: `com.android.settings`/system UI reported no battery data at
all (matches the user's report — "nie raportuje nawet baterii"). Diagnosed
live over `adb` right after it rebooted (uptime ~26 min at diagnosis time):

- `persist.sys.boot.reason.history`'s newest entry: `shutdown,userrequested`
  at unix time `1785093175` (2026-07-26 21:12:55 CEST) — same suspiciously
  "user requested" label already flagged as unconfirmed in INCIDENT #2's
  `reboot,userrequested`; nobody actually requested a shutdown.
- `/sys/fs/pstore/` is empty — **no kernel panic was recorded**, so this
  wasn't a hard kernel crash, consistent with a triggered/graceful shutdown
  path instead.
- `dumpsys battery` showed `present: false`, `level: 0`, `voltage: 0` —
  looked like a dead/disconnected battery.
- **But the raw kernel driver disagrees**: `cat
  /sys/class/power_supply/battery/uevent` (via `xsu`, needed since
  `/sys/class/power_supply/battery/<individual-attr>` reads gave "Permission
  denied" even as root — SELinux is permissive so this wasn't a MAC block,
  cause not fully explained) returned a fully healthy live reading:
  `PRESENT=1`, `CAPACITY=43`, `STATUS=Discharging`, `HEALTH=Good`,
  `VOLTAGE_NOW=7591153`. **The battery itself is fine.** `dmesg` shows
  `healthd` logging a transient `battery none chg=` right around the
  shutdown window, then recovering — the working theory is `system_server`'s
  `BatteryService` got stuck on that transient empty read and never
  resynced, independent of the (healthy) hardware state underneath.
- `dumpsys battery reset` (standard adb debug command, clears a forced
  override) did **not** fix the stuck `present: false` — so this isn't a
  simple debug-override artifact, it's a real stuck framework/HAL state.
  Not yet resolved on-device; likely fixable by briefly plugging in USB
  power (forces a fresh `uevent`) or a full manual power-cycle — not
  confirmed this session.

**This is the third incident in the same family** (INCIDENT #1: `xsud`
crash → `BatteryService$Led` HAL call → `system_server` crash; INCIDENT #2:
`xsu`-heavy dual-app session → full device reboot + empty CSVs; this one:
empty CSVs recur + a different system service, `BatteryService`, left
stuck post-reboot) — all three correlate with test sessions putting heavy
concurrent load on `xsu`/`xsud`. Treat this as a real, recurring reliability
ceiling of the current `xsu`-polling approach under sustained/repeated use,
not three unrelated one-off flukes.

**Decision, picked up next session**: do not resume real A/B test sessions
until the empty-CSV recurrence itself is root-caused — the isolation step
already planned in INCIDENT #2 (verify `xsu` survives sustained polling over
minutes, not just a single manual `id`/`cat` check) still hasn't been done
and is now confirmed necessary, not optional. Raw CSVs from this incident
kept as evidence in `diagnostics/logs/ab-comparison/` (not renamed into the
usual `native_runN`/`pulse_runN` layout — they contain no usable comparison
data, they're incident evidence).

## INCIDENT #2 (2026-07-25, later same day): full device reboot during PULSE + ab-logger + Eden

After the mitigation below, ran PULSE + `ab-logger` + Eden (Mario, ~1-2 min
play) — the device fully rebooted this time (not just `system_server`,
kernel uptime reset). `persist.sys.boot.reason.history` shows two reasons
in sequence: `reboot,rollback_staged_install` (12:20:40 — looks like a
routine Android staged-update rollback, plausibly unrelated) then
`reboot,userrequested` (12:24:47 — the one right before the test data).
Root cause of the second one **not confirmed** — logcat rotates on
reboot, and tombstones/pstore were not readable over adb (permission
denied) to check what preceded it.

**Separately, and more concerning**: the resulting CSVs from this session
and the one before it are **100% empty** (no foreground pkg, no CPU/GPU
values, everything blank) — three empty rows-worth across ~2+ minutes
each. Investigated whether this is a bug in the call-combining mitigation
below: **it isn't** — manually ran the exact same combined `xsu` command
by hand and it returned real data (422 lines, correct activity/layer/CPU
dump) on the first try. Ran it again moments later and got **"Permission
denied" on every single CPU/GPU sysfs read**, despite `xsu -c "id"`
reporting `uid=0` around the same time. Conclusion: root access itself is
intermittently unreliable on this device right now, independent of
anything in this repo's code — consistent with (and a more severe
manifestation of) the already-documented `xsud` crash-and-refork
behavior. Not yet root-caused.

**Decision, picked up next session**: isolate variables before resuming
full A/B testing — run `ab-logger` alone (no `pulse-for-aya`) during real
gameplay first, to check whether the reboot/root-flakiness happens
without PULSE's added concurrent `xsu` polling, or needs both. All test
apps (`pulse-for-aya`, `ab-logger`, `aidl-bind-spike`) uninstalled from
the device at the end of this session for a clean restart next time —
reinstall from each folder's own build/install instructions
(`README.md`/`TESTING.md`) when resuming.

## Bug (2026-07-25, same incident): `pulse-for-aya` falsely claimed "device not compatible"

Downstream of the `system_server` crash below: after Android auto-killed
and relaunched `com.kei.pulse`, its main screen showed a red "PSERVER
UNAVAILABLE" badge and upstream's stock fallback text, "Your device is
not compatible with this app" — while the HUD right next to it kept
showing live, correct CPU/GPU/fan/battery numbers. Not a real
incompatibility: `RootExec.pServerAvailable`'s one-time cached `xsu`
probe latched `false` after a single failed attempt (almost certainly
landing during the post-crash `xsud` instability) and never retried —
`executeAsRoot()` itself doesn't consult that cache, so real functionality
kept working throughout. **Fixed**: only latch `true`, never latch
`false` (matches `RgbController.available()`'s existing pattern in the
same codebase — that lesson was already learned once, just not applied
here). See `research/pulse-for-aya/README.md`'s "Bug found" section.
Builds clean, not yet re-verified on-device.

## INCIDENT (2026-07-25): first real ab-logger session crashed `system_server`, device rebooted

Not a cosmetic bug — read this before running `ab-logger` again. First
real test session (logging started, app backgrounded, playing normally)
made the device appear to freeze for ~25-30s. `logcat` timeline:

- `xsud` (root daemon) `Fatal signal 6 (SIGABRT)` crash traces right as
  the app started/backgrounded (same pattern first noticed in this app's
  original smoke test, then under-weighted as "not confirmed harmful").
- ~10-13s later: `BLASTSyncEngine: ... Application ANR likely to follow`,
  then a confirmed **ANR in `com.android.systemui`**.
- ~18s after that: **`FATAL EXCEPTION IN SYSTEM PROCESS`** —
  `BatteryService$Led`'s charging-LED animation called into the `ILights`
  HAL, got back error `-13`, uncaught — **this crashes `system_server`
  itself**.
- Android auto-restarts `system_server` from scratch (full re-init of
  every system service, visible in logcat as dozens of "PackageWatchdog:
  ... INACTIVE -> PASSED" lines) — this restart, not an app hang, is what
  looked like the device freezing. Confirmed no actual reboot (kernel
  uptime continuous throughout); `com.kei.pulse`'s (`pulse-for-aya`)
  foreground service auto-restarted afterward along with everything else,
  confirming it was ALSO running/polling via `xsu` throughout the
  incident.

**Root cause not proven** (the crash site is vendor/AOSP `BatteryService`
code, unrelated to anything in this repo directly) — but the timing
isn't treated as coincidence: `ab-logger` was making 3-4 separate `xsu`
process spawns every 2 seconds, `pulse-for-aya` was doing its own
concurrent polling, and this device's `xsud` daemon is already known to
crash-and-refork on every connection close. Leading theory: sustained
dual-app `xsu` process-spawn churn created enough system load/binder
contention to starve a fragile, timing-sensitive HAL call that would
otherwise succeed. Elevates the earlier "xsud SIGABRT, not confirmed
harmful" note (see `pulse-glue-assessment/FINDINGS.md` risk section) —
that framing was too reassuring; treat repeated `xsud` crashes as a real
warning sign going forward, not a benign log line.

**Mitigation applied** (see `research/ab-logger/README.md`'s "Incident"
section for the full writeup): combined 3 of `LoggerSession`'s 4 `xsu`
calls per sample into one (down to 1-2 spawns/sample), raised the
sampling interval from 2s to 5s. Builds clean, **not yet re-verified
on-device** — the device was mid-restart by the user when this landed.
**Do not resume the A/B test series until a cautious, closely-observed
retest confirms this holds** — and consider testing `ab-logger` alone
first (without `pulse-for-aya` running) to isolate whether dual-app
concurrent `xsu` load was actually necessary to trigger this, or whether
`ab-logger` alone is enough.

## `research/ab-logger/` built (2026-07-25) — minimal A/B telemetry recorder

New, purpose-built app: two buttons ("Start log"/"Stop log"), reusing
`autotdp-ab-harness`'s proven sampling pipeline
(`XsuShell`/`FpsPipeline`/`ThermalZones`/`PowerFanProbe`, unchanged) inside
a foreground service (survives backgrounding for a whole game session,
unlike the harness's Activity-scoped coroutine). Built instead of reusing
`autotdp-ab-harness` directly because that app's "Start AutoTDP" button
launches the old `pulse_lite_v3.7.sh` bash daemon — using it as-is to test
`pulse-for-aya` would run both controllers at once, fighting over the same
sysfs nodes. See `research/ab-logger/README.md` for the full reasoning and
`TESTING.md` for how to run/pull/clean up logs.

**Builds clean, installs, launches, smoke-tested on-device**: a session
ran for several minutes and produced a well-formed CSV (real CPU
governor/freq per policy, battery current/voltage — thermal zones came
back `n/a` this run, worth checking whether that's a one-off next real
session). Device cleaned up afterward (test files/CSV removed, service
stopped) — not yet used for an actual native-vs-`pulse-for-aya` A/B
comparison, that's the next concrete step and is the user's to run
(see `pulse-for-aya/TESTING.md`).

**Update (2026-07-25, same day): real fan RPM reading added.** The
smoke test's `fan_signal` column came back `not_found` — the generic
`cooling_device` discovery this app inherited doesn't find anything on
this device. `aya-gamewindows-teardown`'s pass 3 (a second session's
work, see its `FINDINGS.md` section 6) found and confirmed live the
actual mechanism: a plain `pwm-fan` hwmon node,
`/sys/devices/platform/soc/soc:pwm-fan/fan_rpm_state`, readable even
without root. `LoggerService`/`LoggerSession` now try that confirmed
path first (falling back to the old generic search if absent), so the
CSV's fan column will carry a real RPM number instead of `n/a` on the
next session. Read-only — doesn't touch `pulse-for-aya`'s still-
deliberately-stubbed `FanController.kt` or add any new actuation; write
access to this fan node remains a separate, unconfirmed question. Now
builds clean; not yet re-verified on-device (left for the user's own
test run rather than another smoke test).

**Two things noticed during that smoke test, folded into
`diagnostics/docs/HARDWARE_PROFILE.md`, not blocking**:
- Repeated `xsud` (the on-device root daemon, a vendor binary) `Fatal
  signal 6 (SIGABRT)` crash traces during rapid app-switching moments
  (opening `ab-logger`, its permission dialog opening) — not reproduced
  during a steady 12s polling window or a single isolated `xsu -c "id"`
  call, and every individual command's result still came through
  correctly (a fresh `xsud` worker handled the next call each time). Most
  likely a per-connection worker crashing on cleanup under rapid/
  concurrent access (both `pulse-for-aya`'s background service and
  `ab-logger` were hitting `xsu` around the same moments this run) — not
  confirmed harmful, but new and undocumented before this session. Worth
  watching for during real, longer A/B sessions.
- Also briefly hit a transient `/sdcard` "Transport endpoint is not
  connected" error (a few seconds, self-resolved, not reproduced since) —
  noted in case it recurs, not treated as a real problem on one occurrence.
- Confirmed, incidentally: the device's real fan PWM node is
  `/sys/devices/platform/soc/soc:pwm-fan/hwmon/hwmon0/pwm1` (standard
  Linux hwmon, not Odin's `gpio5_pwm2`), and this ROM runs SELinux in
  permissive mode (`setenforce 0` observed, `avc: denied ...
  permissive=1` throughout logs). Both purely observational — nothing in
  this repo writes to that fan node or touches SELinux state.

## Repo merge

**Merged in (2026-07-25)**: the formerly-separate `apl-diag`
(`AyaPulseDiag`) repo is now `diagnostics/` in this repo, not a sibling —
see "Repo merge" section below and `diagnostics/README.md` for why. That
repo's own STATUS.md is retired; its content is folded into this section.

## `diagnostics/` (folded in from `apl-diag`, 2026-07-25)

Raw hardware facts and the validated FPS/telemetry measurement script —
see `diagnostics/README.md` and `diagnostics/docs/HARDWARE_PROFILE.md` for
full detail. Carried over, still true, don't re-litigate:

- Foreground-app detection: `dumpsys activity activities | grep
  topResumedActivity=` (not `mCurrentFocus`/`mResumedActivity` — confirmed
  to return nothing on this Android 14 build).
- SurfaceFlinger layer selection needs the 4-tier priority search (BLAST >
  plain SurfaceView > last non-helper match > old fallback) — this took
  four buggy iterations (v4-v7) to get right, already solved, don't redo it.
- `dumpsys gfxinfo` is unreliable for native SurfaceView renderers
  (RetroArch/Eden/Dolphin) — returns 0 frames or frozen/cached stats.
- GPU busy signal: raw `kgsl-3d0/gpubusy`, not `gpubusypercentage`
  (confirmed broken on this kernel) — but the raw counter itself
  wraps/resets between reads, so no controller should trust a single read.
- CPU cores directly observed at 87-96°C with no throttling response from
  AYASpace's Gaming mode — thermal safety is this project's own
  responsibility to design for, not something the vendor firmware is
  confirmed to handle.
- Full CPU OPP tables (per-policy `scaling_available_frequencies`, 18-32
  steps per policy) and the AIDL-sourced per-mode config (fan mode, GPU
  min/max, per-core CPU caps for all 5 AYASpace modes) are both captured in
  `diagnostics/docs/HARDWARE_PROFILE.md` — this is the reference to check
  before assuming a frequency/fan-mode fact needs re-measuring.

**Known open items carried over, still open**: the AYASpace
write-conflict question (what happens if a controller and AYASpace write
the same sysfs node simultaneously — relevant now to the vendor-daemon-
contention risk noted in `pulse-glue-assessment/FINDINGS.md`) was never
directly tested; the "zero span" FPS edge case (~17% of Eden samples) is
low-priority and fails safe.

## `research/pulse-for-aya/` exists now (2026-07-25) — first buildable glue port

Acted on the assessment below: forked `pulse-upstream` (commit
`0d2893e67c`) into `research/pulse-for-aya/`, patched `RootExec.kt`/
`RootSupport.kt` to run through `xsu` instead of `PServerBinder`, stubbed
`FanController`'s writes to no-ops (fan stays fully native-AyaSettings-
owned), left RGB untouched (confirmed self-gating). **Builds clean
(`./gradlew assembleDebug`), installs, launches on the AYANEO Pocket
FIT, and is already reading live CPU/GPU/thermal telemetry over `xsu`
with no crashes** — screenshot showed real values (CPU 3302MHz, GPU
231MHz, 36°C/33°C, 3.3W draw) and a green "PSERVER · LINKED · NO-ROOT"
status badge (cosmetic label from upstream, semantically now means "xsu
probe succeeded"). See `research/pulse-for-aya/README.md` for the exact
patch list and reasoning.

**Not yet exercised**: AutoTDP's actual actuation path (writing CPU/GPU
frequencies) — toggling it on in the UI correctly triggered the app's own
onboarding flow (redirected to Android's Usage Access settings screen,
since that permission isn't granted yet) rather than crashing or silently
failing. Granting that permission and doing a supervised first
AutoTDP run is the next concrete step, followed by an A/B comparison vs
native AyaSettings (see the pulse-glue-assessment FINDINGS.md follow-up
for the proposed protocol, adapted from `research/autotdp-ab-harness`'s
existing paired-session design).

## PLAN FOR NEXT SESSION (2026-07-25 end of day)

**`research/pulse-glue-assessment` reconnaissance is now closed out** (see
that folder's `FINDINGS.md`, "Follow-up pass (2026-07-25)" section) — all
four items from the previous session's "not yet checked" list were
resolved via a full-module `grep` (not just the original 8-file sample)
plus targeted reads of the actual control-loop files
(`AutoTuneController.kt`, `ForegroundAppMonitorService.kt`'s fan-reassert
loop):

- `RootExec`'s choke point is confirmed at the whole-module level — every
  caller across all ~80 files goes through `RootSupport.runRootCommand`/
  `runGeneratedScript`, nothing else touches `PServerBinder`/
  `ServiceManager`. The glue patch really is just rewriting
  `RootExec.executeAsRoot()`'s ~15-line body to shell out via `xsu`
  instead, reusing the already-proven `XsuShell.kt` pattern.
- `minSdk 31`/`targetSdk 34`/manifest permissions — no conflict, ordinary
  app permissions, nothing assumes root/system context.
- **Risk found this pass, then resolved on-device the same day**: the
  fan-reassert loop re-checks live fan duty every `FAN_RECHECK_MS = 120L`ms
  via a root `cat` call — flagged as a possible problem for `xsu`'s
  fork+exec overhead (vs the cheap native Binder call it was tuned
  against). Tested directly on the AYANEO Pocket FIT: `xsu -c "cat
  /sys/class/gpio5_pwm2/duty"` returns empty. Tracing `pulse`'s own gating
  logic (`FanController.customFanAvailable()` →
  `ForegroundAppMonitorService.isCustomFanSupported()`), empty output means
  this loop **never starts** on this device — the cadence question is moot
  for this mechanism specifically.
- **Bigger finding that surfaced from this**: both of `pulse`'s fan-control
  mechanisms (the custom PWM duty curve AND the discrete Silent/Smart/Sport
  modes via `settings put system fan_mode`, read by the AYN vendor app
  `com.odin.settings`) are vendor-specific to AYN Odin/RP6/Thor and almost
  certainly inert on AYANEO hardware. Fan control isn't a "glue the root
  transport" problem like CPU/GPU/display/RGB/AutoTDP — it needs to be
  built separately, most likely on top of the already-proven AIDL bind to
  `com.ayaneo.gamewindow` (which already returns fan mode in its
  whole-profile callback JSON, per `aidl-bind-spike/FINDINGS.md`). See
  `pulse-glue-assessment/FINDINGS.md`'s "On-device confirmation" and "Risk
  assessment" sections for the full writeup, including the other
  (non-fan) risk categories assessed this session (CPU/GPU write safety,
  vendor-daemon contention, display/settings recoverability, the
  world-readable script file).
- **Follow-up, same session: the discrete `fan_mode` writes are NOT gated
  the way the PWM loop is.** Several call sites in
  `ForegroundAppMonitorService.kt` (AutoTDP start, per-app profile apply,
  master-OFF/revert-to-stock, snapshot restore) call
  `fanController.setMode(...)` unconditionally — i.e. `settings put system
  fan_mode <N>` fires regardless of whether Custom/PWM fan is supported.
  Neither AYANEO teardown (`ayaspace-teardown`, `aya-gamewindows-teardown`)
  found any `Settings.System` key involved in AYANEO's own fan control
  (it's all AIDL-based there), suggesting this key is orphaned/unread on
  our device and these writes are no-ops — **not directly verified**.
  Stated intent: keep relying on native AyaSettings' fan control for now,
  do AIDL-based fan control as separate later work — so the plan is to
  explicitly strip/no-op every `fanController.setMode()`/
  `ensureManualMode()` call site in the fork (not just rely on the
  existing `customFanAvailable()` gate), guaranteeing zero fan-touching
  root calls from the ported app instead of assuming the Settings key is
  dead. See FINDINGS.md's "Important gap" subsection for the full call-site
  list and the cheap on-device check (`settings get system fan_mode`
  before/after toggling fan mode in native AyaSettings) that would confirm
  the key really is orphaned, if we want certainty instead of inference.
- **Follow-up, same session: RGB, autostart, and the Quick Settings tile
  all checked clean.** Unlike fan, `RgbController`'s writes ARE properly
  gated everywhere (`if (!available()) return` before every write) and its
  vendor key is `com.ro.*`-specific (AYN/Retroid), almost certainly absent
  on AYANEO — should self-disable safely, no stripping needed (one
  on-device sanity check recommended, not required).
  `BootCompletedReceiver`/`MainActivity`'s permission onboarding
  (`PACKAGE_USAGE_STATS`, overlay) and the `PerformanceTileService` QS tile
  are all stock Android APIs with no AYN-specific assumptions found. The
  glue scope for a first build is confirmed as: CPU/GPU/AutoTDP/display/
  refresh-rate/per-app-profiles/HUD-overlay/QS-tile/boot-autostart — with
  fan control stripped/deferred (see above) as the only carve-out. One
  gap: the `sleep/SleepProfileMonitorService` package hasn't been read yet
  — see `pulse-glue-assessment/FINDINGS.md`'s latest follow-up section for
  the full writeup.

Writing the actual patch (fork `pulse-upstream`, replace `RootExec.kt`, add
the `SG8350P` `DeviceProfile` entry, strip the fan-mode call sites, and
separately scope AIDL-based fan control) is intentionally deferred to a
later session — this session was findings-only.

## PLAN FROM PREVIOUS SESSION (2026-07-24 end of day)

**Two major findings that session, in order of how much they change the plan:**

### 1. `research/pulse-glue-assessment` — maybe don't write `apl/app/` from scratch at all

Upstream `pulse` (`github.com/keiretrogaming/pulse`, GPL-2.0, cloned to
`research/pulse-upstream/` — gitignored, re-clone with `git clone
https://github.com/keiretrogaming/pulse.git research/pulse-upstream` if
needed) already implements AutoTDP, fan curve, HUD overlay, RGB, per-app
profiles, Quick Settings tile — mature, tested on real AYN/Retroid hardware.
Its only blocker for our device is its root mechanism (`PServerBinder`, not
available here). Reconnaissance (8 files read, see
`research/pulse-glue-assessment/FINDINGS.md` + curated `evidence/`)
strongly suggests **this is a narrow, clean substitution, not a rewrite**:

- The ENTIRE `PServerBinder` dependency is one ~25-line file
  (`root/RootExec.kt`) behind a single choke point
  (`RootSupport.runRootCommand`/`runGeneratedScript`) — everything else
  (CPU/GPU tuning, FPS reading, fan curve) only knows `RootExec`'s
  `executeAsRoot(cmd): Result<String?>` signature, not `PServerBinder`
  itself. Swapping this file for an `xsu -c` call (we already have this
  exact code, proven, in every probe's `XsuShell.kt`) is the glue.
- CPU/GPU detection (`CpuPolicyDetector.kt`, `GpuFreqDetector.kt`) is
  already fully dynamic/generic (reads `scaling_available_frequencies`,
  `kgsl-3d0/gpu_available_frequencies`, etc. at runtime) — zero per-SoC
  hardcoding, will work on our device unchanged.
- The one per-device gate (`model/DeviceProfiles.kt`, keyed by
  `ro.soc.model`) already falls back gracefully to a working `UNKNOWN`
  profile for our SG8350P (not in its table yet) — adding a proper entry
  is a small quality refinement, not a blocker.
- **Bonus, independent of the glue decision**: `data/FpsReader.kt` uses
  `dumpsys SurfaceFlinger --timestats` (global present-cadence histogram),
  NOT `--latency` + layer-matching — sidesteps the whole "which layer is
  real" problem that took `pulse_lite_diag_v8.sh` four iterations (v4-v7)
  to solve. Worth adopting regardless of the glue decision.

**Before writing any glue code**, `FINDINGS.md`'s "What's NOT yet checked"
list is the next session's first task: confirm `RootExec`'s public surface
is really only used the one way assumed, read the actual control-loop
files (`AutoTuneController.kt`, `PowerModel.kt`, `FanCurveController.kt`),
check the manifest/permissions and `minSdk` (may conflict with `apl/app/`'s
current placeholder `minSdk 26`).

### 2. `research/aidl-bind-spike` — CONFIRMED on real hardware

A plain, non-system app can flip AYASpace's performance profile (Gaming ↔
Eco, verified via governor + `scaling_cur_freq` read-back, repeated 3x
reliably) over a bare Binder connection to `com.ayaneo.gamewindow` — no
`xsu`, no root, no per-call ~100ms floor. Full per-mode config (CPU
per-core caps, fan mode, GPU frequency range) also came back live in the
callback JSON — resolved the long-standing "Gaming vs Max identical?"
mystery (answer: GPU cap only, 834MHz vs 1050MHz/uncapped) — see
`research/aidl-bind-spike/FINDINGS.md` and the updated table in
`diagnostics/docs/HARDWARE_PROFILE.md`. This remains relevant regardless of
the glue decision above — it's a faster/safer path than `xsu` specifically
for whole-profile switches, whether that code lives in a `pulse` fork or a
from-scratch `apl/app/`.

## Next session, in priority order

1. **Write the glue patch**: reconnaissance is fully closed out
   (module-wide, not just sampled) — fork `pulse-upstream`, replace
   `RootExec.executeAsRoot()` with an `xsu`-backed implementation (reuse
   `XsuShell.kt`'s pattern), add the `SG8350P` `DeviceProfile` entry. Fan
   control is explicitly OUT of scope for this patch — confirmed on-device
   inert on AYANEO (see `pulse-glue-assessment/FINDINGS.md`'s "On-device
   confirmation" section) — track it as a separate follow-up (item 2
   below), don't try to glue it alongside the rest.
2. **Decide `apl/app/`'s actuation architecture**: AIDL-bind (now proven)
   vs `xsu` sysfs writes (also proven, but slower/riskier) — likely AIDL
   for whole-profile switches, `xsu` still needed for anything NOT covered
   by the AIDL command catalog (live FPS/temp/busy% reads — AIDL only
   exposes "set" commands, no monitoring). Applies whether this ends up
   inside a `pulse` fork or a from-scratch app.
3. **Scope and build the per-app profile-mimic feature** (assign Eco/
   Balanced/Streaming/Gaming/Max to specific apps, auto-applied via AIDL on
   foreground-app detection) — either as `apl/app/`'s first real feature
   from scratch, or as the first patch on top of a glued `pulse` fork,
   depending on step 1. Needs: a `ForegroundService` + `BootReceiver`
   (still just placeholders in `app/`), reuse of
   `FpsPipeline.parseForegroundPkg`-style detection (via `xsu`), and
   `AidlProtocol.kt`'s bind/register/send logic ported from the spike
   into production-quality code.
4. **Optional follow-up spike** (not blocking, only if step 3 needs it):
   test `com_set_performance_fan`/`com_set_performance_cpu` (fine-grained,
   not whole-mode) and the controller/key-mapping commands
   (`com_set_abxy_mode` etc.) — the aidl-bind-spike only exercised whole-mode
   switching so far.
5. Everything from before this session remains queued behind the above:
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
  `--latency` FPS calc), originally built and validated in what's now the
  `diagnostics/` folder's shell script (formerly the separate `apl-diag`
  repo), is confirmed reachable the same way, across RetroArch/Eden/Dolphin
  (three distinct layer-naming conventions).
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
  as missing in `diagnostics`'s hardware profile at the time (later
  resolved, see "run2" below), still needed before a precise
  step-controller can be designed (currently only 4 discrete points per
  cluster are known).
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
Resulting CSVs, once collected, should be copied into `diagnostics/logs/
ab-comparison/` — the code lives here for the Gradle/Kotlin scaffolding,
the data belongs there.

**run1 (2026-07-24):** Test 9 PASSED (backgrounded process persists), with
a quirk to expect again (launch call itself hits its timeout rather than
returning fast — daemon still starts regardless). Test 6 (CPU frequency
table) came back completely empty — fixed (split into one call per policy,
explicit raw-exec logging).

**run2 (2026-07-24):** Test 6 fix confirmed working — full CPU OPP table
captured and folded into `diagnostics`'s `HARDWARE_PROFILE.md` (this closes
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
`diagnostics/docs/HARDWARE_PROFILE.md`). Bonus: the same command surface
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
