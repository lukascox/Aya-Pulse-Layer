package com.ayaneo.settings.ui.performance;

import kotlin.Metadata;
import kotlin.enums.EnumEntries;
import kotlin.enums.EnumEntriesKt;
import org.jetbrains.annotations.NotNull;

/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0000\n\u0002\u0010\b\n\u0002\b\n\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\u0011\b\u0002\u0012\u0006\u0010\u0002\u001a\u00020\u0003¢\u0006\u0004\b\u0004\u0010\u0005R\u0011\u0010\u0002\u001a\u00020\u0003¢\u0006\b\n\u0000\u001a\u0004\b\u0006\u0010\u0007j\u0002\b\bj\u0002\b\tj\u0002\b\nj\u0002\b\u000bj\u0002\b\f¨\u0006\r"}, d2 = {"Lcom/ayaneo/settings/ui/performance/FAN_MODE;", "", "intValue", "", "<init>", "(Ljava/lang/String;II)V", "getIntValue", "()I", "FAN_MODE_OFF", "FAN_MODE_MUTE", "FAN_MODE_BALANCE", "FAN_MODE_TURBO", "FAN_MODE_CUSTOM", "app_QCOMRelease"}, k = 1, mv = {2, 0, 0}, xi = 48)
public enum FAN_MODE {
    FAN_MODE_OFF(0),
    FAN_MODE_MUTE(1),
    FAN_MODE_BALANCE(2),
    FAN_MODE_TURBO(3),
    FAN_MODE_CUSTOM(4);


    /* JADX INFO: renamed from: b, reason: collision with root package name */
    public static final /* synthetic */ EnumEntries f16888b = EnumEntriesKt.c(b());
    private final int intValue;

    FAN_MODE(int i2) {
        this.intValue = i2;
    }

    @NotNull
    public static EnumEntries<FAN_MODE> getEntries() {
        return f16888b;
    }

    public final int getIntValue() {
        return this.intValue;
    }
}
