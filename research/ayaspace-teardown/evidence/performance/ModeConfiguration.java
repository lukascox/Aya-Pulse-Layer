package com.ayaneo.settings.ui.performance;

import androidx.core.graphics.PaintCompat;
import androidx.versionedparcelable.ParcelUtils;
import com.google.android.material.motion.MotionUtils;
import com.google.gson.annotations.SerializedName;
import java.util.List;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000>\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\r\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0013\b\u0086\b\u0018\u00002\u00020\u0001B-\u0012\u0006\u0010\u0003\u001a\u00020\u0002\u0012\u0006\u0010\u0005\u001a\u00020\u0004\u0012\f\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006\u0012\u0006\u0010\n\u001a\u00020\t¢\u0006\u0004\b\u000b\u0010\fJ\u0010\u0010\r\u001a\u00020\u0002HÆ\u0003¢\u0006\u0004\b\r\u0010\u000eJ\u0010\u0010\u000f\u001a\u00020\u0004HÆ\u0003¢\u0006\u0004\b\u000f\u0010\u0010J\u0016\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u00070\u0006HÆ\u0003¢\u0006\u0004\b\u0011\u0010\u0012J\u0010\u0010\u0013\u001a\u00020\tHÆ\u0003¢\u0006\u0004\b\u0013\u0010\u0014J>\u0010\u0015\u001a\u00020\u00002\b\b\u0002\u0010\u0003\u001a\u00020\u00022\b\b\u0002\u0010\u0005\u001a\u00020\u00042\u000e\b\u0002\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00070\u00062\b\b\u0002\u0010\n\u001a\u00020\tHÆ\u0001¢\u0006\u0004\b\u0015\u0010\u0016J\u0010\u0010\u0018\u001a\u00020\u0017HÖ\u0001¢\u0006\u0004\b\u0018\u0010\u0019J\u0010\u0010\u001b\u001a\u00020\u001aHÖ\u0001¢\u0006\u0004\b\u001b\u0010\u001cJ\u001a\u0010\u001f\u001a\u00020\u001e2\b\u0010\u001d\u001a\u0004\u0018\u00010\u0001HÖ\u0003¢\u0006\u0004\b\u001f\u0010 R\"\u0010\u0003\u001a\u00020\u00028\u0006@\u0006X\u0087\u000e¢\u0006\u0012\n\u0004\b\u0003\u0010!\u001a\u0004\b\"\u0010\u000e\"\u0004\b#\u0010$R\"\u0010\u0005\u001a\u00020\u00048\u0006@\u0006X\u0087\u000e¢\u0006\u0012\n\u0004\b\u0005\u0010%\u001a\u0004\b&\u0010\u0010\"\u0004\b'\u0010(R(\u0010\b\u001a\b\u0012\u0004\u0012\u00020\u00070\u00068\u0006@\u0006X\u0087\u000e¢\u0006\u0012\n\u0004\b\b\u0010)\u001a\u0004\b*\u0010\u0012\"\u0004\b+\u0010,R\"\u0010\n\u001a\u00020\t8\u0006@\u0006X\u0087\u000e¢\u0006\u0012\n\u0004\b\n\u0010-\u001a\u0004\b.\u0010\u0014\"\u0004\b/\u00100¨\u00061"}, d2 = {"Lcom/ayaneo/settings/ui/performance/ModeConfiguration;", "", "Lcom/ayaneo/settings/ui/performance/FAN_MODE;", "fanMode", "Lcom/ayaneo/settings/ui/performance/CPUSchedulerMode;", "cpuSchedulerMode", "", "Lcom/ayaneo/settings/ui/performance/CPUFrequency;", "cpuFrequencies", "Lcom/ayaneo/settings/ui/performance/GPUFrequency;", "gpuFrequency", "<init>", "(Lcom/ayaneo/settings/ui/performance/FAN_MODE;Lcom/ayaneo/settings/ui/performance/CPUSchedulerMode;Ljava/util/List;Lcom/ayaneo/settings/ui/performance/GPUFrequency;)V", ParcelUtils.f10526a, "()Lcom/ayaneo/settings/ui/performance/FAN_MODE;", "b", "()Lcom/ayaneo/settings/ui/performance/CPUSchedulerMode;", "c", "()Ljava/util/List;", "d", "()Lcom/ayaneo/settings/ui/performance/GPUFrequency;", "e", "(Lcom/ayaneo/settings/ui/performance/FAN_MODE;Lcom/ayaneo/settings/ui/performance/CPUSchedulerMode;Ljava/util/List;Lcom/ayaneo/settings/ui/performance/GPUFrequency;)Lcom/ayaneo/settings/ui/performance/ModeConfiguration;", "", "toString", "()Ljava/lang/String;", "", "hashCode", "()I", "other", "", "equals", "(Ljava/lang/Object;)Z", "Lcom/ayaneo/settings/ui/performance/FAN_MODE;", "i", PaintCompat.f4353b, "(Lcom/ayaneo/settings/ui/performance/FAN_MODE;)V", "Lcom/ayaneo/settings/ui/performance/CPUSchedulerMode;", "h", "l", "(Lcom/ayaneo/settings/ui/performance/CPUSchedulerMode;)V", "Ljava/util/List;", "g", "k", "(Ljava/util/List;)V", "Lcom/ayaneo/settings/ui/performance/GPUFrequency;", "j", "n", "(Lcom/ayaneo/settings/ui/performance/GPUFrequency;)V", "app_QCOMRelease"}, k = 1, mv = {2, 0, 0})
public final /* data */ class ModeConfiguration {

    @SerializedName("cpuFrequencies")
    @NotNull
    private List<CPUFrequency> cpuFrequencies;

    @SerializedName("cpuSchedulerMode")
    @NotNull
    private CPUSchedulerMode cpuSchedulerMode;

    @SerializedName("fanMode")
    @NotNull
    private FAN_MODE fanMode;

    @SerializedName("gpuFrequency")
    @NotNull
    private GPUFrequency gpuFrequency;

    public ModeConfiguration(@NotNull FAN_MODE fanMode, @NotNull CPUSchedulerMode cpuSchedulerMode, @NotNull List<CPUFrequency> cpuFrequencies, @NotNull GPUFrequency gpuFrequency) {
        Intrinsics.p(fanMode, "fanMode");
        Intrinsics.p(cpuSchedulerMode, "cpuSchedulerMode");
        Intrinsics.p(cpuFrequencies, "cpuFrequencies");
        Intrinsics.p(gpuFrequency, "gpuFrequency");
        this.fanMode = fanMode;
        this.cpuSchedulerMode = cpuSchedulerMode;
        this.cpuFrequencies = cpuFrequencies;
        this.gpuFrequency = gpuFrequency;
    }

    /* JADX WARN: Multi-variable type inference failed */
    public static /* synthetic */ ModeConfiguration f(ModeConfiguration modeConfiguration, FAN_MODE fan_mode, CPUSchedulerMode cPUSchedulerMode, List list, GPUFrequency gPUFrequency, int i2, Object obj) {
        if ((i2 & 1) != 0) {
            fan_mode = modeConfiguration.fanMode;
        }
        if ((i2 & 2) != 0) {
            cPUSchedulerMode = modeConfiguration.cpuSchedulerMode;
        }
        if ((i2 & 4) != 0) {
            list = modeConfiguration.cpuFrequencies;
        }
        if ((i2 & 8) != 0) {
            gPUFrequency = modeConfiguration.gpuFrequency;
        }
        return modeConfiguration.e(fan_mode, cPUSchedulerMode, list, gPUFrequency);
    }

    @NotNull
    /* JADX INFO: renamed from: a, reason: from getter */
    public final FAN_MODE getFanMode() {
        return this.fanMode;
    }

    @NotNull
    /* JADX INFO: renamed from: b, reason: from getter */
    public final CPUSchedulerMode getCpuSchedulerMode() {
        return this.cpuSchedulerMode;
    }

    @NotNull
    public final List<CPUFrequency> c() {
        return this.cpuFrequencies;
    }

    @NotNull
    /* JADX INFO: renamed from: d, reason: from getter */
    public final GPUFrequency getGpuFrequency() {
        return this.gpuFrequency;
    }

    @NotNull
    public final ModeConfiguration e(@NotNull FAN_MODE fanMode, @NotNull CPUSchedulerMode cpuSchedulerMode, @NotNull List<CPUFrequency> cpuFrequencies, @NotNull GPUFrequency gpuFrequency) {
        Intrinsics.p(fanMode, "fanMode");
        Intrinsics.p(cpuSchedulerMode, "cpuSchedulerMode");
        Intrinsics.p(cpuFrequencies, "cpuFrequencies");
        Intrinsics.p(gpuFrequency, "gpuFrequency");
        return new ModeConfiguration(fanMode, cpuSchedulerMode, cpuFrequencies, gpuFrequency);
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ModeConfiguration)) {
            return false;
        }
        ModeConfiguration modeConfiguration = (ModeConfiguration) other;
        return this.fanMode == modeConfiguration.fanMode && this.cpuSchedulerMode == modeConfiguration.cpuSchedulerMode && Intrinsics.g(this.cpuFrequencies, modeConfiguration.cpuFrequencies) && Intrinsics.g(this.gpuFrequency, modeConfiguration.gpuFrequency);
    }

    @NotNull
    public final List<CPUFrequency> g() {
        return this.cpuFrequencies;
    }

    @NotNull
    public final CPUSchedulerMode h() {
        return this.cpuSchedulerMode;
    }

    public int hashCode() {
        return this.gpuFrequency.hashCode() + ((this.cpuFrequencies.hashCode() + ((this.cpuSchedulerMode.hashCode() + (this.fanMode.hashCode() * 31)) * 31)) * 31);
    }

    @NotNull
    public final FAN_MODE i() {
        return this.fanMode;
    }

    @NotNull
    public final GPUFrequency j() {
        return this.gpuFrequency;
    }

    public final void k(@NotNull List<CPUFrequency> list) {
        Intrinsics.p(list, "<set-?>");
        this.cpuFrequencies = list;
    }

    public final void l(@NotNull CPUSchedulerMode cPUSchedulerMode) {
        Intrinsics.p(cPUSchedulerMode, "<set-?>");
        this.cpuSchedulerMode = cPUSchedulerMode;
    }

    public final void m(@NotNull FAN_MODE fan_mode) {
        Intrinsics.p(fan_mode, "<set-?>");
        this.fanMode = fan_mode;
    }

    public final void n(@NotNull GPUFrequency gPUFrequency) {
        Intrinsics.p(gPUFrequency, "<set-?>");
        this.gpuFrequency = gPUFrequency;
    }

    @NotNull
    public String toString() {
        return "ModeConfiguration(fanMode=" + this.fanMode + ", cpuSchedulerMode=" + this.cpuSchedulerMode + ", cpuFrequencies=" + this.cpuFrequencies + ", gpuFrequency=" + this.gpuFrequency + MotionUtils.f18953d;
    }
}
