package com.ph48845.datn_qlnh_rmis.data.remote;

import com.ph48845.datn_qlnh_rmis.data.model.HistoryItem;
import com.ph48845.datn_qlnh_rmis.data.model.Ingredient;
import com.ph48845.datn_qlnh_rmis.data.model.LoginResponse;
import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.ReportItem;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.model.User;
import com.ph48845.datn_qlnh_rmis.data.model.Voucher;

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
 * ApiService: Gộp tất cả endpoint cũ.
 * Nhóm rõ ràng theo module: User / Menu / Order / Table / Ingredient / History / Report
 */
public interface ApiService {

    // ===========================
    // --- USER ENDPOINTS ---
    // ===========================
    @GET("users")
    Call<List<User>> getAllUsers();

    @POST("/users/login")
    Call<LoginResponse> login(@Body User user);

    @POST("users")
    Call<User> createUser(@Body User user);

    @PUT("users/{id}")
    Call<User> updateUser(@Path("id") String userId, @Body User user);

    // ===========================
    // --- MENU ITEM ENDPOINTS ---
    // ===========================
    @GET("menu")
    Call<ApiResponse<List<MenuItem>>> getAllMenuItems();

    @POST("menu")
    Call<MenuItem> createMenuItem(@Body MenuItem menuItem);

    @DELETE("menu/{id}")
    Call<Void> deleteMenuItem(@Path("id") String itemId);

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
    Call<Void> deleteOrder(@Path("id") String orderId);

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

    @POST("orders/{id}/request-temp-calculation")
    Call<ApiResponse<Order>> requestTempCalculation(@Path("id") String orderId, @Body Map<String, Object> body);



    // ===========================
    // pay endpoint (file B had this)
    @POST("orders/pay")
    Call<ApiResponse<Order>> payOrder(@Body Map<String, Object> body);

    // ===========================
    // --- TABLE ENDPOINTS ---
    // ===========================
    @GET("tables")
    Call<ApiResponse<List<TableItem>>> getAllTables();

    @PUT("tables/{id}")
    Call<TableItem> updateTable(@Path("id") String tableId, @Body Map<String, Object> updates);

    // Keep both: merge and reserve (reserve có ở file A)
    @POST("tables/{id}/merge")
    Call<TableItem> mergeTable(@Path("id") String targetTableId, @Body Map<String, String> body);

    // ===========================
    // --- INGREDIENT ENDPOINTS ---
    // ===========================
    @POST("tables/{id}/reserve")
    Call<TableItem> reserveTable(@Path("id") String id, @Body Map<String, Object> body);

    // ===========================
    // --- INGREDIENT ENDPOINTS ---
    // ===========================
    @GET("ingredients")
    Call<ApiResponse<List<Ingredient>>> getAllIngredients(@Query("status") String status, @Query("tag") String tag);

    @POST("ingredients/{id}/take")
    Call<ApiResponse<Ingredient>> takeIngredient(@Path("id") String ingredientId, @Body Map<String, Object> body);

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
