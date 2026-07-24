package com.ayaneo.gamewindow.custom.keydetector;

import android.os.Handler;
import com.ayaneo.AyaDevicesUtilKt;
import com.ayaneo.devices.AyaDevicesKt;
import com.ayaneo.gamewindow.LauncherApp;
import com.ayaneo.gamewindow.custom.datamodel.CustomKeyItem;
import com.ayaneo.gamewindow.custom.datamodel.FunInfo;
import com.ayaneo.gamewindow.custom.datamodel.KeyInfo;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory;
import java.util.ArrayList;
import java.util.List;
import kotlin.Metadata;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;

/* JADX INFO: compiled from: CustomKeyDispatch.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000b\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010!\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0004\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002J\u0012\u0010\u0013\u001a\u0004\u0018\u00010\u00142\u0006\u0010\u0015\u001a\u00020\rH\u0002J\u0006\u0010\u0016\u001a\u00020\u0004J\u0016\u0010\u0017\u001a\b\u0012\u0004\u0012\u00020\u00180\u00122\u0006\u0010\u0019\u001a\u00020\u0004H\u0002J\u001a\u0010\u001a\u001a\u00020\r2\u0006\u0010\u0019\u001a\u00020\u00042\b\b\u0002\u0010\u001b\u001a\u00020\u001cH\u0002J\u0016\u0010\u001d\u001a\b\u0012\u0004\u0012\u00020\u001e0\u00122\u0006\u0010\u0019\u001a\u00020\u0004H\u0002J\u0006\u0010\u001f\u001a\u00020 J\u0018\u0010!\u001a\n\u0012\u0004\u0012\u00020\r\u0018\u00010\f2\u0006\u0010\"\u001a\u00020\u0004H\u0002J\u0018\u0010#\u001a\u00020\u00042\u000e\u0010$\u001a\n\u0012\u0004\u0012\u00020\r\u0018\u00010\fH\u0002J\u000e\u0010%\u001a\u00020&2\u0006\u0010'\u001a\u00020(J\u000e\u0010)\u001a\u00020 2\u0006\u0010\"\u001a\u00020\u0004J\b\u0010*\u001a\u00020\u0004H\u0002J\u000e\u0010+\u001a\u00020 2\u0006\u0010\u0015\u001a\u00020\rR\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000RJ\u0010\n\u001a>\u0012\u0018\u0012\u0016\u0012\u0004\u0012\u00020\r \u000e*\n\u0012\u0004\u0012\u00020\r\u0018\u00010\f0\f \u000e*\u001e\u0012\u0018\u0012\u0016\u0012\u0004\u0012\u00020\r \u000e*\n\u0012\u0004\u0012\u00020\r\u0018\u00010\f0\f\u0018\u00010\u000b0\u000bX\u0082\u0004¢\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0010X\u0082\u0004¢\u0006\u0002\n\u0000R\u0014\u0010\u0011\u001a\b\u0012\u0004\u0012\u00020\r0\u0012X\u0082\u0004¢\u0006\u0002\n\u0000¨\u0006,"}, d2 = {"Lcom/ayaneo/gamewindow/custom/keydetector/CustomKeyDispatch;", "", "()V", "kNameD", "", "kNameLC", "kNameMode", "kNameRC", "kNameSE", "kNameTurbo", "listAdapter", "Lcom/squareup/moshi/JsonAdapter;", "", "Lcom/ayaneo/gamewindow/custom/datamodel/CustomKeyItem;", "kotlin.jvm.PlatformType", "runnable", "Ljava/lang/Runnable;", "taskList", "", "conversionToDetector", "Lcom/ayaneo/gamewindow/custom/keydetector/CustomKeyDetector;", "item", "generateDef", "getDEfFunInfoList", "Lcom/ayaneo/gamewindow/custom/datamodel/FunInfo;", "keyName", "getDefCustomKeyInfo", "offset", "", "getDefKeyInfoList", "Lcom/ayaneo/gamewindow/custom/datamodel/KeyInfo;", "init", "", "jsonToList", "json", "listToJson", "list", "onCheckCustomKey", "", "keyEvent", "Landroid/view/KeyEvent;", "parseConfiguration", "queryCustomKeyConfig", "submitItem", "gamewindow_QCOMRelease"}, k = 1, mv = {1, 9, 0}, xi = 48)
@SourceDebugExtension
public final class CustomKeyDispatch {

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    @NotNull
    public static final CustomKeyDispatch f4905a = new CustomKeyDispatch();

    /* JADX INFO: renamed from: b, reason: collision with root package name */
    @NotNull
    public static final ArrayList f4906b = new ArrayList();

    /* JADX INFO: renamed from: c, reason: collision with root package name */
    @NotNull
    public static final com.ayaneo.gamewindow.a f4907c = new com.ayaneo.gamewindow.a(1);

    /* JADX INFO: renamed from: d, reason: collision with root package name */
    public static final JsonAdapter<List<CustomKeyItem>> f4908d = new Moshi.Builder().addLast((JsonAdapter.Factory) new KotlinJsonAdapterFactory()).build().adapter(Types.newParameterizedType(List.class, CustomKeyItem.class));

    @NotNull
    public static String a() {
        List<CustomKeyItem> listK;
        if (AyaDevicesUtilKt.f4810a || AyaDevicesUtilKt.l) {
            listK = CollectionsKt.K(b(0, "LC"), b(1, "RC"), b(2, "="));
        } else if (AyaDevicesUtilKt.e || AyaDevicesUtilKt.f) {
            listK = CollectionsKt.K(b(1, "RC"), b(2, "="));
        } else {
            listK = AyaDevicesUtilKt.o ? CollectionsKt.K(b(0, "MODE"), b(1, "RC"), b(2, "=")) : CollectionsKt.K(b(0, "LC"), b(1, "RC"), b(2, "="), b(3, "Turbo"));
        }
        String json = f4908d.toJson(listK);
        Intrinsics.b(json);
        return json;
    }

    /* JADX WARN: Code duplicated, block: B:22:0x0069  */
    public static CustomKeyItem b(int i, String str) {
        KeyInfo keyInfo;
        long jCurrentTimeMillis = System.currentTimeMillis() + ((long) i);
        int iHashCode = str.hashCode();
        if (iHashCode != 61) {
            if (iHashCode != 2423) {
                if (iHashCode != 2609) {
                    if (iHashCode == 2372003 && str.equals("MODE")) {
                        keyInfo = new KeyInfo(str, AyaDevicesKt.f4814a.z());
                    } else {
                        keyInfo = new KeyInfo(str, AyaDevicesKt.f4814a.getF());
                    }
                } else if (str.equals("RC")) {
                    keyInfo = new KeyInfo(str, AyaDevicesKt.f4814a.getM());
                } else {
                    keyInfo = new KeyInfo(str, AyaDevicesKt.f4814a.getF());
                }
            } else if (str.equals("LC")) {
                keyInfo = new KeyInfo(str, AyaDevicesKt.f4814a.getF4854d());
            } else {
                keyInfo = new KeyInfo(str, AyaDevicesKt.f4814a.getF());
            }
        } else if (str.equals("=")) {
            keyInfo = new KeyInfo(str, AyaDevicesKt.f4814a.getF4853c());
        } else {
            keyInfo = new KeyInfo(str, AyaDevicesKt.f4814a.getF());
        }
        ArrayList arrayListN = CollectionsKt.N(keyInfo);
        FunInfo funInfo = new FunInfo("", 1, 1, new ArrayList());
        FunInfo funInfo2 = new FunInfo("", 3, 7, new ArrayList());
        FunInfo funInfo3 = new FunInfo("", 3, 4, new ArrayList());
        FunInfo funInfo4 = new FunInfo("", 3, 5, new ArrayList());
        FunInfo funInfo5 = new FunInfo("", 1, 1, new ArrayList());
        int iHashCode2 = str.hashCode();
        if (iHashCode2 != 61) {
            if (iHashCode2 != 2423) {
                if (iHashCode2 != 2609) {
                    if (iHashCode2 == 2372003 && str.equals("MODE")) {
                        funInfo5 = funInfo;
                    }
                } else if (str.equals("RC")) {
                    funInfo5 = funInfo3;
                }
            } else if (str.equals("LC")) {
                funInfo5 = funInfo2;
            }
        } else if (str.equals("=")) {
            funInfo5 = funInfo4;
        }
        return new CustomKeyItem(jCurrentTimeMillis, false, false, arrayListN, CollectionsKt.N(funInfo5), new ArrayList(), false, 0L, 198, null);
    }

    public static void c(@NotNull String str) {
        CustomKeyDetector combinationSameTimeDetector;
        if (str.length() == 0) {
            str = a();
        }
        GlobalKeyInterceptKt.f4920d.clear();
        List<CustomKeyItem> listFromJson = str.length() == 0 ? null : f4908d.fromJson(str);
        if (listFromJson != null) {
            for (CustomKeyItem customKeyItem : listFromJson) {
                f4905a.getClass();
                int size = customKeyItem.getKeyInfoList().size();
                int i = customKeyItem.isShortClick() ? 0 : 2;
                if (size == 1) {
                    combinationSameTimeDetector = new CustomSingleKeyDetector(customKeyItem.getKeyInfoList().get(0).getValue(), i, customKeyItem);
                } else if (size <= 1 || !customKeyItem.isTogether()) {
                    combinationSameTimeDetector = null;
                } else {
                    int size2 = customKeyItem.getKeyInfoList().size();
                    Integer[] numArr = new Integer[size2];
                    for (int i2 = 0; i2 < size2; i2++) {
                        numArr[i2] = Integer.valueOf(customKeyItem.getKeyInfoList().get(i2).getValue());
                    }
                    combinationSameTimeDetector = new CombinationSameTimeDetector(numArr, i, customKeyItem);
                }
                if (combinationSameTimeDetector != null) {
                    GlobalKeyInterceptKt.f4920d.add(combinationSameTimeDetector);
                }
            }
        }
    }

    public static void d(@NotNull CustomKeyItem customKeyItem) {
        Intrinsics.e("item", customKeyItem);
        f4906b.add(customKeyItem);
        try {
            LauncherApp.e.getClass();
            Handler mainThreadHandler = LauncherApp.Companion.a().getMainThreadHandler();
            com.ayaneo.gamewindow.a aVar = f4907c;
            mainThreadHandler.removeCallbacks(aVar);
            LauncherApp.Companion.a().getMainThreadHandler().postDelayed(aVar, 100L);
        } catch (Exception e) {
            System.out.println((Object) e.getMessage());
        }
    }
}
