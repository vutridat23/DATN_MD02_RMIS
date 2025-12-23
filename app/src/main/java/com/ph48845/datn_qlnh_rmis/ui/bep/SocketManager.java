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
    private int websocketErrorCount = 0;
    private static final int MAX_WEBSOCKET_ERRORS = 2; // Sau 2 lỗi websocket, chuyển sang polling
    private static boolean usePollingOnly = false; // Flag để nhớ rằng websocket không hoạt động
    private int pollingErrorCount = 0;
    private static final int MAX_POLLING_ERRORS = 3; // Sau 3 lỗi polling, dừng retry

    public boolean isInitialized() {
        boolean initialized = false;
        return initialized;
    }

    public boolean isConnecting() {
        boolean connecting = false;
        return connecting;
    }

    public boolean hasListener() {
        boolean hasListener = false;
        return hasListener;
    }


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
            
            // ✅ FIX: Chỉ dùng polling để tránh websocket error
            // Polling ổn định hơn và ít bị lỗi hơn websocket trong môi trường Android
            opts.transports = new String[]{"polling"}; // Chỉ dùng polling
            usePollingOnly = true; // Đánh dấu để đảm bảo luôn dùng polling
            Log.d(TAG, "Using polling transport only (more stable than websocket)");
            
            opts.reconnection = true;
            opts.reconnectionAttempts = 5; // Giảm số lần retry để tránh spam
            opts.reconnectionDelay = 3000; // Tăng delay giữa các lần retry
            opts.timeout = 20000;
            opts.forceNew = false; // Không force new connection mỗi lần

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
            // Reset error count khi connect thành công
            websocketErrorCount = 0;
            pollingErrorCount = 0;
            Log.d(TAG, "✅ socket connected via polling");
            if (listener != null) listener.onConnect();
        });

        socket.on(Socket.EVENT_DISCONNECT, args -> {
            connected.set(false);
            Log.w(TAG, "⚠️ socket disconnected");
            if (listener != null) listener.onDisconnect();
        });

        socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
            String errorMsg = args != null && args.length > 0 ? String.valueOf(args[0]) : "null";
            Log.e(TAG, "❌ socket connect error: " + errorMsg);
            
            // Kiểm tra loại lỗi
            boolean isWebsocketError = errorMsg.contains("websocket") || errorMsg.contains("WebSocket");
            boolean isPollingError = errorMsg.contains("xhr poll") || errorMsg.contains("polling");
            boolean isNetworkError = errorMsg.contains("EngineIOException") && !isWebsocketError && !isPollingError;
            
            // Chỉ xử lý websocket error, không xử lý polling error (vì đã dùng polling rồi)
            if (isWebsocketError) {
                websocketErrorCount++;
                Log.w(TAG, "WebSocket error detected! Count: " + websocketErrorCount + "/" + MAX_WEBSOCKET_ERRORS);
                
                // Đánh dấu dùng polling only ngay từ lần đầu tiên detect websocket error
                if (!usePollingOnly) {
                    usePollingOnly = true;
                    Log.w(TAG, "Setting usePollingOnly=true, will use polling only from now on");
                    
                    // Reinit với polling only
                    if (socket != null && baseUrl != null) {
                        Log.w(TAG, "Reinitializing with polling only...");
                        try {
                            String savedBaseUrl = baseUrl;
                            socket.disconnect();
                            socket.off();
                            socket.close();
                            socket = null;
                            
                            init(savedBaseUrl);
                            connect();
                        } catch (Exception e) {
                            Log.e(TAG, "Error reinitializing socket: " + e.getMessage(), e);
                        }
                    }
                }
            } else if (isPollingError || isNetworkError) {
                // Đây là lỗi polling hoặc network - không phải websocket
                pollingErrorCount++;
                Log.e(TAG, "Polling/Network error (" + pollingErrorCount + "/" + MAX_POLLING_ERRORS + 
                      ") - server may be down or URL incorrect: " + errorMsg);
                Log.e(TAG, "Please check: 1) Server is running, 2) URL is correct: " + baseUrl);
                
                // Sau một số lần lỗi, dừng retry để tránh spam log
                if (pollingErrorCount >= MAX_POLLING_ERRORS) {
                    Log.e(TAG, "Too many polling errors. Stopping retry. Please check server connection.");
                    // Disable reconnection để tránh spam
                    if (socket != null) {
                        try {
                            socket.io().reconnection(false);
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                }
            }
            
            if (listener != null) listener.onError(new Exception("connect_error: " + errorMsg));
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
