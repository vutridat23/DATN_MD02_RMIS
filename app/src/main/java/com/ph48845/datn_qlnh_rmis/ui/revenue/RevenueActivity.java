package com.ph48845.datn_qlnh_rmis.ui.revenue;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.adapter.RevenueAdapter;
import com.ph48845.datn_qlnh_rmis.data.model.RevenueItem;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RevenueActivity extends AppCompatActivity {

    private EditText etFromDate, etToDate;
    private Button btnSearch;
    private RecyclerView rvRevenueDetails;
    private RevenueAdapter adapter;
    private List<RevenueItem> revenueList = new ArrayList<>();
    private TextView tvInvoiceCount, tvTotalRevenue;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_revenue);

        etFromDate = findViewById(R.id.etFromDate);
        etToDate = findViewById(R.id.etToDate);
        btnSearch = findViewById(R.id.btnSearch);
        rvRevenueDetails = findViewById(R.id.rvRevenueDetails);
        tvInvoiceCount = findViewById(R.id.tv_invoice_count);
        tvTotalRevenue = findViewById(R.id.tv_total_revenue);

        adapter = new RevenueAdapter(revenueList);
        rvRevenueDetails.setLayoutManager(new LinearLayoutManager(this));
        rvRevenueDetails.setAdapter(adapter);

        apiService = RetrofitClient.getInstance().getApiService();

        // Chọn ngày
        etFromDate.setOnClickListener(v -> showDatePicker(etFromDate));
        etToDate.setOnClickListener(v -> showDatePicker(etToDate));

        // Tìm kiếm
        btnSearch.setOnClickListener(v -> fetchRevenue());

        // Hiển thị doanh thu toàn bộ khi mở app
        fetchRevenue();
    }

    private void showDatePicker(EditText editText) {
        Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dpd = new DatePickerDialog(this,
                (view, y, m, d) -> editText.setText(String.format("%04d-%02d-%02d", y, m + 1, d)),
                year, month, day);
        dpd.show();
    }

    private void fetchRevenue() {
        String fromDate = etFromDate.getText().toString().trim();
        String toDate = etToDate.getText().toString().trim();

        final boolean isFilter = !(fromDate.isEmpty() && toDate.isEmpty());

        if (!isFilter) {
            fromDate = null;
            toDate = null;
        }

        final String finalFromDate = fromDate;
        final String finalToDate = toDate;

        Call<ApiResponse<List<RevenueItem>>> call = apiService.getRevenueByRange(fromDate, toDate);
        call.enqueue(new Callback<ApiResponse<List<RevenueItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<RevenueItem>>> call, Response<ApiResponse<List<RevenueItem>>> response) {
                revenueList.clear();

                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<RevenueItem> data = response.body().getData();
                    List<RevenueItem> filteredList = new ArrayList<>();

                    for (RevenueItem item : data) {
                        if (item.getDate() == null) continue;

                        if (isFilter && finalFromDate != null && finalToDate != null) {
                            if (item.getDate().compareTo(finalFromDate) < 0 || item.getDate().compareTo(finalToDate) > 0) {
                                continue;
                            }
                        }
                        filteredList.add(item);
                    }

                    revenueList.addAll(filteredList);
                    adapter.notifyDataSetChanged();

                    double totalRevenue = 0;
                    int invoiceCount = 0;
                    for (RevenueItem item : revenueList) {
                        totalRevenue += item.getTotalRevenue();
                        invoiceCount += item.getInvoiceCount();
                    }

                    NumberFormat formatter = NumberFormat.getInstance(new Locale("vi", "VN"));
                    tvTotalRevenue.setText("Tổng doanh thu: " + formatter.format(totalRevenue) + " VND");
                    tvInvoiceCount.setText("Số lượng hóa đơn: " + formatter.format(invoiceCount));

                } else {
                    adapter.notifyDataSetChanged();
                    tvTotalRevenue.setText("Tổng doanh thu: 0 VND");
                    tvInvoiceCount.setText("Số lượng hóa đơn: 0");

                    if (isFilter) {
                        Toast.makeText(RevenueActivity.this, "Không có dữ liệu cho khoảng ngày này", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<RevenueItem>>> call, Throwable t) {
                Toast.makeText(RevenueActivity.this, "Không thể kết nối server", Toast.LENGTH_SHORT).show();
            }
        });
    }


}
