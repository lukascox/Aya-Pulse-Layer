package com.ayaneo.settings.ui.personalization;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.p003navigation.fragment.FragmentKt;
import com.ayaneo.devices.AyaDevicesKt;
import com.ayaneo.provider.AyaSetting;
import com.ayaneo.provider.AyaSettingProvider;
import com.ayaneo.settings.customview.ButtonIndicator;
import com.ayaneo.settings.customview.ButtonIndicatorKt;
import com.ayaneo.settings.customview.Key;
import com.ayaneo.settings.databinding.FragSwitchPerformanceModeBinding;
import com.ayaneo.settings.utils.UtilKt;
import dagger.hilt.android.AndroidEntryPoint;
import kotlin.Metadata;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function3;
import kotlin.jvm.internal.FunctionReferenceImpl;
import kotlin.jvm.internal.Intrinsics;
import kotlin.jvm.internal.SourceDebugExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/* JADX INFO: loaded from: classes.dex */
@Metadata(d1 = {"\u0000<\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0010\b\n\u0002\b\u0002\n\u0002\u0010\u000b\n\u0002\b\u0007\b\u0007\u0018\u0000 \u00182\b\u0012\u0004\u0012\u00020\u00020\u0001:\u0001\u0019B\u0007¢\u0006\u0004\b\u0003\u0010\u0004J\u0017\u0010\b\u001a\u00020\u00072\u0006\u0010\u0006\u001a\u00020\u0005H\u0002¢\u0006\u0004\b\b\u0010\tJ!\u0010\u000e\u001a\u00020\u00072\u0006\u0010\u000b\u001a\u00020\n2\b\u0010\r\u001a\u0004\u0018\u00010\fH\u0016¢\u0006\u0004\b\u000e\u0010\u000fJ\u000f\u0010\u0011\u001a\u00020\u0010H\u0002¢\u0006\u0004\b\u0011\u0010\u0012J\u001f\u0010\u0016\u001a\u00020\u00072\u0006\u0010\u0014\u001a\u00020\u00132\u0006\u0010\u0015\u001a\u00020\u0010H\u0002¢\u0006\u0004\b\u0016\u0010\u0017¨\u0006\u001a"}, d2 = {"Lcom/ayaneo/settings/ui/personalization/SwitchPerformanceModeFragment;", "Lcom/ayaneo/settings/base/BaseFragment;", "Lcom/ayaneo/settings/databinding/FragSwitchPerformanceModeBinding;", "<init>", "()V", "Lcom/ayaneo/settings/customview/ButtonIndicator;", "buttonKey", "", "n3", "(Lcom/ayaneo/settings/customview/ButtonIndicator;)V", "Landroid/view/View;", "view", "Landroid/os/Bundle;", "savedInstanceState", "q1", "(Landroid/view/View;Landroid/os/Bundle;)V", "", "m3", "()I", "", "bool", "mode", "l3", "(ZI)V", "E0", "PerformanceMode", "app_QCOMRelease"}, k = 1, mv = {2, 0, 0})
@AndroidEntryPoint
@SourceDebugExtension({"SMAP\nSwitchPerformanceModeFragment.kt\nKotlin\n*S Kotlin\n*F\n+ 1 SwitchPerformanceModeFragment.kt\ncom/ayaneo/settings/ui/personalization/SwitchPerformanceModeFragment\n+ 2 View.kt\nandroidx/core/view/ViewKt\n*L\n1#1,123:1\n262#2,2:124\n*S KotlinDebug\n*F\n+ 1 SwitchPerformanceModeFragment.kt\ncom/ayaneo/settings/ui/personalization/SwitchPerformanceModeFragment\n*L\n60#1:124,2\n*E\n"})
public final class SwitchPerformanceModeFragment extends Hilt_SwitchPerformanceModeFragment<FragSwitchPerformanceModeBinding> {
    public static final int F0 = 1;
    public static final int G0 = 2;
    public static final int H0 = 4;
    public static final int I0 = 8;
    public static final int J0 = 16;
    public static final int K0 = 31;

