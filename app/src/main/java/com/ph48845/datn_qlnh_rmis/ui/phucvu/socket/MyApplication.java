package com.ph48845.datn_qlnh_rmis.ui.phucvu.socket;


import android.app.Application;
import android.util.Log;

import com.ph48845.datn_qlnh_rmis.ui.phucvu.socket.SocketManager;

/**
 * Application class - initialize SocketManager here so socket lives for app lifetime.
 * Add android:name=".MyApplication" in AndroidManifest.xml <application> tag.
 */
public class MyApplication extends Application {

    private static final String TAG = "MyApplication";
    private static final String DEFAULT_SOCKET_URL = "http://192.168.1.84:3000";

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            SocketManager sm = SocketManager.getInstance();
            sm.init(DEFAULT_SOCKET_URL);
            sm.connect(); // keep socket connected for app lifetime
            Log.i(TAG, "SocketManager initialized and connected");
        } catch (Exception e) {
            Log.w(TAG, "Failed to init/connect SocketManager", e);
        }
    }
}