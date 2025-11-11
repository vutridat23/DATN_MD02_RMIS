package com.ph48845.datn_qlnh_rmis.data.remote;

import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.User;
import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;


import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.DELETE;
import retrofit2.http.Path;
import retrofit2.http.Query;

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
    // Trả về ApiResponse<List<MenuItem>> để match các API trả wrapper JSON
    @GET("menu")
    Call<ApiResponse<List<MenuItem>>> getAllMenuItems();

    @POST("menu")
    Call<MenuItem> createMenuItem(@Body MenuItem menuItem);

    @DELETE("menu/{id}")
    Call<Void> deleteMenuItem(@Path("id") String itemId);

    // --- ORDER ENDPOINTS ---
    @GET("orders")
    Call<List<Order>> getOrdersByStatus(@Query("status") String orderStatus);

    @POST("orders")
    Call<Order> createOrder(@Body Order order);

    @GET("orders/{id}")
    Call<Order> getOrderById(@Path("id") String orderId);

    @PUT("orders/{id}/status")
    Call<Order> updateOrderStatus(@Path("id") String orderId, @Body String newStatus);
}