package com.ayaneo.settings.utils.aidl;

import androidx.core.app.NotificationCompat;
import androidx.versionedparcelable.ParcelUtils;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.NotNull;

/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000\u0010\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0005\u001a\u001d\u0010\u0004\u001a\u00020\u00032\u0006\u0010\u0001\u001a\u00020\u00002\u0006\u0010\u0002\u001a\u00020\u0000¢\u0006\u0004\b\u0004\u0010\u0005\u001a\u0015\u0010\u0006\u001a\u00020\u00002\u0006\u0010\u0001\u001a\u00020\u0000¢\u0006\u0004\b\u0006\u0010\u0007¨\u0006\b"}, d2 = {"", NotificationCompat.s0, "type", "", "b", "(Ljava/lang/String;Ljava/lang/String;)Z", ParcelUtils.f10526a, "(Ljava/lang/String;)Ljava/lang/String;", "app_QCOMRelease"}, k = 2, mv = {2, 0, 0})
public final class MessagePasserKt {
    @NotNull
    public static final String a(@NotNull String msg) {
        Intrinsics.p(msg, "msg");
        return StringsKt.r5(msg, ":", null, 2, null);
    }

    public static final boolean b(@NotNull String msg, @NotNull String type) {
        Intrinsics.p(msg, "msg");
        Intrinsics.p(type, "type");
        return Intrinsics.g(StringsKt.z5(msg, ":", null, 2, null), type);
    }
}
