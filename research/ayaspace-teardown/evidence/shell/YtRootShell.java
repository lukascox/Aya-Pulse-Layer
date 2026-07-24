package com.ayaneo.settings.utils.shell;

import android.util.Log;
import androidx.versionedparcelable.ParcelUtils;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.io.CloseableKt;
import kotlin.io.TextStreamsKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.SourceDebugExtension;
import kotlin.text.Charsets;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.NotNull;

/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0006\bÆ\u0002\u0018\u00002\u00020\u0001B\t\b\u0002¢\u0006\u0004\b\u0002\u0010\u0003J\u0015\u0010\u0006\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0004¢\u0006\u0004\b\u0006\u0010\u0007J\u001f\u0010\n\u001a\u00020\b2\u0006\u0010\u0005\u001a\u00020\u00042\b\b\u0002\u0010\t\u001a\u00020\b¢\u0006\u0004\b\n\u0010\u000bR\u0014\u0010\r\u001a\u00020\u00048\u0002X\u0082D¢\u0006\u0006\n\u0004\b\n\u0010\f¨\u0006\u000e"}, d2 = {"Lcom/ayaneo/settings/utils/shell/YtRootShell;", "", "<init>", "()V", "", "cmd", ParcelUtils.f10526a, "(Ljava/lang/String;)Ljava/lang/String;", "", "default", "b", "(Ljava/lang/String;I)I", "Ljava/lang/String;", "TAG", "app_QCOMRelease"}, k = 1, mv = {2, 0, 0})
@SourceDebugExtension({"SMAP\nYtRootShell.kt\nKotlin\n*S Kotlin\n*F\n+ 1 YtRootShell.kt\ncom/ayaneo/settings/utils/shell/YtRootShell\n+ 2 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,42:1\n1#2:43\n*E\n"})
public final class YtRootShell {

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    @NotNull
    public static final YtRootShell f17360a = new YtRootShell();

    /* JADX INFO: renamed from: b, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final String TAG = "YtRootShell";

    public static /* synthetic */ int c(YtRootShell ytRootShell, String str, int i2, int i3, Object obj) {
        if ((i3 & 2) != 0) {
            i2 = 0;
        }
        return ytRootShell.b(str, i2);
    }

    @NotNull
    public final String a(@NotNull String cmd) {
        Intrinsics.p(cmd, "cmd");
        String str = "";
        try {
            Process processExec = Runtime.getRuntime().exec("ytsu");
            OutputStream outputStream = processExec.getOutputStream();
            try {
                Charset charset = Charsets.UTF_8;
                byte[] bytes = cmd.getBytes(charset);
                Intrinsics.o(bytes, "getBytes(...)");
                outputStream.write(bytes);
                byte[] bytes2 = "\n".getBytes(charset);
                Intrinsics.o(bytes2, "getBytes(...)");
                outputStream.write(bytes2);
                outputStream.flush();
                byte[] bytes3 = "exit".getBytes(charset);
                Intrinsics.o(bytes3, "getBytes(...)");
                outputStream.write(bytes3);
                byte[] bytes4 = "\n".getBytes(charset);
                Intrinsics.o(bytes4, "getBytes(...)");
                outputStream.write(bytes4);
                outputStream.flush();
                Unit unit = Unit.f21153a;
                CloseableKt.a(outputStream, null);
                InputStream inputStream = processExec.getInputStream();
                Intrinsics.o(inputStream, "getInputStream(...)");
                Reader inputStreamReader = new InputStreamReader(inputStream, charset);
                BufferedReader bufferedReader = inputStreamReader instanceof BufferedReader ? (BufferedReader) inputStreamReader : new BufferedReader(inputStreamReader, 8192);
                try {
                    String strK = TextStreamsKt.k(bufferedReader);
                    CloseableKt.a(bufferedReader, null);
                    try {
                        processExec.waitFor();
                        processExec.destroy();
                        return strK;
                    } catch (Throwable th) {
                        th = th;
                        str = strK;
                        th.printStackTrace();
                        Log.i(TAG, "execRootCmd ERROR" + th);
                        return str;
                    }
                } catch (Throwable th2) {
                    try {
                        throw th2;
                    } catch (Throwable th3) {
                        CloseableKt.a(bufferedReader, th2);
                        throw th3;
                    }
                }
            } catch (Throwable th4) {
                try {
                    throw th4;
                } catch (Throwable th5) {
                    CloseableKt.a(outputStream, th4);
                    throw th5;
                }
            }
        } catch (Throwable th6) {
            th = th6;
        }
    }

    public final int b(@NotNull String cmd, int i2) {
        Intrinsics.p(cmd, "cmd");
        String string = StringsKt.G5(a(cmd)).toString();
        return StringsKt.x3(string) ? i2 : Integer.parseInt(string);
    }
}
