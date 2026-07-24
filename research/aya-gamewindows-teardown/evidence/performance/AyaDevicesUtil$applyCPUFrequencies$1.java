package com.ayaneo;

import a.a;
import com.ayaneo.devices.AyaDevicesKt;
import com.ayaneo.devices.IAyaDevices;
import com.ayaneo.devices.ar01.KtRootShell;
import com.ayaneo.gamewindow.LauncherApp;
import com.ayaneo.gamewindow.ui.window.performance.util.CPUFrequency;
import com.ayaneo.gamewindow.utils.SettingsKt;
import java.util.List;
import kotlin.Metadata;
import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.intrinsics.CoroutineSingletons;
import kotlin.coroutines.jvm.internal.DebugMetadata;
import kotlin.coroutines.jvm.internal.SuspendLambda;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.internal.SourceDebugExtension;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import timber.log.Timber;

/* JADX INFO: compiled from: AyaDevicesUtil.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000\n\n\u0000\n\u0002\u0010\u0002\n\u0002\u0018\u0002\u0010\u0000\u001a\u00020\u0001*\u00020\u0002H\u008a@"}, d2 = {"<anonymous>", "", "Lkotlinx/coroutines/CoroutineScope;"}, k = 3, mv = {1, 9, 0}, xi = 48)
@DebugMetadata(c = "com.ayaneo.AyaDevicesUtil$applyCPUFrequencies$1", f = "AyaDevicesUtil.kt", l = {}, m = "invokeSuspend")
@SourceDebugExtension
public final class AyaDevicesUtil$applyCPUFrequencies$1 extends SuspendLambda implements Function2<CoroutineScope, Continuation<? super Unit>, Object> {
    final /* synthetic */ List<CPUFrequency> $cpuFrequencies;
    final /* synthetic */ boolean $isHighPerformance;
    int label;

    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    public AyaDevicesUtil$applyCPUFrequencies$1(List<CPUFrequency> list, boolean z, Continuation<? super AyaDevicesUtil$applyCPUFrequencies$1> continuation) {
        super(2, continuation);
        this.$cpuFrequencies = list;
        this.$isHighPerformance = z;
    }

    @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
    @NotNull
    public final Continuation<Unit> create(@Nullable Object obj, @NotNull Continuation<?> continuation) {
        return new AyaDevicesUtil$applyCPUFrequencies$1(this.$cpuFrequencies, this.$isHighPerformance, continuation);
    }

    @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
    @Nullable
    public final Object invokeSuspend(@NotNull Object obj) {
        CoroutineSingletons coroutineSingletons = CoroutineSingletons.COROUTINE_SUSPENDED;
        if (this.label != 0) {
            throw new IllegalStateException("call to 'resume' before 'invoke' with coroutine");
        }
        ResultKt.b(obj);
        if (AyaDevicesUtilKt.f4813d || AyaDevicesUtilKt.e || AyaDevicesUtilKt.f) {
            IAyaDevices iAyaDevices = AyaDevicesKt.f4814a;
            int iIndexOf = iAyaDevices.a1().get(0).indexOf(new Integer(this.$cpuFrequencies.get(0).getSelectedFrequency())) + 1;
            int iIndexOf2 = iAyaDevices.a1().get(6).indexOf(new Integer(this.$cpuFrequencies.get(6).getSelectedFrequency())) + 1;
            Timber.Forest forest = Timber.f11226a;
            int selectedFrequency = this.$cpuFrequencies.get(0).getSelectedFrequency();
            int selectedFrequency2 = this.$cpuFrequencies.get(6).getSelectedFrequency();
            StringBuilder sbW = a.w("aidl ar04 cpu set frequency index cpuSmallMax = ", iIndexOf, " , value = ", selectedFrequency, " | cpuBigMax = ");
            sbW.append(iIndexOf2);
            sbW.append(" | value = ");
            sbW.append(selectedFrequency2);
            forest.f(sbW.toString(), new Object[0]);
            LauncherApp.e.getClass();
            SettingsKt.h(LauncherApp.Companion.a(), 1, "yt_small_cpu_min_frequencies");
            SettingsKt.h(LauncherApp.Companion.a(), iIndexOf, "yt_small_cpu_max_frequencies");
            SettingsKt.h(LauncherApp.Companion.a(), 1, "yt_large_cpu_min_frequencies");
            SettingsKt.h(LauncherApp.Companion.a(), iIndexOf2, "yt_large_cpu_max_frequencies");
            SettingsKt.h(LauncherApp.Companion.a(), 1, "yt_boost_switch_toggle_status");
        } else if (AyaDevicesUtilKt.f4810a) {
            int selectedFrequency3 = this.$cpuFrequencies.get(0).getSelectedFrequency();
            int selectedFrequency4 = this.$cpuFrequencies.get(4).getSelectedFrequency();
            int selectedFrequency5 = this.$cpuFrequencies.get(7).getSelectedFrequency();
            if (this.$isHighPerformance) {
                KtRootShell.f4840a.getClass();
                KtRootShell.d(selectedFrequency3, selectedFrequency3);
                KtRootShell.c(selectedFrequency4, selectedFrequency4);
                KtRootShell.b(selectedFrequency5, selectedFrequency5);
            } else {
                KtRootShell.f4840a.getClass();
                KtRootShell.d(-1, selectedFrequency3);
                KtRootShell.c(-1, selectedFrequency4);
                KtRootShell.b(-1, selectedFrequency5);
            }
        } else {
            for (CPUFrequency cPUFrequency : this.$cpuFrequencies) {
                String str = "echo " + cPUFrequency.getSelectedFrequency() + " > /sys/devices/system/cpu/cpu" + cPUFrequency.getCpuId() + "/cpufreq/scaling_max_freq";
                AyaDevicesKt.f4814a.b(str);
                Timber.f11226a.f(a.B("t_807 cpu cmd = ", str), new Object[0]);
            }
        }
        return Unit.f8334a;
    }

    @Override // kotlin.jvm.functions.Function2
    @Nullable
    /* JADX INFO: renamed from: invoke, reason: avoid collision after fix types in other method and merged with bridge method [inline-methods] */
    public final Object mo0invoke(@NotNull CoroutineScope coroutineScope, @Nullable Continuation<? super Unit> continuation) {
        return ((AyaDevicesUtil$applyCPUFrequencies$1) create(coroutineScope, continuation)).invokeSuspend(Unit.f8334a);
    }
}
