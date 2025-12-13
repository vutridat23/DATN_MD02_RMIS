package com.ph48845.datn_qlnh_rmis.ui.bep.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapter hiển thị các bàn trong BepTableFragment.
 */
public class BepAdapter extends RecyclerView.Adapter<BepAdapter.VH> {

    private final Context ctx;
    private List<TableItem> tables = new ArrayList<>();
    private Map<Integer, Integer> remainingCountMap;
    private Map<Integer, Long> earliestTimeMap;
    private long overdueThresholdMs = 10 * 60 * 1000L;

    public interface OnTableClickListener {
        void onTableClick(TableItem table);
    }

    private OnTableClickListener listener;

    public BepAdapter(Context ctx, OnTableClickListener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
    }

    public void setOverdueThresholdMs(long ms) { this.overdueThresholdMs = ms; }

    public void updateList(List<TableItem> list, Map<Integer,Integer> remaining, Map<Integer,Long> earliestTimes) {
        this.tables = list != null ? list : new ArrayList<>();
        this.remainingCountMap = remaining;
        this.earliestTimeMap = earliestTimes;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BepAdapter.VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_table, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull BepAdapter.VH holder, int position) {
        TableItem t = tables.get(position);
        if (t == null) return;
        holder.tvTableNumber.setText("Bàn " + t.getTableNumber());

        Integer remain = remainingCountMap != null ? remainingCountMap.get(t.getTableNumber()) : null;
        if (remain == null) remain = 0;
        holder.tvCapacity.setText(remain > 0 ? ("Còn " + remain + " món") : "");

        holder.tvStatus.setText(t.getStatusDisplay());

        if (remain == 0) {
            holder.cardView.setCardElevation(2f);
            holder.viewStatusStrip.setBackgroundColor(Color.parseColor("#E0E0E0"));
            holder.tvTableNumber.setTextColor(Color.parseColor("#757575"));
            holder.tvStatus.setTextColor(Color.parseColor("#9E9E9E"));
        } else {
            boolean overdue = false;
            if (earliestTimeMap != null && earliestTimeMap.containsKey(t.getTableNumber())) {
                long ts = earliestTimeMap.get(t.getTableNumber());
                long now = System.currentTimeMillis();
                if (ts > 0 && now - ts > overdueThresholdMs) overdue = true;
            }

            int stripColor = overdue ? Color.parseColor("#FBC02D") : Color.parseColor("#4CAF50");
            holder.viewStatusStrip.setBackgroundColor(stripColor);
            holder.tvTableNumber.setTextColor(Color.parseColor("#AA0000"));
            holder.tvStatus.setTextColor(Color.parseColor("#333333"));
            holder.cardView.setCardElevation(8f);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onTableClick(t);
        });
    }

    @Override
    public int getItemCount() { return tables == null ? 0 : tables.size(); }

    static class VH extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView tvTableNumber, tvCapacity, tvStatus;
        View viewStatusStrip;
        VH(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_table);
            tvTableNumber = itemView.findViewById(R.id.tv_table_name);
            tvCapacity = itemView.findViewById(R.id.tv_table_capacity);
            tvStatus = itemView.findViewById(R.id.tv_table_status);
            viewStatusStrip = itemView.findViewById(R.id.view_status_strip);
        }
    }
}