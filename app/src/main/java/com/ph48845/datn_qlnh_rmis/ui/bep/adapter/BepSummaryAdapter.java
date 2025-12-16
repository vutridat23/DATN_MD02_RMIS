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
import com.ph48845.datn_qlnh_rmis.ui.bep.SummaryEntry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter hiển thị tổng số lượng theo tên món (kèm ảnh).
 * Thêm nút "ĐÃ XONG" trên mỗi hàng; có callback khi bấm.
 */
public class BepSummaryAdapter extends RecyclerView.Adapter<BepSummaryAdapter.VH> {

    public interface OnMarkDoneClickListener {
        void onMarkDone(SummaryEntry entry);
    }

    private List<SummaryEntry> items = new ArrayList<>();
    private final OnMarkDoneClickListener listener;
    // track names currently being processed to disable button while in-flight
    private final Set<String> processingNames = new HashSet<>();

    public BepSummaryAdapter(List<SummaryEntry> items, OnMarkDoneClickListener listener) {
        if (items != null) this.items = items;
        this.listener = listener;
    }

    public void setItems(List<SummaryEntry> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setProcessing(String name, boolean processing) {
        if (name == null) return;
        if (processing) processingNames.add(name);
        else processingNames.remove(name);
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
        holder.tvSub.setText("");

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

        boolean processing = processingNames.contains(it.getName());
        holder.btnMarkDone.setEnabled(!processing);
        holder.btnMarkDone.setAlpha(processing ? 0.6f : 1f);

        holder.btnMarkDone.setOnClickListener(v -> {
            if (listener != null && !processing) {
                // immediately mark processing to give instant feedback
                setProcessing(it.getName(), true);
                listener.onMarkDone(it);
            }
        });
    }

    @Override
    public int getItemCount() { return items == null ? 0 : items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView iv;
        TextView tvName, tvQty, tvSub;
        Button btnMarkDone;
        VH(@NonNull View itemView) {
            super(itemView);
            iv = itemView.findViewById(R.id.iv_summary_image);
            tvName = itemView.findViewById(R.id.tv_summary_name);
            tvQty = itemView.findViewById(R.id.tv_summary_qty);
            tvSub = itemView.findViewById(R.id.tv_summary_sub);
            btnMarkDone = itemView.findViewById(R.id.btn_mark_done);
        }
    }
}