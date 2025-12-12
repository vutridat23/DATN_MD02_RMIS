package com.ph48845.datn_qlnh_rmis.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Build;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;
import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.core.base.BaseMenuActivity;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter.TableAdapter;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.OrderActivity;
import com.ph48845.datn_qlnh_rmis.ui.table.MergeManager;
import com.ph48845.datn_qlnh_rmis.ui.table.ReservationHelper;
import com.ph48845.datn_qlnh_rmis.ui.table.TableActionsHandler;
import com.ph48845.datn_qlnh_rmis.ui.table.TransferManager;
import com.ph48845.datn_qlnh_rmis.ui.table.TemporaryBillDialogFragment;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.socket.SocketManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MainActivity (rút gọn): setup UI, load data and listen for socket table events.
 *
 * IMPORTANT: This version delegates the popup menu handling to TableActionsHandler.
 * - onTableLongClick -> tableActionsHandler.showTableActionsMenuForLongPress(...)
 * Other features unchanged.
 */
public class MainActivity extends BaseMenuActivity {

    private static final String TAG = "MainActivityHome";

    ProgressBar progressBar;
    private RecyclerView rvFloor1;
    private RecyclerView rvFloor2;
    private TableAdapter adapterFloor1;
    private TableAdapter adapterFloor2;
    TableRepository tableRepository;
    OrderRepository orderRepository;

    private TransferManager transferManager;
    private MergeManager mergeManager;
    private ReservationHelper reservationHelper;
    private TableActionsHandler tableActionsHandler;
    DrawerLayout drawerLayout;
    NavigationView navigationView;

    private SocketManager socketManager;
    // Default socket URL: your server IP
    private String defaultSocketUrl = "http://192.168.1.84:3000";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // views
        progressBar = findViewById(R.id.progress_bar_loading);
        rvFloor1 = findViewById(R.id.recycler_floor1);
        rvFloor2 = findViewById(R.id.recycler_floor2);

        rvFloor1.setLayoutManager(new GridLayoutManager(this, 3));
        rvFloor2.setLayoutManager(new GridLayoutManager(this, 3));
        rvFloor1.setNestedScrollingEnabled(false);
        rvFloor2.setNestedScrollingEnabled(false);

        drawerLayout = findViewById(R.id.drawerLayout_order);
        Toolbar toolbar = findViewById(R.id.toolbar);
        navigationView = findViewById(R.id.navigationView_order);


