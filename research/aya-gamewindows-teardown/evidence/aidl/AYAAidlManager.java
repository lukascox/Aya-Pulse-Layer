package com.ayaneo.gamewindow.utils.aidl;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import com.ayaneo.devices.AyaDevicesKt;
import com.ayaneo.gamewindow.ui.window.controller.ABXYMode;
import com.ayaneo.gamewindow.ui.window.controller.DirectionDpadMode;
import com.ayaneo.gamewindow.ui.window.controller.other.ControllerSerialManagerKt;
import com.ayaneo.gamewindow.utils.system.AyaShareConfUtilKt;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.CharsKt;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/* JADX INFO: compiled from: AYAAidlManager.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000)\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\n\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\f*\u0001\u000f\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002J\u0006\u0010\u0013\u001a\u00020\u0014J\u0006\u0010\u0015\u001a\u00020\u0014J\u0006\u0010\u0016\u001a\u00020\u0014J\u0010\u0010\u0017\u001a\u00020\u00142\b\b\u0002\u0010\u0018\u001a\u00020\u0004J\u000e\u0010\u0019\u001a\u00020\u00142\u0006\u0010\u001a\u001a\u00020\u0004J\u0006\u0010\u001b\u001a\u00020\u0014J\u001e\u0010\u001c\u001a\u00020\u00142\u0006\u0010\u001d\u001a\u00020\u00042\u0006\u0010\u001e\u001a\u00020\u00042\u0006\u0010\u001f\u001a\u00020\u0004R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u0010\u0010\u000e\u001a\u00020\u000fX\u0082\u0004¢\u0006\u0004\n\u0002\u0010\u0010R\u0010\u0010\u0011\u001a\u0004\u0018\u00010\u0012X\u0082\u000e¢\u0006\u0002\n\u0000¨\u0006 "}, d2 = {"Lcom/ayaneo/gamewindow/utils/aidl/AYAAidlManager;", "", "()V", "CLIENT_ID_UNKNOWN", "", "LOCAL_BINDER_ACTION", "MSG_TYPE_CLOSE_WIFI", "MSG_TYPE_CONTROLLER", "MSG_TYPE_PERFORMANCE", "MSG_TYPE_REGISTER", "MSG_TYPE_RGB", "MSG_TYPE_SYSTEM", "MSG_TYPE_UNKNOWN", "TAG", "mConnection", "com/ayaneo/gamewindow/utils/aidl/AYAAidlManager$mConnection$1", "Lcom/ayaneo/gamewindow/utils/aidl/AYAAidlManager$mConnection$1;", "mService", "Lcom/ayaneo/gamewindow/utils/aidl/AyaAidlService;", "bindAidlService", "", "broadcastAbxyMode", "broadcastDirectionDpadMode", "broadcastL1L2R1R2Mode", "mode", "broadcastPerformanceJson", "json", "broadcastTouchDpadMode", "dealMsg", "clientId", "tag", "msg", "gamewindow_QCOMRelease"}, k = 1, mv = {1, 9, 0}, xi = 48)
public final class AYAAidlManager {

    /* JADX INFO: renamed from: b, reason: collision with root package name */
    @Nullable
    public static AyaAidlService f5973b;

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    @NotNull
    public static final AYAAidlManager f5972a = new AYAAidlManager();

    /* JADX INFO: renamed from: c, reason: collision with root package name */
    @NotNull
    public static final AYAAidlManager$mConnection$1 f5974c = new ServiceConnection() { // from class: com.ayaneo.gamewindow.utils.aidl.AYAAidlManager$mConnection$1
        @Override // android.content.ServiceConnection
        public final void onServiceConnected(@NotNull ComponentName className, @NotNull IBinder service) {
            Intrinsics.e("className", className);
            Intrinsics.e("service", service);
            AYAAidlManager aYAAidlManager = AYAAidlManager.f5972a;
            AYAAidlManager.f5973b = AyaAidlService.this;
        }

        @Override // android.content.ServiceConnection
        public final void onServiceDisconnected(@NotNull ComponentName className) {
            Intrinsics.e("className", className);
        }
    };

    public static void a() {
        ABXYMode aBXYMode = CharsKt.d(ControllerSerialManagerKt.a().charAt(14)) == AyaDevicesKt.f4814a.getR() ? ABXYMode.STANDARD : ABXYMode.JAPAN;
        AyaAidlService ayaAidlService = f5973b;
        if (ayaAidlService != null) {
            ayaAidlService.a("msg_type_controller", "com_get_abxy_mode:" + aBXYMode);
        }
    }

    public static void b() {
        DirectionDpadMode directionDpadMode = DirectionDpadMode.DIRECTION;
        String strD = AyaShareConfUtilKt.d(AyaShareConfUtilKt.b("aya_key_direction_key.conf", directionDpadMode.getCmd()), directionDpadMode.getCmd());
        AyaAidlService ayaAidlService = f5973b;
        if (ayaAidlService != null) {
            ayaAidlService.a("msg_type_controller", "com_get_direction_dpad_mode:".concat(strD));
        }
    }

    public static void c(@NotNull String str) {
        Object objValueOf;
        Intrinsics.e("mode", str);
        AyaAidlService ayaAidlService = f5973b;
        if (ayaAidlService != null) {
            if (Intrinsics.a(str, "")) {
                objValueOf = str;
                objValueOf = Character.valueOf(StringsKt.A(ControllerSerialManagerKt.a()));
            }
            objValueOf = str;
            ayaAidlService.a("msg_type_controller", "com_get_l1l2r1r2_mode:" + objValueOf);
        }
    }
}
