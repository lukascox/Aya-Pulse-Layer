# FINDINGS — AIDL fan spike + sysfs follow-up (2026-07-29 / 2026-07-30)

**Confirmed on-device: `com_set_performance_fan` genuinely drives the real
fan, no root, over the same plain Binder connection `aidl-bind-spike`
already proved for performance-mode.** The discrete-mode half of this
spike (step 1) is a clean, repeatable success — a real, shippable feature.

**A real, editable fan curve (the actual upstream-`pulse`-parity goal) is
NOT achievable via AIDL — that channel is definitively closed. But raw
sysfs IS achievable — an earlier "also blocked" verdict was wrong,
root-caused and fixed the same day:**
1. **AIDL** (`com_set_fan_speed_strategy`) — dead code, proven by reading
   the dispatch bytecode: the handler parses the mode then just logs the
   rest, no format tried across four runs worked, and one malformed guess
   crashed `com.ayaneo.gamewindow` outright, requiring a device reboot.
2. **Raw sysfs** (`hwmon0/pwm1`, `fan_power_state`) — ~~blocked even for
   confirmed genuine root~~ **CONFIRMED WORKING (2026-07-30, later same
   day)**: the "Permission denied" result below was never a hard block —
   it was the same `chmod 666` unlock step this repo already uses for
   CPU/GPU (`PerformanceCommandBuilder.kt`), simply never tried on the fan
   nodes. `chmod 666` + `echo` landed a real, audible, RPM-confirmed duty
   change (2961→4780 RPM). See "Raw sysfs write CONFIRMED WORKING" below —
   supersedes "The sysfs `pwm-fan` write path is ALSO blocked" further
   down, kept for the record with a correction notice rather than deleted.

See "Root cause found" (AIDL) and "Raw sysfs write CONFIRMED WORKING"
below for both investigations in full. Raw logs: `results/run1/` through
`results/run4/` (each with `aidl_fan_spike_result.txt` +
`aidl_fan_spike_logcat_dump.txt`; run4 adds `gamewindow_crash_excerpt.log`
for the crash). The sysfs follow-up (both the original "blocked" finding
and the 2026-07-30 correction) was done by hand via one-off `adb shell xsu
-c` commands, no probe-app rebuild needed — no new `results/` folder.

## Step 1 — discrete fan mode: confirmed, strong evidence, not just plausible

Two independent lines of evidence agree, not just one:

**1. Real PWM duty changed, tracking the requested mode** (via the
confirmed `pwm-fan` hwmon read-back):
- `FAN_MODE_OFF` → duty **0** (run1, both times sent — perfectly consistent).
- `FAN_MODE_MUTE` → duty **76** (all runs, every time sent — perfectly
  consistent).
- `FAN_MODE_TURBO` / `FAN_MODE_BALANCE` → duty noisier across runs (toggles
  between values rather than one fixed value each — see run3's TURBO read
  showing duty=25 right after a CUSTOM-mode send, most likely still
  settling). Read as fan/PWM mechanical settling lag against this spike's
  fixed 1.5s post-send delay (RPM visibly still ramping in several reads),
  not evidence the command didn't work — OFF/MUTE's perfect repeatability
  across all three runs rules out "read timing is just noise" as a general
  explanation.

**2. The vendor's own unsolicited state callback confirms it independently
of our sysfs read.** Every `send()` triggers gamewindow to hand back a
full JSON dump of all 5 modes' `ModeConfiguration` (same bonus finding as
`aidl-bind-spike/FINDINGS.md`). `currentMode` was `3` (Gaming) throughout
run1 — extracting mode `"3"`'s own `fanMode` field from every single
callback in run1, in order:

```
TURBO, BALANCE, MUTE, OFF, TURBO, BALANCE, TURBO, BALANCE, MUTE, OFF, CUSTOM, BALANCE, TURBO, BALANCE
```

