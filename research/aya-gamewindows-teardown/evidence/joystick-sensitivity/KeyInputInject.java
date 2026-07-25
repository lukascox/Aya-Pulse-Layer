package com.ayaneo.gamewindow;

import android.hardware.input.InputManager;
import android.view.KeyEvent;
import com.input.source.AndroidInputReader;
import kotlin.Metadata;
import kotlin.Result;
import kotlin.ResultKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/* JADX INFO: compiled from: KeyInputInject.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0010\t\n\u0002\b\t\n\u0002\u0018\u0002\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0002\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002J\u000e\u0010\u0003\u001a\u00020\u00042\u0006\u0010\u0005\u001a\u00020\u0006Jz\u0010\u0007\u001a\u00020\u00042\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\t2\b\b\u0002\u0010\u000b\u001a\u00020\t2\b\b\u0002\u0010\f\u001a\u00020\r2\b\b\u0002\u0010\u000e\u001a\u00020\r2\b\b\u0002\u0010\u000f\u001a\u00020\t2\b\b\u0002\u0010\u0010\u001a\u00020\t2\b\b\u0002\u0010\u0011\u001a\u00020\t2\b\b\u0002\u0010\u0012\u001a\u00020\t2\b\b\u0002\u0010\u0013\u001a\u00020\t2\b\b\u0002\u0010\u0014\u001a\u00020\t2\b\b\u0002\u0010\u0015\u001a\u00020\tJr\u0010\u0016\u001a\u0004\u0018\u00010\u00172\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\t2\b\b\u0002\u0010\u000b\u001a\u00020\t2\b\b\u0002\u0010\f\u001a\u00020\r2\b\b\u0002\u0010\u000e\u001a\u00020\r2\b\b\u0002\u0010\u000f\u001a\u00020\t2\b\b\u0002\u0010\u0010\u001a\u00020\t2\b\b\u0002\u0010\u0011\u001a\u00020\t2\b\b\u0002\u0010\u0012\u001a\u00020\t2\b\b\u0002\u0010\u0013\u001a\u00020\t2\b\b\u0002\u0010\u0014\u001a\u00020\tJ\u0006\u0010\u0018\u001a\u00020\u0004J\u0006\u0010\u0019\u001a\u00020\u0004J\u0006\u0010\u001a\u001a\u00020\u0004J \u0010\u001b\u001a\u00020\u00042\u0006\u0010\b\u001a\u00020\t2\u0006\u0010\n\u001a\u00020\t2\b\b\u0002\u0010\u001c\u001a\u00020\rJ\u0012\u0010\u001d\u001a\u00020\u00042\n\b\u0002\u0010\u001e\u001a\u0004\u0018\u00010\u001fJ\u0012\u0010 \u001a\u00020\u00042\n\b\u0002\u0010\u001e\u001a\u0004\u0018\u00010\u001f¨\u0006!"}, d2 = {"Lcom/ayaneo/gamewindow/KeyInputInject;", "", "()V", "initInjectServer", "", "launcherApp", "Landroid/app/Application;", "injectKeyEvent", "keyCode", "", "scanCode", "action", "downTime", "", "eventTime", "repeatCount", "flags", "source", "displayId", "metaState", "deviceId", "mode", "newKeyEvent", "Landroid/view/KeyEvent;", "removeMainInputKeyEvent", "removeMappingUIInputKeyEvent", "resetInjectServer", "sendKeyEvent", "pressTime", "setMainInputKeyEvent", "onInputKeyEvent", "Lcom/input/event/OnKeyEventDispatcher;", "setMappingUIInputKeyEvent", "gamewindow_QCOMRelease"}, k = 1, mv = {1, 9, 0}, xi = 48)
public final class KeyInputInject {

    /* JADX INFO: renamed from: a, reason: collision with root package name */
    @NotNull
    public static final KeyInputInject f4868a = new KeyInputInject();

    public static void a(KeyInputInject keyInputInject, int i, int i2, int i3, long j, long j2, int i4) {
        int i5 = (i4 & 4) != 0 ? 0 : i3;
        long jCurrentTimeMillis = (i4 & 8) != 0 ? System.currentTimeMillis() : j;
        long j3 = (i4 & 16) != 0 ? jCurrentTimeMillis : j2;
        int i6 = (i4 & 64) != 0 ? 8 : 0;
        int i7 = (i4 & 128) != 0 ? 257 : 0;
        int i8 = (i4 & 256) != 0 ? -1 : 0;
        keyInputInject.getClass();
        try {
            Result.Companion companion = Result.INSTANCE;
            Result.m15constructorimpl(Boolean.valueOf(InputManager.getInstance().injectInputEvent(KeyEvent.obtain(jCurrentTimeMillis, j3, i5, i, 0, 0, 0, i2, i6, i7, i8, null), 0)));
        } catch (Throwable th) {
            Result.Companion companion2 = Result.INSTANCE;
            Result.m15constructorimpl(ResultKt.a(th));
        }
    }

    public static void b(@Nullable com.ayaneo.keymapping.a aVar) {
        AndroidInputReader.setOnKeyEventDispatcher(Boolean.FALSE, aVar);
    }
}
