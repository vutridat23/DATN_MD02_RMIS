package com.ph48845.datn_qlnh_rmis.ui.thungan.thanhtoan;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;
import com.ph48845.datn_qlnh_rmis.ui.thungan.ThuNganActivity;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PaymentCardActivity extends AppCompatActivity {

    private TextView tvTotalAmount;
    private MaterialButton btnPay;
    private ImageButton btnBack;
    private WebView webViewPayment;
    private View scrollLayout;

    // ===== DATA =====
    private String orderId;                    // 1 order
    private ArrayList<String> orderIds;        // nhiều order
    private double amount;
    private Order currentOrder;

    private OrderRepository orderRepository;
    private TableRepository tableRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_card);

        orderRepository = new OrderRepository();
        tableRepository = new TableRepository();

        initViews();
        getDataFromIntent();
        setupWebView();
        setupEvents();
    }

    private void initViews() {
        tvTotalAmount = findViewById(R.id.tvTotalAmount);
        btnPay = findViewById(R.id.btnPay);
        btnBack = findViewById(R.id.btnBack);
        webViewPayment = findViewById(R.id.webViewPayment);
        scrollLayout = findViewById(R.id.scrollLayout);

        webViewPayment.setVisibility(View.GONE);
    }

    // ================== GET INTENT DATA ==================
    private void getDataFromIntent() {
        orderId = getIntent().getStringExtra("orderId");
        orderIds = getIntent().getStringArrayListExtra("orderIds");
        amount = getIntent().getDoubleExtra("amount", 0);

        NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
        tvTotalAmount.setText("Tổng: " + nf.format(amount) + "₫");

        if (orderId != null) {
            loadSingleOrder(orderId);

        } else if (orderIds != null && !orderIds.isEmpty()) {
            // nhiều hóa đơn → KHÔNG load currentOrder
            currentOrder = null;

        } else {
            toast("Không có hóa đơn để thanh toán");
            finish();
        }
    }

    // ================== LOAD 1 ORDER ==================
    private void loadSingleOrder(String orderId) {
        orderRepository.getOrdersByTableNumber(
                null, null,
                new OrderRepository.RepositoryCallback<java.util.List<Order>>() {
                    @Override
                    public void onSuccess(java.util.List<Order> orders) {
                        for (Order order : orders) {
                            if (order.getId().equals(orderId)) {
                                currentOrder = order;
                                break;
                            }
                        }
                        if (currentOrder == null) {
                            runOnUiThread(() -> {
                                toast("Không tìm thấy đơn hàng");
                                finish();
                            });
                        }
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            toast("Lỗi lấy đơn hàng: " + message);
                            finish();
                        });
                    }
                });
    }

    // ================== WEBVIEW ==================
    private void setupWebView() {
        webViewPayment.getSettings().setJavaScriptEnabled(true);
        webViewPayment.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                if (url.contains("/vnpay-return")) {
                    String responseCode =
                            request.getUrl().getQueryParameter("vnp_ResponseCode");

                    if ("00".equals(responseCode)) {
                        toast("Thanh toán thành công!");

                        if (orderId != null) {
                            processSingleOrderPayment();
                        } else {
                            processMultipleOrdersPayment();
                        }

                    } else {
                        toast("Thanh toán thất bại!");
                        webViewPayment.setVisibility(View.GONE);
                        scrollLayout.setVisibility(View.VISIBLE);
                    }
                    return true;
                }
                return false;
            }
        });
    }

    // ================== EVENTS ==================
    private void setupEvents() {
        btnBack.setOnClickListener(v -> finish());
        btnPay.setOnClickListener(v -> createVnPayPayment());
    }

    // ================== CREATE PAYMENT URL ==================
    private void createVnPayPayment() {
        ApiService apiService = RetrofitClient.getInstance().getApiService();

        Map<String, Object> body = new HashMap<>();

        if (orderId != null) {
            body.put("orderId", orderId);
        } else {
            body.put("orderIds", orderIds);
        }

        apiService.createCardPayment(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call,
                                   Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String paymentUrl =
                            (String) response.body().get("paymentUrl");

                    scrollLayout.setVisibility(View.GONE);
                    webViewPayment.setVisibility(View.VISIBLE);
                    webViewPayment.loadUrl(paymentUrl);
                } else {
                    toast("Không tạo được link thanh toán");
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                toast("Lỗi kết nối server");
            }
        });
    }

    // ================== PAY 1 ORDER ==================
    private void processSingleOrderPayment() {
        orderRepository.payOrder(
                currentOrder.getId(),
                "Thẻ",
                amount,
                new OrderRepository.RepositoryCallback<Order>() {
                    @Override
                    public void onSuccess(Order updatedOrder) {
                        resetTableAndFinish(updatedOrder);
                    }

                    @Override
                    public void onError(String message) {
                        toast("Thanh toán thất bại: " + message);
                    }
                });
    }

    // ================== PAY MULTI ORDERS ==================
    private void processMultipleOrdersPayment() {

        final int total = orderIds.size();
        AtomicInteger successCount = new AtomicInteger(0);

        for (String id : orderIds) {
            orderRepository.payOrder(
                    id,
                    "Thẻ",
                    0,
                    new OrderRepository.RepositoryCallback<Order>() {
                        @Override
                        public void onSuccess(Order order) {
                            if (successCount.incrementAndGet() == total) {
                                resetTableAfterMultiPayment();
                            }
                        }

                        @Override
                        public void onError(String message) {
                            toast("Có hóa đơn thanh toán thất bại");
                            finish();
                        }
                    });
        }
    }

    // ================== RESET TABLE ==================
    private void resetTableAfterMultiPayment() {
        tableRepository.getAllTables(
                new TableRepository.RepositoryCallback<java.util.List<TableItem>>() {
                    @Override
                    public void onSuccess(java.util.List<TableItem> tables) {
                        if (tables == null || tables.isEmpty()) {
                            finishSuccessMulti();
                            return;
                        }

                        String tableId = tables.get(0).getId();
                        tableRepository.resetTableAfterPayment(
                                tableId,
                                new TableRepository.RepositoryCallback<TableItem>() {
                                    @Override
                                    public void onSuccess(TableItem table) {
                                        finishSuccessMulti();
                                    }

                                    @Override
                                    public void onError(String message) {
                                        finishSuccessMulti();
                                    }
                                });
                    }

                    @Override
                    public void onError(String message) {
                        finishSuccessMulti();
                    }
                });
    }

    private void resetTableAndFinish(Order updatedOrder) {
        String tableId = updatedOrder.getTableId();
        if (tableId != null) {
            tableRepository.resetTableAfterPayment(
                    tableId,
                    new TableRepository.RepositoryCallback<TableItem>() {
                        @Override
                        public void onSuccess(TableItem table) {
                            finishSuccessSingle(updatedOrder);
                        }

                        @Override
                        public void onError(String message) {
                            finishSuccessSingle(updatedOrder);
                        }
                    });
        } else {
            finishSuccessSingle(updatedOrder);
        }
    }

    // ================== FINISH ==================
    private void finishSuccessSingle(Order order) {
        Intent intent = new Intent(this, ThuNganActivity.class);
        intent.putExtra("paidOrder", order);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void finishSuccessMulti() {
        Intent intent = new Intent(this, ThuNganActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
