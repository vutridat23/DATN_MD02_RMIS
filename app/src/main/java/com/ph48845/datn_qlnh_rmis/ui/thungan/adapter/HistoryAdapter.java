package com.ph48845.datn_qlnh_rmis.ui.thungan.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.HistoryItem;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private final List<HistoryItem> historyList;

    public HistoryAdapter(List<HistoryItem> historyList) {
        this.historyList = historyList;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Ánh xạ layout item_history mới
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        HistoryItem item = historyList.get(position);

        // 1. Mã hóa đơn (Thêm dấu # cho đẹp)
        String orderId = item.getId() != null ? item.getId() : "---";
        holder.tvOrderId.setText("#" + orderId);

        // 2. Số bàn
        if (item.getTableNumber() != null) {
            holder.tvTableNumber.setText("Bàn " + item.getTableNumber());
        } else {
            holder.tvTableNumber.setText("Mang về");
        }

        // 3. Ngày giờ
        holder.tvOrderDate.setText(formatDate(item.getCreatedAt()));

        // 4. Số món (Chỉ hiện số lượng, không cần chữ "Số món:" thừa thãi)
        // Ví dụ: "05 món"
        int count = item.getTotalItems();
        holder.tvItemCount.setText(String.format(Locale.getDefault(), "%02d món", count));

        // 5. Tổng tiền (Chỉ hiện số tiền, layout đã có chữ "TỔNG THANH TOÁN")
        if (item.getTotalAmount() != null) {
            NumberFormat vnFormat = NumberFormat.getInstance(new Locale("vi", "VN"));
            String money = vnFormat.format(item.getTotalAmount()) + " đ";
            holder.tvTotal.setText(money);
        } else {
            holder.tvTotal.setText("0 đ");
        }

        // 6. Click để xem chi tiết món ăn
        holder.itemView.setOnClickListener(v -> {
            if (item.getItems() != null && !item.getItems().isEmpty()) {
                showOrderDetailsDialog(v.getContext(), item);
            } else {
                android.widget.Toast
                        .makeText(v.getContext(), "Không có chi tiết món ăn", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return historyList != null ? historyList.size() : 0;
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {

        TextView tvOrderId, tvTableNumber, tvOrderDate, tvItemCount, tvTotal;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            // Đảm bảo các ID này khớp với file item_history.xml mới
            tvOrderId = itemView.findViewById(R.id.tvOrderId);
            tvTableNumber = itemView.findViewById(R.id.tvTableNumber);
            tvOrderDate = itemView.findViewById(R.id.tvOrderDate);
            tvItemCount = itemView.findViewById(R.id.tvItemCount);
            tvTotal = itemView.findViewById(R.id.tvTotal);
        }
    }

    /**
     * Kiểm tra xem món ăn đã bị hủy
     */
    private boolean isItemCancelled(HistoryItem.OrderItemDetail item) {
        if (item == null) return false;
        String status = item.getStatus();
        if (status == null || status.trim().isEmpty()) {
            // Nếu không có status, kiểm tra xem có thể là null hoặc empty
            return false;
        }
        
        String statusLower = status.toLowerCase().trim();
        
        // Log để debug
        android.util.Log.d("HistoryAdapter", "Checking status for item: " + item.getDishName() + 
                          ", status: '" + status + "', statusLower: '" + statusLower + "'");
        
        // Kiểm tra đã hủy - kiểm tra nhiều format khác nhau
        boolean isCancelled = statusLower.contains("cancelled") ||
                              statusLower.contains("canceled") ||
                              statusLower.contains("hủy") ||
                              statusLower.contains("huy") ||
                              statusLower.contains("đã hủy") ||
                              statusLower.equals("cancel") ||
                              statusLower.equals("cancelled") ||
                              statusLower.equals("canceled");
        
        if (isCancelled) {
            android.util.Log.d("HistoryAdapter", "Item " + item.getDishName() + " is CANCELLED");
        }
        
        return isCancelled;
    }
    
    /**
     * Kiểm tra xem món ăn đã xong hoặc đang làm
     * Đã xong: done, xong, served, ready, completed, hoàn thành
     * Đang làm: preparing, in_progress, processing, đang làm, đang nấu
     */
    private boolean isItemDoneOrPreparing(HistoryItem.OrderItemDetail item) {
        if (item == null) return false;
        String status = item.getStatus();
        if (status == null || status.trim().isEmpty()) return false;
        
        String statusLower = status.toLowerCase().trim();
        
        // Nếu đã hủy thì không tính là done/preparing
        if (isItemCancelled(item)) return false;
        
        // Kiểm tra đã xong
        boolean isDone = statusLower.contains("done") || 
                        statusLower.contains("xong") || 
                        statusLower.contains("served") || 
                        statusLower.contains("ready") || 
                        statusLower.contains("completed") ||
                        statusLower.contains("hoàn thành");
        
        // Kiểm tra đang làm
        boolean isPreparing = statusLower.contains("preparing") ||
                             statusLower.contains("in_progress") ||
                             statusLower.contains("processing") ||
                             statusLower.contains("đang làm") ||
                             statusLower.contains("đang nấu");
        
        return isDone || isPreparing;
    }
    
    /**
     * Lấy giá của món (0 nếu đã hủy)
     */
    private double getItemPrice(HistoryItem.OrderItemDetail item) {
        if (item == null) return 0.0;
        
        boolean cancelled = isItemCancelled(item);
        if (cancelled) {
            android.util.Log.d("HistoryAdapter", "Item " + item.getDishName() + " is cancelled, returning price 0");
            return 0.0;
        }
        
        double price = item.getPrice();
        android.util.Log.d("HistoryAdapter", "Item " + item.getDishName() + " is not cancelled, returning price: " + price);
        return price;
    }

    // Hiển thị dialog chi tiết món ăn
    private void showOrderDetailsDialog(android.content.Context context, HistoryItem item) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(context);
        builder.setTitle("Chi tiết hóa đơn #"
                + (item.getId() != null ? item.getId().substring(Math.max(0, item.getId().length() - 6)) : ""));

        // Tạo nội dung hiển thị
        StringBuilder details = new StringBuilder();
        details.append("Bàn: ").append(item.getTableNumber() != null ? item.getTableNumber() : "Mang về").append("\n");
        details.append("Ngày: ").append(formatDate(item.getCreatedAt())).append("\n\n");
        details.append("DANH SÁCH MÓN ĂN:\n");
        details.append("─────────────────────\n");

        NumberFormat vnFormat = NumberFormat.getInstance(new Locale("vi", "VN"));
        
        // Tính tổng tiền chỉ từ món đã xong và đang làm (không tính món đã hủy, món pending, etc.)
        double totalAmount = 0.0;

        if (item.getItems() != null) {
            android.util.Log.d("HistoryAdapter", "=== START Processing " + item.getItems().size() + " items ===");
            
            // Hiển thị TẤT CẢ món (done, preparing, pending) - chỉ tính tổng tiền từ done/preparing
            for (HistoryItem.OrderItemDetail orderItem : item.getItems()) {
                String dishName = orderItem.getDishName() != null ? orderItem.getDishName() : "Món ăn";
                int quantity = orderItem.getQuantity();
                String status = orderItem.getStatus();
                double originalPrice = orderItem.getPrice();
                
                // Log chi tiết để debug
                android.util.Log.d("HistoryAdapter", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                android.util.Log.d("HistoryAdapter", "Item: " + dishName);
                android.util.Log.d("HistoryAdapter", "  Quantity: " + quantity);
                android.util.Log.d("HistoryAdapter", "  Status: '" + status + "'");
                android.util.Log.d("HistoryAdapter", "  Original Price: " + originalPrice);
                
                // Món đã hủy sẽ có giá 0 đồng
                double itemPrice = getItemPrice(orderItem);
                double subtotal = quantity * itemPrice;
                
                boolean isCancelled = isItemCancelled(orderItem);
                boolean isDoneOrPreparing = isItemDoneOrPreparing(orderItem);
                
                // Log kết quả
                android.util.Log.d("HistoryAdapter", "  Is Cancelled: " + isCancelled);
                android.util.Log.d("HistoryAdapter", "  Is Done/Preparing: " + isDoneOrPreparing);
                android.util.Log.d("HistoryAdapter", "  Final Price: " + itemPrice);
                android.util.Log.d("HistoryAdapter", "  Subtotal: " + subtotal);
                
                // Chỉ cộng vào tổng tiền nếu món đã xong hoặc đang làm
                if (isDoneOrPreparing) {
                    totalAmount += subtotal;
                    android.util.Log.d("HistoryAdapter", "  ✅ Added to total: " + subtotal);
                } else {
                    android.util.Log.d("HistoryAdapter", "  ❌ NOT added to total (not done/preparing)");
                }

                details.append(String.format("• %s\n", dishName));
                details.append(String.format("  %d x %s đ = %s đ\n\n",
                        quantity,
                        vnFormat.format(itemPrice),
                        vnFormat.format(subtotal)));
            }
            
            android.util.Log.d("HistoryAdapter", "=== END Processing. Total Amount: " + totalAmount + " ===");
        }

        details.append("─────────────────────\n");
        details.append(String.format("TỔNG CỘNG: %s đ", vnFormat.format(totalAmount)));

        builder.setMessage(details.toString());
        builder.setPositiveButton("Đóng", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    // Hàm format ngày tháng chuẩn
    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty())
            return "---";
        try {
            // Định dạng đầu vào từ Server (thường là ISO 8601)
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC")); // Server thường trả về giờ UTC

            Date date = isoFormat.parse(isoDate);

            // Định dạng đầu ra hiển thị (Giờ Việt Nam)
            SimpleDateFormat outFormat = new SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault());
            return outFormat.format(date);

        } catch (Exception e) {
            return isoDate; // Nếu lỗi thì hiện nguyên gốc
        }
    }
}