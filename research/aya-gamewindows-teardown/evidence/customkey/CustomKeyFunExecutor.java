package com.ayaneo.gamewindow.custom.keydetector;

import android.app.ActivityManager;
import android.app.Application;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.PointF;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import com.ayaneo.AyaDevicesUtilKt;
import com.ayaneo.devices.AyaDevicesKt;
import com.ayaneo.devices.IAyaDevices;
import com.ayaneo.gamewindow.AyaWindow;
import com.ayaneo.gamewindow.DisposableWindowImpl;
import com.ayaneo.gamewindow.LauncherApp;
import com.ayaneo.gamewindow.R;
import com.ayaneo.gamewindow.custom.datamodel.CustomKeyItem;
import com.ayaneo.gamewindow.custom.datamodel.FunInfo;
import com.ayaneo.gamewindow.custom.datamodel.KeyInfo;
import com.ayaneo.gamewindow.custom.datamodel.Parameter;
import com.ayaneo.gamewindow.screenrecord.ScreenRecordHelperKt;
import com.ayaneo.gamewindow.service.WindowKeyEventService;
import com.ayaneo.gamewindow.ui.window.magic.BrightnessFloat;
import com.ayaneo.gamewindow.ui.window.magic.fanction.MagicFunctioinActionKt;
import com.ayaneo.gamewindow.ui.window.main.MainPanelManager;
import com.ayaneo.gamewindow.ui.window.performance.PerformanceHolderKt;
import com.ayaneo.gamewindow.ui.window.performance.util.ModeConfiguration;
import com.ayaneo.gamewindow.ui.window.performance.util.PerformanceManager;
import com.ayaneo.gamewindow.utils.FAN_MODE;
import com.ayaneo.gamewindow.utils.UtilKt;
import com.ayaneo.gamewindow.utils.WindowUtilKt;
import com.ayaneo.gamewindow.utils.newserial.other.OtherControllerSerialManager;
import com.ayaneo.gamewindow.utils.rgb.RgbManager;
import com.ayaneo.gamewindow.utils.shell.CmdUtilKt;
import com.ayaneo.gamewindow.utils.system.BrightnessUtils;
import com.ayaneo.gamewindow.utils.system.SystemUtilKt;
import com.ayaneo.provider.AyaShareProvider;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import kotlin.Metadata;
import kotlin.NoWhenBranchMatchedException;
import kotlin.collections.CollectionsKt;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.SourceDebugExtension;
import kotlin.text.StringsKt;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.internal.MainDispatcherLoader;
import kotlinx.coroutines.scheduling.DefaultScheduler;
import org.jetbrains.annotations.NotNull;

