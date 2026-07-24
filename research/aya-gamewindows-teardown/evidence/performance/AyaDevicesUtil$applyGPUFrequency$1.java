package com.ayaneo;

import a.a;
import com.ayaneo.devices.AyaDevicesKt;
import com.ayaneo.devices.IAyaDevices;
import com.ayaneo.gamewindow.ui.window.performance.util.GPUFrequency;
import com.google.android.exoplayer2.PlaybackException;
import kotlin.Metadata;
import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.collections.CollectionsKt;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.intrinsics.CoroutineSingletons;
import kotlin.coroutines.jvm.internal.DebugMetadata;
import kotlin.coroutines.jvm.internal.SuspendLambda;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/* JADX INFO: compiled from: AyaDevicesUtil.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000\n\n\u0000\n\u0002\u0010\u0002\n\u0002\u0018\u0002\u0010\u0000\u001a\u00020\u0001*\u00020\u0002H\u008a@"}, d2 = {"<anonymous>", "", "Lkotlinx/coroutines/CoroutineScope;"}, k = 3, mv = {1, 9, 0}, xi = 48)
@DebugMetadata(c = "com.ayaneo.AyaDevicesUtil$applyGPUFrequency$1", f = "AyaDevicesUtil.kt", l = {}, m = "invokeSuspend")
public final class AyaDevicesUtil$applyGPUFrequency$1 extends SuspendLambda implements Function2<CoroutineScope, Continuation<? super Unit>, Object> {
    final /* synthetic */ GPUFrequency $gpuFrequency;
    int label;

    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    public AyaDevicesUtil$applyGPUFrequency$1(GPUFrequency gPUFrequency, Continuation<? super AyaDevicesUtil$applyGPUFrequency$1> continuation) {
        super(2, continuation);
        this.$gpuFrequency = gPUFrequency;
    }

    @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
    @NotNull
    public final Continuation<Unit> create(@Nullable Object obj, @NotNull Continuation<?> continuation) {
        return new AyaDevicesUtil$applyGPUFrequency$1(this.$gpuFrequency, continuation);
    }

    @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
    @Nullable
    public final Object invokeSuspend(@NotNull Object obj) {
        int i;
        CoroutineSingletons coroutineSingletons = CoroutineSingletons.COROUTINE_SUSPENDED;
        if (this.label != 0) {
            throw new IllegalStateException("call to 'resume' before 'invoke' with coroutine");
        }
        ResultKt.b(obj);
        if (AyaDevicesUtilKt.f4813d || AyaDevicesUtilKt.e || AyaDevicesUtilKt.f) {
            if (this.$gpuFrequency.getMaxFrequency() == 0) {
                GPUFrequency gPUFrequency = this.$gpuFrequency;
                gPUFrequency.e(gPUFrequency.getMinFrequency());
            }
            IAyaDevices iAyaDevices = AyaDevicesKt.f4814a;
            iAyaDevices.b("echo " + CollectionsKt.T(iAyaDevices.a()).indexOf(new Integer(this.$gpuFrequency.getMaxFrequency())) + " > /proc/gpufreqv2/fix_target_opp_index");
        } else if (AyaDevicesUtilKt.r || AyaDevicesUtilKt.t) {
            if (this.$gpuFrequency.getIsFixed()) {
                i = AyaDevicesUtilKt.s ? PlaybackException.CUSTOM_ERROR_CODE_BASE : 10000000;
            } else {
                i = 80;
            }
            String strH = a.h("echo ", i, " > /sys/class/kgsl/kgsl-3d0/idle_timer");
            String strH2 = a.h("echo ", this.$gpuFrequency.getMaxFrequency(), " > /sys/class/kgsl/kgsl-3d0/max_gpuclk");
            String strH3 = a.h("echo ", this.$gpuFrequency.getIsFixed() ? this.$gpuFrequency.getMaxFrequency() : this.$gpuFrequency.getMinFrequency(), " > /sys/class/kgsl/kgsl-3d0/devfreq/min_freq");
            String strH4 = a.h("echo ", this.$gpuFrequency.getMaxFrequency(), " > /sys/class/kgsl/kgsl-3d0/devfreq/max_freq");
            IAyaDevices iAyaDevices2 = AyaDevicesKt.f4814a;
            iAyaDevices2.b(strH);
            iAyaDevices2.b(strH2);
            iAyaDevices2.b(strH4);
            iAyaDevices2.b(strH3);
        } else {
            AyaDevicesKt.f4814a.b("echo " + this.$gpuFrequency.getMaxFrequency() + " > /proc/gpufreq/gpufreq_opp_freq");
        }
        return Unit.f8334a;
    }

    @Override // kotlin.jvm.functions.Function2
    @Nullable
    /* JADX INFO: renamed from: invoke, reason: avoid collision after fix types in other method and merged with bridge method [inline-methods] */
    public final Object mo0invoke(@NotNull CoroutineScope coroutineScope, @Nullable Continuation<? super Unit> continuation) {
        return ((AyaDevicesUtil$applyGPUFrequency$1) create(coroutineScope, continuation)).invokeSuspend(Unit.f8334a);
    }
}
