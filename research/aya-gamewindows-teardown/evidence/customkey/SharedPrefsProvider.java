package com.ayaneo.provider;

import a.a;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;
import com.ayaneo.gamewindow.custom.keydetector.CustomKeyDispatch;
import com.ayaneo.gamewindow.utils.PrefHelper;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.Reflection;
import kotlin.jvm.internal.SourceDebugExtension;
import kotlin.reflect.KClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/* JADX INFO: compiled from: SharedPrefsProvider.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000V\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u0011\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0006\u0018\u00002\u00020\u0001B\u0005¢\u0006\u0002\u0010\u0002J/\u0010\u0017\u001a\u00020\f2\u0006\u0010\u0018\u001a\u00020\u00072\b\u0010\u0019\u001a\u0004\u0018\u00010\u00042\u000e\u0010\u001a\u001a\n\u0012\u0004\u0012\u00020\u0004\u0018\u00010\u001bH\u0016¢\u0006\u0002\u0010\u001cJ\u0012\u0010\u001d\u001a\u0004\u0018\u00010\u00042\u0006\u0010\u0018\u001a\u00020\u0007H\u0016J\u001c\u0010\u001e\u001a\u0004\u0018\u00010\u00072\u0006\u0010\u0018\u001a\u00020\u00072\b\u0010\u001f\u001a\u0004\u0018\u00010 H\u0016J\b\u0010!\u001a\u00020\"H\u0016JK\u0010#\u001a\u0004\u0018\u00010$2\u0006\u0010\u0018\u001a\u00020\u00072\u000e\u0010%\u001a\n\u0012\u0004\u0012\u00020\u0004\u0018\u00010\u001b2\b\u0010\u0019\u001a\u0004\u0018\u00010\u00042\u000e\u0010\u001a\u001a\n\u0012\u0004\u0012\u00020\u0004\u0018\u00010\u001b2\b\u0010&\u001a\u0004\u0018\u00010\u0004H\u0016¢\u0006\u0002\u0010'J9\u0010(\u001a\u00020\f2\u0006\u0010\u0018\u001a\u00020\u00072\b\u0010\u001f\u001a\u0004\u0018\u00010 2\b\u0010\u0019\u001a\u0004\u0018\u00010\u00042\u000e\u0010\u001a\u001a\n\u0012\u0004\u0012\u00020\u0004\u0018\u00010\u001bH\u0016¢\u0006\u0002\u0010)R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082D¢\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082D¢\u0006\u0002\n\u0000R\u0019\u0010\u0006\u001a\n \b*\u0004\u0018\u00010\u00070\u0007¢\u0006\b\n\u0000\u001a\u0004\b\t\u0010\nR\u000e\u0010\u000b\u001a\u00020\fX\u0082D¢\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\fX\u0082D¢\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u0004X\u0082D¢\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0010X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0004X\u0082D¢\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0004X\u0082D¢\u0006\u0002\n\u0000R\u000e\u0010\u0013\u001a\u00020\u0014X\u0082.¢\u0006\u0002\n\u0000R\u000e\u0010\u0015\u001a\u00020\u0016X\u0082\u0004¢\u0006\u0002\n\u0000¨\u0006*"}, d2 = {"Lcom/ayaneo/provider/SharedPrefsProvider;", "Landroid/content/ContentProvider;", "()V", "AUTHORITY", "", "BASE_PATH", "CONTENT_URI", "Landroid/net/Uri;", "kotlin.jvm.PlatformType", "getCONTENT_URI", "()Landroid/net/Uri;", "SHARED_PREFS", "", "SHARED_PREF_KEY", "TAG", "context", "Landroid/content/Context;", "customInfoKey", "customInfoSp", "sp", "Landroid/content/SharedPreferences;", "uriMatcher", "Landroid/content/UriMatcher;", "delete", "uri", "selection", "selectionArgs", "", "(Landroid/net/Uri;Ljava/lang/String;[Ljava/lang/String;)I", "getType", "insert", "values", "Landroid/content/ContentValues;", "onCreate", "", "query", "Landroid/database/Cursor;", "projection", "sortOrder", "(Landroid/net/Uri;[Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)Landroid/database/Cursor;", "update", "(Landroid/net/Uri;Landroid/content/ContentValues;Ljava/lang/String;[Ljava/lang/String;)I", "gamewindow_QCOMRelease"}, k = 1, mv = {1, 9, 0}, xi = 48)
@SourceDebugExtension
public final class SharedPrefsProvider extends ContentProvider {

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    @NotNull
    public final String f6265a = "SharedPrefsProvider";

    /* JADX INFO: renamed from: b, reason: collision with root package name */
    @NotNull
    public final String f6266b = "com.ayaneo.gamewindow.provider.sharedprefs";

    /* JADX INFO: renamed from: c, reason: collision with root package name */
    @NotNull
    public final String f6267c = "shared_prefs";

    /* JADX INFO: renamed from: d, reason: collision with root package name */
    @NotNull
    public final String f6268d = "customInfoSp";

    @NotNull
    public final String e = "customInfoKey";
    public SharedPreferences f;
    public final int g;
    public final int h;

    @NotNull
    public final UriMatcher i;

    public SharedPrefsProvider() {
        Uri.parse("content://com.ayaneo.gamewindow.provider.sharedprefs/shared_prefs");
        this.g = 1;
        this.h = 2;
        UriMatcher uriMatcher = new UriMatcher(-1);
        uriMatcher.addURI("com.ayaneo.gamewindow.provider.sharedprefs", "shared_prefs", 1);
        uriMatcher.addURI("com.ayaneo.gamewindow.provider.sharedprefs", "shared_prefs/#", 2);
        this.i = uriMatcher;
    }

    @Override // android.content.ContentProvider
    public final int delete(@NotNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        Intrinsics.e("uri", uri);
        throw new UnsupportedOperationException("Not supported by this provider.");
    }

    @Override // android.content.ContentProvider
    @Nullable
    public final String getType(@NotNull Uri uri) {
        Intrinsics.e("uri", uri);
        int iMatch = this.i.match(uri);
        int i = this.g;
        String str = this.f6267c;
        String str2 = this.f6266b;
        if (iMatch == i) {
            return a.n("vnd.android.cursor.dir/vnd.", str2, ".", str);
        }
        if (iMatch == this.h) {
            return a.n("vnd.android.cursor.item/vnd.", str2, ".", str);
        }
        Log.i(this.f6265a, "getType: No this");
        return "";
    }

    @Override // android.content.ContentProvider
    @Nullable
    public final Uri insert(@NotNull Uri uri, @Nullable ContentValues values) {
        Intrinsics.e("uri", uri);
        throw new UnsupportedOperationException("Not supported by this provider.");
    }

    @Override // android.content.ContentProvider
    public final boolean onCreate() {
        Log.i(this.f6265a, "onCreate: ");
        Context context = getContext();
        if (context == null) {
            throw new IllegalStateException("Context cannot be null");
        }
        if (this.f != null) {
            return true;
        }
        SharedPreferences sharedPreferences = context.getSharedPreferences(this.f6268d, 0);
        Intrinsics.d("getSharedPreferences(...)", sharedPreferences);
        this.f = sharedPreferences;
        return true;
    }

    /* JADX WARN: Multi-variable type inference failed */
    @Override // android.content.ContentProvider
    @Nullable
    public final Cursor query(@NotNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        String strA;
        Intrinsics.e("uri", uri);
        String str = this.f6265a;
        Log.d(str, "query called with uri: " + uri);
        int iMatch = this.i.match(uri);
        if (iMatch != this.g) {
            if (iMatch != this.h) {
                Log.i(str, "query: no this ");
                return null;
            }
            String lastPathSegment = uri.getLastPathSegment();
            if (lastPathSegment == null) {
                lastPathSegment = "";
            }
            SharedPreferences sharedPreferences = this.f;
            if (sharedPreferences == null) {
                Intrinsics.l("sp");
                throw null;
            }
            String string = sharedPreferences.getString(lastPathSegment, "");
            MatrixCursor matrixCursor = new MatrixCursor(new String[]{"key", "value"});
            matrixCursor.addRow(new String[]{lastPathSegment, string});
            return matrixCursor;
        }
        MatrixCursor matrixCursor2 = new MatrixCursor(new String[]{"value"});
        PrefHelper prefHelper = PrefHelper.f5952a;
        SharedPreferences sharedPreferences2 = this.f;
        if (sharedPreferences2 == null) {
            Intrinsics.l("sp");
            throw null;
        }
        KClass kClassA = Reflection.a(String.class);
        boolean zA = Intrinsics.a(kClassA, Reflection.a(String.class));
        String str2 = this.e;
        if (zA) {
            strA = sharedPreferences2.getString(str2, "");
            if (strA == null) {
                throw new NullPointerException("null cannot be cast to non-null type kotlin.String");
            }
        } else if (Intrinsics.a(kClassA, Reflection.a(Integer.TYPE))) {
            Integer num = "" instanceof Integer ? (Integer) "" : null;
            strA = (String) Integer.valueOf(sharedPreferences2.getInt(str2, num != null ? num.intValue() : -1));
        } else if (Intrinsics.a(kClassA, Reflection.a(Boolean.TYPE))) {
            Boolean bool = "" instanceof Boolean ? (Boolean) "" : null;
            strA = (String) Boolean.valueOf(sharedPreferences2.getBoolean(str2, bool != null ? bool.booleanValue() : false));
        } else if (Intrinsics.a(kClassA, Reflection.a(Float.TYPE))) {
            Float f = "" instanceof Float ? (Float) "" : null;
            strA = (String) Float.valueOf(sharedPreferences2.getFloat(str2, f != null ? f.floatValue() : -1.0f));
        } else {
            if (!Intrinsics.a(kClassA, Reflection.a(Long.TYPE))) {
                throw new UnsupportedOperationException("Not yet implemented");
            }
            Long l = "" instanceof Long ? (Long) "" : null;
            strA = (String) Long.valueOf(sharedPreferences2.getLong(str2, l != null ? l.longValue() : -1L));
        }
        if ((strA.length() == 0) != false) {
            CustomKeyDispatch.f4905a.getClass();
            strA = CustomKeyDispatch.a();
        }
        matrixCursor2.addRow(new String[]{strA});
        return matrixCursor2;
    }

    @Override // android.content.ContentProvider
    public final int update(@NotNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        Intrinsics.e("uri", uri);
        if (this.i.match(uri) != this.g) {
            return Log.i(this.f6265a, "update: No this");
        }
        String asString = values != null ? values.getAsString("value") : null;
        if (asString == null) {
            return 0;
        }
        SharedPreferences sharedPreferences = this.f;
        if (sharedPreferences == null) {
            Intrinsics.l("sp");
            throw null;
        }
        SharedPreferences.Editor editorEdit = sharedPreferences.edit();
        editorEdit.putString(this.e, asString);
        editorEdit.apply();
        CustomKeyDispatch.f4905a.getClass();
        CustomKeyDispatch.c(asString);
        return 1;
    }
}
