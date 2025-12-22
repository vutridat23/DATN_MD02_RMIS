package com.ph48845.datn_qlnh_rmis.ui.phucvu.socket;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.Method;

import io.socket.client.IO;
import io.socket.client.Socket;

/**
 * SocketManager: use default transports (websocket + polling) by default to be robust in
 * environments where websocket is blocked. Keeps detailed logging for connect errors.
 *
 * Note: when emitting table events to listener, we now inject "eventName" into payload
 * so clients can distinguish between table_reserved / table_auto_released / table_status_changed.
 */
public class SocketManager {

    private static final String TAG = "SocketManager";
    private static SocketManager instance;

    private Socket socket;
    private String baseUrl;
    private volatile boolean initialized = false;

    private Integer lastJoinedTable = null;

    public interface OnEventListener {
        default void onOrderCreated(JSONObject payload) {}
        default void onOrderUpdated(JSONObject payload) {}
        default void onConnect() {}
        default void onDisconnect() {}
        default void onError(Exception e) {}
        default void onTableUpdated(JSONObject payload) {}
        default void onCheckItemsRequest(JSONObject payload) {}
    }
    public boolean isConnected() {
        return socket != null && socket.connected();
    }

    // multi listeners
    private final CopyOnWriteArrayList<OnEventListener> listeners =
            new CopyOnWriteArrayList<>();

    // backward compatible single listener
    private volatile OnEventListener singleListener = null;

    private SocketManager() {}

    public static synchronized SocketManager getInstance() {
        if (instance == null) instance = new SocketManager();
        return instance;
    }

    // =====================================================
    // INIT
    // =====================================================
    public synchronized void init(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            Log.w(TAG, "init: baseUrl empty");
            return;
        }

        if (initialized && baseUrl.equals(this.baseUrl)) {
            Log.d(TAG, "init skipped (already initialized)");
            return;
        }

        this.baseUrl = baseUrl;
        initialized = true;

        closeExistingSocket();

