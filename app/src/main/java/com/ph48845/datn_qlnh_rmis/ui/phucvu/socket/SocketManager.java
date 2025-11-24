package com.ph48845.datn_qlnh_rmis.ui.phucvu.socket;




import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.socket.client.IO;
import io.socket.client.Socket;

/**
 * Robust Socket.IO manager: parses String/JSONObject/JSONArray payloads and provides emit/join helpers.
 */
public class SocketManager {

    private static final String TAG = "SocketManager";
    private static SocketManager instance;
    private Socket socket;
    private String baseUrl;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    // remember last requested join table (optional convenience)
    private Integer lastJoinedTable = null;

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
        Log.d(TAG, "init socket with baseUrl=" + baseUrl);
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
            Log.w(TAG, "socket connect error: " + (args != null && args.length > 0 ? args[0] : "null"));
            if (listener != null) listener.onError(new Exception(String.valueOf(args != null && args.length > 0 ? args[0] : "connect_error")));
        });

        // Robust handlers for common order events
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

        // Generic names some servers might use
        socket.on("order", args -> {
            try {
                JSONObject payload = parseArgsToJson(args);
                Log.d(TAG, "order (generic) received: " + (payload != null ? payload.toString() : "null"));
                if (listener != null) listener.onOrderUpdated(payload);
            } catch (Exception e) {
                Log.w(TAG, "order (generic) handle failed", e);
            }
        });

        // NOTE: Socket.EVENT_MESSAGE is package-private in some socket.io-client versions.
        // Use the literal event name "message" instead to avoid "not public" access errors.
        socket.on("message", args -> {
            try {
                JSONObject p = parseArgsToJson(args);
                Log.d(TAG, "\"message\" event received: " + (p != null ? p.toString() : "null"));
            } catch (Exception e) {
                Log.w(TAG, "\"message\" parse failed", e);
            }
        });
    }

    /**
     * Try to convert socket.io args[] into a JSONObject in a forgiving way:
     * - If args[0] instanceof JSONObject => return it
     * - If args[0] instanceof JSONArray => wrap in { "items": [...] }
     * - If args[0] instanceof String => try to parse as JSON (object or array)
     * - Otherwise -> null
     */
    private JSONObject parseArgsToJson(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) return null;
        Object a0 = args[0];
        try {
            if (a0 instanceof JSONObject) {
                return (JSONObject) a0;
            } else if (a0 instanceof JSONArray) {
                JSONObject wrapper = new JSONObject();
                wrapper.put("items", (JSONArray) a0);
                return wrapper;
            } else if (a0 instanceof String) {
                String s = (String) a0;
                s = s.trim();
                if (s.startsWith("{")) {
                    return new JSONObject(s);
                } else if (s.startsWith("[")) {
                    JSONArray arr = new JSONArray(s);
                    JSONObject wrapper = new JSONObject();
                    wrapper.put("items", arr);
                    return wrapper;
                } else {
                    JSONObject wrapper = new JSONObject();
                    wrapper.put("raw", s);
                    return wrapper;
                }
            } else {
                // Could be a Map (Gson LinkedTreeMap) or POJO; try toString -> parse
                try {
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
                    }
                } catch (Exception ignored) {}
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
            socket.connect();
            Log.d(TAG, "socket.connect() called");
        }
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

    /**
     * Generic emit helper - you can emit primitives, String, JSONObject, int, etc.
     */
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

    /**
     * Convenience: request to join a table room.
     * Server may expect different event names; this method emits a few common ones.
     * Adjust according to your server implementation.
     */
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
            Log.d(TAG, "joinTable: emitted join events for table=" + tableNumber);
        } catch (Exception e) {
            Log.w(TAG, "joinTable failed", e);
        }
    }

    /**
     * Overload: use lastJoinedTable if available (convenience).
     */
    public void joinTable() {
        if (lastJoinedTable == null) {
            Log.w(TAG, "joinTable(): no table set previously; nothing to do");
            return;
        }
        joinTable(lastJoinedTable);
    }
}