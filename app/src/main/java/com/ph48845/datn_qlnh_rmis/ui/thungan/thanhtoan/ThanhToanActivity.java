package com.ph48845.datn_qlnh_rmis.ui.thungan.thanhtoan;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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
    private List<String> orderIds; // Danh sách orderIds khi thanh toán nhiều hóa đơn
    private double totalAmount;
    private int tableNumber;
    private String voucherId;

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
        Log.d("ThanhToanDebug", "orderId=" + getIntent().getStringExtra("orderId") +
                ", orderIds=" + getIntent().getStringArrayListExtra("orderIds") +
                ", totalAmount=" + getIntent().getDoubleExtra("totalAmount", -1) +
                ", voucherId=" + getIntent().getStringExtra("voucherId"));


        initViews();

        excludeUnreadyItems = getIntent().getBooleanExtra("excludeUnreadyItems", false);

        if (excludeUnreadyItems) {
            payItems = (ArrayList<Order.OrderItem>)
                    getIntent().getSerializableExtra("pay_items");
        }

        orderRepository = new OrderRepository();
        tableRepository = new TableRepository();

        // Kiểm tra nhiều order hay một order
        ArrayList<String> orderIdsList = getIntent().getStringArrayListExtra("orderIds");
        if (orderIdsList != null && !orderIdsList.isEmpty()) {
            // Thanh toán nhiều hóa đơn
            orderIds = orderIdsList;
            totalAmount = getIntent().getDoubleExtra("totalAmount", 0.0);
            tableNumber = getIntent().getIntExtra("tableNumber", 0);
            voucherId = getIntent().getStringExtra("voucherId");

            tvTotalAmount.setText("Tổng: " + String.format("%,.0f₫", totalAmount));
            setupPaymentButtons();
        } else {
            // Thanh toán một hóa đơn
            String orderId = getIntent().getStringExtra("orderId");
            if (orderId == null || orderId.isEmpty()) {
                Toast.makeText(this, "Không có đơn hàng để thanh toán", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            // 🔹 Kiểm tra tổng tiền đã tính voucher truyền từ Intent
            orderId = getIntent().getStringExtra("orderId");
            double preCalculatedTotal = getIntent().getDoubleExtra("totalAmount", -1);

            if (preCalculatedTotal > 0) {
                totalAmount = preCalculatedTotal;
                voucherId = getIntent().getStringExtra("voucherId");
                tvTotalAmount.setText("Tổng: " + String.format("%,.0f₫", totalAmount));
                setupPaymentButtons();
            } else {
                fetchOrder(orderId); // chỉ khi thực sự không có totalAmount
            }

        }
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
        if (orderId == null || orderId.isEmpty()) {
            Toast.makeText(this, "Không có đơn hàng để thanh toán", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        orderRepository.getOrderById(orderId, new OrderRepository.RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order order) {
                currentOrder = order;
                runOnUiThread(() -> {
                    // Nếu chỉ thanh toán các món đã chọn
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
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(ThanhToanActivity.this, "Không tìm thấy đơn hàng: " + message, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }


    private void setupPaymentButtons() {

        // ====== TIỀN MẶT ======
        cardCash.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Xác nhận thanh toán")
                .setMessage("Đã nhận tiền khách chưa?")
                .setPositiveButton("Đã nhận", (dialog, which) -> processPayment("Tiền mặt"))
                .setNegativeButton("Hủy", null)
                .show()
        );

        // ====== QR ======
        cardQR.setOnClickListener(v -> {
            if ((orderIds == null || orderIds.isEmpty()) &&
                    (getIntent().getStringExtra("orderId") == null)) {
                Toast.makeText(this, "Không xác định được hóa đơn", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(ThanhToanActivity.this, QRPaymentActivity.class);

            // Gửi tổng số tiền
            intent.putExtra("amount", totalAmount);

            // Gửi toàn bộ orderIds
            if (orderIds != null && !orderIds.isEmpty()) {
                intent.putStringArrayListExtra("orderIds", new ArrayList<>(orderIds));
            } else {
                ArrayList<String> singleOrder = new ArrayList<>();
                singleOrder.add(getIntent().getStringExtra("orderId"));
                intent.putStringArrayListExtra("orderIds", singleOrder);
            }

            // Voucher và discount
            String voucherId = getIntent().getStringExtra("voucherId");
            if (voucherId != null && !voucherId.isEmpty()) {
                intent.putExtra("voucherId", voucherId);
            }

            double voucherDiscount = getIntent().getDoubleExtra("voucherDiscount", 0);
            intent.putExtra("voucherDiscount", voucherDiscount);

            qrLauncher.launch(intent);
        });



        // ====== THẺ NGÂN HÀNG ======
//        cardCard.setOnClickListener(v -> {
//            Intent intent = new Intent(ThanhToanActivity.this, PaymentCardActivity.class);
//
//            if (currentOrder != null) {
//                intent.putExtra("orderId", currentOrder.getId());
//            } else if (orderIds != null && !orderIds.isEmpty()) {
//                intent.putStringArrayListExtra("orderIds", new ArrayList<>(orderIds));
//            } else {
//                Toast.makeText(this, "Không có đơn hàng để thanh toán", Toast.LENGTH_SHORT).show();
//                return;
//            }
//
//            intent.putExtra("amount", totalAmount);
//            startActivity(intent);
//        });

        // ====== DISABLE CARD PAYMENT KHI CÓ VOUCHER ======
        if (hasVoucherApplied()) {
            cardCard.setEnabled(false);
            cardCard.setAlpha(0.4f);
        } else {
            cardCard.setEnabled(true);
            cardCard.setAlpha(1.0f);
        }
    }
    private boolean hasVoucherApplied() {
        String voucherId = getIntent().getStringExtra("voucherId");
        double voucherDiscount = getIntent().getDoubleExtra("voucherDiscount", 0.0);

        return (voucherId != null && !voucherId.trim().isEmpty())
                || voucherDiscount > 0;
    }

    private void processPayment(String method) {
        // Nếu không có hóa đơn nào
        if ((orderIds == null || orderIds.isEmpty()) && (getIntent().getStringExtra("orderId") == null)) {
            Toast.makeText(this, "Không có hóa đơn để thanh toán", Toast.LENGTH_SHORT).show();
            return;
        }

        // ----- Thanh toán nhiều hóa đơn -----
        if (orderIds != null && !orderIds.isEmpty()) {
            final int totalCount = orderIds.size();
            final java.util.concurrent.atomic.AtomicInteger finishedCount = new java.util.concurrent.atomic.AtomicInteger(0);
            final java.util.concurrent.atomic.AtomicBoolean allSuccess = new java.util.concurrent.atomic.AtomicBoolean(true);

            orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository.RepositoryCallback<List<Order>>() {
                @Override
                public void onSuccess(List<Order> allOrders) {
                    // Lấy danh sách orders cần thanh toán
                    List<Order> ordersToPay = new ArrayList<>();
                    for (String orderId : orderIds) {
                        for (Order order : allOrders) {
                            if (order.getId().equals(orderId)) {
                                ordersToPay.add(order);
                                break;
                            }
                        }
                    }

                    int foundCount = ordersToPay.size();
                    runOnUiThread(() -> {
                        Toast.makeText(ThanhToanActivity.this,
                                "Đã tìm thấy " + foundCount + "/" + totalCount + " hóa đơn để thanh toán",
                                Toast.LENGTH_SHORT).show();
                    });

                    if (ordersToPay.isEmpty()) return;

                    // Tính tổng trước khi discount
                    double totalBeforeDiscount = 0.0;
                    for (Order order : ordersToPay) {
                        totalBeforeDiscount += order.getTotalAmount() > 0 ? order.getTotalAmount() : order.getFinalAmount();
                    }

                    // Thanh toán từng hóa đơn
                    for (Order order : ordersToPay) {
                        double orderTotal = order.getTotalAmount() > 0 ? order.getTotalAmount() : order.getFinalAmount();
                        double orderDiscount = totalBeforeDiscount > 0 ?
                                (getIntent().getDoubleExtra("voucherDiscount", 0.0) * orderTotal / totalBeforeDiscount) : 0.0;
                        double orderFinalAmount = orderTotal - orderDiscount;
                        if (orderFinalAmount < 0) orderFinalAmount = 0;

                        orderRepository.payOrder(order.getId(), method, orderFinalAmount, voucherId, new OrderRepository.RepositoryCallback<Order>() {
                            @Override
                            public void onSuccess(Order result) {
                                int finished = finishedCount.incrementAndGet();
                                runOnUiThread(() -> {
                                    Toast.makeText(ThanhToanActivity.this,
                                            "Thanh toán thành công " + finished + "/" + totalCount + " hóa đơn",
                                            Toast.LENGTH_SHORT).show();
                                });

                                if (finished >= totalCount) {
                                    runOnUiThread(() -> resetTableAndFinishMultiple());
                                }
                            }

                            @Override
                            public void onError(String message) {
                                allSuccess.set(false);
                                int finished = finishedCount.incrementAndGet();
                                runOnUiThread(() -> {
                                    Toast.makeText(ThanhToanActivity.this,
                                            "Thanh toán thất bại " + finished + "/" + totalCount + " hóa đơn",
                                            Toast.LENGTH_LONG).show();
                                });
                            }
                        });
                    }
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(ThanhToanActivity.this,
                            "Lỗi khi lấy thông tin hóa đơn: " + message,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
        // ----- Thanh toán 1 hóa đơn -----
        else {
            String orderId = getIntent().getStringExtra("orderId");
            double amountCustomerGiven = method.equals("Tiền mặt") ? totalAmount : 0;
            String voucherIdParam = getIntent().getStringExtra("voucherId");

            Toast.makeText(this, "Bắt đầu thanh toán hóa đơn: " + orderId, Toast.LENGTH_SHORT).show();

            orderRepository.payOrder(orderId, method, amountCustomerGiven, voucherIdParam, new OrderRepository.RepositoryCallback<Order>() {
                @Override
                public void onSuccess(Order updatedOrder) {
                    Toast.makeText(ThanhToanActivity.this,
                            "Thanh toán thành công 1/1 hóa đơn",
                            Toast.LENGTH_SHORT).show();
                    resetTableAndFinish(updatedOrder);
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(ThanhToanActivity.this,
                            "Thanh toán thất bại: " + message,
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }


    private void resetTableAndFinishMultiple() {
        if (tableNumber > 0) {
            tableRepository.getAllTables(new TableRepository.RepositoryCallback<List<TableItem>>() {
                @Override
                public void onSuccess(List<TableItem> tables) {
                    String tableId = null;
                    for (TableItem table : tables) {
                        if (table.getTableNumber() == tableNumber) {
                            tableId = table.getId();
                            break;
                        }
                    }

                    if (tableId != null) {
                        tableRepository.resetTableAfterPayment(tableId, new TableRepository.RepositoryCallback<TableItem>() {
                            @Override
                            public void onSuccess(TableItem table) {
                                finishSuccessMultiple();
                            }

                            @Override
                            public void onError(String message) {
                                finishSuccessMultiple();
                            }
                        });
                    } else {
                        finishSuccessMultiple();
                    }
                }

                @Override
                public void onError(String message) {
                    finishSuccessMultiple();
                }
            });
        } else {
            finishSuccessMultiple();
        }
    }

    private void finishSuccessMultiple() {
        Intent intent = new Intent(this, ThuNganActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
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
