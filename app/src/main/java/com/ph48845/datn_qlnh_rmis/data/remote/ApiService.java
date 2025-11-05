package com.ph48845.datn_qlnh_rmis.data.remote;

import com.ph48845.datn_qlnh_rmis.data.respository.MenuRepository;
import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;
import com.ph48845.datn_qlnh_rmis.data.model.User;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ApiService {
    // Example endpoints — adjust to your backend
    @GET("menu")
    Call<MenuRepository> getMenu();

    @GET("menu/{id}")
    Call<MenuItem> getMenuItem(@Path("id") String id);

    @POST("auth/login")
    Call<User> login(@Body User loginBody);

    @POST("order")
    Call<Void> createOrder(@Body Object orderPayload);
}
