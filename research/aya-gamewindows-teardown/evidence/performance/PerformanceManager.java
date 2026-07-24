package com.ayaneo.gamewindow.ui.window.performance.util;

import a.a;
import com.ayaneo.AyaDevicesUtil;
import com.ayaneo.devices.AyaDevicesKt;
import com.ayaneo.devices.IAyaDevices;
import com.ayaneo.gamewindow.LauncherApp;
import com.ayaneo.gamewindow.R;
import com.ayaneo.gamewindow.utils.FAN_MODE;
import com.ayaneo.gamewindow.utils.aidl.AYAAidlManager;
import com.ayaneo.gamewindow.utils.aidl.AyaAidlService;
import com.ayaneo.gamewindow.utils.system.AyaShareConfUtilKt;
import com.ayaneo.provider.AyaSettingProvider;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import java.util.Map;
import kotlin.Metadata;
import kotlin.NoWhenBranchMatchedException;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import timber.log.Timber;

/* JADX INFO: compiled from: PerformanceManager.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000V\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0006\n\u0002\u0010\u000e\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0010\n\u0002\u0018\u0002\n\u0002\b\f\bÆ\u0002\u0018\u00002\u00020\u0001:\u0001CB\u0007\b\u0002¢\u0006\u0002\u0010\u0002J\u0006\u0010\u0019\u001a\u00020\u001aJ\"\u0010\u001b\u001a\u00020\u001a2\u0006\u0010\u001c\u001a\u00020\u001d2\u0006\u0010\u001e\u001a\u00020\u000b2\b\b\u0002\u0010\u001f\u001a\u00020\u0015H\u0002J\u0018\u0010 \u001a\u00020\u001a2\u0006\u0010!\u001a\u00020\u00042\b\b\u0002\u0010\u001f\u001a\u00020\u0015J \u0010\"\u001a\u00020\u001a2\u0006\u0010!\u001a\u00020\u00042\u0006\u0010#\u001a\u00020$2\b\b\u0002\u0010\u0014\u001a\u00020\u0015J\f\u0010%\u001a\b\u0012\u0004\u0012\u00020'0&J\b\u0010(\u001a\u00020\u001dH\u0002J\u0006\u0010)\u001a\u00020\u0004J\u000e\u0010*\u001a\u00020\u000b2\u0006\u0010!\u001a\u00020$J\u0006\u0010+\u001a\u00020\u0004J\u0010\u0010,\u001a\u00020\u00042\u0006\u0010-\u001a\u00020\u0004H\u0002J\u0006\u0010.\u001a\u00020\u0004J\u0006\u0010/\u001a\u00020\u001aJ\u0010\u00100\u001a\u00020\u001a2\b\b\u0002\u0010!\u001a\u00020\u0004J\u001a\u00101\u001a\u00020\u001a2\u0006\u00102\u001a\u00020\u001d2\b\b\u0002\u0010\u001f\u001a\u00020\u0015H\u0002J \u00103\u001a\u00020\u001a2\b\b\u0002\u0010!\u001a\u00020\u00042\u0006\u00104\u001a\u00020\u00042\u0006\u00105\u001a\u00020\u0004J\u0018\u00106\u001a\u00020\u001a2\b\b\u0002\u0010!\u001a\u00020\u00042\u0006\u00107\u001a\u000208J\"\u00109\u001a\u00020\u001a2\b\b\u0002\u0010!\u001a\u00020\u00042\u0006\u0010#\u001a\u00020$2\b\b\u0002\u0010:\u001a\u00020\u0015J\"\u0010;\u001a\u00020$2\b\b\u0002\u0010!\u001a\u00020\u00042\b\b\u0002\u0010:\u001a\u00020\u00152\u0006\u0010<\u001a\u00020\u0015J\u001a\u0010=\u001a\u00020$2\b\b\u0002\u0010!\u001a\u00020\u00042\b\b\u0002\u0010:\u001a\u00020\u0015J\u0018\u0010>\u001a\u00020\u001a2\b\b\u0002\u0010!\u001a\u00020\u00042\u0006\u0010?\u001a\u00020\u0004J\u0018\u0010@\u001a\u00020\u001a2\b\b\u0002\u0010!\u001a\u00020\u00042\u0006\u0010A\u001a\u00020\u0015J\u001a\u0010B\u001a\u00020$2\b\b\u0002\u0010!\u001a\u00020\u00042\b\b\u0002\u0010:\u001a\u00020\u0015R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T¢\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u0014\u0010\n\u001a\u00020\u000bX\u0086D¢\u0006\b\n\u0000\u001a\u0004\b\f\u0010\rR\u0014\u0010\u000e\u001a\u00020\u000bX\u0086D¢\u0006\b\n\u0000\u001a\u0004\b\u000f\u0010\rR\u0011\u0010\u0010\u001a\u00020\u0011¢\u0006\b\n\u0000\u001a\u0004\b\u0012\u0010\u0013R\u001a\u0010\u0014\u001a\u00020\u0015X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u0014\u0010\u0016\"\u0004\b\u0017\u0010\u0018¨\u0006D"}, d2 = {"Lcom/ayaneo/gamewindow/ui/window/performance/util/PerformanceManager;", "", "()V", "AR01_AUTO_FREQ", "", "MODE_BALANCE", "MODE_GAME", "MODE_MAX", "MODE_SAVING", "MODE_STREAMING", "OPEN_PERFORMANCE_MODE", "", "getOPEN_PERFORMANCE_MODE", "()Ljava/lang/String;", "TAG", "getTAG", "gson", "Lcom/google/gson/Gson;", "getGson", "()Lcom/google/gson/Gson;", "isFanForce", "", "()Z", "setFanForce", "(Z)V", "applyMaxPerformanceSkipFan", "", "applyModeConfiguration", "modeConfig", "Lcom/ayaneo/gamewindow/ui/window/performance/util/PerformanceManager$ConfigData;", "jsonData", "needBroadcast", "applyPerformance", "mode", "applyPerformanceX", "fanMode", "Lcom/ayaneo/gamewindow/utils/FAN_MODE;", "getCurrentCPUFrequencies", "", "Lcom/ayaneo/gamewindow/ui/window/performance/util/CPUFrequency;", "getCurrentConfigurations", "getCurrentMode", "getFanModeStr", "getLastMode", "getModeIndex", "index", "getNextMode", "initCurrentMode", "resetCurrentMode", "saveConfigurations", "configData", "setCPUFrequency", "cpuId", "frequency", "setCPUSchedulerMode", "schedulerMode", "Lcom/ayaneo/gamewindow/ui/window/performance/util/CPUSchedulerMode;", "setFanMode", "force", "setFanModeUpOrDown", "isUp", "setFanNextMode", "setGPUFrequency", "maxFrequency", "setGPUIsFixed", "isFixed", "switchFanEnable", "ConfigData", "gamewindow_QCOMRelease"}, k = 1, mv = {1, 9, 0}, xi = 48)
@SourceDebugExtension
public final class PerformanceManager {

    /* JADX INFO: renamed from: b, reason: collision with root package name */
    public static boolean f5865b;

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    @NotNull
    public static final PerformanceManager f5864a = new PerformanceManager();

    /* JADX INFO: renamed from: c, reason: collision with root package name */
    @NotNull
    public static final String f5866c = "aidl_window";

    /* JADX INFO: renamed from: d, reason: collision with root package name */
    @NotNull
    public static final Gson f5867d = new Gson();

    /* JADX INFO: compiled from: PerformanceManager.kt */
    @Metadata(d1 = {"\u0000(\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010%\n\u0002\u0010\b\n\u0002\u0018\u0002\n\u0002\b\f\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\u000e\n\u0000\b\u0086\b\u0018\u00002\u00020\u0001B!\u0012\u0012\u0010\u0002\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00050\u0003\u0012\u0006\u0010\u0006\u001a\u00020\u0004¢\u0006\u0002\u0010\u0007J\u0015\u0010\u000e\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00050\u0003HÆ\u0003J\t\u0010\u000f\u001a\u00020\u0004HÆ\u0003J)\u0010\u0010\u001a\u00020\u00002\u0014\b\u0002\u0010\u0002\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00050\u00032\b\b\u0002\u0010\u0006\u001a\u00020\u0004HÆ\u0001J\u0013\u0010\u0011\u001a\u00020\u00122\b\u0010\u0013\u001a\u0004\u0018\u00010\u0001HÖ\u0003J\t\u0010\u0014\u001a\u00020\u0004HÖ\u0001J\t\u0010\u0015\u001a\u00020\u0016HÖ\u0001R\u001e\u0010\u0006\u001a\u00020\u00048\u0006@\u0006X\u0087\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\b\u0010\t\"\u0004\b\n\u0010\u000bR\"\u0010\u0002\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00050\u00038\u0006X\u0087\u0004¢\u0006\b\n\u0000\u001a\u0004\b\f\u0010\r¨\u0006\u0017"}, d2 = {"Lcom/ayaneo/gamewindow/ui/window/performance/util/PerformanceManager$ConfigData;", "", "modeConfigurations", "", "", "Lcom/ayaneo/gamewindow/ui/window/performance/util/ModeConfiguration;", "currentMode", "(Ljava/util/Map;I)V", "getCurrentMode", "()I", "setCurrentMode", "(I)V", "getModeConfigurations", "()Ljava/util/Map;", "component1", "component2", "copy", "equals", "", "other", "hashCode", "toString", "", "gamewindow_QCOMRelease"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public static final /* data */ class ConfigData {

        @SerializedName("currentMode")
        private int currentMode;

        @SerializedName("modeConfigurations")
        @NotNull
        private final Map<Integer, ModeConfiguration> modeConfigurations;

        public ConfigData(@NotNull Map map) {
            Intrinsics.e("modeConfigurations", map);
            this.modeConfigurations = map;
            this.currentMode = 1;
        }

        /* JADX INFO: renamed from: a, reason: from getter */
        public final int getCurrentMode() {
            return this.currentMode;
        }

        @NotNull
        public final Map<Integer, ModeConfiguration> b() {
            return this.modeConfigurations;
        }

        public final void c(int i) {
            this.currentMode = i;
        }

        public final boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ConfigData)) {
                return false;
            }
            ConfigData configData = (ConfigData) other;
            return Intrinsics.a(this.modeConfigurations, configData.modeConfigurations) && this.currentMode == configData.currentMode;
        }

        public final int hashCode() {
            return Integer.hashCode(this.currentMode) + (this.modeConfigurations.hashCode() * 31);
        }

        @NotNull
        public final String toString() {
            return "ConfigData(modeConfigurations=" + this.modeConfigurations + ", currentMode=" + this.currentMode + ")";
        }
    }

    /* JADX INFO: compiled from: PerformanceManager.kt */
    @Metadata(k = 3, mv = {1, 9, 0}, xi = 48)
    public /* synthetic */ class WhenMappings {

        /* JADX INFO: renamed from: a, reason: collision with root package name */
        public static final /* synthetic */ int[] f5868a;

        static {
            int[] iArr = new int[FAN_MODE.values().length];
            try {
                iArr[FAN_MODE.FAN_MODE_OFF.ordinal()] = 1;
            } catch (NoSuchFieldError unused) {
            }
            try {
                iArr[FAN_MODE.FAN_MODE_MUTE.ordinal()] = 2;
            } catch (NoSuchFieldError unused2) {
            }
            try {
                iArr[FAN_MODE.FAN_MODE_BALANCE.ordinal()] = 3;
            } catch (NoSuchFieldError unused3) {
            }
            try {
                iArr[FAN_MODE.FAN_MODE_TURBO.ordinal()] = 4;
            } catch (NoSuchFieldError unused4) {
            }
            try {
                iArr[FAN_MODE.FAN_MODE_CUSTOM.ordinal()] = 5;
            } catch (NoSuchFieldError unused5) {
            }
            f5868a = iArr;
        }
    }

    public static void a(int i, boolean z) throws InterruptedException {
        Timber.f11226a.f(a.t(new StringBuilder(), f5866c, " setCurrentMode"), new Object[0]);
        ConfigData configDataB = b();
        configDataB.c(i);
        f(configDataB, z);
    }

    public static ConfigData b() {
        Timber.Forest forest = Timber.f11226a;
        forest.f("performance_tag getCurrentConfigurations", new Object[0]);
        IAyaDevices iAyaDevices = AyaDevicesKt.f4814a;
        String strC = AyaShareConfUtilKt.c(iAyaDevices.i1());
        if (strC == null || strC.length() == 0) {
            forest.f("performance_tag jsonString.isNullOrEmpty", new Object[0]);
            return new ConfigData(iAyaDevices.r());
        }
        try {
            ConfigData configData = (ConfigData) f5867d.fromJson(strC, new TypeToken<ConfigData>() { // from class: com.ayaneo.gamewindow.ui.window.performance.util.PerformanceManager$getCurrentConfigurations$type$1
            }.getType());
            return configData == null ? new ConfigData(iAyaDevices.r()) : configData;
        } catch (Exception unused) {
            return new ConfigData(AyaDevicesKt.f4814a.r());
        }
    }

    public static int c() {
        return b().getCurrentMode();
    }

    @NotNull
    public static String d(@NotNull FAN_MODE fan_mode) {
        Intrinsics.e("mode", fan_mode);
        int i = WhenMappings.f5868a[fan_mode.ordinal()];
        if (i == 1) {
            LauncherApp.e.getClass();
            String string = LauncherApp.Companion.a().getString(R.string.closed);
            Intrinsics.d("getString(...)", string);
            return string;
        }
        if (i == 2) {
            LauncherApp.e.getClass();
            String string2 = LauncherApp.Companion.a().getString(R.string.mute);
            Intrinsics.d("getString(...)", string2);
            return string2;
        }
        if (i == 3) {
            LauncherApp.e.getClass();
            String string3 = LauncherApp.Companion.a().getString(R.string.balance);
            Intrinsics.d("getString(...)", string3);
            return string3;
        }
        if (i == 4) {
            LauncherApp.e.getClass();
            String string4 = LauncherApp.Companion.a().getString(R.string.hurricane);
            Intrinsics.d("getString(...)", string4);
            return string4;
        }
        if (i != 5) {
            throw new NoWhenBranchMatchedException();
        }
        LauncherApp.e.getClass();
        String string5 = LauncherApp.Companion.a().getString(R.string.fan_personality);
        Intrinsics.d("getString(...)", string5);
        return string5;
    }

    public static int e() {
        int i;
        int iC = c() + 1;
        int iE = AyaSettingProvider.f6262c.e(31, "switch_performance_mode");
        if (iC > 4) {
            iC = 0;
        }
        if (!AyaDevicesKt.f4814a.getK()) {
            if (iC == 2) {
                iC++;
            } else {
                iC++;
            }
        }
        while (true) {
            if (iC == 0) {
                i = 1;
            } else if (iC == 1) {
                i = 2;
            } else if (iC == 2) {
                i = 4;
            } else {
                i = iC == 3 ? 8 : 16;
            }
            if ((i & iE) != 0) {
                return iC;
            }
            iC++;
            if (iC > 4) {
                iC = 0;
            }
            if (!AyaDevicesKt.f4814a.getK() && iC == 2) {
                iC++;
            }
        }
    }

    public static void f(ConfigData configData, boolean z) throws InterruptedException {
        Timber.Forest forest = Timber.f11226a;
        StringBuilder sb = new StringBuilder();
        String str = f5866c;
        forest.f(a.t(sb, str, " saveConfigurations"), new Object[0]);
        Gson gson = f5867d;
        String json = gson.toJson(configData);
        Intrinsics.b(json);
        Thread.sleep(50L);
        ModeConfiguration modeConfiguration = configData.b().get(Integer.valueOf(configData.getCurrentMode()));
        if (modeConfiguration != null) {
            IAyaDevices iAyaDevices = AyaDevicesKt.f4814a;
            String strB = AyaShareConfUtilKt.b(iAyaDevices.i1(), "");
            forest.f(str + " needBroadcast = " + z, new Object[0]);
            if (z) {
                AYAAidlManager.f5972a.getClass();
                f5864a.getClass();
                forest.f(str + " broadcastPerformanceJson", new Object[0]);
                AyaAidlService ayaAidlService = AYAAidlManager.f5973b;
                if (ayaAidlService != null) {
                    ayaAidlService.a("msg_type_performance", "com_set_performance_mode:".concat(json));
                }
                forest.f(a.m(str, " write jsonData = ", json), new Object[0]);
                AyaShareConfUtilKt.e(iAyaDevices.i1(), json);
            }
            Thread.sleep(50L);
            if (strB == null || strB.length() == 0) {
                iAyaDevices.t(modeConfiguration.getFanMode(), f5865b);
                AyaDevicesUtil ayaDevicesUtil = AyaDevicesUtil.f4807a;
                CPUSchedulerMode cpuSchedulerMode = modeConfiguration.getCpuSchedulerMode();
                ayaDevicesUtil.getClass();
                AyaDevicesUtil.b(cpuSchedulerMode);
                AyaDevicesUtil.a(modeConfiguration.a(), modeConfiguration.getCpuSchedulerMode() == CPUSchedulerMode.HIGH_PERFORMANCE);
                AyaDevicesUtil.c(modeConfiguration.getGpuFrequency());
                return;
            }
            Object objFromJson = gson.fromJson(strB, new TypeToken<ConfigData>() { // from class: com.ayaneo.gamewindow.ui.window.performance.util.PerformanceManager$applyModeConfiguration$localConfig$1
            }.getType());
            Intrinsics.d("fromJson(...)", objFromJson);
            ConfigData configData2 = (ConfigData) objFromJson;
            ModeConfiguration modeConfiguration2 = configData2.b().get(Integer.valueOf(configData2.getCurrentMode()));
            Thread.sleep(50L);
            if ((modeConfiguration2 != null ? modeConfiguration2.getFanMode() : null) != modeConfiguration.getFanMode()) {
                iAyaDevices.t(modeConfiguration.getFanMode(), f5865b);
            }
            AyaDevicesUtil ayaDevicesUtil2 = AyaDevicesUtil.f4807a;
            CPUSchedulerMode cpuSchedulerMode2 = modeConfiguration.getCpuSchedulerMode();
            ayaDevicesUtil2.getClass();
            AyaDevicesUtil.b(cpuSchedulerMode2);
            AyaDevicesUtil.a(modeConfiguration.a(), modeConfiguration.getCpuSchedulerMode() == CPUSchedulerMode.HIGH_PERFORMANCE);
            AyaDevicesUtil.c(modeConfiguration.getGpuFrequency());
        }
    }

    public static void g(PerformanceManager performanceManager, FAN_MODE fan_mode, boolean z, int i) throws InterruptedException {
        int iC;
        FAN_MODE fanMode;
        ModeConfiguration modeConfiguration;
        if ((i & 1) != 0) {
            performanceManager.getClass();
            iC = c();
        } else {
            iC = 0;
        }
        if ((i & 4) != 0) {
            z = false;
        }
        performanceManager.getClass();
        Intrinsics.e("fanMode", fan_mode);
        f5865b = z;
        ConfigData configDataB = b();
        ModeConfiguration modeConfiguration2 = configDataB.b().get(Integer.valueOf(iC));
        if (modeConfiguration2 != null && (fanMode = modeConfiguration2.getFanMode()) != null && (modeConfiguration = configDataB.b().get(Integer.valueOf(iC))) != null) {
            modeConfiguration.h(fanMode);
        }
        ModeConfiguration modeConfiguration3 = configDataB.b().get(Integer.valueOf(iC));
        if (modeConfiguration3 != null) {
            modeConfiguration3.g(fan_mode);
        }
        Timber.Forest forest = Timber.f11226a;
        ModeConfiguration modeConfiguration4 = configDataB.b().get(Integer.valueOf(iC));
        FAN_MODE lastFanMode = modeConfiguration4 != null ? modeConfiguration4.getLastFanMode() : null;
        ModeConfiguration modeConfiguration5 = configDataB.b().get(Integer.valueOf(iC));
        forest.f("t912 from " + lastFanMode + " -> " + (modeConfiguration5 != null ? modeConfiguration5.getFanMode() : null), new Object[0]);
        f(configDataB, true);
    }

    public static FAN_MODE h(PerformanceManager performanceManager, boolean z) throws InterruptedException {
        FAN_MODE fan_mode;
        FAN_MODE fanMode;
        ModeConfiguration modeConfiguration;
        performanceManager.getClass();
        int iC = c();
        performanceManager.getClass();
        f5865b = false;
        ConfigData configDataB = b();
        ModeConfiguration modeConfiguration2 = configDataB.b().get(Integer.valueOf(iC));
        FAN_MODE fanMode2 = modeConfiguration2 != null ? modeConfiguration2.getFanMode() : null;
        int i = fanMode2 == null ? -1 : WhenMappings.f5868a[fanMode2.ordinal()];
        if (i == -1) {
            fan_mode = FAN_MODE.FAN_MODE_OFF;
        } else if (i == 1) {
            fan_mode = z ? FAN_MODE.FAN_MODE_MUTE : FAN_MODE.FAN_MODE_OFF;
        } else if (i == 2) {
            fan_mode = z ? FAN_MODE.FAN_MODE_BALANCE : FAN_MODE.FAN_MODE_OFF;
        } else if (i == 3) {
            fan_mode = z ? FAN_MODE.FAN_MODE_TURBO : FAN_MODE.FAN_MODE_MUTE;
        } else if (i == 4) {
            fan_mode = z ? FAN_MODE.FAN_MODE_CUSTOM : FAN_MODE.FAN_MODE_BALANCE;
        } else {
            if (i != 5) {
                throw new NoWhenBranchMatchedException();
            }
            fan_mode = z ? FAN_MODE.FAN_MODE_CUSTOM : FAN_MODE.FAN_MODE_TURBO;
        }
        ModeConfiguration modeConfiguration3 = configDataB.b().get(Integer.valueOf(iC));
        if (modeConfiguration3 != null && (fanMode = modeConfiguration3.getFanMode()) != null && (modeConfiguration = configDataB.b().get(Integer.valueOf(iC))) != null) {
            modeConfiguration.h(fanMode);
        }
        ModeConfiguration modeConfiguration4 = configDataB.b().get(Integer.valueOf(iC));
        if (modeConfiguration4 != null) {
            modeConfiguration4.g(fan_mode);
        }
        f(configDataB, true);
        return fan_mode;
    }
}
