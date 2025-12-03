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
    private final AtomicBoolean connected = new AtomicBoolean(false);

    private Integer lastJoinedTable = null;

    private boolean attemptedFallback = false;
    private int fallbackRetryAttempts = 0;
    private static final int MAX_FALLBACK_RETRIES = 3;

    public interface OnEventListener {
        void onOrderCreated(JSONObject payload);
        void onOrderUpdated(JSONObject payload);
        void onConnect();
        void onDisconnect();
        void onError(Exception e);
        default void onTableUpdated(JSONObject payload) { /* no-op */ }
    }

    private OnEventListener listener;

    private SocketManager() { }

    public static synchronized SocketManager getInstance() {
        if (instance == null) instance = new SocketManager();
        return instance;
    }

    public synchronized void init(String baseUrl) {
        this.baseUrl = baseUrl;
        attemptedFallback = false;
        fallbackRetryAttempts = 0;
        Log.d(TAG, "init socket with baseUrl=" + baseUrl);
        closeExistingSocket();

        try {
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
        opts.transports = new String[] { "websocket", "polling" };
        opts.reconnection = true;
        opts.reconnectionAttempts = Integer.MAX_VALUE;
        opts.reconnectionDelay = 1000;
        opts.reconnectionDelayMax = 5000;
        opts.timeout = 30000;
        opts.forceNew = false;
        return opts;
    }

    private void closeExistingSocket() {
        if (socket != null) {
            try {
                socket.off();
                socket.close();
            } catch (Exception ignored) {}
            socket = null;
        }
    }

    private void setupListeners() {
        if (socket == null) return;

        socket.on(Socket.EVENT_CONNECT, args -> {
            connected.set(true);
            String sid = safeSocketId();
            Log.d(TAG, "socket connected, id=" + sid);
            attemptedFallback = false;
            fallbackRetryAttempts = 0;
            if (listener != null) listener.onConnect();
        });

        socket.on(Socket.EVENT_DISCONNECT, args -> {
            connected.set(false);
            String sid = safeSocketId();
            Log.d(TAG, "socket disconnected, id=" + sid);
            if (listener != null) listener.onDisconnect();
        });

        socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
            Throwable t = null;
            if (args != null && args.length > 0 && args[0] instanceof Throwable) t = (Throwable) args[0];

            String provided = args != null && args.length > 0 ? String.valueOf(args[0]) : "null";
            String sid = safeSocketId();
            String summary = "socket connect error: " + provided + " | socketId=" + sid + " | connected=" + (socket != null && socket.connected());

            if (t != null) {
                Log.w(TAG, summary, t);
                try { Log.w(TAG, "connect_error stacktrace: " + Log.getStackTraceString(t)); } catch (Exception ignored) {}
            } else {
                Log.w(TAG, summary);
            }

            if (listener != null) {
                try { listener.onError(new Exception(summary, t)); } catch (Exception ignored) {}
            }

            if (!attemptedFallback) {
                attemptedFallback = true;
                Log.i(TAG, "first connect error seen - scheduling a short retry");
                new Handler(Looper.getMainLooper()).postDelayed(this::attemptFallbackConnect, 700);
                return;
            }

            if (attemptedFallback && fallbackRetryAttempts < MAX_FALLBACK_RETRIES) {
                fallbackRetryAttempts++;
                long delay = 700L * fallbackRetryAttempts;
                Log.i(TAG, "scheduling retry #" + fallbackRetryAttempts + " after " + delay + "ms");
                new Handler(Looper.getMainLooper()).postDelayed(this::attemptFallbackConnect, delay);
            } else {
                Log.w(TAG, "connect_error: retries exhausted or not attempting further");
            }
        });

        socket.on("connect_timeout", args -> {
            String details = args != null && args.length > 0 ? String.valueOf(args[0]) : "connect_timeout";
            Log.w(TAG, "socket connect timeout: " + details + " | socketId=" + safeSocketId());
            if (listener != null) listener.onError(new Exception("connect_timeout: " + details));
        });

        socket.on("message", args -> {
            try {
                JSONObject p = parseArgsToJson(args);
                Log.d(TAG, "\"message\" event received: " + (p != null ? p.toString() : "null"));
            } catch (Exception e) {
                Log.w(TAG, "\"message\" parse failed", e);
            }
        });

        socket.on("order_created", args -> {
            try {
                JSONObject payload = parseArgsToJson(args);
                Log.d(TAG, "order_created received: " + (payload != null ? payload.toString() : "null"));
                if (listener != null) listener.onOrderCreated(payload);
            } catch (Exception e) {
                Log.w(TAG, "order_created handle failed", e);
            }
        });

        socket.on("order_updated", args -> {
            try {
                JSONObject payload = parseArgsToJson(args);
                Log.d(TAG, "order_updated received: " + (payload != null ? payload.toString() : "null"));
                if (listener != null) listener.onOrderUpdated(payload);
            } catch (Exception e) {
                Log.w(TAG, "order_updated handle failed", e);
            }
        });

        socket.on("order", args -> {
            try {
                JSONObject payload = parseArgsToJson(args);
                Log.d(TAG, "order (generic) received: " + (payload != null ? payload.toString() : "null"));
                if (listener != null) listener.onOrderUpdated(payload);
            } catch (Exception e) {
                Log.w(TAG, "order (generic) handle failed", e);
            }
        });

        socket.on("table_reserved", args -> handleTableEvent("table_reserved", args));
        socket.on("table_auto_released", args -> handleTableEvent("table_auto_released", args));
        socket.on("table_status_changed", args -> handleTableEvent("table_status_changed", args));
    }

    private void handleTableEvent(String name, Object[] args) {
        try {
            JSONObject payload = parseArgsToJson(args);
            if (payload == null) payload = new JSONObject();
            try { payload.put("eventName", name); } catch (JSONException ignored) {}
            Log.d(TAG, name + " received: " + (payload != null ? payload.toString() : "null") + " | socketId=" + safeSocketId());
            if (listener != null) listener.onTableUpdated(payload);
        } catch (Exception e) {
            Log.w(TAG, name + " handle failed", e);
        }
    }

    private void attemptFallbackConnect() {
        try {
            Log.i(TAG, "attemptFallbackConnect() - retrying connect with default transports");
            closeExistingSocket();
            IO.Options opts = buildDefaultOptions();
            socket = IO.socket(baseUrl, opts);
            setupListeners();
            socket.connect();
            Log.i(TAG, "attemptFallbackConnect: connect() called, socketId=" + safeSocketId());
        } catch (Exception e) {
            Log.w(TAG, "fallback connect attempt failed", e);
            if (listener != null) listener.onError(e);
        }
    }

    private JSONObject parseArgsToJson(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) return null;
        Object a0 = args[0];
        try {
            if (a0 instanceof JSONObject) return (JSONObject) a0;
            if (a0 instanceof JSONArray) {
                JSONObject wrapper = new JSONObject();
                wrapper.put("items", (JSONArray) a0);
                return wrapper;
            }
            if (a0 instanceof String) {
                String s = ((String) a0).trim();
                if (s.startsWith("{")) return new JSONObject(s);
                if (s.startsWith("[")) {
                    JSONArray arr = new JSONArray(s);
                    JSONObject wrapper = new JSONObject();
                    wrapper.put("items", arr);
                    return wrapper;
                }
                JSONObject wrapper = new JSONObject();
                wrapper.put("raw", s);
                return wrapper;
            }
            String s = String.valueOf(a0);
            if (s != null) {
                s = s.trim();
                if (s.startsWith("{")) return new JSONObject(s);
                if (s.startsWith("[")) {
                    JSONArray arr = new JSONArray(s);
                    JSONObject wrapper = new JSONObject();
                    wrapper.put("items", arr);
                    return wrapper;
                }
                JSONObject wrapper = new JSONObject();
                wrapper.put("raw", s);
                return wrapper;
            }
        } catch (JSONException je) {
            Log.w(TAG, "parseArgsToJson JSONException", je);
        } catch (Exception e) {
            Log.w(TAG, "parseArgsToJson exception", e);
        }
        return null;
    }

    public synchronized void connect() {
        Log.d(TAG, "connect() called, socket=" + socket + ", baseUrl=" + baseUrl + ", connected=" + connected.get());
        if (socket == null && baseUrl != null) init(baseUrl);
        if (socket != null && !socket.connected()) {
            try {
                socket.connect();
                Log.d(TAG, "socket.connect() called, id=" + safeSocketId());
            } catch (Exception e) {
                Log.w(TAG, "socket.connect() threw", e);
                if (listener != null) listener.onError(e);
            }
        }
    }

    public synchronized void disconnect() {
        if (socket != null && socket.connected()) {
            try {
                socket.disconnect();
            } catch (Exception e) {
                Log.w(TAG, "socket.disconnect() threw", e);
            }
        }
    }

    public boolean isConnected() {
        return socket != null && socket.connected();
    }

    public void setOnEventListener(OnEventListener listener) {
        this.listener = listener;
    }

    public void emitEvent(String event, Object payload) {
        try {
            if (socket != null) {
                socket.emit(event, payload);
                Log.d(TAG, "emitEvent: event=" + event + " payload=" + String.valueOf(payload));
            } else {
                Log.w(TAG, "emitEvent: socket is null, cannot emit " + event);
            }
        } catch (Exception e) {
            Log.w(TAG, "emitEvent failed: " + e.getMessage(), e);
        }
    }

    public void joinTable(int tableNumber) {
        try {
            lastJoinedTable = tableNumber;
            if (socket == null) {
                Log.w(TAG, "joinTable: socket is null");
                return;
            }
            socket.emit("join_table", tableNumber);
            socket.emit("join", tableNumber);
            socket.emit("subscribeOrder", tableNumber);
            Log.d(TAG, "joinTable: emitted join events for table=" + tableNumber + " | socketId=" + safeSocketId());
        } catch (Exception e) {
            Log.w(TAG, "joinTable failed", e);
        }
    }

    public void joinTable() {
        if (lastJoinedTable == null) {
            Log.w(TAG, "joinTable(): no table set previously; nothing to do");
            return;
        }
        joinTable(lastJoinedTable);
    }

    private String safeSocketId() {
        try {
            if (socket == null) return "null";
            String id = socket.id();
            return id != null ? id : "null";
        } catch (Throwable ignored) {
            try {
                if (socket == null) return "null";
                Method m = socket.getClass().getMethod("id");
                Object r = m.invoke(socket);
                return r != null ? String.valueOf(r) : "null";
            } catch (Throwable t) {
                return "unknown";
            }
        }
    }

    private Throwable getRootCause(Throwable t) {
        if (t == null) return null;
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root;
    }
}