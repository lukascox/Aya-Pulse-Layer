# STATUS

Living document — update this in place at the end of a working session,
commit with a descriptive message. Do not create a new dated/versioned copy
of this file; `git log` is the history.

Remote: `git.internal.example/cox/AyaPulseLite` (Forgejo, self-hosted).

## Added (2026-07-28): correlation logging — dmesg, filtered logcat, battery/online

Gap identified: every crash so far has only ever shown "the log goes silent" —
nothing captured *why*. Three additions to `pulse_daemon.sh`/`PulseDaemon.kt`,
all through the existing daemon (zero extra `xsu` connections):

- **`dmesg -c`**, polled every ~1s in the same loop as `cap_poll`, to a new
  `pulse_<ts>_dmesg.log`. `research/xsu-capability-probe/FINDINGS.md` already
  proved `dmesg` catches `xsud`'s own SIGSEGV/SIGABRT directly — the kernel
  ring buffer survives even when the crashing process doesn't. Never captured
  during a real PULSE crash before.
- **A filtered `logcat`** (`AndroidRuntime:E libc:F DEBUG:F ActivityManager:E
  BatteryService:E`, everything else silenced), spawned once as a fully
  detached process — same "survives the daemon/app dying" property the daemon
  itself already has, to a new `pulse_<ts>_logcat.log`. Narrow enough to not
  itself trigger the 256 KiB ring-buffer overflow a full `logcat` hits under
  `xsu`'s own chatty protocol logging.
- **`battery/online`** added to the existing `cap_poll` line — the
  still-unidentified process seen writing that value right before a crash
  (2026-07-27 investigation, see `STATUS_ARCHIVE.md`) has never been checked
  against a PULSE-side crash directly; now it's in the same timestamped file
  as everything else, so a future pulled log can just be grepped for it.

Orphan handling: the logcat filter process is killed on clean `STOP` and
`pkill`'d (alongside the existing `pulse_daemon.sh` orphan-kill) at the start
of every new session, so restarts don't accumulate copies.

Verified: `compileDebugKotlin`, `testDebugUnitTest`, `lintDebug` all clean,
fresh APK built (`BUILD_TIMESTAMP` 2026-07-28 16:38:07). **Not yet tested
on-device** — next real crash capture should finally show dmesg/logcat lines
alongside the existing PULSE-side log instead of just silence.

## RESUMED (2026-07-28): real correctness bugs found and fixed — untested on-device

Follow-up to the PAUSED entry below, same day. A second, independent review
(different session) re-read this whole file plus the actual code and raw
round1-round5 logs and surfaced several concrete bugs the running narrative had
missed. Each claim below was independently re-verified against the code/data
before acting on it — this isn't taken on faith.

