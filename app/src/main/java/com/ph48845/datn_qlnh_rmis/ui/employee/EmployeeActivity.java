package com.ph48845.datn_qlnh_rmis.ui.employee;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.User;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * EmployeeActivity - Hiển thị danh sách nhân viên (VIEW ONLY)
 */
public class EmployeeActivity extends AppCompatActivity {
    private static final String TAG = "EmployeeActivity";

    private Toolbar toolbar;
    private RecyclerView rvEmployees;
    private EmployeeAdapter adapter;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employee);

        initViews();
        setupToolbar();
        setupRecyclerView();
        loadEmployees();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        rvEmployees = findViewById(R.id.rvEmployees);

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
        adapter = new EmployeeAdapter(new ArrayList<>(), this);
        rvEmployees.setLayoutManager(new LinearLayoutManager(this));
        rvEmployees.setAdapter(adapter);
    }

    private void loadEmployees() {
        apiService.getAllUsers().enqueue(new Callback<ApiResponse<List<User>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<User>>> call, Response<ApiResponse<List<User>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<User> employees = response.body().getData();
                    if (employees != null && !employees.isEmpty()) {
                        adapter.updateList(employees);
                    } else {
                        Toast.makeText(EmployeeActivity.this, "Không có nhân viên", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(EmployeeActivity.this, "Lỗi tải dữ liệu", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<User>>> call, Throwable t) {
                Log.e(TAG, "Error loading employees", t);
                Toast.makeText(EmployeeActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
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
