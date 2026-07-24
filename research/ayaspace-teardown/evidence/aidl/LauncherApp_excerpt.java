// Curated excerpt from com/ayaneo/settings/LauncherApp.java (full file has Hilt/DI
// boilerplate not relevant here). Source: AYA Settings v1.1.112 (versionCode 147),
// app_QCOMRelease build variant. This is where the AIDL bind target is defined --
// see AyaAidlManager.h() in AyaAidlManager.java, which does:
//   intent.setClassName(LauncherApp.f14073k, LauncherApp.f14074l)
//   bindService(intent, mConnection, Context.BIND_AUTO_CREATE)

package com.ayaneo.settings;

public final class LauncherApp {

    // field f14072j
    public static final String SETTINGS_PKG_NAME = "com.ayaneo.settings";

    // field f14073k -- bindService() target package
    public static final String WINDOW_PKG_NAME = "com.ayaneo.gamewindow";

    // field f14074l -- bindService() target class (the actual privileged component)
    public static final String WINDOW_AIDL_SERVICE = "com.ayaneo.gamewindow.utils.aidl.AyaAidlService";

    public static final String HOME_PKG_NAME = "com.ayaneo.home";

    public static final String GAME_LAUNCHER_PKG_NAME = "com.ayaneo.gamelauncher";

    public static final String GAME_LAUNCHER_MAIN_ACTIVITY_NAME = "com.ayaneo.gamelauncher.ui.global.MainActivity";
}
