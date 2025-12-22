package com.ph48845.datn_qlnh_rmis.ui.thungan;

import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.fragment.app.FragmentManager;

import com.google.android.material.navigation.NavigationView;
import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.core.base.BaseMenuActivity;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.model.User;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.ph48845.datn_qlnh_rmis.ui.revenue.ReportActivity;
// Thay thế bằng Activity xem lịch sử thanh toán thực tế của bạn
// import com.ph48845.datn_qlnh_rmis.ui.history.HistoryActivity;
// Thay thế bằng Activity xem chi tiết hóa đơn thực tế của bạn
// import com.ph48845.datn_qlnh_rmis.ui.invoice.InvoiceActivity;
import com.ph48845.datn_qlnh_rmis.ui.table.ReservationHelper;
import com.ph48845.datn_qlnh_rmis.ui.thungan.adapter.ThuNganAdapter;
import com.ph48845.datn_qlnh_rmis.ui.thungan.fragment.ReserveTableDialogFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.text.Normalizer;

import org.json.JSONObject;
import android.view.LayoutInflater;
import android.animation.ObjectAnimator;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.notification.NotificationManager;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.notification.InAppNotificationView;
import com.ph48845.datn_qlnh_rmis.data.model.InAppNotification;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.socket.SocketManager;

import java.net.URI;

/**
 * Activity Thu Ngân: Quản lý danh sách bàn đang hoạt động/chờ thanh toán.
 * - Đã tích hợp in-app realtime notifications giống bên phục vụ.
 * - Khi ấn bàn trống sẽ mở dialog đặt trước (giống bên phục vụ).
 *
 * Lưu ý: một số Activity (HistoryActivity, InvoiceActivity) cần tồn tại trong project.
 */
public class ThuNganActivity extends BaseMenuActivity {

    private static final String TAG = "ThuNganActivity";

    // --- NEW: ID cho menu item "Đặt Bàn Trước" thêm động ---
    private static final int MENU_ID_RESERVE_TABLE = 1000001;

    // Views
    private NotificationManager notificationManager;
    private ReservationHelper reservationHelper;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;
    private ProgressBar progressBar;
    private RecyclerView rvFloor1, rvFloor2;
    private LinearLayout headerFloor1, headerFloor2;

    // Data & Adapters
    private ThuNganAdapter adapterFloor1;
    private ThuNganAdapter adapterFloor2;
    private ThuNganViewModel viewModel;
    private OrderRepository orderRepository;
    private TableRepository tableRepository; // <-- for loading tables when reserving

    // Use the app-wide SocketManager implementation (phucvu socket)
    private final SocketManager socketManager = SocketManager.getInstance();
    private SocketManager.OnEventListener socketListener;

    // Default socket URL (used if Intent doesn't provide socketUrl)
    private String defaultSocketUrl = "http://192.168.1.84:3000";

