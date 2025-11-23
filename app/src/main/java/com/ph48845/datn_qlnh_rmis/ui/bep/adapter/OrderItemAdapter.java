package com.ph48845.datn_qlnh_rmis.ui.bep.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.ph48845.datn_qlnh_rmis.R;

import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.ui.bep.ItemWithOrder;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter that receives List<ItemWithOrder> so each item has parent Order context.
 * Changed: do NOT disable buttons on click to avoid "dimmed" UX.
 */
public class OrderItemAdapter extends RecyclerView.Adapter<OrderItemAdapter.VH> {

    public interface OnActionListener {
        void onChangeStatus(ItemWithOrder wrapper, String newStatus);
    }

    private final List<ItemWithOrder> items = new ArrayList<>();
    private final OnActionListener listener;
    private final DecimalFormat priceFmt = new DecimalFormat("#,###");

    public OrderItemAdapter(OnActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<ItemWithOrder> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bep_order, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ItemWithOrder wrapper = items.get(position);
        Order order = wrapper.getOrder();
        Order.OrderItem oi = wrapper.getItem();

        String name = oi.getMenuItemName() != null && !oi.getMenuItemName().isEmpty() ? oi.getMenuItemName() : oi.getName();
        holder.txtTenMon.setText(name);
        holder.txtTableInfo.setText(order != null ? "Bàn " + order.getTableNumber() : "");
        holder.txtQty.setText("Số lượng: " + oi.getQuantity());
        holder.txtTrangThai.setText("Trạng thái: " + (oi.getStatus() == null ? "" : oi.getStatus()));

        String img = oi.getImageUrl();
        if (img != null && !img.isEmpty()) {
            Glide.with(holder.imgThumb.getContext())
                    .load(img)
                    .centerCrop()
                    .placeholder(R.drawable.ic_menu_placeholder)
                    .error(R.drawable.ic_menu_placeholder)
                    .into(holder.imgThumb);
        } else {
            holder.imgThumb.setImageResource(R.drawable.ic_menu_placeholder);
        }

        // Ensure buttons are enabled when binding
        holder.setButtonsEnabled(true);

        // Buttons: call listener with wrapper and desired new status
        // Note: removed disabling lines to avoid dim/blocked state
        holder.btnDanNhan.setOnClickListener(v -> {
            if (listener != null) listener.onChangeStatus(wrapper, "pending"); // <-- GỬI "pending" thay vì "received"
        });
        holder.btnDangLam.setOnClickListener(v -> {
            if (listener != null) listener.onChangeStatus(wrapper, "preparing");
        });
        holder.btnXongMon.setOnClickListener(v -> {
            if (listener != null) listener.onChangeStatus(wrapper, "ready");
        });
        holder.btnHetMon.setOnClickListener(v -> {
            if (listener != null) listener.onChangeStatus(wrapper, "soldout");
        });
    }



    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imgThumb;
        TextView txtTenMon, txtTableInfo, txtQty, txtPrice, txtTrangThai;
        Button btnDanNhan, btnDangLam, btnXongMon, btnHetMon;

        VH(@NonNull View itemView) {
            super(itemView);
            imgThumb = itemView.findViewById(R.id.imgThumb);
            txtTenMon = itemView.findViewById(R.id.txtTenMon);
            txtTableInfo = itemView.findViewById(R.id.txtTableInfo);
            txtQty = itemView.findViewById(R.id.txtQty);
            txtPrice = itemView.findViewById(R.id.txtPrice);
            txtTrangThai = itemView.findViewById(R.id.txtTrangThai);
            btnDanNhan = itemView.findViewById(R.id.btnDanNhan);
            btnDangLam = itemView.findViewById(R.id.btnDangLam);
            btnXongMon = itemView.findViewById(R.id.btnXongMon);
            btnHetMon = itemView.findViewById(R.id.btnHetMon);
        }

        void setButtonsEnabled(boolean enabled) {
            btnDanNhan.setEnabled(enabled);
            btnDangLam.setEnabled(enabled);
            btnXongMon.setEnabled(enabled);
            btnHetMon.setEnabled(enabled);
            float alpha = enabled ? 1f : 0.5f;
            btnDanNhan.setAlpha(alpha);
            btnDangLam.setAlpha(alpha);
            btnXongMon.setAlpha(alpha);
            btnHetMon.setAlpha(alpha);
        }
    }
}