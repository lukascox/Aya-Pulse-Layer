# FINDINGS — ClusterTune `su` fallback, static analysis

Target: `research/clustertune-upstream/` (fresh clone, commit as checked out
at assessment time). All paths below are relative to that directory unless
stated otherwise. Read in full: the whole `root/` package (7 files),
`docs/execution-methods-plan.md` (the design doc for exactly this
mechanism), the `root/` unit tests, `data/CpuPolicyDetector.kt`,
`data/PerformanceRepository.kt`, `apps/AppProfileMonitorService.kt`,
`ui/TunerViewModel.kt`, and the top-level `README.md`.

## TL;DR

Yes, a real, distinct `su`-based fallback exists (`RootShellExecutionMethod`)
— per-call, not daemon-based, same shape of risk we already know. But
ClusterTune's actual call pattern is structurally very different from
`pulse-for-aya`'s: reads try a plain unprivileged file read first and only
fall back to the privileged path when that fails, and privileged writes only
fire on discrete, user/foreground-app-driven events (profile switch), never
inside a continuous polling loop. That design choice — not clever `su`
engineering — is what would keep it out of the failure mode we hit. See
question 5 for the full verdict.

## 1. Is there a real `su` fallback, distinct from `PServerBinder`? Per-call or daemon-based?

**Yes, distinct, and confirmed per-call — no persistent process, no FIFO, no
daemon anywhere in the codebase.**

ClusterTune generalized its privileged-execution layer behind a
`PrivilegedExecutionMethod` interface
(`app/src/main/java/com/aure/clustertune/root/PrivilegedExecutionMethod.kt:18-32`)
with four concrete implementations selected by a resolver
(`PrivilegedExecutionResolver`, same file, `:34-145`):
`pserver-stdout`, `pserver-file-output`, `root-shell`, and `shizuku`. The
`su` fallback is `RootShellExecutionMethod`
(`root/PrivilegedExecutionMethod.kt:375-414`), backed by
`RootShellCommandRunner`
(`root/PrivilegedExecutionMethod.kt:471-504`).

`RootShellCommandRunner.run()` (`:472-481`) does exactly one thing per call:
```kotlin
ProcessBuilder(listOf("su", "-c", command)).start()
```
— a brand-new subprocess spawn for every single `probe()`, `executeScript()`,
or `readText()` invocation
(`RootShellExecutionMethod.probe()` `:380-391`, `.executeScript()`
`:393-401`, `.readText()` `:403-409`). There is a retry variant for one
specific `su` syntax quirk (`su 0 sh -c <cmd>` on userdebug builds whose `su`
rejects the `-c` short form, `:475-481`, `:500-503`) but it is still a fresh
process each attempt, not a reused shell.

Confirmed no daemon/persistent-process pattern exists anywhere in the app —
a repo-wide grep for `mkfifo|fifo|nohup|persistent|daemon` across
`app/src` returns zero hits. This is the single most direct answer to the
question: **ClusterTune's `su` fallback is architecturally the "naive"
per-call pattern** — exactly what `pulse-for-aya` moved away from with its
FIFO-daemon architecture, not a daemon of its own.

`ShizukuExecutionMethod` (`:416-469`) is a fourth, separate mechanism (talks
to the Shizuku service via reflection, `newProcess`/`sh -c`) — also per-call,
also not a fallback for the no-`PServerBinder` case in practice, since it's
excluded from auto-detection (see question 2).

## 2. How does it detect which mechanism to use?

**Probe-then-cache, ordered list, with an explicit user-override — closer to
a superset of our own `RootExec.pServerAvailable` design than a single
boolean.**

