package com.ph48845.datn_qlnh_rmis.ui.thungan;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Activity hiển thị hóa đơn thanh toán cho bàn ăn.
 * Lấy dữ liệu từ API dựa trên tableNumber được truyền từ Intent.
 */
public class InvoiceActivity extends AppCompatActivity {

    private static final String TAG = "InvoiceActivity";
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,###");

    private Toolbar toolbar;
    private ProgressBar progressBar;
    private TextView tvTableNumber;
    private TextView tvOrderCode;
    private TextView tvDiscountInfo;
    private TextView tvTotalAmount;
    private TextView tvDiscountAmount;
    private TextView tvFinalAmount;
    private LinearLayout llItemsContainer;
    private Button btnPayment;

    private OrderRepository orderRepository;
    private int tableNumber;
    private List<Order> orders = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invoice);

        // Lấy tableNumber từ Intent
        tableNumber = getIntent().getIntExtra("tableNumber", 0);
        if (tableNumber <= 0) {
            Toast.makeText(this, "Thông tin bàn không hợp lệ", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupToolbar();
        
        orderRepository = new OrderRepository();

        // Load dữ liệu từ API
        loadInvoiceData();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        progressBar = findViewById(R.id.progress_bar_loading);
        tvTableNumber = findViewById(R.id.tv_table_number);
        tvOrderCode = findViewById(R.id.tv_order_code);
        tvDiscountInfo = findViewById(R.id.tv_discount_info);
        tvTotalAmount = findViewById(R.id.tv_total_amount);
        tvDiscountAmount = findViewById(R.id.tv_discount_amount);
        tvFinalAmount = findViewById(R.id.tv_final_amount);
        llItemsContainer = findViewById(R.id.ll_items_container);
        btnPayment = findViewById(R.id.btn_payment);

        // Set thông tin bàn
        tvTableNumber.setText("Bàn: " + String.format("%02d", tableNumber));

        // Click listener cho nút thanh toán
        btnPayment.setOnClickListener(v -> {
            // TODO: Xử lý thanh toán
            Toast.makeText(this, "Chức năng thanh toán đang được phát triển", Toast.LENGTH_SHORT).show();
        });
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
     * Load dữ liệu hóa đơn từ API
     */
    private void loadInvoiceData() {
        progressBar.setVisibility(View.VISIBLE);
        llItemsContainer.removeAllViews();

        // Lấy orders của bàn này
        orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orderList) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    
                    if (orderList == null || orderList.isEmpty()) {
                        Toast.makeText(InvoiceActivity.this, "Không có đơn hàng cho bàn này", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    orders = orderList;
                    displayInvoice();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(InvoiceActivity.this, "Lỗi tải dữ liệu: " + message, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error loading orders: " + message);
                });
            }
        });
    }

    /**
     * Hiển thị hóa đơn với dữ liệu đã load
     */
    private void displayInvoice() {
        // Tạo mã đơn từ order đầu tiên (hoặc có thể tạo mới)
        String orderCode = generateOrderCode();
        tvOrderCode.setText("Mã đơn: " + orderCode);

        // Gộp tất cả items từ các orders
        List<Order.OrderItem> allItems = new ArrayList<>();
        double totalAmount = 0;
        double totalDiscount = 0;

        for (Order order : orders) {
            if (order == null) continue;
            order.normalizeItems();
            
            if (order.getItems() != null) {
                allItems.addAll(order.getItems());
            }
            
            totalAmount += order.getTotalAmount();
            totalDiscount += order.getDiscount();
        }

        // Hiển thị danh sách món ăn
        displayItems(allItems);

        // Tính toán và hiển thị tổng kết
        double finalAmount = totalAmount - totalDiscount;

        tvTotalAmount.setText(formatCurrency(totalAmount));
        tvDiscountAmount.setText(formatCurrency(totalDiscount));
        tvFinalAmount.setText(formatCurrency(finalAmount));

        // Hiển thị thông tin chiết khấu nếu có
        if (totalDiscount > 0) {
            double discountPercent = (totalDiscount / totalAmount) * 100;
            tvDiscountInfo.setText("Chiết khấu mã giảm giá: " + String.format("%.0f", discountPercent) + "%");
            tvDiscountInfo.setVisibility(View.VISIBLE);
        } else {
            tvDiscountInfo.setVisibility(View.GONE);
        }
    }

    /**
     * Hiển thị danh sách món ăn trong container
     */
    private void displayItems(List<Order.OrderItem> items) {
        llItemsContainer.removeAllViews();

        if (items == null || items.isEmpty()) {
            return;
        }

        for (Order.OrderItem item : items) {
            if (item == null) continue;

            // Tạo row cho mỗi món
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(
                (int) (16 * getResources().getDisplayMetrics().density),
                (int) (12 * getResources().getDisplayMetrics().density),
                (int) (16 * getResources().getDisplayMetrics().density),
                (int) (12 * getResources().getDisplayMetrics().density)
            );

            // Món ăn
            TextView tvName = new TextView(this);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));
            tvName.setText(item.getName() != null ? item.getName() : "(Không tên)");
            tvName.setTextColor(0xFF000000);
            tvName.setTextSize(14);
            row.addView(tvName);

            // Số lượng
            TextView tvQty = new TextView(this);
            tvQty.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvQty.setText("x" + item.getQuantity());
            tvQty.setTextColor(0xFF000000);
            tvQty.setTextSize(14);
            tvQty.setGravity(android.view.Gravity.CENTER);
            row.addView(tvQty);

            // Giá
            TextView tvPrice = new TextView(this);
            tvPrice.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f));
            double itemTotal = item.getPrice() * item.getQuantity();
            tvPrice.setText(formatCurrency(itemTotal));
            tvPrice.setTextColor(0xFF000000);
            tvPrice.setTextSize(14);
            tvPrice.setGravity(android.view.Gravity.RIGHT);
            row.addView(tvPrice);

            llItemsContainer.addView(row);
        }
    }

    /**
     * Format số tiền theo định dạng Việt Nam (₫)
     */
    private String formatCurrency(double amount) {
        return CURRENCY_FORMAT.format(amount) + "₫";
    }

    /**
     * Tạo mã đơn từ order ID hoặc timestamp
     */
    private String generateOrderCode() {
        if (orders != null && !orders.isEmpty() && orders.get(0).getId() != null) {
            // Sử dụng ID của order đầu tiên, format thành HD2025-XXXX
            String orderId = orders.get(0).getId();
            // Lấy 4 ký tự cuối của ID và chuyển thành số
            String suffix = "0000";
            if (orderId.length() >= 4) {
                String last4 = orderId.substring(orderId.length() - 4);
                // Lấy các chữ số từ 4 ký tự cuối
                StringBuilder digits = new StringBuilder();
                for (char c : last4.toCharArray()) {
                    if (Character.isDigit(c)) {
                        digits.append(c);
                    } else {
                        // Chuyển ký tự thành số (0-9)
                        digits.append(Math.abs(c) % 10);
                    }
                }
                if (digits.length() > 0) {
                    try {
                        int num = Integer.parseInt(digits.toString()) % 10000;
                        suffix = String.format("%04d", num);
                    } catch (NumberFormatException e) {
                        suffix = "0000";
                    }
                }
            }
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy", Locale.getDefault());
            String year = sdf.format(new Date());
            return "HD" + year + "-" + suffix;
        }
        // Fallback: tạo mã từ timestamp
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MMddHHmm", Locale.getDefault());
        return "HD" + sdf.format(new Date());
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

