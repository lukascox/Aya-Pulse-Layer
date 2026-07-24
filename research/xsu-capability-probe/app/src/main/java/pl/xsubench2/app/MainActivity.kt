package pl.xsubench2.app

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

private const val TAG = "XSU_BENCH"
private const val RESULT_FILE_NAME = "xsu_benchmark_result.txt"
private const val SDCARD_RESULT_PATH = "/sdcard/xsu_benchmark_result.txt"

private const val HW_PROFILE_FILE_NAME = "pulsefit_hw_profile.txt"
private const val SDCARD_HW_PROFILE_PATH = "/sdcard/pulsefit_hw_profile.txt"

// Known sysfs targets + safe test values, see HARDWARE_PROFILE.md / pulse_lite v3.7.
private const val CPU_POLICY0_PATH = "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq"
private const val CPU_POLICY0_ECO = 1344000L // policy0 ECO cap -- known-valid test value

private const val CPU_POLICY0_GOVERNOR_PATH = "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor"

private const val GPU_MAX_PWRLEVEL_PATH = "/sys/class/kgsl/kgsl-3d0/max_pwrlevel"
private const val GPU_HIGH_PWRLEVEL = 6L // 680MHz, non-extreme test value (not 0=uncapped, not slowest)

private const val GPU_BUSY_PATH = "/sys/class/kgsl/kgsl-3d0/gpubusy"

private const val PLAUSIBLE_MIN = 0L
private const val PLAUSIBLE_MAX = 10_000_000L // loose sanity guard, covers both cpu freq (Hz) and gpu pwrlevel index

private const val LATENCY_ITERATIONS = 25
private const val FPS_SAMPLE_COUNT = 30
private const val FPS_SAMPLE_INTERVAL_MS = 3000L

// Run 1 finding: the batched CPU/GPU snapshot call stalled ~126s once under heavy
// Dolphin load. A few seconds is enough for a call that is normally ~100-150ms;
// anything beyond that is the anomaly we want to catch and log, not wait out.
private const val SNAPSHOT_TIMEOUT_SEC = 4L

data class LatencyStats(
    val label: String,
    val n: Int,
    val avgMs: Double,
    val minMs: Long,
    val maxMs: Long,
    val p95Ms: Long,
) {
    override fun toString(): String =
        "$label: n=$n avg=${"%.1f".format(avgMs)}ms min=${minMs}ms max=${maxMs}ms p95=${p95Ms}ms"
}

class MainActivity : AppCompatActivity() {

    private lateinit var txtResult: TextView
    private val log = StringBuilder()
    private lateinit var localLogFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtResult = findViewById(R.id.txtResult)
        localLogFile = File(filesDir, RESULT_FILE_NAME)
        localLogFile.writeText("") // reset on each app launch

