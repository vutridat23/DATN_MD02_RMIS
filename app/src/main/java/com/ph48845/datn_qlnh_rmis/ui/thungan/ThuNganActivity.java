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
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.model.User;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.ph48845.datn_qlnh_rmis.ui.revenue.ReportActivity;
import com.ph48845.datn_qlnh_rmis.ui.bep.SocketManager;
// Thay thế bằng Activity xem lịch sử thanh toán thực tế của bạn
// import com.ph48845.datn_qlnh_rmis.ui.history.HistoryActivity;
// Thay thế bằng Activity xem chi tiết hóa đơn thực tế của bạn
// import com.ph48845.datn_qlnh_rmis.ui.invoice.InvoiceActivity;
import com.ph48845.datn_qlnh_rmis.ui.thungan.adapter.ThuNganAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Activity Thu Ngân: Quản lý danh sách bàn đang hoạt động/chờ thanh toán.
 */
public class ThuNganActivity extends BaseMenuActivity {

    private static final String TAG = "ThuNganActivity";

    // Views
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
    private final SocketManager socketManager = SocketManager.getInstance();
    private Handler refreshHandler;
    private static final long SOCKET_REFRESH_DELAY_MS = 5000;
    private BroadcastReceiver refreshTablesReceiver;
    private static final String ACTION_REFRESH_TABLES = "com.ph48845.datn_qlnh_rmis.ACTION_REFRESH_TABLES";
    private Map<String, String> userIdToNameMap = new HashMap<>(); // Map user ID -> user name
    private ActivityResultLauncher<Intent> invoiceLauncher; // Launcher để mở InvoiceActivity và nhận kết quả


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thu_ngan);

        // 1. Khởi tạo ViewModel & Repository
        viewModel = new ThuNganViewModel();
        orderRepository = new OrderRepository();
        refreshHandler = new Handler(Looper.getMainLooper());

        // 2. Ánh xạ View & Setup giao diện
        initViews();
        applyNavigationViewInsets();
        setupToolbar();
        setupNavigationDrawer();
        setupRecyclerViews();
        
        // Khởi tạo ActivityResultLauncher để mở InvoiceActivity và nhận kết quả
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
                        // Reload lại số lượng yêu cầu tạm tính
                        loadTempCalculationRequestsCount();
                        // Đợi lâu hơn để đảm bảo database đã được cập nhật hoàn toàn, sau đó mới reload dialog
                        refreshHandler.postDelayed(() -> {
                            Log.d(TAG, "invoiceLauncher: Reloading temp calculation requests dialog after delay (1.5s)");
                            showTempCalculationRequests();
                        }, 1500); // Delay 1.5s để đảm bảo DB đã được cập nhật hoàn toàn
                    } else {
                        // Chỉ reload số lượng, không mở lại dialog
                        loadTempCalculationRequestsCount();
                    }
                } else {
                    // Chỉ reload số lượng
                    loadTempCalculationRequestsCount();
                }
            }
        );

        // 3. Load dữ liệu ban đầu
        updateNavHeaderInfo();
        loadActiveTables();
        loadUsersForNameMapping(); // Load danh sách users để map ID -> name
        loadTempCalculationRequestsCount();
        startSocketRealtime();
        registerRefreshTablesReceiver();
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

        // Sự kiện click vào bàn -> Mở màn hình hóa đơn
        ThuNganAdapter.OnTableClickListener listener = table -> {
            // Cần Activity hóa đơn
             Intent intent = new Intent(ThuNganActivity.this, InvoiceActivity.class);
             intent.putExtra("tableNumber", table.getTableNumber());
             startActivity(intent);
            Toast.makeText(ThuNganActivity.this, "Mở hóa đơn Bàn " + table.getTableNumber(), Toast.LENGTH_SHORT).show();
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

                    // Cập nhật dữ liệu lên UI
                    adapterFloor1.updateList(floor1Tables);
                    adapterFloor2.updateList(floor2Tables);

                    // Ẩn hiện tiêu đề tầng
                    if (headerFloor1 != null) headerFloor1.setVisibility(floor1Tables.isEmpty() ? View.GONE : View.VISIBLE);
                    if (headerFloor2 != null) headerFloor2.setVisibility(floor2Tables.isEmpty() ? View.GONE : View.VISIBLE);

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
                allTables.addAll(floor1Tables);
                allTables.addAll(floor2Tables);

                boolean needUpdate = false;

                for (TableItem table : allTables) {
                    List<Order> tableOrders = ordersByTable.get(table.getTableNumber());

                    boolean allServed = determineIfAllServed(tableOrders);

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
                // Nếu có bất kỳ món nào chưa "done" -> Chưa xong
                if (status == null || !status.toLowerCase().contains("done")) {
                    return false;
                }
            }
        }
        return true;
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

    private void loadTempCalculationRequestsCount() {
        orderRepository.getOrdersByTableNumber(null, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> allOrders) {
                runOnUiThread(() -> {
                    int count = 0;
                    if (allOrders != null) {
                        for (Order order : allOrders) {
                            if (order != null) {
                                String tempCalcRequestedAt = order.getTempCalculationRequestedAt();
                                String orderStatus = order.getOrderStatus();
                                // Chỉ đếm nếu có yêu cầu tạm tính VÀ chưa in hóa đơn
                                boolean hasTempCalcRequest = tempCalcRequestedAt != null && !tempCalcRequestedAt.trim().isEmpty();
                                boolean isNotPrinted = orderStatus == null || !orderStatus.equals("temp_bill_printed");
                                if (hasTempCalcRequest && isNotPrinted) {
                                    count++;
                                }
                            }
                        }
                    }
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
    protected void onResume() {
        super.onResume();
        loadActiveTables();
        loadTempCalculationRequestsCount();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (refreshHandler != null) refreshHandler.removeCallbacksAndMessages(null);
        try {
            socketManager.setOnEventListener(null);
            socketManager.disconnect();
        } catch (Exception ignored) {}
        try {
            if (refreshTablesReceiver != null) unregisterReceiver(refreshTablesReceiver);
        } catch (Exception ignored) {}
    }

    private void startSocketRealtime() {
        try {
            socketManager.init("http://192.168.1.84:3000");
            socketManager.setOnEventListener(new SocketManager.OnEventListener() {
                @Override
                public void onOrderCreated(org.json.JSONObject payload) {
                    scheduleRefresh();
                }

                @Override
                public void onOrderUpdated(org.json.JSONObject payload) {
                    scheduleRefresh();
                }

                @Override public void onConnect() {}
                @Override public void onDisconnect() {}
                @Override public void onError(Exception e) {}
            });
            socketManager.connect();
        } catch (Exception e) {
            Log.w(TAG, "Socket init failed: " + e.getMessage());
        }
    }

    private void scheduleRefresh() {
        if (refreshHandler == null) return;
        refreshHandler.removeCallbacksAndMessages(null);
        refreshHandler.postDelayed(() -> {
            loadActiveTables();
            loadTempCalculationRequestsCount();
        }, SOCKET_REFRESH_DELAY_MS);
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
            registerReceiver(refreshTablesReceiver, filter);
        }
    }

}