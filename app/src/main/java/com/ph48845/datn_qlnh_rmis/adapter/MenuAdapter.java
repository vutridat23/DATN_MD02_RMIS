package com.ph48845.datn_qlnh_rmis.adapter;



import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter hiển thị danh sách món theo layout item_menu_list.xml
 * Listener cung cấp onAddMenuItem/onRemoveMenuItem (tăng/giảm qty)
 */
public class MenuAdapter extends RecyclerView.Adapter<MenuAdapter.MenuViewHolder> {

    private List<MenuItem> items = new ArrayList<>();
    private final OnMenuClickListener listener;
    private final java.util.Map<String, Integer> qtyMap = new java.util.HashMap<>();

    public interface OnMenuClickListener {
        void onAddMenuItem(MenuItem menu);
        void onRemoveMenuItem(MenuItem menu);
    }

    public MenuAdapter(List<MenuItem> items, OnMenuClickListener listener) {
        if (items != null) {
            this.items = items;
            for (MenuItem m : items) {
                if (m != null && m.getId() != null) {
                    qtyMap.put(m.getId(), 0);
                }
            }
        }
        this.listener = listener;
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

    /**
     * Return a copy/reference to items (read-only usage intended).
     */
    public List<MenuItem> getItems() {
        return items;
    }

    /**
     * Find MenuItem by id or return null.
     */
    public MenuItem findById(String id) {
        if (id == null || items == null) return null;
        for (MenuItem m : items) {
            if (m != null && id.equals(m.getId())) return m;
        }
        return null;
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
        holder.tvName.setText(m.getName() != null ? m.getName() : "");
        holder.tvPrice.setText(String.format("%,.2f", m.getPrice()));

        // Thumbnail: placeholder (replace with Glide if imageUrl present)
        holder.ivThumb.setImageResource(R.drawable.ic_menu_placeholder);

        // Availability badge: default "Còn món". If your MenuItem has stock field adapt here.
        holder.tvBadge.setText("Còn món");
        holder.tvBadge.setBackgroundResource(R.drawable.badge_green_bg);

        // Quantity from internal map (adapter-side)
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
        Button btnAdd, btnMinus;

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