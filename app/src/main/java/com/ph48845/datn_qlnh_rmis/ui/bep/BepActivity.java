package com.ph48845.datn_qlnh_rmis.ui.bep;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;
import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.ui.bep.adapter.OrderItemAdapter;

import java.util.ArrayList;
import com.ph48845.datn_qlnh_rmis.core.base.BaseMenuActivity;


/**
 * BepActivity - màn cho Bếp
 *
 * Sửa để khớp với activity_bep.xml bạn gửi:
 * - recyclerOrderBep -> RecyclerView id
 * - progress_bep -> ProgressBar id
 * - btn_bep_realtime / btn_bep_refresh -> controls
 *
 * Giữ nguyên logic: dùng BepViewModel để fetchOrders(), start/stop realtime,
 * và changeItemStatus() khi bấm nút trong adapter.
 */
public class BepActivity extends BaseMenuActivity implements OrderItemAdapter.OnActionListener {

    private static final String TAG = "BepActivity";
    private static final String SOCKET_URL = "http://192.168.1.229:3000"; // đổi theo server của bạn

    private RecyclerView rvKitchen;
    private ProgressBar progressBar;
    private Button btnRealtime;
    private Button btnRefresh;

    private BepViewModel viewModel;
    private OrderItemAdapter adapter;
    DrawerLayout drawerLayout;
    NavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bep);

        // Views (IDs must match activity_bep.xml)
        rvKitchen = findViewById(R.id.recyclerOrderBep);
        progressBar = findViewById(R.id.progress_bep);
        btnRealtime = findViewById(R.id.btn_bep_realtime);
        btnRefresh = findViewById(R.id.btn_bep_refresh);

        // RecyclerView setup
        rvKitchen.setLayoutManager(new LinearLayoutManager(this));
        adapter = new OrderItemAdapter(this);
        adapter.setItems(new ArrayList<>());
        rvKitchen.setAdapter(adapter);

        drawerLayout = findViewById(R.id.drawerLayout_bep);
        Toolbar toolbar = findViewById(R.id.toolbar);
        navigationView = findViewById(R.id.navigationView_bep);

        toolbar.setNavigationOnClickListener(v -> {
            drawerLayout.openDrawer(GravityCompat.START);
        });

        for (int i = 0; i < navigationView.getMenu().size(); i++) {
            MenuItem menuItem = navigationView.getMenu().getItem(i);
            SpannableString spanString = new SpannableString(menuItem.getTitle().toString());
            spanString.setSpan(new RelativeSizeSpan(1.1f), 0, spanString.length(), 0);
            menuItem.setTitle(spanString);
        }

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_mood) {
                showMoodDialog();
            } else if (id == R.id.nav_contact) {
                showContactDialog();
            } else if (id == R.id.nav_logout) {
                logout();
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        updateNavHeaderInfo();

        // ViewModel
        viewModel = new ViewModelProvider(this).get(BepViewModel.class);

        // Observe items
        viewModel.getItemsLive().observe(this, items -> {
            if (items == null || items.isEmpty()) {
                adapter.setItems(new ArrayList<>());
            } else {
                adapter.setItems(items);
            }
        });

        // Observe loading
        viewModel.getLoadingLive().observe(this, loading -> {
            if (progressBar != null) progressBar.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE);
        });

        // Observe errors
        viewModel.getErrorLive().observe(this, err -> {
            if (err != null && !err.trim().isEmpty()) {
                Toast.makeText(BepActivity.this, err, Toast.LENGTH_LONG).show();
                Log.w(TAG, "Error: " + err);
            }
        });

        // Refresh button -> fetch orders
        btnRefresh.setOnClickListener(v -> {
            try {
                viewModel.fetchOrders();
            } catch (Exception e) {
                Log.e(TAG, "refresh failed: " + e.getMessage(), e);
                Toast.makeText(this, "Làm mới thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Realtime toggle button
        btnRealtime.setOnClickListener(v -> {
            String txt = btnRealtime.getText() != null ? btnRealtime.getText().toString() : "";
            if (txt.toLowerCase().contains("off")) {
                // start realtime
                try {
                    viewModel.startRealtime(SOCKET_URL);
                    btnRealtime.setText("Realtime ON");
                } catch (Exception e) {
                    Log.w(TAG, "startRealtime failed: " + e.getMessage(), e);
                    Toast.makeText(this, "Không thể bật realtime: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                // stop realtime
                try {
                    viewModel.stopRealtime();
                    btnRealtime.setText("Realtime OFF");
                } catch (Exception e) {
                    Log.w(TAG, "stopRealtime failed: " + e.getMessage(), e);
                    Toast.makeText(this, "Không thể tắt realtime: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Initial data load (do not forcibly start realtime)
        viewModel.fetchOrders();
    }

    private void updateNavHeaderInfo() {
        // 1. Lấy tham chiếu đến NavigationView
        NavigationView navigationView = findViewById(R.id.navigationView_bep);

        // Kiểm tra null để tránh lỗi crash nếu chưa setup layout đúng
        if (navigationView != null) {
            // 2. Lấy Header View (cái layout XML bạn gửi lúc đầu nằm ở đây)
            View headerView = navigationView.getHeaderView(0);

            // 3. Ánh xạ các TextView trong Header
            TextView tvName = headerView.findViewById(R.id.textViewName);
            TextView tvRole = headerView.findViewById(R.id.textViewRole);

            // 4. Lấy dữ liệu từ SharedPreferences (dùng đúng tên file và key bạn đã lưu)
            SharedPreferences prefs = getSharedPreferences("RestaurantPrefs", MODE_PRIVATE);

            String savedName = prefs.getString("fullName", "Người dùng"); // "Người dùng" là giá trị mặc định
            String savedRole = prefs.getString("userRole", "");

            // 5. Set text lên giao diện
            tvName.setText(savedName);
            tvRole.setText(getVietnameseRole(savedRole)); // Hàm chuyển đổi role sang tiếng Việt
        }
    }

    // Hàm phụ trợ chuyển đổi Role (giữ nguyên logic cũ cho đồng bộ)
    private String getVietnameseRole(String roleKey) {
        if (roleKey == null) return "";
        switch (roleKey.toLowerCase()) {
            case "cashier":
                return "Thu ngân";
            case "manager":
            case "order":
                return "Phục vụ";
            case "kitchen":
                return "Bếp";
            default:
                return roleKey;
        }
    }

    @Override
    protected void onDestroy() {
        try {
            viewModel.stopRealtime();
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    /**
     * OrderItemAdapter.OnActionListener callback
     * Called when Bếp bấm 1 trong 4 nút (received/preparing/ready/soldout).
     * Gọi ViewModel để cập nhật trạng thái (ViewModel sẽ gọi API và cập nhật local).
     */
    @Override
    public void onChangeStatus(ItemWithOrder wrapper, String newStatus) {
        if (wrapper == null || newStatus == null) return;
        try {
            viewModel.changeItemStatus(wrapper, newStatus);
        } catch (Exception e) {
            Log.e(TAG, "changeItemStatus failed: " + e.getMessage(), e);
            Toast.makeText(this, "Không thể thay đổi trạng thái: " + (e.getMessage() != null ? e.getMessage() : ""), Toast.LENGTH_LONG).show();
        }
    }
}