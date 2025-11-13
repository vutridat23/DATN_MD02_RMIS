package com.ph48845.datn_qlnh_rmis.data.repository;

import android.util.Log;

import com.ph48845.datn_qlnh_rmis.data.model.Order;

import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import java.util.Map;

/**
 * OrderRepository: create order + fetch orders for a table + delete/update order + move orders
 *
 * Fix: moveOrdersForTable is defensive — it filters returned orders by the source tableNumber
 * so we don't accidentally move all orders if backend returned an unfiltered list.
 */
public class OrderRepository {

    private static final String TAG = "OrderRepository";

    public interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    public void createOrder(final Order order, final RepositoryCallback<Order> callback) {
        if (order == null) {
            callback.onError("Order is null");
            return;
        }

        Call<Order> call = RetrofitClient.getInstance().getApiService().createOrder(order);
        call.enqueue(new Callback<Order>() {
            @Override
            public void onResponse(Call<Order> call, Response<Order> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    String msg = "HTTP " + response.code() + " - " + response.message();
                    try {
                        if (response.errorBody() != null) msg += " - " + response.errorBody().string();
                    } catch (Exception ignored) {}
                    Log.e(TAG, "createOrder error: " + msg);
                    callback.onError(msg);
                }
            }

            @Override
            public void onFailure(Call<Order> call, Throwable t) {
                Log.e(TAG, "createOrder onFailure", t);
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    /**
     * Lấy các order thuộc bàn (tableNumber). Nếu status==null sẽ lấy tất cả.
     */
    public void getOrdersByTableNumber(Integer tableNumber, String status, final RepositoryCallback<List<Order>> callback) {
        Call<ApiResponse<List<Order>>> call = RetrofitClient.getInstance().getApiService().getOrdersByTable(tableNumber, status);
        call.enqueue(new Callback<ApiResponse<List<Order>>>() {
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
                    String err = "HTTP " + response.code() + " - " + response.message();
                    try {
                        if (response.errorBody() != null) err += " - " + response.errorBody().string();
                    } catch (IOException ignored) {}
                    Log.e(TAG, "getOrdersByTableNumber error: " + err);
                    callback.onError(err);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Order>>> call, Throwable t) {
                Log.e(TAG, "getOrdersByTableNumber onFailure", t);
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    /**
     * Delete an order by its id.
     */
    public void deleteOrder(String orderId, final RepositoryCallback<Void> callback) {
        if (orderId == null || orderId.trim().isEmpty()) {
            callback.onError("Invalid order id");
            return;
        }
        Call<Void> call = RetrofitClient.getInstance().getApiService().deleteOrder(orderId);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(null);
                } else {
                    String msg = "HTTP " + response.code() + " - " + response.message();
                    try {
                        if (response.errorBody() != null) msg += " - " + response.errorBody().string();
                    } catch (IOException ignored) {}
                    Log.e(TAG, "deleteOrder error: " + msg);
                    callback.onError(msg);
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e(TAG, "deleteOrder onFailure", t);
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    /**
     * Update an order partially (e.g., change tableNumber)
     */
    public void updateOrder(String orderId, Map<String, Object> updates, final RepositoryCallback<Order> callback) {
        if (orderId == null || orderId.trim().isEmpty()) {
            callback.onError("Invalid order id");
            return;
        }
        Call<Order> call = RetrofitClient.getInstance().getApiService().updateOrder(orderId, updates);
        call.enqueue(new Callback<Order>() {
            @Override
            public void onResponse(Call<Order> call, Response<Order> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    String msg = "HTTP " + response.code() + " - " + response.message();
                    try {
                        if (response.errorBody() != null) msg += " - " + response.errorBody().string();
                    } catch (IOException ignored) {}
                    Log.e(TAG, "updateOrder error: " + msg);
                    callback.onError(msg);
                }
            }

            @Override
            public void onFailure(Call<Order> call, Throwable t) {
                Log.e(TAG, "updateOrder onFailure", t);
                callback.onError(t.getMessage() != null ? t.getMessage() : "Network error");
            }
        });
    }

    /**
     * Move all orders from one tableNumber to another (best-effort).
     * onComplete callback will be called when all updates finished (success or fail).
     *
     * Defensive behavior: if backend returns unfiltered list, we explicitly filter by fromTableNumber
     * so only orders that actually belong to the source table are moved.
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

        // 1) get orders of fromTable
        getOrdersByTableNumber(fromTableNumber, null, new RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orders) {
                if (orders == null || orders.isEmpty()) {
                    // nothing to move
                    Log.d(TAG, "moveOrdersForTable: no orders found for table " + fromTableNumber);
                    callback.onSuccess(null);
                    return;
                }

                // Defensive filter: keep only orders whose tableNumber equals fromTableNumber
                List<Order> toMove = new ArrayList<>();
                for (Order o : orders) {
                    if (o == null) continue;
                    try {
                        if (o.getTableNumber() == fromTableNumber) toMove.add(o);
                    } catch (Exception ignored) {}
                }

                if (toMove.isEmpty()) {
                    Log.d(TAG, "moveOrdersForTable: after filtering none belong to table " + fromTableNumber);
                    callback.onSuccess(null);
                    return;
                }

                final int total = toMove.size();
                final int[] finished = {0};
                final int[] errors = {0};

                Log.d(TAG, "moveOrdersForTable: will move " + total + " orders from table " + fromTableNumber + " -> " + toTableNumber);

                for (Order o : toMove) {
                    if (o == null || o.getId() == null) {
                        finished[0]++;
                        if (finished[0] >= total) {
                            if (errors[0] == 0) callback.onSuccess(null);
                            else callback.onError("Some order updates failed");
                        }
                        continue;
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("tableNumber", toTableNumber);

                    updateOrder(o.getId(), updates, new RepositoryCallback<Order>() {
                        @Override
                        public void onSuccess(Order result) {
                            Log.d(TAG, "Moved order " + o.getId() + " to table " + toTableNumber);
                            finished[0]++;
                            if (finished[0] >= total) {
                                if (errors[0] == 0) callback.onSuccess(null);
                                else callback.onError("Some order updates failed");
                            }
                        }

                        @Override
                        public void onError(String message) {
                            Log.w(TAG, "Failed to move order " + o.getId() + " : " + message);
                            errors[0]++;
                            finished[0]++;
                            if (finished[0] >= total) {
                                if (errors[0] == 0) callback.onSuccess(null);
                                else callback.onError("Some order updates failed");
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Cannot fetch orders for table " + fromTableNumber + " : " + message);
                callback.onError(message);
            }
        });
    }
}
