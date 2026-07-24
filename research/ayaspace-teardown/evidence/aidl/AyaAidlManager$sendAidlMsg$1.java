package com.ayaneo.settings.utils.aidl;

import android.os.DeadObjectException;
import android.os.RemoteException;
import com.ayaneo.gamewindow.AyaAidlInterface;
import kotlin.Metadata;
import kotlin.Pair;
import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.intrinsics.CoroutineSingletons;
import kotlin.coroutines.jvm.internal.DebugMetadata;
import kotlin.coroutines.jvm.internal.SuspendLambda;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.CoroutineScope;
import timber.log.Timber;

/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000\n\n\u0000\n\u0002\u0010\u0002\n\u0002\u0018\u0002\u0010\u0000\u001a\u00020\u0001*\u00020\u0002H\n"}, d2 = {"<anonymous>", "", "Lkotlinx/coroutines/CoroutineScope;"}, k = 3, mv = {2, 0, 0}, xi = 48)
@DebugMetadata(c = "com.ayaneo.settings.utils.aidl.AyaAidlManager$sendAidlMsg$1", f = "AyaAidlManager.kt", i = {}, l = {}, m = "invokeSuspend", n = {}, s = {})
public final class AyaAidlManager$sendAidlMsg$1 extends SuspendLambda implements Function2<CoroutineScope, Continuation<? super Unit>, Object> {
    final /* synthetic */ String $message;
    final /* synthetic */ String $type;
    int label;

    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    public AyaAidlManager$sendAidlMsg$1(String str, String str2, Continuation<? super AyaAidlManager$sendAidlMsg$1> continuation) {
        super(2, continuation);
        this.$type = str;
        this.$message = str2;
    }

    @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
    public final Continuation<Unit> create(Object obj, Continuation<?> continuation) {
        return new AyaAidlManager$sendAidlMsg$1(this.$type, this.$message, continuation);
    }

    @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
    public final Object invokeSuspend(Object obj) {
        CoroutineSingletons coroutineSingletons = CoroutineSingletons.COROUTINE_SUSPENDED;
        if (this.label != 0) {
            throw new IllegalStateException("call to 'resume' before 'invoke' with coroutine");
        }
        ResultKt.n(obj);
        if (!AyaAidlManager.mBound || AyaAidlManager.mService == null) {
            AyaAidlManager.messageQueue.add(new Pair<>(this.$type, this.$message));
            AyaAidlManager.f17275a.h();
        } else {
            try {
                String str = AyaAidlManager.clientId + ":" + this.$type + ":" + this.$message;
                AyaAidlInterface ayaAidlInterface = AyaAidlManager.mService;
                if (ayaAidlInterface != null) {
                    ayaAidlInterface.send(str);
                    Unit unit = Unit.f21153a;
                }
            } catch (DeadObjectException unused) {
                Timber.INSTANCE.k("aidl window 被卸载", new Object[0]);
                AyaAidlManager.messageQueue.add(new Pair<>(this.$type, this.$message));
                AyaAidlManager.f17275a.h();
                Unit unit2 = Unit.f21153a;
            } catch (RemoteException e2) {
                e2.printStackTrace();
                Unit unit3 = Unit.f21153a;
            }
        }
        return Unit.f21153a;
    }

    @Override // kotlin.jvm.functions.Function2
    public final Object invoke(CoroutineScope coroutineScope, Continuation<? super Unit> continuation) {
        return ((AyaAidlManager$sendAidlMsg$1) create(coroutineScope, continuation)).invokeSuspend(Unit.f21153a);
    }
}
