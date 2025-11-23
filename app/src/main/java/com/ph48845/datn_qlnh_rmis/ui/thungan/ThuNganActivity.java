package com.ph48845.datn_qlnh_rmis.ui.thungan;

import android.content.Intent;
import android.widget.ImageView;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
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
public class ThuNganActivity extends AppCompatActivity {

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
    private ImageView nav_profile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thu_ngan);

        initViews();
        setupToolbar();
        setupRecyclerViews();
        
        viewModel = new ThuNganViewModel();
        orderRepository = new OrderRepository();

        nav_profile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ThuNganActivity.this, ProfileActivity.class);
                startActivity(intent);
            }
        });
        // Load dữ liệu
        loadActiveTables();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        progressBar = findViewById(R.id.progress_bar_loading);
        rvFloor1 = findViewById(R.id.recycler_floor1);
        rvFloor2 = findViewById(R.id.recycler_floor2);
        headerFloor1 = findViewById(R.id.header_floor1);
        headerFloor2 = findViewById(R.id.header_floor2);
        nav_profile = findViewById(R.id.nav_profile);
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
