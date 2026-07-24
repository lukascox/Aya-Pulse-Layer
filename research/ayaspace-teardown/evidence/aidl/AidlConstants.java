package com.ayaneo.settings.utils.aidl;

import androidx.core.graphics.PaintCompat;
import androidx.exifinterface.media.ExifInterface;
import kotlin.Metadata;
import org.jetbrains.annotations.NotNull;

/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000\u0014\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b6\bÆ\u0002\u0018\u00002\u00020\u0001B\t\b\u0002¢\u0006\u0004\b\u0002\u0010\u0003R\u0014\u0010\u0007\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\u0005\u0010\u0006R\u0014\u0010\t\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\b\u0010\u0006R\u0014\u0010\u000b\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\n\u0010\u0006R\u0014\u0010\r\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\f\u0010\u0006R\u0014\u0010\u000f\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\u000e\u0010\u0006R\u0014\u0010\u0011\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\u0010\u0010\u0006R\u0014\u0010\u0013\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\u0012\u0010\u0006R\u0014\u0010\u0015\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\u0014\u0010\u0006R\u0014\u0010\u0017\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\u0016\u0010\u0006R\u0014\u0010\u0019\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\u0018\u0010\u0006R\u0014\u0010\u001b\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\u001a\u0010\u0006R\u0014\u0010\u001d\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\u001c\u0010\u0006R\u0014\u0010\u001f\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\u001e\u0010\u0006R\u0014\u0010!\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b \u0010\u0006R\u0014\u0010#\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\"\u0010\u0006R\u0014\u0010%\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b$\u0010\u0006R\u0014\u0010'\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b&\u0010\u0006R\u0014\u0010)\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b(\u0010\u0006R\u0014\u0010+\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b*\u0010\u0006R\u0014\u0010-\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b,\u0010\u0006R\u0014\u0010/\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b.\u0010\u0006R\u0014\u00101\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b0\u0010\u0006R\u0014\u00103\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b2\u0010\u0006R\u0014\u00105\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b4\u0010\u0006R\u0014\u00107\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b6\u0010\u0006R\u0014\u00109\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b8\u0010\u0006¨\u0006:"}, d2 = {"Lcom/ayaneo/settings/utils/aidl/AidlConstants;", "", "<init>", "()V", "", "b", "Ljava/lang/String;", "COM_SET_PERFORMANCE_MODE", "c", "COM_SET_PERFORMANCE_RESET", "d", "COM_SET_PERFORMANCE_FAN", "e", "COM_SET_PERFORMANCE_CPU", "f", "COM_SET_PERFORMANCE_SCHEDULER", "g", "COM_SET_PERFORMANCE_GPU", "h", "COM_SET_PERFORMANCE_GPU_IS_FIXED", "i", "COM_SET_CONTROLLER_STATE", "j", "COM_SET_CONTROLLER_STYLE", "k", "COM_SET_KEY_MOUSE_QUICK_KEY", "l", "COM_GET_SINGLE_KEY_MAPPING", PaintCompat.f4353b, "COM_SET_SINGLE_KEY_MAPPING", "n", "COM_INIT_LOCAL_FIRMWARE_VERSION", "o", "COM_SET_RGB_IS_OPEN", "p", "COM_GET_ABXY_MODE", "q", "COM_GET_L1L2R1R2_MODE", "r", "COM_GET_DIRECTION_DPAD_MODE", "s", "COM_SET_DIRECTION_DPAD_MODE", "t", "COM_SET_ABXY_MODE", "u", "COM_SET_L1L2R1R2_MODE", "v", "COM_WIFI_CLOSE", "w", "COM_SET_FAN_SPEED_STRATEGY", "x", "COM_SET_FAN_SPEED_IS_LINEAR", "y", "WIFI_CLOSE_CACHE_FILE", "z", "COM_APK_INSTALL_DONE", ExifInterface.W4, "COM_SHOW_MAGIC_TOUCH", "app_QCOMRelease"}, k = 1, mv = {2, 0, 0})
public final class AidlConstants {

