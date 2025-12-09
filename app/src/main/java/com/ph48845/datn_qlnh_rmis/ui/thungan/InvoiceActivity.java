package com.ph48845.datn_qlnh_rmis.ui.thungan;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
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
import com.ph48845.datn_qlnh_rmis.ui.thanhtoan.ThanhToanActivity;

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

        // Load dữ liệu từ API
        loadInvoiceData();
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
        }
        toolbar.setNavigationOnClickListener(v -> finish());
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
        // Tạo CardView
        CardView cardView = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, (int) (16 * getResources().getDisplayMetrics().density));
        cardView.setLayoutParams(cardParams);
        cardView.setRadius((int) (12 * getResources().getDisplayMetrics().density));
        cardView.setCardElevation(4);
        cardView.setUseCompatPadding(true);

        // Highlight hóa đơn mới vừa tách
        if (newlySplitOrderId != null && order.getId() != null && order.getId().equals(newlySplitOrderId)) {
            cardView.setCardBackgroundColor(0xFFE8F5E9); // Màu xanh nhạt
        }

        // Container bên trong card
        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(
                (int) (20 * getResources().getDisplayMetrics().density),
                (int) (20 * getResources().getDisplayMetrics().density),
                (int) (20 * getResources().getDisplayMetrics().density),
                (int) (20 * getResources().getDisplayMetrics().density)
        );

        // Tiêu đề HÓA ĐƠN THANH TOÁN
        TextView tvTitle = new TextView(this);
        tvTitle.setText("HÓA ĐƠN THANH TOÁN");
        tvTitle.setTextColor(0xFF000000);
        tvTitle.setTextSize(16);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(0, 0, 0, (int) (16 * getResources().getDisplayMetrics().density));
        tvTitle.setLayoutParams(titleParams);
        cardContent.addView(tvTitle);

        // Thông tin bàn và mã đơn
        LinearLayout infoLayout = new LinearLayout(this);
        infoLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        infoParams.setMargins(0, 0, 0, (int) (16 * getResources().getDisplayMetrics().density));
        infoLayout.setLayoutParams(infoParams);

        TextView tvTable = new TextView(this);
        tvTable.setText("Bàn: " + String.format("%02d", tableNumber));
        tvTable.setTextColor(0xFF000000);
        tvTable.setTextSize(16);
        infoLayout.addView(tvTable);

        TextView tvCode = new TextView(this);
        String orderCode = order.getId() != null ? order.getId().substring(0, Math.min(12, order.getId().length())) : "N/A";
        tvCode.setText("Mã đơn: HD" + orderCode);
        tvCode.setTextColor(0xFF000000);
        tvCode.setTextSize(16);
        LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        codeParams.setMargins(0, (int) (4 * getResources().getDisplayMetrics().density), 0, 0);
        tvCode.setLayoutParams(codeParams);
        infoLayout.addView(tvCode);

        cardContent.addView(infoLayout);

        // Header bảng món ăn
        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setPadding(
                (int) (8 * getResources().getDisplayMetrics().density),
                (int) (8 * getResources().getDisplayMetrics().density),
                (int) (8 * getResources().getDisplayMetrics().density),
                (int) (8 * getResources().getDisplayMetrics().density)
        );
        headerLayout.setBackgroundColor(0xFFF0F0F0);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        headerParams.setMargins(0, 0, 0, (int) (8 * getResources().getDisplayMetrics().density));
        headerLayout.setLayoutParams(headerParams);

        TextView tvHeaderName = new TextView(this);
        tvHeaderName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));
        tvHeaderName.setText("Món ăn");
        tvHeaderName.setTextColor(0xFF000000);
        tvHeaderName.setTextSize(16);
        tvHeaderName.setTypeface(null, android.graphics.Typeface.BOLD);
        headerLayout.addView(tvHeaderName);

        TextView tvHeaderQty = new TextView(this);
        tvHeaderQty.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvHeaderQty.setText("SL");
        tvHeaderQty.setTextColor(0xFF000000);
        tvHeaderQty.setTextSize(16);
        tvHeaderQty.setTypeface(null, android.graphics.Typeface.BOLD);
        tvHeaderQty.setGravity(android.view.Gravity.CENTER);
        headerLayout.addView(tvHeaderQty);

        TextView tvHeaderPrice = new TextView(this);
        tvHeaderPrice.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f));
        tvHeaderPrice.setText("Giá");
        tvHeaderPrice.setTextColor(0xFF000000);
        tvHeaderPrice.setTextSize(16);
        tvHeaderPrice.setTypeface(null, android.graphics.Typeface.BOLD);
        tvHeaderPrice.setGravity(android.view.Gravity.RIGHT);
        headerLayout.addView(tvHeaderPrice);

        cardContent.addView(headerLayout);

        // Container cho danh sách món ăn
        LinearLayout itemsContainer = new LinearLayout(this);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams itemsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        itemsParams.setMargins(0, 0, 0, (int) (16 * getResources().getDisplayMetrics().density));
        itemsContainer.setLayoutParams(itemsParams);

        // Hiển thị các món ăn
        List<Order.OrderItem> orderItems = order.getItems();
        boolean isEditingThisOrder = (editingOrder != null && editingOrder.getId() != null &&
                order.getId() != null && editingOrder.getId().equals(order.getId()));

        if (orderItems != null && !orderItems.isEmpty()) {
            for (int i = 0; i < orderItems.size(); i++) {
                final int itemIndex = i;
                Order.OrderItem item = orderItems.get(i);
                if (item == null) continue;
                LinearLayout itemRow = new LinearLayout(this);
                itemRow.setOrientation(LinearLayout.HORIZONTAL);
                itemRow.setPadding(
                        (int) (16 * getResources().getDisplayMetrics().density),
                        (int) (12 * getResources().getDisplayMetrics().density),
                        (int) (16 * getResources().getDisplayMetrics().density),
                        (int) (12 * getResources().getDisplayMetrics().density)
                );

                TextView tvItemName = new TextView(this);
                tvItemName.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));
                tvItemName.setText(item.getName() != null ? item.getName() : "(Không tên)");
                tvItemName.setTextColor(0xFF000000);
                tvItemName.setTextSize(16);
                itemRow.addView(tvItemName);

                // Số lượng với nút +/-
                LinearLayout qtyLayout = new LinearLayout(this);
                qtyLayout.setOrientation(LinearLayout.HORIZONTAL);
                qtyLayout.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                qtyLayout.setGravity(android.view.Gravity.CENTER);

                if (isEditingThisOrder) {
                    int controlSize = (int) (36 * getResources().getDisplayMetrics().density);

                    // Nút trừ (-)
                    Button btnMinus = new Button(this);
                    btnMinus.setText("-");
                    btnMinus.setTextColor(0xFFD35400);
                    btnMinus.setTextSize(18);
                    btnMinus.setTypeface(null, android.graphics.Typeface.BOLD);
                    btnMinus.setBackground(null);
                    LinearLayout.LayoutParams minusParams = new LinearLayout.LayoutParams(controlSize, controlSize);
                    minusParams.setMargins(0, 0, 8, 0);
                    btnMinus.setLayoutParams(minusParams);
                    btnMinus.setOnClickListener(v -> decreaseItemQuantity(order, itemIndex));
                    qtyLayout.addView(btnMinus);

                    // Hiển thị số lượng
                    TextView tvQty = new TextView(this);
                    tvQty.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    ));
                    tvQty.setText(String.valueOf(item.getQuantity()));
                    tvQty.setTextColor(0xFF000000);
                    tvQty.setTextSize(16);
                    tvQty.setTypeface(null, android.graphics.Typeface.BOLD);
                    tvQty.setPadding(16, 0, 16, 0);
                    tvQty.setGravity(android.view.Gravity.CENTER);
                    qtyLayout.addView(tvQty);

                    // Nút cộng (+)
                    Button btnPlus = new Button(this);
                    btnPlus.setText("+");
                    btnPlus.setTextColor(0xFF2BB673);
                    btnPlus.setTextSize(18);
                    btnPlus.setTypeface(null, android.graphics.Typeface.BOLD);
                    btnPlus.setBackground(null);
                    LinearLayout.LayoutParams plusParams = new LinearLayout.LayoutParams(controlSize, controlSize);
                    plusParams.setMargins(8, 0, 0, 0);
                    btnPlus.setLayoutParams(plusParams);
                    btnPlus.setOnClickListener(v -> increaseItemQuantity(order, itemIndex));
                    qtyLayout.addView(btnPlus);
                } else {
                    TextView tvItemQty = new TextView(this);
                    tvItemQty.setLayoutParams(new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    ));
                    tvItemQty.setText("x" + item.getQuantity());
                    tvItemQty.setTextColor(0xFF000000);
                    tvItemQty.setTextSize(16);
                    tvItemQty.setGravity(android.view.Gravity.CENTER);
                    qtyLayout.addView(tvItemQty);
                }

                itemRow.addView(qtyLayout);

                TextView tvItemPrice = new TextView(this);
                tvItemPrice.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f));
                double itemTotal = item.getPrice() * item.getQuantity();
                tvItemPrice.setText(formatCurrency(itemTotal));
                tvItemPrice.setTextColor(0xFF000000);
                tvItemPrice.setTextSize(16);
                tvItemPrice.setGravity(android.view.Gravity.RIGHT);
                itemRow.addView(tvItemPrice);

                itemsContainer.addView(itemRow);
            }
        }

        // Nút thêm món (chỉ hiển thị khi đang chỉnh sửa)
        if (isEditingThisOrder) {
            Button btnAddItem = new Button(this);
            btnAddItem.setText("+ Thêm món");
            btnAddItem.setTextColor(0xFFFFFFFF);
            btnAddItem.setTextSize(16);
            btnAddItem.setTypeface(null, android.graphics.Typeface.BOLD);
            btnAddItem.setBackgroundResource(R.drawable.bg_button_primary);
            LinearLayout.LayoutParams addItemParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            addItemParams.setMargins(0, (int) (8 * getResources().getDisplayMetrics().density), 0, 0);
            btnAddItem.setLayoutParams(addItemParams);
            btnAddItem.setOnClickListener(v -> showAddItemDialog(order));
            itemsContainer.addView(btnAddItem);

            // Nút lưu và hủy
            LinearLayout actionLayout = new LinearLayout(this);
            actionLayout.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            actionParams.setMargins(0, (int) (16 * getResources().getDisplayMetrics().density), 0, 0);
            actionLayout.setLayoutParams(actionParams);

            Button btnSave = new Button(this);
            btnSave.setText("Lưu");
            btnSave.setTextColor(0xFFFFFFFF);
            btnSave.setTextSize(16);
            btnSave.setTypeface(null, android.graphics.Typeface.BOLD);
            btnSave.setBackgroundResource(R.drawable.bg_button_primary);
            LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            saveParams.setMargins(0, 0, (int) (8 * getResources().getDisplayMetrics().density), 0);
            btnSave.setLayoutParams(saveParams);
            btnSave.setOnClickListener(v -> saveOrderChanges(order));
            actionLayout.addView(btnSave);

            Button btnCancel = new Button(this);
            btnCancel.setText("Hủy");
            btnCancel.setTextColor(0xFF000000);
            btnCancel.setTextSize(16);
            btnCancel.setTypeface(null, android.graphics.Typeface.BOLD);
            btnCancel.setBackgroundColor(0xFFE0E0E0);
            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
            );
            cancelParams.setMargins((int) (8 * getResources().getDisplayMetrics().density), 0, 0, 0);
            btnCancel.setLayoutParams(cancelParams);
            btnCancel.setOnClickListener(v -> {
                editingOrder = null;
                loadInvoiceData();
            });
            actionLayout.addView(btnCancel);

            cardContent.addView(actionLayout);
        }

        cardContent.addView(itemsContainer);

        // Tổng kết thanh toán
        LinearLayout totalsLayout = new LinearLayout(this);
        totalsLayout.setOrientation(LinearLayout.VERTICAL);

        // Tổng cộng
        LinearLayout totalRow = new LinearLayout(this);
        totalRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams totalRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        totalRowParams.setMargins(0, 0, 0, (int) (8 * getResources().getDisplayMetrics().density));
        totalRow.setLayoutParams(totalRowParams);

        TextView tvTotalLabel = new TextView(this);
        tvTotalLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvTotalLabel.setText("Tổng cộng:");
        tvTotalLabel.setTextColor(0xFF000000);
        tvTotalLabel.setTextSize(16);
        totalRow.addView(tvTotalLabel);

        TextView tvTotal = new TextView(this);
        tvTotal.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        tvTotal.setText(formatCurrency(order.getTotalAmount()));
        tvTotal.setTextColor(0xFF000000);
        tvTotal.setTextSize(16);
        tvTotal.setTypeface(null, android.graphics.Typeface.BOLD);
        totalRow.addView(tvTotal);

        totalsLayout.addView(totalRow);

        // Giảm giá
        LinearLayout discountRow = new LinearLayout(this);
        discountRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams discountRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        discountRowParams.setMargins(0, 0, 0, (int) (8 * getResources().getDisplayMetrics().density));
        discountRow.setLayoutParams(discountRowParams);

        TextView tvDiscountLabel = new TextView(this);
        tvDiscountLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvDiscountLabel.setText("Giảm giá:");
        tvDiscountLabel.setTextColor(0xFF000000);
        tvDiscountLabel.setTextSize(16);
        discountRow.addView(tvDiscountLabel);

        TextView tvDiscount = new TextView(this);
        tvDiscount.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        tvDiscount.setText(formatCurrency(order.getDiscount()));
        tvDiscount.setTextColor(0xFF000000);
        tvDiscount.setTextSize(16);
        tvDiscount.setTypeface(null, android.graphics.Typeface.BOLD);
        discountRow.addView(tvDiscount);

        totalsLayout.addView(discountRow);

        // Thành tiền
        LinearLayout finalRow = new LinearLayout(this);
        finalRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView tvFinalLabel = new TextView(this);
        tvFinalLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvFinalLabel.setText("Thành tiền:");
        tvFinalLabel.setTextColor(0xFF000000);
        tvFinalLabel.setTextSize(16);
        tvFinalLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        finalRow.addView(tvFinalLabel);

        TextView tvFinal = new TextView(this);
        tvFinal.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        tvFinal.setText(formatCurrency(order.getFinalAmount()));
        tvFinal.setTextColor(0xFF000000);
        tvFinal.setTextSize(16);
        tvFinal.setTypeface(null, android.graphics.Typeface.BOLD);
        finalRow.addView(tvFinal);

        totalsLayout.addView(finalRow);

        cardContent.addView(totalsLayout);

        cardView.addView(cardContent);
        llOrderCards.addView(cardView);

        // Lưu CardView vào map để dễ dàng refresh sau này
        if (order.getId() != null) {
            orderCardMap.put(order.getId(), cardView);
        }

        // Nhấn giữ vào card để mở menu tùy chọn, chạm nhanh để thanh toán
        final Order currentOrder = order;
        cardView.setOnClickListener(v -> {
            // Kiểm tra order hiện tại có hợp lệ không
            if (currentOrder == null || currentOrder.getId() == null) {
                Toast.makeText(InvoiceActivity.this, "Hóa đơn không hợp lệ", Toast.LENGTH_SHORT).show();
                return;
            }

            // Log để debug
            Log.d(TAG, "Opening payment - Order ID: " + currentOrder.getId());
            Log.d(TAG, "Table Number: " + tableNumber);
            Log.d(TAG, "Final Amount: " + currentOrder.getFinalAmount());

            // Chuyển sang màn thanh toán
            Intent intent = new Intent(InvoiceActivity.this, ThanhToanActivity.class);
            intent.putExtra("orderId", currentOrder.getId());
            intent.putExtra("tableNumber", tableNumber);  // QUAN TRỌNG: phải truyền tableNumber
            intent.putExtra("finalAmount", currentOrder.getFinalAmount());

            startActivity(intent);
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
     * Tăng số lượng món (không còn dùng, giữ lại để tránh lỗi)
     */
    private void increaseQuantity(int index) {
        Toast.makeText(this, "Vui lòng sử dụng chức năng chỉnh sửa từ menu", Toast.LENGTH_SHORT).show();
    }

    /**
     * Giảm số lượng món (không còn dùng, giữ lại để tránh lỗi)
     */
    private void decreaseQuantity(int index) {
        Toast.makeText(this, "Vui lòng sử dụng chức năng chỉnh sửa từ menu", Toast.LENGTH_SHORT).show();
    }

    /**
     * Bật/tắt chế độ chỉnh sửa (không còn dùng, giữ lại để tránh lỗi)
     */
    private void toggleEditMode() {
        Toast.makeText(this, "Vui lòng sử dụng chức năng chỉnh sửa từ menu của từng hóa đơn", Toast.LENGTH_SHORT).show();
    }

    /**
     * Lưu thay đổi hóa đơn lên server (không còn dùng, giữ lại để tránh lỗi)
     */
    private void saveInvoiceChanges() {
        if (orders.isEmpty()) {
            isEditMode = false;
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        Order firstOrder = orders.get(0);
        firstOrder.setItems(allItems);

        double newTotal = 0;
        for (Order.OrderItem item : allItems) {
            newTotal += item.getPrice() * item.getQuantity();
        }
        firstOrder.setTotalAmount(newTotal);
        firstOrder.setFinalAmount(newTotal - firstOrder.getDiscount());

        Map<String, Object> updates = new HashMap<>();
        updates.put("items", allItems);
        updates.put("totalAmount", newTotal);
        updates.put("finalAmount", firstOrder.getFinalAmount());

        orderRepository.updateOrder(firstOrder.getId(), updates, new OrderRepository.RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order result) {
                runOnUiThread(() -> {
                    isEditMode = false;
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

    /**
     * Cập nhật hiển thị tổng tiền (không còn dùng, giữ lại để tránh lỗi)
     */
    private void updateTotals(double totalAmount, double totalDiscount) {
        // Method này không còn cần thiết vì mỗi hóa đơn được hiển thị riêng trong card
    }

    /**
     * Hiển thị danh sách các hóa đơn riêng biệt (sau khi tách)
     * Method này không còn được sử dụng, giữ lại để tránh lỗi
     */
    private void displaySplitOrders() {
        if (llOrderCards == null) return;

        llOrderCards.removeAllViews();

        if (orders == null || orders.isEmpty()) {
            return;
        }

        for (Order order : orders) {
            if (order == null) continue;

            CardView cardView = new CardView(this);
            cardView.setUseCompatPadding(true);
            cardView.setCardElevation(4f);
            cardView.setRadius(16f);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.setMargins(0, 0, 0, (int) (12 * getResources().getDisplayMetrics().density));
            cardView.setLayoutParams(cardParams);

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(
                    (int) (16 * getResources().getDisplayMetrics().density),
                    (int) (16 * getResources().getDisplayMetrics().density),
                    (int) (16 * getResources().getDisplayMetrics().density),
                    (int) (16 * getResources().getDisplayMetrics().density)
            );

            TextView tvCode = new TextView(this);
            tvCode.setText("Mã: " + (order.getId() != null ? order.getId() : generateOrderCode()));
            tvCode.setTextSize(15);
            tvCode.setTextColor(0xFF1B5E20);
            tvCode.setTypeface(null, android.graphics.Typeface.BOLD);
            content.addView(tvCode);

            TextView tvInfo = new TextView(this);
            int totalQty = 0;
            if (order.getItems() != null) {
                for (Order.OrderItem item : order.getItems()) {
                    if (item != null) totalQty += item.getQuantity();
                }
            }
            tvInfo.setText("Tổng món: " + totalQty + " • Thanh tiền: " + formatCurrency(order.getFinalAmount()));
            tvInfo.setTextSize(14);
            tvInfo.setTextColor(0xFF333333);
            tvInfo.setPadding(0, (int) (4 * getResources().getDisplayMetrics().density), 0, 0);
            content.addView(tvInfo);

            TextView tvStatus = new TextView(this);
            tvStatus.setText("Trạng thái: " + (order.getOrderStatus() != null ? order.getOrderStatus() : "pending"));
            tvStatus.setTextSize(13);
            tvStatus.setTextColor(0xFF777777);
            tvStatus.setPadding(0, (int) (4 * getResources().getDisplayMetrics().density), 0, (int) (8 * getResources().getDisplayMetrics().density));
            content.addView(tvStatus);

            if (order.getItems() != null && !order.getItems().isEmpty()) {
                for (Order.OrderItem item : order.getItems()) {
                    if (item == null) continue;
                    TextView tvItem = new TextView(this);
                    tvItem.setText("- " + (item.getName() != null ? item.getName() : "Món") +
                            " x" + item.getQuantity() + " (" + formatCurrency(item.getPrice() * item.getQuantity()) + ")");
                    tvItem.setTextSize(13);
                    tvItem.setTextColor(0xFF000000);
                    tvItem.setPadding(0, (int) (2 * getResources().getDisplayMetrics().density), 0, 0);
                    content.addView(tvItem);
                }
            }

            cardView.addView(content);
            llOrderCards.addView(cardView);
        }
    }

    /**
     * Hiển thị dialog hủy hóa đơn
     */
    private void showCancelInvoiceDialog() {
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
                    cancelInvoice(reason);
                })
                .setNegativeButton("Không", null)
                .show();
    }

    /**
     * Hủy hóa đơn
     */
    private void cancelInvoice(String reason) {
        progressBar.setVisibility(View.VISIBLE);

        if (orders.isEmpty()) {
            progressBar.setVisibility(View.GONE);
            return;
        }

        // Xóa order đầu tiên
        Order firstOrder = orders.get(0);
        orderRepository.deleteOrder(firstOrder.getId(), new OrderRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(InvoiceActivity.this, "Đã hủy hóa đơn. Lý do: " + reason, Toast.LENGTH_LONG).show();
                    Log.d(TAG, "Invoice cancelled. Reason: " + reason);
                    finish();
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
     * Hiển thị dialog tách hóa đơn
     */
    private void showSplitInvoiceDialog() {
        if (allItems.isEmpty()) {
            Toast.makeText(this, "Không có món ăn để tách", Toast.LENGTH_SHORT).show();
            return;
        }

        // Tạo dialog chọn món để tách
        boolean[] selectedItems = new boolean[allItems.size()];
        String[] itemNames = new String[allItems.size()];
        for (int i = 0; i < allItems.size(); i++) {
            itemNames[i] = allItems.get(i).getName() + " x" + allItems.get(i).getQuantity();
        }

        new AlertDialog.Builder(this)
                .setTitle("Chọn món ăn để tách hóa đơn")
                .setMultiChoiceItems(itemNames, selectedItems, (dialog, which, isChecked) -> {
                    selectedItems[which] = isChecked;
                })
                .setPositiveButton("Tách", (dialog, which) -> {
                    splitInvoice(selectedItems);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    /**
     * Tách hóa đơn
     */
    private void splitInvoice(boolean[] selectedItems) {
        List<Order.OrderItem> itemsToSplit = new ArrayList<>();
        List<Order.OrderItem> remainingItems = new ArrayList<>();

        for (int i = 0; i < allItems.size(); i++) {
            if (selectedItems[i]) {
                itemsToSplit.add(allItems.get(i));
            } else {
                remainingItems.add(allItems.get(i));
            }
        }

        if (itemsToSplit.isEmpty()) {
            Toast.makeText(this, "Vui lòng chọn ít nhất một món để tách", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        // Lấy serverId và cashierId từ order gốc (hoặc tạo fake nếu không có)
        String serverId = null;
        String cashierId = null;
        String tableId = null;
        if (!orders.isEmpty()) {
            Order originalOrder = orders.get(0);
            serverId = originalOrder.getServerId();
            cashierId = originalOrder.getCashierId();
            tableId = originalOrder.getTableId();
        }

        // Nếu không có, sử dụng fake IDs (giống OrderActivity)
        if (serverId == null || serverId.isEmpty()) {
            serverId = "64a7f3b2c9d1e2f3a4b5c6d7"; // Fake server ID
        }
        if (cashierId == null || cashierId.isEmpty()) {
            cashierId = "64b8e4c3d1f2a3b4c5d6e7f8"; // Fake cashier ID
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
        newOrder.setDiscount(0);
        newOrder.setFinalAmount(splitTotal);
        newOrder.setPaidAmount(0);
        newOrder.setChange(0);
        newOrder.setPaymentMethod("cash");
        newOrder.setOrderStatus("pending");
        newOrder.setPaid(false);

        orderRepository.createOrder(newOrder, new OrderRepository.RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order result) {
                if (!orders.isEmpty()) {
                    Order oldOrder = orders.get(0);
                    oldOrder.setItems(remainingItems);
                    finalizeSplitForOriginalOrder(oldOrder, remainingItems);
                } else {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(InvoiceActivity.this, "Đã tách hóa đơn thành công", Toast.LENGTH_SHORT).show();
                        loadInvoiceData();
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

    private void finalizeSplitForOriginalOrder(Order oldOrder, List<Order.OrderItem> remainingItems) {
        if (oldOrder == null) {
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(InvoiceActivity.this, "Đã tách hóa đơn thành công", Toast.LENGTH_SHORT).show();
                loadInvoiceData();
            });
            return;
        }

        if (remainingItems == null || remainingItems.isEmpty()) {
            orderRepository.deleteOrder(oldOrder.getId(), new OrderRepository.RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(InvoiceActivity.this, "Đã tách hóa đơn thành công", Toast.LENGTH_SHORT).show();
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
            return;
        }

        double remainingTotal = 0;
        for (Order.OrderItem item : remainingItems) {
            remainingTotal += item.getPrice() * item.getQuantity();
        }
        oldOrder.setTotalAmount(remainingTotal);
        oldOrder.setFinalAmount(remainingTotal - oldOrder.getDiscount());

        Map<String, Object> updates = new HashMap<>();
        updates.put("items", remainingItems);
        updates.put("totalAmount", remainingTotal);
        updates.put("finalAmount", oldOrder.getFinalAmount());
        updates.put("tableNumber", oldOrder.getTableNumber());
        if (oldOrder.getTableId() != null && !oldOrder.getTableId().isEmpty()) {
            updates.put("tableId", oldOrder.getTableId());
        }

        orderRepository.updateOrder(oldOrder.getId(), updates, new OrderRepository.RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order updated) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(InvoiceActivity.this, "Đã tách hóa đơn thành công", Toast.LENGTH_SHORT).show();
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
    }

    /**
     * Yêu cầu kiểm tra lại món ăn
     */
    private void requestCheckItems() {
        // Tạo request gửi sang màn phục vụ
        Intent intent = new Intent();
        intent.setAction("com.ph48845.datn_qlnh_rmis.ACTION_CHECK_ITEMS");
        intent.putExtra("tableNumber", tableNumber);
        intent.putExtra("orderIds", getOrderIds());

        // Có thể sử dụng BroadcastReceiver hoặc Notification
        sendBroadcast(intent);

        Toast.makeText(this, "Đã gửi yêu cầu kiểm tra lại món ăn cho bàn " + tableNumber, Toast.LENGTH_LONG).show();
        Log.d(TAG, "Request check items for table " + tableNumber);
    }

    /**
     * Hiển thị menu tùy chọn khi nhấn giữ hóa đơn
     */
    private void showInvoiceOptionsDialog() {
        List<String> options = new ArrayList<>();
        options.add(isEditMode ? "Lưu chỉnh sửa" : "Chỉnh sửa");
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
                            toggleEditMode();
                            break;
                        case 1:
                            showSplitInvoiceDialog();
                            break;
                        case 2:
                            printTemporaryBill();
                            break;
                        case 3:
                            processPayment();
                            break;
                        case 4:
                            requestCheckItems();
                            break;
                        case 5:
                            showCancelInvoiceDialog();
                            break;
                    }
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    /**
     * Lấy danh sách order IDs
     */
    private String[] getOrderIds() {
        String[] ids = new String[orders.size()];
        for (int i = 0; i < orders.size(); i++) {
            ids[i] = orders.get(i).getId();
        }
        return ids;
    }

    /**
     * In tạm tính - Mở Activity hiển thị hóa đơn từ XML layout
     */
    private void printTemporaryBill() {
        if (orders == null || orders.isEmpty()) {
            Toast.makeText(this, "Không có hóa đơn để in", Toast.LENGTH_SHORT).show();
            return;
        }

        // Lấy order đầu tiên để in
        Order firstOrder = orders.get(0);
        if (firstOrder == null) {
            Toast.makeText(this, "Hóa đơn không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        // Mở Activity hiển thị hóa đơn từ XML layout
        Intent intent = new Intent(this, PrintBillActivity.class);
        intent.putExtra("order", firstOrder);
        intent.putExtra("tableNumber", tableNumber);
        intent.putExtra("orderCode", generateOrderCode());
        startActivity(intent);
    }

    /**
     * Xử lý thanh toán
     */
    private void processPayment() {
        if (orders.isEmpty()) {
            Toast.makeText(this, "Không có hóa đơn để thanh toán", Toast.LENGTH_SHORT).show();
            return;
        }

        // TODO: Mở màn hình thanh toán hoặc xử lý thanh toán
        Toast.makeText(this, "Chức năng thanh toán đang được phát triển", Toast.LENGTH_SHORT).show();
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
     * Thanh toán cho một order cụ thể
     */
    private void processPaymentForSpecificOrder(Order order) {
        if (order == null || order.getId() == null) {
            Toast.makeText(this, "Hóa đơn không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        Map<String, Object> updates = new HashMap<>();
        updates.put("orderStatus", "paid");
        updates.put("paid", true);
        updates.put("paidAmount", order.getFinalAmount());
        updates.put("change", 0.0);
        updates.put("paymentMethod", "cash");
        updates.put("paidAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(new Date()));

        orderRepository.updateOrder(order.getId(), updates, new OrderRepository.RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order result) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(InvoiceActivity.this, "Thanh toán thành công", Toast.LENGTH_SHORT).show();
                    loadInvoiceData();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(InvoiceActivity.this, "Lỗi thanh toán: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
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
        Intent intent = new Intent();
        intent.setAction("com.ph48845.datn_qlnh_rmis.ACTION_CHECK_ITEMS");
        intent.putExtra("tableNumber", tableNumber);
        intent.putExtra("orderIds", new String[]{order.getId()});
        sendBroadcast(intent);
        Toast.makeText(this, "Đã gửi yêu cầu kiểm tra lại món ăn", Toast.LENGTH_LONG).show();
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
        editingOrder = order;
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
