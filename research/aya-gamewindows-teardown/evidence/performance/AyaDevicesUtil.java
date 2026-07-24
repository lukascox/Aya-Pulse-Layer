package com.ayaneo;

import com.ayaneo.devices.AyaDevicesKt;
import com.ayaneo.gamewindow.receiver.PowerScreenReceiverKt;
import com.ayaneo.gamewindow.ui.window.performance.util.CPUSchedulerMode;
import com.ayaneo.gamewindow.ui.window.performance.util.GPUFrequency;
import com.ayaneo.gamewindow.utils.system.AyaShareConfUtilKt;
import java.util.List;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import org.jetbrains.annotations.NotNull;

/* JADX INFO: compiled from: AyaDevicesUtil.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000<\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u0005\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\r\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002J\u001c\u0010\t\u001a\u00020\n2\f\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\r0\f2\u0006\u0010\u000e\u001a\u00020\u000fJ\u000e\u0010\u0010\u001a\u00020\n2\u0006\u0010\u0011\u001a\u00020\u0012J\u000e\u0010\u0013\u001a\u00020\n2\u0006\u0010\u0014\u001a\u00020\u0015J\u0006\u0010\u0016\u001a\u00020\nJ\u0006\u0010\u0017\u001a\u00020\u000fJ\u0006\u0010\u0018\u001a\u00020\u000fJ\u0006\u0010\u0019\u001a\u00020\nJ\u0006\u0010\u001a\u001a\u00020\nJ\b\u0010\u001b\u001a\u00020\nH\u0002J\u0006\u0010\u001c\u001a\u00020\nJ\u0006\u0010\u001d\u001a\u00020\nJ\u000e\u0010\u001e\u001a\u00020\nH\u0086@¢\u0006\u0002\u0010\u001fJ\u0010\u0010 \u001a\u00020\n2\b\b\u0002\u0010!\u001a\u00020\u000fR\u001a\u0010\u0003\u001a\u00020\u0004X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u0005\u0010\u0006\"\u0004\b\u0007\u0010\b¨\u0006\""}, d2 = {"Lcom/ayaneo/AyaDevicesUtil;", "", "()V", "lastLaunchTime", "", "getLastLaunchTime", "()J", "setLastLaunchTime", "(J)V", "applyCPUFrequencies", "", "cpuFrequencies", "", "Lcom/ayaneo/gamewindow/ui/window/performance/util/CPUFrequency;", "isHighPerformance", "", "applyCPUSchedulerMode", "schedulerMode", "Lcom/ayaneo/gamewindow/ui/window/performance/util/CPUSchedulerMode;", "applyGPUFrequency", "gpuFrequency", "Lcom/ayaneo/gamewindow/ui/window/performance/util/GPUFrequency;", "destroySerial", "getMaxPerformanceTipIsShow", "getPerformanceAnimaIsNeeded", "initSerial", "powerOffDevice", "powerOffFan", "powerOnDevice", "serialFeatureSetup", "waitForRgbInitAndRestore", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "writeMaximumPerformance", "needBroadcast", "gamewindow_QCOMRelease"}, k = 1, mv = {1, 9, 0}, xi = 48)
public final class AyaDevicesUtil {

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    @NotNull
    public static final AyaDevicesUtil f4807a = new AyaDevicesUtil();

    /* JADX INFO: renamed from: b, reason: collision with root package name */
    public static long f4808b;

    public static void a(@NotNull List list, boolean z) {
        Intrinsics.e("cpuFrequencies", list);
        BuildersKt.b(GlobalScope.f10144a, Dispatchers.f10129b, null, new AyaDevicesUtil$applyCPUFrequencies$1(list, z, null), 2);
    }

    public static void b(@NotNull CPUSchedulerMode cPUSchedulerMode) {
        Intrinsics.e("schedulerMode", cPUSchedulerMode);
        BuildersKt.b(GlobalScope.f10144a, Dispatchers.f10129b, null, new AyaDevicesUtil$applyCPUSchedulerMode$1(cPUSchedulerMode, null), 2);
    }

    public static void c(@NotNull GPUFrequency gPUFrequency) {
        Intrinsics.e("gpuFrequency", gPUFrequency);
        BuildersKt.b(GlobalScope.f10144a, Dispatchers.f10129b, null, new AyaDevicesUtil$applyGPUFrequency$1(gPUFrequency, null), 2);
    }

    public static boolean d() {
        return Boolean.parseBoolean(AyaShareConfUtilKt.d(AyaShareConfUtilKt.b("aya_performance_max_tip_is_open.conf", String.valueOf(AyaDevicesUtilKt.f4812c)), String.valueOf(!AyaDevicesUtilKt.f4810a)));
    }

    public static void e() {
        if (AyaDevicesUtilKt.r) {
            AyaDevicesKt.f4814a.m0(true, false);
        }
        if (PowerScreenReceiverKt.a()) {
            PowerScreenReceiverKt.b();
        }
        BuildersKt.b(GlobalScope.f10144a, Dispatchers.f10129b, null, new AyaDevicesUtil$powerOnDevice$1(null), 2);
    }

    public static void f() {
        if (AyaDevicesKt.f4814a.U0() || AyaDevicesUtilKt.f4810a) {
            BuildersKt.b(GlobalScope.f10144a, Dispatchers.f10129b, null, new AyaDevicesUtil$serialFeatureSetup$1(null), 2);
        }
    }
}
