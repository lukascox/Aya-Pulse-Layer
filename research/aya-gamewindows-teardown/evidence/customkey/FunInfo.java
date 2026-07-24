package com.ayaneo.gamewindow.custom.datamodel;

import a.a;
import com.squareup.moshi.JsonClass;
import java.util.List;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/* JADX INFO: compiled from: CustomKeyItem.kt */
/* JADX INFO: loaded from: classes.dex */
@JsonClass(generateAdapter = true)
@Metadata(d1 = {"\u0000,\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010!\n\u0002\u0018\u0002\n\u0002\b\u0015\n\u0002\u0010\u000b\n\u0002\b\u0004\b\u0087\b\u0018\u00002\u00020\u0001B+\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u0012\u0006\u0010\u0004\u001a\u00020\u0005\u0012\u0006\u0010\u0006\u001a\u00020\u0005\u0012\f\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\b¢\u0006\u0002\u0010\nJ\t\u0010\u0019\u001a\u00020\u0003HÆ\u0003J\t\u0010\u001a\u001a\u00020\u0005HÆ\u0003J\t\u0010\u001b\u001a\u00020\u0005HÆ\u0003J\u000f\u0010\u001c\u001a\b\u0012\u0004\u0012\u00020\t0\bHÆ\u0003J7\u0010\u001d\u001a\u00020\u00002\b\b\u0002\u0010\u0002\u001a\u00020\u00032\b\b\u0002\u0010\u0004\u001a\u00020\u00052\b\b\u0002\u0010\u0006\u001a\u00020\u00052\u000e\b\u0002\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\bHÆ\u0001J\u0013\u0010\u001e\u001a\u00020\u001f2\b\u0010 \u001a\u0004\u0018\u00010\u0001HÖ\u0003J\t\u0010!\u001a\u00020\u0005HÖ\u0001J\t\u0010\"\u001a\u00020\u0003HÖ\u0001R \u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\t0\bX\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u000b\u0010\f\"\u0004\b\r\u0010\u000eR\u001a\u0010\u0006\u001a\u00020\u0005X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u000f\u0010\u0010\"\u0004\b\u0011\u0010\u0012R\u001a\u0010\u0002\u001a\u00020\u0003X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u0013\u0010\u0014\"\u0004\b\u0015\u0010\u0016R\u001a\u0010\u0004\u001a\u00020\u0005X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u0017\u0010\u0010\"\u0004\b\u0018\u0010\u0012¨\u0006#"}, d2 = {"Lcom/ayaneo/gamewindow/custom/datamodel/FunInfo;", "", "name", "", "pCode", "", "funCode", "executePar", "", "Lcom/ayaneo/gamewindow/custom/datamodel/Parameter;", "(Ljava/lang/String;IILjava/util/List;)V", "getExecutePar", "()Ljava/util/List;", "setExecutePar", "(Ljava/util/List;)V", "getFunCode", "()I", "setFunCode", "(I)V", "getName", "()Ljava/lang/String;", "setName", "(Ljava/lang/String;)V", "getPCode", "setPCode", "component1", "component2", "component3", "component4", "copy", "equals", "", "other", "hashCode", "toString", "gamewindow_QCOMRelease"}, k = 1, mv = {1, 9, 0}, xi = 48)
public final /* data */ class FunInfo {

    @NotNull
    private List<Parameter> executePar;
    private int funCode;

    @NotNull
    private String name;
    private int pCode;

    public FunInfo(@NotNull String str, int i, int i2, @NotNull List<Parameter> list) {
        Intrinsics.e("name", str);
        Intrinsics.e("executePar", list);
        this.name = str;
        this.pCode = i;
        this.funCode = i2;
        this.executePar = list;
    }

    /* JADX WARN: Multi-variable type inference failed */
    public static /* synthetic */ FunInfo copy$default(FunInfo funInfo, String str, int i, int i2, List list, int i3, Object obj) {
        if ((i3 & 1) != 0) {
            str = funInfo.name;
        }
        if ((i3 & 2) != 0) {
            i = funInfo.pCode;
        }
        if ((i3 & 4) != 0) {
            i2 = funInfo.funCode;
        }
        if ((i3 & 8) != 0) {
            list = funInfo.executePar;
        }
        return funInfo.copy(str, i, i2, list);
    }

    @NotNull
    /* JADX INFO: renamed from: component1, reason: from getter */
    public final String getName() {
        return this.name;
    }

    /* JADX INFO: renamed from: component2, reason: from getter */
    public final int getPCode() {
        return this.pCode;
    }

    /* JADX INFO: renamed from: component3, reason: from getter */
    public final int getFunCode() {
        return this.funCode;
    }

    @NotNull
    public final List<Parameter> component4() {
        return this.executePar;
    }

    @NotNull
    public final FunInfo copy(@NotNull String name, int pCode, int funCode, @NotNull List<Parameter> executePar) {
        Intrinsics.e("name", name);
        Intrinsics.e("executePar", executePar);
        return new FunInfo(name, pCode, funCode, executePar);
    }

    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FunInfo)) {
            return false;
        }
        FunInfo funInfo = (FunInfo) other;
        return Intrinsics.a(this.name, funInfo.name) && this.pCode == funInfo.pCode && this.funCode == funInfo.funCode && Intrinsics.a(this.executePar, funInfo.executePar);
    }

    @NotNull
    public final List<Parameter> getExecutePar() {
        return this.executePar;
    }

    public final int getFunCode() {
        return this.funCode;
    }

    @NotNull
    public final String getName() {
        return this.name;
    }

    public final int getPCode() {
        return this.pCode;
    }

    public int hashCode() {
        return this.executePar.hashCode() + a.a(this.funCode, a.a(this.pCode, this.name.hashCode() * 31, 31), 31);
    }

    public final void setExecutePar(@NotNull List<Parameter> list) {
        Intrinsics.e("<set-?>", list);
        this.executePar = list;
    }

    public final void setFunCode(int i) {
        this.funCode = i;
    }

    public final void setName(@NotNull String str) {
        Intrinsics.e("<set-?>", str);
        this.name = str;
    }

    public final void setPCode(int i) {
        this.pCode = i;
    }

    @NotNull
    public String toString() {
        return "FunInfo(name=" + this.name + ", pCode=" + this.pCode + ", funCode=" + this.funCode + ", executePar=" + this.executePar + ")";
    }
}
