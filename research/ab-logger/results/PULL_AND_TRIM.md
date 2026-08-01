# Pull and trim procedure — two-device sessions

The end-to-end routine for a `B`/`W` session: pull the logs, triage them,
delete what proves nothing, commit what does. Written after three sessions
where something in this chain went wrong (empty capture, private data in a
manual dump, a directory name that promised fan data it did not hold).

Everything here is read-only on the device except the two clearly marked
steps that delete logs from `/sdcard`.

---

## 0. Before the session

On **both** units:

- Fan card set to **Custom** if the session is meant to say anything about
  the fan curve. On SMART, every `PulseFan` line is `arbiter=None` and the
  pull carries zero curve data. Three sessions in a row have gone this way.
- `sleep` **off** — the one feature with no on-device evidence; enabling it
  confounds everything else.
- Clear the previous session's logs so the pull is unambiguous:

```bash
adb -s "$B" shell rm -rf /sdcard/apl_pulse_logs/
```

```bash
adb -s "$W" shell rm -rf /sdcard/apl_pulse_logs/
```

> **Deletes files on the device.** Only ever this path. Do it *before* a
> session, never after — after is how a pull gets thrown away.

---

## 1. Identify both devices

With two units attached, every bare `adb` command fails with
`more than one device/emulator`. Set the serials once and use `-s` for the
rest of the session:

```bash
adb devices -l
```

```bash
B=<serial-of-black-unit>; W=<serial-of-white-unit>; echo "B=$B W=$W"
```

Sanity-check that you did not swap them — the units are the same model and
the same SKU, only the colour differs:

```bash
for d in "$B" "$W"; do echo -n "$d: "; adb -s "$d" shell getprop ro.serialno; done
```

---

## 2. Create the session directories

```bash
cd research/ab-logger/results && mkdir -p "unsupervised_session_$(date +%F)"/{B,W} && cd "unsupervised_session_$(date +%F)"
```

---

## 3. Pull

```bash
adb -s "$B" pull /sdcard/apl_pulse_logs/ ./B/
```

```bash
adb -s "$W" pull /sdcard/apl_pulse_logs/ ./W/
```

This lands an `apl_pulse_logs/` subfolder inside each. Flatten it so the
directories match the rest of the repo:

```bash
for u in B W; do [ -d "$u/apl_pulse_logs" ] && mv "$u"/apl_pulse_logs/* "$u"/ && rmdir "$u/apl_pulse_logs"; done
```

---

## 4. Summarise — separately per unit, never on the parent

```bash
../../../pulse-for-aya/scripts/analyze-pulse-logs.py ./B/
```

```bash
../../../pulse-for-aya/scripts/analyze-pulse-logs.py ./W/
```

> **Never run it on the parent directory.** It uses `rglob` and groups purely
> by the timestamp in the filename, and `groups[ts][key] = p` silently
> overwrites on collision. Two units started within the same second merge
> into one bogus session with a file dropped, with no error.

---

## 5. Optional live captures

These are the only way to see fan behaviour: `pulse_daemon.sh` filters its
detached logcat down to crash tags, so `PulseFan` lines never reach a pulled
`_logcat.log`. Start **before** playing, leave running, and check afterwards
that the file is not empty — a 0-byte `fan_test.log` has already happened
once.

```bash
adb -s "$B" logcat -s PulseFan:D | tee B_fan_live.log
```

Anything captured with `logcat -b all` is a different matter: see step 8.

---

## 6. Triage — read the summary first

```bash
cat B/SUMMARY.md; cat W/SUMMARY.md
```

Then work down the flags. Almost all of them are known false positives:

| Flag | How to resolve it |
|---|---|
| `dmesg` crash hits: 2 | Boot-time lines at `[1.2s]` whose *driver names* contain "panic" (`gh_panic_notifier`, `sde_dbg_init … panic:1`). Noise, every session. |
| `logcat` crash hits: 14-16 | Compare each timestamp against the session start. `init`/`nvkeeper`/`qcrosvm` aborts that pre-date it are the ring buffer being replayed when the filter attaches. Noise. |
| `clean session end: NO` | Almost always the pull cutting a live session, or the user exiting a game. Check whether the log's last lines look normal. |
| A gap in the app log | Check `cap_poll` across the same window. Still ticking at ~1 Hz with caps released and `batt_online=1` means charger/screen-off idle, not a failure. |

Only a hit that falls **inside** the session window and names a process other
than a known-noisy one deserves a closer look.

Worth checking every time, because none of it is flagged automatically:

```bash
for u in B W; do echo "== $u"; grep -c "session start" $u/pulse_*[0-9].log; grep -c "via xsu" $u/pulse_*[0-9].log; grep "PulseFan" $u/pulse_*[0-9].log | head -5; done
```

More than one `session start` means the daemon restarted. A high `via xsu`
share (over ~5 %) is worth noting — raw `xsu` is the historical prime suspect
for crashes.

---

## 7. Extract evidence before deleting anything

If a raw file holds something real, pull the specific lines into
`<unit>/evidence/<name>.txt` with a header saying what it is and when. Never
keep a whole `_dmesg.log` for the sake of a three-line warning. Example:
`unsupervised_session_2026-08-01/W/evidence/kernel_walt_warning.txt`.

---

## 8. Redaction check — before `git add`, always

`dmesg` masks hardware addresses. **`logcat` does not.** A manual
`logcat -b all` dump has carried the home network name, a BSSID, four
unmasked hardware addresses and a personal e-mail.

```bash
for f in $(find . -type f); do n=$(grep -acoiE "([0-9a-f]{2}:){5}[0-9a-f]{2}|ssid|@gmail|@outlook|token=|Bearer |accountName|/Users/|/home/[a-z]" "$f"); [ "$n" != "0" ] && echo "!! $f -> $n"; done; echo "(no !! = clean)"
```

`SSID` matching inside the word `BSSID` in prose is the one expected false
positive. Anything else: do not commit that file.

**Manual full-logcat dumps are never committed.** Keep them outside the repo,
extract verified lines only.

---

## 9. Delete what proves nothing

Keep per unit: `pulse_<ts>.log` and `pulse_<ts>_cap_poll.log`. That is the
app's own record plus the sysfs ground truth, and it is what every past
finding has actually rested on.

```bash
rm -f */pulse_*_dmesg.log */pulse_*_logcat.log */SUMMARY.md */*_pkg.txt
```

`SUMMARY.md` goes too: it is auto-generated, and its crash-hit list dangles
once the files it cites are gone. Its content belongs in `NOTES.md`, written
by hand.

Typical result: ~23 MB in, ~3 MB out.

---

## 10. Write `NOTES.md`, then commit

One `NOTES.md` per session directory, covering both units. It must state:

- what each unit actually did, and **whether the workloads were comparable**
  (they usually are not — say so at the top, or the numbers get misread later)
- which flags were false positives, and how that was established
- findings worth keeping, with the log excerpt inline
- what was deleted and why, including the redaction note
- capture problems, so the next session fixes them

Then the normal gate: `git push` to Forgejo freely; the GitHub mirror needs an
explicit go-ahead and the review pass in `CLAUDE.md`.
