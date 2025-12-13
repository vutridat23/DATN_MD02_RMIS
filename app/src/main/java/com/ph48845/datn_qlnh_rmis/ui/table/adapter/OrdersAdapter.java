package com.ph48845.datn_qlnh_rmis.ui.table.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.Order.OrderItem;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * OrdersAdapter: hiển thị danh sách Order (mỗi hàng là 1 order) kèm radio để chọn 1 order.
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

    public String getSelectedOrderId() {
        return selectedOrderId;
    }

    /**
     * Safe getter: trả về bản sao danh sách items để fragment có thể duyệt mà không truy cập trực tiếp.
     */
    public List<Order> getItems() {
        return new ArrayList<>(items);
    }

    /**
     * Helper: tìm Order theo id trong adapter (null nếu không tìm thấy)
     */
    public Order getOrderById(String id) {
        if (id == null) return null;
        for (Order o : items) {
            if (o == null) continue;
            String oid = o.getId();
            if (oid == null || oid.isEmpty()) oid = o.getOrderId();
            if (id.equals(oid)) return o;
        }
        return null;
    }

    @NonNull
    @Override
    public OrdersAdapter.VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order_radio, parent, false);
        return new OrdersAdapter.VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull OrdersAdapter.VH holder, int position) {
        Order o = items.get(position);
        if (o == null) return;

        String displayName = o.getOrderId();
        if (displayName == null || displayName.trim().isEmpty()) displayName = o.getId();
        if (displayName == null || displayName.trim().isEmpty()) displayName = "Order " + (position + 1);
        holder.tvOrderId.setText(displayName);

        int count = 0;
        try { count = o.getItems() != null ? o.getItems().size() : 0; } catch (Exception ignored) {}
        double total = 0.0;
        try {
            // prefer getTotalAmount(), fallback compute
            total = o.getTotalAmount();
            if (total <= 0.0) {
                List<OrderItem> its = o.getItems();
                if (its != null) {
                    for (OrderItem it : its) {
                        if (it == null) continue;
                        try { total += it.getPrice() * it.getQuantity(); } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
        String totalStr = priceFmt.format(total) + " VND";
        holder.tvOrderMeta.setText("Món: " + count + " — Tổng: " + totalStr);

        boolean checked = false;
        String oid = o.getId();
        if (oid == null || oid.isEmpty()) oid = o.getOrderId();
        if (selectedOrderId != null && oid != null) checked = selectedOrderId.equals(oid);
        holder.radio.setChecked(checked);

        holder.itemView.setOnClickListener(v -> {
            String idToSelect = o.getId();
            if (idToSelect == null || idToSelect.isEmpty()) idToSelect = o.getOrderId();
            selectedOrderId = idToSelect;
            notifyDataSetChanged();
            if (listener != null) listener.onOrderClick(o);
        });

        holder.radio.setOnClickListener(v -> {
            String idToSelect = o.getId();
            if (idToSelect == null || idToSelect.isEmpty()) idToSelect = o.getOrderId();
            selectedOrderId = idToSelect;
            notifyDataSetChanged();
            if (listener != null) listener.onOrderClick(o);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvOrderId, tvOrderMeta;
        RadioButton radio;
        VH(@NonNull View itemView) {
            super(itemView);
            tvOrderId = itemView.findViewById(R.id.tv_order_id);
            tvOrderMeta = itemView.findViewById(R.id.tv_order_meta);
            radio = itemView.findViewById(R.id.rb_order);
        }
    }
}