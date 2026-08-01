# STATUS

Living document — update this in place at the end of a working session,
commit with a descriptive message. Do not create a new dated/versioned copy
of this file; `git log` is the history.

Remotes: a self-hosted Forgejo instance (private, primary, `origin`) and
GitHub (`github`, https://github.com/lukascox/Aya-Pulse-Layer, public).
Both carry the same history. `git push` goes to Forgejo only; the public
mirror is updated deliberately with `git push github main`.

## The repo is public — implications for future sessions

**History was rewritten once, on purpose.** Before publishing, every commit
was rebuilt to (a) redact device logs and (b) change the author address off
a work e-mail. Redactions applied across all history, not just at HEAD:
the home network name became `HOMENET`, and two unmasked hardware addresses
became `xx:…`/`yy:…` placeholders. Nothing else in the logs was altered,
so their technical value is intact. A pre-rewrite backup of `.git` was
session-local and is now gone; the rewritten history is the only one.

**Anything committed from here on is published the moment it is mirrored.**
Before adding new device logs, run the same check that was used then:
search for the real network name and hardware addresses, plus account
identifiers and tokens. `research/ab-logger/results/` is the folder where
this actually matters — logcat does not mask Bluetooth addresses the way
dmesg does.

**Licence is now explicit.** GPL-2.0 at the repo root, inherited from
`pulse`, covering the repository as a whole. README states plainly that
published logs are redacted.

Repo-name note: the GitHub name is `Aya-Pulse-Layer`, which abbreviates to
`apl` — the local directory name and the `apl-*` research prefixes stay
consistent with it.

## Current state and next step: two-device comparison (2026-07-31)

**Feature parity is closed.** Six of seven upstream features confirmed on
real hardware; full table in `research/pulse-for-aya/README.md`, "Feature
parity vs upstream".

**RGB is OUT OF SCOPE, not a to-do.** The user explicitly does not want it:
these devices historically had a flickering-RGB problem on the sticks, so
the stick LEDs are switched off entirely on their units. Do not propose
wiring it up as remaining work — the mechanism is documented
(`RgbManager`/`RgbUtil`, `Settings.System` under `ayaneo/share/*`,
`aya-gamewindows-teardown/FINDINGS.md` section 5) purely so the record is
complete.

**Shipping build**: `app-debug.apk` built 2026-07-31 00:45:39 — verified no
source file is newer, so it contains everything through commit `3558c2c`
(the fan-OFF arbitration fix). Same file goes to both devices. Stays a
DEBUG build deliberately: the user wants the session logging kept for now.

**Next work — two-device comparison.** Two units, **same model, same SKU,
different colour**: `B` (black) is the unit everything so far was tested on;
`W` (white) is a clean install, so it doubles as a "does the port work on an
untouched device" test. Pulled logs are filed by hand into `B/` and `W/`
subdirectories (the user declined adding a device ID to the log header —
manual foldering is enough, see [[minimal-app-changes-pareto]]).

- **Gotcha, easy to hit**: `analyze-pulse-logs.py` globs *recursively* and
  groups purely by the timestamp in the filename, and `groups[ts][key] = p`
  **silently overwrites** on collision. Two devices started within the same
  second would be merged into one bogus session with a file dropped. **Run
  the script separately on `B/` and on `W/`, never on their parent.**
- Test order on `W` is prerequisite-first, so a step-1 failure explains the
  rest: (1) is `xsu` even present on this unit — the whole app depends on
  it and it is not part of the app; (2) does the Custom fan option appear
  (means the duty-node probe passed); (3) does `fan AIDL client: Ready`
  appear in `logcat -s PulseFan:D`; (4) then normal feature tests.
- **Keep `sleep` OFF for this comparison.** It is the one feature with zero
  on-device evidence, and enabling it here would confound the clean-install
  test with an untested feature. Enable it separately afterwards (needs
  BOTH `sleepProfileEnabled` and a chosen `sleepProfileId` — the toggle
  alone does nothing).
- **Open observation**: `W` subjectively launches games more slowly than
  `B`, on identical hardware and identical software versions. `cap_poll`
  already logs everything needed to test the obvious causes (per-cluster
  CPU max/cur, governor, temps, fan) — if those come back identical, that
  is itself a result and points at storage, which these logs do not see.
  A factory reset of `W` is planned later; capturing logs BEFORE it would
  give a before/after on the same unit.

**Deliberately deferred, with reasons (do not re-propose unprompted):**
- **Gating the session logging behind `FLAG_DEBUGGABLE`** — worth doing,
  but only when a release build is actually being prepared. Currently the
  four log paths in `PulseDaemon.kt` are unconditional, so a release build
  would also write ~80 MB/day and run a 1 Hz poll, a detached `logcat` and
  `dmesg` polling forever. Cheap when the time comes: `pulse_daemon.sh`
  already guards every stream with `[ -n "$VAR" ]`, so passing empty
  strings disables all of it with no script change.
- **Serializing the FIFO round-trip** (a latent reply-correlation race
  between the 120 ms fan loop and the 1 s telemetry loop, both calling
  `readBatch` from separate coroutines) — judged below the Pareto line:
  never observed in any log, mostly masked by the existing size check, and
  its worst realistic outcome self-corrects on the next tick.
- **Listen-testing the discrete fan modes under load** — the modes are
  indistinguishable at idle (see below); whether they diverge under
  sustained load is interesting but unblocks nothing, since the Custom
  curve is the better lever anyway.

**Offered, not started**: a vendor-facing report for AYANEO covering the two
reproducible bugs found in *their* software — `com_set_fan_speed_strategy`
being a dead handler (which likely means AYA Settings' own fan-curve editor
does not work either) and the unvalidated `FAN_MODE.valueOf()` in
`AYAAidlManager.dealMsg` that crashes `gamewindow` (INCIDENT #4). Different
audience and structure from anything in this repo: reproduction, evidence,
suggested fix, impact.

## First two-device unsupervised session — both clean, plus AutoTDP's vsync blind spot (2026-08-01)

Both units, ~2h10m, started two minutes apart, PULSE fully in the background.
Files + full writeup: `research/ab-logger/results/unsupervised_session_2026-08-01/`
(`NOTES.md`).

**Not a controlled A/B, and the numbers must not be read as one**: `B` spent
~50 min mid-session on the charger, idle, screen off (low battery); `W` played
throughout. Both ran Minecraft, `W` also touched Dolphin/RetroArch/Eden/Chrome.

**Confounder eliminated**: vendor versions are **identical** on both units —
settings 1.1.112 over 1.1.100, gamewindow 1.5.84 over 1.5.78.

**Nothing broke on either unit.** No daemon restarts, `dmesg` clean apart from
the usual two boot-time false positives, every logcat hit pre-dating its
session. `B`'s 51-minute log silence (15:01→15:52) is the charger + screen-off
case, not a failure: `cap_poll` kept running at ~1 Hz with caps fully released
and the fan idling. Minecraft's `SIGSEGV` on `B` (15:15:17) falls inside that
window, i.e. **PULSE was demonstrably not regulating when the game died**.

**Regulation behaved as designed on both**: `B` oscillated (296 TRIM / 312
RAISE / 73 HOLD) because it was pinned against its thermal and power ceiling
chasing 90 fps at 75 °C / 10 W; `W` settled (112 / 69 / **982 HOLD**) holding a
flat 90.0 fps at 47 °C / 4.6 W. `xsu` fallback 7.7 % on `B` vs 2.1 % on `W`.

**Open, unexplained, benign**: one kernel `WARN_ON` on `W` — a WALT scheduler
assertion (`android_rvh_try_to_wake_up`). Not an oops; the device ran two more
hours. Checked against PULSE's core parking and it does **not** line up
(parking ended ~50 s earlier). `W` parked 22× to `B`'s 4× and only `W` warned,
but that is association, not a link. Excerpt:
`unsupervised_session_2026-08-01/W/evidence/kernel_walt_warning.txt`.

**New AutoTDP finding, from the Stardew session the previous night** (filed in
`unsupervised_session_2026-07-31/NOTES.md`): **AutoTDP starves vsync-locked 2D
games.** It trimmed the GPU 1050→422 MHz over 45 s and never stopped, because
its stop condition is an fps drop and Stardew reports exactly `fps=60.0
jank=0` until it collapses. The real bottleneck was one pegged core
(`bn=CPU`, `cpuPk=100`, SMAPI/Mono single-threaded) while CPU caps sat at
100 %. Same family as the open Eden thread, inverted. **Workaround: fixed tier,
not AutoTDP, for vsync-capped 2D titles.** Also confirmed there: a per-app
profile plus `gov=performance` pins frequency *at* the cap, which is why AYA
Settings' CPU readout looked frozen — PULSE doing as told, nothing broken.

**Fan: third session in a row with no curve data.** Both units stay on SMART
deliberately — this fan has bad coil whine at its working point. All
`PulseFan` lines are `arbiter=None`, which is correct. Nothing about the
Custom curve can be concluded from any of these pulls.

**Capture problems to fix next time**: `fan_test.log` came back 0 bytes (the
`logcat -s PulseFan:D` capture caught nothing), and neither `ayasettings_run.log`
recorded a UI launch — so the AYA Settings question below is still open with no
evidence either way.

## `W` PASSES the clean-install test — the port is not `B`-specific (2026-08-01)

First run on the white unit, untouched until this install. Six minutes, files
and full writeup in
`research/ab-logger/results/unsupervised_session_2026-07-31/` (`W/`, `NOTES.md`).
The prerequisite-first test order cleared in order:

1. **`xsu` is present and works on a stock unit** — the daemon started and
   carried the session (83 cap writes / 183 telemetry reads via FIFO, 1
   fallback). This was the real unknown and it is closed.
2. **The fan duty node exists and reads** (`fan_duty`/`fan_rpm` in `cap_poll`).
3. **AIDL bound, confirmed twice over** — `AIDL callback:
   fanMode=FAN_MODE_BALANCE` in the ring buffer, and objectively via PWM:
   SPORT drove duty 12→**255** (322→6718 rpm), SMART brought it back to 76,
   ~5 s vendor latency each way. Duty 255 is unreachable without the command
   landing.

AutoTDP ran; `dmesg` crash hits **0**; every logcat hit pre-dated the session
and was Eden crashing on its own.

**Open, and testable after the reboot**: `W` idled at `fan_duty=12` / 322 rpm
(fan effectively off) where `B` idles at 76 / ~2750, moving to 76 only once
PULSE sent SMART. That fits the user's report that **AYA Settings' UI did not
work on this unit** — an uninitialised vendor stack would not be regulating,
which is what duty=12 looks like. If `W` idles at 76 after the reboot, that
confirms it. On that reading PULSE restored cooling on a device whose vendor
software had silently stopped doing it.

The AYA Settings failure itself **left no trace**: a ~2-week `logcat -b all`
has no launch attempt, no exception, no `avc: denied`, no process death; the
package is `installed=true`/`stopped=false`/`enabled=0` and alive at 0 % CPU.
UI/init-level, and nothing implicates PULSE.

Vendor versions on `W` (recorded — a version difference would confound the
comparison): settings **1.1.112** over 1.1.100, gamewindow **1.5.84** over
1.5.78. `B` not checked yet; expected identical.

**Never commit the pre-reboot `logcat -b all` dump** (kept in the user's home
dir, out of the repo): home network name, a BSSID, four unmasked hardware
addresses, a personal e-mail. `dmesg` masks these, `logcat` does not.

## Another project targets this device, and it gives us a Plan B for `xsu` (2026-07-31)

`Ayaneo-PocketFit-tools` (https://github.com/The412Banner/Ayaneo-PocketFit-tools,
active, v1.2.0 dated today) cloned read-only to
`research/Ayaneo-PocketFit-tools/`, gitignored like the other reference
clones. Assessed once; no separate assessment doc, because one fact carries
the whole thing:

**It has a working Magisk root path for the Pocket FIT** (patch `init_boot`,
flash via `xsu`, deliberately no auto-reboot; OKEY method, credited to
@sunflower2333). That matters because everything in `pulse-for-aya` currently
rests on `xsu`, which is a hole AYANEO could close at any time. A proven
route to Magisk means `xsu` is not a single point of failure, and
plain `su` would also retire the whole `xsud` fragility class (~800 char
limit, crash-on-close, chunked fallback). **Not proposed as work** — recorded
so the fallback is known to exist if it is ever needed.

**Cannot be reused as code: the repo has no LICENSE**, so default full
copyright applies and nothing may be copied into this GPL-2.0 repo. Reading
is fine; a real need would mean a clean-room rewrite or asking the author.

No overlap and no contradictions otherwise: it does rooting, partition backup
and display tuning — no fan, no CPU/GPU caps, no telemetry, no button remap,
and it touches not one sysfs path we use. Only shared surface is
`peak_refresh_rate`/`min_refresh_rate`, already noted from the PAM guide.

One untested idea, flagged honestly rather than as a contradiction: they
invoke `xsu` as `listOf(XSU_PATH) + argv` (direct argv, no `-c`). That is a
**third** form, not the banned stdin one (`RootExec.kt:13`) — we use
`ProcessBuilder("xsu","-c",cmd)`. Bypassing the shell might sidestep the
command-length limit, but they only push trivial commands (`id -u`) through
it, so it is evidence of nothing at our write volume.

## Fan↔clock cascade CONFIRMED END-TO-END + the run5 reassert cadence is SUPERSEDED (2026-07-31)

First live `adb logcat -s PulseFan:D` capture with the Fan card actually on
**Custom** (`managed=6`), taken right after the session below showed nothing
because it had been on Smart. Two results, one of them a correction.

**1. The fan↔clock cascade works, and this is the first log that shows the
whole loop closing.** With AutoTDP on EFFICIENT (`ceilingC = 82`,
`AUTOTDP_FAN_CASCADE_GAP_C = 2` ⇒ the fan PI targets ~80 °C), the SoC climbed
44→56 °C while `applied` stayed pinned at the 20 % floor — then **peaked and
came back down on its own** (56→55→54→53→52) with the fan never leaving the
floor. AutoTDP trimmed clocks and shed the heat; the fan stayed silent. That
is precisely the design intent stated in `runCustomFan`'s doc comment
(`ForegroundAppMonitorService.kt`), observed working rather than asserted.
The idle gate behaved correctly on either side of it too: at 34 °C it handed
the fan to vendor Smart (`CUSTOM idle → vendor Smart`, `target=20 floor=20`)
rather than sitting in manual passthrough, and engaged Custom the moment the
game started.

**Consequence worth stating plainly**: in this configuration PULSE
deliberately keeps the fan at the floor until ~80 °C, paying in clocks
instead of noise. That is a choice, not a fault — but `applied=20%` at
75-80 °C would be the point to revisit whether a 2 °C cascade gap is right.
Nothing seen so far comes close (56 °C peak, 24 °C of margin).

**2. The `run5` reassert-cadence recommendation is SUPERSEDED — it was
measured at idle and does not survive load.** Under real gameplay the vendor
daemon reasserts on a **regular ~5 s** cadence, targeting duty **94-109**,
not the 50 s mean / fixed duty 76 the 9-run measurement produced. The
reassert is thermally/load driven, not a periodic timer (which also explains
run5's 1-112 s spread on an idle device). **The derived "~10 s reassert loop
is enough" design figure must not be reused.** No code changes: the shipping
loop runs at 120 ms and caught every drift. Corrected in place with the log
excerpt: `research/aidl-fan-spike/FINDINGS.md`, boxed SUPERSEDED note under
"Vendor daemon reassert cadence measured".

Incidental but useful: `fan_mode` read **4 (vendor Smart) throughout** while
PULSE owned the duty node — discrete mode and duty are independent layers, so
the AIDL mode readback can never tell you whether a Custom curve is live.

## Unsupervised session analyzed (2026-07-31, `B` only): clean, plus three findings and one non-finding

First real unsupervised play on the shipping build (`1.19.6 (303)`).
`W` was not installed yet, so this is single-device data, NOT the two-device
comparison. Kept files + full writeup:
`research/ab-logger/results/unsupervised_session_2026-07-31/` (`NOTES.md`).
Only the long session's `.log` and `_cap_poll.log` were kept; both `_dmesg`
and both `_logcat` files and the short 12:06:50 session were deleted after
review — every flagged crash keyword was verified noise (boot-time
`init`/`nvkeeper`/`qcrosvm` aborts replayed by the logcat filter, and driver
names containing "panic"), and the dmesg files were the only carrier of
partially-masked router BSSIDs in this pull.

- **The daemon survived ~3h15m of suspend and resumed cleanly** — main log
  is silent 18:16:09→21:31:10 while `cap_poll` keeps producing lines
  throughout, and AutoTDP resumes on wake with `session start` appearing
  exactly once in the whole log. Untested by design, strongest FIFO-daemon
  stability evidence so far.
- **`xsu` fallback at 4.9%** (296 vs. 5770 daemon cap writes), all inside
  16:11–18:15, zero afterwards — and **not** a daemon outage: successful
  daemon writes interleave in the same second, and the log has zero
  `error|fail|timeout|denied` lines. Up from ~2% in the first clean FIFO
  session. Unexplained, not chased; flagged because raw `xsu` is the
  historical prime suspect for crashes.
- **Zero Phantom Process Killer kills against 449 `diagservices` ANRs**
  (~every 45s, chronic as ever) — versus 21 kills in 10 minutes on
  2026-07-28. Verified not a filter artifact before the logcat files went.

**Non-finding, worth stating so it isn't misread as a regression**: the fan
did nothing all session (6 `PulseFan` lines, all `arbiter=None`) because the
Fan card was on **Smart** (`managed=4`/`bound=4`; `SMART = 4`, `CUSTOM = 6`),
and `FanArbiter.decide` correctly returns `None` both when AutoTDP owns a
non-Custom mode and when the vendor mode has not drifted. Nothing about the
fan curve can be concluded from this pull. Testing it needs the Fan card on
Custom AND a live `adb logcat -s PulseFan:D` — `fast-loop drift`/
`fan_mode drifted`/`AIDL callback` go to logcat only, never through
`pulseDaemon.log`, so a pulled log will never show them.

## `sleep/` package checked — the module's last unread area, and it needs nothing (2026-07-31)

The one part of `pulse-for-aya` never read during the whole port.
Verdict: **byte-identical to upstream and fully generic** — stock Android
screen-off/on broadcasts, pre-sleep CPU max frequencies stashed in the
app's own DataStore, applied/restored via the same
`PerformanceCommandBuilder` path everything else uses. No
`Settings.System` keys, no vendor packages, no hardcoded sysfs, no
`DeviceProfiles` gating — the opposite of the fan, which looked healthy in
code while writing to a dead key. Also **off by default**
(`AppSettings.sleepProfileEnabled = false`, self-terminates if started
while disabled), so an untouched install never runs it. No wakelocks,
receiver unregistered on destroy, mutex-guarded transitions. Nothing to
patch, nothing to watch.

**This closes the feature-parity assessment**: six of seven upstream
features confirmed working on this hardware, RGB the only real remaining
gap (mechanism already located — `RgbManager`/`RgbUtil`, `Settings.System`
under `ayaneo/share/*` — just not wired in). Full table:
`research/pulse-for-aya/README.md`, "Feature parity vs upstream".

Incidental, for the separate overnight-battery-drain question: this
feature being off by default rules it out as a contributor.

## Discrete fan modes CONFIRMED LIVE + the callback readback question answered YES (2026-07-31)

First on-device test of the discrete-mode work below. **All three modes
(Silent/Smart/Sport) send and are confirmed back by the vendor**, and the
open question from the readback fix is settled: **gamewindow DOES push its
state callback unsolicited when its own UI changes the fan** — so the
readback is a true drift detector, not just an echo of our own writes.
Evidence and the exact log excerpt: `research/pulse-for-aya/README.md`,
"Discrete fan mode implementation plan". PULSE correctly detected a change
made in native AyaSettings and re-applied its managed mode, settling after
each correction (upstream's intended behavior, working here for the first
time; confirmed with the user that they were the one changing it, since an
identical log with nobody touching the device would have meant the vendor
was reverting us instead). Also re-verified from the archived probe data
that `modeConfigurations[currentMode].fanMode` tracks every send exactly —
the parser reads the right field.

**Listen-test done, with an unexpected answer**: Silent sounds identical to
Smart because at idle it *is* identical — correlating `cap_poll`'s fan
duty/RPM against the logcat mode changes shows duty tracking temperature,
not the requested mode (Silent and Sport both at duty 76; Smart and Silent
both at 79; the only real excursion, 81, matches a 48°C spike). All vendor
modes converge on ~30-32% duty / ~2750 RPM at idle; they presumably only
diverge under load. Not conclusive — modes were switched every 3-5s, faster
than the vendor settles — but it explains the ear. **Consequence**: that
fixed idle point is where the fan's resonant whine sits and no discrete mode
escapes it; the Custom curve is the only way off (25% → duty 63, 35% → 89,
vs the vendor's 76-81). Nothing in this fork sets the discrete modes' speeds
— we send a mode name, the vendor computes duty — so nudging Silent/Smart
by a few percent is not something the app can do.

**Real bug found and fixed the same session: fan OFF set in native
AyaSettings was never corrected.** `FAN_MODE_OFF`/`FAN_MODE_CUSTOM` mapped
to `null`, and `FanArbiter` reads a `null` live mode as "unreadable, skip
the tick" rather than as drift — so PULSE went permanently hands-off while
believing it managed Smart, leaving the fan off indefinitely. Introduced by
the readback fix below the day before (its doc comment wrongly claimed the
arbiter would see it as drift). `FanController.arbitrationModeFor` now
separates "don't know yet" from "vendor state we don't manage"; regression
tests at both the mapping and arbiter levels. OFF is the one vendor state
that leaves the device with no active cooling, so this mattered more than
its corner-case framing suggested.

**Operational gotcha worth remembering**: `pulse_daemon.sh`'s detached
logcat is filtered to crash tags only (deliberately, to avoid ring-buffer
overflow), so `PulseFan` lines never appear in a pulled `_logcat.log` —
fan behavior must be captured live with `adb logcat -s PulseFan:D`.

## Fan mode readback fixed via the vendor's AIDL callback (2026-07-30) — a review pass caught the discrete-mode work being only half-live

Found by reviewing the session's own output before any on-device test ran:
the entire fan stack reads state through `FanController.readMode()`, which
on this device always returns `null` (it reads the dead AYN Odin
`Settings.System fan_mode` key — confirmed live, every `fan_mode=null` in
the drift log quoted in `pulse-for-aya/README.md` is that read). That
silently made `FanArbiter` unable to ever return `SetVendorMode`/
`ReleaseToVendor` (both branches bail on a null read), so the release
normalization it was written to fix stayed broken and two of the ten AIDL
call sites wired earlier were dead code; it also blanked the Tuner's fan
chip to `"—"` right after picking a mode.

Fixed by using gamewindow's own unsolicited whole-profile callback — which
`AyaAidlClient` already received and discarded — as the readback:
`parseFanModeFromCallback` extracts `modeConfigurations[currentMode].fanMode`,
`readMode()` prefers that cache over the dead key. **Known limit, stated
rather than papered over**: the callback is confirmed only as an echo of
our own sends; there is no `com_get_*` query command and no state dump on
connect (so `null` now means "unknown", with callers falling back to the
persisted `managedFanMode`), and whether it also fires on vendor-UI-initiated
changes is **unconfirmed** (ANSWERED YES on 2026-07-31, see the section
above) — every callback is now logged. Build/test/lint clean.
Full writeup: `research/pulse-for-aya/README.md`, "Follow-up the same day:
the readback was dead".

## Discrete fan mode (Silent/Smart/Sport) IMPLEMENTED (2026-07-30) — the deferred follow-up from the curve work, same day

No new research needed — re-checked `aidl-fan-spike/FINDINGS.md`,
`pulse-glue-assessment/FINDINGS.md`, and recent commits first and
confirmed everything required was already proven: `com_set_performance_fan`
(AIDL) was already confirmed working, `AyaAidlClient.sendFanMode()`
already existed (just unused outside a debug harness), and
`FanArbiter`/`ForegroundAppMonitorService`/`TunerViewModel` already had
~10 call sites dispatching to `FanController.setMode()` — the stubbed
`false` return was the only real gap. `setMode()` now calls
`sendFanMode(aidlModeFor(mode))` for real
(`SILENT`→`FAN_MODE_MUTE`/`SMART`→`FAN_MODE_BALANCE`/`SPORT`→`FAN_MODE_TURBO`).
Both the service and `TunerViewModel` (which runs its own independent
`FanController`, no channel to the service) now bind their own
`AyaAidlClient`. The earlier "(vendor default — direct switching not yet
available)" toast wording is reverted to a plain pass/fail. `FAN_MODE_OFF`
deliberately excluded (no slot in the 3-mode UI). Build/test/lint clean.
**Not yet on-device tested** — full plan and diff summary:
`research/pulse-for-aya/README.md`'s "Discrete fan mode implementation
plan" section.

## Fan curve controller BUILT and CONFIRMED LIVE (2026-07-30) — the curve half of the fan-control gap is closed

Same day as the reassert-cadence measurement below: found the existing
`FanCurveController`/`FanArbiter`/`FanCurve`/`FanTempController` stack
(inherited from upstream, previously unreachable since
`customFanAvailable()` was hardcoded `false`) was already a complete,
mature control system needing only a device-I/O-layer swap, not a
rewrite. Repointed it at AYANEO's confirmed sysfs path, added
`FAN_POWER_PATH`/RPM-format parsing, and — the one real architectural
change — routed the existing 120ms reassert loop through `PulseDaemon`'s
FIFO (`setCap`/`readBatch`, already-generic verbs, zero
`pulse_daemon.sh` changes) instead of a raw `xsu` connection every tick.
Build/test/lint clean (commit `39c937b`). Full plan and diff summary:
`research/pulse-for-aya/README.md`'s "Fan curve implementation plan"
section.

**Confirmed live the same session** via `adb logcat -s PulseFan:D`: the
arbiter correctly dispatches to the Custom loop, the idle-on-Smart gate
correctly stands down at rest instead of fighting the vendor over the
node, and — the key proof — multiple `"fast-loop drift: node=... 
re-pinned=..."` lines show the vendor daemon trying to reclaim the duty
node (to several different values, not the fixed idle duty seen in
isolated probe testing — plausibly something more dynamic under real
thermal load) and the daemon-routed reassert loop catching and
correcting every one, live, zero raw `xsu` calls, no crashes. This is
full end-to-end validation of the architecture the reassert-cadence
measurement below only predicted would work.

**This closes the BIG half of the fan-control gap**: the actual
upstream-parity goal — a real, editable, temperature-responsive fan curve
— is built, tested, and working on real hardware, not just a documented
possibility. Discrete mode (AIDL, proven 2026-07-29) was still a separate
follow-up at the time of writing; it was implemented later the same day,
see the two sections above. (The heading of this section originally
claimed the whole gap was closed — corrected here, it was only ever about
the curve.)

## Fan control — discrete mode + AIDL curve CLOSED, raw sysfs curve write CONFIRMED WORKING, ready to build (2026-07-30): INCIDENT #4 (crash, device reboot required)

User ran `research/aidl-fan-spike/` four times. Full writeup:
`research/aidl-fan-spike/FINDINGS.md`; feature-parity checklist updated in
`research/pulse-for-aya/README.md`.

**Step 1 (`com_set_performance_fan:<OFF|MUTE|BALANCE|TURBO>`) — confirmed,
strong evidence, two independent signals agree**: real PWM duty (via the
confirmed `pwm-fan` hwmon read) tracked the requested mode (OFF→0, MUTE→76
perfectly repeatable across all runs); AND, independently, gamewindow's
own unsolicited state callback (the same bonus per-mode JSON dump
`aidl-bind-spike` found) echoed the exact fan mode back for every command
sent, in order, zero misses — this is the vendor's own state, not our code
claiming success. **This alone is a usable, real feature** — a discrete
fan-mode toggle in `FanController.kt` is achievable now with high
confidence, independent of the curve question below.

**Step 2/3 (`com_set_fan_speed_strategy`, the real curve write) — confirmed
NOT working for every format tried across 4 runs.** Run1 (moderate curve,
no temp logging) was inconclusive. Runs 2-3 added real SoC temp logging
and an unambiguous flat-100%-everywhere test curve: across 6 attempts at
38-48°C (always above the curve's 30°C floor, which should force
duty≈255 if applied), duty never once reached near 255. Run4 tried two
more format guesses (swapped `duty,temp` order, `;` separator) — still no
effect. Mode-switch to CUSTOM itself remains confirmed working via
callback every time; specifically the curve *content* never lands.

**INCIDENT #4 — run4's third format guess (dropping the mandatory
`FAN_MODE_CUSTOM-` prefix) crashed `com.ayaneo.gamewindow` outright, twice,
and the device needed a full reboot to recover.** Exact cause, from the
crash stack trace: `AYAAidlManager.dealMsg` (in `AYAAidlManager.kt`)
splits the strategy string on the first `-` and passes everything before
it straight to `FAN_MODE.valueOf(...)` with no validation — with no `-`
present, the whole curve string became the "enum name" and threw an
uncaught `IllegalArgumentException`, killing the process (which also owns
the game overlay, notifications, and key-remap service). First crash
auto-restarted silently; the second identical crash (user re-tapped the
same guess after reconnect) triggered Android's crash dialog and the
service didn't fully recover, requiring a manual reboot. Full detail incl.
stack trace: `research/aidl-fan-spike/FINDINGS.md` run4 section; trimmed
crash log: `results/run4/gamewindow_crash_excerpt.log`. **The
crash-triggering guess has been removed from the app** (confirmed
dead end, not a live hypothesis). This does settle one open question for
good: the `FAN_MODE_CUSTOM-` prefix is mandatory, confirmed by the
vendor's own code, not just inferred.

**Root cause of the curve-write dead end, found (2026-07-30) without any
further on-device risk**: `com.ayaneo.gamewindow`'s decompiled sources
already existed locally in `research/aya-gamewindows-teardown/` (a prior
"not present on disk" claim above was wrong — corrected here), but the
crash-site method (`AYAAidlManager$dealMsg$1.invokeSuspend`, a Kotlin
coroutine state machine) had silently failed to decompile with plain
`jadx`. Re-running `jadx --comments-level debug` recovered a full raw
instruction dump instead, which settles it for good:
**`com_set_fan_speed_strategy`'s handler parses out the mode, then just
logs the rest of the payload via Timber — no write, no persistence, no
hardware effect, for any string format.** `com_set_performance_fan`
(works) and `com_set_fan_speed_is_linear` (untested, but does persist for
real) both call into real code paths by contrast. Full writeup:
`research/aya-gamewindows-teardown/FINDINGS.md` section 9, curated
bytecode excerpt in `evidence/aidl/AYAAidlManager_dealMsg_fan_excerpt.txt`
there. **The AIDL route to a custom fan curve is closed — no further
string-format guessing is worthwhile.** `FanViewModel.java` (AYA
Settings' own native curve editor) sends the identical command through
the identical channel, per `research/ayaspace-teardown/FINDINGS.md`'s
Addendum (corrected 2026-07-30) — so AYA's own UI likely doesn't apply
the curve either, not just our probe.

**The remaining lead (plain `pwm-fan` sysfs write, `AR03.t1(int)`) was
also tested live by hand (2026-07-30) — also blocked.** `echo 180 > .../
hwmon0/pwm1` (and the `fan_power_state` write `AR13.n1()` does first)
both failed with `Permission denied`, despite confirmed genuine root
(`xsu`'s `id` → `uid=0(root)`) and SELinux confirmed `Permissive`
(non-enforcing) with zero matching `avc: denied` entries for either
write. **Correction, same day, a few hours later: the sysfs channel was NOT
actually closed — the "blocked" result above was an incomplete
investigation, not a hard wall.** This repo already had the fix sitting in
its own codebase: `PerformanceCommandBuilder.kt`'s `chmod 666`/write/
`chmod`-back "unlock" pattern for CPU/GPU nodes had never been tried on
the fan sysfs nodes. Tried by hand: `chmod 666` on `fan_power_state` +
`hwmon0/pwm1`, then the same write sequence as before — **RPM jumped
2961→4780, user independently confirmed audibly.** The vendor's own fan
daemon reasserted the old value on its own within 1-2 minutes (good news
for safety — nothing was left stuck manually overridden). One loose end:
chmod'ing the files back to their original mode afterward failed with
`Operation not permitted` (asymmetric with the unlock direction) — not a
safety issue, not yet explained, possibly related to a mount-namespace
anomaly also spotted this session (`xsu`'s shell resolves a different
`ns/mnt` than expected). **Net effect: a real, PULSE-style editable fan
curve IS achievable on this device after all — via raw sysfs, not AIDL.**
`FanCurve.kt`/`FanTempController.kt` (upstream's pure math/curve models,
no I/O of their own) are portable as-is; only the I/O layer needs to
target this confirmed path instead of upstream's dead Odin-specific one.
Open question before building it for real: whether the vendor daemon's
reassert cadence fights a sustained curve controller the way the Odin's
daemon fought upstream `pulse`'s own reassert loop — not yet tested,
deferred to a later session per the user's request.

**That open question is now answered too, same day (2026-07-30,
later still): the vendor daemon's reassert cadence was precisely
measured** with two small on-device scripts logging at 1s resolution
(manual `adb shell` timing had proven too imprecise) — 9 runs, 17
write→reassert measurements: **range 1-112 seconds, mean ≈50s, median
54s, no fixed period.** Every reassert corrected to exactly duty=76.
Sending `FAN_MODE_CUSTOM` via AIDL first makes no measurable difference
(rules out the "polite mode-switch hand-off" idea). Practical upshot: a
**~10s periodic reassert loop** in the real `FanController.kt` would
preempt the vendor's correction in 16/17 (94%) of observed cases —
dramatically lighter than upstream `pulse`'s own 120ms Odin-reassert
loop, well inside `xsu`'s established safety margins. **No open questions
remain blocking the real curve-controller implementation** — full data
table and design recommendation in `research/aidl-fan-spike/FINDINGS.md`
("Vendor daemon reassert cadence measured" section); raw logs +
measurement scripts in that project's `results/run5/` and `scripts/`.

## Unsupervised session analyzed (2026-07-29): self-kill fix holding, one new kernel-level anomaly found

First real, fully unsupervised pull since the self-kill fix — no one
watching logs live, just normal play. Full raw logs + deterministic
`SUMMARY.md` reorganized into
`research/ab-logger/results/unsupervised_session_2026-07-29/` (index in
that folder's `NOTES.md`, don't duplicate the analysis there). 4 sessions
today: `151428` (~61min, Eden + brief RetroArch), `161522` (instant false
start, <1s), `161552` (32s idle false start), `161822` (~3h20m, Eden, the
real continuation).

**Confirmed still holding, nothing regressed**:
- Self-kill `pkill -f` bug (fixed 2026-07-28) has **not** recurred — 1088
  cap writes via daemon vs. only 25 xsu fallback in the `161822` session,
  274 vs. 12 in `151428`. No `xsu_conn_handler` crash signature anywhere.
- `com.qti.diagservices` ANR-loop still chronic, unchanged, ~every 20s
  continuously — same pre-existing device condition documented earlier in
  this file, not a regression.
- Eden/AutoTDP FPS tracking looked notably healthier than the previously
  documented "stuck ~30fps" thread — `fps=90.0` against `tgt=90` hit
  exactly at one point in `151428`, wide 24-90 range, `fps=-` unreadable
  only 3-8 times total (transitions, not sustained). Likely session/scene
  variance on the same known issue, not a fix — that thread stays open,
  just noted as a data point.

**New finding, not documented anywhere before this pull**: a kernel-level
**haptic-driver + GPU AHB-bus-error storm** — `kgsl kgsl-3d0: CP: AHB bus
error` and `hid_aya_haptic_play`/`aya_haptic_hid_report_work` lines exist
at low background rate all day (~0.83 AHB errors/min baseline), but spike
to **300 AHB errors + 6360 haptic "enter" events in ~150 kernel-seconds**
(kernel uptime 3705.67s→3855.53s) — exactly the gap between the two false-
start sessions (`161522`→`161552`). A `gen7_err_callback: 1159 callbacks
suppressed` line confirms the real error rate was even higher than what
made it into the log. Purely kernel/HAL-level — confirmed invisible to
logcat (`161522`'s and `161552`'s logcat dumps are byte-for-byte identical
per `diff -q`, meaning nothing about this storm ever reached userspace
logs). Plausible but unconfirmed theory: a stuck-stick/controller rumble
storm hammering the haptic HID driver, coincidentally or causally
stressing the GPU command processor at the same moment the two AutoTDP
sessions aborted near-instantly. **Not root-caused, no action taken** —
flagging for next session, since this is exactly the kind of thing
supervised testing would never have caught (both false-start sessions
were too short and too quiet in the app's own log to have drawn attention
without the raw dmesg cross-check).

The two false-start sessions' near-empty `.log` files (124B, 1048B) are
NOT cleanup candidates despite their size — they're now understood to be
the actual evidence window for the finding above, kept as-is.

**Bottom line**: clean, healthy unsupervised session overall — the thing
this whole day's `xsu`/self-kill investigation was ultimately for — with
one genuinely new, unexplained anomaly surfaced specifically because this
was real unsupervised play rather than a supervised smoke test.

## `research/aidl-fan-spike/` built (2026-07-29): probe ready, not yet run on-device

Built the concrete next step flagged in the entry below: a throwaway probe
app, same shape/conventions as `research/aidl-bind-spike/` (copied
`AidlProtocol.kt`'s bind/register/send mechanism, not shared — one-shot
probes, not a library). Builds clean (`./gradlew assembleDebug`, verified
via `grunt`, one unused-parameter warning found and fixed).

Two staged tests: **step 1** sends discrete `com_set_performance_fan:
FAN_MODE_<OFF|MUTE|BALANCE|TURBO>` only (isolates whether the simplest
command does anything); **step 2** switches to CUSTOM and pushes a real
test curve via `com_set_fan_speed_strategy:FAN_MODE_CUSTOM-50,12|65,32|
78,68|85,95|95,100` (the same "ramp harder, sooner" shape discussed
earlier this session as a sensible curve improvement, not a random or
extreme value). Every send is followed by an objective read-back of the
confirmed `pwm-fan` hwmon node (RPM + PWM duty), not just trusting that
the Binder `transact()` didn't throw — same empirical standard as
`aidl-bind-spike`'s cpufreq read-back.

**One real unknown flagged honestly, not glossed over**: the exact enum
string format (`FAN_MODE_CUSTOM` vs. just `CUSTOM`, etc.) sent to
`com_set_performance_fan`/`com_set_fan_speed_strategy` is reconstructed
from `FanSpeedConfig.java`'s decompiled `WhenMappings` block, not
independently confirmed — this app is the first live test of whether that
assumption holds. Full detail, exact commands, and the quick
build/install/logcat test loop: `research/aidl-fan-spike/README.md`.

**Not yet run on-device** — this requires the physical device and this
repo's own hard rule (ELI5 + explicit sign-off before any device-touching
command, every time). ELI5 already given in-session; actual install/run
deferred since the user was using the device concurrently for their own
log pull at the time this was built.

## Feature-parity checklist written (2026-07-29): fan is the last big gap, RGB + one unknown remain smaller

End-of-session stocktake, prompted by the user asking whether fan control
would close feature parity with upstream `pulse` — full checklist now in
`research/pulse-for-aya/README.md`'s "Feature parity vs upstream" section
(kept there, not duplicated here, since that's the doc meant to eventually
inform a summary back to upstream's author). Short version:

**Confirmed working on this hardware**: AutoTDP (real regulation, clean
FIFO sessions), manual tiers/CPU/GPU caps, per-app profiles, live
telemetry HUD/OSD, Quick Settings tile/autostart/themes (inherited
untouched, no device dependency).

**Confirmed remaining gap, in priority order**:
1. **Fan control** — the big one, `FanController.kt` fully stubbed. Now
   has a validated way forward (AIDL `com_set_fan_speed_strategy`, see the
   entry below) instead of being a dead end — next concrete step is a
   small on-device spike, same shape as `aidl-bind-spike`, **not yet
   attempted** (needs explicit device-touch sign-off first, per this
   file's hard rule — ELI5 given in-session, approval not yet requested/
   granted).
2. **RGB** — smaller, same shape of gap (upstream's own mechanism dead
   here, self-gates safely; a real AYANEO-native mechanism exists,
   `aya-gamewindows-teardown/FINDINGS.md` section 5, not yet wired in).
   Deferred behind fan — cosmetic, not safety-relevant.
3. **Unknown, never checked**: `sleep/SleepProfileMonitorService` — not
   read during the original glue assessment.

**Parked idea, not a milestone**: a single codebase supporting both
AYN/Retroid (native `PServerBinder`) and AYANEO (`xsu` glue). Technically
the natural endpoint of this research, but the user does not need it — the
working goal (pulse running on their own devices, with per-app profiles)
is met. Recorded so the idea is not lost; do not propose it as next work.

**Repo housekeeping this session**: checked for stray files/build debris
(none found), confirmed `.gitignore` covers all three community-repo
clones added earlier today, confirmed no uncommitted cruft. Not done:
`STATUS.md` itself has grown to ~750 lines — a proper archive pass
(moving genuinely closed threads to `STATUS_ARCHIVE.md`, per this file's
own stated convention) would be a reasonable future cleanup, but wasn't
attempted this session (most of today's additions are new open threads,
not closed ones ready to archive — rushing that pass risked losing
context, not worth it under today's time pressure).

## Button remapping + gyroscope researched (2026-07-29): extra buttons remappable to anything, ABXY architecturally blocked, gyro not a real Android Sensor

Third research vector this session (after fan control and community
repos): user asked whether extra/back buttons can be remapped to
arbitrary keyboard/gamepad actions, and whether gyro is usable/improvable
(the latter specifically with future Moonlight/Artemis streaming in
mind). Full writeup: `research/aya-gamewindows-teardown/FINDINGS.md`
sections 4 (expanded) and 8 (new).

- **Extra/back buttons (LC/RC paddles, Mode, Home, Roller, MagicTouch,
  volume keys) — confirmed remappable to arbitrary keyboard/macro
  actions**, via an exported, zero-permission-check `ContentProvider`
  (`content://com.ayaneo.gamewindow.provider.sharedprefs/shared_prefs`),
  no root, no AIDL binding needed. Full `pCode`/`funCode` action catalog
  now documented (11 categories — open-app, input/keyevent/tap/swipe,
  nav, volume/DND, media, brightness, screen/power, clipboard/
  screenshot, keyboard macros incl. `input keycombination`,
  connectivity). `appWhite` field confirmed as a real per-foreground-app
  scoping mechanism. **Not yet tried live on-device** — next step is the
  read-first `adb shell content query`/`update` recipe already in
  FINDINGS.md section 4d.
- **ABXY/D-pad/shoulders/Start-Select — confirmed NOT remappable through
  this mechanism, architecturally, not just untested.** The event router
  (`OnKeyInterceptKt.b()`, an `AccessibilityService` callback) only
  forwards a small hardcoded allowlist of AYA-specific extra-button
  keycodes to the remap engine — any other keycode falls through
  unrouted, regardless of what's written via the `ContentProvider`. A
  `CustomKeyItem` bound to an ABXY keycode would silently never fire.
- **Gyro exists (`IAyaDevices.hasGyro`) but is not a standard Android
  `Sensor`** — same out-of-band pattern as the analog sticks (section 7):
  driven over the proprietary controller serial link, zero
  `SensorManager`/`TYPE_GYROSCOPE` usage anywhere in either app's own
  code (the only real `Sensor` registration found is unrelated bundled
  ExoPlayer 360°-video code). No AIDL exposure either. **Bad news for the
  later Moonlight/Artemis streaming idea**: a third-party app cannot read
  raw gyro data via any standard Android API today — same fundamental
  wall as the joystick-curve investigation, likely shares a root cause
  (the opaque native serial-decode boundary), worth keeping in mind if
  that UART reverse-engineering project ever gets picked up, since it
  would probably solve both at once.

## Fan control revisited (2026-07-29): native curve editor is a full AIDL surface, not just discrete mode

Follow-up to the community-repo assessments below: re-traced how AYA's
native Custom fan-curve editor (the "Fan Settings" UI, draggable
temp→duty points + Linear/Step-Based toggle) actually works, since the
previously-known `com_set_performance_fan:<mode>` AIDL command only covers
the discrete OFF/MUTE/BALANCE/TURBO/CUSTOM mode, not the curve. Found in
`ayasettings_decompiled/`'s `FanViewModel.java`/`FanSpeedConfig.java` (full
writeup: `research/ayaspace-teardown/FINDINGS.md`, "Addendum" section):
**two more AIDL commands exist** — `com_set_fan_speed_strategy:<mode>-<temp,duty|...>`
replaces the WHOLE curve in one call, `com_set_fan_speed_is_linear:<mode>`
toggles the interpolation shape. Same no-root `AyaAidlManager` channel
already proven live for `com_set_performance_mode`
(`research/aidl-bind-spike/FINDINGS.md`), but **these two specific
commands have never actually been tested on-device** — same confidence
level as the mode-switch spike, still an assumption until verified.

This changes the fan-control picture from earlier in this project
(`pulse-glue-assessment/FINDINGS.md`, `aya-gamewindows-teardown/FINDINGS.md`
section 6): a full curve-write lever exists over AIDL, no sysfs race with
the vendor daemon needed. Opens a real path for `pulse`'s own
`FanTempController.kt` (PI closed-loop controller — genuinely more
sophisticated than what native AYA offers, which is curve-shape-only, no
closed-loop temp-holding) to be ported and driven through this AIDL
channel instead of raw sysfs. **Next concrete step, not yet done**: a
small follow-up spike (same shape as `aidl-bind-spike`'s original) to
confirm `com_set_fan_speed_strategy`/`_is_linear` actually take effect
live — requires on-device testing, not attempted this session.

## Community repos assessed (2026-07-29): KonaBess-Next-G3Gen3, ClusterTune, PAM Stock OS Optimization Guide — one concrete lead, two ruled out

**Third repo (PAM Stock OS Optimization Guide)** — a written guide, not
code (`research/pam-stock-os-optimization-upstream/`, assessment in
`research/pam-stock-os-optimization-assessment/FINDINGS.md`). Hoped-for
lead was chapter 2 ("Canta and Shizuku") documenting Shizuku as a general
no-root privilege-delegation technique that could replace/supplement
`xsu` — **did not pan out**: the guide only uses Shizuku for its
narrowest common case (authorizing one uninstaller app), no `UserService`
API or general technique shown. "Shizuku as an `xsu` alternative" remains
an untested idea from prior general knowledge, not something validated by
this source. Rest of the guide is mostly generic Android debloat/appops
tuning (background-activity/battery, not CPU/GPU/thermal) — two minor,
low-priority facts worth a `HARDWARE_PROFILE.md` footnote someday
(`peak_refresh_rate`/`min_refresh_rate` 60Hz lock, and a root-gated
SurfaceFlinger VSync toggle with the guide's own OLED-damage warning). Its
"Game Driver" chapter turned out to be just the stock Android
developer-options driver picker, not a driver-swap technique — no light
shed on our still-open `persist.sys.fake.gpu` spoofing question. Target
device ("PAM") never confirmed to be our specific Pocket FIT.

## Community repos assessed (2026-07-29): KonaBess-Next-G3Gen3, ClusterTune — one concrete lead, one ruled out

Two community repos cloned as read-only references
(`research/konabess-g3gen3-upstream/`, `research/clustertune-upstream/`,
gitignored, same pattern as `pulse-upstream/`) and confronted against our
own findings via `scout`. Full writeups:
`research/konabess-g3gen3-assessment/FINDINGS.md`,
`research/clustertune-assessment/FINDINGS.md`.

- **KonaBess-Next-G3Gen3**: reference only, not reusable. It's a
  DTB-patch-and-reboot GPU OPP editor (Magisk root, raw `dd` to the
  boot/vendor_boot/dtbo partition, unlocked bootloader required) — a
  categorically more invasive risk class than `pulse-for-aya`'s
  instantly-reversible sysfs caps, not a smaller version of it. No CPU
  control, no fan/thermal control, no hardcoded frequency/voltage numbers
  for our chip (only generic Qualcomm voltage-corner labels, populated
  from whatever DTB the user's own device supplies). One free, minor
  confirmation: its chip-inference table independently matches
  `ro.board.platform=pineapple` to Snapdragon 8 Gen 3 — corroborates
  `diagnostics/docs/HARDWARE_PROFILE.md`'s existing identification, not
  new information.
- **ClusterTune** (the project upstream `pulse`'s own README credits as
  pioneering its no-root technique): confirmed to have a real `su`-based
  fallback (`RootShellExecutionMethod`) for devices without
  `PServerBinder` — but it's per-call (fresh `su -c` subprocess every
  time, no daemon) and, worse, inlines whole multi-line scripts into a
  single `-c` argument with no file-write/chunking discipline — the same
  anti-pattern behind `xsud`'s crashes on our device. Does NOT validate or
  improve on our FIFO-daemon architecture; ClusterTune has evidently never
  hit this class of bug, most likely because standard Magisk/KernelSU
  `su` is more hardened than AYANEO's bespoke `xsud`, not because of
  anything in their design. **One genuinely actionable idea found**,
  independent of the `su` question: `CpuPolicyDetector.readText()` always
  tries a plain **unprivileged** file read first, only escalating to the
  privileged path on failure — `scaling_max_freq`-class nodes are commonly
  world-readable. Worth a cheap on-device check next session: `ls -l` on
  whatever sysfs nodes `TelemetryReader`/`FpsReader` read — if any are
  world-readable, skipping the FIFO daemon round-trip for those specific
  reads would cut real connection volume for free, same win ClusterTune
  gets today. Independently confirms our `RootExec.kt` "never cache a
  `false` probe" fix — ClusterTune's own resolver has the identical
  never-cache-negative design, arrived at separately.

## Per-app profile testing (2026-07-28, night): 5 findings, full writeup in `pulse-for-aya/README.md`

Real per-app testing session (RetroArch/GBA, Mario Odyssey on Eden,
Minecraft, `retrohrai` frontend), post self-kill-fix. Full detail + code
references: `research/pulse-for-aya/README.md`'s "Per-app profile testing
session" section. Summary:

1. **RESOLVED**: `com.miHoYo.Yuanshen` in the log was Eden (Switch emulator),
   not Genshin Impact — Eden's build uses that `applicationId` (likely
   deliberate camouflage, common post-Yuzu-lawsuit). Confirmed via
   `dumpsys package` (`versionName=1f6734c`, a git-hash, matches Eden's
   Settings > Apps entry exactly; sideloaded, not Play Store). Not a PULSE
   bug — foreground detection was correct throughout. Full trail in
   `pulse-for-aya/README.md`.
2. Custom tier per-app binding shares ONE global slider set (no per-app
   custom frequency curves) — confirmed by reading the code, a real
   limitation not a bug.
3. Power Saving tier (0.55/0.45 factors) is unstable for some RetroArch
   titles (Super Wario Land 4, ~55-60fps) — expected trade-off of the most
   restrictive tier, not a bug.
4. AutoTDP regulates Minecraft correctly, loses the FPS target on Eden
   (stuck ~30fps against tgt=90 despite continuous RAISE) — extends the
   still-open Eden thread below. AAA/Max (static, no AutoTDP) ran the same
   session smoothly, isolating the problem to the AutoTDP loop specifically,
   not the hardware. The regulation log line doesn't print which package
   it's regulating — cheap logging fix identified, not yet done.
5. AAA/Max sustained max CPU clock (~70°C, fan never ramped) — expected:
   `performance` governor pins the frequency ceiling, not actual power draw,
   which still tracks real workload intensity; PULSE's fan control is a
   confirmed no-op on this device regardless of tier.

Raw evidence: `research/ab-logger/results/per_app_profile_test/`.

## VERIFIED ON-DEVICE (2026-07-28, late evening): self-kill fix confirmed — daemon fully functional again

First real post-fix test (build `20:18:16`, Minecraft, ~4.5min session
20:19:49→20:24:03): `analyze-pulse-logs.py`'s summary —

- AutoTDP engaged: YES. Cap writes: **65 via daemon, 3 via xsu fallback**
  (previously 0 via daemon most of tonight). Telemetry reads: **60 via
  daemon, 0 fallback** — zero raw `xsu` reads the entire session.
  Regulation: 9 TRIM, 2 RAISE, 12 HOLD — real decisions, not just idle.
- `cap_poll` confirms real sysfs changes landed (p0/p2/p5/p7_max,
  gpu_max_pwrlevel all changed within-session — ground truth matches).
- `dmesg`: 0 crash-keyword hits during the session.
- `logcat`: 14 crash-keyword hits, but all pre-date session start by 20+
  minutes (timestamps `01-02` and `19:58:03` vs session start `20:19:49`,
  unrelated processes `nvkeeper`/`qcrosvm`/`init`) — the filtered logcat
  replays matching lines already in the ring buffer before attaching, not
  new events; confirmed noise, not a crash in this session.
- `clean session end: NO` — user manually exited the game/app rather than
  using an in-app stop, not a crash (confirmed directly).

**This is the first fully clean FIFO-daemon session since the investigation
began.** The self-kill `pkill` bug (below) was very likely also a
contributor to earlier "crash" reports beyond just tonight's silent-log
symptom — if the daemon intermittently failed to (re)start during real
play (e.g. after any app-process restart mid-session, which happens
naturally under memory pressure/game transitions), every write/read for the
rest of that session would fall back to the old high-frequency raw-`xsu`
pattern, which is exactly the pattern independently identified as the
dominant crash contributor earlier in this investigation. Not proven
retroactively, but a plausible unifying explanation worth keeping in mind.

**Next step**: longer, real gameplay sessions (not just a short manual
smoke test) to see whether the original crash still recurs now that the
daemon reliably starts and stays on the FIFO path.

## FIXED (2026-07-28, late evening): PulseDaemon.start() was self-killing on every launch — root cause of tonight's "empty /sdcard/apl_pulse_logs/" mystery

Found and fixed the actual reason the daemon never started tonight (separate
from the `com.qti.diagservices` phantom-killer thread below, which turned out
to be real but NOT the cause of this specific symptom). Full trail:

1. Added launch-outcome logging to `start()` (was completely silent before) —
   showed `FAILED: no exception, but unexpected stdout=null` on every attempt,
   100% reproducible, both before and after an app-process restart (ruling out
   flakiness/system-stress as the cause).
2. Added an independent local-file confirmation (daemon script overwrites its
   own private-`filesDir` log as its first action) — also failed every time,
   ruling out "xsud just drops the launch command's stdout" (a real,
   documented risk, `xsu-capability-probe/FINDINGS.md`) as the explanation —
   the script itself never ran, not just an output-capture artifact.
3. Manually reproduced the exact launch command via interactive `xsu -c` —
   got `Terminated` (exit 143 = SIGTERM), instantly, 100% reproducible.
4. **Root cause**: `start()`'s orphan-cleanup used `pkill -f 'pulse_daemon.sh'`
   — `pkill -f` matches every process's FULL command line, and the ENTIRE
   launch command (including this pkill call itself) is passed as ONE
   `xsu -c "<string>"` argument, which necessarily contains the literal text
   "pulse_daemon.sh" (to invoke the script by path). The invoking shell's own
   cmdline matched the pattern — `pkill -f` was killing its own parent (the
   shell currently running this exact command) via SIGTERM, before ever
   reaching `mkdir`/the `sh ... &` launch/the final echo marker. This pkill
   line was added in `c22f2e6` (the "correctness fixes" commit) — round1-5
   (this morning, working) predate it entirely, matching the regression
   window exactly.

**Fixed**: `pgrep -f 'pulse_daemon.sh'` + explicit exclusion of the current
shell's own PID (`$$`) before killing, instead of self-matching `pkill -f`.
Also dropped the equivalent orphaned-logcat `pkill` (same trap; an orphaned
logcat filter is harmless to leave running, not worth the extra ~170 chars
this close to the ~800-char `xsu -c` safe margin).

Verified: `compileDebugKotlin testDebugUnitTest lintDebug` clean, fresh APK
built (`BUILD_TIMESTAMP` 2026-07-28 20:18:16). **Not yet re-tested on-device
after this specific fix** — next session's first job: confirm `start()` now
logs `succeeded` and `/sdcard/apl_pulse_logs/` actually gets files.

Side lessons worth keeping in mind for future shell-command work in this repo:
- **`pkill -f`/`grep -f`-style full-command-line matching is dangerous inside
  an `xsu -c "<the whole command>"` invocation** — the pattern almost always
  also matches the invoking shell's own cmdline. Prefer `pgrep` + explicit
  `$$`-exclusion whenever the kill target's name might appear in the launch
  command's own text.
- This device's `grep`/`pgrep` (toybox, not GNU) doesn't support `\|` as
  alternation in a pattern — confirmed earlier tonight, cost real time before
  being diagnosed (see the Phantom Process Killer entry below for the trail).

## CONFIRMED (2026-07-28, evening): `com.qti.diagservices` ANR-loop drives Android's Phantom Process Killer to reap our `xsu` children, ~every 20s

Testing the FIFO-migration build tonight: toggled AutoTune Games on, Minecraft
profile set, game launched — `/sdcard/apl_pulse_logs/` stayed completely empty,
no root `pulse_daemon.sh` process ever showed in `ps -A`. Diagnostic trail (all
read-only, via the user's own interactive `adb shell`):

- `xsu -c "id"` from the interactive shell works fine (`uid=0(root)`) — root
  itself isn't broken.
- `logcat -d | grep -i "a\|b\|c"` (OR-style pattern) returned nothing for
  everything, which briefly looked like "no logcat at all" — turned out to be
  a **grep gotcha, not a real absence**: this device's `grep` doesn't support
  `\|` as alternation (toybox/busybox, not GNU). Single-term / `grep -E`
  searches worked fine and had plenty of output (`logcat -d -b all` = 21045
  lines). Worth remembering next session — don't trust an empty combined
  `\|` grep on this device again.
- Real `logcat -d` (correct syntax) showed `com.kei.pulse` alive and active
  (up to 46% CPU), and several `avc: denied` lines for the app's own process
  trying to reach `xsu`/the `xsud` socket — but every one has `permissive=1`,
  meaning SELinux logged but did NOT enforce the denial. Not the blocker.
- The real signal: `ActivityManager: Process PhantomProcessRecord
  {...:4361:com.kei.pulse/u0a183} died` — Android's Phantom Process Killer
  (Android 12+, reaps untracked child processes spawned via raw
  `ProcessBuilder`/`Runtime.exec`, i.e. exactly how `RootExec`/`xsu` spawns
  the daemon) killed a child of PULSE's own process, in the same window the
  log directory stayed empty.
- **This isn't new, just never flagged**: the exact same signature —
  `PhantomProcessRecord {...xsu/u0a183} died`, six times, every ~20-21s on
  the clock (10:34:54 → 10:36:35) — is already sitting in yesterday's
  `research/ab-logger/results/minecraft_crash_investigation/round4_2026-07-27_1035_schedutil_logcat_capture/minecraft_crash_20260727_103555.log`,
  each one immediately following an `ANR in com.qti.diagservices` (a vendor
  Qualcomm diagnostics service, ANRing on a near-metronomic ~20s cycle,
  clearly a chronic device condition unrelated to anything PULSE does).

**Why this matters**: everything chased today (command length, call
frequency, stale scripts) assumed the crash trigger was something *inside*
PULSE's own root-shell usage. This raises a real alternative: a chronic,
device-wide `com.qti.diagservices` ANR loop may be triggering Android's own
process-management to reap "phantom" (untracked) child processes system-wide
— including ours — as collateral, regardless of how carefully our own xsu
calls are shaped. Would explain why fixes kept "working" briefly then the
crash recurred anyway: the actual trigger was never fully in our control to
begin with.

**Follow-up capture confirmed it** — full ~10.5min `logcat -d` pull
(`research/ab-logger/results/phantom_process_killer_investigation/
round1_2026-07-28_1811_diagservices_anr_correlation/`, `NOTES.md` has the
full writeup):

- `com.qti.diagservices` (a persistent, freezer-exempt vendor service) ANRs,
  gets killed, and auto-restarts in an infinite loop, **exactly every ~20s,
  continuously for the whole session** (32 cycles, 18:11:12 → 18:21:35) —
  fully independent of PULSE/AutoTDP/Minecraft; a pre-existing, chronic
  device condition. Same signature already sat unnoticed in yesterday's
  round4 crash logs (`minecraft_crash_investigation/round4.../
  minecraft_crash_20260727_103555.log`, 6 occurrences, same cadence).
- Every single Phantom Process Killer kill of a `com.kei.pulse`/`xsu` child
  (21 kills total in this window; some sweeps instead/also caught
  `com.ayaneo.gamewindow`'s periodic `top` spawns — confirms this sweep
  isn't PULSE-specific, it hits every app's untracked children) lines up
  **within <30ms** of one of `com.qti.diagservices`'s ANR timestamps —
  checked by hand across the whole set, not a loose pattern-match. Same
  ActivityManager cleanup pass triggers both.

**Still open**: (a) whether the specific PID reaped is ever the long-lived
`pulse_daemon.sh` shell itself, vs. only the short-lived `xsu` spawner
(`phantom.log`'s process names show `xsu`/`com.kei.pulse`, never
`sh`/`pulse_daemon.sh` by name — inconclusive either way), (b) whether
disabling `com.qti.diagservices` (`pm disable-user`, reversible) stops the
~20s sweep entirely — the cleanest test of whether this is really *the*
trigger behind the whole day's (and yesterday's) crash investigation, or
just an additional contributing factor.

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

**Update (2026-07-28, night)**: confirmed again, new data point — AAA/Max
(static tier, no AutoTDP) ran the same Eden/Mario session smoothly, isolating
the problem to the AutoTDP loop itself, not the SoC/hardware. See the
"Per-app profile testing" entry above for the log evidence and the identified
next step (log which package AutoTDP is regulating, currently invisible in
the `tgt=` telemetry lines).

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