1. **Stale on-device daemon script (verified in code, but user reports it
   doesn't apply here)**: `PulseDaemon.start()` only copied `pulse_daemon.sh`
   from assets `if (!scriptFile.exists())` — and `adb install -r` does NOT
   clear `filesDir`, so a script copied once could silently keep running
   unpatched through 5 later protocol changes (`cafb98c`→`293b9d6`→`12ea8e2`→
   `55268d6`→`2d59fc4`→`35c0c51`, each adding an argument). `BUILD_TIMESTAMP`
   couldn't have caught this — it proves the APK is fresh, not the script,
   since the version label is passed as a Kotlin-side launch argument. **User
   confirmed their workflow always fully uninstalls before reinstalling**,
   which does clear `filesDir` — so this specific risk likely didn't cause
   today's crashes, but the bug is real or the next person's workflow, and the
   fix is essentially free, so applied anyway (see below).
2. **`FpsReader`'s TimeStats-reduction script is 874 characters, sent whole as
   `xsu -c`'s argument, every ~1-2s during real gameplay** — independently
   measured, matches the report exactly. `research/xsu-capability-probe/FINDINGS.md`
   (read directly, not from memory) confirmed via on-device bisection: safe
   under ~800 chars, a fuzzy failure band ~1000-1200, consistently fails above
   that. 874 sits close enough to the danger band, sustained over the entire
   session, to be a real suspect that was never audited — `RootSupport.kt`'s
   own 2026-07-25 simplification (inlining scripts instead of writing them to a
   file first) predates and is unrelated to that finding, so nobody had
   connected the two until now.
3. **The `dispatch()` xsu-fallback path sends the WHOLE write batch as one
   combined command** — for a 7-10 node batch (governor engage / release),
   that's comfortably in the "consistently fails" range by the same bisection.
   Confirmed this fires exactly under stress (the daemon already failed/timed
   out) — a plausible amplifying feedback loop, not just a coincidence: load →
   daemon slow → timeout → fallback → long command → crash.
4. **`readBatch()` had zero success/failure logging** — already flagged as the
   open gap in the PAUSED entry below; means round3-5's "telemetry migration"
   could have been silently no-op'ing back to per-path `xsu` calls the entire
   time and nothing would show it.
5. **Orphaned daemon processes can compete for the same fixed FIFO path** —
   the daemon's read loop re-resolves `$FIFO_IN` by path every iteration, so
   after a crash, a hard-killed orphan reopens the SAME path once a new daemon
   `rm -f`s + `mkfifo`s it, and silently competes for commands.

**Fixed, all cheap correctness fixes, no architecture change**:
- `PulseDaemon.start()` now always overwrites the script from assets (no more
  `if (!exists())`), `pkill -f pulse_daemon.sh` first to clear orphans, and
  logs a CRC32 of the exact script bytes it just wrote alongside the existing
  version label — every pulled log now proves which script was actually running.
- `RootSupport.runGeneratedScript()` now writes `scriptContents` to this app's
  own `filesDir` and runs it via a short `sh '<path>'` command instead of
  inlining the whole script as `xsu -c`'s argument — fixes `FpsReader` and the
  other two callers (`qa_combo_producer.sh`, `apply-frequencies.sh`) at once.
  `filesDir` stays private to this app + root, so this does NOT reintroduce
  the world-readable exposure the original upstream file-based approach had.
- `AutoTuneController`'s xsu-fallback path now chunks under ~700 chars
  (matching `research/ab-logger`'s already-established `XsuShell.execChunked`
  pattern for the same underlying finding) instead of sending one combined
  command, and logs the node count + character count so a future log can show
  exactly how big a fallback command was.
- `PulseDaemon.readBatch()` now logs "via daemon"/"via xsu fallback" the same
  way the write path already does.

Verified: `compileDebugKotlin`, `testDebugUnitTest`, `lintDebug` all clean.
**Not yet tested on-device** — next session's first job.

## PAUSED HERE (2026-07-28, user's request) — the crash still isn't solved; read this first before resuming

User is out of patience with this thread after repeated regressions across a
full day of iteration (governor-write FIFO → cap-write FIFO → telemetry-read
FIFO, each fixing something real but the crash recurring every time). Explicitly
asked to stop chasing it for now. Honest state below — don't reopen this without
reading it, and don't restart by guessing; the next concrete step is already
identified.

**What we actually have, most recent test**
(`research/ab-logger/results/pulse_daemon_fifo_test/round5_2026-07-28_1245_minecraft_crash_after_write_fallback/`,
build `2026-07-28 11:47:25` — cap writes AND telemetry reads both migrated to
the `PulseDaemon` FIFO by this point): a real Minecraft session engaged AutoTDP
cleanly, ran a genuine TRIM sequence under heavy load (120fps target, temps up
to 83°C CPU / 73°C GPU, several domains capped simultaneously, confirmed
independently by the paired `poll-cpufreq.sh` ground-truth file) — then, at
12:47:07, the **first-ever "cap write via xsu fallback"** line appears (every
prior write in this session, and in every successful session before it, went
"via daemon"), and the log stops one second later. This is consistent with the
already-documented `xsud`/`BatteryService$Led` crash signature, still
happening, on the build with both migrations in place.

**What's genuinely fixed vs. still open**:
- Cap-write and telemetry-read `xsu` connection volume are both real,
  confirmed reductions (zero fallback for the whole HOLD/TRIM run until the
  very end) — these were not wasted effort.
- The crash itself is NOT solved. Something still eventually breaks the
  daemon's FIFO round-trip (that's what the "xsu fallback" line means: a
  `setCap`/`readBatch` call timed out or failed) shortly before the
  system-level crash. Whether the daemon dying is the cause or a downstream
  symptom of the same event that kills `system_server` is not yet known.

