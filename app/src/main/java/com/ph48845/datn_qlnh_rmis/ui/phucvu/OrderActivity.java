package com.ph48845.datn_qlnh_rmis.ui.phucvu;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView; // <-- SỬA Ở ĐÂY: sử dụng androidx.recyclerview.widget.RecyclerView

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.adapter.MenuAdapter;
import com.ph48845.datn_qlnh_rmis.adapter.OrderAdapter;
import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.Order.OrderItem;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.repository.MenuRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;
import com.ph48845.datn_qlnh_rmis.ui.bep.SocketManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * OrderActivity: hiển thị danh sách món đã order (mặc định),
 * nút "Thêm món" chuyển sang chế độ thêm món (hiển thị danh sách menu).
 *
 * Sửa: khi bấm vào bàn đang có khách thì sẽ luôn hiện danh sách các món đã order (nếu có),
 * nếu server trả orders rỗng nhưng bàn có status OCCUPIED thì vẫn show ordered view (với message).
 *
 * Đồng thời: tích hợp socket realtime cho phục vụ để nhận "order_updated" từ server.
 *
 * LƯU Ý: Chỉ bổ sung phương thức interface và overload để sửa lỗi compile,
 * không thay đổi logic gốc.
 */
public class OrderActivity extends AppCompatActivity implements MenuAdapter.OnMenuClickListener {

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

    // cart for adding new items (menuId -> qty)
    private final Map<String, Integer> addQtyMap = new HashMap<>();

    private String tableId;
    private int tableNumber;

    // fake ids for testing (24-hex) - remove/replace in production
    private final String fakeServerId = "64a7f3b2c9d1e2f3a4b5c6d7";
    private final String fakeCashierId = "64b8e4c3d1f2a3b4c5d6e7f8";

    // Socket
    private final SocketManager socketManager = SocketManager.getInstance();
    private final String SOCKET_URL = "http://192.168.1.84:3000"; // đổi theo server của bạn

    // BroadcastReceiver để nhận request kiểm tra món từ thu ngân
    private BroadcastReceiver checkItemsReceiver;
    private static final String ACTION_CHECK_ITEMS = "com.ph48845.datn_qlnh_rmis.ACTION_CHECK_ITEMS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order);

        rvOrderedList = findViewById(R.id.rv_ordered_list);
        rvMenuList = findViewById(R.id.rv_menu_list);
        progressBar = findViewById(R.id.progress_bar_order);
        tvTable = findViewById(R.id.tv_table_label);
        tvTotal = findViewById(R.id.tv_total_amount_ordered);
        btnAddMore = findViewById(R.id.btn_add_more);
        btnConfirm = findViewById(R.id.btn_confirm_order);

        menuRepository = new MenuRepository();
        orderRepository = new OrderRepository();
        tableRepository = new TableRepository();

        tableId = getIntent().getStringExtra("tableId");
        tableNumber = getIntent().getIntExtra("tableNumber", 0);
        tvTable.setText("Bàn " + tableNumber);

        // ordered list setup
        rvOrderedList.setLayoutManager(new LinearLayoutManager(this));
        orderedAdapter = new OrderAdapter(new ArrayList<>(), item -> {
            // optional: show item details or allow marking status
            Toast.makeText(OrderActivity.this, "Món: " + item.getName(), Toast.LENGTH_SHORT).show();
        });
        rvOrderedList.setAdapter(orderedAdapter);

        // menu list setup (for adding)
        rvMenuList.setLayoutManager(new LinearLayoutManager(this));
        menuAdapter = new MenuAdapter(new ArrayList<>(), this);
        rvMenuList.setAdapter(menuAdapter);

        btnAddMore.setOnClickListener(v -> showMenuView());
        btnConfirm.setOnClickListener(v -> confirmAddItems());

        // Load menu and existing orders (so previously-ordered items get preloaded)
        loadMenuItems();
        loadExistingOrdersForTable();

        // Start socket realtime and join room 'phucvu' so this client receives updates
        startRealtimeSocket();

