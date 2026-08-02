package com.kei.pulse.root

import android.content.Context
import com.kei.pulse.BuildConfig
import java.io.File
import java.io.FileOutputStream

/**
 * One-xsu-connection-per-session daemon for CPU/GPU cap writes -- replaces the old "one xsu
 * connection per write" pattern with the architecture the original bash `pulse_lite`
 * (`docs/archive/pulse_lite/`) already used, before this repo had `xsu`/`xsud` at all: launch a
 * script once via a single `xsu -c "... &"` call, then apply every subsequent write as a plain
 * shell builtin already running as root, with zero further `xsu`/`xsud` connections. Validated
 * on-device three times (`research/pulse-for-aya/scripts/daemon-persistence-test.sh`) plus once
 * more with the named-pipe bridge used here (`fifo-daemon-test.sh`) -- see `STATUS.md`'s
 * Minecraft-crash investigation, 2026-07-27, "New direction found and validated" / the FIFO
 * update, for the full evidence trail.
 *
 * The pipe lives under this app's own private storage (`filesDir`) -- `/data/local/tmp` was tried
 * first but this app's own process gets `EACCES` there (confirmed on-device); `filesDir` works for
 * both the app and the root daemon (also confirmed on-device, same session).
 *
 * NOT thread-safe for concurrent [start]/[stop] calls; callers must serialize through their own
 * lock (`ForegroundAppMonitorService`'s `transitionMutex` already owns this class's lifecycle).
 * [setCap] itself is safe to call from any thread but does blocking file I/O -- call it off the
 * main thread.
 */
class PulseDaemon(context: Context) {

    private val scriptFile = File(context.filesDir, "pulse_daemon.sh")
    private val fifoInPath = File(context.filesDir, "pulse_fifo_in").absolutePath
    private val fifoOutPath = File(context.filesDir, "pulse_fifo_out").absolutePath
    private val logPath = File(context.filesDir, "pulse_daemon.log").absolutePath
    private val assets = context.assets

