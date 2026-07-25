package com.ayaneo.gamewindow.ui.window.controller.other;

import com.ayaneo.gamewindow.utils.newserial.other.NewControllerSerialManagerKt;
import kotlin.Metadata;
import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.intrinsics.CoroutineSingletons;
import kotlin.coroutines.jvm.internal.DebugMetadata;
import kotlin.coroutines.jvm.internal.SuspendLambda;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.internal.SourceDebugExtension;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.DelayKt;
import kotlinx.coroutines.flow.MutableStateFlow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/* JADX INFO: compiled from: ControllerViewModel.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000\n\n\u0000\n\u0002\u0010\u0002\n\u0002\u0018\u0002\u0010\u0000\u001a\u00020\u0001*\u00020\u0002H\u008a@"}, d2 = {"<anonymous>", "", "Lkotlinx/coroutines/CoroutineScope;"}, k = 3, mv = {1, 9, 0}, xi = 48)
@DebugMetadata(c = "com.ayaneo.gamewindow.ui.window.controller.other.OtherControllerViewModel$configJoystickDeadZone$1", f = "ControllerViewModel.kt", l = {296, 298}, m = "invokeSuspend")
@SourceDebugExtension
final class OtherControllerViewModel$configJoystickDeadZone$1 extends SuspendLambda implements Function2<CoroutineScope, Continuation<? super Unit>, Object> {
    final /* synthetic */ boolean $on;
    int label;
    final /* synthetic */ OtherControllerViewModel this$0;

    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    public OtherControllerViewModel$configJoystickDeadZone$1(OtherControllerViewModel otherControllerViewModel, boolean z, Continuation<? super OtherControllerViewModel$configJoystickDeadZone$1> continuation) {
        super(2, continuation);
        this.this$0 = otherControllerViewModel;
        this.$on = z;
    }

    @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
    @NotNull
    public final Continuation<Unit> create(@Nullable Object obj, @NotNull Continuation<?> continuation) {
        return new OtherControllerViewModel$configJoystickDeadZone$1(this.this$0, this.$on, continuation);
    }

    @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
    @Nullable
    public final Object invokeSuspend(@NotNull Object obj) {
        CoroutineSingletons coroutineSingletons = CoroutineSingletons.COROUTINE_SUSPENDED;
        int i = this.label;
        if (i != 0) {
            if (i == 1) {
                ResultKt.b(obj);
            } else {
                if (i != 2) {
                    throw new IllegalStateException("call to 'resume' before 'invoke' with coroutine");
                }
                ResultKt.b(obj);
            }
            return Unit.f8334a;
        }
        ResultKt.b(obj);
        MutableStateFlow<OtherControllerViewModel.UiState> mutableStateFlow = this.this$0.f5717a;
        boolean z = this.$on;
        while (true) {
            OtherControllerViewModel.UiState value = mutableStateFlow.getValue();
            boolean z2 = z;
            if (mutableStateFlow.e(value, OtherControllerViewModel.UiState.a(value, null, null, null, null, z, null, false, false, null, null, null, null, null, 0, 0, 0, 0, 0, 0, null, null, null, 8388591))) {
                break;
            }
            z = z2;
        }
        this.label = 1;
        if (DelayKt.a(100L, this) == coroutineSingletons) {
            return coroutineSingletons;
        }
        if (OtherControllerViewModel.a(this.this$0)) {
            return Unit.f8334a;
        }
        com.ayaneo.gamewindow.utils.newserial.other.OtherControllerSerialManager otherControllerSerialManager = this.this$0.f5720d;
        boolean z3 = this.$on;
        this.label = 2;
        otherControllerSerialManager.f6017a = NewControllerSerialManagerKt.b(otherControllerSerialManager.f6017a, 6, z3 ? '0' : '1');
        Object objC = otherControllerSerialManager.c(this);
        if (objC != CoroutineSingletons.COROUTINE_SUSPENDED) {
            objC = Unit.f8334a;
        }
        if (objC == coroutineSingletons) {
            return coroutineSingletons;
        }
        return Unit.f8334a;
    }

    @Override // kotlin.jvm.functions.Function2
    @Nullable
    /* JADX INFO: renamed from: invoke, reason: avoid collision after fix types in other method and merged with bridge method [inline-methods] */
    public final Object mo0invoke(@NotNull CoroutineScope coroutineScope, @Nullable Continuation<? super Unit> continuation) {
        return ((OtherControllerViewModel$configJoystickDeadZone$1) create(coroutineScope, continuation)).invokeSuspend(Unit.f8334a);
    }
}
