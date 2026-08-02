# Testing procedure

Rewritten in English 2026-08-02. This file was previously in Polish, written
for a non-technical tester who was handed a prepared device. That is no longer
the situation: testing is done by the author, and the first outside tester
found the project through a public thread. The old A/B procedure it described
(paired 10-minute runs driven by `AutoTdpAbHarness`) is retired -- that harness
is superseded by `research/ab-logger/`, and PULSE now records everything by
itself with no second app and no stopwatch.

## What testing means now

Play normally, for as long as you would anyway. PULSE logs itself in the
background. There is nothing to start, stop, or time.

Everything that follows is about making the resulting logs answer a question,
rather than just existing.

## Before you play

**Decide what this session is supposed to prove, and change one thing.** Four
sessions in a row produced no fan-curve data because the setting that would
have generated it was never on. A session that changes nothing tells you only
that the app did not crash.

- **Fan card**: `Custom` if the session should say anything about fan control.
  Note that Custom has two sub-modes -- a **curve**, and **hold target temp**
  (a PI loop, default target 78 °C). They are different controllers and they
  produce different logs. Know which one is selected; this has already been
  misread once.
- **AutoTDP**: on for most titles. Off, with a fixed tier, for emulators and
  framerate-capped 2D games -- AutoTDP trims those into the floor because their
  reported framerate stays flat until they collapse.
- **Sleep**: off. There is no on-device evidence for it yet and it would
  confound everything else.

**Clear the previous logs**, so the pull is unambiguous:

```bash
adb shell rm -rf /sdcard/apl_pulse_logs/
```

## While you play

Nothing. That is the point.

**Stop and report immediately** if any of these happen, rather than finishing
the session:

- the device gets noticeably hotter than it normally does in that same game
- the fan behaves oddly -- very loud, or silent where it normally spins
- the device reboots, or freezes for more than a few seconds
- a game crashes repeatedly in a spot where it normally does not

The last two have real precedent in this project's history. The device matters
more than the session.

**Write down what you actually played**, roughly, with times. Without it two
units' logs get compared as if they were the same workload, which has already
produced one wrong conclusion.

## After you play

Pull and trim the logs following
[`research/ab-logger/results/PULL_AND_TRIM.md`](../ab-logger/results/PULL_AND_TRIM.md).
That file is the authoritative procedure and is kept up to date; do not
improvise around it.

The three checks worth running before anything else:

```bash
grep -c "session start" pulse_*[0-9].log
grep -aho "applied=[0-9]*% target=[0-9]*" pulse_*[0-9].log | sort | uniq -c | sort -rn | head
for f in pulse_*[0-9].log; do echo "$f caps $(grep -ac 'cap write via xsu' $f)/$(grep -ac 'cap write via' $f) reads $(grep -ac 'read via xsu' $f)/$(grep -ac 'read via' $f)"; done
```

More than one `session start` per unit means the daemon restarted, and that is
the single most important thing to notice. The second command tells you whether
fan control did anything other than sit at its floor. The third tracks how much
traffic bypassed the daemon FIFO and went through raw `xsu`, which is the
historical suspect for instability.

## If you are testing this on your own device

Two things you should know, because nothing in the app tells you:

**It writes logs and never deletes them.** Roughly 3-4 MB per hour of play into
`/sdcard/apl_pulse_logs/`, unconditionally, in every build that currently
exists. Clear that directory yourself.

**It is tested on two devices, both the author's.** Known-open problems are
listed in `STATUS.md`, which is kept honest, including about the conclusions
that later turned out to be wrong. Reading it before installing is reasonable.
