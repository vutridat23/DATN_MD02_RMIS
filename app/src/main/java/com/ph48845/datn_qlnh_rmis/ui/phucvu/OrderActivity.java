package com.ph48845.datn_qlnh_rmis.ui.phucvu;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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
import com.ph48845.datn_qlnh_rmis.data.repository.MenuRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
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
 * Slimmed down OrderActivity. UI wiring only; delegates socket, long-press and order operations.
 *
 * Persist both menu notes and cancel-notes separately. MenuLongPressHandler uses getNoteForMenu/putNoteForMenu
 * with plain menuId; OrderAdapter uses getNoteForMenu/putNoteForMenu with key "cancel:<id>" for cancel reasons.
 */
public class OrderActivity extends AppCompatActivity implements MenuAdapter.OnMenuClickListener,
        OrderSocketHandler.Listener, MenuLongPressHandler.NoteStore {

    private static final String TAG = "OrderActivity";

    private RecyclerView rvOrderedList;
    private RecyclerView rvMenuList;
    private ProgressBar progressBar;
    private TextView tvTable;
    private TextView tvTotal;
    private ImageView imgBack;
    private Button btnAddMore;
    private Button btnConfirm;
    private View redDot;

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
        setContentView(R.layout.activity_order);

        rvOrderedList = findViewById(R.id.rv_ordered_list);
        rvMenuList = findViewById(R.id.rv_menu_list);
        progressBar = findViewById(R.id.progress_bar_order);
        tvTable = findViewById(R.id.tv_table_label);
        tvTotal = findViewById(R.id.tv_total_amount_ordered);
        btnAddMore = findViewById(R.id.btn_add_more);
        btnConfirm = findViewById(R.id.btn_confirm_order);
        imgBack = findViewById(R.id.btn_back);
        redDot = findViewById(R.id.redDot); // if present in layout

        if (imgBack != null) {
            imgBack.setOnClickListener(v -> {
                Intent intent = new Intent(OrderActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            });
        }

        // load persisted notes (both normal notes and cancel notes) before anything
        loadNotesFromPrefs();

        menuRepository = new MenuRepository();
        orderRepository = new OrderRepository();
        tableRepository = new TableRepository();

        tableId = getIntent().getStringExtra("tableId");
        tableNumber = getIntent().getIntExtra("tableNumber", 0);
        if (tvTable != null) tvTable.setText("Bàn " + tableNumber);

        String extraSocket = getIntent().getStringExtra("socketUrl");
        if (extraSocket != null && !extraSocket.trim().isEmpty()) socketUrl = extraSocket.trim();

        rvOrderedList.setLayoutManager(new LinearLayoutManager(this));
        // Pass 'this' as NoteStore so adapter can prefill/save cancel reasons into cancelNotesMap via prefix "cancel:"
        orderedAdapter = new OrderAdapter(new ArrayList<>(), item -> {
            if (!isFinishing() && !isDestroyed()) {
                runOnUiThread(() -> Toast.makeText(OrderActivity.this, "Món: " + item.getName(), Toast.LENGTH_SHORT).show());
            }
        }, this);
        rvOrderedList.setAdapter(orderedAdapter);

        rvMenuList.setLayoutManager(new LinearLayoutManager(this));
        menuAdapter = new MenuAdapter(new ArrayList<>(), this);
        rvMenuList.setAdapter(menuAdapter);

        // New handlers
        longPressHandler = new MenuLongPressHandler(this, rvMenuList, menuAdapter, this);
        longPressHandler.setup();

        if (btnAddMore != null) btnAddMore.setOnClickListener(v -> showMenuView());
        if (btnConfirm != null) btnConfirm.setOnClickListener(v -> confirmAddItems());

        // socket handler
        socketHandler = new OrderSocketHandler(this, socketUrl, tableNumber, this);
        socketHandler.initAndConnect();

        loadMenuItems();
        loadExistingOrdersForTable();
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (socketHandler != null) socketHandler.disconnect();
        } catch (Exception e) {
            Log.w(TAG, "socket disconnect error", e);
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

    private void loadExistingOrdersForTable() {
        if (tableNumber <= 0) { showMenuView(); return; }
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        if (tableId != null && !tableId.trim().isEmpty()) {
            tableRepository.getTableById(tableId, new TableRepository.RepositoryCallback<TableItem>() {
                @Override
                public void onSuccess(TableItem tableItem) {
                    boolean isOccupied = false;
                    try { isOccupied = tableItem != null && tableItem.getStatus() == TableItem.Status.OCCUPIED; } catch (Exception ignored) {}
                    fetchOrdersForTable(isOccupied);
                }
                @Override
                public void onError(String message) {
                    fetchOrdersForTable(false);
                }
            });
        } else {
            fetchOrdersForTable(false);
        }
    }

    private void fetchOrdersForTable(final boolean tableIsOccupied) {
        orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orders) {
                runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
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
                        if (tableIsOccupied) {
                            if (orderedAdapter != null) orderedAdapter.setItems(new ArrayList<>());
                            if (tvTotal != null) tvTotal.setText("0 VND");
                            Toast.makeText(OrderActivity.this, "Bàn đang có khách nhưng chưa có món. Bạn có thể thêm món.", Toast.LENGTH_LONG).show();
                            hideMenuView();
                        } else {
                            showMenuView();
                        }
                        return;
                    }

                    // Flatten items from all orders, preserving parent order id on each OrderItem
                    List<OrderItem> flattened = new ArrayList<>();
                    for (Order o : filtered) {
                        if (o == null || o.getItems() == null) continue;
                        String orderId = o.getId();
                        for (OrderItem oi : o.getItems()) {
                            if (oi == null) continue;
                            try {
                                oi.normalize();
                                // Ensure we extract cancelReason even if it's stored in nested raw map
                                ensureCancelReasonFromRaw(oi);
                            } catch (Exception ignored) {}
                            // preserve cancelReason and status because oi is the server object
                            oi.setParentOrderId(orderId);
                            flattened.add(oi);
                        }
                    }

                    // Apply locally persisted cancelReasons (cancelNotesMap) if server didn't provide them
                    try {
                        for (OrderItem oi : flattened) {
                            if (oi == null) continue;
                            String menuId = oi.getMenuItemId();
                            String itemId = oi.getId();
                            String saved = null;
                            if (menuId != null && !menuId.isEmpty()) saved = cancelNotesMap.get(menuId);
                            if ((saved == null || saved.isEmpty()) && itemId != null && !itemId.isEmpty()) saved = cancelNotesMap.get(itemId);
                            try {
                                String existing = oi.getCancelReason();
                                if ((existing == null || existing.trim().isEmpty()) && saved != null && !saved.isEmpty()) {
                                    oi.setCancelReason(saved);
                                }
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception ignored) {}

                    if (orderedAdapter != null) orderedAdapter.setItems(flattened);
                    double total = 0.0;
                    for (OrderItem oi : flattened) {
                        try { total += oi.getPrice() * oi.getQuantity(); } catch (Exception ignored) {}
                    }
                    if (tvTotal != null) tvTotal.setText(String.format("%,.0f VND", total));
                    hideMenuView();
                });
            }
            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Toast.makeText(OrderActivity.this, "Lỗi tải đơn hàng: " + message, Toast.LENGTH_LONG).show();
                    showMenuView();
                });
            }
        });
    }

    /**
     * Try to extract cancelReason from possible locations:
     * - direct field (getCancelReason)
     * - nested menuItemRaw map (key: cancelReason, cancel_reason, reason)
     * - fallback: leave as-is
     */
    @SuppressWarnings("unchecked")
    private void ensureCancelReasonFromRaw(OrderItem oi) {
        try {
            if (oi == null) return;
            String cr = null;
            try { cr = oi.getCancelReason(); } catch (Exception ignored) {}
            if (cr != null && !cr.trim().isEmpty()) return; // already present

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
                        oi.setCancelReason(s.trim());
                        return;
                    }
                }
            }

            // Also try reading a raw field on the OrderItem map itself via toString (heuristic)
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
            } catch (Exception ignored) {}

        } catch (Exception ignored) {}
    }

    // MenuAdapter.OnMenuClickListener implementations (including overloads for compatibility)

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

    // Compatibility overloads (NO @Override: prevents "does not override" if interface lacks these)
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
        View v1 = findViewById(R.id.ordered_container);
        View v2 = findViewById(R.id.order_summary);
        View v3 = findViewById(R.id.menu_container);
        if (v1 != null) v1.setVisibility(View.GONE);
        if (v2 != null) v2.setVisibility(View.GONE);
        if (v3 != null) v3.setVisibility(View.VISIBLE);
    }
    private void hideMenuView() {
        View v1 = findViewById(R.id.menu_container);
        View v2 = findViewById(R.id.ordered_container);
        View v3 = findViewById(R.id.order_summary);
        if (v1 != null) v1.setVisibility(View.GONE);
        if (v2 != null) v2.setVisibility(View.VISIBLE);
        if (v3 != null) v3.setVisibility(View.VISIBLE);
    }

    public void confirmAddItems(View view) { confirmAddItems(); }

    private void confirmAddItems() {
        OrderHelper.showConfirmationDialog(this, addQtyMap, notesMap, menuAdapter, (confirmed) -> {
            if (!confirmed) return;
            // proceed: fetch existing orders, then merge/create
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
                                    hideMenuView();
                                    loadExistingOrdersForTable();
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
                        OrderHelper.createNewOrderFromAddMap(addQtyMap, notesMap, menuAdapter, tableNumber, fakeServerId, fakeCashierId, orderRepository, new OrderHelper.OrderCallback() {
                            @Override
                            public void onSuccess() {
                                runOnUiThread(() -> {
                                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                                    Toast.makeText(OrderActivity.this, "Thêm món thành công", Toast.LENGTH_SHORT).show();
                                    addQtyMap.clear();
                                    notesMap.clear();
                                    saveNotesToPrefs();
                                    hideMenuView();
                                    loadExistingOrdersForTable();
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
                        });
                    }
                }

                @Override
                public void onError(String message) {
                    // couldn't fetch -> create new
                    OrderHelper.createNewOrderFromAddMap(addQtyMap, notesMap, menuAdapter, tableNumber, fakeServerId, fakeCashierId, orderRepository, new OrderHelper.OrderCallback() {
                        @Override
                        public void onSuccess() {
                            runOnUiThread(() -> {
                                if (progressBar != null) progressBar.setVisibility(View.GONE);
                                Toast.makeText(OrderActivity.this, "Thêm món thành công", Toast.LENGTH_SHORT).show();
                                addQtyMap.clear();
                                notesMap.clear();
                                saveNotesToPrefs();
                                hideMenuView();
                                loadExistingOrdersForTable();
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
                    });
                }
            });
        });
    }

    /**
     * OrderSocketHandler.Listener implementations
     */
    @Override
    public void onItemStatusMatched(String candidateId, String status) {
        try {
            boolean updated = false;
            if (orderedAdapter != null) updated = orderedAdapter.updateItemStatus(candidateId, status);
            if (!updated) {
                runOnUiThread(this::loadExistingOrdersForTable);
            }
        } catch (Exception e) {
            Log.w(TAG, "onItemStatusMatched error", e);
            runOnUiThread(this::loadExistingOrdersForTable);
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

    /**
     * MenuLongPressHandler.NoteStore implementations
     * (simple local map persisted to prefs). Accepts keys both plain (menu notes)
     * and "cancel:<id>" (cancel reasons).
     */
    @Override
    public String getNoteForMenu(String menuId) {
        if (menuId == null) return "";
        try {
            if (menuId.startsWith("cancel:")) {
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
        } catch (Exception ignored) {}
    }

    // ===== Persistence helpers =====
    private void loadNotesFromPrefs() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String json = prefs.getString(NOTES_KEY, null);
            if (json != null && !json.isEmpty()) {
                JSONObject o = new JSONObject(json);
                Iterator<String> keys = o.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    String v = o.optString(k, "");
                    if (k != null && !k.isEmpty() && v != null) notesMap.put(k, v);
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "loadNotesFromPrefs (notes) failed: " + e.getMessage());
        } catch (Exception ignored) {}

        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String json = prefs.getString(CANCEL_NOTES_KEY, null);
            if (json != null && !json.isEmpty()) {
                JSONObject o = new JSONObject(json);
                Iterator<String> keys = o.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    String v = o.optString(k, "");
                    if (k != null && !k.isEmpty() && v != null) cancelNotesMap.put(k, v);
                }
            }
        } catch (JSONException e) {
            Log.w(TAG, "loadNotesFromPrefs (cancel notes) failed: " + e.getMessage());
        } catch (Exception ignored) {}
    }

    private void saveNotesToPrefs() {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, String> e : notesMap.entrySet()) {
                String k = e.getKey();
                String v = e.getValue();
                if (k != null && !k.isEmpty() && v != null) {
                    o.put(k, v);
                }
            }
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit().putString(NOTES_KEY, o.toString()).apply();
        } catch (JSONException e) {
            Log.w(TAG, "saveNotesToPrefs (notes) failed: " + e.getMessage());
        } catch (Exception ignored) {}

        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, String> e : cancelNotesMap.entrySet()) {
                String k = e.getKey();
                String v = e.getValue();
                if (k != null && !k.isEmpty() && v != null) {
                    o.put(k, v);
                }
            }
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit().putString(CANCEL_NOTES_KEY, o.toString()).apply();
        } catch (JSONException e) {
            Log.w(TAG, "saveNotesToPrefs (cancel notes) failed: " + e.getMessage());
        } catch (Exception ignored) {}
    }
}