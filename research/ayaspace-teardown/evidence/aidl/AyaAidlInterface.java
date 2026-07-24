package com.ayaneo.gamewindow;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

/* JADX INFO: loaded from: classes.dex */
public interface AyaAidlInterface extends IInterface {

    /* JADX INFO: renamed from: k, reason: collision with root package name */
    public static final String f13905k = "com.ayaneo.gamewindow.AyaAidlInterface";

    public static class Default implements AyaAidlInterface {
        @Override // android.os.IInterface
        public IBinder asBinder() {
            return null;
        }

        @Override // com.ayaneo.gamewindow.AyaAidlInterface
        public void k(AyaAidlCallback ayaAidlCallback) throws RemoteException {
        }

        @Override // com.ayaneo.gamewindow.AyaAidlInterface
        public void q(AyaAidlCallback ayaAidlCallback) throws RemoteException {
        }

        @Override // com.ayaneo.gamewindow.AyaAidlInterface
        public void send(String str) throws RemoteException {
        }
    }

    public static abstract class Stub extends Binder implements AyaAidlInterface {

        /* JADX INFO: renamed from: l, reason: collision with root package name */
        public static final int f13906l = 1;

        /* JADX INFO: renamed from: m, reason: collision with root package name */
        public static final int f13907m = 2;

        /* JADX INFO: renamed from: n, reason: collision with root package name */
        public static final int f13908n = 3;

        public static class Proxy implements AyaAidlInterface {

            /* JADX INFO: renamed from: l, reason: collision with root package name */
            public IBinder f13909l;

            public Proxy(IBinder iBinder) {
                this.f13909l = iBinder;
            }

            public String B() {
                return AyaAidlInterface.f13905k;
            }

            @Override // android.os.IInterface
            public IBinder asBinder() {
                return this.f13909l;
            }

            @Override // com.ayaneo.gamewindow.AyaAidlInterface
            public void k(AyaAidlCallback ayaAidlCallback) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(AyaAidlInterface.f13905k);
                    parcelObtain.writeStrongInterface(ayaAidlCallback);
                    this.f13909l.transact(2, parcelObtain, parcelObtain2, 0);
                    parcelObtain2.readException();
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // com.ayaneo.gamewindow.AyaAidlInterface
            public void q(AyaAidlCallback ayaAidlCallback) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(AyaAidlInterface.f13905k);
                    parcelObtain.writeStrongInterface(ayaAidlCallback);
                    this.f13909l.transact(3, parcelObtain, parcelObtain2, 0);
                    parcelObtain2.readException();
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }

            @Override // com.ayaneo.gamewindow.AyaAidlInterface
            public void send(String str) throws RemoteException {
                Parcel parcelObtain = Parcel.obtain();
                Parcel parcelObtain2 = Parcel.obtain();
                try {
                    parcelObtain.writeInterfaceToken(AyaAidlInterface.f13905k);
                    parcelObtain.writeString(str);
                    this.f13909l.transact(1, parcelObtain, parcelObtain2, 0);
                    parcelObtain2.readException();
                } finally {
                    parcelObtain2.recycle();
                    parcelObtain.recycle();
                }
            }
        }

        public Stub() {
            attachInterface(this, AyaAidlInterface.f13905k);
        }

        public static AyaAidlInterface B(IBinder iBinder) {
            if (iBinder == null) {
                return null;
            }
            IInterface iInterfaceQueryLocalInterface = iBinder.queryLocalInterface(AyaAidlInterface.f13905k);
            return (iInterfaceQueryLocalInterface == null || !(iInterfaceQueryLocalInterface instanceof AyaAidlInterface)) ? new Proxy(iBinder) : (AyaAidlInterface) iInterfaceQueryLocalInterface;
        }

        @Override // android.os.IInterface
        public IBinder asBinder() {
            return this;
        }

        @Override // android.os.Binder
        public boolean onTransact(int i2, Parcel parcel, Parcel parcel2, int i3) throws RemoteException {
            if (i2 >= 1 && i2 <= 16777215) {
                parcel.enforceInterface(AyaAidlInterface.f13905k);
            }
            if (i2 == 1598968902) {
                parcel2.writeString(AyaAidlInterface.f13905k);
                return true;
            }
            if (i2 == 1) {
                send(parcel.readString());
                parcel2.writeNoException();
            } else if (i2 == 2) {
                k(AyaAidlCallback.Stub.B(parcel.readStrongBinder()));
                parcel2.writeNoException();
            } else {
                if (i2 != 3) {
                    return super.onTransact(i2, parcel, parcel2, i3);
                }
                q(AyaAidlCallback.Stub.B(parcel.readStrongBinder()));
                parcel2.writeNoException();
            }
            return true;
        }
    }

    void k(AyaAidlCallback ayaAidlCallback) throws RemoteException;

    void q(AyaAidlCallback ayaAidlCallback) throws RemoteException;

    void send(String str) throws RemoteException;
}
