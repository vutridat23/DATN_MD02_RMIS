package com.ph48845.datn_qlnh_rmis.ui.table;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;


import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * OrdersAdapter: hiển thị danh sách Order (mỗi hàng là 1 order) kèm radio để chọn 1 order.
 *
 * Đặt ở package ui.table để TemporaryBillDialogFragment (và các class khác trong package này)
 * có thể dễ dàng sử dụng.
 */
public class OrdersAdapter extends RecyclerView.Adapter<OrdersAdapter.VH> {

    public interface OnOrderClickListener {
        void onOrderClick(Order order);
    }

    private final OnOrderClickListener listener;
    private final List<Order> items = new ArrayList<>();
    private String selectedOrderId;
    private final DecimalFormat priceFmt = new DecimalFormat("#,###");

    public OrdersAdapter(OnOrderClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Order> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public void setSelectedOrderId(String id) {
        selectedOrderId = id;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OrdersAdapter.VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the item_tam_tinh layout which contains tvOrderName, tvOrderInfo and radioOrder
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tam_tinh, parent, false);
        return new OrdersAdapter.VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull OrdersAdapter.VH holder, int position) {
        Order o = items.get(position);
        if (o == null) return;

        String displayName = o.getOrderId();
        if (displayName == null || displayName.trim().isEmpty()) displayName = o.getId();
        if (displayName == null || displayName.trim().isEmpty()) displayName = "Order " + (position + 1);
        holder.tvOrderName.setText(displayName);

        int count = 0;
        try { count = o.getItems() != null ? o.getItems().size() : 0; } catch (Exception ignored) {}
        String total = "";
        try { total = priceFmt.format(o.getTotalAmount()) + " VND"; } catch (Exception ignored) { total = ""; }

        holder.tvOrderInfo.setText("Món: " + count + " — Tổng: " + total);
        holder.radio.setChecked(o.getId() != null && o.getId().equals(selectedOrderId));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onOrderClick(o);
        });
        holder.radio.setOnClickListener(v -> {
            if (listener != null) listener.onOrderClick(o);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvOrderName, tvOrderInfo;
        RadioButton radio;
        VH(@NonNull View itemView) {
            super(itemView);
            tvOrderName = itemView.findViewById(R.id.tvOrderName);
            tvOrderInfo = itemView.findViewById(R.id.tvOrderInfo);
            radio = itemView.findViewById(R.id.radioOrder);
        }
    }
}