    /* JADX INFO: renamed from: com.ayaneo.settings.ui.personalization.SwitchPerformanceModeFragment$1, reason: invalid class name */
    @Metadata(k = 3, mv = {2, 0, 0}, xi = 48)
    public /* synthetic */ class AnonymousClass1 extends FunctionReferenceImpl implements Function3<LayoutInflater, ViewGroup, Boolean, FragSwitchPerformanceModeBinding> {
        public static final AnonymousClass1 INSTANCE = new AnonymousClass1();

        public AnonymousClass1() {
            super(3, FragSwitchPerformanceModeBinding.class, "inflate", "inflate(Landroid/view/LayoutInflater;Landroid/view/ViewGroup;Z)Lcom/ayaneo/settings/databinding/FragSwitchPerformanceModeBinding;", 0);
        }

        public final FragSwitchPerformanceModeBinding invoke(LayoutInflater p0, ViewGroup viewGroup, boolean z) {
            Intrinsics.p(p0, "p0");
            return FragSwitchPerformanceModeBinding.e(p0, viewGroup, z);
        }

        @Override // kotlin.jvm.functions.Function3
        public /* bridge */ /* synthetic */ FragSwitchPerformanceModeBinding invoke(LayoutInflater layoutInflater, ViewGroup viewGroup, Boolean bool) {
            return invoke(layoutInflater, viewGroup, bool.booleanValue());
        }
    }

    public SwitchPerformanceModeFragment() {
        super(AnonymousClass1.INSTANCE);
    }

