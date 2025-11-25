package com.ph48845.datn_qlnh_rmis.ui.thungan;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.ui.revenue.RevenueActivity;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvEmployeeName;
    private CardView cardHistory;
    private CardView cardRevenue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        tvEmployeeName = findViewById(R.id.tvEmployeeName);
        cardHistory = findViewById(R.id.cardHistory);
        cardRevenue = findViewById(R.id.cardRevenue);

        loadEmployeeData();

        setupClickEvents();
    }

    private void loadEmployeeData() {
        SharedPreferences prefs = getSharedPreferences("RestaurantPrefs", MODE_PRIVATE);
        String fullName = prefs.getString("fullName", "Nhân viên");
        tvEmployeeName.setText(fullName);
    }

    private void setupClickEvents() {
        cardHistory.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileActivity.this, HistoryActivity.class);
            startActivity(intent);
        });


        cardRevenue.setOnClickListener(v ->{
            Intent intent = new Intent(ProfileActivity.this, RevenueActivity.class);
            startActivity(intent);
                });
    }
}
