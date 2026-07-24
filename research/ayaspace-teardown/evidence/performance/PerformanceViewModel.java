package com.ayaneo.settings.ui.performance;

import androidx.exifinterface.media.ExifInterface;
import androidx.p001lifecycle.ViewModel;
import androidx.p001lifecycle.ViewModelKt;
import androidx.versionedparcelable.ParcelUtils;
import com.ayaneo.devices.AyaDevicesKt;
import com.ayaneo.settings.utils.aidl.AyaAidlManager;
import com.google.android.material.motion.MotionUtils;
import com.google.gson.Gson;
import dagger.hilt.android.lifecycle.HiltViewModel;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import kotlin.Metadata;
import kotlin.NoWhenBranchMatchedException;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.SourceDebugExtension;
import kotlinx.coroutines.BuildersKt__Builders_commonKt;
import kotlinx.coroutines.flow.FlowKt;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlow;
import kotlinx.coroutines.flow.StateFlowKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import timber.log.Timber;

/* JADX INFO: loaded from: classes.dex */
@HiltViewModel
@Metadata(d1 = {"\u0000b\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0007\n\u0002\u0010\u000b\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\b\b\u0007\u0018\u00002\u00020\u0001:\u000289B\t\b\u0007¢\u0006\u0004\b\u0002\u0010\u0003J\u0015\u0010\u0007\u001a\u00020\u00062\u0006\u0010\u0005\u001a\u00020\u0004¢\u0006\u0004\b\u0007\u0010\bJ\u000f\u0010\t\u001a\u00020\u0006H\u0002¢\u0006\u0004\b\t\u0010\u0003J\u0017\u0010\u000b\u001a\u00020\u00062\u0006\u0010\n\u001a\u00020\u0004H\u0002¢\u0006\u0004\b\u000b\u0010\bJ\u0017\u0010\u000e\u001a\u00020\u00062\u0006\u0010\r\u001a\u00020\fH\u0002¢\u0006\u0004\b\u000e\u0010\u000fJ\u0017\u0010\u0012\u001a\u00020\u00062\u0006\u0010\u0011\u001a\u00020\u0010H\u0002¢\u0006\u0004\b\u0012\u0010\u0013J\u001f\u0010\u0017\u001a\u00020\u00062\u0006\u0010\u0015\u001a\u00020\u00142\u0006\u0010\u0016\u001a\u00020\u0014H\u0002¢\u0006\u0004\b\u0017\u0010\u0018J\u0017\u0010\u001a\u001a\u00020\u00062\u0006\u0010\u0019\u001a\u00020\u0014H\u0002¢\u0006\u0004\b\u001a\u0010\u001bJ\u0017\u0010\u001e\u001a\u00020\u00062\u0006\u0010\u001d\u001a\u00020\u001cH\u0002¢\u0006\u0004\b\u001e\u0010\u001fJ\u0017\u0010!\u001a\u00020\u00062\u0006\u0010 \u001a\u00020\u0014H\u0002¢\u0006\u0004\b!\u0010\u001bJ#\u0010%\u001a\u00020\u00062\u0012\u0010$\u001a\u000e\u0012\u0004\u0012\u00020#\u0012\u0004\u0012\u00020\u00060\"H\u0002¢\u0006\u0004\b%\u0010&R\u001a\u0010+\u001a\b\u0012\u0004\u0012\u00020(0'8\u0002X\u0082\u0004¢\u0006\u0006\n\u0004\b)\u0010*R\u001d\u00101\u001a\b\u0012\u0004\u0012\u00020(0,8\u0006¢\u0006\f\n\u0004\b-\u0010.\u001a\u0004\b/\u00100R#\u00107\u001a\u000e\u0012\u0004\u0012\u000202\u0012\u0004\u0012\u00020\u00060\"8\u0006¢\u0006\f\n\u0004\b3\u00104\u001a\u0004\b5\u00106¨\u0006:"}, d2 = {"Lcom/ayaneo/settings/ui/performance/PerformanceViewModel;", "Landroidx/lifecycle/ViewModel;", "<init>", "()V", "", "message", "", "t", "(Ljava/lang/String;)V", "u", "configJson", "q", "Lcom/ayaneo/settings/ui/performance/FAN_MODE;", "fanMode", "C", "(Lcom/ayaneo/settings/ui/performance/FAN_MODE;)V", "Lcom/ayaneo/settings/ui/performance/CPUSchedulerMode;", "schedulerMode", "z", "(Lcom/ayaneo/settings/ui/performance/CPUSchedulerMode;)V", "", "cpuId", "frequency", "x", "(II)V", "maxFrequency", ExifInterface.S4, "(I)V", "", "isFixe", "G", "(Z)V", "mode", "v", "Lkotlin/Function1;", "Lcom/ayaneo/settings/ui/performance/PerformanceModel$ConfigData;", "updateAction", "B", "(Lkotlin/jvm/functions/Function1;)V", "Lkotlinx/coroutines/flow/MutableStateFlow;", "Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiState;", "d", "Lkotlinx/coroutines/flow/MutableStateFlow;", "_uiState", "Lkotlinx/coroutines/flow/StateFlow;", "e", "Lkotlinx/coroutines/flow/StateFlow;", "s", "()Lkotlinx/coroutines/flow/StateFlow;", "uiState", "Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction;", "f", "Lkotlin/jvm/functions/Function1;", "r", "()Lkotlin/jvm/functions/Function1;", "uiAction", "UiState", "UiAction", "app_QCOMRelease"}, k = 1, mv = {2, 0, 0})
@SourceDebugExtension({"SMAP\nPerformanceViewModel.kt\nKotlin\n*S Kotlin\n*F\n+ 1 PerformanceViewModel.kt\ncom/ayaneo/settings/ui/performance/PerformanceViewModel\n+ 2 StateFlow.kt\nkotlinx/coroutines/flow/StateFlowKt\n+ 3 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,149:1\n230#2,5:150\n230#2,5:155\n230#2,5:160\n1#3:165\n*S KotlinDebug\n*F\n+ 1 PerformanceViewModel.kt\ncom/ayaneo/settings/ui/performance/PerformanceViewModel\n*L\n40#1:150,5\n59#1:155,5\n132#1:160,5\n*E\n"})
public final class PerformanceViewModel extends ViewModel {