`PrivilegedExecutionResolver.default()` (`root/PrivilegedExecutionMethod.kt:133-144`)
builds the four methods in this fixed candidate list, but only three
participate in automatic detection:
```kotlin
DEFAULT_AUTO_DETECTION_ORDER = listOf("pserver-stdout", "pserver-file-output", "root-shell")
```
(`:127-131`). `selectBestMethod()` (`:86-101`) probes candidates **in that
order** and stops at the first one whose `probe()` reports
`isAvailable == true` — i.e. `PServerBinder`-with-stdout is tried first,
then `PServerBinder`-without-reliable-stdout, and `su` is the last resort,
exactly the "device doesn't have PServerBinder → fall back to su" case the
user's tip described. `shizuku` is deliberately left out of this list —
confirmed both in the design doc
(`docs/execution-methods-plan.md:13`, `:54-57`: "Shizuku remains available
for explicit testing, but is not part of automatic selection because a
normal shell-level Shizuku session may lack permission to change CPU
controls") and in a dedicated test,
`PrivilegedExecutionResolverTest.kt:94-107`
(`` `auto detect skips shizuku and persists root when pserver methods are
unavailable` ``), which asserts `shizuku.probeCount == 0` even when it's the
only other available method.

**Caching**: `selectedMethod(forceReprobe = false)` (`:65-84`) returns the
cached method immediately if one is already selected; otherwise it
re-probes the *entire* candidate list from scratch, every time, until one
succeeds. Critically — same lesson as `RootExec.kt`'s "only latch `true`,
never cache `false`" fix in `pulse-for-aya`
(`research/pulse-for-aya/app/src/main/java/com/kei/pulse/root/RootExec.kt:25-35`)
— **ClusterTune's resolver never caches a negative result either**: if
`selectBestMethod()` finds nothing available it returns `null` without
setting `cachedMethod`
(`root/PrivilegedExecutionMethod.kt:86-101`, no assignment on the `null`
path), so the very next call to `isAvailable`/`selectedMethod()` re-probes
everything again rather than staying poisoned. Independently arrived at,
same fix. One difference worth flagging: because there's no boolean latch at
all, a persistently-unavailable device would re-probe the full 2-3-method
list on every call site that checks `isAvailable` (e.g. once per
`observeState()` emission, question 5) — cheaper to write correctly, but
each individual probe still costs a full `echo <marker>` round trip through
whichever method is being tried, so a chronically-unavailable device pays a
repeated cost this codebase doesn't seem to have optimized away (not
something we experience, since our device settles on `root-shell`
successfully every time via `xsu`, but worth noting as the mirror-image
tradeoff of never caching false).

**User-selectable override**: a persisted setting
(`data/SettingsStorage.kt:48,76,196-198`, key `privileged_execution_method_id`)
can force a specific method via `setConfiguredMethodId()`
(`root/PrivilegedExecutionMethod.kt:52-57`), which `selectedMethod()` checks
*before* falling through to auto-detection (`:72-81`) — confirmed by test
`` `configured method wins over auto detection order` ``
(`PrivilegedExecutionResolverTest.kt:81-91`). This is the only way `shizuku`
is ever reachable in practice — a user must explicitly pick it in Settings
(`ui/SettingsScreen.kt:351`, `ui/TunerViewModel.kt:351`).

## 3. What does it write, and is there any batching/chunking discipline?

**CPU-only — `scaling_max_freq` per cpufreq policy, nothing else.** A
repo-wide grep for `kgsl|gpu_avail|devfreq|gpufreq` across `app/src` returns
zero hits — no GPU sysfs surface at all, confirmed also by the README's own
scope statement ("tuning CPU frequency limits", `README.md:11,22`). This is
narrower than `pulse`'s own scope (which also does GPU `kgsl-3d0`
pwrlevels) — ClusterTune is CPU-cluster-cap only, no fan, no GPU.

**Write shape**: `PerformanceCommandBuilder.buildApplyScript()`
(`root/PerformanceCommandBuilder.kt:7-27`) generates one small shell script
per apply, 3 lines per policy:
```
chmod 666 <scalingMaxPath>
echo <value> > <scalingMaxPath>
chmod <444|644> <scalingMaxPath>
```
For a typical device (2-4 cpufreq policies) that's roughly 6-12 lines.

**Batching/chunking discipline — present for PServer paths, absent for the
`su` path.** This is the most concrete, evidence-backed structural
difference:
- The PServer stdout method writes the whole script to a file first, then
  runs only `sh <path>` as the actual command
  (`PServerStdoutExecutionMethod.executeScript()`,
  `root/PrivilegedExecutionMethod.kt:176-188`, `writeScriptFile()`
  `:630-644`) — the exact "write to file first" pattern `pulse-for-aya`
  eventually adopted for the same reason (`STATUS.md`'s "correctness fixes"
  entry, `RootSupport.runGeneratedScript()`).
