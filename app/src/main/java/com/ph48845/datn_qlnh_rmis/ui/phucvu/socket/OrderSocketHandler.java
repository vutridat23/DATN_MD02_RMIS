package com.ph48845.datn_qlnh_rmis.ui.phucvu.socket;



import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

public class OrderSocketHandler {

    private static final String TAG = "OrderSocketHandler";

    public interface Listener {
        void onItemStatusMatched(String candidateId, String status);
        void onNoMatchReload();
        void onSocketConnected();
        void onSocketDisconnected();
    }

    private final Context ctx;
    private final String socketUrl;
    private final int tableNumber;
    private final Listener listener;
    private SocketManager socketManager;

    public OrderSocketHandler(Context ctx, String socketUrl, int tableNumber, Listener listener) {
        this.ctx = ctx;
        this.socketUrl = socketUrl;
        this.tableNumber = tableNumber;
        this.listener = listener;
    }

    public void initAndConnect() {
        try {
            socketManager = SocketManager.getInstance();
            socketManager.init(socketUrl);
            socketManager.setOnEventListener(new SocketManager.OnEventListener() {
                @Override
                public void onOrderCreated(JSONObject payload) {
                    handleOrderSocketPayload(payload);
                }

                @Override
                public void onOrderUpdated(JSONObject payload) {
                    handleOrderSocketPayload(payload);
                }

                @Override
                public void onConnect() {
                    Log.d(TAG, "socket connected");
                    try {
                        socketManager.joinTable(tableNumber);
                    } catch (Exception e) { Log.w(TAG, "joinTable failed", e); }
                    if (listener != null) listener.onSocketConnected();
                }

                @Override
                public void onDisconnect() {
                    Log.d(TAG, "socket disconnected");
                    if (listener != null) listener.onSocketDisconnected();
                }

                @Override
                public void onError(Exception e) {
                    Log.w(TAG, "socket error: " + (e != null ? e.getMessage() : "null"), e);
                }
            });
            socketManager.connect();
        } catch (Exception e) {
            Log.w(TAG, "initAndConnect failed", e);
        }
    }

    public void connect() {
        try { if (socketManager != null) socketManager.connect(); } catch (Exception ignored) {}
    }

    public void disconnect() {
        try { if (socketManager != null) socketManager.disconnect(); } catch (Exception ignored) {}
    }

    /**
     * Parses payload and tries to find a candidateId / status to notify listener.
     */
    private void handleOrderSocketPayload(JSONObject payload) {
        if (payload == null) return;
        Log.d(TAG, "handleOrderSocketPayload raw: " + payload.toString());
        try {
            JSONObject orderJson = payload;
            if (payload.has("order") && payload.opt("order") instanceof JSONObject) orderJson = payload.optJSONObject("order");

            int payloadTableNumber = -1;
            if (orderJson.has("tableNumber")) payloadTableNumber = orderJson.optInt("tableNumber", -1);
            else if (orderJson.has("table")) payloadTableNumber = orderJson.optInt("table", -1);
            else if (payload.has("tableNumber")) payloadTableNumber = payload.optInt("tableNumber", -1);

            JSONArray itemsArr = orderJson.optJSONArray("items");
            if (itemsArr == null) {
                Log.d(TAG, "no items array");
                return;
            }

            if (payloadTableNumber != -1 && payloadTableNumber != tableNumber) {
                Log.d(TAG, "payload tableNumber mismatch -> ignore");
                return;
            }

            for (int i = 0; i < itemsArr.length(); i++) {
                JSONObject it = itemsArr.optJSONObject(i);
                if (it == null) continue;

                String menuId = null;
                if (it.has("menuItem")) {
                    Object mi = it.opt("menuItem");
                    if (mi instanceof JSONObject) {
                        menuId = ((JSONObject) mi).optString("_id", null);
                        if ((menuId == null || menuId.isEmpty())) menuId = ((JSONObject) mi).optString("id", null);
                    } else if (mi instanceof String) {
                        menuId = (String) mi;
                    }
                }
                if (menuId == null || menuId.isEmpty()) {
                    menuId = it.optString("menuItemId", it.optString("menuItemRaw", null));
                }

                String imageUrl = it.optString("imageUrl", it.optString("image", null));
                String menuName = it.optString("menuItemName", it.optString("name", null));
                String status = it.optString("status", it.optString("state", ""));

                final String candidateId = (menuId != null && !menuId.isEmpty()) ? menuId
                        : (imageUrl != null && !imageUrl.isEmpty()) ? imageUrl
                        : (menuName != null && !menuName.isEmpty()) ? menuName : null;
                final String st = status != null ? status : "";

                Log.d(TAG, "item[" + i + "] menuId=" + menuId + " image=" + imageUrl + " name=" + menuName + " status=" + st);

                if (candidateId == null) {
                    Log.d(TAG, "skipping item with no id/name/image");
                    continue;
                }

                // Notify listener to try update adapter; if listener cannot match, it should reload
                if (listener != null) {
                    listener.onItemStatusMatched(candidateId, st);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "handleOrderSocketPayload failed", e);
            if (listener != null) listener.onNoMatchReload();
        }
    }
}