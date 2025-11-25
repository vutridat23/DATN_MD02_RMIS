package com.ph48845.datn_qlnh_rmis.ui.thungan;

import android.content.Intent;
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
import com.ph48845.datn_qlnh_rmis.ui.thanhtoan.ThanhToanActivity;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Activity hiển thị hóa đơn thanh toán cho bàn ăn.
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

        tableNumber = getIntent().getIntExtra("tableNumber", 0);
        Log.d(TAG, "Received tableNumber: " + tableNumber);

        if (tableNumber <= 0) {
            Toast.makeText(this, "Thông tin bàn không hợp lệ", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupToolbar();

        orderRepository = new OrderRepository();
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

        tvTableNumber.setText("Bàn: " + String.format("%02d", tableNumber));

        btnPayment.setOnClickListener(v -> {
            if (orders == null || orders.isEmpty()) {
                Toast.makeText(InvoiceActivity.this, "Không có đơn hàng để thanh toán", Toast.LENGTH_SHORT).show();
                return;
            }

            Order orderToPay = null;
            for (Order o : orders) {
                if (o != null && String.valueOf(o.getTableNumber()).equals(String.valueOf(tableNumber))) {
                    orderToPay = o;
                    break;
                }
            }

            if (orderToPay == null) {
                Toast.makeText(InvoiceActivity.this, "Không tìm thấy đơn hàng của bàn này", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(InvoiceActivity.this, ThanhToanActivity.class);
            intent.putExtra("orderId", orderToPay.getId());
            startActivity(intent);
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

    private void loadInvoiceData() {
        progressBar.setVisibility(View.VISIBLE);
        llItemsContainer.removeAllViews();

        orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orderList) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Log.d(TAG, "Orders loaded: " + (orderList != null ? orderList.size() : "null"));

                    if (orderList == null || orderList.isEmpty()) {
                        Toast.makeText(InvoiceActivity.this, "Không có đơn hàng cho bàn này", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    orders.clear();
                    for (Order order : orderList) {
                        if (order != null && String.valueOf(order.getTableNumber()).equals(String.valueOf(tableNumber))) {
                            orders.add(order);
                        }
                    }

                    if (orders.isEmpty()) {
                        Toast.makeText(InvoiceActivity.this, "Không có đơn hàng cho bàn này", Toast.LENGTH_SHORT).show();
                        return;
                    }

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

    private void displayInvoice() {
        llItemsContainer.removeAllViews();

        if (orders == null || orders.isEmpty()) return;

        tvOrderCode.setText("Mã đơn: " + generateOrderCode());

        for (int i = 0; i < orders.size(); i++) {
            Order order = orders.get(i);
            if (order == null) continue;

            order.normalizeItems();

            if (orders.size() > 1) {
                TextView tvOrderHeader = new TextView(this);
                tvOrderHeader.setText("Đơn #" + (i + 1) + " - ID: " + order.getId());
                tvOrderHeader.setTextSize(16);
                tvOrderHeader.setTextColor(0xFF000000);
                tvOrderHeader.setTypeface(null, android.graphics.Typeface.BOLD);
                LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                headerParams.setMargins(16,16,16,8);
                tvOrderHeader.setLayoutParams(headerParams);
                llItemsContainer.addView(tvOrderHeader);
            }

            displayItemsForOrder(order.getItems());

            if (i < orders.size() - 1) {
                View separator = new View(this);
                LinearLayout.LayoutParams separatorParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        2
                );
                separatorParams.setMargins(16,16,16,16);
                separator.setLayoutParams(separatorParams);
                separator.setBackgroundColor(0xFFE0E0E0);
                llItemsContainer.addView(separator);
            }
        }

        double totalAmount = 0;
        double totalDiscount = 0;
        for (Order order : orders) {
            if (order == null) continue;
            totalAmount += order.getTotalAmount();
            totalDiscount += order.getDiscount();
        }
        double finalAmount = totalAmount - totalDiscount;

        tvTotalAmount.setText(formatCurrency(totalAmount));
        tvDiscountAmount.setText(formatCurrency(totalDiscount));
        tvFinalAmount.setText(formatCurrency(finalAmount));

        if (totalDiscount > 0 && totalAmount > 0) {
            double discountPercent = (totalDiscount / totalAmount) * 100;
            tvDiscountInfo.setText("Chiết khấu mã giảm giá: " + String.format("%.0f", discountPercent) + "%");
            tvDiscountInfo.setVisibility(View.VISIBLE);
        } else {
            tvDiscountInfo.setVisibility(View.GONE);
        }

        Log.d(TAG, "Displayed " + orders.size() + " orders");
    }

    private void displayItemsForOrder(List<Order.OrderItem> items) {
        if (items == null || items.isEmpty()) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("  (Chưa có món)");
            tvEmpty.setTextSize(14);
            tvEmpty.setTextColor(0xFF757575);
            llItemsContainer.addView(tvEmpty);
            return;
        }

        for (Order.OrderItem item : items) {
            if (item == null) continue;

            String itemName = (item.getName() != null && !item.getName().isEmpty()) ? item.getName() : "(Không tên)";

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(16,8,16,8);

            TextView tvName = new TextView(this);
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));
            tvName.setText(itemName);
            tvName.setTextColor(0xFF000000);
            tvName.setTextSize(14);
            row.addView(tvName);

            TextView tvQty = new TextView(this);
            tvQty.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tvQty.setText("x" + item.getQuantity());
            tvQty.setTextColor(0xFF000000);
            tvQty.setTextSize(14);
            tvQty.setGravity(android.view.Gravity.CENTER);
            row.addView(tvQty);

            TextView tvPrice = new TextView(this);
            tvPrice.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f));
            tvPrice.setText(formatCurrency(item.getPrice() * item.getQuantity()));
            tvPrice.setTextColor(0xFF000000);
            tvPrice.setTextSize(14);
            tvPrice.setGravity(android.view.Gravity.RIGHT);
            row.addView(tvPrice);

            llItemsContainer.addView(row);
        }
    }

    private String formatCurrency(double amount) {
        return CURRENCY_FORMAT.format(amount) + "₫";
    }

    private String generateOrderCode() {
        if (orders != null && !orders.isEmpty() && orders.get(0).getId() != null) {
            String orderId = orders.get(0).getId();
            String suffix = "0000";

            if (orderId.length() >= 4) {
                String last4 = orderId.substring(orderId.length() - 4);
                StringBuilder digits = new StringBuilder();
                for (char c : last4.toCharArray()) {
                    digits.append(Character.isDigit(c) ? c : Math.abs(c) % 10);
                }
                try {
                    int num = Integer.parseInt(digits.toString()) % 10000;
                    suffix = String.format("%04d", num);
                } catch (NumberFormatException e) {
                    suffix = "0000";
                }
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy", Locale.getDefault());
            String year = sdf.format(new Date());
            return "HD" + year + "-" + suffix;
        }

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
