package com.ph48845.datn_qlnh_rmis.ui.thungan.adapter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ThuNganAdapter extends RecyclerView.Adapter<ThuNganAdapter.TableViewHolder> {

    private final Context context;
    private List<TableItem> tableList;
    private OnTableClickListener listener;

    private boolean showServingStatus = true;
    private boolean showDaCoKhach = true;

    // --- Setter ---
    public void setShowServingStatus(boolean showServingStatus) {
        this.showServingStatus = showServingStatus;
    }

    public void setShowDaCoKhach(boolean showDaCoKhach) {
        this.showDaCoKhach = showDaCoKhach;
    }

    // --- Interface ---
    public interface OnTableClickListener {
        void onTableClick(TableItem table);
    }

    // --- Constructor ---
    public ThuNganAdapter(Context context, List<TableItem> tableList, OnTableClickListener listener) {
        this.context = context;
        this.tableList = tableList != null ? tableList : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public TableViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Sử dụng layout mới đã fix lỗi
        View view = LayoutInflater.from(context).inflate(R.layout.item_table_active, parent, false);
        return new TableViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TableViewHolder holder, int position) {
        TableItem table = tableList.get(position);
        if (table == null) return;

        holder.tvTableNumber.setText("Bàn " + table.getTableNumber());
        holder.tvTableNumber.setTextColor(Color.parseColor("#AA0000"));
        holder.cardView.setCardBackgroundColor(Color.WHITE);

        ServingStatus servingStatus = getServingStatus(table);
        boolean isFullServing = tableFullServingMap.getOrDefault(table.getTableNumber(), false);

        int newColor;
        float newElevation;

        // ===============================
        // RESET animator cũ (QUAN TRỌNG)
        // ===============================
        if (holder.isBlinking) {
            if (holder.fadeAnimator != null) {
                holder.fadeAnimator.cancel();
                holder.fadeAnimator = null;
            }
            holder.isBlinking = false;
        }

        // ===============================
        // 1️⃣ FULL MÓN → XANH DƯƠNG
        // ===============================
        if (isFullServing) {
            newColor = Color.parseColor("#2196F3"); // xanh dương
            newElevation = table.getViewState() == TableItem.ViewState.UNSEEN ? 8f : 4f;
            holder.viewStatusStrip.setBackgroundColor(newColor);
        }

        // ===============================
        // 2️⃣ ORDER MỚI hoặc CHƯA XEM → XANH LÁ NHÁY
        // ===============================
        else if (table.isNewOccupied() || table.getViewState() == TableItem.ViewState.UNSEEN) {
            newColor = Color.parseColor("#2e7d32"); // xanh lá
            newElevation = 8f;

            holder.isBlinking = true;

            holder.fadeAnimator = ValueAnimator.ofFloat(0.3f, 1f);
            holder.fadeAnimator.setDuration(1000);
            holder.fadeAnimator.setRepeatMode(ValueAnimator.REVERSE);
            holder.fadeAnimator.setRepeatCount(ValueAnimator.INFINITE);
            holder.fadeAnimator.addUpdateListener(animation -> {
                float alpha = (float) animation.getAnimatedValue();
                int r = Color.red(newColor);
                int g = Color.green(newColor);
                int b = Color.blue(newColor);
                holder.viewStatusStrip.setBackgroundColor(
                        Color.argb((int) (alpha * 255), r, g, b)
                );
            });
            holder.fadeAnimator.start();
        }

        // ===============================
        // 3️⃣ ĐÃ BẤM → ĐỎ
        // ===============================
        else {
            newColor = Color.parseColor("#AA0000"); // đỏ
            newElevation = 4f;
            holder.viewStatusStrip.setBackgroundColor(newColor);
        }

        // Elevation
        holder.cardView.setCardElevation(newElevation);

        // Serving status text
        if (showServingStatus) {
            holder.tvServingStatus.setText(servingStatus.text);
            holder.tvServingStatus.setVisibility(View.VISIBLE);
            holder.tvServingStatus.setTextColor(Color.parseColor("#757575"));
        } else {
            holder.tvServingStatus.setVisibility(View.GONE);
        }

        // ===============================
        // CLICK → ĐỎ + TẮT ORDER MỚI
        // ===============================
        holder.itemView.setOnClickListener(v -> {
            table.setViewState(TableItem.ViewState.SEEN);
            table.setNewOccupied(false); // 🔥 QUAN TRỌNG
            notifyItemChanged(holder.getAdapterPosition());
            if (listener != null) listener.onTableClick(table);
        });
    }


    private final Map<Integer, Boolean> tableFullServingMap = new HashMap<>();

    public void updateFullServingStatus(int tableNumber, boolean isFullServing) {
        tableFullServingMap.put(tableNumber, isFullServing);
        notifyDataSetChanged();
    }

    private ServingStatus getServingStatus(TableItem table) {
        TableItem.Status status = table.getStatus();
        if (status == TableItem.Status.FINISH_SERVE) {
            return new ServingStatus("- Đã phục vụ xong - Chờ thanh toán", false);
        }
        return new ServingStatus("- Đang phục vụ", true);
    }

    @Override
    public int getItemCount() {
        return tableList == null ? 0 : tableList.size();
    }

    public void updateList(List<TableItem> newList) {
        this.tableList = newList != null ? new ArrayList<>(newList) : new ArrayList<>();
        notifyDataSetChanged();
    }

    private static class ServingStatus {
        String text;
        boolean isServing;
        ServingStatus(String text, boolean isServing) {
            this.text = text;
            this.isServing = isServing;
        }
    }

    // --- ViewHolder Cập nhật ---
    public static class TableViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView tvTableNumber;
        TextView tvServingStatus;
        View viewStatusStrip; // Biến mới cho thanh màu
        boolean isBlinking = false; // dùng để check hiệu ứng đang chạy
        ValueAnimator fadeAnimator; // animator cho fade in/out
        int currentStripColor = Color.TRANSPARENT;

        public TableViewHolder(@NonNull View itemView) {
            super(itemView);
            // Ánh xạ ID chuẩn từ item_table_active.xml
            cardView = itemView.findViewById(R.id.card_table);
            tvTableNumber = itemView.findViewById(R.id.tv_table_name);
            tvServingStatus = itemView.findViewById(R.id.tv_serving_status);
            viewStatusStrip = itemView.findViewById(R.id.view_status_strip);
        }
    }
}