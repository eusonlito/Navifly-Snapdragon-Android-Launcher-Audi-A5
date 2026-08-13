package com.szchoiceway.eventcenter;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface IEventService extends IInterface {
    void setDashBoardCallback(ICallbackfn cb) throws RemoteException;
    int getGearType() throws RemoteException;

    abstract class Stub extends Binder implements IEventService {
        private static final String DESCRIPTOR = "com.szchoiceway.eventcenter.IEventService";

        public static IEventService asInterface(IBinder obj) {
            if (obj == null) return null;
            IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (iin != null && iin instanceof IEventService) {
                return (IEventService) iin;
            }
            return new Proxy(obj);
        }

        private static class Proxy implements IEventService {
            private final IBinder mRemote;

            Proxy(IBinder remote) {
                mRemote = remote;
            }

            @Override
            public IBinder asBinder() {
                return mRemote;
            }

            @Override
            public void setDashBoardCallback(ICallbackfn cb) throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    data.writeStrongBinder(cb != null ? cb.asBinder() : null);
                    // 120 is the exact transaction ID for setDashBoardCallback
                    mRemote.transact(120, data, reply, 0);
                    reply.readException();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }

            @Override
            public int getGearType() throws RemoteException {
                Parcel data = Parcel.obtain();
                Parcel reply = Parcel.obtain();
                try {
                    data.writeInterfaceToken(DESCRIPTOR);
                    // 154 is the exact transaction ID for getGearType
                    mRemote.transact(154, data, reply, 0);
                    reply.readException();
                    return reply.readInt();
                } finally {
                    reply.recycle();
                    data.recycle();
                }
            }
        }
    }
}