— a **perfect, 1:1, zero-miss match** against the exact sequence of
buttons pressed. This isn't our own code reporting success; it's
`com.ayaneo.gamewindow` itself confirming, unprompted, that the active
profile's fan mode changed to exactly what was requested, every time.
Runs 2 and 3 show the same pattern on spot-check (every mode send's
callback `fanMode` matches what was sent). As strong a confirmation as
this project has produced for any AIDL command.

## Step 2 — custom curve write (`com_set_fan_speed_strategy`): strong negative after 3 runs

The mode-switch half of step 2 reliably works in every run (`fanMode` for
mode 3 correctly shows `CUSTOM` in the callback right after sending
`com_set_performance_fan:FAN_MODE_CUSTOM`). The curve-*content* write does
not show any evidence of taking effect.

### Run1 (moderate curve, temp not logged) — inconclusive

`com_set_fan_speed_strategy:FAN_MODE_CUSTOM-50,12|65,32|78,68|85,95|95,100`
produced `duty=0` immediately, `duty=25` on manual re-check — `25` being
suspiciously identical to run1's own pre-test baseline duty. No SoC temp
was logged this run, so "the SoC was below the curve's lowest point
(50°C)" couldn't be ruled out. Verdict at the time: not proven either way.

### Run2 + Run3 (flat 100%-everywhere curve, temp logged every read) — strong negative

Runs 2 and 3 switched to a deliberately unambiguous test curve —
`30,100|50,100|70,100|85,100|95,100`, i.e. **100% duty at every defined
temperature point, including the lowest (30°C)** — and added real SoC
temp logging (max across all `thermal_zone*` CPU zones) to every read, so
"was the SoC too cold" is no longer a blind spot.

Every curve-write attempt across both runs, with the CPU temp logged at
that exact read:

| Run | Attempt | CPU temp (max) | Duty read back |
|---|---|---|---|
| run2 | 1 | 39.6°C | 76 |
| run2 | 2 | 38.0°C | 25 |
| run3 | 1 | 42.2°C | 76 |
| run3 | 2 | 42.2°C | 25 |
| run3 | 3 | 39.5°C | 25 |
| run3 | 4 | 40.3°C | 76 |

