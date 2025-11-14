package com.ph48845.datn_qlnh_rmis.data.repository;

import android.util.Log;

import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.OrderApi;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrderRepository {

    private static final String TAG = "OrderRepository";

    private final OrderApi api;

    // Constructor inject cho ViewModel tự tạo Retrofit
    public OrderRepository(OrderApi api) {
        this.api = api;
    }

    // Constructor cũ: dùng singleton RetrofitClient
    public OrderRepository() {
        this.api = RetrofitClient.getInstance().getOrderApi();
    }

    // ===== Callback interface giữ nguyên =====
    public interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    // ===== Các method dạng Call cho BepViewModel =====
    public Call<List<Order>> getAllOrders() {
        return api.getAllOrders();
    }

    public Call<Void> updateOrderItemStatus(String orderId, String itemId, String newStatus) {
        return api.updateOrderItemStatus(orderId, itemId, new OrderApi.StatusUpdate(newStatus));
    }

    // ===== Các wrapper callback nguyên gốc =====
    public void createOrder(final Order order, final RepositoryCallback<Order> callback) {
        if (order == null) {
            callback.onError("Order is null");
            return;
        }
        api.createOrder(order).enqueue(new Callback<Order>() {
            @Override
            public void onResponse(Call<Order> call, Response<Order> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(buildHttpError("createOrder", response));
                }
            }
            @Override
            public void onFailure(Call<Order> call, Throwable t) {
                callback.onError(logFailure("createOrder onFailure", t));
            }
        });
    }

    /**
     * Lấy các order thuộc bàn (tableNumber). Nếu status == null sẽ lấy tất cả.
     */
    public void getOrdersByTableNumber(Integer tableNumber, String status, final RepositoryCallback<List<Order>> callback) {
        api.getOrdersByTable(tableNumber, status).enqueue(new Callback<ApiResponse<List<Order>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Order>>> call, Response<ApiResponse<List<Order>>> response) {
                if (response.isSuccessful()) {
                    ApiResponse<List<Order>> apiResponse = response.body();
                    if (apiResponse != null) {
                        List<Order> list = apiResponse.getData();
                        if (list != null) {
                            callback.onSuccess(list);
                        } else {
                            String msg = "Server returned empty order list";
                            if (apiResponse.getMessage() != null) msg += ": " + apiResponse.getMessage();
                            callback.onError(msg);
                        }
                    } else {
                        callback.onError("Response body is null");
                    }
                } else {
                    callback.onError(buildHttpError("getOrdersByTableNumber", response));
                }
            }
            @Override
            public void onFailure(Call<ApiResponse<List<Order>>> call, Throwable t) {
                callback.onError(logFailure("getOrdersByTableNumber onFailure", t));
            }
        });
    }

    public void deleteOrder(String orderId, final RepositoryCallback<Void> callback) {
        if (orderId == null || orderId.trim().isEmpty()) {
            callback.onError("Invalid order id");
            return;
        }
        api.deleteOrder(orderId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(null);
                } else {
                    callback.onError(buildHttpError("deleteOrder", response));
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onError(logFailure("deleteOrder onFailure", t));
            }
        });
    }

    public void updateOrder(String orderId, Map<String, Object> updates, final RepositoryCallback<Order> callback) {
        if (orderId == null || orderId.trim().isEmpty()) {
            callback.onError("Invalid order id");
            return;
        }
        api.updateOrder(orderId, updates).enqueue(new Callback<Order>() {
            @Override
            public void onResponse(Call<Order> call, Response<Order> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError(buildHttpError("updateOrder", response));
                }
            }
            @Override
            public void onFailure(Call<Order> call, Throwable t) {
                callback.onError(logFailure("updateOrder onFailure", t));
            }
        });
    }

    /**
     * Move all orders from one tableNumber to another (best-effort).
     * Giữ logic phòng thủ: chỉ di chuyển order đúng bàn nguồn.
     */
    public void moveOrdersForTable(int fromTableNumber, int toTableNumber, final RepositoryCallback<Void> callback) {
        if (fromTableNumber <= 0) {
            callback.onError("Invalid fromTableNumber: " + fromTableNumber);
            return;
        }
        if (toTableNumber <= 0) {
            callback.onError("Invalid toTableNumber: " + toTableNumber);
            return;
        }

        getOrdersByTableNumber(fromTableNumber, null, new RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orders) {
                if (orders == null || orders.isEmpty()) {
                    Log.d(TAG, "moveOrdersForTable: no orders found for table " + fromTableNumber);
                    callback.onSuccess(null);
                    return;
                }

                List<Order> toMove = new ArrayList<>();
                for (Order o : orders) {
                    if (o != null) {
                        try {
                            if (o.getTableNumber() == fromTableNumber) toMove.add(o);
                        } catch (Exception ignored) {}
                    }
                }

                if (toMove.isEmpty()) {
                    Log.d(TAG, "moveOrdersForTable: after filtering none belong to table " + fromTableNumber);
                    callback.onSuccess(null);
                    return;
                }

                final int total = toMove.size();
                final int[] finished = {0};
                final int[] errors = {0};

                Log.d(TAG, "moveOrdersForTable: will move " + total + " orders from table "
                        + fromTableNumber + " -> " + toTableNumber);

                for (Order o : toMove) {
                    if (o == null || o.getId() == null) {
                        finished[0]++;
                        checkMoveFinished(finished, errors, total, callback);
                        continue;
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("tableNumber", toTableNumber);

                    updateOrder(o.getId(), updates, new RepositoryCallback<Order>() {
                        @Override
                        public void onSuccess(Order result) {
                            finished[0]++;
                            Log.d(TAG, "Moved order " + o.getId());
                            checkMoveFinished(finished, errors, total, callback);
                        }

                        @Override
                        public void onError(String message) {
                            errors[0]++;
                            finished[0]++;
                            Log.w(TAG, "Failed move order " + o.getId() + ": " + message);
                            checkMoveFinished(finished, errors, total, callback);
                        }
                    });
                }
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Cannot fetch orders for table " + fromTableNumber + ": " + message);
                callback.onError(message);
            }
        });
    }

    // ===== Helpers =====
    private void checkMoveFinished(int[] finished, int[] errors, int total, RepositoryCallback<Void> callback) {
        if (finished[0] >= total) {
            if (errors[0] == 0) callback.onSuccess(null);
            else callback.onError("Some order updates failed");
        }
    }

    private String buildHttpError(String action, Response<?> response) {
        String msg = "HTTP " + response.code() + " - " + response.message();
        try {
            if (response.errorBody() != null) msg += " - " + response.errorBody().string();
        } catch (IOException ignored) {}
        Log.e(TAG, action + " error: " + msg);
        return msg;
    }

    private String logFailure(String logMsg, Throwable t) {
        Log.e(TAG, logMsg, t);
        return t.getMessage() != null ? t.getMessage() : "Network error";
    }
}