- The PServer file-output method does the same (`:236-303`, writes both the
  command script and a wrapper script to files).
- **`RootShellExecutionMethod.executeScript()` does not** — it passes
  `scriptContents` (the whole multi-line script, unmodified) directly as
  the `su -c` command-line argument
  (`root/PrivilegedExecutionMethod.kt:393-401` →
  `RootShellCommandRunner.run(command, ...)` → `ProcessBuilder(listOf("su",
  "-c", command))`, `:472-481`). `ShizukuExecutionMethod.executeScript()`
  does the identical thing (`:448-456` → `newProcess(arrayOf("sh", "-c",
  command))`, `:533-545`). **This is precisely the anti-pattern our own
  investigation flagged as the trigger for `xsud`'s crash** — a whole
  multi-line command blasted through `-c "<string>"` as one argument,
  instead of writing to a file and invoking `sh <path>`. No chunking, no
  length check, no file-write fallback for the `su`/Shizuku paths at all.
  In practice ClusterTune's own scripts are short (a handful of lines, a
  few hundred characters at most — nowhere near the ~800-1200 char band
  bisected in `research/xsu-capability-probe/FINDINGS.md`), so this isn't
  observed to be a live bug in ClusterTune's own usage, but the *pattern* is
  the one we know is risky, present exactly where the design doc says it's
  least tested ("First implementation can be conservative and not lock
  files aggressively until tested", `docs/execution-methods-plan.md:52`,
  and Milestone 2's own open checkbox: "On-device verify before treating it
  as supported in UI", `:74`).

## 4. Error handling / retry / crash-recovery around the `su` fallback?

**Minimal, and none of it is about broker flakiness.** The only retry logic
found anywhere in `root/` is `shouldRetryWithUserdebugSuSyntax()`
(`root/PrivilegedExecutionMethod.kt:500-503`), which detects the specific
string `"invalid uid/gid '-c'"` in a failed `su -c` call's output and
retries once with `su 0 sh -c <cmd>` instead — a compatibility shim for a
known `su` argument-parsing variant on some ROMs/`su` implementations, not a
response to timeouts, crashes, or a flaky root broker. A repo-wide grep for
`retry|attempt` across `app/src` returns only this one hit.

There is no automatic retry-on-timeout or retry-on-crash anywhere:
`collectOutput()` (`root/PrivilegedExecutionMethod.kt:584-617`) enforces a
per-call timeout (30s for `executeScript`, 10s for `readText`/`probe`,
`:398`, `:404`, `:381`), and on timeout just returns a failed
`ShellCommandResult` with `failureMessage = "Command timed out after ${t}s"`
— no retry loop wraps it. At the UI layer,
`TunerViewModel.applyCurrent()` (`ui/TunerViewModel.kt:133-160`) surfaces a
failure as a one-shot `transientError` message
(`:153-154`, `throwable.message ?: "Failed to apply limits"`) for the user
to read and manually retry (re-press Apply) — no automatic backoff/retry
loop exists at any layer above the shell-command runner.

**Bottom line for this question**: ClusterTune has evidently never fought
the specific class of problem `STATUS.md` documents (`xsud`'s
`xsu_conn_handler` stack-overflow crash, connection-volume pile-up,
Android's Phantom Process Killer reaping orphaned children). Its only
hardening is a narrow, unrelated `su`-syntax compatibility fix. Nothing in
this codebase looks like it was written in response to broker crashes or
connection-volume problems — consistent with a fallback path that, per
question 5, is rarely exercised under sustained load in the first place.

## 5. Bottom-line verdict: better pattern, validation, or naive fallback that would hit our wall?

**Mostly the third — a naive per-call fallback, by ClusterTune's own
admission (still marked "not yet on-device verified" in its own design doc)
— but it's saved from being a direct parallel to our problem by a scope and
call-pattern difference, not by better `su`-handling engineering. Worth
being precise about which claim is being made here.**

- **On raw `su`-fallback mechanics: no better pattern, and no validation of
  our approach either.** `RootShellExecutionMethod` is exactly the "spawn a
  fresh root-shell process for every single write/read" pattern the user
  asked about, with the added anti-pattern (question 3) of inlining whole
  multi-line scripts into a single `-c` argument instead of writing them to
  a file first — worse, not better, than even upstream `pulse`'s
  `PServerBinder`-targeted `RootSupport.runGeneratedScript()`, which at
  least wrote scripts to a file
  (`research/pulse-glue-assessment/FINDINGS.md`, section 1). ClusterTune's
  own docs (`docs/execution-methods-plan.md:52,74`) explicitly flag this
  path as unverified on real devices and conservative-by-necessity, not a
  deliberately hardened design. It offers `pulse-for-aya` nothing to adopt
  on the mechanism itself.
- **What actually protects ClusterTune isn't the `su` layer — it's how
  rarely and how narrowly it's invoked.** Two structural choices matter
  more than anything in `root/`:
  1. `CpuPolicyDetector.readText()` (`data/CpuPolicyDetector.kt:109-122`)
     always tries a **plain, unprivileged filesystem read first**
     (`fileSystem.readText(path)`) and only escalates to the privileged
     executor if that fails — and `scaling_max_freq` is commonly
     world-readable on Android/Linux, so in the common case the
     `su`/PServer path is never touched for reads at all. `pulse-for-aya`
     has no equivalent "try unprivileged first" short-circuit; every read
     goes through `xsu` (or now the FIFO daemon) unconditionally.
  2. Privileged **writes** only happen on discrete, user- or
     foreground-app-change-driven events — an explicit "Apply" button press
     (`ui/TunerViewModel.kt:133-160`), a Quick Settings tile cycle
     (`data/PerformanceRepository.kt:353-390`), or a foreground-app
     transition detected by a 750ms polling loop
     (`apps/AppProfileMonitorService.kt:172`, `:55-107`) — but that loop
     itself only calls `applyProfileTemporarily()`/
     `restoreNormalProfileTemporarily()` (an `executeScript` write) when
     the *assigned profile actually changes*, not every tick; most ticks
     just re-read already-cached state. There is no analogue anywhere in
     ClusterTune to `pulse-for-aya`'s continuous AutoTDP tick loop
     (`STATUS.md`'s ~120ms-cadence fan-reassert / continuous regulation
     loop) that would need to sustain `su` calls at a rate anywhere close
     to the connection-volume pile-up that triggers `xsud`'s crash.
  3. `PerformanceCommandBuilder` batches an entire profile-apply into
     **one** script/one `su -c` call regardless of policy count
     (question 3) — so even the rare write event is a single connection,
     not one-per-node.
- **On the crash-risk transfer itself: likely inapplicable, and for a
  reason specific to our device, not ClusterTune's design.** ClusterTune
  targets standard `su` (Magisk/KernelSU-class root on genuinely rooted
  phones/handhelds) — a far more heavily used, hardened root broker than
  AYANEO's bespoke `xsud`. The specific bug we chased (`xsu_conn_handler`'s
  stack-overflow, `STATUS.md`) is a property of AYANEO's own binary, not a
  generic property of "any root-shell fallback"; nothing in ClusterTune's
  source or docs suggests its authors have ever seen an equivalent failure,
  and question 4 found no code shaped like a response to one.

**Honest summary**: ClusterTune's `su` fallback does not validate our
FIFO-daemon architecture (it doesn't solve the same problem at all — it
never needed to, given its call pattern), and it does not offer a better
mechanism to adopt (it's a thinner, less-hardened version of what upstream
`pulse` already had). What it *does* offer, worth carrying back into
`pulse-for-aya` as a genuinely useful idea independent of the `su` question,
is the **direct-unprivileged-read-before-privileged-fallback** pattern in
`CpuPolicyDetector.readText()` — if any of `pulse-for-aya`'s own telemetry
reads target sysfs nodes that are world-readable on our device (worth a
quick on-device `ls -l` check on the nodes `TelemetryReader`/`FpsReader`
read), that could shave real connection volume off the FIFO daemon's read
side for free, the same class of win ClusterTune gets for its CPU-cap
reads today.