    private final void n3(ButtonIndicator buttonKey) {
        buttonKey.r(false, ButtonIndicatorKt.f(), ButtonIndicatorKt.d(), ButtonIndicatorKt.b());
        buttonKey.setOnButtonClick(new Function1() { // from class: com.ayaneo.settings.ui.personalization.u
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return SwitchPerformanceModeFragment.o3(this.f17068a, (Key) obj);
            }
        });
    }

    /* JADX WARN: Multi-variable type inference failed */
    public static final Unit o3(SwitchPerformanceModeFragment switchPerformanceModeFragment, Key it) {
        Intrinsics.p(it, "it");
        if (it == Key.A) {
            View viewFindFocus = ((FragSwitchPerformanceModeBinding) switchPerformanceModeFragment.Q2()).f15429a.findFocus();
            if (viewFindFocus != null) {
                viewFindFocus.performClick();
            }
        } else if (it == Key.B) {
            FragmentKt.a(switchPerformanceModeFragment).s0();
        }
        return Unit.f21153a;
    }

    /* JADX WARN: Multi-variable type inference failed */
    public static final Unit p3(SwitchPerformanceModeFragment switchPerformanceModeFragment, View it) {
        Intrinsics.p(it, "it");
        boolean z = !((FragSwitchPerformanceModeBinding) switchPerformanceModeFragment.Q2()).f15439k.isChecked();
        if (switchPerformanceModeFragment.m3() > 2 || z) {
            ((FragSwitchPerformanceModeBinding) switchPerformanceModeFragment.Q2()).f15439k.setChecked(z);
            switchPerformanceModeFragment.l3(z, 1);
        }
        return Unit.f21153a;
    }

    /* JADX WARN: Multi-variable type inference failed */
    public static final Unit q3(SwitchPerformanceModeFragment switchPerformanceModeFragment, View it) {
        Intrinsics.p(it, "it");
        boolean z = !((FragSwitchPerformanceModeBinding) switchPerformanceModeFragment.Q2()).f15436h.isChecked();
        if (switchPerformanceModeFragment.m3() > 2 || z) {
            ((FragSwitchPerformanceModeBinding) switchPerformanceModeFragment.Q2()).f15436h.setChecked(z);
            switchPerformanceModeFragment.l3(z, 2);
        }
        return Unit.f21153a;
    }

    /* JADX WARN: Multi-variable type inference failed */
    public static final Unit r3(SwitchPerformanceModeFragment switchPerformanceModeFragment, View it) {
        Intrinsics.p(it, "it");
        boolean z = !((FragSwitchPerformanceModeBinding) switchPerformanceModeFragment.Q2()).f15440l.isChecked();
        if (switchPerformanceModeFragment.m3() > 2 || z) {
            ((FragSwitchPerformanceModeBinding) switchPerformanceModeFragment.Q2()).f15440l.setChecked(z);
            switchPerformanceModeFragment.l3(z, 4);
        }
        return Unit.f21153a;
    }

    /* JADX WARN: Multi-variable type inference failed */
    public static final Unit s3(SwitchPerformanceModeFragment switchPerformanceModeFragment, View it) {
        Intrinsics.p(it, "it");
        boolean z = !((FragSwitchPerformanceModeBinding) switchPerformanceModeFragment.Q2()).f15437i.isChecked();
        if (switchPerformanceModeFragment.m3() > 2 || z) {
            ((FragSwitchPerformanceModeBinding) switchPerformanceModeFragment.Q2()).f15437i.setChecked(z);
            switchPerformanceModeFragment.l3(z, 8);
        }
        return Unit.f21153a;
    }

    /* JADX WARN: Multi-variable type inference failed */
    public static final Unit t3(SwitchPerformanceModeFragment switchPerformanceModeFragment, View it) {
        Intrinsics.p(it, "it");
        boolean z = !((FragSwitchPerformanceModeBinding) switchPerformanceModeFragment.Q2()).f15438j.isChecked();
        if (switchPerformanceModeFragment.m3() > 2 || z) {
            ((FragSwitchPerformanceModeBinding) switchPerformanceModeFragment.Q2()).f15438j.setChecked(z);
            switchPerformanceModeFragment.l3(z, 16);
        }
        return Unit.f21153a;
    }

    public static final Unit u3(SwitchPerformanceModeFragment switchPerformanceModeFragment, View it) {
        Intrinsics.p(it, "it");
        FragmentKt.a(switchPerformanceModeFragment).s0();
        return Unit.f21153a;
    }

    public final void l3(boolean bool, int mode) {
        AyaSettingProvider ayaSettingProvider = AyaSettingProvider.f13956c;
        int iJ = ayaSettingProvider.j(AyaSetting.SWITCH_PERFORMANCE_MODE, 31);
        ayaSettingProvider.r(AyaSetting.SWITCH_PERFORMANCE_MODE, bool ? iJ | mode : (~mode) & iJ);
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r0v14, types: [boolean, int] */
    /* JADX WARN: Type inference failed for: r0v5, types: [boolean, int] */
    public final int m3() {
        int i2;
        int i3;
        if (!AyaDevicesKt.a().getHasStreamingMode()) {
            ?? IsChecked = ((FragSwitchPerformanceModeBinding) Q2()).f15439k.isChecked();
            if (((FragSwitchPerformanceModeBinding) Q2()).f15436h.isChecked()) {
                i2 = IsChecked;
                i2 = IsChecked + 1;
            }
            i2 = IsChecked;
            int i4 = i2;
            if (((FragSwitchPerformanceModeBinding) Q2()).f15437i.isChecked()) {
                i4 = i2 + 1;
            }
            return ((FragSwitchPerformanceModeBinding) Q2()).f15438j.isChecked() ? i4 + 1 : i4;
        }
        ?? IsChecked2 = ((FragSwitchPerformanceModeBinding) Q2()).f15439k.isChecked();
        if (((FragSwitchPerformanceModeBinding) Q2()).f15436h.isChecked()) {
            i3 = IsChecked2;
            i3 = IsChecked2 + 1;
        }
        i3 = IsChecked2;
        int i5 = i3;
        if (((FragSwitchPerformanceModeBinding) Q2()).f15440l.isChecked()) {
            i5 = i3 + 1;
        }
        int i6 = i5;
        if (((FragSwitchPerformanceModeBinding) Q2()).f15437i.isChecked()) {
            i6 = i5 + 1;
        }
        return ((FragSwitchPerformanceModeBinding) Q2()).f15438j.isChecked() ? i6 + 1 : i6;
    }

    /* JADX WARN: Multi-variable type inference failed */
    @Override // com.ayaneo.settings.base.BaseFragment, androidx.fragment.app.Fragment
    public void q1(@NotNull View view, @Nullable Bundle savedInstanceState) {
        Intrinsics.p(view, "view");
        super.q1(view, savedInstanceState);
        RelativeLayout rlStreaming = ((FragSwitchPerformanceModeBinding) Q2()).f15435g;
        Intrinsics.o(rlStreaming, "rlStreaming");
        rlStreaming.setVisibility(AyaDevicesKt.a().getHasStreamingMode() ? 0 : 8);
        int iJ = AyaSettingProvider.f13956c.j(AyaSetting.SWITCH_PERFORMANCE_MODE, 31);
        ((FragSwitchPerformanceModeBinding) Q2()).f15439k.setChecked((iJ & 1) != 0);
        ((FragSwitchPerformanceModeBinding) Q2()).f15436h.setChecked((iJ & 2) != 0);
        ((FragSwitchPerformanceModeBinding) Q2()).f15440l.setChecked((iJ & 4) != 0);
        ((FragSwitchPerformanceModeBinding) Q2()).f15437i.setChecked((iJ & 8) != 0);
        ((FragSwitchPerformanceModeBinding) Q2()).f15438j.setChecked((iJ & 16) != 0);
        ((FragSwitchPerformanceModeBinding) Q2()).f15434f.requestFocus();
        RelativeLayout rlSavingMode = ((FragSwitchPerformanceModeBinding) Q2()).f15434f;
        Intrinsics.o(rlSavingMode, "rlSavingMode");
        UtilKt.Q(rlSavingMode, false, new Function1() { // from class: com.ayaneo.settings.ui.personalization.o
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return SwitchPerformanceModeFragment.p3(this.f17062a, (View) obj);
            }
        }, 1, null);
        RelativeLayout rlBalanceMode = ((FragSwitchPerformanceModeBinding) Q2()).f15431c;
        Intrinsics.o(rlBalanceMode, "rlBalanceMode");
        UtilKt.Q(rlBalanceMode, false, new Function1() { // from class: com.ayaneo.settings.ui.personalization.p
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return SwitchPerformanceModeFragment.q3(this.f17063a, (View) obj);
            }
        }, 1, null);
        RelativeLayout rlStreaming2 = ((FragSwitchPerformanceModeBinding) Q2()).f15435g;
        Intrinsics.o(rlStreaming2, "rlStreaming");
        UtilKt.Q(rlStreaming2, false, new Function1() { // from class: com.ayaneo.settings.ui.personalization.q
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return SwitchPerformanceModeFragment.r3(this.f17064a, (View) obj);
            }
        }, 1, null);
        RelativeLayout rlGameMode = ((FragSwitchPerformanceModeBinding) Q2()).f15432d;
        Intrinsics.o(rlGameMode, "rlGameMode");
        UtilKt.Q(rlGameMode, false, new Function1() { // from class: com.ayaneo.settings.ui.personalization.r
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return SwitchPerformanceModeFragment.s3(this.f17065a, (View) obj);
            }
        }, 1, null);
        RelativeLayout rlMaxMode = ((FragSwitchPerformanceModeBinding) Q2()).f15433e;
        Intrinsics.o(rlMaxMode, "rlMaxMode");
        UtilKt.Q(rlMaxMode, false, new Function1() { // from class: com.ayaneo.settings.ui.personalization.s
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return SwitchPerformanceModeFragment.t3(this.f17066a, (View) obj);
            }
        }, 1, null);
        TextView tvReturn = ((FragSwitchPerformanceModeBinding) Q2()).f15441m;
        Intrinsics.o(tvReturn, "tvReturn");
        UtilKt.b0(tvReturn, new Function1() { // from class: com.ayaneo.settings.ui.personalization.t
            @Override // kotlin.jvm.functions.Function1
            public final Object invoke(Object obj) {
                return SwitchPerformanceModeFragment.u3(this.f17067a, (View) obj);
            }
        });
        ButtonIndicator buttonKey = ((FragSwitchPerformanceModeBinding) Q2()).f15430b;
        Intrinsics.o(buttonKey, "buttonKey");
        n3(buttonKey);
    }
}
