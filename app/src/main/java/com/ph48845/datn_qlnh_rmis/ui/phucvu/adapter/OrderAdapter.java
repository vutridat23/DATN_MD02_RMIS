package com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter;

import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * OrderAdapter: hiển thị Order.OrderItem với ảnh, tên, số lượng, giá, trạng thái.
 * - Gọi oi.normalize() trước khi bind để lấy imageUrl/name/price từ menuItemRaw nếu cần.
 * - Normalize đường dẫn ảnh relative bằng FALLBACK_BASE (thay thế phù hợp).
 * - Log URL và lỗi Glide để debug.
 */
public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.VH> {

    private static final String TAG = "OrderAdapter";
    private List<Order.OrderItem> items = new ArrayList<>();
    public interface OnItemClick { void onItemClick(Order.OrderItem item); }
    private OnItemClick listener;
    private final DecimalFormat priceFmt = new DecimalFormat("#,###");

    // Use LAN IP as fallback to match your server
    private static final String FALLBACK_BASE = "http://192.168.1.84:3000";

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
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Order.OrderItem oi = items.get(position);
        if (oi == null) return;

        // ensure normalize -> populate name/image/price from menuItemRaw if needed
        try { oi.normalize(); } catch (Exception e) { Log.w(TAG, "normalize failed: " + e.getMessage()); }

        // DEBUG: log object so you know what API returned
        Log.d(TAG, "Binding item pos=" + position + " -> name=\"" + oi.getMenuItemName() + "\" imageUrl=\"" + oi.getImageUrl() + "\" price=" + oi.getPrice() + " qty=" + oi.getQuantity());

        // Prefer menuItemName (snapshot) then name
        String displayName = oi.getMenuItemName();
        if (displayName == null || displayName.trim().isEmpty()) displayName = oi.getName();
        if (displayName == null || displayName.trim().isEmpty()) displayName = "(Không tên)";
        holder.tvName.setText(displayName);

        // Quantity and total price for line
        int qty = oi.getQuantity() <= 0 ? 1 : oi.getQuantity();
        double unitPrice = oi.getPrice();
        double total = unitPrice * qty;
        holder.tvQty.setText("Số lượng: " + qty);
        holder.tvPrice.setText(priceFmt.format(total) + " VND");

        // Prepare image URL: prefer imageUrl snapshot; normalize relative path
        String rawUrl = oi.getImageUrl();
        String imgUrl = normalizeImageUrl(rawUrl);

        if (imgUrl != null && !imgUrl.isEmpty()) {
            // Load with Glide and log on failure
            Glide.with(holder.ivThumb.getContext())
                    .load(imgUrl)
                    .centerCrop()
                    .placeholder(R.drawable.ic_menu_placeholder)
                    .error(R.drawable.ic_menu_placeholder)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.w(TAG, "Glide load failed. model=" + model + ", err=" + (e != null ? e.getMessage() : "null"));
                            return false; // allow Glide to set placeholder
                        }
                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    })
                    .into(holder.ivThumb);
        } else {
            // No URL -> placeholder
            holder.ivThumb.setImageResource(R.drawable.ic_menu_placeholder);
        }

        // Status badge text
        String s = oi.getStatus() != null ? oi.getStatus().toLowerCase() : "preparing";
        if (s.contains("done") || s.contains("xong") || s.contains("completed") || s.contains("served")) {
            holder.tvStatus.setText("Đã xong");
            holder.tvStatus.setBackgroundResource(R.drawable.badge_green_bg);
        } else if (s.contains("out") || s.contains("het") || s.contains("sold") || s.contains("unavailable")) {
            holder.tvStatus.setText("Đã hết");
            holder.tvStatus.setBackgroundResource(R.drawable.badge_red_bg);
        } else if (s.contains("cancel") || s.contains("huy")) {
            holder.tvStatus.setText("Đã huỷ");
            holder.tvStatus.setBackgroundResource(R.drawable.badge_red_bg);
        } else {
            holder.tvStatus.setText("Đang nấu");
            holder.tvStatus.setBackgroundResource(R.drawable.badge_yellow_bg);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(oi);
        });
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    private String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null) return null;
        imageUrl = imageUrl.trim();
        if (imageUrl.isEmpty()) return null;
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) return imageUrl;
        // relative path -> prefix fallback base
        if (!imageUrl.startsWith("/")) imageUrl = "/" + imageUrl;
        return FALLBACK_BASE + imageUrl;
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        TextView tvName, tvQty, tvPrice, tvStatus;
        VH(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.iv_item_thumb);
            tvName = itemView.findViewById(R.id.tv_item_name);
            tvQty = itemView.findViewById(R.id.tv_item_qty);
            tvPrice = itemView.findViewById(R.id.tv_item_price);
            tvStatus = itemView.findViewById(R.id.tv_item_status);
        }
    }
}