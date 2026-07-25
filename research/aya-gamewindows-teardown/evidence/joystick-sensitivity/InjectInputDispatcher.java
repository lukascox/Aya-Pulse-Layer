package com.input.source;

import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.view.MotionEvent;
import com.android.internal.annotations.Keep;
import com.input.inject.InjectPointer;
import com.input.inject.InjectPointerArray;

/* JADX INFO: loaded from: classes2.dex */
@Keep
public class InjectInputDispatcher {
    private static final int INJECT_RESULT1 = 1;
    private static final int INJECT_RESULT2 = 0;
    private static final int deviceId = 7;
    private static final int display = 0;
    private static long downloadTime = 0;
    private static final int flag = 64;
    private static InjectPointerArray pointerArray = new InjectPointerArray();
    private static final int source = 4098;

    public static native void addInjectParams(int i, int i2, float f, float f2, int i3, int i4, float f3, int i5, boolean z, float f4, float f5, boolean z2, boolean z3);

    public static void cancelAllInject() {
        stopInjectTouch();
        clearInjectParams();
        injectCancelEvent();
    }

    public static native void clearInjectParams();

    private static int getActionDown(int i) {
        return (i << 8) | 5;
    }

    private static int getActionUp(int i) {
        return (i << 8) | 6;
    }

    public static void injectCancelEvent() {
        try {
            if (pointerArray.c()) {
                if (!InputManager.getInstance().injectInputEvent(obtainEvent(3), 1)) {
                    InputManager.getInstance().cancelCurrentTouch();
                }
            }
        } catch (Throwable unused) {
        }
        pointerArray.a();
        downloadTime = 0L;
    }

    private static void injectTouchEvent(MotionEvent motionEvent) {
        try {
            try {
                try {
                    if (!InputManager.getInstance().injectInputEvent(motionEvent, 1)) {
                        injectCancelEvent();
                    }
                    motionEvent.recycle();
                } catch (Throwable unused) {
                    injectCancelEvent();
                    motionEvent.recycle();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Throwable th) {
            try {
                motionEvent.recycle();
            } catch (Exception e2) {
                e2.printStackTrace();
            }
            throw th;
        }
    }

    public static void injectTouchMoveEvent(int i, float f, float f2) {
        InjectPointer injectPointer;
        try {
            InjectPointerArray injectPointerArray = pointerArray;
            int actionDown = 0;
            int i2 = 0;
            while (true) {
                InjectPointer[] injectPointerArr = injectPointerArray.f7864b;
                if (i2 >= injectPointerArr.length) {
                    injectPointer = null;
                    break;
                }
                injectPointer = injectPointerArr[i2];
                if (injectPointer != null && injectPointer.f7860a == i) {
                    injectPointer.f7861b.id = i2;
                    break;
                }
                i2++;
            }
            if (injectPointer != null) {
                MotionEvent.PointerCoords pointerCoords = injectPointer.f7862c;
                pointerCoords.x = f;
                pointerCoords.y = f2;
                injectTouchEvent(obtainMoveEvent());
                return;
            }
            InjectPointer injectPointerB = pointerArray.b(i);
            if (injectPointerB == null) {
                return;
            }
            InjectPointerArray injectPointerArray2 = pointerArray;
            if (injectPointerArray2.f7863a == 1) {
                downloadTime = SystemClock.uptimeMillis();
            } else {
                int i3 = 0;
                while (true) {
                    InjectPointer[] injectPointerArr2 = injectPointerArray2.f7864b;
                    if (actionDown >= injectPointerArr2.length) {
                        break;
                    }
                    InjectPointer injectPointer2 = injectPointerArr2[actionDown];
                    if (injectPointer2 != null) {
                        if (injectPointer2.f7860a == i) {
                            break;
                        } else {
                            i3++;
                        }
                    }
                    actionDown++;
                }
                actionDown = getActionDown(i3);
            }
            MotionEvent.PointerCoords pointerCoords2 = injectPointerB.f7862c;
            pointerCoords2.x = f;
            pointerCoords2.y = f2;
            injectTouchEvent(obtainEvent(actionDown));
        } catch (Throwable unused) {
        }
    }

    public static void injectTouchUpEvent(int i) {
        InjectPointer injectPointer;
        try {
            if (!pointerArray.c()) {
                return;
            }
            InjectPointerArray injectPointerArray = pointerArray;
            int i2 = 0;
            int i3 = 0;
            while (true) {
                InjectPointer[] injectPointerArr = injectPointerArray.f7864b;
                if (i3 >= injectPointerArr.length) {
                    injectPointer = null;
                    break;
                }
                injectPointer = injectPointerArr[i3];
                if (injectPointer != null && injectPointer.f7860a == i) {
                    injectPointer.f7861b.id = i3;
                    break;
                }
                i3++;
            }
            if (injectPointer == null) {
                return;
            }
            InjectPointerArray injectPointerArray2 = pointerArray;
            int i4 = 0;
            int i5 = 0;
            while (true) {
                InjectPointer[] injectPointerArr2 = injectPointerArray2.f7864b;
                if (i4 >= injectPointerArr2.length) {
                    break;
                }
                InjectPointer injectPointer2 = injectPointerArr2[i4];
                if (injectPointer2 != null) {
                    if (injectPointer2.f7860a == i) {
                        break;
                    } else {
                        i5++;
                    }
                }
                i4++;
            }
            int actionUp = 1;
            if (pointerArray.f7863a != 1) {
                actionUp = getActionUp(i5);
            }
            injectTouchEvent(obtainEvent(actionUp));
            InjectPointerArray injectPointerArray3 = pointerArray;
            while (true) {
                InjectPointer[] injectPointerArr3 = injectPointerArray3.f7864b;
                if (i2 >= injectPointerArr3.length) {
                    return;
                }
                InjectPointer injectPointer3 = injectPointerArr3[i2];
                if (injectPointer3 != null && injectPointer3.f7860a == i) {
                    injectPointerArr3[i2] = null;
                    injectPointerArray3.f7863a--;
                    injectPointerArray3.f7865c.a(injectPointer3);
                    return;
                }
                i2++;
            }
        } catch (Throwable unused) {
        }
    }

    private static MotionEvent newEvent(int i, MotionEvent.PointerProperties[] pointerPropertiesArr, MotionEvent.PointerCoords[] pointerCoordsArr) {
        return MotionEvent.obtain(downloadTime, SystemClock.uptimeMillis(), i, pointerPropertiesArr.length, pointerPropertiesArr, pointerCoordsArr, 0, 0, 1.0f, 1.0f, 7, 0, source, 0, 64);
    }

    private static MotionEvent obtainEvent(int i) {
        return newEvent(i, pointerArray.e(), pointerArray.d());
    }

    private static MotionEvent obtainMoveEvent() {
        return newEvent(2, pointerArray.e(), pointerArray.d());
    }

    public static native void startInjectTouch();

    public static native void stopInjectTouch();
}
