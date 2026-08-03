# Aya-Pulse-Layer — research notes for gluing `pulse` onto an AYANEO Pocket FIT

**This repo is not an app. It is the research that makes one small patch
possible.**

There is already an excellent performance tuner for handheld gaming
devices — [`pulse`](https://github.com/keiretrogaming/pulse) (GPL-2.0). It
does auto FPS tuning, custom fan curves, per-game profiles, a live
overlay. It works, it is mature, and it was written for AYN Odin and
Retroid hardware.

It does not run on an AYANEO Pocket FIT, and the interesting question was
**why not, and how little would it take to fix that**. Everything here
exists to answer that question with evidence instead of guesses.

## The two rules

Everything in this repo follows from these, and they are worth stating
plainly because they decided almost every judgement call:

1. **KISS.** Prefer the smallest change that actually closes a gap. If
   something can be solved by doing things differently rather than by
   writing code, do that instead.
2. **Change as little of `pulse` as humanly possible.** Every line we
   modify is a line that has to be reconciled by hand against upstream
   forever after. A pristine copy of upstream lives in the repo purely so
   any change can be diffed against it — that copy is read-only and is
   never built, never edited, never touched.

The result is a *glue patch*, not a fork in spirit: swap the parts that
genuinely cannot work on this hardware, and leave everything else exactly
as its authors wrote it.

## What was actually wrong, and what it took

**The blocker was smaller than it looked.** `pulse` talks to the operating
system's protected settings through a privileged helper service that ships
on AYN devices and simply does not exist here. That turned out to be one
narrow piece of plumbing, not a deep assumption — swapping it for this
device's own root shell was most of the work of getting the app running.

**Then the root shell turned out to be buggy.** This device's root helper
is closed-source and crashes when it is asked for too many separate
connections, which a real gaming session comfortably manages. Nothing here
can fix someone else's binary, so instead the app stopped *making* all
those connections: it now starts one privileged helper per session and
talks to it through a named pipe, rather than opening a new connection for
every single read and write.

**Fan control was the real surprise.** Both of the ways `pulse` controls a
fan are AYN-specific and do nothing at all on this hardware — the code
looked healthy while writing to a setting nothing on the device reads. The
first investigation concluded the direct hardware route was blocked too.
That conclusion was wrong: the fan node just needed the same permission
unlock this codebase already used elsewhere for the CPU and GPU. Once that
was found, `pulse`'s existing fan controller — which is genuinely good, and
was written to fight exactly this kind of vendor interference — worked
almost unchanged. The simpler on/off fan modes go through a command the
vendor's own software uses, discovered by taking that software apart.

**Almost everything else needed nothing.** CPU and GPU detection already
read the hardware's own capability tables at runtime. The overlay, themes,
profiles, boot behaviour, sleep handling and Quick Settings tile are
generic Android and were carried over untouched.

## Where things stand

Of the features upstream `pulse` offers, all but one are confirmed working
on this device — tested on real hardware, not assumed from reading code.
The exception is joystick RGB lighting, which is deliberately out of scope.

Two things found along the way are bugs in the *vendor's* software rather
than ours: a fan-curve command that quietly does nothing (which likely
means the device's own fan-curve editor does not work either), and an
input-validation gap that can crash a system app outright.

`STATUS.md` is the single living record of where everything actually is.
Read it first.

## Builds

Tagged releases are built and signed by GitHub Actions and attached to the
[Releases](https://github.com/lukascox/Aya-Pulse-Layer/releases) page, so
nobody has to take an APK by hand from a stranger. The build is reproducible
from this repository and the workflow that produced it is in
`.github/workflows/release.yml`.

Every release is signed with the same key. If you want to check that a build
you are holding really came from here — and that a later update has not been
swapped for something else — compare its signing certificate against this:

```
Signer: CN=Lukas Cox, OU=S2K, O=S2K, C=PL
SHA-256: 78:0A:EE:69:AF:4A:A6:30:28:A7:52:82:BD:01:27:F1:0C:41:DE:BB:B5:CF:8B:5A:DA:50:D8:2D:58:60:C2:E7
```

```bash
apksigner verify --print-certs pulse-for-aya-*.apk
```

A different fingerprint means a different key, and Android will refuse to
install it over an existing copy anyway.

**Installing one is entirely at your own risk.** This is a hobby research
project, tested on two devices, both mine. It writes to privileged system
settings and to fan control on hardware whose vendor software is closed and,
in at least two places documented here, buggy. This project's own history
includes crashes and reboots that needed a hard power cycle. Nothing about a
signed release changes that; it only means the file came from the code you
can read here.

**This is not a fork of `pulse`, and it is not trying to become one.**
`pulse` is somebody else's app and it is a good one. Its code is upstream's,
carried here as unchanged as I could manage; the work in this repo is the
glue that lets it talk to an AYANEO Pocket FIT. Bugs you hit are far more
likely to be in that glue than in `pulse` — please report them here rather
than upstream, unless you can reproduce them on an AYN or Retroid device.

## How this was built

I am not a professional application developer, but I do work professionally
with systems architecture, security and Linux — and most of the judgement
calls here come from that background rather than from Android experience.
Treating the closed-source root helper as an untrusted component to be worked
around rather than fixed, refusing approaches that touch the boot partition
while a fully reversible one existed, and insisting on a risk assessment
before writing anything to fan control are all that instinct, not this
project's.

The code itself was written iteratively with AI assistance, deliberately:
part of the point was to find out how far the right tooling can carry someone
into an unfamiliar domain. That is a hobby question as much as a technical
one, and the honest answer is "a long way, but only against a hard evidence
discipline".

Hence the rules the rest of this repo follows. A claim is expected to name
what was measured, and to say plainly when something is inferred rather
than observed. Everything load-bearing was confirmed on real hardware, not
concluded from reading code. Several findings here reversed earlier ones —
those reversals are left visible on purpose, because a research log that
only records the correct guesses is not a research log.

## Repo layout

```
Aya-Pulse-Layer/
├── research/
│   ├── pulse-for-aya/            # THE DELIVERABLE: the glue patch itself
│   ├── pulse-glue-assessment/    # why glue and not a rewrite; risk assessment
│   ├── pulse-upstream/           # read-only reference copy, for diffing only
│   │
│   ├── aidl-fan-spike/           # how fan control actually works here
│   ├── aidl-bind-spike/          # driving vendor profiles without root
│   ├── ayaspace-teardown/        # taking the vendor apps apart; this is where
│   ├── aya-gamewindows-teardown/    the undocumented commands came from
│   │
│   ├── xsu-capability-probe/     # what the root shell can and cannot do
│   ├── autotdp-ab-harness/       # early probes, superseded
│   ├── ab-logger/                # telemetry recorder + pulled session logs
│   │
│   └── *-assessment/             # scoped read-only passes over community
│                                    projects (see below); each has its own
│                                    *-upstream/ read-only clone
├── diagnostics/                  # raw hardware facts + a validated FPS script
├── app/                          # abandoned from-scratch skeleton, kept as
│                                    history; superseded by the glue approach
├── docs/archive/                 # frozen pre-git history, do not extend
└── STATUS.md                     # current state, living document
```

**Detail lives close to the work.** This file stays deliberately shallow.
Every `research/` subfolder carries its own `FINDINGS.md` or `README.md`
with the actual measurements, dead ends and reasoning; pulled device logs
carry a `SUMMARY.md` and `NOTES.md` explaining what they show. If you want
to know *how* something was established, that is where to look.

## Community projects referenced

Cloned locally, read-only, never built or modified, and confronted against
this repo's own findings — looking for reusable technique, not general
reading. Each has a full write-up in its `*-assessment/FINDINGS.md`.

- **[`pulse`](https://github.com/keiretrogaming/pulse)** — the app all of
  this exists to serve. GPL-2.0.
- **[ClusterTune](https://github.com/AurelioB/ClusterTune)** — pioneered
  the no-root technique `pulse` itself credits. Its fallback for devices
  without that helper turned out to be the exact pattern that crashes this
  device, so it validated the approach here by counter-example. One good
  idea borrowed: try an ordinary unprivileged read before escalating.
- **[KonaBess-Next-G3Gen3](https://github.com/thefiqs/KonaBess-Next-G3Gen3)**
  — GPU frequency table editor. Reference only: it patches boot partitions
  and needs an unlocked bootloader, a much riskier class of change than the
  fully reversible settings this project touches.
- **[PAM Stock OS Optimization Guide](https://github.com/BruhMeh/PAM-Stock-OS-Optimization-Guide)**
  — community tuning guide. The hoped-for lead did not pan out; two minor
  display-latency facts were worth keeping.

## Working conventions

- **Findings before code.** Write down what was checked and confirmed, so
  the next session does not rediscover it.
- **One living document.** `STATUS.md` is updated in place; `git log` is
  the history. No `HANDOFF_v2.md`-style files — that pattern was retired
  when this repo moved into git (its remains are in `docs/archive/`).
- **Verify on hardware.** Reading code is how a change gets proposed;
  running it on the device is how it gets believed.

## Licence

GPL-2.0, inherited from [`pulse`](https://github.com/keiretrogaming/pulse),
of which the deliverable in `research/pulse-for-aya/` is a derivative work.
The same licence covers this repository as a whole, so that the research and
the patch it produced cannot be separated from those terms.

Device logs published here are redacted: network names and hardware
addresses are replaced with placeholders. Nothing else about them is altered.
