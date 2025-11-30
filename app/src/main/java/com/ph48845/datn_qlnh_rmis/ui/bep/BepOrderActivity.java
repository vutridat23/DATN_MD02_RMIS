package com.ph48845.datn_qlnh_rmis.ui.bep;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
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

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class BepOrderActivity extends AppCompatActivity implements OrderItemAdapter.OnActionListener {

    private static final String TAG = "BepOrderActivityRealtime";
    private static final String SOCKET_URL = "http://192.168.1.84:3000";

    private Toolbar toolbar;
    private TextView tvTableLabel;
    private ProgressBar progressBar;
    private RecyclerView rvItems;

    private OrderRepository orderRepository;
    private OrderItemAdapter adapter;

    private int tableNumber;
    private String tableId;

    private final SocketManager socketManager = SocketManager.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bep_order);

        toolbar = findViewById(R.id.toolbar_bep_order);
        tvTableLabel = findViewById(R.id.tv_bep_table_label);
        progressBar = findViewById(R.id.progress_bep_order);
        rvItems = findViewById(R.id.recycler_table_orders);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        tableNumber = getIntent().getIntExtra("tableNumber", 0);
        tableId = getIntent().getStringExtra("tableId");
        tvTableLabel.setText("Bàn " + tableNumber);

        rvItems.setLayoutManager(new LinearLayoutManager(this));
        // PASS context and listener
        adapter = new OrderItemAdapter(this, this);
        adapter.setItems(new ArrayList<>());
        rvItems.setAdapter(adapter);

        orderRepository = new OrderRepository();

        loadTableOrders();
    }

    @Override
    protected void onStart() {
        super.onStart();
        startRealtimeForTable();
        if (adapter != null) adapter.startTimer();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopRealtimeForTable();
        if (adapter != null) adapter.stopTimer();
    }

    private void loadTableOrders() {
        progressBar.setVisibility(android.view.View.VISIBLE);
        orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orders) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(android.view.View.GONE);
                    if (orders == null || orders.isEmpty()) {
                        adapter.setItems(new ArrayList<>());
                        Toast.makeText(BepOrderActivity.this, "Không có đơn hàng cho bàn này", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<ItemWithOrder> toShow = new ArrayList<>();
                    for (Order o : orders) {
                        if (o == null) continue;
                        try { o.normalizeItems(); } catch (Exception ignored) {}
                        if (o.getItems() == null) continue;
                        for (Order.OrderItem it : o.getItems()) {
                            if (it == null) continue;
                            String s = it.getStatus() == null ? "" : it.getStatus().trim().toLowerCase();
                            if ("pending".equals(s) || "preparing".equals(s) || "processing".equals(s)) {
                                toShow.add(new ItemWithOrder(o, it));
                            }
                        }
                    }
                    adapter.setItems(toShow);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(android.view.View.GONE);
                    Toast.makeText(BepOrderActivity.this, "Lỗi tải đơn hàng: " + message, Toast.LENGTH_LONG).show();
                    Log.w(TAG, "Error loading orders: " + message);
                });
            }
        });
    }

    private void startRealtimeForTable() {
        try {
            socketManager.init(SOCKET_URL);
            socketManager.setOnEventListener(new SocketManager.OnEventListener() {
                @Override
                public void onOrderCreated(JSONObject payload) {
                    runOnUiThread(() -> loadTableOrders());
                }

                @Override
                public void onOrderUpdated(JSONObject payload) {
                    runOnUiThread(() -> loadTableOrders());
                }

                @Override
                public void onConnect() {
                    try {
                        socketManager.emitJoinRoom("bep");
                        socketManager.emitJoinRoom("phucvu");
                    } catch (Exception ignored) {}
                }

                @Override
                public void onDisconnect() { }

                @Override
                public void onError(Exception e) {
                    Log.w(TAG, "Socket error (BepOrderActivity): " + (e != null ? e.getMessage() : ""));
                }
            });
            socketManager.connect();
        } catch (Exception e) {
            Log.w(TAG, "startRealtimeForTable failed: " + e.getMessage(), e);
        }
    }

    private void stopRealtimeForTable() {
        try {
            socketManager.disconnect();
        } catch (Exception ignored) {}
    }

    @Override
    public void onChangeStatus(ItemWithOrder wrapper, String newStatus) {
        if (wrapper == null || newStatus == null) return;
        Order order = wrapper.getOrder();
        Order.OrderItem item = wrapper.getItem();
        if (order == null || item == null) return;

        final String displayName = (item.getMenuItemName() != null && !item.getMenuItemName().isEmpty())
                ? item.getMenuItemName() : item.getName();

        String message;
        if ("ready".equals(newStatus)) {
            message = "Xác nhận xong món?";
        } else if ("soldout".equals(newStatus)) {
            message = "Xác nhận hết món?";
        } else if ("preparing".equals(newStatus)) {
            message = "Xác nhận đang làm món?";
        } else {
            // fallback
            message = "Xác nhận cập nhật trạng thái cho món \"" + displayName + "\"?";
        }

        new AlertDialog.Builder(this)
                .setTitle("Xác nhận")
                .setMessage(message)
                .setNegativeButton("Hủy", (dialog, which) -> { })
                .setPositiveButton("Xác nhận", (dialog, which) -> {
                    String orderId = order.getId();
                    String itemId = item.getMenuItemId();
                    if (orderId == null || orderId.trim().isEmpty() || itemId == null || itemId.trim().isEmpty()) {
                        Toast.makeText(BepOrderActivity.this, "Không thể xác định order/item id", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    progressBar.setVisibility(android.view.View.VISIBLE);

                    orderRepository.updateOrderItemStatus(orderId, itemId, newStatus).enqueue(new retrofit2.Callback<Void>() {
                        @Override
                        public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                            runOnUiThread(() -> progressBar.setVisibility(android.view.View.GONE));
                            if (response.isSuccessful()) {
                                item.setStatus(newStatus);
                                runOnUiThread(() -> {
                                    Toast.makeText(BepOrderActivity.this, "Cập nhật trạng thái thành công", Toast.LENGTH_SHORT).show();
                                    loadTableOrders();
                                });
                            } else {
                                runOnUiThread(() -> Toast.makeText(BepOrderActivity.this, "Cập nhật thất bại: HTTP " + response.code(), Toast.LENGTH_LONG).show());
                            }
                        }
                        @Override
                        public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                            runOnUiThread(() -> {
                                progressBar.setVisibility(android.view.View.GONE);
                                Toast.makeText(BepOrderActivity.this, "Lỗi mạng: " + (t.getMessage() != null ? t.getMessage() : ""), Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                })
                .show();
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