package com.ph48845.datn_qlnh_rmis.data.remote;

import com.ph48845.datn_qlnh_rmis.data.model. HistoryItem;
import com.ph48845.datn_qlnh_rmis.data.model. Ingredient;
import com.ph48845.datn_qlnh_rmis.data.model.LoginResponse;
import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.ReportItem;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.model. User;
import com.ph48845.datn_qlnh_rmis.data.model. Voucher;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

/**
 * ApiService:  Gộp tất cả endpoint
 * Chiến lược: Giữ nguyên return types cũ, chỉ wrap ApiResponse cho endpoints MỚI
 */
public interface ApiService {

    // ===========================
    // --- USER ENDPOINTS ---
    // ===========================
    @GET("users")
    Call<ApiResponse<List<User>>> getAllUsers();

    @POST("/users/login")
    Call<LoginResponse> login(@Body User user);

    @POST("users")
    Call<ApiResponse<User>> createUser(@Body User user);

    @PUT("users/{id}")
    Call<ApiResponse<User>> updateUser(@Path("id") String userId, @Body User user);

    // ===========================
    // --- MENU ITEM ENDPOINTS ---
    // ===========================
    @GET("menu")
    Call<ApiResponse<List<MenuItem>>> getAllMenuItems();

    @POST("menu")
    Call<ApiResponse<MenuItem>> createMenuItem(@Body MenuItem menuItem);

    @DELETE("menu/{id}")
    Call<ApiResponse<Void>> deleteMenuItem(@Path("id") String itemId);

    // ===========================
    // --- ORDER ENDPOINTS ---
    // ===========================
    @GET("orders")
    Call<ApiResponse<List<Order>>> getAllOrders();

    @GET("orders")
    Call<ApiResponse<List<Order>>> getOrdersByTable(
            @Query("tableNumber") Integer tableNumber,
            @Query("status") String status
    );

    @POST("orders")
    Call<ApiResponse<Order>> createOrder(@Body Order order);

    @GET("orders/{id}")
    Call<ApiResponse<Order>> getOrderById(@Path("id") String orderId);

    @PUT("orders/{id}")
    Call<ApiResponse<Order>> updateOrder(@Path("id") String orderId, @Body Map<String, Object> updates);

    @PUT("orders/{id}/status")
    Call<ApiResponse<Order>> updateOrderStatus(@Path("id") String orderId, @Body Map<String, Object> newStatusBody);

    @DELETE("orders/{id}")
    Call<ApiResponse<Void>> deleteOrder(@Path("id") String orderId);

    /**
     * GIỮ NGUYÊN Call<Void> - không ảnh hưởng code cũ
     */
    @PATCH("orders/{orderId}/items/{itemId}/status")
    Call<Void> updateOrderItemStatus(
            @Path("orderId") String orderId,
            @Path("itemId") String itemId,
            @Body StatusUpdate statusUpdate
    );

    @POST("orders/{orderId}/items/{itemId}/request-cancel")
    Call<ApiResponse<Order>> requestCancelItem(
            @Path("orderId") String orderId,
            @Path("itemId") String itemId,
            @Body Map<String, Object> body
    );

    /**
     * YÊU CẦU TẠM TÍNH - orderId trong URL path
     * POST /orders/{orderId}/request-temp-calculation
     * Body: { requestedBy, requestedByName }
     */
    @POST("orders/{orderId}/request-temp-calculation")
    Call<ApiResponse<Order>> requestTempCalculation(
            @Path("orderId") String orderId,
            @Body Map<String, Object> body
    );

    /**
     * DI CHUYỂN TẤT CẢ ORDERS SANG BÀN KHÁC (không tách hóa đơn)
     * POST /orders/move-to-table
     * Body: { fromTableNumber, toTableNumber, movedBy }
     */
    @POST("orders/move-to-table")
    Call<ApiResponse<Map<String, Object>>> moveOrdersToTable(@Body Map<String, Object> body);

