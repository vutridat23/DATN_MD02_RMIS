package com.ph48845.datn_qlnh_rmis.data.repository;

import com.ph48845.datn_qlnh_rmis.data.model.User;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;

import retrofit2.Call;
import com.ph48845.datn_qlnh_rmis.data.model.LoginResponse; // Import Wrapper mới

public class AuthRepository {

    private final ApiService apiService;

    public AuthRepository() {
        this.apiService = RetrofitClient.getInstance().getApiService();
    }

    public Call<LoginResponse> login(String username, String password) {

        // 1. Tạo object User để GỬI ĐI (Request Body)
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);

        // 2. Gọi API và nhận về LoginResponse (Response Body)
        return apiService.login(user);
    }
}