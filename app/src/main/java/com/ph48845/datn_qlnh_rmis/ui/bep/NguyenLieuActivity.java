package com.ph48845.datn_qlnh_rmis.ui.bep;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Ingredient;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;
import com.ph48845.datn_qlnh_rmis.ui.bep.adapter.IngredientAdapter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NguyenLieuActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private ProgressBar progress;
    private IngredientAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_nguyen_lieu);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        recycler = findViewById(R.id.recyclerIngredients);
        progress = findViewById(R.id.progress);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new IngredientAdapter(this, (ingredient, amount, position) -> {
            // gọi API take (amount là double)
            takeIngredient(ingredient, amount, position);
        });
        recycler.setAdapter(adapter);

        loadIngredients();
    }

    private void loadIngredients() {
        progress.setVisibility(View.VISIBLE);
        ApiService api = RetrofitClient.getInstance().getApiService();
        // gọi với null filters để lấy tất cả
        Call<ApiResponse<List<Ingredient>>> call = api.getAllIngredients(null, null);
        call.enqueue(new Callback<ApiResponse<List<Ingredient>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Ingredient>>> call, Response<ApiResponse<List<Ingredient>>> response) {
                progress.setVisibility(View.GONE);
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(NguyenLieuActivity.this, "Lỗi khi lấy nguyên liệu: HTTP " + response.code(), Toast.LENGTH_SHORT).show();
                    return;
                }
                ApiResponse<List<Ingredient>> body = response.body();
                if (!body.isSuccess()) {
                    Toast.makeText(NguyenLieuActivity.this, "API lỗi: " + body.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }
                adapter.setItems(body.getData());
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Ingredient>>> call, Throwable t) {
                progress.setVisibility(View.GONE);
                Toast.makeText(NguyenLieuActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void takeIngredient(Ingredient ingredient, double amount, int position) {
        ApiService api = RetrofitClient.getInstance().getApiService();
        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount); // now a double
        Call<ApiResponse<Ingredient>> call = api.takeIngredient(ingredient.getId(), body);
        // show progress for button? adapter disables button already
        call.enqueue(new Callback<ApiResponse<Ingredient>>() {
            @Override
            public void onResponse(Call<ApiResponse<Ingredient>> call, Response<ApiResponse<Ingredient>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(NguyenLieuActivity.this, "Lỗi: HTTP " + response.code(), Toast.LENGTH_SHORT).show();
                    // reload to get fresh data
                    loadIngredients();
                    return;
                }
                ApiResponse<Ingredient> resp = response.body();
                if (!resp.isSuccess()) {
                    Toast.makeText(NguyenLieuActivity.this, "API: " + resp.getMessage(), Toast.LENGTH_SHORT).show();
                    loadIngredients();
                    return;
                }
                Ingredient updated = resp.getData();
                // cập nhật item UI
                adapter.updateItem(position, updated);
                // show notification nếu server trả notification thông qua message field
                if (resp.getMessage() != null && !resp.getMessage().isEmpty()) {
                    Toast.makeText(NguyenLieuActivity.this, resp.getMessage(), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(NguyenLieuActivity.this, "Đã lấy thành công", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Ingredient>> call, Throwable t) {
                Toast.makeText(NguyenLieuActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                loadIngredients();
            }
        });
    }
}