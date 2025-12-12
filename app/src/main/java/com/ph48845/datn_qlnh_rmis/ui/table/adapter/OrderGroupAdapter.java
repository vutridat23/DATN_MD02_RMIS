package com.ph48845.datn_qlnh_rmis.ui.table.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter hiển thị danh sách order (group). Mỗi order có nested RecyclerView của món với checkbox.
 */
public class OrderGroupAdapter extends RecyclerView.Adapter<OrderGroupAdapter.VH> {

    public interface SelectionListener { void onSelectionChanged(int selectedCount); }

    private final List<Order> orders = new ArrayList<>();
    private final Map<String, List<Order.OrderItem>> selectedByOrder = new HashMap<>();
    private final SelectionListener listener;

    public OrderGroupAdapter(SelectionListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Order> list) {
        orders.clear();
        if (list != null) orders.addAll(list);
        selectedByOrder.clear();
        notifyDataSetChanged();
    }

    public Order getOrderById(String id) {
        if (id == null) return null;
        for (Order o : orders) {
            if (o == null) continue;
            String oid = o.getId();
            if (oid == null || oid.isEmpty()) oid = o.getOrderId();
            if (id.equals(oid)) return o;
        }
        return null;
    }

    public Map<String, List<Order.OrderItem>> getSelectedItemsGroupedByOrderId() {
        // return copy
        Map<String, List<Order.OrderItem>> copy = new HashMap<>();
        for (Map.Entry<String, List<Order.OrderItem>> e : selectedByOrder.entrySet()) {
            copy.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return copy;
    }

    private void notifySelectionChanged() {
        int total = 0;
        for (List<Order.OrderItem> l : selectedByOrder.values()) total += l.size();
        if (listener != null) listener.onSelectionChanged(total);
    }

    @NonNull
    @Override
    public OrderGroupAdapter.VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order_group, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderGroupAdapter.VH holder, int position) {
        Order o = orders.get(position);
        if (o == null) return;
        String id = o.getId();
        if (id == null || id.isEmpty()) id = o.getOrderId();
        holder.tvOrderId.setText(id != null ? id : ("Order " + (position+1)));
        int count = o.getItems() != null ? o.getItems().size() : 0;
        holder.tvMeta.setText("Món: " + count + " — Tổng: " + (long)o.getTotalAmount() + " VND");

        // nested adapter
        OrderItemCheckboxAdapter itemAdapter = new OrderItemCheckboxAdapter(o, (orderId, item, checked) -> {
            List<Order.OrderItem> sel = selectedByOrder.get(orderId);
            if (checked) {
                if (sel == null) { sel = new ArrayList<>(); selectedByOrder.put(orderId, sel); }
                sel.add(item);
            } else {
                if (sel != null) {
                    // remove by identity
                    for (int i = 0; i < sel.size(); i++) {
                        Order.OrderItem si = sel.get(i);
                        if (si == item || (si.getId() != null && si.getId().equals(item.getId()))) { sel.remove(i); break; }
                    }
                    if (sel.isEmpty()) selectedByOrder.remove(orderId);
                }
            }
            notifySelectionChanged();
        });
        holder.rvItems.setLayoutManager(new LinearLayoutManager(holder.rvItems.getContext()));
        holder.rvItems.setAdapter(itemAdapter);
        itemAdapter.submitList(o.getItems() != null ? o.getItems() : new ArrayList<>());
    }

    @Override
    public int getItemCount() { return orders.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvOrderId, tvMeta;
        RecyclerView rvItems;
        VH(@NonNull View itemView) {
            super(itemView);
            tvOrderId = itemView.findViewById(R.id.tv_group_order_id);
            tvMeta = itemView.findViewById(R.id.tv_group_order_meta);
            rvItems = itemView.findViewById(R.id.rv_group_items);
        }
    }
}