    /**
     * YÊU CẦU KIỂM TRA BÀN - MỚI (dùng ApiResponse)
     */
    @POST("tables/request-check")
    Call<ApiResponse<Void>> requestTableCheck(@Body Map<String, Object> body);

    // ===========================
    // pay endpoint
    // ===========================
    @POST("orders/pay")
    Call<ApiResponse<Order>> payOrder(@Body Map<String, Object> body);

    // ===========================
    // --- TABLE ENDPOINTS ---
    // GIỮ NGUYÊN Call<TableItem> cho tất cả
    // ===========================
    @GET("tables")
    Call<ApiResponse<List<TableItem>>> getAllTables();

    @GET("tables/{id}")
    Call<ApiResponse<TableItem>> getTableById(@Path("id") String tableId);

    /**
     * GIỮ NGUYÊN Call<TableItem>
     */
    @PUT("tables/{id}")
    Call<TableItem> updateTable(@Path("id") String tableId, @Body Map<String, Object> updates);

    /**
     * GIỮ NGUYÊN Call<TableItem>
     */
    @POST("tables/{id}/merge")
    Call<TableItem> mergeTable(@Path("id") String targetTableId, @Body Map<String, String> body);

    /**
     * GIỮ NGUYÊN Call<TableItem>
     */
    @POST("tables/{id}/reserve")
    Call<TableItem> reserveTable(@Path("id") String id, @Body Map<String, Object> body);

    /**
     * TÁCH BÀN KHÔNG TÁCH HÓA ĐƠN - MỚI (dùng ApiResponse)
     */
    @POST("tables/{sourceTableId}/split-table-only")
    Call<ApiResponse<Void>> splitTableOnly(@Path("sourceTableId") String sourceTableId, @Body Map<String, Object> body);

    // ===========================
    // --- INGREDIENT ENDPOINTS ---
    // ===========================
    @GET("ingredients")
    Call<ApiResponse<List<Ingredient>>> getAllIngredients(@Query("status") String status, @Query("tag") String tag);

    @POST("ingredients/{id}/take")
    Call<ApiResponse<Ingredient>> takeIngredient(@Path("id") String ingredientId, @Body Map<String, Object> body);

    @POST("recipes/consume")
    Call<ApiResponse<Void>> consumeRecipe(@Body Map<String, Object> body);

    // ===========================
    // --- HISTORY ENDPOINTS ---
    // ===========================
    @GET("history")
    Call<ApiResponse<List<HistoryItem>>> getAllHistory(@QueryMap Map<String, String> filters);

    @GET("history/{id}")
    Call<ApiResponse<HistoryItem>> getHistoryById(@Path("id") String historyId);

    @GET("orders/historyod")
    Call<ApiResponse<List<HistoryItem>>> getAllOrdersHistory();

    // ===========================
    // --- REPORT ENDPOINTS ---
    // ===========================
    @GET("reports")
    Call<ApiResponse<List<ReportItem>>> getAllReports();

    @GET("reports/byDate")
    Call<ApiResponse<List<ReportItem>>> getReportsByDate(@QueryMap Map<String, String> params);

    @GET("reports/{id}")
    Call<ApiResponse<ReportItem>> getReportById(@Path("id") String reportId);

    @POST("reports/daily")
    Call<ApiResponse<ReportItem>> createDailyReport(@Body Map<String, String> body);

    @POST("reports/weekly")
    Call<ApiResponse<ReportItem>> createWeeklyReport(@Body Map<String, String> body);

    // ===========================
    // --- VOUCHER ENDPOINTS ---
    // ===========================
    @GET("vouchers")
    Call<ApiResponse<List<Voucher>>> getAllVouchers(@Query("status") String status);

    @GET("vouchers/{id}")
    Call<ApiResponse<Voucher>> getVoucherById(@Path("id") String voucherId);

    @GET("vouchers/code/{code}")
    Call<ApiResponse<Voucher>> getVoucherByCode(@Path("code") String code);

    // ===========================
    // --- HELPER CLASSES ---
    // ===========================
    class StatusUpdate {
        public String status;

        public StatusUpdate(String status) {
            this.status = status;
        }
    }
}