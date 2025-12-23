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
        if (item.getFinalAmount() != null) {
            NumberFormat vnFormat = NumberFormat.getInstance(new Locale("vi", "VN"));
            String money = vnFormat.format(item.getFinalAmount()) + " đ";
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

        if (item.getItems() != null) {
            for (HistoryItem.OrderItemDetail orderItem : item.getItems()) {
                String dishName = orderItem.getDishName() != null ? orderItem.getDishName() : "Món ăn";
                int quantity = orderItem.getQuantity();
                double price = orderItem.getPrice();
                double subtotal = quantity * price;

                details.append(String.format("• %s\n", dishName));
                details.append(String.format("  %d x %s đ = %s đ\n\n",
                        quantity,
                        vnFormat.format(price),
                        vnFormat.format(subtotal)));
            }
        }

        details.append("─────────────────────\n");
        details.append(String.format("THANH TOÁN: %s đ",
                vnFormat.format(item.getFinalAmount() != null ? item.getFinalAmount() : 0)));


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