package com.ph48845.datn_qlnh_rmis;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class SocketManager {
    private static final String TAG = "SocketManager";
    private static Socket mSocket;

    // Listener interface để Activity/ViewModel nhận event new_order
    public interface OnNewOrderListener {
        void onNewOrder(JSONObject order);
    }

    private static OnNewOrderListener newOrderListener;

    public static synchronized void init(String serverUrl, final String restaurantId, final String stationId) {
        if (mSocket != null) return;
        try {
            IO.Options opts = new IO.Options();
            opts.forceNew = true;
            opts.reconnection = true;
            mSocket = IO.socket(serverUrl, opts);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            Log.e(TAG, "URI Syntax error: " + e.getMessage());
            return;
        }

        mSocket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                log("Socket connected: " + mSocket.id());
                // Join station room ngay khi connect
                try {
                    JSONObject join = new JSONObject();
                    join.put("restaurantId", restaurantId);
                    join.put("stationId", stationId);
                    mSocket.emit("join_station", join);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        mSocket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                log("Socket disconnected");
            }
        });

        // Nhận order mới
        mSocket.on("new_order", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                // chạy UI thread nếu cần cập nhật UI
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        Object payload = args != null && args.length > 0 ? args[0] : null;
                        log("new_order: " + String.valueOf(payload));
                        // Emit callback cho listener (Activity/ViewModel)
                        if (newOrderListener != null) {
                            if (payload instanceof JSONObject) {
                                newOrderListener.onNewOrder((JSONObject) payload);
                            } else {
                                // nếu payload là Map hoặc khác, cố gắng convert sang JSONObject
                                try {
                                    JSONObject json = new JSONObject(String.valueOf(payload));
                                    newOrderListener.onNewOrder(json);
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                }
                            }
                        }

                        // Nếu muốn auto-ack, bạn có thể giữ phần ack ở đây hoặc làm trong Activity
                        try {
                            JSONObject ack = new JSONObject();
                            if (payload instanceof JSONObject) {
                                JSONObject p = (JSONObject) payload;
                                ack.put("orderId", p.optString("orderId"));
                            }
                            ack.put("stationId", stationId);
                            ack.put("kitchenUserId", "kitchen_user_1");
                            mSocket.emit("ack_order", ack);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                });
            }
        });

        // Lắng nghe các event khác (cập nhật trạng thái,...)
        mSocket.on("order_updated", new Emitter.Listener() {
            @Override
            public void call(final Object... args) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        log("order_updated: " + (args != null && args.length > 0 ? args[0] : ""));
                        // Bạn có thể thêm callback khác nếu cần
                    }
                });
            }
        });

        mSocket.connect();
    }

    // Đăng ký / hủy listener
    public static void setOnNewOrderListener(OnNewOrderListener listener) {
        newOrderListener = listener;
    }

    public static void removeOnNewOrderListener() {
        newOrderListener = null;
    }

    // Gửi cập nhật trạng thái món từ bếp tới server
    public static void emitUpdateStatus(String orderId, String itemId, String status, String updatedBy) {
        if (mSocket == null) return;
        try {
            JSONObject payload = new JSONObject();
            payload.put("orderId", orderId);
            payload.put("itemId", itemId);
            payload.put("status", status);
            payload.put("updatedBy", updatedBy);
            mSocket.emit("update_status", payload);
            log("emit update_status: " + payload.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Tắt socket khi không cần
    public static void disconnect() {
        if (mSocket != null) {
            mSocket.disconnect();
            mSocket.off();
            mSocket = null;
            log("socket disconnected and cleared");
        }
        removeOnNewOrderListener();
    }

    private static void log(String s) {
        Log.d(TAG, s);
    }
}