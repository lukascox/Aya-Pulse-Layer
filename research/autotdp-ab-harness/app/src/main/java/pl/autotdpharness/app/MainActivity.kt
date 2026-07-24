package pl.autotdpharness.app

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.io.File
import java.util.Date

private const val TAG = "XSU_BENCH"
private const val RESULT_FILE_NAME = "xsu_benchmark_result.txt"
private const val SDCARD_RESULT_PATH = "/sdcard/xsu_benchmark_result.txt"
private const val HW_PROFILE_FILE_NAME = "pulsefit_hw_profile.txt"
private const val SDCARD_HW_PROFILE_PATH = "/sdcard/pulsefit_hw_profile.txt"

private const val CPU_POLICY0_PATH = "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq"
private const val CPU_POLICY0_ECO = 1344000L
private const val CPU_POLICY0_GOVERNOR_PATH = "/sys/devices/system/cpu/cpufreq/policy0/scaling_governor"
private const val GPU_MAX_PWRLEVEL_PATH = "/sys/class/kgsl/kgsl-3d0/max_pwrlevel"
private const val GPU_HIGH_PWRLEVEL = 6L
private const val GPU_BUSY_PATH = "/sys/class/kgsl/kgsl-3d0/gpubusy"
private const val PLAUSIBLE_MIN = 0L
private const val PLAUSIBLE_MAX = 10_000_000L
private const val LATENCY_ITERATIONS = 25
private const val FPS_SAMPLE_COUNT = 30
private const val FPS_SAMPLE_INTERVAL_MS = 3000L
private const val SNAPSHOT_TIMEOUT_SEC = 4L

private const val BG_TEST_LOG = "/sdcard/xsu_bgtest.log"

private const val SESSION_INTERVAL_MS = 2000L // matches pulse_lite_v3.7.sh's own loop cadence

class MainActivity : AppCompatActivity() {

    private lateinit var txtResult: TextView
    private val log = StringBuilder()
    private lateinit var localLogFile: File

    private var sessionJob: Job? = null
    private var activeSession: AbSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtResult = findViewById(R.id.txtResult)
        localLogFile = File(filesDir, RESULT_FILE_NAME)
        localLogFile.writeText("")

        val btnCapability = findViewById<Button>(R.id.btnRunCapabilityTests)
        val btnBaseline = findViewById<Button>(R.id.btnStartBaseline)
        val btnAutoTdp = findViewById<Button>(R.id.btnStartAutoTdp)
        val btnStop = findViewById<Button>(R.id.btnStopSession)

        btnCapability.setOnClickListener {
            btnCapability.isEnabled = false
            lifecycleScope.launch(Dispatchers.IO) {
                runCapabilityTests()
                runOnUiThread { btnCapability.isEnabled = true }
            }
        }