**Concrete gap found, worth closing FIRST whenever this resumes**: unlike
[`dispatch()`](../research/pulse-for-aya/app/src/main/java/com/kei/pulse/data/AutoTuneController.kt)'s
write-path logging ("cap write via daemon"/"via xsu fallback"),
`PulseDaemon.readBatch()` has NO success/failure logging at all. We cannot
currently tell, from any pulled log, whether `TelemetryReader`'s reads were
actually going through the daemon for the whole session or silently falling
back to per-path `xsu` calls the entire time — which would mean the telemetry
migration (round3/round4 above) never actually engaged during the crash
sessions, and we'd be no better off than before it. **Add the same
via-daemon/via-fallback logging to the read path before drawing any more
conclusions about whether that migration helped.**

**Also unresolved, lower priority**: two of round2/round4/round5's sessions
never got AutoTDP to engage at all (crash-loop before foreground tracking) —
that's the separate, older "app needs a reboot after install/relaunch" thread,
still not root-caused either.

**Do NOT** start another migration/rewrite round on the first resume session —
start by adding read-path logging (small, safe, no architecture change) and
getting ONE more real crash capture with it in place, so we can finally see
whether reads or writes (or neither) are failing right before the crash.

## To investigate next session: does Minecraft's post-install crash-until-reboot ritual trace to the boot receiver's unconditional core re-online?

Raised by the user (2026-07-27, later session): every session so far has
needed a device reboot between installing/reinstalling `pulse-for-aya` and
Minecraft launching successfully — an established, unquestioned ritual by
now, but never actually root-caused. New candidate found this session,
**not yet tested**:

`BootCompletedReceiver.kt` (confirmed byte-identical to upstream `pulse` —
this is inherited stock behavior, not something this fork added) listens
for both `ACTION_BOOT_COMPLETED` and `ACTION_MY_PACKAGE_REPLACED`. The
second one fires on **every** `adb install -r` — no manual app-open
needed — and restarts `ForegroundAppMonitorService` if `WatcherActivation.shouldRun(...)`
is true (which it is, once AutoTDP permission/config exists, as it does
for every build installed this session). Inside `pollLoop()`'s own
startup (`ForegroundAppMonitorService.kt` ~line 645-652), there's an
"anti-stranding net" that **unconditionally** re-onlines the prime CPU
cluster's cores via `cpuN/online` writes on every service (re)start — a
real CPU hotplug operation, happening automatically after every reinstall,
before any game is even foreground.

This lines up with the already-documented round5 crash mechanism further
down this file: Minecraft's bundled `cpuinfo`/XNNPACK library reads
`/sys/devices/system/cpu/possible`/`present` at startup and crashes
(`SIGABRT`, `cpuinfo_get_packages_count called before cpuinfo is
initialized`) if those reads land mid-hotplug-transition. Previously this
was only suspected in connection with `aggressivePark` parking/unparking
**during** a game session; this is the same write firing at a completely
different, earlier moment — service (re)start, independent of any game
being open yet.

**Doesn't fully explain "only a reboot fixes it, not just waiting"** — a
purely transient hotplug race should clear itself in well under a second,
not need a full reboot. That's the open gap in this theory.

**Next session, cheapest test, no code change needed**: after the next
`adb install -r`, **wait ~30-60s before launching Minecraft** instead of
immediately rebooting. If that alone is enough, the theory holds and the
fix is straightforward (stop re-onlining unconditionally on every restart,
only when actually resuming a stranded session). If it still crashes even
after waiting, this candidate is ruled out and the search moves back to
"what does a real reboot clear that killing+restarting the service
doesn't."

## To investigate next session: Eden (Switch emulation) stays at ~40 FPS despite AutoTDP visibly regulating

