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
        // ====== LOG KIỂM TRA INTENT ======
        String orderId = getIntent().getStringExtra("orderId");
        ArrayList<String> orderIds = getIntent().getStringArrayListExtra("orderIds");
        double totalAmount = getIntent().getDoubleExtra("totalAmount", -1);
        int tableNumber = getIntent().getIntExtra("tableNumber", -1);
        String voucherId = getIntent().getStringExtra("voucherId");

        Log.d("ThanhToanActivity", "orderId=" + orderId);
        Log.d("ThanhToanActivity", "orderIds=" + orderIds);
        Log.d("ThanhToanActivity", "totalAmount=" + totalAmount);
        Log.d("ThanhToanActivity", "tableNumber=" + tableNumber);
        Log.d("ThanhToanActivity", "voucherId=" + voucherId);

        initViews();

//        excludeUnreadyItems = getIntent().getBooleanExtra("excludeUnreadyItems", false);
//
//        if (excludeUnreadyItems) {
//            payItems = (ArrayList<Order.OrderItem>)
//                    getIntent().getSerializableExtra("pay_items");
//
//        }



        excludeUnreadyItems = getIntent().getBooleanExtra("excludeUnreadyItems", false);

        if (excludeUnreadyItems) {
            payItems = (ArrayList<Order.OrderItem>) getIntent().getSerializableExtra("pay_items");
        }


        orderRepository = new OrderRepository();
        tableRepository = new TableRepository();

        // Kiểm tra xem có nhiều orders hay một order
        ArrayList<String> orderIdsList = getIntent().getStringArrayListExtra("orderIds");
        if (orderIdsList != null && !orderIdsList.isEmpty()) {
            // Trường hợp thanh toán nhiều hóa đơn
            orderIds = orderIdsList;
            totalAmount = getIntent().getDoubleExtra("totalAmount", 0.0);
            tableNumber = getIntent().getIntExtra("tableNumber", 0);
            voucherId = getIntent().getStringExtra("voucherId");
            
            if (totalAmount <= 0) {
                Toast.makeText(this, "Lỗi: Tổng tiền không hợp lệ", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            
            tvTotalAmount.setText("Tổng: " + String.format("%,.0f₫", totalAmount));
            setupPaymentButtons();
        } else {
            // Trường hợp thanh toán một hóa đơn
            orderId = getIntent().getStringExtra("orderId");
            if (orderId == null || orderId.isEmpty()) {
                Toast.makeText(this, "Lỗi: không có đơn hàng để thanh toán", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            
            // Kiểm tra xem có tổng tiền đã tính sẵn không (từ InvoiceActivity với voucher)
            double preCalculatedTotal = getIntent().getDoubleExtra("totalAmount", -1);
            if (preCalculatedTotal > 0) {
                // Có tổng tiền đã tính sẵn (đã có voucher), dùng luôn
                totalAmount = preCalculatedTotal;
                tableNumber = getIntent().getIntExtra("tableNumber", 0);
                voucherId = getIntent().getStringExtra("voucherId");

                tvTotalAmount.setText("Tổng: " + String.format("%,.0f₫", totalAmount));
                setupPaymentButtons();
                return;
            }
            else {
                // Không có tổng tiền tính sẵn, fetch order và tính lại
                fetchOrder(orderId);
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
                            double preCalculatedTotal = getIntent().getDoubleExtra("totalAmount", -1);

                            if (preCalculatedTotal > 0) {
                                // ✅ ĐÃ ÁP VOUCHER → KHÔNG TÍNH LẠI
                                totalAmount = preCalculatedTotal;
                            } else {
                                // ❌ KHÔNG CÓ VOUCHER → TÍNH BÌNH THƯỜNG
                                totalAmount = currentOrder.getFinalAmount();
                            }
                        }


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
            Intent intent = new Intent(ThanhToanActivity.this, QRPaymentActivity.class);
            intent.putExtra("amount", totalAmount);
            if (currentOrder != null) {
                intent.putExtra("orderId", currentOrder.getId());
            } else if (orderIds != null && !orderIds.isEmpty()) {
                intent.putExtra("orderId", orderIds.get(0));
            }
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
        if (orderIds != null && !orderIds.isEmpty()) {
            // Thanh toán nhiều hóa đơn
            processMultipleOrdersPayment(method);
        } else if (currentOrder != null) {
            // Thanh toán một hóa đơn (như cũ)
            double amountCustomerGiven = method.equals("Tiền mặt") ? totalAmount : 0;
            String voucherIdParam = getIntent().getStringExtra("voucherId");

            orderRepository.payOrder(currentOrder.getId(), method, amountCustomerGiven, voucherIdParam, new OrderRepository.RepositoryCallback<Order>() {
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
        } else {
            Toast.makeText(this, "Lỗi: không có đơn hàng để thanh toán", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * Thanh toán nhiều hóa đơn
     */
    private void processMultipleOrdersPayment(String method) {
        if (orderIds == null || orderIds.isEmpty()) {
            Toast.makeText(this, "Lỗi: không có hóa đơn để thanh toán", Toast.LENGTH_SHORT).show();
            return;
        }

        double amountCustomerGiven = method.equals("Tiền mặt") ? totalAmount : 0;
        
        // Tính discount cho mỗi hóa đơn
        double discountPerOrder = orderIds.size() > 0 ? 
            (getIntent().getDoubleExtra("voucherDiscount", 0.0) / orderIds.size()) : 0.0;
        
        final java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger totalCount = new java.util.concurrent.atomic.AtomicInteger(orderIds.size());
        final java.util.concurrent.atomic.AtomicBoolean allSuccess = new java.util.concurrent.atomic.AtomicBoolean(true);
        
        // Lấy totalAmount của từng order để tính lại
        orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> allOrders) {
                // Tìm các orders cần thanh toán
                List<Order> ordersToPay = new ArrayList<>();
                for (String orderId : orderIds) {
                    for (Order order : allOrders) {
                        if (order.getId().equals(orderId)) {
                            ordersToPay.add(order);
                            break;
                        }
                    }
                }
                
                if (ordersToPay.isEmpty()) {
                    Toast.makeText(ThanhToanActivity.this, "Không tìm thấy hóa đơn để thanh toán", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Tính lại discount cho mỗi order dựa trên tỷ lệ
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
                            int current = successCount.incrementAndGet();
                            if (current >= totalCount.get()) {
                                runOnUiThread(() -> {
                                    if (allSuccess.get()) {
                                        Toast.makeText(ThanhToanActivity.this, "Thanh toán thành công " + totalCount.get() + " hóa đơn", Toast.LENGTH_SHORT).show();
                                        resetTableAndFinishMultiple();
                                    } else {
                                        Toast.makeText(ThanhToanActivity.this, "Một số hóa đơn thanh toán thất bại", Toast.LENGTH_LONG).show();
                                        finish();
                                    }
                                });
                            }
                        }

                        @Override
                        public void onError(String message) {
                            allSuccess.set(false);
                            int current = successCount.incrementAndGet();
                            if (current >= totalCount.get()) {
                                runOnUiThread(() -> {
                                    Toast.makeText(ThanhToanActivity.this, "Thanh toán thất bại: " + message, Toast.LENGTH_LONG).show();
                                    finish();
                                });
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ThanhToanActivity.this, "Lỗi khi lấy thông tin hóa đơn: " + message, Toast.LENGTH_SHORT).show();
            }
        });
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
