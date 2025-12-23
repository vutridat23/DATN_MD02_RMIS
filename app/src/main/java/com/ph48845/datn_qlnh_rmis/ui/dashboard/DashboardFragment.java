package com.ph48845.datn_qlnh_rmis.ui.dashboard;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.DashboardData;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;

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

public class DashboardFragment extends Fragment {

    private TextView tvTodayRevenue, tvTodayOrders;
    private TextView tvServingTables, tvWaitingPayment, tvServingInvoices, tvActiveStaff;
    private TextView tvTotalTables, tvAvailableTables, tvReservedTables;
    private BarChart barChart;

    private ApiService apiService;
    private OrderRepository orderRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);

        apiService = RetrofitClient.getInstance().getApiService();
        orderRepository = new OrderRepository();

        loadDashboardData();
    }

    private void initViews(View view) {
        // Doanh thu
        tvTodayRevenue = view.findViewById(R.id.tvTodayRevenue);
        tvTodayOrders = view.findViewById(R.id.tvTodayOrders);

        // Thống kê chính
        tvServingTables = view.findViewById(R.id.tvServingTables);
        tvWaitingPayment = view.findViewById(R.id.tvWaitingPayment);
        tvServingInvoices = view.findViewById(R.id.tvServingInvoices);
        tvActiveStaff = view.findViewById(R.id.tvActiveStaff);

        // Thống kê bàn
        tvTotalTables = view.findViewById(R.id.tvTotalTables);
        tvAvailableTables = view.findViewById(R.id.tvAvailableTables);
        tvReservedTables = view.findViewById(R.id.tvReservedTables);

        // Chart
        barChart = view.findViewById(R.id.barChart);
        setupBarChart();
    }

    private void setupBarChart() {
        if (barChart == null)
            return;

        // Chart styling
        barChart.getDescription().setEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.setDrawBarShadow(false);
        barChart.setHighlightFullBarEnabled(false);
        barChart.setDrawValueAboveBar(true);
        barChart.setPinchZoom(false);
        barChart.setScaleEnabled(false);

        // X Axis
        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(Color.parseColor("#757575"));
        xAxis.setTextSize(10f);

        // Y Axis Left
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(Color.parseColor("#E0E0E0"));
        leftAxis.setTextColor(Color.parseColor("#757575"));
        leftAxis.setAxisMinimum(0f);
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (value >= 1000000) {
                    return String.format(Locale.US, "%.1fM", value / 1000000);
                } else if (value >= 1000) {
                    return String.format(Locale.US, "%.0fK", value / 1000);
                }
                return String.format(Locale.US, "%.0f", value);
            }
        });

        // Y Axis Right (disable)
        YAxis rightAxis = barChart.getAxisRight();
        rightAxis.setEnabled(false);

        // Legend
        barChart.getLegend().setEnabled(false);

        // Animation
        barChart.animateY(1000);
    }

    private void loadDashboardData() {
        // Load dashboard statistics
        Call<ApiResponse<DashboardData>> dashboardCall = apiService.getServiceDashboard();
        dashboardCall.enqueue(new Callback<ApiResponse<DashboardData>>() {
            @Override
            public void onResponse(Call<ApiResponse<DashboardData>> call,
                                   Response<ApiResponse<DashboardData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    DashboardData data = response.body().getData();
                    updateDashboardUI(data);
                }
                loadTableStatistics();
                load7DaysRevenue();
            }

            @Override
            public void onFailure(Call<ApiResponse<DashboardData>> call, Throwable t) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void loadTableStatistics() {
        Call<ApiResponse<List<TableItem>>> tablesCall = apiService.getAllTables();
        tablesCall.enqueue(new Callback<ApiResponse<List<TableItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<TableItem>>> call,
                                   Response<ApiResponse<List<TableItem>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<TableItem> tables = response.body().getData();
                    updateTableStatistics(tables);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<TableItem>>> call, Throwable t) {
                // Silent fail
            }
        });

        loadTodayRevenue();
    }

    /**
     * Load doanh thu hôm nay - SỬ DỤNG HISTORY API (action='pay')
     * Thay vì lấy từ Order, giờ lấy từ History để đảm bảo dữ liệu chính xác
     */
    private void loadTodayRevenue() {
        Map<String, String> filters = new HashMap<>();
        filters.put("action", "pay");

        apiService.getAllHistory(filters).enqueue(new Callback<ApiResponse<List<com.ph48845.datn_qlnh_rmis.data.model.HistoryItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<com.ph48845.datn_qlnh_rmis.data.model.HistoryItem>>> call,
                                   Response<ApiResponse<List<com.ph48845.datn_qlnh_rmis.data.model.HistoryItem>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<com.ph48845.datn_qlnh_rmis.data.model.HistoryItem> histories = response.body().getData();
                    if (getActivity() != null && histories != null) {
                        getActivity().runOnUiThread(() -> {
                            double totalRevenue = 0;
                            int paidCount = 0;

                            // Lấy thời điểm bắt đầu ngày hôm nay (00:00:00)
                            Calendar today = Calendar.getInstance();
                            today.set(Calendar.HOUR_OF_DAY, 0);
                            today.set(Calendar.MINUTE, 0);
                            today.set(Calendar.SECOND, 0);
                            today.set(Calendar.MILLISECOND, 0);

                            // CẬP NHẬT: Xử lý nhiều định dạng ngày tháng
                            // Định dạng 1: Có mili-giây và Z (Ví dụ: 2025-12-24T10:00:00.123Z)
                            SimpleDateFormat sdfIso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
                            sdfIso.setTimeZone(java.util.TimeZone.getTimeZone("UTC")); // Server thường trả về UTC

                            // Định dạng 2: Không có mili-giây (Ví dụ: 2025-12-24T10:00:00)
                            SimpleDateFormat sdfSimple = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());

                            for (com.ph48845.datn_qlnh_rmis.data.model.HistoryItem history : histories) {
                                if (history.getCreatedAt() != null) {
                                    try {
                                        java.util.Date createdDate;
                                        // Thử parse theo chuẩn ISO 8601 (có .SSSZ) trước
                                        try {
                                            createdDate = sdfIso.parse(history.getCreatedAt());
                                        } catch (Exception e1) {
                                            // Nếu lỗi, thử parse theo kiểu đơn giản
                                            createdDate = sdfSimple.parse(history.getCreatedAt());
                                        }

                                        if (createdDate != null && createdDate.after(today.getTime())) {
                                            // Kiểm tra null cho details
                                            if (history.getDetails() != null) {
                                                totalRevenue += history.getDetails().getFinalAmount();
                                                paidCount++;
                                            } else {
                                                // Log để biết nếu details bị null
                                                android.util.Log.e("DEBUG_REVENUE", "ID: " + history.getId() + " có details là NULL");
                                            }
                                        }
                                    } catch (Exception e) {
                                        // In lỗi ra Logcat để xem chuỗi ngày thực tế là gì
                                        android.util.Log.e("DEBUG_REVENUE", "Lỗi parse ngày: " + history.getCreatedAt());
                                        e.printStackTrace();
                                    }
                                }
                            }

                            NumberFormat formatter = NumberFormat.getInstance(Locale.US);
                            tvTodayRevenue.setText(formatter.format(totalRevenue) + " VND");
                            tvTodayOrders.setText(paidCount + " hóa đơn đã thanh toán");
                        });
                    }
                } else {
                    // Xử lý khi response không thành công
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            tvTodayRevenue.setText("0 VND");
                            tvTodayOrders.setText("0 hóa đơn (API Error)");
                        });
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<com.ph48845.datn_qlnh_rmis.data.model.HistoryItem>>> call, Throwable t) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        tvTodayRevenue.setText("0 VND");
                        tvTodayOrders.setText("Lỗi mạng");
                    });
                }
            }
        });
    }

    /**
     * Load doanh thu 7 ngày - SỬ DỤNG HISTORY API (action='pay')
     * Thay vì lấy từ Order, giờ lấy từ History để đảm bảo dữ liệu chính xác
     */
    private void load7DaysRevenue() {
        // Sử dụng History API với filter action='pay'
        java.util.Map<String, String> filters = new java.util.HashMap<>();
        filters.put("action", "pay");

        apiService.getAllHistory(filters).enqueue(new Callback<ApiResponse<List<com.ph48845.datn_qlnh_rmis.data.model.HistoryItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<com.ph48845.datn_qlnh_rmis.data.model.HistoryItem>>> call,
                                   Response<ApiResponse<List<com.ph48845.datn_qlnh_rmis.data.model.HistoryItem>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<com.ph48845.datn_qlnh_rmis.data.model.HistoryItem> histories = response.body().getData();
                    if (getActivity() != null && histories != null) {
                        getActivity().runOnUiThread(() -> {
                            Map<String, Double> dailyRevenue = new HashMap<>();
                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

                            // 1. Khởi tạo danh sách 7 ngày qua
                            List<String> dates = new ArrayList<>();
                            Calendar cal = Calendar.getInstance();
                            for (int i = 6; i >= 0; i--) {
                                Calendar tempCal = Calendar.getInstance();
                                tempCal.add(Calendar.DAY_OF_YEAR, -i);
                                String dateKey = dateFormat.format(tempCal.getTime());
                                dates.add(dateKey);
                                dailyRevenue.put(dateKey, 0.0);
                            }

                            // 2. Định nghĩa Parser chuẩn ISO-8601 (Quan trọng!)
                            // Server trả về dạng: 2025-12-24T10:00:00.123Z
                            SimpleDateFormat sdfIso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                            sdfIso.setTimeZone(java.util.TimeZone.getTimeZone("UTC")); // Server luôn trả về UTC

                            // Fallback parser (phòng hờ)
                            SimpleDateFormat sdfSimple = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);

                            // 3. Duyệt và cộng tiền
                            for (com.ph48845.datn_qlnh_rmis.data.model.HistoryItem history : histories) {
                                if (history.getCreatedAt() != null) {
                                    try {
                                        java.util.Date createdDate;
                                        try {
                                            createdDate = sdfIso.parse(history.getCreatedAt());
                                        } catch (Exception ex) {
                                            // Thử parse kiểu đơn giản nếu kiểu ISO thất bại
                                            createdDate = sdfSimple.parse(history.getCreatedAt());
                                        }

                                        if (createdDate != null) {
                                            // Format lại ngày theo múi giờ điện thoại (Local) để mapping đúng cột ngày
                                            String dateKey = dateFormat.format(createdDate);

                                            if (dailyRevenue.containsKey(dateKey)) {
                                                double amount = 0;
                                                if (history.getDetails() != null) {
                                                    amount = history.getDetails().getFinalAmount();
                                                }
                                                dailyRevenue.put(dateKey, dailyRevenue.get(dateKey) + amount);
                                            }
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace(); // Log lỗi nhưng không làm crash app
                                    }
                                }
                            }

                            updateBarChart(dates, dailyRevenue);
                        });
                    }
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<com.ph48845.datn_qlnh_rmis.data.model.HistoryItem>>> call, Throwable t) {
                // Silent fail
            }
        });
    }

    private void updateBarChart(List<String> dates, Map<String, Double> dailyRevenue) {
        if (barChart == null || getContext() == null)
            return;

        ArrayList<BarEntry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();

        for (int i = 0; i < dates.size(); i++) {
            String date = dates.get(i);
            float revenue = dailyRevenue.get(date).floatValue();
            entries.add(new BarEntry(i, revenue));
            labels.add(getDayLabel(date));
        }

        BarDataSet dataSet = new BarDataSet(entries, "Doanh thu");
        dataSet.setColor(Color.parseColor("#4CAF50"));
        dataSet.setValueTextColor(Color.parseColor("#212121"));
        dataSet.setValueTextSize(9f);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (value == 0)
                    return "";
                if (value >= 1000000) {
                    return String.format(Locale.US, "%.1fM", value / 1000000);
                } else if (value >= 1000) {
                    return String.format(Locale.US, "%.0fK", value / 1000);
                }
                return String.format(Locale.US, "%.0f", value);
            }
        });

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.7f);

        barChart.setData(barData);
        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        barChart.getXAxis().setLabelCount(labels.size());
        barChart.invalidate();
    }

    private String getDayLabel(String dateStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            java.util.Date date = sdf.parse(dateStr);
            SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", new Locale("vi"));
            return dayFormat.format(date);
        } catch (Exception e) {
            return dateStr.substring(8); // Return day number
        }
    }

    private void updateDashboardUI(DashboardData data) {
        tvServingTables.setText(String.valueOf(data.getServingTables()));
        tvWaitingPayment.setText(String.valueOf(data.getWaitingPayment()));
        tvServingInvoices.setText(String.valueOf(data.getServingInvoices()));
        tvActiveStaff.setText(String.valueOf(data.getPaidToday()));
    }

    private void updateTableStatistics(List<TableItem> tables) {
        int total = tables.size();
        int available = 0;
        int reserved = 0;

        for (TableItem table : tables) {
            table.normalize();

            TableItem.Status status = table.getStatus();
            if (status == TableItem.Status.AVAILABLE) {
                available++;
            } else if (status == TableItem.Status.RESERVED) {
                reserved++;
            }
        }

        tvTotalTables.setText(String.valueOf(total));
        tvAvailableTables.setText(String.valueOf(available));
        tvReservedTables.setText(String.valueOf(reserved));
    }
}