        btnBaseline.setOnClickListener { startSession(SessionMode.BASELINE, btnBaseline, btnAutoTdp, btnStop) }
        btnAutoTdp.setOnClickListener { startSession(SessionMode.AUTOTDP, btnBaseline, btnAutoTdp, btnStop) }
        btnStop.setOnClickListener { stopSession(btnBaseline, btnAutoTdp, btnStop) }
    }

    // -------------------------------------------------------------------
    // Shared logging (Tests 1-9 prose log)
    // -------------------------------------------------------------------

    private fun appendLog(line: String) {
        Log.d(TAG, line)
        log.appendLine(line)
        localLogFile.appendText(line + "\n")
        runOnUiThread { txtResult.text = log.toString() }
    }

    private fun syncResultToSdcard() {
        val r = XsuShell.exec("cat '${localLogFile.absolutePath}' > $SDCARD_RESULT_PATH")
        if (r.exitCode != 0) Log.w(TAG, "sync to /sdcard failed: exit=${r.exitCode} err=${r.stderr}")
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

    data class LatencyStats(val label: String, val n: Int, val avgMs: Double, val minMs: Long, val maxMs: Long, val p95Ms: Long) {
        override fun toString() = "$label: n=$n avg=${"%.1f".format(avgMs)}ms min=${minMs}ms max=${maxMs}ms p95=${p95Ms}ms"
    }

    private fun computeStats(label: String, samples: List<Long>): LatencyStats {
        if (samples.isEmpty()) return LatencyStats(label, 0, 0.0, 0, 0, 0)
        val sorted = samples.sorted()
        val p95 = sorted[(0.95 * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)]
        return LatencyStats(label, samples.size, samples.average(), sorted.first(), sorted.last(), p95)
    }

    // -------------------------------------------------------------------
    // Tests 1-9
    // -------------------------------------------------------------------

    private fun runCapabilityTests() {
        log.clear()
        appendLog("===== XSU BENCHMARK SUITE (Tests 1-9) =====")
        appendLog("time=${Date()} applicationId=${BuildConfig.APPLICATION_ID}")
        appendLog("")

        appendLog("--- TEST 1: xsu availability (regression check) ---")
        val idRes = XsuShell.exec("id")
        appendLog("cmd=\"id\" exit=${idRes.exitCode} elapsed=${idRes.elapsedMs}ms error=${idRes.error ?: "-"}")
        appendLog("stdout: ${idRes.stdout}")
        val rootOk = idRes.looksLikeRoot
        appendLog(if (rootOk) "TEST 1 PASS: uid=0 confirmed." else "TEST 1 CRITICAL FAIL -- aborting, everything below depends on this.")
        syncResultToSdcard()
        if (!rootOk) {
            appendLog("===== BENCHMARK ABORTED (TEST 1 FAILED) =====")
            syncResultToSdcard()
            return
        }
        appendLog("")

        appendLog("--- TEST 2: CPU sysfs write+verify ($CPU_POLICY0_PATH) ---")
        val cpuOriginal = readSysfsLong(CPU_POLICY0_PATH)
        appendLog("original_value=$cpuOriginal")
        if (cpuOriginal != null) {
            val writeRes = XsuShell.exec("echo $CPU_POLICY0_ECO > $CPU_POLICY0_PATH; cat $CPU_POLICY0_PATH")
            appendLog("write+verify: elapsed=${writeRes.elapsedMs}ms stdout=${writeRes.stdout}")
            val ok = writeRes.stdout.trim().toLongOrNull() == CPU_POLICY0_ECO
            val restoreRes = XsuShell.exec("echo $cpuOriginal > $CPU_POLICY0_PATH; cat $CPU_POLICY0_PATH")
            appendLog("restore: got=${restoreRes.stdout.trim()}")
            appendLog("TEST 2 RESULT: ${if (ok) "PASS" else "FAIL"}")
        } else {
            appendLog("TEST 2 SKIPPED: could not parse a plausible original value.")
        }
        syncResultToSdcard()
        appendLog("")

        appendLog("--- TEST 3: GPU sysfs write+verify ($GPU_MAX_PWRLEVEL_PATH) ---")
        val gpuOriginal = readSysfsLong(GPU_MAX_PWRLEVEL_PATH)
        appendLog("original_value=$gpuOriginal")
        if (gpuOriginal != null) {
            val writeRes = XsuShell.exec("echo $GPU_HIGH_PWRLEVEL > $GPU_MAX_PWRLEVEL_PATH; cat $GPU_MAX_PWRLEVEL_PATH")
            appendLog("write+verify: elapsed=${writeRes.elapsedMs}ms stdout=${writeRes.stdout}")
            val ok = writeRes.stdout.trim().toLongOrNull() == GPU_HIGH_PWRLEVEL
            val restoreRes = XsuShell.exec("echo $gpuOriginal > $GPU_MAX_PWRLEVEL_PATH; cat $GPU_MAX_PWRLEVEL_PATH")
            appendLog("restore: got=${restoreRes.stdout.trim()}")
            appendLog("TEST 3 RESULT: ${if (ok) "PASS -- xsu app-channel CAN write kgsl nodes." else "FAIL."}")
        } else {
            appendLog("TEST 3 SKIPPED: could not parse a plausible original value.")
        }
        syncResultToSdcard()
        appendLog("")

        appendLog("--- TEST 4: GPU busy node ($GPU_BUSY_PATH) ---")
        val lsRes = XsuShell.exec("ls /sys/class/kgsl/kgsl-3d0/ | grep -i busy")
        appendLog("sanity ls: ${lsRes.stdout}")
        val r1 = XsuShell.exec("cat $GPU_BUSY_PATH")
        appendLog("read1: elapsed=${r1.elapsedMs}ms raw=\"${r1.stdout}\" pct=${computeGpuBusyPct(r1.stdout)}")
        Thread.sleep(500)
        val r2 = XsuShell.exec("cat $GPU_BUSY_PATH")
        appendLog("read2 (+500ms): elapsed=${r2.elapsedMs}ms raw=\"${r2.stdout}\" pct=${computeGpuBusyPct(r2.stdout)}")
        appendLog(
            "WARNING: raw gpubusy is confirmed to wrap/reset between reads on this kernel " +
                "(values as extreme as -2718%% observed in prior diagnostic runs) -- AutoTdpController " +
                "must NOT base tier decisions on a single raw read; needs smoothing or a secondary signal."
        )
        syncResultToSdcard()
        appendLog("")

        appendLog("--- TEST 5: FPS pipeline, $FPS_SAMPLE_COUNT samples x ${FPS_SAMPLE_INTERVAL_MS}ms ---")
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
            } else Triple("n/a", 0, "n/a (no layer matched)")
            val pipelineElapsed = (System.nanoTime() - t0) / 1_000_000
            pipelineLatencies.add(pipelineElapsed)

            val snapCmd = buildString {
                for (p in listOf(0, 2, 5, 7)) {
                    append("echo P${p}_GOV=\$(cat /sys/devices/system/cpu/cpufreq/policy$p/scaling_governor); ")
                    append("echo P${p}_FREQ=\$(cat /sys/devices/system/cpu/cpufreq/policy$p/scaling_cur_freq); ")
                }
                append("echo GPU_CLK=\$(cat /sys/class/kgsl/kgsl-3d0/gpuclk); ")
                append("echo GPU_BUSY=\$(cat /sys/class/kgsl/kgsl-3d0/gpubusy)")
            }
            val snapRes = XsuShell.exec(snapCmd, timeoutSec = SNAPSHOT_TIMEOUT_SEC)
            snapshotLatencies.add(snapRes.elapsedMs)
            val tagged = FpsPipeline.parseTaggedLines(snapRes.stdout)

            appendLog("[sample $i] pkg=$pkgShort layer=$matchedLayer refresh_ns=$refreshNs frames=$frameCount fps=$fps pipeline_ms=$pipelineElapsed snapshot_ms=${snapRes.elapsedMs} snapshot_error=${snapRes.error ?: "-"}")
            appendLog("  focus_raw: $focusLine")
            appendLog("  p0:gov=${tagged["P0_GOV"]},freq=${tagged["P0_FREQ"]} p2:gov=${tagged["P2_GOV"]},freq=${tagged["P2_FREQ"]} p5:gov=${tagged["P5_GOV"]},freq=${tagged["P5_FREQ"]} p7:gov=${tagged["P7_GOV"]},freq=${tagged["P7_FREQ"]} | gpu_freq_hz=${tagged["GPU_CLK"]} gpu_busy_raw=${tagged["GPU_BUSY"]}")

            if (i % 5 == 0) syncResultToSdcard()
            Thread.sleep(FPS_SAMPLE_INTERVAL_MS)
        }
        syncResultToSdcard()
        appendLog("")

        appendLog("--- TEST 6: full CPU frequency table dump -> $SDCARD_HW_PROFILE_PATH ---")
        // v2 fix: one call per policy (not one 24-line batched call) -- see HwProfile.kt
        // comment. Run 1 got EVERY field back as "?" with no raw exec diagnostics logged
        // to explain why; this version logs exit/error/elapsed for each policy's call
        // individually so a repeat failure is directly attributable, not just visible
        // as more question marks.
        val cpuTagged = mutableMapOf<String, String>()
        for (p in HwProfile.POLICIES) {
            val res = XsuShell.exec(HwProfile.buildCpuProfileCommandForPolicy(p), timeoutSec = 8)
            appendLog("policy$p query: exit=${res.exitCode} elapsed=${res.elapsedMs}ms error=${res.error ?: "-"} stdout_len=${res.stdout.length}")
            if (res.stdout.isBlank() && res.error != null) {
                appendLog("  ** policy$p query produced NO output (${res.error}) -- fields for this policy will show as '?' below **")
            }
            cpuTagged.putAll(FpsPipeline.parseTaggedLines(res.stdout))
        }
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
        appendLog(if (hwCopyRes.exitCode == 0) "TEST 6 RESULT: PASS -- written to $SDCARD_HW_PROFILE_PATH" else "TEST 6 RESULT: FAIL copying to /sdcard")
        syncResultToSdcard()
        appendLog("")

        appendLog("--- TEST 7: governor write+verify+restore (policy0 only) ---")
        val availableGovernors = HwProfile.policy0AvailableGovernors(cpuTagged)
        appendLog("policy0 available governors (from TEST 6): $availableGovernors")
        val govOriginal = XsuShell.exec("cat $CPU_POLICY0_GOVERNOR_PATH").stdout.trim()
        appendLog("original_governor=$govOriginal")
        val testGovernor = listOf("performance", "schedutil").firstOrNull { it in availableGovernors && it != govOriginal }
            ?: availableGovernors.firstOrNull { it != govOriginal }
        if (govOriginal.isNotEmpty() && testGovernor != null) {
            val writeRes = XsuShell.exec("echo $testGovernor > $CPU_POLICY0_GOVERNOR_PATH; cat $CPU_POLICY0_GOVERNOR_PATH")
            val ok = writeRes.stdout.trim() == testGovernor
            appendLog("write+verify: test_governor=$testGovernor got=${writeRes.stdout.trim()} -> ${if (ok) "OK" else "FAIL"}")
            val restoreRes = XsuShell.exec("echo $govOriginal > $CPU_POLICY0_GOVERNOR_PATH; cat $CPU_POLICY0_GOVERNOR_PATH")
            appendLog("restore: got=${restoreRes.stdout.trim()}")
            appendLog("TEST 7 RESULT: ${if (ok) "PASS -- scaling_governor is writable via xsu app-channel." else "FAIL."}")
        } else {
            appendLog("TEST 7 SKIPPED: no safe original/test governor determined.")
        }
        syncResultToSdcard()
        appendLog("")

        appendLog("--- TEST 8: fan RPM / power-draw node discovery ---")
        val discRes = XsuShell.exec(PowerFanProbe.buildDiscoveryCommand(), timeoutSec = 8)
        val blocks = PowerFanProbe.parseBlockTags(discRes.stdout)
        val disc = PowerFanProbe.parseDiscovery(blocks)
        appendLog("hwmon entries: ${disc.hwmonEntries.ifEmpty { listOf("(none found)") }}")
        appendLog("cooling devices: ${disc.coolingDevices.ifEmpty { listOf("(none found)") }}")
        appendLog("fan-name search hits: ${disc.fanSearchHits.ifEmpty { listOf("(none found)") }}")
        appendLog("power_supply entries: ${disc.powerSupplyEntries}")
        appendLog("battery current_now=${disc.batteryCurrentNow ?: "n/a"} voltage_now=${disc.batteryVoltageNow ?: "n/a"} power_now=${disc.batteryPowerNow ?: "n/a"} (whole-device proxy, NOT per-chip)")
        appendLog("debugfs mounted: ${disc.debugfsMounted}")
        if (disc.fanLikeCoolingDevices.isNotEmpty()) {
            val readCmd = PowerFanProbe.buildFanReadCommand(disc.fanLikeCoolingDevices.map { it.first })
            val readRes = XsuShell.exec(readCmd)
            val readTagged = FpsPipeline.parseTaggedLines(readRes.stdout)
            appendLog("fan-like cooling device readings: $readTagged")
            appendLog("TEST 8 RESULT: fan signal FOUND (cooling_device step index, NOT RPM) -- ${disc.fanLikeCoolingDevices}")
        } else {
            appendLog("TEST 8 RESULT: no fan signal found (expected on a 'user' build) -- fallback is 'n/a' for the A/B harness's fan_signal column.")
        }
        syncResultToSdcard()
        appendLog("")

        appendLog("--- TEST 9: backgrounded-process persistence (blocking prerequisite for A/B harness) ---")
        val launchRes = XsuShell.exec(buildBgTestLaunchCommand(), timeoutSec = 8)
        appendLog("launch: elapsed=${launchRes.elapsedMs}ms exit=${launchRes.exitCode} error=${launchRes.error ?: "-"} (expect FAST return if backgrounding works)")
        Thread.sleep(10_000)
        val check1 = XsuShell.exec("cat $BG_TEST_LOG")
        val lines1 = check1.stdout.lines().filter { it.isNotBlank() }.size
        Thread.sleep(3000)
        val check2 = XsuShell.exec("cat $BG_TEST_LOG")
        val lines2 = check2.stdout.lines().filter { it.isNotBlank() }.size
        val pgrepRes = XsuShell.exec("pgrep -f xsu_bgtest")
        appendLog("check1 (+10s): $lines1 lines in log")
        appendLog("check2 (+13s): $lines2 lines in log")
        appendLog("pgrep -f xsu_bgtest: ${if (pgrepRes.stdout.isNotBlank()) "FOUND pid(s): ${pgrepRes.stdout}" else "NOT FOUND"}")
        val persisted = lines2 > lines1 && pgrepRes.stdout.isNotBlank()
        appendLog(
            if (persisted) "TEST 9 RESULT: PASS -- backgrounded process persists independently of the launching xsu call. A/B harness can launch pulse_lite.sh the same way."
            else "TEST 9 RESULT: FAIL -- background process did NOT persist. A/B harness's AutoTDP mode needs a polling-loop fallback instead of a true background daemon."
        )
        XsuShell.exec("pkill -f xsu_bgtest; rm -f $BG_TEST_LOG")
        syncResultToSdcard()
        appendLog("")

        appendLog("--- LATENCY BENCHMARK ($LATENCY_ITERATIONS iterations per category unless reused from TEST 5) ---")
        val catSamples = mutableListOf<Long>()
        repeat(LATENCY_ITERATIONS) { catSamples.add(XsuShell.exec("cat /sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq").elapsedMs) }
        appendLog(computeStats("simple_read_cat", catSamples).toString())

        val wvOriginal = readSysfsLong(CPU_POLICY0_PATH)
        if (wvOriginal != null) {
            val wvSamples = mutableListOf<Long>()
            repeat(LATENCY_ITERATIONS) { wvSamples.add(XsuShell.exec("echo $wvOriginal > $CPU_POLICY0_PATH; cat $CPU_POLICY0_PATH").elapsedMs) }
            appendLog(computeStats("write_verify_combined", wvSamples).toString())
        }

        val dumpsysSamples = mutableListOf<Long>()
        repeat(LATENCY_ITERATIONS) { dumpsysSamples.add(XsuShell.exec("dumpsys activity activities").elapsedMs) }
        appendLog(computeStats("single_dumpsys_call", dumpsysSamples).toString())

        appendLog(computeStats("full_fps_pipeline_per_sample", pipelineLatencies).toString())
        appendLog(computeStats("batched_snapshot_call", snapshotLatencies).toString())
        appendLog("(both categories above reuse per-sample timings from TEST 5, see FINDINGS.md in xsu-capability-probe for why)")

        appendLog("")
        appendLog("===== CAPABILITY TESTS COMPLETE =====")
        syncResultToSdcard()
    }

    private fun buildBgTestLaunchCommand(): String =
        "sh -c 'i=0; while [ \$i -lt 120 ]; do date +%s >> $BG_TEST_LOG; i=\$((i+1)); sleep 1; done &'"

    // -------------------------------------------------------------------
    // A/B comparison harness
    // -------------------------------------------------------------------

    private fun startSession(mode: SessionMode, btnBaseline: Button, btnAutoTdp: Button, btnStop: Button) {
        btnBaseline.isEnabled = false
        btnAutoTdp.isEnabled = false
        btnStop.isEnabled = true
        log.clear()
        appendSessionLog("Starting session: ${mode.label}")

        sessionJob = lifecycleScope.launch(Dispatchers.IO) {
            val zoneRes = XsuShell.exec(ThermalZones.buildResolveCommand(), timeoutSec = 8)
            val zones = ThermalZones.parseResolveOutput(zoneRes.stdout)
            appendSessionLog("Resolved zones: cpu=${zones.cpuZones.size} gpu=${zones.gpuZones.size} skin=${zones.skinZone != null} battery=${zones.batteryZone != null}")

            val discRes = XsuShell.exec(PowerFanProbe.buildDiscoveryCommand(), timeoutSec = 8)
            val disc = PowerFanProbe.parseDiscovery(PowerFanProbe.parseBlockTags(discRes.stdout))
            val fanNode = disc.fanLikeCoolingDevices.firstOrNull()?.let {
                FanNode(it.first, "/sys/class/thermal/${it.first}/cur_state", "cooling_device_step(${it.first})")
            }
            appendSessionLog("Fan signal: ${fanNode?.label ?: "not_found"}")

            val session = AbSession(mode, zones, fanNode, filesDir)
            activeSession = session
            appendSessionLog("CSV: ${session.sdcardCsvPath}")

            if (mode == SessionMode.AUTOTDP) {
                val launchRes = XsuShell.exec(PulseLiteV37.buildLaunchCommand(), timeoutSec = 8)
                appendSessionLog("Launched pulse_lite.sh (elapsed=${launchRes.elapsedMs}ms, error=${launchRes.error ?: "-"})")
            }

            var idx = 0
            while (isActive) {
                val timing = session.sampleOnce()
                appendSessionLog("[sample $idx] mode=${mode.label} pipeline_ms=${timing.pipelineMs} snapshot_ms=${timing.snapshotMs}")
                if (idx % 10 == 0) session.syncToSdcard()
                idx++
                Thread.sleep(SESSION_INTERVAL_MS)
            }
        }
    }

    private fun stopSession(btnBaseline: Button, btnAutoTdp: Button, btnStop: Button) {
        btnStop.isEnabled = false
        val session = activeSession
        val job = sessionJob
        lifecycleScope.launch(Dispatchers.IO) {
            job?.cancelAndJoin()
            session?.syncToSdcard()

            if (session != null && session.mode == SessionMode.AUTOTDP) {
                appendSessionLog("Stopping pulse_lite.sh (sentinel)...")
                XsuShell.exec(PulseLiteV37.buildStopCommand())
                Thread.sleep(4000)
                val p0 = XsuShell.exec("cat /sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq").stdout.trim().toLongOrNull()
                val p2 = XsuShell.exec("cat /sys/devices/system/cpu/cpufreq/policy2/scaling_max_freq").stdout.trim().toLongOrNull()
                val p5 = XsuShell.exec("cat /sys/devices/system/cpu/cpufreq/policy5/scaling_max_freq").stdout.trim().toLongOrNull()
                val p7 = XsuShell.exec("cat /sys/devices/system/cpu/cpufreq/policy7/scaling_max_freq").stdout.trim().toLongOrNull()
                val gpu = XsuShell.exec("cat /sys/class/kgsl/kgsl-3d0/max_pwrlevel").stdout.trim().toLongOrNull()
                val restored = p0 == PulseLiteV37.CPU0_STOCK && p2 == PulseLiteV37.CPU2_STOCK &&
                    p5 == PulseLiteV37.CPU5_STOCK && p7 == PulseLiteV37.CPU7_STOCK && gpu == PulseLiteV37.GPU_UNCAP
                appendSessionLog("p0=$p0 p2=$p2 p5=$p5 p7=$p7 gpu=$gpu")
                appendSessionLog(if (restored) "STOCK RESTORE: PASS" else "STOCK RESTORE: FAIL -- do not trust the daemon's own exit trap blindly, check manually.")
            }

            appendSessionLog("Session stopped. CSV: ${session?.sdcardCsvPath}")
            activeSession = null
            sessionJob = null
            runOnUiThread {
                btnBaseline.isEnabled = true
                btnAutoTdp.isEnabled = true
            }
        }
    }

    private fun appendSessionLog(line: String) {
        Log.d(TAG, line)
        log.appendLine(line)
        runOnUiThread { txtResult.text = log.toString() }
    }
}
