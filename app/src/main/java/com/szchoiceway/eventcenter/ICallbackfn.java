package com.szchoiceway.eventcenter;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface ICallbackfn extends IInterface {
    void notifyEvt(int i, int i2, int i3, byte[] bArr, String str) throws RemoteException;

    abstract class Stub extends Binder implements ICallbackfn {
        private static final String DESCRIPTOR = "com.szchoiceway.eventcenter.ICallbackfn";

        public Stub() {
            attachInterface(this, DESCRIPTOR);
        }

        public static ICallbackfn asInterface(IBinder obj) {
            if (obj == null) return null;
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && iin instanceof ICallbackfn) {
                return (ICallbackfn) iin;
            }
            return new Proxy(obj);
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == 1) { // TRANSACTION_notifyEvt = 1
                data.enforceInterface(DESCRIPTOR);
                int i = data.readInt();
                int i2 = data.readInt();
                int i3 = data.readInt();
                byte[] bArr = data.createByteArray();
                String str = data.readString();
                notifyEvt(i, i2, i3, bArr, str);
                reply.writeNoException();
                return true;
            }
            if (code == 1598968902) {
                reply.writeString(DESCRIPTOR);
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }

        private static class Proxy implements ICallbackfn {
            private final IBinder mRemote;

            Proxy(IBinder remote) {
                mRemote = remote;
            }

            @Override
            public IBinder asBinder() {
                return this.mRemote;
            }

            @Override
            public void notifyEvt(int i, int i2, int i3, byte[] bArr, String str) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeInt(i);
                    data.writeInt(i2);
                    data.writeInt(i3);
                    data.writeByteArray(bArr);
                    data.writeString(str);
                    mRemote.transact(1, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }
        }
    }
}
