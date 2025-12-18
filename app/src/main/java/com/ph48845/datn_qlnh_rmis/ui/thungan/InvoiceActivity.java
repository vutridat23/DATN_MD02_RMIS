package com.ph48845.datn_qlnh_rmis.ui.thungan;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.core.widget.NestedScrollView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.model.Voucher;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.repository.MenuRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.VoucherRepository;
import com.ph48845.datn_qlnh_rmis.ui.bep.SocketManager;
import com.ph48845.datn_qlnh_rmis.ui.thungan.thanhtoan.ThanhToanActivity;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Activity hiển thị hóa đơn thanh toán cho bàn ăn với đầy đủ chức năng:
 * - Chỉnh sửa hóa đơn (thêm/bớt món)
 * - Hủy hóa đơn với lý do
 * - Tách hóa đơn khi thanh toán
 * - Yêu cầu kiểm tra lại món ăn
 * - In tạm tính
 */
public class InvoiceActivity extends AppCompatActivity {

    private static final String TAG = "InvoiceActivity";
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,###");

    private Toolbar toolbar;
    private ProgressBar progressBar;
    private LinearLayout llOrderCards;
    private NestedScrollView scrollInvoice;
    private Button btnProceedPayment;
    private double orderTotal;


    private OrderRepository orderRepository;
    private MenuRepository menuRepository;
    private TableRepository tableRepository;
    private VoucherRepository voucherRepository;
    private int tableNumber;
    private List<Order> orders = new ArrayList<>();
    private List<Order.OrderItem> allItems = new ArrayList<>();
    private boolean isEditMode = false;
    private Order editingOrder = null; // Order đang được chỉnh sửa
    private Map<String, CardView> orderCardMap = new ConcurrentHashMap<>(); // Map order ID -> CardView
    private String newlySplitOrderId = null; // ID của hóa đơn mới vừa tách (để highlight)
    private String targetOrderId = null; // Order cần focus khi mở từ danh sách tạm tính
    private Voucher selectedVoucher = null; // Voucher đã chọn cho tổng tiền chung
    private Map<String, Voucher> orderVoucherMap = new ConcurrentHashMap<>(); // Map order ID -> Voucher đã chọn cho từng order
    private static final String ACTION_REFRESH_TABLES = "com.ph48845.datn_qlnh_rmis.ACTION_REFRESH_TABLES";

    // Socket để gửi request lên server
    private final SocketManager socketManager = SocketManager.getInstance();
    private final String SOCKET_URL = "http://192.168.1.84:3000"; // đổi theo server của bạn

    // Launcher cho PrintBillActivity để nhận kết quả sau khi in xong
    private final ActivityResultLauncher<Intent> printBillLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    String orderId = result.getData() != null ? result.getData().getStringExtra("orderId") : null;
                    if (orderId != null) {
                        Log.d(TAG, "printBillLauncher: Received result for orderId: " + orderId);
                        // Tìm order và clear temp calculation request
                        Order foundOrder = null;
                        for (Order order : orders) {
                            if (order != null && order.getId() != null && order.getId().equals(orderId)) {
                                foundOrder = order;
                                break;
                            }
                        }

                        if (foundOrder != null) {
                            // Clear temp calculation request và reload danh sách
                            clearTempCalculationRequest(foundOrder, () -> {
                                Log.d(TAG, "printBillLauncher: Temp calculation request cleared, reloading data");
                                // Reload dữ liệu để cập nhật danh sách
                                loadInvoiceData();
                            });
                        } else {
                            Log.w(TAG, "printBillLauncher: Order not found in current list: " + orderId);
                        }
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invoice);

