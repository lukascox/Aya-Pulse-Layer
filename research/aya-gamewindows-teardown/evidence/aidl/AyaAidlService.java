package com.ayaneo.gamewindow.utils.aidl;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import com.ayaneo.gamewindow.ui.window.performance.util.PerformanceManager;
import java.util.LinkedHashMap;
import java.util.Map;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.SourceDebugExtension;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import org.jetbrains.annotations.NotNull;
import timber.log.Timber;

/* JADX INFO: compiled from: AyaAidlService.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000C\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010%\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0000\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\b*\u0001\n\u0018\u00002\u00020\u0001:\u0001\u001bB\u0005¢\u0006\u0002\u0010\u0002J\"\u0010\f\u001a\u0014\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\u00070\r2\u0006\u0010\u000e\u001a\u00020\u0007H\u0002J\u0010\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\u0012H\u0016J\u0010\u0010\u0013\u001a\u00020\u00142\u0006\u0010\u0015\u001a\u00020\u0007H\u0002J\u0016\u0010\u0016\u001a\u00020\u00142\u0006\u0010\u0017\u001a\u00020\u00072\u0006\u0010\u0018\u001a\u00020\u0007J \u0010\u0019\u001a\u00020\u00142\u0006\u0010\u001a\u001a\u00020\u00072\u0006\u0010\u0017\u001a\u00020\u00072\u0006\u0010\u0018\u001a\u00020\u0007H\u0002R\u0012\u0010\u0003\u001a\u00060\u0004R\u00020\u0000X\u0082\u0004¢\u0006\u0002\n\u0000R\u001a\u0010\u0005\u001a\u000e\u0012\u0004\u0012\u00020\u0007\u0012\u0004\u0012\u00020\b0\u0006X\u0082\u0004¢\u0006\u0002\n\u0000R\u0010\u0010\t\u001a\u00020\nX\u0082\u0004¢\u0006\u0004\n\u0002\u0010\u000b¨\u0006\u001c"}, d2 = {"Lcom/ayaneo/gamewindow/utils/aidl/AyaAidlService;", "Landroid/app/Service;", "()V", "binder", "Lcom/ayaneo/gamewindow/utils/aidl/AyaAidlService$LocalBinder;", "callbackIndexMap", "", "", "Lcom/ayaneo/gamewindow/AyaAidlCallback;", "mAidlBinder", "com/ayaneo/gamewindow/utils/aidl/AyaAidlService$mAidlBinder$1", "Lcom/ayaneo/gamewindow/utils/aidl/AyaAidlService$mAidlBinder$1;", "extractMessageComponents", "Lkotlin/Triple;", "message", "onBind", "Landroid/os/IBinder;", "intent", "Landroid/content/Intent;", "receiveMsg", "", "data", "sendBroadcastMsg", "type", "msg", "sendMsg", "clientId", "LocalBinder", "gamewindow_QCOMRelease"}, k = 1, mv = {1, 9, 0}, xi = 48)
@SourceDebugExtension
public final class AyaAidlService extends Service {

    /* JADX INFO: renamed from: d, reason: collision with root package name */
    public static final /* synthetic */ int f5975d = 0;

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    @NotNull
    public final LinkedHashMap f5976a = new LinkedHashMap();

    /* JADX INFO: renamed from: b, reason: collision with root package name */
    @NotNull
    public final LocalBinder f5977b = new LocalBinder();

    /* JADX INFO: renamed from: c, reason: collision with root package name */
    @NotNull
    public final AyaAidlService$mAidlBinder$1 f5978c = new AyaAidlService$mAidlBinder$1(this);

    /* JADX INFO: compiled from: AyaAidlService.kt */
    @Metadata(d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\b\u0086\u0004\u0018\u00002\u00020\u0001B\u0005¢\u0006\u0002\u0010\u0002J\u0006\u0010\u0003\u001a\u00020\u0004¨\u0006\u0005"}, d2 = {"Lcom/ayaneo/gamewindow/utils/aidl/AyaAidlService$LocalBinder;", "Landroid/os/Binder;", "(Lcom/ayaneo/gamewindow/utils/aidl/AyaAidlService;)V", "getService", "Lcom/ayaneo/gamewindow/utils/aidl/AyaAidlService;", "gamewindow_QCOMRelease"}, k = 1, mv = {1, 9, 0}, xi = 48)
    public final class LocalBinder extends Binder {
        public LocalBinder() {
        }
    }

    public final void a(@NotNull String str, @NotNull String str2) {
        Intrinsics.e("msg", str2);
        for (Map.Entry entry : this.f5976a.entrySet()) {
            String str3 = (String) entry.getKey();
            Timber.Forest forest = Timber.f11226a;
            PerformanceManager.f5864a.getClass();
            forest.f(PerformanceManager.f5866c + " sendBroadcastMsg to client<" + str3 + ">", new Object[0]);
            BuildersKt.b(GlobalScope.f10144a, Dispatchers.f10129b, null, new AyaAidlService$sendMsg$1(this, str3, str, str2, null), 2);
        }
    }

    @Override // android.app.Service
    @NotNull
    public final IBinder onBind(@NotNull Intent intent) {
        Intrinsics.e("intent", intent);
        return Intrinsics.a(intent.getAction(), "com.ayaneo.aidl.server") ? this.f5977b : this.f5978c;
    }
}
