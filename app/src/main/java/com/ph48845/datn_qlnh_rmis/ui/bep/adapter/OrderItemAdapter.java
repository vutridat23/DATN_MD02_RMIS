package com.ph48845.datn_qlnh_rmis.ui.bep.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.R;
import java.util.List;

public class OrderItemAdapter extends RecyclerView.Adapter<OrderItemAdapter.OrderItemViewHolder> {
    public interface OnStatusChangeListener {
        void onStatusChange(Order order, Order.OrderItem item, String newStatus);
    }

    private List<Order.OrderItem> items;
    private OnStatusChangeListener listener;
    private Order currentOrder; // Truyền từ List<Order> nếu cần mapping

    public OrderItemAdapter(OnStatusChangeListener listener) {
        this.listener = listener;
    }

    public void setItems(List<Order.OrderItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    public OrderItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bep_order, parent, false);
        return new OrderItemViewHolder(v);
    }

    public void onBindViewHolder(@NonNull OrderItemViewHolder vh, int pos) {
        Order.OrderItem item = items.get(pos);
        // Hiển thị data
        vh.txtTenMon.setText(item.getMenuItem());
        vh.txtTrangThai.setText(item.getStatus());

        vh.btnDanNhan.setOnClickListener(view -> listener.onStatusChange(currentOrder, item, "pending"));
        vh.btnDangLam.setOnClickListener(view -> listener.onStatusChange(currentOrder, item, "preparing"));
        vh.btnXongMon.setOnClickListener(view -> listener.onStatusChange(currentOrder, item, "ready"));
        vh.btnHetMon.setOnClickListener(view -> listener.onStatusChange(currentOrder, item, "soldout"));
    }

    public int getItemCount() { return items != null ? items.size() : 0; }

    public static class OrderItemViewHolder extends RecyclerView.ViewHolder {
        public TextView txtTenMon, txtTrangThai;
        public Button btnDanNhan, btnDangLam, btnXongMon, btnHetMon;
        public OrderItemViewHolder(View v) {
            super(v);
            txtTenMon = v.findViewById(R.id.txtTenMon);
            txtTrangThai = v.findViewById(R.id.txtTrangThai);
            btnDanNhan = v.findViewById(R.id.btnDanNhan);
            btnDangLam = v.findViewById(R.id.btnDangLam);
            btnXongMon = v.findViewById(R.id.btnXongMon);
            btnHetMon = v.findViewById(R.id.btnHetMon);
        }
    }
}