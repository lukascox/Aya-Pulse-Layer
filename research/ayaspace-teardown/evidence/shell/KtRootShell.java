package com.ayaneo.settings.utils.shell;

import com.kingtop.shellcmd.ShellCmd;
import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;
import q.b;

/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000\u001c\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\u0005\bÆ\u0002\u0018\u00002\u00020\u0001B\t\b\u0002¢\u0006\u0004\b\u0002\u0010\u0003J\r\u0010\u0005\u001a\u00020\u0004¢\u0006\u0004\b\u0005\u0010\u0006J\r\u0010\u0007\u001a\u00020\u0004¢\u0006\u0004\b\u0007\u0010\u0006J\u0015\u0010\t\u001a\u00020\u00042\u0006\u0010\b\u001a\u00020\u0004¢\u0006\u0004\b\t\u0010\nR\u001b\u0010\u000f\u001a\u00020\u000b8BX\u0082\u0084\u0002¢\u0006\f\n\u0004\b\f\u0010\r\u001a\u0004\b\f\u0010\u000e¨\u0006\u0010"}, d2 = {"Lcom/ayaneo/settings/utils/shell/KtRootShell;", "", "<init>", "()V", "", "f", "()Ljava/lang/String;", "e", "cmd", "c", "(Ljava/lang/String;)Ljava/lang/String;", "Lcom/kingtop/shellcmd/ShellCmd;", "b", "Lkotlin/Lazy;", "()Lcom/kingtop/shellcmd/ShellCmd;", "shellCmd", "app_QCOMRelease"}, k = 1, mv = {2, 0, 0})
public final class KtRootShell {

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    @NotNull
    public static final KtRootShell f17356a = new KtRootShell();

    /* JADX INFO: renamed from: b, reason: collision with root package name and from kotlin metadata */
    @NotNull
    public static final Lazy shellCmd = LazyKt.c(new b());

    public static ShellCmd a() {
        return new ShellCmd();
    }

    public static final ShellCmd d() {
        return new ShellCmd();
    }

    public final ShellCmd b() {
        return (ShellCmd) shellCmd.getValue();
    }

    @NotNull
    public final String c(@NotNull String cmd) {
        Intrinsics.p(cmd, "cmd");
        String strA = b().a(cmd, 1);
        Intrinsics.o(strA, "shellJni(...)");
        return strA;
    }

    @NotNull
    public final String e() {
        b().a("echo 0 > /proc/gpufreq/gpufreq_opp_freq", 1);
        String strA = b().a("echo -1 -1 -1 > /proc/ppm/policy/ut_fix_freq_idx", 1);
        Intrinsics.o(strA, "shellJni(...)");
        return strA;
    }

    @NotNull
    public final String f() {
        b().a("echo 886000 > /proc/gpufreq/gpufreq_opp_freq", 1);
        String strA = b().a("echo 0 0 0 > /proc/ppm/policy/ut_fix_freq_idx", 1);
        Intrinsics.o(strA, "shellJni(...)");
        return strA;
    }
}