    private Handler refreshHandler;
    private static final long SOCKET_REFRESH_DELAY_MS = 5000;
    private BroadcastReceiver refreshTablesReceiver;
    private static final String ACTION_REFRESH_TABLES = "com.ph48845.datn_qlnh_rmis.ACTION_REFRESH_TABLES";
    private Map<String, String> userIdToNameMap = new HashMap<>(); // Map user ID -> user name
    private ActivityResultLauncher<Intent> invoiceLauncher; // Launcher để mở InvoiceActivity và nhận kết quả
    private Set<String> knownTempCalcRequestOrderIds = new HashSet<>(); // Lưu các order IDs đã có yêu cầu tạm tính để phát hiện yêu cầu mới
    private final Map<Integer, Long> knownTempCalcRequestTableTimestamps = new HashMap<>(); // dedupe by table
    private static final long TEMP_REQUEST_DEDUPE_MS = 30_000; // 30s avoid duplicate notifications for same table
    private AlertDialog currentNotificationDialog; // Dialog thông báo hiện tại (để tránh hiển thị nhiều dialog cùng lúc)
    // Bàn -> orderId đã click
    private final Map<String, String> tableClickedOrderMap = new HashMap<>();
    // Lưu trạng thái bàn có order hay không (lần load trước)
    private final Map<String, Boolean> tableHasOrderMap = new HashMap<>();
    private final Map<String, TableItem.ViewState> tableViewStateMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thu_ngan);

        // 1. Khởi tạo ViewModel & Repository
        viewModel = new ThuNganViewModel();
        orderRepository = new OrderRepository();
        tableRepository = new TableRepository(); // initialize repository for reserve dialog
        refreshHandler = new Handler(Looper.getMainLooper());

        // 2. Ánh xạ View & Setup giao diện
        initViews();
        applyNavigationViewInsets();
        setupToolbar();
        setupNavigationDrawer();
        setupRecyclerViews();

        // Khởi tạo ReservationHelper (needs progressBar)
        reservationHelper = new ReservationHelper(this, tableRepository, progressBar);

        // Khởi tạo in-app NotificationManager (same as phục vụ)
        initNotificationManager();

        // Prepare socket URL (Intent override or default)
        String socketUrl = null;
        try {
            if (getIntent() != null) {
                socketUrl = getIntent().getStringExtra("socketUrl");
            }
            if (socketUrl == null || socketUrl.trim().isEmpty()) {
                socketUrl = defaultSocketUrl;
            }

            if (isProbablyEmulator()) {
                try {
                    String replaced = replaceHostForEmulator(socketUrl);
                    Log.i(TAG, "Emulator detected - using socket URL: " + replaced);
                    socketUrl = replaced;
                } catch (Exception e) {
                    Log.w(TAG, "Failed to adapt socketUrl for emulator: " + e.getMessage(), e);
                }
            }

            // Init socketManager with computed URL (safe: SocketManager.init is idempotent)
            socketManager.init(socketUrl);
        } catch (Exception e) {
            Log.w(TAG, "SocketManager.init failed in onCreate", e);
        }

        // Setup socket listener object (but don't register yet, register in onStart)
        socketListener = new SocketManager.OnEventListener() {
            @Override
            public void onOrderCreated(JSONObject payload) {
                Log.d(TAG, "socket:onOrderCreated: " + payload);
                try {
                    runOnUiThread(() -> {
                        checkForNewTempCalculationRequest(payload);
                        scheduleRefresh();
                        loadTempCalculationRequestsCount();

                        // Show lightweight in-app banner for new order (optional)
                        try {
                            int tableNum = payload != null ? payload.optInt("tableNumber", -1) : -1;
                            int itemCount = 0;
                            if (payload != null && payload.has("items")) {
                                org.json.JSONArray items = payload.optJSONArray("items");
                                if (items != null) itemCount = items.length();
                            }
                            InAppNotification notif = new InAppNotification.Builder(
                                    InAppNotification.Type.ORDER_NEW,
                                    "🍽️ Đơn hàng mới",
                                    "Bàn " + (tableNum > 0 ? tableNum : "") + " đặt " + itemCount + " món"
                            )
                                    .actionData("table:" + tableNum + (payload != null ? ":order:" + payload.optString("_id", "") : ""))
                                    .duration(5000)
                                    .build();
                            if (notificationManager != null) NotificationManager.getInstance().show(notif);
                        } catch (Throwable t) {
                            Log.w(TAG, "orderCreated: fail to show banner", t);
                        }
                    });
                } catch (Exception e) {
                    Log.w(TAG, "onOrderCreated handler error", e);
                }
            }

            @Override
            public void onOrderUpdated(JSONObject payload) {
                Log.d(TAG, "socket:onOrderUpdated: " + payload);
                try {
                    runOnUiThread(() -> {
                        checkForNewTempCalculationRequest(payload);
                        scheduleRefresh();
                        loadTempCalculationRequestsCount();

                        // Optional banner for order updated
                        try {
                            int tableNum = payload != null ? payload.optInt("tableNumber", -1) : -1;
                            String status = payload != null ? payload.optString("status", "") : "";
                            String title = "📝 Cập nhật đơn";
                            String message = "Bàn " + (tableNum > 0 ? tableNum : "") + " - " + getStatusText(status);
                            InAppNotification notif = new InAppNotification.Builder(
                                    InAppNotification.Type.ORDER_UPDATED,
                                    title,
                                    message
                            ).actionData("table:" + tableNum + (payload != null ? ":order:" + payload.optString("_id", "") : ""))
                                    .duration(4000)
                                    .build();
                            if (notificationManager != null) NotificationManager.getInstance().show(notif);
                        } catch (Throwable t) {
                            Log.w(TAG, "orderUpdated: fail to show banner", t);
                        }
                    });
                } catch (Exception e) {
                    Log.w(TAG, "onOrderUpdated handler error", e);
                }
            }

            @Override
            public void onTableUpdated(JSONObject payload) {
                Log.d(TAG, "socket:onTableUpdated: " + payload);
                try {
                    runOnUiThread(() -> {
                        // Optional: show table updated banner
                        try {
                            int tableNum = payload != null ? (payload.has("tableNumber") ? payload.optInt("tableNumber", -1) : payload.optInt("table", -1)) : -1;
                            String status = payload != null ? payload.optString("status", "") : "";
                            if (tableNum > 0 && status != null && !status.isEmpty()) {
                                InAppNotification notif = new InAppNotification.Builder(
                                        InAppNotification.Type.TABLE_UPDATED,
                                        "🪑 Cập nhật bàn",
                                        "Bàn " + tableNum + " - " + getTableStatusText(status)
                                )
                                        .actionData("table:" + tableNum)
                                        .duration(3500)
                                        .build();
                                if (notificationManager != null) NotificationManager.getInstance().show(notif);
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "tableUpdated: fail to show banner", t);
                        }

                        // Refresh UI to reflect table state change
                        scheduleRefresh();
                    });
                } catch (Exception e) {
                    Log.w(TAG, "onTableUpdated handler error", e);
                }
            }

            @Override
            public void onCheckItemsRequest(JSONObject payload) {
                Log.d(TAG, "socket:onCheckItemsRequest: " + payload);
                try {
                    runOnUiThread(() -> {
                        // Show both banner and dialog flow: the check detection will be handled by checkForNewTempCalculationRequest()
                        checkForNewTempCalculationRequest(payload);
                        scheduleRefresh();
                        loadTempCalculationRequestsCount();

                        // Banner
                        try {
                            int tableNum = payload != null ? payload.optInt("tableNumber", -1) : -1;
                            InAppNotification notif = new InAppNotification.Builder(
                                    InAppNotification.Type.CHECK_ITEMS,
                                    "🔍 Yêu cầu kiểm đồ",
                                    "Bàn " + (tableNum > 0 ? tableNum : "") + " yêu cầu kiểm đồ"
                            )
                                    .actionData("check:" + tableNum)
                                    .duration(8000)
                                    .build();
                            if (notificationManager != null) NotificationManager.getInstance().show(notif);
                        } catch (Throwable t) {
                            Log.w(TAG, "checkItemsRequest: fail to show banner", t);
                        }
                    });
                } catch (Exception e) {
                    Log.w(TAG, "onCheckItemsRequest handler error", e);
                }
            }

            @Override
            public void onConnect() {
                Log.d(TAG, "Socket connected (ThuNganActivity listener)");
                // Sync state on connect: call REST to refresh lists
                runOnUiThread(() -> {
                    loadActiveTables();
                    loadTempCalculationRequestsCount();
                });
            }

            @Override
            public void onDisconnect() {
                Log.d(TAG, "Socket disconnected (ThuNganActivity listener)");
            }

            @Override
            public void onError(Exception e) {
                Log.w(TAG, "Socket error (ThuNganActivity listener): " + (e != null ? e.getMessage() : "null"), e);
            }
        };

        // 3. Load dữ liệu ban đầu
        updateNavHeaderInfo();
        loadActiveTables();
        loadUsersForNameMapping(); // Load danh sách users để map ID -> name
        loadTempCalculationRequestsCount();

        registerRefreshTablesReceiver();

        // Prepare invoice launcher (moved here to keep onCreate tidy)
        invoiceLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // Khi quay lại từ InvoiceActivity, reload lại danh sách yêu cầu tạm tính
                    Log.d(TAG, "invoiceLauncher: Returned from InvoiceActivity, resultCode=" + result.getResultCode());
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        boolean invoicePrinted = data != null && data.getBooleanExtra("invoicePrinted", false);
                        if (invoicePrinted) {
                            Log.d(TAG, "invoiceLauncher: Invoice was printed, will reload temp calculation requests after delay");
                            loadTempCalculationRequestsCount();
                            refreshHandler.postDelayed(() -> {
                                showTempCalculationRequests();
                            }, 1500);
                        } else {
                            loadTempCalculationRequestsCount();
                        }
                    } else {
                        loadTempCalculationRequestsCount();
                    }
                }
        );
    }

    private void initNotificationManager() {
        try {
            notificationManager = NotificationManager.getInstance();
            notificationManager.init(this, new InAppNotificationView.OnNotificationClickListener() {
                @Override
                public void onNotificationClick(InAppNotification notification) {
                    if (notification == null) return;
                    String actionData = notification.getActionData();
                    if (actionData == null || actionData.isEmpty()) return;
                    try {
                        String[] parts = actionData.split(":");
                        if (parts.length >= 2 && "table".equals(parts[0])) {
                            int tableNumber = Integer.parseInt(parts[1]);
                            Intent intent = new Intent(ThuNganActivity.this, com.ph48845.datn_qlnh_rmis.ui.phucvu.OrderActivity.class);
                            intent.putExtra("tableNumber", tableNumber);
                            if (parts.length >= 4 && "order".equals(parts[2])) {
                                intent.putExtra("orderId", parts[3]);
                            }
                            startActivity(intent);
                        } else if (parts.length >= 1 && "check".equals(parts[0])) {
                            showTempCalculationRequests();
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "onNotificationClick: failed to handle actionData", e);
                    }
                }

                @Override
                public void onNotificationDismissed(InAppNotification notification) {
                    Log.d(TAG, "Notification dismissed: " + (notification != null ? notification.getTitle() : "null"));
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "initNotificationManager failed", e);
            notificationManager = null;
        }
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


    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout_thungan);
        navigationView = findViewById(R.id.navigationView_thungan);
        toolbar = findViewById(R.id.toolbar);

        progressBar = findViewById(R.id.progress_bar_loading);

        rvFloor1 = findViewById(R.id.recycler_floor1);
        rvFloor2 = findViewById(R.id.recycler_floor2);

        headerFloor1 = findViewById(R.id.header_floor1);
        headerFloor2 = findViewById(R.id.header_floor2);

    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }
        toolbar.setNavigationIcon(null);
    }

    private void setupNavigationDrawer() {
        // Xử lý nút Menu (Hamburger icon)
        ImageView navIcon = findViewById(R.id.nav_icon);
        if (navIcon != null && drawerLayout != null) {
            navIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        }

        if (navigationView != null) {
            // Format lại font chữ cho menu
            for (int i = 0; i < navigationView.getMenu().size(); i++) {
                MenuItem menuItem = navigationView.getMenu().getItem(i);
                SpannableString spanString = new SpannableString(menuItem.getTitle().toString());
                spanString.setSpan(new RelativeSizeSpan(1.1f), 0, spanString.length(), 0);
                menuItem.setTitle(spanString);
            }

            // --- NEW: thêm nút "Đặt Bàn Trước" cho role Thu ngân nếu cần ---
            // Thay thế phần thêm menu động trong setupNavigationDrawer() bằng đoạn này
// --- Đặt logout luôn ở cuối và chèn "Đặt Bàn Trước" bên trên nó ---
            try {
                SharedPreferences prefs = getSharedPreferences("RestaurantPrefs", MODE_PRIVATE);
                String rawRole = prefs.getString("userRole", "");
                String normalizedRole = normalizeRoleString(rawRole);

                // Fallback: lấy role hiển thị trong header nếu có
                String headerRole = "";
                try {
                    View header = navigationView.getHeaderView(0);
                    if (header != null) {
                        TextView tvRole = header.findViewById(R.id.textViewRole);
                        if (tvRole != null && tvRole.getText() != null) {
                            headerRole = tvRole.getText().toString();
                        }
                    }
                } catch (Exception ignored) {}

                String normalizedHeaderRole = normalizeRoleString(headerRole);

                boolean isCashier = false;
                if (!normalizedRole.isEmpty()) {
                    isCashier = normalizedRole.equals("cashier")
                            || normalizedRole.equals("thungan")
                            || normalizedRole.equals("thu ngan")
                            || normalizedRole.equals("thu_ngan")
                            || (normalizedRole.contains("thu") && normalizedRole.contains("ngan"));
                }
                if (!isCashier && !normalizedHeaderRole.isEmpty()) {
                    isCashier = normalizedHeaderRole.equals("cashier")
                            || normalizedHeaderRole.equals("thungan")
                            || normalizedHeaderRole.equals("thu ngan")
                            || normalizedHeaderRole.equals("thu_ngan")
                            || (normalizedHeaderRole.contains("thu") && normalizedHeaderRole.contains("ngan"));
                }

                Log.d(TAG, "setupNavigationDrawer: roleRaw='" + rawRole + "' normalized='" + normalizedRole +
                        "', headerRaw='" + headerRole + "' normalizedHeader='" + normalizedHeaderRole +
                        "', isCashier=" + isCashier);

                Menu navMenu = navigationView.getMenu();

                // 1) Lưu title + icon của logout (nếu tồn tại), rồi remove item
                MenuItem logoutItem = navMenu.findItem(R.id.nav_logout);
                CharSequence logoutTitle = "Đăng xuất";
                android.graphics.drawable.Drawable logoutIcon = null;
                if (logoutItem != null) {
                    try {
                        logoutTitle = logoutItem.getTitle();
                        logoutIcon = logoutItem.getIcon();
                    } catch (Exception ignored) {}
                    navMenu.removeItem(R.id.nav_logout);
                }

                // 2) Re-add logout với order cao để luôn ở cuối
                final int LOGOUT_ORDER = 9999;
                MenuItem newLogout = navMenu.add(Menu.NONE, R.id.nav_logout, LOGOUT_ORDER, logoutTitle);
                if (logoutIcon != null) {
                    newLogout.setIcon(logoutIcon);
                }

                // 3) Nếu là Thu ngân thì thêm Đặt Bàn Trước với order = LOGOUT_ORDER - 1, tránh duplicate
                if (isCashier) {
                    if (navMenu.findItem(MENU_ID_RESERVE_TABLE) == null) {
                        int reserveOrder = Math.max(0, LOGOUT_ORDER - 1);
                        MenuItem reserveItem = navMenu.add(Menu.NONE, MENU_ID_RESERVE_TABLE, reserveOrder, "Đặt Bàn Trước");
                        reserveItem.setIcon(android.R.drawable.ic_menu_my_calendar);
                        reserveItem.setCheckable(false);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "setupNavigationDrawer: failed to add Đặt Bàn Trước menu item: " + e.getMessage(), e);
            }
            // Xử lý sự kiện chọn menu
            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_mood) {
                    showMoodDialog();
                } else if (id == R.id.nav_contact) {
                    showContactDialog();
                } else if (id == R.id.nav_temp_calculation_requests) {
                    showTempCalculationRequests(); // Gọi hàm đã được khôi phục
                } else if (id == R.id.nav_logout) {
                    logout();
                } else if (id == R.id.nav_payment_history) {
                    // Cần Activity xem lịch sử thanh toán
                    startActivity(new Intent(ThuNganActivity.this, HistoryActivity.class));
                    Toast.makeText(ThuNganActivity.this, "Chức năng Lịch sử thanh toán", Toast.LENGTH_SHORT).show();
                } else if (id == R.id.nav_revenue) {
                    startActivity(new Intent(ThuNganActivity.this, ReportActivity.class));
                } else if (id == MENU_ID_RESERVE_TABLE) {
                    // Xử lý khi bấm "Đặt Bàn Trước" -> dùng DialogFragment
                    showReserveDialog();
                }

                if (drawerLayout != null) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
                return true;
            });
        }
    }

    private void setupRecyclerViews() {
        // Sử dụng Grid 3 cột cho cả 2 tầng
        rvFloor1.setLayoutManager(new GridLayoutManager(this, 3));
        rvFloor2.setLayoutManager(new GridLayoutManager(this, 3));

        rvFloor1.setNestedScrollingEnabled(false);
        rvFloor2.setNestedScrollingEnabled(false);

        // Sự kiện click vào bàn -> Mở màn hình hóa đơn (với logic đặt trước khi bàn trống)
        ThuNganAdapter.OnTableClickListener listener = table -> {
            if (table == null) return;

            // If table is AVAILABLE, show confirmation then reservation form
            boolean isAvailable = false;
            try {
                isAvailable = table.getStatus() == TableItem.Status.AVAILABLE;
            } catch (Exception ignored) { }

            if (isAvailable) {
                new AlertDialog.Builder(ThuNganActivity.this)
                        .setTitle("Xác nhận đặt bàn")
                        .setMessage("Bạn có muốn đặt trước Bàn " + table.getTableNumber() + " ?")
                        .setPositiveButton("Đặt", (d, w) -> {
                            if (reservationHelper == null) reservationHelper = new ReservationHelper(ThuNganActivity.this, tableRepository, progressBar);
                            reservationHelper.showReservationDialogWithPickers(table);
                        })
                        .setNegativeButton("Hủy", null)
                        .show();
                return;
            }

            table.setViewState(TableItem.ViewState.SEEN);
            tableViewStateMap.put(table.getId(), TableItem.ViewState.SEEN);

            adapterFloor1.notifyDataSetChanged();
            adapterFloor2.notifyDataSetChanged();

            Intent intent = new Intent(ThuNganActivity.this, InvoiceActivity.class);
            intent.putExtra("tableNumber", table.getTableNumber());
            startActivity(intent);
        };

        // Khởi tạo Adapter
        adapterFloor1 = new ThuNganAdapter(this, new ArrayList<>(), listener);
        adapterFloor2 = new ThuNganAdapter(this, new ArrayList<>(), listener);

        rvFloor1.setAdapter(adapterFloor1);
        rvFloor2.setAdapter(adapterFloor2);
    }

    private void loadActiveTables() {
        progressBar.setVisibility(View.VISIBLE);

        viewModel.loadActiveTables(new ThuNganViewModel.DataCallback() {
            @Override
            public void onTablesLoaded(List<TableItem> floor1Tables, List<TableItem> floor2Tables) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    // 🔥 RESTORE VIEW STATE
                    restoreViewState(floor1Tables);
                    restoreViewState(floor2Tables);

                    adapterFloor1.updateList(floor1Tables);
                    adapterFloor2.updateList(floor2Tables);

                    if (headerFloor1 != null)
                        headerFloor1.setVisibility(floor1Tables.isEmpty() ? View.GONE : View.VISIBLE);
                    if (headerFloor2 != null)
                        headerFloor2.setVisibility(floor2Tables.isEmpty() ? View.GONE : View.VISIBLE);

                    // Check trạng thái món ăn để đổi màu thẻ (Đỏ -> Cam)
                    loadOrdersForServingStatus(floor1Tables, floor2Tables);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(ThuNganActivity.this, "Lỗi tải dữ liệu: " + message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void restoreViewState(List<TableItem> tables) {
        if (tables == null) return;
        for (TableItem table : tables) {

            String tableId = table.getId();

            // Hiện tại bàn có order hay không
            boolean hasOrderNow = table.getStatus() != TableItem.Status.EMPTY;

            // Trạng thái trước đó
            boolean hadOrderBefore = tableHasOrderMap.getOrDefault(tableId, false);

            // 🔥 CASE 1: VỪA CÓ ORDER MỚI (sau thanh toán)
            if (!hadOrderBefore && hasOrderNow) {
                table.setViewState(TableItem.ViewState.UNSEEN);
                tableViewStateMap.remove(tableId);
            }
            // 🔥 CASE 2: BÀN VỪA THANH TOÁN XONG (trở về trống)
            else if (hadOrderBefore && !hasOrderNow) {
                table.setViewState(TableItem.ViewState.UNSEEN);
                tableViewStateMap.remove(tableId);
            }
            // 🔴 CASE 3: ĐÃ CLICK → GIỮ ĐỎ
            else if (tableViewStateMap.containsKey(tableId)) {
                table.setViewState(tableViewStateMap.get(tableId));
            }
            // 🟢 CASE 4: MẶC ĐỊNH
            else {
                table.setViewState(TableItem.ViewState.UNSEEN);
            }

            // 🔄 Cập nhật lại trạng thái
            tableHasOrderMap.put(tableId, hasOrderNow);
        }
    }

    private void loadOrdersForServingStatus(List<TableItem> floor1Tables, List<TableItem> floor2Tables) {
        // Dùng getAllOrders để đảm bảo lấy đủ dữ liệu từ server
        orderRepository.getAllOrders(new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> allOrders) {
                if (allOrders == null) {
                    Log.w(TAG, "loadOrdersForServingStatus: Received null orders");
                    return;
                }
                Log.d(TAG, "loadOrdersForServingStatus: Received " + allOrders.size() + " orders from server");

                Map<Integer, List<Order>> ordersByTable = new HashMap<>();
                for (Order order : allOrders) {
                    if (order == null) continue;
                    // order.normalizeItems(); // Bỏ comment nếu bạn cần chuẩn hóa OrderItem

                    int tNum = order.getTableNumber();
                    if (!ordersByTable.containsKey(tNum)) {
                        ordersByTable.put(tNum, new ArrayList<>());
                    }
                    ordersByTable.get(tNum).add(order);
                }

                List<TableItem> allTables = new ArrayList<>();
                if (floor1Tables != null) allTables.addAll(floor1Tables);
                if (floor2Tables != null) allTables.addAll(floor2Tables);

                boolean needUpdate = false;

                // Giả sử bạn có adapter tên adapterFloor1 / adapterFloor2
                for (TableItem table : allTables) {
                    List<Order> tableOrders = ordersByTable.get(table.getTableNumber());

                    boolean allServed = determineIfAllServed(tableOrders);

                    // Lưu trạng thái món đã lên hết vào adapter map
                    adapterFloor1.updateFullServingStatus(table.getTableNumber(), allServed);
                    adapterFloor2.updateFullServingStatus(table.getTableNumber(), allServed);

                    // Nếu đã đủ món -> Đổi trạng thái sang FINISH_SERVE
                    if (allServed && table.getStatus() != TableItem.Status.FINISH_SERVE) {
                        table.setStatus(TableItem.Status.FINISH_SERVE);
                        needUpdate = true;
                    }
                }

                if (needUpdate) {
                    runOnUiThread(() -> {
                        adapterFloor1.notifyDataSetChanged();
                        adapterFloor2.notifyDataSetChanged();
                    });
                }
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Lỗi check trạng thái món: " + message);
            }
        });
    }

    private boolean determineIfAllServed(List<Order> orders) {
        if (orders == null || orders.isEmpty()) return false;

        for (Order order : orders) {
            if (order == null || order.getItems() == null) continue;

            for (Order.OrderItem item : order.getItems()) {
                if (item == null) continue;

                String status = item.getStatus();
                if (status == null) return false;

                // ❗ CHỈ CẦN 1 MÓN CHƯA READY → FALSE
                if (!status.equalsIgnoreCase("ready")) {
                    return false;
                }
            }
        }
        return true; // ✅ tất cả món đều ready
    }

    // =========================================================================
    // KHÔI PHỤC HÀM BỊ MẤT (FIX LỖI)
    // =========================================================================

    /**
     * Tải và hiển thị danh sách yêu cầu tạm tính khi click từ menu.
     */
    private void showTempCalculationRequests() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        // Đảm bảo load users trước khi load orders
        loadUsersForNameMapping(() -> {
            // Sau khi load users xong, mới load orders
            // Dùng getAllOrders để đảm bảo lấy tất cả orders mới nhất từ server (force refresh)
            Log.d(TAG, "showTempCalculationRequests: Loading all orders from server (force refresh)");
            orderRepository.getAllOrders(new OrderRepository.RepositoryCallback<List<Order>>() {
                @Override
                public void onSuccess(List<Order> allOrders) {
                    runOnUiThread(() -> {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);

                        // Lọc các orders có tempCalculationRequestedAt (KHÔNG NULL và KHÔNG RỖNG)
                        List<Order> tempCalculationOrders = new ArrayList<>();
                        if (allOrders != null) {
                            Log.d(TAG, "showTempCalculationRequests: Checking " + allOrders.size() + " orders for temp calculation requests");
                            for (Order order : allOrders) {
                                if (order == null) continue;
                                String tempCalcRequestedAt = order.getTempCalculationRequestedAt();
                                String orderStatus = order.getOrderStatus();
                                String orderId = order.getId();
                                int tableNumber = order.getTableNumber();

                                // CHỈ thêm vào danh sách nếu:
                                // 1. tempCalculationRequestedAt không null và không rỗng
                                // 2. orderStatus KHÔNG phải "temp_bill_printed" (đã in hóa đơn rồi)
                                // (Khi in hóa đơn, tempCalculationRequestedAt sẽ được set null và orderStatus = "temp_bill_printed")
                                boolean hasTempCalcRequest = tempCalcRequestedAt != null && !tempCalcRequestedAt.trim().isEmpty();
                                boolean isNotPrinted = orderStatus == null || !orderStatus.equals("temp_bill_printed");

                                if (hasTempCalcRequest && isNotPrinted) {
                                    tempCalculationOrders.add(order);
                                    Log.d(TAG, "showTempCalculationRequests: ✅ Found temp calc request for order " + orderId +
                                            " (table " + tableNumber + "), tempCalculationRequestedAt=" + tempCalcRequestedAt +
                                            ", orderStatus=" + orderStatus);
                                } else {
                                    if (!hasTempCalcRequest) {
                                        Log.d(TAG, "showTempCalculationRequests: Order " + orderId + " (table " + tableNumber +
                                                ") has no temp calc request (tempCalculationRequestedAt=" + tempCalcRequestedAt +
                                                ", orderStatus=" + orderStatus + ")");
                                    } else if (!isNotPrinted) {
                                        Log.d(TAG, "showTempCalculationRequests: Order " + orderId + " (table " + tableNumber +
                                                ") already printed (orderStatus=" + orderStatus + "), skipping");
                                    }
                                }
                            }
                        }

                        Log.d(TAG, "showTempCalculationRequests: 📊 Summary - Found " + tempCalculationOrders.size() +
                                " temp calculation requests out of " + (allOrders != null ? allOrders.size() : 0) + " total orders");

                        if (tempCalculationOrders.isEmpty()) {
                            Toast.makeText(ThuNganActivity.this, "Không có yêu cầu tạm tính nào", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Hiển thị dialog với danh sách yêu cầu
                        showTempCalculationRequestsDialog(tempCalculationOrders);
                    });
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        Toast.makeText(ThuNganActivity.this, "Lỗi tải yêu cầu tạm tính: " + message, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }

    /**
     * Load danh sách users để map ID -> name
     * @param callback Callback được gọi sau khi load xong (có thể null)
     */
    private void loadUsersForNameMapping(Runnable callback) {
        ApiService api = RetrofitClient.getInstance().getApiService();
        api.getAllUsers().enqueue(new Callback<ApiResponse<List<User>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<User>>> call, Response<ApiResponse<List<User>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<List<User>> apiResponse = response.body();
                    List<User> users = apiResponse.getData();

                    if (users != null && !users.isEmpty()) {
                        userIdToNameMap.clear();
                        for (User user : users) {
                            if (user != null && user.getId() != null) {
                                String userId = user.getId().trim();

                                // Ưu tiên fullName (từ field "name" trong JSON), nếu không có thì dùng username
                                String name = user.getFullName();
                                if (name == null || name.trim().isEmpty()) {
                                    name = user.getUsername();
                                }

                                if (name != null && !name.trim().isEmpty()) {
                                    // Normalize: trim cả key và value
                                    userIdToNameMap.put(userId, name.trim());
                                    Log.d(TAG, "loadUsersForNameMapping: Mapped userId '" + userId + "' -> '" + name.trim() + "'");
                                } else {
                                    Log.w(TAG, "loadUsersForNameMapping: User " + userId + " has no name or username");
                                }
                            } else {
                                Log.w(TAG, "loadUsersForNameMapping: Skipping null user or user with null ID");
                            }
                        }
                        Log.d(TAG, "loadUsersForNameMapping: Loaded " + userIdToNameMap.size() + " users for name mapping");
                        // Log một vài entries để debug
                        int count = 0;
                        for (Map.Entry<String, String> entry : userIdToNameMap.entrySet()) {
                            if (count++ < 5) {
                                Log.d(TAG, "loadUsersForNameMapping: Sample entry - ID: '" + entry.getKey() + "', Name: '" + entry.getValue() + "'");
                            }
                        }
                    } else {
                        Log.e(TAG, "loadUsersForNameMapping: Response data is null or empty. Success: " + apiResponse.isSuccess() + ", Message: " + apiResponse.getMessage());
                    }
                } else {
                    Log.e(TAG, "loadUsersForNameMapping: Response not successful. Code: " + (response != null ? response.code() : "null"));
                }
                // Gọi callback sau khi load xong (dù thành công hay thất bại)
                if (callback != null) {
                    runOnUiThread(callback);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<User>>> call, Throwable t) {
                Log.e(TAG, "loadUsersForNameMapping: Failed to load users: " + t.getMessage(), t);
                // Vẫn gọi callback dù thất bại
                if (callback != null) {
                    runOnUiThread(callback);
                }
            }
        });
    }

    /**
     * Overload method không có callback (để tương thích với code cũ)
     */
    private void loadUsersForNameMapping() {
        loadUsersForNameMapping(null);
    }

    /**
     * Lấy tên nhân viên từ Order
     * Xử lý cả trường hợp server trả về Map (object) hoặc String (ID)
     */
    private String getEmployeeNameFromOrder(Order order) {
        if (order == null) {
            Log.w(TAG, "getEmployeeNameFromOrder: order is null");
            return "Nhân viên";
        }

        // Bước 1: Thử lấy ID từ getTempCalculationRequestedById() (ưu tiên)
        String userId = order.getTempCalculationRequestedById();
        Log.d(TAG, "getEmployeeNameFromOrder: getTempCalculationRequestedById() returned: '" + userId + "'");

        if (userId != null && !userId.trim().isEmpty()) {
            userId = userId.trim();

            // Tra cứu trong map (đã được normalize khi load)
            String name = userIdToNameMap.get(userId);
            Log.d(TAG, "getEmployeeNameFromOrder: Looking up userId '" + userId + "' in map");
            Log.d(TAG, "getEmployeeNameFromOrder: Map size: " + userIdToNameMap.size());

            if (name != null && !name.trim().isEmpty()) {
                Log.d(TAG, "getEmployeeNameFromOrder: ✓ Found name: '" + name + "' for userId: '" + userId + "'");
                return name.trim();
            } else {
                // Thử tìm với các biến thể của ID (nếu có)
                Log.w(TAG, "getEmployeeNameFromOrder: ✗ UserId '" + userId + "' not found in map");
                Log.d(TAG, "getEmployeeNameFromOrder: Available keys in map (first 10): " +
                        userIdToNameMap.keySet().stream().limit(10).collect(java.util.stream.Collectors.toList()));
            }
        }

        // Bước 2: Fallback - thử lấy từ getTempCalculationRequestedBy()
        String requester = order.getTempCalculationRequestedBy();
        Log.d(TAG, "getEmployeeNameFromOrder: getTempCalculationRequestedBy() returned: '" + requester + "'");

        if (requester != null && !requester.trim().isEmpty()) {
            requester = requester.trim();

            // Nếu có khoảng trắng, có vẻ đã là tên rồi (full name như "Nhân viên 2")
            if (requester.contains(" ")) {
                Log.d(TAG, "getEmployeeNameFromOrder: ✓ Requester contains space, assuming it's a name: '" + requester + "'");
                return requester;
            }

            // Thử check xem có trong map không (có thể là ID)
            String name = userIdToNameMap.get(requester);
            if (name != null && !name.trim().isEmpty()) {
                Log.d(TAG, "getEmployeeNameFromOrder: ✓ Found name from requester in map: '" + name + "'");
                return name.trim();
            }

            Log.w(TAG, "getEmployeeNameFromOrder: ✗ Requester '" + requester + "' not found in map");
        }

        Log.w(TAG, "getEmployeeNameFromOrder: ✗ Could not find employee name, returning default 'Nhân viên'");
        return "Nhân viên";
    }

    /**
     * Lấy tên nhân viên từ ID
     */
    private String getEmployeeName(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return "Nhân viên";
        }

        // Kiểm tra trong map
        String name = userIdToNameMap.get(userId.trim());
        if (name != null && !name.trim().isEmpty()) {
            return name;
        }

        // Nếu không tìm thấy, trả về "Nhân viên" thay vì ID
        return "Nhân viên";
    }

    /**
     * Hiển thị dialog danh sách yêu cầu tạm tính.
     */
    private void showTempCalculationRequestsDialog(List<Order> orders) {
        // Tạo danh sách hiển thị với format rõ ràng, luôn bao gồm tên nhân viên
        String[] items = new String[orders.size()];
        for (int i = 0; i < orders.size(); i++) {
            Order order = orders.get(i);

            // Thông tin bàn
            String tableInfo = "Bàn " + order.getTableNumber();

            // Thông tin thời gian
            String timeInfo = "";
            if (order.getTempCalculationRequestedAt() != null) {
                try {
                    java.text.SimpleDateFormat inputFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
                    java.text.SimpleDateFormat outputFormat = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault());
                    java.util.Date date = inputFormat.parse(order.getTempCalculationRequestedAt());
                    timeInfo = outputFormat.format(date);
                } catch (Exception e) {
                    timeInfo = order.getTempCalculationRequestedAt();
                }
            }

            // Lấy tên nhân viên yêu cầu (luôn có giá trị, ít nhất là "Nhân viên")
            String requesterName = getEmployeeNameFromOrder(order);
            if (requesterName == null || requesterName.trim().isEmpty()) {
                requesterName = "Nhân viên";
            }

            // Format hiển thị: "Bàn X - DD/MM/YYYY HH:mm • NV: Tên nhân viên"
            // Luôn hiển thị tên nhân viên để người dùng biết ai yêu cầu
            StringBuilder displayText = new StringBuilder();
            displayText.append(tableInfo);

            if (!timeInfo.isEmpty()) {
                displayText.append(" - ").append(timeInfo);
            }

            // Luôn thêm thông tin nhân viên
            displayText.append(" • NV: ").append(requesterName);

            items[i] = displayText.toString();
        }

        new android.app.AlertDialog.Builder(this)
                .setTitle("Yêu cầu tạm tính (" + orders.size() + ")")
                .setItems(items, (dialog, which) -> {
                    // Mở đúng hóa đơn (theo orderId) trong cùng bàn
                    Order selectedOrder = orders.get(which);
                    if (selectedOrder == null) {
                        Toast.makeText(ThuNganActivity.this, "Hóa đơn không hợp lệ", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent intent = new Intent(ThuNganActivity.this, InvoiceActivity.class);
                    intent.putExtra("tableNumber", selectedOrder.getTableNumber());
                    intent.putExtra("orderId", selectedOrder.getId()); // focus đúng hóa đơn
                    // Sử dụng launcher để có thể nhận kết quả khi quay lại
                    invoiceLauncher.launch(intent);
                    Log.d(TAG, "showTempCalculationRequestsDialog: Opening InvoiceActivity for table " +
                            selectedOrder.getTableNumber() + ", orderId: " + selectedOrder.getId());
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    // =========================================================================
    // CÁC HÀM HỖ TRỢ VỀ THÔNG TIN & MENU
    // =========================================================================

    private void updateNavHeaderInfo() {
        if (navigationView != null && navigationView.getHeaderCount() > 0) {
            View headerView = navigationView.getHeaderView(0);
            TextView tvName = headerView.findViewById(R.id.textViewName);
            TextView tvRole = headerView.findViewById(R.id.textViewRole);

            SharedPreferences prefs = getSharedPreferences("RestaurantPrefs", MODE_PRIVATE);
            if (tvName != null) tvName.setText(prefs.getString("fullName", "Người dùng"));
            if (tvRole != null) tvRole.setText(getVietnameseRole(prefs.getString("userRole", "")));
        }
    }

    private String getVietnameseRole(String roleKey) {
        if (roleKey == null) return "";
        switch (roleKey.toLowerCase()) {
            case "cashier": return "Thu ngân";
            case "manager": return "Quản lý";
            case "order": return "Phục vụ";
            case "kitchen": return "Bếp";
            default: return roleKey;
        }
    }

    // --- NEW: helper chuẩn hóa chuỗi role (loại bỏ dấu, lowercase) ---
    private String normalizeRoleString(String s) {
        if (s == null) return "";
        String normalized = Normalizer.normalize(s, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return normalized.trim().toLowerCase();
    }

    // --- Emulator helpers (same approach as MainActivity) ---
    private boolean isProbablyEmulator() {
        String fingerprint = Build.FINGERPRINT;
        String model = Build.MODEL;
        String product = Build.PRODUCT;
        return (fingerprint != null && (fingerprint.contains("generic") || fingerprint.contains("emulator")))
                || (model != null && model.contains("Emulator"))
                || (product != null && product.contains("sdk"));
    }

    private String replaceHostForEmulator(String url) {
        try {
            if (url == null) return url;
            java.net.URI uri = new URI(url);
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

    // --- UPDATED: showReserveDialog now uses ReserveTableDialogFragment ---
    // Thay thế method showReserveDialog() trong ThuNganActivity.java bằng nội dung này
    private void showReserveDialog() {
        try {
            FragmentManager fm = getSupportFragmentManager();
            ReserveTableDialogFragment f = ReserveTableDialogFragment.newInstance();
            // Khi chọn bàn từ fragment, hiển thị dialog xác nhận trước, sau đó mở form đặt trước
            f.setOnTablePickedListener(table -> {
                if (table == null) return;

                // Hiển thị dialog xác nhận: "Xác nhận đặt bàn"
                new AlertDialog.Builder(ThuNganActivity.this)
                        .setTitle("Xác nhận đặt bàn")
                        .setMessage("Bạn có muốn đặt trước Bàn " + table.getTableNumber() + " ?")
                        .setPositiveButton("Đặt", (dialogInterface, i) -> {
                            try {
                                if (reservationHelper == null) {
                                    reservationHelper = new ReservationHelper(ThuNganActivity.this, tableRepository, progressBar);
                                }
                                // Mở form nhập thông tin đặt trước giống bên phục vụ
                                reservationHelper.showReservationDialogWithPickers(table);
                            } catch (Exception e) {
                                Log.e(TAG, "showReserveDialog: failed to open reservation helper", e);
                                Toast.makeText(ThuNganActivity.this, "Lỗi mở form đặt trước: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        })
                        .setNegativeButton("Hủy", (dialogInterface, i) -> {
                            // Do nothing - chỉ đóng xác nhận
                        })
                        .setCancelable(true)
                        .show();
            });
            f.show(fm, "reserveTableDialog");
        } catch (Exception e) {
            Log.e(TAG, "showReserveDialog: error showing fragment", e);
            Toast.makeText(this, "Lỗi khi mở dialog đặt bàn: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadTempCalculationRequestsCount() {
        orderRepository.getOrdersByTableNumber(null, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> allOrders) {
                runOnUiThread(() -> {
                    int count = 0;
                    // Cập nhật danh sách order IDs đã có yêu cầu tạm tính
                    Set<String> currentTempCalcOrderIds = new HashSet<>();
                    if (allOrders != null) {
                        for (Order order : allOrders) {
                            if (order != null && order.getId() != null) {
                                String tempCalcRequestedAt = order.getTempCalculationRequestedAt();
                                String orderStatus = order.getOrderStatus();
                                // Chỉ đếm nếu có yêu cầu tạm tính VÀ chưa in hóa đơn
                                boolean hasTempCalcRequest = tempCalcRequestedAt != null && !tempCalcRequestedAt.trim().isEmpty();
                                boolean isNotPrinted = orderStatus == null || !orderStatus.equals("temp_bill_printed");
                                if (hasTempCalcRequest && isNotPrinted) {
                                    count++;
                                    currentTempCalcOrderIds.add(order.getId());
                                }
                            }
                        }
                    }
                    // Cập nhật danh sách đã biết (để phát hiện yêu cầu mới)
                    knownTempCalcRequestOrderIds = currentTempCalcOrderIds;
                    updateTempCalculationMenuBadge(count);
                });
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Lỗi đếm yêu cầu tạm tính: " + message);
            }
        });
    }

    private void updateTempCalculationMenuBadge(int count) {
        if (navigationView == null) return;
        MenuItem menuItem = navigationView.getMenu().findItem(R.id.nav_temp_calculation_requests);
        if (menuItem != null) {
            String title = "Yêu cầu tạm tính";
            if (count > 0) title += " (" + count + ")";
            SpannableString spanString = new SpannableString(title);
            spanString.setSpan(new RelativeSizeSpan(1.1f), 0, spanString.length(), 0);
            menuItem.setTitle(spanString);

        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Register listener to the global socket manager so this activity receives realtime events
        try {
            if (socketListener != null) socketManager.registerListener(socketListener);
            socketManager.connect(); // ensure connected (MyApplication may have already connected)
        } catch (Exception e) {
            Log.w(TAG, "onStart: failed to register/connect socket listener", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadActiveTables();
        loadTempCalculationRequestsCount();

        // Ensure socket connected (safe no-op if already connected)
        try {
            if (socketManager != null) {
                socketManager.connect();
            }
        } catch (Exception e) {
            Log.w(TAG, "onResume: Socket reconnect failed", e);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unregister listener to avoid leaks / duplicate handling
        try {
            if (socketListener != null) socketManager.unregisterListener(socketListener);
        } catch (Exception e) {
            Log.w(TAG, "onStop: failed to unregister socket listener", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (refreshHandler != null) refreshHandler.removeCallbacksAndMessages(null);
        try {
            if (refreshTablesReceiver != null) unregisterReceiver(refreshTablesReceiver);
        } catch (Exception ignored) {}
        // Do NOT disconnect the global socket here (socket is app-lifetime in MyApplication).
        // Just unregister listener (already done in onStop).
        // cleanup notification manager overlay
        if (notificationManager != null) {
            try { notificationManager.destroy(); } catch (Throwable ignored) {}
            notificationManager = null;
        }
    }

    private void scheduleRefresh() {
        if (refreshHandler == null) return;
        refreshHandler.removeCallbacksAndMessages(null);
        refreshHandler.postDelayed(() -> {
            loadActiveTables();
            loadTempCalculationRequestsCount();
        }, SOCKET_REFRESH_DELAY_MS); // 5 giây
    }

    /**
     * Kiểm tra xem có yêu cầu tạm tính mới từ socket payload không
     * Đã mở rộng: chấp nhận nhiều dạng payload (orderId, orderIds array, tableNumber),
     * log raw payload để debug, dedupe theo orderId / table (30s).
     */
    private void checkForNewTempCalculationRequest(org.json.JSONObject payload) {
        if (payload == null) return;

        try {
            // Debug log raw payload for server mapping
            Log.d(TAG, "checkForNewTempCalculationRequest: raw payload: " + payload.toString());

            // 1) Try find orderId in multiple fields
            String orderId = null;
            if (payload.has("_id")) orderId = payload.optString("_id", null);
            if ((orderId == null || orderId.trim().isEmpty()) && payload.has("id")) orderId = payload.optString("id", null);
            if ((orderId == null || orderId.trim().isEmpty()) && payload.has("orderId")) orderId = payload.optString("orderId", null);
            if ((orderId == null || orderId.trim().isEmpty()) && payload.has("order_id")) orderId = payload.optString("order_id", null);
            if (orderId != null) orderId = orderId.trim();

            // 2) If payload contains orderIds array -> treat each as new request if not known
            org.json.JSONArray arr = null;
            if (payload.has("orderIds")) arr = payload.optJSONArray("orderIds");
            if ((arr == null || arr.length() == 0) && payload.has("orders")) arr = payload.optJSONArray("orders");
            if ((arr == null || arr.length() == 0) && payload.has("orderIds[]")) arr = payload.optJSONArray("orderIds[]");

            if (arr != null && arr.length() > 0) {
                for (int i = 0; i < arr.length(); i++) {
                    String id = null;
                    try { id = String.valueOf(arr.get(i)); } catch (Exception ignored) {}
                    if (id == null) continue;
                    id = id.trim();
                    if (id.isEmpty()) continue;
                    if (!knownTempCalcRequestOrderIds.contains(id)) {
                        knownTempCalcRequestOrderIds.add(id);
                        int tableNumber = payload.optInt("tableNumber", -1);
                        final int finalTableNumber = tableNumber;
                        final String finalOrderId = id;
                        runOnUiThread(() -> showTempCalculationNotification(finalTableNumber, finalOrderId));
                    } else {
                        Log.d(TAG, "checkForNewTempCalculationRequest: orderId in array already known: " + id);
                    }
                }
                return;
            }

            // 3) Get tableNumber if present
            int tableNumber = payload.optInt("tableNumber", -1);

            // 4) Heuristic: decide if payload indicates temp calc request
            boolean looksLikeTempRequest = false;
            if (payload.has("tempCalculationRequestedAt") && !payload.optString("tempCalculationRequestedAt", "").trim().isEmpty()) {
                looksLikeTempRequest = true;
            }
            if (!looksLikeTempRequest) {
                if (payload.has("isTempCalculation") && payload.optBoolean("isTempCalculation", false)) looksLikeTempRequest = true;
                if (payload.has("type") && "temp_calc".equalsIgnoreCase(payload.optString("type", ""))) looksLikeTempRequest = true;
            }

            // 5) If we have orderId -> check fields + dedupe
            if (orderId != null && !orderId.isEmpty()) {
                String tempCalcRequestedAt = payload.optString("tempCalculationRequestedAt", null);
                String orderStatus = payload.optString("orderStatus", null);

                boolean hasTempCalcRequest = tempCalcRequestedAt != null && !tempCalcRequestedAt.trim().isEmpty();
                boolean isNotPrinted = orderStatus == null || !orderStatus.equals("temp_bill_printed");

                if ((hasTempCalcRequest || looksLikeTempRequest) && isNotPrinted) {
                    if (!knownTempCalcRequestOrderIds.contains(orderId)) {
                        knownTempCalcRequestOrderIds.add(orderId);
                        final String finalOrderId = orderId;
                        final int finalTableNumber = tableNumber;
                        runOnUiThread(() -> showTempCalculationNotification(finalTableNumber, finalOrderId));
                    } else {
                        Log.d(TAG, "checkForNewTempCalculationRequest: orderId already known: " + orderId);
                    }
                } else {
                    Log.d(TAG, "checkForNewTempCalculationRequest: payload for orderId " + orderId + " not indicating temp request (tempCalc=" + tempCalcRequestedAt + ", status=" + orderStatus + ", looksLike=" + looksLikeTempRequest + ")");
                }
                return;
            }

            // 6) If no orderId but tableNumber + looksLikeTempRequest -> dedupe by table
            if (tableNumber > 0 && looksLikeTempRequest) {
                long now = System.currentTimeMillis();
                Long lastTs = knownTempCalcRequestTableTimestamps.get(tableNumber);
                if (lastTs == null || now - lastTs > TEMP_REQUEST_DEDUPE_MS) {
                    knownTempCalcRequestTableTimestamps.put(tableNumber, now);
                    final int finalTableNumber = tableNumber;
                    runOnUiThread(() -> showTempCalculationNotification(finalTableNumber, null));
                } else {
                    Log.d(TAG, "checkForNewTempCalculationRequest: duplicate table temp request ignored for table " + tableNumber);
                }
                return;
            }

            // None matched -> log for debug
            Log.d(TAG, "checkForNewTempCalculationRequest: payload did not match temp-calc pattern. orderId=" + orderId + ", tableNumber=" + tableNumber + ", looksLike=" + looksLikeTempRequest);
        } catch (Exception e) {
            Log.e(TAG, "checkForNewTempCalculationRequest: Error parsing payload", e);
        }
    }

    /**
     * Hiển thị popup thông báo yêu cầu tạm tính mới (tự động đóng sau 3 giây)
     */
    private void showTempCalculationNotification(int tableNumber, String orderId) {
        // Đóng dialog cũ nếu có
        if (currentNotificationDialog != null && currentNotificationDialog.isShowing()) {
            currentNotificationDialog.dismiss();
        }

        // Tạo dialog thông báo
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_temp_calc_notification, null);
        builder.setView(dialogView);

        // Set nội dung
        TextView tvMessage = dialogView.findViewById(R.id.tv_notification_message);
        if (tvMessage != null) {
            String message = "Có yêu cầu tạm tính mới";
            if (tableNumber > 0) {
                message += "\nBàn " + tableNumber;
            }
            if (orderId != null && !orderId.trim().isEmpty()) {
                message += "\nMã đơn: " + orderId;
            }
            tvMessage.setText(message);
        }

        // Tạo dialog
        currentNotificationDialog = builder.create();
        currentNotificationDialog.setCancelable(true);
        currentNotificationDialog.setCanceledOnTouchOutside(true);

        // Lấy progress bar để animate đếm ngược
        ProgressBar progressBar = dialogView.findViewById(R.id.progress_countdown);

        // Hiển thị dialog
        currentNotificationDialog.show();

        // Animate progress bar từ 100 xuống 0 trong 3 giây
        if (progressBar != null) {
            ObjectAnimator progressAnimator = ObjectAnimator.ofInt(progressBar, "progress", 100, 0);
            progressAnimator.setDuration(3000); // 3 giây
            progressAnimator.start();
        }

        // Tự động đóng sau 3 giây
        refreshHandler.postDelayed(() -> {
            if (currentNotificationDialog != null && currentNotificationDialog.isShowing()) {
                currentNotificationDialog.dismiss();
                currentNotificationDialog = null;
            }
        }, 3000); // 3 giây
    }

    private void registerRefreshTablesReceiver() {
        refreshTablesReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_REFRESH_TABLES.equals(intent.getAction())) {
                    // Reload danh sách yêu cầu tạm tính
                    Log.d(TAG, "refreshTablesReceiver: Received ACTION_REFRESH_TABLES, reloading temp calculation requests");
                    loadTempCalculationRequestsCount();
                    // Tự động reload lại dialog nếu có yêu cầu tạm tính
                    // (sẽ chỉ reload nếu người dùng mở lại menu)
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_REFRESH_TABLES);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshTablesReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(refreshTablesReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        }
    }

    // HELPER: translate order status to friendly Vietnamese
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

    // HELPER: translate table status
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
}