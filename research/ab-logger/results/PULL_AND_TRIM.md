# Pull and trim procedure

One device on the cable at a time. Run this from the directory the logs
should end up in.

Written after three sessions where something in this chain went wrong: a
0-byte fan capture nobody noticed, a manual logcat dump carrying the home
network name and a personal e-mail, and a directory name promising fan data
it did not hold.

---

## Before the session

- Fan card on **Custom** if the session should say anything about the fan
  curve. On SMART every `PulseFan` line is `arbiter=None` and the pull holds
  zero curve data. Three sessions in a row have gone that way.
- Check the **per-app** fan setting for the game you are about to play, not
  just the global Fan card. A per-app mode wins over the global one
  (`FanArbiter.decide()` resolves `boundFanMode ?: managedFanMode`), silently
  — the card still reads Custom and the arbiter still returns `None`. This
  cost `W` its first 49 minutes on 2026-08-03.
- `sleep` **off**. This started as caution and now has evidence behind it: on
  `B`, every one of the seven process kills on 2026-08-03 was triggered by
  `SleepProfileMonitorService`, and `W` with Sleep off had none
  (`unsupervised_session_2026-08-03/NOTES.md` §2). Leave it off until that is
  settled deliberately.
- Clear the previous logs, so the pull is unambiguous:

```bash
adb shell rm -rf /sdcard/apl_pulse_logs/
```

> Deletes files on the device. Only this path, and only **before** a session
> — doing it afterwards is how a pull gets thrown away.

> **No live fan capture needed.** An earlier version of this file told you to
> run `adb logcat -s PulseFan:D | tee fan_live.log` before playing. Drop it:
> it produced a 0-byte file twice running, and it is redundant — the app
> writes its `PulseFan` lines into `pulse_<ts>.log` directly, which is where
> the whole 2026-08-02 fan analysis came from. Grep the pulled app log
> instead (see triage below).

---

## Pull

```bash
adb pull /sdcard/apl_pulse_logs/ ./ && mv apl_pulse_logs/* . && rmdir apl_pulse_logs
```

```bash
../../pulse-for-aya/scripts/analyze-pulse-logs.py .
```

(Adjust the relative path to `analyze-pulse-logs.py` for wherever you are.)

> If two units' logs ever end up under one tree, run the analyzer on each
> unit's directory separately. It recurses and groups by the timestamp in the
> filename, silently overwriting on collision — two sessions started in the
> same second merge into one bogus session with a file dropped, no error.

---

## Triage — read `SUMMARY.md` first

```bash
cat SUMMARY.md
```

Nearly every flag it raises is a known false positive:

| Flag | How to resolve it |
|---|---|
| `dmesg` crash hits: 2 | Boot-time lines at `[1.2s]` whose *driver names* contain "panic" (`gh_panic_notifier`, `sde_dbg_init … panic:1`). Noise, every session. |
| `logcat` crash hits: ~15 | Compare each timestamp against the session start. `init`/`nvkeeper`/`qcrosvm` aborts that pre-date it are the ring buffer replayed when the filter attaches. Noise. |
| `clean session end: NO` | Usually the pull cutting a live session, or the user exiting a game. Check the log's last lines look normal. |
| A gap in the app log | Check `cap_poll` over the same window. Still ticking at ~1 Hz with caps released and `batt_online=1` means charger / screen-off idle, not a failure. |

Only a hit **inside** the session window, naming a process that is not one of
the known-noisy ones, deserves a closer look.

Then three things the summary does not flag:

```bash
grep -c "session start" pulse_*[0-9].log
grep -aho "applied=[0-9]*% target=[0-9]*" pulse_*[0-9].log | sort | uniq -c | sort -rn | head
for f in pulse_*[0-9].log; do echo "$f caps $(grep -ac 'cap write via xsu' $f)/$(grep -ac 'cap write via' $f) reads $(grep -ac 'read via xsu' $f)/$(grep -ac 'read via' $f)"; done
```

**More than one session file per unit means the daemon restarted**, and that
is the single most important thing to check. On 2026-08-02 `B` came back with
five, from six `com.kei.pulse` kills — which is only visible in a manual
`logcat -b all` (`grep "Process com.kei.pulse .* has died"`). Pull one if the
session count is wrong, extract the lines, then delete the dump.