/* JADX INFO: compiled from: CustomKeyFunExecutor.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000\\\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b*\n\u0002\u0010\t\n\u0002\b(\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0004\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002J\u000e\u0010W\u001a\u00020X2\u0006\u0010Y\u001a\u00020ZJ\u0010\u0010[\u001a\u00020X2\u0006\u0010Y\u001a\u00020ZH\u0002J\u0016\u0010\\\u001a\u00020]2\f\u0010^\u001a\b\u0012\u0004\u0012\u00020`0_H\u0002J\u0010\u0010a\u001a\u00020X2\u0006\u0010Y\u001a\u00020ZH\u0002J\u000e\u0010b\u001a\u00020X2\u0006\u0010c\u001a\u00020dJ\u0010\u0010e\u001a\u00020X2\u0006\u0010Y\u001a\u00020ZH\u0002J\u0010\u0010f\u001a\u00020X2\u0006\u0010Y\u001a\u00020ZH\u0002J\u0010\u0010g\u001a\u00020]2\u0006\u0010h\u001a\u00020iH\u0002J\u000e\u0010j\u001a\u00020X2\u0006\u0010Y\u001a\u00020ZJ\u0010\u0010k\u001a\u00020X2\u0006\u0010Y\u001a\u00020ZH\u0002J \u0010l\u001a\u00020X2\u0006\u0010Y\u001a\u00020Z2\u000e\b\u0002\u0010m\u001a\b\u0012\u0004\u0012\u00020n0_H\u0002J\u0010\u0010B\u001a\u00020X2\u0006\u0010Y\u001a\u00020ZH\u0002J\u0010\u0010o\u001a\u00020X2\u0006\u0010p\u001a\u00020qH\u0002J\u0010\u0010r\u001a\u00020X2\u0006\u0010Y\u001a\u00020ZH\u0002J\u0010\u0010s\u001a\u00020X2\u0006\u0010Y\u001a\u00020ZH\u0002J\u0010\u0010t\u001a\u00020X2\u0006\u0010p\u001a\u00020qH\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\n\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u000b\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\f\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\r\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u000f\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0010\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0011\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0012\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0013\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0014\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0015\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0016\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0017\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0018\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u0019\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u001a\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u001b\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u001c\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u001d\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u001e\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\u001f\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010 \u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010!\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010\"\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010#\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010$\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010%\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010&\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010'\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010(\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010)\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010*\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010+\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010,\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010-\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010.\u001a\u00020/X\u0082\u000e¢\u0006\u0002\n\u0000R\u000e\u00100\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u00101\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u00102\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u00103\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u00104\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u00105\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u00106\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u00107\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u00108\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u00109\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010:\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010;\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010<\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010=\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010>\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010?\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010@\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010A\u001a\u00020/X\u0082D¢\u0006\u0002\n\u0000R\u000e\u0010B\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010C\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010D\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010E\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010F\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010G\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010H\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010I\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010J\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010K\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010L\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010M\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010N\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010O\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010P\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010Q\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010R\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010S\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010T\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010U\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000R\u000e\u0010V\u001a\u00020\u0004X\u0086T¢\u0006\u0002\n\u0000¨\u0006u"}, d2 = {"Lcom/ayaneo/gamewindow/custom/keydetector/CustomKeyFunExecutor;", "", "()V", "connect", "", "connect_closeBluetooth", "connect_closeFlyMode", "connect_closeWifi", "connect_openBluetooth", "connect_openFlyMode", "connect_openWifi", "connect_switchBluetooth", "connect_switchFlyMode", "connect_switchWifi", "content", "content_copy", "content_cut", "content_opeUrl", "content_paste", "content_screenRecord", "content_screenShot", "display", "display_closeAutoAdjBrightness", "display_decreaseBrightness", "display_increaseBrightness", "display_openAutoAdjBrightness", "display_switchAutoAdjBrightness", "hc", "hc_DirectionDpadSwitch", "hc_RGBSwitch", "hc_performanceCoverSwitch", "hc_switchFanMode", "hc_switchHandleMode", "hc_switchPerformanceMode", "input", "input_clickScreen", "input_inputKeyCode", "input_inputKeyEvent", "input_inputText", "input_slideScreen", "keyb", "keyb_atCursorSelectWord", "keyb_cursorToEnd", "keyb_inputSpecifyKey", "keyb_switchInput", "keyb_switchKeyBoard", "lastTimeMill", "", "midea", "midea_backOff", "midea_fastForward", "midea_nextSong", "midea_pausePlay", "midea_previousSong", "midea_resumePlay", "midea_switchPlay", "nav", "nav_closeNoticeBar", "nav_closeStateBar", "nav_expandNoticeBar", "nav_expandStateBar", "nav_goBacckPreviousApp", "nav_goBackHome", "nav_return", "nav_turnToTaskWindow", "onTapTime", "openApp", "openAppFunCode", "screen", "screen_closeAutoRotateScreen", "screen_displayPowerMenu", "screen_forceLandScapeScreen", "screen_forcePortraitScreen", "screen_openAutoRotateScreen", "screen_sleepWakupScreen", "screen_switchAutoRotateScreen", "sound", "sound_closeDndMode", "sound_closeMute", "sound_decreaseVolume", "sound_displaySoundDialog", "sound_increaseVolume", "sound_openDndMode", "sound_openMute", "sound_selectSoundMode", "sound_switchDndMode", "sound_switchMute", "connectAboutFun", "", "funInfo", "Lcom/ayaneo/gamewindow/custom/datamodel/FunInfo;", "contentAboutFun", "curPageOnAppWhite", "", "appWhiteList", "", "Lcom/ayaneo/gamewindow/custom/datamodel/AppInfo;", "displayAboutFun", "executeItem", "item", "Lcom/ayaneo/gamewindow/custom/datamodel/CustomKeyItem;", "hvAboutFun", "inputAboutFun", "isIntentAvailable", "intent", "Landroid/content/Intent;", "keybAboutFun", "mediaAboutFun", "navAboutFun", "keyInfoList", "Lcom/ayaneo/gamewindow/custom/datamodel/KeyInfo;", "openUrl", "url", "", "screenAboutFun", "soundAboutFun", "tryOpenUrl", "gamewindow_QCOMRelease"}, k = 1, mv = {1, 9, 0}, xi = 48)
@SourceDebugExtension
public final class CustomKeyFunExecutor {

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    @NotNull
    public static final CustomKeyFunExecutor f4910a = new CustomKeyFunExecutor();

    /* JADX INFO: renamed from: b, reason: collision with root package name */
    public static final long f4911b = ExoPlayer.DEFAULT_DETACH_SURFACE_TIMEOUT_MS;

    /* JADX INFO: renamed from: c, reason: collision with root package name */
    public static long f4912c;

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r15v11 */
    /* JADX WARN: Type inference failed for: r15v12 */
    /* JADX WARN: Type inference failed for: r15v5 */
    /* JADX WARN: Type inference failed for: r15v6 */
    /* JADX WARN: Type inference failed for: r15v8 */
    /* JADX WARN: Type inference failed for: r15v9 */
    /* JADX WARN: Type inference failed for: r3v29 */
    /* JADX WARN: Type inference failed for: r3v30 */
    /* JADX WARN: Type inference failed for: r3v34 */
    /* JADX WARN: Type inference failed for: r4v54 */
    /* JADX WARN: Type inference failed for: r4v55 */
    /* JADX WARN: Type inference failed for: r4v56 */
    /* JADX WARN: Type inference failed for: r9v0 */
    /* JADX WARN: Type inference failed for: r9v10 */
    /* JADX WARN: Type inference failed for: r9v11 */
    /* JADX WARN: Type inference failed for: r9v2 */
    /* JADX WARN: Type inference failed for: r9v21 */
    /* JADX WARN: Type inference failed for: r9v3 */
    public static void a(@NotNull CustomKeyItem customKeyItem) throws InterruptedException {
        FAN_MODE fan_mode;
        FAN_MODE fanMode;
        ModeConfiguration modeConfiguration;
        AccessibilityNodeInfo accessibilityNodeInfoFindFocus;
        ComponentName componentName;
        ComponentName componentName2;
        Intrinsics.e("item", customKeyItem);
        for (FunInfo funInfo : customKeyItem.getFunInfoList()) {
            packageName = null;
            String packageName = null;
            int i = 6;
            int i2 = 1;
            switch (funInfo.getPCode()) {
                case 0:
                    if (funInfo.getExecutePar().size() == 2) {
                        String value = funInfo.getExecutePar().get(0).getValue();
                        String value2 = funInfo.getExecutePar().get(1).getValue();
                        Intent intent = new Intent();
                        intent.setClassName(value, value2);
                        intent.addFlags(268435456);
                        LauncherApp.e.getClass();
                        LauncherApp.Companion.a().startActivity(intent);
                    }
                    break;
                case 1:
                    int funCode = funInfo.getFunCode();
                    if (funCode == 0) {
                        new OtherControllerSerialManager().j();
                    } else if (funCode == 1) {
                        AyaWindow.f4858a.getClass();
                        if (!AyaWindow.f.d() && !AyaWindow.l()) {
                            PerformanceManager.f5864a.getClass();
                            int iE = PerformanceManager.e();
                            DisposableWindowImpl<MainPanelManager> disposableWindowImpl = AyaWindow.f4860c;
                            if (disposableWindowImpl.d() && ((MainPanelManager) disposableWindowImpl.b()).f5844c.g.getR() == R.id.tv_performance) {
                                MainPanelManager mainPanelManager = (MainPanelManager) disposableWindowImpl.c();
                                if (mainPanelManager != null) {
                                    mainPanelManager.a(iE);
                                }
                            } else {
                                PerformanceHolderKt.a(iE);
                            }
                        }
                    } else if (funCode == 2) {
                        PerformanceManager.f5864a.getClass();
                        int iC = PerformanceManager.c();
                        PerformanceManager.f5865b = false;
                        PerformanceManager.ConfigData configDataB = PerformanceManager.b();
                        ModeConfiguration modeConfiguration2 = configDataB.b().get(Integer.valueOf(iC));
                        FAN_MODE fanMode2 = modeConfiguration2 != null ? modeConfiguration2.getFanMode() : null;
                        int i3 = fanMode2 == null ? -1 : PerformanceManager.WhenMappings.f5868a[fanMode2.ordinal()];
                        if (i3 == -1) {
                            fan_mode = FAN_MODE.FAN_MODE_OFF;
                        } else if (i3 == 1) {
                            fan_mode = FAN_MODE.FAN_MODE_MUTE;
                        } else if (i3 == 2) {
                            fan_mode = FAN_MODE.FAN_MODE_BALANCE;
                        } else if (i3 == 3) {
                            fan_mode = FAN_MODE.FAN_MODE_TURBO;
                        } else if (i3 == 4) {
                            fan_mode = FAN_MODE.FAN_MODE_CUSTOM;
                        } else {
                            if (i3 != 5) {
                                throw new NoWhenBranchMatchedException();
                            }
                            fan_mode = FAN_MODE.FAN_MODE_OFF;
                        }
                        ModeConfiguration modeConfiguration3 = configDataB.b().get(Integer.valueOf(iC));
                        if (modeConfiguration3 != null && (fanMode = modeConfiguration3.getFanMode()) != null && (modeConfiguration = configDataB.b().get(Integer.valueOf(iC))) != null) {
                            modeConfiguration.h(fanMode);
                        }
                        ModeConfiguration modeConfiguration4 = configDataB.b().get(Integer.valueOf(iC));
                        if (modeConfiguration4 != null) {
                            modeConfiguration4.g(fan_mode);
                        }
                        PerformanceManager.f(configDataB, true);
                        LauncherApp.e.getClass();
                        UtilKt.j(a.a.k(LauncherApp.Companion.a().getString(R.string.switched_to), PerformanceManager.d(fan_mode)), LauncherApp.Companion.a());
                    } else if (funCode == 3) {
                        AyaWindow.f4858a.getClass();
                        if (AyaWindow.e.d()) {
                            AyaWindow.k();
                        } else {
                            AyaWindow.w();
                        }
                    } else if (funCode == 4) {
                        RgbManager.f6047a.getClass();
                        boolean z = !RgbManager.e();
                        LauncherApp.e.getClass();
                        RgbManager.c(LauncherApp.Companion.a(), z);
                    } else if (funCode == 5) {
                        MagicFunctioinActionKt.k();
                    }
                    break;
                case 2:
                    int funCode2 = funInfo.getFunCode();
                    if (funCode2 != 0) {
                        if (funCode2 != 1) {
                            if (funCode2 != 3) {
                                if (funCode2 == 4) {
                                    int i4 = 200;
                                    int i5 = 0;
                                    for (Object obj : funInfo.getExecutePar()) {
                                        int i6 = i5 + 1;
                                        if (i5 < 0) {
                                            CollectionsKt.f0();
                                            throw null;
                                        }
                                        Parameter parameter = (Parameter) obj;
                                        String type = parameter.getType();
                                        if (!Intrinsics.a(type, "imgPath")) {
                                            if (Intrinsics.a(type, "duration")) {
                                                i4 = (parameter.getValue().length() == 0) != false ? 200 : Integer.parseInt(parameter.getValue());
                                            } else if (Intrinsics.a(type, "xy")) {
                                                ArrayList arrayList = new ArrayList();
                                                int i7 = 0;
                                                for (Object obj2 : StringsKt.O(parameter.getValue(), new String[]{"|"}, 0, i)) {
                                                    int i8 = i7 + 1;
                                                    if (i7 < 0) {
                                                        CollectionsKt.f0();
                                                        throw null;
                                                    }
                                                    List listO = StringsKt.O((String) obj2, new String[]{","}, 0, i);
                                                    if (listO.size() >= 2) {
                                                        String str = (String) listO.get(0);
                                                        if ((str.length() == 0) != false) {
                                                            str = "0";
                                                        }
                                                        String str2 = (String) listO.get(1);
                                                        arrayList.add(new PointF(Float.parseFloat(str), Float.parseFloat((str2.length() == 0) == true ? "0" : str2)));
                                                    }
                                                    i = 6;
                                                    i7 = i8;
                                                }
                                                if (arrayList.size() > 2) {
                                                    int i9 = (int) ((PointF) arrayList.get(0)).x;
                                                    int i10 = (int) ((PointF) arrayList.get(0)).y;
                                                    int i11 = (int) ((PointF) arrayList.get(arrayList.size() - 1)).x;
                                                    int i12 = (int) ((PointF) arrayList.get(arrayList.size() - 1)).y;
                                                    StringBuilder sbW = a.a.w("input swipe ", i9, " ", i10, "  ");
                                                    sbW.append(i11);
                                                    sbW.append(" ");
                                                    sbW.append(i12);
                                                    sbW.append(" ");
                                                    sbW.append(i4);
                                                    CmdUtilKt.f(sbW.toString());
                                                }
                                            } else {
                                                continue;
                                            }
                                            break;
                                        }
                                        i = 6;
                                        i5 = i6;
                                    }
                                } else if (funCode2 == 5 && (!funInfo.getExecutePar().isEmpty())) {
                                    String value3 = funInfo.getExecutePar().get(0).getValue();
                                    Intrinsics.e("str", value3);
                                    WindowKeyEventService.f5444d.getClass();
                                    WindowKeyEventService windowKeyEventService = WindowKeyEventService.e;
                                    AccessibilityNodeInfo rootInActiveWindow = windowKeyEventService != null ? windowKeyEventService.getRootInActiveWindow() : null;
                                    if (rootInActiveWindow != null && (accessibilityNodeInfoFindFocus = rootInActiveWindow.findFocus(1)) != null) {
                                        if (accessibilityNodeInfoFindFocus.isEditable()) {
                                            CharSequence text = Intrinsics.a(accessibilityNodeInfoFindFocus.getHintText(), accessibilityNodeInfoFindFocus.getText()) ? "" : accessibilityNodeInfoFindFocus.getText();
                                            Bundle bundle = new Bundle();
                                            bundle.putCharSequence("ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE", ((Object) text) + value3);
                                            accessibilityNodeInfoFindFocus.performAction(2097152, bundle);
                                        }
                                        accessibilityNodeInfoFindFocus.recycle();
                                        rootInActiveWindow.recycle();
                                    }
                                }
                            } else if (!funInfo.getExecutePar().isEmpty()) {
                                List listO2 = StringsKt.O(funInfo.getExecutePar().get(0).getValue(), new String[]{","}, 0, 6);
                                if (listO2.size() >= 2) {
                                    CmdUtilKt.f("input tap " + listO2.get(0) + " " + listO2.get(1));
                                }
                            }
                        } else if (!funInfo.getExecutePar().isEmpty()) {
                            CmdUtilKt.e("input keyevent " + funInfo.getExecutePar().get(0).getValue());
                        }
                    } else if (!funInfo.getExecutePar().isEmpty()) {
                        Parameter parameter2 = funInfo.getExecutePar().get(0);
                        if ((parameter2.getValue().length() > 0 ? 1 : 0) != 0) {
                            CmdUtilKt.f("input text " + parameter2.getValue());
                        }
                    }
                    break;
                case 3:
                    List<KeyInfo> keyInfoList = customKeyItem.getKeyInfoList();
                    int funCode3 = funInfo.getFunCode();
                    if (funCode3 == 0) {
                        MagicFunctioinActionKt.d(83);
                    } else if (funCode3 == 1) {
                        MagicFunctioinActionKt.d(83);
                    } else if (funCode3 == 4) {
                        CmdUtilKt.f("input keyevent KEYCODE_BACK");
                    } else if (funCode3 != 5) {
                        if (funCode3 == 6) {
                            MagicFunctioinActionKt.e();
                        } else if (funCode3 == 7) {
                            CmdUtilKt.f("input keyevent KEYCODE_APP_SWITCH");
                        }
                    } else if (keyInfoList.size() != 1 || keyInfoList.get(0).getValue() != AyaDevicesKt.f4814a.getT()) {
                        Intent intent2 = new Intent("android.intent.action.MAIN");
                        intent2.addCategory("android.intent.category.HOME");
                        intent2.setFlags(268435456);
                        AyaWindow.f4858a.getClass();
                        AyaWindow.f4859b.startActivity(intent2);
                    } else if (AyaShareProvider.f6263c.c("home_double_confirm", false)) {
                        LauncherApp.e.getClass();
                        ActivityManager.RunningTaskInfo runningTaskInfoB = SystemUtilKt.b(LauncherApp.Companion.a());
                        if (Intrinsics.a((runningTaskInfoB == null || (componentName = runningTaskInfoB.topActivity) == null) ? null : componentName.getPackageName(), "com.ayaneo.home")) {
                            Application applicationA = LauncherApp.Companion.a();
                            Intent intent3 = new Intent("ayaGoHomeBroadcast");
                            intent3.setPackage("com.ayaneo.home");
                            applicationA.sendBroadcast(intent3);
                        } else {
                            long jCurrentTimeMillis = System.currentTimeMillis();
                            if (jCurrentTimeMillis - f4912c <= f4911b) {
                                WindowUtilKt.b();
                            } else {
                                f4912c = jCurrentTimeMillis;
                                GlobalScope globalScope = GlobalScope.f10144a;
                                DefaultScheduler defaultScheduler = Dispatchers.f10128a;
                                BuildersKt.b(globalScope, MainDispatcherLoader.f10388a, null, new CustomKeyFunExecutor$navAboutFun$1(null), 2);
                            }
                        }
                    } else {
                        LauncherApp.e.getClass();
                        ActivityManager.RunningTaskInfo runningTaskInfoB2 = SystemUtilKt.b(LauncherApp.Companion.a());
                        if (runningTaskInfoB2 != null && (componentName2 = runningTaskInfoB2.topActivity) != null) {
                            packageName = componentName2.getPackageName();
                        }
                        if (Intrinsics.a(packageName, "com.ayaneo.home")) {
                            Application applicationA2 = LauncherApp.Companion.a();
                            Intent intent4 = new Intent("ayaGoHomeBroadcast");
                            intent4.setPackage("com.ayaneo.home");
                            applicationA2.sendBroadcast(intent4);
                        } else {
                            WindowUtilKt.b();
                        }
                    }
                    break;
                case 4:
                    int funCode4 = funInfo.getFunCode();
                    int i13 = R.drawable.img_volum_mid;
                    switch (funCode4) {
                        case 0:
                            AyaWindow.f4858a.getClass();
                            AyaWindow.n();
                            LauncherApp.e.getClass();
                            int iA = SystemUtilKt.a(LauncherApp.Companion.a()) + 1;
                            int iE2 = SystemUtilKt.e(LauncherApp.Companion.a());
                            if (iA > iE2) {
                                iA = iE2;
                            }
                            SystemUtilKt.s(LauncherApp.Companion.a(), iA);
                            float f = iA / 15.0f;
                            if (f < 0.066f) {
                                i13 = R.drawable.img_volum_low;
                            } else if (f >= 0.5f) {
                                i13 = R.drawable.img_volum_high;
                            }
                            ((BrightnessFloat) AyaWindow.k.b()).setProgress(f, i13);
                            break;
                        case 1:
                            AyaWindow.f4858a.getClass();
                            AyaWindow.n();
                            LauncherApp.e.getClass();
                            int iA2 = SystemUtilKt.a(LauncherApp.Companion.a()) - 1;
                            int i14 = iA2 >= 0 ? iA2 : 0;
                            SystemUtilKt.s(LauncherApp.Companion.a(), i14);
                            float f2 = i14 / 15.0f;
                            if (f2 < 0.066f) {
                                i13 = R.drawable.img_volum_low;
                            } else if (f2 >= 0.5f) {
                                i13 = R.drawable.img_volum_high;
                            }
                            ((BrightnessFloat) AyaWindow.k.b()).setProgress(f2, i13);
                            break;
                        case 2:
                            AyaWindow.f4858a.getClass();
                            AyaWindow.n();
                            LauncherApp.e.getClass();
                            int iA3 = SystemUtilKt.a(LauncherApp.Companion.a());
                            SystemUtilKt.s(LauncherApp.Companion.a(), iA3);
                            float f3 = iA3 / 15.0f;
                            if (f3 < 0.066f) {
                                i13 = R.drawable.img_volum_low;
                            } else if (f3 >= 0.5f) {
                                i13 = R.drawable.img_volum_high;
                            }
                            ((BrightnessFloat) AyaWindow.k.b()).setProgress(f3, i13);
                            break;
                        case 4:
                            LauncherApp.e.getClass();
                            Object systemService = LauncherApp.Companion.a().getSystemService("notification");
                            Intrinsics.c("null cannot be cast to non-null type android.app.NotificationManager", systemService);
                            if ((((NotificationManager) systemService).getCurrentInterruptionFilter() == 3 ? 1 : 0) != 0) {
                                MagicFunctioinActionKt.c();
                            } else {
                                MagicFunctioinActionKt.h();
                            }
                            break;
                        case 5:
                            MagicFunctioinActionKt.h();
                            break;
                        case 6:
                            MagicFunctioinActionKt.c();
                            break;
                        case 7:
                            AyaWindow.f4858a.getClass();
                            AyaWindow.n();
                            LauncherApp.e.getClass();
                            if (SystemUtilKt.a(LauncherApp.Companion.a()) == 0) {
                                MagicFunctioinActionKt.i();
                            } else {
                                MagicFunctioinActionKt.f();
                            }
                            break;
                        case 8:
                            MagicFunctioinActionKt.f();
                            break;
                        case 9:
                            MagicFunctioinActionKt.i();
                            break;
                    }
                    break;
                case 5:
                    switch (funInfo.getFunCode()) {
                        case 0:
                            CmdUtilKt.f("input keyevent KEYCODE_MEDIA_PAUSE");
                            break;
                        case 1:
                            CmdUtilKt.f("input keyevent KEYCODE_MEDIA_PLAY");
                            break;
                        case 2:
                            CmdUtilKt.f("input keyevent KEYCODE_MEDIA_PLAY_PAUSE");
                            break;
                        case 3:
                            CmdUtilKt.f("input keyevent KEYCODE_MEDIA_PREVIOUS");
                            break;
                        case 4:
                            CmdUtilKt.f("input keyevent KEYCODE_MEDIA_NEXT");
                            break;
                        case 5:
                            CmdUtilKt.f("input keyevent KEYCODE_MEDIA_FAST_FORWARD");
                            break;
                        case 6:
                            CmdUtilKt.f("input keyevent KEYCODE_MEDIA_REWIND");
                            break;
                    }
                    break;
                case 6:
                    int funCode5 = funInfo.getFunCode();
                    if (funCode5 == 0) {
                        LauncherApp.e.getClass();
                        Settings.System.putInt(LauncherApp.Companion.a().getContentResolver(), "screen_brightness_mode", 1);
                    } else if (funCode5 == 1) {
                        LauncherApp.e.getClass();
                        Settings.System.putInt(LauncherApp.Companion.a().getContentResolver(), "screen_brightness_mode", 0);
                    } else if (funCode5 != 2) {
                        int i15 = R.drawable.img_brightness_mid;
                        if (funCode5 == 3) {
                            AyaWindow.f4858a.getClass();
                            AyaWindow.n();
                            int i16 = AyaDevicesUtilKt.f4811b ? 5 : 0;
                            IAyaDevices iAyaDevices = AyaDevicesKt.f4814a;
                            int i17 = iAyaDevices.getF() ? 255 : 65535;
                            LauncherApp.e.getClass();
                            int iMin = Math.min((i17 / 24) + SystemUtilKt.g(LauncherApp.Companion.a()), i17);
                            if (!iAyaDevices.getF()) {
                                iMin = BrightnessUtils.a(iMin, i16, i17);
                            }
                            SystemUtilKt.w(LauncherApp.Companion.a(), iMin);
                            if (iMin >= 245) {
                                i2 = 255;
                            } else if (iMin > 15) {
                                i2 = iMin;
                            }
                            float f4 = i2 / 255.0f;
                            if (f4 < 0.066f) {
                                i15 = R.drawable.img_brightness_low;
                            } else if (f4 >= 0.5f) {
                                i15 = R.drawable.img_brightness_high;
                            }
                            ((BrightnessFloat) AyaWindow.k.b()).setProgress(f4, i15);
                        } else if (funCode5 == 4) {
                            AyaWindow.f4858a.getClass();
                            AyaWindow.n();
                            int i18 = AyaDevicesUtilKt.f4811b ? 5 : 0;
                            IAyaDevices iAyaDevices2 = AyaDevicesKt.f4814a;
                            int i19 = iAyaDevices2.getF() ? 255 : 65535;
                            LauncherApp.e.getClass();
                            int iMax = Math.max(SystemUtilKt.g(LauncherApp.Companion.a()) - (i19 / 24), i18);
                            if (!iAyaDevices2.getF()) {
                                iMax = BrightnessUtils.a(iMax, i18, i19);
                            }
                            SystemUtilKt.w(LauncherApp.Companion.a(), iMax);
                            if (iMax >= 245) {
                                i2 = 255;
                            } else if (iMax > 15) {
                                i2 = iMax;
                            }
                            float f5 = i2 / 255.0f;
                            if (f5 < 0.066f) {
                                i15 = R.drawable.img_brightness_low;
                            } else if (f5 >= 0.5f) {
                                i15 = R.drawable.img_brightness_high;
                            }
                            ((BrightnessFloat) AyaWindow.k.b()).setProgress(f5, i15);
                        }
                    } else {
                        LauncherApp.Companion companion = LauncherApp.e;
                        companion.getClass();
                        int i20 = Settings.System.getInt(LauncherApp.Companion.a().getContentResolver(), "screen_brightness_mode") == 1 ? 1 : 0;
                        companion.getClass();
                        Settings.System.putInt(LauncherApp.Companion.a().getContentResolver(), "screen_brightness_mode", i20 ^ 1);
                    }
                    break;
                case 7:
                    int funCode6 = funInfo.getFunCode();
                    if (funCode6 == 0) {
                        LauncherApp.e.getClass();
                        ContentResolver contentResolver = LauncherApp.Companion.a().getContentResolver();
                        Intrinsics.d("getContentResolver(...)", contentResolver);
                        AyaDevicesKt.f4814a.J0(false);
                        Settings.System.putInt(contentResolver, "accelerometer_rotation", 1);
                    } else if (funCode6 == 1) {
                        MagicFunctioinActionKt.a();
                    } else if (funCode6 == 2) {
                        LauncherApp.Companion companion2 = LauncherApp.e;
                        companion2.getClass();
                        ContentResolver contentResolver2 = LauncherApp.Companion.a().getContentResolver();
                        Intrinsics.d("getContentResolver(...)", contentResolver2);
                        ?? r4 = Settings.System.getInt(contentResolver2, "accelerometer_rotation", 0) == 1;
                        IAyaDevices iAyaDevices3 = AyaDevicesKt.f4814a;
                        boolean zJ1 = iAyaDevices3.j1();
                        if (r4 != true || zJ1) {
                            companion2.getClass();
                            ContentResolver contentResolver3 = LauncherApp.Companion.a().getContentResolver();
                            Intrinsics.d("getContentResolver(...)", contentResolver3);
                            iAyaDevices3.J0(false);
                            Settings.System.putInt(contentResolver3, "accelerometer_rotation", 1);
                        } else {
                            MagicFunctioinActionKt.a();
                        }
                    } else if (funCode6 == 3) {
                        MagicFunctioinActionKt.a();
                        AyaDevicesKt.f4814a.J0(true);
                    } else if (funCode6 == 5) {
                        CmdUtilKt.f("input keyevent KEYCODE_POWER");
                    } else if (funCode6 == 6) {
                        CmdUtilKt.f("input keyevent --longpress POWER");
                    }
                    break;
                case 8:
                    int funCode7 = funInfo.getFunCode();
                    if (funCode7 == 0) {
                        String value4 = funInfo.getExecutePar().get(0).getValue();
                        if (StringsKt.Q(value4, "http", false)) {
                            b(value4);
                        } else {
                            b("https://".concat(value4));
                        }
                    } else if (funCode7 == 1) {
                        MagicFunctioinActionKt.d(277);
                    } else if (funCode7 == 2) {
                        MagicFunctioinActionKt.d(278);
                    } else if (funCode7 == 3) {
                        MagicFunctioinActionKt.d(279);
                    } else if (funCode7 == 4) {
                        MagicFunctioinActionKt.j();
                    } else if (funCode7 == 5) {
                        ScreenRecordHelperKt.a();
                    }
                    break;
                case 9:
                    int funCode8 = funInfo.getFunCode();
                    if (funCode8 == 0) {
                        CmdUtilKt.e("input keyevent 123");
                    } else if (funCode8 == 1) {
                        MagicFunctioinActionKt.d(111);
                    } else if (funCode8 == 2) {
                        CmdUtilKt.e("input keycombination -t 500 KEYCODE_CTRL_LEFT KEYCODE_A");
                    } else if (funCode8 == 3) {
                        LauncherApp.e.getClass();
                        String string = Settings.Secure.getString(LauncherApp.Companion.a().getContentResolver(), "default_input_method");
                        String string2 = Settings.Secure.getString(LauncherApp.Companion.a().getContentResolver(), "enabled_input_methods");
                        Intrinsics.b(string2);
                        List listO3 = StringsKt.O(string2, new String[]{":"}, 0, 6);
                        int i21 = 0;
                        int i22 = 0;
                        for (Object obj3 : listO3) {
                            int i23 = i22 + 1;
                            if (i22 < 0) {
                                CollectionsKt.f0();
                                throw null;
                            }
                            if (Intrinsics.a(string, (String) obj3)) {
                                i21 = i23 >= listO3.size() ? 0 : i23;
                            }
                            i22 = i23;
                        }
                        LauncherApp.e.getClass();
                        Settings.Secure.putString(LauncherApp.Companion.a().getContentResolver(), "default_input_method", (String) listO3.get(i21));
                    } else if (funCode8 == 4 && (!funInfo.getExecutePar().isEmpty())) {
                        ArrayList arrayListO0 = CollectionsKt.o0(StringsKt.O(funInfo.getExecutePar().get(0).getValue(), new String[]{","}, 0, 6));
                        arrayListO0.removeIf(new Predicate() { // from class: com.ayaneo.gamewindow.custom.keydetector.b
                            @Override // java.util.function.Predicate
                            public final boolean test(Object obj4) {
                                return "".equals(obj4);
                            }
                        });
                        Iterator it = arrayListO0.iterator();
                        while (it.hasNext()) {
                            CmdUtilKt.e("input keyevent " + ((String) it.next()));
                        }
                    }
                    break;
                case 10:
                    switch (funInfo.getFunCode()) {
                        case 0:
                            LauncherApp.e.getClass();
                            Object systemService2 = LauncherApp.Companion.a().getSystemService("wifi");
                            Intrinsics.c("null cannot be cast to non-null type android.net.wifi.WifiManager", systemService2);
                            WifiManager wifiManager = (WifiManager) systemService2;
                            wifiManager.setWifiEnabled(!wifiManager.isWifiEnabled());
                            break;
                        case 1:
                            LauncherApp.e.getClass();
                            Object systemService3 = LauncherApp.Companion.a().getSystemService("wifi");
                            Intrinsics.c("null cannot be cast to non-null type android.net.wifi.WifiManager", systemService3);
                            ((WifiManager) systemService3).setWifiEnabled(true);
                            break;
                        case 2:
                            LauncherApp.e.getClass();
                            Object systemService4 = LauncherApp.Companion.a().getSystemService("wifi");
                            Intrinsics.c("null cannot be cast to non-null type android.net.wifi.WifiManager", systemService4);
                            ((WifiManager) systemService4).setWifiEnabled(false);
                            break;
                        case 3:
                            if (BluetoothAdapter.getDefaultAdapter().isEnabled()) {
                                MagicFunctioinActionKt.b();
                            } else {
                                MagicFunctioinActionKt.g();
                            }
                            break;
                        case 4:
                            MagicFunctioinActionKt.g();
                            break;
                        case 5:
                            MagicFunctioinActionKt.b();
                            break;
                        case 6:
                            LauncherApp.e.getClass();
                            int i24 = Settings.Global.getInt(LauncherApp.Companion.a().getContentResolver(), "airplane_mode_on") == 1 ? 0 : 1;
                            Settings.Global.putInt(LauncherApp.Companion.a().getContentResolver(), "airplane_mode_on", i24);
                            Intent intent5 = new Intent("android.intent.action.AIRPLANE_MODE");
                            intent5.putExtra("state", i24 == 1);
                            LauncherApp.Companion.a().sendBroadcast(intent5);
                            break;
                        case 7:
                            LauncherApp.e.getClass();
                            Settings.Global.putInt(LauncherApp.Companion.a().getContentResolver(), "airplane_mode_on", 1);
                            Intent intent6 = new Intent("android.intent.action.AIRPLANE_MODE");
                            intent6.putExtra("state", true);
                            LauncherApp.Companion.a().sendBroadcast(intent6);
                            break;
                        case 8:
                            LauncherApp.e.getClass();
                            Settings.Global.putInt(LauncherApp.Companion.a().getContentResolver(), "airplane_mode_on", 0);
                            Intent intent7 = new Intent("android.intent.action.AIRPLANE_MODE");
                            intent7.putExtra("state", false);
                            LauncherApp.Companion.a().sendBroadcast(intent7);
                            break;
                    }
                    break;
            }
        }
    }

    public static void b(String str) {
        try {
            Intent intent = new Intent("android.intent.action.VIEW", Uri.parse(str));
            LauncherApp.e.getClass();
            List<ResolveInfo> listQueryIntentActivities = LauncherApp.Companion.a().getPackageManager().queryIntentActivities(intent, C.DEFAULT_BUFFER_SEGMENT_SIZE);
            Intrinsics.d("queryIntentActivities(...)", listQueryIntentActivities);
            if (!listQueryIntentActivities.isEmpty()) {
                intent.setFlags(268435456);
                LauncherApp.Companion.a().startActivity(intent);
            } else {
                b("http://" + str);
            }
        } catch (Exception e) {
            Log.i("DBF", "tryOpenUrl: " + e.getMessage());
        }
    }
}
