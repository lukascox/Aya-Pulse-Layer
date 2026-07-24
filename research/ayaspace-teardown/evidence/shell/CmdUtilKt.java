package com.ayaneo.settings.utils.shell;

import androidx.versionedparcelable.ParcelUtils;
import coil.p008util.FileDescriptorCounter;
import com.ayaneo.AyaDevicesUtilKt;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kotlin.Metadata;
import kotlin.Pair;
import kotlin.Result;
import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.io.CloseableKt;
import kotlin.io.TextStreamsKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.SourceDebugExtension;
import kotlin.text.Charsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000,\n\u0002\u0010\u000e\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\u0010\u000b\n\u0002\b\u0002\n\u0002\u0010\u0007\n\u0002\b\u0003\n\u0002\u0010\u0011\n\u0002\u0010\t\n\u0002\b\u0006\u001a\u0017\u0010\u0002\u001a\b\u0012\u0004\u0012\u00020\u00000\u0001*\u00020\u0000¢\u0006\u0004\b\u0002\u0010\u0003\u001a-\u0010\u0007\u001a\u0016\u0012\u0012\u0012\u0010\u0012\u0004\u0012\u00020\u0006\u0012\u0006\u0012\u0004\u0018\u00010\u00000\u00050\u0001*\u00020\u00002\u0006\u0010\u0004\u001a\u00020\u0000¢\u0006\u0004\b\u0007\u0010\b\u001a\u000f\u0010\n\u001a\u0004\u0018\u00010\t¢\u0006\u0004\b\n\u0010\u000b\u001a\u0017\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u00000\u0001*\u00020\u0000¢\u0006\u0004\b\f\u0010\u0003\u001a\u0017\u0010\u000f\u001a\b\u0012\u0004\u0012\u00020\u000e0\r*\u00020\u0000¢\u0006\u0004\b\u000f\u0010\u0010\u001a\u001b\u0010\u0012\u001a\u0004\u0018\u00010\u0000*\u00020\u00002\u0006\u0010\u0011\u001a\u00020\u0000¢\u0006\u0004\b\u0012\u0010\u0013¨\u0006\u0014"}, d2 = {"", "Lkotlin/Result;", "f", "(Ljava/lang/String;)Ljava/lang/Object;", "shell", "Lkotlin/Pair;", "", "b", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;", "", ParcelUtils.f10526a, "()Ljava/lang/Float;", "c", "", "", "e", "(Ljava/lang/String;)[Ljava/lang/Long;", "regex", "d", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", "app_QCOMRelease"}, k = 2, mv = {2, 0, 0})
@SourceDebugExtension({"SMAP\nCmdUtil.kt\nKotlin\n*S Kotlin\n*F\n+ 1 CmdUtil.kt\ncom/ayaneo/settings/utils/shell/CmdUtilKt\n+ 2 ArraysJVM.kt\nkotlin/collections/ArraysKt__ArraysJVMKt\n*L\n1#1,109:1\n37#2,2:110\n*S KotlinDebug\n*F\n+ 1 CmdUtil.kt\ncom/ayaneo/settings/utils/shell/CmdUtilKt\n*L\n97#1:110,2\n*E\n"})
public final class CmdUtilKt {
    @Nullable
    public static final Float a() {
        String strD;
        String strD2;
        Float f2 = null;
        try {
            Result.Companion companion = Result.INSTANCE;
            if (AyaDevicesUtilKt.e()) {
                return Float.valueOf(TcRootShell.f17359a.b("top -m 1 -n 1 | grep cpu | awk '{print $1, $5}' | awk -F% '{print $1,$2}' | awk '{printf(\"%d\", ($1-$3)/8)}'", 0) / 100);
            }
            Process processExec = Runtime.getRuntime().exec("top -m 1 -n 1");
            InputStream inputStream = processExec.getInputStream();
            Intrinsics.o(inputStream, "getInputStream(...)");
            Reader inputStreamReader = new InputStreamReader(inputStream, Charsets.UTF_8);
            BufferedReader bufferedReader = inputStreamReader instanceof BufferedReader ? (BufferedReader) inputStreamReader : new BufferedReader(inputStreamReader, 8192);
            try {
                bufferedReader.readLine();
                bufferedReader.readLine();
                bufferedReader.readLine();
                String line = bufferedReader.readLine();
                Intrinsics.m(line);
                String strD3 = d(line, "\\d+%cpu");
                int i2 = FileDescriptorCounter.FILE_DESCRIPTOR_LIMIT;
                int i3 = (strD3 == null || (strD2 = d(strD3, "\\d+")) == null) ? 800 : Integer.parseInt(strD2);
                String strD4 = d(line, "\\d+%idle");
                if (strD4 != null && (strD = d(strD4, "\\d+")) != null) {
                    i2 = Integer.parseInt(strD);
                }
                Float fValueOf = Float.valueOf(((i3 - i2) * 1.0f) / i3);
                try {
                    processExec.exitValue();
                    try {
                        CloseableKt.a(bufferedReader, null);
                        processExec.destroy();
                        Result.m23constructorimpl(processExec);
                        return fValueOf;
                    } catch (Throwable th) {
                        th = th;
                        f2 = fValueOf;
                        Result.Companion companion2 = Result.INSTANCE;
                        Result.m23constructorimpl(ResultKt.a(th));
                        return f2;
                    }
                } catch (Throwable th2) {
                    th = th2;
                    f2 = fValueOf;
                    try {
                        throw th;
                    } catch (Throwable th3) {
                        CloseableKt.a(bufferedReader, th);
                        throw th3;
                    }
                }
            } catch (Throwable th4) {
                th = th4;
            }
        } catch (Throwable th5) {
            th = th5;
            Result.Companion companion3 = Result.INSTANCE;
            Result.m23constructorimpl(ResultKt.a(th));
            return f2;
        }
    }

