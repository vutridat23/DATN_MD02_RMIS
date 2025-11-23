package com.ph48845.datn_qlnh_rmis.ui.bep;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.ui.bep.adapter.OrderItemAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * BepOrderActivity - Hiển thị danh sách món cần làm cho một bàn được chọn.
 * Nhận intent extras: tableNumber (số bàn)
 * Hiển thị các order items có trạng thái: pending, preparing, processing
 */
public class BepOrderActivity extends AppCompatActivity implements OrderItemAdapter.OnActionListener {

    private static final String TAG = "BepOrderActivity";
    
    private Toolbar toolbar;
    private TextView tvTableInfo;
    private RecyclerView rvOrderItems;
    private ProgressBar progressBar;
    
    private OrderItemAdapter adapter;
    private OrderRepository orderRepository;
    private int tableNumber;
    private List<ItemWithOrder> allItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bep_order);

        // Get table number from intent
        tableNumber = getIntent().getIntExtra("tableNumber", -1);
        if (tableNumber == -1) {
            Toast.makeText(this, "Không tìm thấy thông tin bàn", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupToolbar();
        setupRecyclerView();
        
        orderRepository = new OrderRepository();
        
        // Load order items for this table
        loadOrderItems();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvTableInfo = findViewById(R.id.tv_table_info);
        rvOrderItems = findViewById(R.id.rv_order_items);
        progressBar = findViewById(R.id.progress_bar);
        
        tvTableInfo.setText("Bàn " + tableNumber);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Món cần làm");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        rvOrderItems.setLayoutManager(new LinearLayoutManager(this));
        adapter = new OrderItemAdapter(this);
        rvOrderItems.setAdapter(adapter);
    }

    /**
     * Load all orders for this table and filter items that need to be prepared
     */
    private void loadOrderItems() {
        progressBar.setVisibility(View.VISIBLE);
        
        orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orders) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    
                    if (orders == null || orders.isEmpty()) {
                        Toast.makeText(BepOrderActivity.this, "Không có món nào cần làm", Toast.LENGTH_SHORT).show();
                        adapter.setItems(new ArrayList<>());
                        return;
                    }
                    
                    // Flatten orders to items that need to be prepared
                    List<ItemWithOrder> itemsToShow = new ArrayList<>();
                    for (Order order : orders) {
                        if (order == null) continue;
                        
                        try {
                            order.normalizeItems();
                        } catch (Exception e) {
                            Log.w(TAG, "Error normalizing items: " + e.getMessage());
                        }
                        
                        List<Order.OrderItem> items = order.getItems();
                        if (items == null) continue;
                        
                        for (Order.OrderItem item : items) {
                            if (item == null) continue;
                            
                            String status = item.getStatus();
                            if (status == null) continue;
                            
                            String trimmedStatus = status.trim();
                            // Only show items that are pending, preparing, or processing
                            if ("pending".equalsIgnoreCase(trimmedStatus) || 
                                "preparing".equalsIgnoreCase(trimmedStatus) || 
                                "processing".equalsIgnoreCase(trimmedStatus)) {
                                itemsToShow.add(new ItemWithOrder(order, item));
                            }
                        }
                    }
                    
                    allItems = itemsToShow;
                    adapter.setItems(itemsToShow);
                    
                    if (itemsToShow.isEmpty()) {
                        Toast.makeText(BepOrderActivity.this, "Không có món nào cần làm", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(BepOrderActivity.this, "Lỗi tải danh sách món: " + message, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error loading order items: " + message);
                });
            }
        });
    }

    /**
     * OrderItemAdapter.OnActionListener callback
     * Called when kitchen staff changes the status of an item
     */
    @Override
    public void onChangeStatus(ItemWithOrder wrapper, String newStatus) {
        if (wrapper == null || newStatus == null) return;
        
        Order order = wrapper.getOrder();
        Order.OrderItem item = wrapper.getItem();
        if (order == null || item == null) return;
        
        String orderId = order.getId();
        String itemId = item.getMenuItemId();
        
        if (orderId == null || orderId.trim().isEmpty() || itemId == null || itemId.trim().isEmpty()) {
            Toast.makeText(this, "Không thể cập nhật trạng thái", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Update status on server
        orderRepository.updateOrderItemStatus(orderId, itemId, newStatus).enqueue(new retrofit2.Callback<Void>() {
            @Override
            public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        // Update local model
                        item.setStatus(newStatus);
                        
                        // If status is "done", remove from list
                        if ("done".equalsIgnoreCase(newStatus)) {
                            allItems.remove(wrapper);
                            adapter.setItems(allItems);
                            Toast.makeText(BepOrderActivity.this, "Đã hoàn thành món", Toast.LENGTH_SHORT).show();
                        } else {
                            // Just refresh the list
                            adapter.setItems(allItems);
                            Toast.makeText(BepOrderActivity.this, "Đã cập nhật trạng thái", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(BepOrderActivity.this, "Cập nhật thất bại: HTTP " + response.code(), Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                runOnUiThread(() -> {
                    Toast.makeText(BepOrderActivity.this, "Lỗi cập nhật: " + (t.getMessage() != null ? t.getMessage() : "Lỗi mạng"), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh when returning to this screen
        loadOrderItems();
    }
}
