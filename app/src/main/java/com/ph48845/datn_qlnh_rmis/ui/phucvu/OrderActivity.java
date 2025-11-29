package com.ph48845.datn_qlnh_rmis.ui.phucvu;

import android.content.Intent;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Slimmed down OrderActivity. UI wiring only; delegates socket, long-press and order operations.
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

    private String tableId;
    private int tableNumber;

    private final String fakeServerId = "64a7f3b2c9d1e2f3a4b5c6d7";
    private final String fakeCashierId = "64b8e4c3d1f2a3b4c5d6e7f8";

    // Handlers
    private OrderSocketHandler socketHandler;
    private MenuLongPressHandler longPressHandler;

    // default socket url (can be overridden via intent)
    private String socketUrl = "http://192.168.1.84:3000";

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

        imgBack.setOnClickListener(v -> {
            Intent intent = new Intent(OrderActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        });

        menuRepository = new MenuRepository();
        orderRepository = new OrderRepository();
        tableRepository = new TableRepository();

        tableId = getIntent().getStringExtra("tableId");
        tableNumber = getIntent().getIntExtra("tableNumber", 0);
        tvTable.setText("Bàn " + tableNumber);

        String extraSocket = getIntent().getStringExtra("socketUrl");
        if (extraSocket != null && !extraSocket.trim().isEmpty()) socketUrl = extraSocket.trim();

        rvOrderedList.setLayoutManager(new LinearLayoutManager(this));
        orderedAdapter = new OrderAdapter(new ArrayList<>(), item -> {
            if (!isFinishing() && !isDestroyed()) {
                runOnUiThread(() -> Toast.makeText(OrderActivity.this, "Món: " + item.getName(), Toast.LENGTH_SHORT).show());
            }
        });
        rvOrderedList.setAdapter(orderedAdapter);

        rvMenuList.setLayoutManager(new LinearLayoutManager(this));
        menuAdapter = new MenuAdapter(new ArrayList<>(), this);
        rvMenuList.setAdapter(menuAdapter);

        // New handlers
        longPressHandler = new MenuLongPressHandler(this, rvMenuList, menuAdapter, this);
        longPressHandler.setup();

        btnAddMore.setOnClickListener(v -> showMenuView());
        btnConfirm.setOnClickListener(v -> confirmAddItems());

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
        progressBar.setVisibility(View.VISIBLE);
        menuRepository.getAllMenuItems(new MenuRepository.RepositoryCallback<List<MenuItem>>() {
            @Override
            public void onSuccess(List<MenuItem> data) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    menuAdapter.setItems(data != null ? data : new ArrayList<>());
                    for (Map.Entry<String, Integer> e : addQtyMap.entrySet()) {
                        menuAdapter.setQty(e.getKey(), e.getValue());
                    }
                });
            }
            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(OrderActivity.this, "Lỗi tải menu: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void loadExistingOrdersForTable() {
        if (tableNumber <= 0) { showMenuView(); return; }
        progressBar.setVisibility(View.VISIBLE);

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
                    progressBar.setVisibility(View.GONE);
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
                            orderedAdapter.setItems(new ArrayList<>());
                            tvTotal.setText("0 VND");
                            Toast.makeText(OrderActivity.this, "Bàn đang có khách nhưng chưa có món. Bạn có thể thêm món.", Toast.LENGTH_LONG).show();
                            hideMenuView();
                        } else {
                            showMenuView();
                        }
                        return;
                    }

                    // Merge items by id/name but preserve server OrderItem objects (so note remains)
                    List<OrderItem> merged = new ArrayList<>();
                    for (Order o : filtered) {
                        if (o == null || o.getItems() == null) continue;
                        for (OrderItem oi : o.getItems()) {
                            if (oi == null) continue;
                            try { oi.normalize(); } catch (Exception ignored) {}
                            boolean found = false;
                            String menuId = oi.getMenuItemId();
                            if (menuId != null && !menuId.isEmpty()) {
                                for (OrderItem ex : merged) {
                                    if (ex == null) continue;
                                    if (menuId.equals(ex.getMenuItemId())) {
                                        ex.setQuantity(ex.getQuantity() + oi.getQuantity());
                                        found = true;
                                        break;
                                    }
                                }
                            } else {
                                String keyName = oi.getMenuItemName() != null && !oi.getMenuItemName().isEmpty() ? oi.getMenuItemName() : oi.getName();
                                if (keyName != null && !keyName.isEmpty()) {
                                    for (OrderItem ex : merged) {
                                        if (ex == null) continue;
                                        String exName = ex.getMenuItemName() != null && !ex.getMenuItemName().isEmpty() ? ex.getMenuItemName() : ex.getName();
                                        if (exName != null && exName.equalsIgnoreCase(keyName)) {
                                            ex.setQuantity(ex.getQuantity() + oi.getQuantity());
                                            found = true;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (!found) merged.add(oi);
                        }
                    }

                    orderedAdapter.setItems(merged);
                    double total = 0.0;
                    for (OrderItem oi : merged) total += oi.getPrice() * oi.getQuantity();
                    tvTotal.setText(String.format("%,.0f VND", total));
                    hideMenuView();
                });
            }
            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(OrderActivity.this, "Lỗi tải đơn hàng: " + message, Toast.LENGTH_LONG).show();
                    showMenuView();
                });
            }
        });
    }

    @Override
    public void onAddMenuItem(MenuItem menu) {
        if (menu == null) return;
        int cur = addQtyMap.getOrDefault(menu.getId(), 0) + 1;
        addQtyMap.put(menu.getId(), cur);
        menuAdapter.setQty(menu.getId(), cur);
        btnConfirm.setEnabled(!addQtyMap.isEmpty());
    }

    @Override
    public void onRemoveMenuItem(MenuItem menu) {
        if (menu == null) return;
        int cur = addQtyMap.getOrDefault(menu.getId(), 0);
        if (cur > 0) {
            cur--;
            if (cur == 0) addQtyMap.remove(menu.getId());
            else addQtyMap.put(menu.getId(), cur);
            menuAdapter.setQty(menu.getId(), cur);
        }
        btnConfirm.setEnabled(!addQtyMap.isEmpty());
    }

    private void showMenuView() { findViewById(R.id.ordered_container).setVisibility(View.GONE); findViewById(R.id.order_summary).setVisibility(View.GONE); findViewById(R.id.menu_container).setVisibility(View.VISIBLE); }
    private void hideMenuView() { findViewById(R.id.menu_container).setVisibility(View.GONE); findViewById(R.id.ordered_container).setVisibility(View.VISIBLE); findViewById(R.id.order_summary).setVisibility(View.VISIBLE); }

    public void confirmAddItems(View view) { confirmAddItems(); }

    private void confirmAddItems() {
        OrderHelper.showConfirmationDialog(this, addQtyMap, notesMap, menuAdapter, (confirmed) -> {
            if (!confirmed) return;
            // proceed: fetch existing orders, then merge/create
            runOnUiThread(() -> {
                progressBar.setVisibility(View.VISIBLE);
                btnConfirm.setEnabled(false);
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
                                    progressBar.setVisibility(View.GONE);
                                    addQtyMap.clear();
                                    notesMap.clear();
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
                    } else {
                        OrderHelper.createNewOrderFromAddMap(addQtyMap, notesMap, menuAdapter, tableNumber, fakeServerId, fakeCashierId, orderRepository, new OrderHelper.OrderCallback() {
                            @Override
                            public void onSuccess() {
                                runOnUiThread(() -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(OrderActivity.this, "Thêm món thành công", Toast.LENGTH_SHORT).show();
                                    addQtyMap.clear();
                                    notesMap.clear();
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
                }

                @Override
                public void onError(String message) {
                    // couldn't fetch -> create new
                    OrderHelper.createNewOrderFromAddMap(addQtyMap, notesMap, menuAdapter, tableNumber, fakeServerId, fakeCashierId, orderRepository, new OrderHelper.OrderCallback() {
                        @Override
                        public void onSuccess() {
                            runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(OrderActivity.this, "Thêm món thành công", Toast.LENGTH_SHORT).show();
                                addQtyMap.clear();
                                notesMap.clear();
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
            });
        });
    }

    /**
     * OrderSocketHandler.Listener implementations
     */
    @Override
    public void onItemStatusMatched(String candidateId, String status) {
        // Try update adapter by candidateId; if adapter can't update, request reload
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
     * (simple local map)
     */
    @Override
    public String getNoteForMenu(String menuId) {
        return notesMap.getOrDefault(menuId, "");
    }

    @Override
    public void putNoteForMenu(String menuId, String note) {
        if (menuId == null) return;
        if (note == null || note.isEmpty()) notesMap.remove(menuId);
        else notesMap.put(menuId, note);
    }
}