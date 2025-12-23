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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Kiểm tra xem món ăn đã bị hủy
     */
    private boolean isItemCancelled(Order.OrderItem item) {
        if (item == null) return false;
        String status = item.getStatus();
        if (status == null || status.trim().isEmpty()) return false;
        
        String statusLower = status.toLowerCase().trim();
        
        // Kiểm tra đã hủy
        return statusLower.contains("cancelled") ||
               statusLower.contains("canceled") ||
               statusLower.contains("hủy") ||
               statusLower.contains("huy") ||
               statusLower.contains("đã hủy");
    }

    /**
     * Kiểm tra xem món ăn đã xong hoặc đang làm
     */
    private boolean isItemDoneOrPreparing(Order.OrderItem item) {
        if (item == null) return false;
        String status = item.getStatus();
        if (status == null || status.trim().isEmpty()) return false;
        
        String statusLower = status.toLowerCase().trim();
        
        // Nếu đã hủy thì không tính là done/preparing
        if (isItemCancelled(item)) return false;
        
        // Kiểm tra đã xong
        boolean isDone = statusLower.contains("done") || 
                        statusLower.contains("xong") || 
                        statusLower.contains("served") || 
                        statusLower.contains("ready") || 
                        statusLower.contains("completed") ||
                        statusLower.contains("hoàn thành");
        
        // Kiểm tra đang làm
        boolean isPreparing = statusLower.contains("preparing") ||
                             statusLower.contains("in_progress") ||
                             statusLower.contains("processing") ||
                             statusLower.contains("đang làm") ||
                             statusLower.contains("đang nấu");
        
        return isDone || isPreparing;
    }

    /**
     * Tính tổng tiền chỉ từ những món đã xong hoặc đang làm
     * Món đã hủy sẽ có giá 0 đồng
     */
    private double calculateTotalFromDoneOrPreparingItems(List<Order.OrderItem> items) {
        if (items == null || items.isEmpty()) return 0.0;
        
        double total = 0.0;
        for (Order.OrderItem item : items) {
            if (item != null && isItemDoneOrPreparing(item)) {
                // Nếu món đã hủy, giá sẽ là 0
                double itemPrice = isItemCancelled(item) ? 0.0 : item.getPrice();
                total += itemPrice * item.getQuantity();
            }
        }
        return total;
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
                            // Món đã hủy sẽ có giá 0
                            double itemPrice = isItemCancelled(item) ? 0.0 : item.getPrice();
                            totalAmount += itemPrice * item.getQuantity();
                        }
                    } else {
                        // Tính tổng tiền chỉ từ những món đã xong hoặc đang làm
                        currentOrder.normalizeItems();
                        totalAmount = calculateTotalFromDoneOrPreparingItems(currentOrder.getItems());
                        if (totalAmount <= 0) {
                            // Nếu không có món nào đã xong hoặc đang làm, thử lấy từ finalAmount
                            totalAmount = currentOrder.getFinalAmount();
                            if (totalAmount <= 0) {
                                totalAmount = currentOrder.getTotalAmount();
                            }
                        }
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

            // Gửi tableNumber để có thể lấy orders từ server
            intent.putExtra("tableNumber", tableNumber);

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

                    // Tính tổng trước khi discount - chỉ từ những món đã xong hoặc đang làm
                    double totalBeforeDiscount = 0.0;
                    for (Order order : ordersToPay) {
                        order.normalizeItems();
                        double orderTotal = calculateTotalFromDoneOrPreparingItems(order.getItems());
                        if (orderTotal <= 0) {
                            // Nếu không có món nào đã xong hoặc đang làm, thử lấy từ totalAmount
                            orderTotal = order.getTotalAmount();
                            if (orderTotal <= 0) {
                                orderTotal = order.getFinalAmount();
                            }
                        }
                        totalBeforeDiscount += orderTotal;
                    }

                    // Thanh toán từng hóa đơn - chỉ tính từ những món đã xong hoặc đang làm
                    for (Order order : ordersToPay) {
                        order.normalizeItems();
                        double orderTotal = calculateTotalFromDoneOrPreparingItems(order.getItems());
                        if (orderTotal <= 0) {
                            // Nếu không có món nào đã xong hoặc đang làm, thử lấy từ totalAmount
                            orderTotal = order.getTotalAmount();
                            if (orderTotal <= 0) {
                                orderTotal = order.getFinalAmount();
                            }
                        }
                        
                        // Kiểm tra số tiền hợp lệ
                        if (orderTotal <= 0) {
                            Log.w("ThanhToanActivity", "Skipping order " + order.getId() + " - invalid amount: " + orderTotal);
                            int finished = finishedCount.incrementAndGet();
                            allSuccess.set(false);
                            if (finished >= totalCount) {
                                runOnUiThread(() -> {
                                    Toast.makeText(ThanhToanActivity.this, 
                                        "Một số hóa đơn có số tiền không hợp lệ (≤ 0). Vui lòng kiểm tra lại các món đã xong/đang làm.", 
                                        Toast.LENGTH_LONG).show();
                                });
                            }
                            continue;
                        }
                        
                        double orderDiscount = totalBeforeDiscount > 0 ?
                                (getIntent().getDoubleExtra("voucherDiscount", 0.0) * orderTotal / totalBeforeDiscount) : 0.0;
                        double orderFinalAmount = orderTotal - orderDiscount;
                        if (orderFinalAmount < 0) orderFinalAmount = 0;
                        
                        Log.d("ThanhToanActivity", "Paying order " + order.getId() + 
                              ": orderTotal (from done/preparing) = " + orderTotal +
                              ", discount = " + orderDiscount +
                              ", finalAmount = " + orderFinalAmount);
                        
                        // Cập nhật totalAmount và finalAmount trên database trước khi thanh toán
                        // để đảm bảo số tiền khớp với server
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("totalAmount", orderTotal);
                        updates.put("finalAmount", orderFinalAmount);
                        
                        final double finalAmountToPay = orderFinalAmount;
                        final String finalOrderId = order.getId();
                        final String finalMethod = method;
                        final String finalVoucherId = voucherId;
                        
                        orderRepository.updateOrder(order.getId(), updates, new OrderRepository.RepositoryCallback<Order>() {
                            @Override
                            public void onSuccess(Order result) {
                                // Sau khi cập nhật thành công, tiến hành thanh toán
                                Log.d("ThanhToanActivity", "Order " + finalOrderId + " updated successfully, proceeding with payment");
                                paySingleOrderInBatch(finalOrderId, finalMethod, finalAmountToPay, finalVoucherId, 
                                                     finishedCount, totalCount, allSuccess);
                            }
                            
                            @Override
                            public void onError(String message) {
                                Log.w("ThanhToanActivity", "Failed to update order " + finalOrderId + " before payment: " + message + 
                                      ". Proceeding with payment anyway...");
                                // Vẫn tiếp tục thanh toán nếu cập nhật thất bại
                                paySingleOrderInBatch(finalOrderId, finalMethod, finalAmountToPay, finalVoucherId, 
                                                     finishedCount, totalCount, allSuccess);
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
            String voucherIdParam = getIntent().getStringExtra("voucherId");
            
            // Đảm bảo totalAmount đã được tính từ món đã xong/đang làm
            // Nếu currentOrder có sẵn, tính lại từ món đã xong/đang làm
            double calculatedTotal = totalAmount;
            if (currentOrder != null) {
                currentOrder.normalizeItems();
                double tempTotal = calculateTotalFromDoneOrPreparingItems(currentOrder.getItems());
                if (tempTotal > 0) {
                    calculatedTotal = tempTotal;
                }
            }
            
            // Áp dụng voucher discount nếu có
            double voucherDiscount = getIntent().getDoubleExtra("voucherDiscount", 0.0);
            double finalAmountToPay = calculatedTotal - voucherDiscount;
            if (finalAmountToPay < 0) finalAmountToPay = 0;
            
            // Kiểm tra số tiền hợp lệ
            if (finalAmountToPay <= 0) {
                Toast.makeText(this, "Không thể thanh toán: Số tiền không hợp lệ (" + finalAmountToPay + "). Vui lòng kiểm tra lại các món đã xong/đang làm.", Toast.LENGTH_LONG).show();
                Log.e("ThanhToanActivity", "Invalid amount for single order payment: " + finalAmountToPay);
                return;
            }
            
            // Làm final để dùng trong inner class
            final double finalAmount = finalAmountToPay;
            final String finalOrderId = orderId;
            final String finalMethod = method;
            final String finalVoucherId = voucherIdParam;
            
            // Cập nhật totalAmount và finalAmount trên database trước khi thanh toán
            // để đảm bảo số tiền khớp với server
            if (currentOrder != null) {
                currentOrder.normalizeItems();
                double tempCalculatedTotal = calculateTotalFromDoneOrPreparingItems(currentOrder.getItems());
                if (tempCalculatedTotal > 0) {
                    double tempVoucherDiscount = getIntent().getDoubleExtra("voucherDiscount", 0.0);
                    double tempCalculatedFinal = tempCalculatedTotal - tempVoucherDiscount;
                    if (tempCalculatedFinal < 0) tempCalculatedFinal = 0;
                    
                    // Cập nhật order trên database
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("totalAmount", tempCalculatedTotal);
                    updates.put("finalAmount", tempCalculatedFinal);
                    
                    Log.d("ThanhToanActivity", "Updating order " + finalOrderId + 
                          " before payment: totalAmount = " + tempCalculatedTotal +
                          ", finalAmount = " + tempCalculatedFinal);
                    
                    orderRepository.updateOrder(finalOrderId, updates, new OrderRepository.RepositoryCallback<Order>() {
                        @Override
                        public void onSuccess(Order result) {
                            // Sau khi cập nhật thành công, tiến hành thanh toán
                            proceedWithPayment(finalOrderId, finalMethod, finalAmount, finalVoucherId);
                        }
                        
                        @Override
                        public void onError(String message) {
                            Log.w("ThanhToanActivity", "Failed to update order before payment: " + message + 
                                  ". Proceeding with payment anyway...");
                            // Vẫn tiếp tục thanh toán nếu cập nhật thất bại
                            proceedWithPayment(finalOrderId, finalMethod, finalAmount, finalVoucherId);
                        }
                    });
                    return; // Return để chờ callback
                }
            }
            
            // Nếu không cần cập nhật, tiến hành thanh toán trực tiếp
            proceedWithPayment(finalOrderId, finalMethod, finalAmount, finalVoucherId);
        }
    }
    
    /**
     * Helper method để thanh toán một order trong batch (nhiều hóa đơn)
     */
    private void paySingleOrderInBatch(String orderId, String method, double amount, String voucherId,
                                      java.util.concurrent.atomic.AtomicInteger finishedCount,
                                      int totalCount,
                                      java.util.concurrent.atomic.AtomicBoolean allSuccess) {
        orderRepository.payOrder(orderId, method, amount, voucherId, new OrderRepository.RepositoryCallback<Order>() {
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
                            "Thanh toán thất bại " + finished + "/" + totalCount + " hóa đơn: " + message,
                            Toast.LENGTH_LONG).show();
                });
                
                if (finished >= totalCount) {
                    runOnUiThread(() -> resetTableAndFinishMultiple());
                }
            }
        });
    }
    
    private void proceedWithPayment(String orderId, String method, double amountCustomerGiven, String voucherIdParam) {
        Log.d("ThanhToanActivity", "Paying single order " + orderId + 
              ": amountCustomerGiven = " + amountCustomerGiven +
              ", method = " + method);

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
