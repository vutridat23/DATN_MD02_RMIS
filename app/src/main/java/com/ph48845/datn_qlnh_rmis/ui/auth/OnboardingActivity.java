package com.ph48845.datn_qlnh_rmis.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.OnboardingItem;
import com.ph48845.datn_qlnh_rmis.ui.auth.Adapter.OnboardingAdapter;

import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private LinearLayout layoutOnboardingIndicators;
    private ImageView btnAction;
    private TextView tvSkip;
    private OnboardingAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        viewPager = findViewById(R.id.viewPager);
        layoutOnboardingIndicators = findViewById(R.id.layoutOnboardingIndicators);
        btnAction = findViewById(R.id.btnNext);
        tvSkip = findViewById(R.id.tvSkip);

        List<OnboardingItem> items = new ArrayList<>();
        items.add(new OnboardingItem(
                R.drawable.banner_login,
                "Gọi món tại bàn",
                "Nhân viên order trực tiếp trên điện thoại, đơn hàng được chuyển ngay xuống bếp không cần đi lại."
        ));
        items.add(new OnboardingItem(
                R.drawable.banner_login2,
                "Quản lý Bếp & Bar",
                "Hiển thị danh sách món cần làm theo thứ tự thời gian, báo hết món và trả món nhanh chóng."
        ));
        items.add(new OnboardingItem(
                R.drawable.banner_login3,
                "Báo cáo doanh thu",
                "Theo dõi dòng tiền, hóa đơn và hiệu suất nhân viên theo thời gian thực chính xác tuyệt đối."
        ));

        adapter = new OnboardingAdapter(items);
        viewPager.setAdapter(adapter);

        setupOnboardingIndicators();
        setCurrentOnboardingIndicator(0);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                setCurrentOnboardingIndicator(position);
            }
        });

        btnAction.setOnClickListener(v -> {
            if (viewPager.getCurrentItem() + 1 < adapter.getItemCount()) {
                viewPager.setCurrentItem(viewPager.getCurrentItem() + 1);
            } else {
                startActivity(new Intent(OnboardingActivity.this, LoginActivity.class));
                finish();
            }
        });

        tvSkip.setOnClickListener(v -> {
            startActivity(new Intent(OnboardingActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void setupOnboardingIndicators() {
        ImageView[] indicators = new ImageView[adapter.getItemCount()];
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(12, 0, 12, 0);

        layoutOnboardingIndicators.removeAllViews();

        for (int i = 0; i < indicators.length; i++) {
            indicators[i] = new ImageView(getApplicationContext());
            indicators[i].setImageDrawable(ContextCompat.getDrawable(
                    getApplicationContext(),
                    R.drawable.tab_indicator
            ));
            indicators[i].setLayoutParams(layoutParams);
            layoutOnboardingIndicators.addView(indicators[i]);
        }
    }

    private void setCurrentOnboardingIndicator(int index) {
        int childCount = layoutOnboardingIndicators.getChildCount();
        for (int i = 0; i < childCount; i++) {
            ImageView imageView = (ImageView) layoutOnboardingIndicators.getChildAt(i);
            if (i == index) {
                imageView.setSelected(true);
            } else {
                imageView.setSelected(false);
            }
        }

        if (index == adapter.getItemCount() - 1) {
            btnAction.setImageResource(R.drawable.ic_arrow_forward_green);
            tvSkip.setVisibility(View.INVISIBLE);
        }
    }
}