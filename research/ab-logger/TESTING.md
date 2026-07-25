# Running ab-logger, and pulling/cleaning up its logs

This covers the tool itself. For the actual native-vs-`pulse-for-aya` A/B
test procedure (what to do in-game, in what order, what to record), see
`research/pulse-for-aya/TESTING.md` (written in Polish, for a
non-technical tester to run directly on the device).

## Install (once)

```bash
cd research/ab-logger
echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > local.properties
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Running a session

1. Open **AB Logger** on the device.
2. Tap **Start log**. On first run, Android will ask for notification
   permission — allow it if you want to see live progress ("Logging...
   N samples") in the notification shade while backgrounded; denying it
   still lets logging run, you just won't see that progress indicator.
3. Background the app (home button, switch to the game, whatever) — the
   foreground service keeps sampling every 2 seconds regardless of what's
   in the foreground.
4. When done, reopen AB Logger and tap **Stop log**. The status line
   shows the CSV path that was written.
5. Repeat as many times as needed — each "Start log" begins a new,
   separately-timestamped file, nothing gets overwritten.

## Pulling logs off the device

All sessions land in one directory, `/sdcard/apl_ab_logs/`:

```bash
adb pull /sdcard/apl_ab_logs/ ./pulled_logs/
```

This grabs every session CSV in one go — no need to pull them one at a
time or track filenames as they're created.

## Renaming and filing

The generic `session_<timestamp>.csv` filename doesn't say which arm
(native AyaSettings vs `pulse-for-aya`) was active — that's only in
whatever notes were taken during the session (timestamps + which mode),
per `pulse-for-aya/TESTING.md`. Match timestamps, rename accordingly
(e.g. `native_run1.csv`, `pulse_run1.csv`), and file into
`diagnostics/logs/ab-comparison/<game>_<scene>/` — see that folder's own
`README.md` for the suggested layout.

## Cleaning up the device

Once logs are safely pulled and renamed on the computer, clear the
on-device copies so old sessions don't pile up or get mixed into a later
`adb pull`:

```bash
adb shell rm -rf /sdcard/apl_ab_logs/*
```

Safe to run any time between sessions — the app recreates the directory
on the next "Start log" if it's gone.
