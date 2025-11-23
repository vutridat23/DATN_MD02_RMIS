package com.ph48845.datn_qlnh_rmis.ui.thanhtoan;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.RevenueRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;
import com.ph48845.datn_qlnh_rmis.ui.thungan.ThuNganActivity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ThanhToanActivity extends AppCompatActivity {

    private CardView cardCash, cardQR, cardCard;
    private TextView tvTotalAmount;
    private ImageButton btnBack;

    private Order currentOrder;
    private double totalAmount;

    private RevenueRepository revenueRepository;
    private OrderRepository orderRepository;
    private TableRepository tableRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thanh_toan);

        initViews();

        revenueRepository = new RevenueRepository();
        orderRepository = new OrderRepository();
        tableRepository = new TableRepository();

        String orderId = getIntent().getStringExtra("orderId");
        if (orderId == null || orderId.isEmpty()) {
            Toast.makeText(this, "Lỗi: không có đơn hàng để thanh toán", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        fetchOrder(orderId);
    }

    private void initViews() {
        cardCash = findViewById(R.id.cardCash);
        cardQR = findViewById(R.id.cardQR);
        cardCard = findViewById(R.id.cardCard);
        tvTotalAmount = findViewById(R.id.tvTotalAmount);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());
    }

    private void fetchOrder(String orderId) {
        orderRepository.getOrdersByTableNumber(null, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orders) {
                for (Order order : orders) {
                    if (order.getId().equals(orderId)) {
                        currentOrder = order;
                        break;
                    }
                }

                runOnUiThread(() -> {
                    if (currentOrder != null) {
                        totalAmount = currentOrder.getFinalAmount();
                        tvTotalAmount.setText("Tổng: " + String.format("%,.0f₫", totalAmount));
                        setupPaymentButtons();
                    } else {
                        Toast.makeText(ThanhToanActivity.this, "Không tìm thấy đơn hàng", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() ->
                        Toast.makeText(ThanhToanActivity.this, "Lỗi khi lấy đơn hàng: " + message, Toast.LENGTH_SHORT).show()
                );
                finish();
            }
        });
    }

    private void setupPaymentButtons() {
        cardCash.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Xác nhận thanh toán")
                .setMessage("Đã nhận tiền chưa?")
                .setPositiveButton("Đã nhận", (dialog, which) -> processPayment("Tiền mặt"))
                .setNegativeButton("Hủy", null)
                .show()
        );

        cardQR.setOnClickListener(v -> {
            Intent intent = new Intent(ThanhToanActivity.this, QRPaymentActivity.class);
            intent.putExtra("amount", totalAmount);   // gửi số tiền qua QR screen
            intent.putExtra("orderId", currentOrder.getId());
            startActivity(intent);
        });


        cardCard.setOnClickListener(v ->
                processPayment("Thẻ ngân hàng")
        );
    }

    private void processPayment(String method) {
        if (currentOrder == null) {
            Toast.makeText(this, "Lỗi: không có đơn hàng để thanh toán", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        double paidAmount = totalAmount; // hoặc lấy từ input nếu có

        // Gọi payOrder đúng 3 tham số
        orderRepository.payOrder(currentOrder.getId(), paidAmount, new OrderRepository.RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order updatedOrder) {
                // Reset bàn
                String tableId = updatedOrder.getTableId(); // Lấy tableId từ order
                if (tableId != null) {
                    tableRepository.resetTableAfterPayment(tableId, new TableRepository.RepositoryCallback<TableItem>() {
                        @Override
                        public void onSuccess(TableItem table) {
                            Toast.makeText(ThanhToanActivity.this, "Thanh toán thành công và bàn đã reset", Toast.LENGTH_SHORT).show();
                            finishSuccess(updatedOrder);
                        }


                        @Override
                        public void onError(String message) {
                            Toast.makeText(ThanhToanActivity.this,
                                    "Thanh toán xong nhưng không reset được bàn: " + message,
                                    Toast.LENGTH_SHORT).show();
                            finishSuccess(updatedOrder);
                        }
                    });
                } else {
                    finishSuccess(updatedOrder);
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ThanhToanActivity.this, "Thanh toán thất bại: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void finishSuccess(Order order) {
        // Tạo Intent chuyển về màn hình thu ngân (CashierActivity)
        Intent intent = new Intent(this, ThuNganActivity.class); // đổi CashierActivity thành Activity thu ngân của bạn
        intent.putExtra("paidOrder", order); // nếu cần truyền order vừa thanh toán
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);

        finish(); // đóng màn hình thanh toán
    }

}
