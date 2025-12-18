package com.ph48845.datn_qlnh_rmis.data.repository;

import android.util.Log;

import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote. RetrofitClient;

import java. io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrderRepository {

    private static final String TAG = "OrderRepository";

    private final ApiService api;

    public OrderRepository(ApiService api) {
        this.api = api;
    }

    public OrderRepository() {
        this.api = RetrofitClient. getInstance().getApiService();
    }

    public interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    public Call<ApiResponse<List<Order>>> getAllOrders() {
        return api.getAllOrders();
    }

    public Call<ApiResponse<Void>> consumeRecipeCall(String menuItemId, double quantity, String orderId) {
        Map<String, Object> body = new HashMap<>();
        body.put("menuItemId", menuItemId);
        body.put("quantity", quantity);
        if (orderId != null) body.put("orderId", orderId);
        return api.consumeRecipe(body);
    }

    /**
     * GIỮ NGUYÊN Call<Void>
     */
    public Call<Void> updateOrderItemStatus(String orderId, String itemId, String newStatus) {
        return api.updateOrderItemStatus(orderId, itemId, new ApiService.StatusUpdate(newStatus));
    }

    /**
     * GIỮ NGUYÊN xử lý Call<Void>
     */
    public void updateOrderItemStatus(String orderId, String itemId, String newStatus, final RepositoryCallback<Void> callback) {
        if (orderId == null || orderId.trim().isEmpty()) {
            callback.onError("Invalid orderId");
            return;
        }
        if (itemId == null || itemId.trim().isEmpty()) {
            callback.onError("Invalid itemId");
            return;
        }
        Call<Void> call = api. updateOrderItemStatus(orderId, itemId, new ApiService.StatusUpdate(newStatus));
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(null);
                } else {
                    callback.onError(buildHttpError("updateOrderItemStatus", response));
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onError(logFailure("updateOrderItemStatus onFailure", t));
            }
        });
    }

    public void createOrder(final Order order, final RepositoryCallback<Order> callback) {
        if (order == null) {
            callback.onError("Order is null");
            return;
        }
        api.createOrder(order).enqueue(new Callback<ApiResponse<Order>>() {
            @Override
            public void onResponse(Call<ApiResponse<Order>> call, Response<ApiResponse<Order>> response) {
                if (response. isSuccessful() && response.body() != null) {
                    ApiResponse<Order> apiResp = response.body();
                    if (apiResp.isSuccess() && apiResp.getData() != null) {
                        callback. onSuccess(apiResp.getData());
                    } else {
                        callback.onError("Server returned no order data: " + apiResp.getMessage());
                    }
                } else {
                    callback.onError(buildHttpError("createOrder", response));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Order>> call, Throwable t) {
                callback.onError(logFailure("createOrder onFailure", t));
            }
        });
    }

    public void getOrdersByTableNumber(Integer tableNumber, String status, final RepositoryCallback<List<Order>> callback) {
        api.getOrdersByTable(tableNumber, status).enqueue(new Callback<ApiResponse<List<Order>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Order>>> call, Response<ApiResponse<List<Order>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<Order>> apiResponse = response.body();
                    if (apiResponse.isSuccess()) {
                        List<Order> list = apiResponse.getData();
                        callback.onSuccess(list != null ? list : new ArrayList<>());
                    } else {
                        String msg = "Server error";
                        if (apiResponse. getMessage() != null) msg += ": " + apiResponse.getMessage();
                        callback.onError(msg);
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

    /**
     * SỬA:  deleteOrder xử lý ApiResponse<Void>
     */
    public void deleteOrder(String orderId, final RepositoryCallback<Void> callback) {
        if (orderId == null || orderId.trim().isEmpty()) {
            callback. onError("Invalid order id");
            return;
        }

        api.deleteOrder(orderId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Void> apiResp = response.body();
                    if (apiResp.isSuccess()) {
                        callback. onSuccess(null);
                    } else {
                        callback. onError(apiResp.getMessage() != null ? apiResp.getMessage() : "Delete failed");
                    }
                } else {
                    callback.onError(buildHttpError("deleteOrder", response));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                callback.onError(logFailure("deleteOrder onFailure", t));
            }
        });
    }

    public void updateOrder(String orderId, Map<String, Object> updates, final RepositoryCallback<Order> callback) {
        if (orderId == null || orderId.trim().isEmpty()) {
            callback.onError("Invalid order id");
            return;
        }
        api.updateOrder(orderId, updates).enqueue(new Callback<ApiResponse<Order>>() {
            @Override
            public void onResponse(Call<ApiResponse<Order>> call, Response<ApiResponse<Order>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Order> apiResp = response.body();
                    if (apiResp.isSuccess() && apiResp.getData() != null) {
                        callback. onSuccess(apiResp.getData());
                    } else {
                        callback.onError("Server returned no order data: " + (apiResp.getMessage() != null ? apiResp.getMessage() : ""));
                    }
                } else {
                    callback.onError(buildHttpError("updateOrder", response));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Order>> call, Throwable t) {
                callback.onError(logFailure("updateOrder onFailure", t));
            }
        });
    }

    public void moveOrdersForTable(int fromTableNumber, int toTableNumber, final RepositoryCallback<Void> callback) {
        if (fromTableNumber <= 0) {
            callback.onError("Invalid fromTableNumber:  " + fromTableNumber);
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
                    Log. d(TAG, "moveOrdersForTable: no orders found for table " + fromTableNumber);
                    callback.onSuccess(null);
                    return;
                }

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

    public void payOrder(String orderId, String paymentMethod, double amountCustomerGiven, RepositoryCallback<Order> callback) {
        payOrder(orderId, paymentMethod, amountCustomerGiven, null, callback);
    }

    public void payOrder(String orderId, String paymentMethod, double amountCustomerGiven, String voucherId, RepositoryCallback<Order> callback) {
        if (orderId == null || orderId.trim().isEmpty()) {
            callback.onError("Invalid orderId");
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("orderId", orderId);
        body.put("paymentMethod", paymentMethod != null ? paymentMethod : "Tiền mặt");
        body.put("paidAmount", amountCustomerGiven);
        body.put("amountCustomerGiven", amountCustomerGiven);
        if (voucherId != null && ! voucherId.trim().isEmpty()) {
            body.put("voucherId", voucherId);
        }

        api.payOrder(body).enqueue(new Callback<ApiResponse<Order>>() {
            @Override
            public void onResponse(Call<ApiResponse<Order>> call, Response<ApiResponse<Order>> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        ApiResponse<Order> apiResp = response.body();
                        if (apiResp.isSuccess()) {
                            callback.onSuccess(apiResp.getData());
                        } else {
                            String msg = apiResp.getMessage() != null ? apiResp.getMessage() : "Thanh toán thất bại";
                            callback.onError(msg);
                        }
                    } else {
                        String errBody = null;
                        try {
                            if (response. errorBody() != null) {
                                errBody = response.errorBody().string();
                            }
                        } catch (IOException ioe) {
                            errBody = "Không thể đọc errorBody: " + ioe.getMessage();
                        }
                        String msg = "HTTP " + response.code() + " - " + response.message();
                        if (errBody != null && !errBody.isEmpty()) msg += " - " + errBody;
                        Log.e(TAG, "payOrder failed: " + msg);
                        callback.onError(msg);
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "payOrder onResponse exception", ex);
                    callback.onError("Lỗi xử lý response: " + ex.getMessage());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Order>> call, Throwable t) {
                String err = t != null && t.getMessage() != null ? t.getMessage() : "Lỗi kết nối";
                Log. e(TAG, "payOrder onFailure", t);
                callback.onError(err);
            }
        });
    }

    // ===== Helpers =====
    private void checkMoveFinished(int[] finished, int[] errors, int total, RepositoryCallback<Void> callback) {
        if (finished[0] >= total) {
            if (errors[0] == 0) {
                callback. onSuccess(null);
            } else {
                callback.onError("Some order updates failed (" + errors[0] + "/" + total + ")");
            }
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