    /* JADX INFO: renamed from: d, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public final MutableStateFlow<UiState> _uiState;

    /* JADX INFO: renamed from: e, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public final StateFlow<UiState> uiState;

    /* JADX INFO: renamed from: f, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public final Function1<UiAction, Unit> uiAction;

    @Metadata(d1 = {"\u0000*\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\b6\u0018\u00002\u00020\u0001:\u0007\u0004\u0005\u0006\u0007\b\t\nB\t\b\u0004¢\u0006\u0004\b\u0002\u0010\u0003\u0082\u0001\u0007\u000b\f\r\u000e\u000f\u0010\u0011¨\u0006\u0012"}, d2 = {"Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction;", "", "<init>", "()V", "ChangeMode", "UpdateFanMode", "UpdateCPUSchedulerMode", "UpdateCPUFrequency", "UpdateGPUFrequency", "UpdateGPUIsFixed", "Reset", "Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction$ChangeMode;", "Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction$Reset;", "Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction$UpdateCPUFrequency;", "Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction$UpdateCPUSchedulerMode;", "Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction$UpdateFanMode;", "Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction$UpdateGPUFrequency;", "Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction$UpdateGPUIsFixed;", "app_QCOMRelease"}, k = 1, mv = {2, 0, 0}, xi = 48)
    public static abstract class UiAction {

        @Metadata(d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000e\n\u0002\b\b\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0005\b\u0086\b\u0018\u00002\u00020\u0001B\u000f\u0012\u0006\u0010\u0003\u001a\u00020\u0002¢\u0006\u0004\b\u0004\u0010\u0005J\u0010\u0010\u0006\u001a\u00020\u0002HÆ\u0003¢\u0006\u0004\b\u0006\u0010\u0007J\u001a\u0010\b\u001a\u00020\u00002\b\b\u0002\u0010\u0003\u001a\u00020\u0002HÆ\u0001¢\u0006\u0004\b\b\u0010\tJ\u0010\u0010\n\u001a\u00020\u0002HÖ\u0001¢\u0006\u0004\b\n\u0010\u0007J\u0010\u0010\f\u001a\u00020\u000bHÖ\u0001¢\u0006\u0004\b\f\u0010\rJ\u001a\u0010\u0011\u001a\u00020\u00102\b\u0010\u000f\u001a\u0004\u0018\u00010\u000eHÖ\u0003¢\u0006\u0004\b\u0011\u0010\u0012R\u0017\u0010\u0003\u001a\u00020\u00028\u0006¢\u0006\f\n\u0004\b\u0006\u0010\u0013\u001a\u0004\b\u0014\u0010\u0007¨\u0006\u0015"}, d2 = {"Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction$ChangeMode;", "Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction;", "", "jsonData", "<init>", "(Ljava/lang/String;)V", ParcelUtils.f10526a, "()Ljava/lang/String;", "b", "(Ljava/lang/String;)Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction$ChangeMode;", "toString", "", "hashCode", "()I", "", "other", "", "equals", "(Ljava/lang/Object;)Z", "Ljava/lang/String;", "d", "app_QCOMRelease"}, k = 1, mv = {2, 0, 0})
        public static final /* data */ class ChangeMode extends UiAction {

            /* JADX INFO: renamed from: a, reason: collision with root package name and from kotlin metadata */
            @NotNull
            public final String jsonData;

            public ChangeMode(@NotNull String jsonData) {
                Intrinsics.p(jsonData, "jsonData");
                this.jsonData = jsonData;
            }

            public static /* synthetic */ ChangeMode c(ChangeMode changeMode, String str, int i2, Object obj) {
                if ((i2 & 1) != 0) {
                    str = changeMode.jsonData;
                }
                return changeMode.b(str);
            }

            @NotNull
            /* JADX INFO: renamed from: a, reason: from getter */
            public final String getJsonData() {
                return this.jsonData;
            }

            @NotNull
            public final ChangeMode b(@NotNull String jsonData) {
                Intrinsics.p(jsonData, "jsonData");
                return new ChangeMode(jsonData);
            }

            @NotNull
            public final String d() {
                return this.jsonData;
            }

            public boolean equals(@Nullable Object other) {
                if (this == other) {
                    return true;
                }
                return (other instanceof ChangeMode) && Intrinsics.g(this.jsonData, ((ChangeMode) other).jsonData);
            }

            public int hashCode() {
                return this.jsonData.hashCode();
            }

            @NotNull
            public String toString() {
                return androidx.constraintlayout.solver.widgets.analyzer.a.a("ChangeMode(jsonData=", this.jsonData, MotionUtils.f18953d);
            }
        }

