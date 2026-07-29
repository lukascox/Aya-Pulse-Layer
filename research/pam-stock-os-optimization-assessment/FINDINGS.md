# FINDINGS — PAM Stock OS Optimization Guide (`BruhMeh/PAM-Stock-OS-Optimization-Guide`), static read

Target: `research/pam-stock-os-optimization-upstream/` (fresh clone, gitignored,
read-only). All paths below are relative to that directory. Read in full:
`README.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, and every chapter under
`Guide/` (`01-Introduction.md` through `06-Appendices.md`, ~1,830 lines
total). This is a written guide, not source code — evidence pointers below
are section headings / line numbers into the Markdown chapters, not
function references.

## TL;DR

**Shizuku is a dead end for our use case as documented here** — chapter 2
only uses it for its single most common purpose (granting one specific
uninstaller app, Canta, permission to remove system packages), never as a
general technique for handing our own app privileged sysfs/AIDL access.
Chapter 4's ADB toolkit *is* genuinely useful reference material — about a
dozen concrete `settings put`/`cmd`/`appops`/`device_config` commands, none
of which touch CPU/GPU/thermal directly (they're background-activity/
battery/compile-cache tuning) but two (`peak_refresh_rate`/
`min_refresh_rate`, and a root-gated SurfaceFlinger VSync toggle) are
display-performance-adjacent and worth a `HARDWARE_PROFILE.md` footnote.
Chapter 5 ("Game Driver") is just the standard Android Developer Options
"Game Driver Preferences" toggle — no driver-swapping technique, no
insight into the `persist.sys.fake.gpu` spoofing question. Overall verdict:
(c) neither a root-alternative technique nor a major reference haul —
mostly generic Android power-user debloat advice, with a handful of small,
concrete facts worth a low-priority note. Full detail below.

## 1. Chapter `02-Canta-and-Shizuku` — concrete Shizuku technique for privileged access?

**No — Shizuku appears only in its narrowest, most common role: granting
one specific companion app (Canta) permission to call Android's package-
management APIs to uninstall/disable system packages. There is no
discussion of Shizuku as a general mechanism our own app could use to gain
privileged sysfs write access or to authorize an AIDL client.**

- The entire chapter (`Guide/02-Canta-and-Shizuku.md`) is structured as:
  prerequisites (Developer Options, USB/Wireless Debugging, Shizuku +
  Canta installed, lines 16-24) → "Start the Shizuku Service" (pair via
  Wireless Debugging, confirm status "Running," lines 28-42) → "Grant
  Permission to Canta" (open Canta, grant via Shizuku prompt, lines 46-56)
  → package selection screenshots and category lists (System Applications,
  Google Components, Android Components, Android Themes, Overlay Packages,
  lines 72-140) → reboot/verification checklist. That's the complete
  content — no mention of `pm grant`, `WRITE_SECURE_SETTINGS`, Shizuku's
  `UserService` API (the mechanism that lets a Shizuku-authorized app run
  its own code with shell/system privileges), or any technique beyond
  "install Shizuku, pair once, let Canta borrow its permission."
- The glossary confirms the guide's own framing is generic and shallow:
  "**Canta** — Application used together with Shizuku to remove or disable
  system packages without root access" and "**Shizuku** — Application that
  allows supported apps to access privileged Android APIs without
  requiring root access" (`Guide/06-Appendices.md:122-136`). Both
  definitions are accurate as far as they go, but neither hints at
  Shizuku's actual underlying mechanism (it runs a privileged process via
  `adb shell` (Wireless Debugging pairing) or root, then exposes a
  `Manager`/`UserService` Binder API other apps can bind to after the user
  grants permission) — the guide treats it as a black box you point Canta
  at, not a platform capability to build on.
- **Canta confirmed**: exactly what the task brief assumed — a system-app
  uninstaller/debloat tool, nothing more, not relevant to us beyond
  confirming the assumption.

**Could Shizuku still plausibly work as an `xsu`/`AyaAidlService`
alternative, even though this guide doesn't document it?** Plausibly yes,
in principle — Shizuku's real capability (any app the user explicitly
authorizes can run commands with the privilege of the paired ADB/root
session, no continuous physical connection needed after pairing) is a
legitimate root/no-root-hybrid transport that a determined implementer
could point at sysfs writes or as an authorization layer for our own AIDL
client. But that is **not something this guide demonstrates or evidences
in any way** — it would be new engineering investigation on our part, not
a technique lifted from this source. Treat "Shizuku as an `xsu`
alternative" as an idea validated only by prior general Android knowledge,
not by anything found in this repo.

## 2. Chapter `04-01-Advanced-ADB-Toolkit` — new CPU/GPU/thermal/performance commands?

**No CPU/GPU/thermal commands at all — the chapter's "Performance
Optimizations" and "System Resource Management" sections are about
background-activity/scheduling/compile-cache tuning, a different axis
entirely from what `HARDWARE_PROFILE.md`/`STATUS.md` document (direct
`cpufreq`/`kgsl` sysfs caps).** Full extraction of every concrete command
in the chapter, since the task asked not to hand-wave this:

- `adb shell cmd appops set com.google.android.gms RUN_IN_BACKGROUND ignore`
  + `adb shell am set-standby-bucket com.google.android.gms restricted`
  — restricts Play Services background execution (`Guide/04-01-Advanced-ADB-Toolkit.md:36-39`).
- Same two-command pattern repeated for Play Store (`com.android.vending`,
  lines 54-57), Gboard (`com.google.android.inputmethod.latin`, lines
  67-70), and **the AYANEO OTA updater itself**,
  `com.ayaneo.update` (lines 82-85) — this last one is the one AYANEO-
  specific package name in the whole guide, confirming the guide targets
  some AYANEO device but not narrowing which one.
- `adb shell am set-inactive <frontend-package> false` — keeps a
  frontend launcher (Daijisho, either the GitHub or Play Store package
  variant) from being marked inactive/suspended (lines 97-104).
- `adb shell cmd package compile -m speed -a` (AOT-compile all installed
  apps) and `-f <package_name>` (compile one) (lines 128-152) — dexopt/AOT
  tuning, not CPU frequency.
- `adb shell cmd package bg-dexopt-job` — force-run background dexopt now
  instead of waiting for the scheduled maintenance window (lines 162-164).
- `adb shell settings put secure send_action_app_error 0` (line 175),
  `adb shell settings put system send_security_reports 0` (line 187),
  `adb shell settings put global master_sync_allow_background_in_battery_saver 0`
  (line 199) — disable error/security reporting and sync-during-battery-
  saver.
- `adb shell settings put global job_scheduler_quota_controller_max_job_count_bg 3`
  (line 221) — caps concurrent background jobs.
- `adb shell device_config put activity_manager_native_boot use_freezer true`
  (line 235) — enables Android's cached-app freezer (Android 12+ feature;
  worth flagging since our own project has separately investigated
  Android's Phantom Process Killer reaping orphaned `xsu`/`xsud` children,
  per `STATUS.md`'s phantom-process-killer thread — the freezer is a
  *related* but distinct mechanism (freezes cached processes' cgroups
  rather than killing them); this command is not a fix or workaround for
  our phantom-process-killer problem, just worth knowing it exists as a
  neighboring OS knob).
- `adb shell setprop sys.use_fifo_ui 1` (line 249) — raises System UI's
  scheduling priority; a real Android system property, unrelated to our
  own project's unrelated use of the word "FIFO" for the `xsud`
  daemon-connection architecture (`STATUS.md`) — flagging only to avoid
  future confusion, not because there's any actual connection.
- `adb shell settings put global captive_portal_mode 2` (line 263) —
  disables captive-portal connectivity checks.
- `adb shell pm trim-caches 999G` (line 282) — reclaims package cache
  storage.
- `adb shell settings put global alarm_manager_constants "allow_while_idle_short_time=10000,allow_while_idle_long_time=20000"`
  (line 296) — loosens idle-alarm timing to reduce wakeups.
- Three `appops` toggles on Play Services for activity recognition, usage
  stats, and device-identifier access (lines 310-335), all repeated
  verbatim (plus location/Bluetooth/Wi-Fi-scan/wake-lock/auto-start/
  overlay-window `appops`, and two `pm disable-user` calls for
  `com.google.android.feedback`/`com.google.android.apps.turbo`, and two
  `settings put global sensor_privacy 1` / `wifi_scan_always_enabled 0`)
  in the follow-on chapter `04-02-Google-Play-Service-Hardening.md:20-151`
  — this second chapter is entirely Play-Services-specific `appops`/
  `pm disable-user` hardening, same non-CPU/GPU/thermal category.

**None of this overlaps with or extends `HARDWARE_PROFILE.md`'s existing
`cpufreq`/`kgsl` sysfs facts or `STATUS.md`'s `xsu`/AIDL threads** — it's a
disjoint category (Android app-lifecycle/scheduling/battery tuning via
public `settings`/`cmd`/`appops` surfaces, no root needed for any of it,
no sysfs touched at all). Genuinely new information relative to what's
already documented in this repo, but not in the category the task was
hoping for.

## 3. Chapter `05-Game-Driver` — GPU driver swapping, and any light on `persist.sys.fake.gpu`?

**No — this chapter is shallower than the task brief assumed. It documents
using the standard, built-in Android "Game Driver Preferences" screen
(Developer Options → Game Driver Preferences) to let already-installed
apps pick from graphics drivers the OS already offers — it does not cover
installing, downloading, or updating a driver independent of the ROM, and
says nothing about driver internals, ANGLE, or Vulkan version specifics.**

- The entire chapter is four short sections: "Open Game Driver
  Preferences" (just the menu path, `Guide/05-Game-Driver.md:20-27`),
  "Configure Each Emulator" ("Apply the graphics driver recommended in the
  original guide" — no drivers named, no versions, no source, lines
  31-39), "Validate the Configuration" (a generic stability-testing
  checklist, lines 43-58), and "Compatibility Notes" (a bare list of
  variables that might matter: Android version, Stock OS version, GPU
  firmware, driver version, Vulkan-vs-OpenGL backend, lines 69-78) — no
  content beyond that list.
- The chapter opens with an explicit peer-review caveat not present
  anywhere else in the guide: **"From this point on, the guide was not yet
  peer validated, follow with caution!"** (`Guide/05-Game-Driver.md:1-3`)
  — the guide's own authors flag this chapter as the least-vetted part of
  the whole document.
- The referenced "Game Driver Preferences" screen is Android's own
  standard `GameManager`/`updatable graphics driver` developer-options
  feature (present on any device whose OEM ships it, unrelated to any
  AYANEO-specific mechanism) — this guide neither explains how that
  screen is populated nor names a driver package/source; it just says to
  open it and pick something, deferring to "the original guide" (a
  reference to the un-cloned original PDF/source this Community Edition
  reformats, not present in this clone).
- **Zero mention of `persist.sys.fake.gpu`, ANGLE, Adreno driver versions,
  or ROM-independent driver installation anywhere in the chapter or the
  rest of the guide** (grepped case-insensitively across all of `Guide/`
  for `angle|adreno|vulkan|opengl|fake.gpu|game.driver.*update|libvulkan`
  — the only hits are this chapter's own generic "Vulkan or OpenGL"
  backend mention, `05-Game-Driver.md:77`, and an identical line in
  `06-Appendices.md:187`). This chapter sheds **no light** on our own
  unresolved spoofed-GPU-identifier question — it's out of scope for what
  this guide actually covers, contrary to what the chapter title might
  suggest.

## 4. Chapters `03-System-Settings` / `04-03-Display-and-User-Experience` — thermal/performance/battery beyond AYA's native settings?

**Almost entirely generic Android debloat/UI-preference tweaks already
superseded by AYA's native fan-curve/performance-mode system or
irrelevant to thermal/performance. Two exceptions worth noting, both
display-latency related rather than thermal/power-management, and one of
them requires root (a dependency `pulse-for-aya` deliberately avoids).**

- `03-System-Settings.md` is entirely non-thermal: disabling Contacts
  sync, Play Protect, Location/Wi-Fi/Bluetooth scanning, account auto-
  sync, diagnostic logging, per-app background battery restriction, and
  Developer Options animation scales (lines 13-176) — background-activity
  and battery-adjacent, but nothing that touches CPU governors, GPU power
  levels, or fan curves. The one line that comes closest, "Keep Hardware
  Overlays Enabled" (lines 163-175), is a UI-composition recommendation
  ("no measurable benefit" from disabling it, "may increase GPU workload"
  per the guide's own testing note) — not a lever, just advice not to
  touch a setting.
- `04-03-Display-and-User-Experience.md` repeats the animation-scale and
  hardware-overlay advice (lines 9-40, duplicating `03-System-Settings.md`
  verbatim) and adds two genuinely new items:
  1. **FPS/refresh-rate lock**: `adb shell settings put global
     peak_refresh_rate 60.0` + `adb shell settings put global
     min_refresh_rate 60.0` (lines 46-51) — pins both peak and minimum
     refresh rate to 60Hz so VSync doesn't dip below it to save power.
     No-root, `settings put global`, plausible small reference addition
     for `HARDWARE_PROFILE.md` if our panel's actual refresh-rate
     capabilities are ever documented there — currently not present in
     `HARDWARE_PROFILE.md` (grepped, no match).
  2. **SurfaceFlinger VSync disable**: `adb shell su -c "service call
     SurfaceFlinger 1008 i32 1"` (enable) / `... i32 0` (disable) (lines
     69-79) — **requires root** (`su -c`, explicitly stated at line 73:
     "You need to have root privileges to execute this command"), reduces
     input latency at the cost of possible screen tearing and higher
     battery use, and the guide itself warns **"DO NOT DO THIS ON OLED
     DEVICES OR SCREENS WITH MORE THAN 60HZ PANEL SPEEDS!"** (line 58,
     all-caps in source). Root-gated and display-only (not CPU/GPU/fan),
     so out of `pulse-for-aya`'s current scope, but worth flagging as a
     candidate if display-latency tuning is ever added to scope — it
     would need its own risk assessment before going anywhere near
     `pulse-for-aya` given the guide's own screen-damage-adjacent warning
     and root dependency.
