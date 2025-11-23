package com.ph48845.datn_qlnh_rmis.ui.bep.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
 * Changed: show confirmation dialog before calling listener.onChangeStatus(...)
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

        // Buttons: show confirm dialog then call listener with wrapper and desired new status
        holder.btnDanNhan.setOnClickListener(v -> {
            showConfirmDialog(holder, name, "pending", wrapper);
        });
        holder.btnDangLam.setOnClickListener(v -> {
            showConfirmDialog(holder, name, "preparing", wrapper);
        });
        holder.btnXongMon.setOnClickListener(v -> {
            showConfirmDialog(holder, name, "ready", wrapper);
        });
        holder.btnHetMon.setOnClickListener(v -> {
            showConfirmDialog(holder, name, "soldout", wrapper);
        });
    }

    private void showConfirmDialog(@NonNull VH holder, String itemName, String newStatus, ItemWithOrder wrapper) {
        String title = "Xác nhận";
        String displayStatus = humanizeStatus(newStatus);
        String message = "Bạn có chắc chắn muốn chuyển trạng thái món \"" + itemName + "\" sang \"" + displayStatus + "\"?";

        AlertDialog.Builder b = new AlertDialog.Builder(holder.itemView.getContext());
        b.setTitle(title);
        b.setMessage(message);
        b.setNegativeButton("Hủy", (dialog, which) -> dialog.dismiss());
        b.setPositiveButton("Xác nhận", (dialog, which) -> {
            if (listener != null) listener.onChangeStatus(wrapper, newStatus);
            dialog.dismiss();
        });
        b.show();
    }

    private String humanizeStatus(String status) {
        if (status == null) return "";
        switch (status) {
            case "pending": return "Đã nhận";
            case "preparing": return "Đang làm";
            case "ready": return "Xong";
            case "soldout": return "Hết";
            default: return status;
        }
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