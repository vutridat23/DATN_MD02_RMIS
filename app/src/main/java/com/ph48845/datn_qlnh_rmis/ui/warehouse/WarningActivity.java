package com.ph48845.datn_qlnh_rmis.ui.warehouse;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Ingredient;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * WarningActivity - Hiển thị danh sách nguyên liệu cảnh báo (VIEW ONLY)
 */
public class WarningActivity extends AppCompatActivity {
    private static final String TAG = "WarningActivity";

    private Toolbar toolbar;
    private TextView tvWarningCount, tvOutOfStockCount, tvLowStockCount;
    private RecyclerView rvWarnings;
    private WarningAdapter adapter;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_warning);

        initViews();
        setupToolbar();
        setupRecyclerView();
        loadWarnings();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvWarningCount = findViewById(R.id.tvWarningCount);
        tvOutOfStockCount = findViewById(R.id.tvOutOfStockCount);
        tvLowStockCount = findViewById(R.id.tvLowStockCount);
        rvWarnings = findViewById(R.id.rvWarnings);

        apiService = RetrofitClient.getInstance().getApiService();
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
    }

    private void setupRecyclerView() {
        adapter = new WarningAdapter(new ArrayList<>());
        rvWarnings.setLayoutManager(new LinearLayoutManager(this));
        rvWarnings.setAdapter(adapter);
    }

    private void loadWarnings() {
        apiService.getWarningIngredients().enqueue(new Callback<ApiResponse<List<Ingredient>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Ingredient>>> call,
                    Response<ApiResponse<List<Ingredient>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Ingredient> warnings = response.body().getData();
                    if (warnings != null) {
                        adapter.updateList(warnings);
                        updateSummary(warnings);
                    }
                } else {
                    Toast.makeText(WarningActivity.this, "Lỗi tải dữ liệu", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Ingredient>>> call, Throwable t) {
                Log.e(TAG, "Error loading warnings", t);
                Toast.makeText(WarningActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateSummary(List<Ingredient> warnings) {
        int outOfStock = 0;
        int lowStock = 0;

        for (Ingredient ingredient : warnings) {
            if ("out_of_stock".equals(ingredient.getStatus())) {
                outOfStock++;
            } else if ("low_stock".equals(ingredient.getStatus())) {
                lowStock++;
            }
        }

        tvWarningCount.setText("Số nguyên liệu cảnh báo: " + warnings.size());
        tvOutOfStockCount.setText("Hết hàng: " + outOfStock);
        tvLowStockCount.setText("Sắp hết: " + lowStock);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