        try {
            IO.Options opts = new IO.Options();
            opts.transports = new String[]{"websocket", "polling"};
            opts.reconnection = true;
            opts.reconnectionAttempts = Integer.MAX_VALUE;
            opts.reconnectionDelay = 1000;
            opts.reconnectionDelayMax = 5000;
            opts.timeout = 30000;
            opts.forceNew = false;

            socket = IO.socket(baseUrl, opts);
            setupListeners();

            Log.i(TAG, "Socket initialized: " + baseUrl);

        } catch (URISyntaxException e) {
            Log.e(TAG, "init socket failed", e);
            dispatchError(e);
        }
            IO.Options opts = buildDefaultOptions();
            socket = IO.socket(baseUrl, opts);
            setupListeners();
        } catch (URISyntaxException e) {
            Log.e(TAG, "init socket failed (URISyntaxException): " + e.getMessage(), e);
            if (listener != null) listener.onError(e);
        } catch (Exception e) {
            Log.e(TAG, "init socket failed (unexpected): " + e.getMessage(), e);
            if (listener != null) listener.onError(e);
        }
    }

    private IO.Options buildDefaultOptions() {
        IO.Options opts = new IO.Options();
        // ✅ FIX: Chỉ dùng polling để tránh websocket error
        // Polling ổn định hơn và ít bị lỗi hơn websocket trong môi trường Android
        opts.transports = new String[] { "polling" }; // Chỉ dùng polling
        Log.d(TAG, "Using polling transport only (more stable than websocket)");
        opts.reconnection = true;
        opts.reconnectionAttempts = 5; // Giảm số lần retry
        opts.reconnectionDelay = 3000; // Tăng delay
        opts.reconnectionDelayMax = 5000;
        opts.timeout = 30000;
        opts.forceNew = false;
        return opts;
    }

    /**
     * ❗ FIX DUY NHẤT:
     * ❌ KHÔNG socket.off()
     */
    private void closeExistingSocket() {
        if (socket != null) {
            try {
                socket.disconnect();
            } catch (Exception ignored) {}
            socket = null;
        }
    }

    private void setupListeners() {
        if (socket == null) return;

        socket.on(Socket.EVENT_CONNECT, args -> {
            Log.d(TAG, "socket connected, id=" + safeSocketId());
            if (lastJoinedTable != null) joinTable(lastJoinedTable);
            dispatchOnConnect();
        });

        socket.on(Socket.EVENT_DISCONNECT, args -> {
            Log.d(TAG, "socket disconnected");
            dispatchOnDisconnect();
        });

        socket.on("order_created", args -> {
            JSONObject payload = parseArgsToJson(args);
            Log.d(TAG, "order_created: " + payload);
            for (OnEventListener l : listeners) l.onOrderCreated(payload);
            if (singleListener != null) singleListener.onOrderCreated(payload);
        });

        socket.on("order_updated", args -> {
            JSONObject payload = parseArgsToJson(args);
            Log.d(TAG, "order_updated: " + payload);
            for (OnEventListener l : listeners) l.onOrderUpdated(payload);
            if (singleListener != null) singleListener.onOrderUpdated(payload);
        });

        socket.on("table_updated", args -> {
            JSONObject payload = parseArgsToJson(args);
            for (OnEventListener l : listeners) l.onTableUpdated(payload);
            if (singleListener != null) singleListener.onTableUpdated(payload);
        });

        socket.on("table_auto_released", args -> {
            JSONObject payload = parseArgsToJson(args);
            try { payload.put("eventName", "table_auto_released"); } catch (Exception ignored) {}
            for (OnEventListener l : listeners) l.onTableUpdated(payload);
            if (singleListener != null) singleListener.onTableUpdated(payload);
        });

        socket.on("check_items_request", args -> {
            JSONObject payload = parseArgsToJson(args);
            for (OnEventListener l : listeners) l.onCheckItemsRequest(payload);
            if (singleListener != null) singleListener.onCheckItemsRequest(payload);
        });
    }

    // =====================================================
    // PARSE
    // =====================================================
    private JSONObject parseArgsToJson(Object[] args) {
        try {
            if (args == null || args.length == 0 || args[0] == null) return null;
            Object o = args[0];
            if (o instanceof JSONObject) return (JSONObject) o;
            if (o instanceof JSONArray) {
                JSONObject j = new JSONObject();
                j.put("items", o);
                return j;
            }
            return new JSONObject(String.valueOf(o));
        } catch (Exception e) {
            Log.w(TAG, "parseArgsToJson error", e);
            return null;
        }
    }

    // =====================================================
    // CONNECT / DISCONNECT
    // =====================================================
    public synchronized void connect() {
        if (!initialized) return;
        if (socket == null) init(baseUrl);
        if (socket != null && !socket.connected()) {
            socket.connect();
            Log.d(TAG, "socket.connect()");
        }
    }

    public synchronized void disconnect() {
        if (socket != null && socket.connected()) {
            socket.disconnect();
            Log.d(TAG, "socket.disconnect()");
        }
    }

    // =====================================================
    // LISTENER MANAGEMENT
    // =====================================================
    public void registerListener(OnEventListener l) {
        if (l != null) listeners.addIfAbsent(l);
    }

    public void unregisterListener(OnEventListener l) {
        if (l != null) listeners.remove(l);
    }

    public void setOnEventListener(OnEventListener l) {
        singleListener = l;
    }

    // =====================================================
    // JOIN TABLE
    // =====================================================
    public void joinTable(int tableNumber) {
        lastJoinedTable = tableNumber;
        if (socket != null) {
            socket.emit("join_table", tableNumber);
            socket.emit("join", tableNumber);
            socket.emit("subscribeOrder", tableNumber);
            Log.d(TAG, "joinTable " + tableNumber);
        }
    }

    // =====================================================
    // EMIT EVENT (GIỮ ĐỂ KHÔNG LỖI CHỨC NĂNG KHÁC)
    // =====================================================
    public void emitEvent(String event, String payload) {
        try {
            if (socket != null) {
                socket.emit(event, payload);
                Log.d(TAG, "emitEvent: " + event + " | " + payload);
            }
        } catch (Exception e) {
            Log.w(TAG, "emitEvent error", e);
        }
    }

    public void emitEvent(String event, JSONObject payload) {
        try {
            if (socket != null) {
                socket.emit(event, payload);
                Log.d(TAG, "emitEvent: " + event + " | " + payload);
            }
        } catch (Exception e) {
            Log.w(TAG, "emitEvent error", e);
        }
    }

    // =====================================================
    // DISPATCH
    // =====================================================
    private void dispatchOnConnect() {
        for (OnEventListener l : listeners) l.onConnect();
        if (singleListener != null) singleListener.onConnect();
    }

    private void dispatchOnDisconnect() {
        for (OnEventListener l : listeners) l.onDisconnect();
        if (singleListener != null) singleListener.onDisconnect();
    }

    private void dispatchError(Exception e) {
        for (OnEventListener l : listeners) l.onError(e);
        if (singleListener != null) singleListener.onError(e);
    }

    private String safeSocketId() {
        try {
            return socket != null ? socket.id() : "null";
        } catch (Throwable t) {
            return "unknown";
        }
    }
}
