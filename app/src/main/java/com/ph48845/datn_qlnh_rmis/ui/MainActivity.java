package com.ph48845.datn_qlnh_rmis. ui;

import android.content. Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Build;
import android.text.SpannableString;
import android. text.style.RelativeSizeSpan;
import android.util.Log;
import android.view. LayoutInflater;
import android. view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget. ImageView;
import android.widget. ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx. core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;
import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.core.base.BaseMenuActivity;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter.TableAdapter;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter.TempCalculationListAdapter;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter.CheckItemsListAdapter;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com. ph48845.datn_qlnh_rmis.data. repository.TableRepository;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.OrderActivity;
import com.ph48845.datn_qlnh_rmis.ui.table.MergeManager;
import com.ph48845.datn_qlnh_rmis.ui.table.ReservationHelper;
import com.ph48845.datn_qlnh_rmis.ui.table.TableActionsHandler;
import com.ph48845.datn_qlnh_rmis.ui.table.TransferManager;
import com.ph48845.datn_qlnh_rmis.ui.table. TemporaryBillDialogFragment;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.socket.SocketManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util. Comparator;
import java.util.HashMap;
import java.util.List;
import java.util. Locale;
import java.util. Map;
import java.util. regex.Matcher;
import java. util.regex.Pattern;

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
    private String defaultSocketUrl = "http://192.168.1.84:3000";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        progressBar = findViewById(R.id. progress_bar_loading);
        rvFloor1 = findViewById(R.id. recycler_floor1);
        rvFloor2 = findViewById(R.id. recycler_floor2);

        rvFloor1.setLayoutManager(new GridLayoutManager(this, 3));
        rvFloor2.setLayoutManager(new GridLayoutManager(this, 3));
        rvFloor1.setNestedScrollingEnabled(false);
        rvFloor2.setNestedScrollingEnabled(false);

        drawerLayout = findViewById(R.id.drawerLayout_order);
        Toolbar toolbar = findViewById(R.id.toolbar);
        navigationView = findViewById(R.id. navigationView_order);

        ImageView navIcon = findViewById(R.id.nav_icon);
        if (navIcon != null && drawerLayout != null) {
            navIcon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }

        if (toolbar != null && drawerLayout != null) {
            toolbar.setNavigationOnClickListener(new View. OnClickListener() {
                @Override
                public void onClick(View v) {
                    drawerLayout.openDrawer(GravityCompat.START);
                }
            });
        }

        if (navigationView != null) {
            try {
                for (int i = 0; i < navigationView.getMenu().size(); i++) {
                    MenuItem menuItem = navigationView.getMenu().getItem(i);
                    SpannableString spanString = new SpannableString(menuItem.getTitle().toString());
                    spanString.setSpan(new RelativeSizeSpan(1.1f), 0, spanString.length(), 0);
                    menuItem.setTitle(spanString);
                }
            } catch (Exception e) {
                Log.w(TAG, "Unable to modify navigation menu items:  " + e.getMessage(), e);
            }

            navigationView.setNavigationItemSelectedListener(new NavigationView. OnNavigationItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(MenuItem item) {
                    int id = item.getItemId();

                    if (id == R. id.nav_mood) {
                        showMoodDialog();
                    } else if (id == R.id.nav_contact) {
                        showContactDialog();
                    } else if (id == R.id.nav_logout) {
                        logout();
                    } else if (id == R.id.nav_pre_bill) {
                        showTempCalculationRequests();
                    } else if (id == R.id.nav_check_items_requests) {
                        showCheckItemsRequests();
                    }

                    if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                    return true;
                }
            });
        } else {
            Log.w(TAG, "navigationView is null");
        }

        updateNavHeaderInfo();

        tableRepository = new TableRepository();
        orderRepository = new OrderRepository();

        transferManager = new TransferManager(this, tableRepository, orderRepository, progressBar);
        mergeManager = new MergeManager(this, tableRepository, orderRepository, progressBar);
        reservationHelper = new ReservationHelper(this, tableRepository, progressBar);
        tableActionsHandler = new TableActionsHandler(this, transferManager, mergeManager, reservationHelper);

        tableActionsHandler.setTemporaryBillRequester(new TableActionsHandler.TemporaryBillRequester() {
            @Override
            public void requestTemporaryBill(TableItem table) {
                if (table == null) return;
                TemporaryBillDialogFragment f = TemporaryBillDialogFragment.newInstance(table,
                        new TemporaryBillDialogFragment. Listener() {
                            @Override
                            public void onTemporaryBillRequested(Order order) {
                                fetchTablesFromServer();
                            }
                        });
                f.show(getSupportFragmentManager(), "tempBill");
            }
        });

        TableAdapter. OnTableClickListener listener = new TableAdapter.OnTableClickListener() {
            @Override
            public void onTableClick(View v, TableItem table) {
                if (table == null) return;
                Intent intent = new Intent(MainActivity.this, OrderActivity.class);
                intent.putExtra("tableId", table.getId());
                intent.putExtra("tableNumber", table.getTableNumber());
                intent.putExtra("forceShowOrders", true);
                startActivity(intent);
            }

            @Override
            public void onTableLongClick(View v, TableItem table) {
                if (table == null) return;
                tableActionsHandler.showTableActionsMenuForLongPress(v, table);
            }
        };

        adapterFloor1 = new TableAdapter(this, new ArrayList<TableItem>(), listener);
        adapterFloor2 = new TableAdapter(this, new ArrayList<TableItem>(), listener);
        rvFloor1.setAdapter(adapterFloor1);
        rvFloor2.setAdapter(adapterFloor2);

        String socketUrl = getIntent().getStringExtra("socketUrl");
        if (socketUrl == null || socketUrl.trim().isEmpty()) socketUrl = defaultSocketUrl;

        if (isProbablyEmulator()) {
            try {
                String replaced = replaceHostForEmulator(socketUrl);
                Log.i(TAG, "Emulator detected - using socket URL: " + replaced);
                socketUrl = replaced;
            } catch (Exception e) {
                Log.w(TAG, "Failed to adapt socketUrl for emulator: " + e.getMessage(), e);
            }
        }

        try {
            socketManager = SocketManager.getInstance();
            socketManager.init(socketUrl);
            socketManager.setOnEventListener(new SocketManager.OnEventListener() {
                @Override
                public void onOrderCreated(JSONObject payload) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            fetchTablesFromServer();
                        }
                    });
                }

                @Override
                public void onOrderUpdated(JSONObject payload) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            fetchTablesFromServer();
                        }
                    });
                }

                @Override
                public void onConnect() {
                    Log. d(TAG, "socket connected (main)");
                }

                @Override
                public void onDisconnect() {
                    Log. d(TAG, "socket disconnected (main)");
                }

                @Override
                public void onError(Exception e) {
                    Log. w(TAG, "socket error (main): " + (e != null ? e.getMessage() : "null"));
                }

                @Override
                public void onTableUpdated(JSONObject payload) {
                    if (payload != null) {
                        String evt = payload.optString("eventName", "");
                        if ("table_auto_released".equals(evt)) {
                            int tblNum = -1;
                            if (payload.has("tableNumber")) tblNum = payload.optInt("tableNumber", -1);
                            else if (payload.has("table")) tblNum = payload.optInt("table", -1);
                            final int shownNum = tblNum;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        String msg = "Bàn " + (shownNum > 0 ? shownNum :  "") + " đã tự động hủy đặt trước. ";
                                        new AlertDialog.Builder(MainActivity.this)
                                                .setTitle("Thông báo")
                                                .setMessage(msg)
                                                .setCancelable(false)
                                                .setPositiveButton("OK", null)
                                                .show();
                                        fetchTablesFromServer();
                                    } catch (Exception ex) {
                                        Log. w(TAG, "show auto-release dialog failed", ex);
                                        fetchTablesFromServer();
                                    }
                                }
                            });
                            return;
                        }
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            fetchTablesFromServer();
                        }
                    });
                }
            });
            socketManager.connect();
        } catch (Exception e) {
            Log.w(TAG, "Failed to init socket in MainActivity:  " + e.getMessage(), e);
        }

        applyNavigationViewInsets();
        fetchTablesFromServer();
    }

    // ======================================================================
    // ✅ YÊU CẦU TẠM TÍNH
    // ======================================================================

    private void showTempCalculationRequests() {
        if (orderRepository == null) {
            orderRepository = new OrderRepository();
        }

        if (progressBar != null) {
            progressBar.setVisibility(View. VISIBLE);
        }

        Log.d(TAG, "Loading temp calculation requests...");

        orderRepository.getTemporaryBillOrders(new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> tempBillOrders) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (progressBar != null) {
                            progressBar.setVisibility(View. GONE);
                        }

                        Log.d(TAG, "Found " + (tempBillOrders != null ?  tempBillOrders.size() : 0) + " temp calculation requests");

                        if (tempBillOrders == null || tempBillOrders.isEmpty()) {
                            Toast.makeText(MainActivity.this, "Không có yêu cầu tạm tính nào", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Collections.sort(tempBillOrders, new Comparator<Order>() {
                            @Override
                            public int compare(Order o1, Order o2) {
                                String time1 = o1.getTempCalculationRequestedAt();
                                String time2 = o2.getTempCalculationRequestedAt();
                                if (time1 == null) return 1;
                                if (time2 == null) return -1;
                                return time2.compareTo(time1);
                            }
                        });

                        showTempCalculationDialog(tempBillOrders);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (progressBar != null) {
                            progressBar.setVisibility(View.GONE);
                        }
                        Log.e(TAG, "Failed to load temp calculation requests: " + message);
                        Toast.makeText(MainActivity.this, "Không thể tải danh sách:  " + message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showTempCalculationDialog(List<Order> requests) {
        if (requests == null || requests.isEmpty()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_temp_calculation_list, null);

        TextView tvTitle = dialogView.findViewById(R. id.tv_dialog_title);
        RecyclerView recyclerView = dialogView.findViewById(R.id.rv_temp_calculations);

        tvTitle.setText("Yêu cầu tạm tính (" + requests.size() + ")");

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        TempCalculationListAdapter adapter = new TempCalculationListAdapter(requests);
        recyclerView.setAdapter(adapter);

        builder.setView(dialogView);
        builder.setPositiveButton("Đóng", null);
        builder.show();
    }

    // ======================================================================
    // ✅ YÊU CẦU KIỂM TRA BÀN
    // ======================================================================

    private void showCheckItemsRequests() {
        if (orderRepository == null) {
            orderRepository = new OrderRepository();
        }

        if (progressBar != null) {
            progressBar. setVisibility(View.VISIBLE);
        }

        Log. d(TAG, "🔍 Loading check items requests.. .");

        orderRepository.getCheckItemsOrders(new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> checkItemsOrders) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (progressBar != null) {
                            progressBar.setVisibility(View.GONE);
                        }

                        Log. d(TAG, "📦 Found " + (checkItemsOrders != null ? checkItemsOrders.size() : 0) + " check items requests");

                        if (checkItemsOrders == null || checkItemsOrders.isEmpty()) {
                            Toast.makeText(MainActivity.this, "Không có yêu cầu kiểm tra bàn nào", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Collections.sort(checkItemsOrders, new Comparator<Order>() {
                            @Override
                            public int compare(Order o1, Order o2) {
                                String time1 = o1.getCheckItemsRequestedAt();
                                String time2 = o2.getCheckItemsRequestedAt();
                                if (time1 == null) return 1;
                                if (time2 == null) return -1;
                                return time2.compareTo(time1);
                            }
                        });

                        showCheckItemsDialog(checkItemsOrders);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (progressBar != null) {
                            progressBar.setVisibility(View.GONE);
                        }
                        Log.e(TAG, "❌ Failed to load check items requests: " + message);
                        Toast.makeText(MainActivity.this, "Không thể tải danh sách: " + message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void showCheckItemsDialog(List<Order> requests) {
        if (requests == null || requests.isEmpty()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_check_items_list, null);

        TextView tvTitle = dialogView. findViewById(R.id.tv_dialog_title);
        RecyclerView recyclerView = dialogView.findViewById(R.id.rv_check_items);

        tvTitle.setText("Yêu cầu kiểm tra bàn (" + requests.size() + ")");

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        CheckItemsListAdapter adapter = new CheckItemsListAdapter(requests, new CheckItemsListAdapter.OnCheckItemClickListener() {
            @Override
            public void onCheckItemClick(Order order) {
                showCheckItemsConfirmDialog(order);
            }
        });
        recyclerView.setAdapter(adapter);

        builder.setView(dialogView);
        builder.setPositiveButton("Đóng", null);
        builder.show();
    }

    private void showCheckItemsConfirmDialog(Order order) {
        if (order == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_check_items_confirm, null);

        TextView tvTableInfo = dialogView.findViewById(R. id.tv_table_info);
        EditText etNote = dialogView.findViewById(R. id.tv_note);

        tvTableInfo.setText("Kiểm tra bàn " + order.getTableNumber());

        builder.setView(dialogView);
        builder.setTitle("Xác nhận kiểm tra");
        builder.setPositiveButton("Xác nhận đã kiểm tra", new android.content.DialogInterface.OnClickListener() {
            @Override
            public void onClick(android.content.DialogInterface dialog, int which) {
                String note = etNote.getText().toString().trim();
                confirmCheckItems(order, note);
            }
        });
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    /**
     * ✅ SỬA LỖI:  Method này được gọi khi user click "Xác nhận đã kiểm tra"
     *
     * THAY ĐỔI QUAN TRỌNG:
     * - Toast CHỈ hiển thị KHI API call thành công (trong onSuccess callback)
     * - KHÔNG hiển thị Toast trước khi gọi API
     * - Log chi tiết để debug
     * - Handle error với dialog retry
     */
    private void confirmCheckItems(Order order, String note) {
        if (order == null || order.getId() == null) {
            Toast.makeText(this, "Lỗi:  Thông tin không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        // ✅ Hiển thị progress bar TRƯỚC KHI gọi API
        if (progressBar != null) {
            progressBar.setVisibility(View. VISIBLE);
        }

        SharedPreferences prefs = getSharedPreferences("RestaurantPrefs", MODE_PRIVATE);
        String userId = prefs.getString("userId", "");
        String fullName = prefs.getString("fullName", "Nhân viên");

        java.text.SimpleDateFormat sdf = new java. text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        String currentTime = sdf.format(new java.util.Date());

        Map<String, Object> updates = new HashMap<>();
        updates.put("checkItemsStatus", "completed");
        updates.put("checkItemsCompletedBy", userId. isEmpty() ? fullName : userId);
        updates.put("checkItemsCompletedAt", currentTime);

        if (note != null && ! note.trim().isEmpty()) {
            updates.put("checkItemsNote", note. trim());
        } else {
            updates.put("checkItemsNote", "");
        }

        // ✅ LOG CHI TIẾT để debug
        Log.d(TAG, "=== CONFIRM CHECK ITEMS ===");
        Log.d(TAG, "Order ID: " + order.getId());
        Log.d(TAG, "Table:  " + order.getTableNumber());
        Log.d(TAG, "Status: completed");
        Log.d(TAG, "Completed By: " + (userId.isEmpty() ? fullName : userId));
        Log.d(TAG, "Completed At: " + currentTime);
        Log.d(TAG, "Note: " + (note. trim().isEmpty() ? "(empty)" : note));
        Log.d(TAG, "Payload: " + updates. toString());

        // ✅ GỌI API - Toast CHỈ hiển thị trong onSuccess
        orderRepository.updateOrder(order.getId(), updates, new OrderRepository.RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // ✅ Ẩn progress bar
                        if (progressBar != null) {
                            progressBar. setVisibility(View.GONE);
                        }

                        Log.d(TAG, "✅ Successfully confirmed check items for table " + order.getTableNumber());

                        // ✅✅✅ CHỈ HIỂN THỊ TOAST KHI API THÀNH CÔNG ✅✅✅
                        String successMessage = "✅ Đã xác nhận kiểm tra bàn " + order. getTableNumber() +
                                "\n📤 Đang gửi thông báo cho thu ngân... ";
                        if (note != null && !note. trim().isEmpty()) {
                            successMessage += "\n📝 Ghi chú: " + note;
                        }
                        Toast.makeText(MainActivity.this, successMessage, Toast.LENGTH_LONG).show();

                        // ✅ Reload danh sách để cập nhật
                        showCheckItemsRequests();
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // ✅ Ẩn progress bar
                        if (progressBar != null) {
                            progressBar.setVisibility(View. GONE);
                        }

                        Log.e(TAG, "❌ Failed to confirm check items: " + message);

                        // ❌ HIỂN THỊ LỖI
                        String errorMessage = "Lỗi xác nhận kiểm tra:\n" + message;
                        Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();

                        // ✅ Hiển thị dialog cho phép thử lại
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Lỗi xác nhận")
                                . setMessage(errorMessage)
                                .setPositiveButton("Thử lại", new android.content.DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(android.content.DialogInterface dialog, int which) {
                                        confirmCheckItems(order, note);
                                    }
                                })
                                .setNegativeButton("Đóng", null)
                                .show();
                    }
                });
            }
        });
    }

    // ======================================================================
    // HELPER METHODS
    // ======================================================================

    private void applyNavigationViewInsets() {
        if (navigationView == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(navigationView, new androidx.core.view.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                int statusBar = insets.getInsets(WindowInsetsCompat.Type. statusBars()).top;
                View header = navigationView.getHeaderView(0);
                if (header != null) {
                    header.setPadding(
                            header.getPaddingLeft(),
                            statusBar,
                            header.getPaddingRight(),
                            header.getPaddingBottom()
                    );
                }
                return insets;
            }
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
            return port > 0 ? scheme + "://" + newHost + ":" + port + path + query :  scheme + "://" + newHost + path + query;
        } catch (Exception e) {
            if (url.startsWith("http://localhost")) return url. replace("localhost", "10.0.2.2");
            if (url.startsWith("http://127.0.0.1")) return url.replace("127.0.0.1", "10.0.2.2");
            return url;
        }
    }

    private void updateNavHeaderInfo() {
        if (navigationView == null) return;
        try {
            View headerView = navigationView.getHeaderView(0);
            if (headerView == null) return;

            TextView tvName = headerView.findViewById(R. id.textViewName);
            TextView tvRole = headerView.findViewById(R. id.textViewRole);

            SharedPreferences prefs = getSharedPreferences("RestaurantPrefs", MODE_PRIVATE);
            String savedName = prefs.getString("fullName", "Người dùng");
            String savedRole = prefs.getString("userRole", "");

            if (tvName != null) tvName.setText(savedName);
            if (tvRole != null) tvRole.setText(getVietnameseRole(savedRole));
        } catch (Exception e) {
            Log.w(TAG, "updateNavHeaderInfo failed:  " + e.getMessage(), e);
        }
    }

    private String getVietnameseRole(String roleKey) {
        if (roleKey == null) return "";
        switch (roleKey. toLowerCase()) {
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
        try {
            if (socketManager != null) socketManager.connect();
        } catch (Exception e) {
        }
        fetchTablesFromServer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (socketManager != null) socketManager.disconnect();
        } catch (Exception e) {
        }
    }

    public void fetchTablesFromServer() {
        if (progressBar != null) progressBar.setVisibility(ProgressBar.VISIBLE);
        tableRepository.getAllTables(new TableRepository. RepositoryCallback<List<TableItem>>() {
            @Override
            public void onSuccess(List<TableItem> result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (progressBar != null) progressBar.setVisibility(ProgressBar.GONE);
                        if (result == null || result.isEmpty()) {
                            adapterFloor1.updateList(new ArrayList<TableItem>());
                            adapterFloor2.updateList(new ArrayList<TableItem>());
                            return;
                        }

                        for (TableItem t : result)
                            if (t != null && t.getLocation() == null) t.setLocation("");

                        List<TableItem> floor1 = new ArrayList<>();
                        List<TableItem> floor2 = new ArrayList<>();
                        for (TableItem t :  result) {
                            int floor = parseFloorFromLocation(t.getLocation());
                            if (floor == 2) floor2.add(t);
                            else floor1.add(t);
                        }

                        Comparator<TableItem> byNumber = new Comparator<TableItem>() {
                            @Override
                            public int compare(TableItem a, TableItem b) {
                                if (a == null && b == null) return 0;
                                if (a == null) return 1;
                                if (b == null) return -1;
                                try {
                                    return Integer.compare(a.getTableNumber(), b.getTableNumber());
                                } catch (Exception e) {
                                    return String.valueOf(a.getTableNumber()).compareTo(String.valueOf(b. getTableNumber()));
                                }
                            }
                        };
                        Collections.sort(floor1, byNumber);
                        Collections.sort(floor2, byNumber);

                        adapterFloor1.updateList(floor1);
                        adapterFloor2.updateList(floor2);

                        List<TableItem> all = new ArrayList<>();
                        all.addAll(floor1);
                        all.addAll(floor2);
                        syncTableStatusesWithOrders(all);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (progressBar != null) progressBar.setVisibility(ProgressBar.GONE);
                        Toast.makeText(MainActivity.this, "Lỗi tải danh sách bàn: " + message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private int parseFloorFromLocation(String location) {
        if (location == null) return 1;
        try {
            Pattern p = Pattern.compile("(\\d+)");
            Matcher m = p.matcher(location. toLowerCase(Locale.getDefault()));
            if (m. find()) return Integer.parseInt(m.group(1));
        } catch (Exception ignored) {
        }
        return 1;
    }

    private void syncTableStatusesWithOrders(List<TableItem> tables) {
        if (tables == null || tables.isEmpty()) return;
        orderRepository.getOrdersByTableNumber(null, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orders) {
                final java.util.Set<Integer> occupiedTableNumbers = new java.util.HashSet<>();
                if (orders != null) {
                    for (Order o : orders) if (o != null) occupiedTableNumbers.add(o. getTableNumber());
                }
                List<TableItem> toUpdate = new ArrayList<>();
                final List<String> desired = new ArrayList<>();
                for (TableItem t : tables) {
                    if (t == null) continue;
                    boolean isReserved = false;
                    try {
                        isReserved = t.getStatus() == TableItem.Status.RESERVED;
                    } catch (Exception ignored) {
                    }
                    if (isReserved) continue;
                    String cur = t.getStatus() != null ? t.getStatus().name().toLowerCase() : "";
                    String want = occupiedTableNumbers.contains(t. getTableNumber()) ? "occupied" : "available";
                    if (!cur.equals(want)) {
                        toUpdate.add(t);
                        desired.add(want);
                    }
                }
                if (toUpdate.isEmpty()) return;
                final int total = toUpdate.size();
                final int[] finished = {0};
                for (int i = 0; i < toUpdate.size(); i++) {
                    TableItem ti = toUpdate.get(i);
                    String want = desired.get(i);
                    tableRepository.updateTableStatus(ti. getId(), want, new TableRepository. RepositoryCallback<TableItem>() {
                        @Override
                        public void onSuccess(TableItem updated) {
                            finished[0]++;
                            if (finished[0] >= total) runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    fetchTablesFromServer();
                                }
                            });
                        }

                        @Override
                        public void onError(String message) {
                            finished[0]++;
                            if (finished[0] >= total) runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    fetchTablesFromServer();
                                }
                            });
                        }
                    });
                }
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "sync orders error: " + message);
            }
        });
    }
}