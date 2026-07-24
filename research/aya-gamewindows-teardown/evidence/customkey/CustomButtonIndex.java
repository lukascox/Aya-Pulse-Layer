package com.ayaneo.gamewindow.ui.window.controller.protocol;

import kotlin.Metadata;
import kotlin.enums.EnumEntries;
import kotlin.enums.EnumEntriesKt;
import org.jetbrains.annotations.NotNull;

/* JADX WARN: Enum visitor error
jadx.core.utils.exceptions.JadxRuntimeException: Can't remove SSA var: r8v3 com.ayaneo.gamewindow.ui.window.controller.protocol.CustomButtonIndex[], still in use, count: 1, list:
  (r8v3 com.ayaneo.gamewindow.ui.window.controller.protocol.CustomButtonIndex[]) from 0x0049: INVOKE (r8v3 com.ayaneo.gamewindow.ui.window.controller.protocol.CustomButtonIndex[]) STATIC call: kotlin.enums.EnumEntriesKt.a(java.lang.Enum[]):kotlin.enums.EnumEntries A[MD:<E extends java.lang.Enum<E>>:(E extends java.lang.Enum<E>[]):kotlin.enums.EnumEntries<E extends java.lang.Enum<E>> (m), WRAPPED] (LINE:74)
	at jadx.core.utils.InsnRemover.removeSsaVar(InsnRemover.java:164)
	at jadx.core.utils.InsnRemover.unbindResult(InsnRemover.java:129)
	at jadx.core.utils.InsnRemover.lambda$unbindInsns$1(InsnRemover.java:101)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1612)
	at jadx.core.utils.InsnRemover.unbindInsns(InsnRemover.java:100)
	at jadx.core.utils.InsnRemover.removeAllAndUnbind(InsnRemover.java:257)
	at jadx.core.dex.visitors.EnumVisitor.convertToEnum(EnumVisitor.java:187)
	at jadx.core.dex.visitors.EnumVisitor.visit(EnumVisitor.java:102)
 */
/* JADX WARN: Failed to restore enum class, 'enum' modifier and super class removed */
/* JADX INFO: compiled from: BehindButManager.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0000\n\u0002\u0010\b\n\u0002\b\t\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u000f\b\u0002\u0012\u0006\u0010\u0002\u001a\u00020\u0003¢\u0006\u0002\u0010\u0004R\u0011\u0010\u0002\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006j\u0002\b\u0007j\u0002\b\bj\u0002\b\tj\u0002\b\nj\u0002\b\u000b¨\u0006\f"}, d2 = {"Lcom/ayaneo/gamewindow/ui/window/controller/protocol/CustomButtonIndex;", "", "value", "", "(Ljava/lang/String;II)V", "getValue", "()I", "NONE", "LC_SHOULDER", "RC_SHOULDER", "LC_BACK", "RC_BACK", "gamewindow_QCOMRelease"}, k = 1, mv = {1, 9, 0}, xi = 48)
public final class CustomButtonIndex {
    NONE(0),
    LC_SHOULDER(16),
    RC_SHOULDER(17),
    LC_BACK(18),
    RC_BACK(19);


    /* JADX INFO: renamed from: b, reason: collision with root package name */
    public static final /* synthetic */ EnumEntries f5790b;
    private final int value;

    static {
        f5790b = EnumEntriesKt.a(customButtonIndexArr);
    }

    public CustomButtonIndex(int i) {
        super(str, i);
        this.value = i;
    }

    @NotNull
    public static EnumEntries<CustomButtonIndex> getEntries() {
        return f5790b;
    }

    public static CustomButtonIndex valueOf(String str) {
        return (CustomButtonIndex) Enum.valueOf(CustomButtonIndex.class, str);
    }

    public static CustomButtonIndex[] values() {
        return (CustomButtonIndex[]) f5789a.clone();
    }

    public final int getValue() {
        return this.value;
    }
}
