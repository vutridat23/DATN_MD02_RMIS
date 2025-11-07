package com.ph48845.datn_qlnh_rmis.ui.thungan.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;

import java.util.List;

public class ThuNganAdapter extends RecyclerView.Adapter<ThuNganAdapter.ViewHolder> {

    private List<Order.OrderItem> orderItems;

    public ThuNganAdapter(List<Order.OrderItem> orderItems) {
        this.orderItems = orderItems;
    }

    public void updateData(List<Order.OrderItem> newList) {
        this.orderItems = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_hoa_don, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Order.OrderItem item = orderItems.get(position);
        holder.txtName.setText(item.getMenuItemName());
        holder.txtQty.setText("x" + item.getQuantity());
        holder.txtPrice.setText(String.format("%.0fđ", item.getPrice() * item.getQuantity()));
    }

    @Override
    public int getItemCount() {
        return orderItems != null ? orderItems.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView txtName, txtQty, txtPrice;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtName = itemView.findViewById(R.id.txtName);
            txtQty = itemView.findViewById(R.id.txtQty);
            txtPrice = itemView.findViewById(R.id.txtPrice);
        }
    }
}
