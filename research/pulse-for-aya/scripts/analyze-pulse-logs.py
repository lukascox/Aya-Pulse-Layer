#!/usr/bin/env python3
"""
Groups PulseDaemon session logs by shared timestamp, trims the main log's
idle-tick repetition in place, and writes one combined SUMMARY.md covering
every session found -- so a pulled directory can be read as one short report
instead of N raw files.

Usage:
    ./analyze-pulse-logs.py <pulled_dir>

Intended to slot into the existing pull procedure, no model involved:
    adb shell rm -rf /sdcard/apl_pulse_logs/*
    ...  (play, let it crash or not)
    adb pull /sdcard/apl_pulse_logs/ ./pulled/
    research/pulse-for-aya/scripts/analyze-pulse-logs.py ./pulled/

Recursively finds pulse_<timestamp>[_cap_poll|_dmesg|_logcat].log files under
<pulled_dir> (handles both flat pulls and whole-directory pulls that preserve
an `apl_pulse_logs/` subfolder), groups them by the <timestamp> they share,
and per group:
  - trims pulse_<timestamp>.log's idle TICK-SKIP repetition IN PLACE (same
    collapse-runs convention already used by hand throughout STATUS.md:
    3+ consecutive lines identical except for the timestamp collapse to
    first + elision count + last)
  - leaves _cap_poll/_dmesg/_logcat files untouched -- see "Why not
    auto-trimmed" below
  - appends one section to SUMMARY.md in <pulled_dir>, in chronological order

Why cap_poll/dmesg/logcat aren't auto-trimmed here: cap_poll's live-clock
values jitter every sample even when nothing interesting is happening, so
exact-line dedup collapses almost nothing without a fuzzier (and riskier)
rule -- kept whole, matching the convention already used for every previous
cap_poll file in this repo. dmesg/logcat are new as of 2026-07-28 and there
isn't yet a real crash capture to learn "safe boilerplate" from -- the
summary flags size and any crash-keyword hits instead of guessing at a trim.
"""

import re
import sys
from collections import defaultdict
from pathlib import Path

TS_RE = re.compile(r"pulse_(\d{8}_\d{6})(_cap_poll|_dmesg|_logcat)?\.log$")
LINE_TS_RE = re.compile(r"^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\s+(.*)$")

CRASH_KEYWORDS = [
    "SIGSEGV", "Fatal signal", "signal 11", "signal 6", "FATAL EXCEPTION",
    "Killed", "panic", "Segmentation fault",
]


def collapse_idle_repetition(path: Path) -> tuple[int, int]:
    """Collapses runs of 3+ consecutive lines identical except for the leading
    timestamp. Overwrites `path` in place (prepending a TRIMMED header) if
    anything was collapsed. Returns (original_line_count, new_line_count)."""
    lines = path.read_text(errors="replace").splitlines()
    orig_size = path.stat().st_size

    def content(line: str) -> str:
        m = LINE_TS_RE.match(line)
        return m.group(2) if m else line

    out: list[str] = []
    i, n = 0, len(lines)
    collapsed_any = False
    while i < n:
        j = i
        while j + 1 < n and content(lines[j + 1]) == content(lines[i]):
            j += 1
        run_len = j - i + 1
        if run_len >= 3:
            out.append(lines[i])
            out.append(f"# ... {run_len - 2} more identical lines elided (same content, only timestamp differs) ...")
            out.append(lines[j])
            collapsed_any = True
        else:
            out.extend(lines[i:j + 1])
        i = j + 1

    if not collapsed_any:
        return len(lines), len(lines)

    header = [
        f"# TRIMMED (analyze-pulse-logs.py) -- original was {len(lines)} lines / {orig_size}B.",
        "# Kept: every distinct line; runs of 3+ consecutive identical lines (idle TICK-SKIP polling,",
        "# only the timestamp differs) collapsed to first + elision count + last.",
        "# ---",
    ]
    path.write_text("\n".join(header + out) + "\n")
    return len(lines), len(header) + len(out)


def count(lines: list[str], needle: str) -> int:
    return sum(1 for l in lines if needle in l)


def first_last_ts(lines: list[str]) -> tuple[str | None, str | None]:
    ts = [m.group(1) for l in lines if (m := LINE_TS_RE.match(l))]
    return (ts[0], ts[-1]) if ts else (None, None)


def field_ever_changes(lines: list[str], field: str) -> bool:
    """True if `field=value` (e.g. p2_max=...) takes more than one distinct value across the file."""
    pat = re.compile(rf"{re.escape(field)}=(\S*)")
    values = {m.group(1) for l in lines for m in [pat.search(l)] if m}
    return len(values) > 1


