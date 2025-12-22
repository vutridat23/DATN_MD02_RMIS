package com.ph48845.datn_qlnh_rmis.ui.bep;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;

import io.socket.client.IO;
import io.socket.client.Socket;

/**
 * SocketManager: quản lý Socket.IO kết nối chung cho cả Bếp và Phục vụ.
 * FIXED:
 *  - Không emit khi socket chưa connect (emitSafely)
 *  - Cho phép fallback polling (tránh websocket error)
 *  - Đảm bảo event không bị mất sau reconnect
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

    private SocketManager() {}

    public static synchronized SocketManager getInstance() {
        if (instance == null) instance = new SocketManager();
        return instance;
    }

    /**
     * Initialize socket with base URL (VD: http://192.168.1.157:3000)
     */
    public synchronized void init(String baseUrl) {
        this.baseUrl = baseUrl;

        if (socket != null) {
            try {
                socket.off();
                socket.close();
            } catch (Exception ignored) {}
            socket = null;
        }

        try {
            IO.Options opts = new IO.Options();
            // ✅ FIX: cho phép fallback polling
            opts.transports = new String[]{"websocket", "polling"};
            opts.reconnection = true;
            opts.reconnectionAttempts = Integer.MAX_VALUE;
            opts.reconnectionDelay = 2000;
            opts.timeout = 20000;
            opts.forceNew = true;

            socket = IO.socket(baseUrl, opts);
            setupListeners();

        } catch (URISyntaxException e) {
            Log.e(TAG, "init socket failed", e);
            if (listener != null) listener.onError(e);
        }
    }

    private void setupListeners() {
        if (socket == null) return;

        socket.on(Socket.EVENT_CONNECT, args -> {
            connected.set(true);
            Log.d(TAG, "✅ socket connected");
            if (listener != null) listener.onConnect();
        });

        socket.on(Socket.EVENT_DISCONNECT, args -> {
            connected.set(false);
            Log.w(TAG, "⚠️ socket disconnected");
            if (listener != null) listener.onDisconnect();
        });

        socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
            Log.e(TAG, "❌ socket connect error: " + (args != null && args.length > 0 ? args[0] : "null"));
            if (listener != null) listener.onError(new Exception("connect_error"));
        });

        socket.on("error", args -> {
            Log.e(TAG, "❌ socket error event: " + (args != null && args.length > 0 ? args[0] : "null"));
            if (listener != null) listener.onError(new Exception("socket_error"));
        });

        socket.on("order_created", args -> handleJsonEvent("order_created", args, true));
        socket.on("order_updated", args -> handleJsonEvent("order_updated", args, false));
    }

    private void handleJsonEvent(String event, Object[] args, boolean isCreated) {
        try {
            JSONObject payload = (args != null && args.length > 0 && args[0] instanceof JSONObject)
                    ? (JSONObject) args[0]
                    : (args != null && args.length > 0 ? new JSONObject(String.valueOf(args[0])) : null);

            Log.d(TAG, event + " received: " + payload);

            if (listener != null) {
                if (isCreated) listener.onOrderCreated(payload);
                else listener.onOrderUpdated(payload);
            }
        } catch (Exception e) {
            Log.e(TAG, event + " handle failed", e);
        }
    }

    public synchronized void connect() {
        if (socket == null && baseUrl != null) init(baseUrl);
        if (socket != null && !socket.connected()) {
            Log.d(TAG, "🔌 Attempting socket connect to " + baseUrl);
            socket.connect();
        }
    }

    public synchronized void disconnect() {
        if (socket != null) socket.disconnect();
    }

    public boolean isConnected() {
        return socket != null && socket.connected();
    }

    public void setOnEventListener(OnEventListener listener) {
        this.listener = listener;
    }

    // =========================
    // ✅ CORE FIX: emit an toàn
    // =========================
    private void emitSafely(String event, JSONObject payload) {
        if (socket == null) return;

        if (socket.connected()) {
            socket.emit(event, payload);
            Log.d(TAG, "📤 emit " + event + ": " + payload);
        } else {
            Log.w(TAG, "⏳ socket not connected, waiting to emit " + event);
            connect();

            socket.once(Socket.EVENT_CONNECT, args -> {
                socket.emit(event, payload);
                Log.d(TAG, "📤 emit " + event + " after connect: " + payload);
            });
        }
    }

    public void emitJoinRoom(String room) {
        if (room == null || room.trim().isEmpty()) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("room", room);
            emitSafely("join_room", payload);
        } catch (JSONException e) {
            Log.e(TAG, "emitJoinRoom error", e);
            if (listener != null) listener.onError(e);
        }
    }

    public void emitOrderStatusChanged(String orderId, String itemId, String status) {
        if (orderId == null || itemId == null || status == null) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("orderId", orderId);
            payload.put("itemId", itemId);
            payload.put("status", status);
            emitSafely("order_updated", payload);
        } catch (JSONException e) {
            Log.e(TAG, "emitOrderStatusChanged error", e);
            if (listener != null) listener.onError(e);
        }
    }

    /**
     * Gửi request kiểm tra bàn / kiểm tra món
     */
    public void emitCheckItemsRequest(int tableNumber, String[] orderIds) {
        if (tableNumber <= 0) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("tableNumber", tableNumber);

            if (orderIds != null && orderIds.length > 0) {
                org.json.JSONArray arr = new org.json.JSONArray();
                for (String id : orderIds) if (id != null && !id.trim().isEmpty()) arr.put(id);
                payload.put("orderIds", arr);
            }

            emitSafely("check_items_request", payload);

        } catch (JSONException e) {
            Log.e(TAG, "emitCheckItemsRequest error", e);
            if (listener != null) listener.onError(e);
        }
    }
}