        // Lấy tableNumber từ Intent
        tableNumber = getIntent().getIntExtra("tableNumber", 0);
        targetOrderId = getIntent().getStringExtra("orderId"); // có thể null
        if (tableNumber <= 0) {
            Toast.makeText(this, "Thông tin bàn không hợp lệ", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupToolbar();

        orderRepository = new OrderRepository();
        menuRepository = new MenuRepository();
        tableRepository = new TableRepository();
        voucherRepository = new VoucherRepository();

        // Khởi tạo socket để gửi request lên server
        initSocket();

        // Setup voucher và payment button
        setupVoucherAndPaymentButton();

        // Load dữ liệu từ API
        loadInvoiceData();
    }

    /**
     * Khởi tạo socket connection
     */
    private void initSocket() {
        try {
            socketManager.init(SOCKET_URL);
            socketManager.setOnEventListener(new SocketManager.OnEventListener() {
                @Override
                public void onOrderCreated(org.json.JSONObject payload) {
                    // Không cần xử lý
                }

                @Override
                public void onOrderUpdated(org.json.JSONObject payload) {
                    // Không cần xử lý
                }

                @Override
                public void onConnect() {
                    Log.d(TAG, "Socket connected in InvoiceActivity");
                }

                @Override
                public void onDisconnect() {
                    Log.d(TAG, "Socket disconnected in InvoiceActivity");
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Socket error in InvoiceActivity: " + (e != null ? e.getMessage() : "unknown"));
                }
            });
            socketManager.connect();
            Log.d(TAG, "Socket initialized for InvoiceActivity, URL: " + SOCKET_URL);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize socket: " + e.getMessage(), e);
            Toast.makeText(this, "Lỗi kết nối socket: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        progressBar = findViewById(R.id.progress_bar_loading);
        llOrderCards = findViewById(R.id.ll_order_cards);
        scrollInvoice = findViewById(R.id.scrollInvoice);

        Log.d(TAG, "initViews: toolbar=" + (toolbar != null) + ", progressBar=" + (progressBar != null) +
              ", llOrderCards=" + (llOrderCards != null) + ", scrollInvoice=" + (scrollInvoice != null));

        if (llOrderCards == null) {
            Log.e(TAG, "initViews: CRITICAL - llOrderCards is null! Check layout activity_invoice.xml");
        }
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            // Ẩn title mặc định để chỉ hiển thị TextView custom
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Đảm bảo navigation icon hiển thị và có thể click được
        toolbar.post(() -> {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowHomeEnabled(true);
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
        });
    }

    /**
     * Load dữ liệu hóa đơn từ API - Chỉ lấy các đơn chưa thanh toán
     */
    private void loadInvoiceData() {
        Log.d(TAG, "loadInvoiceData: Starting to load data for table " + tableNumber);
        progressBar.setVisibility(View.VISIBLE);
        llOrderCards.removeAllViews();

        // Lấy orders của bàn này (lấy tất cả, sau đó filter các đơn chưa thanh toán)
        orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orderList) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Log.d(TAG, "loadInvoiceData: Received " + (orderList != null ? orderList.size() : 0) + " orders from API");

                    if (orderList == null || orderList.isEmpty()) {
                        Log.w(TAG, "loadInvoiceData: No orders found for table " + tableNumber);
                        Toast.makeText(InvoiceActivity.this, "Không có đơn hàng cho bàn này", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Lọc bỏ các đơn đã thanh toán
                    orders = filterUnpaidOrders(orderList);
                    Log.d(TAG, "loadInvoiceData: After filtering, " + (orders != null ? orders.size() : 0) + " unpaid orders remain");

                    // DEBUG: Nếu không có unpaid orders, tạm thời hiển thị tất cả để debug
                    if (orders == null || orders.isEmpty()) {
                        Log.w(TAG, "loadInvoiceData: No unpaid orders found. Showing all orders for debugging...");
                        orders = orderList; // Tạm thời hiển thị tất cả orders
                        Log.d(TAG, "loadInvoiceData: Showing all " + (orders != null ? orders.size() : 0) + " orders (including paid)");
                    }

                    // Nếu đang ở chế độ chỉnh sửa, thay thế order trong danh sách bằng editingOrder
                    if (editingOrder != null && editingOrder.getId() != null && orders != null) {
                        String editingId = editingOrder.getId().trim();
                        Log.d(TAG, "Looking for editingOrder with ID: '" + editingId + "' in " + orders.size() + " orders");
                        boolean found = false;
                        for (int i = 0; i < orders.size(); i++) {
                            Order o = orders.get(i);
                            if (o != null && o.getId() != null) {
                                String orderId = o.getId().trim();
                                Log.d(TAG, "Comparing order[" + i + "] ID: '" + orderId + "' with editingId: '" + editingId + "'");
                                if (orderId.equals(editingId)) {
                                    Log.d(TAG, "FOUND! Replacing order in list with editingOrder at index " + i);
                                    orders.set(i, editingOrder);
                                    found = true;
                                    break;
                                }
                            }
                        }
                        if (!found) {
                            Log.w(TAG, "editingOrder ID not found in orders list! This might cause buttons not to show.");
                            runOnUiThread(() -> {
                                Toast.makeText(InvoiceActivity.this, "Warning: Editing order not found in list", Toast.LENGTH_LONG).show();
                            });
                        }
                    } else {
                        Log.d(TAG, "No editingOrder to replace - editingOrder: " + (editingOrder != null) +
                              ", orders: " + (orders != null));
                    }

                    if (orders == null || orders.isEmpty()) {
                        Log.w(TAG, "loadInvoiceData: No unpaid orders after filtering");
                        Toast.makeText(InvoiceActivity.this, "Không có đơn hàng chưa thanh toán cho bàn này", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Log.d(TAG, "loadInvoiceData: Calling displayInvoice with " + orders.size() + " orders");
                    displayInvoice();
                    updateVoucherDisplay();
                    updateTotalSummary();
                    // Cập nhật trạng thái nút chọn voucher sau khi load dữ liệu
                    updateVoucherButtonState();
                    updateOrderVoucherButtonsState();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Log.e(TAG, "loadInvoiceData: Error loading orders for table " + tableNumber + ": " + message);
                    Toast.makeText(InvoiceActivity.this, "Lỗi tải dữ liệu: " + message, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error loading orders: " + message);
                });
            }
        });
    }

    /**
     * Lọc bỏ các đơn đã thanh toán, chỉ giữ lại các đơn chưa thanh toán
     */
    private List<Order> filterUnpaidOrders(List<Order> allOrders) {
        List<Order> unpaidOrders = new ArrayList<>();
        Log.d(TAG, "filterUnpaidOrders: Filtering " + (allOrders != null ? allOrders.size() : 0) + " orders");

        for (Order order : allOrders) {
            if (order == null) {
                Log.w(TAG, "filterUnpaidOrders: Skipping null order");
                continue;
            }

            // Kiểm tra nếu đã thanh toán
            boolean isPaid = false;

            // Kiểm tra trường paid
            if (order.isPaid()) {
                isPaid = true;
            }

            // Kiểm tra paidAt
            String paidAt = order.getPaidAt();
            if (paidAt != null && !paidAt.trim().isEmpty()) {
                isPaid = true;
            }

            // Kiểm tra orderStatus
            String orderStatus = order.getOrderStatus();
            if (orderStatus != null) {
                String status = orderStatus.toLowerCase().trim();
                if (status.equals("paid") || status.contains("paid") ||
                    status.equals("completed") || status.contains("completed") ||
                    status.contains("đã thanh toán") || status.contains("hoàn thành")) {
                    isPaid = true;
                }
            }

            // Nếu không có dấu hiệu thanh toán, thêm vào danh sách chưa thanh toán
            if (!isPaid) {
                unpaidOrders.add(order);
                Log.d(TAG, "filterUnpaidOrders: Added unpaid order " + (order.getId() != null ? order.getId() : "null"));
            } else {
                Log.d(TAG, "filterUnpaidOrders: Skipping paid order " + (order.getId() != null ? order.getId() : "null") +
                      " - paid: " + order.isPaid() + ", paidAt: " + order.getPaidAt() + ", status: " + order.getOrderStatus());
            }
        }

        Log.d(TAG, "filterUnpaidOrders: Returning " + unpaidOrders.size() + " unpaid orders");
        return unpaidOrders;
    }

    /**
     * Hiển thị hóa đơn với dữ liệu đã load
     */
    private void displayInvoice() {
        Log.d(TAG, "displayInvoice: Starting to display " + (orders != null ? orders.size() : 0) + " orders");
        // Xóa tất cả card cũ
        llOrderCards.removeAllViews();
        orderCardMap.clear(); // Xóa map

        if (orders == null || orders.isEmpty()) {
            Log.w(TAG, "displayInvoice: No orders to display");
            return;
        }

        // Tạo một card hóa đơn cho mỗi order
        int cardCount = 0;
        for (Order order : orders) {
            if (order == null) {
                Log.w(TAG, "displayInvoice: Skipping null order");
                continue;
            }
            try {
                // Normalize items trước khi hiển thị
                order.normalizeItems();
                List<Order.OrderItem> items = order.getItems();
                Log.d(TAG, "displayInvoice: Order " + (order.getId() != null ? order.getId() : "null") +
                      " has " + (items != null ? items.size() : 0) + " items after normalize");

                if (items == null || items.isEmpty()) {
                    Log.w(TAG, "displayInvoice: Order " + (order.getId() != null ? order.getId() : "null") +
                          " has no items after normalize!");
                }

                createInvoiceCard(order);
                cardCount++;
                Log.d(TAG, "displayInvoice: Created card for order " + (order.getId() != null ? order.getId() : "null"));
            } catch (Exception e) {
                Log.e(TAG, "displayInvoice: Error creating card for order: " + e.getMessage(), e);
                e.printStackTrace();
            }
        }
        Log.d(TAG, "displayInvoice: Created " + cardCount + " cards, llOrderCards child count: " + llOrderCards.getChildCount());

        // Cập nhật tổng tiền
        updateTotalSummary();

        // Nếu có targetOrderId (mở từ danh sách tạm tính), focus vào hóa đơn đó
        if (targetOrderId != null && !targetOrderId.trim().isEmpty()) {
            focusOrderCard(targetOrderId.trim());
        }

        // Reset highlight sau khi đã hiển thị xong
        newlySplitOrderId = null;
    }

    /**
     * Setup voucher selection và payment button
     */
    private void setupVoucherAndPaymentButton() {
        Button btnSelectVoucher = findViewById(R.id.btn_select_voucher);
        btnProceedPayment = findViewById(R.id.btn_proceed_payment);

        if (btnSelectVoucher != null) {
            btnSelectVoucher.setOnClickListener(v -> showVoucherSelectionDialog());
            // Cập nhật trạng thái nút dựa trên điều kiện
            updateVoucherButtonState();
        }

        if (btnProceedPayment != null) {
            btnProceedPayment.setOnClickListener(v -> {
                if (orders == null || orders.isEmpty()) {
                    Toast.makeText(this, "Không có hóa đơn", Toast.LENGTH_SHORT).show();
                    return;
                }

                int unreadyCount = countUnreadyItemsForAllOrders(orders);

                handlePaymentWithConfirm(unreadyCount, () -> {
                    processPaymentForAllOrders(); // giữ nguyên voucher
                });
            });

        }
    }

    /**
     * Cập nhật trạng thái nút chọn voucher chung (enable/disable)
     */
    private void updateVoucherButtonState() {
        Button btnSelectVoucher = findViewById(R.id.btn_select_voucher);
        if (btnSelectVoucher != null) {
            // Nếu đã có voucher cho bất kỳ order nào, disable nút chọn voucher chung
            boolean hasOrderVoucher = orderVoucherMap != null && !orderVoucherMap.isEmpty();
            btnSelectVoucher.setEnabled(!hasOrderVoucher);
            if (hasOrderVoucher) {
                btnSelectVoucher.setAlpha(0.5f); // Làm mờ nút
            } else {
                btnSelectVoucher.setAlpha(1.0f); // Bình thường
            }
        }
    }

    /**
     * Cập nhật trạng thái nút chọn voucher cho tất cả các order (enable/disable)
     */
    private void updateOrderVoucherButtonsState() {
        if (orders == null || orderCardMap == null) {
            return;
        }

        // Nếu đã có voucher chung, disable tất cả nút chọn voucher cho các order
        boolean hasGlobalVoucher = selectedVoucher != null;

        for (Order order : orders) {
            if (order == null || order.getId() == null) {
                continue;
            }

            CardView cardView = orderCardMap.get(order.getId());
            if (cardView != null) {
                Button btnSelectVoucherCard = cardView.findViewById(R.id.btn_select_voucher_card);
                if (btnSelectVoucherCard != null) {
                    btnSelectVoucherCard.setEnabled(!hasGlobalVoucher);
                    if (hasGlobalVoucher) {
                        btnSelectVoucherCard.setAlpha(0.5f); // Làm mờ nút
                    } else {
                        btnSelectVoucherCard.setAlpha(1.0f); // Bình thường
                    }
                }
            }
        }
    }

    /**
     * Hiển thị dialog chọn voucher
     */
    private void showVoucherSelectionDialog() {
        // Kiểm tra xem đã có voucher cho bất kỳ order nào chưa
        if (orderVoucherMap != null && !orderVoucherMap.isEmpty()) {
            Toast.makeText(this, "Không thể sử dụng voucher chung khi đã chọn voucher cho từng hóa đơn. Vui lòng xóa voucher ở các hóa đơn trước.", Toast.LENGTH_LONG).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        // Load danh sách vouchers (thử cả null và "active")
        voucherRepository.getAllVouchers(null, new VoucherRepository.RepositoryCallback<List<Voucher>>() {
            @Override
            public void onSuccess(List<Voucher> vouchers) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    if (vouchers == null || vouchers.isEmpty()) {
                        Toast.makeText(InvoiceActivity.this, "Không có voucher nào khả dụng", Toast.LENGTH_SHORT).show();
                        Log.w(TAG, "showVoucherSelectionDialog: No vouchers returned");
                        return;
                    }

                    Log.d(TAG, "showVoucherSelectionDialog: Received " + vouchers.size() + " vouchers");
                    showVoucherDialog(vouchers);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Log.e(TAG, "showVoucherSelectionDialog: Error loading vouchers - " + message);
                    // Thử lại với status = "active" nếu lần đầu gọi với null
                    if (message.contains("Network error") || message.contains("404") || message.contains("500")) {
                        // Nếu là lỗi network hoặc server, không thử lại
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(InvoiceActivity.this, "Lỗi tải vouchers: " + message, Toast.LENGTH_SHORT).show();
                    } else {
                        // Thử lại với status = "active"
                        Log.d(TAG, "Retrying with status='active'");
                        voucherRepository.getAllVouchers("active", new VoucherRepository.RepositoryCallback<List<Voucher>>() {
                            @Override
                            public void onSuccess(List<Voucher> vouchers) {
                                runOnUiThread(() -> {
                                    progressBar.setVisibility(View.GONE);
                                    if (vouchers == null || vouchers.isEmpty()) {
                                        Toast.makeText(InvoiceActivity.this, "Không có voucher nào khả dụng", Toast.LENGTH_SHORT).show();
                                        return;
                                    }
                                    showVoucherDialog(vouchers);
                                });
                            }

                            @Override
                            public void onError(String errorMessage) {
                                runOnUiThread(() -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(InvoiceActivity.this, "Lỗi tải vouchers: " + errorMessage, Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    }
                });
            }
        });
    }

    /**
     * Hiển thị dialog chọn voucher cho một order cụ thể
     */
    private void showVoucherDialogForOrder(Order order) {
        if (order == null || order.getId() == null) {
            Toast.makeText(this, "Hóa đơn không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        // Kiểm tra xem đã có voucher chung chưa
        if (selectedVoucher != null) {
            Toast.makeText(this, "Không thể chọn voucher cho từng hóa đơn khi đã chọn voucher chung. Vui lòng xóa voucher chung trước.", Toast.LENGTH_LONG).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        // Load danh sách vouchers
        voucherRepository.getAllVouchers(null, new VoucherRepository.RepositoryCallback<List<Voucher>>() {
            @Override
            public void onSuccess(List<Voucher> vouchers) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    if (vouchers == null || vouchers.isEmpty()) {
                        Toast.makeText(InvoiceActivity.this, "Không có voucher nào khả dụng", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    showVoucherDialogForOrderWithList(order, vouchers);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(InvoiceActivity.this, "Lỗi tải vouchers: " + message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * Hiển thị dialog chọn voucher cho order với danh sách vouchers đã có
     */
    private void showVoucherDialogForOrderWithList(Order order, List<Voucher> vouchers) {
        // Tính tổng tiền của order này
        double orderTotal = order.getTotalAmount();
        if (orderTotal <= 0) {
            orderTotal = order.getFinalAmount() + order.getDiscount();
        }

        // Tạo danh sách tên voucher để hiển thị
        List<String> voucherNames = new ArrayList<>();
        voucherNames.add("Không sử dụng voucher"); // Option đầu tiên
        for (Voucher v : vouchers) {
            String display = v.getName() + " (" + v.getCode() + ")";
            double discount = v.calculateDiscount(orderTotal);
            if ("percentage".equalsIgnoreCase(v.getDiscountType())) {
                display += " - Giảm " + v.getDiscountValue() + "% (" + formatCurrency(discount) + ")";
            } else {
                display += " - Giảm " + formatCurrency(v.getDiscountValue());
            }
            voucherNames.add(display);
        }

        String[] voucherArray = voucherNames.toArray(new String[0]);

        // Lấy voucher hiện tại của order này
        Voucher currentVoucher = orderVoucherMap.get(order.getId());
        int selectedIndex = 0;
        if (currentVoucher != null) {
            for (int i = 0; i < vouchers.size(); i++) {
                if (vouchers.get(i).getId().equals(currentVoucher.getId())) {
                    selectedIndex = i + 1; // +1 vì có option "Không sử dụng voucher" ở đầu
                    break;
                }
            }
        }

        new AlertDialog.Builder(InvoiceActivity.this)
                .setTitle("Chọn voucher cho hóa đơn")
                .setSingleChoiceItems(voucherArray, selectedIndex, null)
                .setPositiveButton("Xác nhận", (dialog, which) -> {
                    int selectedPosition = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                    runOnUiThread(() -> {
                        // Kiểm tra lại điều kiện trước khi xác nhận (phòng trường hợp voucher chung được chọn trong lúc này)
                        if (selectedVoucher != null && selectedPosition != 0) {
                            Toast.makeText(InvoiceActivity.this, "Không thể chọn voucher cho từng hóa đơn khi đã chọn voucher chung. Vui lòng xóa voucher chung trước.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        if (selectedPosition == 0) {
                            // Không sử dụng voucher
                            orderVoucherMap.remove(order.getId());
                            Log.d(TAG, "Voucher removed for order " + order.getId());
                        } else {
                            // Chọn voucher
                            Voucher selectedV = vouchers.get(selectedPosition - 1);
                            orderVoucherMap.put(order.getId(), selectedV);
                            Log.d(TAG, "Voucher selected for order " + order.getId() + ": " + selectedV.getCode());
                            // Xóa voucher chung nếu có (đảm bảo chỉ dùng một loại)
                            if (selectedVoucher != null) {
                                selectedVoucher = null;
                                updateVoucherDisplay();
                            }
                        }
                        // Refresh lại card để hiển thị tổng tiền mới
                        refreshOrderCard(order);
                        updateTotalSummary(); // Cập nhật tổng tiền chung
                        // Cập nhật trạng thái nút chọn voucher chung
                        updateVoucherButtonState();
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    /**
     * Refresh lại card của một order để cập nhật tổng tiền sau khi chọn voucher
     */

    private void refreshOrderCard(Order order) {
        if (order == null || order.getId() == null) {
            return;
        }

        // Tìm card của order này
        CardView cardView = orderCardMap.get(order.getId());
        if (cardView == null) {
            return;
        }

        // Lấy các TextView trong totals
        LinearLayout llTotals = cardView.findViewById(R.id.llTotals);
        if (llTotals == null || llTotals.getChildCount() == 0) {
            return;
        }

        View totalsView = llTotals.getChildAt(0);
        TextView tvTotal = totalsView.findViewById(R.id.tvTotal);
        TextView tvDiscount = totalsView.findViewById(R.id.tvDiscount);
        TextView tvFinalAmount = totalsView.findViewById(R.id.tvFinalAmount);
        TextView tvVoucherCard = totalsView.findViewById(R.id.tv_voucher_card);

        if (tvTotal == null || tvDiscount == null || tvFinalAmount == null) {
            return;
        }

        // Tính lại tổng tiền với voucher
        orderTotal = order.getTotalAmount();
        if (orderTotal <= 0) {
            // Nếu totalAmount = 0, tính từ finalAmount + discount mặc định
            double defaultDiscount = order.getDiscount();
            orderTotal = order.getFinalAmount() + defaultDiscount;
        }

        // Chỉ tính discount từ voucher nếu có chọn voucher
        // Nếu không chọn voucher, KHÔNG có discount (kể cả discount mặc định của order)
        double orderDiscount = 0.0; // Bắt đầu từ 0
        Voucher orderVoucher = orderVoucherMap.get(order.getId());

        if (orderVoucher != null && orderVoucher.canApply()) {
            // Chỉ tính discount từ voucher khi có chọn voucher
            orderDiscount = orderVoucher.calculateDiscount(orderTotal);
            Log.d(TAG, "refreshOrderCard: Voucher applied for order " + order.getId() +
                  ", discount: " + formatCurrency(orderDiscount));
        } else {
            // Không có voucher, không có discount
            Log.d(TAG, "refreshOrderCard: No voucher selected for order " + order.getId() + ", discount = 0");
        }

        double finalAmount = orderTotal - orderDiscount;
        if (finalAmount < 0) finalAmount = 0;

        // Cập nhật hiển thị
        tvTotal.setText(formatCurrency(orderTotal));
        tvDiscount.setText(formatCurrency(orderDiscount));
        tvFinalAmount.setText(formatCurrency(finalAmount));

        if (tvVoucherCard != null) {
            if (orderVoucher != null) {
                tvVoucherCard.setText(orderVoucher.getName() + " (" + orderVoucher.getCode() + ")");
                tvVoucherCard.setTextColor(Color.parseColor("#4CAF50"));
            } else {
                tvVoucherCard.setText("Chưa chọn");
                tvVoucherCard.setTextColor(Color.parseColor("#757575"));
            }
        }
    }

    /**
     * Hiển thị dialog chọn voucher với danh sách đã có
     */
    private void showVoucherDialog(List<Voucher> vouchers) {
        // Tạo danh sách tên voucher để hiển thị
        List<String> voucherNames = new ArrayList<>();
        voucherNames.add("Không sử dụng voucher"); // Option đầu tiên
        for (Voucher v : vouchers) {
            String display = v.getName() + " (" + v.getCode() + ")";
            if ("percentage".equalsIgnoreCase(v.getDiscountType())) {
                display += " - Giảm " + v.getDiscountValue() + "%";
            } else {
                display += " - Giảm " + formatCurrency(v.getDiscountValue());
            }
            voucherNames.add(display);
        }

        String[] voucherArray = voucherNames.toArray(new String[0]);

        new AlertDialog.Builder(InvoiceActivity.this)
                .setTitle("Chọn voucher")
                .setItems(voucherArray, (dialog, which) -> {
                    runOnUiThread(() -> {
                        // Kiểm tra lại điều kiện trước khi xác nhận (phòng trường hợp voucher order được chọn trong lúc này)
                        if (orderVoucherMap != null && !orderVoucherMap.isEmpty() && which != 0) {
                            Toast.makeText(InvoiceActivity.this, "Không thể chọn voucher chung khi đã chọn voucher cho từng hóa đơn. Vui lòng xóa voucher ở các hóa đơn trước.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        if (which == 0) {
                            // Không sử dụng voucher
                            selectedVoucher = null;
                            Log.d(TAG, "Voucher removed");
                        } else {
                            // Chọn voucher
                            selectedVoucher = vouchers.get(which - 1);
                            Log.d(TAG, "Voucher selected: " + selectedVoucher.getCode() +
                                  ", valid: " + selectedVoucher.isValid() +
                                  ", type: " + selectedVoucher.getDiscountType() +
                                  ", value: " + selectedVoucher.getDiscountValue());
                            // Xóa tất cả voucher của các order (đảm bảo chỉ dùng một loại)
                            if (orderVoucherMap != null && !orderVoucherMap.isEmpty()) {
                                orderVoucherMap.clear();
                                // Refresh lại tất cả các card
                                for (Order order : orders) {
                                    if (order != null && order.getId() != null) {
                                        refreshOrderCard(order);
                                    }
                                }
                            }
                        }
                        updateVoucherDisplay();
                        updateTotalSummary();
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    /**
     * Cập nhật hiển thị voucher đã chọn
     */
    private void updateVoucherDisplay() {
        TextView tvVoucherSelected = findViewById(R.id.tv_voucher_selected);
        if (tvVoucherSelected != null) {
            if (selectedVoucher != null) {
                tvVoucherSelected.setText(selectedVoucher.getName() + " (" + selectedVoucher.getCode() + ")");
                tvVoucherSelected.setTextColor(Color.parseColor("#4CAF50"));
            } else {
                tvVoucherSelected.setText("Chưa chọn");
                tvVoucherSelected.setTextColor(Color.parseColor("#757575"));
            }
        }
        // Cập nhật trạng thái nút chọn voucher chung
        updateVoucherButtonState();
    }

    /**
     * Cập nhật tổng tiền tạm tính (có tính voucher nếu có)
     */
    private void updateTotalSummary() {
        TextView tvTotalSummary = findViewById(R.id.tv_total_summary);
        if (tvTotalSummary == null) {
            Log.w(TAG, "updateTotalSummary: tv_total_summary not found");
            return;
        }

        if (orders == null || orders.isEmpty()) {
            tvTotalSummary.setText("---");
            Log.d(TAG, "updateTotalSummary: No orders, setting total to ---");
            return;
        }

        // Tính tổng tiền từ totalAmount của các order (trước khi có discount của order)
        // Và tính cả discount từ voucher của từng order
        double totalBeforeVoucher = 0.0;
        double totalOrderVoucherDiscount = 0.0;

        for (Order order : orders) {
            if (order != null) {
                // Lấy totalAmount (tổng tiền gốc) thay vì finalAmount (đã có discount của order)
                double orderTotal = order.getTotalAmount();
                if (orderTotal <= 0) {
                    // Nếu totalAmount = 0, thử lấy finalAmount + discount
                    orderTotal = order.getFinalAmount() + order.getDiscount();
                }
                totalBeforeVoucher += orderTotal;

                // Tính discount từ voucher của order này (nếu có)
                Voucher orderVoucher = orderVoucherMap.get(order.getId());
                if (orderVoucher != null && orderVoucher.canApply()) {
                    double orderVoucherDiscount = orderVoucher.calculateDiscount(orderTotal);
                    totalOrderVoucherDiscount += orderVoucherDiscount;
                }
            }
        }

        Log.d(TAG, "updateTotalSummary: Total before voucher = " + formatCurrency(totalBeforeVoucher) +
              ", Total order voucher discount = " + formatCurrency(totalOrderVoucherDiscount));

        // Áp dụng voucher chung nếu có
        double finalTotal = totalBeforeVoucher - totalOrderVoucherDiscount; // Trừ discount từ voucher của từng order
        double voucherDiscount = totalOrderVoucherDiscount; // Discount từ voucher của các order

        if (selectedVoucher != null) {
            // Kiểm tra voucher có thể áp dụng được không
            boolean canApply = selectedVoucher.canApply();
            Log.d(TAG, "updateTotalSummary: Global voucher check - code: " + selectedVoucher.getCode() +
                  ", status: " + selectedVoucher.getStatus() +
                  ", discountType: " + selectedVoucher.getDiscountType() +
                  ", discountValue: " + selectedVoucher.getDiscountValue() +
                  ", minOrderAmount: " + selectedVoucher.getMinOrderAmount() +
                  ", canApply: " + canApply +
                  ", totalBeforeVoucher: " + totalBeforeVoucher);

            if (canApply) {
                double globalVoucherDiscount = selectedVoucher.calculateDiscount(totalBeforeVoucher);
                Log.d(TAG, "updateTotalSummary: Calculated global discount = " + formatCurrency(globalVoucherDiscount));

                if (globalVoucherDiscount > 0) {
                    voucherDiscount = globalVoucherDiscount; // Dùng discount từ voucher chung
                    finalTotal = totalBeforeVoucher - voucherDiscount;
                    if (finalTotal < 0) finalTotal = 0;
                    Log.d(TAG, "updateTotalSummary: Global voucher discount = " + formatCurrency(voucherDiscount) +
                          ", Final total = " + formatCurrency(finalTotal));
                } else {
                    Log.w(TAG, "updateTotalSummary: Global discount = 0, using order vouchers");
                    // Nếu voucher chung không áp dụng được, dùng discount từ voucher của các order
                    finalTotal = totalBeforeVoucher - totalOrderVoucherDiscount;
                    voucherDiscount = totalOrderVoucherDiscount;
                }
            } else {
                Log.w(TAG, "updateTotalSummary: Global voucher cannot be applied, using order vouchers");
                // Nếu voucher chung không áp dụng được, dùng discount từ voucher của các order
                finalTotal = totalBeforeVoucher - totalOrderVoucherDiscount;
                voucherDiscount = totalOrderVoucherDiscount;
            }
        } else {
            // Không có voucher chung, chỉ dùng discount từ voucher của các order
            finalTotal = totalBeforeVoucher - totalOrderVoucherDiscount;
            voucherDiscount = totalOrderVoucherDiscount;
            Log.d(TAG, "updateTotalSummary: No global voucher selected, using order vouchers only");
        }

        tvTotalSummary.setText(formatCurrency(finalTotal));
        Log.d(TAG, "updateTotalSummary: Display total = " + formatCurrency(finalTotal));

        // Hiển thị số tiền giảm giá từ voucher
        TextView tvVoucherDiscount = findViewById(R.id.tv_voucher_discount);
        if (tvVoucherDiscount != null) {
            if (selectedVoucher != null && selectedVoucher.isValid() && voucherDiscount > 0) {
                tvVoucherDiscount.setText("Giảm giá: -" + formatCurrency(voucherDiscount));
                tvVoucherDiscount.setVisibility(View.VISIBLE);
            } else {
                tvVoucherDiscount.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Tạo một card hóa đơn cho một order
     */
    private void createInvoiceCard(Order order) {
        Log.d(TAG, "createInvoiceCard: Starting for order " + (order.getId() != null ? order.getId() : "null"));
        // Inflate XML layout cho card
        View cardView = LayoutInflater.from(this).inflate(R.layout.card_invoice, llOrderCards, false);
        if (cardView == null) {
            Log.e(TAG, "createInvoiceCard: Failed to inflate card_invoice layout");
            return;
        }
        CardView cardViewContainer = cardView.findViewById(R.id.cardInvoice);
        if (cardViewContainer == null) {
            Log.e(TAG, "createInvoiceCard: cardInvoice view not found in layout");
            return;
        }

        // Highlight hóa đơn mới vừa tách
        if (newlySplitOrderId != null && order.getId() != null && order.getId().equals(newlySplitOrderId)) {
            cardViewContainer.setCardBackgroundColor(0xFFE8F5E9); // Màu xanh nhạt
        }

        // Lấy các view từ XML
        TextView tvTable = cardView.findViewById(R.id.tvTable);
        TextView tvOrderCode = cardView.findViewById(R.id.tvOrderCode);
        LinearLayout llItemsContainer = cardView.findViewById(R.id.llItemsContainer);
        LinearLayout llTotals = cardView.findViewById(R.id.llTotals);

        if (tvTable == null || tvOrderCode == null || llItemsContainer == null || llTotals == null) {
            Log.e(TAG, "createInvoiceCard: Required views not found - tvTable: " + (tvTable != null) +
                  ", tvOrderCode: " + (tvOrderCode != null) + ", llItemsContainer: " + (llItemsContainer != null) +
                  ", llTotals: " + (llTotals != null));
            return;
        }

        // Thiết lập thông tin bàn và mã đơn
        tvTable.setText("Bàn: " + String.format("%02d", tableNumber));
        String orderCode = order.getId() != null ? order.getId().substring(0, Math.min(12, order.getId().length())) : "N/A";
        tvOrderCode.setText("Mã đơn: HD" + orderCode);

        // Hiển thị các món ăn
        List<Order.OrderItem> orderItems = order.getItems();
        Log.d(TAG, "createInvoiceCard: Order has " + (orderItems != null ? orderItems.size() : 0) + " items");

        // Kiểm tra xem order này có đang được chỉnh sửa không
        // CÁCH 1: So sánh ID
        boolean isEditingThisOrder = false;
        if (editingOrder != null && editingOrder.getId() != null && order.getId() != null) {
            String editingId = editingOrder.getId().trim();
            String orderId = order.getId().trim();
            isEditingThisOrder = editingId.equals(orderId);
            Log.d(TAG, "=== Checking edit mode ===");
            Log.d(TAG, "editingOrderId: '" + editingId + "' (length: " + editingId.length() + ")");
            Log.d(TAG, "orderId: '" + orderId + "' (length: " + orderId.length() + ")");
            Log.d(TAG, "isEditingThisOrder: " + isEditingThisOrder);

            if (!isEditingThisOrder) {
                Log.w(TAG, "ID mismatch! Comparing character by character:");
                int minLen = Math.min(editingId.length(), orderId.length());
                for (int i = 0; i < minLen; i++) {
                    if (editingId.charAt(i) != orderId.charAt(i)) {
                        Log.w(TAG, "Difference at position " + i + ": editingId='" + editingId.charAt(i) +
                              "' (" + (int)editingId.charAt(i) + "), orderId='" + orderId.charAt(i) +
                              "' (" + (int)orderId.charAt(i) + ")");
                        break;
                    }
                }
            }
        } else {
            Log.d(TAG, "Edit mode check failed - editingOrder: " + (editingOrder != null) +
                  ", editingOrderId: " + (editingOrder != null ? editingOrder.getId() : "null") +
                  ", orderId: " + (order.getId() != null ? order.getId() : "null"));
        }

        // CÁCH 2: So sánh object reference (fallback)
        if (!isEditingThisOrder && editingOrder != null && order == editingOrder) {
            Log.d(TAG, "Objects are the same reference, forcing isEditingThisOrder = true");
            isEditingThisOrder = true;
        }

        // CÁCH 3: Kiểm tra lại một lần nữa trước khi tạo items
        // Nếu editingOrder != null và có cùng tableNumber thì kiểm tra lại ID
        if (!isEditingThisOrder && editingOrder != null && editingOrder.getTableNumber() == order.getTableNumber()) {
            if (editingOrder.getId() != null && order.getId() != null) {
                String editingId = editingOrder.getId().trim();
                String orderId = order.getId().trim();
                if (editingId.equals(orderId)) {
                    isEditingThisOrder = true;
                    Log.d(TAG, "Re-check before creating items: Force isEditingThisOrder = true based on ID match");
                }
            }
        }

        llItemsContainer.removeAllViews();
        if (orderItems != null && !orderItems.isEmpty()) {
            Log.d(TAG, "createInvoiceCard: Processing " + orderItems.size() + " items for order " + order.getId());
            for (int i = 0; i < orderItems.size(); i++) {
                final int itemIndex = i;
                Order.OrderItem item = orderItems.get(i);
                if (item == null) {
                    Log.w(TAG, "createInvoiceCard: Item at index " + i + " is null, skipping");
                    continue;
                }

                Log.d(TAG, "createInvoiceCard: Processing item " + i + ": name=" + item.getName() +
                      ", quantity=" + item.getQuantity() + ", price=" + item.getPrice());

                // Sử dụng layout khác nhau cho edit mode và view mode
                View itemRow;
                try {
                    if (isEditingThisOrder) {
                        Log.d(TAG, "Using edit layout for item: " + item.getName() + " at index: " + itemIndex);
                        itemRow = LayoutInflater.from(this).inflate(R.layout.item_invoice_row_edit, llItemsContainer, false);

                        Button btnMinus = itemRow.findViewById(R.id.btnMinus);
                        Button btnPlus = itemRow.findViewById(R.id.btnPlus);
                        TextView tvQty = itemRow.findViewById(R.id.tvItemQuantity);

                        if (btnMinus != null && btnPlus != null && tvQty != null) {
                            // Hiển thị số lượng
                            tvQty.setText(String.valueOf(item.getQuantity()));

                            // Setup click listeners
                            btnMinus.setOnClickListener(v -> {
                                Log.d(TAG, "Minus button clicked for item index: " + itemIndex);
                                decreaseItemQuantity(order, itemIndex);
                            });

                            btnPlus.setOnClickListener(v -> {
                                Log.d(TAG, "Plus button clicked for item index: " + itemIndex);
                                increaseItemQuantity(order, itemIndex);
                            });

                            // Đảm bảo buttons hiển thị
                            btnMinus.setVisibility(View.VISIBLE);
                            btnPlus.setVisibility(View.VISIBLE);
                            btnMinus.setEnabled(true);
                            btnPlus.setEnabled(true);

                            Log.d(TAG, "Edit buttons set up successfully for item: " + item.getName());
                        } else {
                            Log.e(TAG, "Edit layout buttons not found! btnMinus=" + (btnMinus != null) +
                                  ", btnPlus=" + (btnPlus != null) + ", tvQty=" + (tvQty != null));
                        }
                    } else {
                        // Không ở chế độ chỉnh sửa - chỉ hiển thị số lượng
                        itemRow = LayoutInflater.from(this).inflate(R.layout.item_invoice_row, llItemsContainer, false);
                        if (itemRow == null) {
                            Log.e(TAG, "createInvoiceCard: Failed to inflate item_invoice_row layout");
                            continue;
                        }
                        TextView tvQty = itemRow.findViewById(R.id.tvItemQuantity);
                        if (tvQty != null) {
                            tvQty.setText("x" + item.getQuantity());
                        } else {
                            Log.e(TAG, "createInvoiceCard: tvItemQuantity not found in item_invoice_row layout");
                        }
                    }

                    if (itemRow == null) {
                        Log.e(TAG, "createInvoiceCard: itemRow is null after inflation, skipping item " + i);
                        continue;
                    }

                    TextView tvItemName = itemRow.findViewById(R.id.tvItemName);
                    TextView tvItemPrice = itemRow.findViewById(R.id.tvItemPrice);

                    if (tvItemName == null || tvItemPrice == null) {
                        Log.e(TAG, "createInvoiceCard: Required views not found - tvItemName=" + (tvItemName != null) +
                              ", tvItemPrice=" + (tvItemPrice != null));
                        continue;
                    }

                    tvItemName.setText(item.getName() != null ? item.getName() : "(Không tên)");
                    double itemTotal = item.getPrice() * item.getQuantity();
                    tvItemPrice.setText(formatCurrency(itemTotal));

                    llItemsContainer.addView(itemRow);
                    Log.d(TAG, "createInvoiceCard: Successfully added item " + i + " to container. Container now has " +
                          llItemsContainer.getChildCount() + " children");
                } catch (Exception e) {
                    Log.e(TAG, "createInvoiceCard: Error creating item row for item " + i + ": " + e.getMessage(), e);
                }
            }
            Log.d(TAG, "createInvoiceCard: Finished processing items. Total items added: " + llItemsContainer.getChildCount());
        } else {
            Log.w(TAG, "createInvoiceCard: No items to display - orderItems is " + (orderItems == null ? "null" : "empty"));
        }

        // Nút thêm món (chỉ hiển thị khi đang chỉnh sửa)
        if (isEditingThisOrder) {
            Log.d(TAG, "Adding action buttons for editing order: " + order.getId());
            View actionLayout = LayoutInflater.from(this).inflate(R.layout.layout_invoice_actions, llItemsContainer, false);
            Button btnAddItem = actionLayout.findViewById(R.id.btnAddItem);
            Button btnSave = actionLayout.findViewById(R.id.btnSave);
            Button btnCancel = actionLayout.findViewById(R.id.btnCancel);

            if (btnAddItem != null) {
                btnAddItem.setOnClickListener(v -> {
                    Log.d(TAG, "btnAddItem clicked");
                    showAddItemDialog(order);
                });
            } else {
                Log.e(TAG, "btnAddItem not found in layout!");
            }

            if (btnSave != null) {
                btnSave.setOnClickListener(v -> {
                    Log.d(TAG, "btnSave clicked");
                    saveOrderChanges(order);
                });
            } else {
                Log.e(TAG, "btnSave not found in layout!");
            }

            if (btnCancel != null) {
                btnCancel.setOnClickListener(v -> {
                    Log.d(TAG, "btnCancel clicked");
                    editingOrder = null;
                    loadInvoiceData();
                });
            } else {
                Log.e(TAG, "btnCancel not found in layout!");
            }

            llItemsContainer.addView(actionLayout);

            // Nếu items đã được tạo với layout thường, rebuild lại với edit layout
            if (!isEditingThisOrder) {
                Log.w(TAG, "Action buttons added but items were created with normal layout, rebuilding...");
                // Items đã được add ở trên, không cần rebuild lại
            }
        } else {
            Log.d(TAG, "Not in edit mode for order: " + order.getId());
        }

        // Tổng kết thanh toán - Sử dụng XML layout
        View totalsView = LayoutInflater.from(this).inflate(R.layout.layout_invoice_totals, llTotals, false);
        TextView tvTotal = totalsView.findViewById(R.id.tvTotal);
        TextView tvDiscount = totalsView.findViewById(R.id.tvDiscount);
        TextView tvFinalAmount = totalsView.findViewById(R.id.tvFinalAmount);
        TextView tvVoucherCard = totalsView.findViewById(R.id.tv_voucher_card);
        Button btnSelectVoucherCard = totalsView.findViewById(R.id.btn_select_voucher_card);
        LinearLayout llVoucherCard = totalsView.findViewById(R.id.ll_voucher_card);

        // Lấy voucher đã chọn cho order này (nếu có)
        Voucher orderVoucher = orderVoucherMap.get(order.getId());

        // Tính tổng tiền với voucher của order này
        double orderTotal = order.getTotalAmount();
        if (orderTotal <= 0) {
            // Nếu totalAmount = 0, tính từ finalAmount + discount mặc định
            double defaultDiscount = order.getDiscount();
            orderTotal = order.getFinalAmount() + defaultDiscount;
        }

        // Chỉ tính discount từ voucher nếu có chọn voucher
        // Nếu không chọn voucher, KHÔNG có discount (kể cả discount mặc định của order)
        double orderDiscount = 0.0; // Bắt đầu từ 0

        if (orderVoucher != null && orderVoucher.canApply()) {
            // Chỉ tính discount từ voucher khi có chọn voucher
            orderDiscount = orderVoucher.calculateDiscount(orderTotal);
            Log.d(TAG, "createInvoiceCard: Voucher applied for order " + order.getId() +
                  ", discount: " + formatCurrency(orderDiscount));
        } else {
            // Không có voucher, không có discount
            Log.d(TAG, "createInvoiceCard: No voucher selected for order " + order.getId() + ", discount = 0");
        }

        double finalAmount = orderTotal - orderDiscount;
        if (finalAmount < 0) finalAmount = 0;

        // Hiển thị thông tin
        tvTotal.setText(formatCurrency(orderTotal));
        tvDiscount.setText(formatCurrency(orderDiscount));
        tvFinalAmount.setText(formatCurrency(finalAmount));

        // Hiển thị voucher
        if (tvVoucherCard != null) {
            if (orderVoucher != null) {
                tvVoucherCard.setText(orderVoucher.getName() + " (" + orderVoucher.getCode() + ")");
                tvVoucherCard.setTextColor(Color.parseColor("#4CAF50"));
            } else {
                tvVoucherCard.setText("Chưa chọn");
                tvVoucherCard.setTextColor(Color.parseColor("#757575"));
            }
        }

        // Setup nút chọn voucher cho order này
        if (btnSelectVoucherCard != null) {
            final Order currentOrder = order;
            btnSelectVoucherCard.setOnClickListener(v -> showVoucherDialogForOrder(currentOrder));
            // Cập nhật trạng thái nút dựa trên điều kiện
            if (selectedVoucher != null) {
                // Nếu đã có voucher chung, disable nút chọn voucher cho order
                btnSelectVoucherCard.setEnabled(false);
                btnSelectVoucherCard.setAlpha(0.5f); // Làm mờ nút
            } else {
                btnSelectVoucherCard.setEnabled(true);
                btnSelectVoucherCard.setAlpha(1.0f); // Bình thường
            }
        }

        llTotals.removeAllViews();
        llTotals.addView(totalsView);

        // Thêm card vào container
        if (llOrderCards != null) {
            try {
                llOrderCards.addView(cardView);
                Log.d(TAG, "createInvoiceCard: Card added to llOrderCards. Total children: " + llOrderCards.getChildCount());
                Log.d(TAG, "createInvoiceCard: CardView visibility: " + cardView.getVisibility() + ", width: " + cardView.getWidth() + ", height: " + cardView.getHeight());
            } catch (Exception e) {
                Log.e(TAG, "createInvoiceCard: Error adding card to container: " + e.getMessage(), e);
                e.printStackTrace();
            }
        } else {
            Log.e(TAG, "createInvoiceCard: llOrderCards is null! Cannot add card.");
        }

        // Lưu CardView vào map để dễ dàng refresh sau này
        if (order.getId() != null) {
            orderCardMap.put(order.getId(), cardViewContainer);

            // Highlight nếu là order cần focus
            if (targetOrderId != null && targetOrderId.trim().equals(order.getId().trim())) {
                cardViewContainer.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
            }
        }

        // Nhấn giữ vào card để mở menu tùy chọn, chạm nhanh để thanh toán
        final Order currentOrder = order;

        cardViewContainer.setOnLongClickListener(v -> {
            showInvoiceOptionsDialogForOrder(currentOrder);
            return true;
        });
    }

    /**
     * Thanh toán cho một order cụ thể - chuyển sang màn hình thanh toán
     */
    private void processPaymentForOrder(Order order) {
        if (order == null || order.getId() == null) {
            Toast.makeText(this, "Hóa đơn không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        // Tính tổng tiền với voucher của order này (nếu có) - giống như trong card
        double orderTotal = order.getTotalAmount();
        if (orderTotal <= 0) {
            double defaultDiscount = order.getDiscount();
            orderTotal = order.getFinalAmount() + defaultDiscount;
        }

        // Lấy voucher đã chọn cho order này
        Voucher orderVoucher = orderVoucherMap.get(order.getId());
        double discount = 0.0;
        double finalAmount = orderTotal;

        if (orderVoucher != null && orderVoucher.canApply()) {
            discount = orderVoucher.calculateDiscount(orderTotal);
            finalAmount = orderTotal - discount;
            if (finalAmount < 0) finalAmount = 0;
        }

        Log.d(TAG, "processPaymentForOrder: orderId = " + order.getId() +
              ", orderTotal = " + formatCurrency(orderTotal) +
              ", discount = " + formatCurrency(discount) +
              ", finalAmount = " + formatCurrency(finalAmount));

        // Chuyển sang màn hình thanh toán với tổng tiền đã tính voucher
        Intent intent = new Intent(InvoiceActivity.this, ThanhToanActivity.class);
        intent.putExtra("orderId", order.getId());
        intent.putExtra("tableNumber", tableNumber);
        intent.putExtra("totalAmount", finalAmount); // Truyền tổng tiền đã tính voucher
        intent.putExtra("totalBeforeVoucher", orderTotal);
        intent.putExtra("voucherDiscount", discount);
        if (orderVoucher != null) {
            intent.putExtra("voucherId", orderVoucher.getId());
            intent.putExtra("voucherCode", orderVoucher.getCode());
        } else if (selectedVoucher != null) {
            // Nếu order không có voucher riêng, dùng voucher chung
            intent.putExtra("voucherId", selectedVoucher.getId());
        }
        startActivity(intent);
    }

    /**
     * Thanh toán tất cả các hóa đơn (đã tách hoặc chưa) - Chuyển sang màn hình thanh toán
     */
    private void processPaymentForAllOrders() {
        if (orders == null || orders.isEmpty()) {
            Toast.makeText(this, "Không có hóa đơn để thanh toán", Toast.LENGTH_SHORT).show();
            return;
        }

        // Tính tổng tiền từ totalAmount (trước discount của order)
        // Và tính cả discount từ voucher của từng order
        double totalBeforeVoucher = 0.0;
        double totalOrderVoucherDiscount = 0.0;

        for (Order order : orders) {
            if (order == null || order.getId() == null) continue;

            double orderTotal = order.getTotalAmount();
            if (orderTotal <= 0) {
                orderTotal = order.getFinalAmount() + order.getDiscount();
            }

            totalBeforeVoucher += orderTotal;

            Voucher orderVoucher = orderVoucherMap.get(order.getId());
            if (orderVoucher != null && orderVoucher.canApply()) {
                totalOrderVoucherDiscount += orderVoucher.calculateDiscount(orderTotal);
            }
        }


        // Áp dụng voucher chung nếu có (ưu tiên voucher chung)
        double discount = totalOrderVoucherDiscount; // Bắt đầu từ discount của các order
        double finalAmount = totalBeforeVoucher - discount;

        if (selectedVoucher != null && selectedVoucher.canApply()) {
            // Nếu có voucher chung, dùng voucher chung thay vì voucher của từng order
            double globalVoucherDiscount = selectedVoucher.calculateDiscount(totalBeforeVoucher);
            if (globalVoucherDiscount > 0) {
                discount = globalVoucherDiscount;
                finalAmount = totalBeforeVoucher - discount;
            }
        }

        if (finalAmount < 0) finalAmount = 0;

        Log.d(TAG, "processPaymentForAllOrders: totalBeforeVoucher = " + formatCurrency(totalBeforeVoucher) +
              ", discount = " + formatCurrency(discount) +
              ", finalAmount = " + formatCurrency(finalAmount));

        // Lấy danh sách orderIds
        ArrayList<String> orderIds = new ArrayList<>();
        for (Order order : orders) {
            if (order != null && order.getId() != null) {
                orderIds.add(order.getId());
            }
        }

        if (orderIds.isEmpty()) {
            Toast.makeText(this, "Không có hóa đơn hợp lệ để thanh toán", Toast.LENGTH_SHORT).show();
            return;
        }

        // Chuyển sang màn hình thanh toán
        Intent intent = new Intent(InvoiceActivity.this, ThanhToanActivity.class);
        intent.putStringArrayListExtra("orderIds", orderIds);
        intent.putExtra("tableNumber", tableNumber);
        intent.putExtra("totalAmount", finalAmount);
        intent.putExtra("totalBeforeVoucher", totalBeforeVoucher);
        intent.putExtra("voucherDiscount", discount);
        if (selectedVoucher != null) {
            intent.putExtra("voucherId", selectedVoucher.getId());
            intent.putExtra("voucherCode", selectedVoucher.getCode());
        }
        startActivity(intent);
    }

    /**
     * Thanh toán tuần tự tất cả các hóa đơn
     */
    private void payAllOrdersSequentially() {
        if (orders == null || orders.isEmpty()) {
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        // Tính tổng tiền và discount
        double totalAmount = 0.0;
        for (Order order : orders) {
            if (order != null) {
                totalAmount += order.getFinalAmount();
            }
        }

        double discount = 0.0;
        if (selectedVoucher != null && selectedVoucher.isValid()) {
            discount = selectedVoucher.calculateDiscount(totalAmount);
        }

        // Tính số tiền cho mỗi hóa đơn (chia đều discount)
        double discountPerOrder = orders.size() > 0 ? discount / orders.size() : 0;

        final java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger totalCount = new java.util.concurrent.atomic.AtomicInteger(orders.size());
        final java.util.concurrent.atomic.AtomicBoolean allSuccess = new java.util.concurrent.atomic.AtomicBoolean(true);

        for (Order order : orders) {
            if (order == null || order.getId() == null) {
                totalCount.decrementAndGet();
                continue;
            }

            double orderAmount = order.getFinalAmount() - discountPerOrder;
            if (orderAmount < 0) orderAmount = 0;

            // Thanh toán từng hóa đơn
            String voucherId = selectedVoucher != null ? selectedVoucher.getId() : null;
            orderRepository.payOrder(order.getId(), "Tiền mặt", orderAmount, voucherId, new OrderRepository.RepositoryCallback<Order>() {
                @Override
                public void onSuccess(Order result) {
                    int current = successCount.incrementAndGet();
                    Log.d(TAG, "Payment success for order " + order.getId() + " (" + current + "/" + totalCount.get() + ")");

                    if (current >= totalCount.get()) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            if (allSuccess.get()) {
                                Toast.makeText(InvoiceActivity.this, "Thanh toán thành công tất cả " + totalCount.get() + " hóa đơn", Toast.LENGTH_SHORT).show();
                                // Cập nhật trạng thái bàn và quay lại
                                updateTableStatusAndFinish();
                            } else {
                                Toast.makeText(InvoiceActivity.this, "Một số hóa đơn thanh toán thất bại", Toast.LENGTH_LONG).show();
                                loadInvoiceData(); // Reload để cập nhật
                            }
                        });
                    }
                }

                @Override
                public void onError(String message) {
                    allSuccess.set(false);
                    int current = successCount.incrementAndGet();
                    Log.e(TAG, "Payment failed for order " + order.getId() + ": " + message);

                    if (current >= totalCount.get()) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(InvoiceActivity.this, "Một số hóa đơn thanh toán thất bại: " + message, Toast.LENGTH_LONG).show();
                            loadInvoiceData(); // Reload để cập nhật
                        });
                    }
                }
            });
        }
    }

    /**
     * Hiển thị menu tùy chọn cho một order cụ thể
     */
    private void showInvoiceOptionsDialogForOrder(Order order) {
        if (order == null) {
            Toast.makeText(this, "Hóa đơn không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }
        showInvoiceOptionsDialogForSpecificOrder(order);
    }


    /**
     * Lưu request kiểm tra món vào database cho các order
     */
    private void saveCheckItemsRequestToDatabase(String[] orderIds, String userId, Runnable onComplete) {
        if (orderIds == null || orderIds.length == 0) {
            if (onComplete != null) onComplete.run();
            return;
        }

        String currentTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(new Date());

        final java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger totalCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicBoolean callbackCalled = new java.util.concurrent.atomic.AtomicBoolean(false);

        // Đếm số order hợp lệ
        for (String orderId : orderIds) {
            if (orderId != null && !orderId.trim().isEmpty()) {
                totalCount.incrementAndGet();
            }
        }

        if (totalCount.get() == 0) {
            if (onComplete != null) onComplete.run();
            return;
        }

        // Cập nhật từng order với checkItemsRequestedBy và checkItemsRequestedAt
        for (String orderId : orderIds) {
            if (orderId == null || orderId.trim().isEmpty()) {
                continue;
            }

            Map<String, Object> updates = new HashMap<>();
            if (userId != null && !userId.trim().isEmpty()) {
                updates.put("checkItemsRequestedBy", userId);
            }
            updates.put("checkItemsRequestedAt", currentTime);

            Log.d(TAG, "Updating order " + orderId + " with checkItemsRequestedAt: " + currentTime);

            orderRepository.updateOrder(orderId, updates, new OrderRepository.RepositoryCallback<Order>() {
                @Override
                public void onSuccess(Order result) {
                    int current = successCount.incrementAndGet();
                    Log.d(TAG, "Check items request saved to database for order: " + orderId + " (" + current + "/" + totalCount.get() + ")");

                    // Log chi tiết response từ server
                    if (result != null) {
                        Log.d(TAG, "Order response - checkItemsRequestedAt: " + result.getCheckItemsRequestedAt());
                        Log.d(TAG, "Order response - checkItemsRequestedBy: " + result.getCheckItemsRequestedBy());
                        if (result.getCheckItemsRequestedAt() == null || result.getCheckItemsRequestedAt().trim().isEmpty()) {
                            Log.w(TAG, "WARNING: Server response does not contain checkItemsRequestedAt field!");
                        }
                    } else {
                        Log.w(TAG, "WARNING: Server returned null order object!");
                    }

                    // Nếu tất cả đã thành công, gọi callback (chỉ gọi một lần)
                    if (current >= totalCount.get() && onComplete != null && !callbackCalled.getAndSet(true)) {
                        onComplete.run();
                    }
                }

                @Override
                public void onError(String message) {
                    Log.e(TAG, "Failed to save check items request to database for order " + orderId + ": " + message);
                    int current = successCount.incrementAndGet();

                    // Nếu đã xử lý hết (dù thành công hay thất bại), gọi callback
                    if (current >= totalCount.get() && onComplete != null && !callbackCalled.getAndSet(true)) {
                        runOnUiThread(() -> {
                            Toast.makeText(InvoiceActivity.this, "Một số yêu cầu không thể lưu, nhưng đã gửi qua socket", Toast.LENGTH_LONG).show();
                        });
                        onComplete.run();
                    }
                }
            });
        }
    }

    /**
     * Format số tiền theo định dạng Việt Nam (₫)
     */
    private String formatCurrency(double amount) {
        return CURRENCY_FORMAT.format(amount) + "₫";
    }

    /**
     * Tạo mã đơn từ order ID hoặc timestamp
     */
    private String generateOrderCode() {
        if (orders != null && !orders.isEmpty() && orders.get(0).getId() != null) {
            String orderId = orders.get(0).getId();
            String suffix = "0000";
            if (orderId.length() >= 4) {
                String last4 = orderId.substring(orderId.length() - 4);
                StringBuilder digits = new StringBuilder();
                for (char c : last4.toCharArray()) {
                    if (Character.isDigit(c)) {
                        digits.append(c);
                    } else {
                        digits.append(Math.abs(c) % 10);
                    }
                }
                if (digits.length() > 0) {
                    try {
                        int num = Integer.parseInt(digits.toString()) % 10000;
                        suffix = String.format("%04d", num);
                    } catch (NumberFormatException e) {
                        suffix = "0000";
                    }
                }
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy", Locale.getDefault());
            String year = sdf.format(new Date());
            return "HD" + year + "-" + suffix;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MMddHHmm", Locale.getDefault());
        return "HD" + sdf.format(new Date());
    }

    /**
     * Hiển thị menu tùy chọn cho một order cụ thể
     */
    private void showInvoiceOptionsDialogForSpecificOrder(Order order) {
        List<String> options = new ArrayList<>();
        options.add("Chỉnh sửa");
        options.add("Tách hóa đơn");
        options.add("In tạm tính");
        options.add("Thanh toán");
        options.add("Yêu cầu kiểm tra");
        options.add("Hủy hóa đơn");

        String[] optionArray = options.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Tùy chọn hóa đơn")
                .setItems(optionArray, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            startEditOrder(order);
                            break;
                        case 1:
                            showSplitInvoiceDialogForOrder(order);
                            break;
                        case 2:
                            printTemporaryBillForOrder(order);
                            break;
                        case 3: // Thanh toán
                            // Kiểm tra có voucher cho order hoặc voucher chung không
                            Voucher orderVoucher = orderVoucherMap.get(order.getId());
                            boolean hasVoucher =
                                    (orderVoucher != null && orderVoucher.canApply())
                                            || (selectedVoucher != null && selectedVoucher.canApply());

                            if (hasVoucher) {
                                // Có voucher → dùng luồng btnProceedPayment
                                if (btnProceedPayment != null) {
                                    btnProceedPayment.performClick();
                                } else {
                                    Toast.makeText(this, "Không tìm thấy nút thanh toán", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                // Không có voucher → dùng luồng cũ
                                handlePayment(order);
                            }
                            break;





                        case 4:
                            requestCheckItemsForOrder(order);
                            break;
                        case 5:
                            showCancelInvoiceDialogForOrder(order);
                            break;
                    }
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    /**
     * Tách hóa đơn cho một order cụ thể
     */
    private void showSplitInvoiceDialogForOrder(Order order) {
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
            Toast.makeText(this, "Hóa đơn không có món để tách", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Order.OrderItem> items = order.getItems();

        // Inflate XML layout
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_split_invoice, null);
        LinearLayout llItemsContainer = dialogView.findViewById(R.id.llItemsContainer);

        // Map để lưu EditText cho mỗi item
        Map<Integer, TextView> qtyEditTextMap = new HashMap<>();

        for (int i = 0; i < items.size(); i++) {
            Order.OrderItem item = items.get(i);
            if (item == null) continue;

            // Inflate item layout
            View itemView = LayoutInflater.from(this).inflate(R.layout.dialog_split_invoice_item, llItemsContainer, false);

            // Tên món và số lượng hiện tại
            TextView tvItemInfo = itemView.findViewById(R.id.tvItemInfo);
            TextView tvQty = itemView.findViewById(R.id.tvQtySplit);
            Button btnMinus = itemView.findViewById(R.id.btnMinusSplit);
            Button btnPlus = itemView.findViewById(R.id.btnPlusSplit);

            tvItemInfo.setText(item.getName() + " (hiện có: x" + item.getQuantity() + ")");
            tvQty.setText("0");

            qtyEditTextMap.put(i, tvQty);

            btnMinus.setOnClickListener(v -> {
                int current = parseIntSafe(tvQty.getText().toString());
                if (current > 0) {
                    tvQty.setText(String.valueOf(current - 1));
                }
            });

            btnPlus.setOnClickListener(v -> {
                int current = parseIntSafe(tvQty.getText().toString());
                // Giới hạn không vượt quá số lượng hiện có
                if (current < item.getQuantity()) {
                    tvQty.setText(String.valueOf(current + 1));
                }
            });

            llItemsContainer.addView(itemView);
        }

        new AlertDialog.Builder(this)
                .setTitle("Tách hóa đơn - Nhập số lượng")
                .setView(dialogView)
                .setPositiveButton("Tách", (dialog, which) -> {
                    // Lấy số lượng từ các EditText
                    Map<Integer, Integer> splitQuantities = new HashMap<>();
                    boolean hasAnySplit = false;
                    for (Map.Entry<Integer, TextView> entry : qtyEditTextMap.entrySet()) {
                        int itemIndex = entry.getKey();
                        TextView tv = entry.getValue();
                        int qty = parseIntSafe(tv.getText().toString());
                        if (qty > 0) {
                            splitQuantities.put(itemIndex, qty);
                            hasAnySplit = true;
                        }
                    }

                    if (!hasAnySplit) {
                        Toast.makeText(this, "Vui lòng nhập số lượng muốn tách (ít nhất 1 món)", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Kiểm tra số lượng hợp lệ
                    boolean isValid = true;
                    for (Map.Entry<Integer, Integer> entry : splitQuantities.entrySet()) {
                        int itemIndex = entry.getKey();
                        int splitQty = entry.getValue();
                        Order.OrderItem item = items.get(itemIndex);
                        if (splitQty > item.getQuantity()) {
                            Toast.makeText(this, "Số lượng tách không được vượt quá số lượng hiện có của " + item.getName(), Toast.LENGTH_LONG).show();
                            isValid = false;
                            break;
                        }
                    }

                    if (isValid) {
                        splitInvoiceForOrderWithQuantities(order, splitQuantities);
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    /**
     * Tách hóa đơn cho một order cụ thể với số lượng cụ thể cho từng món
     */
    private void splitInvoiceForOrderWithQuantities(Order order, Map<Integer, Integer> splitQuantities) {
        if (order == null || order.getItems() == null || splitQuantities == null || splitQuantities.isEmpty()) {
            return;
        }

        List<Order.OrderItem> itemsToSplit = new ArrayList<>();
        List<Order.OrderItem> remainingItems = new ArrayList<>();

        List<Order.OrderItem> items = order.getItems();
        for (int i = 0; i < items.size(); i++) {
            Order.OrderItem item = items.get(i);
            if (item == null) continue;

            Integer splitQty = splitQuantities.get(i);
            if (splitQty != null && splitQty > 0) {
                // Tạo item mới cho phần tách
                Order.OrderItem splitItem = new Order.OrderItem();
                splitItem.setMenuItemId(item.getMenuItemId());
                splitItem.setName(item.getName());
                splitItem.setPrice(item.getPrice());
                splitItem.setQuantity(splitQty);
                itemsToSplit.add(splitItem);

                // Tạo item mới cho phần còn lại
                int remainingQty = item.getQuantity() - splitQty;
                if (remainingQty > 0) {
                    Order.OrderItem remainingItem = new Order.OrderItem();
                    remainingItem.setMenuItemId(item.getMenuItemId());
                    remainingItem.setName(item.getName());
                    remainingItem.setPrice(item.getPrice());
                    remainingItem.setQuantity(remainingQty);
                    remainingItems.add(remainingItem);
                }
            } else {
                // Không tách món này, giữ nguyên
                remainingItems.add(item);
            }
        }

        if (itemsToSplit.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập số lượng muốn tách", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        // Lấy thông tin từ order gốc
        String serverId = order.getServerId();
        String cashierId = order.getCashierId();
        String tableId = order.getTableId();

        if (serverId == null || serverId.isEmpty()) {
            serverId = "64a7f3b2c9d1e2f3a4b5c6d7";
        }
        if (cashierId == null || cashierId.isEmpty()) {
            cashierId = "64b8e4c3d1f2a3b4c5d6e7f8";
        }

        // Tạo order mới cho phần tách
        Order newOrder = new Order();
        newOrder.setTableNumber(tableNumber);
        newOrder.setItems(itemsToSplit);
        newOrder.setServerId(serverId);
        newOrder.setCashierId(cashierId);
        if (tableId != null && !tableId.isEmpty()) {
            newOrder.setTableId(tableId);
        }

        double splitTotal = 0;
        for (Order.OrderItem item : itemsToSplit) {
            splitTotal += item.getPrice() * item.getQuantity();
        }
        newOrder.setTotalAmount(splitTotal);
        newOrder.setFinalAmount(splitTotal);
        newOrder.setDiscount(0);
        newOrder.setPaidAmount(0);
        newOrder.setChange(0);
        newOrder.setPaymentMethod("cash");
        newOrder.setOrderStatus("pending");
        newOrder.setPaid(false);

        orderRepository.createOrder(newOrder, new OrderRepository.RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order result) {
                // Lưu ID của hóa đơn mới để highlight
                if (result != null && result.getId() != null) {
                    newlySplitOrderId = result.getId();
                }

                // Cập nhật order cũ với items còn lại
                if (!remainingItems.isEmpty()) {
                    double remainingTotal = 0;
                    for (Order.OrderItem item : remainingItems) {
                        remainingTotal += item.getPrice() * item.getQuantity();
                    }

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("items", remainingItems);
                    updates.put("totalAmount", remainingTotal);
                    updates.put("finalAmount", remainingTotal - order.getDiscount());

                    orderRepository.updateOrder(order.getId(), updates, new OrderRepository.RepositoryCallback<Order>() {
                        @Override
                        public void onSuccess(Order updated) {
                            runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(InvoiceActivity.this, "Đã tách hóa đơn thành công. Hóa đơn mới đã được tạo.", Toast.LENGTH_SHORT).show();
                                loadInvoiceData();
                            });
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(InvoiceActivity.this, "Lỗi cập nhật hóa đơn: " + message, Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                } else {
                    // Nếu không còn món, xóa order cũ
                    orderRepository.deleteOrder(order.getId(), new OrderRepository.RepositoryCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(InvoiceActivity.this, "Đã tách hóa đơn thành công. Hóa đơn mới đã được tạo.", Toast.LENGTH_SHORT).show();
                                loadInvoiceData();
                            });
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(InvoiceActivity.this, "Lỗi xóa hóa đơn cũ: " + message, Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                }
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(InvoiceActivity.this, "Lỗi tách hóa đơn: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * In tạm tính (lấy order đầu tiên) - Sau khi in xong sẽ xóa dấu yêu cầu tạm tính trên DB
     */
    private void printTemporaryBill() {
        if (orders == null || orders.isEmpty()) {
            Toast.makeText(this, "Không có hóa đơn để in", Toast.LENGTH_SHORT).show();
            return;
        }

        Order firstOrder = orders.get(0);
        if (firstOrder == null) {
            Toast.makeText(this, "Hóa đơn không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        printTemporaryBillForOrder(firstOrder);
    }

    /**
     * In tạm tính cho một order cụ thể - Mở Activity hiển thị hóa đơn từ XML layout
     * Sau khi in xong sẽ xóa dấu yêu cầu tạm tính trên DB
     */
    private void printTemporaryBillForOrder(Order order) {
        if (order == null) {
            Toast.makeText(this, "Hóa đơn không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        // Mở PrintBillActivity với orderId để có thể clear sau khi in xong
        Intent intent = new Intent(this, PrintBillActivity.class);
        intent.putExtra("order", order);
        intent.putExtra("tableNumber", tableNumber);
        intent.putExtra("orderId", order.getId()); // Truyền orderId để clear sau khi in
        String orderCode = order.getId() != null
                ? (order.getId().length() > 12 ? "HD" + order.getId().substring(0, 12) : "HD" + order.getId())
                : generateOrderCode();
        intent.putExtra("orderCode", orderCode);

        // Sử dụng ActivityResultLauncher để nhận kết quả sau khi in xong
        printBillLauncher.launch(intent);
    }

    /**
     * Hàm chung mở màn hình in tạm tính
     */
    private void startPrintBillActivity(Order order) {
        Intent intent = new Intent(this, PrintBillActivity.class);
        intent.putExtra("order", order);
        intent.putExtra("tableNumber", tableNumber);
        String orderCode = order.getId() != null
                ? (order.getId().length() > 12 ? "HD" + order.getId().substring(0, 12) : "HD" + order.getId())
                : generateOrderCode();
        intent.putExtra("orderCode", orderCode);
        startActivity(intent);
    }

    /**
     * Xóa cờ yêu cầu tạm tính trên DB để yêu cầu biến mất
     */
    private void clearTempCalculationRequest(Order order, Runnable onDone) {
        if (order == null || order.getId() == null) {
            if (onDone != null) onDone.run();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("tempCalculationRequestedBy", null);
        updates.put("tempCalculationRequestedAt", null);

        Log.d(TAG, "clearTempCalculationRequest: Clearing temp calculation request for order " + order.getId());

        orderRepository.updateOrder(order.getId(), updates, new OrderRepository.RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order result) {
                Log.d(TAG, "clearTempCalculationRequest: Successfully cleared temp calculation request for order " + order.getId());
                // Gửi broadcast để ThuNganActivity reload danh sách yêu cầu
                Intent refreshIntent = new Intent(ACTION_REFRESH_TABLES);
                sendBroadcast(refreshIntent);
                Log.d(TAG, "clearTempCalculationRequest: Broadcast sent to refresh tables");
                if (onDone != null) onDone.run();
            }

            @Override
            public void onError(String message) {
                // Nếu fail vẫn cho phép in, tránh chặn luồng người dùng
                Log.w(TAG, "clearTempCalculationRequest failed: " + message);
                // Vẫn gửi broadcast để reload (có thể server đã xóa rồi)
                Intent refreshIntent = new Intent(ACTION_REFRESH_TABLES);
                sendBroadcast(refreshIntent);
                if (onDone != null) onDone.run();
            }
        });
    }

    /**
     * Yêu cầu kiểm tra cho một order cụ thể
     */
    private void requestCheckItemsForOrder(Order order) {
        if (order == null || order.getId() == null) {
            Toast.makeText(this, "Hóa đơn không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] orderIds = new String[]{order.getId()};

        progressBar.setVisibility(View.VISIBLE);

        // Lấy user ID từ SharedPreferences
        SharedPreferences prefs = getSharedPreferences("RestaurantPrefs", MODE_PRIVATE);
        String userId = prefs.getString("userId", null);

        Log.d(TAG, "Starting check items request for order " + order.getId());

        // Lưu request vào database
        saveCheckItemsRequestToDatabase(orderIds, userId, new Runnable() {
            @Override
            public void run() {
                // Sau khi lưu vào database thành công, gửi socket event
                runOnUiThread(() -> {
                    // Đảm bảo socket đã được init và connect
                    if (!socketManager.isConnected()) {
                        Log.w(TAG, "Socket not connected, initializing...");
                        initSocket();
                        // Đợi một chút để socket kết nối
                        new android.os.Handler().postDelayed(() -> {
                            socketManager.emitCheckItemsRequest(tableNumber, orderIds);
                        }, 1000);
                    } else {
                        socketManager.emitCheckItemsRequest(tableNumber, orderIds);
                    }

                    // Gửi broadcast trong app để màn phục vụ nhận được ngay (nếu đang mở)
                    Intent intent = new Intent();
                    intent.setAction("com.ph48845.datn_qlnh_rmis.ACTION_CHECK_ITEMS");
                    intent.putExtra("tableNumber", tableNumber);
                    intent.putExtra("orderIds", orderIds);
                    sendBroadcast(intent);

                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(InvoiceActivity.this, "Đã gửi yêu cầu kiểm tra lại món ăn", Toast.LENGTH_LONG).show();
                    Log.d(TAG, "Request check items for order " + order.getId() + " sent to server and broadcast");
                });
            }
        });
    }

    /**
     * Hủy hóa đơn cho một order cụ thể
     */
    private void showCancelInvoiceDialogForOrder(Order order) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_cancel_invoice, null);
        EditText etReason = dialogView.findViewById(R.id.etReason);

        new AlertDialog.Builder(this)
            .setTitle("Hủy hóa đơn")
            .setView(dialogView)
            .setPositiveButton("Hủy đơn", (dialog, which) -> {
                String reason = etReason.getText().toString().trim();
                if (reason.isEmpty()) {
                    Toast.makeText(this, "Vui lòng nhập lý do hủy", Toast.LENGTH_SHORT).show();
                    return;
                }
                cancelInvoiceForOrder(order, reason);
            })
            .setNegativeButton("Không", null)
            .show();
    }

    /**
     * Hủy hóa đơn cho một order cụ thể
     * Khi hủy chỉ xóa order được chọn, sau đó kiểm tra xem còn order nào trong bàn không
     * Nếu không còn order nào thì mới reset bàn
     */
    private void cancelInvoiceForOrder(Order order, String reason) {
        if (order == null || order.getId() == null) {
            Toast.makeText(this, "Hóa đơn không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        Log.d(TAG, "cancelInvoiceForOrder: Starting to cancel order " + order.getId() + " for table " + tableNumber + " with reason: " + reason);

        // Cập nhật order với lý do hủy trước khi xóa
        Map<String, Object> updates = new HashMap<>();
        updates.put("cancelReason", reason != null ? reason : "Hủy hóa đơn");
        updates.put("orderStatus", "cancelled");

        orderRepository.updateOrder(order.getId(), updates, new OrderRepository.RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order updatedOrder) {
                Log.d(TAG, "cancelInvoiceForOrder: Updated order " + order.getId() + " with cancelReason, now deleting...");
                // Sau khi cập nhật thành công, xóa order
                orderRepository.deleteOrder(order.getId(), new OrderRepository.RepositoryCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        Log.d(TAG, "cancelInvoiceForOrder: Successfully deleted order " + order.getId());
                        // Kiểm tra xem còn order nào trong bàn không
                        checkRemainingOrdersAndUpdateTable();
                    }

                    @Override
                    public void onError(String message) {
                        Log.e(TAG, "cancelInvoiceForOrder: Error deleting order " + order.getId() + ": " + message);
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(InvoiceActivity.this, "Lỗi hủy hóa đơn: " + message, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "cancelInvoiceForOrder: Error updating order " + order.getId() + " with cancelReason: " + message);
                // Vẫn tiếp tục xóa order (có thể server không hỗ trợ update cancelReason)
                orderRepository.deleteOrder(order.getId(), new OrderRepository.RepositoryCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        Log.d(TAG, "cancelInvoiceForOrder: Successfully deleted order " + order.getId() + " (without cancelReason)");
                        // Kiểm tra xem còn order nào trong bàn không
                        checkRemainingOrdersAndUpdateTable();
                    }

                    @Override
                    public void onError(String deleteError) {
                        Log.e(TAG, "cancelInvoiceForOrder: Error deleting order " + order.getId() + ": " + deleteError);
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(InvoiceActivity.this, "Lỗi hủy hóa đơn: " + deleteError, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            }
        });
    }

    /**
     * Kiểm tra xem còn order nào trong bàn không
     * Nếu còn: reload dữ liệu hóa đơn
     * Nếu không còn: reset bàn
     */
    private void checkRemainingOrdersAndUpdateTable() {
        orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> remainingOrders) {
                // Lọc các order chưa thanh toán và chưa bị hủy
                List<Order> activeOrders = filterUnpaidOrders(remainingOrders);

                if (activeOrders == null || activeOrders.isEmpty()) {
                    // Không còn order nào, reset bàn
                    Log.d(TAG, "checkRemainingOrdersAndUpdateTable: No remaining orders, resetting table");
                    updateTableStatusAndFinish();
                } else {
                    // Còn order, reload dữ liệu hóa đơn
                    Log.d(TAG, "checkRemainingOrdersAndUpdateTable: Found " + activeOrders.size() + " remaining orders, reloading invoice data");
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(InvoiceActivity.this, "Đã hủy hóa đơn", Toast.LENGTH_SHORT).show();
                        // Reload dữ liệu hóa đơn (sẽ tự động cập nhật orderVoucherMap khi reload)
                        loadInvoiceData();
                    });
                }
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "checkRemainingOrdersAndUpdateTable: Error loading remaining orders: " + message);
                // Nếu không thể kiểm tra, vẫn reload dữ liệu để cập nhật UI
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(InvoiceActivity.this, "Đã hủy hóa đơn", Toast.LENGTH_SHORT).show();
                    loadInvoiceData();
                });
            }
        });
    }

    /**
     * Xóa tất cả orders của bàn (đệ quy)
     * Trước khi xóa, cập nhật order với lý do hủy (cancelReason)
     */
    private void deleteAllOrdersForTable(List<Order> ordersToDelete, int currentIndex, String cancelReason) {
        if (currentIndex >= ordersToDelete.size()) {
            // Đã xóa hết, cập nhật trạng thái bàn
            Log.d(TAG, "deleteAllOrdersForTable: All orders deleted, updating table status");
            updateTableStatusAndFinish();
            return;
        }

        Order order = ordersToDelete.get(currentIndex);
        if (order == null || order.getId() == null) {
            // Bỏ qua order null, tiếp tục với order tiếp theo
            deleteAllOrdersForTable(ordersToDelete, currentIndex + 1, cancelReason);
            return;
        }

        Log.d(TAG, "deleteAllOrdersForTable: Processing order " + order.getId() + " (" + (currentIndex + 1) + "/" + ordersToDelete.size() + ")");

        // Cập nhật order với lý do hủy trước khi xóa
        Map<String, Object> updates = new HashMap<>();
        updates.put("cancelReason", cancelReason != null ? cancelReason : "Hủy hóa đơn");
        updates.put("orderStatus", "cancelled");

        orderRepository.updateOrder(order.getId(), updates, new OrderRepository.RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order updatedOrder) {
                Log.d(TAG, "deleteAllOrdersForTable: Updated order " + order.getId() + " with cancelReason, now deleting...");
                // Sau khi cập nhật thành công, xóa order
                orderRepository.deleteOrder(order.getId(), new OrderRepository.RepositoryCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        Log.d(TAG, "deleteAllOrdersForTable: Successfully deleted order " + order.getId());
                        // Tiếp tục xóa order tiếp theo
                        deleteAllOrdersForTable(ordersToDelete, currentIndex + 1, cancelReason);
                    }

                    @Override
                    public void onError(String message) {
                        Log.e(TAG, "deleteAllOrdersForTable: Error deleting order " + order.getId() + ": " + message);
                        // Vẫn tiếp tục xóa các order còn lại
                        deleteAllOrdersForTable(ordersToDelete, currentIndex + 1, cancelReason);
                    }
                });
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "deleteAllOrdersForTable: Error updating order " + order.getId() + " with cancelReason: " + message);
                // Vẫn tiếp tục xóa order (có thể server không hỗ trợ update cancelReason)
                orderRepository.deleteOrder(order.getId(), new OrderRepository.RepositoryCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        Log.d(TAG, "deleteAllOrdersForTable: Successfully deleted order " + order.getId() + " (without cancelReason)");
                        deleteAllOrdersForTable(ordersToDelete, currentIndex + 1, cancelReason);
                    }

                    @Override
                    public void onError(String deleteError) {
                        Log.e(TAG, "deleteAllOrdersForTable: Error deleting order " + order.getId() + ": " + deleteError);
                        deleteAllOrdersForTable(ordersToDelete, currentIndex + 1, cancelReason);
                    }
                });
            }
        });
    }

    /**
     * Cập nhật trạng thái bàn về available và đóng màn hình
     */
    private void updateTableStatusAndFinish() {
        // Tìm tableId của bàn
        tableRepository.getAllTables(new TableRepository.RepositoryCallback<List<TableItem>>() {
            @Override
            public void onSuccess(List<TableItem> tables) {
                String tableId = null;
                if (tables != null) {
                    for (TableItem t : tables) {
                        if (t != null && t.getTableNumber() == tableNumber) {
                            tableId = t.getId();
                            break;
                        }
                    }
                }

                if (tableId == null || tableId.trim().isEmpty()) {
                    Log.w(TAG, "updateTableStatusAndFinish: Table ID not found for table " + tableNumber);
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(InvoiceActivity.this, "Đã hủy hóa đơn", Toast.LENGTH_SHORT).show();
                        sendTableRefreshBroadcast(null, tableNumber);
                        finish();
                    });
                    return;
                }

                // Cập nhật trạng thái bàn về available và xóa các thông tin liên quan
                // Tạo biến final để sử dụng trong lambda
                final String finalTableId = tableId;
                Log.d(TAG, "updateTableStatusAndFinish: Updating table " + tableNumber + " (ID: " + finalTableId + ") to available on server");
                tableRepository.resetTableAfterPayment(finalTableId, new TableRepository.RepositoryCallback<TableItem>() {
                    @Override
                    public void onSuccess(TableItem result) {
                        Log.d(TAG, "updateTableStatusAndFinish: Successfully updated table status to available on server");
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(InvoiceActivity.this, "Đã hủy hóa đơn và cập nhật trạng thái bàn", Toast.LENGTH_SHORT).show();
                            // Gửi broadcast để các màn hình khác reload danh sách bàn
                            sendTableRefreshBroadcast(finalTableId, tableNumber);
                            finish();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        Log.e(TAG, "updateTableStatusAndFinish: Error updating table status on server: " + message);
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(InvoiceActivity.this, "Đã hủy hóa đơn nhưng không thể cập nhật trạng thái bàn: " + message, Toast.LENGTH_LONG).show();
                            // Vẫn gửi broadcast để reload danh sách bàn
                            sendTableRefreshBroadcast(finalTableId, tableNumber);
                            finish();
                        });
                    }
                });
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "updateTableStatusAndFinish: Error loading tables: " + message);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(InvoiceActivity.this, "Đã hủy hóa đơn", Toast.LENGTH_SHORT).show();
                    sendTableRefreshBroadcast(null, tableNumber);
                    finish();
                });
            }
        });
    }

    /**
     * Kiểm tra và reset bàn sau khi hủy hóa đơn, đồng thời gửi broadcast để màn danh sách bàn reload
     */
    private void checkAndResetTableIfNeeded(String tableId) {
        if (tableRepository == null) tableRepository = new TableRepository();

        // Nếu không có tableId, thử tìm theo tableNumber
        if (tableId == null || tableId.trim().isEmpty()) {
            tableRepository.getAllTables(new TableRepository.RepositoryCallback<List<TableItem>>() {
                @Override
                public void onSuccess(List<TableItem> tables) {
                    String foundTableId = null;
                    if (tables != null) {
                        for (TableItem t : tables) {
                            if (t != null && t.getTableNumber() == tableNumber) {
                                foundTableId = t.getId();
                                break;
                            }
                        }
                    }
                    if (foundTableId != null) {
                        checkOrdersAndResetTable(foundTableId);
                    } else {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(InvoiceActivity.this, "Đã hủy hóa đơn", Toast.LENGTH_SHORT).show();
                            sendTableRefreshBroadcast(null, tableNumber);
                            finish();
                        });
                    }
                }

                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(InvoiceActivity.this, "Đã hủy hóa đơn", Toast.LENGTH_SHORT).show();
                        sendTableRefreshBroadcast(null, tableNumber);
                        finish();
                    });
                }
            });
        } else {
            checkOrdersAndResetTable(tableId);
        }
    }

    /**
     * Kiểm tra còn order chưa thanh toán; nếu hết thì reset bàn
     */
    private void checkOrdersAndResetTable(String tableId) {
        orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> remainingOrders) {
                List<Order> unpaidOrders = filterUnpaidOrders(remainingOrders);
                if (unpaidOrders == null || unpaidOrders.isEmpty()) {
                    resetTableStatus(tableId);
                } else {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(InvoiceActivity.this, "Đã hủy hóa đơn", Toast.LENGTH_SHORT).show();
                        loadInvoiceData();
                    });
                }
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Error checking remaining orders: " + message);
                resetTableStatus(tableId);
            }
        });
    }

    /**
     * Reset trạng thái bàn về available và gửi broadcast cập nhật danh sách bàn
     */
    private void resetTableStatus(String tableId) {
        if (tableId == null || tableId.trim().isEmpty()) {
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(InvoiceActivity.this, "Đã hủy hóa đơn", Toast.LENGTH_SHORT).show();
                sendTableRefreshBroadcast(null, tableNumber);
                finish();
            });
            return;
        }

        tableRepository.resetTableAfterPayment(tableId, new TableRepository.RepositoryCallback<TableItem>() {
            @Override
            public void onSuccess(TableItem result) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(InvoiceActivity.this, "Đã hủy hóa đơn và reset trạng thái bàn", Toast.LENGTH_SHORT).show();
                    sendTableRefreshBroadcast(tableId, tableNumber);
                    finish();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(InvoiceActivity.this, "Đã hủy hóa đơn nhưng không thể reset bàn: " + message, Toast.LENGTH_LONG).show();
                    sendTableRefreshBroadcast(tableId, tableNumber);
                    finish();
                });
            }
        });
    }

    /**
     * Gửi broadcast để màn danh sách bàn reload
     */
    private void sendTableRefreshBroadcast(String tableId, int tableNumber) {
        try {
            Intent intent = new Intent(ACTION_REFRESH_TABLES);
            if (tableId != null) intent.putExtra("tableId", tableId);
            intent.putExtra("tableNumber", tableNumber);
            sendBroadcast(intent);
        } catch (Exception e) {
            Log.w(TAG, "sendTableRefreshBroadcast failed: " + e.getMessage());
        }
    }

    /**
     * Bật chế độ chỉnh sửa cho một order
     */
    private void startEditOrder(Order order) {
        if (order == null) {
            Toast.makeText(this, "Hóa đơn không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }
        if (order.getId() == null) {
            Toast.makeText(this, "Hóa đơn chưa có ID", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Order has no ID!");
            return;
        }
        Log.d(TAG, "Starting edit mode for order: " + order.getId());

        // Tìm order trong danh sách orders hiện tại để giữ reference
        Order orderToEdit = null;
        String orderIdToFind = order.getId() != null ? order.getId().trim() : null;
        if (orderIdToFind != null && orders != null) {
            for (Order o : orders) {
                if (o != null && o.getId() != null && o.getId().trim().equals(orderIdToFind)) {
                    orderToEdit = o;
                    break;
                }
            }
        }

        if (orderToEdit == null) {
            // Nếu không tìm thấy trong danh sách, dùng order được truyền vào
            orderToEdit = order;
            Log.d(TAG, "Order not found in list, using passed order");
        }

        // Tạo một bản copy của order để tránh thay đổi trực tiếp cho đến khi lưu
        editingOrder = new Order();
        editingOrder.setId(orderToEdit.getId());
        editingOrder.setTableNumber(orderToEdit.getTableNumber());
        if (orderToEdit.getItems() != null) {
            List<Order.OrderItem> copiedItems = new ArrayList<>();
            for (Order.OrderItem item : orderToEdit.getItems()) {
                if (item != null) {
                    Order.OrderItem copiedItem = new Order.OrderItem();
                    copiedItem.setMenuItemId(item.getMenuItemId());
                    copiedItem.setName(item.getName());
                    copiedItem.setPrice(item.getPrice());
                    copiedItem.setQuantity(item.getQuantity());
                    copiedItem.setStatus(item.getStatus());
                    copiedItem.setNote(item.getNote());
                    copiedItems.add(copiedItem);
                }
            }
            editingOrder.setItems(copiedItems);
        }
        editingOrder.setTotalAmount(orderToEdit.getTotalAmount());
        editingOrder.setDiscount(orderToEdit.getDiscount());
        editingOrder.setFinalAmount(orderToEdit.getFinalAmount());

        Log.d(TAG, "editingOrder set with ID: '" + editingOrder.getId() + "'" +
              ", items count: " + (editingOrder.getItems() != null ? editingOrder.getItems().size() : 0));

        // Cập nhật order trong danh sách orders để khi reload, nó sẽ dùng editingOrder
        if (orders != null && editingOrder.getId() != null) {
            String editingId = editingOrder.getId().trim();
            for (int i = 0; i < orders.size(); i++) {
                Order o = orders.get(i);
                if (o != null && o.getId() != null && o.getId().trim().equals(editingId)) {
                    Log.d(TAG, "Updating order in list at index " + i);
                    orders.set(i, editingOrder);
                    break;
                }
            }
        }

        loadInvoiceData(); // Reload để hiển thị nút +/-
        Toast.makeText(this, "Đang ở chế độ chỉnh sửa", Toast.LENGTH_SHORT).show();
    }

    /**
     * Tăng số lượng món trong order
     */
    private void increaseItemQuantity(Order order, int itemIndex) {
        if (order == null || order.getItems() == null || itemIndex < 0 || itemIndex >= order.getItems().size()) {
            return;
        }
        Order.OrderItem item = order.getItems().get(itemIndex);
        if (item != null) {
            item.setQuantity(item.getQuantity() + 1);
            // Cập nhật lại tổng tiền
            recalculateOrderTotal(order);
            // Refresh lại card đang chỉnh sửa
            refreshEditingCard();
        }
    }

    /**
     * Giảm số lượng món trong order
     */
    private void decreaseItemQuantity(Order order, int itemIndex) {
        if (order == null || order.getItems() == null || itemIndex < 0 || itemIndex >= order.getItems().size()) {
            return;
        }
        Order.OrderItem item = order.getItems().get(itemIndex);
        if (item != null) {
            if (item.getQuantity() > 1) {
                item.setQuantity(item.getQuantity() - 1);
            } else {
                // Xóa món nếu số lượng = 0
                order.getItems().remove(itemIndex);
            }
            // Cập nhật lại tổng tiền
            recalculateOrderTotal(order);
            // Refresh lại card đang chỉnh sửa
            refreshEditingCard();
        }
    }

    /**
     * Tính lại tổng tiền cho order
     */
    private void recalculateOrderTotal(Order order) {
        if (order == null || order.getItems() == null) {
            return;
        }
        double total = 0;
        for (Order.OrderItem item : order.getItems()) {
            total += item.getPrice() * item.getQuantity();
        }
        order.setTotalAmount(total);
        order.setFinalAmount(total - order.getDiscount());
    }

    /**
     * Refresh lại card đang chỉnh sửa mà không reload từ server
     */
    private void refreshEditingCard() {
        if (editingOrder == null || editingOrder.getId() == null || llOrderCards == null) {
            return;
        }
        // Tìm CardView từ map
        CardView oldCard = orderCardMap.get(editingOrder.getId());
        int insertIndex = -1;
        if (oldCard != null && oldCard.getParent() != null) {
            // Lấy index của card trong parent
            ViewGroup parent = (ViewGroup) oldCard.getParent();
            insertIndex = parent.indexOfChild(oldCard);
            // Xóa card cũ
            parent.removeViewAt(insertIndex);
            // Xóa khỏi map
            orderCardMap.remove(editingOrder.getId());
        }
        // Tạo lại card mới với dữ liệu đã cập nhật
        createInvoiceCard(editingOrder);
        // Nếu có index, di chuyển card mới vào đúng vị trí
        if (insertIndex >= 0 && insertIndex < llOrderCards.getChildCount()) {
            CardView newCard = orderCardMap.get(editingOrder.getId());
            if (newCard != null) {
                llOrderCards.removeView(newCard);
                llOrderCards.addView(newCard, insertIndex);
            }
        }
    }

    /**
     * Focus và scroll tới card của orderId nếu tồn tại
     */
    private void focusOrderCard(String orderId) {
        if (orderId == null || orderId.trim().isEmpty()) return;
        CardView card = orderCardMap.get(orderId.trim());
        if (card != null) {
            card.post(() -> {
                if (scrollInvoice != null) {
                    int[] loc = new int[2];
                    card.getLocationOnScreen(loc);
                    scrollInvoice.smoothScrollTo(0, card.getTop());
                }
                // reset target sau khi focus xong
                targetOrderId = null;
            });
        }
    }

    /**
     * Hiển thị dialog để thêm món mới vào order
     */
    private void showAddItemDialog(Order order) {
        if (order == null) {
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        menuRepository.getAllMenuItems(new MenuRepository.RepositoryCallback<List<MenuItem>>() {
            @Override
            public void onSuccess(List<MenuItem> menuItems) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (menuItems == null || menuItems.isEmpty()) {
                        Toast.makeText(InvoiceActivity.this, "Không có món ăn nào", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Inflate XML layout
                    View dialogView = LayoutInflater.from(InvoiceActivity.this).inflate(R.layout.dialog_add_item, null);
                    ListView lvMenuItems = dialogView.findViewById(R.id.lvMenuItems);

                    // Tạo adapter cho ListView
                    String[] itemNames = new String[menuItems.size()];
                    for (int i = 0; i < menuItems.size(); i++) {
                        itemNames[i] = menuItems.get(i).getName() + " - " + formatCurrency(menuItems.get(i).getPrice());
                    }

                    android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                        InvoiceActivity.this,
                        R.layout.item_menu_dialog,
                        R.id.tvMenuItem,
                        itemNames
                    );
                    lvMenuItems.setAdapter(adapter);

                    AlertDialog dialog = new AlertDialog.Builder(InvoiceActivity.this)
                            .setView(dialogView)
                            .setNegativeButton("Hủy", null)
                            .create();

                    lvMenuItems.setOnItemClickListener((parent, view, position, id) -> {
                        MenuItem selectedMenu = menuItems.get(position);
                        addItemToOrder(order, selectedMenu);
                        dialog.dismiss();
                    });

                    dialog.show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(InvoiceActivity.this, "Lỗi tải menu: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Thêm món mới vào order
     */
    private void addItemToOrder(Order order, MenuItem menuItem) {
        if (order == null || menuItem == null) {
            return;
        }
        List<Order.OrderItem> items = order.getItems();
        if (items == null) {
            items = new ArrayList<>();
            order.setItems(items);
        }
        // Kiểm tra xem món đã có chưa
        boolean found = false;
        for (Order.OrderItem item : items) {
            if (item.getMenuItemId() != null && item.getMenuItemId().equals(menuItem.getId())) {
                item.setQuantity(item.getQuantity() + 1);
                found = true;
                break;
            }
        }
        if (!found) {
            Order.OrderItem newItem = new Order.OrderItem();
            newItem.setMenuItemId(menuItem.getId());
            newItem.setName(menuItem.getName());
            newItem.setPrice(menuItem.getPrice());
            newItem.setQuantity(1);
            items.add(newItem);
        }
        // Cập nhật lại tổng tiền
        recalculateOrderTotal(order);
        // Refresh lại card đang chỉnh sửa
        refreshEditingCard();
    }

    /**
     * Lưu thay đổi của order lên server
     */
    private void saveOrderChanges(Order order) {
        if (order == null || order.getId() == null) {
            Toast.makeText(this, "Hóa đơn không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        // Tính lại tổng tiền
        double newTotal = 0;
        if (order.getItems() != null) {
            for (Order.OrderItem item : order.getItems()) {
                newTotal += item.getPrice() * item.getQuantity();
            }
        }
        order.setTotalAmount(newTotal);
        order.setFinalAmount(newTotal - order.getDiscount());
        Map<String, Object> updates = new HashMap<>();
        updates.put("items", order.getItems());
        updates.put("totalAmount", newTotal);
        updates.put("finalAmount", order.getFinalAmount());
        orderRepository.updateOrder(order.getId(), updates, new OrderRepository.RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order result) {
                runOnUiThread(() -> {
                    editingOrder = null;
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(InvoiceActivity.this, "Đã lưu thay đổi", Toast.LENGTH_SHORT).show();
                    loadInvoiceData();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(InvoiceActivity.this, "Lỗi lưu thay đổi: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Parse integer an toàn, trả về 0 nếu lỗi
     */
    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }
    private int countUnreadyItems(Order order) {
        if (order == null || order.getItems() == null) return 0;

        int count = 0;
        for (Order.OrderItem item : order.getItems()) {
            if (item == null) continue;
            if (!"ready".equalsIgnoreCase(item.getStatus())) {
                count += item.getQuantity();
            }
        }
        return count;
    }
    private int countUnreadyItemsForAllOrders(List<Order> orders) {
        if (orders == null) return 0;

        int total = 0;
        for (Order order : orders) {
            total += countUnreadyItems(order);
        }
        return total;
    }



    private void handlePayment(Order order) {
        if (order == null || order.getId() == null) {
            Toast.makeText(this, "Hóa đơn không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        int unreadyCount = countUnreadyItems(order);

        // ✅ TẤT CẢ ĐÃ READY → THANH TOÁN BÌNH THƯỜNG
        if (unreadyCount == 0) {
            goToPayment(order, false);
            return;
        }

        // ⚠️ CÒN MÓN CHƯA LÊN → HỎI XÁC NHẬN
        showPendingItemsPaymentDialog(order, unreadyCount);
    }


    private void showPendingItemsPaymentDialog(Order order, int unreadyCount) {
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận thanh toán")
                .setMessage("Còn " + unreadyCount + " món chưa lên.\nBạn muốn thanh toán ngay?")

                .setNeutralButton("Thanh toán gồm món chưa lên", (d, w) -> {
                    goToPayment(order, false);
                })
                .setNegativeButton("Hủy (tiếp tục dùng bữa)", null)
                .show();
    }


    private void goToPayment(Order order, boolean excludeUnreadyItems) {
        Intent intent = new Intent(InvoiceActivity.this, ThanhToanActivity.class);
        intent.putExtra("orderId", order.getId());
        intent.putExtra("tableNumber", tableNumber);
        intent.putExtra("excludeUnreadyItems", excludeUnreadyItems);

        if (excludeUnreadyItems) {
            ArrayList<Order.OrderItem> readyItems = getReadyItems(order);
            intent.putExtra("pay_items", readyItems);

        }

        startActivity(intent);
    }


    private ArrayList<Order.OrderItem> getReadyItems(Order order) {
        ArrayList<Order.OrderItem> readyItems = new ArrayList<>();
        if (order == null || order.getItems() == null) return readyItems;

        for (Order.OrderItem item : order.getItems()) {
            if (item != null && "ready".equalsIgnoreCase(item.getStatus())) {
                readyItems.add(item);
            }
        }
        return readyItems;
    }
    private void handlePaymentWithConfirm(
            int unreadyCount,
            Runnable onConfirmPayment
    ) {
        if (unreadyCount == 0) {
            onConfirmPayment.run();
            return;
        }

        // Dialog xác nhận
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận thanh toán")
                .setMessage("Còn " + unreadyCount + " món chưa lên. Bạn vẫn muốn thanh toán?")
                .setPositiveButton("Thanh toán", (dialog, which) -> {
                    onConfirmPayment.run();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }



}




