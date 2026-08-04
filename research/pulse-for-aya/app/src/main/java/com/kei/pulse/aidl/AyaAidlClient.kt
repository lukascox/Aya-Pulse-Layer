package com.kei.pulse.aidl

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Parcel
import com.kei.pulse.model.DeviceSupport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Hand-rolled reconstruction of `com.ayaneo.gamewindow`'s undocumented AIDL wire protocol --
 * there is no `.aidl` file (it's an internal, unpublished interface), so this mirrors exactly
 * what jadx showed in the decompiled `AyaAidlInterface.Stub`/`AyaAidlCallback.Stub` classes (see
 * `research/ayaspace-teardown/evidence/aidl/` and `research/aya-gamewindows-teardown/evidence/
 * aidl/` in this repo), using raw `Parcel`/`Binder.transact()` instead of AIDL codegen. Copied
 * verbatim from `research/aidl-bind-spike/app/.../AidlProtocol.kt` (already confirmed working
 * repeatably on this exact hardware) -- transaction codes and interface tokens are taken directly
 * from the decompiled source, not guessed, so this is deliberately NOT "cleaned up" or changed.
 */
private object AyaAidlProtocol {
    const val SERVICE_PACKAGE = "com.ayaneo.gamewindow"
    const val SERVICE_CLASS = "com.ayaneo.gamewindow.utils.aidl.AyaAidlService"

    const val SERVICE_INTERFACE_TOKEN = "com.ayaneo.gamewindow.AyaAidlInterface"
    const val CALLBACK_INTERFACE_TOKEN = "com.ayaneo.gamewindow.AyaAidlCallback"

    const val TXN_SEND = 1
    const val TXN_REGISTER_CALLBACK = 2
    const val TXN_UNREGISTER_CALLBACK = 3
    const val CALLBACK_TXN_DELIVER = 1

    /** Mirrors `AyaAidlInterface.Stub.Proxy.send(String)`. */
    fun send(service: IBinder, message: String) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(SERVICE_INTERFACE_TOKEN)
            data.writeString(message)
            service.transact(TXN_SEND, data, reply, 0)
            reply.readException()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /** Mirrors `AyaAidlInterface.Stub.Proxy.k(AyaAidlCallback)` -- register callback. */
    fun registerCallback(service: IBinder, callback: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(SERVICE_INTERFACE_TOKEN)
            data.writeStrongBinder(callback)
            service.transact(TXN_REGISTER_CALLBACK, data, reply, 0)
            reply.readException()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /** Mirrors `AyaAidlInterface.Stub.Proxy.q(AyaAidlCallback)` -- unregister callback. */
    fun unregisterCallback(service: IBinder, callback: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(SERVICE_INTERFACE_TOKEN)
            data.writeStrongBinder(callback)
            service.transact(TXN_UNREGISTER_CALLBACK, data, reply, 0)
            reply.readException()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}

/**
 * Our side of the callback channel -- gamewindow transacts INTO this (code 1 = `v(String)` in the
 * decompiled `AyaAidlCallback.Stub`) once registered, delivering `"msg_type_register:<id>"` on
 * connect and any unsolicited messages afterward. A plain `Binder` subclass is enough; we don't
 * need `attachInterface`/`IInterface` since gamewindow only ever calls `transact()` on us, it
 * never calls `queryLocalInterface()` (that would only matter same-process, we're cross-process).
 */
private class AyaAidlCallbackStub(private val onMessage: (String) -> Unit) : Binder() {
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == IBinder.INTERFACE_TRANSACTION) {
            reply?.writeString(AyaAidlProtocol.CALLBACK_INTERFACE_TOKEN)
            return true
        }
        if (code == AyaAidlProtocol.CALLBACK_TXN_DELIVER) {
            data.enforceInterface(AyaAidlProtocol.CALLBACK_INTERFACE_TOKEN)
            val msg = data.readString() ?: ""
            reply?.writeNoException()
            onMessage(msg)
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }
}

/**
 * Step 1 of the AIDL migration (`STATUS.md`'s 2026-07-27 Minecraft-crash investigation, "sensible
 * mitigation" discussion): a clean, isolated client for `com.ayaneo.gamewindow`'s `AyaAidlService`,
 * ported from the already-validated `research/aidl-bind-spike` (confirmed working repeatably on
 * this exact hardware, from a *different* app package). **Not wired into any live control path
 * yet** -- see `MainActivity`'s debug-only verification hook, which only binds + registers (never
 * calls [sendPerformanceMode], so it can't change device behavior just by launching a debug
 * build). The eventual point, once verified, is to replace `ForegroundAppMonitorService`'s
 * `xsu`-based governor reset on a foreground-app change with a `send()` here instead -- removing
 * one of our own `xsu` connections from the exact moment AYASpace's own launch hooks are *also*
 * hitting `xsu`, per the `xsu_conn_handler` stack-overflow findings in `STATUS.md`. That's step 2,
 * deliberately not done here.
 */
class AyaAidlClient(private val context: Context) {

    sealed class BindResult {
        data class Ready(val clientId: String) : BindResult()
        data class Failed(val reason: String) : BindResult()
    }

    private var serviceBinder: IBinder? = null
    private var clientId: String? = null
    private var callbackStub: AyaAidlCallbackStub? = null
    private var connection: ServiceConnection? = null
    @Volatile private var lastFanMode: String? = null

    /**
     * Binds and registers our callback; [onReady] fires once (on whichever thread the callback
     * transaction lands on -- Binder's own thread pool, not necessarily the caller's) with the
     * outcome. Safe to call once per instance; call [unbind] when done, don't reuse after that.
     */
    fun bind(onReady: (BindResult) -> Unit) {
        // apl glue gate (2026-08-03): second chokepoint alongside RootExec. Non-AYANEO hardware was
        // never reachable here anyway -- the Intent names com.ayaneo.gamewindow explicitly and simply
        // fails to bind where that package is absent -- so what this actually stops is AYANEO's own
        // MediaTek handhelds. These opcodes were reconstructed from ONE firmware image, and this
        // project has already documented an input-validation gap in this very service that crashes a
        // system app outright. See DeviceSupport for why the SoC family is the test being applied.
        if (!socSupported) {
            onReady(BindResult.Failed(UNSUPPORTED_SOC_MESSAGE))
            return
        }
        val stub = AyaAidlCallbackStub { msg -> handleCallback(msg, onReady) }
        callbackStub = stub
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder == null) {
                    onReady(BindResult.Failed("connected but binder was null"))
                    return
                }
                serviceBinder = binder
                try {
                    AyaAidlProtocol.registerCallback(binder, stub)
                    // onReady fires from handleCallback() once gamewindow's msg_type_register reply
                    // actually arrives -- registerCallback() returning without an exception only
                    // means the transaction was accepted, not that we're ready yet.
                } catch (e: Exception) {
                    onReady(BindResult.Failed("registerCallback threw: ${e.javaClass.simpleName}: ${e.message}"))
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                serviceBinder = null
                clientId = null
            }
        }
        connection = conn

        // No action set on the Intent -- AyaAidlService.onBind() returns the useless plain
        // LocalBinder if action == "com.ayaneo.aidl.server", and the real AIDL binder otherwise
        // (confirmed in aya-gamewindows-teardown/evidence/aidl/AyaAidlService.java). Leaving
        // action unset is deliberate, not an omission -- matches the spike exactly.
        val intent = Intent().apply {
            setClassName(AyaAidlProtocol.SERVICE_PACKAGE, AyaAidlProtocol.SERVICE_CLASS)
        }
        val bound = try {
            context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            onReady(BindResult.Failed("bindService threw: ${e.javaClass.simpleName}: ${e.message}"))
            false
        }
        if (!bound) onReady(BindResult.Failed("bindService returned false"))
    }

    private fun handleCallback(msg: String, onReady: (BindResult) -> Unit) {
        if (msg.startsWith("msg_type_register:")) {
            val id = msg.substringAfter("msg_type_register:")
            clientId = id
            onReady(BindResult.Ready(id))
            return
        }
        // Every other callback is logged in full-length-capped form (2026-07-30): gamewindow pushes a
        // whole-profile config dump here, which is our ONLY readback channel for fan mode (there is no
        // `com_get_*` query command -- confirmed absent from the full AIDL catalog in
        // `research/ayaspace-teardown/FINDINGS.md`, and registering delivers no initial state dump).
        // The log line is also a deliberate open-question probe: it is CONFIRMED this fires as an echo of
        // our own sends, but UNCONFIRMED whether it also fires when the vendor's own UI changes the fan.
        // If a session log ever shows one of these arriving without a preceding send of ours, that
        // question is answered and [lastKnownFanMode] becomes a true drift detector rather than just a
        // confirmation of our own writes. See `research/pulse-for-aya/README.md`'s "Discrete fan mode".
        val fanMode = parseFanModeFromCallback(msg)
        if (fanMode != null) {
            val changed = fanMode != lastFanMode
            lastFanMode = fanMode
            android.util.Log.d("PulseFan", "AIDL callback: fanMode=$fanMode${if (changed) " (CHANGED)" else ""}")
        } else {
            android.util.Log.d("PulseFan", "AIDL callback (no fan state): ${msg.take(80)}")
        }
    }

    /**
     * The vendor's own last-reported fan mode as an AIDL `FAN_MODE_*` string, or `null` if gamewindow
     * hasn't told us yet (nothing arrives on connect -- the cache stays empty until the first callback).
     * `@Volatile` because it's written on a Binder thread pool thread and read from ours.
     */
    fun lastKnownFanMode(): String? = lastFanMode

    /**
     * `modeIndex`: 0=Eco, 1=Balanced, 2=Streaming, 3=Gaming, 4=Max (per
     * `aidl-bind-spike/FINDINGS.md`). **Not called anywhere yet** -- this is the step-2 hook,
     * left here so it exists and is unit-testable in isolation, not invoked automatically.
     */
    fun sendPerformanceMode(modeIndex: Int): Result<Unit> = sendRaw("com_set_performance_mode:$modeIndex")

    /**
     * `mode`: `"POWER_SAVING"`, `"BALANCED"`, or `"HIGH_PERFORMANCE"` -- the exact enum constant
     * names `com.ayaneo.settings`'s own "CPU Scheduling Mode" selector sends (confirmed by reading
     * `PerformanceViewModel.z()`/`CPUSchedulerMode` in `research/ayaspace-teardown`'s decompiled
     * source, 2026-07-27). On this SoC, `BALANCED` resolves to `schedutil` natively -- see
     * `STATUS.md`'s Minecraft-crash investigation for why replacing `pulse-for-aya`'s own
     * `xsu`-based governor write with this is the whole point of this file existing.
     */
    fun sendScheduler(mode: String): Result<Unit> = sendRaw("com_set_performance_scheduler:$mode")

    /** `cpuId`: 0-7 (one physical core, not a cluster/policy id). `frequencyKHz`: same raw unit
     * `scaling_max_freq` uses. Confirmed format from `PerformanceViewModel.x()`/`CpuFragment`. */
    fun sendCpuFrequency(cpuId: Int, frequencyKHz: Int): Result<Unit> =
        sendRaw("com_set_performance_cpu:${cpuId}_$frequencyKHz")

    /** `maxFrequencyHz`: GPU max clock in Hz -- matches `kgsl-3d0/max_gpuclk` and
     * `kgsl-3d0/devfreq/max_freq`'s own unit (confirmed receiver-side in
     * `AyaDevicesUtil$applyGPUFrequency$1`, `aya-gamewindows-teardown/FINDINGS.md` section 2),
     * NOT the truncated MHz the "GPU Limit" slider displays (e.g. `231`-`1050`) -- unverified
     * whether the UI converts before sending or the display is just cosmetic truncation, so
     * confirm with an `xsu` readback before trusting this on a real value. */
    fun sendGpuFrequency(maxFrequencyHz: Int): Result<Unit> = sendRaw("com_set_performance_gpu:$maxFrequencyHz")

    /** Mirrors the "Lock GPU at Max Frequency" switch. Confirmed format from `PerformanceViewModel.G()`. */
    fun sendGpuFixed(isFixed: Boolean): Result<Unit> = sendRaw("com_set_performance_gpu_is_fixed:$isFixed")

    /** `mode`: `"FAN_MODE_OFF"`, `"FAN_MODE_MUTE"`, `"FAN_MODE_BALANCE"`, `"FAN_MODE_TURBO"`, or
     * `"FAN_MODE_CUSTOM"` (per `FAN_MODE` enum in the decompiled `ayasettings` source). Not
     * relevant to the current CPU/GPU mitigation work -- included for completeness since it's the
     * same call shape, not because fan control is in scope here (see `CLAUDE.md`'s hard rule on
     * fan-control paths before ever actually wiring this one up). */
    fun sendFanMode(mode: String): Result<Unit> = sendRaw("com_set_performance_fan:$mode")

    /** `mode`: same 0-4 index as [sendPerformanceMode]. Confirmed format from `PerformanceViewModel.v()`
     * ("Reset to Default" button) -- not yet understood whether this resets just that mode's Custom
     * overrides or something broader; don't call without checking on-device first. */
    fun sendReset(mode: Int): Result<Unit> = sendRaw("com_set_performance_reset:$mode")

    private fun sendRaw(command: String): Result<Unit> {
        // Belt and braces: bind() already refuses, so serviceBinder is null here anyway. Kept because
        // this is the function that actually puts a reverse-engineered opcode on the wire.
        if (!socSupported) return Result.failure(IllegalStateException(UNSUPPORTED_SOC_MESSAGE))
        val binder = serviceBinder ?: return Result.failure(IllegalStateException("not bound"))
        val id = clientId ?: return Result.failure(IllegalStateException("no clientId yet -- bind()'s onReady hasn't fired"))
        return try {
            AyaAidlProtocol.send(binder, "$id:msg_type_performance:$command")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private val socSupported: Boolean by lazy {
        DeviceSupport.isSupportedSoc(Build.SOC_MANUFACTURER, Build.HARDWARE)
    }

    companion object {

        private const val UNSUPPORTED_SOC_MESSAGE =
            "PULSE for AYANEO was only ever run on Qualcomm hardware; refusing to bind the vendor AIDL service here."

        /** The one callback prefix that carries a whole-profile config dump (the only one that has ever
         *  been observed carrying fan state -- `research/aidl-fan-spike/results/run1/`). */
        private const val PERFORMANCE_CALLBACK_PREFIX = "msg_type_performance:com_set_performance_mode:"

        /**
         * Extracts the ACTIVE profile's fan mode from a raw callback message, or `null` for any message
         * that doesn't carry one. The real observed payload (verbatim, trimmed --
         * `research/aidl-fan-spike/results/run1/aidl_fan_spike_result.txt:16`) is:
         *
         * ```
         * msg_type_performance:com_set_performance_mode:{"currentMode":3,"modeConfigurations":{
         *   "0":{...,"fanMode":"FAN_MODE_OFF",...,"lastFanMode":"FAN_MODE_MUTE"},
         *   "1":{...,"fanMode":"FAN_MODE_CUSTOM",...}, ...}}
         * ```
         *
         * i.e. the dump carries ALL five profiles' settings, so the active one has to be selected via the
         * top-level `currentMode` index (note the sibling `lastFanMode` key -- deliberately NOT read;
         * `fanMode` is the live one). Total-function by construction: this runs on a Binder thread, where
         * an exception would propagate into the vendor's own transact, so every parse failure (malformed
         * JSON, missing/renamed key, unexpected type) returns `null` rather than throwing.
         */
        fun parseFanModeFromCallback(msg: String): String? {
            if (!msg.startsWith(PERFORMANCE_CALLBACK_PREFIX)) return null
            return runCatching {
                val root = Json.parseToJsonElement(msg.substringAfter(PERFORMANCE_CALLBACK_PREFIX)).jsonObject
                val current = root["currentMode"]?.jsonPrimitive?.int ?: return null
                root["modeConfigurations"]?.jsonObject
                    ?.get(current.toString())?.jsonObject
                    ?.get("fanMode")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
        }
    }

    /** Releases the binding. Safe to call even if [bind] never succeeded. */
    fun unbind() {
        connection?.let {
            try {
                context.unbindService(it)
            } catch (_: Exception) {
                // not bound / already unbound -- fine to ignore
            }
        }
        connection = null
        serviceBinder = null
        clientId = null
        callbackStub = null
    }
}
