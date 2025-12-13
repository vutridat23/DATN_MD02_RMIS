package com.ph48845.datn_qlnh_rmis.ui.thanhtoan;

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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PaymentCardActivity extends AppCompatActivity {

    private TextView tvTotalAmount;
    private MaterialButton btnPay;
    private ImageButton btnBack;
    private WebView webViewPayment;
    private View scrollLayout;

    private String orderId;
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

    private void getDataFromIntent() {
        orderId = getIntent().getStringExtra("orderId");
        amount = getIntent().getDoubleExtra("amount", 0);

        NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
        tvTotalAmount.setText("Tổng: " + nf.format(amount) + "₫");

        // Lấy thông tin order từ repository để lưu vào currentOrder
        orderRepository.getOrdersByTableNumber(null, null, new OrderRepository.RepositoryCallback<java.util.List<Order>>() {
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
                        Toast.makeText(PaymentCardActivity.this, "Không tìm thấy đơn hàng", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(PaymentCardActivity.this, "Lỗi khi lấy đơn hàng: " + message, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void setupWebView() {
        webViewPayment.getSettings().setJavaScriptEnabled(true);
        webViewPayment.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                if (url.contains("/vnpay-return")) {
                    String responseCode = request.getUrl().getQueryParameter("vnp_ResponseCode");
                    if ("00".equals(responseCode)) {
                        toast("Thanh toán thành công!");
                        processPayment("Thẻ"); // thanh toán thành công
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

    private void setupEvents() {
        btnBack.setOnClickListener(v -> finish());
        btnPay.setOnClickListener(v -> createVnPayPayment());
    }

    private void createVnPayPayment() {
        ApiService apiService = RetrofitClient.getInstance().getApiService();

        Map<String, Object> body = new HashMap<>();
        body.put("orderId", orderId);

        apiService.createCardPayment(body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String paymentUrl = (String) response.body().get("paymentUrl");
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

    private void processPayment(String method) {
        if (currentOrder == null) {
            toast("Lỗi: không có đơn hàng để thanh toán");
            finish();
            return;
        }

        double amountCustomerGiven = amount; // thanh toán thẻ không cần tiền mặt

        orderRepository.payOrder(currentOrder.getId(), method, amountCustomerGiven, new OrderRepository.RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order updatedOrder) {
                resetTableAndFinish(updatedOrder);
            }

            @Override
            public void onError(String message) {
                toast("Thanh toán thất bại: " + message);
                webViewPayment.setVisibility(View.GONE);
                scrollLayout.setVisibility(View.VISIBLE);
            }
        });
    }

    private void resetTableAndFinish(Order updatedOrder) {
        String tableId = updatedOrder.getTableId();
        if (tableId != null) {
            tableRepository.resetTableAfterPayment(tableId, new TableRepository.RepositoryCallback<TableItem>() {
                @Override
                public void onSuccess(TableItem table) {
                    finishSuccess(updatedOrder);
                }

                @Override
                public void onError(String message) {
                    toast("Thanh toán xong nhưng không reset bàn: " + message);
                    finishSuccess(updatedOrder);
                }
            });
        } else {
            finishSuccess(updatedOrder);
        }
    }

    private void finishSuccess(Order order) {
        Intent intent = new Intent(this, ThuNganActivity.class);
        intent.putExtra("paidOrder", order);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
