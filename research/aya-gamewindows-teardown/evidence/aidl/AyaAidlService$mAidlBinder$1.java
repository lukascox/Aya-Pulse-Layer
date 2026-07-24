package com.ayaneo.gamewindow.utils.aidl;

import com.ayaneo.gamewindow.AyaAidlInterface;
import java.util.List;
import kotlin.Metadata;
import kotlin.Triple;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import org.jetbrains.annotations.NotNull;
import timber.log.Timber;

/* JADX INFO: compiled from: AyaAidlService.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000!\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002*\u0001\u0000\b\n\u0018\u00002\u00020\u0001J\u0012\u0010\u0002\u001a\u00020\u00032\b\u0010\u0004\u001a\u0004\u0018\u00010\u0005H\u0016J\u0010\u0010\u0006\u001a\u00020\u00032\u0006\u0010\u0007\u001a\u00020\bH\u0016J\u0012\u0010\t\u001a\u00020\u00032\b\u0010\u0004\u001a\u0004\u0018\u00010\u0005H\u0016¨\u0006\n"}, d2 = {"com/ayaneo/gamewindow/utils/aidl/AyaAidlService$mAidlBinder$1", "Lcom/ayaneo/gamewindow/AyaAidlInterface$Stub;", "registerCallback", "", "callback", "Lcom/ayaneo/gamewindow/AyaAidlCallback;", "send", "data", "", "unregisterCallback", "gamewindow_QCOMRelease"}, k = 1, mv = {1, 9, 0}, xi = 48)
public final class AyaAidlService$mAidlBinder$1 extends AyaAidlInterface.Stub {

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    public final /* synthetic */ AyaAidlService f5980a;

    public AyaAidlService$mAidlBinder$1(AyaAidlService ayaAidlService) {
        this.f5980a = ayaAidlService;
    }

    public final void i0(@NotNull String str) {
        Intrinsics.e("data", str);
        int i = AyaAidlService.f5975d;
        this.f5980a.getClass();
        Timber.f11226a.f("aya_aidl_window receiveMsg -> ".concat(str), new Object[0]);
        List listO = StringsKt.O(str, new String[]{":"}, 3, 2);
        String str2 = (String) CollectionsKt.D(0, listO);
        if (str2 == null) {
            str2 = "client_id_unknown";
        }
        String str3 = (String) CollectionsKt.D(1, listO);
        if (str3 == null) {
            str3 = "";
        }
        String str4 = (String) CollectionsKt.D(2, listO);
        Triple triple = new Triple(str2, str3, str4 != null ? str4 : "");
        String str5 = (String) triple.getFirst();
        String str6 = (String) triple.getSecond();
        String str7 = (String) triple.getThird();
        AYAAidlManager.f5972a.getClass();
        Intrinsics.e("clientId", str5);
        Intrinsics.e("tag", str6);
        Intrinsics.e("msg", str7);
        BuildersKt.b(GlobalScope.f10144a, Dispatchers.f10129b, null, new AYAAidlManager$dealMsg$1(str7, str6, null), 2);
    }
}
