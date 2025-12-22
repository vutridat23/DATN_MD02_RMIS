package com.ph48845.datn_qlnh_rmis.ui.bep.adapter;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BepAdapter extends RecyclerView.Adapter<BepAdapter.VH> {

    private final Context ctx;
    private List<TableItem> tables = new ArrayList<>();
    private Map<Integer, Integer> remainingCountMap;
    private Map<Integer, Long> earliestTimeMap;

    // NEW
    private Set<Integer> attentionTables = new HashSet<>();
    private Set<Integer> mutedTables = new HashSet<>();

    public interface OnTableClickListener {
        void onTableClick(TableItem table);
    }

    private final OnTableClickListener listener;

    public BepAdapter(Context ctx, OnTableClickListener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
    }

    public void updateList(List<TableItem> list,
                           Map<Integer, Integer> remaining,
                           Map<Integer, Long> earliestTimes,
                           Set<Integer> attentionTables) {
        this.tables = list != null ? list : new ArrayList<>();
        this.remainingCountMap = remaining;
        this.earliestTimeMap = earliestTimes;
        this.attentionTables = attentionTables != null ? attentionTables : new HashSet<>();
        notifyDataSetChanged();
    }

    public void setAttentionTables(Set<Integer> attentionTables) {
        this.attentionTables = attentionTables != null ? attentionTables : new HashSet<>();
        notifyDataSetChanged();
    }

    // NEW: allow activity/fragment to mute blink for some tables (keeps ordering)
    public void setMutedTables(Set<Integer> mutedTables) {
        this.mutedTables = mutedTables != null ? mutedTables : new HashSet<>();
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

        int tn = t.getTableNumber();

        holder.tvTableNumber.setText("Bàn " + tn);

        Integer remain = remainingCountMap != null ? remainingCountMap.get(tn) : null;
        if (remain == null) remain = 0;
        holder.tvCapacity.setText(remain > 0 ? ("Còn " + remain + " món") : "");
        holder.tvStatus.setText(t.getStatusDisplay());

        // stop old animation because RecyclerView reuses views
        holder.stopBlink();

        boolean isAttention = attentionTables != null && attentionTables.contains(tn);
        boolean isMuted = mutedTables != null && mutedTables.contains(tn);

        if (isAttention && !isMuted) {
            holder.viewStatusStrip.setBackgroundColor(Color.parseColor("#FB8C00"));
            holder.startBlink(); // blink nhẹ
        } else {
            // default after "seen" or not attention
            holder.viewStatusStrip.setBackgroundColor(Color.parseColor("#AA0000"));
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

        private ObjectAnimator blinkAnim;

        VH(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_table);
            tvTableNumber = itemView.findViewById(R.id.tv_table_name);
            tvCapacity = itemView.findViewById(R.id.tv_table_capacity);
            tvStatus = itemView.findViewById(R.id.tv_table_status);
            viewStatusStrip = itemView.findViewById(R.id.view_status_strip);
        }

        void startBlink() {
            stopBlink();
            blinkAnim = ObjectAnimator.ofFloat(itemView, "alpha", 1f, 0.65f, 1f);
            blinkAnim.setDuration(900);
            blinkAnim.setRepeatCount(ValueAnimator.INFINITE);
            blinkAnim.setRepeatMode(ValueAnimator.RESTART);
            blinkAnim.start();
        }

        void stopBlink() {
            if (blinkAnim != null) {
                try { blinkAnim.cancel(); } catch (Exception ignored) {}
                blinkAnim = null;
            }
            itemView.setAlpha(1f);
        }
    }
}