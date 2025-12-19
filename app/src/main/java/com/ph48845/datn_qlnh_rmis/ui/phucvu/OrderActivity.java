package com.ph48845.datn_qlnh_rmis.ui.phucvu;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.ui.MainActivity;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter.MenuAdapter;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter.OrderAdapter;
import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.Order.OrderItem;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.MenuRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.socket.OrderSocketHandler;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * OrderActivity with:
 * - OnBackPressedDispatcher handling (menu <-> order details navigation),
 * - Confirm add items -> return to MainActivity,
 * - Click on items with "done/xong/served/ready" status -> show confirmation dialog and update server.
 */
public class OrderActivity extends AppCompatActivity implements MenuAdapter.OnMenuClickListener,
        OrderSocketHandler.Listener, MenuLongPressHandler.NoteStore {

    private static final String TAG = "OrderActivity";

    private RecyclerView rvOrderedList;
    private RecyclerView rvMenuList;
    private ProgressBar progressBar;
    private TextView tvTable;
    private TextView tvTotal;
    private Button btnAddMore;
    private Button btnConfirm;

    private MenuRepository menuRepository;
    private OrderRepository orderRepository;
    private TableRepository tableRepository;

    private OrderAdapter orderedAdapter;
    private MenuAdapter menuAdapter;

    // quantities to add (menuId -> qty)
    private final Map<String, Integer> addQtyMap = new HashMap<>();
    // persistent notes per menu item (menuId -> note)
    private final Map<String, String> notesMap = new HashMap<>();
    // persistent cancel-reasons per menu/item (menuId or itemId -> cancel reason)
    private final Map<String, String> cancelNotesMap = new HashMap<>();

    private String tableId;
    private int tableNumber;

    private final String fakeServerId = "64a7f3b2c9d1e2f3a4b5c6d7";
    private final String fakeCashierId = "64b8e4c3d1f2a3b4c5d6e7f8";

    // Handlers
    private OrderSocketHandler socketHandler;
    private MenuLongPressHandler longPressHandler;

    // default socket url (can be overridden via intent)
    private String socketUrl = "http://192.168.1.84:3000";

    // Persistence keys
    private static final String PREFS_NAME = "RestaurantPrefs";
    private static final String NOTES_KEY = "menu_notes_json";
    private static final String CANCEL_NOTES_KEY = "cancel_notes_json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "🚀 OrderActivity onCreate started");

        try {
            setContentView(R.layout.activity_order);
            Log.d(TAG, "✅ setContentView completed");
        } catch (Exception e) {
            Log.e(TAG, "❌ setContentView failed", e);
            throw e;
        }

        menuRepository = new MenuRepository();
        orderRepository = new OrderRepository();
        tableRepository = new TableRepository();
        Log.d(TAG, "✅ Repositories initialized");

        // Ánh xạ các View
        rvOrderedList = findViewById(R.id.rv_ordered_list);
        rvMenuList = findViewById(R. id.rv_menu_list);
        progressBar = findViewById(R.id.progress_bar_order);
        tvTable = findViewById(R.id.tv_table_label);
        tvTotal = findViewById(R.id.tv_total_amount_ordered);
//        tvEmptyState = findViewById(R.id.tv_empty_state); // ✅ THÊM
        btnAddMore = findViewById(R.id.btn_add_more);
        btnConfirm = findViewById(R.id.btn_confirm_order);

        // Toolbar navigation -> dùng OnBackPressedDispatcher để thống nhất gesture / hardware / toolbar back
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        // ✅ FIX: OnBackPressedCallback
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                try {
                    View orderedContainer = findViewById(R.id.ordered_container);
                    View menuContainer = findViewById(R.id.menu_container);

                    // Case 1: Đang ở màn MENU → Quay về ORDER
                    if (menuContainer != null && menuContainer.getVisibility() == View.VISIBLE) {
                        hideMenuView();
                        return;
                    }

                    // Case 2: Đang ở màn ORDER → Về MainActivity
                    if (orderedContainer != null && orderedContainer.getVisibility() == View.VISIBLE) {
                        navigateBackToMain();
                        return;
                    }

                    navigateBackToMain();
                } catch (Exception e) {
                    Log.w(TAG, "handleOnBackPressed error", e);
                    navigateBackToMain();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, callback);

        loadNotesFromPrefs();

        // Setup RecyclerView - Danh sách món ĐÃ GỌI
        rvOrderedList.setLayoutManager(new LinearLayoutManager(this));
        orderedAdapter = new OrderAdapter(new ArrayList<>(), item -> {
            if (isItemDone(item. getStatus())) {
                showConfirmServedDialog(item);
            } else {
                Toast.makeText(OrderActivity.this, item.getName(), Toast.LENGTH_SHORT).show();
            }
        }, this);
        rvOrderedList.setAdapter(orderedAdapter);

        // Setup RecyclerView - Danh sách MENU
        rvMenuList. setLayoutManager(new LinearLayoutManager(this));
        menuAdapter = new MenuAdapter(new ArrayList<>(), this);
        rvMenuList.setAdapter(menuAdapter);

        longPressHandler = new MenuLongPressHandler(this, rvMenuList, menuAdapter, this);
        longPressHandler.setup();

        if (btnAddMore != null) {
            btnAddMore.setOnClickListener(v -> showMenuView());
        }
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(this::confirmAddItems);
        }

        socketHandler = new OrderSocketHandler(this, socketUrl, tableNumber, this);
        socketHandler.initAndConnect();

        tableId = getIntent().getStringExtra("tableId");
        tableNumber = getIntent().getIntExtra("tableNumber", 0);
        if (tvTable != null) tvTable.setText("Bàn " + tableNumber);

        String extraSocket = getIntent().getStringExtra("socketUrl");
        if (extraSocket != null && !extraSocket.trim().isEmpty()) {
            socketUrl = extraSocket. trim();
        }

        loadMenuItems();
        loadExistingOrdersForTable();

        // Register broadcast receiver for check items request
        try {
            registerCheckItemsReceiver();
            Log.d(TAG, "✅ Broadcast receiver registration completed");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to register broadcast receiver", e);
        }

        // Khởi tạo polling handler (sau khi orderRepository đã được khởi tạo)
        // Delay một chút để đảm bảo activity đã hoàn toàn sẵn sàng
        try {
            pollingHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            Log.d(TAG, "✅ Polling handler created, will start in 1 second");
            // Delay 1 giây để đảm bảo activity đã render xong
            pollingHandler.postDelayed(() -> {
                try {
                    Log.d(TAG, "⏰ Starting polling now...");
                    startPollingForCheckItemsRequest();
                } catch (Exception e) {
                    Log.e(TAG, "❌ Failed to start polling", e);
                }
            }, 1000);
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to initialize polling handler", e);
        }

        Log.d(TAG, "✅ OrderActivity onCreate completed");
    }

    /**
     * Bắt đầu polling để kiểm tra check items request định kỳ
     */
    private void startPollingForCheckItemsRequest() {
        if (pollingHandler == null) {
            Log.e(TAG, "❌ Cannot start polling: pollingHandler is null");
            return;
        }

        // Dừng polling cũ nếu có
        stopPollingForCheckItemsRequest();

        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    checkForCheckItemsRequest();
                    // Lên lịch cho lần tiếp theo
                    if (pollingHandler != null && pollingRunnable != null) {
                        pollingHandler.postDelayed(this, POLLING_INTERVAL_MS);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ Error in polling runnable", e);
                }
            }
        };

        try {
            pollingHandler.postDelayed(pollingRunnable, POLLING_INTERVAL_MS);
            Log.d(TAG, "✅ Started polling for check items request (interval: " + POLLING_INTERVAL_MS + "ms)");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to start polling", e);
        }
    }

    /**
     * Kiểm tra xem có check items request mới không bằng cách query database
     */
    private void checkForCheckItemsRequest() {
        if (tableNumber <= 0) {
            Log.d(TAG, "⏭️ Skipping checkForCheckItemsRequest: invalid tableNumber");
            return;
        }

        if (orderRepository == null) {
            Log.e(TAG, "❌ Cannot check for check items request: orderRepository is null");
            return;
        }

        try {
            orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orders) {
                if (orders == null || orders.isEmpty()) return;

                // Tìm order có checkItemsRequestedAt mới nhất
                String latestRequestedAt = null;
                String latestOrderId = null;

                for (Order order : orders) {
                    if (order == null) continue;
                    String requestedAt = order.getCheckItemsRequestedAt();
                    if (requestedAt != null && !requestedAt.trim().isEmpty()) {
                        // So sánh với thời gian đã xử lý trước đó
                        if (lastCheckItemsRequestedAt == null ||
                            requestedAt.compareTo(lastCheckItemsRequestedAt) > 0) {
                            latestRequestedAt = requestedAt;
                            latestOrderId = order.getId();
                        }
                    }
                }

                // Nếu có request mới, xử lý nó
                if (latestRequestedAt != null && !latestRequestedAt.equals(lastCheckItemsRequestedAt)) {
                    Log.d(TAG, "🔔 Polling detected new check items request for table " + tableNumber +
                          " at " + latestRequestedAt);
                    lastCheckItemsRequestedAt = latestRequestedAt;

                    // Tạo biến final để sử dụng trong lambda
                    final String finalOrderId = latestOrderId;
                    final int finalTableNumber = tableNumber;

                    runOnUiThread(() -> {
                        String[] orderIds = finalOrderId != null ? new String[]{finalOrderId} : null;
                        handleCheckItemsRequest(finalTableNumber, orderIds);
                    });
                }
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Polling checkForCheckItemsRequest error: " + message);
            }
            });
        } catch (Exception e) {
            Log.e(TAG, "❌ Exception in checkForCheckItemsRequest", e);
        }
    }

    /**
     * Dừng polling
     */
    private void stopPollingForCheckItemsRequest() {
        if (pollingHandler != null && pollingRunnable != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
            Log.d(TAG, "⏹️ Stopped polling for check items request");
        }
    }

    private void registerCheckItemsReceiver() {
        checkItemsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) {
                    Log.w(TAG, "checkItemsReceiver: intent is null");
                    return;
                }
                String action = intent.getAction();
                Log.d(TAG, "checkItemsReceiver: received action = " + action);

                if ("com.ph48845.datn_qlnh_rmis.ACTION_CHECK_ITEMS".equals(action)) {
                    int receivedTableNumber = intent.getIntExtra("tableNumber", -1);
                    String[] orderIds = intent.getStringArrayExtra("orderIds");

                    Log.d(TAG, "checkItemsReceiver: tableNumber = " + receivedTableNumber + ", current table = " + tableNumber);
                    Log.d(TAG, "checkItemsReceiver: orderIds = " + (orderIds != null ? java.util.Arrays.toString(orderIds) : "null"));

                    // Chỉ xử lý nếu là bàn hiện tại
                    if (receivedTableNumber == tableNumber) {
                        Log.d(TAG, "✅ Received check items request broadcast for table " + tableNumber);
                        handleCheckItemsRequest(receivedTableNumber, orderIds);
                    } else {
                        Log.d(TAG, "⏭️ Ignoring check items request for table " + receivedTableNumber + " (current: " + tableNumber + ")");
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter("com.ph48845.datn_qlnh_rmis.ACTION_CHECK_ITEMS");
        try {
            registerReceiver(checkItemsReceiver, filter);
            Log.d(TAG, "✅ Registered checkItemsReceiver");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to register checkItemsReceiver", e);
        }
    }

    private void handleCheckItemsRequest(int tableNum, String[] orderIds) {
        Log.d(TAG, "🔄 handleCheckItemsRequest: table=" + tableNum + ", orderIds=" + (orderIds != null ? java.util.Arrays.toString(orderIds) : "null"));

        // Đảm bảo chạy trên UI thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showCheckItemsRequestNotification(tableNum, orderIds);
        } else {
            runOnUiThread(() -> showCheckItemsRequestNotification(tableNum, orderIds));
        }
    }

    private void showCheckItemsRequestNotification(int tableNum, String[] orderIds) {
        String message = "🔔 Có yêu cầu kiểm tra bàn " + tableNum;
        if (orderIds != null && orderIds.length > 0) {
            message += " cho " + orderIds.length + " hóa đơn";
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();

        // Reload orders để cập nhật trạng thái
        Log.d(TAG, "🔄 Reloading orders for table " + tableNum);
        loadExistingOrdersForTable();
    }

    private boolean isItemDone(String status) {
        if (status == null) return false;
        String s = status.toLowerCase().trim();
        return s.contains("done") || s.contains("xong") || s.contains("served") || s.contains("ready") || s.contains("completed");
    }

    private void showConfirmServedDialog(OrderItem item) {
        if (item == null) return;
        String displayName = item.getName() != null && !item.getName().isEmpty() ? item.getName() : item.getMenuItemName();
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận món đã phục vụ")
                .setMessage("Bạn có chắc món \"" + displayName + "\" đã được phục vụ không?")
                .setPositiveButton("Xác nhận", (dialog, which) -> performMarkServed(item))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void performMarkServed(OrderItem item) {
        if (item == null) return;
        String orderId = item.getParentOrderId();
        String itemId = item.getId();
        if (itemId == null || itemId.isEmpty()) itemId = item.getMenuItemId();

        if (orderId == null || orderId.isEmpty() || itemId == null || itemId.isEmpty()) {
            Toast. makeText(this, "Không xác định được order/item id", Toast.LENGTH_SHORT).show();
            return;
        }

        runOnUiThread(() -> {
            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        });

        orderRepository.updateOrderItemStatus(orderId, itemId, "served", new OrderRepository. RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    item.setStatus("served");
                    if (orderedAdapter != null) orderedAdapter.notifyDataSetChanged();
                    Toast. makeText(OrderActivity.this, "Đã xác nhận phục vụ", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View. GONE);
                    Toast.makeText(OrderActivity.this, "Cập nhật thất bại: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (socketHandler == null) return;
            socketHandler.connect();
        } catch (Exception e) {
            Log.w(TAG, "socket connect error", e);
        }

        // Đảm bảo broadcast receiver được đăng ký
        if (checkItemsReceiver == null) {
            registerCheckItemsReceiver();
        }

        // Kiểm tra ngay khi resume (có thể có request mới khi activity ở background)
        checkForCheckItemsRequest();

        // Đảm bảo polling đang chạy
        if (pollingHandler == null) {
            pollingHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        }
        stopPollingForCheckItemsRequest();
        startPollingForCheckItemsRequest();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (socketHandler != null) socketHandler.disconnect();
        } catch (Exception e) {
            Log.w(TAG, "socket disconnect error", e);
        }

        // Dừng polling khi pause để tiết kiệm tài nguyên
        stopPollingForCheckItemsRequest();

        // Không unregister receiver ở đây vì có thể cần nhận khi activity ở background
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Dừng polling
        stopPollingForCheckItemsRequest();

        // Unregister broadcast receiver
        try {
            if (checkItemsReceiver != null) {
                unregisterReceiver(checkItemsReceiver);
                checkItemsReceiver = null;
                Log.d(TAG, "✅ Unregistered checkItemsReceiver");
            }
        } catch (Exception e) {
            Log.w(TAG, "Error unregistering broadcast receiver", e);
        }
    }

    private void loadMenuItems() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        menuRepository.getAllMenuItems(new MenuRepository.RepositoryCallback<List<MenuItem>>() {
            @Override
            public void onSuccess(List<MenuItem> data) {
                runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (menuAdapter != null) menuAdapter.setItems(data != null ? data : new ArrayList<>());
                    for (Map.Entry<String, Integer> e : addQtyMap.entrySet()) {
                        if (menuAdapter != null) menuAdapter.setQty(e.getKey(), e.getValue());
                    }
                });
            }
            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Toast.makeText(OrderActivity.this, "Lỗi tải menu: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ✅ FIX:  Luôn hiển thị màn hình order (dù có món hay không)
    private void loadExistingOrdersForTable() {
        if (tableNumber <= 0) {
            showEmptyOrderState(); // ✅ THAY ĐỔI: hiển thị empty state thay vì chuyển menu
            return;
        }

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orders) {
                runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View. GONE);

                    List<Order> filtered = new ArrayList<>();
                    if (orders != null) {
                        for (Order o : orders) {
                            if (o == null) continue;
                            try { if (o.getTableNumber() == tableNumber) filtered.add(o); } catch (Exception ignored) {}
                        }
                    }

                    for (Order o : filtered) {
                        if (o == null) continue;
                        try { o.normalizeItems(); } catch (Exception ignored) {}
                    }

                    if (filtered.isEmpty()) {
                        showEmptyOrderState();
                        return;
                    }

                    List<OrderItem> flattened = new ArrayList<>();
                    for (Order o : filtered) {
                        if (o == null || o.getItems() == null) continue;
                        String orderId = o.getId();
                        for (OrderItem oi : o.getItems()) {
                            if (oi == null) continue;
                            try {
                                oi.normalize();
                                ensureCancelReasonFromRaw(oi);
                            } catch (Exception ignored) {}
                            oi.setParentOrderId(orderId);
                            flattened.add(oi);
                        }
                    }

                    // Apply locally persisted cancelReasons if server didn't provide them
                    try {
                        for (OrderItem oi : flattened) {
                            if (oi == null) continue;
                            String menuId = oi.getMenuItemId();
                            String itemId = oi.getId();
                            String saved = null;
                            if (menuId != null && ! menuId.isEmpty()) saved = cancelNotesMap.get(menuId);
                            if ((saved == null || saved.isEmpty()) && itemId != null && !itemId.isEmpty()) saved = cancelNotesMap.get(itemId);
                            try {
                                String existing = oi.getCancelReason();
                                if ((existing == null || existing.trim().isEmpty()) && saved != null && !saved.isEmpty()) {
                                    oi.setCancelReason(saved);
                                }
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception ignored) {}

                    // ✅ Hiển thị danh sách có món
                    showOrderListWithItems(flattened);
                });
            }
            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Toast.makeText(OrderActivity.this, "Lỗi tải đơn hàng: " + message, Toast.LENGTH_LONG).show();
                    showEmptyOrderState(); // ✅ Hiển thị empty state thay vì menu
                });
            }
        });
    }

    // ✅ THÊM: Hiển thị empty state (bàn chưa có món)
    private void showEmptyOrderState() {
        hideMenuView(); // Đảm bảo ở màn order

        if (orderedAdapter != null) orderedAdapter.setItems(new ArrayList<>());
        if (tvTotal != null) tvTotal.setText("0 VND");

        // Hiển thị TextView empty state
        if (tvEmptyState != null) {
            tvEmptyState.setVisibility(View.VISIBLE);
            tvEmptyState.setText("Chưa có món nào được gọi.\nNhấn \"Thêm món\" để bắt đầu.");
        }

        if (rvOrderedList != null) rvOrderedList.setVisibility(View.GONE);
    }

    // ✅ THÊM: Hiển thị danh sách có món
    private void showOrderListWithItems(List<OrderItem> items) {
        hideMenuView(); // Đảm bảo ở màn order

        if (orderedAdapter != null) orderedAdapter.setItems(items);

        double total = 0.0;
        for (OrderItem oi : items) {
            try { total += oi.getPrice() * oi.getQuantity(); } catch (Exception ignored) {}
        }
        if (tvTotal != null) tvTotal.setText(String.format("%,.0f VND", total));

        // Ẩn empty state, hiển thị RecyclerView
        if (tvEmptyState != null) tvEmptyState.setVisibility(View.GONE);
        if (rvOrderedList != null) rvOrderedList.setVisibility(View.VISIBLE);
    }

    @SuppressWarnings("unchecked")
    private void ensureCancelReasonFromRaw(OrderItem oi) {
        try {
            if (oi == null) return;
            String cr = null;
            try { cr = oi.getCancelReason(); } catch (Exception ignored) {}
            if (cr != null && !cr.trim().isEmpty()) return;

            Object raw = null;
            try { raw = oi.getMenuItemRaw(); } catch (Exception ignored) {}
            if (raw instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) raw;
                Object v = m.get("cancelReason");
                if (v == null) v = m.get("cancel_reason");
                if (v == null) v = m.get("reason");
                if (v != null) {
                    String s = String.valueOf(v);
                    if (s != null && !s.trim().isEmpty()) {
                        oi.setCancelReason(s. trim());
                        return;
                    }
                }
            }

            try {
                String repr = oi.toString();
                if (repr != null && repr.contains("cancelReason")) {
                    int idx = repr.indexOf("cancelReason='");
                    if (idx > 0) {
                        int start = idx + "cancelReason='".length();
                        int end = repr.indexOf('\'', start);
                        if (end > start) {
                            String s = repr.substring(start, end);
                            if (s != null && !s. trim().isEmpty()) oi.setCancelReason(s.trim());
                        }
                    }
                }
            } catch (Exception ignored) {}

        } catch (Exception ignored) {}
    }

    public void onAddMenuItem(MenuItem menu) {
        if (menu == null) return;
        int cur = addQtyMap.getOrDefault(menu.getId(), 0) + 1;
        addQtyMap.put(menu.getId(), cur);
        if (menuAdapter != null) menuAdapter.setQty(menu.getId(), cur);
        if (btnConfirm != null) btnConfirm.setEnabled(! addQtyMap.isEmpty());
    }

    public void onRemoveMenuItem(MenuItem menu) {
        if (menu == null) return;
        int cur = addQtyMap.getOrDefault(menu. getId(), 0);
        if (cur > 0) {
            cur--;
            if (cur == 0) addQtyMap.remove(menu.getId());
            else addQtyMap.put(menu.getId(), cur);
            if (menuAdapter != null) menuAdapter.setQty(menu.getId(), cur);
        }
        if (btnConfirm != null) btnConfirm.setEnabled(!addQtyMap.isEmpty());
    }

    public void onAddMenuItem(MenuItem menu, int qty) {
        if (menu == null) return;
        int cur = addQtyMap.getOrDefault(menu.getId(), 0) + qty;
        if (cur <= 0) addQtyMap.remove(menu. getId());
        else addQtyMap.put(menu.getId(), cur);
        if (menuAdapter != null) menuAdapter.setQty(menu.getId(), cur);
        if (btnConfirm != null) btnConfirm.setEnabled(!addQtyMap. isEmpty());
    }

    public void onRemoveMenuItem(MenuItem menu, int qty) {
        if (menu == null) return;
        int cur = addQtyMap.getOrDefault(menu.getId(), 0) - qty;
        if (cur <= 0) addQtyMap.remove(menu.getId());
        else addQtyMap.put(menu.getId(), cur);
        if (menuAdapter != null) menuAdapter.setQty(menu. getId(), Math.max(0, cur));
        if (btnConfirm != null) btnConfirm.setEnabled(!addQtyMap.isEmpty());
    }

    private void showMenuView() {
        View vOrdered = findViewById(R.id. ordered_container);
        View vSummary = findViewById(R.id. order_summary);
        View vMenu = findViewById(R.id.menu_container);

        if (vOrdered != null) vOrdered.setVisibility(View.GONE);
        if (vSummary != null) vSummary.setVisibility(View. VISIBLE);
        if (vMenu != null) vMenu.setVisibility(View.VISIBLE);

        if (btnAddMore != null) btnAddMore.setVisibility(View. GONE);
        if (btnConfirm != null) btnConfirm.setVisibility(View. VISIBLE);
    }

    private void hideMenuView() {
        View vMenu = findViewById(R.id.menu_container);
        View vOrdered = findViewById(R. id.ordered_container);
        View vSummary = findViewById(R.id.order_summary);

        if (vMenu != null) vMenu.setVisibility(View.GONE);
        if (vOrdered != null) vOrdered.setVisibility(View.VISIBLE);
        if (vSummary != null) vSummary.setVisibility(View.VISIBLE);

        if (btnAddMore != null) btnAddMore.setVisibility(View.VISIBLE);
        if (btnConfirm != null) btnConfirm.setVisibility(View.GONE);
    }

    public void confirmAddItems(View view) { confirmAddItems(); }

    private void confirmAddItems() {
        OrderHelper. showConfirmationDialog(this, addQtyMap, notesMap, menuAdapter, (confirmed) -> {
            if (! confirmed) return;
            runOnUiThread(() -> {
                if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
                if (btnConfirm != null) btnConfirm.setEnabled(false);
            });

            orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository.RepositoryCallback<List<Order>>() {
                @Override
                public void onSuccess(List<Order> orders) {
                    Order targetOrder = OrderHelper.pickTargetOrderForMerge(orders, tableNumber);
                    if (targetOrder != null) {
                        OrderHelper.mergeIntoExistingOrderAndUpdate(targetOrder, addQtyMap, notesMap, menuAdapter, orderRepository, new OrderHelper.OrderCallback() {
                            @Override
                            public void onSuccess() {
                                runOnUiThread(() -> {
                                    if (progressBar != null) progressBar.setVisibility(View. GONE);
                                    addQtyMap.clear();
                                    notesMap.clear();
                                    saveNotesToPrefs();
                                    Toast. makeText(OrderActivity.this, "Thêm món vào order hiện có thành công", Toast.LENGTH_SHORT).show();
                                    navigateBackToMain();
                                });
                            }
                            @Override
                            public void onError(String message) {
                                runOnUiThread(() -> {
                                    if (progressBar != null) progressBar.setVisibility(View. GONE);
                                    if (btnConfirm != null) btnConfirm.setEnabled(true);
                                    Toast.makeText(OrderActivity.this, "Không thể cập nhật order: " + message, Toast.LENGTH_LONG).show();
                                });
                            }
                        });
                    } else {
                        OrderHelper.createNewOrderFromAddMap(
                                addQtyMap,
                                notesMap,
                                menuAdapter,
                                tableNumber,
                                fakeServerId,
                                fakeCashierId,
                                orderRepository,
                                new OrderHelper.OrderCallback() {
                                    @Override
                                    public void onSuccess() {
                                        runOnUiThread(() -> {
                                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                                            Toast.makeText(OrderActivity.this, "Thêm món thành công", Toast.LENGTH_SHORT).show();
                                            addQtyMap.clear();
                                            notesMap.clear();
                                            saveNotesToPrefs();
                                            navigateBackToMain();
                                        });
                                    }

                                    @Override
                                    public void onError(String message) {
                                        runOnUiThread(() -> {
                                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                                            if (btnConfirm != null) btnConfirm.setEnabled(true);
                                            Toast.makeText(OrderActivity. this, "Lỗi thêm món: " + message, Toast.LENGTH_LONG).show();
                                        });
                                    }
                                }
                        );
                    }
                }

                @Override
                public void onError(String message) {
                    OrderHelper.createNewOrderFromAddMap(
                            addQtyMap,
                            notesMap,
                            menuAdapter,
                            tableNumber,
                            fakeServerId,
                            fakeCashierId,
                            orderRepository,
                            new OrderHelper.OrderCallback() {
                                @Override
                                public void onSuccess() {
                                    runOnUiThread(() -> {
                                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                                        Toast.makeText(OrderActivity. this, "Thêm món thành công", Toast.LENGTH_SHORT).show();
                                        addQtyMap.clear();
                                        notesMap.clear();
                                        saveNotesToPrefs();
                                        navigateBackToMain();
                                    });
                                }
                                @Override
                                public void onError(String message) {
                                    runOnUiThread(() -> {
                                        if (progressBar != null) progressBar.setVisibility(View. GONE);
                                        if (btnConfirm != null) btnConfirm.setEnabled(true);
                                        Toast. makeText(OrderActivity.this, "Lỗi thêm món: " + message, Toast.LENGTH_LONG).show();
                                    });
                                }
                            }
                    );
                }
            });
        });
    }

    private void navigateBackToMain() {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent. FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "navigateBackToMain failed", e);
        } finally {
            finish();
        }
    }

    @Override
    public void onItemStatusMatched(String candidateId, String status) {
        try {
            boolean updated = false;
            if (orderedAdapter != null) updated = orderedAdapter.updateItemStatus(candidateId, status);
            if (! updated) {
                runOnUiThread(this::loadExistingOrdersForTable);
            }
        } catch (Exception e) {
            Log.w(TAG, "onItemStatusMatched error", e);
            runOnUiThread(this:: loadExistingOrdersForTable);
        }
    }

    @Override
    public void onNoMatchReload() {
        runOnUiThread(this::loadExistingOrdersForTable);
    }

    @Override
    public void onSocketConnected() {
        Log.d(TAG, "socket connected (activity)");
    }

    @Override
    public void onSocketDisconnected() {
        Log.d(TAG, "socket disconnected (activity)");
    }

    @Override
    public void onCheckItemsRequest(int tableNum, String[] orderIds) {
        Log.d(TAG, "✅ onCheckItemsRequest received via socket for table " + tableNum + " (current: " + tableNumber + ")");
        if (tableNum == tableNumber || tableNum <= 0) {
            handleCheckItemsRequest(tableNum > 0 ? tableNum : tableNumber, orderIds);
        } else {
            Log.d(TAG, "⏭️ Ignoring check items request for table " + tableNum + " (current: " + tableNumber + ")");
        }
    }

    @Override
    public String getNoteForMenu(String menuId) {
        if (menuId == null) return "";
        try {
            if (menuId.startsWith("cancel: ")) {
                String key = menuId.substring("cancel:".length());
                return cancelNotesMap.getOrDefault(key, "");
            } else {
                return notesMap.getOrDefault(menuId, "");
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    @Override
    public void putNoteForMenu(String menuId, String note) {
        if (menuId == null) return;
        try {
            if (menuId.startsWith("cancel:")) {
                String key = menuId.substring("cancel:".length());
                if (note == null || note.isEmpty()) cancelNotesMap.remove(key);
                else cancelNotesMap. put(key, note);
            } else {
                if (note == null || note.isEmpty()) notesMap.remove(menuId);
                else notesMap.put(menuId, note);
            }
            saveNotesToPrefs();
        } catch (Exception ignored) {}
    }

    private void loadNotesFromPrefs() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            loadMapFromPrefs(prefs, NOTES_KEY, notesMap);
            loadMapFromPrefs(prefs, CANCEL_NOTES_KEY, cancelNotesMap);
        } catch (Exception e) {
            Log.w(TAG, "loadNotesFromPrefs failed:  " + e.getMessage(), e);
        }
    }

    private void loadMapFromPrefs(SharedPreferences prefs, String key, Map<String, String> dest) {
        if (prefs == null || key == null || dest == null) return;
        String json = prefs.getString(key, null);
        if (json == null || json.isEmpty()) return;
        try {
            JSONObject o = new JSONObject(json);
            Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                String v = o.optString(k, "");
                if (k != null && ! k.isEmpty() && v != null) dest.put(k, v);
            }
        } catch (JSONException je) {
            Log.w(TAG, "loadMapFromPrefs(" + key + ") failed: " + je.getMessage());
        }
    }

    private void saveNotesToPrefs() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            saveMapToPrefs(prefs, NOTES_KEY, notesMap);
            saveMapToPrefs(prefs, CANCEL_NOTES_KEY, cancelNotesMap);
        } catch (Exception e) {
            Log.w(TAG, "saveNotesToPrefs failed: " + e.getMessage(), e);
        }
    }

    private void saveMapToPrefs(SharedPreferences prefs, String key, Map<String, String> src) {
        if (prefs == null || key == null) return;
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, String> e : src.entrySet()) {
                String k = e.getKey();
                String v = e.getValue();
                if (k != null && !k. isEmpty() && v != null) o.put(k, v);
            }
            prefs.edit().putString(key, o.toString()).apply();
        } catch (JSONException je) {
            Log.w(TAG, "saveMapToPrefs(" + key + ") failed: " + je.getMessage());
        }
    }
}