    @NotNull
    public static final Object b(@NotNull String str, @NotNull String shell) {
        Intrinsics.p(str, "<this>");
        Intrinsics.p(shell, "shell");
        try {
            Result.Companion companion = Result.INSTANCE;
            Process processStart = new ProcessBuilder(shell, "-c", str).start();
            InputStream inputStream = processStart.getInputStream();
            Intrinsics.o(inputStream, "getInputStream(...)");
            Charset charset = Charsets.UTF_8;
            Reader inputStreamReader = new InputStreamReader(inputStream, charset);
            BufferedReader bufferedReader = inputStreamReader instanceof BufferedReader ? (BufferedReader) inputStreamReader : new BufferedReader(inputStreamReader, 8192);
            try {
                TextStreamsKt.k(bufferedReader);
                Unit unit = Unit.f21153a;
                CloseableKt.a(bufferedReader, null);
                InputStream errorStream = processStart.getErrorStream();
                Intrinsics.o(errorStream, "getErrorStream(...)");
                Reader inputStreamReader2 = new InputStreamReader(errorStream, charset);
                BufferedReader bufferedReader2 = inputStreamReader2 instanceof BufferedReader ? (BufferedReader) inputStreamReader2 : new BufferedReader(inputStreamReader2, 8192);
                try {
                    String strK = TextStreamsKt.k(bufferedReader2);
                    CloseableKt.a(bufferedReader2, null);
                    int iWaitFor = processStart.waitFor();
                    processStart.destroy();
                    return Result.m23constructorimpl(new Pair(Boolean.valueOf(iWaitFor == 0), strK));
                } catch (Throwable th) {
                    try {
                        throw th;
                    } catch (Throwable th2) {
                        CloseableKt.a(bufferedReader2, th);
                        throw th2;
                    }
                }
            } catch (Throwable th3) {
                try {
                    throw th3;
                } catch (Throwable th4) {
                    CloseableKt.a(bufferedReader, th3);
                    throw th4;
                }
            }
        } catch (Throwable th5) {
            Result.Companion companion2 = Result.INSTANCE;
            return Result.m23constructorimpl(ResultKt.a(th5));
        }
    }

    @NotNull
    public static final Object c(@NotNull String str) {
        Intrinsics.p(str, "<this>");
        try {
            Result.Companion companion = Result.INSTANCE;
            BufferedReader bufferedReader = new BufferedReader(new FileReader(str));
            try {
                String strK = TextStreamsKt.k(bufferedReader);
                CloseableKt.a(bufferedReader, null);
                return Result.m23constructorimpl(strK);
            } catch (Throwable th) {
                try {
                    throw th;
                } catch (Throwable th2) {
                    CloseableKt.a(bufferedReader, th);
                    throw th2;
                }
            }
        } catch (Throwable th3) {
            Result.Companion companion2 = Result.INSTANCE;
            return Result.m23constructorimpl(ResultKt.a(th3));
        }
    }

    @Nullable
    public static final String d(@NotNull String str, @NotNull String regex) {
        Intrinsics.p(str, "<this>");
        Intrinsics.p(regex, "regex");
        Matcher matcher = Pattern.compile(regex).matcher(str);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    @NotNull
    public static final Long[] e(@NotNull String str) {
        Intrinsics.p(str, "<this>");
        Matcher matcher = Pattern.compile("\\d+").matcher(str);
        ArrayList arrayList = new ArrayList();
        while (matcher.find()) {
            String strGroup = matcher.group();
            Intrinsics.m(strGroup);
            arrayList.add(Long.valueOf(Long.parseLong(strGroup)));
        }
        return (Long[]) arrayList.toArray(new Long[0]);
    }

    @NotNull
    public static final Object f(@NotNull String str) {
        Intrinsics.p(str, "<this>");
        try {
            Result.Companion companion = Result.INSTANCE;
            Process processExec = Runtime.getRuntime().exec(str);
            InputStream inputStream = processExec.getInputStream();
            Intrinsics.o(inputStream, "getInputStream(...)");
            Reader inputStreamReader = new InputStreamReader(inputStream, Charsets.UTF_8);
            BufferedReader bufferedReader = inputStreamReader instanceof BufferedReader ? (BufferedReader) inputStreamReader : new BufferedReader(inputStreamReader, 8192);
            try {
                String strK = TextStreamsKt.k(bufferedReader);
                processExec.waitFor();
                CloseableKt.a(bufferedReader, null);
                processExec.destroy();
                return Result.m23constructorimpl(strK);
            } catch (Throwable th) {
                try {
                    throw th;
                } catch (Throwable th2) {
                    CloseableKt.a(bufferedReader, th);
                    throw th2;
                }
            }
        } catch (Throwable th3) {
            Result.Companion companion2 = Result.INSTANCE;
            return Result.m23constructorimpl(ResultKt.a(th3));
        }
    }
}
