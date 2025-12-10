package com.ph48845.datn_qlnh_rmis.ui.thungan;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.HistoryItem;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;
import com.ph48845.datn_qlnh_rmis.ui.thungan.adapter.HistoryAdapter;

import java.text.NumberFormat;
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

    // Views
    private EditText edtStartDate, edtEndDate;
    private Button btnSearch;
    private TextView tvTotalRevenue, tvTotalInvoices;
    private RecyclerView rvHistory;

    // Data & Adapter
    private HistoryAdapter adapter;
    private List<HistoryItem> historyList = new ArrayList<>();
    private ApiService apiService;

    // Date Format
    private final SimpleDateFormat displayDateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    // Format gửi lên server (thường là yyyy-MM-dd), bạn chỉnh lại nếu server yêu cầu khác
    private final SimpleDateFormat serverDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private Calendar calendar = Calendar.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // 1. Khởi tạo Views
        initViews();

        // 2. Setup Toolbar (Không dùng setSupportActionBar để tránh lỗi icon)
        setupToolbar();

        // 3. Setup RecyclerView
        setupRecyclerView();

        // 4. API & Listeners
        apiService = RetrofitClient.getInstance().getApiService();
        setupListeners();

        // 5. Load dữ liệu ban đầu (Load tất cả hoặc theo ngày hiện tại tùy logic)
        fetchHistory(null, null);
    }

    private void initViews() {
        edtStartDate = findViewById(R.id.edt_start_date);
        edtEndDate = findViewById(R.id.edt_end_date);
        btnSearch = findViewById(R.id.btn_search);

        tvTotalRevenue = findViewById(R.id.tv_total_revenue);
        tvTotalInvoices = findViewById(R.id.tv_total_invoices);

        rvHistory = findViewById(R.id.recycler_history_list);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        // Xử lý sự kiện nút back
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new HistoryAdapter(historyList);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        rvHistory.setAdapter(adapter);
    }

    private void setupListeners() {
        // Sự kiện chọn ngày Bắt đầu
        edtStartDate.setOnClickListener(v -> showDatePicker(edtStartDate));

        // Sự kiện chọn ngày Kết thúc
        edtEndDate.setOnClickListener(v -> showDatePicker(edtEndDate));

        // Sự kiện nút Tìm kiếm
        btnSearch.setOnClickListener(v -> {
            String start = edtStartDate.getText().toString();
            String end = edtEndDate.getText().toString();

            // Chuyển đổi định dạng ngày nếu cần thiết trước khi gọi API
            // Ở đây mình tạm truyền string gốc, bạn có thể parse sang format server cần
            fetchHistory(start, end);
        });
    }

    private void showDatePicker(EditText targetEditText) {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    targetEditText.setText(displayDateFormat.format(calendar.getTime()));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void fetchHistory(String startDate, String endDate) {
        Map<String, String> queryMap = new HashMap<>();

        // Nếu có chọn ngày thì gửi lên server
        // Lưu ý: Cần đảm bảo format ngày gửi lên đúng với Backend yêu cầu
        if (startDate != null && !startDate.isEmpty()) {
            queryMap.put("startDate", convertDateForServer(startDate));
        }
        if (endDate != null && !endDate.isEmpty()) {
            queryMap.put("endDate", convertDateForServer(endDate));
        }

        Call<ApiResponse<List<HistoryItem>>> call = apiService.getAllHistory(queryMap);

        call.enqueue(new Callback<ApiResponse<List<HistoryItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<HistoryItem>>> call,
                                   Response<ApiResponse<List<HistoryItem>>> response) {

                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<HistoryItem> data = response.body().getData();
                    historyList.clear();

                    if (data != null) {
                        historyList.addAll(data);
                    }

                    adapter.notifyDataSetChanged();

                    // --- QUAN TRỌNG: Cập nhật tổng doanh thu ---
                    updateSummary(historyList);

                } else {
                    Toast.makeText(HistoryActivity.this, "Không lấy được dữ liệu", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<HistoryItem>>> call, Throwable t) {
                Toast.makeText(HistoryActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Tính tổng doanh thu và cập nhật lên giao diện
     */
    private void updateSummary(List<HistoryItem> items) {
        double totalRevenue = 0;
        for (HistoryItem item : items) {
            if (item.getTotalAmount() != null) {
                totalRevenue += item.getTotalAmount();
            }
        }

        // Format tiền tệ Việt Nam
        NumberFormat vnFormat = NumberFormat.getInstance(new Locale("vi", "VN"));
        String formattedRevenue = vnFormat.format(totalRevenue) + " VND";

        tvTotalRevenue.setText(formattedRevenue);
        tvTotalInvoices.setText("Tổng số hóa đơn: " + items.size());
    }

    /**
     * Helper: Chuyển ngày từ dd/MM/yyyy (hiển thị) sang yyyy-MM-dd (server thường dùng)
     */
    private String convertDateForServer(String dateString) {
        try {
            return serverDateFormat.format(displayDateFormat.parse(dateString));
        } catch (Exception e) {
            return dateString; // Nếu lỗi trả về nguyên gốc
        }
    }
}