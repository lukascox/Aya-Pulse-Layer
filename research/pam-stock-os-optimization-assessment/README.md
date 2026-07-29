# PAM Stock OS Optimization Guide assessment — static read, reference clone

Read-only review of a fresh `git clone` of
`https://github.com/BruhMeh/PAM-Stock-OS-Optimization-Guide.git`, kept
locally at `research/pam-stock-os-optimization-upstream/` (gitignored,
read-only reference clone — same pattern as `research/pulse-upstream/` and
the other `*-upstream/` clones in this repo: never modify it, consult it for
reading only).

Unlike the other assessed community repos this session
(`research/konabess-g3gen3-assessment/`,
`research/clustertune-assessment/`), this target is **not an Android
codebase** — it's a written guide (Markdown chapters under `Guide/`, plus a
PDF export) documenting manual optimization steps for "PAM Stock OS," a
community term for the stock AYANEO Android handheld firmware. It targets
some AYANEO Pocket-family device (confirmed AYANEO-specific by the
`com.ayaneo.update` package reference in the ADB chapter) but the guide
never states an exact model or SoC, so it is **not confirmed to be our
Pocket FIT specifically** — see `FINDINGS.md` question 5.

## Why this, why now

Two things made this worth a read:

1. **The `xsu`-crash problem is the dominant open thread in this project**
   (`STATUS.md`'s `xsud` stack-overflow saga). Any credible root-free
   alternative for privileged sysfs access is high-value. This guide's
   chapter 2 is titled "Canta and Shizuku" — Shizuku is a real,
   ADB-pairing-based, no-root elevated-permission mechanism on Android, and
   the tip worth checking was whether the guide documents using it as a
   general privilege-granting technique (usable by our own app), not just
   as plumbing for one specific uninstaller tool.
2. **General reference value.** Even absent a root-alternative technique,
   a curated list of `adb shell settings put` / `cmd` / `appops` commands
   for CPU/GPU/thermal/performance tuning on an AYANEO handheld could
   contain facts worth folding into `diagnostics/docs/HARDWARE_PROFILE.md`.

## Scope of this assessment

Pure document analysis: every chapter under `Guide/` (`01-Introduction`
through `06-Appendices`) read in full, plus `README.md`, `CHANGELOG.md`,
`CONTRIBUTING.md`. No build, no install, no device involved — no `adb`
command was run against the physical device for this assessment (not
needed; nothing here required verification against live hardware to
evaluate applicability). See `FINDINGS.md` for the numbered Q&A with
section/line evidence pointers into `research/pam-stock-os-optimization-upstream/`.
