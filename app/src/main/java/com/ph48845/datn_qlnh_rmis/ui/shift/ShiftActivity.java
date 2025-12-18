package com.ph48845.datn_qlnh_rmis.ui.shift;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Shift;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * ShiftActivity - Hiển thị danh sách ca làm việc với nhân viên (VIEW ONLY)
 */
public class ShiftActivity extends AppCompatActivity {
    private static final String TAG = "ShiftActivity";

    private Toolbar toolbar;
    private EditText etDate;
    private Button btnSearch;
    private RecyclerView rvShifts;
    private ShiftAdapter adapter;
    private ApiService apiService;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private SimpleDateFormat displayFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private Calendar selectedDate = Calendar.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shift);

        initViews();
        setupToolbar();
        setupRecyclerView();
        setupDatePicker();
        loadShifts();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        etDate = findViewById(R.id.etDate);
        btnSearch = findViewById(R.id.btnSearch);
        rvShifts = findViewById(R.id.rvShifts);

        apiService = RetrofitClient.getInstance().getApiService();

        // Set today's date
        etDate.setText(displayFormat.format(selectedDate.getTime()));
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
    }

    private void setupRecyclerView() {
        adapter = new ShiftAdapter(new ArrayList<>());
        rvShifts.setLayoutManager(new LinearLayoutManager(this));
        rvShifts.setAdapter(adapter);
    }

    private void setupDatePicker() {
        etDate.setOnClickListener(v -> showDatePicker());
        btnSearch.setOnClickListener(v -> loadShifts());
    }

    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedDate.set(year, month, dayOfMonth);
                    etDate.setText(displayFormat.format(selectedDate.getTime()));
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.show();
    }

    private void loadShifts() {
        String date = dateFormat.format(selectedDate.getTime());
        Map<String, String> params = new HashMap<>();
        params.put("date", date);

        apiService.getAllShifts(params).enqueue(new Callback<ApiResponse<List<Shift>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Shift>>> call, Response<ApiResponse<List<Shift>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Shift> shifts = response.body().getData();
                    if (shifts != null && !shifts.isEmpty()) {
                        adapter.updateList(shifts);
                    } else {
                        adapter.updateList(new ArrayList<>());
                        Toast.makeText(ShiftActivity.this, "Không có ca làm việc trong ngày này", Toast.LENGTH_SHORT)
                                .show();
                    }
                } else {
                    Toast.makeText(ShiftActivity.this, "Lỗi tải dữ liệu", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Shift>>> call, Throwable t) {
                Log.e(TAG, "Error loading shifts", t);
                Toast.makeText(ShiftActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
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
