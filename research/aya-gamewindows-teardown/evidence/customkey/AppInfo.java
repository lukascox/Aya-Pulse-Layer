package com.ayaneo.gamewindow.custom.datamodel;

import com.squareup.moshi.JsonClass;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/* JADX INFO: compiled from: CustomKeyItem.kt */
/* JADX INFO: loaded from: classes.dex */
@JsonClass(generateAdapter = true)
@Metadata(d1 = {"\u0000\"\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0002\b\t\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0002\b\u0087\b\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0003¢\u0006\u0002\u0010\u0005J\t\u0010\t\u001a\u00020\u0003HÆ\u0003J\t\u0010\n\u001a\u00020\u0003HÆ\u0003J\u001d\u0010\u000b\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u0003HÆ\u0001J\u0013\u0010\f\u001a\u00020\r2\b\u0010\u000e\u001a\u0004\u0018\u00010\u0001HÖ\u0003J\t\u0010\u000f\u001a\u00020\u0010HÖ\u0001J\t\u0010\u0011\u001a\u00020\u0003HÖ\u0001R\u0011\u0010\u0004\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007R\u0011\u0010\u0002\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\b\u0010\u0007¨\u0006\u0012"}, d2 = {"Lcom/ayaneo/gamewindow/custom/datamodel/AppInfo;", "", "packageName", "", "activityName", "(Ljava/lang/String;Ljava/lang/String;)V", "getActivityName", "()Ljava/lang/String;", "getPackageName", "component1", "component2", "copy", "equals", "", "other", "hashCode", "", "toString", "gamewindow_QCOMRelease"}, k = 1, mv = {1, 9, 0}, xi = 48)
public final /* data */ class AppInfo {

    @NotNull
    private final String activityName;

    @NotNull
    private final String packageName;

    public AppInfo(@NotNull String str, @NotNull String str2) {
        Intrinsics.e("packageName", str);
        Intrinsics.e("activityName", str2);
        this.packageName = str;
        this.activityName = str2;
    }

    public static /* synthetic */ AppInfo copy$default(AppInfo appInfo, String str, String str2, int i, Object obj) {
        if ((i & 1) != 0) {
            str = appInfo.packageName;
        }
        if ((i & 2) != 0) {
            str2 = appInfo.activityName;
        }
        return appInfo.copy(str, str2);
    }

    @NotNull
    /* JADX INFO: renamed from: component1, reason: from getter */
    public final String getPackageName() {
        return this.packageName;
    }

    @NotNull
    /* JADX INFO: renamed from: component2, reason: from getter */
    public final String getActivityName() {
        return this.activityName;
    }

    @NotNull
    public final AppInfo copy(@NotNull String packageName, @NotNull String activityName) {
        Intrinsics.e("packageName", packageName);
        Intrinsics.e("activityName", activityName);
        return new AppInfo(packageName, activityName);
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AppInfo)) {
            return false;
        }
        AppInfo appInfo = (AppInfo) other;
        return Intrinsics.a(this.packageName, appInfo.packageName) && Intrinsics.a(this.activityName, appInfo.activityName);
    }

    @NotNull
    public final String getActivityName() {
        return this.activityName;
    }

    @NotNull
    public final String getPackageName() {
        return this.packageName;
    }

    public int hashCode() {
        return this.activityName.hashCode() + (this.packageName.hashCode() * 31);
    }

    @NotNull
    public String toString() {
        return "AppInfo(packageName=" + this.packageName + ", activityName=" + this.activityName + ")";
    }
}