- **Nothing in either chapter duplicates or extends AYA's native
  fan-curve/performance-mode system** — no fan, no thermal-trip-point, no
  CPU/GPU-frequency content appears in either chapter at all (grepped
  case-insensitively for `fan|thermal|cooling|cpufreq|kgsl|governor`
  across both files — zero hits in either).

## 5. Is this AYANEO/SD8Gen3-specific, or generic Android/Qualcomm-handheld advice?

**Overwhelmingly generic Android power-user advice; the AYANEO-specific
surface is thin and the target device is not confirmed to be our Pocket
FIT.**

- The only AYANEO-specific artifact anywhere in the guide is the
  `com.ayaneo.update` package name (`Guide/04-01-Advanced-ADB-Toolkit.md:83`)
  used for one `appops`/standby-bucket pair — everything else (Play
  Services/Play Store/Gboard `appops`, AOT compilation, job-scheduler
  quotas, freezer, animation scales, refresh-rate lock, SurfaceFlinger
  VSync, Shizuku/Canta debloating) is stock Android functionality that
  applies to essentially any modern Android device, handheld or not.
- The guide's own device label, "PAM," is never expanded or defined
  anywhere in the clone (`README.md`, `CHANGELOG.md`, `CONTRIBUTING.md`,
  all `Guide/*.md` — grepped, no expansion found) — it reads as an
  internal community shorthand for some specific AYANEO Pocket-family
  model, but this assessment cannot confirm which one, and cannot confirm
  it's the Pocket FIT (`ro.vendor.qti.soc_model=SG8350P`) our own hardware
  profile documents. No SoC name, no Snapdragon/MediaTek mention, no
  `getprop` value, no chip codename appears anywhere in the guide.
