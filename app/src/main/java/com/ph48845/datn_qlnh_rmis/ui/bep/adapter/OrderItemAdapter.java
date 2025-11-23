package com.ph48845.datn_qlnh_rmis.ui.bep.adapter;

import android.util.Log;
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

public class OrderItemAdapter extends RecyclerView.Adapter<OrderItemAdapter.VH> {

    private static final String TAG = "OrderItemAdapter";

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

        // Table number: top-right
        if (order != null && order.getTableNumber() > 0) {
            holder.txtTableNumber.setText("Bàn " + order.getTableNumber());
            holder.txtTableNumber.setVisibility(View.VISIBLE);
        } else {
            holder.txtTableNumber.setText("");
            holder.txtTableNumber.setVisibility(View.GONE);
        }

        // Note: show under name in txtNote; hide if empty
        String note = oi.getNote() != null ? oi.getNote().trim() : "";
        Log.d(TAG, "bind pos=" + position + " item=" + name + " note=[" + note + "]" + " table=" + (order != null ? order.getTableNumber() : "null"));
        if (!note.isEmpty()) {
            holder.txtNote.setVisibility(View.VISIBLE);
            holder.txtNote.setText(note);
        } else {
            holder.txtNote.setVisibility(View.GONE);
            holder.txtNote.setText("");
        }

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

        holder.setButtonsEnabled(true);

        holder.btnDanNhan.setOnClickListener(v -> {
            if (listener != null) listener.onChangeStatus(wrapper, "pending");
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
        TextView txtTenMon, txtTableNumber, txtNote, txtQty, txtPrice, txtTrangThai;
        Button btnDanNhan, btnDangLam, btnXongMon, btnHetMon;

        VH(@NonNull View itemView) {
            super(itemView);
            imgThumb = itemView.findViewById(R.id.imgThumb);
            txtTenMon = itemView.findViewById(R.id.txtTenMon);
            txtTableNumber = itemView.findViewById(R.id.txtTableNumber);
            txtNote = itemView.findViewById(R.id.txtNote);
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