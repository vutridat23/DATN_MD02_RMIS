package com.ph48845.datn_qlnh_rmis.ui.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.LoginResponse;
import com.ph48845.datn_qlnh_rmis.data.model.User;
import com.ph48845.datn_qlnh_rmis.data.repository.AuthRepository; // Import Repository
import com.ph48845.datn_qlnh_rmis.ui.MainActivity;
import com.ph48845.datn_qlnh_rmis.ui.bep.BepActivity;
import com.ph48845.datn_qlnh_rmis.ui.thungan.ThuNganActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername;
    private TextInputEditText etPassword;
    private Button btnLogin;

    // Khai báo Repository
    private AuthRepository authRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Khởi tạo Repository
        authRepository = new AuthRepository();

    //    checkExistingLogin();
        initViews();

        btnLogin.setOnClickListener(v -> handleLogin());
    }

    private void initViews() {
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
    }

    private void handleLogin() {
        String usernameInput = etUsername.getText().toString().trim();
        String passwordInput = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(usernameInput)) {
            etUsername.setError("Vui lòng nhập Tên đăng nhập");
            return;
        }
        if (TextUtils.isEmpty(passwordInput)) {
            etPassword.setError("Vui lòng nhập Mật khẩu");
            return;
        }

        // --- SỬ DỤNG REPOSITORY ---
        // Code cũ: RetrofitClient.getInstance().getApiService().login(user)...
        // Code mới: Gọi qua Repository
        authRepository.login(usernameInput, passwordInput).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<LoginResponse> call, @NonNull Response<LoginResponse> response) {
                // Code xử lý kết quả ở đây
                if (response.isSuccessful() && response.body() != null) {

                    // Hứng kết quả là LoginResponse
                    LoginResponse loginData = response.body();

                    // Tiếp tục logic điều hướng...
                    User currentUser = loginData.getUser();

                    if (currentUser != null && currentUser.getRole() != null) {
                        saveLoginState(currentUser);
                        navigateBasedOnRole(currentUser.getRole());
                    } else {
                        Toast.makeText(LoginActivity.this, "Tài khoản không có quyền truy cập", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(LoginActivity.this, "Sai tài khoản hoặc mật khẩu!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<LoginResponse> call, @NonNull Throwable t) {
                Toast.makeText(LoginActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ... Các hàm saveLoginState, navigateBasedOnRole, checkExistingLogin giữ nguyên như cũ ...
    private void saveLoginState(User user) {
        SharedPreferences prefs = getSharedPreferences("RestaurantPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("isLoggedIn", true);
        editor.putString("userId", user.getId());
        editor.putString("userRole", user.getRole());
        editor.putString("fullName", user.getFullName());
        editor.apply();
    }

    private void navigateBasedOnRole(String role) {
        Intent intent = null;
        if (role == null) role = "";
        String cleanRole = role.trim().toLowerCase();

        switch (cleanRole) {
            case "thungan":
            case "cashier": intent = new Intent(LoginActivity.this, ThuNganActivity.class); break;
            case "phucvu":
            case "order": intent = new Intent(LoginActivity.this, MainActivity.class); break;
            case "kitchen": intent = new Intent(LoginActivity.this, BepActivity.class); break;
            default: intent = new Intent(LoginActivity.this, MainActivity.class); break;
        }
            startActivity(intent);
            finish();
    }

//    private void checkExistingLogin() {
 //       SharedPreferences prefs = getSharedPreferences("RestaurantPrefs", MODE_PRIVATE);
//        if (prefs.getBoolean("isLoggedIn", false)) {
//            navigateBasedOnRole(prefs.getString("userRole", ""));
 //       }
 //   }
}