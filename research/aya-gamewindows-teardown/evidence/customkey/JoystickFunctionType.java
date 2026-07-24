package com.ayaneo.gamewindow.ui.window.controller.protocol;

import kotlin.Metadata;
import kotlin.enums.EnumEntries;
import kotlin.enums.EnumEntriesKt;
import org.jetbrains.annotations.NotNull;

/* JADX WARN: Enum visitor error
jadx.core.utils.exceptions.JadxRuntimeException: Can't remove SSA var: r2v14 com.ayaneo.gamewindow.ui.window.controller.protocol.JoystickFunctionType[], still in use, count: 1, list:
  (r2v14 com.ayaneo.gamewindow.ui.window.controller.protocol.JoystickFunctionType[]) from 0x018b: INVOKE (r2v14 com.ayaneo.gamewindow.ui.window.controller.protocol.JoystickFunctionType[]) STATIC call: kotlin.enums.EnumEntriesKt.a(java.lang.Enum[]):kotlin.enums.EnumEntries A[MD:<E extends java.lang.Enum<E>>:(E extends java.lang.Enum<E>[]):kotlin.enums.EnumEntries<E extends java.lang.Enum<E>> (m), WRAPPED] (LINE:396)
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
@Metadata(d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0000\n\u0002\u0010\b\n\u0002\b\u001d\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u000f\b\u0002\u0012\u0006\u0010\u0002\u001a\u00020\u0003¢\u0006\u0002\u0010\u0004R\u0011\u0010\u0002\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u0005\u0010\u0006j\u0002\b\u0007j\u0002\b\bj\u0002\b\tj\u0002\b\nj\u0002\b\u000bj\u0002\b\fj\u0002\b\rj\u0002\b\u000ej\u0002\b\u000fj\u0002\b\u0010j\u0002\b\u0011j\u0002\b\u0012j\u0002\b\u0013j\u0002\b\u0014j\u0002\b\u0015j\u0002\b\u0016j\u0002\b\u0017j\u0002\b\u0018j\u0002\b\u0019j\u0002\b\u001aj\u0002\b\u001bj\u0002\b\u001cj\u0002\b\u001dj\u0002\b\u001ej\u0002\b\u001f¨\u0006 "}, d2 = {"Lcom/ayaneo/gamewindow/ui/window/controller/protocol/JoystickFunctionType;", "", "value", "", "(Ljava/lang/String;II)V", "getValue", "()I", "NONE", "A", "B", "X", "Y", "SELECT", "START", "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "LB", "RB", "LEFT_THUMB", "RIGHT_THUMB", "LT", "RT", "LEFTSTICK_UP", "LEFTSTICK_DOWN", "LEFTSTICK_LEFT", "LEFTSTICK_RIGHT", "RIGHTSTICK_UP", "RIGHTSTICK_DOWN", "RIGHTSTICK_LEFT", "RIGHTSTICK_RIGHT", "gamewindow_QCOMRelease"}, k = 1, mv = {1, 9, 0}, xi = 48)
public final class JoystickFunctionType {
    NONE(0),
    A(1),
    B(2),
    X(3),
    Y(4),
    SELECT(5),
    START(6),
    DPAD_UP(7),
    DPAD_DOWN(8),
    DPAD_LEFT(9),
    DPAD_RIGHT(10),
    LB(11),
    RB(12),
    LEFT_THUMB(13),
    RIGHT_THUMB(14),
    LT(15),
    RT(16),
    LEFTSTICK_UP(17),
    LEFTSTICK_DOWN(18),
    LEFTSTICK_LEFT(19),
    LEFTSTICK_RIGHT(20),
    RIGHTSTICK_UP(21),
    RIGHTSTICK_DOWN(22),
    RIGHTSTICK_LEFT(23),
    RIGHTSTICK_RIGHT(24);


    /* JADX INFO: renamed from: b, reason: collision with root package name */
    public static final /* synthetic */ EnumEntries f5796b;
    private final int value;

    static {
        f5796b = EnumEntriesKt.a(joystickFunctionTypeArr);
    }

    public JoystickFunctionType(int i) {
        super(str, i);
        this.value = i;
    }

    @NotNull
    public static EnumEntries<JoystickFunctionType> getEntries() {
        return f5796b;
    }

    public static JoystickFunctionType valueOf(String str) {
        return (JoystickFunctionType) Enum.valueOf(JoystickFunctionType.class, str);
    }

    public static JoystickFunctionType[] values() {
        return (JoystickFunctionType[]) f5795a.clone();
    }

    public final int getValue() {
        return this.value;
    }
}
