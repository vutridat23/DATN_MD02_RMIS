package com.ph48845.datn_qlnh_rmis.data.repository;

import android.util.Log;

import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrderRepository {

    private final ApiService apiService;

    public OrderRepository() {
        apiService = RetrofitClient.getApiService();
    }

    // Interface callback để trả kết quả về cho Activity
    public interface RepositoryCallback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    // Gửi Order mới lên server
    public void createOrder(Order order, RepositoryCallback<Order> callback) {
        apiService.createOrder(order).enqueue(new Callback<Order>() {
            @Override
            public void onResponse(Call<Order> call, Response<Order> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Mã lỗi: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Order> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }


}
