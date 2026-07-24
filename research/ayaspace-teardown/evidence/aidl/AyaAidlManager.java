package com.ayaneo.settings.utils.aidl;

import android.content.Intent;
import android.os.RemoteException;
import androidx.concurrent.futures.b;
import androidx.core.graphics.PaintCompat;
import com.ayaneo.gamewindow.AyaAidlCallback;
import com.ayaneo.gamewindow.AyaAidlInterface;
import com.ayaneo.settings.LauncherApp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kotlin.Metadata;
import kotlin.Pair;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;
import kotlinx.coroutines.BuildersKt__Builders_commonKt;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import timber.log.Timber;

/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000R\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0016\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010%\n\u0002\b\u0003\n\u0002\b\u0004\n\u0002\b\u0005*\u00026:\bÆ\u0002\u0018\u00002\u00020\u0001B\t\b\u0002¢\u0006\u0004\b\u0002\u0010\u0003J\u0015\u0010\u0007\u001a\u00020\u00062\u0006\u0010\u0005\u001a\u00020\u0004¢\u0006\u0004\b\u0007\u0010\bJ\u001d\u0010\n\u001a\u00020\u00062\u0006\u0010\t\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0004¢\u0006\u0004\b\n\u0010\u000bJ\r\u0010\f\u001a\u00020\u0006¢\u0006\u0004\b\f\u0010\u0003J\r\u0010\r\u001a\u00020\u0006¢\u0006\u0004\b\r\u0010\u0003J\u001d\u0010\u0010\u001a\u00020\u00062\u0006\u0010\t\u001a\u00020\u00042\u0006\u0010\u000f\u001a\u00020\u000e¢\u0006\u0004\b\u0010\u0010\u0011J\u0017\u0010\u0012\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0004H\u0002¢\u0006\u0004\b\u0012\u0010\u0013R\u0014\u0010\u0016\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\u0014\u0010\u0015R\u0014\u0010\u0018\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\u0017\u0010\u0015R\u0014\u0010\u001a\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\u0019\u0010\u0015R\u0014\u0010\u001c\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\u001b\u0010\u0015R\u0014\u0010\u001e\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\u001d\u0010\u0015R\u0014\u0010 \u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\u001f\u0010\u0015R\u0014\u0010!\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\f\u0010\u0015R\u0014\u0010\"\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\u0012\u0010\u0015R\u0014\u0010#\u001a\u00020\u00048\u0006X\u0086T¢\u0006\u0006\n\u0004\b\u0007\u0010\u0015R\u0014\u0010$\u001a\u00020\u00048\u0002X\u0082D¢\u0006\u0006\n\u0004\b\n\u0010\u0015R\u0018\u0010'\u001a\u0004\u0018\u00010%8\u0002@\u0002X\u0082\u000e¢\u0006\u0006\n\u0004\b\u0010\u0010&R\u0016\u0010*\u001a\u00020(8\u0002@\u0002X\u0082\u000e¢\u0006\u0006\n\u0004\b\r\u0010)R\u0016\u0010,\u001a\u00020\u00048\u0002@\u0002X\u0082\u000e¢\u0006\u0006\n\u0004\b+\u0010\u0015R&\u00101\u001a\u0014\u0012\u0010\u0012\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u00040.0-8\u0002X\u0082\u0004¢\u0006\u0006\n\u0004\b/\u00100R \u00105\u001a\u000e\u0012\u0004\u0012\u00020\u0004\u0012\u0004\u0012\u00020\u000e028\u0002X\u0082\u0004¢\u0006\u0006\n\u0004\b3\u00104R\u0014\u00109\u001a\u0002068\u0002X\u0082\u0004¢\u0006\u0006\n\u0004\b7\u00108R\u0014\u0010=\u001a\u00020:8\u0002X\u0082\u0004¢\u0006\u0006\n\u0004\b;\u0010<¨\u0006>"}, d2 = {"Lcom/ayaneo/settings/utils/aidl/AyaAidlManager;", "", "<init>", "()V", "", "message", "", "j", "(Ljava/lang/String;)V", "type", "k", "(Ljava/lang/String;Ljava/lang/String;)V", "h", PaintCompat.f4353b, "Lcom/ayaneo/gamewindow/AyaAidlCallback;", "callback", "l", "(Ljava/lang/String;Lcom/ayaneo/gamewindow/AyaAidlCallback;)V", "i", "(Ljava/lang/String;)Ljava/lang/String;", "b", "Ljava/lang/String;", "MSG_TYPE_REGISTER", "c", "MSG_TYPE_PERFORMANCE", "d", "MSG_TYPE_RGB", "e", "MSG_TYPE_CONTROLLER", "f", "MSG_TYPE_DEVICE", "g", "MSG_TYPE_UNKNOWN", "MSG_TYPE_FAN", "MSG_TYPE_CLOSE_WIFI", "MSG_TYPE_SYSTEM", "TAG", "Lcom/ayaneo/gamewindow/AyaAidlInterface;", "Lcom/ayaneo/gamewindow/AyaAidlInterface;", "mService", "", "Z", "mBound", "n", "clientId", "", "Lkotlin/Pair;", "o", "Ljava/util/List;", "messageQueue", "", "p", "Ljava/util/Map;", "callbackMap", "com/ayaneo/settings/utils/aidl/AyaAidlManager$globalCallback$1", "q", "Lcom/ayaneo/settings/utils/aidl/AyaAidlManager$globalCallback$1;", "globalCallback", "com/ayaneo/settings/utils/aidl/AyaAidlManager$mConnection$1", "r", "Lcom/ayaneo/settings/utils/aidl/AyaAidlManager$mConnection$1;", "mConnection", "app_QCOMRelease"}, k = 1, mv = {2, 0, 0})
public final class AyaAidlManager {

