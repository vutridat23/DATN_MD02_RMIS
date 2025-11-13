package com.ph48845.datn_qlnh_rmis.ui.bep.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;

import java.util.List;

public class OrderItemAdapter extends RecyclerView.Adapter<OrderItemAdapter.OrderItemViewHolder> {
    private List<Order.OrderItem> itemList;
    private final OnItemStatusChangeListener onStatusChangeListener;

    public interface OnItemStatusChangeListener {
        void onStatusChange(Order.OrderItem item, String newStatus);
    }

    public OrderItemAdapter(List<Order.OrderItem> itemList, OnItemStatusChangeListener listener) {
        this.itemList = itemList;
        this.onStatusChangeListener = listener;
    }

    public void setData(List<Order.OrderItem> items) {
        this.itemList = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OrderItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bep_order, parent, false);
        return new OrderItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderItemViewHolder holder, int position) {
        Order.OrderItem item = itemList.get(position);
        holder.txtName.setText(item.getMenuItemName());
        holder.txtStatus.setText(getStatusLabel(item.getStatus()));

        holder.btnDanNhan.setText("Đã nhận");
        holder.btnDangLam.setText("Đang làm");
        holder.btnXongMon.setText("Xong món");
        holder.btnHetMon.setText("Hết món");

        holder.btnDanNhan.setOnClickListener(v -> onStatusChangeListener.onStatusChange(item, "PREPARING"));
        holder.btnDangLam.setOnClickListener(v -> onStatusChangeListener.onStatusChange(item, "PREPARING"));
        holder.btnXongMon.setOnClickListener(v -> onStatusChangeListener.onStatusChange(item, "READY"));
        holder.btnHetMon.setOnClickListener(v -> onStatusChangeListener.onStatusChange(item, "SOLD_OUT"));
    }

    @Override
    public int getItemCount() {
        return itemList != null ? itemList.size() : 0;
    }

    static class OrderItemViewHolder extends RecyclerView.ViewHolder {
        TextView txtName, txtStatus;
        Button btnDanNhan, btnDangLam, btnXongMon, btnHetMon;

        public OrderItemViewHolder(@NonNull View itemView) {
            super(itemView);
            txtName = itemView.findViewById(R.id.txtTenMon);
            txtStatus = itemView.findViewById(R.id.txtTrangThai);
            btnDanNhan = itemView.findViewById(R.id.btnDanNhan);
            btnDangLam = itemView.findViewById(R.id.btnDangLam);
            btnXongMon = itemView.findViewById(R.id.btnXongMon);
            btnHetMon = itemView.findViewById(R.id.btnHetMon);
        }
    }

    private String getStatusLabel(String status) {
        switch (status) {
            case "PENDING":
                return "Đã nhận";
            case "PREPARING":
                return "Đang làm";
            case "READY":
                return "Xong món";
            case "SOLD_OUT":
                return "Hết món";
            default:
                return status;
        }
    }
}