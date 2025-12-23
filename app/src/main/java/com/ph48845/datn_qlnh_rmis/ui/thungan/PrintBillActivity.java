package com.ph48845.datn_qlnh_rmis.ui.thungan;

import android.content.Intent;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;
import android.util.Log;
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
            // Ẩn title mặc định để chỉ hiển thị TextView custom
            getSupportActionBar().setDisplayShowTitleEnabled(false);
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

    /**
     * Kiểm tra xem món ăn đã bị hủy
     */
    private boolean isItemCancelled(Order.OrderItem item) {
        if (item == null) return false;
        String status = item.getStatus();
        if (status == null || status.trim().isEmpty()) return false;
        
        String statusLower = status.toLowerCase().trim();
        
        // Kiểm tra đã hủy
        return statusLower.contains("cancelled") ||
               statusLower.contains("canceled") ||
               statusLower.contains("hủy") ||
               statusLower.contains("huy") ||
               statusLower.contains("đã hủy");
    }
    
    /**
     * Lấy giá của món (0 nếu đã hủy)
     */
    private double getItemPrice(Order.OrderItem item) {
        if (item == null) return 0.0;
        if (isItemCancelled(item)) return 0.0;
        return item.getPrice();
    }

    private void addItemRow(Order.OrderItem item) {
        View itemView = getLayoutInflater().inflate(R.layout.item_bill_row, llItemsContainer, false);
        
        TextView tvItemName = itemView.findViewById(R.id.tvItemName);
        TextView tvItemQuantity = itemView.findViewById(R.id.tvItemQuantity);
        TextView tvItemPrice = itemView.findViewById(R.id.tvItemPrice);
        TextView tvItemTotal = itemView.findViewById(R.id.tvItemTotal);

        tvItemName.setText(item.getName() != null ? item.getName() : "Món");
        tvItemQuantity.setText(String.valueOf(item.getQuantity()));
        
        // Món đã hủy sẽ có giá 0 đồng
        double itemPrice = getItemPrice(item);
        tvItemPrice.setText(formatCurrency(itemPrice));
        
        double itemTotal = itemPrice * item.getQuantity();
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
        // Đọc HTML template từ file
        String template = readRawResource(R.raw.bill_template);
        
        // Thay thế các placeholders
        template = template.replace("{{TABLE_NUMBER}}", String.format("%02d", tableNumber));
        template = template.replace("{{ORDER_CODE}}", tvOrderCode.getText().toString().replace("Mã đơn: ", ""));
        template = template.replace("{{DATE}}", tvDate.getText().toString().replace("Ngày: ", ""));
        
        // Tạo rows cho bảng món ăn
        StringBuilder itemsRows = new StringBuilder();
        if (order.getItems() != null) {
            for (Order.OrderItem item : order.getItems()) {
                if (item == null) continue;
                // Món đã hủy sẽ có giá 0 đồng
                double itemPrice = getItemPrice(item);
                double itemTotal = itemPrice * item.getQuantity();
                itemsRows.append("<tr>");
                itemsRows.append("<td>").append(item.getName() != null ? item.getName() : "Món").append("</td>");
                itemsRows.append("<td>").append(item.getQuantity()).append("</td>");
                itemsRows.append("<td>").append(formatCurrency(itemPrice)).append("</td>");
                itemsRows.append("<td>").append(formatCurrency(itemTotal)).append("</td>");
                itemsRows.append("</tr>");
            }
        }
        template = template.replace("{{ITEMS_TABLE_ROWS}}", itemsRows.toString());
        
        // Thay thế tổng cộng
        template = template.replace("{{TOTAL}}", tvTotal.getText().toString());
        
        // Thay thế giảm giá (nếu có)
        if (order.getDiscount() > 0) {
            template = template.replace("{{DISCOUNT_ROW}}", 
                "<div class='total'>Giảm giá: " + tvDiscount.getText().toString() + "</div>");
        } else {
            template = template.replace("{{DISCOUNT_ROW}}", "");
        }
        
        // Thay thế thành tiền (nếu có)
        if (order.getFinalAmount() > 0 && order.getFinalAmount() != order.getTotalAmount()) {
            template = template.replace("{{FINAL_AMOUNT_ROW}}", 
                "<div class='total'>Thành tiền: " + tvFinalAmount.getText().toString() + "</div>");
        } else {
            template = template.replace("{{FINAL_AMOUNT_ROW}}", "");
        }
        
        // Thay thế ghi chú
        template = template.replace("{{NOTE}}", tvNote.getText().toString());
        
        return template;
    }
    
    private String readRawResource(int resourceId) {
        try {
            java.io.InputStream inputStream = getResources().openRawResource(resourceId);
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(inputStream, "UTF-8"));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
            reader.close();
            inputStream.close();
            return stringBuilder.toString();
        } catch (Exception e) {
            Log.e("PrintBillActivity", "Error reading template: " + e.getMessage());
            // Fallback: trả về template đơn giản
            return getFallbackTemplate();
        }
    }
    
    private String getFallbackTemplate() {
        // Template dự phòng nếu không đọc được file
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>" +
               "body { font-family: Arial, sans-serif; padding: 20px; }" +
               "h1 { text-align: center; font-size: 20px; margin-bottom: 16px; }" +
               "table { width: 100%; border-collapse: collapse; margin: 16px 0; }" +
               "th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }" +
               "th { background-color: #f0f0f0; font-weight: bold; }" +
               ".info { margin-bottom: 8px; }" +
               ".total { font-weight: bold; margin-top: 8px; }" +
               ".note { font-style: italic; color: #666; text-align: center; margin-top: 8px; }" +
               "</style></head><body>" +
               "<h1>HÓA ĐƠN TẠM TÍNH</h1>" +
               "<div class='info'><strong>Bàn:</strong> {{TABLE_NUMBER}}</div>" +
               "<div class='info'><strong>Mã đơn:</strong> {{ORDER_CODE}}</div>" +
               "<div class='info'><strong>Ngày:</strong> {{DATE}}</div>" +
               "<table><tr><th>Món ăn</th><th>SL</th><th>Giá</th><th>Thành tiền</th></tr>" +
               "{{ITEMS_TABLE_ROWS}}</table>" +
               "<div class='total'>Tổng cộng: {{TOTAL}}</div>" +
               "{{DISCOUNT_ROW}}{{FINAL_AMOUNT_ROW}}" +
               "<div class='note'>{{NOTE}}</div></body></html>";
    }

    private void createPrintJob(android.webkit.WebView webView) {
        PrintManager printManager = (PrintManager) getSystemService(PRINT_SERVICE);
        String jobName = "Hóa đơn tạm tính - Bàn " + tableNumber;
        PrintDocumentAdapter printAdapter = webView.createPrintDocumentAdapter(jobName);
        PrintAttributes.Builder builder = new PrintAttributes.Builder();
        builder.setMediaSize(PrintAttributes.MediaSize.ISO_A4);
        PrintJob printJob = printManager.print(jobName, printAdapter, builder.build());
        
        // Lấy orderId từ Intent
        String orderId = getIntent().getStringExtra("orderId");
        Log.d("PrintBillActivity", "createPrintJob: orderId = " + orderId);
        
        if (printJob != null) {
            android.widget.Toast.makeText(this, "Đang in hóa đơn...", android.widget.Toast.LENGTH_SHORT).show();
            Log.d("PrintBillActivity", "createPrintJob: Print job created successfully");
            
            // SetResult ngay để InvoiceActivity có thể clear temp calculation request
            // (PrintJob không có callback rõ ràng, nên setResult ngay sau khi tạo print job)
            if (orderId != null) {
                Intent resultIntent = new Intent();
                resultIntent.putExtra("orderId", orderId);
                setResult(RESULT_OK, resultIntent);
                Log.d("PrintBillActivity", "✅ SetResult OK for orderId: " + orderId);
            } else {
                Log.w("PrintBillActivity", "⚠️ WARNING: orderId is null, cannot setResult!");
            }
        } else {
            android.widget.Toast.makeText(this, "Không thể tạo print job", android.widget.Toast.LENGTH_SHORT).show();
            Log.e("PrintBillActivity", "❌ Failed to create print job");
            // Vẫn setResult để InvoiceActivity biết đã thử in (dù thất bại)
            if (orderId != null) {
                Intent resultIntent = new Intent();
                resultIntent.putExtra("orderId", orderId);
                setResult(RESULT_CANCELED, resultIntent);
                Log.d("PrintBillActivity", "SetResult CANCELED for orderId: " + orderId + " (print job failed)");
            }
        }
    }

    private String formatCurrency(double amount) {
        return CURRENCY_FORMAT.format(amount) + "₫";
    }
}

