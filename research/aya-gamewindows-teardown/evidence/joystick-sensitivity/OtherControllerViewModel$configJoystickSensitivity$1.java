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
import kotlin.text.CharsKt;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.DelayKt;
import kotlinx.coroutines.flow.MutableStateFlow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/* JADX INFO: compiled from: ControllerViewModel.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000\n\n\u0000\n\u0002\u0010\u0002\n\u0002\u0018\u0002\u0010\u0000\u001a\u00020\u0001*\u00020\u0002H\u008a@"}, d2 = {"<anonymous>", "", "Lkotlinx/coroutines/CoroutineScope;"}, k = 3, mv = {1, 9, 0}, xi = 48)
@DebugMetadata(c = "com.ayaneo.gamewindow.ui.window.controller.other.OtherControllerViewModel$configJoystickSensitivity$1", f = "ControllerViewModel.kt", l = {346, 349}, m = "invokeSuspend")
@SourceDebugExtension
final class OtherControllerViewModel$configJoystickSensitivity$1 extends SuspendLambda implements Function2<CoroutineScope, Continuation<? super Unit>, Object> {
    final /* synthetic */ int $level;
    final /* synthetic */ int $which;
    int label;
    final /* synthetic */ OtherControllerViewModel this$0;

    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    public OtherControllerViewModel$configJoystickSensitivity$1(OtherControllerViewModel otherControllerViewModel, int i, int i2, Continuation<? super OtherControllerViewModel$configJoystickSensitivity$1> continuation) {
        super(2, continuation);
        this.this$0 = otherControllerViewModel;
        this.$level = i;
        this.$which = i2;
    }

    @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
    @NotNull
    public final Continuation<Unit> create(@Nullable Object obj, @NotNull Continuation<?> continuation) {
        return new OtherControllerViewModel$configJoystickSensitivity$1(this.this$0, this.$level, this.$which, continuation);
    }

    @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
    @Nullable
    public final Object invokeSuspend(@NotNull Object obj) {
        OtherControllerViewModel.UiState value;
        OtherControllerViewModel.UiState uiStateA;
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
        int i2 = this.$which;
        int i3 = this.$level;
        do {
            value = mutableStateFlow.getValue();
            OtherControllerViewModel.UiState uiState = value;
            if (i2 == 0) {
                OtherControllerViewModel.JoystickSensitivity joystickSensitivity = uiState.f5765d;
                int i4 = joystickSensitivity.f5729b;
                joystickSensitivity.getClass();
                uiStateA = OtherControllerViewModel.UiState.a(uiState, null, null, null, new OtherControllerViewModel.JoystickSensitivity(i3, i4), false, null, false, false, null, null, null, null, null, 0, 0, 0, 0, 0, 0, null, null, null, 8388599);
            } else if (i2 != 1) {
                uiState.f5765d.getClass();
                uiStateA = OtherControllerViewModel.UiState.a(uiState, null, null, null, new OtherControllerViewModel.JoystickSensitivity(i3, i3), false, null, false, false, null, null, null, null, null, 0, 0, 0, 0, 0, 0, null, null, null, 8388599);
            } else {
                OtherControllerViewModel.JoystickSensitivity joystickSensitivity2 = uiState.f5765d;
                int i5 = joystickSensitivity2.f5728a;
                joystickSensitivity2.getClass();
                uiStateA = OtherControllerViewModel.UiState.a(uiState, null, null, null, new OtherControllerViewModel.JoystickSensitivity(i5, i3), false, null, false, false, null, null, null, null, null, 0, 0, 0, 0, 0, 0, null, null, null, 8388599);
            }
        } while (!mutableStateFlow.e(value, uiStateA));
        this.label = 1;
        if (DelayKt.a(100L, this) == coroutineSingletons) {
            return coroutineSingletons;
        }
        if (OtherControllerViewModel.a(this.this$0)) {
            return Unit.f8334a;
        }
        com.ayaneo.gamewindow.utils.newserial.other.OtherControllerSerialManager otherControllerSerialManager = this.this$0.f5720d;
        int i6 = this.$level + 1;
        int i7 = this.$which;
        this.label = 2;
        if (i7 == 0) {
            otherControllerSerialManager.f6017a = NewControllerSerialManagerKt.b(otherControllerSerialManager.f6017a, 4, CharsKt.c(i6));
        } else if (i7 == 1) {
            otherControllerSerialManager.f6017a = NewControllerSerialManagerKt.b(otherControllerSerialManager.f6017a, 5, CharsKt.c(i6));
        } else if (i7 == 2) {
            otherControllerSerialManager.f6017a = NewControllerSerialManagerKt.b(NewControllerSerialManagerKt.b(otherControllerSerialManager.f6017a, 4, CharsKt.c(i6)), 5, CharsKt.c(i6));
        }
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
        return ((OtherControllerViewModel$configJoystickSensitivity$1) create(coroutineScope, continuation)).invokeSuspend(Unit.f8334a);
    }
}