def summarize_group(ts: str, files: dict[str, Path]) -> str:
    out = [f"## {ts}\n"]

    main = files.get("main")
    if main:
        orig_n, new_n = collapse_idle_repetition(main)
        lines = main.read_text(errors="replace").splitlines()
        first_ts, last_ts = first_last_ts(lines)
        version_line = next((l for l in lines if "version=" in l), None)
        clean_stop = any("session end (clean stop)" in l for l in lines)
        tick_skip = count(lines, "TICK-SKIP")
        # NOTE: "autoActive=true" never appears as literal text -- that field is only printed on the
        # TICK-SKIP (early-return/idle) branch, which by definition stops firing once AutoTDP actually
        # engages. Detect engagement instead via the session header or any real regulation action.
        auto_active = any("AUTOTDP-SESSION" in l or "act=" in l for l in lines)
        writes_daemon = count(lines, "cap write via daemon")
        writes_fallback = count(lines, "cap write via xsu fallback")
        reads_daemon = count(lines, "telemetry read via daemon")
        reads_fallback = count(lines, "telemetry read via xsu fallback")
        trims = count(lines, "act=TRIM")
        raises = count(lines, "act=RAISE")
        holds = count(lines, "act=HOLD")

        out.append(f"- `{main.name}`: {orig_n} lines" + (f" (trimmed to {new_n})" if new_n != orig_n else "") + "\n")
        if version_line:
            out.append(f"  - version: `{version_line.split(' ', 2)[-1].strip()}`\n")
        out.append(f"  - span: {first_ts} -> {last_ts}\n")
        out.append(f"  - clean session end: {'YES' if clean_stop else '**NO -- crash suspected**'}\n")
        out.append(f"  - TICK-SKIP (idle) lines: {tick_skip}\n")
        out.append(f"  - AutoTDP ever engaged this session: {'YES' if auto_active else 'no'}\n")
        out.append(f"  - cap writes: {writes_daemon} via daemon, {writes_fallback} via xsu fallback\n")
        out.append(f"  - telemetry reads: {reads_daemon} via daemon, {reads_fallback} via xsu fallback\n")
        out.append(f"  - regulation actions: {trims} TRIM, {raises} RAISE, {holds} HOLD\n")
    else:
        out.append("- **main `.log` missing from this group**\n")

    cap_poll = files.get("cap_poll")
    if cap_poll:
        lines = cap_poll.read_text(errors="replace").splitlines()
        clean_stop = any("session end (clean stop)" in l for l in lines)
        changed_fields = [
            f for f in ("p0_max", "p2_max", "p5_max", "p7_max", "gpu_max_pwrlevel", "gov")
            if field_ever_changes(lines, f)
        ]
        out.append(f"- `{cap_poll.name}`: {len(lines)} lines, clean end: {'YES' if clean_stop else 'NO'}, "
                    f"fields that changed within-session: {', '.join(changed_fields) if changed_fields else 'none (flat)'}\n")

    for key, label in (("dmesg", "dmesg"), ("logcat", "logcat")):
        f = files.get(key)
        if not f:
            continue
        lines = f.read_text(errors="replace").splitlines()
        hits = [l for l in lines if any(kw in l for kw in CRASH_KEYWORDS)]
        out.append(f"- `{f.name}`: {len(lines)} lines, crash-keyword hits: {len(hits)}"
                    + (" -- **review manually, see lines below**" if hits else "") + "\n")
        for h in hits[:15]:
            out.append(f"  - `{h.strip()}`\n")
        if len(hits) > 15:
            out.append(f"  - ... {len(hits) - 15} more, open the file directly\n")

    out.append("\n")
    return "".join(out)


def main() -> None:
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} <pulled_dir>", file=sys.stderr)
        sys.exit(1)
    root = Path(sys.argv[1])
    if not root.is_dir():
        print(f"Not a directory: {root}", file=sys.stderr)
        sys.exit(1)

    groups: dict[str, dict[str, Path]] = defaultdict(dict)
    for p in sorted(root.rglob("pulse_*.log")):
        m = TS_RE.search(p.name)
        if not m:
            continue
        ts, kind = m.group(1), m.group(2)
        key = {None: "main", "_cap_poll": "cap_poll", "_dmesg": "dmesg", "_logcat": "logcat"}[kind]
        groups[ts][key] = p

    if not groups:
        print(f"No pulse_*.log files found under {root}", file=sys.stderr)
        sys.exit(1)

    sections = [summarize_group(ts, files) for ts, files in sorted(groups.items())]

    summary_path = root / "SUMMARY.md"
    summary_path.write_text(
        f"# PULSE session summary -- {len(groups)} session(s)\n\n"
        "Auto-generated by `analyze-pulse-logs.py`. Read this before opening any raw log file.\n\n"
        + "".join(sections)
    )
    print(f"Wrote {summary_path} ({len(groups)} session group(s))")


if __name__ == "__main__":
    main()
