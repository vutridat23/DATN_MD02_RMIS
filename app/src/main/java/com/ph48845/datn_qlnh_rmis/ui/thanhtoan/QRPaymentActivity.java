package com.ph48845.datn_qlnh_rmis.ui.thanhtoan;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

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
                .setPositiveButton("Đã nhận", (dialog, which) -> payOrder()) // chỉ gọi khi xác nhận
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
                    Toast.makeText(QRPaymentActivity.this, "Thanh toán QR thành công", Toast.LENGTH_SHORT).show();

                    // Chuyển sang màn ThuNganActivity
                    Intent intent = new Intent(QRPaymentActivity.this, ThuNganActivity.class);
                    intent.putExtra("orderId", orderId); // nếu cần truyền dữ liệu
                    startActivity(intent);

                    // Kết thúc màn QRPaymentActivity
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

}
