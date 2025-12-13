package com.ph48845.datn_qlnh_rmis.ui.revenue;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import androidx.appcompat.widget.Toolbar;


import com.ph48845.datn_qlnh_rmis.data.model.ReportItem;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;
import com.ph48845.datn_qlnh_rmis.ui.revenue.adapter.ReportAdapter;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.text.SimpleDateFormat;

public class ReportActivity extends AppCompatActivity {

    private EditText etFromDate, etToDate;
    private Button btnSearch;
    private RecyclerView rvReports;
    private ReportAdapter adapter;
    private List<ReportItem> reportList = new ArrayList<>();
    private ApiService apiService;
    private TextView tvInvoiceCount, tvTotalRevenue;

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_revenue);

        etFromDate = findViewById(R.id.etFromDate);
        etToDate = findViewById(R.id.etToDate);
        btnSearch = findViewById(R.id.btnSearch);
        rvReports = findViewById(R.id.rvRevenueDetails);

        // Thêm TextView tổng hợp từ layout
        tvInvoiceCount = findViewById(R.id.tv_invoice_count);
        tvTotalRevenue = findViewById(R.id.tv_total_revenue);

        adapter = new ReportAdapter(reportList);
        rvReports.setLayoutManager(new LinearLayoutManager(this));
        rvReports.setAdapter(adapter);

        apiService = RetrofitClient.getInstance().getApiService();

        etFromDate.setOnClickListener(v -> showDatePicker(etFromDate));
        etToDate.setOnClickListener(v -> showDatePicker(etToDate));

        btnSearch.setOnClickListener(v -> fetchReportsByDate());
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        toolbar.setNavigationOnClickListener(v -> {
            finish(); // Quay về màn hình trước
        });


        // Load tất cả báo cáo khi mở activity
        fetchAllReports();
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
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void fetchAllReports() {
        Call<ApiResponse<List<ReportItem>>> call = apiService.getAllReports();
        call.enqueue(new Callback<ApiResponse<List<ReportItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<ReportItem>>> call, Response<ApiResponse<List<ReportItem>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    reportList.clear();
                    reportList.addAll(mergeReports(response.body().getData()));
                    adapter.notifyDataSetChanged();
                    updateSummary(reportList);
                    // cập nhật tổng hợp
                } else {
                    Toast.makeText(ReportActivity.this, "Không lấy được danh sách báo cáo", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<ReportItem>>> call, Throwable t) {
                t.printStackTrace();
                Toast.makeText(ReportActivity.this, "Lỗi kết nối server", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchReportsByDate() {
        String fromDate = etFromDate.getText().toString().trim();
        String toDate = etToDate.getText().toString().trim();

        if (fromDate.isEmpty() || toDate.isEmpty()) {
            Toast.makeText(this, "Vui lòng chọn đủ khoảng ngày", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, String> params = new HashMap<>();
        params.put("startDate", fromDate); // trùng với backend
        params.put("endDate", toDate);

        Call<ApiResponse<List<ReportItem>>> call = apiService.getReportsByDate(params);
        call.enqueue(new Callback<ApiResponse<List<ReportItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<ReportItem>>> call, Response<ApiResponse<List<ReportItem>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    reportList.clear();
                    reportList.addAll(mergeReports(response.body().getData()));
                    adapter.notifyDataSetChanged();
                    updateSummary(reportList);
                    // cập nhật tổng hợp
                } else {
                    Toast.makeText(ReportActivity.this, "Không tìm thấy báo cáo trong khoảng ngày", Toast.LENGTH_SHORT).show();
                    tvInvoiceCount.setText("Số lượng hóa đơn: 0");
                    tvTotalRevenue.setText("0 VND");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<ReportItem>>> call, Throwable t) {
                t.printStackTrace();
                Toast.makeText(ReportActivity.this, "Lỗi kết nối server", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Tính tổng hóa đơn và tổng doanh thu
    private void updateSummary(List<ReportItem> list) {
        int totalOrders = 0;
        double totalRevenue = 0;

        for (ReportItem item : list) {
            totalOrders += item.getTotalOrders();
            totalRevenue += item.getTotalRevenue();
        }

        tvInvoiceCount.setText("Số lượng hóa đơn: " + totalOrders);

        NumberFormat formatter = NumberFormat.getInstance(Locale.US);
        String revenueStr = formatter.format(totalRevenue);
        tvTotalRevenue.setText(revenueStr + " VND");
    }
    // Gộp báo cáo cùng ngày + loại bỏ báo cáo không có đơn
    // Gộp báo cáo cùng ngày + loại bỏ báo cáo không có đơn
    private List<ReportItem> mergeReports(List<ReportItem> rawList) {
        Map<String, ReportItem> map = new HashMap<>();

        for (ReportItem item : rawList) {

            // Bỏ ngày không có đơn
            if (item.getTotalOrders() == 0 || item.getTotalRevenue() == 0) {
                continue;
            }

            // Format date từ Date -> String yyyy-MM-dd
            String date = sdf.format(item.getDate());

            if (!map.containsKey(date)) {
                map.put(date, item);
            } else {
                // Gộp nếu trùng ngày
                ReportItem exist = map.get(date);

                exist.setTotalOrders(exist.getTotalOrders() + item.getTotalOrders());
                exist.setTotalRevenue(exist.getTotalRevenue() + item.getTotalRevenue());
            }
        }

        // Chuyển map -> list
        List<ReportItem> mergedList = new ArrayList<>(map.values());

        // Sắp xếp giảm dần theo ngày
        mergedList.sort((a, b) -> b.getDate().compareTo(a.getDate()));

        return mergedList;
    }



}
