package com.ayaneo.gamewindow.custom.datamodel;

import com.squareup.moshi.JsonClass;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/* JADX INFO: compiled from: CustomKeyItem.kt */
/* JADX INFO: loaded from: classes.dex */
@JsonClass(generateAdapter = true)
@Metadata(d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0002\b\t\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0087\b\u0018\u00002\u00020\u0001B\u0015\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005¢\u0006\u0002\u0010\u0006J\t\u0010\u000b\u001a\u00020\u0003HÆ\u0003J\t\u0010\f\u001a\u00020\u0005HÆ\u0003J\u001d\u0010\r\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u0005HÆ\u0001J\u0013\u0010\u000e\u001a\u00020\u000f2\b\u0010\u0010\u001a\u0004\u0018\u00010\u0001HÖ\u0003J\t\u0010\u0011\u001a\u00020\u0005HÖ\u0001J\t\u0010\u0012\u001a\u00020\u0003HÖ\u0001R\u0011\u0010\u0002\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u0007\u0010\bR\u0011\u0010\u0004\u001a\u00020\u0005¢\u0006\b\n\u0000\u001a\u0004\b\t\u0010\n¨\u0006\u0013"}, d2 = {"Lcom/ayaneo/gamewindow/custom/datamodel/KeyInfo;", "", "keyName", "", "value", "", "(Ljava/lang/String;I)V", "getKeyName", "()Ljava/lang/String;", "getValue", "()I", "component1", "component2", "copy", "equals", "", "other", "hashCode", "toString", "gamewindow_QCOMRelease"}, k = 1, mv = {1, 9, 0}, xi = 48)
public final /* data */ class KeyInfo {

    @NotNull
    private final String keyName;
    private final int value;

    public KeyInfo(@NotNull String str, int i) {
        Intrinsics.e("keyName", str);
        this.keyName = str;
        this.value = i;
    }

    public static /* synthetic */ KeyInfo copy$default(KeyInfo keyInfo, String str, int i, int i2, Object obj) {
        if ((i2 & 1) != 0) {
            str = keyInfo.keyName;
        }
        if ((i2 & 2) != 0) {
            i = keyInfo.value;
        }
        return keyInfo.copy(str, i);
    }

    @NotNull
    /* JADX INFO: renamed from: component1, reason: from getter */
    public final String getKeyName() {
        return this.keyName;
    }

    /* JADX INFO: renamed from: component2, reason: from getter */
    public final int getValue() {
        return this.value;
    }

    @NotNull
    public final KeyInfo copy(@NotNull String keyName, int value) {
        Intrinsics.e("keyName", keyName);
        return new KeyInfo(keyName, value);
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof KeyInfo)) {
            return false;
        }
        KeyInfo keyInfo = (KeyInfo) other;
        return Intrinsics.a(this.keyName, keyInfo.keyName) && this.value == keyInfo.value;
    }

    @NotNull
    public final String getKeyName() {
        return this.keyName;
    }

    public final int getValue() {
        return this.value;
    }

    public int hashCode() {
        return Integer.hashCode(this.value) + (this.keyName.hashCode() * 31);
    }

    @NotNull
    public String toString() {
        return "KeyInfo(keyName=" + this.keyName + ", value=" + this.value + ")";
    }
}