    /* JADX INFO: renamed from: A, reason: from kotlin metadata */
    @NotNull
    public static final String COM_SHOW_MAGIC_TOUCH = "com_show_magic_touch";

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    @NotNull
    public static final AidlConstants f17254a = new AidlConstants();

    /* JADX INFO: renamed from: b, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String COM_SET_PERFORMANCE_MODE = "com_set_performance_mode";

    /* JADX INFO: renamed from: c, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String COM_SET_PERFORMANCE_RESET = "com_set_performance_reset";

    /* JADX INFO: renamed from: d, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String COM_SET_PERFORMANCE_FAN = "com_set_performance_fan";

    /* JADX INFO: renamed from: e, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String COM_SET_PERFORMANCE_CPU = "com_set_performance_cpu";

    /* JADX INFO: renamed from: f, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String COM_SET_PERFORMANCE_SCHEDULER = "com_set_performance_scheduler";

    /* JADX INFO: renamed from: g, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String COM_SET_PERFORMANCE_GPU = "com_set_performance_gpu";

    /* JADX INFO: renamed from: h, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String COM_SET_PERFORMANCE_GPU_IS_FIXED = "com_set_performance_gpu_is_fixed";

    /* JADX INFO: renamed from: i, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String COM_SET_CONTROLLER_STATE = "com_set_controller_state";

    /* JADX INFO: renamed from: j, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String COM_SET_CONTROLLER_STYLE = "com_set_controller_style";

    /* JADX INFO: renamed from: k, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String COM_SET_KEY_MOUSE_QUICK_KEY = "com_set_key_mouse_quick_key";

    /* JADX INFO: renamed from: l, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String COM_GET_SINGLE_KEY_MAPPING = "com_get_single_key_mapping";

    /* JADX INFO: renamed from: m, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String COM_SET_SINGLE_KEY_MAPPING = "com_set_single_key_mapping";

    /* JADX INFO: renamed from: n, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String COM_INIT_LOCAL_FIRMWARE_VERSION = "com_init_local_firmware_version";

    /* JADX INFO: renamed from: o, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String COM_SET_RGB_IS_OPEN = "com_set_rgb_is_open";

    /* JADX INFO: renamed from: p, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String COM_GET_ABXY_MODE = "com_get_abxy_mode";

    /* JADX INFO: renamed from: q, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String COM_GET_L1L2R1R2_MODE = "com_get_l1l2r1r2_mode";

    /* JADX INFO: renamed from: r, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String COM_GET_DIRECTION_DPAD_MODE = "com_get_direction_dpad_mode";

    /* JADX INFO: renamed from: s, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String COM_SET_DIRECTION_DPAD_MODE = "com_set_direction_dpad_mode";

    /* JADX INFO: renamed from: t, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String COM_SET_ABXY_MODE = "com_set_abxy_mode";

    /* JADX INFO: renamed from: u, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String COM_SET_L1L2R1R2_MODE = "com_set_l1l2r1r2_mode";

    /* JADX INFO: renamed from: v, reason: from kotlin metadata */
    @NotNull
    public static final String COM_WIFI_CLOSE = "com_wifi_close";

    /* JADX INFO: renamed from: w, reason: from kotlin metadata */
    @NotNull
    public static final String COM_SET_FAN_SPEED_STRATEGY = "com_set_fan_speed_strategy";

    /* JADX INFO: renamed from: x, reason: from kotlin metadata */
    @NotNull
    public static final String COM_SET_FAN_SPEED_IS_LINEAR = "com_set_fan_speed_is_linear";

    /* JADX INFO: renamed from: y, reason: from kotlin metadata */
    @NotNull
    public static final String WIFI_CLOSE_CACHE_FILE = "TurnOffWifi.ini";

    /* JADX INFO: renamed from: z, reason: from kotlin metadata */
    @NotNull
    public static final String COM_APK_INSTALL_DONE = "com_apk_install_done";
}