Raised by the user (2026-07-27, later session), user-observed: with
`AutoTdpBias.SMOOTH` forced (highest power-ceiling label, though the
ceiling itself doesn't actually apply on this SoC — see the entry above),
Minecraft ran a smooth, stable 120 FPS session — but the same build,
same device, running Eden (Switch emulation, Super Mario Odyssey) still
sat around ~40 FPS despite `poll-cpufreq.sh` confirming CPU caps were
genuinely changing over the session (not frozen). **Not yet investigated
this session** — ruled out so far: the Odin watt-ceiling (entry above,
confirmed inapplicable), and "AutoTDP isn't running at all" (ruled out by
the same cap_poll evidence — it's regulating, just apparently not enough
to fix Eden specifically, or GPU/emulation-thread-bound rather than
CPU-cap-bound). Emulation workloads are qualitatively different from a
native game (heavier single-thread emulation-core load, more erratic
frame pacing) — worth checking `cpuCorePeakPercent`/bottleneck detection
in `AutoTuneController.step()` against an actual Eden session's telemetry
before assuming this is fixable by the CPU/GPU cap path at all; could
just be Eden's own CPU-bound emulation ceiling on this SoC, unrelated to
PULSE.

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

**END-OF-SESSION RECAP (2026-07-27, night) — read this first; the full
blow-by-blow (including two earlier root-causing sessions) is preserved
below for reference.**

**Confirmed root cause**: `xsud` (the vendor's root-shell broker, used by
`pulse-for-aya` via `xsu` and, it turns out, by AYASpace's own
`com.ayaneo.gamewindow` too) has a real stack-overflow bug in
`xsu_conn_handler`, reliably reproduced across many captures with an
identical crash backtrace. It's triggered by cumulative/concurrent `xsu`
connection load building up over the first ~60-70s a game is foreground,
eventually taking `system_server` down with it (`BatteryService$Led`'s
charging-LED animation is the specific call site that dies, but it's a
downstream victim of `xsud`'s corruption, not the root cause itself).
Governor choice (`walt` vs `schedutil`) and `aggressivePark` are both
directly ruled out as the trigger.

**Tried and reverted**: migrating `pulse-for-aya`'s AutoTDP-engage
governor write from `xsu` to AIDL (`com_set_performance_scheduler`).
Confirmed live it changes nothing (crash still ~60s, matching baseline)
— then found out why: `gamewindow`'s own AIDL receiver, for this device's
default code branch (`AR03`), *also* shells out through `xsu` internally
(`AR03.b() → TcRootShell.a() → Runtime.exec("xsu "+cmd)`), and for the
scheduler command specifically fires **4** separate bare-echo `xsu`
connections (one per cpufreq policy) where our own code would have used
just 1. The migration was net-negative for connection count, not
neutral. Reverted to the plain `xsu` write (KISS) — see the "AIDL
migration, step 2" update below and its mirror in
`research/pulse-for-aya/README.md`.

**Investigated and set aside**: disabling `gamewindow`'s own
foreground-change reaction (`AyaTaskStackSubscriber`, confirmed to be a
runtime-registered `TaskStackListener`, not a manifest component) to stop
it contributing its own `xsu` connections. No user-facing toggle exists;
`pm disable-user` can't target a non-manifest listener; the one shared
root-shell chokepoint in `gamewindow`'s own code also carries its fan
writes, which must not be touched blanket-style. Not pursued further —
too much blast radius (would mean patching/freezing a signed system app)
for an uncertain payoff.

**Most promising direction found this session, validated repeatedly**:
the OLD bash `pulse_lite` (v3.2-v3.7, `docs/archive/pulse_lite/`) never
called `xsu` per-write at all — it launched ONCE (via AYASpace's own
"Root Script" feature) and ran its whole tuning loop as plain shell
builtins already running as root, for the whole session. Replicated the
core mechanism as a standalone script
(`research/pulse-for-aya/scripts/daemon-persistence-test.sh`): background
a script via a single `xsu -c "sh script.sh > out 2>&1 < /dev/null &"`
call (the stdio redirect is required — without it the launching
connection stays open for the whole run instead of closing immediately),
and it can write real `scaling_max_freq` caps continuously with **zero**
further `xsu` connections. **Confirmed 3-for-3 on real device**,
including once after a clean reboot, zero crashes each time, launch
connection closing in single-digit milliseconds. This is the strongest,
most evidence-backed lever found in the whole investigation — `AutoTDP`'s
own continuous tick loop is almost certainly the single largest
contributor to this device's total `xsu` connection volume over a real
session (far more than `gamewindow`'s one-time per-launch reaction), so
collapsing it to one connection could meaningfully reduce the pile-up
this bug thrives on.

**Refined further**: for the Kotlin↔daemon communication (Kotlin sends
new target values, daemon reports telemetry back), a **named pipe
(FIFO)** beats plain-file polling — confirmed on-device
(`research/pulse-for-aya/scripts/fifo-daemon-test.sh`): sub-millisecond
delivery vs. a multi-second poll interval, same "one connection ever"
property, no meaningful flash wear either way at this data volume.

**Checked and ruled out**: AIDL cannot help with telemetry reads either —
the complete `AyaAidlInterface` transaction table has only `send`/
`registerCallback`/`unregisterCallback`, no query method anywhere, and
the richer-than-expected callback payload only ever carries the *static*
5-mode preset table, never live FPS/thermal/busy% data. `TelemetryReader`/
`FpsReader` (both confirmed to go through `xsu` today) have no AIDL
shortcut — they'd need to move into the daemon-script pattern too, same
as the writes.

**One blocker found AND resolved tonight**: a FIFO needs a filesystem
location both the root daemon and the sandboxed `pulse-for-aya` app can
open. `/data/local/tmp` works fine for `xsu`/root but `pulse-for-aya`'s
own process gets `EACCES` there — confirmed live via a debug-only probe
(`verifyDataLocalTmpAccessOnDebugBuild` in `MainActivity.kt`,
`FileNotFoundException: ... EACCES`). Fix: the app's own private internal
storage, `filesDir` — confirmed to resolve to `/data/user/0/com.kei.pulse/
files` (via a second probe, `verifyFilesDirAccessOnDebugBuild`) — and
confirmed the root daemon can reach the SAME path too (`xsu -c "echo
probe > /data/user/0/com.kei.pulse/files/apl_root_probe.txt; cat ...; rm
..."` succeeded, read back `probe` correctly). Both probes are harmless,
reversible, debug-build-only, and left in `MainActivity.kt` as tooling
(not wired into the live path) — same pattern as the existing
`AyaAidlClient` verification hooks.

**Not yet done — the concrete next steps, in order**:
1. Confirm `mkfifo` itself works under `/data/user/0/com.kei.pulse/files`
   specifically (`fifo-daemon-test.sh` only exercised `/data/local/tmp`
   for the actual pipe creation) — cheap, should just be a path-swap
   rerun of the same script, but hasn't been done yet.
2. The small pre-engage delay idea (1-10s before AutoTDP's very first
   device-facing write on a fresh foreground-app change, to let
   AYASpace's own launch hooks finish first) has been discussed
   repeatedly this session but **never actually implemented or tested**.
   Cheap (a few lines, imperceptible to the user) and complements the
   daemon idea rather than competing with it.
3. Build the real thing: wire `AutoTuneController`'s CPU/GPU cap writes
   (and ideally `TelemetryReader`/`FpsReader`'s reads too) through the
   daemon+FIFO pattern instead of per-call `xsu`, add the pre-engage
   delay from (2), rebuild.
4. **The actual proof**: re-run the full Minecraft crash-timing
   reproduction (Game Mode ON → crash, compared against the existing
   ~59-73s baseline) with that build. Everything above is groundwork;
   this is the only test that actually answers whether it worked.


## Resolved / archived threads (full history in `STATUS_ARCHIVE.md`)

Everything below is closed, superseded, or purely historical — kept as a
one-line pointer so nothing is lost, but not worth every session re-reading
in full. Search `STATUS_ARCHIVE.md` for the heading text to find the full
entry.

- Inconclusive RetroArch test on the telemetry-migrated build — crash-looped before AutoTDP ever engaged, same shape as the post-install-reboot issue; no signal either way for the telemetry fix.
- `TelemetryReader` identified as the ~13-15-xsu-connections-per-call dominant crash contributor (not cap writes) — fixed via `PulseDaemon.readBatch()`; see the RESUMED entry above for current status.
- First continuous evidence AutoTDP genuinely regulates (a real TRIM/RAISE arc, zero xsu fallback, no crash) — proves regulation works correctly when a session gets the chance to run uninterrupted.
- An earlier "DEFINITIVELY CONFIRMED" ground-truth claim about AutoTDP regulation was wrong (all `cap_poll` files were flat within-session) — retracted, then properly reconfirmed (see the entry above).
- AutoTDP's tick loop appeared frozen (zero `PulseAutoTdp` log lines across 3/3 sessions) — root-caused to a FIFO hang in the first `PulseDaemon` cut (fixed) plus logcat's own 256 KiB ring buffer overflowing under `xsu` chatter.
- Odin power-ceiling tuning (11/12.5/14 W) confirmed inapplicable to this device's SoC (`SG8350P` resolves to `UNKNOWN`) — ruled out as an Eden-FPS cause.
- Diffed `pulse-for-aya` against upstream `pulse` (commit `0d2893e`): 2 new files + 6 modestly-modified files, manifest untouched — confirmed still a clean glue patch, not an unpatchable fork.
- Full blow-by-blow of the Minecraft/PULSE `xsud` crash investigation (governor-AIDL migration tried+reverted, daemon/FIFO architecture built, multiple crash captures) — durable facts are already folded into the RECAP kept above.
- `ab-logger`'s "empty CSV" bug root-caused: `xsud` segfaults on `xsu -c` commands over ~1000-1200 characters (bisected in `research/xsu-capability-probe/FINDINGS.md`); fixed via `XsuShell.execChunked`.
- Follow-up: call frequency/concurrency confirmed a much weaker `xsud` crash trigger than command length.
- INCIDENT #3: the empty-CSV bug recurred, then the device powered off entirely with `BatteryService` left stuck.
- INCIDENT #2: a full device reboot occurred while PULSE + `ab-logger` + Eden ran together.
- Bug: `pulse-for-aya` falsely showed "device not compatible" — a `pServerAvailable` cache latched `false` after one transient probe failure; fixed.
- INCIDENT #1: the first real `ab-logger` session crashed `system_server`, device rebooted.
- `research/ab-logger/` built (2026-07-25): minimal A/B telemetry recorder vs. native AyaSettings.
- Repo merge note (2026-07-25).
- `diagnostics/` folded in from the separate `apl-diag` repo (2026-07-25).
- `research/pulse-for-aya/` created (2026-07-25): first buildable glue port of upstream `pulse`.
- Old end-of-day plan (2026-07-25) — superseded by `pulse-for-aya`'s actual build-out.
- Old end-of-day plan (2026-07-24) — superseded.
- Old "next session" priority list — superseded.
- Where things stood at this repo's first commit.
- Early "confirmed, don't re-litigate" facts (`xsu` callable via `ProcessBuilder`, sysfs writes confirmed, stdin method broken, etc.) — the durable ones are already promoted into `CLAUDE.md`'s Conventions/Hard rules.
- Old known risk (a batched sysfs-read stalled ~126s once under heavy Dolphin load, pre-`pulse-for-aya` prototype) — never re-verified against the current architecture.
- Old "not yet done" list for the abandoned from-scratch `apl/app/` build — moot since `pulse-for-aya` (forking upstream `pulse`) replaced that approach entirely.
- `research/autotdp-ab-harness/` created — second probe app, added fan/thermal testing.
- RESOLVED, major finding: AyaSpace's `AyaAidlService` is exported with no permission check — any app can drive profile/fan/GPU-cap/RGB changes via AIDL, no root needed. `pulse-for-aya` ended up using the `xsu` glue-patch route instead (see `pulse-glue-assessment/FINDINGS.md`), but this AIDL path remains available.
- Old rough-priority next-steps list for the abandoned from-scratch build — superseded by `pulse-for-aya`.
