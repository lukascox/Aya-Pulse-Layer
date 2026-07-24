package com.ayaneo.gamewindow;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/* JADX INFO: loaded from: classes.dex */
public interface AyaAidlCallback extends IInterface {

    /* JADX INFO: renamed from: j, reason: collision with root package name */
    public static final String f13902j = "com.ayaneo.gamewindow.AyaAidlCallback";

    public static class Default implements AyaAidlCallback {
        @Override // android.os.IInterface
        public IBinder asBinder() {
            return null;
        }

        @Override // com.ayaneo.gamewindow.AyaAidlCallback
        public void v(String str) throws RemoteException {
        }
    }

    public static abstract class Stub extends Binder implements AyaAidlCallback {

        /* JADX INFO: renamed from: l, reason: collision with root package name */
        public static final int f13903l = 1;

        public static class Proxy implements AyaAidlCallback {

            /* JADX INFO: renamed from: l, reason: collision with root package name */
            public IBinder f13904l;

            public Proxy(IBinder iBinder) {
                this.f13904l = iBinder;
            }

            public String B() {
                return AyaAidlCallback.f13902j;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.f13904l;
            }

            @Override // com.ayaneo.gamewindow.AyaAidlCallback
            public void v(String str) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(AyaAidlCallback.f13902j);
                    parcelObtain.writeString(str);
                    this.f13904l.transact(1, parcelObtain, parcelObtain2, 0);
                    parcelObtain2.readException();
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }
        }

        public Stub() {
            attachInterface(this, AyaAidlCallback.f13902j);
        }

        public static AyaAidlCallback B(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface iInterfaceQueryLocalInterface = iBinder.queryLocalInterface(AyaAidlCallback.f13902j);
            return (iInterfaceQueryLocalInterface == null || !(iInterfaceQueryLocalInterface instanceof AyaAidlCallback)) ? new Proxy(iBinder) : (AyaAidlCallback) iInterfaceQueryLocalInterface;
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int i2, Parcel parcel, Parcel parcel2, int i3) throws RemoteException {
            if (i2 >= 1 && i2 <= 16777215) {
                parcel.enforceInterface(AyaAidlCallback.f13902j);
            }
            if (i2 == 1598968902) {
                parcel2.writeString(AyaAidlCallback.f13902j);
                return true;
            }
            if (i2 != 1) {
                return super.onTransact(i2, parcel, parcel2, i3);
            }
            v(parcel.readString());
            parcel2.writeNoException();
            return true;
        }
    }

    void v(String str) throws RemoteException;
}
