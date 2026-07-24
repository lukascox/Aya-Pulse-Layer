package com.ayaneo.gamewindow.custom.datamodel;

import b.a;
import com.google.android.exoplayer2.text.ttml.TtmlNode;
import com.squareup.moshi.JsonClass;
import java.util.ArrayList;
import java.util.List;
import kotlin.Metadata;
import kotlin.jvm.internal.DefaultConstructorMarker;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import timber.log.Timber;

/* JADX INFO: compiled from: CustomKeyItem.kt */
/* JADX INFO: loaded from: classes.dex */
@JsonClass(generateAdapter = true)
@Metadata(d1 = {"\u0000H\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\t\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b$\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0002\b\u0004\b\u0087\b\u0018\u00002\u00020\u0001Bg\u0012\b\b\u0002\u0010\u0002\u001a\u00020\u0003\u0012\b\b\u0002\u0010\u0004\u001a\u00020\u0005\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0005\u0012\u000e\b\u0002\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\b\u0012\u000e\b\u0002\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u000b0\b\u0012\u000e\b\u0002\u0010\f\u001a\b\u0012\u0004\u0012\u00020\r0\b\u0012\b\b\u0002\u0010\u000e\u001a\u00020\u0005\u0012\b\b\u0002\u0010\u000f\u001a\u00020\u0003¢\u0006\u0002\u0010\u0010J\t\u0010&\u001a\u00020\u0003HÆ\u0003J\t\u0010'\u001a\u00020\u0005HÆ\u0003J\t\u0010(\u001a\u00020\u0005HÆ\u0003J\u000f\u0010)\u001a\b\u0012\u0004\u0012\u00020\t0\bHÆ\u0003J\u000f\u0010*\u001a\b\u0012\u0004\u0012\u00020\u000b0\bHÆ\u0003J\u000f\u0010+\u001a\b\u0012\u0004\u0012\u00020\r0\bHÆ\u0003J\t\u0010,\u001a\u00020\u0005HÆ\u0003J\t\u0010-\u001a\u00020\u0003HÆ\u0003Jk\u0010.\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00052\u000e\b\u0002\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\b2\u000e\b\u0002\u0010\n\u001a\b\u0012\u0004\u0012\u00020\u000b0\b2\u000e\b\u0002\u0010\f\u001a\b\u0012\u0004\u0012\u00020\r0\b2\b\b\u0002\u0010\u000e\u001a\u00020\u00052\b\b\u0002\u0010\u000f\u001a\u00020\u0003HÆ\u0001J\u0013\u0010/\u001a\u00020\u00052\b\u00100\u001a\u0004\u0018\u00010\u0001HÖ\u0003J\t\u00101\u001a\u000202HÖ\u0001J\u0016\u00103\u001a\u00020\u00052\u0006\u00104\u001a\u0002052\u0006\u00106\u001a\u000205J\u0006\u00107\u001a\u000208J\u0006\u00109\u001a\u000208J\u0006\u0010:\u001a\u000208J\t\u0010;\u001a\u000205HÖ\u0001R \u0010\f\u001a\b\u0012\u0004\u0012\u00020\r0\bX\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u0011\u0010\u0012\"\u0004\b\u0013\u0010\u0014R\u001a\u0010\u000e\u001a\u00020\u0005X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u0015\u0010\u0016\"\u0004\b\u0017\u0010\u0018R \u0010\n\u001a\b\u0012\u0004\u0012\u00020\u000b0\bX\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u0019\u0010\u0012\"\u0004\b\u001a\u0010\u0014R\u0011\u0010\u001b\u001a\u00020\u0005¢\u0006\b\n\u0000\u001a\u0004\b\u001c\u0010\u0016R\u0011\u0010\u0002\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u001d\u0010\u001eR\u001a\u0010\u0004\u001a\u00020\u0005X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u0004\u0010\u0016\"\u0004\b\u001f\u0010\u0018R\u001a\u0010\u0006\u001a\u00020\u0005X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u0006\u0010\u0016\"\u0004\b \u0010\u0018R \u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\bX\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b!\u0010\u0012\"\u0004\b\"\u0010\u0014R\u001a\u0010\u000f\u001a\u00020\u0003X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b#\u0010\u001e\"\u0004\b$\u0010%¨\u0006<"}, d2 = {"Lcom/ayaneo/gamewindow/custom/datamodel/CustomKeyItem;", "", TtmlNode.ATTR_ID, "", "isShortClick", "", "isTogether", "keyInfoList", "", "Lcom/ayaneo/gamewindow/custom/datamodel/KeyInfo;", "funInfoList", "Lcom/ayaneo/gamewindow/custom/datamodel/FunInfo;", "appWhite", "Lcom/ayaneo/gamewindow/custom/datamodel/AppInfo;", "enable", "updateTime", "(JZZLjava/util/List;Ljava/util/List;Ljava/util/List;ZJ)V", "getAppWhite", "()Ljava/util/List;", "setAppWhite", "(Ljava/util/List;)V", "getEnable", "()Z", "setEnable", "(Z)V", "getFunInfoList", "setFunInfoList", "hasAppWhiteList", "getHasAppWhiteList", "getId", "()J", "setShortClick", "setTogether", "getKeyInfoList", "setKeyInfoList", "getUpdateTime", "setUpdateTime", "(J)V", "component1", "component2", "component3", "component4", "component5", "component6", "component7", "component8", "copy", "equals", "other", "hashCode", "", "isInAppWhite", "packageName", "", "activityName", "printFunInfo", "", "printKeyInfo", "printWhiteApp", "toString", "gamewindow_QCOMRelease"}, k = 1, mv = {1, 9, 0}, xi = 48)
@SourceDebugExtension
public final /* data */ class CustomKeyItem {

    @NotNull
    private List<AppInfo> appWhite;
    private boolean enable;

    @NotNull
    private List<FunInfo> funInfoList;
    private final boolean hasAppWhiteList;
    private final long id;
    private boolean isShortClick;
    private boolean isTogether;

    @NotNull
    private List<KeyInfo> keyInfoList;
    private long updateTime;

    public CustomKeyItem() {
        this(0L, false, false, null, null, null, false, 0L, 255, null);
    }

    /* JADX INFO: renamed from: component1, reason: from getter */
    public final long getId() {
        return this.id;
    }

    /* JADX INFO: renamed from: component2, reason: from getter */
    public final boolean getIsShortClick() {
        return this.isShortClick;
    }

    /* JADX INFO: renamed from: component3, reason: from getter */
    public final boolean getIsTogether() {
        return this.isTogether;
    }

    @NotNull
    public final List<KeyInfo> component4() {
        return this.keyInfoList;
    }

    @NotNull
    public final List<FunInfo> component5() {
        return this.funInfoList;
    }

    @NotNull
    public final List<AppInfo> component6() {
        return this.appWhite;
    }

    /* JADX INFO: renamed from: component7, reason: from getter */
    public final boolean getEnable() {
        return this.enable;
    }

    /* JADX INFO: renamed from: component8, reason: from getter */
    public final long getUpdateTime() {
        return this.updateTime;
    }

    @NotNull
    public final CustomKeyItem copy(long id, boolean isShortClick, boolean isTogether, @NotNull List<KeyInfo> keyInfoList, @NotNull List<FunInfo> funInfoList, @NotNull List<AppInfo> appWhite, boolean enable, long updateTime) {
        Intrinsics.e("keyInfoList", keyInfoList);
        Intrinsics.e("funInfoList", funInfoList);
        Intrinsics.e("appWhite", appWhite);
        return new CustomKeyItem(id, isShortClick, isTogether, keyInfoList, funInfoList, appWhite, enable, updateTime);
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CustomKeyItem)) {
            return false;
        }
        CustomKeyItem customKeyItem = (CustomKeyItem) other;
        return this.id == customKeyItem.id && this.isShortClick == customKeyItem.isShortClick && this.isTogether == customKeyItem.isTogether && Intrinsics.a(this.keyInfoList, customKeyItem.keyInfoList) && Intrinsics.a(this.funInfoList, customKeyItem.funInfoList) && Intrinsics.a(this.appWhite, customKeyItem.appWhite) && this.enable == customKeyItem.enable && this.updateTime == customKeyItem.updateTime;
    }

    @NotNull
    public final List<AppInfo> getAppWhite() {
        return this.appWhite;
    }

    public final boolean getEnable() {
        return this.enable;
    }

    @NotNull
    public final List<FunInfo> getFunInfoList() {
        return this.funInfoList;
    }

    public final boolean getHasAppWhiteList() {
        return this.hasAppWhiteList;
    }

    public final long getId() {
        return this.id;
    }

    @NotNull
    public final List<KeyInfo> getKeyInfoList() {
        return this.keyInfoList;
    }

    public final long getUpdateTime() {
        return this.updateTime;
    }

    public int hashCode() {
        return Long.hashCode(this.updateTime) + a.c(this.enable, a.a.c(this.appWhite, a.a.c(this.funInfoList, a.a.c(this.keyInfoList, a.c(this.isTogether, a.c(this.isShortClick, Long.hashCode(this.id) * 31, 31), 31), 31), 31), 31), 31);
    }

    /* JADX WARN: Code duplicated, block: B:10:0x002b A[RETURN, SYNTHETIC] */
    /* JADX WARN: Code duplicated, block: B:11:0x002d A[ORIG_RETURN, RETURN] */
    public final boolean isInAppWhite(@NotNull String packageName, @NotNull String activityName) {
        Intrinsics.e("packageName", packageName);
        Intrinsics.e("activityName", activityName);
        for (Object obj : this.appWhite) {
            if (Intrinsics.a(((AppInfo) obj).getPackageName(), packageName)) {
                if (obj != null) {
                    return true;
                }
                return false;
            }
        }
        obj = null;
        if (obj != null) {
            return true;
        }
        return false;
    }

    public final boolean isShortClick() {
        return this.isShortClick;
    }

    public final boolean isTogether() {
        return this.isTogether;
    }

    public final void printFunInfo() {
        for (FunInfo funInfo : this.funInfoList) {
            Timber.f11226a.f("funInfoList keyName:" + funInfo.getName() + ",funCode:" + funInfo.getFunCode() + ",pCode:" + funInfo.getPCode(), new Object[0]);
        }
    }

    public final void printKeyInfo() {
        for (KeyInfo keyInfo : this.keyInfoList) {
            Timber.f11226a.f("printKeyInfo keyName:" + keyInfo.getKeyName() + ",value:" + keyInfo.getValue(), new Object[0]);
        }
    }

    public final void printWhiteApp() {
        for (AppInfo appInfo : this.appWhite) {
            Timber.f11226a.f(a.a.n("appWhite packageName:", appInfo.getPackageName(), ",activityName:", appInfo.getActivityName()), new Object[0]);
        }
    }

    public final void setAppWhite(@NotNull List<AppInfo> list) {
        Intrinsics.e("<set-?>", list);
        this.appWhite = list;
    }

    public final void setEnable(boolean z) {
        this.enable = z;
    }

    public final void setFunInfoList(@NotNull List<FunInfo> list) {
        Intrinsics.e("<set-?>", list);
        this.funInfoList = list;
    }

    public final void setKeyInfoList(@NotNull List<KeyInfo> list) {
        Intrinsics.e("<set-?>", list);
        this.keyInfoList = list;
    }

    public final void setShortClick(boolean z) {
        this.isShortClick = z;
    }

    public final void setTogether(boolean z) {
        this.isTogether = z;
    }

    public final void setUpdateTime(long j) {
        this.updateTime = j;
    }

    @NotNull
    public String toString() {
        return "CustomKeyItem(id=" + this.id + ", isShortClick=" + this.isShortClick + ", isTogether=" + this.isTogether + ", keyInfoList=" + this.keyInfoList + ", funInfoList=" + this.funInfoList + ", appWhite=" + this.appWhite + ", enable=" + this.enable + ", updateTime=" + this.updateTime + ")";
    }

    public CustomKeyItem(long j, boolean z, boolean z2, @NotNull List<KeyInfo> list, @NotNull List<FunInfo> list2, @NotNull List<AppInfo> list3, boolean z3, long j2) {
        Intrinsics.e("keyInfoList", list);
        Intrinsics.e("funInfoList", list2);
        Intrinsics.e("appWhite", list3);
        this.id = j;
        this.isShortClick = z;
        this.isTogether = z2;
        this.keyInfoList = list;
        this.funInfoList = list2;
        this.appWhite = list3;
        this.enable = z3;
        this.updateTime = j2;
        this.hasAppWhiteList = !list3.isEmpty();
    }

    public /* synthetic */ CustomKeyItem(long j, boolean z, boolean z2, List list, List list2, List list3, boolean z3, long j2, int i, DefaultConstructorMarker defaultConstructorMarker) {
        this((i & 1) != 0 ? 0L : j, (i & 2) != 0 ? true : z, (i & 4) != 0 ? true : z2, (i & 8) != 0 ? new ArrayList() : list, (i & 16) != 0 ? new ArrayList() : list2, (i & 32) != 0 ? new ArrayList() : list3, (i & 64) != 0 ? true : z3, (i & 128) != 0 ? 0L : j2);
    }
}
