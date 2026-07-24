package com.ayaneo.settings.ui.performance;

import kotlin.Metadata;
import kotlin.enums.EnumEntries;
import kotlin.enums.EnumEntriesKt;
import org.jetbrains.annotations.NotNull;

/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0010\n\u0002\b\u0006\b\u0086\u0081\u0002\u0018\u00002\b\u0012\u0004\u0012\u00020\u00000\u0001B\t\b\u0002¢\u0006\u0004\b\u0002\u0010\u0003j\u0002\b\u0004j\u0002\b\u0005j\u0002\b\u0006¨\u0006\u0007"}, d2 = {"Lcom/ayaneo/settings/ui/performance/CPUSchedulerMode;", "", "<init>", "(Ljava/lang/String;I)V", "POWER_SAVING", "BALANCED", "HIGH_PERFORMANCE", "app_QCOMRelease"}, k = 1, mv = {2, 0, 0}, xi = 48)
public enum CPUSchedulerMode {
    POWER_SAVING,
    BALANCED,
    HIGH_PERFORMANCE;


    /* JADX INFO: renamed from: b, reason: collision with root package name */
    public static final /* synthetic */ EnumEntries f16886b = EnumEntriesKt.c(b());

    @NotNull
    public static EnumEntries<CPUSchedulerMode> getEntries() {
        return f16886b;
    }
}
