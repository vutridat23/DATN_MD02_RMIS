package com.ph48845.datn_qlnh_rmis.ui.thungan;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;
import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.core.base.BaseMenuActivity;

import com.ph48845.datn_qlnh_rmis.ui.thungan.adapter.ThuNganAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Activity hiển thị danh sách bàn ăn đang hoạt động cho hệ thống thu ngân.
 * Chỉ hiển thị các bàn có status: OCCUPIED, PENDING_PAYMENT, FINISH_SERVE.
 * Hiển thị trạng thái phục vụ: "Đang phục vụ lên món" (xanh) hoặc "Đã phục vụ đủ món" (đỏ).
 */
public class ThuNganActivity extends BaseMenuActivity {

    private static final String TAG = "ThuNganActivity";
    
    private Toolbar toolbar;
    private ProgressBar progressBar;
    private RecyclerView rvFloor1;
    private RecyclerView rvFloor2;
    private TextView headerFloor1;
    private TextView headerFloor2;
    
    private ThuNganAdapter adapterFloor1;
    private ThuNganAdapter adapterFloor2;
    private ThuNganViewModel viewModel;
    private OrderRepository orderRepository;
    DrawerLayout drawerLayout;
    NavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thu_ngan);

        initViews();
        setupToolbar();
        setupRecyclerViews();
        
        viewModel = new ThuNganViewModel();
        orderRepository = new OrderRepository();

        drawerLayout = findViewById(R.id.drawerLayout_thungan);
        Toolbar toolbar = findViewById(R.id.toolbar);
        navigationView = findViewById(R.id.navigationView_thungan);

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

        // Load dữ liệu
        loadActiveTables();
    }

    private void updateNavHeaderInfo() {
        // 1. Lấy tham chiếu đến NavigationView
        NavigationView navigationView = findViewById(R.id.navigationView_thungan);

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

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        progressBar = findViewById(R.id.progress_bar_loading);
        rvFloor1 = findViewById(R.id.recycler_floor1);
        rvFloor2 = findViewById(R.id.recycler_floor2);
        headerFloor1 = findViewById(R.id.header_floor1);
        headerFloor2 = findViewById(R.id.header_floor2);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerViews() {
        // Grid với 3 cột
        rvFloor1.setLayoutManager(new GridLayoutManager(this, 3));
        rvFloor2.setLayoutManager(new GridLayoutManager(this, 3));
        
        rvFloor1.setNestedScrollingEnabled(false);
        rvFloor2.setNestedScrollingEnabled(false);

        // Tạo adapter với click listener
        ThuNganAdapter.OnTableClickListener listener = table -> {
            // Chuyển sang màn hình hóa đơn
            Intent intent = new Intent(ThuNganActivity.this, InvoiceActivity.class);
            intent.putExtra("tableNumber", table.getTableNumber());
            startActivity(intent);
        };

        adapterFloor1 = new ThuNganAdapter(this, new ArrayList<>(), listener);
        adapterFloor2 = new ThuNganAdapter(this, new ArrayList<>(), listener);
        
        rvFloor1.setAdapter(adapterFloor1);
        rvFloor2.setAdapter(adapterFloor2);
    }

    /**
     * Load danh sách bàn đang hoạt động
     */
    private void loadActiveTables() {
        progressBar.setVisibility(View.VISIBLE);
        
        viewModel.loadActiveTables(new ThuNganViewModel.DataCallback() {
            @Override
            public void onTablesLoaded(List<TableItem> floor1Tables, List<TableItem> floor2Tables) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    
                    // Cập nhật adapter
                    adapterFloor1.updateList(floor1Tables);
                    adapterFloor2.updateList(floor2Tables);
                    
                    // Ẩn/hiện header theo số lượng bàn
                    if (floor1Tables.isEmpty()) {
                        headerFloor1.setVisibility(View.GONE);
                    } else {
                        headerFloor1.setVisibility(View.VISIBLE);
                    }
                    
                    if (floor2Tables.isEmpty()) {
                        headerFloor2.setVisibility(View.GONE);
                    } else {
                        headerFloor2.setVisibility(View.VISIBLE);
                    }
                    
                    // Load orders để xác định trạng thái phục vụ chi tiết
                    loadOrdersForServingStatus(floor1Tables, floor2Tables);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(ThuNganActivity.this, "Lỗi tải danh sách bàn: " + message, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error loading tables: " + message);
                });
            }
        });
    }

    /**
     * Load orders để xác định trạng thái phục vụ chi tiết cho từng bàn.
     * Cập nhật status của table nếu tất cả món đã được phục vụ.
     */
    private void loadOrdersForServingStatus(List<TableItem> floor1Tables, List<TableItem> floor2Tables) {
        // Lấy tất cả orders
        orderRepository.getOrdersByTableNumber(null, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> allOrders) {
                // Tạo map: tableNumber -> List<Order>
                Map<Integer, List<Order>> ordersByTable = new HashMap<>();
                if (allOrders != null) {
                    for (Order order : allOrders) {
                        if (order == null) continue;
                        order.normalizeItems();
                        int tableNum = order.getTableNumber();
                        if (!ordersByTable.containsKey(tableNum)) {
                            ordersByTable.put(tableNum, new ArrayList<>());
                        }
                        ordersByTable.get(tableNum).add(order);
                    }
                }

                // Xác định trạng thái phục vụ cho từng bàn và cập nhật status nếu cần
                List<TableItem> allTables = new ArrayList<>();
                allTables.addAll(floor1Tables);
                allTables.addAll(floor2Tables);
                
                boolean needUpdate = false;
                for (TableItem table : allTables) {
                    List<Order> tableOrders = ordersByTable.get(table.getTableNumber());
                    boolean allServed = determineIfAllServed(tableOrders);
                    
                    // Nếu tất cả đã phục vụ và status chưa phải FINISH_SERVE, cập nhật
                    if (allServed && table.getStatus() != TableItem.Status.FINISH_SERVE) {
                        table.setStatus(TableItem.Status.FINISH_SERVE);
                        needUpdate = true;
                    }
                }

                // Cập nhật lại adapter nếu có thay đổi
                if (needUpdate) {
                    runOnUiThread(() -> {
                        adapterFloor1.updateList(floor1Tables);
                        adapterFloor2.updateList(floor2Tables);
                    });
                }
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Error loading orders for serving status: " + message);
                // Không cần xử lý, adapter sẽ dùng status mặc định từ table
            }
        });
    }

    /**
     * Xác định xem tất cả món đã được phục vụ chưa.
     */
    private boolean determineIfAllServed(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return false; // Không có order thì chưa phục vụ
        }

        for (Order order : orders) {
            if (order == null || order.getItems() == null) continue;
            
            for (Order.OrderItem item : order.getItems()) {
                if (item == null) continue;
                String status = item.getStatus();
                // Nếu có item chưa "done" thì chưa phục vụ xong
                if (status == null || !status.toLowerCase().contains("done")) {
                    return false;
                }
            }
        }
        return true; // Tất cả items đều "done"
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh khi quay lại màn hình
        loadActiveTables();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
