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
        // Lấy tableNumber từ Intent hoặc từ order trước
        int tableNumber = getIntent().getIntExtra("tableNumber", -1);

        orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orders) {
                currentOrder = null;

                for (Order order : orders) {
                    if (order != null && order.getId().equals(orderId)) {
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
        // Card Cash
        cardCash.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Xác nhận thanh toán")
                .setMessage("Đã nhận tiền chưa?")
                .setPositiveButton("Đã nhận", (dialog, which) -> processPayment("Tiền mặt"))
                .setNegativeButton("Hủy", null)
                .show()
        );

        // Card QR - đặt trong đây để chắc chắn currentOrder != null
        cardQR.setOnClickListener(v -> {
            if (currentOrder == null) {
                Toast.makeText(ThanhToanActivity.this, "Đang tải đơn hàng, vui lòng thử lại", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(ThanhToanActivity.this, QRPaymentActivity.class);
            intent.putExtra("amount", totalAmount);
            intent.putExtra("orderId", currentOrder.getId());
            startActivity(intent);
        });

        // Card Card
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

        long now = System.currentTimeMillis();

        Map<String, Object> updates = new HashMap<>();
        updates.put("paid", true);
        updates.put("paymentMethod", method);
        updates.put("paidAt", now);
        updates.put("orderStatus", "paid");

        orderRepository.updateOrder(currentOrder.getId(), updates, new OrderRepository.RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order updatedOrder) {
                // Đẩy doanh thu
                revenueRepository.addRevenue(updatedOrder);

                // Lấy danh sách bàn và tìm bàn trùng tableNumber
                tableRepository.getAllTables(new TableRepository.RepositoryCallback<List<TableItem>>() {
                    @Override
                    public void onSuccess(List<TableItem> tables) {
                        TableItem matchedTable = null;
                        int orderTableNumber = updatedOrder.getTableNumber();
                        for (TableItem t : tables) {
                            if (t != null && t.getTableNumber() == orderTableNumber) {
                                matchedTable = t;
                                break;
                            }
                        }

                        if (matchedTable != null) {
                            // Reset bàn về available
                            tableRepository.updateTableStatus(
                                    currentOrder.getTableId(), // dùng trực tiếp tableId từ order
                                    "available",
                                    new TableRepository.RepositoryCallback<TableItem>() {
                                        @Override
                                        public void onSuccess(TableItem result) {
                                            finishSuccess(updatedOrder);
                                        }

                                        @Override
                                        public void onError(String error) {
                                            Toast.makeText(ThanhToanActivity.this,
                                                    "Thanh toán xong nhưng không clear bàn: " + error,
                                                    Toast.LENGTH_SHORT).show();
                                            finishSuccess(updatedOrder);
                                        }
                                    });

                        } else {
                            Toast.makeText(ThanhToanActivity.this,
                                    "Không tìm thấy bàn để reset",
                                    Toast.LENGTH_SHORT).show();
                            finishSuccess(updatedOrder);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(ThanhToanActivity.this,
                                "Lấy danh sách bàn thất bại: " + message,
                                Toast.LENGTH_SHORT).show();
                        finishSuccess(updatedOrder);
                    }
                });
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ThanhToanActivity.this, "Thanh toán thất bại: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void finishSuccess(Order order) {
        Intent result = new Intent();
        result.putExtra("order", order);
        setResult(RESULT_OK, result);
        finish();
    }
}