        @Metadata(d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\b\n\u0002\b\u0007\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0005\b\u0086\b\u0018\u00002\u00020\u0001B\u000f\u0012\u0006\u0010\u0003\u001a\u00020\u0002¢\u0006\u0004\b\u0004\u0010\u0005J\u0010\u0010\u0006\u001a\u00020\u0002HÆ\u0003¢\u0006\u0004\b\u0006\u0010\u0007J\u001a\u0010\b\u001a\u00020\u00002\b\b\u0002\u0010\u0003\u001a\u00020\u0002HÆ\u0001¢\u0006\u0004\b\b\u0010\tJ\u0010\u0010\u000b\u001a\u00020\nHÖ\u0001¢\u0006\u0004\b\u000b\u0010\fJ\u0010\u0010\r\u001a\u00020\u0002HÖ\u0001¢\u0006\u0004\b\r\u0010\u0007J\u001a\u0010\u0011\u001a\u00020\u00102\b\u0010\u000f\u001a\u0004\u0018\u00010\u000eHÖ\u0003¢\u0006\u0004\b\u0011\u0010\u0012R\u0017\u0010\u0003\u001a\u00020\u00028\u0006¢\u0006\f\n\u0004\b\u0006\u0010\u0013\u001a\u0004\b\u0014\u0010\u0007¨\u0006\u0015"}, d2 = {"Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction$Reset;", "Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction;", "", "mode", "<init>", "(I)V", ParcelUtils.f10526a, "()I", "b", "(I)Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction$Reset;", "", "toString", "()Ljava/lang/String;", "hashCode", "", "other", "", "equals", "(Ljava/lang/Object;)Z", "I", "d", "app_QCOMRelease"}, k = 1, mv = {2, 0, 0})
        public static final /* data */ class Reset extends UiAction {

            /* JADX INFO: renamed from: a, reason: collision with root package name and from kotlin metadata */
            public final int mode;

            public Reset(int i2) {
                this.mode = i2;
            }

            public static Reset c(Reset reset, int i2, int i3, Object obj) {
                if ((i3 & 1) != 0) {
                    i2 = reset.mode;
                }
                reset.getClass();
                return new Reset(i2);
            }

            /* JADX INFO: renamed from: a, reason: from getter */
            public final int getMode() {
                return this.mode;
            }

            @NotNull
            public final Reset b(int mode) {
                return new Reset(mode);
            }

            public final int d() {
                return this.mode;
            }

            public boolean equals(@Nullable Object other) {
                if (this == other) {
                    return true;
                }
                return (other instanceof Reset) && this.mode == ((Reset) other).mode;
            }

            public int hashCode() {
                return Integer.hashCode(this.mode);
            }

            @NotNull
            public String toString() {
                return androidx.constraintlayout.solver.a.a("Reset(mode=", this.mode, MotionUtils.f18953d);
            }
        }

        @Metadata(d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\b\n\u0002\b\t\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0006\b\u0086\b\u0018\u00002\u00020\u0001B\u0017\u0012\u0006\u0010\u0003\u001a\u00020\u0002\u0012\u0006\u0010\u0004\u001a\u00020\u0002¢\u0006\u0004\b\u0005\u0010\u0006J\u0010\u0010\u0007\u001a\u00020\u0002HÆ\u0003¢\u0006\u0004\b\u0007\u0010\bJ\u0010\u0010\t\u001a\u00020\u0002HÆ\u0003¢\u0006\u0004\b\t\u0010\bJ$\u0010\n\u001a\u00020\u00002\b\b\u0002\u0010\u0003\u001a\u00020\u00022\b\b\u0002\u0010\u0004\u001a\u00020\u0002HÆ\u0001¢\u0006\u0004\b\n\u0010\u000bJ\u0010\u0010\r\u001a\u00020\fHÖ\u0001¢\u0006\u0004\b\r\u0010\u000eJ\u0010\u0010\u000f\u001a\u00020\u0002HÖ\u0001¢\u0006\u0004\b\u000f\u0010\bJ\u001a\u0010\u0013\u001a\u00020\u00122\b\u0010\u0011\u001a\u0004\u0018\u00010\u0010HÖ\u0003¢\u0006\u0004\b\u0013\u0010\u0014R\u0017\u0010\u0003\u001a\u00020\u00028\u0006¢\u0006\f\n\u0004\b\u0007\u0010\u0015\u001a\u0004\b\u0016\u0010\bR\u0017\u0010\u0004\u001a\u00020\u00028\u0006¢\u0006\f\n\u0004\b\t\u0010\u0015\u001a\u0004\b\u0017\u0010\b¨\u0006\u0018"}, d2 = {"Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction$UpdateCPUFrequency;", "Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction;", "", "cpuId", "frequency", "<init>", "(II)V", ParcelUtils.f10526a, "()I", "b", "c", "(II)Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction$UpdateCPUFrequency;", "", "toString", "()Ljava/lang/String;", "hashCode", "", "other", "", "equals", "(Ljava/lang/Object;)Z", "I", "e", "f", "app_QCOMRelease"}, k = 1, mv = {2, 0, 0})
        public static final /* data */ class UpdateCPUFrequency extends UiAction {

            /* JADX INFO: renamed from: a, reason: collision with root package name and from kotlin metadata */
            public final int cpuId;

            /* JADX INFO: renamed from: b, reason: collision with root package name and from kotlin metadata */
            public final int frequency;

            public UpdateCPUFrequency(int i2, int i3) {
                this.cpuId = i2;
                this.frequency = i3;
            }

            public static UpdateCPUFrequency d(UpdateCPUFrequency updateCPUFrequency, int i2, int i3, int i4, Object obj) {
                if ((i4 & 1) != 0) {
                    i2 = updateCPUFrequency.cpuId;
                }
                if ((i4 & 2) != 0) {
                    i3 = updateCPUFrequency.frequency;
                }
                updateCPUFrequency.getClass();
                return new UpdateCPUFrequency(i2, i3);
            }

            /* JADX INFO: renamed from: a, reason: from getter */
            public final int getCpuId() {
                return this.cpuId;
            }

            /* JADX INFO: renamed from: b, reason: from getter */
            public final int getFrequency() {
                return this.frequency;
            }

            @NotNull
            public final UpdateCPUFrequency c(int cpuId, int frequency) {
                return new UpdateCPUFrequency(cpuId, frequency);
            }

            public final int e() {
                return this.cpuId;
            }

            public boolean equals(@Nullable Object other) {
                if (this == other) {
                    return true;
                }
                if (!(other instanceof UpdateCPUFrequency)) {
                    return false;
                }
                UpdateCPUFrequency updateCPUFrequency = (UpdateCPUFrequency) other;
                return this.cpuId == updateCPUFrequency.cpuId && this.frequency == updateCPUFrequency.frequency;
            }

            public final int f() {
                return this.frequency;
            }

            public int hashCode() {
                return Integer.hashCode(this.frequency) + (Integer.hashCode(this.cpuId) * 31);
            }

            @NotNull
            public String toString() {
                return "UpdateCPUFrequency(cpuId=" + this.cpuId + ", frequency=" + this.frequency + MotionUtils.f18953d;
            }
        }

        @Metadata(d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0005\b\u0086\b\u0018\u00002\u00020\u0001B\u000f\u0012\u0006\u0010\u0003\u001a\u00020\u0002¢\u0006\u0004\b\u0004\u0010\u0005J\u0010\u0010\u0006\u001a\u00020\u0002HÆ\u0003¢\u0006\u0004\b\u0006\u0010\u0007J\u001a\u0010\b\u001a\u00020\u00002\b\b\u0002\u0010\u0003\u001a\u00020\u0002HÆ\u0001¢\u0006\u0004\b\b\u0010\tJ\u0010\u0010\u000b\u001a\u00020\nHÖ\u0001¢\u0006\u0004\b\u000b\u0010\fJ\u0010\u0010\u000e\u001a\u00020\rHÖ\u0001¢\u0006\u0004\b\u000e\u0010\u000fJ\u001a\u0010\u0013\u001a\u00020\u00122\b\u0010\u0011\u001a\u0004\u0018\u00010\u0010HÖ\u0003¢\u0006\u0004\b\u0013\u0010\u0014R\u0017\u0010\u0003\u001a\u00020\u00028\u0006¢\u0006\f\n\u0004\b\u0006\u0010\u0015\u001a\u0004\b\u0016\u0010\u0007¨\u0006\u0017"}, d2 = {"Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction$UpdateCPUSchedulerMode;", "Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction;", "Lcom/ayaneo/settings/ui/performance/CPUSchedulerMode;", "schedulerMode", "<init>", "(Lcom/ayaneo/settings/ui/performance/CPUSchedulerMode;)V", ParcelUtils.f10526a, "()Lcom/ayaneo/settings/ui/performance/CPUSchedulerMode;", "b", "(Lcom/ayaneo/settings/ui/performance/CPUSchedulerMode;)Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction$UpdateCPUSchedulerMode;", "", "toString", "()Ljava/lang/String;", "", "hashCode", "()I", "", "other", "", "equals", "(Ljava/lang/Object;)Z", "Lcom/ayaneo/settings/ui/performance/CPUSchedulerMode;", "d", "app_QCOMRelease"}, k = 1, mv = {2, 0, 0})
        public static final /* data */ class UpdateCPUSchedulerMode extends UiAction {

            /* JADX INFO: renamed from: a, reason: collision with root package name and from kotlin metadata */
            @NotNull
            public final CPUSchedulerMode schedulerMode;

            public UpdateCPUSchedulerMode(@NotNull CPUSchedulerMode schedulerMode) {
                Intrinsics.p(schedulerMode, "schedulerMode");
                this.schedulerMode = schedulerMode;
            }

            public static /* synthetic */ UpdateCPUSchedulerMode c(UpdateCPUSchedulerMode updateCPUSchedulerMode, CPUSchedulerMode cPUSchedulerMode, int i2, Object obj) {
                if ((i2 & 1) != 0) {
                    cPUSchedulerMode = updateCPUSchedulerMode.schedulerMode;
                }
                return updateCPUSchedulerMode.b(cPUSchedulerMode);
            }

            @NotNull
            /* JADX INFO: renamed from: a, reason: from getter */
            public final CPUSchedulerMode getSchedulerMode() {
                return this.schedulerMode;
            }

            @NotNull
            public final UpdateCPUSchedulerMode b(@NotNull CPUSchedulerMode schedulerMode) {
                Intrinsics.p(schedulerMode, "schedulerMode");
                return new UpdateCPUSchedulerMode(schedulerMode);
            }

            @NotNull
            public final CPUSchedulerMode d() {
                return this.schedulerMode;
            }

            public boolean equals(@Nullable Object other) {
                if (this == other) {
                    return true;
                }
                return (other instanceof UpdateCPUSchedulerMode) && this.schedulerMode == ((UpdateCPUSchedulerMode) other).schedulerMode;
            }

            public int hashCode() {
                return this.schedulerMode.hashCode();
            }

            @NotNull
            public String toString() {
                return "UpdateCPUSchedulerMode(schedulerMode=" + this.schedulerMode + MotionUtils.f18953d;
            }
        }

        @Metadata(d1 = {"\u0000.\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0005\b\u0086\b\u0018\u00002\u00020\u0001B\u000f\u0012\u0006\u0010\u0003\u001a\u00020\u0002¢\u0006\u0004\b\u0004\u0010\u0005J\u0010\u0010\u0006\u001a\u00020\u0002HÆ\u0003¢\u0006\u0004\b\u0006\u0010\u0007J\u001a\u0010\b\u001a\u00020\u00002\b\b\u0002\u0010\u0003\u001a\u00020\u0002HÆ\u0001¢\u0006\u0004\b\b\u0010\tJ\u0010\u0010\u000b\u001a\u00020\nHÖ\u0001¢\u0006\u0004\b\u000b\u0010\fJ\u0010\u0010\u000e\u001a\u00020\rHÖ\u0001¢\u0006\u0004\b\u000e\u0010\u000fJ\u001a\u0010\u0013\u001a\u00020\u00122\b\u0010\u0011\u001a\u0004\u0018\u00010\u0010HÖ\u0003¢\u0006\u0004\b\u0013\u0010\u0014R\u0017\u0010\u0003\u001a\u00020\u00028\u0006¢\u0006\f\n\u0004\b\u0006\u0010\u0015\u001a\u0004\b\u0016\u0010\u0007¨\u0006\u0017"}, d2 = {"Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction$UpdateFanMode;", "Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction;", "Lcom/ayaneo/settings/ui/performance/FAN_MODE;", "fanMode", "<init>", "(Lcom/ayaneo/settings/ui/performance/FAN_MODE;)V", ParcelUtils.f10526a, "()Lcom/ayaneo/settings/ui/performance/FAN_MODE;", "b", "(Lcom/ayaneo/settings/ui/performance/FAN_MODE;)Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction$UpdateFanMode;", "", "toString", "()Ljava/lang/String;", "", "hashCode", "()I", "", "other", "", "equals", "(Ljava/lang/Object;)Z", "Lcom/ayaneo/settings/ui/performance/FAN_MODE;", "d", "app_QCOMRelease"}, k = 1, mv = {2, 0, 0})
        public static final /* data */ class UpdateFanMode extends UiAction {

            /* JADX INFO: renamed from: a, reason: collision with root package name and from kotlin metadata */
            @NotNull
            public final FAN_MODE fanMode;

            public UpdateFanMode(@NotNull FAN_MODE fanMode) {
                Intrinsics.p(fanMode, "fanMode");
                this.fanMode = fanMode;
            }

            public static /* synthetic */ UpdateFanMode c(UpdateFanMode updateFanMode, FAN_MODE fan_mode, int i2, Object obj) {
                if ((i2 & 1) != 0) {
                    fan_mode = updateFanMode.fanMode;
                }
                return updateFanMode.b(fan_mode);
            }

            @NotNull
            /* JADX INFO: renamed from: a, reason: from getter */
            public final FAN_MODE getFanMode() {
                return this.fanMode;
            }

            @NotNull
            public final UpdateFanMode b(@NotNull FAN_MODE fanMode) {
                Intrinsics.p(fanMode, "fanMode");
                return new UpdateFanMode(fanMode);
            }

            @NotNull
            public final FAN_MODE d() {
                return this.fanMode;
            }

            public boolean equals(@Nullable Object other) {
                if (this == other) {
                    return true;
                }
                return (other instanceof UpdateFanMode) && this.fanMode == ((UpdateFanMode) other).fanMode;
            }

            public int hashCode() {
                return this.fanMode.hashCode();
            }

            @NotNull
            public String toString() {
                return "UpdateFanMode(fanMode=" + this.fanMode + MotionUtils.f18953d;
            }
        }

        @Metadata(d1 = {"\u0000&\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\b\n\u0002\b\u0007\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0005\b\u0086\b\u0018\u00002\u00020\u0001B\u000f\u0012\u0006\u0010\u0003\u001a\u00020\u0002¢\u0006\u0004\b\u0004\u0010\u0005J\u0010\u0010\u0006\u001a\u00020\u0002HÆ\u0003¢\u0006\u0004\b\u0006\u0010\u0007J\u001a\u0010\b\u001a\u00020\u00002\b\b\u0002\u0010\u0003\u001a\u00020\u0002HÆ\u0001¢\u0006\u0004\b\b\u0010\tJ\u0010\u0010\u000b\u001a\u00020\nHÖ\u0001¢\u0006\u0004\b\u000b\u0010\fJ\u0010\u0010\r\u001a\u00020\u0002HÖ\u0001¢\u0006\u0004\b\r\u0010\u0007J\u001a\u0010\u0011\u001a\u00020\u00102\b\u0010\u000f\u001a\u0004\u0018\u00010\u000eHÖ\u0003¢\u0006\u0004\b\u0011\u0010\u0012R\u0017\u0010\u0003\u001a\u00020\u00028\u0006¢\u0006\f\n\u0004\b\u0006\u0010\u0013\u001a\u0004\b\u0014\u0010\u0007¨\u0006\u0015"}, d2 = {"Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction$UpdateGPUFrequency;", "Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction;", "", "maxFrequency", "<init>", "(I)V", ParcelUtils.f10526a, "()I", "b", "(I)Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction$UpdateGPUFrequency;", "", "toString", "()Ljava/lang/String;", "hashCode", "", "other", "", "equals", "(Ljava/lang/Object;)Z", "I", "d", "app_QCOMRelease"}, k = 1, mv = {2, 0, 0})
        public static final /* data */ class UpdateGPUFrequency extends UiAction {

            /* JADX INFO: renamed from: a, reason: collision with root package name and from kotlin metadata */
            public final int maxFrequency;

            public UpdateGPUFrequency(int i2) {
                this.maxFrequency = i2;
            }

            public static UpdateGPUFrequency c(UpdateGPUFrequency updateGPUFrequency, int i2, int i3, Object obj) {
                if ((i3 & 1) != 0) {
                    i2 = updateGPUFrequency.maxFrequency;
                }
                updateGPUFrequency.getClass();
                return new UpdateGPUFrequency(i2);
            }

            /* JADX INFO: renamed from: a, reason: from getter */
            public final int getMaxFrequency() {
                return this.maxFrequency;
            }

            @NotNull
            public final UpdateGPUFrequency b(int maxFrequency) {
                return new UpdateGPUFrequency(maxFrequency);
            }

            public final int d() {
                return this.maxFrequency;
            }

            public boolean equals(@Nullable Object other) {
                if (this == other) {
                    return true;
                }
                return (other instanceof UpdateGPUFrequency) && this.maxFrequency == ((UpdateGPUFrequency) other).maxFrequency;
            }

            public int hashCode() {
                return Integer.hashCode(this.maxFrequency);
            }

            @NotNull
            public String toString() {
                return androidx.constraintlayout.solver.a.a("UpdateGPUFrequency(maxFrequency=", this.maxFrequency, MotionUtils.f18953d);
            }
        }

        @Metadata(d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0002\b\u0007\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u0000\n\u0002\b\u0006\b\u0086\b\u0018\u00002\u00020\u0001B\u000f\u0012\u0006\u0010\u0003\u001a\u00020\u0002¢\u0006\u0004\b\u0004\u0010\u0005J\u0010\u0010\u0006\u001a\u00020\u0002HÆ\u0003¢\u0006\u0004\b\u0006\u0010\u0007J\u001a\u0010\b\u001a\u00020\u00002\b\b\u0002\u0010\u0003\u001a\u00020\u0002HÆ\u0001¢\u0006\u0004\b\b\u0010\tJ\u0010\u0010\u000b\u001a\u00020\nHÖ\u0001¢\u0006\u0004\b\u000b\u0010\fJ\u0010\u0010\u000e\u001a\u00020\rHÖ\u0001¢\u0006\u0004\b\u000e\u0010\u000fJ\u001a\u0010\u0012\u001a\u00020\u00022\b\u0010\u0011\u001a\u0004\u0018\u00010\u0010HÖ\u0003¢\u0006\u0004\b\u0012\u0010\u0013R\u0017\u0010\u0003\u001a\u00020\u00028\u0006¢\u0006\f\n\u0004\b\u0006\u0010\u0014\u001a\u0004\b\u0015\u0010\u0007¨\u0006\u0016"}, d2 = {"Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction$UpdateGPUIsFixed;", "Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction;", "", "isFixe", "<init>", "(Z)V", ParcelUtils.f10526a, "()Z", "b", "(Z)Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiAction$UpdateGPUIsFixed;", "", "toString", "()Ljava/lang/String;", "", "hashCode", "()I", "", "other", "equals", "(Ljava/lang/Object;)Z", "Z", "d", "app_QCOMRelease"}, k = 1, mv = {2, 0, 0})
        public static final /* data */ class UpdateGPUIsFixed extends UiAction {

            /* JADX INFO: renamed from: a, reason: collision with root package name and from kotlin metadata */
            public final boolean isFixe;

            public UpdateGPUIsFixed(boolean z) {
                this.isFixe = z;
            }

            public static UpdateGPUIsFixed c(UpdateGPUIsFixed updateGPUIsFixed, boolean z, int i2, Object obj) {
                if ((i2 & 1) != 0) {
                    z = updateGPUIsFixed.isFixe;
                }
                updateGPUIsFixed.getClass();
                return new UpdateGPUIsFixed(z);
            }

            /* JADX INFO: renamed from: a, reason: from getter */
            public final boolean getIsFixe() {
                return this.isFixe;
            }

            @NotNull
            public final UpdateGPUIsFixed b(boolean isFixe) {
                return new UpdateGPUIsFixed(isFixe);
            }

            public final boolean d() {
                return this.isFixe;
            }

            public boolean equals(@Nullable Object other) {
                if (this == other) {
                    return true;
                }
                return (other instanceof UpdateGPUIsFixed) && this.isFixe == ((UpdateGPUIsFixed) other).isFixe;
            }

            public int hashCode() {
                return Boolean.hashCode(this.isFixe);
            }

            @NotNull
            public String toString() {
                return "UpdateGPUIsFixed(isFixe=" + this.isFixe + MotionUtils.f18953d;
            }
        }

        public UiAction() {
        }

        public UiAction(DefaultConstructorMarker defaultConstructorMarker) {
        }
    }

    @Metadata(d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\u0010\u000e\n\u0002\b\b\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0005\b\u0086\b\u0018\u00002\u00020\u0001B\u0011\u0012\b\b\u0002\u0010\u0003\u001a\u00020\u0002¢\u0006\u0004\b\u0004\u0010\u0005J\u0010\u0010\u0006\u001a\u00020\u0002HÆ\u0003¢\u0006\u0004\b\u0006\u0010\u0007J\u001a\u0010\b\u001a\u00020\u00002\b\b\u0002\u0010\u0003\u001a\u00020\u0002HÆ\u0001¢\u0006\u0004\b\b\u0010\tJ\u0010\u0010\n\u001a\u00020\u0002HÖ\u0001¢\u0006\u0004\b\n\u0010\u0007J\u0010\u0010\f\u001a\u00020\u000bHÖ\u0001¢\u0006\u0004\b\f\u0010\rJ\u001a\u0010\u0010\u001a\u00020\u000f2\b\u0010\u000e\u001a\u0004\u0018\u00010\u0001HÖ\u0003¢\u0006\u0004\b\u0010\u0010\u0011R\u0017\u0010\u0003\u001a\u00020\u00028\u0006¢\u0006\f\n\u0004\b\u0006\u0010\u0012\u001a\u0004\b\u0013\u0010\u0007¨\u0006\u0014"}, d2 = {"Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiState;", "", "", "configDataJson", "<init>", "(Ljava/lang/String;)V", ParcelUtils.f10526a, "()Ljava/lang/String;", "b", "(Ljava/lang/String;)Lcom/ayaneo/settings/ui/performance/PerformanceViewModel$UiState;", "toString", "", "hashCode", "()I", "other", "", "equals", "(Ljava/lang/Object;)Z", "Ljava/lang/String;", "d", "app_QCOMRelease"}, k = 1, mv = {2, 0, 0})
    public static final /* data */ class UiState {

        /* JADX INFO: renamed from: a, reason: collision with root package name and from kotlin metadata */
        @NotNull
        public final String configDataJson;

        /* JADX WARN: Multi-variable type inference failed */
        public UiState() {
            this(null, 1, 0 == true ? 1 : 0);
        }

        public static /* synthetic */ UiState c(UiState uiState, String str, int i2, Object obj) {
            if ((i2 & 1) != 0) {
                str = uiState.configDataJson;
            }
            return uiState.b(str);
        }

        @NotNull
        /* JADX INFO: renamed from: a, reason: from getter */
        public final String getConfigDataJson() {
            return this.configDataJson;
        }

        @NotNull
        public final UiState b(@NotNull String configDataJson) {
            Intrinsics.p(configDataJson, "configDataJson");
            return new UiState(configDataJson);
        }

        @NotNull
        public final String d() {
            return this.configDataJson;
        }

        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            return (other instanceof UiState) && Intrinsics.g(this.configDataJson, ((UiState) other).configDataJson);
        }

        public int hashCode() {
            return this.configDataJson.hashCode();
        }

        @NotNull
        public String toString() {
            return androidx.constraintlayout.solver.widgets.analyzer.a.a("UiState(configDataJson=", this.configDataJson, MotionUtils.f18953d);
        }

        public UiState(@NotNull String configDataJson) {
            Intrinsics.p(configDataJson, "configDataJson");
            this.configDataJson = configDataJson;
        }

        public /* synthetic */ UiState(String str, int i2, DefaultConstructorMarker defaultConstructorMarker) {
            this((i2 & 1) != 0 ? "" : str);
        }
    }

    /* JADX WARN: Multi-variable type inference failed */
    @Inject
    public PerformanceViewModel() {
        MutableStateFlow<UiState> mutableStateFlowA = StateFlowKt.a(new UiState(null, 1, 0 == true ? 1 : 0));
        this._uiState = mutableStateFlowA;
        this.uiState = FlowKt.m(mutableStateFlowA);
        this.uiAction = new Function1() { // from class: com.ayaneo.settings.ui.performance.y
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return PerformanceViewModel.o(this.f16950a, (PerformanceViewModel.UiAction) obj);
            }
        };
        u();
    }

    public static final Unit A(CPUSchedulerMode cPUSchedulerMode, PerformanceModel.ConfigData config) {
        Intrinsics.p(config, "config");
        ModeConfiguration modeConfiguration = config.f().get(Integer.valueOf(config.e()));
        if (modeConfiguration != null) {
            modeConfiguration.l(cPUSchedulerMode);
        }
        return Unit.f21153a;
    }

    public static final Unit D(FAN_MODE fan_mode, PerformanceModel.ConfigData config) {
        Intrinsics.p(config, "config");
        ModeConfiguration modeConfiguration = config.f().get(Integer.valueOf(config.e()));
        if (modeConfiguration != null) {
            modeConfiguration.m(fan_mode);
        }
        return Unit.f21153a;
    }

    public static final Unit F(int i2, PerformanceModel.ConfigData config) {
        GPUFrequency gPUFrequencyJ;
        Intrinsics.p(config, "config");
        ModeConfiguration modeConfiguration = config.f().get(Integer.valueOf(config.e()));
        if (modeConfiguration != null && (gPUFrequencyJ = modeConfiguration.j()) != null) {
            gPUFrequencyJ.j(i2);
        }
        return Unit.f21153a;
    }

    public static final Unit H(boolean z, PerformanceModel.ConfigData config) {
        GPUFrequency gPUFrequencyJ;
        Intrinsics.p(config, "config");
        ModeConfiguration modeConfiguration = config.f().get(Integer.valueOf(config.e()));
        if (modeConfiguration != null && (gPUFrequencyJ = modeConfiguration.j()) != null) {
            gPUFrequencyJ.i(z);
        }
        return Unit.f21153a;
    }

    public static final Unit o(PerformanceViewModel performanceViewModel, UiAction action) {
        Intrinsics.p(action, "action");
        if (action instanceof UiAction.ChangeMode) {
            performanceViewModel.q(((UiAction.ChangeMode) action).jsonData);
        } else if (action instanceof UiAction.UpdateFanMode) {
            performanceViewModel.C(((UiAction.UpdateFanMode) action).fanMode);
        } else if (action instanceof UiAction.UpdateCPUSchedulerMode) {
            performanceViewModel.z(((UiAction.UpdateCPUSchedulerMode) action).schedulerMode);
        } else if (action instanceof UiAction.UpdateCPUFrequency) {
            UiAction.UpdateCPUFrequency updateCPUFrequency = (UiAction.UpdateCPUFrequency) action;
            performanceViewModel.x(updateCPUFrequency.cpuId, updateCPUFrequency.frequency);
        } else if (action instanceof UiAction.UpdateGPUFrequency) {
            performanceViewModel.E(((UiAction.UpdateGPUFrequency) action).maxFrequency);
        } else if (action instanceof UiAction.UpdateGPUIsFixed) {
            performanceViewModel.G(((UiAction.UpdateGPUIsFixed) action).isFixe);
        } else {
            if (!(action instanceof UiAction.Reset)) {
                throw new NoWhenBranchMatchedException();
            }
            performanceViewModel.v(((UiAction.Reset) action).mode);
        }
        return Unit.f21153a;
    }

    public static final Unit w(int i2, PerformanceModel.ConfigData config) {
        Intrinsics.p(config, "config");
        Integer numValueOf = Integer.valueOf(i2);
        Map<Integer, ModeConfiguration> mapF = config.f();
        ModeConfiguration modeConfiguration = AyaDevicesKt.a().N().get(Integer.valueOf(i2));
        Intrinsics.m(modeConfiguration);
        mapF.put(numValueOf, modeConfiguration);
        return Unit.f21153a;
    }

    public static final Unit y(int i2, int i3, PerformanceModel.ConfigData config) {
        List<CPUFrequency> listG;
        Object next;
        Intrinsics.p(config, "config");
        ModeConfiguration modeConfiguration = config.f().get(Integer.valueOf(config.e()));
        if (modeConfiguration != null && (listG = modeConfiguration.g()) != null) {
            Iterator<T> it = listG.iterator();
            do {
                if (!it.hasNext()) {
                    next = null;
                    break;
                }
                next = it.next();
            } while (((CPUFrequency) next).e() != i3);
            CPUFrequency cPUFrequency = (CPUFrequency) next;
            if (cPUFrequency != null) {
                cPUFrequency.g(i2);
            }
        }
        return Unit.f21153a;
    }

    public final void B(Function1<? super PerformanceModel.ConfigData, Unit> updateAction) {
        UiState value;
        PerformanceModel.ConfigData configData = (PerformanceModel.ConfigData) new Gson().fromJson(this._uiState.getValue().configDataJson, PerformanceModel.ConfigData.class);
        Intrinsics.m(configData);
        updateAction.invoke(configData);
        String json = new Gson().toJson(configData);
        Timber.INSTANCE.k("aidl_settings updateConfig", new Object[0]);
        MutableStateFlow<UiState> mutableStateFlow = this._uiState;
        do {
            value = mutableStateFlow.getValue();
            Intrinsics.m(json);
        } while (!mutableStateFlow.d(value, value.b(json)));
    }

    public final void C(final FAN_MODE fanMode) {
        Timber.INSTANCE.k("aidl_settings updateFanMode fanMode = " + fanMode, new Object[0]);
        B(new Function1() { // from class: com.ayaneo.settings.ui.performance.z
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return PerformanceViewModel.D(fanMode, (PerformanceModel.ConfigData) obj);
            }
        });
        AyaAidlManager.f17275a.k(AyaAidlManager.MSG_TYPE_PERFORMANCE, "com_set_performance_fan:" + fanMode);
    }

    public final void E(final int maxFrequency) {
        Timber.INSTANCE.k(androidx.appcompat.view.menu.a.a("aidl_settings updateGPUFrequency maxFrequency = ", maxFrequency), new Object[0]);
        B(new Function1() { // from class: com.ayaneo.settings.ui.performance.t
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return PerformanceViewModel.F(maxFrequency, (PerformanceModel.ConfigData) obj);
            }
        });
        AyaAidlManager.f17275a.k(AyaAidlManager.MSG_TYPE_PERFORMANCE, "com_set_performance_gpu:" + maxFrequency);
    }