        ImageView navIcon = findViewById(R.id.nav_icon);
        if (navIcon != null && drawerLayout != null) {
            navIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        if (toolbar != null && drawerLayout != null) {
            toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        // navigation menu style
        if (navigationView != null) {
            try {
                for (int i = 0; i < navigationView.getMenu().size(); i++) {
                    MenuItem menuItem = navigationView.getMenu().getItem(i);
                    SpannableString spanString = new SpannableString(menuItem.getTitle().toString());
                    spanString.setSpan(new RelativeSizeSpan(1.1f), 0, spanString.length(), 0);
                    menuItem.setTitle(spanString);
                }
            } catch (Exception e) {
                Log.w(TAG, "Unable to modify navigation menu items: " + e.getMessage(), e);
            }

            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_mood) {
                    showMoodDialog();
                } else if (id == R.id.nav_contact) {
                    showContactDialog();
                } else if (id == R.id.nav_logout) {
                    logout();
                }
                if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            });
        } else {
            Log.w(TAG, "navigationView is null - check activity_main.xml: id should be navigationView_order");
        }

        updateNavHeaderInfo();

        tableRepository = new TableRepository();
        orderRepository = new OrderRepository();

        transferManager = new TransferManager(this, tableRepository, orderRepository, progressBar);
        mergeManager = new MergeManager(this, tableRepository, orderRepository, progressBar);
        reservationHelper = new ReservationHelper(this, tableRepository, progressBar);
        tableActionsHandler = new TableActionsHandler(this, transferManager, mergeManager, reservationHelper);

        // register temporary bill handler
        tableActionsHandler.setTemporaryBillRequester(table -> {
            if (table == null) return;
            TemporaryBillDialogFragment f = TemporaryBillDialogFragment.newInstance(table, updatedOrder -> fetchTablesFromServer());
            f.show(getSupportFragmentManager(), "tempBill");
        });

        // adapters and helpers
        TableAdapter.OnTableClickListener listener = new TableAdapter.OnTableClickListener() {
            @Override
            public void onTableClick(View v, TableItem table) {
                if (table == null) return;
                Intent intent = new Intent(MainActivity.this, OrderActivity.class);
                intent.putExtra("tableId", table.getId());
                intent.putExtra("tableNumber", table.getTableNumber());
                boolean isCustomerPresent = false;
                try {
                    TableItem.Status st = table.getStatus();
                    if (st == TableItem.Status.OCCUPIED || st == TableItem.Status.PENDING_PAYMENT) isCustomerPresent = true;
                } catch (Exception ignored) {}
                intent.putExtra("forceShowOrders", isCustomerPresent);
                startActivity(intent);
            }

            @Override
            public void onTableLongClick(View v, TableItem table) {
                if (table == null) return;
                // Delegate to TableActionsHandler which shows the popup and calls managers
                tableActionsHandler.showTableActionsMenuForLongPress(v, table);
            }
        };

        adapterFloor1 = new TableAdapter(this, new ArrayList<>(), listener);
        adapterFloor2 = new TableAdapter(this, new ArrayList<>(), listener);
        rvFloor1.setAdapter(adapterFloor1);
        rvFloor2.setAdapter(adapterFloor2);

        // Determine socket URL (intent override possible)
        String socketUrl = getIntent().getStringExtra("socketUrl");
        if (socketUrl == null || socketUrl.trim().isEmpty()) socketUrl = defaultSocketUrl;

        // If running on emulator, try the special emulator host (10.0.2.2)
        if (isProbablyEmulator()) {
            try {
                String replaced = replaceHostForEmulator(socketUrl);
                Log.i(TAG, "Emulator detected - using socket URL: " + replaced + " (original: " + socketUrl + ")");
                socketUrl = replaced;
            } catch (Exception e) {
                Log.w(TAG, "Failed to adapt socketUrl for emulator: " + e.getMessage(), e);
            }
        } else {
            Log.i(TAG, "Using socket URL: " + socketUrl);
        }

        // Initialize socket to receive live table updates
        try {
            socketManager = SocketManager.getInstance();
            socketManager.init(socketUrl);
            socketManager.setOnEventListener(new SocketManager.OnEventListener() {
                @Override public void onOrderCreated(JSONObject payload) {}
                @Override public void onOrderUpdated(JSONObject payload) {}
                @Override public void onConnect() { Log.d(TAG, "socket connected (main)"); }
                @Override public void onDisconnect() { Log.d(TAG, "socket disconnected (main)"); }
                @Override public void onError(Exception e) { Log.w(TAG, "socket error (main): " + (e != null ? e.getMessage() : "null")); }
                @Override
                public void onTableUpdated(JSONObject payload) {
                    if (payload != null) {
                        String evt = payload.optString("eventName", "");
                        if ("table_auto_released".equals(evt)) {
                            int tblNum = -1;
                            if (payload.has("tableNumber")) tblNum = payload.optInt("tableNumber", -1);
                            else if (payload.has("table")) tblNum = payload.optInt("table", -1);
                            final int shownNum = tblNum;
                            runOnUiThread(() -> {
                                try {
                                    String msg = "Bàn " + (shownNum > 0 ? shownNum : "") + " đã tự động hủy đặt trước.";
                                    new AlertDialog.Builder(MainActivity.this)
                                            .setTitle("Thông báo")
                                            .setMessage(msg)
                                            .setCancelable(false)
                                            .setPositiveButton("OK", (d, w) -> {
                                                d.dismiss();
                                                fetchTablesFromServer();
                                            })
                                            .show();
                                } catch (Exception ex) {
                                    Log.w(TAG, "show auto-release dialog failed", ex);
                                    fetchTablesFromServer();
                                }
                            });
                            return;
                        }
                    }
                    // default: refresh list
                    runOnUiThread(() -> fetchTablesFromServer());
                }
            });
            socketManager.connect();
        } catch (Exception e) {
            Log.w(TAG, "Failed to init socket in MainActivity: " + e.getMessage(), e);
        }
        applyNavigationViewInsets();

        // initial load
        fetchTablesFromServer();
    }

    private void applyNavigationViewInsets() {
        if (navigationView == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (view, insets) -> {

            int statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;

            // Lấy header của NavigationView
            View header = navigationView.getHeaderView(0);
            if (header != null) {
                header.setPadding(
                        header.getPaddingLeft(),
                        statusBar,   // ĐẨY XUỐNG ĐỂ TRÁNH DÍNH STATUS BAR
                        header.getPaddingRight(),
                        header.getPaddingBottom()
                );
            }

            return insets;
        });
    }

    private boolean isProbablyEmulator() {
        String fingerprint = Build.FINGERPRINT;
        String model = Build.MODEL;
        String product = Build.PRODUCT;
        return fingerprint != null && (fingerprint.contains("generic") || fingerprint.contains("emulator"))
                || model != null && model.contains("Emulator")
                || product != null && product.contains("sdk");
    }

    private String replaceHostForEmulator(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
            int port = uri.getPort();
            String path = uri.getRawPath() != null ? uri.getRawPath() : "";
            String query = uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "";
            String newHost = "10.0.2.2";
            String newUrl;
            if (port > 0) newUrl = scheme + "://" + newHost + ":" + port + path + query;
            else newUrl = scheme + "://" + newHost + path + query;
            return newUrl;
        } catch (Exception e) {
            if (url.startsWith("http://localhost")) return url.replace("localhost", "10.0.2.2");
            if (url.startsWith("http://127.0.0.1")) return url.replace("127.0.0.1", "10.0.2.2");
            return url;
        }
    }

    private void updateNavHeaderInfo() {
        if (navigationView == null) {
            Log.w(TAG, "updateNavHeaderInfo: navigationView is null, skip updating header");
            return;
        }

        try {
            View headerView = navigationView.getHeaderView(0);
            if (headerView == null) {
                Log.w(TAG, "NavigationView headerView is null");
                return;
            }

            TextView tvName = headerView.findViewById(R.id.textViewName);
            TextView tvRole = headerView.findViewById(R.id.textViewRole);

            SharedPreferences prefs = getSharedPreferences("RestaurantPrefs", MODE_PRIVATE);

            String savedName = prefs.getString("fullName", "Người dùng");
            String savedRole = prefs.getString("userRole", "");

            if (tvName != null) tvName.setText(savedName);
            if (tvRole != null) tvRole.setText(getVietnameseRole(savedRole));
        } catch (Exception e) {
            Log.w(TAG, "updateNavHeaderInfo failed: " + e.getMessage(), e);
        }
    }

    private String getVietnameseRole(String roleKey) {
        if (roleKey == null) return "";
        switch (roleKey.toLowerCase()) {
            case "cashier":
                return "Thu ngân";
            case "manager":
            case "order":
                return "Phục vụ";
            case "kitchen":
                return "Bếp";
            default:
                return roleKey;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try { if (socketManager != null) socketManager.connect(); } catch (Exception e) { Log.w(TAG, "socket connect onResume failed", e); }
        fetchTablesFromServer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { if (socketManager != null) socketManager.disconnect(); } catch (Exception e) { Log.w(TAG, "socket disconnect onPause failed", e); }
    }

    public void fetchTablesFromServer() {
        if (progressBar != null) progressBar.setVisibility(ProgressBar.VISIBLE);
        tableRepository.getAllTables(new TableRepository.RepositoryCallback<List<TableItem>>() {
            @Override
            public void onSuccess(List<TableItem> result) {
                runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(ProgressBar.GONE);
                    if (result == null || result.isEmpty()) {
                        adapterFloor1.updateList(new ArrayList<>());
                        adapterFloor2.updateList(new ArrayList<>());
                        return;
                    }

                    for (TableItem t : result) if (t != null && t.getLocation() == null) t.setLocation("");

                    List<TableItem> floor1 = new ArrayList<>();
                    List<TableItem> floor2 = new ArrayList<>();
                    for (TableItem t : result) {
                        int floor = parseFloorFromLocation(t.getLocation());
                        if (floor == 2) floor2.add(t); else floor1.add(t);
                    }

                    Comparator<TableItem> byNumber = (a, b) -> {
                        if (a == null && b == null) return 0;
                        if (a == null) return 1;
                        if (b == null) return -1;
                        try { return Integer.compare(a.getTableNumber(), b.getTableNumber()); }
                        catch (Exception e) { return String.valueOf(a.getTableNumber()).compareTo(String.valueOf(b.getTableNumber())); }
                    };
                    Collections.sort(floor1, byNumber);
                    Collections.sort(floor2, byNumber);

                    adapterFloor1.updateList(floor1);
                    adapterFloor2.updateList(floor2);

                    List<TableItem> all = new ArrayList<>();
                    all.addAll(floor1);
                    all.addAll(floor2);
                    syncTableStatusesWithOrders(all);
                });
            }
            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(ProgressBar.GONE);
                    Toast.makeText(MainActivity.this, "Lỗi tải danh sách bàn: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private int parseFloorFromLocation(String location) {
        if (location == null) return 1;
        try {
            String lower = location.toLowerCase(Locale.getDefault());
            Pattern p = Pattern.compile("(\\d+)");
            Matcher m = p.matcher(lower);
            if (m.find()) return Integer.parseInt(m.group(1));
        } catch (Exception ignored) {}
        return 1;
    }

    private void syncTableStatusesWithOrders(List<TableItem> tables) {
        if (tables == null || tables.isEmpty()) return;
        orderRepository.getOrdersByTableNumber(null, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orders) {
                final java.util.Set<Integer> occupiedTableNumbers = new java.util.HashSet<>();
                if (orders != null) {
                    for (Order o : orders) if (o != null) occupiedTableNumbers.add(o.getTableNumber());
                }
                List<TableItem> toUpdate = new ArrayList<>();
                final List<String> desired = new ArrayList<>();
                for (TableItem t : tables) {
                    if (t == null) continue;
                    boolean isReserved = false;
                    try { isReserved = t.getStatus() == TableItem.Status.RESERVED; } catch (Exception ignored) {}
                    if (isReserved) continue;
                    String cur = t.getStatus() != null ? t.getStatus().name().toLowerCase() : "";
                    String want = occupiedTableNumbers.contains(t.getTableNumber()) ? "occupied" : "available";
                    if (!cur.equals(want)) { toUpdate.add(t); desired.add(want); }
                }
                if (toUpdate.isEmpty()) return;
                final int total = toUpdate.size();
                final int[] finished = {0};
                for (int i = 0; i < toUpdate.size(); i++) {
                    TableItem ti = toUpdate.get(i);
                    String want = desired.get(i);
                    tableRepository.updateTableStatus(ti.getId(), want, new TableRepository.RepositoryCallback<TableItem>() {
                        @Override
                        public void onSuccess(TableItem updated) { finished[0]++; if (finished[0] >= total) runOnUiThread(() -> fetchTablesFromServer()); }
                        @Override
                        public void onError(String message) { finished[0]++; if (finished[0] >= total) runOnUiThread(() -> fetchTablesFromServer()); }
                    });
                }
            }
            @Override
            public void onError(String message) { Log.w(TAG, "sync orders error: " + message); }
        });
    }
}