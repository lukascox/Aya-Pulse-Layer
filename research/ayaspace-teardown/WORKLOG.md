# WORKLOG — AYA Settings teardown, session 1 (2026-07-24)

Standalone record of *how* this teardown was done and *why*, kept alongside
`FINDINGS.md` so someone picking this up later — possibly not the person who
ran it — has the full trail: not just what we learned, but how we got the
artifact and what to reuse or redo. See `README.md` in this directory for the
original plan this session followed.

## Why this exists

Two concrete open questions about `com.ayaneo.settings` (AYA Settings) had
direct architectural impact on `apl/app/`'s eventual `XsuShell.kt` and
`AutoTdpController.kt`, and neither had been checked against real decompiled
source before now (see `README.md` for the full rationale). This is static
analysis only — no code from AYA Settings runs anywhere, we just read it.

## Methodology

1. Confirm the app is actually present on-device and get its install path
   (`pm list packages` / `pm path`) — done manually, on-device, by the human
   operator, not by the assistant. Rationale: the assistant has no reason to
   need direct `adb`/device access for this task, everything downstream is
   static file analysis; keeping device commands manual also means the
   operator always knows exactly what touched their device.
2. Pull the `base.apk` off-device with plain `adb pull` (no root channel
   needed this time — `pm path` + `pull` worked directly, unlike some
   priv-app APKs on other AYA devices where `pull` is denied and the
   `xsu -c 'cat ... > /sdcard/...'` fallback from `README.md` is needed).
3. Decompile with `jadx -d ayasettings_decompiled ayasettings.apk` (jadx
   1.5.6, confirmed already installed). jadx exits with a summary that reads
   as `ERROR` on the terminal if *any* file in the tree fails to fully
   decompile — this is normal for large apps and not a sign the pull or
   decompile failed. Confirmed here: 107 of 6986 `.java` files (~1.5%) carry
   a `JADX ERROR` marker, all in third-party library code (Kotlin
   coroutines, AndroidX, Lottie) — none in `com.ayaneo.*`, which decompiled
   cleanly and un-obfuscated (no ProGuard/R8 name mangling on AYA's own
   packages, only on some bundled libraries).
4. Orient via `AndroidManifest.xml` first (component map, permissions,
   `sharedUserId`) before touching Java, per `README.md`'s process — this is
   what surfaced the `android.uid.system` fact immediately, which reframed
   the whole rest of the analysis (see `FINDINGS.md`, "Why this app doesn't
   need `xsu` at all").
5. Grep-driven exploration, not linear reading: searched for the specific
   terms `README.md` suggested (`SwitchPerformanceModeFragment`, `CpuFragment`,
   `PServerBinder`-adjacent terms, `xsud`, `RootShell`, sysfs node names),
   plus terms that came up organically while reading (`AidlConstants`,
   `AyaAidlManager`, `ModeConfiguration`) — each hit read in full, followed
   into whatever it called, until reaching either a shell command string, a
   Binder transaction, or a dead end (a separate, not-yet-decompiled APK).
6. Curated only the specific `.java` files that carry the actual evidence
   into `evidence/` (full paths noted per-claim in `FINDINGS.md`) — the full
   `ayasettings_decompiled/` tree and `base.apk` stay local, excluded by
   `.gitignore`, never committed.

## Extraction path (exact commands run, on-device, by the operator)

```
cox in apl/research/ayaspace-teardown on  main ❯ adb shell pm list packages | grep -i ayaneo
package:com.ayaneo.gamewindow
package:com.ayaneo.gamelauncher
package:com.ayaneo.home
package:com.ayaneo.settings
package:com.ayaneo.ayakeytester
cox in apl/research/ayaspace-teardown on  main ❯ adb shell pm path com.ayaneo.settings
package:/data/app/~~oFhfuZ1y9JrxP0udAel8-A==/com.ayaneo.settings-kjhmIV1zu1twACbkmCnNDQ==/base.apk
cox in apl/research/ayaspace-teardown on  main ❯ adb pull /data/app/~~oFhfuZ1y9JrxP0udAel8-A==/com.ayaneo.settings-kjhmIV1zu1twACbkmCnNDQ==/base.apk
```

followed by, still on the operator's machine:

```
jadx -d ayasettings_decompiled base.apk
```

Both `base.apk` and `ayasettings_decompiled/` land in
`research/ayaspace-teardown/` and are excluded from git by the local
`.gitignore` in this directory (`*.apk`, `*_decompiled/` — plus the repo
root `.gitignore` excludes `*.apk` globally as a second layer). Only the
curated files in `evidence/` and this prose in `FINDINGS.md`/`WORKLOG.md`
are committed.

**Sibling packages worth noting for a future pass**, from the `pm list
packages` output above — all AYA-vendor apps present on this same device,
any of which could be a future teardown target if a question points at
them:
- `com.ayaneo.gamewindow` — confirmed in this pass as the real target for
  performance-mode changes (see `FINDINGS.md`, "Open question / next step").
  This is the most likely next teardown.
- `com.ayaneo.gamelauncher` — referenced in AYA Settings' own code
  (`LauncherApp.GAME_LAUNCHER_PKG_NAME`) as a launchable component; role
  otherwise unexplored.
- `com.ayaneo.home` — referenced similarly (`LauncherApp.HOME_PKG_NAME`);
  unexplored.
- `com.ayaneo.ayakeytester` — not referenced anywhere seen in this pass;
  unexplored, name suggests a controller/button diagnostic tool.

## What this pass produced

- `FINDINGS.md` — the actual technical findings, with concrete answers to
  both open questions from `README.md`.
- `evidence/` — curated `.java` excerpts backing every claim in
  `FINDINGS.md`, organized by topic (`aidl/`, `performance/`, `shell/`).
- This file.

## Potential benefits / why this matters for `apl` going forward

- **Answers a question that was open since the first `xsu_handoff` document**
  (whether a Binder/AIDL alternative to `xsu -c` exists) — with a concrete,
  partial answer: yes, but not where originally guessed. This closes the
  "have we just not looked?" uncertainty; the remaining uncertainty (is
  `gamewindow`'s service reachable from a non-system app?) is a much
  narrower, well-defined follow-up instead of an open-ended one.
- **Gives `apl`'s profile-mimicking feature a fan-curve lever it didn't know
  it was missing** — confirmed from AYA's own data model
  (`ModeConfiguration.fanMode`), not inferred or guessed.
- **De-risks the current `xsu -c` approach** by showing *why* AYA Settings
  doesn't need it (system UID) rather than leaving it as an unexplained
  difference — useful context for anyone later asking "why doesn't `apl` just
  do what AYA Settings does".
- **Sets up a well-scoped next session** (`com.ayaneo.gamewindow` teardown)
  instead of leaving "check for a Binder interface" as a vague todo — the
  next session has an exact package name, exact service class name, and two
  exact questions to answer.