    public final void G(final boolean isFixe) {
        Timber.INSTANCE.k("aidl_settings updateGPUIsFixed isFixe = " + isFixe, new Object[0]);
        B(new Function1() { // from class: com.ayaneo.settings.ui.performance.x
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return PerformanceViewModel.H(isFixe, (PerformanceModel.ConfigData) obj);
            }
        });
        AyaAidlManager.f17275a.k(AyaAidlManager.MSG_TYPE_PERFORMANCE, "com_set_performance_gpu_is_fixed:" + isFixe);
    }

    public final void q(String configJson) {
        UiState value;
        Timber.INSTANCE.k(androidx.constraintlayout.motion.widget.a.a("aidl_settings changeMode configJson = ", configJson), new Object[0]);
        MutableStateFlow<UiState> mutableStateFlow = this._uiState;
        do {
            value = mutableStateFlow.getValue();
        } while (!mutableStateFlow.d(value, value.b(configJson)));
    }

    @NotNull
    public final Function1<UiAction, Unit> r() {
        return this.uiAction;
    }

    @NotNull
    public final StateFlow<UiState> s() {
        return this.uiState;
    }

    public final void t(@NotNull String message) {
        UiState value;
        Timber.Companion companion;
        Intrinsics.p(message, "message");
        MutableStateFlow<UiState> mutableStateFlow = this._uiState;
        do {
            value = mutableStateFlow.getValue();
            companion = Timber.INSTANCE;
            companion.k(androidx.constraintlayout.motion.widget.a.a("aidl_settings aidlMessage message = ", message), new Object[0]);
        } while (!mutableStateFlow.d(value, value.b(message)));
        companion.k(androidx.constraintlayout.motion.widget.a.a("aidl_settings aidlMessage message 2 = ", this._uiState.getValue().configDataJson), new Object[0]);
    }

    public final void u() {
        BuildersKt__Builders_commonKt.f(ViewModelKt.a(this), null, null, new PerformanceViewModel$loadInitialData$1(this, null), 3, null);
    }

    public final void v(final int mode) {
        Timber.INSTANCE.k(androidx.appcompat.view.menu.a.a("aidl_settings reset mode = ", mode), new Object[0]);
        B(new Function1() { // from class: com.ayaneo.settings.ui.performance.w
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return PerformanceViewModel.w(mode, (PerformanceModel.ConfigData) obj);
            }
        });
        AyaAidlManager.f17275a.k(AyaAidlManager.MSG_TYPE_PERFORMANCE, "com_set_performance_reset:" + mode);
    }

    public final void x(final int cpuId, final int frequency) {
        Timber.INSTANCE.k(androidx.emoji2.text.flatbuffer.a.a("aidl_settings updateCPUFrequency cpuId = ", cpuId, " | frequency = ", frequency), new Object[0]);
        B(new Function1() { // from class: com.ayaneo.settings.ui.performance.v
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return PerformanceViewModel.y(frequency, cpuId, (PerformanceModel.ConfigData) obj);
            }
        });
        AyaAidlManager.f17275a.k(AyaAidlManager.MSG_TYPE_PERFORMANCE, androidx.emoji2.text.flatbuffer.a.a("com_set_performance_cpu:", cpuId, "_", frequency));
    }

    public final void z(final CPUSchedulerMode schedulerMode) {
        Timber.INSTANCE.k("aidl_settings updateCPUSchedulerMode schedulerMode = " + schedulerMode, new Object[0]);
        B(new Function1() { // from class: com.ayaneo.settings.ui.performance.u
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return PerformanceViewModel.A(schedulerMode, (PerformanceModel.ConfigData) obj);
            }
        });
        AyaAidlManager.f17275a.k(AyaAidlManager.MSG_TYPE_PERFORMANCE, "com_set_performance_scheduler:" + schedulerMode);
    }
}
