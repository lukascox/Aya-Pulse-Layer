package com.ayaneo.provider;

import androidx.room.processor.a;
import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.Metadata;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

/* JADX INFO: compiled from: AyaSystemProvider.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000:\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\n\u0002\u0010\u000b\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010\b\n\u0002\u0018\u0002\n\u0002\b\u0005\bÆ\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002¢\u0006\u0002\u0010\u0002J\u0006\u0010\u000b\u001a\u00020\fJ;\u0010\r\u001a\u00020\f2\u0006\u0010\u000e\u001a\u00020\u000f2\b\b\u0002\u0010\u0010\u001a\u00020\u000f2!\u0010\u0011\u001a\u001d\u0012\u0013\u0012\u00110\u0013¢\u0006\f\b\u0014\u0012\b\b\u000e\u0012\u0004\b\b(\u0015\u0012\u0004\u0012\u00020\f0\u0012J\u0006\u0010\u0016\u001a\u00020\fJ\u000e\u0010\u0017\u001a\u00020\f2\u0006\u0010\u000e\u001a\u00020\u000fJ\u0018\u0010\u0018\u001a\u00020\f2\u0006\u0010\u000e\u001a\u00020\u000f2\b\b\u0002\u0010\u0010\u001a\u00020\u000fR\u001b\u0010\u0003\u001a\u00020\u00048BX\u0082\u0084\u0002¢\u0006\f\n\u0004\b\u0007\u0010\b\u001a\u0004\b\u0005\u0010\u0006R\u000e\u0010\t\u001a\u00020\nX\u0082\u000e¢\u0006\u0002\n\u0000¨\u0006\u0019"}, d2 = {"Lcom/ayaneo/provider/AyaShareProvider;", "Lcom/ayaneo/provider/SystemProvider;", "()V", "ayaContentObserver", "Lcom/ayaneo/provider/AyaContentObserver;", "getAyaContentObserver", "()Lcom/ayaneo/provider/AyaContentObserver;", "ayaContentObserver$delegate", "Lkotlin/Lazy;", "isInitRegister", "", "initRegister", "", "registerObserver", "name", "", "tag", "observer", "Lkotlin/Function1;", "", "Lkotlin/ParameterName;", "flags", "releaseRegister", "unregisterAllObserver", "unregisterObserver", "gamewindow_QCOMRelease"}, k = 1, mv = {1, 9, 0}, xi = 48)
public final class AyaShareProvider extends SystemProvider {

    /* JADX INFO: renamed from: c, reason: collision with root package name */
    @NotNull
    public static final AyaShareProvider f6263c = new AyaShareProvider();

    /* JADX INFO: renamed from: d, reason: collision with root package name */
    @NotNull
    public static final Lazy f6264d = LazyKt.b(new Function0<AyaContentObserver>() { // from class: com.ayaneo.provider.AyaShareProvider$ayaContentObserver$2
        /* JADX WARN: Can't rename method to resolve collision */
        @Override // kotlin.jvm.functions.Function0
        @NotNull
        public final AyaContentObserver invoke() {
            return new AyaContentObserver();
        }
    });
    public static volatile boolean e;

    public AyaShareProvider() {
        super("share");
    }

    public static void l(AyaShareProvider ayaShareProvider, String str, Function1 function1) {
        ayaShareProvider.getClass();
        Intrinsics.e("name", str);
        Intrinsics.e("tag", str);
        Intrinsics.e("observer", function1);
        if (!e) {
            e = true;
            SystemProvider.k().registerContentObserver(ayaShareProvider.f6270b, true, (AyaContentObserver) f6264d.getValue());
        }
        AyaContentObserver ayaContentObserver = (AyaContentObserver) f6264d.getValue();
        ayaContentObserver.getClass();
        ayaContentObserver.f6258a.add(new AyaContentObserver.AyaObserver(str, str, function1));
    }

    public static void m(AyaShareProvider ayaShareProvider, final String str) {
        ayaShareProvider.getClass();
        Intrinsics.e("name", str);
        Intrinsics.e("tag", str);
        if (e) {
            AyaContentObserver ayaContentObserver = (AyaContentObserver) f6264d.getValue();
            ayaContentObserver.getClass();
            ayaContentObserver.f6258a.removeIf(new a(1, new Function1<AyaContentObserver.AyaObserver, Boolean>() { // from class: com.ayaneo.provider.AyaContentObserver$removeObserver$1
                /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
                {
                    super(1);
                }

                /* JADX WARN: Code duplicated, block: B:7:0x001b  */
                @Override // kotlin.jvm.functions.Function1
                @NotNull
                public final Boolean invoke(@NotNull AyaContentObserver.AyaObserver ayaObserver) {
                    boolean z;
                    Intrinsics.e("it", ayaObserver);
                    if (Intrinsics.a(ayaObserver.f6259a, str)) {
                        if (Intrinsics.a(ayaObserver.f6260b, str)) {
                            z = true;
                        } else {
                            z = false;
                        }
                    } else {
                        z = false;
                    }
                    return Boolean.valueOf(z);
                }
            }));
        }
    }
}
