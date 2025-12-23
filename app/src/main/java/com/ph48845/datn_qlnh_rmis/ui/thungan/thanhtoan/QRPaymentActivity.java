package com.ph48845.datn_qlnh_rmis.ui.thungan.thanhtoan;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.ph48845.datn_qlnh_rmis.R;
import android.util.Log;

import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.ui.thungan.ThuNganActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class QRPaymentActivity extends AppCompatActivity {

    private ImageView ivQRCode;
    private TextView tvAmount;
    private Button btnThanhToan;
    private String voucherId;

    private String orderId;
    private double amount;
    private OrderRepository orderRepository;
    private int tableNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrpayment);

        ivQRCode = findViewById(R.id.ivQRCode);
        tvAmount = findViewById(R.id.tvQRAmount);
        btnThanhToan = findViewById(R.id.btnThanhToan);

        orderRepository = new OrderRepository();
        
        orderId = getIntent().getStringExtra("orderId");
        amount = getIntent().getDoubleExtra("amount", 0);
        voucherId = getIntent().getStringExtra("voucherId");
        tableNumber = getIntent().getIntExtra("tableNumber", 0);
        tvAmount.setText(String.format("%,.0f₫", amount));

        generateQR("PAY|" + amount);

        // Tạo Notification Channel
        createNotificationChannel();

        // Xin quyền hiển thị thông báo
        requestNotificationPermission();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            sendPaymentNotification(amount);

            // 🔊 Phát âm thanh ting-ting
            MediaPlayer mediaPlayer = MediaPlayer.create(QRPaymentActivity.this, R.raw.ting_ting);
            mediaPlayer.start();
        }, 5000);

        btnThanhToan.setOnClickListener(v -> showConfirmDialog());
    }

    private void generateQR(String content) {
        try {
            BarcodeEncoder encoder = new BarcodeEncoder();
            Bitmap bitmap = encoder.encodeBitmap(content, BarcodeFormat.QR_CODE, 600, 600);
            ivQRCode.setImageBitmap(bitmap);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận thanh toán QR")
                .setMessage("Khách hàng đã quét và thanh toán chưa?")
                .setPositiveButton("Đã nhận", (dialog, which) -> payOrder())
                .setNegativeButton("Chưa nhận", null)
                .show();
    }

    /**
     * Kiểm tra xem món ăn đã bị hủy
     */
    private boolean isItemCancelled(Order.OrderItem item) {
        if (item == null) return false;
        String status = item.getStatus();
        if (status == null || status.trim().isEmpty()) return false;
        
        String statusLower = status.toLowerCase().trim();
        
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

    private void payOrder() {
        // Lấy tất cả orderIds từ Intent
        ArrayList<String> orderIds = getIntent().getStringArrayListExtra("orderIds");
        if (orderIds == null || orderIds.isEmpty()) {
            Toast.makeText(this, "Không có hóa đơn để thanh toán", Toast.LENGTH_SHORT).show();
            return;
        }

        double voucherDiscount = getIntent().getDoubleExtra("voucherDiscount", 0);
        String voucherId = getIntent().getStringExtra("voucherId");

        final int totalCount = orderIds.size();
        final java.util.concurrent.atomic.AtomicInteger finishedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicBoolean allSuccess = new java.util.concurrent.atomic.AtomicBoolean(true);

        // Lấy danh sách orders từ server để tính số tiền chính xác
        if (tableNumber > 0) {
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

                    if (ordersToPay.isEmpty()) {
                        Toast.makeText(QRPaymentActivity.this, "Không tìm thấy hóa đơn để thanh toán", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Tính tổng trước khi discount - chỉ từ những món đã xong hoặc đang làm
                    double totalBeforeDiscount = 0.0;
                    for (Order order : ordersToPay) {
                        order.normalizeItems();
                        double orderTotal = calculateTotalFromDoneOrPreparingItems(order.getItems());
                        if (orderTotal <= 0) {
                            orderTotal = order.getTotalAmount();
                            if (orderTotal <= 0) {
                                orderTotal = order.getFinalAmount();
                            }
                        }
                        totalBeforeDiscount += orderTotal;
                    }

                    // Thanh toán từng hóa đơn
                    for (Order order : ordersToPay) {
                        order.normalizeItems();
                        double orderTotal = calculateTotalFromDoneOrPreparingItems(order.getItems());
                        if (orderTotal <= 0) {
                            orderTotal = order.getTotalAmount();
                            if (orderTotal <= 0) {
                                orderTotal = order.getFinalAmount();
                            }
                        }
                        
                        // Kiểm tra số tiền hợp lệ
                        if (orderTotal <= 0) {
                            Log.w("QRPaymentActivity", "Skipping order " + order.getId() + " - invalid amount: " + orderTotal);
                            int finished = finishedCount.incrementAndGet();
                            allSuccess.set(false);
                            if (finished >= totalCount) {
                                runOnUiThread(() -> {
                                    Toast.makeText(QRPaymentActivity.this, 
                                        "Một số hóa đơn có số tiền không hợp lệ (≤ 0). Vui lòng kiểm tra lại các món đã xong/đang làm.", 
                                        Toast.LENGTH_LONG).show();
                                });
                            }
                            continue;
                        }
                        
                        double orderDiscount = totalBeforeDiscount > 0 ?
                                (voucherDiscount * orderTotal / totalBeforeDiscount) : 0.0;
                        double orderFinalAmount = orderTotal - orderDiscount;
                        if (orderFinalAmount < 0) orderFinalAmount = 0;
                        
                        Log.d("QRPaymentActivity", "Paying order " + order.getId() + 
                              ": orderTotal (from done/preparing) = " + orderTotal +
                              ", discount = " + orderDiscount +
                              ", finalAmount = " + orderFinalAmount);
                        
                        // Cập nhật totalAmount và finalAmount trên database trước khi thanh toán
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("totalAmount", orderTotal);
                        updates.put("finalAmount", orderFinalAmount);
                        
                        final double finalAmountToPay = orderFinalAmount;
                        final String finalOrderId = order.getId();
                        final String finalVoucherId = voucherId;
                        
                        orderRepository.updateOrder(finalOrderId, updates, new OrderRepository.RepositoryCallback<Order>() {
                            @Override
                            public void onSuccess(Order result) {
                                // Sau khi cập nhật thành công, tiến hành thanh toán
                                paySingleOrder(finalOrderId, "QR", finalAmountToPay, finalVoucherId, finishedCount, totalCount, allSuccess);
                            }
                            
                            @Override
                            public void onError(String message) {
                                Log.w("QRPaymentActivity", "Failed to update order before payment: " + message + 
                                      ". Proceeding with payment anyway...");
                                // Vẫn tiếp tục thanh toán nếu cập nhật thất bại
                                paySingleOrder(finalOrderId, "QR", finalAmountToPay, finalVoucherId, finishedCount, totalCount, allSuccess);
                            }
                        });
                    }
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(QRPaymentActivity.this, "Không thể lấy thông tin hóa đơn: " + message, Toast.LENGTH_LONG).show();
                }
            });
        } else {
            // Nếu không có tableNumber, thanh toán trực tiếp với số tiền đã cho
            double amountPerOrder = totalCount > 0 ? amount / totalCount : amount;
            for (String id : orderIds) {
                paySingleOrder(id, "QR", amountPerOrder, voucherId, finishedCount, totalCount, allSuccess);
            }
        }
    }

    /**
     * Helper method để thanh toán một order
     */
    private void paySingleOrder(String orderId, String method, double amount, String voucherId,
                               java.util.concurrent.atomic.AtomicInteger finishedCount,
                               int totalCount,
                               java.util.concurrent.atomic.AtomicBoolean allSuccess) {
        orderRepository.payOrder(orderId, method, amount, voucherId, new OrderRepository.RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order result) {
                int finished = finishedCount.incrementAndGet();
                runOnUiThread(() -> {
                    Toast.makeText(QRPaymentActivity.this,
                            "Thanh toán thành công " + finished + "/" + totalCount + " hóa đơn",
                            Toast.LENGTH_SHORT).show();
                });

                if (finished >= totalCount) {
                    runOnUiThread(() -> {
                        MediaPlayer mediaPlayer =
                                MediaPlayer.create(QRPaymentActivity.this, R.raw.ting_ting);
                        mediaPlayer.start();

                        Intent intent = new Intent(QRPaymentActivity.this, ThuNganActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                        finish();
                    });
                }
            }

            @Override
            public void onError(String message) {
                allSuccess.set(false);
                int finished = finishedCount.incrementAndGet();
                runOnUiThread(() -> {
                    Toast.makeText(QRPaymentActivity.this,
                            "Thanh toán thất bại " + finished + "/" + totalCount + " hóa đơn: " + message,
                            Toast.LENGTH_LONG).show();
                });

                if (finished >= totalCount) {
                    runOnUiThread(() -> {
                        Intent intent = new Intent(QRPaymentActivity.this, ThuNganActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                        finish();
                    });
                }
            }
        });
    }


    // ------------------ NOTIFICATION FUNCTIONS ------------------

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
    }

    private void sendPaymentNotification(double amount) {

        // Kiểm tra quyền trước khi notify
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, "payment_channel")
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("Đã nhận thanh toán")
                        .setContentText("Đã nhận được " + String.format("%,.0f₫", amount))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setSilent(true)
                        .setAutoCancel(true);

        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        manager.notify(1001, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "payment_channel",
                    "Payment Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Thông báo khi nhận thanh toán");
            channel.setSound(null, null);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
}
