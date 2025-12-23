package com.ph48845.datn_qlnh_rmis.ui.thungan;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.HistoryItem;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;
import com.ph48845.datn_qlnh_rmis.ui.thungan.adapter.HistoryAdapter;

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

public class HistoryActivity extends AppCompatActivity {

    private RecyclerView rvHistory;
    private HistoryAdapter adapter;
    private List<HistoryItem> historyList = new ArrayList<>();
    private ApiService apiService;

    private EditText etFromDate, etToDate;
    private Button btnSearch, btnClearFilter;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        initViews();
        setupToolbar();

        adapter = new HistoryAdapter(historyList);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        rvHistory.setAdapter(adapter);

        // Setup date pickers
        etFromDate.setOnClickListener(v -> showDatePicker(etFromDate));
        etToDate.setOnClickListener(v -> showDatePicker(etToDate));

        // Setup search button
        btnSearch.setOnClickListener(v -> fetchHistoryByDate());

        // Setup clear filter button
        btnClearFilter.setOnClickListener(v -> {
            etFromDate.setText("");
            etToDate.setText("");
            fetchAllHistory();
            Toast.makeText(HistoryActivity.this, "Đã bỏ lọc", Toast.LENGTH_SHORT).show();
        });

        // Load all history initially
        fetchAllHistory();
    }

    private void initViews() {
        rvHistory = findViewById(R.id.rvHistory);
        etFromDate = findViewById(R.id.etFromDate);
        etToDate = findViewById(R.id.etToDate);
        btnSearch = findViewById(R.id.btnSearch);
        btnClearFilter = findViewById(R.id.btnClearFilter);
        apiService = RetrofitClient.getInstance().getApiService();
    }

    private void setupToolbar() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
        }
    }

    private void showDatePicker(final EditText editText) {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    editText.setText(sdf.format(calendar.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        datePickerDialog.show();
    }

    private void fetchAllHistory() {
        // Lấy TẤT CẢ hóa đơn (không filter theo status)
        Map<String, String> queryMap = new HashMap<>();
        // Không thêm status filter để lấy tất cả

        Call<ApiResponse<List<HistoryItem>>> call = apiService.getAllHistory(queryMap);
        call.enqueue(new Callback<ApiResponse<List<HistoryItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<HistoryItem>>> call,
                                   Response<ApiResponse<List<HistoryItem>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<HistoryItem> data = response.body().getData();
                    if (data != null && !data.isEmpty()) {
                        historyList.clear();
                        historyList.addAll(data);
                        adapter.notifyDataSetChanged();
                    } else {
                        historyList.clear();
                        adapter.notifyDataSetChanged();
                        Toast.makeText(HistoryActivity.this, "Không có dữ liệu lịch sử", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(HistoryActivity.this, "Lấy dữ liệu thất bại", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<HistoryItem>>> call, Throwable t) {
                t.printStackTrace();
                Toast.makeText(HistoryActivity.this, "Không thể kết nối server", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchHistoryByDate() {
        String fromDate = etFromDate.getText().toString().trim();
        String toDate = etToDate.getText().toString().trim();

        if (fromDate.isEmpty() || toDate.isEmpty()) {
            Toast.makeText(this, "Vui lòng chọn đủ khoảng ngày", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, String> queryMap = new HashMap<>();
        queryMap.put("startDate", fromDate);
        queryMap.put("endDate", toDate);
        // Không thêm status filter để lấy tất cả hóa đơn trong khoảng ngày

        Call<ApiResponse<List<HistoryItem>>> call = apiService.getAllHistory(queryMap);
        call.enqueue(new Callback<ApiResponse<List<HistoryItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<HistoryItem>>> call,
                                   Response<ApiResponse<List<HistoryItem>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<HistoryItem> data = response.body().getData();
                    if (data != null && !data.isEmpty()) {
                        historyList.clear();
                        historyList.addAll(data);
                        adapter.notifyDataSetChanged();
                    } else {
                        historyList.clear();
                        adapter.notifyDataSetChanged();
                        Toast.makeText(HistoryActivity.this, "Không tìm thấy hóa đơn trong khoảng ngày",
                                Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(HistoryActivity.this, "Lấy dữ liệu thất bại", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<HistoryItem>>> call, Throwable t) {
                t.printStackTrace();
                Toast.makeText(HistoryActivity.this, "Không thể kết nối server", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    @Deprecated
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}
