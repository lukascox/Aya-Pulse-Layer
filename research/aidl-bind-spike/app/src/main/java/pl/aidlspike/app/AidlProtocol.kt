package pl.aidlspike.app

import android.os.Binder
import android.os.IBinder
import android.os.Parcel

/**
 * Hand-rolled reconstruction of `com.ayaneo.gamewindow`'s undocumented AIDL wire
 * protocol -- there is no `.aidl` file (it's an internal, unpublished interface), so
 * this mirrors exactly what jadx showed in the decompiled `AyaAidlInterface.Stub`/
 * `AyaAidlCallback.Stub` classes (see `apl` repo's `research/ayaspace-teardown/
 * evidence/aidl/` and `research/aya-gamewindows-teardown/evidence/aidl/`), using raw
 * `Parcel`/`Binder.transact()` instead of AIDL codegen.
 *
 * Transaction codes and the interface token strings are taken directly from the
 * decompiled source, not guessed.
 */
object AidlProtocol {
    const val SERVICE_PACKAGE = "com.ayaneo.gamewindow"
    const val SERVICE_CLASS = "com.ayaneo.gamewindow.utils.aidl.AyaAidlService"

    const val SERVICE_INTERFACE_TOKEN = "com.ayaneo.gamewindow.AyaAidlInterface"
    const val CALLBACK_INTERFACE_TOKEN = "com.ayaneo.gamewindow.AyaAidlCallback"

    const val TXN_SEND = 1
    const val TXN_REGISTER_CALLBACK = 2
    const val TXN_UNREGISTER_CALLBACK = 3
    const val CALLBACK_TXN_DELIVER = 1

    /** Mirrors `AyaAidlInterface.Stub.Proxy.send(String)`. */
    fun send(service: IBinder, message: String) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(SERVICE_INTERFACE_TOKEN)
            data.writeString(message)
            service.transact(TXN_SEND, data, reply, 0)
            reply.readException()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /** Mirrors `AyaAidlInterface.Stub.Proxy.k(AyaAidlCallback)` -- register callback. */
    fun registerCallback(service: IBinder, callback: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(SERVICE_INTERFACE_TOKEN)
            data.writeStrongBinder(callback)
            service.transact(TXN_REGISTER_CALLBACK, data, reply, 0)
            reply.readException()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    /** Mirrors `AyaAidlInterface.Stub.Proxy.q(AyaAidlCallback)` -- unregister callback. */
    fun unregisterCallback(service: IBinder, callback: IBinder) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(SERVICE_INTERFACE_TOKEN)
            data.writeStrongBinder(callback)
            service.transact(TXN_UNREGISTER_CALLBACK, data, reply, 0)
            reply.readException()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }
}

/**
 * Our side of the callback channel -- gamewindow transacts INTO this (code 1 =
 * `v(String)` in the decompiled `AyaAidlCallback.Stub`) once registered, delivering
 * `"msg_type_register:<id>"` on connect and any unsolicited messages afterward. A
 * plain `Binder` subclass is enough; we don't need `attachInterface`/`IInterface`
 * since gamewindow only ever calls `transact()` on us, it never calls
 * `queryLocalInterface()` (that would only matter same-process, we're cross-process).
 */
class AidlCallbackStub(private val onMessage: (String) -> Unit) : Binder() {
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == IBinder.INTERFACE_TRANSACTION) {
            reply?.writeString(AidlProtocol.CALLBACK_INTERFACE_TOKEN)
            return true
        }
        if (code == AidlProtocol.CALLBACK_TXN_DELIVER) {
            data.enforceInterface(AidlProtocol.CALLBACK_INTERFACE_TOKEN)
            val msg = data.readString() ?: ""
            reply?.writeNoException()
            onMessage(msg)
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }
}
