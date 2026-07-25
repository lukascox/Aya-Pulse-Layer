package com.ayaneo.gamewindow.utils.rgb;

import android.content.Context;
import com.ayaneo.devices.AyaDevicesKt;
import com.ayaneo.gamewindow.R;
import com.ayaneo.gamewindow.utils.system.AyaShareConfUtilKt;
import com.ayaneo.provider.AyaShareProvider;
import com.google.android.exoplayer2.metadata.icy.IcyHeaders;
import java.util.ArrayList;
import kotlin.Metadata;
import kotlin.Result;
import kotlin.jvm.internal.Intrinsics;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import timber.log.Timber;

/* JADX INFO: compiled from: RgbManager.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000F\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u000e\n\u0002\u0010\u000e\n\u0002\b\u0011\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\u0018\u0002\n\u0002\b\u000f\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0002\b\u000e\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002J\u000e\u0010>\u001a\u00020?2\u0006\u0010@\u001a\u00020AJ\u000e\u0010B\u001a\u00020?2\u0006\u0010@\u001a\u00020AJ\u000e\u0010C\u001a\u00020?2\u0006\u0010@\u001a\u00020AJ\u0018\u0010D\u001a\u0004\u0018\u00010\u00132\u0006\u0010E\u001a\u00020F2\u0006\u0010@\u001a\u00020AJ\u000e\u0010G\u001a\u00020?2\u0006\u0010@\u001a\u00020AJ\u000e\u0010H\u001a\u00020?2\u0006\u0010@\u001a\u00020AJ\u000e\u0010,\u001a\u00020\u00042\u0006\u0010@\u001a\u00020AJ\u000e\u0010I\u001a\u00020\u00132\u0006\u0010@\u001a\u00020AJ\u000e\u0010J\u001a\u00020?2\u0006\u0010@\u001a\u00020AJ\u0016\u0010K\u001a\u00020?2\u0006\u0010@\u001a\u00020AH\u0086@¢\u0006\u0002\u0010LJ\u0006\u0010E\u001a\u00020FJ\u0006\u0010M\u001a\u00020?J\u0012\u0010N\u001a\u0004\u0018\u00010\u00132\u0006\u0010@\u001a\u00020AH\u0002J\u0010\u0010O\u001a\u00020?2\b\u0010@\u001a\u0004\u0018\u00010AJ\u000e\u0010P\u001a\u00020?2\u0006\u0010@\u001a\u00020AJ\u0006\u0010Q\u001a\u00020?J\u000e\u0010R\u001a\u00020?2\u0006\u0010@\u001a\u00020AJ\u000e\u0010S\u001a\u00020?2\u0006\u0010@\u001a\u00020AR\u001a\u0010\u0003\u001a\u00020\u0004X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u0005\u0010\u0006\"\u0004\b\u0007\u0010\bR\u001a\u0010\t\u001a\u00020\u0004X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\n\u0010\u0006\"\u0004\b\u000b\u0010\bR\u001a\u0010\f\u001a\u00020\u0004X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\r\u0010\u0006\"\u0004\b\u000e\u0010\bR\u001a\u0010\u000f\u001a\u00020\u0004X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u0010\u0010\u0006\"\u0004\b\u0011\u0010\bR\u001a\u0010\u0012\u001a\u00020\u0013X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u0014\u0010\u0015\"\u0004\b\u0016\u0010\u0017R\u001a\u0010\u0018\u001a\u00020\u0013X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u0019\u0010\u0015\"\u0004\b\u001a\u0010\u0017R\u001a\u0010\u001b\u001a\u00020\u0013X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u001c\u0010\u0015\"\u0004\b\u001d\u0010\u0017R\u001a\u0010\u001e\u001a\u00020\u0013X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\u001f\u0010\u0015\"\u0004\b \u0010\u0017R\u001a\u0010!\u001a\u00020\u0013X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b\"\u0010\u0015\"\u0004\b#\u0010\u0017R*\u0010$\u001a\u0012\u0012\u0004\u0012\u00020\u00130%j\b\u0012\u0004\u0012\u00020\u0013`&X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b'\u0010(\"\u0004\b)\u0010*R\u001a\u0010+\u001a\u00020\u0004X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b,\u0010\u0006\"\u0004\b-\u0010\bR\u0011\u0010.\u001a\u00020/¢\u0006\b\n\u0000\u001a\u0004\b0\u00101R\u001a\u00102\u001a\u00020\u0013X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b3\u0010\u0015\"\u0004\b4\u0010\u0017R\u001a\u00105\u001a\u00020\u0013X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b6\u0010\u0015\"\u0004\b7\u0010\u0017R\u001a\u00108\u001a\u00020\u0013X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b9\u0010\u0015\"\u0004\b:\u0010\u0017R\u001a\u0010;\u001a\u00020\u0013X\u0086\u000e¢\u0006\u000e\n\u0000\u001a\u0004\b<\u0010\u0015\"\u0004\b=\u0010\u0017¨\u0006T"}, d2 = {"Lcom/ayaneo/gamewindow/utils/rgb/RgbManager;", "", "()V", "currentB", "", "getCurrentB", "()I", "setCurrentB", "(I)V", "currentBright", "getCurrentBright", "setCurrentBright", "currentG", "getCurrentG", "setCurrentG", "currentR", "getCurrentR", "setCurrentR", "defaultModeBright", "", "getDefaultModeBright", "()Ljava/lang/String;", "setDefaultModeBright", "(Ljava/lang/String;)V", "defaultModeColor", "getDefaultModeColor", "setDefaultModeColor", "followModeBackBright", "getFollowModeBackBright", "setFollowModeBackBright", "followModeBackColor", "getFollowModeBackColor", "setFollowModeBackColor", "followModeFrontColor", "getFollowModeFrontColor", "setFollowModeFrontColor", "mode", "Ljava/util/ArrayList;", "Lkotlin/collections/ArrayList;", "getMode", "()Ljava/util/ArrayList;", "setMode", "(Ljava/util/ArrayList;)V", "rgbMode", "getRgbMode", "setRgbMode", "rgbUtil", "Lcom/ayaneo/gamewindow/utils/rgb/RgbUtil;", "getRgbUtil", "()Lcom/ayaneo/gamewindow/utils/rgb/RgbUtil;", "singleBreathModeBright", "getSingleBreathModeBright", "setSingleBreathModeBright", "singleBreathModeColor", "getSingleBreathModeColor", "setSingleBreathModeColor", "singleModeBright", "getSingleModeBright", "setSingleModeBright", "singleModeColor", "getSingleModeColor", "setSingleModeColor", "breathGoogle", "", "context", "Landroid/content/Context;", "breathRgbCycle", "breathSingle", "changeRgbState", "isOpen", "", "defaultMode", "followMode", "getRgbModeText", "initModeStr", "initRgb", "(Landroid/content/Context;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "justClose", "openRgb", "recoverRgbState", "scanMode", "setRgbObserver", "singleColor", "waveMode", "gamewindow_QCOMRelease"}, k = 1, mv = {1, 9, 0}, xi = 48)
public final class RgbManager {

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    @NotNull
    public static final RgbManager f6047a = new RgbManager();

    /* JADX INFO: renamed from: b, reason: collision with root package name */
    public static int f6048b;

    /* JADX INFO: renamed from: c, reason: collision with root package name */
    @NotNull
    public static String f6049c;

    /* JADX INFO: renamed from: d, reason: collision with root package name */
    @NotNull
    public static String f6050d;

    @NotNull
    public static String e;

    @NotNull
    public static String f;

    @NotNull
    public static String g;

    @NotNull
    public static String h;

    @NotNull
    public static String i;

    @NotNull
    public static String j;

    @NotNull
    public static String k;
    public static int l;
    public static int m;
    public static int n;
    public static int o;

    @NotNull
    public static final RgbUtil p;

    @NotNull
    public static final ArrayList<String> q;

    static {
        AyaShareProvider ayaShareProvider = AyaShareProvider.f6263c;
        Object objG = ayaShareProvider.g("aya_rgb_mode.conf");
        if (Result.m20isFailureimpl(objG)) {
            objG = null;
        }
        f6048b = Integer.parseInt(AyaShareConfUtilKt.d((String) objG, "0"));
        f6049c = ayaShareProvider.f("aya_rgb_default_mode_color.conf", "255,255,255");
        f6050d = ayaShareProvider.f("aya_rgb_single_mode_color.conf", "255,255,255");
        e = ayaShareProvider.f("aya_rgb_breath_single_mode_color.conf", "0,255,0");
        f = ayaShareProvider.f("aya_rgb_follow_mode_front_color.conf", "235,0,255");
        g = ayaShareProvider.f("aya_rgb_follow_mode_back_color.conf", "0,0,255");
        h = ayaShareProvider.f("aya_rgb_default_mode_bright.conf", "100");
        i = ayaShareProvider.f("aya_rgb_single_mode_bright.conf", "100");
        j = ayaShareProvider.f("aya_rgb_breath_single_mode_bright.conf", "100");
        k = ayaShareProvider.f("aya_rgb_follow_mode_bright.conf", "100");
        p = AyaDevicesKt.f4814a.V();
        q = new ArrayList<>();
    }

    public static void a(@NotNull Context context) {
        AyaShareProvider ayaShareProvider = AyaShareProvider.f6263c;
        ayaShareProvider.j("aya_rgb_mode.conf", "3");
        ayaShareProvider.j("aya_rgb_is_open.conf", "true");
        Timber.f11226a.f("breathGoogle 4", new Object[0]);
        p.b(context);
    }

    public static void b(@NotNull Context context) {
        AyaShareProvider ayaShareProvider = AyaShareProvider.f6263c;
        ayaShareProvider.j("aya_rgb_mode.conf", "2");
        ayaShareProvider.j("aya_rgb_is_open.conf", "true");
        Timber.f11226a.f("breathRgbCycle 4", new Object[0]);
        p.e(context);
    }

    @Nullable
    public static String c(@NotNull Context context, boolean z) {
        Timber.Forest forest = Timber.f11226a;
        Object[] objArr = new Object[1];
        objArr[0] = z ? "开" : "关";
        forest.f("设备页,切换RGB灯效开关为:%s", objArr);
        RgbUtil rgbUtil = p;
        if (!z) {
            rgbUtil.i();
            return context.getString(R.string.closed_mode);
        }
        int i2 = f6048b;
        forest.f("当前RGB模式为 %s", Integer.valueOf(i2));
        switch (i2) {
            case 1:
                AyaShareProvider ayaShareProvider = AyaShareProvider.f6263c;
                ayaShareProvider.j("aya_rgb_mode.conf", IcyHeaders.REQUEST_HEADER_ENABLE_METADATA_VALUE);
                ayaShareProvider.j("aya_rgb_is_open.conf", "true");
                rgbUtil.a(context);
                return context.getString(R.string.breath_single);
            case 2:
                b(context);
                return context.getString(R.string.breath_rgb_recicle);
            case 3:
                a(context);
                return context.getString(R.string.breath_google);
            case 4:
                f(context);
                return context.getString(R.string.breath_scan);
            case 5:
                i(context);
                return context.getString(R.string.wave);
            case 6:
                h(context);
                rgbUtil.h(context);
                return context.getString(R.string.single_on);
            case 7:
                AyaShareProvider ayaShareProvider2 = AyaShareProvider.f6263c;
                ayaShareProvider2.j("aya_rgb_mode.conf", "7");
                ayaShareProvider2.j("aya_rgb_is_open.conf", "true");
                rgbUtil.c(context);
                return context.getString(R.string.rgb_mode_follow);
            default:
                AyaShareProvider ayaShareProvider3 = AyaShareProvider.f6263c;
                ayaShareProvider3.j("aya_rgb_mode.conf", "0");
                ayaShareProvider3.j("aya_rgb_is_open.conf", "true");
                rgbUtil.f(context);
                return context.getString(R.string.default_mode);
        }
    }

    public static void d(@NotNull Context context) {
        Intrinsics.e("context", context);
        ArrayList<String> arrayList = q;
        arrayList.clear();
        arrayList.add(context.getString(R.string.default_mode));
        arrayList.add(context.getString(R.string.breath_single));
        arrayList.add(context.getString(R.string.breath_rgb_recicle));
        arrayList.add(context.getString(R.string.breath_google));
        arrayList.add(context.getString(R.string.breath_scan));
        arrayList.add(context.getString(R.string.wave));
        arrayList.add(context.getString(R.string.single_on));
        arrayList.add(context.getString(R.string.rgb_mode_follow));
    }

    public static boolean e() {
        return Boolean.parseBoolean(AyaShareProvider.f6263c.f("aya_rgb_is_open.conf", String.valueOf(AyaDevicesKt.f4814a.getM())));
    }

    public static void f(@NotNull Context context) {
        AyaShareProvider ayaShareProvider = AyaShareProvider.f6263c;
        ayaShareProvider.j("aya_rgb_mode.conf", "4");
        ayaShareProvider.j("aya_rgb_is_open.conf", "true");
        Timber.f11226a.f("scanMode 4", new Object[0]);
        p.g(context);
    }

    public static void g() {
        BuildersKt.b(GlobalScope.f10144a, Dispatchers.f10129b, null, new RgbManager$setRgbObserver$1(null), 2);
    }

    public static void h(@NotNull Context context) {
        Timber.f11226a.f("写入shareText singleColor", new Object[0]);
        AyaShareProvider ayaShareProvider = AyaShareProvider.f6263c;
        ayaShareProvider.j("aya_rgb_mode.conf", "6");
        ayaShareProvider.j("aya_rgb_is_open.conf", "true");
    }

    public static void i(@NotNull Context context) {
        AyaShareProvider ayaShareProvider = AyaShareProvider.f6263c;
        ayaShareProvider.j("aya_rgb_mode.conf", "5");
        ayaShareProvider.j("aya_rgb_is_open.conf", "true");
        Timber.f11226a.f("waveMode 4", new Object[0]);
        p.d(context);
    }
}
