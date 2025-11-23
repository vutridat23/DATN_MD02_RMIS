package com.ph48845.datn_qlnh_rmis.ui.bep;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
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

import java.util.ArrayList;
import java.util.List;

/**
 * BepOrderActivity - Read-only activity để xem danh sách món cần làm cho một bàn.
 * Không có chức năng cập nhật trạng thái món (read-only).
 */
public class BepOrderActivity extends AppCompatActivity {

    private static final String TAG = "BepOrderActivity";

    private Toolbar toolbar;
    private ProgressBar progressBar;
    private RecyclerView recyclerOrderItems;
    private TextView tvEmptyMessage;

    private BepOrderItemAdapter adapter;
    private OrderRepository orderRepository;
    
    private int tableNumber;
    private String tableName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bep_order);

        // Get intent extras
        tableNumber = getIntent().getIntExtra("tableNumber", 0);
        tableName = getIntent().getStringExtra("tableName");
        if (tableName == null || tableName.isEmpty()) {
            tableName = "Bàn " + tableNumber;
        }

        initViews();
        setupToolbar();
        setupRecyclerView();

        orderRepository = new OrderRepository();

        // Load order items for this table
        loadOrderItems();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar_bep_order);
        progressBar = findViewById(R.id.progress_bep_order);
        recyclerOrderItems = findViewById(R.id.recycler_bep_order_items);
        tvEmptyMessage = findViewById(R.id.tv_empty_message);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle("Món cần làm - " + tableName);
        }
    }

    private void setupRecyclerView() {
        recyclerOrderItems.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BepOrderItemAdapter(this, new ArrayList<>());
        recyclerOrderItems.setAdapter(adapter);
    }

    /**
     * Load danh sách order items cho bàn này.
     * Chỉ hiển thị các món chưa hoàn thành.
     */
    private void loadOrderItems() {
        if (tableNumber <= 0) {
            Toast.makeText(this, "Số bàn không hợp lệ", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        tvEmptyMessage.setVisibility(View.GONE);

        // Lấy tất cả orders của bàn này
        orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orders) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    if (orders == null || orders.isEmpty()) {
                        tvEmptyMessage.setVisibility(View.VISIBLE);
                        tvEmptyMessage.setText("Không có món nào cần làm cho bàn này");
                        adapter.updateList(new ArrayList<>());
                        return;
                    }

                    // Collect all order items from all orders for this table
                    List<Order.OrderItem> allItems = new ArrayList<>();
                    for (Order order : orders) {
                        if (order == null) continue;
                        order.normalizeItems();
                        List<Order.OrderItem> items = order.getItems();
                        if (items != null) {
                            // Only add items that are not "done" (still need to be prepared)
                            for (Order.OrderItem item : items) {
                                if (item != null) {
                                    String status = item.getStatus();
                                    // Include all items for read-only viewing
                                    // Kitchen can see what needs to be done
                                    allItems.add(item);
                                }
                            }
                        }
                    }

                    if (allItems.isEmpty()) {
                        tvEmptyMessage.setVisibility(View.VISIBLE);
                        tvEmptyMessage.setText("Không có món nào cần làm cho bàn này");
                    } else {
                        tvEmptyMessage.setVisibility(View.GONE);
                    }

                    adapter.updateList(allItems);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvEmptyMessage.setVisibility(View.VISIBLE);
                    tvEmptyMessage.setText("Lỗi tải danh sách món: " + message);
                    Log.e(TAG, "Error loading order items: " + message);
                    Toast.makeText(BepOrderActivity.this, "Lỗi: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
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
