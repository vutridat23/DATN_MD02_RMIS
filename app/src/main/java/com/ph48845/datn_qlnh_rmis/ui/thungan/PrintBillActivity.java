package com.ph48845.datn_qlnh_rmis.ui.thungan;

import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Activity hiển thị hóa đơn tạm tính và cho phép in
 */
public class PrintBillActivity extends AppCompatActivity {

    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("#,###");
    
    private TextView tvTitle;
    private TextView tvTable;
    private TextView tvOrderCode;
    private TextView tvDate;
    private LinearLayout llItemsContainer;
    private TextView tvTotal;
    private TextView tvDiscount;
    private TextView tvFinalAmount;
    private TextView tvNote;
    private Button btnPrint;
    private Toolbar toolbar;
    private LinearLayout llDiscount;
    private LinearLayout llFinalAmount;
    
    private Order order;
    private int tableNumber;
    private String orderCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_print_bill);

        // Lấy dữ liệu từ Intent
        order = (Order) getIntent().getSerializableExtra("order");
        tableNumber = getIntent().getIntExtra("tableNumber", 0);
        orderCode = getIntent().getStringExtra("orderCode");

        initViews();
        setupToolbar();
        setupData();
        setupPrintButton();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvTitle = findViewById(R.id.tvTitle);
        tvTable = findViewById(R.id.tvTable);
        tvOrderCode = findViewById(R.id.tvOrderCode);
        tvDate = findViewById(R.id.tvDate);
        llItemsContainer = findViewById(R.id.llItemsContainer);
        tvTotal = findViewById(R.id.tvTotal);
        tvDiscount = findViewById(R.id.tvDiscount);
        tvFinalAmount = findViewById(R.id.tvFinalAmount);
        tvNote = findViewById(R.id.tvNote);
        btnPrint = findViewById(R.id.btnPrint);
        llDiscount = findViewById(R.id.llDiscount);
        llFinalAmount = findViewById(R.id.llFinalAmount);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupData() {
        if (order == null) {
            finish();
            return;
        }

        // Hiển thị thông tin bàn
        tvTable.setText("Bàn: " + String.format("%02d", tableNumber));
        
        // Hiển thị mã đơn
        if (orderCode != null && !orderCode.isEmpty()) {
            tvOrderCode.setText("Mã đơn: " + orderCode);
        } else if (order.getId() != null) {
            String code = order.getId().length() > 12 
                ? order.getId().substring(0, 12) 
                : order.getId();
            tvOrderCode.setText("Mã đơn: HD" + code);
        } else {
            tvOrderCode.setText("Mã đơn: N/A");
        }

        // Hiển thị ngày
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        tvDate.setText("Ngày: " + sdf.format(new Date()));

        // Hiển thị danh sách món ăn
        llItemsContainer.removeAllViews();
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            for (Order.OrderItem item : order.getItems()) {
                if (item == null) continue;
                addItemRow(item);
            }
        }

        // Hiển thị tổng tiền
        tvTotal.setText(formatCurrency(order.getTotalAmount()));

        // Hiển thị giảm giá nếu có
        if (order.getDiscount() > 0) {
            llDiscount.setVisibility(View.VISIBLE);
            tvDiscount.setText(formatCurrency(order.getDiscount()));
        } else {
            llDiscount.setVisibility(View.GONE);
        }

        // Hiển thị thành tiền nếu có
        if (order.getFinalAmount() > 0 && order.getFinalAmount() != order.getTotalAmount()) {
            llFinalAmount.setVisibility(View.VISIBLE);
            tvFinalAmount.setText(formatCurrency(order.getFinalAmount()));
        } else {
            llFinalAmount.setVisibility(View.GONE);
        }
    }

    private void addItemRow(Order.OrderItem item) {
        View itemView = getLayoutInflater().inflate(R.layout.item_bill_row, llItemsContainer, false);
        
        TextView tvItemName = itemView.findViewById(R.id.tvItemName);
        TextView tvItemQuantity = itemView.findViewById(R.id.tvItemQuantity);
        TextView tvItemPrice = itemView.findViewById(R.id.tvItemPrice);
        TextView tvItemTotal = itemView.findViewById(R.id.tvItemTotal);

        tvItemName.setText(item.getName() != null ? item.getName() : "Món");
        tvItemQuantity.setText(String.valueOf(item.getQuantity()));
        tvItemPrice.setText(formatCurrency(item.getPrice()));
        
        double itemTotal = item.getPrice() * item.getQuantity();
        tvItemTotal.setText(formatCurrency(itemTotal));

        llItemsContainer.addView(itemView);
    }

    private void setupPrintButton() {
        btnPrint.setOnClickListener(v -> printBill());
    }

    private void printBill() {
        // Tạo WebView để in
        android.webkit.WebView webView = new android.webkit.WebView(this);
        webView.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public void onPageFinished(android.webkit.WebView view, String url) {
                createPrintJob(webView);
            }
        });
        
        // Tạo HTML từ layout hiện tại
        String htmlContent = generateHTMLFromLayout();
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null);
    }

    private String generateHTMLFromLayout() {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>");
        html.append("body { font-family: Arial, sans-serif; padding: 20px; }");
        html.append("h1 { text-align: center; font-size: 20px; margin-bottom: 16px; }");
        html.append("table { width: 100%; border-collapse: collapse; margin: 16px 0; }");
        html.append("th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }");
        html.append("th { background-color: #f0f0f0; font-weight: bold; }");
        html.append(".info { margin-bottom: 8px; }");
        html.append(".total { font-weight: bold; margin-top: 8px; }");
        html.append(".note { font-style: italic; color: #666; text-align: center; margin-top: 8px; }");
        html.append("</style></head><body>");
        
        html.append("<h1>HÓA ĐƠN TẠM TÍNH</h1>");
        html.append("<div class='info'><strong>Bàn:</strong> ").append(String.format("%02d", tableNumber)).append("</div>");
        html.append("<div class='info'><strong>Mã đơn:</strong> ").append(tvOrderCode.getText().toString().replace("Mã đơn: ", "")).append("</div>");
        html.append("<div class='info'><strong>Ngày:</strong> ").append(tvDate.getText().toString().replace("Ngày: ", "")).append("</div>");
        
        html.append("<table>");
        html.append("<tr><th>Món ăn</th><th>SL</th><th>Giá</th><th>Thành tiền</th></tr>");
        
        if (order.getItems() != null) {
            for (Order.OrderItem item : order.getItems()) {
                if (item == null) continue;
                double itemTotal = item.getPrice() * item.getQuantity();
                html.append("<tr>");
                html.append("<td>").append(item.getName() != null ? item.getName() : "Món").append("</td>");
                html.append("<td>").append(item.getQuantity()).append("</td>");
                html.append("<td>").append(formatCurrency(item.getPrice())).append("</td>");
                html.append("<td>").append(formatCurrency(itemTotal)).append("</td>");
                html.append("</tr>");
            }
        }
        
        html.append("</table>");
        html.append("<div class='total'>Tổng cộng: ").append(tvTotal.getText().toString()).append("</div>");
        
        if (order.getDiscount() > 0) {
            html.append("<div class='total'>Giảm giá: ").append(tvDiscount.getText().toString()).append("</div>");
        }
        
        if (order.getFinalAmount() > 0 && order.getFinalAmount() != order.getTotalAmount()) {
            html.append("<div class='total'>Thành tiền: ").append(tvFinalAmount.getText().toString()).append("</div>");
        }
        
        html.append("<div class='note'>").append(tvNote.getText().toString()).append("</div>");
        html.append("</body></html>");
        
        return html.toString();
    }

    private void createPrintJob(android.webkit.WebView webView) {
        PrintManager printManager = (PrintManager) getSystemService(PRINT_SERVICE);
        String jobName = "Hóa đơn tạm tính - Bàn " + tableNumber;
        PrintDocumentAdapter printAdapter = webView.createPrintDocumentAdapter(jobName);
        PrintAttributes.Builder builder = new PrintAttributes.Builder();
        builder.setMediaSize(PrintAttributes.MediaSize.ISO_A4);
        PrintJob printJob = printManager.print(jobName, printAdapter, builder.build());
        
        if (printJob != null) {
            android.widget.Toast.makeText(this, "Đang in hóa đơn...", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private String formatCurrency(double amount) {
        return CURRENCY_FORMAT.format(amount) + "₫";
    }
}