**The `applied=/target=` histogram is how you tell whether the fan curve did
anything.** If every sample is `target=20` the curve is pinned at
`FanCurve.MIN_PERCENT` and the session says nothing about curve behaviour —
this has now happened once with Custom actually enabled. A narrow band well
above the floor is not a failure: on 2026-08-03 `B` sat at 26-28 % because it
never got hotter than 55 °C, which is what its curve says. Read it against the
temperature, not on its own:

```bash
grep -aho "arbiter=[A-Za-z]*" pulse_*[0-9].log | sort | uniq -c
grep -ah "CUSTOM fan running" pulse_*[0-9].log | sed -E 's/^([0-9-]+ [0-9]{2}:[0-9]).*temp=([0-9]+).*applied=([0-9]+)%.*/\1 \2 \3/' | awk '{s[$1" "$2]+=$3; a[$1" "$2]+=$4; n[$1" "$2]++} END{for(k in s) printf "%s temp_avg=%.0f applied_avg=%.0f n=%d\n", k, s[k]/n[k], a[k]/n[k], n[k]}' | sort
```

The first line is the cheaper check of the two: any meaningful count of
`arbiter=None` means something took the fan away from the curve, and the
per-app override above is the usual reason.

**Track read fallback separately from write fallback.** They differ by a lot
(2026-08-02: 17.4 % of reads vs 5.0 % of writes on `W`), and only writes were
being counted before. Raw `xsu` is the historical prime suspect for crashes.

---

## Keep the evidence, drop the file

If a raw file holds something real, copy the specific lines into
`evidence/<name>.txt` with a header saying what it is and when. Never keep a
whole `_dmesg.log` for the sake of a three-line warning. Example:
`unsupervised_session_2026-08-01/W/evidence/kernel_walt_warning.txt`.

---

## Redaction check — before `git add`, always

`dmesg` masks hardware addresses. **`logcat` does not.** A manual
`logcat -b all` dump has carried the home network name, a BSSID, four
unmasked hardware addresses and a personal e-mail.

```bash
for f in $(find . -type f); do n=$(grep -acoiE "([0-9a-f]{2}:){5}[0-9a-f]{2}|ssid|@gmail|@outlook|token=|Bearer |accountName|/Users/|/home/[a-z]" "$f"); [ "$n" != "0" ] && echo "!! $f -> $n"; done; echo "(no !! = clean)"
```

The one expected false positive is `SSID` matching inside the word `BSSID` in
prose. Anything else: that file does not get committed.

**If the session involved PC streaming (Artemis / Moonlight), run a second
sweep.** The one above looks for none of what streaming can leak — a host
name, a local IP, a GPU model:

```bash
for f in $(find . -type f); do n=$(grep -acoE "([0-9]{1,3}\.){3}[0-9]{1,3}|\.local\b|moonlight|sunshine|GeForce|nvidia" "$f"); [ "$n" != "0" ] && echo "!! $f -> $n"; done; echo "(no !! = clean)"
```

On 2026-08-04 this came back clean — the streaming client's networking never
reaches PULSE's own logs — but that was the first session to test it, and a
clean result once is not a guarantee.

**Manual full-logcat dumps are never committed.** Keep them outside the repo
and extract verified lines only.

---

## Delete what proves nothing

Keep `pulse_<ts>.log` and `pulse_<ts>_cap_poll.log` — the app's own record
plus the sysfs ground truth. Every finding so far has rested on those two.

```bash
rm -f pulse_*_dmesg.log pulse_*_logcat.log SUMMARY.md *_pkg.txt
```

`SUMMARY.md` goes too: it is auto-generated and its crash-hit list dangles
once the files it cites are gone. Its content belongs in a hand-written
`NOTES.md`.

Typical result: ~23 MB in, ~3 MB out.

---

## `NOTES.md`, then commit

One per session directory. It must state:

- what was actually played, and **whether two units' workloads were
  comparable** — they usually are not, so say it at the top or the numbers
  get misread later
- which flags were false positives, and how that was established
- findings worth keeping, with the log excerpt inline
- what was deleted and why, including the redaction note
- capture problems, so the next session fixes them

Then the usual gate: `git push` to Forgejo freely; the GitHub mirror needs an
explicit go-ahead plus the review pass in `CLAUDE.md`.
