package com.ph48845.datn_qlnh_rmis.ui.table.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter hiển thị món trong 1 order, có checkbox.
 */
public class OrderItemCheckboxAdapter extends RecyclerView.Adapter<OrderItemCheckboxAdapter.VH> {

    public interface ItemCheckListener { void onItemChecked(String orderId, Order.OrderItem item, boolean checked); }

    private final List<Order.OrderItem> items = new ArrayList<>();
    private final Order parentOrder;
    private final ItemCheckListener listener;

    public OrderItemCheckboxAdapter(Order parentOrder, ItemCheckListener listener) {
        this.parentOrder = parentOrder;
        this.listener = listener;
    }

    public void submitList(List<Order.OrderItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OrderItemCheckboxAdapter.VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order_item_checkbox, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderItemCheckboxAdapter.VH holder, int position) {
        final Order.OrderItem oi = items.get(position);
        if (oi == null) return;
        String name = oi.getMenuItemName();
        if (name == null || name.isEmpty()) name = oi.getName();
        holder.tvName.setText(name + " x" + oi.getQuantity());
        holder.tvPrice.setText(String.valueOf((long)(oi.getPrice() * oi.getQuantity())) + " VND");

        holder.check.setOnCheckedChangeListener(null);
        holder.check.setChecked(false);
        holder.check.setOnCheckedChangeListener((buttonView, isChecked) -> {
            String oid = parentOrder.getId();
            if (oid == null || oid.isEmpty()) oid = parentOrder.getOrderId();
            if (listener != null) listener.onItemChecked(oid, oi, isChecked);
        });

        holder.itemView.setOnClickListener(v -> {
            boolean newState = !holder.check.isChecked();
            holder.check.setChecked(newState);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        CheckBox check;
        TextView tvName, tvPrice;
        VH(@NonNull View itemView) {
            super(itemView);
            check = itemView.findViewById(R.id.cb_item);
            tvName = itemView.findViewById(R.id.tv_item_name);
            tvPrice = itemView.findViewById(R.id.tv_item_price);
        }
    }
}