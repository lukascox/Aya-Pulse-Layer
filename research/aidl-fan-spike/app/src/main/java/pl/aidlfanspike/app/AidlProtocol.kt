package pl.aidlfanspike.app

import android.os.Binder
import android.os.IBinder
import android.os.Parcel

/**
 * Same hand-rolled AIDL wire-protocol reconstruction as
 * `research/aidl-bind-spike/app/src/main/java/pl/aidlspike/app/AidlProtocol.kt` --
 * copied rather than shared, since these are throwaway single-purpose probes, not a
 * library. Transaction codes and interface tokens are unchanged (this targets the
 * same `AyaAidlService`, just a different `send()` message payload -- fan strategy
 * instead of performance mode).
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
 * Our side of the callback channel -- see the sibling `aidl-bind-spike` project's
 * `AidlCallbackStub` doc comment for why a plain `Binder` subclass (not
 * `IInterface`/AIDL codegen) is enough here.
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
