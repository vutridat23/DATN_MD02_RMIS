package com.ph48845.datn_qlnh_rmis.ui.thungan.thanhtoan;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;
import com.ph48845.datn_qlnh_rmis.ui.thungan.ThuNganActivity;

import java.util.ArrayList;
import java.util.List;

public class ThanhToanActivity extends AppCompatActivity {

    private CardView cardCash, cardQR, cardCard;
    private TextView tvTotalAmount;
    private ImageButton btnBack;

    private Order currentOrder;
    private double totalAmount;

    private OrderRepository orderRepository;
    private TableRepository tableRepository;
    private boolean excludeUnreadyItems = false;
    private List<Order.OrderItem> payItems;



    // Launcher QR payment
    private final ActivityResultLauncher<Intent> qrLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    processPayment("QR");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thanh_toan);

        initViews();

        excludeUnreadyItems = getIntent().getBooleanExtra("excludeUnreadyItems", false);

        if (excludeUnreadyItems) {
            payItems = (ArrayList<Order.OrderItem>)
                    getIntent().getSerializableExtra("pay_items");

        }


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

                        if (excludeUnreadyItems && payItems != null && !payItems.isEmpty()) {
                            totalAmount = 0;
                            for (Order.OrderItem item : payItems) {
                                totalAmount += item.getPrice() * item.getQuantity();
                            }
                        } else {
                            totalAmount = currentOrder.getFinalAmount();
                        }

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
                runOnUiThread(() -> {
                    Toast.makeText(ThanhToanActivity.this, "Lỗi khi lấy đơn hàng: " + message, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void setupPaymentButtons() {
        // TIỀN MẶT
        cardCash.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Xác nhận thanh toán")
                .setMessage("Đã nhận tiền khách chưa?")
                .setPositiveButton("Đã nhận", (dialog, which) -> processPayment("Tiền mặt"))
                .setNegativeButton("Hủy", null)
                .show()
        );

        // QR
        cardQR.setOnClickListener(v -> {
            Intent intent = new Intent(ThanhToanActivity.this, QRPaymentActivity.class);
            intent.putExtra("amount", totalAmount);
            intent.putExtra("orderId", currentOrder.getId());
            qrLauncher.launch(intent);
        });

        // THẺ NGÂN HÀNG
        cardCard.setOnClickListener(v -> {
            Intent intent = new Intent(ThanhToanActivity.this, PaymentCardActivity.class);
            intent.putExtra("orderId", currentOrder.getId());   // truyền id order
            intent.putExtra("amount", totalAmount);       // truyền số tiền
            startActivity(intent);
        });


    }

    private void processPayment(String method) {
        if (currentOrder == null) {
            Toast.makeText(this, "Lỗi: không có đơn hàng để thanh toán", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        double amountCustomerGiven = method.equals("Tiền mặt") ? totalAmount : 0;

        orderRepository.payOrder(currentOrder.getId(), method, amountCustomerGiven, new OrderRepository.RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order updatedOrder) {
                Toast.makeText(ThanhToanActivity.this, "Thanh toán thành công", Toast.LENGTH_SHORT).show();
                resetTableAndFinish(updatedOrder);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ThanhToanActivity.this, "Thanh toán thất bại: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void resetTableAndFinish(Order updatedOrder) {
        String tableId = updatedOrder.getTableId();
        if (tableId != null) {
            tableRepository.resetTableAfterPayment(tableId, new TableRepository.RepositoryCallback<TableItem>() {
                @Override
                public void onSuccess(TableItem table) {
                    Toast.makeText(ThanhToanActivity.this, "Thanh toán thành công", Toast.LENGTH_SHORT).show();
                    finishSuccess(updatedOrder);
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(ThanhToanActivity.this, "Thanh toán xong nhưng không reset bàn: " + message, Toast.LENGTH_SHORT).show();
                    finishSuccess(updatedOrder);
                }
            });
        } else {
            finishSuccess(updatedOrder);
        }
    }

    private void finishSuccess(Order order) {
        Intent intent = new Intent(this, ThuNganActivity.class);
        intent.putExtra("paidOrder", order);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