    /* JADX INFO: renamed from: b, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String MSG_TYPE_REGISTER = "msg_type_register";

    /* JADX INFO: renamed from: c, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String MSG_TYPE_PERFORMANCE = "msg_type_performance";

    /* JADX INFO: renamed from: d, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String MSG_TYPE_RGB = "msg_type_rgb";

    /* JADX INFO: renamed from: e, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String MSG_TYPE_CONTROLLER = "msg_type_controller";

    /* JADX INFO: renamed from: f, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String MSG_TYPE_DEVICE = "msg_type_device";

    /* JADX INFO: renamed from: g, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String MSG_TYPE_UNKNOWN = "msg_type_unknown";

    /* JADX INFO: renamed from: h, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String MSG_TYPE_FAN = "msg_type_fan";

    /* JADX INFO: renamed from: i, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String MSG_TYPE_CLOSE_WIFI = "msg_type_close_wifi";

    /* JADX INFO: renamed from: j, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String MSG_TYPE_SYSTEM = "msg_type_system";

    /* JADX INFO: renamed from: k, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String TAG = "aidl_settings";

    /* JADX INFO: renamed from: l, reason: collision with root package name and from kotlin metadata */
    @Nullable
    public static AyaAidlInterface mService = null;

    /* JADX INFO: renamed from: m, reason: collision with root package name and from kotlin metadata */
    public static boolean mBound = false;

    /* JADX INFO: renamed from: n, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static String clientId = "";

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    @NotNull
    public static final AyaAidlManager f17275a = new AyaAidlManager();

    /* JADX INFO: renamed from: o, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final List<Pair<String, String>> messageQueue = new ArrayList();

    /* JADX INFO: renamed from: p, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final Map<String, AyaAidlCallback> callbackMap = new LinkedHashMap();

    /* JADX INFO: renamed from: q, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final AyaAidlManager$globalCallback$1 globalCallback = new AyaAidlManager$globalCallback$1();

    /* JADX INFO: renamed from: r, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final AyaAidlManager$mConnection$1 mConnection = new AyaAidlManager$mConnection$1();

    public final void h() {
        if (mBound) {
            return;
        }
        Intent intent = new Intent();
        intent.setClassName(LauncherApp.f14073k, LauncherApp.f14074l);
        LauncherApp.INSTANCE.a().bindService(intent, mConnection, 1);
    }

    public final String i(String message) {
        return StringsKt.W2(message, ":", false, 2, null) ? StringsKt.z5(message, ":", null, 2, null) : MSG_TYPE_UNKNOWN;
    }

    public final void j(@NotNull String message) throws RemoteException {
        Intrinsics.p(message, "message");
        String strI = i(message);
        if (Intrinsics.g(strI, MSG_TYPE_REGISTER)) {
            clientId = StringsKt.r5(message, ":", null, 2, null);
            Timber.INSTANCE.k(b.a(TAG, " clientId = ", clientId), new Object[0]);
            return;
        }
        String strR5 = StringsKt.r5(message, ":", null, 2, null);
        Timber.Companion companion = Timber.INSTANCE;
        String str = TAG;
        String str2 = clientId;
        Map<String, AyaAidlCallback> map = callbackMap;
        companion.k(str + " client<" + str2 + "> receive -> " + strR5 + " | type = " + strI + " | callbackMap[type] = " + map.get(strI), new Object[0]);
        AyaAidlCallback ayaAidlCallback = map.get(strI);
        if (ayaAidlCallback != null) {
            ayaAidlCallback.v(strR5);
        }
    }

    public final void k(@NotNull String type, @NotNull String message) {
        Intrinsics.p(type, "type");
        Intrinsics.p(message, "message");
        BuildersKt__Builders_commonKt.f(GlobalScope.f24604a, Dispatchers.c(), null, new AyaAidlManager$sendAidlMsg$1(type, message, null), 2, null);
    }

    public final void l(@NotNull String type, @NotNull AyaAidlCallback callback) {
        Intrinsics.p(type, "type");
        Intrinsics.p(callback, "callback");
        callbackMap.put(type, callback);
    }

    public final void m() {
        if (mBound) {
            try {
                AyaAidlInterface ayaAidlInterface = mService;
                if (ayaAidlInterface != null) {
                    ayaAidlInterface.q(globalCallback);
                }
            } catch (RemoteException e2) {
                e2.printStackTrace();
            }
            LauncherApp.INSTANCE.a().unbindService(mConnection);
            mBound = false;
        }
    }
}
