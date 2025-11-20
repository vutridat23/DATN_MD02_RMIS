package com.ph48845.datn_qlnh_rmis.ui.revenue;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Activity hiển thị thống kê doanh thu từ các hóa đơn đã thanh toán.
 * Hiển thị biểu đồ cột theo ngày trong tuần và tổng kết doanh thu.
 */
public class RevenueActivity extends AppCompatActivity {

    private static final String TAG = "RevenueActivity";
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,###");
    
    // Date formats
    private static final SimpleDateFormat DATE_FORMAT_ISO = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
    private static final SimpleDateFormat DATE_FORMAT_ISO2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
    private static final SimpleDateFormat DATE_FORMAT_SIMPLE = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private static final SimpleDateFormat DATE_FORMAT_DATE = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private Toolbar toolbar;
    private ProgressBar progressBar;
    private BarChartView barChart;
    private TextView tvInvoiceCount;
    private TextView tvTotalRevenue;

    private OrderRepository orderRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_revenue);

        initViews();
        setupToolbar();

        orderRepository = new OrderRepository();

        // Load dữ liệu từ API
        loadRevenueData();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        progressBar = findViewById(R.id.progress_bar_loading);
        barChart = findViewById(R.id.bar_chart);
        tvInvoiceCount = findViewById(R.id.tv_invoice_count);
        tvTotalRevenue = findViewById(R.id.tv_total_revenue);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    /**
     * Load dữ liệu doanh thu từ API
     */
    private void loadRevenueData() {
        progressBar.setVisibility(View.VISIBLE);
        
        // Lấy tất cả orders (không filter theo tableNumber)
        orderRepository.getOrdersByTableNumber(null, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> allOrders) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    
                    if (allOrders == null || allOrders.isEmpty()) {
                        Toast.makeText(RevenueActivity.this, "Không có dữ liệu", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Lọc các orders đã thanh toán
                    List<Order> paidOrders = filterPaidOrders(allOrders);
                    
                    // Tính toán và hiển thị thống kê
                    displayRevenueStatistics(paidOrders);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(RevenueActivity.this, "Lỗi tải dữ liệu: " + message, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error loading orders: " + message);
                });
            }
        });
    }

    /**
     * Lọc các orders đã thanh toán (có paidAt hoặc orderStatus = "paid")
     */
    private List<Order> filterPaidOrders(List<Order> allOrders) {
        List<Order> paidOrders = new ArrayList<>();
        
        for (Order order : allOrders) {
            if (order == null) continue;
            
            // Kiểm tra nếu đã thanh toán
            boolean isPaid = false;
            
            // Kiểm tra paidAt
            String paidAt = order.getPaidAt();
            if (paidAt != null && !paidAt.trim().isEmpty()) {
                isPaid = true;
            }
            
            // Kiểm tra orderStatus
            String orderStatus = order.getOrderStatus();
            if (orderStatus != null) {
                String status = orderStatus.toLowerCase().trim();
                if (status.contains("paid") || status.contains("đã thanh toán") || 
                    status.contains("completed") || status.contains("hoàn thành")) {
                    isPaid = true;
                }
            }
            
            // Nếu không có paidAt và orderStatus, kiểm tra finalAmount > 0 và paidAmount > 0
            if (!isPaid && order.getFinalAmount() > 0 && order.getPaidAmount() > 0) {
                isPaid = true;
            }
            
            if (isPaid) {
                paidOrders.add(order);
            }
        }
        
        return paidOrders;
    }

    /**
     * Hiển thị thống kê doanh thu
     */
    private void displayRevenueStatistics(List<Order> paidOrders) {
        if (paidOrders == null || paidOrders.isEmpty()) {
            tvInvoiceCount.setText("Số lượng hóa đơn: 0");
            tvTotalRevenue.setText("Tổng cộng doanh thu: 0₫");
            barChart.setData(new ArrayList<>());
            return;
        }

        // Tính tổng số hóa đơn
        int invoiceCount = paidOrders.size();
        tvInvoiceCount.setText("Số lượng hóa đơn: " + invoiceCount);

        // Tính tổng doanh thu
        double totalRevenue = 0;
        for (Order order : paidOrders) {
            totalRevenue += order.getFinalAmount();
        }
        tvTotalRevenue.setText("Tổng cộng doanh thu: " + formatCurrency(totalRevenue));

        // Tính doanh thu theo ngày trong tuần
        List<Double> revenueByDay = calculateRevenueByDay(paidOrders);
        
        // Cập nhật biểu đồ
        barChart.setData(revenueByDay);
    }

    /**
     * Tính doanh thu theo ngày trong tuần (Thứ 2 đến Chủ nhật và Hôm nay)
     * Trả về danh sách 7 giá trị (triệu đồng): [M, T, W, T, F, S, Today]
     * Luôn trả về 7 giá trị, với "Today" luôn ở vị trí cuối cùng
     */
    private List<Double> calculateRevenueByDay(List<Order> paidOrders) {
        // Khởi tạo mảng doanh thu theo ngày (7 ngày: Thứ 2 - Chủ nhật/Today)
        double[] revenueByDay = new double[7];
        
        // Lấy ngày hiện tại
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        
        // Lấy ngày đầu tuần (Thứ 2)
        Calendar weekStart = (Calendar) today.clone();
        int dayOfWeek = weekStart.get(Calendar.DAY_OF_WEEK);
        // Chuyển đổi: Chủ nhật = 1 -> 6, Thứ 2 = 2 -> 0, ..., Thứ 7 = 7 -> 5
        int daysFromMonday = (dayOfWeek == Calendar.SUNDAY) ? 6 : dayOfWeek - Calendar.MONDAY;
        weekStart.add(Calendar.DAY_OF_MONTH, -daysFromMonday);
        
        // Xác định ngày trong tuần hiện tại (0 = Thứ 2, 6 = Chủ nhật)
        int todayDayOfWeek = daysFromMonday;
        
        // Tính doanh thu cho từng order
        for (Order order : paidOrders) {
            Date orderDate = parseOrderDate(order);
            if (orderDate == null) continue;
            
            // Xác định ngày của order
            Calendar orderCal = Calendar.getInstance();
            orderCal.setTime(orderDate);
            orderCal.set(Calendar.HOUR_OF_DAY, 0);
            orderCal.set(Calendar.MINUTE, 0);
            orderCal.set(Calendar.SECOND, 0);
            orderCal.set(Calendar.MILLISECOND, 0);
            
            // Kiểm tra nếu order trong tuần này (từ Thứ 2 đến hôm nay)
            if (orderCal.before(weekStart) || orderCal.after(today)) {
                continue; // Bỏ qua orders ngoài tuần này
            }
            
            // Tính số ngày từ Thứ 2 (0 = Thứ 2, 6 = Chủ nhật)
            long diffInMillis = orderCal.getTimeInMillis() - weekStart.getTimeInMillis();
            int daysDiff = (int) (diffInMillis / (1000 * 60 * 60 * 24));
            
            // Đảm bảo daysDiff trong khoảng 0-6
            if (daysDiff >= 0 && daysDiff < 7) {
                // Chuyển đổi sang triệu đồng
                double revenueInMillion = order.getFinalAmount() / 1000000.0;
                
                // Nếu order là hôm nay, đặt vào "Today" (index 6)
                if (daysDiff == todayDayOfWeek) {
                    revenueByDay[6] += revenueInMillion; // "Today" ở index 6
                } else if (daysDiff < 6) {
                    // Các ngày từ Thứ 2 đến Thứ 7 (không phải hôm nay)
                    revenueByDay[daysDiff] += revenueInMillion;
                }
            }
        }
        
        // Chuyển đổi sang List (7 giá trị: M, T, W, T, F, S, Today)
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            result.add(revenueByDay[i]);
        }
        
        return result;
    }

    /**
     * Parse ngày từ order (ưu tiên paidAt, sau đó createdAt)
     */
    private Date parseOrderDate(Order order) {
        // Ưu tiên paidAt
        String dateStr = order.getPaidAt();
        if (dateStr == null || dateStr.trim().isEmpty()) {
            dateStr = order.getCreatedAt();
        }
        
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        dateStr = dateStr.trim();
        
        // Thử các format khác nhau
        try {
            return DATE_FORMAT_ISO.parse(dateStr);
        } catch (ParseException e) {
            try {
                return DATE_FORMAT_ISO2.parse(dateStr);
            } catch (ParseException e2) {
                try {
                    return DATE_FORMAT_SIMPLE.parse(dateStr);
                } catch (ParseException e3) {
                    try {
                        return DATE_FORMAT_DATE.parse(dateStr);
                    } catch (ParseException e4) {
                        Log.w(TAG, "Cannot parse date: " + dateStr);
                        return null;
                    }
                }
            }
        }
    }

    /**
     * Format số tiền theo định dạng Việt Nam (₫)
     */
    private String formatCurrency(double amount) {
        return CURRENCY_FORMAT.format(amount) + "₫";
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

