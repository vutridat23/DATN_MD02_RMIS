package com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * MenuAdapter with Glide, URL normalization and debug logs.
 *
 * Added:
 *  - getItems() and findById(String) helpers so activities can lookup menu info without reflection.
 */
public class MenuAdapter extends RecyclerView.Adapter<MenuAdapter.MenuViewHolder> {

    private static final String TAG = "MenuAdapter";
    // Adjust FALLBACK_BASE to match your Retrofit base URL if running on local network
    private static final String FALLBACK_BASE = "http://192.168.1.84:3000";

    private List<MenuItem> items = new ArrayList<>();
    private final OnMenuClickListener listener;
    private final java.util.Map<String, Integer> qtyMap = new java.util.HashMap<>();
    private static final DecimalFormat PRICE_FMT = new DecimalFormat("#,###");

    public interface OnMenuClickListener {
        void onAddMenuItem(MenuItem menu);
        void onRemoveMenuItem(MenuItem menu);
    }

    public MenuAdapter(List<MenuItem> items, OnMenuClickListener listener) {
        if (items != null) {
            this.items = items;
            for (MenuItem m : items) {
                if (m != null && m.getId() != null) qtyMap.put(m.getId(), 0);
            }
        }
        this.listener = listener;
    }

    public List<MenuItem> getItems() {
        return items;
    }

    public MenuItem findById(String id) {
        if (id == null) return null;
        for (MenuItem m : items) {
            if (m == null) continue;
            if (id.equals(m.getId())) return m;
        }
        return null;
    }

    public void setItems(List<MenuItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        qtyMap.clear();
        for (MenuItem m : this.items) {
            if (m != null && m.getId() != null) qtyMap.put(m.getId(), 0);
        }
        notifyDataSetChanged();
    }

    public void setQty(String menuId, int qty) {
        if (menuId == null) return;
        qtyMap.put(menuId, qty);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MenuViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_menu, parent, false);
        return new MenuViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull MenuViewHolder holder, int position) {
        MenuItem m = items.get(position);
        if (m == null) {
            holder.tvName.setText("(Không tên)");
            holder.tvPrice.setText("");
            holder.ivThumb.setImageResource(R.drawable.ic_menu_placeholder);
            holder.tvQty.setText("0");
            return;
        }

        // Debug log
        Log.d(TAG, "Binding menu pos=" + position + " id=" + m.getId() + " name=\"" + m.getName() + "\" price=" + m.getPrice() + " imageUrl=\"" + m.getImageUrl() + "\"");

        // Name
        String name = m.getName();
        holder.tvName.setText(name == null || name.trim().isEmpty() ? "(Không tên)" : name);

        // Price formatted
        holder.tvPrice.setText(PRICE_FMT.format(m.getPrice()) + " VND");

        // Image handling: normalize relative URL and load with Glide
        String imageUrl = m.getImageUrl();
        if (imageUrl != null) imageUrl = imageUrl.trim();
        if (imageUrl == null || imageUrl.isEmpty()) {
            holder.ivThumb.setImageResource(R.drawable.ic_menu_placeholder);
        } else {
            if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {
                if (!imageUrl.startsWith("/")) imageUrl = "/" + imageUrl;
                imageUrl = FALLBACK_BASE + imageUrl;
                Log.d(TAG, "Normalized image URL -> " + imageUrl);
            }
            Glide.with(holder.ivThumb.getContext())
                    .load(imageUrl)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(R.drawable.ic_menu_placeholder)
                    .error(R.drawable.ic_menu_placeholder)
                    .listener(new RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(GlideException e, Object model, Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                            Log.w(TAG, "Glide load failed for model=" + model + " err=" + (e != null ? e.getMessage() : "null"));
                            return false; // allow placeholder
                        }

                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, Target<android.graphics.drawable.Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    })
                    .into(holder.ivThumb);
        }

        // Badge default
        holder.tvBadge.setText("Còn món");
        holder.tvBadge.setBackgroundResource(R.drawable.badge_green_bg);

        // Quantity UI
        Integer q = qtyMap.get(m.getId());
        if (q == null) q = 0;
        holder.tvQty.setText(String.valueOf(q));

        holder.btnAdd.setOnClickListener(v -> {
            int cur = qtyMap.getOrDefault(m.getId(), 0) + 1;
            qtyMap.put(m.getId(), cur);
            holder.tvQty.setText(String.valueOf(cur));
            if (listener != null) listener.onAddMenuItem(m);
        });

        holder.btnMinus.setOnClickListener(v -> {
            int cur = qtyMap.getOrDefault(m.getId(), 0);
            if (cur > 0) {
                cur--;
                qtyMap.put(m.getId(), cur);
                holder.tvQty.setText(String.valueOf(cur));
                if (listener != null) listener.onRemoveMenuItem(m);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static class MenuViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        TextView tvName, tvPrice, tvBadge, tvQty;
        ImageView btnAdd, btnMinus;

        public MenuViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.iv_menu_thumb);
            tvName = itemView.findViewById(R.id.tv_menu_name);
            tvPrice = itemView.findViewById(R.id.tv_menu_price);
            tvBadge = itemView.findViewById(R.id.tv_badge);
            tvQty = itemView.findViewById(R.id.tv_qty);
            btnAdd = itemView.findViewById(R.id.btn_add_menu);
            btnMinus = itemView.findViewById(R.id.btn_minus_menu);
        }
    }
}