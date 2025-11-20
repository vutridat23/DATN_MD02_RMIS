package com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter;



import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


import com.ph48845.datn_qlnh_rmis.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter cho danh sách món trong giỏ hàng.
 */
public class CartAdapter extends RecyclerView.Adapter<CartAdapter.CartViewHolder> {

    public static class CartModel {
        public final String menuId;
        public final String name;
        public final double price;
        public int qty;
        public CartModel(String menuId, String name, double price, int qty) {
            this.menuId = menuId; this.name = name; this.price = price; this.qty = qty;
        }
    }

    public interface OnCartChangeListener {
        void onQtyChanged(String menuId, int newQty);
    }

    private List<CartModel> items = new ArrayList<>();
    private final OnCartChangeListener listener;

    public CartAdapter(List<CartModel> items, OnCartChangeListener listener) {
        if (items != null) this.items = items;
        this.listener = listener;
    }

    public void setItems(List<CartModel> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cart_row, parent, false);
        return new CartViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull CartViewHolder holder, int position) {
        CartModel m = items.get(position);
        holder.tvName.setText(m.name);
        holder.tvQty.setText(String.valueOf(m.qty));
        holder.tvPrice.setText(String.format("%.0f đ", m.price * m.qty));

        holder.btnPlus.setOnClickListener(v -> {
            m.qty++;
            holder.tvQty.setText(String.valueOf(m.qty));
            holder.tvPrice.setText(String.format("%.0f đ", m.price * m.qty));
            if (listener != null) listener.onQtyChanged(m.menuId, m.qty);
        });

        holder.btnMinus.setOnClickListener(v -> {
            if (m.qty > 1) {
                m.qty--;
                holder.tvQty.setText(String.valueOf(m.qty));
                holder.tvPrice.setText(String.format("%.0f đ", m.price * m.qty));
                if (listener != null) listener.onQtyChanged(m.menuId, m.qty);
            } else {
                // qty -> 0 remove
                if (listener != null) listener.onQtyChanged(m.menuId, 0);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    static class CartViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvQty, tvPrice;
        ImageButton btnPlus, btnMinus;
        CartViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_cart_name);
            tvQty = itemView.findViewById(R.id.tv_cart_qty);
            tvPrice = itemView.findViewById(R.id.tv_cart_price);
            btnPlus = itemView.findViewById(R.id.btn_cart_plus);
            btnMinus = itemView.findViewById(R.id.btn_cart_minus);
        }
    }
}