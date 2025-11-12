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
 * Sửa: nếu bàn đã có order mở thì khi "Thêm món" sẽ cập nhật order tồn tại (merge items)
 * thay vì tạo order mới.
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

    // fake ids for testing (24-hex)
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

    /**
     * Tải order hiện có cho bàn (nếu có) và merge các OrderItem vào ordered list.
     * Sửa: nếu không có order hoặc server trả orders không thuộc bàn này -> showMenuView() để thêm món.
     */
    private void loadExistingOrdersForTable() {
        if (tableNumber <= 0) {
            // Nếu tableNumber không hợp lệ, chuyển thẳng sang menu để thêm món
            showMenuView();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        // Lấy orders của table (server có thể filter theo tableNumber)
        orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orders) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    // Defensive: filter orders whose tableNumber actually equals this.tableNumber
                    List<Order> filtered = new ArrayList<>();
                    if (orders != null) {
                        for (Order o : orders) {
                            if (o == null) continue;
                            if (o.getTableNumber() == tableNumber) filtered.add(o);
                        }
                    }

                    if (filtered.isEmpty()) {
                        // KHÔNG CÓ order thực sự cho bàn này -> show menu để thêm món
                        showMenuView();
                        return;
                    }

                    // merge items from all returned orders into list (sum quantities)
                    List<OrderItem> merged = new ArrayList<>();
                    for (Order o : filtered) {
                        if (o == null || o.getItems() == null) continue;
                        for (Order.OrderItem oi : o.getItems()) {
                            if (oi == null) continue;
                            String menuId = oi.getMenuItemId();
                            if (menuId == null || menuId.trim().isEmpty()) continue;

                            boolean found = false;
                            for (OrderItem ex : merged) {
                                if (ex.getMenuItemId() != null && ex.getMenuItemId().equals(menuId)) {
                                    ex.setQuantity(ex.getQuantity() + oi.getQuantity());
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                // safe copy: try 4-arg constructor, else reuse object
                                try {
                                    merged.add(new OrderItem(menuId, oi.getName(), oi.getQuantity(), oi.getPrice()));
                                } catch (Exception ex) {
                                    merged.add(oi);
                                }
                            }
                        }
                    }

                    // update orderedAdapter and total
                    orderedAdapter.setItems(merged);
                    double total = 0.0;
                    for (OrderItem oi : merged) {
                        total += oi.getPrice() * oi.getQuantity();
                    }
                    tvTotal.setText(String.format("%,.0f VND", total));

                    // ensure ordered view visible
                    hideMenuView();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Log.w(TAG, "Lỗi khi lấy orders cho bàn: " + message);
                    // Trong trường hợp lỗi mạng: để an toàn, show menu để nhân viên có thể thêm món
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
     * Confirm thêm món: nếu bàn đã có order đang mở thì gộp vào order đó (update),
     * ngược lại tạo order mới (create).
     */
    private void confirmAddItems() {
        if (addQtyMap.isEmpty()) {
            Toast.makeText(this, "Chưa chọn món để thêm", Toast.LENGTH_SHORT).show();
            return;
        }

        // disable UI while processing
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
                        // ensure order belongs to this table
                        if (o.getTableNumber() != tableNumber) continue;

                        String s = o.getOrderStatus() != null ? o.getOrderStatus().toLowerCase() : "";
                        if (s.contains("pending") || s.contains("preparing") || s.contains("open") || s.contains("unpaid") || s.isEmpty()) {
                            targetOrder = o;
                            break;
                        }
                    }
                    if (targetOrder == null) {
                        // fallback: pick the most recent order (by createdAt) if no "pending" found
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
                    // merge to existing order and perform update
                    mergeIntoExistingOrderAndUpdate(targetOrder);
                } else {
                    // no existing order -> create new order (fallback to original behavior)
                    createNewOrderFromAddMap();
                }
            }

            @Override
            public void onError(String message) {
                // If cannot fetch orders, fallback to creating a new order to avoid blocking staff
                Log.w(TAG, "Cannot fetch orders to merge: " + message);
                createNewOrderFromAddMap();
            }
        });
    }

    /**
     * Merge addQtyMap into an existing order and call updateOrder.
     */
    private void mergeIntoExistingOrderAndUpdate(Order existing) {
        if (existing == null) {
            createNewOrderFromAddMap();
            return;
        }

        // build map menuId -> OrderItem starting from existing items
        Map<String, OrderItem> mergedMap = new HashMap<>();
        if (existing.getItems() != null) {
            for (OrderItem oi : existing.getItems()) {
                if (oi == null) continue;
                String mid = oi.getMenuItemId();
                if (mid == null) continue;
                try {
                    // copy to avoid mutating original model
                    OrderItem copy = new OrderItem(mid, oi.getName(), oi.getQuantity(), oi.getPrice());
                    mergedMap.put(mid, copy);
                } catch (Exception ex) {
                    mergedMap.put(mid, oi);
                }
            }
        }

        // merge in new items from addQtyMap
        for (Map.Entry<String, Integer> e : addQtyMap.entrySet()) {
            String menuId = e.getKey();
            int addQty = e.getValue();
            if (menuId == null || addQty <= 0) continue;

            OrderItem existingOi = mergedMap.get(menuId);
            if (existingOi != null) {
                existingOi.setQuantity(existingOi.getQuantity() + addQty);
            } else {
                // find menu info from menuAdapter (if available)
                MenuItem mi = menuAdapter.findById(menuId);
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

        // build merged list and compute totals
        List<OrderItem> mergedList = new ArrayList<>(mergedMap.values());
        double total = 0.0;
        for (OrderItem oi : mergedList) {
            total += oi.getPrice() * oi.getQuantity();
        }

        // prepare updates map for partial update
        Map<String, Object> updates = new HashMap<>();
        updates.put("items", mergedList);
        updates.put("totalAmount", total);
        updates.put("finalAmount", total);
        // you can add other fields to update if your backend expects them (e.g., discount, paidAmount)

        // call updateOrder
        orderRepository.updateOrder(existing.getId(), updates, new OrderRepository.RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order result) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    addQtyMap.clear();
                    Toast.makeText(OrderActivity.this, "Thêm món vào order hiện có thành công", Toast.LENGTH_SHORT).show();
                    hideMenuView();
                    // reload orders to refresh UI
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

    /**
     * Create a new order from addQtyMap (original behavior).
     */
    private void createNewOrderFromAddMap() {
        List<OrderItem> items = new ArrayList<>();
        double total = 0.0;
        for (Map.Entry<String, Integer> e : addQtyMap.entrySet()) {
            String menuId = e.getKey();
            int qty = e.getValue();
            MenuItem mi = menuAdapter.findById(menuId);
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
}