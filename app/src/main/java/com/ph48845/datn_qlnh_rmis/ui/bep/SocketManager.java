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
 * - Ép websocket-only để tránh "xhr poll error" trong nhiều môi trường.
 * - Dùng tên sự kiện dạng chuỗi cho các event không có constant trong client lib.
 */
public class SocketManager {

    private static final String TAG = "SocketManager";
    private static SocketManager instance;
    private Socket socket;
    private String baseUrl;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    public interface OnEventListener {
        void onOrderCreated(org.json.JSONObject payload);
        void onOrderUpdated(org.json.JSONObject payload);
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
            // FORCE websocket transport to avoid xhr poll issues in some networks
            opts.transports = new String[] { "websocket" };
            opts.reconnection = true;
            opts.reconnectionAttempts = Integer.MAX_VALUE;
            opts.timeout = 20000; // 20s
            opts.forceNew = true;

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

        // Một số phiên bản client không expose constant cho connect_timeout -> dùng string
        socket.on("connect_timeout", args -> {
            Log.w(TAG, "socket connect timeout");
            if (listener != null) listener.onError(new Exception("connect_timeout"));
        });

        // Một số phiên bản client không expose constant EVENT_ERROR -> dùng string "error"
        socket.on("error", args -> {
            Log.w(TAG, "socket error event: " + (args != null && args.length>0 ? args[0] : "null"));
            if (listener != null) listener.onError(new Exception(String.valueOf(args != null && args.length>0 ? args[0] : "socket_error")));
        });

        socket.on("order_created", args -> {
            try {
                org.json.JSONObject payload = (args != null && args.length > 0 && args[0] instanceof org.json.JSONObject)
                        ? (org.json.JSONObject) args[0]
                        : (args != null && args.length > 0 ? new org.json.JSONObject(String.valueOf(args[0])) : null);
                Log.d(TAG, "order_created received: " + (payload != null ? payload.toString() : "null"));
                if (listener != null) listener.onOrderCreated(payload);
            } catch (Exception e) {
                Log.w(TAG, "order_created handle failed", e);
            }
        });

        socket.on("order_updated", args -> {
            try {
                org.json.JSONObject payload = (args != null && args.length > 0 && args[0] instanceof org.json.JSONObject)
                        ? (org.json.JSONObject) args[0]
                        : (args != null && args.length > 0 ? new org.json.JSONObject(String.valueOf(args[0])) : null);
                Log.d(TAG, "order_updated received: " + (payload != null ? payload.toString() : "null"));
                if (listener != null) listener.onOrderUpdated(payload);
            } catch (Exception e) {
                Log.w(TAG, "order_updated handle failed", e);
            }
        });

        // general error event fallback (already handled above with "error")
    }

    public synchronized void connect() {
        if (socket == null && baseUrl != null) init(baseUrl);
        if (socket != null && !socket.connected()) {
            Log.d(TAG, "Attempting socket connect to " + baseUrl);
            socket.connect();
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

    public void emitJoinRoom(String room) {
        if (room == null || room.trim().isEmpty()) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("room", room);
            if (socket != null && socket.connected()) {
                socket.emit("join_room", payload);
                Log.d(TAG, "emit join_room: " + room);
            } else {
                connect();
                if (socket != null) {
                    socket.emit("join_room", payload);
                    Log.d(TAG, "emit join_room (after connect attempt): " + room);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "emitJoinRoom JSON error: " + e.getMessage(), e);
            if (listener != null) listener.onError(e);
        } catch (Exception e) {
            Log.e(TAG, "emitJoinRoom error: " + e.getMessage(), e);
            if (listener != null) listener.onError(e);
        }
    }

    public void emitOrderStatusChanged(String orderId, String itemId, String status) {
        if (orderId == null || orderId.trim().isEmpty() || itemId == null || itemId.trim().isEmpty() || status == null) {
            Log.w(TAG, "emitOrderStatusChanged: invalid params");
            return;
        }
        try {
            JSONObject payload = new JSONObject();
            payload.put("orderId", orderId);
            payload.put("itemId", itemId);
            payload.put("status", status);

            if (socket != null && socket.connected()) {
                socket.emit("order_updated", payload);
                Log.d(TAG, "emit order_updated: " + payload.toString());
            } else {
                Log.w(TAG, "socket not connected, attempting to connect and emit");
                connect();
                if (socket != null) {
                    socket.emit("order_updated", payload);
                    Log.d(TAG, "emit order_updated (after connect attempt): " + payload.toString());
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "emitOrderStatusChanged JSON error: " + e.getMessage(), e);
            if (listener != null) listener.onError(e);
        } catch (Exception e) {
            Log.e(TAG, "emitOrderStatusChanged error: " + e.getMessage(), e);
            if (listener != null) listener.onError(e);
        }
    }

    /**
     * Gửi request kiểm tra món ăn lên server
     * @param tableNumber Số bàn cần kiểm tra
     * @param orderIds Danh sách ID các order cần kiểm tra
     */
    public void emitCheckItemsRequest(int tableNumber, String[] orderIds) {
        if (tableNumber <= 0) {
            Log.w(TAG, "emitCheckItemsRequest: invalid tableNumber");
            return;
        }
        try {
            JSONObject payload = new JSONObject();
            payload.put("tableNumber", tableNumber);
            
            // Thêm danh sách orderIds vào payload
            if (orderIds != null && orderIds.length > 0) {
                org.json.JSONArray orderIdsArray = new org.json.JSONArray();
                for (String orderId : orderIds) {
                    if (orderId != null && !orderId.trim().isEmpty()) {
                        orderIdsArray.put(orderId);
                    }
                }
                payload.put("orderIds", orderIdsArray);
            }

            if (socket != null && socket.connected()) {
                socket.emit("check_items_request", payload);
                Log.d(TAG, "emit check_items_request: " + payload.toString());
            } else {
                Log.w(TAG, "socket not connected, attempting to connect and emit check_items_request");
                connect();
                if (socket != null) {
                    socket.emit("check_items_request", payload);
                    Log.d(TAG, "emit check_items_request (after connect attempt): " + payload.toString());
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "emitCheckItemsRequest JSON error: " + e.getMessage(), e);
            if (listener != null) listener.onError(e);
        } catch (Exception e) {
            Log.e(TAG, "emitCheckItemsRequest error: " + e.getMessage(), e);
            if (listener != null) listener.onError(e);
        }
    }
}