- A genuinely knowledgeable Android power-user (someone who already knows
  `appops`, `am set-standby-bucket`, `cmd package compile`, Shizuku, and
  Developer Options) would already know the overwhelming majority of this
  guide's content — its value is curation and sequencing for a specific
  community's device, not novel technique. The `com.ayaneo.update` package
  name and the "PAM" community shorthand are the only things a generic
  Android guide wouldn't already contain.

## 6. Bottom-line verdict

**(c), with a small side order of (b) — mostly generic reference material,
no new technique worth prototyping.** Ranked by actual usefulness to this
project:

1. **Shizuku is not a validated lead from this source (see Q1).** The
   guide documents Shizuku's single narrowest use case (authorizing one
   uninstaller app) and gives zero evidence for, or even discussion of,
   using it as a general privilege-delegation mechanism for our own app's
   sysfs writes or as an authorization layer for `AyaAidlService`. Anyone
   pursuing "Shizuku as an `xsu` alternative" for `pulse-for-aya` would be
   doing new investigation, not applying anything demonstrated here — be
   honest that this task's most-hoped-for outcome did not pan out.
2. **A modest, disjoint set of reference facts worth a low-priority
   `HARDWARE_PROFILE.md` footnote, not urgent enough for a dedicated
   edit**: the `peak_refresh_rate`/`min_refresh_rate` lock (Q4) and the
   root-gated SurfaceFlinger VSync toggle (Q4) are the only two commands
   in the whole guide that touch anything resembling performance/latency
   tuning rather than background-activity/battery management; everything
   else in Q2's extraction (appops/standby-bucket/dexopt/job-scheduler/
   freezer/captive-portal commands) is a legitimate but off-axis category
   (background-activity tuning, not CPU/GPU/thermal) that doesn't belong
   in `HARDWARE_PROFILE.md` as currently scoped.
3. **Chapter 5 ("Game Driver") is a dead end for the `persist.sys.fake.gpu`
   question (Q3)** — it documents using a stock Android developer-options
   screen, not driver installation/swapping, and the guide's own authors
   flag the chapter as not-yet-peer-validated.
4. **Device-scope caveat applies to everything above (Q5)**: this guide's
   "PAM" target device is never confirmed to be our Pocket FIT, so even
   the handful of concrete facts above carry that caveat if ever acted on.

No follow-up action recommended for `pulse-for-aya` itself. If
`diagnostics/docs/HARDWARE_PROFILE.md` is touched for an unrelated reason,
the `peak_refresh_rate`/`min_refresh_rate` pair (Q4) is a reasonable
one-line addition; nothing else here rises to that bar.
