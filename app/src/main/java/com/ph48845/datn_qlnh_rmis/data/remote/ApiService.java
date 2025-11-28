package com.ph48845.datn_qlnh_rmis.data.remote;


import com.ph48845.datn_qlnh_rmis.data.model.LoginResponse;
import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
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
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Retrofit API definitions.
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
    @GET("orders")
    Call<ApiResponse<List<Order>>> getAllOrders();

    @GET("orders")
        // use wrapper to be consistent with other endpoints (menu / tables)
    Call<ApiResponse<List<Order>>> getOrdersByTable(
            @Query("tableNumber") Integer tableNumber,
            @Query("status") String status
    );

    @PATCH("orders/{orderId}/items/{itemId}/status")
    Call<Void> updateOrderItemStatus(
            @Path("orderId") String orderId,
            @Path("itemId") String itemId,
            @Body StatusUpdate statusUpdate
    );

    @POST("orders")
    Call<ApiResponse<Order>> createOrder(@Body Order order);

    @GET("orders/{id}")
    Call<ApiResponse<Order>> getOrderById(@Path("id") String orderId);

    @PUT("orders/{id}/status")
    Call<ApiResponse<Order>> updateOrderStatus(@Path("id") String orderId, @Body Map<String, Object> newStatusBody);

    @PUT("orders/{id}")
    Call<ApiResponse<Order>> updateOrder(@Path("id") String orderId, @Body Map<String, Object> updates);

    @DELETE("orders/{id}")
    Call<Void> deleteOrder(@Path("id") String orderId);

    // --- TABLE ENDPOINTS ---
    @GET("tables")
    Call<ApiResponse<List<TableItem>>> getAllTables();

    @PUT("tables/{id}")
    Call<TableItem> updateTable(@Path("id") String tableId, @Body Map<String, Object> updates);

    @POST("tables/{id}/merge")
    Call<TableItem> mergeTable(@Path("id") String targetTableId, @Body Map<String, String> body);


    // --- INGREDIENTS (nguyên liệu) ---
    // Lấy tất cả nguyên liệu (API trả về wrapper { success, data: [ingredient] })
    @GET("ingredients")
    Call<ApiResponse<List<Ingredient>>> getAllIngredients(@Query("status") String status, @Query("tag") String tag);

    // Bếp lấy nguyên liệu (POST /ingredients/{id}/take) body: { amount: number }
    @POST("ingredients/{id}/take")
    Call<ApiResponse<Ingredient>> takeIngredient(@Path("id") String ingredientId, @Body Map<String, Object> body);

    /**
     * Helper class for sending status updates to the server.
     * This replaces OrderApi.StatusUpdate previously used.
     */
    class StatusUpdate {
        // public field so Gson serializes it as {"status": "..."}
        public String status;

        public StatusUpdate(String status) {
            this.status = status;
        }
    }
}