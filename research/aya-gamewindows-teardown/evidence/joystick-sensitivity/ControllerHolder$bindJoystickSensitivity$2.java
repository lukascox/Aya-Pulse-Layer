package com.ayaneo.gamewindow.ui.window.controller.other;

import com.ayaneo.gamewindow.databinding.LayoutHandleOtherBinding;
import kotlin.Metadata;
import kotlin.ResultKt;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.intrinsics.CoroutineSingletons;
import kotlin.coroutines.jvm.internal.DebugMetadata;
import kotlin.coroutines.jvm.internal.SuspendLambda;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.FlowKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import timber.log.Timber;

/* JADX INFO: compiled from: ControllerHolder.kt */
/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000\n\n\u0000\n\u0002\u0010\u0002\n\u0002\u0018\u0002\u0010\u0000\u001a\u00020\u0001*\u00020\u0002H\u008a@"}, d2 = {"<anonymous>", "", "Lkotlinx/coroutines/CoroutineScope;"}, k = 3, mv = {1, 9, 0}, xi = 48)
@DebugMetadata(c = "com.ayaneo.gamewindow.ui.window.controller.other.ControllerHolder$bindJoystickSensitivity$2", f = "ControllerHolder.kt", l = {351}, m = "invokeSuspend")
final class ControllerHolder$bindJoystickSensitivity$2 extends SuspendLambda implements Function2<CoroutineScope, Continuation<? super Unit>, Object> {
    final /* synthetic */ Flow<OtherControllerViewModel.JoystickSensitivity> $flow;
    final /* synthetic */ LayoutHandleOtherBinding $this_bindJoystickSensitivity;
    int label;

    /* JADX INFO: renamed from: com.ayaneo.gamewindow.ui.window.controller.other.ControllerHolder$bindJoystickSensitivity$2$1, reason: invalid class name */
    /* JADX INFO: compiled from: ControllerHolder.kt */
    @Metadata(d1 = {"\u0000\f\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\u0010\u0000\u001a\u00020\u00012\u0006\u0010\u0002\u001a\u00020\u0003H\u008a@"}, d2 = {"<anonymous>", "", "it", "Lcom/ayaneo/gamewindow/ui/window/controller/other/OtherControllerViewModel$JoystickSensitivity;"}, k = 3, mv = {1, 9, 0}, xi = 48)
    @DebugMetadata(c = "com.ayaneo.gamewindow.ui.window.controller.other.ControllerHolder$bindJoystickSensitivity$2$1", f = "ControllerHolder.kt", l = {}, m = "invokeSuspend")
    public static final class AnonymousClass1 extends SuspendLambda implements Function2<OtherControllerViewModel.JoystickSensitivity, Continuation<? super Unit>, Object> {
        final /* synthetic */ LayoutHandleOtherBinding $this_bindJoystickSensitivity;
        /* synthetic */ Object L$0;
        int label;

        /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
        public AnonymousClass1(LayoutHandleOtherBinding layoutHandleOtherBinding, Continuation<? super AnonymousClass1> continuation) {
            super(2, continuation);
            this.$this_bindJoystickSensitivity = layoutHandleOtherBinding;
        }

        @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
        @NotNull
        public final Continuation<Unit> create(@Nullable Object obj, @NotNull Continuation<?> continuation) {
            AnonymousClass1 anonymousClass1 = new AnonymousClass1(this.$this_bindJoystickSensitivity, continuation);
            anonymousClass1.L$0 = obj;
            return anonymousClass1;
        }

        @Override // kotlin.jvm.functions.Function2
        @Nullable
        /* JADX INFO: renamed from: invoke, reason: avoid collision after fix types in other method and merged with bridge method [inline-methods] */
        public final Object mo0invoke(@NotNull OtherControllerViewModel.JoystickSensitivity joystickSensitivity, @Nullable Continuation<? super Unit> continuation) {
            return ((AnonymousClass1) create(joystickSensitivity, continuation)).invokeSuspend(Unit.f8334a);
        }

        @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
        @Nullable
        public final Object invokeSuspend(@NotNull Object obj) {
            String str;
            CoroutineSingletons coroutineSingletons = CoroutineSingletons.COROUTINE_SUSPENDED;
            if (this.label != 0) {
                throw new IllegalStateException("call to 'resume' before 'invoke' with coroutine");
            }
            ResultKt.b(obj);
            OtherControllerViewModel.JoystickSensitivity joystickSensitivity = (OtherControllerViewModel.JoystickSensitivity) this.L$0;
            Timber.f11226a.a("bindJoystickSensitivity ", new Object[0]);
            int i = joystickSensitivity.f5728a;
            String str2 = "";
            if (i == 0) {
                str = "50%";
            } else if (i != 1) {
                str = i != 2 ? "" : "150%";
            } else {
                str = "100%";
            }
            String strConcat = "L : ".concat(str);
            int i2 = joystickSensitivity.f5729b;
            if (i2 == 0) {
                str2 = "50%";
            } else if (i2 == 1) {
                str2 = "100%";
            } else if (i2 == 2) {
                str2 = "150%";
            }
            String strConcat2 = " R : ".concat(str2);
            this.$this_bindJoystickSensitivity.r.setSummary(strConcat + " " + strConcat2);
            return Unit.f8334a;
        }
    }

    /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
    public ControllerHolder$bindJoystickSensitivity$2(Flow<OtherControllerViewModel.JoystickSensitivity> flow, LayoutHandleOtherBinding layoutHandleOtherBinding, Continuation<? super ControllerHolder$bindJoystickSensitivity$2> continuation) {
        super(2, continuation);
        this.$flow = flow;
        this.$this_bindJoystickSensitivity = layoutHandleOtherBinding;
    }

    @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
    @NotNull
    public final Continuation<Unit> create(@Nullable Object obj, @NotNull Continuation<?> continuation) {
        return new ControllerHolder$bindJoystickSensitivity$2(this.$flow, this.$this_bindJoystickSensitivity, continuation);
    }

    @Override // kotlin.coroutines.jvm.internal.BaseContinuationImpl
    @Nullable
    public final Object invokeSuspend(@NotNull Object obj) {
        CoroutineSingletons coroutineSingletons = CoroutineSingletons.COROUTINE_SUSPENDED;
        int i = this.label;
        if (i == 0) {
            ResultKt.b(obj);
            Flow<OtherControllerViewModel.JoystickSensitivity> flow = this.$flow;
            AnonymousClass1 anonymousClass1 = new AnonymousClass1(this.$this_bindJoystickSensitivity, null);
            this.label = 1;
            if (FlowKt.c(flow, anonymousClass1, this) == coroutineSingletons) {
                return coroutineSingletons;
            }
        } else {
            if (i != 1) {
                throw new IllegalStateException("call to 'resume' before 'invoke' with coroutine");
            }
            ResultKt.b(obj);
        }
        return Unit.f8334a;
    }

    @Override // kotlin.jvm.functions.Function2
    @Nullable
    /* JADX INFO: renamed from: invoke, reason: avoid collision after fix types in other method and merged with bridge method [inline-methods] */
    public final Object mo0invoke(@NotNull CoroutineScope coroutineScope, @Nullable Continuation<? super Unit> continuation) {
        return ((ControllerHolder$bindJoystickSensitivity$2) create(coroutineScope, continuation)).invokeSuspend(Unit.f8334a);
    }
}
