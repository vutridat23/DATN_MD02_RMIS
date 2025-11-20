package com.ph48845.datn_qlnh_rmis.ui.bep;



import android.util.Log;

import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.socket.client.IO;
import io.socket.client.Socket;

/**
 * Simple Socket.IO manager for realtime events.
 */
public class SocketManager {

    private static final String TAG = "SocketManager";
    private static SocketManager instance;
    private Socket socket;
    private String baseUrl;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    public interface OnEventListener {
        void onOrderCreated(JSONObject payload);
        void onOrderUpdated(JSONObject payload);
        void onConnect();
        void onDisconnect();
        void onError(Exception e);
    }

    private OnEventListener listener;

    private SocketManager() { }

    public static synchronized SocketManager getInstance() {
        if (instance == null) instance = new SocketManager();
        return instance;
    }

    /**
     * Initialize socket with base URL (e.g. "http://192.168.1.84:3000")
     */
    public synchronized void init(String baseUrl) {
        this.baseUrl = baseUrl;
        if (socket != null) {
            try { socket.off(); socket.close(); } catch (Exception ignored) {}
            socket = null;
        }
        try {
            IO.Options opts = new IO.Options();
            socket = IO.socket(baseUrl, opts);
            setupListeners();
        } catch (URISyntaxException e) {
            Log.e(TAG, "init socket failed: " + e.getMessage(), e);
            if (listener != null) listener.onError(e);
        }
    }

    private void setupListeners() {
        if (socket == null) return;

        socket.on(Socket.EVENT_CONNECT, args -> {
            connected.set(true);
            Log.d(TAG, "socket connected");
            if (listener != null) listener.onConnect();
        });

        socket.on(Socket.EVENT_DISCONNECT, args -> {
            connected.set(false);
            Log.d(TAG, "socket disconnected");
            if (listener != null) listener.onDisconnect();
        });

        socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
            Log.w(TAG, "socket connect error: " + args[0]);
            if (listener != null) listener.onError(new Exception(String.valueOf(args[0])));
        });

        // Expect server emits 'order_created' and 'order_updated' events (adjust names if needed)
        socket.on("order_created", args -> {
            try {
                JSONObject payload = (args != null && args.length > 0 && args[0] instanceof JSONObject) ? (JSONObject) args[0] : null;
                Log.d(TAG, "order_created received: " + (payload != null ? payload.toString() : "null"));
                if (listener != null) listener.onOrderCreated(payload);
            } catch (Exception e) {
                Log.w(TAG, "order_created handle failed", e);
            }
        });

        socket.on("order_updated", args -> {
            try {
                JSONObject payload = (args != null && args.length > 0 && args[0] instanceof JSONObject) ? (JSONObject) args[0] : null;
                Log.d(TAG, "order_updated received: " + (payload != null ? payload.toString() : "null"));
                if (listener != null) listener.onOrderUpdated(payload);
            } catch (Exception e) {
                Log.w(TAG, "order_updated handle failed", e);
            }
        });
    }

    public synchronized void connect() {
        if (socket == null && baseUrl != null) init(baseUrl);
        if (socket != null && !socket.connected()) socket.connect();
    }

    public synchronized void disconnect() {
        if (socket != null && socket.connected()) socket.disconnect();
    }

    public boolean isConnected() {
        return socket != null && socket.connected();
    }

    public void setOnEventListener(OnEventListener listener) {
        this.listener = listener;
    }
}