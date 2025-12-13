package com.ph48845.datn_qlnh_rmis.ui.thungan;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
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

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.repository.MenuRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
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

    private OrderRepository orderRepository;
    private MenuRepository menuRepository;
    private int tableNumber;
    private List<Order> orders = new ArrayList<>();
    private List<Order.OrderItem> allItems = new ArrayList<>();
    private boolean isEditMode = false;
    private Order editingOrder = null; // Order đang được chỉnh sửa
    private Map<String, CardView> orderCardMap = new ConcurrentHashMap<>(); // Map order ID -> CardView
    private String newlySplitOrderId = null; // ID của hóa đơn mới vừa tách (để highlight)

    // Socket để gửi request lên server
    private final SocketManager socketManager = SocketManager.getInstance();
    private final String SOCKET_URL = "http://192.168.1.84:3000"; // đổi theo server của bạn

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_invoice);

        // Lấy tableNumber từ Intent
        tableNumber = getIntent().getIntExtra("tableNumber", 0);
        if (tableNumber <= 0) {
            Toast.makeText(this, "Thông tin bàn không hợp lệ", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupToolbar();

        orderRepository = new OrderRepository();
        menuRepository = new MenuRepository();

        // Khởi tạo socket để gửi request lên server
        initSocket();

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
        progressBar.setVisibility(View.VISIBLE);
        llOrderCards.removeAllViews();

        // Lấy orders của bàn này (lấy tất cả, sau đó filter các đơn chưa thanh toán)
        orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orderList) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);

                    if (orderList == null || orderList.isEmpty()) {
                        Toast.makeText(InvoiceActivity.this, "Không có đơn hàng cho bàn này", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Lọc bỏ các đơn đã thanh toán
                    orders = filterUnpaidOrders(orderList);

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
                        Toast.makeText(InvoiceActivity.this, "Không có đơn hàng chưa thanh toán cho bàn này", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    displayInvoice();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
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

        for (Order order : allOrders) {
            if (order == null) continue;

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
            }
        }

        return unpaidOrders;
    }

    /**
     * Hiển thị hóa đơn với dữ liệu đã load
     */
    private void displayInvoice() {
        // Xóa tất cả card cũ
        llOrderCards.removeAllViews();
        orderCardMap.clear(); // Xóa map

        if (orders == null || orders.isEmpty()) {
            return;
        }

        // Tạo một card hóa đơn cho mỗi order
        for (Order order : orders) {
            if (order == null) continue;
            order.normalizeItems();
            createInvoiceCard(order);
        }

        // Reset highlight sau khi đã hiển thị xong
        newlySplitOrderId = null;
    }

    /**
     * Tạo một card hóa đơn cho một order
     */
    private void createInvoiceCard(Order order) {
        // Inflate XML layout cho card
        View cardView = LayoutInflater.from(this).inflate(R.layout.card_invoice, llOrderCards, false);
        CardView cardViewContainer = cardView.findViewById(R.id.cardInvoice);

        // Highlight hóa đơn mới vừa tách
        if (newlySplitOrderId != null && order.getId() != null && order.getId().equals(newlySplitOrderId)) {
            cardViewContainer.setCardBackgroundColor(0xFFE8F5E9); // Màu xanh nhạt
        }

        // Lấy các view từ XML
        TextView tvTable = cardView.findViewById(R.id.tvTable);
        TextView tvOrderCode = cardView.findViewById(R.id.tvOrderCode);
        LinearLayout llItemsContainer = cardView.findViewById(R.id.llItemsContainer);
        LinearLayout llTotals = cardView.findViewById(R.id.llTotals);

        // Thiết lập thông tin bàn và mã đơn
        tvTable.setText("Bàn: " + String.format("%02d", tableNumber));
        String orderCode = order.getId() != null ? order.getId().substring(0, Math.min(12, order.getId().length())) : "N/A";
        tvOrderCode.setText("Mã đơn: HD" + orderCode);

        // Hiển thị các món ăn
        List<Order.OrderItem> orderItems = order.getItems();

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
            for (int i = 0; i < orderItems.size(); i++) {
                final int itemIndex = i;
                Order.OrderItem item = orderItems.get(i);
                if (item == null) continue;

                // Sử dụng layout khác nhau cho edit mode và view mode
                View itemRow;
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
                        Log.e(TAG, "Edit layout buttons not found!");
                    }
                } else {
                    // Không ở chế độ chỉnh sửa - chỉ hiển thị số lượng
                    itemRow = LayoutInflater.from(this).inflate(R.layout.item_invoice_row, llItemsContainer, false);
                    TextView tvQty = itemRow.findViewById(R.id.tvItemQuantity);
                    if (tvQty != null) {
                        tvQty.setText("x" + item.getQuantity());
                    }
                }


                TextView tvItemName = itemRow.findViewById(R.id.tvItemName);
                TextView tvItemPrice = itemRow.findViewById(R.id.tvItemPrice);

                tvItemName.setText(item.getName() != null ? item.getName() : "(Không tên)");
                double itemTotal = item.getPrice() * item.getQuantity();
                tvItemPrice.setText(formatCurrency(itemTotal));

                llItemsContainer.addView(itemRow);
            }
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

        tvTotal.setText(formatCurrency(order.getTotalAmount()));
        tvDiscount.setText(formatCurrency(order.getDiscount()));
        tvFinalAmount.setText(formatCurrency(order.getFinalAmount()));

        llTotals.removeAllViews();
        llTotals.addView(totalsView);

        // Thêm card vào container
        llOrderCards.addView(cardView);

        // Lưu CardView vào map để dễ dàng refresh sau này
        if (order.getId() != null) {
            orderCardMap.put(order.getId(), cardViewContainer);
        }

        // Nhấn giữ vào card để mở menu tùy chọn, chạm nhanh để thanh toán
        final Order currentOrder = order;
        cardViewContainer.setOnClickListener(v -> processPaymentForOrder(currentOrder));
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
        // Chuyển sang màn hình thanh toán
        Intent intent = new Intent(InvoiceActivity.this, ThanhToanActivity.class);
        intent.putExtra("orderId", order.getId());
        intent.putExtra("tableNumber", tableNumber);
        startActivity(intent);
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
                        case 3:
                            // Chuyển sang màn hình thanh toán
                            Intent intent = new Intent(InvoiceActivity.this, ThanhToanActivity.class);
                            intent.putExtra("orderId", order.getId());
                            intent.putExtra("tableNumber", tableNumber);
                            startActivity(intent);
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
        Map<Integer, EditText> qtyEditTextMap = new HashMap<>();

        for (int i = 0; i < items.size(); i++) {
            Order.OrderItem item = items.get(i);
            if (item == null) continue;

            // Inflate item layout
            View itemView = LayoutInflater.from(this).inflate(R.layout.dialog_split_invoice_item, llItemsContainer, false);

            // Tên món và số lượng hiện tại
            TextView tvItemInfo = itemView.findViewById(R.id.tvItemInfo);
            tvItemInfo.setText(item.getName() + " (hiện có: x" + item.getQuantity() + ")");

            // EditText để nhập số lượng tách
            EditText etQty = itemView.findViewById(R.id.etQty);
            qtyEditTextMap.put(i, etQty);

            llItemsContainer.addView(itemView);
        }

        new AlertDialog.Builder(this)
                .setTitle("Tách hóa đơn - Nhập số lượng")
                .setView(dialogView)
                .setPositiveButton("Tách", (dialog, which) -> {
                    // Lấy số lượng từ các EditText
                    Map<Integer, Integer> splitQuantities = new HashMap<>();
                    boolean hasAnySplit = false;
                    for (Map.Entry<Integer, EditText> entry : qtyEditTextMap.entrySet()) {
                        int itemIndex = entry.getKey();
                        EditText et = entry.getValue();
                        String qtyStr = et.getText().toString().trim();
                        int qty = 0;
                        try {
                            qty = Integer.parseInt(qtyStr);
                        } catch (NumberFormatException e) {
                            qty = 0;
                        }
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
     * In tạm tính cho một order cụ thể - Mở Activity hiển thị hóa đơn từ XML layout
     */
    private void printTemporaryBillForOrder(Order order) {
        if (order == null) {
            Toast.makeText(this, "Hóa đơn không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        // Mở Activity hiển thị hóa đơn từ XML layout
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
     */
    private void cancelInvoiceForOrder(Order order, String reason) {
        if (order == null || order.getId() == null) {
            Toast.makeText(this, "Hóa đơn không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        orderRepository.deleteOrder(order.getId(), new OrderRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(InvoiceActivity.this, "Đã hủy hóa đơn", Toast.LENGTH_SHORT).show();
                    loadInvoiceData();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(InvoiceActivity.this, "Lỗi hủy hóa đơn: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
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

}
