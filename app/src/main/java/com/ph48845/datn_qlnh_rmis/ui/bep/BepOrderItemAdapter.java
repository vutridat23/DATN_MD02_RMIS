package com.ph48845.datn_qlnh_rmis.ui.bep;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter cho danh sách order items trong BepOrderActivity (read-only).
 * Hiển thị tên món, số lượng, ghi chú và trạng thái.
 */
public class BepOrderItemAdapter extends RecyclerView.Adapter<BepOrderItemAdapter.OrderItemViewHolder> {

    private final Context context;
    private List<Order.OrderItem> items;

    public BepOrderItemAdapter(Context context, List<Order.OrderItem> items) {
        this.context = context;
        this.items = items != null ? items : new ArrayList<>();
    }

    @NonNull
    @Override
    public OrderItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_bep_order_item, parent, false);
        return new OrderItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderItemViewHolder holder, int position) {
        Order.OrderItem item = items.get(position);
        if (item == null) return;

        // Tên món
        String name = item.getMenuItemName();
        if (name == null || name.isEmpty()) {
            name = item.getName();
        }
        holder.tvItemName.setText(name != null && !name.isEmpty() ? name : "Món ăn");

        // Số lượng
        holder.tvQuantity.setText("SL: " + item.getQuantity());

        // Ghi chú
        String note = item.getNote();
        if (note != null && !note.isEmpty()) {
            holder.tvNote.setVisibility(View.VISIBLE);
            holder.tvNote.setText("Ghi chú: " + note);
        } else {
            holder.tvNote.setVisibility(View.GONE);
        }

        // Trạng thái
        String status = item.getStatus();
        if (status != null && !status.isEmpty()) {
            holder.tvStatus.setVisibility(View.VISIBLE);
            String statusText = formatStatus(status);
            holder.tvStatus.setText("Trạng thái: " + statusText);
            
            // Set màu theo trạng thái
            int statusColor = getStatusColor(status);
            holder.tvStatus.setTextColor(statusColor);
        } else {
            holder.tvStatus.setVisibility(View.VISIBLE);
            holder.tvStatus.setText("Trạng thái: Chờ chế biến");
            holder.tvStatus.setTextColor(Color.parseColor("#FF9800"));
        }
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    public void updateList(List<Order.OrderItem> newItems) {
        this.items = newItems != null ? new ArrayList<>(newItems) : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Format trạng thái thành text hiển thị
     */
    private String formatStatus(String status) {
        if (status == null) return "Chờ chế biến";
        String lower = status.toLowerCase();
        if (lower.contains("done") || lower.contains("ready")) return "Đã hoàn thành";
        if (lower.contains("preparing") || lower.contains("cooking")) return "Đang chế biến";
        if (lower.contains("received")) return "Đã nhận";
        if (lower.contains("pending")) return "Chờ chế biến";
        return status;
    }

    /**
     * Lấy màu theo trạng thái
     */
    private int getStatusColor(String status) {
        if (status == null) return Color.parseColor("#FF9800"); // Orange
        String lower = status.toLowerCase();
        if (lower.contains("done") || lower.contains("ready")) {
            return Color.parseColor("#4CAF50"); // Green
        }
        if (lower.contains("preparing") || lower.contains("cooking")) {
            return Color.parseColor("#2196F3"); // Blue
        }
        if (lower.contains("received")) {
            return Color.parseColor("#9C27B0"); // Purple
        }
        return Color.parseColor("#FF9800"); // Orange (pending)
    }

    /**
     * ViewHolder
     */
    public static class OrderItemViewHolder extends RecyclerView.ViewHolder {
        TextView tvItemName;
        TextView tvQuantity;
        TextView tvNote;
        TextView tvStatus;

        public OrderItemViewHolder(@NonNull View itemView) {
            super(itemView);
            tvItemName = itemView.findViewById(R.id.tv_bep_item_name);
            tvQuantity = itemView.findViewById(R.id.tv_bep_item_quantity);
            tvNote = itemView.findViewById(R.id.tv_bep_item_note);
            tvStatus = itemView.findViewById(R.id.tv_bep_item_status);
        }
    }
}
