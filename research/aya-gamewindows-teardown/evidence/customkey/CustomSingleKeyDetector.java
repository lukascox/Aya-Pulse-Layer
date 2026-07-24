package com.ayaneo.gamewindow.custom.keydetector;

import android.os.Handler;
import android.view.KeyEvent;
import com.ayaneo.gamewindow.LauncherApp;
import com.ayaneo.gamewindow.custom.datamodel.CustomKeyItem;
import com.ayaneo.gamewindow.observer.CurrentTaskInfo;
import com.ayaneo.gamewindow.observer.TaskStackObserverKt;
import kotlin.Metadata;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

/* JADX INFO: compiled from: CustomKeyDetector.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000D\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0018\u0002\n\u0002\b\u0004\n\u0002\u0010\u000b\n\u0002\b\u0005\n\u0002\u0010\t\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\u0018\u00002\u00020\u00012\u00020\u0002B'\u0012\u0006\u0010\u0003\u001a\u00020\u0004\u0012\u0006\u0010\u0005\u001a\u00020\u0004\u0012\b\b\u0002\u0010\u0006\u001a\u00020\u0004\u0012\u0006\u0010\u0007\u001a\u00020\b¢\u0006\u0002\u0010\tJ\u0010\u0010\u001a\u001a\u00020\u00042\u0006\u0010\u001b\u001a\u00020\u001cH\u0016J\b\u0010\u001d\u001a\u00020\u001eH\u0016J\b\u0010\u001f\u001a\u00020\u001eH\u0016R\u0014\u0010\u0006\u001a\u00020\u0004X\u0096\u0004¢\u0006\b\n\u0000\u001a\u0004\b\n\u0010\u000bR\u0014\u0010\u0005\u001a\u00020\u0004X\u0096\u0004¢\u0006\b\n\u0000\u001a\u0004\b\f\u0010\u000bR\u0016\u0010\r\u001a\n \u000f*\u0004\u0018\u00010\u000e0\u000eX\u0082\u0004¢\u0006\u0002\n\u0000R\u0014\u0010\u0007\u001a\u00020\bX\u0096\u0004¢\u0006\b\n\u0000\u001a\u0004\b\u0010\u0010\u0011R\u000e\u0010\u0012\u001a\u00020\u0013X\u0082\u000e¢\u0006\u0002\n\u0000R\u0014\u0010\u0014\u001a\u00020\u0013X\u0096D¢\u0006\b\n\u0000\u001a\u0004\b\u0014\u0010\u0015R\u000e\u0010\u0016\u001a\u00020\u0013X\u0082\u000e¢\u0006\u0002\n\u0000R\u0011\u0010\u0003\u001a\u00020\u0004¢\u0006\b\n\u0000\u001a\u0004\b\u0017\u0010\u000bR\u000e\u0010\u0018\u001a\u00020\u0019X\u0082\u000e¢\u0006\u0002\n\u0000¨\u0006 "}, d2 = {"Lcom/ayaneo/gamewindow/custom/keydetector/CustomSingleKeyDetector;", "Lcom/ayaneo/gamewindow/custom/keydetector/CustomKeyDetector;", "Ljava/lang/Runnable;", "keyCode", "", "functionType", "flag", "info", "Lcom/ayaneo/gamewindow/custom/datamodel/CustomKeyItem;", "(IIILcom/ayaneo/gamewindow/custom/datamodel/CustomKeyItem;)V", "getFlag", "()I", "getFunctionType", "handler", "Landroid/os/Handler;", "kotlin.jvm.PlatformType", "getInfo", "()Lcom/ayaneo/gamewindow/custom/datamodel/CustomKeyItem;", "isActive", "", "isCombination", "()Z", "isLongPressActive", "getKeyCode", "lastDownTime", "", "dispatchKeyEvent", "keyEvent", "Landroid/view/KeyEvent;", "resetState", "", "run", "gamewindow_QCOMRelease"}, k = 1, mv = {1, 9, 0}, xi = 48)
public final class CustomSingleKeyDetector implements CustomKeyDetector, Runnable {

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    public final int f4913a;

    /* JADX INFO: renamed from: b, reason: collision with root package name */
    public final int f4914b;

    /* JADX INFO: renamed from: c, reason: collision with root package name */
    @NotNull
    public final CustomKeyItem f4915c;

    /* JADX INFO: renamed from: d, reason: collision with root package name */
    public long f4916d;
    public boolean e;
    public boolean f;
    public final Handler g;

    public CustomSingleKeyDetector(int i, int i2, @NotNull CustomKeyItem customKeyItem) {
        this.f4913a = i;
        this.f4914b = i2;
        this.f4915c = customKeyItem;
        LauncherApp.e.getClass();
        this.g = LauncherApp.Companion.a().getMainThreadHandler();
    }

    @Override // com.ayaneo.gamewindow.custom.keydetector.CustomKeyDetector
    @NotNull
    /* JADX INFO: renamed from: a, reason: from getter */
    public final CustomKeyItem getG() {
        return this.f4915c;
    }

    @Override // com.ayaneo.gamewindow.custom.keydetector.CustomKeyDetector
    /* JADX INFO: renamed from: b, reason: from getter */
    public final int getF() {
        return this.f4914b;
    }

    @Override // com.ayaneo.gamewindow.custom.keydetector.CustomKeyDetector
    public final void c() {
        this.g.removeCallbacks(this);
        this.e = false;
        this.f4916d = 0L;
    }

    @Override // com.ayaneo.gamewindow.custom.keydetector.CustomKeyDetector
    public final int dispatchKeyEvent(@NotNull KeyEvent keyEvent) {
        boolean z;
        Intrinsics.e("keyEvent", keyEvent);
        if ((keyEvent.getKeyCode() <= 0 ? keyEvent.getScanCode() : keyEvent.getKeyCode()) != this.f4913a) {
            c();
            this.f = false;
            return 0;
        }
        CurrentTaskInfo currentTaskInfo = TaskStackObserverKt.f5350a.f5327a;
        String str = currentTaskInfo.f5333a;
        String str2 = currentTaskInfo.f5334b;
        CustomKeyItem customKeyItem = this.f4915c;
        if (customKeyItem.getHasAppWhiteList() && !customKeyItem.isInAppWhite(str, str2)) {
            c();
            return 0;
        }
        int action = keyEvent.getAction();
        Handler handler = this.g;
        if (action == 0) {
            this.e = true;
            this.f4916d = System.currentTimeMillis();
            this.f = false;
            if (CustomKeyDetector.DefaultImpls.a(this)) {
                handler.removeCallbacks(this);
                handler.postDelayed(this, 500L);
            }
            return 1;
        }
        if (CustomKeyDetector.DefaultImpls.a(this)) {
            handler.removeCallbacks(this);
        }
        if (!this.e || CustomKeyDetector.DefaultImpls.a(this) || System.currentTimeMillis() - this.f4916d >= 500) {
            z = false;
        } else {
            CustomKeyDetector.DefaultImpls.b(this);
            z = true;
        }
        boolean z2 = this.f;
        if (z2) {
            z = z2;
        }
        c();
        this.f = false;
        return z ? 2 : 1;
    }

    @Override // java.lang.Runnable
    public final void run() {
        this.f = true;
        CustomKeyDetector.DefaultImpls.b(this);
    }
}