**0 of 6 attempts produced anything near duty=255**, despite every single
read showing CPU temp comfortably above the curve's lowest point (30°C) —
if the curve had been applied, every one of these reads should show
duty≈255, unconditionally. Instead, results only ever land on **25 or
76** — the same two values already established elsewhere in these runs as
belonging to *other* states (76 = confirmed exact `FAN_MODE_MUTE` duty;
25 = this session's pre-test idle value) — and show no correlation with
the (mildly rising, 38→48°C) temp trend across the runs, which a real
temperature-responsive curve (ours or a pre-existing saved one) should
show. Most consistent explanation: these are settling-lag leftovers from
whichever discrete-mode command preceded the curve send, not a curve
being evaluated at all.

**Verdict: `com_set_fan_speed_strategy`, as constructed here, does not
appear to change real fan behavior.** This doesn't yet prove the *reason*
(malformed string format vs. a code path that isn't wired to PWM output
on this firmware vs. something else) — see "Not yet done" — but the
"we just happened to test at the wrong temperature" explanation (run1's
open hypothesis #1) is now firmly ruled out by runs 2-3, and "coincidence
with a legitimately temp-driven pre-existing curve" (hypothesis #3) is
much weaker too, since 6 attempts across a real temp range never showed
temp-correlated variation.

## Run4 — three format guesses, one of them crashes `com.ayaneo.gamewindow`

Run4 tried three alternate `mode-pairs` string formats (all still built on
the same flat-100%-everywhere shape): swapped `duty,temp` order, `;` as
the pair separator, and dropping the `FAN_MODE_CUSTOM-` prefix entirely.

**Guess A (swap order) and Guess B (semicolon) — still no effect,
consistent with runs 2-3.** Tried twice each (once before, once after the
crash below, with a fresh `clientId`): duty read back as 25 or 76 both
times, never near 255. Same pattern as before — mode-switch to CUSTOM
confirmed via callback, curve content still not landing.

**Guess C (no `FAN_MODE_CUSTOM-` prefix) crashed `com.ayaneo.gamewindow`
outright — twice.** Full stack trace from `full_logcat.log`:

```
FATAL EXCEPTION: DefaultDispatcher-worker-10
Process: com.ayaneo.gamewindow, PID: 4122
java.lang.IllegalArgumentException: No enum constant com.ayaneo.gamewindow.utils.FAN_MODE.30,100|50,100|70,100|85,100|95,100
	at java.lang.Enum.valueOf(Enum.java:300)
	at com.ayaneo.gamewindow.utils.FAN_MODE.valueOf(SettingsUtil.kt:3)
	at com.ayaneo.gamewindow.utils.aidl.AYAAidlManager$dealMsg$1.invokeSuspend(AYAAidlManager.kt:922)
```

This is a **real, reproducible, unprivileged crash bug in a vendor system
service** — not a "nothing happened" result. `AYAAidlManager.dealMsg`
evidently splits the `com_set_fan_speed_strategy` payload on the first
`-` and feeds everything before it straight into
`FAN_MODE.valueOf(...)` with no validation; when the prefix is missing,
the *entire* curve string becomes the "enum name" and `valueOf()` throws
an exception nothing catches, killing the whole `com.ayaneo.gamewindow`
process (which also owns the game overlay, notifications, and
`WindowKeyEventService`/key remapping). Android auto-restarted it after
the first crash (silent), but the *second* identical crash (same guess,
retried after reconnect with a new `clientId`) triggered Android's
user-facing crash dialog instead and the service didn't cleanly come
back — the user had to reboot the device to fully recover. Full context
kept in `results/run4/gamewindow_crash_excerpt.log` (trimmed from a
1.2MB full-system logcat dump to the ~450 lines around both crashes).

**This settles the "is the prefix mandatory" question for good — yes,
confirmed by the vendor's own code, not just inferred.** It also
confirms the enum type really is `com.ayaneo.gamewindow.utils.FAN_MODE`
(not just our reconstructed guess) and pinpoints the exact handler
(`AYAAidlManager.dealMsg`, decompiles to `AYAAidlManager.kt`). The
no-prefix guess button has been **removed from the app** (was never a
live hypothesis to retry — it's now a confirmed crash trigger, not
something to tap again).

## Root cause found: `com_set_fan_speed_strategy` is a stub, not a format bug (2026-07-30)

The mystery of why even correctly-prefixed guesses (Guess A/B, and the
original format in runs 1-3) never changed real duty is now **fully and
definitively closed** — not by more live guessing, but by reading the
actual message-dispatch bytecode. `research/aya-gamewindows-teardown/`'s
decompile of this exact method had previously failed silently (JADX
couldn't reconstruct this specific Kotlin-coroutine state machine); a
re-decompile with `jadx --comments-level debug` recovered a raw
instruction dump instead, which — while harder to read than clean Java —
is complete and unambiguous. Full detail, including the curated
instruction-dump excerpt of all three fan-related command branches:
`research/aya-gamewindows-teardown/FINDINGS.md` section 9 and
`research/aya-gamewindows-teardown/evidence/aidl/
AYAAidlManager_dealMsg_fan_excerpt.txt`.

**The short version: `com_set_fan_speed_strategy`'s handler splits the
payload on `-`, parses the mode from element `[0]` (which is exactly what
crashed on the no-prefix guess), and then does nothing with element `[1]`
except log it via Timber (labeled `"| json = "` in the log line — a real
hint the intended payload was JSON, not our reconstructed
`temp,duty|temp,duty` shape) — no write, no persistence, no hardware
effect of any kind.** It's a genuine stub/dead code path in this app
build, full stop. No string format could ever have made it work — the
handler doesn't act on its input regardless of shape. For contrast,
`com_set_performance_fan` (which does work) calls straight into
`PerformanceManager.g(mode, ...)`, and `com_set_fan_speed_is_linear`
(untested live, but structurally real) persists via a `ContentProvider`
write (`AyaShareConfUtilKt.e(...)`).

## Raw sysfs write initially looked blocked — SUPERSEDED, see next section (2026-07-30, morning)

Following the AIDL dead end, the plain `pwm-fan` sysfs write
(`research/aya-gamewindows-teardown/FINDINGS.md` section 6, `AR03.t1(int)`)
was tried live, by hand, via a few one-off `adb shell xsu -c "..."`
commands (no probe app needed — this is just a kernel file). Result:
**also blocked, and not for an obvious reason.**

- `echo 180 > .../hwmon0/pwm1` → `Permission denied`, even after first
  writing `fan_power_state=1` (the exact sequence `AR13.n1()` — the
  device-specific class *confirmed live on this exact hardware* — uses
  before its own `t1()` write, per `evidence/fan/AR13_fan_excerpt.java`).
  Both the `fan_power_state` and `pwm1` writes failed the same way.
- The file is `-rw-r--r-- root root` — a completely ordinary,
  root-writable-looking mode.
- `xsu` confirmed genuine `uid=0(root) gid=0(root)` (`id` output) — this
  is real root, not a partial/fake elevation.
- `getenforce` → `Permissive` — SELinux is not enforcing anything system-
  wide right now.
- `dmesg` around the failed writes shows **zero matching `avc: denied`
  entries** for either `fan_power_state` or `pwm1` (the only denials
  present are unrelated — `dmesg`'s own syslog access, and a `cat` read
  of `fan_rpm_state` that "denied" in the log but **succeeded** in
  practice, exactly as expected under Permissive).
- `/sys/kernel/security/lsm` (would list all active Linux Security
  Modules, if `securityfs` exposes it) returned empty — inconclusive, but
  rules out an easy "oh, there's a second LSM stacked with SELinux and
  *that one's* enforcing" answer via the standard mechanism.

**Genuine root, ordinary file mode, SELinux confirmed non-enforcing, zero
audit trail — and the write still fails.** The most likely explanation is
that the kernel driver behind this specific `pwm-fan` sysfs attribute
enforces its own access check inside its `store()` callback (independent
of standard Unix permissions and independent of SELinux), or that some
other protection mechanism is involved that doesn't register through the
usual channels checked here (e.g. something TrustZone/TEE-adjacent).
Confirming exactly which would require decompiling/analyzing the actual
kernel driver binary — a materially bigger undertaking than the
adb-one-liner probing this whole `aidl-fan-spike` project has used so
far, and out of scope unless deliberately picked up as its own effort.

**Correction (2026-07-30, later same day): the framing above was an
overreach — see the next section.** The actual fix took one shell command
already sitting in this repo's own codebase.

## Raw sysfs write CONFIRMED WORKING — the missing step was chmod-unlock (2026-07-30, later same day)

The "blocked" verdict above was wrong — not a wrong observation (the
writes genuinely failed as reported), but an incomplete investigation.
This repo already has a working precedent for exactly this class of
problem:
[`PerformanceCommandBuilder.kt`](../pulse-for-aya/app/src/main/java/com/kei/pulse/root/PerformanceCommandBuilder.kt:5)
generates `chmod 666 $path; echo $value > $path; chmod $mode $path` for
CPU/GPU cap writes on this exact device — an "unlock, write, relock" dance
that was never tried on the fan sysfs nodes during the morning
investigation above. It was the missing step.

**Live test, by hand, via `adb shell xsu -c`:**

```
$ xsu -c "ls -la /sys/devices/platform/soc/soc:pwm-fan/"
-rw-rw-r-- 1 root root 4096 ... fan_power_state
-r--r--r-- 1 root root 4096 ... fan_rpm_state   (read-only, expected)

$ xsu -c "chmod 666 .../fan_power_state .../hwmon0/pwm1; \
          echo 1 > .../fan_power_state; \
          echo 180 > .../hwmon0/pwm1; \
          sleep 2; cat .../fan_rpm_state; cat .../hwmon0/pwm1"
Current RPM 4780
180
```

Baseline RPM immediately before this test was 2961-2666 (multiple reads).
**RPM jumped to 4780 and the user independently heard the fan audibly spin
up** — two independent confirmations (sysfs read-back + physical/audible),
same evidentiary standard already used for the AIDL discrete-mode result
above.

**The system reasserts control on its own, fairly quickly.** A follow-up
read-only check ~1-2 minutes later (fan mode was never switched away from
BALANCE during this test) showed RPM already back down to 2663-2666 and
`pwm1` back to 76 — the vendor's own fan-control daemon overwrote our
manual value without any action on our part. This is good news for safety
(nothing was left stuck in a forced state) and confirms the same "vendor
daemon re-pins the value" behavior `pulse-glue-assessment/FINDINGS.md`'s
risk-assessment section flagged as a real possibility (mirroring the
Odin's behavior `pulse`'s own reassert loop was built to fight). **Open
question for actual `FanController.kt` implementation**: a real curve
controller will need to either out-pace this reassert cadence (same
`FAN_RECHECK_MS`-style loop `pulse` already has) or get the vendor daemon
to relinquish control first (untested whether switching to
`FAN_MODE_CUSTOM` via the already-proven AIDL discrete command stops the
daemon's own writes to `pwm1`) — not yet tested, next concrete step for
whenever this feature is actually built.

**One loose end, low priority, not a safety concern**: `chmod`-ing the
files back down to their original mode (644 for `pwm1`, 664 for
`fan_power_state`, the same values `ls -la` showed before the test) failed
with `Operation not permitted` (`EPERM`) — asymmetric with the unlock
direction, which worked without issue. Both nodes are currently left more
permissive (666) than their stock mode. Not believed to be a safety or
stability risk (sysfs attribute files are typically re-created by the
driver at boot with their default mode, so a reboot most likely restores
it on its own), and doesn't block using the unlock pattern going forward
(the unlock step is what matters for writing). Possibly related to the
mount-namespace anomaly noted below — not chased further this session,
flagged for whenever the real curve controller gets built.

**Also noted in passing, not chased further**: `readlink
/proc/self/ns/mnt` for the `xsu` shell returned a mount-namespace ID
distinct from what would be expected for a shell sharing PID 1's default
namespace (output was ambiguous/truncated in the terminal capture, only
one line of a multi-path `readlink` request printed cleanly) — consistent
with `xsu` running its shell in its own mount namespace, which could
explain permission asymmetries like the chmod-back failure above. Not
confirmed, not investigated further. `CapEff` for the `xsu` shell was
confirmed full (`000001ffffffffff`), so this isn't a capability-dropping
issue.

**Correction to the morning's dead-end framing**: SELinux Permissive
mode, genuine root (confirmed `uid=0`), and ordinary-looking file mode
were all real observations — they just didn't rule out the one thing that
actually mattered (whether the file's own mode bits were gating the write
at the kernel's standard permission-check layer, independent of the
process's identity). The "would need kernel-driver-level reverse
engineering" framing from the morning session was an overreach — the
actual fix was a two-line shell command already sitting in this repo's
own codebase, not found sooner because the CPU/GPU precedent wasn't
cross-checked against the fan investigation before writing the "closed"
verdict.

## Vendor daemon reassert cadence measured — no fixed period, range 1-112s (2026-07-30)

The open question from the previous section (does the vendor's fan daemon
fight a sustained manual duty write, and how often) was measured directly
instead of guessed at. Manual `adb shell` timing turned out too imprecise
for this (confirmed by two earlier by-hand attempts that gave
inconsistent-looking results) — two small on-device scripts were used
instead, both logging at 1s resolution with real timestamps to
`/sdcard/apl_pulse_logs/`:

- [`scripts/fan_reassert_probe.sh`](scripts/fan_reassert_probe.sh) — write
  duty=150 once, poll every 1s for 180s, record when (if) it reverts.
- [`scripts/fan_reassert_probe2.sh`](scripts/fan_reassert_probe2.sh) —
  same, but auto-detects the first reversion, immediately re-writes
  duty=150, and keeps watching to see if the *second* write also gets
  reverted (and after how long).

First attempt at the launcher script failed silently (no output at all,
even in the foreground) — root-caused to relying on `${1:-default}`
positional-parameter expansion, which this device's minimal `sh` likely
doesn't support cleanly, plus depending on the launching `xsu -c`
invocation's stdout redirect to capture output. Fixed by hardcoding the
constants (no positional-parameter defaulting) and having the script
write directly to its own log file under `/sdcard/apl_pulse_logs/`
(the same directory `pulse-for-aya`'s own session logs already use,
confirmed accessible with no special setup) instead of relying on an
external redirect.

**9 runs, 17 write→reassert measurements, raw data in
`results/run5/*.log`:**

```
1, 18, 19, 34, 41, 42, 47, 52, 54, 54, 55, 55, 57, 57, 57, 103, 112  (seconds)
```

- **Range: 1-112 seconds. Mean ≈ 50s, median = 54s.** No fixed period —
  ruled out definitively (a 1s and a 112s result exist in the same
  9-run sample).
- **First-write delay** (n=9, one per script run): mean ≈ 34s, range
  1-54s.
- **Second-write delay** (n=8, immediately after the first correction
  already landed): mean ≈ 69s, range 54-112s — consistently higher than
  the first-write delay across every run that measured both. Possibly a
  backoff/debounce after a correction fires, possibly coincidence at
  this sample size — not confirmed, flagged for anyone who wants more
  data later, not chased further here.
- **Every single reassert corrected to exactly duty=76** — same value
  confirmed elsewhere as `FAN_MODE_MUTE`'s exact duty and this device's
  apparent idle/rest target, regardless of which of the two writes
  triggered it.
- **Sending `FAN_MODE_CUSTOM` via AIDL first does not disable or slow
  this reassert** — tested directly (custom_command intent extra sending
  `com_set_performance_fan:FAN_MODE_CUSTOM` before writing) — no
  detectable difference from not sending it. The "maybe CUSTOM mode is a
  polite hand-off signal" hypothesis is ruled out.

**Practical implication for a real curve controller**: only 1 of the 17
measurements (the single 1s outlier) fell under 10 seconds — writing the
target duty on a **~10s cadence** would preempt the vendor's correction in
16/17 (94%) of observed cases, with the rare exception producing at most
one brief, self-correcting blip rather than a sustained fight. This is a
dramatically lighter requirement than upstream `pulse`'s own 120ms
Odin-reassert loop — comfortably inside `xsu`'s established ~100ms
per-call floor with enormous margin, and far below the call-frequency
range this repo's own findings already ruled out as a meaningful crash
trigger (`STATUS_ARCHIVE.md`: "call frequency/concurrency confirmed a much
weaker `xsud` crash trigger than command length"). **A ~10s periodic
reassert loop, analogous in shape to `pulse`'s existing one but far less
demanding, is the concrete design for `FanController.kt`'s real curve
write.**

**One more practical note, not yet directly re-tested but logically
likely**: the `chmod 666` unlock could not be reverted back to the
original mode earlier (`Operation not permitted`, see above) — meaning
the fan sysfs nodes are very likely still unlocked right now. A real
implementation's reassert loop probably only needs to `chmod` once at
startup (or not at all, if it's confirmed still unlocked), then do a
plain `echo $duty > $path` on every tick — cheaper than a full
chmod+write cycle every ~10s.

## What this means for `pulse-for-aya`

**Step 1 alone is already a usable, real feature** — a discrete
OFF/MUTE/BALANCE/TURBO fan-mode toggle via this AIDL channel could be
wired into `FanController.kt` today with high confidence. This closes a
meaningful slice of the fan-control gap in
`research/pulse-for-aya/README.md`'s "Feature parity vs upstream" section
by itself.

**A real, PULSE-style editable fan curve is achievable — via raw sysfs,
not AIDL.** The two channels ended up in very different places:
- **AIDL** (`com_set_fan_speed_strategy`) — the handler is dead code, logs
  and returns, proven by reading the dispatch bytecode. No format guess
  can fix it. Closed, not worth revisiting.
- **Raw sysfs** (`fan_power_state` + `hwmon0/pwm1`) — **confirmed
  working** once unlocked with the same `chmod 666`/write/`chmod`-back
  pattern this repo already uses for CPU/GPU
  (`PerformanceCommandBuilder.kt`). Live, audible, RPM-confirmed duty
  change achieved.

Upstream `pulse`'s own `FanCurve.kt`/`FanTempController.kt` are pure
math/state models with no I/O of their own
(`pulse-glue-assessment/FINDINGS.md`, "Control-loop logic read") — they
were never the blocker. What was missing is a device-specific I/O layer
targeting the confirmed AYANEO path (`soc:pwm-fan/hwmon0/pwm1`, not
upstream's Odin-specific `gpio5_pwm2`) with the chmod-unlock step folded
in, mirroring `PerformanceCommandBuilder.kt`'s existing shape for CPU/GPU.
**The reassert-cadence question is now resolved (see "Vendor daemon
reassert cadence measured" above)**: the vendor daemon does fight a
sustained write, on an irregular 1-112s cycle (measured across 17
samples) — but a simple ~10s periodic re-write loop, much lighter than
upstream `pulse`'s own 120ms Odin-reassert loop, preempts it in the
overwhelming majority of cases. **Ready to design and build the real
`FanController.kt` curve path** — no further open questions block
starting that work.

## Not yet done

- ~~Root-causing why the AIDL curve write doesn't take effect~~ — **done,
  see above.** Confirmed dead code, not a format problem.
- ~~Testing the plain sysfs `pwm-fan` write path~~ — **done, see above.**
  Confirmed working with the `chmod 666` unlock step.
- Testing `com_set_fan_speed_is_linear` — still a live, worthwhile probe
  in its own right (unlike the strategy command, its handler genuinely
  persists a value via `AyaShareConfUtilKt.e(...)`, per
  `research/aya-gamewindows-teardown/FINDINGS.md` section 9), though it's
  now lower priority since the sysfs path doesn't need it. Not exercised
  in any run yet; possible via the `custom_command` intent extra without a
  rebuild, see `README.md`.
- Building the real `FanController.kt` curve controller against the
  confirmed sysfs path (~10s temp-polling/reassert loop, duty write via
  chmod-unlock, reused `FanCurve.kt`/`FanTempController.kt` math from
  upstream) — not started, deferred to a later session per the user's
  request. This is now the only remaining item that isn't a low-priority
  curiosity.
- ~~Whether the vendor daemon's reassert cadence fights a sustained curve
  controller, and whether switching to `FAN_MODE_CUSTOM` via AIDL first
  makes it back off~~ — **done, see "Vendor daemon reassert cadence
  measured" above.** Irregular 1-112s cycle, mean ~50s; `FAN_MODE_CUSTOM`
  confirmed to make no difference.
- The chmod-back-to-original-mode `EPERM` oddity and the mount-namespace
  anomaly that might explain it — low priority, no safety impact,
  optional curiosity for later.
- Identifying exactly why the chmod-unlock step is necessary for genuine
  root in the first place (bypassing `CAP_DAC_OVERRIDE` isn't supposed to
  need it) — academic at this point, not blocking any real work.
