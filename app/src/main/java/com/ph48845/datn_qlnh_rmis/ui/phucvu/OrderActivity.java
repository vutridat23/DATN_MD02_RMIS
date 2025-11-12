package com.ph48845.datn_qlnh_rmis.ui.phucvu;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OrderActivity: hiển thị danh sách món đã order (mặc định),
 * nút "Thêm món" chuyển sang chế độ thêm món (hiển thị danh sách menu).
 *
 * Sửa: khi bấm vào bàn đang có khách thì sẽ luôn hiện danh sách các món đã order (nếu có),
 * nếu server trả orders rỗng nhưng bàn có status OCCUPIED thì vẫn show ordered view (với message).
 *
 * Logic:
 *  - Trước hết refetch thông tin bàn (tableId) để biết status hiện tại.
 *  - Sau đó gọi API lấy orders cho bàn; nếu tìm thấy orders -> merge và hiển thị.
 *  - Nếu không có orders:
 *      - nếu bàn hiện là OCCUPIED -> show ordered view (adapter rỗng) và hiển thị Toast "Chưa có món".
 *      - nếu bàn không occupied -> show menu view để thêm món.
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

    // MenuAdapter.OnMenuClickListener
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
}