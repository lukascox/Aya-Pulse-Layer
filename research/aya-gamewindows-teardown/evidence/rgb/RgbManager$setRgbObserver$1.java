package com.ayaneo.gamewindow.utils.rgb;

import com.ayaneo.gamewindow.LauncherApp;
import com.ayaneo.gamewindow.observer.ShareTextObserver;
import java.lang.ref.WeakReference;
import kotlin.Metadata;
import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.intrinsics.CoroutineSingletons;
import kotlin.coroutines.jvm.internal.DebugMetadata;
import kotlin.coroutines.jvm.internal.SuspendLambda;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.internal.Intrinsics;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.DelayKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import timber.log.Timber;

/* JADX INFO: compiled from: RgbManager.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000\n\n\u0000\n\u0002\u0010\u0002\n\u0002\u0018\u0002\u0010\u0000\u001a\u00020\u0001*\u00020\u0002H\u008a@"}, d2 = {"<anonymous>", "", "Lkotlinx/coroutines/CoroutineScope;"}, k = 3, mv = {1, 9, 0}, xi = 48)
@DebugMetadata(c = "com.ayaneo.gamewindow.utils.rgb.RgbManager$setRgbObserver$1", f = "RgbManager.kt", l = {57}, m = "invokeSuspend")
public final class RgbManager$setRgbObserver$1 extends SuspendLambda implements Function2<CoroutineScope, Continuation<? super Unit>, Object> {
    int label;

    public RgbManager$setRgbObserver$1(Continuation<? super RgbManager$setRgbObserver$1> continuation) {
        super(2, continuation);
    }

    @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
    @NotNull
    public final Continuation<Unit> create(@Nullable Object obj, @NotNull Continuation<?> continuation) {
        return new RgbManager$setRgbObserver$1(continuation);
    }

    @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
    @Nullable
    public final Object invokeSuspend(@NotNull Object obj) {
        CoroutineSingletons coroutineSingletons = CoroutineSingletons.COROUTINE_SUSPENDED;
        int i = this.label;
        if (i == 0) {
            ResultKt.b(obj);
            this.label = 1;
            if (DelayKt.a(100L, this) == coroutineSingletons) {
                return coroutineSingletons;
            }
        } else {
            if (i != 1) {
                throw new IllegalStateException("call to 'resume' before 'invoke' with coroutine");
            }
            ResultKt.b(obj);
        }
        Timber.f11226a.f("setRgbObserver", new Object[0]);
        ShareTextObserver.Companion companion = ShareTextObserver.f5345c;
        AnonymousClass1 anonymousClass1 = new Function2<String, String, Unit>() { // from class: com.ayaneo.gamewindow.utils.rgb.RgbManager$setRgbObserver$1.1
            @Override // kotlin.jvm.functions.Function2
            /* JADX INFO: renamed from: invoke */
            public /* bridge */ /* synthetic */ Unit mo0invoke(String str, String str2) throws InterruptedException {
                invoke2(str, str2);
                return Unit.f8334a;
            }

            /* JADX WARN: Failed to restore switch over string. Please report as a decompilation issue */
            /* JADX INFO: renamed from: invoke, reason: avoid collision after fix types in other method */
            public final void invoke2(@NotNull String str, @NotNull String str2) throws InterruptedException {
                Intrinsics.e("path", str);
                Intrinsics.e("value", str2);
                switch (str.hashCode()) {
                    case -2044386041:
                        if (str.equals("aya_rgb_mode.conf")) {
                            RgbManager rgbManager = RgbManager.f6047a;
                            int i2 = Integer.parseInt(str2);
                            rgbManager.getClass();
                            RgbManager.f6048b = i2;
                            if (i2 == 0) {
                                Timber.f11226a.f("defaultMode 1", new Object[0]);
                                RgbUtil rgbUtil = RgbManager.p;
                                LauncherApp.e.getClass();
                                rgbUtil.f(LauncherApp.Companion.a());
                                break;
                            } else if (i2 == 1) {
                                Timber.f11226a.f("breathSingle 1", new Object[0]);
                                RgbUtil rgbUtil2 = RgbManager.p;
                                LauncherApp.e.getClass();
                                rgbUtil2.a(LauncherApp.Companion.a());
                                break;
                            } else if (i2 == 6) {
                                Timber.f11226a.f("singleColor 1", new Object[0]);
                                RgbUtil rgbUtil3 = RgbManager.p;
                                LauncherApp.e.getClass();
                                rgbUtil3.h(LauncherApp.Companion.a());
                                break;
                            } else if (i2 == 7) {
                                Timber.f11226a.f("follow 1", new Object[0]);
                                RgbUtil rgbUtil4 = RgbManager.p;
                                LauncherApp.e.getClass();
                                rgbUtil4.c(LauncherApp.Companion.a());
                                break;
                            }
                        }
                        break;
                    case -1787717589:
                        if (str.equals("aya_rgb_breath_single_mode_color.conf")) {
                            Timber.f11226a.f("breathSingle 2 colors = ".concat(str2), new Object[0]);
                            RgbManager.f6047a.getClass();
                            RgbManager.e = str2;
                            RgbUtil rgbUtil5 = RgbManager.p;
                            LauncherApp.e.getClass();
                            rgbUtil5.a(LauncherApp.Companion.a());
                            break;
                        }
                        break;
                    case -1775858820:
                        if (str.equals("aya_rgb_single_mode_color.conf")) {
                            Timber.f11226a.f("singleColor 2 colors = ".concat(str2), new Object[0]);
                            RgbManager.f6047a.getClass();
                            RgbManager.f6050d = str2;
                            RgbUtil rgbUtil6 = RgbManager.p;
                            LauncherApp.e.getClass();
                            rgbUtil6.h(LauncherApp.Companion.a());
                            break;
                        }
                        break;
                    case -1660098715:
                        if (str.equals("aya_rgb_default_mode_color.conf")) {
                            Timber.f11226a.f("defaultMode 2 colors = ".concat(str2), new Object[0]);
                            RgbManager.f6047a.getClass();
                            RgbManager.f6049c = str2;
                            RgbUtil rgbUtil7 = RgbManager.p;
                            LauncherApp.e.getClass();
                            rgbUtil7.f(LauncherApp.Companion.a());
                            break;
                        }
                        break;
                    case -1271558206:
                        if (str.equals("aya_rgb_default_mode_bright.conf")) {
                            Timber.f11226a.f("defaultMode 3", new Object[0]);
                            RgbManager.f6047a.getClass();
                            RgbManager.h = str2;
                            RgbUtil rgbUtil8 = RgbManager.p;
                            LauncherApp.e.getClass();
                            rgbUtil8.f(LauncherApp.Companion.a());
                            break;
                        }
                        break;
                    case -932776004:
                        if (str.equals("aya_rgb_breath_single_mode_bright.conf")) {
                            Timber.f11226a.f("breathSingle 3", new Object[0]);
                            RgbManager.f6047a.getClass();
                            RgbManager.j = str2;
                            RgbUtil rgbUtil9 = RgbManager.p;
                            LauncherApp.e.getClass();
                            rgbUtil9.a(LauncherApp.Companion.a());
                            break;
                        }
                        break;
                    case -565154165:
                        if (str.equals("aya_rgb_single_mode_bright.conf")) {
                            Timber.f11226a.f("singleColor 3", new Object[0]);
                            RgbManager.f6047a.getClass();
                            RgbManager.i = str2;
                            RgbUtil rgbUtil10 = RgbManager.p;
                            LauncherApp.e.getClass();
                            rgbUtil10.h(LauncherApp.Companion.a());
                            break;
                        }
                        break;
                    case -383363518:
                        if (str.equals("aya_rgb_follow_mode_bright.conf")) {
                            Timber.f11226a.f("follow bright 3", new Object[0]);
                            RgbManager.f6047a.getClass();
                            RgbManager.k = str2;
                            RgbUtil rgbUtil11 = RgbManager.p;
                            LauncherApp.e.getClass();
                            rgbUtil11.c(LauncherApp.Companion.a());
                            break;
                        }
                        break;
                    case 54059003:
                        if (str.equals("aya_rgb_is_open.conf") && !Boolean.parseBoolean(str2)) {
                            RgbManager.f6047a.getClass();
                            RgbManager.p.close();
                            Thread.sleep(100L);
                            SerialCoroutineManager.f6054a.getClass();
                            SerialCoroutineManager.b();
                        }
                        break;
                    case 150230225:
                        if (str.equals("aya_rgb_follow_mode_back_color.conf")) {
                            Timber.f11226a.f("follow back 3", new Object[0]);
                            RgbManager.f6047a.getClass();
                            RgbManager.g = str2;
                            RgbUtil rgbUtil12 = RgbManager.p;
                            LauncherApp.e.getClass();
                            rgbUtil12.c(LauncherApp.Companion.a());
                            break;
                        }
                        break;
                    case 662510651:
                        if (str.equals("aya_rgb_follow_mode_front_color.conf")) {
                            Timber.f11226a.f("follow front 3", new Object[0]);
                            RgbManager.f6047a.getClass();
                            RgbManager.f = str2;
                            RgbUtil rgbUtil13 = RgbManager.p;
                            LauncherApp.e.getClass();
                            rgbUtil13.c(LauncherApp.Companion.a());
                            break;
                        }
                        break;
                }
            }
        };
        companion.getClass();
        ShareTextObserver.g = anonymousClass1 != null;
        ShareTextObserver.e = new WeakReference<>(anonymousClass1);
        ShareTextObserver.Companion.a();
        return Unit.f8334a;
    }

    @Override // kotlin.jvm.functions.Function2
    @Nullable
    /* JADX INFO: renamed from: invoke, reason: avoid collision after fix types in other method and merged with bridge method [inline-methods] */
    public final Object mo0invoke(@NotNull CoroutineScope coroutineScope, @Nullable Continuation<? super Unit> continuation) {
        return ((RgbManager$setRgbObserver$1) create(coroutineScope, continuation)).invokeSuspend(Unit.f8334a);
    }
}
