package com.ph48845.datn_qlnh_rmis.data.repository;

import androidx.annotation.NonNull;


import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;

import java.io.IOException;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository dùng Retrofit để lấy danh sách MenuItem.
 * Bây giờ xử lý response wrapper ApiResponse<List<MenuItem>>.
 */
public class MenuRepository {

    public interface RepositoryCallback<T> {
        void onSuccess(T data);
        void onError(String message);
    }

    private final ApiService apiService;

    public MenuRepository() {
        apiService = RetrofitClient.getApiService();
    }

    public void getAllMenuItems(@NonNull final RepositoryCallback<List<MenuItem>> callback) {
        Call<ApiResponse<List<MenuItem>>> call = apiService.getAllMenuItems();
        call.enqueue(new Callback<ApiResponse<List<MenuItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<MenuItem>>> call, Response<ApiResponse<List<MenuItem>>> response) {
                if (response.isSuccessful()) {
                    ApiResponse<List<MenuItem>> apiResponse = response.body();
                    if (apiResponse != null) {
                        List<MenuItem> items = apiResponse.getData();
                        if (items != null) {
                            callback.onSuccess(items);
                        } else {
                            // Trường hợp body có wrapper nhưng data null
                            String msg = "Server returned empty data";
                            // nếu server có message, thêm vào
                            if (apiResponse.getMessage() != null) msg += ": " + apiResponse.getMessage();
                            callback.onError(msg);
                        }
                    } else {
                        callback.onError("Response body is null");
                    }
                } else {
                    // Lấy raw error body để debug
                    String err = "HTTP " + response.code() + " - " + response.message();
                    try {
                        if (response.errorBody() != null) {
                            err += " - " + response.errorBody().string();
                        }
                    } catch (IOException ignored) {}
                    callback.onError(err);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<MenuItem>>> call, Throwable t) {
                callback.onError(t.getMessage() == null ? "Unknown network error" : t.getMessage());
            }
        });
    }

    public void createMenuItem(MenuItem item, RepositoryCallback<MenuItem> callback) {
        apiService.createMenuItem(item).enqueue(new Callback<MenuItem>() {
            @Override
            public void onResponse(Call<MenuItem> call, Response<MenuItem> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Mã lỗi: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<MenuItem> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

}