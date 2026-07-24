package com.ayaneo;

import com.ayaneo.devices.AyaDevicesKt;
import com.ayaneo.devices.IAyaDevices;
import com.ayaneo.gamewindow.LauncherApp;
import com.ayaneo.gamewindow.ui.window.performance.util.CPUFrequency;
import com.ayaneo.gamewindow.ui.window.performance.util.CPUSchedulerMode;
import com.ayaneo.gamewindow.ui.window.performance.util.ModeConfiguration;
import com.ayaneo.gamewindow.ui.window.performance.util.PerformanceManager;
import com.ayaneo.gamewindow.utils.SettingsKt;
import java.util.List;
import kotlin.Metadata;
import kotlin.NoWhenBranchMatchedException;
import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.collections.CollectionsKt;
import kotlin.collections.EmptyList;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.intrinsics.CoroutineSingletons;
import kotlin.coroutines.jvm.internal.DebugMetadata;
import kotlin.coroutines.jvm.internal.SuspendLambda;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.SourceDebugExtension;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/* JADX INFO: compiled from: AyaDevicesUtil.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000\n\n\u0000\n\u0002\u0010\u0002\n\u0002\u0018\u0002\u0010\u0000\u001a\u00020\u0001*\u00020\u0002H\u008a@"}, d2 = {"<anonymous>", "", "Lkotlinx/coroutines/CoroutineScope;"}, k = 3, mv = {1, 9, 0}, xi = 48)
@DebugMetadata(c = "com.ayaneo.AyaDevicesUtil$applyCPUSchedulerMode$1", f = "AyaDevicesUtil.kt", l = {}, m = "invokeSuspend")
@SourceDebugExtension
public final class AyaDevicesUtil$applyCPUSchedulerMode$1 extends SuspendLambda implements Function2<CoroutineScope, Continuation<? super Unit>, Object> {
    final /* synthetic */ CPUSchedulerMode $schedulerMode;
    int label;

    /* JADX INFO: compiled from: AyaDevicesUtil.kt */
    @Metadata(k = 3, mv = {1, 9, 0}, xi = 48)
    public /* synthetic */ class WhenMappings {

        /* JADX INFO: renamed from: a, reason: collision with root package name */
        public static final /* synthetic */ int[] f4809a;

        static {
            int[] iArr = new int[CPUSchedulerMode.values().length];
            try {
                iArr[CPUSchedulerMode.POWER_SAVING.ordinal()] = 1;
            } catch (NoSuchFieldError unused) {
            }
            try {
                iArr[CPUSchedulerMode.BALANCED.ordinal()] = 2;
            } catch (NoSuchFieldError unused2) {
            }
            try {
                iArr[CPUSchedulerMode.HIGH_PERFORMANCE.ordinal()] = 3;
            } catch (NoSuchFieldError unused3) {
            }
            f4809a = iArr;
        }
    }

    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    public AyaDevicesUtil$applyCPUSchedulerMode$1(CPUSchedulerMode cPUSchedulerMode, Continuation<? super AyaDevicesUtil$applyCPUSchedulerMode$1> continuation) {
        super(2, continuation);
        this.$schedulerMode = cPUSchedulerMode;
    }

    @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
    @NotNull
    public final Continuation<Unit> create(@Nullable Object obj, @NotNull Continuation<?> continuation) {
        return new AyaDevicesUtil$applyCPUSchedulerMode$1(this.$schedulerMode, continuation);
    }

    @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
    @Nullable
    public final Object invokeSuspend(@NotNull Object obj) {
        String str;
        List<CPUFrequency> listA;
        CoroutineSingletons coroutineSingletons = CoroutineSingletons.COROUTINE_SUSPENDED;
        if (this.label != 0) {
            throw new IllegalStateException("call to 'resume' before 'invoke' with coroutine");
        }
        ResultKt.b(obj);
        int i = 0;
        if (AyaDevicesUtilKt.f4813d || AyaDevicesUtilKt.e || AyaDevicesUtilKt.f) {
            int i2 = WhenMappings.f4809a[this.$schedulerMode.ordinal()];
            if (i2 == 1) {
                LauncherApp.e.getClass();
                SettingsKt.h(LauncherApp.Companion.a(), 0, "open_performance_mode");
            } else if (i2 == 2) {
                LauncherApp.e.getClass();
                SettingsKt.h(LauncherApp.Companion.a(), 1, "open_performance_mode");
            } else if (i2 == 3) {
                LauncherApp.e.getClass();
                SettingsKt.h(LauncherApp.Companion.a(), 2, "open_performance_mode");
            }
        } else {
            int i3 = WhenMappings.f4809a[this.$schedulerMode.ordinal()];
            if (i3 == 1) {
                str = "powersave";
            } else if (i3 == 2) {
                str = (!AyaDevicesUtilKt.s && AyaDevicesUtilKt.r) ? "walt" : "schedutil";
            } else {
                if (i3 != 3) {
                    throw new NoWhenBranchMatchedException();
                }
                str = "performance";
            }
            IAyaDevices iAyaDevices = AyaDevicesKt.f4814a;
            iAyaDevices.b("echo " + str + " > /sys/devices/system/cpu/cpufreq/policy0/scaling_governor");
            if (AyaDevicesUtilKt.s) {
                iAyaDevices.b("echo " + str + " > /sys/devices/system/cpu/cpufreq/policy2/scaling_governor");
                iAyaDevices.b("echo " + str + " > /sys/devices/system/cpu/cpufreq/policy5/scaling_governor");
            } else if (AyaDevicesUtilKt.t) {
                iAyaDevices.b("echo " + str + " > /sys/devices/system/cpu/cpufreq/policy6/scaling_governor");
            } else if (AyaDevicesUtilKt.u) {
                iAyaDevices.b("echo " + str + " > /sys/devices/system/cpu/cpufreq/policy4/scaling_governor");
            } else {
                iAyaDevices.b("echo " + str + " > /sys/devices/system/cpu/cpufreq/policy" + (AyaDevicesUtilKt.r ? 3 : 4) + "/scaling_governor");
            }
            iAyaDevices.b("echo " + str + " > /sys/devices/system/cpu/cpufreq/policy7/scaling_governor");
            for (int i4 = 0; i4 < 8; i4++) {
                IAyaDevices iAyaDevices2 = AyaDevicesKt.f4814a;
                iAyaDevices2.b("echo " + CollectionsKt.x(iAyaDevices2.a1().get(i4)) + " > /sys/devices/system/cpu/cpu" + i4 + "/cpufreq/scaling_min_freq");
            }
            if (AyaDevicesUtilKt.q) {
                IAyaDevices iAyaDevices3 = AyaDevicesKt.f4814a;
                iAyaDevices3.b("echo " + CollectionsKt.x(iAyaDevices3.a1().get(0)) + " > /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq");
                iAyaDevices3.b("echo " + CollectionsKt.x(iAyaDevices3.a1().get(3)) + " > /sys/devices/system/cpu/cpufreq/policy3/scaling_min_freq");
                iAyaDevices3.b("echo " + CollectionsKt.x(iAyaDevices3.a1().get(7)) + " > /sys/devices/system/cpu/cpufreq/policy7/scaling_min_freq");
            }
            boolean z = AyaDevicesUtilKt.s;
            if (z) {
                IAyaDevices iAyaDevices4 = AyaDevicesKt.f4814a;
                iAyaDevices4.b("echo " + CollectionsKt.x(iAyaDevices4.a1().get(0)) + " > /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq");
                iAyaDevices4.b("echo " + CollectionsKt.x(iAyaDevices4.a1().get(2)) + " > /sys/devices/system/cpu/cpufreq/policy2/scaling_min_freq");
                iAyaDevices4.b("echo " + CollectionsKt.x(iAyaDevices4.a1().get(5)) + " > /sys/devices/system/cpu/cpufreq/policy5/scaling_min_freq");
                iAyaDevices4.b("echo " + CollectionsKt.x(iAyaDevices4.a1().get(7)) + " > /sys/devices/system/cpu/cpufreq/policy7/scaling_min_freq");
            }
            if (AyaDevicesUtilKt.t) {
                IAyaDevices iAyaDevices5 = AyaDevicesKt.f4814a;
                iAyaDevices5.b("echo " + CollectionsKt.x(iAyaDevices5.a1().get(0)) + " > /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq");
                iAyaDevices5.b("echo " + CollectionsKt.x(iAyaDevices5.a1().get(6)) + " > /sys/devices/system/cpu/cpufreq/policy6/scaling_min_freq");
            }
            if (AyaDevicesUtilKt.u) {
                IAyaDevices iAyaDevices6 = AyaDevicesKt.f4814a;
                iAyaDevices6.b("echo " + CollectionsKt.x(iAyaDevices6.a1().get(0)) + " > /sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq");
                iAyaDevices6.b("echo " + CollectionsKt.x(iAyaDevices6.a1().get(2)) + " > /sys/devices/system/cpu/cpufreq/policy4/scaling_min_freq");
                iAyaDevices6.b("echo " + CollectionsKt.x(iAyaDevices6.a1().get(7)) + " > /sys/devices/system/cpu/cpufreq/policy7/scaling_min_freq");
            }
            if (z && Intrinsics.a(str, "performance")) {
                PerformanceManager.f5864a.getClass();
                ModeConfiguration modeConfiguration = PerformanceManager.b().b().get(Integer.valueOf(PerformanceManager.c()));
                if (modeConfiguration == null || (listA = modeConfiguration.a()) == null) {
                    listA = EmptyList.INSTANCE;
                }
                for (Object obj2 : listA) {
                    int i5 = i + 1;
                    if (i < 0) {
                        CollectionsKt.f0();
                        throw null;
                    }
                    CPUFrequency cPUFrequency = (CPUFrequency) obj2;
                    if (cPUFrequency.getCpuId() == 0 || cPUFrequency.getCpuId() == 2 || cPUFrequency.getCpuId() == 5 || cPUFrequency.getCpuId() == 7) {
                        AyaDevicesKt.f4814a.b("echo " + cPUFrequency.getSelectedFrequency() + " > /sys/devices/system/cpu/cpufreq/policy" + i + "/scaling_max_freq");
                    }
                    i = i5;
                }
            }
        }
        return Unit.f8334a;
    }

    @Override // kotlin.jvm.functions.Function2
    @Nullable
    /* JADX INFO: renamed from: invoke, reason: avoid collision after fix types in other method and merged with bridge method [inline-methods] */
    public final Object mo0invoke(@NotNull CoroutineScope coroutineScope, @Nullable Continuation<? super Unit> continuation) {
        return ((AyaDevicesUtil$applyCPUSchedulerMode$1) create(coroutineScope, continuation)).invokeSuspend(Unit.f8334a);
    }
}
