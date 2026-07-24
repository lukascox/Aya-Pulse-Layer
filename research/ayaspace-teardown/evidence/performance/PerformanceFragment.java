package com.ayaneo.settings.ui.performance;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.ViewGroupKt;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentViewModelLazyKt;
import androidx.p001lifecycle.HasDefaultViewModelProviderFactory;
import androidx.p001lifecycle.LifecycleOwner;
import androidx.p001lifecycle.LifecycleOwnerKt;
import androidx.p001lifecycle.ViewModelProvider;
import androidx.p001lifecycle.ViewModelStore;
import androidx.p001lifecycle.ViewModelStoreOwner;
import androidx.p001lifecycle.viewmodel.CreationExtras;
import androidx.recyclerview.widget.RecyclerView;
import com.ayaneo.AyaDevicesUtilKt;
import com.ayaneo.devices.AyaDevicesKt;
import com.ayaneo.gamewindow.AyaAidlCallback;
import com.ayaneo.settings.R;
import com.ayaneo.settings.base.BaseFragment;
import com.ayaneo.settings.customview.ButtonIndicator;
import com.ayaneo.settings.customview.ButtonIndicatorKt;
import com.ayaneo.settings.customview.ChildKeyEventListener;
import com.ayaneo.settings.customview.Key;
import com.ayaneo.settings.databinding.FragPerformanceBinding;
import com.ayaneo.settings.ui.global.MainActivity;
import com.ayaneo.settings.utils.AppSound;
import com.ayaneo.settings.utils.AppUiAdapter;
import com.ayaneo.settings.utils.AyaShareConfUtilKt;
import com.ayaneo.settings.utils.Raw;
import com.ayaneo.settings.utils.UtilKt;
import com.ayaneo.settings.utils.aidl.AidlConstants;
import com.ayaneo.settings.utils.aidl.AyaAidlManager;
import com.ayaneo.settings.utils.aidl.MessagePasserKt;
import com.google.gson.Gson;
import java.util.List;
import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.LazyThreadSafetyMode;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function3;
import kotlin.jvm.internal.FunctionReferenceImpl;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.Reflection;
import kotlin.jvm.internal.SourceDebugExtension;
import kotlinx.coroutines.BuildersKt__Builders_commonKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000[\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\f\n\u0002\u0010\u000e\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0006\n\u0002\u0018\u0002\n\u0002\b\b\n\u0002\b\u0005*\u00016\u0018\u00002\b\u0012\u0004\u0012\u00020\u00020\u0001B\u0007¢\u0006\u0004\b\u0003\u0010\u0004J\u000f\u0010\u0006\u001a\u00020\u0005H\u0002¢\u0006\u0004\b\u0006\u0010\u0004J\u0017\u0010\t\u001a\u00020\u00052\u0006\u0010\b\u001a\u00020\u0007H\u0002¢\u0006\u0004\b\t\u0010\nJ!\u0010\u000f\u001a\u00020\u00052\u0006\u0010\f\u001a\u00020\u000b2\b\u0010\u000e\u001a\u0004\u0018\u00010\rH\u0016¢\u0006\u0004\b\u000f\u0010\u0010J\u0015\u0010\u0013\u001a\u00020\u00052\u0006\u0010\u0012\u001a\u00020\u0011¢\u0006\u0004\b\u0013\u0010\u0014J\u001d\u0010\u0018\u001a\u00020\u00052\u0006\u0010\u0016\u001a\u00020\u00152\u0006\u0010\u0017\u001a\u00020\u0015¢\u0006\u0004\b\u0018\u0010\u0019J\u0015\u0010\u001b\u001a\u00020\u00052\u0006\u0010\u001a\u001a\u00020\u0015¢\u0006\u0004\b\u001b\u0010\u001cJ\u000f\u0010\u001d\u001a\u00020\u0005H\u0016¢\u0006\u0004\b\u001d\u0010\u0004J\u000f\u0010\u001e\u001a\u00020\u0005H\u0016¢\u0006\u0004\b\u001e\u0010\u0004J\r\u0010\u001f\u001a\u00020\u0005¢\u0006\u0004\b\u001f\u0010\u0004J\r\u0010 \u001a\u00020\u0005¢\u0006\u0004\b \u0010\u0004J\u001f\u0010$\u001a\u00020\u00052\u0006\u0010!\u001a\u00020\u00152\u0006\u0010#\u001a\u00020\"H\u0002¢\u0006\u0004\b$\u0010%J\u0017\u0010(\u001a\u00020\u00052\u0006\u0010'\u001a\u00020&H\u0002¢\u0006\u0004\b(\u0010)J\u0017\u0010+\u001a\u00020\u00052\u0006\u0010*\u001a\u00020\u0015H\u0002¢\u0006\u0004\b+\u0010\u001cJ\u0017\u0010,\u001a\u00020\u00052\u0006\u0010*\u001a\u00020\u0015H\u0002¢\u0006\u0004\b,\u0010\u001cR\u001b\u00102\u001a\u00020-8BX\u0082\u0084\u0002¢\u0006\f\n\u0004\b.\u0010/\u001a\u0004\b0\u00101R\u0016\u00105\u001a\u00020\u00158\u0002@\u0002X\u0082\u000e¢\u0006\u0006\n\u0004\b3\u00104R\u0014\u00109\u001a\u0002068\u0002X\u0082\u0004¢\u0006\u0006\n\u0004\b7\u00108¨\u0006:"}, d2 = {"Lcom/ayaneo/settings/ui/performance/PerformanceFragment;", "Lcom/ayaneo/settings/base/BaseFragment;", "Lcom/ayaneo/settings/databinding/FragPerformanceBinding;", "<init>", "()V", "", "k3", "Lcom/ayaneo/settings/customview/ButtonIndicator;", "buttonKey", "q3", "(Lcom/ayaneo/settings/customview/ButtonIndicator;)V", "Landroid/view/View;", "view", "Landroid/os/Bundle;", "savedInstanceState", "q1", "(Landroid/view/View;Landroid/os/Bundle;)V", "", "isShow", "z3", "(Z)V", "", "cpuId", "value", "A3", "(II)V", "id", "w3", "(I)V", "o1", "p1", "x3", "y3", "currentMode", "", "configDataJson", "B3", "(ILjava/lang/String;)V", "Landroidx/fragment/app/Fragment;", "fragment", "v3", "(Landroidx/fragment/app/Fragment;)V", "mode", "u3", "t3", "Lcom/ayaneo/settings/ui/performance/PerformanceViewModel;", "z0", "Lkotlin/Lazy;", "j3", "()Lcom/ayaneo/settings/ui/performance/PerformanceViewModel;", "viewModel", "A0", "I", "curMode", "com/ayaneo/settings/ui/performance/PerformanceFragment$aidlCallBack$1", "B0", "Lcom/ayaneo/settings/ui/performance/PerformanceFragment$aidlCallBack$1;", "aidlCallBack", "app_QCOMRelease"}, k = 1, mv = {2, 0, 0})
@SourceDebugExtension({"SMAP\nPerformanceFragment.kt\nKotlin\n*S Kotlin\n*F\n+ 1 PerformanceFragment.kt\ncom/ayaneo/settings/ui/performance/PerformanceFragment\n+ 2 FragmentViewModelLazy.kt\nandroidx/fragment/app/FragmentViewModelLazyKt\n+ 3 View.kt\nandroidx/core/view/ViewKt\n+ 4 _Sequences.kt\nkotlin/sequences/SequencesKt___SequencesKt\n+ 5 _Collections.kt\nkotlin/collections/CollectionsKt___CollectionsKt\n+ 6 fake.kt\nkotlin/jvm/internal/FakeKt\n*L\n1#1,242:1\n106#2,15:243\n262#3,2:258\n262#3,2:262\n1317#4,2:260\n1863#5,2:264\n1#6:266\n*S KotlinDebug\n*F\n+ 1 PerformanceFragment.kt\ncom/ayaneo/settings/ui/performance/PerformanceFragment\n*L\n59#1:243,15\n73#1:258,2\n130#1:262,2\n83#1:260,2\n192#1:264,2\n*E\n"})
public final class PerformanceFragment extends BaseFragment<FragPerformanceBinding> {

    /* JADX INFO: renamed from: A0, reason: from kotlin metadata */
    public int curMode;

    /* JADX INFO: renamed from: B0, reason: from kotlin metadata */
    @NotNull
    public final PerformanceFragment$aidlCallBack$1 aidlCallBack;

    /* JADX INFO: renamed from: z0, reason: from kotlin metadata */
    @NotNull
    public final Lazy viewModel;

    /* JADX INFO: renamed from: com.ayaneo.settings.ui.performance.PerformanceFragment$1, reason: invalid class name */
    @Metadata(k = 3, mv = {2, 0, 0}, xi = 48)
    public /* synthetic */ class AnonymousClass1 extends FunctionReferenceImpl implements Function3<LayoutInflater, ViewGroup, Boolean, FragPerformanceBinding> {
        public static final AnonymousClass1 INSTANCE = new AnonymousClass1();

        public AnonymousClass1() {
            super(3, FragPerformanceBinding.class, "inflate", "inflate(Landroid/view/LayoutInflater;Landroid/view/ViewGroup;Z)Lcom/ayaneo/settings/databinding/FragPerformanceBinding;", 0);
        }

        public final FragPerformanceBinding invoke(LayoutInflater p0, ViewGroup viewGroup, boolean z) {
            Intrinsics.p(p0, "p0");
            return FragPerformanceBinding.e(p0, viewGroup, z);
        }

        @Override // kotlin.jvm.functions.Function3
        public /* bridge */ /* synthetic */ FragPerformanceBinding invoke(LayoutInflater layoutInflater, ViewGroup viewGroup, Boolean bool) {
            return invoke(layoutInflater, viewGroup, bool.booleanValue());
        }
    }

    /* JADX WARN: Type inference failed for: r0v5, types: [com.ayaneo.settings.ui.performance.PerformanceFragment$aidlCallBack$1] */
    public PerformanceFragment() {
        super(AnonymousClass1.INSTANCE);
        final Function0<Fragment> function0 = new Function0<Fragment>() { // from class: com.ayaneo.settings.ui.performance.PerformanceFragment$special$$inlined$viewModels$default$1
            {
                super(0);
            }

            /* JADX WARN: Can't rename method to resolve collision */
            @Override // kotlin.jvm.functions.Function0
            @NotNull
            public final Fragment invoke() {
                return this;
            }
        };
        final Lazy lazyB = LazyKt.b(LazyThreadSafetyMode.NONE, new Function0<ViewModelStoreOwner>() { // from class: com.ayaneo.settings.ui.performance.PerformanceFragment$special$$inlined$viewModels$default$2
            {
                super(0);
            }

            /* JADX WARN: Can't rename method to resolve collision */
            @Override // kotlin.jvm.functions.Function0
            @NotNull
            public final ViewModelStoreOwner invoke() {
                return (ViewModelStoreOwner) function0.invoke();
            }
        });
        final Function0 function1 = null;
        this.viewModel = FragmentViewModelLazyKt.h(this, Reflection.d(PerformanceViewModel.class), new Function0<ViewModelStore>() { // from class: com.ayaneo.settings.ui.performance.PerformanceFragment$special$$inlined$viewModels$default$3
            {
                super(0);
            }

            /* JADX WARN: Can't rename method to resolve collision */
            @Override // kotlin.jvm.functions.Function0
            @NotNull
            public final ViewModelStore invoke() {
                ViewModelStore viewModelStoreW = ((ViewModelStoreOwner) lazyB.getValue()).w();
                Intrinsics.o(viewModelStoreW, "owner.viewModelStore");
                return viewModelStoreW;
            }
        }, new Function0<CreationExtras>() { // from class: com.ayaneo.settings.ui.performance.PerformanceFragment$special$$inlined$viewModels$default$4
            /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
            {
                super(0);
            }

            /* JADX WARN: Can't rename method to resolve collision */
            @Override // kotlin.jvm.functions.Function0
            @NotNull
            public final CreationExtras invoke() {
                CreationExtras creationExtras;
                Function0 function2 = function1;
                if (function2 != null && (creationExtras = (CreationExtras) function2.invoke()) != null) {
                    return creationExtras;
                }
                ViewModelStoreOwner viewModelStoreOwner = (ViewModelStoreOwner) lazyB.getValue();
                HasDefaultViewModelProviderFactory hasDefaultViewModelProviderFactory = viewModelStoreOwner instanceof HasDefaultViewModelProviderFactory ? (HasDefaultViewModelProviderFactory) viewModelStoreOwner : null;
                CreationExtras creationExtrasP = hasDefaultViewModelProviderFactory != null ? hasDefaultViewModelProviderFactory.p() : null;
                return creationExtrasP == null ? CreationExtras.Empty.f7236b : creationExtrasP;
            }
        }, new Function0<ViewModelProvider.Factory>() { // from class: com.ayaneo.settings.ui.performance.PerformanceFragment$special$$inlined$viewModels$default$5
            /* JADX WARN: 'super' call moved to the top of the method (can break code semantics) */
            {
                super(0);
            }

            /* JADX WARN: Can't rename method to resolve collision */
            @Override // kotlin.jvm.functions.Function0
            @NotNull
            public final ViewModelProvider.Factory invoke() {
                ViewModelProvider.Factory factoryO;
                ViewModelStoreOwner viewModelStoreOwner = (ViewModelStoreOwner) lazyB.getValue();
                HasDefaultViewModelProviderFactory hasDefaultViewModelProviderFactory = viewModelStoreOwner instanceof HasDefaultViewModelProviderFactory ? (HasDefaultViewModelProviderFactory) viewModelStoreOwner : null;
                if (hasDefaultViewModelProviderFactory == null || (factoryO = hasDefaultViewModelProviderFactory.o()) == null) {
                    factoryO = this.o();
                }
                Intrinsics.o(factoryO, "(owner as? HasDefaultVie…tViewModelProviderFactory");
                return factoryO;
            }
        });
        this.curMode = -1;
        this.aidlCallBack = new AyaAidlCallback.Stub() { // from class: com.ayaneo.settings.ui.performance.PerformanceFragment$aidlCallBack$1
            @Override // com.ayaneo.gamewindow.AyaAidlCallback
            public void v(String msg) {
                Intrinsics.p(msg, "msg");
                if (MessagePasserKt.b(msg, AidlConstants.COM_SET_PERFORMANCE_MODE)) {
                    this.f16891m.j3().t(MessagePasserKt.a(msg));
                }
            }
        };
    }

    /* JADX INFO: Access modifiers changed from: private */
    public final PerformanceViewModel j3() {
        return (PerformanceViewModel) this.viewModel.getValue();
    }

    private final void k3() {
        TextView tvMode01 = Q2().f15293h;
        Intrinsics.o(tvMode01, "tvMode01");
        UtilKt.b0(tvMode01, new Function1() { // from class: com.ayaneo.settings.ui.performance.o
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return PerformanceFragment.l3(this.f16939a, (View) obj);
            }
        });
        TextView tvMode02 = Q2().f15294i;
        Intrinsics.o(tvMode02, "tvMode02");
        UtilKt.b0(tvMode02, new Function1() { // from class: com.ayaneo.settings.ui.performance.p
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return PerformanceFragment.m3(this.f16940a, (View) obj);
            }
        });
        TextView tvMode03 = Q2().f15295j;
        Intrinsics.o(tvMode03, "tvMode03");
        UtilKt.b0(tvMode03, new Function1() { // from class: com.ayaneo.settings.ui.performance.q
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return PerformanceFragment.n3(this.f16941a, (View) obj);
            }
        });
        TextView tvMode04 = Q2().f15296k;
        Intrinsics.o(tvMode04, "tvMode04");
        UtilKt.b0(tvMode04, new Function1() { // from class: com.ayaneo.settings.ui.performance.r
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return PerformanceFragment.o3(this.f16942a, (View) obj);
            }
        });
        TextView tvMode05 = Q2().f15297l;
        Intrinsics.o(tvMode05, "tvMode05");
        UtilKt.b0(tvMode05, new Function1() { // from class: com.ayaneo.settings.ui.performance.s
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return PerformanceFragment.p3(this.f16943a, (View) obj);
            }
        });
    }

    public static final Unit l3(PerformanceFragment performanceFragment, View it) {
        Intrinsics.p(it, "it");
        performanceFragment.u3(0);
        return Unit.f21153a;
    }

    public static final Unit m3(PerformanceFragment performanceFragment, View it) {
        Intrinsics.p(it, "it");
        performanceFragment.u3(1);
        return Unit.f21153a;
    }

    public static final Unit n3(PerformanceFragment performanceFragment, View it) {
        Intrinsics.p(it, "it");
        performanceFragment.u3(2);
        return Unit.f21153a;
    }

    public static final Unit o3(PerformanceFragment performanceFragment, View it) {
        Intrinsics.p(it, "it");
        performanceFragment.u3(3);
        return Unit.f21153a;
    }

    public static final Unit p3(PerformanceFragment performanceFragment, View it) {
        Intrinsics.p(it, "it");
        performanceFragment.u3(4);
        return Unit.f21153a;
    }

    private final void q3(ButtonIndicator buttonKey) {
        FragmentActivity fragmentActivityW1 = W1();
        Intrinsics.n(fragmentActivityW1, "null cannot be cast to non-null type com.ayaneo.settings.ui.global.MainActivity");
        ((MainActivity) fragmentActivityW1).s1().s(new ChildKeyEventListener() { // from class: com.ayaneo.settings.ui.performance.m
            @Override // com.ayaneo.settings.customview.ChildKeyEventListener
            public final void a() {
                PerformanceFragment.s3(this.f16937a);
            }
        });
        FragmentActivity fragmentActivityW2 = W1();
        Intrinsics.o(fragmentActivityW2, "requireActivity(...)");
        buttonKey.m(fragmentActivityW2);
        buttonKey.r(false, ButtonIndicatorKt.f(), ButtonIndicatorKt.d(), ButtonIndicatorKt.b());
        buttonKey.setOnButtonClick(new Function1() { // from class: com.ayaneo.settings.ui.performance.n
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return PerformanceFragment.r3(this.f16938a, (Key) obj);
            }
        });
    }

    public static final Unit r3(PerformanceFragment performanceFragment, Key it) {
        Intrinsics.p(it, "it");
        if (it == Key.A) {
            View viewFindFocus = performanceFragment.Q2().f15286a.findFocus();
            if (viewFindFocus != null) {
                viewFindFocus.performClick();
            }
            View viewFindFocus2 = performanceFragment.Q2().f15290e.findFocus();
            if (viewFindFocus2 != null) {
                viewFindFocus2.performClick();
            }
        } else if (it == Key.B) {
            AppSound.f17174a.c(Raw.CANCEL);
            if (performanceFragment.v() != null) {
                FragmentActivity fragmentActivityV = performanceFragment.v();
                Intrinsics.n(fragmentActivityV, "null cannot be cast to non-null type com.ayaneo.settings.ui.global.MainActivity");
                ((MainActivity) fragmentActivityV).N1();
            }
        }
        return Unit.f21153a;
    }

    public static final void s3(PerformanceFragment performanceFragment) {
        performanceFragment.Q2().f15293h.requestFocus();
    }

    public final void A3(int cpuId, int value) {
        List<Fragment> listI0 = D().I0();
        Intrinsics.o(listI0, "getFragments(...)");
        for (Fragment fragment : listI0) {
            if (Intrinsics.g(fragment.C, "ModeFragment")) {
                Intrinsics.n(fragment, "null cannot be cast to non-null type com.ayaneo.settings.ui.performance.ModeFragment");
                ((ModeFragment) fragment).J3(cpuId, value);
            }
        }
    }

    public final void B3(int currentMode, String configDataJson) {
        t3(currentMode);
        if (this.curMode != currentMode) {
            v3(ModeFragment.INSTANCE.a(currentMode));
            this.curMode = currentMode;
        } else {
            ModeFragment modeFragment = (ModeFragment) D().I0().get(0);
            if (modeFragment != null) {
                modeFragment.u3(configDataJson);
            }
        }
    }

    @Override // androidx.fragment.app.Fragment
    public void o1() {
        this.J = true;
        AyaAidlManager ayaAidlManager = AyaAidlManager.f17275a;
        ayaAidlManager.h();
        ayaAidlManager.l(AyaAidlManager.MSG_TYPE_PERFORMANCE, this.aidlCallBack);
    }

    @Override // androidx.fragment.app.Fragment
    public void p1() {
        this.J = true;
        AyaAidlManager.f17275a.m();
    }

    @Override // com.ayaneo.settings.base.BaseFragment, androidx.fragment.app.Fragment
    public void q1(@NotNull View view, @Nullable Bundle savedInstanceState) {
        Intrinsics.p(view, "view");
        super.q1(view, savedInstanceState);
        TextView tvMode03 = Q2().f15295j;
        Intrinsics.o(tvMode03, "tvMode03");
        tvMode03.setVisibility(AyaDevicesKt.a().getHasStreamingMode() ? 0 : 8);
        if (AyaDevicesUtilKt.d()) {
            ViewGroup.LayoutParams layoutParams = Q2().f15288c.getLayoutParams();
            Intrinsics.n(layoutParams, "null cannot be cast to non-null type android.view.ViewGroup.MarginLayoutParams");
            AppUiAdapter appUiAdapter = AppUiAdapter.f17178a;
            ((ViewGroup.MarginLayoutParams) layoutParams).setMarginStart(appUiAdapter.o(50));
            ViewGroup.LayoutParams layoutParams2 = Q2().f15288c.getLayoutParams();
            Intrinsics.n(layoutParams2, "null cannot be cast to non-null type android.view.ViewGroup.MarginLayoutParams");
            ((ViewGroup.MarginLayoutParams) layoutParams2).setMarginEnd(appUiAdapter.o(64));
        } else if (AyaDevicesUtilKt.w) {
            ConstraintLayout constraintLayout = Q2().f15289d;
            ViewGroup.LayoutParams layoutParams3 = constraintLayout.getLayoutParams();
            layoutParams3.height = AppUiAdapter.f17178a.o(125);
            Q2().f15289d.setLayoutParams(layoutParams3);
            Intrinsics.m(constraintLayout);
            for (View view2 : ViewGroupKt.e(constraintLayout)) {
                ViewGroup.LayoutParams layoutParams4 = view2.getLayoutParams();
                layoutParams4.height = AppUiAdapter.f17178a.o(78);
                view2.setLayoutParams(layoutParams4);
            }
        } else if (AyaDevicesUtilKt.B) {
            TextView tvMode01 = Q2().f15293h;
            Intrinsics.o(tvMode01, "tvMode01");
            AppUiAdapter appUiAdapter2 = AppUiAdapter.f17178a;
            UtilKt.Y(tvMode01, appUiAdapter2.o(30));
            TextView tvMode02 = Q2().f15294i;
            Intrinsics.o(tvMode02, "tvMode02");
            UtilKt.Y(tvMode02, appUiAdapter2.o(40));
            TextView tvMode04 = Q2().f15295j;
            Intrinsics.o(tvMode04, "tvMode03");
            UtilKt.Y(tvMode04, appUiAdapter2.o(40));
            TextView tvMode05 = Q2().f15296k;
            Intrinsics.o(tvMode05, "tvMode04");
            UtilKt.Y(tvMode05, appUiAdapter2.o(40));
            TextView tvMode06 = Q2().f15296k;
            Intrinsics.o(tvMode06, "tvMode04");
            UtilKt.X(tvMode06, appUiAdapter2.o(40));
            TextView tvMode07 = Q2().f15297l;
            Intrinsics.o(tvMode07, "tvMode05");
            UtilKt.Y(tvMode07, appUiAdapter2.o(40));
        } else if (AyaDevicesUtilKt.f13827q) {
            TextView tvMode08 = Q2().f15293h;
            Intrinsics.o(tvMode08, "tvMode01");
            AppUiAdapter appUiAdapter3 = AppUiAdapter.f17178a;
            UtilKt.Y(tvMode08, appUiAdapter3.o(30));
            TextView tvMode09 = Q2().f15294i;
            Intrinsics.o(tvMode09, "tvMode02");
            UtilKt.Y(tvMode09, appUiAdapter3.o(40));
            TextView tvMode010 = Q2().f15295j;
            Intrinsics.o(tvMode010, "tvMode03");
            UtilKt.Y(tvMode010, appUiAdapter3.o(40));
            TextView tvMode011 = Q2().f15296k;
            Intrinsics.o(tvMode011, "tvMode04");
            UtilKt.Y(tvMode011, appUiAdapter3.o(40));
            TextView tvMode012 = Q2().f15297l;
            Intrinsics.o(tvMode012, "tvMode05");
            UtilKt.Y(tvMode012, appUiAdapter3.o(40));
        }
        LifecycleOwner lifecycleOwnerS0 = s0();
        Intrinsics.o(lifecycleOwnerS0, "getViewLifecycleOwner(...)");
        BuildersKt__Builders_commonKt.f(LifecycleOwnerKt.a(lifecycleOwnerS0), null, null, new PerformanceFragment$onViewCreated$2(this, null), 3, null);
        ButtonIndicator buttonKey = Q2().f15287b;
        Intrinsics.o(buttonKey, "buttonKey");
        q3(buttonKey);
        k3();
        TextView tvMode013 = Q2().f15297l;
        Intrinsics.o(tvMode013, "tvMode05");
        UtilKt.n(tvMode013, 22);
    }

    public final void t3(int mode) {
        Q2().f15293h.setSelected(mode == 0);
        Q2().f15294i.setSelected(mode == 1);
        Q2().f15295j.setSelected(mode == 2);
        Q2().f15296k.setSelected(mode == 3);
        Q2().f15297l.setSelected(mode == 4);
    }

    public final void u3(int mode) {
        PerformanceModel.ConfigData configDataA;
        String json = new Gson().toJson(AyaDevicesKt.a().N().get(Integer.valueOf(mode)));
        String strF = AyaShareConfUtilKt.f(AyaDevicesKt.f13832a.Y0(), null, 1, null);
        if (strF != null && strF.length() != 0 && (configDataA = PerformanceModelKt.a(strF)) != null) {
            configDataA.g(mode);
            json = PerformanceModelKt.b(configDataA);
        }
        Function1<PerformanceViewModel.UiAction, Unit> function1 = j3().uiAction;
        Intrinsics.m(json);
        function1.invoke(new PerformanceViewModel.UiAction.ChangeMode(json));
        AyaAidlManager.f17275a.k(AyaAidlManager.MSG_TYPE_PERFORMANCE, "com_set_performance_mode:" + mode);
    }

    public final void v3(Fragment fragment) {
        D().u().D(R.id.Z0, fragment, "ModeFragment").q();
    }

    public final void w3(int id) {
        Q2().f15286a.findViewById(id).requestFocus();
    }

    public final void x3() {
        Q2().f15291f.Y(0, AppUiAdapter.f17178a.o(RecyclerView.e1));
    }

    public final void y3() {
        Q2().f15291f.Y(0, 0);
    }

    public final void z3(boolean isShow) {
        ButtonIndicator buttonKey = Q2().f15287b;
        Intrinsics.o(buttonKey, "buttonKey");
        buttonKey.setVisibility(isShow ? 0 : 8);
    }
}
