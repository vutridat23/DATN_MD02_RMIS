package com.ph48845.datn_qlnh_rmis.data.remote;

import com.ph48845.datn_qlnh_rmis.data.model.HistoryItem;
import com.ph48845.datn_qlnh_rmis.data.model.LoginResponse;
import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.RevenueItem;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.model.User;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.PATCH;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

/**
 * Retrofit API definitions.
 *
 * NOTE:
 * - Endpoints that return the wrapper { "success", "data", "message" } use ApiResponse<T>.
 * - Ensure server responses match this wrapper; otherwise adjust accordingly.
 */
public interface ApiService {

    // --- USER ENDPOINTS ---
    @GET("users")
    Call<List<User>> getAllUsers();

    @POST("/users/login")
    Call<LoginResponse> login(@Body User user);

    @POST("users")
    Call<User> createUser(@Body User user);

    @PUT("users/{id}")
    Call<User> updateUser(@Path("id") String userId, @Body User user);

    // --- MENU ITEM ENDPOINTS ---
    @GET("menu")
    Call<ApiResponse<List<MenuItem>>> getAllMenuItems();

    @POST("menu")
    Call<MenuItem> createMenuItem(@Body MenuItem menuItem);

    @DELETE("menu/{id}")
    Call<Void> deleteMenuItem(@Path("id") String itemId);

    // --- ORDER ENDPOINTS ---

    // Get all orders (wrapped)
    @GET("orders")
    Call<ApiResponse<List<Order>>> getAllOrders();

    // Get orders for a specific table (wrapped)
    @GET("orders")
    Call<ApiResponse<List<Order>>> getOrdersByTable(
            @Query("tableNumber") Integer tableNumber,
            @Query("status") String status
    );

    // Update a single order item status (no wrapper assumed)
    // NOTE: Use PATCH and plural "items" to match server route: PATCH /orders/{orderId}/items/{itemId}/status
    @PATCH("orders/{orderId}/items/{itemId}/status")
    Call<Void> updateOrderItemStatus(
            @Path("orderId") String orderId,
            @Path("itemId") String itemId,
            @Body StatusUpdate statusUpdate
    );

    // Create order (server returns wrapper { success, data: order })
    @POST("orders")
    Call<ApiResponse<Order>> createOrder(@Body Order order);

    // Get order by id (wrapped)
    @GET("orders/{id}")
    Call<ApiResponse<Order>> getOrderById(@Path("id") String orderId);

    // Update order status (wrapped)
    @PUT("orders/{id}/status")
    Call<ApiResponse<Order>> updateOrderStatus(@Path("id") String orderId, @Body Map<String, Object> newStatusBody);

    // Update order (partial) - server returns wrapper
    @PUT("orders/{id}")
    Call<ApiResponse<Order>> updateOrder(@Path("id") String orderId, @Body Map<String, Object> updates);

    // Delete order (no wrapper)
    @DELETE("orders/{id}")
    Call<Void> deleteOrder(@Path("id") String orderId);

    // --- ORDER PAYMENT ENDPOINT ---
    @POST("orders/pay")
    Call<ApiResponse<Order>> payOrder(@Body Map<String, Object> body);

    @GET("orders/revenue")
    Call<ApiResponse<List<RevenueItem>>> getRevenueFromOrders(@QueryMap Map<String, String> params);
    // Lấy doanh thu theo ngày / khoảng ngày
    @GET("orders/byDate")
    Call<ApiResponse<List<RevenueItem>>> getRevenueByDate(@QueryMap Map<String, String> params);
//    @GET("revenue/daily")
//    Call<ApiResponse<List<RevenueItem>>> getRevenueByDay(@Query("date") String date);
@POST("reports/daily")
Call<ApiResponse<RevenueItem>> createDailyReport(@Body Map<String, String> body);

    @GET("orders/historyod")
    Call<ApiResponse<List<HistoryItem>>> getAllHistory();


    @GET("revenue/daily")
    Call<ApiResponse<List<RevenueItem>>> getRevenueByRange(
            @Query("fromDate") String fromDate,
            @Query("toDate") String toDate
    );


    // --- TABLE ENDPOINTS ---
    @GET("tables")
    Call<ApiResponse<List<TableItem>>> getAllTables();

    @PUT("tables/{id}")
    Call<TableItem> updateTable(@Path("id") String tableId, @Body Map<String, Object> updates);



    @POST("tables/{id}/merge")
    Call<TableItem> mergeTable(@Path("id") String targetTableId, @Body Map<String, String> body);


    /**
     * Helper class for sending status updates to the server.
     * Use field name "status" because server typically expects this key.
     */
    class StatusUpdate {
        // public field so Gson serializes it as {"status": "..."}
        public String status;

        public StatusUpdate(String status) {
            this.status = status;
        }
    }
}