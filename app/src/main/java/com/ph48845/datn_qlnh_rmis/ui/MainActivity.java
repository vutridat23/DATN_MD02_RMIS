package com.ph48845.datn_qlnh_rmis.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Build;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;
import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.core.base.BaseMenuActivity;
import com.ph48845.datn_qlnh_rmis.data.model.InAppNotification;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter.TableAdapter;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter.TempCalculationListAdapter;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter.CheckItemsListAdapter;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.OrderActivity;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.notification.InAppNotificationView;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.notification.NotificationManager;
import com.ph48845.datn_qlnh_rmis.ui.table.MergeManager;
import com.ph48845.datn_qlnh_rmis.ui.table.ReservationHelper;
import com.ph48845.datn_qlnh_rmis.ui.table.TableActionsHandler;
import com.ph48845.datn_qlnh_rmis.ui.table.TransferManager;
import com.ph48845.datn_qlnh_rmis.ui.table.TemporaryBillDialogFragment;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.socket.SocketManager;
import com.ph48845.datn_qlnh_rmis.ui.table.fragment.AutoReleaseDialogFragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MainActivity với tích hợp In-app Notification.
 * Hiển thị thông báo trượt từ trên xuống khi có cập nhật realtime.
 *
 * NOTE: Updated to register/unregister a persistent listener with the global SocketManager
 * so the socket connection remains app-wide and not overwritten by activity listeners.
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
    private String defaultSocketUrl = "http://192.168.1.229:3000";

    // main listener instance so we can register/unregister without losing it
    private SocketManager.OnEventListener mainSocketListener;
    private boolean socketListenerRegistered = false;

    // ✅ THÊM NOTIFICATION MANAGER
    private NotificationManager notificationManager;

    // Track whether activity is visible (foreground)
    boolean activityVisible = false;
    // If an auto-release event arrives when activity is not visible or window not focused, keep pending table number
    Integer pendingAutoReleasedTable = null;
    // Thêm vào class MainActivity (chỉ phần method — chèn vào trong class)
    public synchronized boolean isActivityVisible() {
        return this.activityVisible;
    }

    public synchronized void setPendingAutoReleasedTable(int tableNumber) {
        this.pendingAutoReleasedTable = tableNumber;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
            // debug helper: long-press to simulate auto-release (useful to confirm dialog display)
            navIcon.setOnLongClickListener(v -> {
                Log.d(TAG, "DEBUG: simulate auto-release (table 5)");
                pendingAutoReleasedTable = null;
                showAutoReleaseDialogIfPossible(5);
                return true;
            });
        }

        if (toolbar != null && drawerLayout != null) {
            toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        // navigation menu style
        if (navigationView != null) {
            loadMenuBasedOnRole();

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
                handleNavigationItemClick(id);
                if (drawerLayout != null)
                    drawerLayout.closeDrawer(GravityCompat.START);
                return true;
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

        tableActionsHandler.setTemporaryBillRequester(table -> {
            if (table == null) return;
            TemporaryBillDialogFragment f = TemporaryBillDialogFragment.newInstance(table,
                    updatedOrder -> fetchTablesFromServer());
            f.show(getSupportFragmentManager(), "tempBill");
        });

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
                    if (st == TableItem.Status.OCCUPIED || st == TableItem.Status.PENDING_PAYMENT)
                        isCustomerPresent = true;
                } catch (Exception ignored) {}
                intent.putExtra("forceShowOrders", isCustomerPresent);
                startActivity(intent);
            }

            @Override
            public void onTableLongClick(View v, TableItem table) {
                if (table == null) return;
                tableActionsHandler.showTableActionsMenuForLongPress(v, table);
            }
        };

        adapterFloor1 = new TableAdapter(this, new ArrayList<>(), listener);
        adapterFloor2 = new TableAdapter(this, new ArrayList<>(), listener);
        rvFloor1.setAdapter(adapterFloor1);
        rvFloor2.setAdapter(adapterFloor2);

        // ✅✅✅ INITIALIZE NOTIFICATION MANAGER ✅✅✅
        initNotificationManager();

        String socketUrl = getIntent().getStringExtra("socketUrl");
        if (socketUrl == null || socketUrl.trim().isEmpty())
            socketUrl = defaultSocketUrl;

        if (isProbablyEmulator()) {
            try {
                String replaced = replaceHostForEmulator(socketUrl);
                Log.i(TAG, "Emulator detected - using socket URL: " + replaced);
                socketUrl = replaced;
            } catch (Exception e) {
                Log.w(TAG, "Failed to adapt socketUrl for emulator: " + e.getMessage(), e);
            }
        }

        initSocket(socketUrl);

        applyNavigationViewInsets();
        fetchTablesFromServer();
        updateCheckItemsRequestBadge();

        // Start reservation helper listening for auto-release events
        try {
            reservationHelper.startListening();
        } catch (Exception e) {
            Log.w(TAG, "Failed to start ReservationHelper listener: " + e.getMessage(), e);
        }

        NotificationManager.getInstance().init(this, null);
    }

    // ✅✅✅ INITIALIZE NOTIFICATION MANAGER ✅✅✅
    private void initNotificationManager() {
        notificationManager = NotificationManager.getInstance();
        notificationManager.init(this, new InAppNotificationView.OnNotificationClickListener() {
            @Override
            public void onNotificationClick(InAppNotification notification) {
                handleNotificationClick(notification);
            }

            @Override
            public void onNotificationDismissed(InAppNotification notification) {
                Log.d(TAG, "Notification dismissed:  " + notification.getTitle());
            }
        });
    }

    // ✅✅✅ INIT SOCKET WITH NOTIFICATION ✅✅✅
    private void initSocket(String socketUrl) {
        try {
            socketManager = SocketManager.getInstance();
            socketManager.init(socketUrl);

            // Build the main listener and keep reference so we can register/unregister without losing it
            mainSocketListener = new SocketManager.OnEventListener() {
                @Override
                public void onOrderCreated(JSONObject payload) {
                    runOnUiThread(() -> {
                        try {
                            int tableNum = payload.optInt("tableNumber", -1);
                            String orderId = payload.optString("_id", "");

                            // Count items
                            int itemCount = 0;
                            if (payload.has("items")) {
                                JSONArray items = payload.optJSONArray("items");
                                if (items != null) {
                                    itemCount = items.length();
                                }
                            }

                            // ✅ SHOW NOTIFICATION
                            InAppNotification notification = new InAppNotification.Builder(
                                    InAppNotification.Type.ORDER_NEW,
                                    "🍽️ Đơn hàng mới! ",
                                    "Bàn " + tableNum + " vừa đặt " + itemCount + " món"
                            )
                                    .icon(android.R.drawable.ic_menu_add)
                                    .actionData("table:" + tableNum + ":order:" + orderId)
                                    .duration(6000)
                                    .build();

                            notificationManager.show(notification);

                        } catch (Exception e) {
                            Log.e(TAG, "Error showing order created notification", e);
                        }

                        fetchTablesFromServer();
                        updateCheckItemsRequestBadge();
                    });
                }

                @Override
                public void onOrderUpdated(JSONObject payload) {
                    runOnUiThread(() -> {
                        try {
                            int tableNum = payload.optInt("tableNumber", -1);
                            String status = payload.optString("status", "");
                            String orderId = payload.optString("_id", "");

                            // Determine notification type and message
                            InAppNotification.Type type;
                            String title;
                            String message;

                            if ("completed".equalsIgnoreCase(status)) {
                                type = InAppNotification.Type.SUCCESS;
                                title = "✅ Hoàn thành! ";
                                message = "Bàn " + tableNum + " đã hoàn thành đơn hàng";
                            } else if ("cancelled".equalsIgnoreCase(status)) {
                                type = InAppNotification.Type.ERROR;
                                title = "❌ Đã hủy";
                                message = "Bàn " + tableNum + " đã hủy đơn hàng";
                            } else {
                                type = InAppNotification.Type.INFO;
                                title = "📝 Cập nhật đơn hàng";
                                message = "Bàn " + tableNum + " - " + getStatusText(status);
                            }

                            // ✅ SHOW NOTIFICATION
                            InAppNotification notification = new InAppNotification.Builder(
                                    type,
                                    title,
                                    message
                            )
                                    .actionData("table:" + tableNum + ":order:" + orderId)
                                    .duration(5000)
                                    .build();

                            notificationManager.show(notification);

                        } catch (Exception e) {
                            Log.e(TAG, "Error showing order updated notification", e);
                        }

                        fetchTablesFromServer();
                        updateCheckItemsRequestBadge();
                    });
                }

                @Override
                public void onConnect() {
                    Log.d(TAG, "socket connected (main)");
                }

                @Override
                public void onDisconnect() {
                    Log.d(TAG, "socket disconnected (main)");
                }

                @Override
                public void onError(Exception e) {
                    Log.w(TAG, "socket error (main): " + (e != null ? e.getMessage() : "null"));
                }

                @Override
                public void onTableUpdated(JSONObject payload) {
                    Log.d(TAG, "onTableUpdated called, payload=" + (payload != null ? payload.toString() : "null")
                            + " | eventName=" + (payload != null ? payload.optString("eventName", "(none)") : "(no payload)")
                            + " | activityVisible=" + activityVisible
                            + " | pendingAutoReleasedTable=" + pendingAutoReleasedTable);

                    if (payload != null) {
                        String evt = payload.optString("eventName", "");
                        String statusInPayload = payload.optString("status", "").toLowerCase();

                        // 1) HANDLE AUTO-RELEASE (existing special flow)
                        if ("table_auto_released".equals(evt)) {
                            int tblNum = -1;
                            if (payload.has("tableNumber"))
                                tblNum = payload.optInt("tableNumber", -1);
                            else if (payload.has("table"))
                                tblNum = payload.optInt("table", -1);

                            final int shownNum = tblNum;
                            Log.d(TAG, "table_auto_released received for tableNumber=" + shownNum);

                            // Always show an in-app notification so user sees it even if dialog cannot be shown
                            try {
                                InAppNotification notification = new InAppNotification.Builder(
                                        InAppNotification.Type.WARNING,
                                        "⏰ Hủy đặt bàn tự động",
                                        "Bàn " + (shownNum > 0 ? shownNum : "") + " đã hết thời gian đặt trước"
                                )
                                        .actionData("table:" + shownNum)
                                        .duration(8000)
                                        .build();
                                // show notification without caring about activity state
                                notificationManager.show(notification);
                            } catch (Exception e) {
                                Log.w(TAG, "Failed to show in-app notification for auto-release", e);
                            }

                            // Determine whether it's safe to show AlertDialog now:
                            boolean canShowDialog = true;
                            if (isFinishing()) canShowDialog = false;
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed()) canShowDialog = false;

                            // hasWindowFocus is a better indicator that Activity is in foreground
                            boolean hasFocus = false;
                            try {
                                hasFocus = MainActivity.this.hasWindowFocus();
                            } catch (Exception ignored) {}

                            Log.d(TAG, "canShowDialog=" + canShowDialog + ", activityVisible=" + activityVisible + ", hasWindowFocus=" + hasFocus);

                            if (canShowDialog && (activityVisible || hasFocus)) {
                                // show dialog now on UI thread
                                runOnUiThread(() -> {
                                    try {
                                        Toast.makeText(MainActivity.this, "DEBUG: show auto-release dialog for table " + shownNum, Toast.LENGTH_SHORT).show();

                                        // show DialogFragment
                                        AutoReleaseDialogFragment.newInstance(shownNum)
                                                .show(getSupportFragmentManager(), "autoRelease");
                                    } catch (Exception ex) {
                                        Log.w(TAG, "show auto-release dialog failed", ex);
                                    } finally {
                                        runOnUiThread(() -> fetchTablesFromServer());
                                    }
                                });
                            } else {
                                // Activity not in a state to show dialog now — save pending and show onResume
                                pendingAutoReleasedTable = shownNum;
                                Log.d(TAG, "Saved pendingAutoReleasedTable=" + pendingAutoReleasedTable + " to show on resume");
                                runOnUiThread(() -> fetchTablesFromServer());
                            }

                            return;
                        }

                        // 2) HANDLE RESERVATION / RESERVED UPDATES
                        // Accept several possible event names used by server/client and also inspect status field.
                        boolean looksLikeReservationEvent = false;
                        if ("table_reserved".equalsIgnoreCase(evt)
                                || "table_reservation_created".equalsIgnoreCase(evt)
                                || "table_reservation".equalsIgnoreCase(evt)
                                || "reservation_created".equalsIgnoreCase(evt)
                                || "table_updated".equalsIgnoreCase(evt) // server might emit generic update for reservation
                        ) {
                            looksLikeReservationEvent = true;
                        }

                        if (!looksLikeReservationEvent) {
                            // If eventName absent but status indicates reserved, treat it as reservation
                            if ("reserved".equalsIgnoreCase(statusInPayload)) {
                                looksLikeReservationEvent = true;
                            }
                        }

                        if (looksLikeReservationEvent) {
                            int tableNum = payload.optInt("tableNumber", -1);
                            if (tableNum == -1) tableNum = payload.optInt("table", -1);

                            final int shownNum = tableNum;
                            Log.d(TAG, "Reservation-like event received (eventName=" + evt + ", status=" + statusInPayload + ") for table=" + shownNum);

                            // Show notification and refresh lists
                            runOnUiThread(() -> {
                                try {
                                    if (shownNum > 0) {
                                        InAppNotification notification = new InAppNotification.Builder(
                                                InAppNotification.Type.INFO,
                                                "📅 Đặt trước",
                                                "Bàn " + shownNum + " vừa được đặt trước"
                                        )
                                                .actionData("table:" + shownNum)
                                                .duration(5000)
                                                .build();
                                        notificationManager.show(notification);
                                    } else {
                                        // generic notification
                                        InAppNotification notification = new InAppNotification.Builder(
                                                InAppNotification.Type.INFO,
                                                "📅 Đặt trước",
                                                "Có cập nhật đặt trước"
                                        ).duration(4000).build();
                                        notificationManager.show(notification);
                                    }
                                } catch (Exception e) {
                                    Log.w(TAG, "Failed to show reservation notification", e);
                                } finally {
                                    fetchTablesFromServer();
                                }
                            });

                            return;
                        }

                        // 3) OTHER table updates (existing flow)
                        runOnUiThread(() -> {
                            try {
                                int tableNum = payload.optInt("tableNumber", -1);
                                if (tableNum == -1) {
                                    tableNum = payload.optInt("table", -1);
                                }

                                String status = payload.optString("status", "");

                                if (tableNum > 0 && !status.isEmpty()) {
                                    // ✅ SHOW NOTIFICATION
                                    InAppNotification notification = new InAppNotification.Builder(
                                            InAppNotification.Type.INFO,
                                            "🪑 Cập nhật bàn",
                                            "Bàn " + tableNum + " - " + getTableStatusText(status)
                                    )
                                            .actionData("table:" + tableNum)
                                            .duration(4000)
                                            .build();

                                    notificationManager.show(notification);
                                }

                            } catch (Exception e) {
                                Log.e(TAG, "Error showing table updated notification", e);
                            }

                            fetchTablesFromServer();
                        });
                    } else {
                        runOnUiThread(() -> fetchTablesFromServer());
                    }
                }

                @Override
                public void onCheckItemsRequest(JSONObject payload) {
                    runOnUiThread(() -> {
                        try {
                            int tableNum = payload.optInt("tableNumber", -1);

                            // ✅ SHOW NOTIFICATION
                            InAppNotification notification = new InAppNotification.Builder(
                                    InAppNotification.Type.WARNING,
                                    "🔍 Yêu cầu kiểm tra bàn! ",
                                    "Khách hàng bàn " + tableNum + " yêu cầu kiểm tra món"
                            )
                                    .icon(android.R.drawable.ic_menu_search)
                                    .actionData("check:" + tableNum)
                                    .duration(10000) // 10 giây vì quan trọng
                                    .build();

                            notificationManager.show(notification);

                        } catch (Exception e) {
                            Log.e(TAG, "Error showing check items notification", e);
                        }

                        updateCheckItemsRequestBadge();
                    });
                }
            };

            // Register main listener (do not disconnect from socket on pause)
            try {
                if (!socketListenerRegistered) {
                    socketManager.setOnEventListener(mainSocketListener);
                    socketListenerRegistered = true;
                } else {
                    Log.d(TAG, "Socket listener already registered");
                }
                socketManager.connect();
            } catch (Exception ex) {
                Log.w(TAG, "Failed to register/connect socket listener: " + ex.getMessage(), ex);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to init socket in MainActivity:  " + e.getMessage(), e);
        }
    }

    // ✅✅✅ HANDLE NOTIFICATION CLICK ✅✅✅
    private void handleNotificationClick(InAppNotification notification) {
        String actionData = notification.getActionData();
        if (actionData == null || actionData.isEmpty()) {
            return;
        }

        try {
            // Parse action data:  "table:5:order:abc123" or "check:5"
            String[] parts = actionData.split(":");

            if (parts.length >= 2 && "table".equals(parts[0])) {
                int tableNumber = Integer.parseInt(parts[1]);

                // Navigate to OrderActivity
                Intent intent = new Intent(this, OrderActivity.class);
                intent.putExtra("tableNumber", tableNumber);

                if (parts.length >= 4 && "order".equals(parts[2])) {
                    intent.putExtra("orderId", parts[3]);
                }

                startActivity(intent);
            } else if (parts.length >= 2 && "check".equals(parts[0])) {
                // Open check items dialog
                showCheckItemsRequests();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling notification click", e);
        }
    }

    // ✅ HELPER:  Get status text in Vietnamese
    private String getStatusText(String status) {
        if (status == null) return "";
        switch (status.toLowerCase()) {
            case "pending":  return "Đang chờ";
            case "preparing": return "Đang nấu";
            case "ready":  return "Sẵn sàng";
            case "completed": return "Hoàn thành";
            case "cancelled":  return "Đã hủy";
            default: return status;
        }
    }

    // ✅ HELPER: Get table status text in Vietnamese
    private String getTableStatusText(String status) {
        if (status == null) return "";
        switch (status.toLowerCase()) {
            case "available": return "Trống";
            case "occupied":  return "Có khách";
            case "reserved": return "Đã đặt";
            case "pending_payment": return "Chờ thanh toán";
            default: return status;
        }
    }

    // ======================================================================
    // REST OF THE EXISTING CODE (UNCHANGED)
    // ======================================================================

    private void showTempCalculationRequests() {
        if (orderRepository == null) {
            orderRepository = new OrderRepository();
        }

        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }

        Log.d(TAG, "Loading temp calculation requests...");

        orderRepository.getTemporaryBillOrders(new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> tempBillOrders) {
                runOnUiThread(() -> {
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }

                    Log.d(TAG, "Found " + (tempBillOrders != null ? tempBillOrders.size() : 0) + " temp calculation requests");

                    if (tempBillOrders == null || tempBillOrders.isEmpty()) {
                        Toast.makeText(MainActivity.this, "Không có yêu cầu tạm tính nào", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Collections.sort(tempBillOrders, (o1, o2) -> {
                        String time1 = o1.getTempCalculationRequestedAt();
                        String time2 = o2.getTempCalculationRequestedAt();
                        if (time1 == null) return 1;
                        if (time2 == null) return -1;
                        return time2.compareTo(time1);
                    });

                    showTempCalculationDialog(tempBillOrders);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    Log.e(TAG, "Failed to load temp calculation requests: " + message);
                    Toast.makeText(MainActivity.this, "Không thể tải danh sách:  " + message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showTempCalculationDialog(List<Order> requests) {
        if (requests == null || requests.isEmpty()) return;

        List<Order> filteredRequests = new ArrayList<>();
        for (Order order : requests) {
            if (order != null) {
                String orderStatus = order.getOrderStatus();
                if (orderStatus == null || !orderStatus.equalsIgnoreCase("temp_bill_printed")) {
                    filteredRequests.add(order);
                    Log.d(TAG, "✅ Including order:  " + order.getId() + " (status: " + orderStatus + ")");
                } else {
                    Log.d(TAG, "❌ Filtering out order: " + order.getId() + " (status: temp_bill_printed)");
                }
            }
        }

        if (filteredRequests.isEmpty()) {
            Toast.makeText(MainActivity.this, "Không có yêu cầu tạm tính nào cần xử lý", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_temp_calculation_list, null);

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        RecyclerView recyclerView = dialogView.findViewById(R.id.rv_temp_calculations);

        tvTitle.setText("Yêu cầu tạm tính (" + filteredRequests.size() + ")");

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        TempCalculationListAdapter adapter = new TempCalculationListAdapter(filteredRequests);
        recyclerView.setAdapter(adapter);

        builder.setView(dialogView);
        builder.setPositiveButton("Đóng", null);
        builder.show();
    }

    private void showCheckItemsRequests() {
        if (orderRepository == null) {
            orderRepository = new OrderRepository();
        }

        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }

        Log.d(TAG, "🔍 Loading check items requests.. .");

        orderRepository.getCheckItemsOrders(new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> checkItemsOrders) {
                runOnUiThread(() -> {
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }

                    Log.d(TAG, "📦 Found " + (checkItemsOrders != null ? checkItemsOrders.size() : 0) + " check items requests");

                    if (checkItemsOrders == null || checkItemsOrders.isEmpty()) {
                        Toast.makeText(MainActivity.this, "Không có yêu cầu kiểm tra bàn nào", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Collections.sort(checkItemsOrders, (o1, o2) -> {
                        String time1 = o1.getCheckItemsRequestedAt();
                        String time2 = o2.getCheckItemsRequestedAt();
                        if (time1 == null) return 1;
                        if (time2 == null) return -1;
                        return time2.compareTo(time1);
                    });

                    showCheckItemsDialog(checkItemsOrders);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    Log.e(TAG, "❌ Failed to load check items requests: " + message);
                    Toast.makeText(MainActivity.this, "Không thể tải danh sách: " + message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private AlertDialog checkItemsListDialog;

    private void showCheckItemsDialog(List<Order> requests) {
        if (requests == null || requests.isEmpty()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_check_items_list, null);

        TextView tvTitle = dialogView.findViewById(R.id.tv_dialog_title);
        RecyclerView recyclerView = dialogView.findViewById(R.id.rv_check_items);

        tvTitle.setText("Yêu cầu kiểm tra bàn (" + requests.size() + ")");

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        CheckItemsListAdapter adapter = new CheckItemsListAdapter(requests, order -> {
            showCheckItemsConfirmDialog(order);
        });
        recyclerView.setAdapter(adapter);

        builder.setView(dialogView);
        builder.setPositiveButton("Đóng", null);

        checkItemsListDialog = builder.create();
        checkItemsListDialog.show();
    }

    private void showCheckItemsConfirmDialog(Order order) {
        if (order == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_check_items_confirm, null);

        TextView tvTableInfo = dialogView.findViewById(R.id.tv_table_info);
        EditText etNote = dialogView.findViewById(R.id.tv_note);

        tvTableInfo.setText("Kiểm tra bàn " + order.getTableNumber());

        builder.setView(dialogView);
        builder.setTitle("Xác nhận kiểm tra");
        builder.setPositiveButton("Xác nhận đã kiểm tra", (dialog, which) -> {
            String note = etNote.getText().toString().trim();
            confirmCheckItems(order, note);
        });
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    private void confirmCheckItems(Order order, String note) {
        if (order == null || order.getId() == null) {
            Toast.makeText(this, "Lỗi: Thông tin không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }

        SharedPreferences prefs = getSharedPreferences("RestaurantPrefs", MODE_PRIVATE);
        String userId = prefs.getString("userId", "");
        String fullName = prefs.getString("fullName", "Nhân viên");

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        String currentTime = sdf.format(new java.util.Date());

        Map<String, Object> updates = new HashMap<>();
        updates.put("checkItemsStatus", "completed");
        updates.put("checkItemsCompletedBy", userId.isEmpty() ? fullName : userId);
        updates.put("checkItemsCompletedAt", currentTime);

        if (note != null && !note.trim().isEmpty()) {
            updates.put("checkItemsNote", note.trim());
        } else {
            updates.put("checkItemsNote", "");
        }

        Log.d(TAG, "=== CONFIRM CHECK ITEMS ===");
        Log.d(TAG, "Order ID: " + order.getId());
        Log.d(TAG, "Table: " + order.getTableNumber());
        Log.d(TAG, "Status: completed");
        Log.d(TAG, "Completed By: " + (userId.isEmpty() ? fullName : userId));
        Log.d(TAG, "Completed At: " + currentTime);
        Log.d(TAG, "Note: " + (note.trim().isEmpty() ? "(empty)" : note));
        Log.d(TAG, "Payload: " + updates.toString());

        orderRepository.updateOrder(order.getId(), updates, new OrderRepository.RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order result) {
                runOnUiThread(() -> {
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }

                    Log.d(TAG, "✅ Successfully confirmed check items for table " + order.getTableNumber());

                    String successMessage = "✅ Đã xác nhận kiểm tra bàn " + order.getTableNumber() +
                            "\n📤 Đang gửi thông báo cho thu ngân... ";
                    if (note != null && !note.trim().isEmpty()) {
                        successMessage += "\n📝 Ghi chú:  " + note;
                    }
                    Toast.makeText(MainActivity.this, successMessage, Toast.LENGTH_LONG).show();

                    if (checkItemsListDialog != null && checkItemsListDialog.isShowing()) {
                        checkItemsListDialog.dismiss();
                        Log.d(TAG, "✅ Closed check items list dialog");
                    }

                    fetchTablesFromServer();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }

                    Log.e(TAG, "❌ Failed to confirm check items: " + message);

                    String errorMessage = "Lỗi xác nhận kiểm tra:\n" + message;
                    Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();

                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Lỗi xác nhận")
                            .setMessage(errorMessage)
                            .setPositiveButton("Thử lại", (dialog, which) -> confirmCheckItems(order, note))
                            .setNegativeButton("Đóng", null)
                            .show();
                });
            }
        });
    }

    private void applyNavigationViewInsets() {
        // Kiểm tra null để tránh crash
        if (navigationView == null) return;

        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (view, insets) -> {
            // Lấy chiều cao của Status Bar (thanh trạng thái)
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;

            // Lấy Header View (cái layout màu đỏ chứa Avatar)
            View header = navigationView.getHeaderView(0);

            if (header != null) {
                int paddingLeft = header.getPaddingLeft();
                int paddingRight = header.getPaddingRight();
                int paddingBottom = header.getPaddingBottom();

                int originalPaddingTop = header.getPaddingTop();


                header.setPadding(
                        paddingLeft,
                        statusBarHeight + 10, // Cộng thêm 20px - 30px cho thoáng
                        paddingRight,
                        paddingBottom
                );
            }

            // Trả về insets để hệ thống tiếp tục xử lý các phần khác nếu cần
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
            if (port > 0)
                newUrl = scheme + "://" + newHost + ":" + port + path + query;
            else
                newUrl = scheme + "://" + newHost + path + query;
            return newUrl;
        } catch (Exception e) {
            if (url.startsWith("http://localhost"))
                return url.replace("localhost", "10.0.2.2");
            if (url.startsWith("http://127.0.0.1"))
                return url.replace("127.0.0.1", "10.0.2.2");
            return url;
        }
    }

    private void updateNavHeaderInfo() {
        if (navigationView == null) return;
        try {
            View headerView = navigationView.getHeaderView(0);
            if (headerView == null) return;

            TextView tvName = headerView.findViewById(R.id.textViewName);
            TextView tvRole = headerView.findViewById(R.id.textViewRole);

            SharedPreferences prefs = getSharedPreferences("RestaurantPrefs", MODE_PRIVATE);
            String savedName = prefs.getString("fullName", "Người dùng");
            String savedRole = prefs.getString("userRole", "");

            if (tvName != null)
                tvName.setText(savedName);
            if (tvRole != null)
                tvRole.setText(getVietnameseRole(savedRole));
        } catch (Exception e) {
            Log.w(TAG, "updateNavHeaderInfo failed:  " + e.getMessage(), e);
        }
    }

    private String getVietnameseRole(String roleKey) {
        if (roleKey == null) return "";
        switch (roleKey.toLowerCase()) {
            case "cashier":  return "Thu ngân";
            case "admin": return "Quản lý";
            case "waiter":
            case "order":  return "Phục vụ";
            case "kitchen": return "Bếp";
            default: return roleKey;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            // Mark activity visible
            activityVisible = true;
            Log.d(TAG, "onResume: activityVisible=true, pendingAutoReleasedTable=" + pendingAutoReleasedTable);

            // If we had a pending auto-release while activity was backgrounded, show dialog now
            if (pendingAutoReleasedTable != null) {
                final int tnum = pendingAutoReleasedTable;
                pendingAutoReleasedTable = null;
                showAutoReleaseDialogIfPossible(tnum);
            }
        } catch (Exception e) {
            Log.w(TAG, "onResume handling failed", e);
        }
        fetchTablesFromServer();
        updateCheckItemsRequestBadge();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            // Mark activity not visible
            activityVisible = false;
            Log.d(TAG, "onPause: activityVisible=false");
        } catch (Exception e) {
            Log.w(TAG, "onPause handling failed", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // cleanup socket listener registration flag if needed
        try {
            if (socketManager != null && mainSocketListener != null && socketListenerRegistered) {
                socketManager.setOnEventListener(null); // Unregister bằng cách set null
                socketListenerRegistered = false;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to unregister socket listener on destroy", e);
        }
        // ✅ CLEANUP NOTIFICATION MANAGER
        if (notificationManager != null) {
            notificationManager.destroy();
        }

        // Stop reservation helper listener
        try {
            if (reservationHelper != null) {
                reservationHelper.stopListening();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to stop reservation helper listener: " + e.getMessage(), e);
        }
    }

    private void showAutoReleaseDialogIfPossible(int shownNum) {
        try {
            // If activity is finishing/destroyed, skip showing dialog
            boolean canShow = true;
            if (isFinishing()) canShow = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                if (isDestroyed()) canShow = false;
            }
            if (!canShow) {
                Log.d(TAG, "Cannot show auto-release dialog because activity is not in valid state");
                return;
            }

            // if the activity has window focus or is marked visible, it's safe to show dialog
            boolean hasFocus = false;
            try {
                hasFocus = this.hasWindowFocus();
            } catch (Exception ignored) {}

            if (!(activityVisible || hasFocus)) {
                // still save as pending so it will be shown later
                pendingAutoReleasedTable = shownNum;
                Log.d(TAG, "Deferring showing auto-release dialog until activity foreground (pending=" + shownNum + ")");
                return;
            }

            runOnUiThread(() -> {
                try {
                    InAppNotification notification = new InAppNotification.Builder(
                            InAppNotification.Type.WARNING,
                            "⏰ Hủy đặt bàn tự động",
                            "Bàn " + (shownNum > 0 ? shownNum : "") + " đã hết thời gian đặt trước"
                    )
                            .actionData("table:" + shownNum)
                            .duration(8000)
                            .build();

                    notificationManager.show(notification);

                    String msg = "Bàn " + (shownNum > 0 ? shownNum : "") + " đã tự động hủy đặt trước.";
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Thông báo")
                            .setMessage(msg)
                            .setCancelable(false)
                            .setPositiveButton("OK", null)
                            .show();

                    fetchTablesFromServer();
                } catch (Exception ex) {
                    Log.w(TAG, "show auto-release dialog failed", ex);
                    fetchTablesFromServer();
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "showAutoReleaseDialogIfPossible failed", e);
        }
    }

    public void fetchTablesFromServer() {
        if (progressBar != null)
            progressBar.setVisibility(ProgressBar.VISIBLE);
        tableRepository.getAllTables(new TableRepository.RepositoryCallback<List<TableItem>>() {
            @Override
            public void onSuccess(List<TableItem> result) {
                runOnUiThread(() -> {
                    if (progressBar != null)
                        progressBar.setVisibility(ProgressBar.GONE);
                    if (result == null || result.isEmpty()) {
                        adapterFloor1.updateList(new ArrayList<>());
                        adapterFloor2.updateList(new ArrayList<>());
                        return;
                    }

                    for (TableItem t : result)
                        if (t != null && t.getLocation() == null)
                            t.setLocation("");

                    List<TableItem> floor1 = new ArrayList<>();
                    List<TableItem> floor2 = new ArrayList<>();
                    for (TableItem t : result) {
                        int floor = parseFloorFromLocation(t.getLocation());
                        if (floor == 2)
                            floor2.add(t);
                        else
                            floor1.add(t);
                    }

                    Comparator<TableItem> byNumber = (a, b) -> {
                        if (a == null && b == null) return 0;
                        if (a == null) return 1;
                        if (b == null) return -1;
                        try {
                            return Integer.compare(a.getTableNumber(), b.getTableNumber());
                        } catch (Exception e) {
                            return String.valueOf(a.getTableNumber()).compareTo(String.valueOf(b.getTableNumber()));
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

                    updateCheckItemsRequestBadge();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (progressBar != null)
                        progressBar.setVisibility(ProgressBar.GONE);
                    Toast.makeText(MainActivity.this, "Lỗi tải danh sách bàn: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private int parseFloorFromLocation(String location) {
        if (location == null || location.trim().isEmpty()) return 1;
        try {
            String lower = location.toLowerCase(Locale.getDefault()).trim();
            
            // Tìm từ khóa "tầng" hoặc "floor" và lấy số sau đó
            // Pattern: "tầng" hoặc "floor" theo sau bởi số
            Pattern pattern = Pattern.compile("(tầng|floor)\\s*(\\d+)");
            Matcher matcher = pattern.matcher(lower);
            
            if (matcher.find()) {
                String floorNum = matcher.group(2);
                int floor = Integer.parseInt(floorNum);
                // Chỉ chấp nhận tầng 1 hoặc 2
                if (floor == 2) return 2;
                if (floor == 1) return 1;
            }
        } catch (Exception ignored) {}
        return 1; // Mặc định tầng 1
    }

    private void syncTableStatusesWithOrders(List<TableItem> tables) {
        if (tables == null || tables.isEmpty()) return;
        orderRepository.getOrdersByTableNumber(null, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orders) {
                final java.util.Set<Integer> occupiedTableNumbers = new java.util.HashSet<>();
                if (orders != null) {
                    for (Order o : orders)
                        if (o != null)
                            occupiedTableNumbers.add(o.getTableNumber());
                }
                List<TableItem> toUpdate = new ArrayList<>();
                final List<String> desired = new ArrayList<>();
                for (TableItem t : tables) {
                    if (t == null) continue;
                    boolean isReserved = false;
                    try {
                        isReserved = t.getStatus() == TableItem.Status.RESERVED;
                    } catch (Exception ignored) {}
                    if (isReserved) continue;
                    String cur = t.getStatus() != null ? t.getStatus().name().toLowerCase() : "";
                    String want = occupiedTableNumbers.contains(t.getTableNumber()) ? "occupied" : "available";
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
                    tableRepository.updateTableStatus(ti.getId(), want, new TableRepository.RepositoryCallback<TableItem>() {
                        @Override
                        public void onSuccess(TableItem updated) {
                            finished[0]++;
                            if (finished[0] >= total)
                                runOnUiThread(() -> fetchTablesFromServer());
                        }

                        @Override
                        public void onError(String message) {
                            finished[0]++;
                            if (finished[0] >= total)
                                runOnUiThread(() -> fetchTablesFromServer());
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

    private void loadMenuBasedOnRole() {
        if (navigationView == null) return;

        SharedPreferences prefs = getSharedPreferences("RestaurantPrefs", MODE_PRIVATE);
        String userRole = prefs.getString("userRole", "waiter");

        Log.d(TAG, "Loading menu for role: " + userRole);

        navigationView.getMenu().clear();

        switch (userRole.toLowerCase()) {
            case "admin":
                navigationView.inflateMenu(R.menu.menu_drawer_admin);
                break;
            case "cashier":
                navigationView.inflateMenu(R.menu.menu_drawer_thungan);
                break;
            case "kitchen":
                navigationView.inflateMenu(R.menu.menu_drawer_bep);
                break;
            case "waiter":
            default:
                navigationView.inflateMenu(R.menu.menu_drawer_order);
                break;
        }
    }

    private void handleNavigationItemClick(int itemId) {
        if (itemId == R.id.nav_mood) {
            showMoodDialog();
        } else if (itemId == R.id.nav_contact) {
            showContactDialog();
        } else if (itemId == R.id.nav_logout) {
            logout();
        } else if (itemId == R.id.nav_reports) {
            Intent intent = new Intent(this, com.ph48845.datn_qlnh_rmis.ui.revenue.ReportActivity.class);
            startActivity(intent);
        } else if (itemId == R.id.nav_dashboard) {
            Intent intent = new Intent(this, com.ph48845.datn_qlnh_rmis.ui.dashboard.DashboardActivity.class);
            startActivity(intent);
        } else if (itemId == R.id.nav_revenue) {
            Intent intent = new Intent(this, com.ph48845.datn_qlnh_rmis.ui.revenue.ReportActivity.class);
            startActivity(intent);
        } else if (itemId == R.id.nav_warnings) {
            Intent intent = new Intent(this, com.ph48845.datn_qlnh_rmis.ui.warehouse.WarningActivity.class);
            startActivity(intent);
        } else if (itemId == R.id.nav_shifts) {
            Intent intent = new Intent(this, com.ph48845.datn_qlnh_rmis.ui.shift.ShiftActivity.class);
            startActivity(intent);
        } else if (itemId == R.id.nav_employees) {
            Intent intent = new Intent(this, com.ph48845.datn_qlnh_rmis.ui.employee.EmployeeActivity.class);
            startActivity(intent);
        } else if (itemId == R.id.nav_payment_history) {
            Intent intent = new Intent(this, com.ph48845.datn_qlnh_rmis.ui.thungan.HistoryActivity.class);
            startActivity(intent);
        } else if (itemId == R.id.nav_temp_calculation_requests || itemId == R.id.nav_pre_bill) {
            showTempCalculationRequests();
        } else if (itemId == R.id.nav_check_items_requests) {
            showCheckItemsRequests();
        }
    }

    private void updateCheckItemsRequestBadge() {
        if (orderRepository == null) {
            orderRepository = new OrderRepository();
        }

        orderRepository.getAllOrders(new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> allOrders) {
                runOnUiThread(() -> {
                    int count = 0;
                    if (allOrders != null) {
                        for (Order order : allOrders) {
                            if (order != null) {
                                String requestedAt = order.getCheckItemsRequestedAt();
                                if (requestedAt != null && !requestedAt.trim().isEmpty()) {
                                    count++;
                                }
                            }
                        }
                    }

                    updateBadgeOnMenuItem(count);
                });
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Failed to update check items request badge: " + message);
                runOnUiThread(() -> updateBadgeOnMenuItem(0));
            }
        });
    }

    private void updateBadgeOnMenuItem(int count) {
        if (navigationView == null) {
            return;
        }

        try {
            MenuItem menuItem = navigationView.getMenu().findItem(R.id.nav_check_items_requests);
            if (menuItem == null) {
                Log.d(TAG, "Menu item nav_check_items_requests not found");
                return;
            }

            String baseTitle = "Yêu cầu kiểm tra bàn";
            String displayTitle;

            if (count > 0) {
                displayTitle = baseTitle + " (" + count + ")";
                Log.d(TAG, "✅ Updated badge:  " + count + " check items requests");
            } else {
                displayTitle = baseTitle;
                Log.d(TAG, "✅ Updated badge: 0 check items requests (no badge)");
            }

            SpannableString spanString = new SpannableString(displayTitle);
            spanString.setSpan(new RelativeSizeSpan(1.1f), 0, spanString.length(), 0);
            menuItem.setTitle(spanString);

        } catch (Exception e) {
            Log.w(TAG, "Failed to update badge on menu item:  " + e.getMessage(), e);
        }
    }
}