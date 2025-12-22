package com.ph48845.datn_qlnh_rmis.ui.phucvu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.InAppNotification;
import com.ph48845.datn_qlnh_rmis.ui.MainActivity;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter.MenuAdapter;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter.OrderAdapter;
import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.Order.OrderItem;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.MenuRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.notification.NotificationManager;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.socket.SocketManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OrderActivity
 * - Uses SocketManager singleton to receive real-time events
 * - Category-based menu filtering
 * - Real-time total calculation in menu view
 * - OnBackPressedDispatcher handling (menu <-> order details navigation)
 * - Confirm add items -> return to MainActivity
 * - Click on items with "done/xong/served/ready" status -> show confirmation dialog and update server
 * - Check items request handling via broadcast and polling
 *
 * Note: Activity registers/unregisters its SocketManager.OnEventListener in onResume/onPause.
 */
public class OrderActivity extends AppCompatActivity implements MenuAdapter.OnMenuClickListener,
        MenuLongPressHandler.NoteStore {

    private static final String TAG = "OrderActivity";

    // Check items request handling
    private BroadcastReceiver checkItemsReceiver;
    private Handler pollingHandler;
    private Runnable pollingRunnable;
    private static final long POLLING_INTERVAL_MS = 5000;
    private String lastCheckItemsRequestedAt = null;

    // UI Components
    private RecyclerView rvOrderedList;
    private RecyclerView rvMenuList;
    private ProgressBar progressBar;
    private TextView tvTable;
    private TextView tvTotal;
    private TextView tvEmptyState;
    private Button btnAddMore;
    private Button btnConfirm;
    private TabLayout tabLayoutCategory;

    // Repositories
    private MenuRepository menuRepository;
    private OrderRepository orderRepository;
    private TableRepository tableRepository;

    // Adapters
    private OrderAdapter orderedAdapter;
    private MenuAdapter menuAdapter;

    // Menu filtering
    private List<MenuItem> allMenuItems = new ArrayList<>();
    private String currentCategory = "Tất cả";

    // Data maps
    private final Map<String, Integer> addQtyMap = new HashMap<>();
    private final Map<String, String> notesMap = new HashMap<>();
    private final Map<String, String> cancelNotesMap = new HashMap<>();

    // Table info
    private String tableId;
    private int tableNumber;

    // Fake IDs
    private final String fakeServerId = "64a7f3b2c9d1e2f3a4b5c6d7";
    private final String fakeCashierId = "64b8e4c3d1f2a3b4c5d6e7f8";

    // Handlers
    private MenuLongPressHandler longPressHandler;

    // Socket
    private SocketManager socketManager;
    private SocketManager.OnEventListener orderListener;

    // Socket URL
    private String socketUrl = "http://192.168.1.84:3000";

    // SharedPreferences keys
    private static final String PREFS_NAME = "RestaurantPrefs";
    private static final String NOTES_KEY = "menu_notes_json";
    private static final String CANCEL_NOTES_KEY = "cancel_notes_json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "🚀 OrderActivity onCreate started");

        setContentView(R.layout.activity_order);

        // =========================
        // INIT REPOSITORIES
        // =========================
        menuRepository = new MenuRepository();
        orderRepository = new OrderRepository();
        tableRepository = new TableRepository();

        // =========================
        // MAP VIEWS
        // =========================
        rvOrderedList = findViewById(R.id.rv_ordered_list);
        rvMenuList = findViewById(R.id.rv_menu_list);
        progressBar = findViewById(R.id.progress_bar_order);
        tvTable = findViewById(R.id.tv_table_label);
        tvTotal = findViewById(R.id.tv_total_amount_ordered);
        tvEmptyState = findViewById(R.id.tv_empty_state);
        btnAddMore = findViewById(R.id.btn_add_more);
        btnConfirm = findViewById(R.id.btn_confirm_order);
        tabLayoutCategory = findViewById(R.id.tab_layout_category);

        // =========================
        // TOOLBAR BACK
        // =========================
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v ->
                    getOnBackPressedDispatcher().onBackPressed()
            );
        }

        // =========================
        // BACK PRESS HANDLER
        // =========================
        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        View menu = findViewById(R.id.menu_container);
                        View ordered = findViewById(R.id.ordered_container);

                        if (menu != null && menu.getVisibility() == View.VISIBLE) {
                            hideMenuView();
                            return;
                        }

                        navigateBackToMain();
                    }
                });

        // =========================
        // LOAD NOTES
        // =========================
        loadNotesFromPrefs();

        // =========================
        // ORDER LIST
        // =========================
        rvOrderedList.setLayoutManager(new LinearLayoutManager(this));
        orderedAdapter = new OrderAdapter(new ArrayList<>(), item -> {
            if (isItemDone(item.getStatus())) {
                showConfirmServedDialog(item);
            }
        }, this);
        rvOrderedList.setAdapter(orderedAdapter);

        // =========================
        // MENU LIST
        // =========================
        rvMenuList.setLayoutManager(new LinearLayoutManager(this));
        menuAdapter = new MenuAdapter(new ArrayList<>(), this);
        rvMenuList.setAdapter(menuAdapter);

        longPressHandler = new MenuLongPressHandler(this, rvMenuList, menuAdapter, this);
        longPressHandler.setup();

        // =========================
        // CATEGORY TABS
        // =========================
        if (tabLayoutCategory != null) {
            tabLayoutCategory.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    currentCategory = tab.getText() != null
                            ? tab.getText().toString()
                            : "Tất cả";
                    filterMenuByCategory(currentCategory);
                }
                @Override public void onTabUnselected(TabLayout.Tab tab) {}
                @Override public void onTabReselected(TabLayout.Tab tab) {}
            });
        }

        // =========================
        // BUTTONS
        // =========================
        if (btnAddMore != null) btnAddMore.setOnClickListener(v -> showMenuView());
        if (btnConfirm != null) btnConfirm.setOnClickListener(this::confirmAddItems);

        // =========================
        // TABLE INFO
        // =========================
        tableId = getIntent().getStringExtra("tableId");
        tableNumber = getIntent().getIntExtra("tableNumber", 0);
        if (tvTable != null) tvTable.setText("Bàn " + tableNumber);

        // =========================
        // SOCKET INIT (KHÔNG REGISTER LISTENER Ở ĐÂY)
        // =========================
        String extraSocket = getIntent().getStringExtra("socketUrl");
        if (extraSocket != null && !extraSocket.trim().isEmpty()) {
            socketUrl = extraSocket.trim();
        }

        socketManager = SocketManager.getInstance();
        socketManager.init(socketUrl);

        // ⚠️ CHỈ join room – KHÔNG connect, KHÔNG register listener
        socketManager.joinTable(tableNumber);

        // =========================
        // LOAD DATA
        // =========================
        loadMenuItems();
        loadExistingOrdersForTable();

        // =========================
        // BROADCAST + POLLING
        // =========================
        registerCheckItemsReceiver();

        pollingHandler = new Handler(Looper.getMainLooper());
        pollingHandler.postDelayed(this::startPollingForCheckItemsRequest, 1000);

        // =========================
        // NOTIFICATION
        // =========================
        NotificationManager.getInstance().init(this, null);

        Log.d(TAG, "✅ OrderActivity onCreate completed");
    }


    // ======================================================================
    // CATEGORY FILTERING
    // ======================================================================

    private void setupCategoryTabs(List<MenuItem> items) {
        if (tabLayoutCategory == null || items == null) return;

        tabLayoutCategory.removeAllTabs();

        Set<String> categories = new LinkedHashSet<>();
        for (MenuItem item : items) {
            if (item != null && item.getCategory() != null && !item.getCategory().trim().isEmpty()) {
                categories.add(item.getCategory().trim());
            }
        }

        tabLayoutCategory.addTab(tabLayoutCategory.newTab().setText("Tất cả"));

        for (String category : categories) {
            tabLayoutCategory.addTab(tabLayoutCategory.newTab().setText(category));
        }

        Log.d(TAG, "✅ Created " + tabLayoutCategory.getTabCount() + " tabs");
    }

    private void filterMenuByCategory(String category) {
        if (allMenuItems == null || allMenuItems.isEmpty()) {
            if (menuAdapter != null) menuAdapter.setItems(new ArrayList<>());
            return;
        }

        List<MenuItem> filtered = new ArrayList<>();

        if ("Tất cả".equals(category)) {
            filtered.addAll(allMenuItems);
        } else {
            for (MenuItem item : allMenuItems) {
                if (item != null && category.equals(item.getCategory())) {
                    filtered.add(item);
                }
            }
        }

        Log.d(TAG, "📋 Filtering by category: " + category + " → " + filtered.size() + " items");

        if (menuAdapter != null) {
            menuAdapter.setItems(filtered);

            for (MenuItem item : filtered) {
                if (item != null && item.getId() != null) {
                    Integer qty = addQtyMap.get(item.getId());
                    if (qty != null && qty > 0) {
                        menuAdapter.setQty(item.getId(), qty);
                    }
                }
            }
        }

        // Cập nhật tạm tính sau khi filter
        updateTotalInMenuView();
    }

    // ======================================================================
    // TOTAL CALCULATION IN MENU VIEW
    // ======================================================================

    private void updateTotalInMenuView() {
        if (tvTotal == null || allMenuItems == null) return;

        double total = 0.0;

        for (Map.Entry<String, Integer> entry : addQtyMap.entrySet()) {
            String menuId = entry.getKey();
            int qty = entry.getValue();

            if (qty <= 0) continue;

            for (MenuItem item : allMenuItems) {
                if (item != null && menuId.equals(item.getId())) {
                    total += item.getPrice() * qty;
                    break;
                }
            }
        }

        final double finalTotal = total;
        runOnUiThread(() -> {
            if (tvTotal != null) {
                try {
                    java.text.DecimalFormat formatter = new java.text.DecimalFormat("#,###");
                    String formattedTotal = formatter.format(finalTotal) + " VND";
                    tvTotal.setText(formattedTotal);
                    Log.d(TAG, "💰 Updated total in menu view: " + formattedTotal);
                } catch (Exception e) {
                    Log.w(TAG, "DecimalFormat error, using fallback", e);
                    tvTotal.setText(String.format(java.util.Locale.US, "%.0f VND", finalTotal));
                }
            }
        });
    }

    // ======================================================================
    // POLLING & BROADCAST RECEIVER
    // ======================================================================

    private void startPollingForCheckItemsRequest() {
        if (pollingHandler == null) {
            Log.e(TAG, "❌ Cannot start polling: pollingHandler is null");
            return;
        }

        stopPollingForCheckItemsRequest();

        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    checkForCheckItemsRequest();
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

                    String latestRequestedAt = null;
                    String latestOrderId = null;

                    for (Order order : orders) {
                        if (order == null) continue;
                        String requestedAt = order.getCheckItemsRequestedAt();
                        if (requestedAt != null && !requestedAt.trim().isEmpty()) {
                            if (lastCheckItemsRequestedAt == null ||
                                    requestedAt.compareTo(lastCheckItemsRequestedAt) > 0) {
                                latestRequestedAt = requestedAt;
                                latestOrderId = order.getId();
                            }
                        }
                    }

                    if (latestRequestedAt != null && !latestRequestedAt.equals(lastCheckItemsRequestedAt)) {
                        Log.d(TAG, "🔔 Polling detected new check items request for table " + tableNumber +
                                " at " + latestRequestedAt);
                        lastCheckItemsRequestedAt = latestRequestedAt;

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

    private void stopPollingForCheckItemsRequest() {
        if (pollingHandler != null && pollingRunnable != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
            Log.d(TAG, "⏹️ Stopped polling for check items request");
        }
    }

    private void registerCheckItemsReceiver() {
        if (checkItemsReceiver != null) return;
        checkItemsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) {
                    Log.w(TAG, "checkItemsReceiver:  intent is null");
                    return;
                }
                String action = intent.getAction();
                Log.d(TAG, "checkItemsReceiver: received action = " + action);

                if ("com.ph48845.datn_qlnh_rmis.ACTION_CHECK_ITEMS".equals(action)) {
                    int receivedTableNumber = intent.getIntExtra("tableNumber", -1);
                    String[] orderIds = intent.getStringArrayExtra("orderIds");

                    Log.d(TAG, "checkItemsReceiver: tableNumber = " + receivedTableNumber + ", current table = " + tableNumber);
                    Log.d(TAG, "checkItemsReceiver: orderIds = " + (orderIds != null ? java.util.Arrays.toString(orderIds) : "null"));

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

        Log.d(TAG, "🔄 Reloading orders for table " + tableNum);
        loadExistingOrdersForTable();
    }

    // ======================================================================
    // ITEM STATUS & SERVED
    // ======================================================================

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
            Toast.makeText(this, "Không xác định được order/item id", Toast.LENGTH_SHORT).show();
            return;
        }

        runOnUiThread(() -> {
            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        });

        orderRepository.updateOrderItemStatus(orderId, itemId, "served", new OrderRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    item.setStatus("served");
                    if (orderedAdapter != null) orderedAdapter.notifyDataSetChanged();
                    Toast.makeText(OrderActivity.this, "Đã xác nhận phục vụ", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Toast.makeText(OrderActivity.this, "Cập nhật thất bại: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ======================================================================
    // LIFECYCLE
    // ======================================================================

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "▶️ OrderActivity onResume");

        // =========================
        // TRÁNH TẠO LISTENER TRÙNG
        // =========================
        if (orderListener == null) {

            orderListener = new SocketManager.OnEventListener() {

                @Override
                public void onOrderCreated(JSONObject payload) {
                    Log.d(TAG, "📩 Socket onOrderCreated: " + payload);

                    if (payload == null) return;

                    int t = payload.optInt("tableNumber", -1);
                    if (t != tableNumber) return;

                    runOnUiThread(() -> {
                        loadExistingOrdersForTable();

                        InAppNotification notification =
                                new InAppNotification.Builder(
                                        InAppNotification.Type.ORDER_NEW,
                                        "Có order mới!",
                                        "Bàn " + t + " vừa có order mới."
                                ).duration(5000).build();

                        NotificationManager.getInstance().show(notification);
                        Log.d(TAG, "🔔 [NOTIFY] NotificationManager.show called: " + notification.getTitle());
                    });

                }

                @Override
                public void onOrderUpdated(JSONObject payload) {

                    Log.d(TAG, "📩 Socket onOrderUpdated: " + payload);

                    boolean updatedAny = false;

                    try {
                        if (payload != null && payload.has("items")) {
                            JSONArray items = payload.optJSONArray("items");
                            if (items != null && orderedAdapter != null) {
                                for (int i = 0; i < items.length(); i++) {
                                    JSONObject it = items.optJSONObject(i);
                                    if (it == null) continue;

                                    String itemId = it.optString(
                                            "itemId",
                                            it.optString("_id", it.optString("id", ""))
                                    );
                                    String status = it.optString(
                                            "status",
                                            it.optString("newStatus", "")
                                    );

                                    if (!itemId.isEmpty() && !status.isEmpty()) {
                                        if (orderedAdapter.updateItemStatus(itemId, status)) {
                                            updatedAny = true;
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "onOrderUpdated parse error", e);
                    }

                    final boolean needReload = !updatedAny;

                    runOnUiThread(() -> {
                        if (needReload) loadExistingOrdersForTable();

                        InAppNotification notification =
                                new InAppNotification.Builder(
                                        InAppNotification.Type.ORDER_UPDATED,
                                        "Order cập nhật",
                                        "Có cập nhật mới cho order."
                                ).duration(5000).build();

                        NotificationManager.getInstance().show(notification);
                        Log.d(TAG, "🔔 [NOTIFY] NotificationManager.show called: " + notification.getTitle());
                    });

                }

                @Override
                public void onTableUpdated(JSONObject payload) {
                    Log.d(TAG, "📩 Socket tableUpdated: " + payload);

                    if (payload != null &&
                            payload.optInt("tableNumber", -1) == tableNumber) {
                        runOnUiThread(() -> loadExistingOrdersForTable());
                    }
                }

                @Override
                public void onCheckItemsRequest(JSONObject payload) {
                    Log.d(TAG, "📩 Socket checkItemsRequest: " + payload);

                    if (payload == null) return;

                    int tbl = payload.optInt("tableNumber", -1);
                    String orderId = payload.optString("orderId", null);

                    if (tbl == tableNumber || tbl <= 0) {
                        handleCheckItemsRequest(
                                tbl > 0 ? tbl : tableNumber,
                                orderId != null ? new String[]{orderId} : null
                        );
                    }
                }

                @Override
                public void onConnect() {
                    Log.d(TAG, "✅ Socket connected (OrderActivity)");
                    socketManager.joinTable(tableNumber);
                }

                @Override
                public void onDisconnect() {
                    Log.d(TAG, "⛔ Socket disconnected (OrderActivity)");
                }

                @Override
                public void onError(Exception e) {
                    Log.w(TAG, "⚠️ Socket error (OrderActivity)", e);
                }
            };
        }

        // =========================
        // REGISTER + CONNECT
        // =========================
        socketManager.registerListener(orderListener);

        if (!socketManager.isConnected()) {
            socketManager.connect();
        }

        socketManager.joinTable(tableNumber);

        // =========================
        // CHECK ITEMS + POLLING
        // =========================
        if (checkItemsReceiver == null) {
            registerCheckItemsReceiver();
        }

        checkForCheckItemsRequest();

        if (pollingHandler == null) {
            pollingHandler = new Handler(Looper.getMainLooper());
        }

        stopPollingForCheckItemsRequest();
        startPollingForCheckItemsRequest();
    }


    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        stopPollingForCheckItemsRequest();

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

    // ======================================================================
    // LOAD MENU & ORDERS
    // ======================================================================

    private void loadMenuItems() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        menuRepository.getAllMenuItems(new MenuRepository.RepositoryCallback<List<MenuItem>>() {
            @Override
            public void onSuccess(List<MenuItem> data) {
                runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);

                    allMenuItems = data != null ? data : new ArrayList<>();

                    setupCategoryTabs(allMenuItems);

                    filterMenuByCategory("Tất cả");

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

    private void loadExistingOrdersForTable() {
        if (tableNumber <= 0) {
            showEmptyOrderState();
            return;
        }

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orders) {
                runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);

                    List<Order> filtered = new ArrayList<>();
                    if (orders != null) {
                        for (Order o : orders) {
                            if (o == null) continue;
                            try {
                                if (o.getTableNumber() == tableNumber) filtered.add(o);
                            } catch (Exception ignored) {
                            }
                        }
                    }

                    for (Order o : filtered) {
                        if (o == null) continue;
                        try {
                            o.normalizeItems();
                        } catch (Exception ignored) {
                        }
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
                            } catch (Exception ignored) {
                            }
                            oi.setParentOrderId(orderId);
                            flattened.add(oi);
                        }
                    }

                    try {
                        for (OrderItem oi : flattened) {
                            if (oi == null) continue;
                            String menuId = oi.getMenuItemId();
                            String itemId = oi.getId();
                            String saved = null;
                            if (menuId != null && !menuId.isEmpty()) saved = cancelNotesMap.get(menuId);
                            if ((saved == null || saved.isEmpty()) && itemId != null && !itemId.isEmpty())
                                saved = cancelNotesMap.get(itemId);
                            try {
                                String existing = oi.getCancelReason();
                                if ((existing == null || existing.trim().isEmpty()) && saved != null && !saved.isEmpty()) {
                                    oi.setCancelReason(saved);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    } catch (Exception ignored) {
                    }

                    showOrderListWithItems(flattened);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Toast.makeText(OrderActivity.this, "Lỗi tải đơn hàng: " + message, Toast.LENGTH_LONG).show();
                    showEmptyOrderState();
                });
            }
        });
    }

    private void showEmptyOrderState() {
        hideMenuView();

        if (orderedAdapter != null) orderedAdapter.setItems(new ArrayList<>());
        if (tvTotal != null) tvTotal.setText("0 VND");

        if (tvEmptyState != null) {
            tvEmptyState.setVisibility(View.VISIBLE);
            tvEmptyState.setText("Chưa có món nào được gọi.\nNhấn \"Thêm món\" để bắt đầu.");
        }

        if (rvOrderedList != null) rvOrderedList.setVisibility(View.GONE);
    }

    private void showOrderListWithItems(List<OrderItem> items) {
        hideMenuView();

        if (orderedAdapter != null) orderedAdapter.setItems(items);

        double total = 0.0;
        for (OrderItem oi : items) {
            try {
                total += oi.getPrice() * oi.getQuantity();
            } catch (Exception ignored) {
            }
        }
        if (tvTotal != null) tvTotal.setText(String.format("%,.0f VND", total));

        if (tvEmptyState != null) tvEmptyState.setVisibility(View.GONE);
        if (rvOrderedList != null) rvOrderedList.setVisibility(View.VISIBLE);
    }

    @SuppressWarnings("unchecked")
    private void ensureCancelReasonFromRaw(OrderItem oi) {
        try {
            if (oi == null) return;
            String cr = null;
            try {
                cr = oi.getCancelReason();
            } catch (Exception ignored) {
            }
            if (cr != null && !cr.trim().isEmpty()) return;

            Object raw = null;
            try {
                raw = oi.getMenuItemRaw();
            } catch (Exception ignored) {
            }
            if (raw instanceof Map) {
                Map<?, ?> m = (Map<?, ?>) raw;
                Object v = m.get("cancelReason");
                if (v == null) v = m.get("cancel_reason");
                if (v == null) v = m.get("reason");
                if (v != null) {
                    String s = String.valueOf(v);
                    if (s != null && !s.trim().isEmpty()) {
                        oi.setCancelReason(s.trim());
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
                            if (s != null && !s.trim().isEmpty()) oi.setCancelReason(s.trim());
                        }
                    }
                }
            } catch (Exception ignored) {
            }

        } catch (Exception ignored) {
        }
    }

    // ======================================================================
    // MENU ADAPTER CALLBACKS
    // ======================================================================

    @Override
    public void onAddMenuItem(MenuItem menu) {
        if (menu == null) return;
        int cur = addQtyMap.getOrDefault(menu.getId(), 0) + 1;
        addQtyMap.put(menu.getId(), cur);
        if (menuAdapter != null) menuAdapter.setQty(menu.getId(), cur);
        if (btnConfirm != null) btnConfirm.setEnabled(!addQtyMap.isEmpty());

        // Cập nhật tạm tính
        updateTotalInMenuView();
    }

    @Override
    public void onRemoveMenuItem(MenuItem menu) {
        if (menu == null) return;
        int cur = addQtyMap.getOrDefault(menu.getId(), 0);
        if (cur > 0) {
            cur--;
            if (cur == 0) addQtyMap.remove(menu.getId());
            else addQtyMap.put(menu.getId(), cur);
            if (menuAdapter != null) menuAdapter.setQty(menu.getId(), cur);
        }
        if (btnConfirm != null) btnConfirm.setEnabled(!addQtyMap.isEmpty());

        // Cập nhật tạm tính
        updateTotalInMenuView();
    }

    public void onAddMenuItem(MenuItem menu, int qty) {
        if (menu == null) return;
        int cur = addQtyMap.getOrDefault(menu.getId(), 0) + qty;
        if (cur <= 0) addQtyMap.remove(menu.getId());
        else addQtyMap.put(menu.getId(), cur);
        if (menuAdapter != null) menuAdapter.setQty(menu.getId(), cur);
        if (btnConfirm != null) btnConfirm.setEnabled(!addQtyMap.isEmpty());

        // Cập nhật tạm tính
        updateTotalInMenuView();
    }

    public void onRemoveMenuItem(MenuItem menu, int qty) {
        if (menu == null) return;
        int cur = addQtyMap.getOrDefault(menu.getId(), 0) - qty;
        if (cur <= 0) addQtyMap.remove(menu.getId());
        else addQtyMap.put(menu.getId(), cur);
        if (menuAdapter != null) menuAdapter.setQty(menu.getId(), Math.max(0, cur));
        if (btnConfirm != null) btnConfirm.setEnabled(!addQtyMap.isEmpty());

        // Cập nhật tạm tính
        updateTotalInMenuView();
    }

    // ======================================================================
    // VIEW NAVIGATION
    // ======================================================================

    private void showMenuView() {
        View vOrdered = findViewById(R.id.ordered_container);
        View vSummary = findViewById(R.id.order_summary);
        View vMenu = findViewById(R.id.menu_container);

        if (vOrdered != null) vOrdered.setVisibility(View.GONE);
        if (vSummary != null) vSummary.setVisibility(View.VISIBLE);
        if (vMenu != null) vMenu.setVisibility(View.VISIBLE);

        if (btnAddMore != null) btnAddMore.setVisibility(View.GONE);
        if (btnConfirm != null) btnConfirm.setVisibility(View.VISIBLE);

        // Cập nhật tạm tính khi mở menu view
        updateTotalInMenuView();
    }

    private void hideMenuView() {
        View vMenu = findViewById(R.id.menu_container);
        View vOrdered = findViewById(R.id.ordered_container);
        View vSummary = findViewById(R.id.order_summary);

        if (vMenu != null) vMenu.setVisibility(View.GONE);
        if (vOrdered != null) vOrdered.setVisibility(View.VISIBLE);
        if (vSummary != null) vSummary.setVisibility(View.VISIBLE);

        if (btnAddMore != null) btnAddMore.setVisibility(View.VISIBLE);
        if (btnConfirm != null) btnConfirm.setVisibility(View.GONE);
    }

    // ======================================================================
    // CONFIRM ADD ITEMS
    // ======================================================================

    public void confirmAddItems(View view) {
        confirmAddItems();
    }

    private void confirmAddItems() {
        OrderHelper.showConfirmationDialog(this, addQtyMap, notesMap, menuAdapter, (confirmed) -> {
            if (!confirmed) return;
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
                                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                                    addQtyMap.clear();
                                    notesMap.clear();
                                    saveNotesToPrefs();
                                    Toast.makeText(OrderActivity.this, "Thêm món vào order hiện có thành công", Toast.LENGTH_SHORT).show();
                                    navigateBackToMain();
                                });
                            }

                            @Override
                            public void onError(String message) {
                                runOnUiThread(() -> {
                                    if (progressBar != null) progressBar.setVisibility(View.GONE);
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
                                            Toast.makeText(OrderActivity.this, "Lỗi thêm món: " + message, Toast.LENGTH_LONG).show();
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
                                        Toast.makeText(OrderActivity.this, "Lỗi thêm món: " + message, Toast.LENGTH_LONG).show();
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
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "navigateBackToMain failed", e);
        } finally {
            finish();
        }
    }

    // ======================================================================
    // NOTE STORE CALLBACKS
    // ======================================================================

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
                else cancelNotesMap.put(key, note);
            } else {
                if (note == null || note.isEmpty()) notesMap.remove(menuId);
                else notesMap.put(menuId, note);
            }
            saveNotesToPrefs();
        } catch (Exception ignored) {
        }
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
                if (k != null && !k.isEmpty() && v != null) dest.put(k, v);
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
                if (k != null && !k.isEmpty() && v != null) o.put(k, v);
            }
            prefs.edit().putString(key, o.toString()).apply();
        } catch (JSONException je) {
            Log.w(TAG, "saveMapToPrefs(" + key + ") failed: " + je.getMessage());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

    }


}