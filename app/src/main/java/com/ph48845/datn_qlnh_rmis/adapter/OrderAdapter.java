package com.ph48845.datn_qlnh_rmis.adapter;



import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter hiển thị các OrderItem (món đã order) với badge trạng thái.
 */
public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.VH> {

    private List<Order.OrderItem> items = new ArrayList<>();

    public interface OnItemClick {
        void onItemClick(Order.OrderItem item);
    }

    private OnItemClick listener;

    public OrderAdapter(List<Order.OrderItem> items, OnItemClick listener) {
        this.items = items != null ? items : new ArrayList<>();
        this.listener = listener;
    }

    public void setItems(List<Order.OrderItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ordered, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Order.OrderItem oi = items.get(position);
        holder.tvName.setText(oi.getName() != null ? oi.getName() : "");
        holder.tvQtyPrice.setText("Số lượng: " + oi.getQuantity() + "  •  " + String.format("%,.0f VND", oi.getPrice()));
        // TODO: use Glide/Picasso if image url available - use placeholder for now
        holder.ivThumb.setImageResource(R.drawable.ic_menu_placeholder);

        // badge based on oi.getStatus() if exists; else default "Đang nấu"
        String s = oi.getStatus() != null ? oi.getStatus().toLowerCase() : "preparing";
        if (s.contains("done") || s.contains("xong") || s.contains("completed")) {
            holder.tvBadge.setText("Đã xong");
            holder.tvBadge.setBackgroundResource(R.drawable.badge_green_bg);
        } else if (s.contains("out") || s.contains("het") || s.contains("sold") || s.contains("unavailable")) {
            holder.tvBadge.setText("Đã hết");
            holder.tvBadge.setBackgroundResource(R.drawable.badge_red_bg);
        } else {
            holder.tvBadge.setText("Đang nấu");
            holder.tvBadge.setBackgroundResource(R.drawable.badge_yellow_bg);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(oi);
        });
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        TextView tvName, tvQtyPrice, tvBadge;
        VH(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.iv_thumb);
            tvName = itemView.findViewById(R.id.tv_name);
            tvQtyPrice = itemView.findViewById(R.id.tv_qty_price);
            tvBadge = itemView.findViewById(R.id.tv_status_badge);
        }
    }
}