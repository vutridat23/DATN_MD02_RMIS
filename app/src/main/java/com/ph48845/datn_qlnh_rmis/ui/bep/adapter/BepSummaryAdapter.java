package com.ph48845.datn_qlnh_rmis.ui.bep.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.ui.bep.SummaryEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter hiển thị tổng số lượng theo tên món (kèm ảnh).
 */
public class BepSummaryAdapter extends RecyclerView.Adapter<BepSummaryAdapter.VH> {

    private List<SummaryEntry> items = new ArrayList<>();

    public BepSummaryAdapter(List<SummaryEntry> items) {
        if (items != null) this.items = items;
    }

    public void setItems(List<SummaryEntry> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BepSummaryAdapter.VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bep_summary_row, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull BepSummaryAdapter.VH holder, int position) {
        SummaryEntry it = items.get(position);
        holder.tvName.setText(it.getName());
        holder.tvQty.setText(String.valueOf(it.getQty()));
        holder.tvSub.setText(""); // optional, could show extra info
        String img = it.getImageUrl();
        if (img != null && !img.trim().isEmpty()) {
            Glide.with(holder.iv.getContext())
                    .load(img)
                    .centerCrop()
                    .placeholder(R.drawable.ic_menu_placeholder)
                    .error(R.drawable.ic_menu_placeholder)
                    .into(holder.iv);
        } else {
            holder.iv.setImageResource(R.drawable.ic_menu_placeholder);
        }
    }

    @Override
    public int getItemCount() { return items == null ? 0 : items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView iv;
        TextView tvName, tvQty, tvSub;
        VH(@NonNull View itemView) {
            super(itemView);
            iv = itemView.findViewById(R.id.iv_summary_image);
            tvName = itemView.findViewById(R.id.tv_summary_name);
            tvQty = itemView.findViewById(R.id.tv_summary_qty);
            tvSub = itemView.findViewById(R.id.tv_summary_sub);
        }
    }
}