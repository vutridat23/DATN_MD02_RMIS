package com.ph48845.datn_qlnh_rmis.ui.thungan;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;
import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.core.base.BaseMenuActivity;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.ui.revenue.ReportActivity;
// Thay thế bằng Activity xem lịch sử thanh toán thực tế của bạn
// import com.ph48845.datn_qlnh_rmis.ui.history.HistoryActivity;
// Thay thế bằng Activity xem chi tiết hóa đơn thực tế của bạn
// import com.ph48845.datn_qlnh_rmis.ui.invoice.InvoiceActivity;
import com.ph48845.datn_qlnh_rmis.ui.thungan.adapter.ThuNganAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Activity Thu Ngân: Quản lý danh sách bàn đang hoạt động/chờ thanh toán.
 */
public class ThuNganActivity extends BaseMenuActivity {

    private static final String TAG = "ThuNganActivity";

    // Views
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;
    private ProgressBar progressBar;
    private RecyclerView rvFloor1, rvFloor2;
    private LinearLayout headerFloor1, headerFloor2;


    // Data & Adapters
    private ThuNganAdapter adapterFloor1;
    private ThuNganAdapter adapterFloor2;
    private ThuNganViewModel viewModel;
    private OrderRepository orderRepository;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thu_ngan);

        // 1. Khởi tạo ViewModel & Repository
        viewModel = new ThuNganViewModel();
        orderRepository = new OrderRepository();

        // 2. Ánh xạ View & Setup giao diện
        initViews();
        applyNavigationViewInsets();
        setupToolbar();
        setupNavigationDrawer();
        setupRecyclerViews();

        // 3. Load dữ liệu ban đầu
        updateNavHeaderInfo();
        loadActiveTables();
        loadTempCalculationRequestsCount();
    }

    private void applyNavigationViewInsets() {
        if (navigationView == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (view, insets) -> {

            int statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;

            // Lấy header của NavigationView
            View header = navigationView.getHeaderView(0);
            if (header != null) {
                header.setPadding(
                        header.getPaddingLeft(),
                        statusBar,   // ĐẨY XUỐNG ĐỂ TRÁNH DÍNH STATUS BAR
                        header.getPaddingRight(),
                        header.getPaddingBottom()
                );
            }

            return insets;
        });
    }


    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout_thungan);
        navigationView = findViewById(R.id.navigationView_thungan);
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
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
        toolbar.setNavigationIcon(null);
    }

    private void setupNavigationDrawer() {
        // Xử lý nút Menu (Hamburger icon)
        ImageView navIcon = findViewById(R.id.nav_icon);
        if (navIcon != null && drawerLayout != null) {
            navIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        if (navigationView != null) {
            // Format lại font chữ cho menu
            for (int i = 0; i < navigationView.getMenu().size(); i++) {
                MenuItem menuItem = navigationView.getMenu().getItem(i);
                SpannableString spanString = new SpannableString(menuItem.getTitle().toString());
                spanString.setSpan(new RelativeSizeSpan(1.1f), 0, spanString.length(), 0);
                menuItem.setTitle(spanString);
            }

            // Xử lý sự kiện chọn menu
            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_mood) {
                    showMoodDialog();
                } else if (id == R.id.nav_contact) {
                    showContactDialog();
                } else if (id == R.id.nav_temp_calculation_requests) {
                    showTempCalculationRequests(); // Gọi hàm đã được khôi phục
                } else if (id == R.id.nav_logout) {
                    logout();
                } else if (id == R.id.nav_payment_history) {
                    // Cần Activity xem lịch sử thanh toán
                     startActivity(new Intent(ThuNganActivity.this, HistoryActivity.class));
                    Toast.makeText(ThuNganActivity.this, "Chức năng Lịch sử thanh toán", Toast.LENGTH_SHORT).show();
                } else if (id == R.id.nav_revenue) {
                    startActivity(new Intent(ThuNganActivity.this, ReportActivity.class));
                }

                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
                return true;
            });
        }
    }

    private void setupRecyclerViews() {
        // Sử dụng Grid 3 cột cho cả 2 tầng
        rvFloor1.setLayoutManager(new GridLayoutManager(this, 3));
        rvFloor2.setLayoutManager(new GridLayoutManager(this, 3));

        rvFloor1.setNestedScrollingEnabled(false);
        rvFloor2.setNestedScrollingEnabled(false);

        // Sự kiện click vào bàn -> Mở màn hình hóa đơn
        ThuNganAdapter.OnTableClickListener listener = table -> {
            // Cần Activity hóa đơn
             Intent intent = new Intent(ThuNganActivity.this, InvoiceActivity.class);
             intent.putExtra("tableNumber", table.getTableNumber());
             startActivity(intent);
            Toast.makeText(ThuNganActivity.this, "Mở hóa đơn Bàn " + table.getTableNumber(), Toast.LENGTH_SHORT).show();
        };

        // Khởi tạo Adapter
        adapterFloor1 = new ThuNganAdapter(this, new ArrayList<>(), listener);
        adapterFloor2 = new ThuNganAdapter(this, new ArrayList<>(), listener);

        rvFloor1.setAdapter(adapterFloor1);
        rvFloor2.setAdapter(adapterFloor2);
    }

    private void loadActiveTables() {
        progressBar.setVisibility(View.VISIBLE);

        viewModel.loadActiveTables(new ThuNganViewModel.DataCallback() {
            @Override
            public void onTablesLoaded(List<TableItem> floor1Tables, List<TableItem> floor2Tables) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    // Cập nhật dữ liệu lên UI
                    adapterFloor1.updateList(floor1Tables);
                    adapterFloor2.updateList(floor2Tables);

                    // Ẩn hiện tiêu đề tầng
                    if (headerFloor1 != null) headerFloor1.setVisibility(floor1Tables.isEmpty() ? View.GONE : View.VISIBLE);
                    if (headerFloor2 != null) headerFloor2.setVisibility(floor2Tables.isEmpty() ? View.GONE : View.VISIBLE);

                    // Check trạng thái món ăn để đổi màu thẻ (Đỏ -> Cam)
                    loadOrdersForServingStatus(floor1Tables, floor2Tables);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(ThuNganActivity.this, "Lỗi tải dữ liệu: " + message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void loadOrdersForServingStatus(List<TableItem> floor1Tables, List<TableItem> floor2Tables) {
        orderRepository.getOrdersByTableNumber(null, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> allOrders) {
                if (allOrders == null) return;

                Map<Integer, List<Order>> ordersByTable = new HashMap<>();
                for (Order order : allOrders) {
                    if (order == null) continue;
                    // order.normalizeItems(); // Bỏ comment nếu bạn cần chuẩn hóa OrderItem

                    int tNum = order.getTableNumber();
                    if (!ordersByTable.containsKey(tNum)) {
                        ordersByTable.put(tNum, new ArrayList<>());
                    }
                    ordersByTable.get(tNum).add(order);
                }

                List<TableItem> allTables = new ArrayList<>();
                allTables.addAll(floor1Tables);
                allTables.addAll(floor2Tables);

                boolean needUpdate = false;

                for (TableItem table : allTables) {
                    List<Order> tableOrders = ordersByTable.get(table.getTableNumber());

                    boolean allServed = determineIfAllServed(tableOrders);

                    // Nếu đã đủ món -> Đổi trạng thái sang FINISH_SERVE
                    if (allServed && table.getStatus() != TableItem.Status.FINISH_SERVE) {
                        table.setStatus(TableItem.Status.FINISH_SERVE);
                        needUpdate = true;
                    }
                }

                if (needUpdate) {
                    runOnUiThread(() -> {
                        adapterFloor1.notifyDataSetChanged();
                        adapterFloor2.notifyDataSetChanged();
                    });
                }
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Lỗi check trạng thái món: " + message);
            }
        });
    }

    private boolean determineIfAllServed(List<Order> orders) {
        if (orders == null || orders.isEmpty()) return false;

        for (Order order : orders) {
            if (order == null || order.getItems() == null) continue;
            for (Order.OrderItem item : order.getItems()) {
                if (item == null) continue;
                String status = item.getStatus();
                // Nếu có bất kỳ món nào chưa "done" -> Chưa xong
                if (status == null || !status.toLowerCase().contains("done")) {
                    return false;
                }
            }
        }
        return true;
    }

    // =========================================================================
    // KHÔI PHỤC HÀM BỊ MẤT (FIX LỖI)
    // =========================================================================

    /**
     * Tải và hiển thị danh sách yêu cầu tạm tính khi click từ menu.
     */
    private void showTempCalculationRequests() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        orderRepository.getOrdersByTableNumber(null, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> allOrders) {
                runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);

                    // Lọc các orders có tempCalculationRequestedAt
                    List<Order> tempCalculationOrders = new ArrayList<>();
                    if (allOrders != null) {
                        for (Order order : allOrders) {
                            if (order != null && order.getTempCalculationRequestedAt() != null
                                    && !order.getTempCalculationRequestedAt().trim().isEmpty()) {
                                tempCalculationOrders.add(order);
                            }
                        }
                    }

                    if (tempCalculationOrders.isEmpty()) {
                        Toast.makeText(ThuNganActivity.this, "Không có yêu cầu tạm tính nào", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Hiển thị dialog với danh sách yêu cầu
                    showTempCalculationRequestsDialog(tempCalculationOrders);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Toast.makeText(ThuNganActivity.this, "Lỗi tải yêu cầu tạm tính: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Hiển thị dialog danh sách yêu cầu tạm tính.
     */
    private void showTempCalculationRequestsDialog(List<Order> orders) {
        // Tạo danh sách hiển thị
        String[] items = new String[orders.size()];
        for (int i = 0; i < orders.size(); i++) {
            Order order = orders.get(i);
            String tableInfo = "Bàn " + order.getTableNumber();
            String timeInfo = "";
            if (order.getTempCalculationRequestedAt() != null) {
                try {
                    // Parse ISO date và format lại
                    java.text.SimpleDateFormat inputFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
                    java.text.SimpleDateFormat outputFormat = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault());
                    java.util.Date date = inputFormat.parse(order.getTempCalculationRequestedAt());
                    timeInfo = " - " + outputFormat.format(date);
                } catch (Exception e) {
                    timeInfo = " - " + order.getTempCalculationRequestedAt();
                }
            }
            items[i] = tableInfo + timeInfo;
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle("Yêu cầu tạm tính (" + orders.size() + ")")
                .setItems(items, (dialog, which) -> {
                    // Mở màn hình hóa đơn cho bàn được chọn
                    Order selectedOrder = orders.get(which);
                     Intent intent = new Intent(ThuNganActivity.this, InvoiceActivity.class);
                     intent.putExtra("tableNumber", selectedOrder.getTableNumber());
                     startActivity(intent);
                    Toast.makeText(ThuNganActivity.this, "Mở hóa đơn Bàn " + selectedOrder.getTableNumber(), Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    // =========================================================================
    // CÁC HÀM HỖ TRỢ VỀ THÔNG TIN & MENU
    // =========================================================================

    private void updateNavHeaderInfo() {
        if (navigationView != null && navigationView.getHeaderCount() > 0) {
            View headerView = navigationView.getHeaderView(0);
            TextView tvName = headerView.findViewById(R.id.textViewName);
            TextView tvRole = headerView.findViewById(R.id.textViewRole);

            SharedPreferences prefs = getSharedPreferences("RestaurantPrefs", MODE_PRIVATE);
            if (tvName != null) tvName.setText(prefs.getString("fullName", "Người dùng"));
            if (tvRole != null) tvRole.setText(getVietnameseRole(prefs.getString("userRole", "")));
        }
    }

    private String getVietnameseRole(String roleKey) {
        if (roleKey == null) return "";
        switch (roleKey.toLowerCase()) {
            case "cashier": return "Thu ngân";
            case "manager": return "Quản lý";
            case "order": return "Phục vụ";
            case "kitchen": return "Bếp";
            default: return roleKey;
        }
    }

    private void loadTempCalculationRequestsCount() {
        orderRepository.getOrdersByTableNumber(null, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> allOrders) {
                runOnUiThread(() -> {
                    int count = 0;
                    if (allOrders != null) {
                        for (Order order : allOrders) {
                            if (order != null && order.getTempCalculationRequestedAt() != null
                                    && !order.getTempCalculationRequestedAt().trim().isEmpty()) {
                                count++;
                            }
                        }
                    }
                    updateTempCalculationMenuBadge(count);
                });
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Lỗi đếm yêu cầu tạm tính: " + message);
            }
        });
    }

    private void updateTempCalculationMenuBadge(int count) {
        if (navigationView == null) return;
        MenuItem menuItem = navigationView.getMenu().findItem(R.id.nav_temp_calculation_requests);
        if (menuItem != null) {
            String title = "Yêu cầu tạm tính";
            if (count > 0) title += " (" + count + ")";
            SpannableString spanString = new SpannableString(title);
            spanString.setSpan(new RelativeSizeSpan(1.1f), 0, spanString.length(), 0);
            menuItem.setTitle(spanString);

        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadActiveTables();
        loadTempCalculationRequestsCount();
    }
}