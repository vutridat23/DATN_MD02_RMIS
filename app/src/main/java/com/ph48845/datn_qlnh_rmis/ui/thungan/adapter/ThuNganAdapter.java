package com.ph48845.datn_qlnh_rmis.ui.thungan.adapter;

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
import java.util.List;

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

        // 1. Tên bàn: Màu Đỏ (#AA0000)
        holder.tvTableNumber.setText("Bàn " + table.getTableNumber());
        holder.tvTableNumber.setTextColor(Color.parseColor("#AA0000"));

        // 2. Xử lý Trạng thái & Màu sắc
        ServingStatus servingStatus = getServingStatus(table);

        // Luôn set nền thẻ màu TRẮNG
        holder.cardView.setCardBackgroundColor(Color.WHITE);

        // Đổi màu thanh Strip dựa trên trạng thái
        if (servingStatus.isServing) {
            // Đang phục vụ: Màu ĐỎ (#AA0000)
            holder.viewStatusStrip.setBackgroundColor(Color.parseColor("#AA0000"));
            holder.cardView.setCardElevation(8f); // Nổi cao
        } else {
            // Đã xong/Chờ thanh toán: Màu CAM (#F57F17)
            holder.viewStatusStrip.setBackgroundColor(Color.parseColor("#F57F17"));
            holder.cardView.setCardElevation(4f); // Nổi thấp hơn
        }

        // 4. Dòng trạng thái chi tiết "Đang lên món..."
        if (showServingStatus) {
            holder.tvServingStatus.setText(servingStatus.text);
            holder.tvServingStatus.setVisibility(View.VISIBLE);
            holder.tvServingStatus.setTextColor(Color.parseColor("#757575")); // Màu xám nhạt
        } else {
            holder.tvServingStatus.setVisibility(View.GONE);
        }

        // 5. Sự kiện Click
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onTableClick(table);
        });
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