        findViewById<Button>(R.id.btnRun).setOnClickListener { btn ->
            btn.isEnabled = false
            lifecycleScope.launch(Dispatchers.IO) {
                runBenchmarkSuite()
                runOnUiThread { btn.isEnabled = true }
            }
        }
    }

    // -------------------------------------------------------------------
    // Logging: screen + local file (durable immediately, no root needed) + logcat.
    // Local file is synced to /sdcard via root periodically (after each test section),
    // so a crash mid-run loses at most the currently-running section, not everything.
    // -------------------------------------------------------------------

    private fun appendLog(line: String) {
        Log.d(TAG, line)
        log.appendLine(line)
        localLogFile.appendText(line + "\n")
        runOnUiThread { txtResult.text = log.toString() }
    }

    private fun syncToSdcard() {
        val r = XsuShell.exec("cat '${localLogFile.absolutePath}' > $SDCARD_RESULT_PATH")
        if (r.exitCode != 0) {
            Log.w(TAG, "sync to /sdcard failed: exit=${r.exitCode} err=${r.stderr}")
        }
    }

    private fun computeStats(label: String, samplesMs: List<Long>): LatencyStats {
        if (samplesMs.isEmpty()) return LatencyStats(label, 0, 0.0, 0, 0, 0)
        val sorted = samplesMs.sorted()
        val avg = samplesMs.average()
        val p95Index = (0.95 * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return LatencyStats(label, samplesMs.size, avg, sorted.first(), sorted.last(), sorted[p95Index])
    }

    private fun readSysfsLong(path: String): Long? {
        val v = XsuShell.exec("cat $path").stdout.trim().toLongOrNull() ?: return null
        return if (v in PLAUSIBLE_MIN..PLAUSIBLE_MAX) v else null
    }

    private fun computeGpuBusyPct(raw: String): String {
        val parts = raw.trim().split(Regex("\\s+"))
        if (parts.size < 2) return "n/a (parse_error)"
        val busy = parts[0].toLongOrNull() ?: return "n/a (parse_error)"
        val total = parts[1].toLongOrNull() ?: return "n/a (parse_error)"
        if (total <= 0) return "n/a (zero_total)"
        val pct = (busy * 100) / total
        return if (pct < 0 || pct > 100) "n/a (counter_reset)" else pct.toString()
    }

    // -------------------------------------------------------------------
    // Full suite, run sequentially on Dispatchers.IO.
    // -------------------------------------------------------------------

    private fun runBenchmarkSuite() {
        appendLog("===== XSU BENCHMARK SUITE v2 =====")
        appendLog("time=${Date()} applicationId=${BuildConfig.APPLICATION_ID}")
        appendLog("")

        // ---------------- TEST 1: xsu availability (regression check) ----------------
        appendLog("--- TEST 1: xsu availability (regression check) ---")
        val idRes = XsuShell.exec("id")
        appendLog("cmd=\"id\" exit=${idRes.exitCode} elapsed=${idRes.elapsedMs}ms error=${idRes.error ?: "-"}")
        appendLog("stdout: ${idRes.stdout}")
        appendLog("stderr: ${idRes.stderr}")
        val rootOk = idRes.looksLikeRoot
        appendLog(
            if (rootOk) "TEST 1 PASS: uid=0 confirmed."
            else "TEST 1 CRITICAL FAIL: no uid=0 -- aborting remaining tests, everything below depends on this."
        )
        syncToSdcard()
        if (!rootOk) {
            appendLog("===== BENCHMARK ABORTED (TEST 1 FAILED) =====")
            syncToSdcard()
            return
        }
        appendLog("")

        // ---------------- TEST 2: CPU sysfs write+verify (regression, timed) ----------------
        appendLog("--- TEST 2: CPU sysfs write+verify ($CPU_POLICY0_PATH) ---")
        val cpuOriginal = readSysfsLong(CPU_POLICY0_PATH)
        appendLog("original_value=$cpuOriginal")
        var test2Ok = false
        if (cpuOriginal != null) {
            val writeRes = XsuShell.exec("echo $CPU_POLICY0_ECO > $CPU_POLICY0_PATH; cat $CPU_POLICY0_PATH")
            appendLog("write+verify (single round trip): exit=${writeRes.exitCode} elapsed=${writeRes.elapsedMs}ms stdout=${writeRes.stdout} stderr=${writeRes.stderr}")
            val afterWrite = writeRes.stdout.trim().toLongOrNull()
            test2Ok = afterWrite == CPU_POLICY0_ECO
            appendLog("write result: expected=$CPU_POLICY0_ECO got=$afterWrite -> ${if (test2Ok) "OK" else "FAIL"}")
            val restoreRes = XsuShell.exec("echo $cpuOriginal > $CPU_POLICY0_PATH; cat $CPU_POLICY0_PATH")
            val afterRestore = restoreRes.stdout.trim().toLongOrNull()
            appendLog("restore: expected=$cpuOriginal got=$afterRestore -> ${if (afterRestore == cpuOriginal) "OK" else "MISMATCH -- check manually"}")
        } else {
            appendLog("TEST 2 SKIPPED: could not parse a plausible original value, refusing to write blind.")
        }
        appendLog("TEST 2 RESULT: ${if (test2Ok) "PASS" else "FAIL/SKIPPED"}")
        syncToSdcard()
        appendLog("")

        // ---------------- TEST 3: GPU sysfs write+verify ----------------
        appendLog("--- TEST 3: GPU sysfs write+verify ($GPU_MAX_PWRLEVEL_PATH) ---")
        val gpuOriginal = readSysfsLong(GPU_MAX_PWRLEVEL_PATH)
        appendLog("original_value=$gpuOriginal")
        var test3Ok = false
        if (gpuOriginal != null) {
            val writeRes = XsuShell.exec("echo $GPU_HIGH_PWRLEVEL > $GPU_MAX_PWRLEVEL_PATH; cat $GPU_MAX_PWRLEVEL_PATH")
            appendLog("write+verify: exit=${writeRes.exitCode} elapsed=${writeRes.elapsedMs}ms stdout=${writeRes.stdout} stderr=${writeRes.stderr}")
            val afterWrite = writeRes.stdout.trim().toLongOrNull()
            test3Ok = afterWrite == GPU_HIGH_PWRLEVEL
            appendLog("write result: expected=$GPU_HIGH_PWRLEVEL got=$afterWrite -> ${if (test3Ok) "OK" else "FAIL"}")
            val restoreRes = XsuShell.exec("echo $gpuOriginal > $GPU_MAX_PWRLEVEL_PATH; cat $GPU_MAX_PWRLEVEL_PATH")
            val afterRestore = restoreRes.stdout.trim().toLongOrNull()
            appendLog("restore: expected=$gpuOriginal got=$afterRestore -> ${if (afterRestore == gpuOriginal) "OK" else "MISMATCH -- check manually"}")
        } else {
            appendLog("TEST 3 SKIPPED: could not parse a plausible original value, refusing to write blind.")
        }
        appendLog(if (test3Ok) "TEST 3 RESULT: PASS (confirmed again: xsu app-channel can write kgsl nodes)." else "TEST 3 RESULT: FAIL/SKIPPED.")
        syncToSdcard()
        appendLog("")

        // ---------------- TEST 4: GPU busy node ----------------
        appendLog("--- TEST 4: GPU busy node ($GPU_BUSY_PATH) ---")
        val lsRes = XsuShell.exec("ls /sys/class/kgsl/kgsl-3d0/ | grep -i busy")
        appendLog("sanity ls: ${lsRes.stdout}")
        val r1 = XsuShell.exec("cat $GPU_BUSY_PATH")
        appendLog("read1: elapsed=${r1.elapsedMs}ms raw=\"${r1.stdout}\" pct=${computeGpuBusyPct(r1.stdout)}")
        Thread.sleep(500)
        val r2 = XsuShell.exec("cat $GPU_BUSY_PATH")
        appendLog("read2 (+500ms): elapsed=${r2.elapsedMs}ms raw=\"${r2.stdout}\" pct=${computeGpuBusyPct(r2.stdout)}")
        appendLog(
            if (r1.stdout != r2.stdout) "TEST 4 RESULT: counters changed between reads (expected, live signal), node reachable through app channel."
            else "TEST 4 RESULT: counters IDENTICAL between reads -- either device idle right now, or worth a longer soak."
        )
        appendLog(
            "WARNING (per HARDWARE_PROFILE_v6_en.md, 7 diagnostic runs): the raw gpubusy cycle " +
                "counter is confirmed to WRAP/RESET between reads on this kernel, producing values as " +
                "extreme as -2718% -- this is a confirmed, recurring property of the signal, not a rare " +
                "edge case. This app's job is only to confirm the node is reachable through the app " +
                "channel -- it is NOT this app's job to fix the signal. AutoTdpController must NOT base " +
                "tier decisions on a single raw gpubusy read; it needs either a smoothing strategy " +
                "(e.g. rolling median across several samples) or a secondary signal, decided at " +
                "controller-design time, not here."
        )
        syncToSdcard()
        appendLog("")

        // ---------------- TEST 5: FPS pipeline, ported from pulse_lite_diag_v8.sh ----------------
        appendLog("--- TEST 5: FPS pipeline, $FPS_SAMPLE_COUNT samples x ${FPS_SAMPLE_INTERVAL_MS}ms interval ---")
        appendLog("Sampling whatever is on screen NOW. Switch to RetroArch/Eden/Dolphin for a run comparable to prior validation.")
        val pipelineLatencies = mutableListOf<Long>()
        val snapshotLatencies = mutableListOf<Long>()
        for (i in 0 until FPS_SAMPLE_COUNT) {
            val t0 = System.nanoTime()

            val actRes = XsuShell.exec("dumpsys activity activities")
            val (focusLine, pkgShort) = FpsPipeline.parseForegroundPkg(actRes.stdout)

            val listRes = XsuShell.exec("dumpsys SurfaceFlinger --list")
            val matchedLayer = FpsPipeline.matchLayer(pkgShort, listRes.stdout)

            val (refreshNs, frameCount, fps) = if (matchedLayer != "NoMatch") {
                val latRes = XsuShell.exec("dumpsys SurfaceFlinger --latency \"$matchedLayer\"")
                FpsPipeline.parseFps(latRes.stdout)
            } else {
                Triple("n/a", 0, "n/a (no layer matched)")
            }

            // pipeline_ms scopes ONLY the 3 chained dumpsys calls above (a+b+c) -- this
            // is deliberately kept separate from the snapshot call's own timing below,
            // per the run-1 finding that folding them together hid a ~126s stall.
            val pipelineElapsed = (System.nanoTime() - t0) / 1_000_000
            pipelineLatencies.add(pipelineElapsed)

            // v2 fix: short explicit timeout + own logged duration for this call
            // specifically, since THIS is the call that stalled in run 1.
            val snapRes = XsuShell.exec(FpsPipeline.buildSnapshotCommand(), timeoutSec = SNAPSHOT_TIMEOUT_SEC)
            snapshotLatencies.add(snapRes.elapsedMs)
            val snap = FpsPipeline.parseSnapshot(snapRes.stdout)

            appendLog("[sample $i] pkg=$pkgShort layer=$matchedLayer refresh_ns=$refreshNs frames=$frameCount fps=$fps pipeline_ms=$pipelineElapsed snapshot_ms=${snapRes.elapsedMs} snapshot_error=${snapRes.error ?: "-"}")
            appendLog("  focus_raw: $focusLine")
            appendLog("  $snap")
            if (snapRes.error != null) {
                appendLog("  ** snapshot call did not complete within ${SNAPSHOT_TIMEOUT_SEC}s -- this is the anomaly run 1 found under heavy Dolphin load, now bounded and logged instead of silently stalling. **")
            }

            if (i % 5 == 0) syncToSdcard()
            Thread.sleep(FPS_SAMPLE_INTERVAL_MS)
        }
        syncToSdcard()
        appendLog("")

        // ---------------- TEST 6: full CPU frequency table dump ----------------
        appendLog("--- TEST 6: full CPU frequency table dump -> $SDCARD_HW_PROFILE_PATH ---")
        val cpuProfileRes = XsuShell.exec(HwProfile.buildCpuProfileCommand(), timeoutSec = 8)
        appendLog("cpu profile query: exit=${cpuProfileRes.exitCode} elapsed=${cpuProfileRes.elapsedMs}ms error=${cpuProfileRes.error ?: "-"}")
        val cpuTagged = FpsPipeline.parseTaggedLines(cpuProfileRes.stdout)
        val cpuProfileText = HwProfile.formatCpuProfile(cpuTagged)
        appendLog(cpuProfileText)

        val hwProfileContent = buildString {
            appendLine(HwProfile.GPU_REFERENCE_TABLE)
            appendLine()
            appendLine("CPU frequency table (queried live by this app, ${Date()}):")
            append(cpuProfileText)
        }
        val hwProfileLocalFile = File(filesDir, HW_PROFILE_FILE_NAME)
        hwProfileLocalFile.writeText(hwProfileContent)
        val hwCopyRes = XsuShell.exec("cat '${hwProfileLocalFile.absolutePath}' > $SDCARD_HW_PROFILE_PATH")
        appendLog(
            if (hwCopyRes.exitCode == 0) "TEST 6 RESULT: PASS -- hardware profile written to $SDCARD_HW_PROFILE_PATH"
            else "TEST 6 RESULT: FAIL copying to /sdcard (exit=${hwCopyRes.exitCode} err=${hwCopyRes.stderr})"
        )
        syncToSdcard()
        appendLog("")

        // ---------------- TEST 7: governor write+verify+restore (policy0 only) ----------------
        appendLog("--- TEST 7: governor write+verify+restore (policy0 only) ---")
        val availableGovernors = HwProfile.policy0AvailableGovernors(cpuTagged)
        appendLog("policy0 available governors (from TEST 6, not re-queried): $availableGovernors")
        val govOriginal = XsuShell.exec("cat $CPU_POLICY0_GOVERNOR_PATH").stdout.trim()
        appendLog("original_governor=$govOriginal")
        val testGovernor = listOf("performance", "schedutil")
            .firstOrNull { it in availableGovernors && it != govOriginal }
            ?: availableGovernors.firstOrNull { it != govOriginal }

        if (govOriginal.isNotEmpty() && testGovernor != null) {
            val writeRes = XsuShell.exec("echo $testGovernor > $CPU_POLICY0_GOVERNOR_PATH; cat $CPU_POLICY0_GOVERNOR_PATH")
            appendLog("write+verify: test_governor=$testGovernor exit=${writeRes.exitCode} elapsed=${writeRes.elapsedMs}ms stdout=${writeRes.stdout} stderr=${writeRes.stderr}")
            val afterWrite = writeRes.stdout.trim()
            val test7Ok = afterWrite == testGovernor
            appendLog("write result: expected=$testGovernor got=$afterWrite -> ${if (test7Ok) "OK" else "FAIL"}")
            val restoreRes = XsuShell.exec("echo $govOriginal > $CPU_POLICY0_GOVERNOR_PATH; cat $CPU_POLICY0_GOVERNOR_PATH")
            val afterRestore = restoreRes.stdout.trim()
            appendLog("restore: expected=$govOriginal got=$afterRestore -> ${if (afterRestore == govOriginal) "OK" else "MISMATCH -- check manually"}")
            appendLog(if (test7Ok) "TEST 7 RESULT: PASS -- scaling_governor is writable through the app-invoked xsu channel." else "TEST 7 RESULT: FAIL.")
        } else {
            appendLog("TEST 7 SKIPPED: could not determine a safe original governor and/or a distinct test governor from TEST 6's data.")
        }
        syncToSdcard()
        appendLog("")

        // ---------------- Latency benchmark categories ----------------
        // Measured separately per operation type -- NOT averaged together, they are not comparable.
        appendLog("--- LATENCY BENCHMARK ($LATENCY_ITERATIONS iterations per category unless reused from TEST 5) ---")

        // Category A: simple read (cat) -- baseline for Test 1/2-style single reads.
        val catSamples = mutableListOf<Long>()
        repeat(LATENCY_ITERATIONS) {
            catSamples.add(XsuShell.exec("cat /sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq").elapsedMs)
        }
        appendLog(computeStats("simple_read_cat", catSamples).toString())

        // Category B: write+verify combined. Writes back the SAME original value every
        // iteration -- idempotent, zero net effect on device state, still genuinely
        // exercises the write path.
        val wvOriginal = readSysfsLong(CPU_POLICY0_PATH)
        if (wvOriginal != null) {
            val wvSamples = mutableListOf<Long>()
            repeat(LATENCY_ITERATIONS) {
                wvSamples.add(XsuShell.exec("echo $wvOriginal > $CPU_POLICY0_PATH; cat $CPU_POLICY0_PATH").elapsedMs)
            }
            appendLog(computeStats("write_verify_combined", wvSamples).toString())
        } else {
            appendLog("Category B SKIPPED: could not read a safe original value to write back idempotently.")
        }

        // Category C: single dumpsys call alone -- isolates base per-call dumpsys cost.
        val dumpsysSamples = mutableListOf<Long>()
        repeat(LATENCY_ITERATIONS) {
            dumpsysSamples.add(XsuShell.exec("dumpsys activity activities").elapsedMs)
        }
        appendLog(computeStats("single_dumpsys_call", dumpsysSamples).toString())

        // Category D: full FPS pipeline (3 chained dumpsys calls) per-sample cost --
        // reused from TEST 5's own 30 samples above, same reasoning as v1: Test 5
        // already mandates exactly 30 iterations at a fixed interval, so a dedicated
        // second loop here would just re-run the same 90s window for no new data.
        appendLog(computeStats("full_fps_pipeline_per_sample", pipelineLatencies).toString())
        appendLog("(category D reuses per-sample pipeline_ms timings from TEST 5, avoids a redundant second 90s run)")

        // Category E (NEW in v2): batched CPU/GPU snapshot call latency -- this is the
        // call that stalled ~126s in run 1. Also reused from TEST 5's 30 samples rather
        // than a separate loop, for the same reason as category D, AND because
        // reproducing the actual stall condition (heavy emulator load) requires real
        // gameplay on screen during Test 5 anyway -- an isolated dedicated loop with
        // nothing running would not exercise the same conditions.
        appendLog(computeStats("batched_snapshot_call", snapshotLatencies).toString())
        appendLog("(category E reuses per-sample snapshot_ms timings from TEST 5 -- this is the call that stalled in run 1, see step d comments in FpsPipeline.kt)")

        appendLog("")
        appendLog("===== BENCHMARK COMPLETE =====")
        syncToSdcard()
    }
}
