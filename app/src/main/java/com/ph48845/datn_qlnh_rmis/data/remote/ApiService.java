package com.ph48845.datn_qlnh_rmis.data.remote;


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

    @POST("auth/login")
    Call<User> login(@Body User user);

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
    // use wrapper to be consistent with other endpoints (menu / tables)
    Call<ApiResponse<List<Order>>> getOrdersByTable(
            @Query("tableNumber") Integer tableNumber,
            @Query("status") String status
    );

    @POST("orders")
    Call<Order> createOrder(@Body Order order);

    @GET("orders/{id}")
    Call<Order> getOrderById(@Path("id") String orderId);

    @PUT("orders/{id}/status")
    Call<Order> updateOrderStatus(@Path("id") String orderId, @Body String newStatus);

    // Update order (partial) - e.g., change tableNumber
    @PUT("orders/{id}")
    Call<Order> updateOrder(@Path("id") String orderId, @Body Map<String, Object> updates);

    // Delete order
    @DELETE("orders/{id}")
    Call<Void> deleteOrder(@Path("id") String orderId);

    // --- TABLE ENDPOINTS ---
    @GET("tables")
    Call<ApiResponse<List<TableItem>>> getAllTables();

    @PUT("tables/{id}")
    Call<TableItem> updateTable(@Path("id") String tableId, @Body Map<String, Object> updates);

    @POST("tables/{id}/merge")
    Call<TableItem> mergeTable(@Path("id") String targetTableId, @Body Map<String, String> body);
}