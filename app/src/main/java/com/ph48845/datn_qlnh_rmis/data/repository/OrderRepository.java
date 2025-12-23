package com. ph48845.datn_qlnh_rmis.data.repository;

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
        this.api = RetrofitClient.getInstance().getApiService();
    }

    public interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    // ✅ Trả về Call<ApiResponse<List<Order>>>
    public Call<ApiResponse<List<Order>>> getAllOrders() {
        return api.getAllOrders();
    }

    /**
     * Lấy tất cả orders với callback
     */
    public void getAllOrders(final RepositoryCallback<List<Order>> callback) {
        api.getAllOrders().enqueue(new Callback<ApiResponse<List<Order>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Order>>> call, Response<ApiResponse<List<Order>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<Order>> apiResp = response.body();
                    if (apiResp.isSuccess() && apiResp.getData() != null) {
                        callback.onSuccess(apiResp.getData());
                    } else {
                        callback.onError("Server returned no order data:  " + (apiResp.getMessage() != null ? apiResp.getMessage() : ""));
                    }
                } else {
                    callback.onError(buildHttpError("getAllOrders", response));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Order>>> call, Throwable t) {
                callback.onError(logFailure("getAllOrders onFailure", t));
            }
        });
    }

    public Call<ApiResponse<Void>> consumeRecipeCall(String menuItemId, double quantity, String orderId) {
        Map<String, Object> body = new HashMap<>();
        body.put("menuItemId", menuItemId);
        body.put("quantity", quantity);
        if (orderId != null) body.put("orderId", orderId);
        return api.consumeRecipe(body);
    }

    public Call<Void> updateOrderItemStatus(String orderId, String itemId, String newStatus) {
        return api.updateOrderItemStatus(orderId, itemId, new ApiService.StatusUpdate(newStatus));
    }

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
                    callback. onSuccess(null);
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
                        callback.onSuccess(apiResp.getData());
                    } else {
                        callback.onError("Server returned no order data: " + (apiResp.getMessage() != null ? apiResp.getMessage() : ""));
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
                        callback.onSuccess(list != null ? list : new ArrayList<Order>());
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

    public void deleteOrder(String orderId, final RepositoryCallback<Void> callback) {
        if (orderId == null || orderId.trim().isEmpty()) {
            callback.onError("Invalid order id");
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
                    callback. onError(buildHttpError("deleteOrder", response));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                callback.onError(logFailure("deleteOrder onFailure", t));
            }
        });
    }

    public void updateOrder(String orderId, Map<String, Object> updates, final RepositoryCallback<Order> callback) {
        if (orderId == null || orderId. trim().isEmpty()) {
            callback.onError("Invalid order id");
            return;
        }
        api.updateOrder(orderId, updates).enqueue(new Callback<ApiResponse<Order>>() {
            @Override
            public void onResponse(Call<ApiResponse<Order>> call, Response<ApiResponse<Order>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Order> apiResp = response.body();
                    if (apiResp.isSuccess()) {
                        if (apiResp.getData() != null) {
                            Order updatedOrder = apiResp.getData();
                            // Kiểm tra xem response có chứa checkItemsRequestedAt không
                            // Nếu không có, query lại order để lấy dữ liệu mới nhất
                            if (updates.containsKey("checkItemsRequestedAt") &&
                                    (updatedOrder.getCheckItemsRequestedAt() == null ||
                                            updatedOrder. getCheckItemsRequestedAt().trim().isEmpty())) {
                                Log.d(TAG, "Response does not contain checkItemsRequestedAt, querying order again.. .");
                                // Query lại order để lấy dữ liệu mới nhất
                                getOrderById(orderId, callback);
                            } else {
                                callback.onSuccess(updatedOrder);
                            }
                        } else {
                            callback.onError("Server returned no order data: " + (apiResp.getMessage() != null ? apiResp.getMessage() : ""));
                        }
                    } else {
                        callback.onError("Update failed: " + (apiResp.getMessage() != null ? apiResp.getMessage() : ""));
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

    /**
     * Lấy một order theo ID
     */
    public void getOrderById(String orderId, final RepositoryCallback<Order> callback) {
        if (orderId == null || orderId.trim().isEmpty()) {
            callback.onError("Invalid order id");
            return;
        }
        api.getOrderById(orderId).enqueue(new Callback<ApiResponse<Order>>() {
            @Override
            public void onResponse(Call<ApiResponse<Order>> call, Response<ApiResponse<Order>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Order> apiResp = response.body();
                    if (apiResp.getData() != null) {
                        callback.onSuccess(apiResp.getData());
                    } else {
                        callback.onError("Server returned no order data: " + apiResp.getMessage());
                    }
                } else {
                    callback.onError(buildHttpError("getOrderById", response));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Order>> call, Throwable t) {
                callback.onError(logFailure("getOrderById onFailure", t));
            }
        });
    }

    /**
     * Move all orders from one tableNumber to another (best-effort).
     * Giữ logic phòng thủ:  chỉ di chuyển order đúng bàn nguồn.
     */
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
                    Log.d(TAG, "moveOrdersForTable: no orders found for table " + fromTableNumber);
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
        if (voucherId != null && !voucherId.trim().isEmpty()) {
            body.put("voucherId", voucherId);
        }

        Log.d(TAG, "=== PAY ORDER REQUEST ===");
        Log.d(TAG, "orderId: " + orderId);
        Log.d(TAG, "paymentMethod: " + (paymentMethod != null ? paymentMethod : "Tiền mặt"));
        Log.d(TAG, "paidAmount: " + amountCustomerGiven);
        Log.d(TAG, "amountCustomerGiven: " + amountCustomerGiven);
        Log.d(TAG, "voucherId: " + (voucherId != null ? voucherId : "null"));
        Log.d(TAG, "Body: " + body.toString());

        api.payOrder(body).enqueue(new Callback<ApiResponse<Order>>() {
            @Override
            public void onResponse(Call<ApiResponse<Order>> call, Response<ApiResponse<Order>> response) {
                try {
                    if (response. isSuccessful() && response.body() != null) {
                        ApiResponse<Order> apiResp = response.body();
                        if (apiResp.isSuccess()) {
                            callback. onSuccess(apiResp.getData());
                        } else {
                            String msg = apiResp.getMessage() != null ? apiResp.getMessage() : "Thanh toán thất bại";
                            callback.onError(msg);
                        }
                    } else {
                        String errBody = null;
                        try {
                            if (response.errorBody() != null) {
                                errBody = response.errorBody().string();
                            }
                        } catch (IOException ioe) {
                            errBody = "Không thể đọc errorBody:  " + ioe.getMessage();
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
                Log.e(TAG, "payOrder onFailure", t);
                callback.onError(err);
            }
        });
    }

    /**
     * ✅ Lấy danh sách orders có yêu cầu tạm tính
     */
    /**
     * ✅ Lấy danh sách orders có yêu cầu tạm tính
     * ❌ KHÔNG bao gồm orders có orderStatus = "temp_bill_printed"
     */
    public void getTemporaryBillOrders(final RepositoryCallback<List<Order>> callback) {
        getAllOrders(new RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> allOrders) {
                List<Order> tempBillOrders = new ArrayList<>();
                if (allOrders != null) {
                    for (Order order : allOrders) {
                        if (order != null) {
                            String requestedAt = order.getTempCalculationRequestedAt();
                            String orderStatus = order.getOrderStatus();

                            // ✅ Chỉ thêm nếu:
                            // 1. Có tempCalculationRequestedAt
                            // 2. orderStatus KHÔNG PHẢI "temp_bill_printed"
                            if (requestedAt != null && !requestedAt. trim().isEmpty()) {
                                if (orderStatus == null ||
                                        !orderStatus.equalsIgnoreCase("temp_bill_printed")) {
                                    tempBillOrders.add(order);
                                    Log.d(TAG, "✅ Including temp bill order: " + order.getId() +
                                            " (status: " + orderStatus + ")");
                                } else {
                                    Log.d(TAG, "❌ Skipping temp_bill_printed order: " + order.getId());
                                }
                            }
                        }
                    }
                }

                Log.d(TAG, "📊 Total temp bill orders (after filtering): " + tempBillOrders.size());
                callback.onSuccess(tempBillOrders);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * ✅ Lấy danh sách orders có yêu cầu kiểm tra bàn
     * CHỈ lấy những order có status = "pending" hoặc null
     */
    public void getCheckItemsOrders(final RepositoryCallback<List<Order>> callback) {
        getAllOrders(new RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> allOrders) {
                List<Order> checkItemsOrders = new ArrayList<>();
                if (allOrders != null) {
                    for (Order order : allOrders) {
                        if (order != null) {
                            String requestedAt = order.getCheckItemsRequestedAt();
                            String status = order.getCheckItemsStatus();

                            // Chỉ lấy orders có yêu cầu VÀ chưa hoàn thành
                            if (requestedAt != null && !requestedAt.trim().isEmpty()) {
                                // Bỏ qua nếu đã completed hoặc acknowledged
                                if (status != null && (status.equals("completed") || status.equals("acknowledged"))) {
                                    continue;
                                }
                                checkItemsOrders.add(order);
                                Log.d(TAG, "✅ Found check items request: Table " + order.getTableNumber());
                            }
                        }
                    }
                }

                Log.d(TAG, "📦 Total check items requests: " + checkItemsOrders.size());
                callback.onSuccess(checkItemsOrders);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * Gửi yêu cầu kiểm tra bàn/kiểm tra món lên database
     * Cập nhật order với checkItemsRequestedBy và checkItemsRequestedAt
     *
     * @param orderId ID của order cần gửi yêu cầu
     * @param userId ID của người gửi yêu cầu (có thể null)
     * @param callback Callback để xử lý kết quả
     */
    public void sendCheckItemsRequest(String orderId, String userId, RepositoryCallback<Order> callback) {
        if (orderId == null || orderId.trim().isEmpty()) {
            if (callback != null) {
                callback.onError("Order ID không hợp lệ");
            }
            return;
        }

        // Tạo timestamp theo định dạng ISO 8601
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss. SSS'Z'", java. util.Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        String currentTime = sdf.format(new java.util.Date());

        Map<String, Object> updates = new HashMap<>();
        if (userId != null && !userId. trim().isEmpty()) {
            updates.put("checkItemsRequestedBy", userId);
        }
        updates.put("checkItemsRequestedAt", currentTime);

        Log.d(TAG, "Sending check items request for order:  " + orderId + ", userId: " + userId + ", time: " + currentTime);

        updateOrder(orderId, updates, new RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order result) {
                Log.d(TAG, "Check items request saved successfully for order: " + orderId);
                if (result != null) {
                    Log.d(TAG, "Order response - checkItemsRequestedAt: " + result.getCheckItemsRequestedAt());
                    Log.d(TAG, "Order response - checkItemsRequestedBy:  " + result.getCheckItemsRequestedBy());
                    if (result.getCheckItemsRequestedAt() == null || result.getCheckItemsRequestedAt().trim().isEmpty()) {
                        Log.w(TAG, "WARNING: Server response does not contain checkItemsRequestedAt field!");
                    }
                }
                if (callback != null) {
                    callback.onSuccess(result);
                }
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Failed to send check items request for order " + orderId + ": " + message);
                if (callback != null) {
                    callback. onError(message);
                }
            }
        });
    }

    /**
     * Gửi yêu cầu kiểm tra bàn/kiểm tra món lên database cho nhiều orders
     *
     * @param orderIds Mảng các order ID cần gửi yêu cầu
     * @param userId ID của người gửi yêu cầu (có thể null)
     * @param callback Callback để xử lý kết quả (sẽ được gọi một lần khi tất cả đã hoàn thành)
     */
    public void sendCheckItemsRequestForMultipleOrders(String[] orderIds, String userId, RepositoryCallback<List<Order>> callback) {
        if (orderIds == null || orderIds.length == 0) {
            if (callback != null) {
                callback.onSuccess(new ArrayList<>());
            }
            return;
        }

        final List<Order> successResults = new ArrayList<>();
        final java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent. atomic.AtomicInteger totalCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent. atomic.AtomicBoolean callbackCalled = new java.util.concurrent.atomic.AtomicBoolean(false);

        // Đếm số order hợp lệ
        for (String orderId : orderIds) {
            if (orderId != null && !orderId.trim().isEmpty()) {
                totalCount. incrementAndGet();
            }
        }

        if (totalCount.get() == 0) {
            if (callback != null) {
                callback. onSuccess(new ArrayList<>());
            }
            return;
        }

        // Gửi request cho từng order
        for (String orderId : orderIds) {
            if (orderId == null || orderId.trim().isEmpty()) {
                continue;
            }

            sendCheckItemsRequest(orderId, userId, new RepositoryCallback<Order>() {
                @Override
                public void onSuccess(Order result) {
                    synchronized (successResults) {
                        if (result != null) {
                            successResults.add(result);
                        }
                    }
                    int current = successCount.incrementAndGet();
                    Log.d(TAG, "Check items request completed:  " + current + "/" + totalCount. get());

                    // Nếu tất cả đã hoàn thành, gọi callback (chỉ gọi một lần)
                    if (current >= totalCount.get() && callback != null && !callbackCalled.getAndSet(true)) {
                        callback.onSuccess(successResults);
                    }
                }

                @Override
                public void onError(String message) {
                    int current = successCount.incrementAndGet();
                    Log.w(TAG, "Check items request failed for order " + orderId + ": " + message);

                    // Nếu đã xử lý hết (dù thành công hay thất bại), gọi callback
                    if (current >= totalCount.get() && callback != null && !callbackCalled.getAndSet(true)) {
                        // Trả về danh sách các order đã thành công (có thể rỗng)
                        callback.onSuccess(successResults);
                    }
                }
            });
        }
    }

    // ===== Helpers =====
    private void checkMoveFinished(int[] finished, int[] errors, int total, RepositoryCallback<Void> callback) {
        if (finished[0] >= total) {
            if (errors[0] == 0) {
                callback.onSuccess(null);
            } else {
                callback.onError("Some order updates failed (" + errors[0] + "/" + total + ")");
            }
        }
    }

    private String buildHttpError(String action, Response<? > response) {
        String msg = "HTTP " + response.code() + " - " + response. message();
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