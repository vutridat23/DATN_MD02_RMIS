package com.ph48845.datn_qlnh_rmis.ui.revenue;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.adapter.RevenueAdapter;
import com.ph48845.datn_qlnh_rmis.data.model.RevenueItem;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RevenueActivity extends AppCompatActivity {

    private EditText etFromDate, etToDate;
    private Button btnSearch;
    private TextView tvInvoiceCount, tvTotalRevenue;
    private RecyclerView rvRevenueDetails;
    private RevenueAdapter revenueAdapter;
    private List<RevenueItem> revenueList = new ArrayList<>();

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_revenue);

        etFromDate = findViewById(R.id.etFromDate);
        etToDate = findViewById(R.id.etToDate);
        btnSearch = findViewById(R.id.btnSearch);
        tvInvoiceCount = findViewById(R.id.tv_invoice_count);
        tvTotalRevenue = findViewById(R.id.tv_total_revenue);
        rvRevenueDetails = findViewById(R.id.rvRevenueDetails);

        revenueAdapter = new RevenueAdapter(revenueList);
        rvRevenueDetails.setLayoutManager(new LinearLayoutManager(this));
        rvRevenueDetails.setAdapter(revenueAdapter);

        etFromDate.setOnClickListener(v -> showDatePicker(etFromDate));
        etToDate.setOnClickListener(v -> showDatePicker(etToDate));
        btnSearch.setOnClickListener(v -> fetchRevenueByRange());

        // Load doanh thu toàn bộ khi vào màn hình
        fetchRevenueAll();
    }

    private void showDatePicker(EditText editText) {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    editText.setText(dateFormat.format(calendar.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    // Lấy tất cả doanh thu
    private void fetchRevenueAll() {
        ApiService apiService = RetrofitClient.getInstance().getApiService();
        // Thay đổi Callback khi fetchRevenueAll()

        apiService.getRevenueFromOrders(new HashMap<>()).enqueue(new Callback<ApiResponse<List<RevenueItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<RevenueItem>>> call, Response<ApiResponse<List<RevenueItem>>> response) {
                if(response.isSuccessful() && response.body() != null && response.body().isSuccess()){
                    updateRevenueList(response.body().getData());
                } else {
                    Toast.makeText(RevenueActivity.this, "Không có dữ liệu", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<RevenueItem>>> call, Throwable t) {
                Toast.makeText(RevenueActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });


    }

    // Lấy doanh thu theo khoảng ngày
    private void fetchRevenueByRange() {
        String fromDate = etFromDate.getText().toString();
        String toDate = etToDate.getText().toString();

        if (fromDate.isEmpty() || toDate.isEmpty()) {
            Toast.makeText(this, "Vui lòng chọn đầy đủ ngày", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, String> params = new HashMap<>();
        params.put("fromDate", fromDate);
        params.put("toDate", toDate);

        ApiService apiService = RetrofitClient.getInstance().getApiService();
        apiService.getRevenueByDate(params).enqueue(new Callback<ApiResponse<List<RevenueItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<RevenueItem>>> call, Response<ApiResponse<List<RevenueItem>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    updateRevenueList(response.body().getData());
                } else {
                    Toast.makeText(RevenueActivity.this, "Không có dữ liệu", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<RevenueItem>>> call, Throwable t) {
                Toast.makeText(RevenueActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Cập nhật RecyclerView và tổng tóm tắt
    private void updateRevenueList(List<RevenueItem> list) {
        revenueList.clear();
        if (list != null) revenueList.addAll(list);
        revenueAdapter.notifyDataSetChanged();

        double totalRevenue = 0;
        int totalInvoices = 0;
        for (RevenueItem item : revenueList) {
            totalRevenue += item.getTotalAmount();
            totalInvoices += item.getTotalOrders();
        }
        tvTotalRevenue.setText(String.format("Tổng doanh thu: %.0f VND", totalRevenue));
        tvInvoiceCount.setText("Số lượng hóa đơn: " + totalInvoices);
    }
}
