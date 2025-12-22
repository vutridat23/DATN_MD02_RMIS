package com.ph48845.datn_qlnh_rmis.data.repository;

import android.util.Log;

import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
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

    private final ApiService api;

    // Constructor inject cho ViewModel tự tạo Retrofit
    public OrderRepository(ApiService api) {
        this.api = api;
    }

    // Constructor cũ: dùng singleton RetrofitClient
    public OrderRepository() {
        this.api = RetrofitClient.getInstance().getApiService();
    }

    // ===== Callback interface giữ nguyên =====
    public interface RepositoryCallback<T> {
        void onSuccess(T result);

        void onError(String message);
    }

    // ===== Các method dạng Call cho BepViewModel =====
    // Sửa: trả về Call<ApiResponse<List<Order>>> để khớp với ApiService.getAllOrders()
    public Call<ApiResponse<List<Order>>> getAllOrders() {
        return api.getAllOrders();
    }

    /**
     * Lấy tất cả orders với callback
     */
    public void getAllOrders(RepositoryCallback<List<Order>> callback) {
        api.getAllOrders().enqueue(new Callback<ApiResponse<List<Order>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Order>>> call, Response<ApiResponse<List<Order>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<Order>> apiResp = response.body();
                    if (apiResp.getData() != null) {
                        // 🔍 DEBUG: Log để kiểm tra backend có trả về checkItemsRequestedAt không
                        try {
                            List<Order> orders = apiResp.getData();
                            Log.d(TAG, "📦 getAllOrders: Received " + orders.size() + " orders from server");
                            
                            // Kiểm tra 3 orders đầu tiên
                            int sampleSize = Math.min(3, orders.size());
                            for (int i = 0; i < sampleSize; i++) {
                                Order order = orders.get(i);
                                if (order != null) {
                                    String requestedAt = order.getCheckItemsRequestedAt();
                                    String requestedBy = order.getCheckItemsRequestedBy();
                                    Log.d(TAG, "🔍 Order[" + i + "] ID=" + order.getId() + 
                                          ", Table=" + order.getTableNumber() + 
                                          ", checkItemsRequestedAt=" + (requestedAt != null ? requestedAt : "NULL") +
                                          ", checkItemsRequestedBy=" + (requestedBy != null && !requestedBy.isEmpty() ? requestedBy : "NULL"));
                                }
                            }
                            
                            // Đếm số orders có checkItemsRequestedAt
                            int countWithRequest = 0;
                            for (Order order : orders) {
                                if (order != null && order.getCheckItemsRequestedAt() != null && !order.getCheckItemsRequestedAt().trim().isEmpty()) {
                                    countWithRequest++;
                                }
                            }
                            Log.d(TAG, "📊 Total orders with checkItemsRequestedAt: " + countWithRequest + "/" + orders.size());
                            
                            if (countWithRequest == 0 && orders.size() > 0) {
                                Log.w(TAG, "⚠️ WARNING: Backend KHÔNG trả về field checkItemsRequestedAt cho bất kỳ order nào!");
                                Log.w(TAG, "⚠️ Hãy kiểm tra backend: GET /orders có SELECT field checkItemsRequestedAt không?");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error logging debug info: " + e.getMessage(), e);
                        }
                        
                        callback.onSuccess(apiResp.getData());
                    } else {
                        callback.onError("Server returned no order data: " + apiResp.getMessage());
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

    // New: call to consume recipe on server (deduct ingredients based on recipe)
    public Call<ApiResponse<Void>> consumeRecipeCall(String menuItemId, double quantity, String orderId) {
        Map<String, Object> body = new HashMap<>();
        body.put("menuItemId", menuItemId);
        body.put("quantity", quantity);
        if (orderId != null) body.put("orderId", orderId);
        return api.consumeRecipe(body);
    }

    /**
     * Trả về Call<Void> cho trường hợp caller muốn enqueue trực tiếp.
     * Chú ý: ApiService.updateOrderItemStatus(...) phải tồn tại và trả về Call<Void>.
     */
    public Call<Void> updateOrderItemStatus(String orderId, String itemId, String newStatus) {
        return api.updateOrderItemStatus(orderId, itemId, new ApiService.StatusUpdate(newStatus));
    }

    /**
     * Hỗ trợ wrapper callback để gọi và xử lý kết quả theo phong cách RepositoryCallback.
     * Dùng khi caller muốn không thao tác với retrofit Call trực tiếp.
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
        Call<Void> call = api.updateOrderItemStatus(orderId, itemId, new ApiService.StatusUpdate(newStatus));
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

    // ===== Các wrapper callback nguyên gốc =====
    public void createOrder(final Order order, final RepositoryCallback<Order> callback) {
        if (order == null) {
            callback.onError("Order is null");
            return;
        }
        // ApiService.createOrder returns Call<ApiResponse<Order>> (wrapper)
        api.createOrder(order).enqueue(new Callback<ApiResponse<Order>>() {
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
                    callback.onError(buildHttpError("createOrder", response));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Order>> call, Throwable t) {
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
                            if (apiResponse.getMessage() != null)
                                msg += ": " + apiResponse.getMessage();
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
        // ApiService.updateOrder returns Call<ApiResponse<Order>> (wrapper)
        api.updateOrder(orderId, updates).enqueue(new Callback<ApiResponse<Order>>() {
            @Override
            public void onResponse(Call<ApiResponse<Order>> call, Response<ApiResponse<Order>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<Order> apiResp = response.body();
                    if (apiResp.getData() != null) {
                        Order updatedOrder = apiResp.getData();
                        boolean needRequery = false;
                        
                        // Kiểm tra xem response có chứa checkItemsRequestedAt không
                        // Nếu không có, query lại order để lấy dữ liệu mới nhất
                        if (updates.containsKey("checkItemsRequestedAt") && 
                            (updatedOrder.getCheckItemsRequestedAt() == null || 
                             updatedOrder.getCheckItemsRequestedAt().trim().isEmpty())) {
                            Log.d(TAG, "Response does not contain checkItemsRequestedAt, querying order again...");
                            needRequery = true;
                        }
                        
                        // Kiểm tra xem response có chứa tempCalculationRequestedAt không
                        // Nếu đang clear (set null), luôn query lại để đảm bảo có dữ liệu mới nhất
                        if (updates.containsKey("tempCalculationRequestedAt") && updates.get("tempCalculationRequestedAt") == null) {
                            String tempCalcAt = updatedOrder.getTempCalculationRequestedAt();
                            Log.d(TAG, "Clearing tempCalculationRequestedAt - response has: " + tempCalcAt);
                            // Luôn query lại để đảm bảo có dữ liệu mới nhất từ server
                            // (vì server có thể không trả về field này trong response)
                            Log.d(TAG, "Will query order again to verify tempCalculationRequestedAt is cleared...");
                            needRequery = true;
                        }
                        
                        if (needRequery) {
                            // Query lại order để lấy dữ liệu mới nhất
                            getOrderById(orderId, callback);
                        } else {
                            callback.onSuccess(updatedOrder);
                        }
                    } else {
                        callback.onError("Server returned no order data: " + apiResp.getMessage());
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
                    if (o == null) continue;
                    try {
                        if (o.getTableNumber() == fromTableNumber) toMove.add(o);
                    } catch (Exception ignored) {
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
        } catch (IOException ignored) {
        }
        Log.e(TAG, action + " error: " + msg);
        return msg;
    }

    private String logFailure(String logMsg, Throwable t) {
        Log.e(TAG, logMsg, t);
        return t.getMessage() != null ? t.getMessage() : "Network error";
    }

    public void payOrder(String orderId,
                         String paymentMethod,
                         double amountCustomerGiven,
                         RepositoryCallback<Order> callback) {
        payOrder(orderId, paymentMethod, amountCustomerGiven, null, callback);
    }

    public void payOrder(String orderId,
                         String paymentMethod,
                         double amountCustomerGiven,
                         String voucherId,
                         RepositoryCallback<Order> callback) {

        if (orderId == null || orderId.trim().isEmpty()) {
            callback.onError("Invalid orderId");
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("orderId", orderId);                       // ID hóa đơn
        body.put("paymentMethod", paymentMethod != null ? paymentMethod : "Tiền mặt");
        // gửi cả 2 trường paidAmount & amountCustomerGiven để backend ko bị thiếu dữ liệu
        body.put("paidAmount", amountCustomerGiven);        // backend cũ có thể gọi là paidAmount
        body.put("amountCustomerGiven", amountCustomerGiven);// backend mới có thể dùng amountCustomerGiven
        if (voucherId != null && !voucherId.trim().isEmpty()) {
            body.put("voucherId", voucherId);              // ID voucher nếu có
        }

        api.payOrder(body).enqueue(new Callback<ApiResponse<Order>>() {
            @Override
            public void onResponse(Call<ApiResponse<Order>> call, Response<ApiResponse<Order>> response) {
                try {
                    if (response.isSuccessful()) {
                        ApiResponse<Order> apiResp = response.body();
                        if (apiResp != null) {
                            if (apiResp.isSuccess()) {
                                callback.onSuccess(apiResp.getData());
                            } else {
                                // server trả success=false kèm message
                                String msg = apiResp.getMessage() != null ? apiResp.getMessage() : "Thanh toán thất bại";
                                callback.onError(msg);
                            }
                        } else {
                            // response successful nhưng body null (lỗi server)
                            String err = "Server trả về body rỗng (200).";
                            // Try read errorBody (rare on 200)
                            callback.onError(err);
                        }
                    } else {
                        // Không phải 2xx -> đọc errorBody nếu có để debug
                        String errBody = null;
                        try {
                            if (response.errorBody() != null) {
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
                Log.e(TAG, "payOrder onFailure", t);
                callback.onError(err);
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
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        String currentTime = sdf.format(new java.util.Date());

        Map<String, Object> updates = new HashMap<>();
        if (userId != null && !userId.trim().isEmpty()) {
            updates.put("checkItemsRequestedBy", userId);
        }
        updates.put("checkItemsRequestedAt", currentTime);

        Log.d(TAG, "Sending check items request for order: " + orderId + ", userId: " + userId + ", time: " + currentTime);

        updateOrder(orderId, updates, new RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order result) {
                Log.d(TAG, "Check items request saved successfully for order: " + orderId);
                if (result != null) {
                    Log.d(TAG, "Order response - checkItemsRequestedAt: " + result.getCheckItemsRequestedAt());
                    Log.d(TAG, "Order response - checkItemsRequestedBy: " + result.getCheckItemsRequestedBy());
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
                    callback.onError(message);
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
        final java.util.concurrent.atomic.AtomicInteger totalCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicBoolean callbackCalled = new java.util.concurrent.atomic.AtomicBoolean(false);

        // Đếm số order hợp lệ
        for (String orderId : orderIds) {
            if (orderId != null && !orderId.trim().isEmpty()) {
                totalCount.incrementAndGet();
            }
        }

        if (totalCount.get() == 0) {
            if (callback != null) {
                callback.onSuccess(new ArrayList<>());
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
                    Log.d(TAG, "Check items request completed: " + current + "/" + totalCount.get());

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

}