    /**
     * One shared timestamp for this daemon launch, used to name BOTH session files below so they're trivially
     * pairable after a pull (same `<timestamp>` in both names).
     */
    private val sessionTimestamp =
        java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())

    /**
     * Master switch for the four `/sdcard/apl_pulse_logs/` session files below. **Debug builds only.**
     *
     * These exist for this project's own soak testing and are deliberately verbose: measured at roughly
     * 3-4 MB per hour of play across the four files (2026-08-02 sessions), written for as long as the daemon
     * runs, with nothing anywhere that ever deletes them. That is the right trade while the only devices
     * running this are the two being investigated, and completely wrong on a stranger's device -- a release
     * user would silently accumulate hundreds of MB in a directory they have no reason to know about, plus a
     * detached `logcat` and a 1 Hz `dmesg -c` drain running for the life of the session.
     *
     * The mechanism is just an empty path: `pulse_daemon.sh` already guards **every** use of all four
     * arguments behind `[ -n "$VAR" ]` (it was written that way for device/kernel variance, where a thermal
     * zone or the fan node might legitimately be missing), so passing "" turns each stream off at the source
     * -- no cap-poll loop, no dmesg drain, no detached logcat, no session file created. No script change was
     * needed for this and none should be added: keeping the shell side unconditional-but-guarded is what
     * makes this a one-line policy decision on the Kotlin side.
     *
     * NOT gated, deliberately: [logPath], the daemon's own log in the app's private `filesDir`. It is
     * truncated (`>`, not `>>`) at every launch, never grows, is unreachable to other apps, and is the only
     * thing that can explain a failed launch after the fact.
     *
     * If a release build ever needs diagnostics from a real user, this should become a user-visible opt-in
     * toggle that also offers to delete the directory -- not a silent default.
     */
    private val sessionDiagnosticsEnabled = BuildConfig.DEBUG

    /**
     * One human-readable session log per daemon launch, under `/sdcard` (not this app's own `filesDir`) so it
     * can be pulled directly (`adb pull`, no root/`run-as` needed) even if the device crashes before a host
     * `logcat` capture can be taken -- logcat itself has proven unreliable under real gameplay load (its
     * 256 KiB ring buffer overflows under `xsu`'s own chatty protocol logging, STATUS.md 2026-07-27). Written
     * by the SAME already-root daemon process via the existing FIFO ([log]), so this adds zero further `xsu`
     * connections -- unlike the app's own sandboxed process, which per this repo's own established finding
     * (`MainActivity.kt`'s AIDL probes) can't reliably reach `/sdcard` directly under scoped storage.
     */
    private val sdcardLogPath =
        if (sessionDiagnosticsEnabled) "/sdcard/apl_pulse_logs/pulse_$sessionTimestamp.log" else ""

    /**
     * Ground-truth sysfs cap/cur poll (same fields `scripts/poll-cpufreq.sh` reads by hand from a host), now
     * collected automatically by a background loop inside the daemon script itself for the whole session --
     * see `pulse_daemon.sh`'s header comment (STATUS.md, 2026-07-28) for why this still has real cross-check
     * value despite running inside the app's own daemon: it's a direct sysfs read, not a read of
     * `AutoTuneController`'s internal state, so it still answers "did the value actually land on the device"
     * independent of the app's own decision-making code.
     */
    private val capPollLogPath =
        if (sessionDiagnosticsEnabled) "/sdcard/apl_pulse_logs/pulse_${sessionTimestamp}_cap_poll.log" else ""

    /**
     * Kernel ring-buffer dump, polled (`dmesg -c`, clears after each read) in the same background loop as
     * [capPollLogPath] -- this is what finally lets a pulled log answer "did `xsud` itself segfault/abort"
     * directly: `research/xsu-capability-probe/FINDINGS.md` already proved `dmesg` catches that signal even
     * when the crashing process is long gone, since the kernel ring buffer doesn't depend on any userspace
     * process surviving (STATUS.md, 2026-07-28 -- added because nothing before this ever captured *why* a
     * crash happened, only that the log went silent).
     */
    private val dmesgLogPath =
        if (sessionDiagnosticsEnabled) "/sdcard/apl_pulse_logs/pulse_${sessionTimestamp}_dmesg.log" else ""

    /**
     * Filtered `logcat` (only the tags this crash's known signature needs: `AndroidRuntime`'s FATAL
     * EXCEPTION, `libc`'s Fatal signal, `DEBUG`'s backtrace, `ActivityManager`/`BatteryService` errors),
     * spawned ONCE by the daemon script as a fully detached process -- narrow enough that it can't itself
     * overflow the 256 KiB ring buffer the way a full `logcat` does under `xsu`'s own chatty protocol
     * logging (STATUS.md, 2026-07-27), and survives independently even if this daemon or the app dies
     * (same backgrounding pattern already validated for the daemon itself).
     */
    private val logcatLogPath =
        if (sessionDiagnosticsEnabled) "/sdcard/apl_pulse_logs/pulse_${sessionTimestamp}_logcat.log" else ""

    /**
     * Written as the very first line of [sdcardLogPath] (passed straight to the launch command, not sent
     * over the FIFO after the fact, so there's no race with the daemon script not being ready yet) -- lets
     * every pulled log answer "which build produced this" on its own, without cross-referencing a separate
     * version check. `versionName`/`versionCode` follow upstream's own scheme and aren't bumped for this
     * fork's patches; `BUILD_TIMESTAMP` (stamped fresh by Gradle every build, see `app/build.gradle.kts`) is
     * what actually tells two builds of the same `versionName` apart (STATUS.md, 2026-07-28 -- added after a
     * suspected regression turned out to need "is this really the patched build" ruled out first). Also
     * includes a CRC32 of the script asset [start] is ABOUT to write -- this is the one piece the APK's own
     * version can't vouch for: [start] used to copy the asset into `filesDir` only `if (!scriptFile.exists())`,
     * so `adb install -r` (which does NOT clear `filesDir`) could leave a stale script from days earlier
     * silently running forever, with the Kotlin side never able to tell (STATUS.md, 2026-07-28 -- the daemon
     * protocol changed 5 times that session alone). [start] now always overwrites, so this hash should always
     * match the asset in the APK that logged it -- if it ever doesn't, that itself is the bug to chase.
     */
    private val versionLabel =
        "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) built ${BuildConfig.BUILD_TIMESTAMP}"

    @Volatile private var running = false

    /**
     * Copies the daemon script from assets -- ALWAYS, overwriting any previous copy (see [versionLabel]'s
     * doc for why a stale, protocol-mismatched script left over from an earlier `adb install -r` was a real,
     * silent-failure risk) -- and launches it via one `xsu` call. Safe to call repeatedly -- a no-op while
     * already running.
     *
     * Logs the launch outcome (STATUS.md, 2026-07-28: `start()` used to log NOTHING -- the one genuine blind
     * spot left after a session where `/sdcard/apl_pulse_logs/` stayed completely empty and there was no way
     * to tell "the launching `xsu` call itself failed" apart from "it launched fine but the script died a
     * moment later"). The launch command appends `echo PULSE_DAEMON_LAUNCHED` -- that only prints once
     * `pkill`/`mkdir`/the backgrounding `sh ... &` have all been accepted by the outer shell, so seeing it
     * back confirms the OUTER `xsu` call succeeded; its absence means `xsu`/`RootExec` itself failed or timed
     * out, before the daemon script ever got a chance to run.
     *
     * Uses [RootSupport.runRootCommandResult] (not the plain [RootSupport.runRootCommand]) specifically so
     * the log line can show the REAL exception/timeout reason instead of a bare `null` -- a first pass at
     * this logging (STATUS.md, 2026-07-28) only had `result=null` to show, and that turned out to be
     * genuinely ambiguous: `run-as com.kei.pulse xsu -c "id"` proved root, and even the app's own UID/SELinux
     * context via `run-as`, both work instantly, so whatever's failing here is specific to how the call
     * happens from inside the app's own live (Zygote-forked) process -- only the actual exception message
     * (e.g. a `PATH` resolution failure vs a real 8s timeout) can narrow that down further.
     */
    fun start() {
        if (running) return
        val scriptBytes = assets.open(ASSET_NAME).use { it.readBytes() }
        scriptFile.writeBytes(scriptBytes)
        val scriptCrc = java.util.zip.CRC32().apply { update(scriptBytes) }.value
        val label = "$versionLabel script_crc32=${scriptCrc.toString(16)}"
        // Baseline BEFORE launching -- the script overwrites $LOG (`>`, not `>>`) as its very first action,
        // so comparing mtimes after the fact tells a genuinely fresh write apart from a stale leftover file
        // from an earlier session (plain existence isn't enough to prove THIS launch reached the script).
        val logFile = File(logPath)
        val mtimeBeforeLaunch = if (logFile.exists()) logFile.lastModified() else -1L
        val launchResult = RootSupport.runRootCommandResult(
            // Kill any orphaned daemon from a previous session first -- a hard process kill (e.g. OOM) leaves
            // one reading the same fixed FIFO_IN path forever (STATUS.md, 2026-07-28: once this daemon's
            // rm -f + mkfifo recreates that path, an orphan's next read loop iteration re-opens the new inode
            // too and silently competes for commands with the daemon Kotlin thinks it's talking to).
            //
            // STATUS.md, 2026-07-28 (SELF-KILL BUG, found + fixed): this used to be plain `pkill -f
            // 'pulse_daemon.sh'` -- but `pkill -f` matches against the FULL command line of every process,
            // and THIS ENTIRE launch command is itself passed as one `xsu -c "<this string>"` argument, which
            // necessarily contains the literal text "pulse_daemon.sh" (to invoke the script by path) --
            // meaning the invoking shell's OWN cmdline matched the pattern, and `pkill -f` killed its own
            // parent (this exact shell) via SIGTERM before ever reaching `mkdir`/the `sh ... &` launch/the
            // final `echo`. 100% reproducible (confirmed manually: `Terminated`, exit 143, on every single
            // run), not a flaky race -- explains the whole `start()` launch logging showing "no exception,
            // but unexpected stdout=null" every time since this pkill line was added. Fixed by explicitly
            // excluding the current shell's own PID (`$$`) via `pgrep` + a filtered `kill`, instead of the
            // self-matching `pkill -f`. Dropped the equivalent orphaned-logcat cleanup here (same self-kill
            // trap would apply to it too) -- an orphaned logcat filter is harmless (writes to one abandoned
            // file, no FIFO contention), not worth the extra ~170 chars this close to the ~800-char safe
            // margin (`research/xsu-capability-probe/FINDINGS.md`'s bisection).
            "mypid=\$\$; for p in \$(pgrep -f 'pulse_daemon.sh' 2>/dev/null); do " +
                "[ \"\$p\" = \"\$mypid\" ] || kill \"\$p\" 2>/dev/null; done; " +
                (if (sessionDiagnosticsEnabled) "mkdir -p /sdcard/apl_pulse_logs; " else "") +
                "sh '${scriptFile.absolutePath}' '$fifoInPath' '$logPath' '$sdcardLogPath' '$capPollLogPath' " +
                "'$label' '$fifoOutPath' '$dmesgLogPath' '$logcatLogPath' > /dev/null 2>&1 < /dev/null & " +
                "echo PULSE_DAEMON_LAUNCHED",
        )
        val launchValue = launchResult.getOrNull()
        android.util.Log.d(
            "PulseDaemon",
            if (launchValue == "PULSE_DAEMON_LAUNCHED") {
                "start() launch xsu call succeeded, $label"
            } else {
                val reason = launchResult.exceptionOrNull()?.let { "${it.javaClass.simpleName}: ${it.message}" }
                    ?: "no exception, but unexpected stdout=$launchValue"
                "start() launch xsu call FAILED: $reason, $label"
            },
        )
        // Independent confirmation that doesn't depend on the launch xsu call's own stdout, which
        // xsu-capability-probe/FINDINGS.md already documented as sometimes silently dropped by `xsud` even
        // on a command that otherwise completes cleanly (no timeout, no exception) -- STATUS.md, 2026-07-28:
        // confirmed happening on THIS exact launch command, not just a theoretical risk. $logFile lives in
        // this app's own private filesDir, so the app can read what the root daemon wrote there directly,
        // zero extra `xsu` calls -- same "root writes, app reads the same filesDir" mechanism the FIFO
        // pipes themselves already rely on.
        Thread {
            Thread.sleep(700)
            val confirmed = logFile.exists() && logFile.lastModified() > mtimeBeforeLaunch
            android.util.Log.d(
                "PulseDaemon",
                if (confirmed) {
                    "daemon script confirmed alive via local $logPath (independent of launch xsu stdout)"
                } else {
                    "daemon script NOT confirmed via local $logPath 700ms after launch -- " +
                        "either the script never ran, or it ran but this check itself raced it"
                },
            )
        }.apply { isDaemon = true }.start()
        running = true
    }

    /**
     * Sends one write (`echo <value> > <path>`, then `chmod <mode> <path>` -- e.g. `"644"` for
     * governor writes, `"444"` for CPU/GPU frequency caps, matching each control's own existing
     * convention so the vendor perf daemon can't silently stomp them) to the already-running
     * daemon -- zero `xsu` calls, just a blocking write into the pipe (the daemon's own read loop
     * wakes up within its current cycle, observed sub-millisecond in testing). `value` is a plain
     * string so this covers both numeric caps (frequencies) and names (governors) with one
     * protocol. Returns `false` (never throws) if the daemon isn't running or the pipe write
     * fails, so callers can fall back to a direct root-shell write.
     *
     * Opening a FIFO for write blocks until a reader is on the other end -- if the daemon script
     * never actually started (the launch `xsu` call silently failed) or died mid-session (e.g.
     * killed by `xsud`'s own documented instability under load), NOTHING will ever open the read
     * end again, so a bare `FileOutputStream` open here would block forever. [start] optimistically
     * marks `running = true` without confirming the script is actually alive, so that dead-daemon
     * case is real, not hypothetical. The write runs on a throwaway daemon thread with a bounded
     * [SET_CAP_TIMEOUT_MS] join instead of directly on the caller's thread/coroutine, so a wedged
     * daemon can never freeze the caller -- `ForegroundAppMonitorService.tick()`'s single poll-loop
     * coroutine calls this synchronously from `startAutoTdp()`, and a hang there would silently
     * stall the whole watcher (overlay/fan/RGB/AutoTDP) with no exception and no log to explain why
     * (STATUS.md's AutoTDP-tick-loop investigation, 2026-07-27 -- root-caused to exactly this).
     * On timeout the writer thread is abandoned (never interrupted -- a blocked FIFO open doesn't
     * reliably respond to `Thread.interrupt()`), a bounded, rare leak that matches the existing
     * documented lifecycle gap in [start]'s doc rather than introducing a new one.
     */
    fun setCap(path: String, mode: String, value: String): Boolean {
        if (!running) return false
        return sendLine("$path $mode $value")
    }

    /**
     * Appends one line (timestamped by the daemon script) to this session's `/sdcard` log file --
     * same zero-`xsu`, bounded-timeout FIFO write as [setCap], just a different protocol verb
     * (`"LOG <message>"` instead of a sysfs write). `message` must not contain a newline (any found
     * are replaced with a space, so one call always produces exactly one log line). Returns `false`
     * (never throws) on a dead/not-yet-started daemon -- callers should treat this as best-effort:
     * this is a diagnostic aid, not a control-path write, so there's no xsu fallback to drop back to.
     */
    fun log(message: String): Boolean {
        if (!running) return false
        // Nothing consumes this in a release build (SDCARD_LOG is ""), so skip the FIFO write entirely
        // rather than send a line the daemon will discard on every tick.
        if (!sessionDiagnosticsEnabled) return false
        return sendLine("LOG " + message.replace('\n', ' '))
    }

    /**
     * Reads every path in [paths] in ONE round trip through the daemon (`cat` run by the already-root shell,
     * zero `xsu` calls) instead of one `xsu` connection per path -- STATUS.md, 2026-07-28: `TelemetryReader`'s
     * per-tick read used to open ~13-15 separate `xsu` connections every ~1s during real gameplay (far more
     * than the cap writes [setCap] already covers), confirmed as the dominant contributor to this device's
     * `xsud` crash. Returns `null` (never throws) on a dead/not-yet-started daemon, a write/read failure, or a
     * response that doesn't match [paths] 1:1 -- callers should treat any of those as "batch failed" and fall
     * back to the existing per-path `xsu` reads entirely (same all-or-nothing contract as [setCap]'s callers),
     * not a partial mix. A successful result's elements line up positionally with [paths]; a path whose `cat`
     * came back empty (e.g. the node doesn't exist) is `null` in that position, matching plain `cat()`'s own
     * `null`-on-empty convention elsewhere in this codebase.
     *
     * Protocol: sends `"READ <path1> <path2> ...`" over the existing input FIFO, then reads ONE `|`-delimited
     * response line back over a second, output-only FIFO ([fifoOutPath]) -- paths never contain `|` or spaces,
     * but battery `status` values can ("Not charging"), so `|` (not a space) is the field delimiter. Same
     * bounded-timeout-on-a-throwaway-thread pattern as [setCap]/[log] -- see [setCap]'s doc for why a dead
     * daemon must never block the caller.
     */
    fun readBatch(paths: List<String>): List<String?>? {
        if (!running || paths.isEmpty()) return null
        val result = java.util.concurrent.atomic.AtomicReference<List<String?>?>(null)
        val worker = Thread {
            try {
                FileOutputStream(fifoInPath).use { it.write(("READ " + paths.joinToString(" ") + "\n").toByteArray()) }
                val line = File(fifoOutPath).inputStream().bufferedReader().use { it.readLine() }
                if (line != null) {
                    val values = line.split("|").map { it.ifEmpty { null } }
                    if (values.size == paths.size) result.set(values)
                }
            } catch (e: Exception) {
                // leave result null
            }
        }
        worker.isDaemon = true
        worker.start()
        worker.join(READ_BATCH_TIMEOUT_MS)
        val values = result.get()
        // STATUS.md, 2026-07-28: dispatch()'s write path already logs "via daemon"/"via xsu fallback";
        // this read path had NO equivalent, so no pulled log could ever confirm whether TelemetryReader's
        // reads were actually going through the daemon or silently falling back the whole session -- a
        // real blind spot, since a stale on-device daemon script (see [start]'s doc) would make every
        // readBatch() call fail exactly like this, indistinguishable from "working but slow" without this.
        val msg = if (values != null) {
            "telemetry read via daemon (${paths.size} path(s))"
        } else {
            "telemetry read via xsu fallback (${paths.size} path(s))"
        }
        android.util.Log.d("PulseDaemon", msg)
        log("PulseDaemon: $msg")
        return values
    }

    /**
     * Reads one `Settings.System` key (e.g. `fan_mode`) through the daemon -- `settings get system <key>`,
     * zero `xsu` calls -- instead of a fresh `xsu` invocation per read. First caller:
     * `FanController.readMode()`, found (STATUS.md, 2026-07-28) firing every ~1s from the live discrete
     * fan-mode arbiter tick, the one fan/RGB code path that turned out NOT to be already dead/self-gated on
     * this device (unlike the 120ms PWM-duty loop and RGB's `available()` probe, both confirmed inert here).
     * Same bounded-timeout-on-a-throwaway-thread pattern as [readBatch] -- see [setCap]'s doc for why a dead
     * daemon must never block the caller. Returns `null` (never throws) on a dead/not-yet-started daemon or a
     * timeout, so callers fall back to a direct `xsu` read exactly like [readBatch]'s callers do.
     */
    fun readSetting(key: String): String? {
        if (!running) return null
        val result = java.util.concurrent.atomic.AtomicReference<String?>(null)
        val worker = Thread {
            try {
                FileOutputStream(fifoInPath).use { it.write(("GETSETTING $key\n").toByteArray()) }
                val line = File(fifoOutPath).inputStream().bufferedReader().use { it.readLine() }
                if (line != null) result.set(line)
            } catch (e: Exception) {
                // leave result null
            }
        }
        worker.isDaemon = true
        worker.start()
        worker.join(READ_BATCH_TIMEOUT_MS)
        return result.get()
    }

    /** Tells the daemon to exit, clean up its FIFO, and stop its background cap-poll loop. Safe to call even if
     * never started. A hard process kill (no [stop] call reaching the daemon, e.g. OOM) leaves the cap-poll
     * loop running as an orphan alongside the FIFO reader -- the same pre-existing lifecycle gap [start]'s doc
     * already covers for the daemon itself, not a new one; harmless (near-zero CPU, a slowly-growing but
     * bounded-rate file) until the next reboot. */
    fun stop() {
        if (!running) return
        try {
            FileOutputStream(fifoInPath).use { it.write("STOP\n".toByteArray()) }
        } catch (_: Exception) {
            // daemon already gone -- fine, nothing left to signal
        }
        running = false
    }

    /** Shared bounded-timeout FIFO line write backing [setCap] and [log] -- see [setCap]'s doc for why the
     * timeout/daemon-thread pattern is required (a dead daemon must never block the caller). */
    private fun sendLine(line: String): Boolean {
        val succeeded = java.util.concurrent.atomic.AtomicBoolean(false)
        val writer = Thread {
            try {
                FileOutputStream(fifoInPath).use { it.write("$line\n".toByteArray()) }
                succeeded.set(true)
            } catch (e: Exception) {
                // leave succeeded = false
            }
        }
        writer.isDaemon = true
        writer.start()
        writer.join(SET_CAP_TIMEOUT_MS)
        return succeeded.get()
    }

    companion object {
        private const val ASSET_NAME = "pulse_daemon.sh"

        // Healthy round trips are sub-millisecond (fifo-daemon-test.sh); this is generous slack for
        // a live daemon while still cutting a dead one's stall down from "forever" to "unnoticeable."
        private const val SET_CAP_TIMEOUT_MS = 500L

        // A batch read is a write + up to ~15 `cat`s + a write-back, all plain shell builtins already
        // running as root -- still comfortably sub-millisecond in practice, but a bit more shell work
        // than a single setCap(), so this gets a bit more slack than SET_CAP_TIMEOUT_MS.
        private const val READ_BATCH_TIMEOUT_MS = 750L
    }
}
