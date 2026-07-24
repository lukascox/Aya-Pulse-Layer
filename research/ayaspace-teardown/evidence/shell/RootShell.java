package com.ayaneo.settings.utils.shell;

import androidx.constraintlayout.motion.widget.a;
import androidx.versionedparcelable.ParcelUtils;
import com.ayaneo.AyaDevicesUtilKt;
import kotlin.Metadata;
import kotlin.Result;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import timber.log.Timber;

/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0003\bÆ\u0002\u0018\u00002\u00020\u0001B\t\b\u0002¢\u0006\u0004\b\u0002\u0010\u0003J\u0015\u0010\u0006\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0004¢\u0006\u0004\b\u0006\u0010\u0007J\r\u0010\t\u001a\u00020\b¢\u0006\u0004\b\t\u0010\u0003J\r\u0010\n\u001a\u00020\b¢\u0006\u0004\b\n\u0010\u0003¨\u0006\u000b"}, d2 = {"Lcom/ayaneo/settings/utils/shell/RootShell;", "", "<init>", "()V", "", "cmd", ParcelUtils.f10526a, "(Ljava/lang/String;)Ljava/lang/String;", "", "b", "c", "app_QCOMRelease"}, k = 1, mv = {2, 0, 0})
public final class RootShell {

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    @NotNull
    public static final RootShell f17358a = new RootShell();

    @NotNull
    public final String a(@NotNull String cmd) {
        Intrinsics.p(cmd, "cmd");
        if (AyaDevicesUtilKt.c()) {
            return KtRootShell.f17356a.c(cmd);
        }
        if (AyaDevicesUtilKt.B || AyaDevicesUtilKt.z) {
            return TcRootShell.f17359a.a(cmd);
        }
        if (!AyaDevicesUtilKt.f13823m && !AyaDevicesUtilKt.f13824n && !AyaDevicesUtilKt.f13825o) {
            if (AyaDevicesUtilKt.y) {
                return Bw02RootShell.f17354a.d(cmd);
            }
            Timber.INSTANCE.k(a.a("runCom ", cmd), new Object[0]);
            Object objF = CmdUtilKt.f(cmd);
            if (Result.m28isFailureimpl(objF)) {
                objF = "";
            }
            return (String) objF;
        }
        return YtRootShell.f17360a.a(cmd);
    }

    public final void b() {
        a("setenforce 0");
    }

    public final void c() {
        a("setenforce 0");
    }
}
