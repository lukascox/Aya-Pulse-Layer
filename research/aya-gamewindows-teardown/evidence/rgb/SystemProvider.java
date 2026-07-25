package com.ayaneo.provider;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.Settings;
import com.ayaneo.gamewindow.LauncherApp;
import kotlin.Metadata;
import kotlin.Result;
import kotlin.ResultKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/* JADX INFO: compiled from: AyaSystemProvider.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000P\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0007\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\t\n\u0002\b\u000b\n\u0002\u0018\u0002\n\u0000\b\u0016\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003¢\u0006\u0002\u0010\u0004J\u000e\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\u0003J\u000e\u0010\r\u001a\u00020\u000e2\u0006\u0010\f\u001a\u00020\u0003J\u0010\u0010\u000f\u001a\u00020\u00032\u0006\u0010\f\u001a\u00020\u0003H\u0002J\u000e\u0010\u0010\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\u0003J!\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\u000e0\u00122\u0006\u0010\f\u001a\u00020\u0003ø\u0001\u0000ø\u0001\u0001¢\u0006\u0004\b\u0013\u0010\u0014J\u0016\u0010\u0011\u001a\u00020\u000e2\u0006\u0010\f\u001a\u00020\u00032\u0006\u0010\u0015\u001a\u00020\u000eJ!\u0010\u0016\u001a\b\u0012\u0004\u0012\u00020\u00170\u00122\u0006\u0010\f\u001a\u00020\u0003ø\u0001\u0000ø\u0001\u0001¢\u0006\u0004\b\u0018\u0010\u0014J\u0016\u0010\u0016\u001a\u00020\u00172\u0006\u0010\f\u001a\u00020\u00032\u0006\u0010\u0015\u001a\u00020\u0017J!\u0010\u0019\u001a\b\u0012\u0004\u0012\u00020\u001a0\u00122\u0006\u0010\f\u001a\u00020\u0003ø\u0001\u0000ø\u0001\u0001¢\u0006\u0004\b\u001b\u0010\u0014J\u0016\u0010\u0019\u001a\u00020\u001a2\u0006\u0010\f\u001a\u00020\u00032\u0006\u0010\u0015\u001a\u00020\u001aJ!\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\u001d0\u00122\u0006\u0010\f\u001a\u00020\u0003ø\u0001\u0000ø\u0001\u0001¢\u0006\u0004\b\u001e\u0010\u0014J\u0016\u0010\u001c\u001a\u00020\u001d2\u0006\u0010\f\u001a\u00020\u00032\u0006\u0010\u0015\u001a\u00020\u001dJ!\u0010\u001f\u001a\b\u0012\u0004\u0012\u00020\u00030\u00122\u0006\u0010\f\u001a\u00020\u0003ø\u0001\u0000ø\u0001\u0001¢\u0006\u0004\b \u0010\u0014J\u0016\u0010\u001f\u001a\u00020\u00032\u0006\u0010\f\u001a\u00020\u00032\u0006\u0010!\u001a\u00020\u0003J\u0016\u0010\"\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\u00032\u0006\u0010#\u001a\u00020\u000eJ\u0016\u0010$\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\u00032\u0006\u0010#\u001a\u00020\u0017J\u0016\u0010%\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\u00032\u0006\u0010#\u001a\u00020\u001aJ\u0016\u0010&\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\u00032\u0006\u0010#\u001a\u00020\u001dJ\u0018\u0010'\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\u00032\b\u0010#\u001a\u0004\u0018\u00010\u0003J\b\u0010(\u001a\u00020)H\u0004R\u000e\u0010\u0005\u001a\u00020\u0003X\u0082\u0004¢\u0006\u0002\n\u0000R\u0014\u0010\u0006\u001a\u00020\u0007X\u0084\u0004¢\u0006\b\n\u0000\u001a\u0004\b\b\u0010\t\u0082\u0002\u000b\n\u0002\b!\n\u0005\b¡\u001e0\u0001¨\u0006*"}, d2 = {"Lcom/ayaneo/provider/SystemProvider;", "", "namespace", "", "(Ljava/lang/String;)V", "ayaSpace", "ayaUri", "Landroid/net/Uri;", "getAyaUri", "()Landroid/net/Uri;", "clear", "", "name", "contains", "", "createCompositeName", "delete", "getBoolean", "Lkotlin/Result;", "getBoolean-IoAF18A", "(Ljava/lang/String;)Ljava/lang/Object;", "def", "getFloat", "", "getFloat-IoAF18A", "getInt", "", "getInt-IoAF18A", "getLong", "", "getLong-IoAF18A", "getString", "getString-IoAF18A", "default", "putBoolean", "value", "putFloat", "putInt", "putLong", "putString", "resolver", "Landroid/content/ContentResolver;", "gamewindow_QCOMRelease"}, k = 1, mv = {1, 9, 0}, xi = 48)
@SourceDebugExtension
public class SystemProvider {

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    @NotNull
    public final String f6269a;

    /* JADX INFO: renamed from: b, reason: collision with root package name */
    @NotNull
    public final Uri f6270b;

    public SystemProvider(@NotNull String str) {
        String strConcat = "ayaneo/".concat(str);
        this.f6269a = strConcat;
        Uri uriFor = Settings.System.getUriFor(strConcat);
        Intrinsics.d("getUriFor(...)", uriFor);
        this.f6270b = uriFor;
    }

    @NotNull
    public static ContentResolver k() {
        LauncherApp.e.getClass();
        ContentResolver contentResolver = LauncherApp.Companion.a().getContentResolver();
        Intrinsics.d("getContentResolver(...)", contentResolver);
        return contentResolver;
    }

    public final void a(@NotNull String str) {
        Settings.System.putString(k(), b(str), null);
    }

    public final String b(String str) {
        return this.f6269a + "/" + str;
    }

    public final boolean c(@NotNull String str, boolean z) {
        Object objM15constructorimpl;
        try {
            Result.Companion companion = Result.INSTANCE;
            int i = Settings.System.getInt(k(), b(str));
            boolean z2 = true;
            if (i != 1) {
                z2 = false;
            }
            objM15constructorimpl = Result.m15constructorimpl(Boolean.valueOf(z2));
        } catch (Throwable th) {
            Result.Companion companion2 = Result.INSTANCE;
            objM15constructorimpl = Result.m15constructorimpl(ResultKt.a(th));
        }
        Boolean boolValueOf = Boolean.valueOf(z);
        if (Result.m20isFailureimpl(objM15constructorimpl)) {
            objM15constructorimpl = boolValueOf;
        }
        return ((Boolean) objM15constructorimpl).booleanValue();
    }

    public final float d(@NotNull String str) {
        Object objM15constructorimpl;
        Intrinsics.e("name", str);
        try {
            Result.Companion companion = Result.INSTANCE;
            objM15constructorimpl = Result.m15constructorimpl(Float.valueOf(Settings.System.getFloat(k(), b(str), 0.0f)));
        } catch (Throwable th) {
            Result.Companion companion2 = Result.INSTANCE;
            objM15constructorimpl = Result.m15constructorimpl(ResultKt.a(th));
        }
        Float fValueOf = Float.valueOf(0.0f);
        if (Result.m20isFailureimpl(objM15constructorimpl)) {
            objM15constructorimpl = fValueOf;
        }
        return ((Number) objM15constructorimpl).floatValue();
    }

    public final int e(int i, @NotNull String str) {
        Object objM15constructorimpl;
        try {
            Result.Companion companion = Result.INSTANCE;
            objM15constructorimpl = Result.m15constructorimpl(Integer.valueOf(Settings.System.getInt(k(), b(str), i)));
        } catch (Throwable th) {
            Result.Companion companion2 = Result.INSTANCE;
            objM15constructorimpl = Result.m15constructorimpl(ResultKt.a(th));
        }
        Integer numValueOf = Integer.valueOf(i);
        if (Result.m20isFailureimpl(objM15constructorimpl)) {
            objM15constructorimpl = numValueOf;
        }
        return ((Number) objM15constructorimpl).intValue();
    }

    @NotNull
    public final String f(@NotNull String str, @NotNull String str2) {
        Object objM15constructorimpl;
        Intrinsics.e("default", str2);
        try {
            Result.Companion companion = Result.INSTANCE;
            String string = Settings.System.getString(k(), b(str));
            if (string == null) {
                string = str2;
            }
            objM15constructorimpl = Result.m15constructorimpl(string);
        } catch (Throwable th) {
            Result.Companion companion2 = Result.INSTANCE;
            objM15constructorimpl = Result.m15constructorimpl(ResultKt.a(th));
        }
        if (Result.m20isFailureimpl(objM15constructorimpl)) {
            objM15constructorimpl = null;
        }
        String str3 = (String) objM15constructorimpl;
        return str3 == null ? str2 : str3;
    }

    @NotNull
    public final Object g(@NotNull String str) {
        Intrinsics.e("name", str);
        try {
            Result.Companion companion = Result.INSTANCE;
            return Result.m15constructorimpl(Settings.System.getString(k(), b(str)));
        } catch (Throwable th) {
            Result.Companion companion2 = Result.INSTANCE;
            return Result.m15constructorimpl(ResultKt.a(th));
        }
    }

    public final void h(@NotNull String str, boolean z) {
        Settings.System.putInt(k(), b(str), z ? 1 : 0);
    }

    public final void i(int i, @NotNull String str) {
        Intrinsics.e("name", str);
        Settings.System.putInt(k(), b(str), i);
    }

    public final void j(@NotNull String str, @Nullable String str2) {
        Settings.System.putString(k(), b(str), str2);
    }
}