        // Đăng ký BroadcastReceiver để nhận request kiểm tra món từ thu ngân
        registerCheckItemsReceiver();
        
        // Load các yêu cầu kiểm tra từ server
        loadCheckItemsRequestsFromServer();
    }

    private void startRealtimeSocket() {
        try {
            socketManager.init(SOCKET_URL);
            socketManager.setOnEventListener(new SocketManager.OnEventListener() {
                @Override
                public void onOrderCreated(org.json.JSONObject payload) {
                    // simple behavior: reload orders
                    Log.d(TAG, "Socket onOrderCreated: " + (payload != null ? payload.toString() : "null"));
                    runOnUiThread(() -> loadExistingOrdersForTable());
                }

                @Override
                public void onOrderUpdated(org.json.JSONObject payload) {
                    Log.d(TAG, "Socket onOrderUpdated: " + (payload != null ? payload.toString() : "null"));
                    // Reload orders for current table (quick and safe)
                    runOnUiThread(() -> {
                        loadExistingOrdersForTable();
                        // Kiểm tra lại yêu cầu kiểm tra từ server
                        loadCheckItemsRequestsFromServer();
                    });
                }

                @Override
                public void onConnect() {
                    Log.d(TAG, "Socket connected (phucvu)");
                    // join room 'phucvu' so server can broadcast only to phục vụ clients
                    socketManager.emitJoinRoom("phucvu");
                    // Đăng ký listener cho event check_items_request từ server
                    registerSocketCheckItemsListener();
                }

                @Override
                public void onDisconnect() {
                    Log.d(TAG, "Socket disconnected (phucvu)");
                }

                @Override
                public void onError(Exception e) {
                    Log.w(TAG, "Socket error (phucvu): " + (e != null ? e.getMessage() : ""));
                }
            });
            socketManager.connect();
        } catch (Exception e) {
            Log.w(TAG, "startRealtimeSocket failed: " + e.getMessage(), e);
        }
    }

    /**
     * Đăng ký listener cho socket event "check_items_request" từ server
     */
    private void registerSocketCheckItemsListener() {
        try {
            // Khi order được update với checkItemsRequestedAt từ server,
            // event order_updated sẽ được trigger và loadCheckItemsRequestsFromServer() sẽ được gọi
            // Nếu server gửi event riêng "check_items_request", có thể thêm listener ở đây
            Log.d(TAG, "Socket listener for check_items_request ready (via order_updated event)");
        } catch (Exception e) {
            Log.w(TAG, "Failed to register socket check items listener: " + e.getMessage());
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Reload yêu cầu kiểm tra khi quay lại màn hình
        loadCheckItemsRequestsFromServer();
    }

    @Override
    protected void onDestroy() {
        try {
            socketManager.disconnect();
        } catch (Exception ignored) {}
        // Hủy đăng ký BroadcastReceiver
        unregisterCheckItemsReceiver();
        super.onDestroy();
    }

    /**
     * Đăng ký BroadcastReceiver để nhận request kiểm tra món từ thu ngân
     */
    private void registerCheckItemsReceiver() {
        checkItemsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_CHECK_ITEMS.equals(intent.getAction())) {
                    int requestTableNumber = intent.getIntExtra("tableNumber", 0);
                    String[] orderIds = intent.getStringArrayExtra("orderIds");
                    
                    Log.d(TAG, "Received check items request for table " + requestTableNumber);
                    
                    // Nếu request là cho bàn hiện tại, hiển thị thông báo và reload orders
                    if (requestTableNumber == tableNumber) {
                        runOnUiThread(() -> {
                            Toast.makeText(OrderActivity.this, 
                                "Yêu cầu kiểm tra lại món ăn cho bàn " + tableNumber, 
                                Toast.LENGTH_LONG).show();
                            
                            // Reload orders để cập nhật danh sách món
                            loadExistingOrdersForTable();
                        });
                    } else {
                        // Nếu request là cho bàn khác, chỉ log (có thể mở rộng để hiển thị thông báo chung)
                        Log.d(TAG, "Check items request for different table: " + requestTableNumber + 
                            " (current: " + tableNumber + ")");
                    }
                }
            }
        };
        
        IntentFilter filter = new IntentFilter(ACTION_CHECK_ITEMS);
        registerReceiver(checkItemsReceiver, filter);
        Log.d(TAG, "Registered check items receiver");
    }

    /**
     * Hủy đăng ký BroadcastReceiver
     */
    private void unregisterCheckItemsReceiver() {
        if (checkItemsReceiver != null) {
            try {
                unregisterReceiver(checkItemsReceiver);
                Log.d(TAG, "Unregistered check items receiver");
            } catch (IllegalArgumentException e) {
                // Receiver chưa được đăng ký, bỏ qua
                Log.w(TAG, "Receiver not registered: " + e.getMessage());
            }
            checkItemsReceiver = null;
        }
    }

    /**
     * Load các yêu cầu kiểm tra từ server (orders có checkItemsRequestedAt)
     */
    private void loadCheckItemsRequestsFromServer() {
        // Lấy tất cả orders để tìm các yêu cầu kiểm tra
        orderRepository.getOrdersByTableNumber(null, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> allOrders) {
                runOnUiThread(() -> {
                    if (allOrders == null || allOrders.isEmpty()) {
                        return;
                    }
                    
                    // Lọc các orders có checkItemsRequestedAt
                    List<Order> checkRequests = new ArrayList<>();
                    for (Order order : allOrders) {
                        if (order != null && order.getCheckItemsRequestedAt() != null 
                            && !order.getCheckItemsRequestedAt().trim().isEmpty()) {
                            checkRequests.add(order);
                        }
                    }
                    
                    // Nếu có yêu cầu kiểm tra cho bàn hiện tại, hiển thị thông báo
                    for (Order requestOrder : checkRequests) {
                        if (requestOrder.getTableNumber() == tableNumber) {
                            showCheckItemsRequestNotification(requestOrder);
                            break; // Chỉ hiển thị một lần
                        }
                    }
                    
                    // Nếu có yêu cầu cho các bàn khác, có thể hiển thị tổng số
                    if (!checkRequests.isEmpty()) {
                        int otherTableRequests = 0;
                        for (Order requestOrder : checkRequests) {
                            if (requestOrder.getTableNumber() != tableNumber) {
                                otherTableRequests++;
                            }
                        }
                        if (otherTableRequests > 0) {
                            Log.d(TAG, "Có " + otherTableRequests + " yêu cầu kiểm tra cho các bàn khác");
                        }
                    }
                });
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Error loading check items requests: " + message);
            }
        });
    }

    /**
     * Hiển thị thông báo yêu cầu kiểm tra món
     */
    private void showCheckItemsRequestNotification(Order order) {
        if (order == null) {
            return;
        }
        
        String timeInfo = "";
        if (order.getCheckItemsRequestedAt() != null) {
            try {
                // Parse ISO date và format lại
                java.text.SimpleDateFormat inputFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
                java.text.SimpleDateFormat outputFormat = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
                java.util.Date date = inputFormat.parse(order.getCheckItemsRequestedAt());
                timeInfo = " lúc " + outputFormat.format(date);
            } catch (Exception e) {
                timeInfo = "";
            }
        }
        
        Toast.makeText(this, 
            "Yêu cầu kiểm tra lại món ăn cho bàn " + order.getTableNumber() + timeInfo, 
            Toast.LENGTH_LONG).show();
        
        // Reload orders để cập nhật
        loadExistingOrdersForTable();
    }

    private void loadMenuItems() {
        progressBar.setVisibility(View.VISIBLE);
        menuRepository.getAllMenuItems(new MenuRepository.RepositoryCallback<List<MenuItem>>() {
            @Override
            public void onSuccess(List<MenuItem> data) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    menuAdapter.setItems(data != null ? data : new ArrayList<>());
                    // update adapter qty display if any added already
                    for (Map.Entry<String, Integer> e : addQtyMap.entrySet()) {
                        menuAdapter.setQty(e.getKey(), e.getValue());
                    }
                    Log.d(TAG, "Loaded menu items count=" + (data != null ? data.size() : 0));
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(OrderActivity.this, "Lỗi tải menu: " + message, Toast.LENGTH_LONG).show();
                    Log.w(TAG, "loadMenuItems error: " + message);
                });
            }
        });
    }

    /**
     * Load existing orders for the table.
     *
     * Changes:
     *  - First fetch table info (by tableId) to get its status.
     *  - Then fetch orders for this tableNumber and decide UI:
     *      * if orders found -> merge items and show ordered view
     *      * if no orders and table is OCCUPIED -> show ordered view (empty) and inform user
     *      * if no orders and table is not OCCUPIED -> show menu view so staff can add items
     */
    private void loadExistingOrdersForTable() {
        if (tableNumber <= 0) {
            Log.w(TAG, "loadExistingOrdersForTable: invalid tableNumber=" + tableNumber);
            showMenuView();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        Log.d(TAG, "loadExistingOrdersForTable: tableId=" + tableId + " tableNumber=" + tableNumber);

        // If we have tableId, try to fetch latest table info to know its status
        if (tableId != null && !tableId.trim().isEmpty()) {
            tableRepository.getTableById(tableId, new TableRepository.RepositoryCallback<TableItem>() {
                @Override
                public void onSuccess(TableItem tableItem) {
                    boolean isOccupied = false;
                    try {
                        isOccupied = tableItem != null && tableItem.getStatus() == TableItem.Status.OCCUPIED;
                    } catch (Exception ignored) {}
                    // proceed to fetch orders with knowledge of occupation state
                    fetchOrdersForTable(isOccupied);
                }

                @Override
                public void onError(String message) {
                    Log.w(TAG, "Could not fetch table info: " + message + " - proceeding to fetch orders without table status");
                    // fallback: fetch orders without knowing status (treat as not-occupied)
                    fetchOrdersForTable(false);
                }
            });
        } else {
            // No tableId (maybe caller didn't pass) - still fetch orders by tableNumber
            fetchOrdersForTable(false);
        }
    }

    /**
     * Internal helper: fetch orders and decide UI based on whether table is occupied (from param).
     */
    private void fetchOrdersForTable(final boolean tableIsOccupied) {
        orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orders) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Log.d(TAG, "getOrdersByTableNumber.onSuccess: orders=" + (orders == null ? "null" : orders.size()));

                    // Defensive: filter orders whose tableNumber actually equals this.tableNumber
                    List<Order> filtered = new ArrayList<>();
                    if (orders != null) {
                        for (Order o : orders) {
                            if (o == null) continue;
                            try {
                                if (o.getTableNumber() == tableNumber) filtered.add(o);
                            } catch (Exception ex) {
                                Log.w(TAG, "Error reading order.tableNumber: " + ex.getMessage(), ex);
                            }
                        }
                    }

                    Log.d(TAG, "Filtered orders for table " + tableNumber + " => count=" + filtered.size());
                    for (Order o : filtered) {
                        if (o == null) continue;
                        Log.d(TAG, "Order id=" + o.getId() + " status=" + o.getOrderStatus() + " itemsCount=" + (o.getItems() == null ? 0 : o.getItems().size()));
                    }

                    if (filtered.isEmpty()) {
                        // No orders returned for this table
                        if (tableIsOccupied) {
                            // Table is occupied but no orders returned: show ordered view (empty) so staff can see empty list and add items
                            orderedAdapter.setItems(new ArrayList<>());
                            tvTotal.setText("0 VND");
                            Toast.makeText(OrderActivity.this, "Bàn đang có khách nhưng chưa có món. Bạn có thể thêm món.", Toast.LENGTH_LONG).show();
                            hideMenuView(); // show ordered view (empty)
                        } else {
                            // Table not occupied and no orders -> show menu so staff can add items
                            showMenuView();
                        }
                        return;
                    }

                    // Merge items from all returned orders into list (sum quantities)
                    List<OrderItem> merged = new ArrayList<>();
                    for (Order o : filtered) {
                        if (o == null || o.getItems() == null) continue;
                        for (Order.OrderItem oi : o.getItems()) {
                            if (oi == null) continue;
                            String menuId = oi.getMenuItemId();
                            if (menuId == null || menuId.trim().isEmpty()) {
                                Log.w(TAG, "OrderItem missing menuId in order=" + o.getId() + " name=" + oi.getName());
                            }
                            boolean found = false;
                            for (OrderItem ex : merged) {
                                if (ex.getMenuItemId() != null && ex.getMenuItemId().equals(menuId)) {
                                    ex.setQuantity(ex.getQuantity() + oi.getQuantity());
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                try {
                                    merged.add(new OrderItem(menuId, oi.getName(), oi.getQuantity(), oi.getPrice()));
                                } catch (Exception ex) {
                                    merged.add(oi);
                                }
                            }
                        }
                    }

                    orderedAdapter.setItems(merged);
                    double total = 0.0;
                    for (OrderItem oi : merged) {
                        total += oi.getPrice() * oi.getQuantity();
                    }
                    tvTotal.setText(String.format("%,.0f VND", total));

                    // Ensure ordered view visible
                    hideMenuView();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Log.w(TAG, "Lỗi khi lấy orders cho bàn " + tableNumber + ": " + message);
                    Toast.makeText(OrderActivity.this, "Lỗi tải đơn hàng: " + message, Toast.LENGTH_LONG).show();
                    // Fallback: show menu so staff can still add items
                    showMenuView();
                });
            }
        });
    }

    // MenuAdapter.OnMenuClickListener - thực thi interface (các method bắt buộc)
    @Override
    public void onAddMenuItem(MenuItem menu) {
        if (menu == null) return;
        int cur = addQtyMap.getOrDefault(menu.getId(), 0) + 1;
        addQtyMap.put(menu.getId(), cur);
        if (menuAdapter != null) menuAdapter.setQty(menu.getId(), cur);
        if (btnConfirm != null) btnConfirm.setEnabled(!addQtyMap.isEmpty());
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
    }

    // Một số adapter/interface có thể định nghĩa các phương thức overload (ví dụ có thêm param qty/position).
    // Thêm các overload an toàn để tránh lỗi khi interface thay đổi nhẹ.
    public void onAddMenuItem(MenuItem menu, int qty) {
        if (menu == null) return;
        int cur = addQtyMap.getOrDefault(menu.getId(), 0) + qty;
        if (cur <= 0) addQtyMap.remove(menu.getId());
        else addQtyMap.put(menu.getId(), cur);
        if (menuAdapter != null) menuAdapter.setQty(menu.getId(), cur);
        if (btnConfirm != null) btnConfirm.setEnabled(!addQtyMap.isEmpty());
    }

    public void onRemoveMenuItem(MenuItem menu, int qty) {
        if (menu == null) return;
        int cur = addQtyMap.getOrDefault(menu.getId(), 0) - qty;
        if (cur <= 0) addQtyMap.remove(menu.getId());
        else addQtyMap.put(menu.getId(), cur);
        if (menuAdapter != null) menuAdapter.setQty(menu.getId(), Math.max(0, cur));
        if (btnConfirm != null) btnConfirm.setEnabled(!addQtyMap.isEmpty());
    }

    private void showMenuView() {
        findViewById(R.id.ordered_container).setVisibility(View.GONE);
        findViewById(R.id.order_summary).setVisibility(View.GONE);
        findViewById(R.id.menu_container).setVisibility(View.VISIBLE);
    }

    private void hideMenuView() {
        findViewById(R.id.menu_container).setVisibility(View.GONE);
        findViewById(R.id.ordered_container).setVisibility(View.VISIBLE);
        findViewById(R.id.order_summary).setVisibility(View.VISIBLE);
    }

    /**
     * Confirm thêm món: nếu bàn đã có order đang mở thì gộp vào order tồn tại (update),
     * ngược lại tạo order mới (create).
     */
    private void confirmAddItems() {
        if (addQtyMap.isEmpty()) {
            Toast.makeText(this, "Chưa chọn món để thêm", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnConfirm.setEnabled(false);

        // 1) Try to find an existing open order for this table
        orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orders) {
                // Find an "open" order: prefer orderStatus "pending" or "preparing", otherwise newest order
                Order targetOrder = null;
                if (orders != null && !orders.isEmpty()) {
                    for (Order o : orders) {
                        if (o == null) continue;
                        if (o.getTableNumber() != tableNumber) continue;
                        String s = o.getOrderStatus() != null ? o.getOrderStatus().toLowerCase() : "";
                        if (s.contains("pending") || s.contains("preparing") || s.contains("open") || s.contains("unpaid") || s.isEmpty()) {
                            targetOrder = o;
                            break;
                        }
                    }
                    if (targetOrder == null) {
                        Order newest = null;
                        for (Order o : orders) {
                            if (o == null) continue;
                            if (o.getTableNumber() != tableNumber) continue;
                            if (newest == null) newest = o;
                            else {
                                String a = newest.getCreatedAt() != null ? newest.getCreatedAt() : "";
                                String b = o.getCreatedAt() != null ? o.getCreatedAt() : "";
                                if (b.compareTo(a) > 0) newest = o;
                            }
                        }
                        targetOrder = newest;
                    }
                }

                if (targetOrder != null) {
                    mergeIntoExistingOrderAndUpdate(targetOrder);
                } else {
                    createNewOrderFromAddMap();
                }
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Cannot fetch orders to merge: " + message);
                createNewOrderFromAddMap();
            }
        });
    }

    private void mergeIntoExistingOrderAndUpdate(Order existing) {
        if (existing == null) {
            createNewOrderFromAddMap();
            return;
        }

        Map<String, OrderItem> mergedMap = new HashMap<>();
        if (existing.getItems() != null) {
            for (OrderItem oi : existing.getItems()) {
                if (oi == null) continue;
                String mid = oi.getMenuItemId();
                if (mid == null) continue;
                try {
                    OrderItem copy = new OrderItem(mid, oi.getName(), oi.getQuantity(), oi.getPrice());
                    mergedMap.put(mid, copy);
                } catch (Exception ex) {
                    mergedMap.put(mid, oi);
                }
            }
        }

        for (Map.Entry<String, Integer> e : addQtyMap.entrySet()) {
            String menuId = e.getKey();
            int addQty = e.getValue();
            if (menuId == null || addQty <= 0) continue;

            OrderItem existingOi = mergedMap.get(menuId);
            if (existingOi != null) {
                existingOi.setQuantity(existingOi.getQuantity() + addQty);
            } else {
                // Safely try to get menu info from adapter without assuming method exists
                MenuItem mi = getMenuItemFromAdapter(menuId);
                String name = mi != null ? mi.getName() : "";
                double price = mi != null ? mi.getPrice() : 0.0;
                OrderItem newOi;
                try {
                    newOi = new OrderItem(menuId, name, addQty, price);
                } catch (Exception ex) {
                    newOi = new OrderItem();
                    newOi.setMenuItemId(menuId);
                    newOi.setName(name);
                    newOi.setQuantity(addQty);
                    newOi.setPrice(price);
                }
                mergedMap.put(menuId, newOi);
            }
        }

        List<OrderItem> mergedList = new ArrayList<>(mergedMap.values());
        double total = 0.0;
        for (OrderItem oi : mergedList) {
            total += oi.getPrice() * oi.getQuantity();
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("items", mergedList);
        updates.put("totalAmount", total);
        updates.put("finalAmount", total);

        orderRepository.updateOrder(existing.getId(), updates, new OrderRepository.RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order result) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    addQtyMap.clear();
                    Toast.makeText(OrderActivity.this, "Thêm món vào order hiện có thành công", Toast.LENGTH_SHORT).show();
                    hideMenuView();
                    loadExistingOrdersForTable();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnConfirm.setEnabled(true);
                    Toast.makeText(OrderActivity.this, "Không thể cập nhật order: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void createNewOrderFromAddMap() {
        List<OrderItem> items = new ArrayList<>();
        double total = 0.0;
        for (Map.Entry<String, Integer> e : addQtyMap.entrySet()) {
            String menuId = e.getKey();
            int qty = e.getValue();
            // Safely try to get menu info from adapter without assuming method exists
            MenuItem mi = getMenuItemFromAdapter(menuId);
            String name = mi != null ? mi.getName() : "";
            double price = mi != null ? mi.getPrice() : 0.0;
            OrderItem oi;
            try {
                oi = new OrderItem(menuId, name, qty, price);
            } catch (Exception ex) {
                oi = new OrderItem();
                oi.setMenuItemId(menuId);
                oi.setName(name);
                oi.setQuantity(qty);
                oi.setPrice(price);
            }
            items.add(oi);
            total += price * qty;
        }

        Order order = new Order();
        order.setTableNumber(tableNumber);
        order.setItems(items);
        order.setTotalAmount(total);
        order.setDiscount(0);
        order.setFinalAmount(total);
        order.setPaidAmount(0);
        order.setChange(0);
        order.setServerId(fakeServerId);
        order.setCashierId(fakeCashierId);
        order.setPaymentMethod("cash");
        order.setOrderStatus("pending");

        orderRepository.createOrder(order, new OrderRepository.RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order result) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(OrderActivity.this, "Thêm món thành công", Toast.LENGTH_SHORT).show();
                    addQtyMap.clear();
                    hideMenuView();
                    loadExistingOrdersForTable();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnConfirm.setEnabled(true);
                    Toast.makeText(OrderActivity.this, "Lỗi thêm món: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Helper: safely get MenuItem info from MenuAdapter without assuming adapter has a direct API.
     * Tries reflection to call findById or getItems if available; otherwise returns null.
     */
    @SuppressWarnings("unchecked")
    private MenuItem getMenuItemFromAdapter(String menuId) {
        if (menuAdapter == null || menuId == null) return null;
        // Try direct method findById if present
        try {
            java.lang.reflect.Method m = menuAdapter.getClass().getMethod("findById", String.class);
            Object res = m.invoke(menuAdapter, menuId);
            if (res instanceof MenuItem) return (MenuItem) res;
        } catch (NoSuchMethodException nsme) {
            // ignore and try other options
        } catch (Exception e) {
            Log.w(TAG, "getMenuItemFromAdapter: reflection findById failed: " + e.getMessage());
        }

        // Try getItems() method to iterate items
        try {
            java.lang.reflect.Method gi = menuAdapter.getClass().getMethod("getItems");
            Object list = gi.invoke(menuAdapter);
            if (list instanceof List) {
                for (Object o : (List) list) {
                    if (o instanceof MenuItem) {
                        MenuItem mi = (MenuItem) o;
                        if (menuId.equals(mi.getId())) return mi;
                    }
                }
            }
        } catch (NoSuchMethodException nsme) {
            // getItems not available - fall through
        } catch (Exception e) {
            Log.w(TAG, "getMenuItemFromAdapter: reflection getItems failed: " + e.getMessage());
        }

        // As last resort, try to access internal fields via reflection (qtyMap/items) - not recommended but fallback
        try {
            java.lang.reflect.Field itemsField = menuAdapter.getClass().getDeclaredField("items");
            itemsField.setAccessible(true);
            Object itemsObj = itemsField.get(menuAdapter);
            if (itemsObj instanceof List) {
                for (Object o : (List) itemsObj) {
                    if (o instanceof MenuItem) {
                        MenuItem mi = (MenuItem) o;
                        if (menuId.equals(mi.getId())) return mi;
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore - no further fallback
        }

        return null;
    }

    // ----- The rest of table-related methods (fetchTablesFromServer, syncTableStatusesWithOrders, showTransferDialog, etc.)
    // If you have the MainActivity's table logic in this file or want to reuse, you can copy those helpers here.
    // For this OrderActivity we keep it focused on menu/order operations.

}