package com.ph48845.datn_qlnh_rmis.ui.thanhtoan;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.ph48845.datn_qlnh_rmis.R;

public class QRPaymentActivity extends AppCompatActivity {

    ImageView ivQRCode;
    TextView tvAmount;
    Button btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qrpayment);

        ivQRCode = findViewById(R.id.ivQRCode);
        tvAmount = findViewById(R.id.tvQRAmount);
        btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v->{
            finish();
        });

        double amount = getIntent().getDoubleExtra("amount", 0);
        tvAmount.setText(String.format("%,.0f₫", amount));

        generateQR("PAY|" + amount);
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
}

