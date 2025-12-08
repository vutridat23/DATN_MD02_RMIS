package com.ph48845.datn_qlnh_rmis.ui.thanhtoan;

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
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;
import com.ph48845.datn_qlnh_rmis.ui.thungan.ThuNganActivity;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class QRPaymentActivity extends AppCompatActivity {

    private ImageView ivQRCode;
    private TextView tvAmount;
    private Button btnThanhToan;

    private String orderId;
    private double amount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrpayment);

        ivQRCode = findViewById(R.id.ivQRCode);
        tvAmount = findViewById(R.id.tvQRAmount);
        btnThanhToan = findViewById(R.id.btnThanhToan);

        orderId = getIntent().getStringExtra("orderId");
        amount = getIntent().getDoubleExtra("amount", 0);

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

    private void payOrder() {
        ApiService api = RetrofitClient.getInstance().getApiService();

        Map<String, Object> body = new HashMap<>();
        body.put("orderId", orderId);
        body.put("paidAmount", amount);
        body.put("paymentMethod", "QR");

        api.payOrder(body).enqueue(new Callback<ApiResponse<Order>>() {
            @Override
            public void onResponse(Call<ApiResponse<Order>> call, Response<ApiResponse<Order>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {

                    // 🔊 Phát âm thanh ting-ting
                    MediaPlayer mediaPlayer = MediaPlayer.create(QRPaymentActivity.this, R.raw.ting_ting);
                    mediaPlayer.start();

                    // 🔔 Gửi thông báo
//                    sendPaymentNotification(amount);

                    Toast.makeText(QRPaymentActivity.this, "Thanh toán QR thành công!", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(QRPaymentActivity.this, ThuNganActivity.class);
                    intent.putExtra("orderId", orderId);
                    startActivity(intent);

                    finish();
                } else {
                    Toast.makeText(QRPaymentActivity.this,
                            "Thanh toán thất bại: " + (response.body() != null ? response.body().getMessage() : "Lỗi server"),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Order>> call, Throwable t) {
                Toast.makeText(QRPaymentActivity.this, "Thanh toán thất bại: " + t.getMessage(), Toast.LENGTH_LONG).show();
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
