package com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.cardview.widget.CardView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;

import java.util.ArrayList;
import java.util.List;

public class TableAdapter extends RecyclerView.Adapter<TableAdapter.TableViewHolder> {

    public interface OnTableClickListener {
        void onTableClick(View v, TableItem table);
        void onTableLongClick(View v, TableItem table);
    }

    private final OnTableClickListener listener;
    private List<TableItem> tableList = new ArrayList<>();

    // Constructor 1
    public TableAdapter(OnTableClickListener listener, List<TableItem> tables) {
        this.listener = listener;
        this.tableList = tables != null ? tables : new ArrayList<>();
    }

    // Constructor 2
    public TableAdapter(Context ctx, List<TableItem> tables, OnTableClickListener listener) {
        this(listener, tables);
    }

    @NonNull
    @Override
    public TableAdapter.TableViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_table, parent, false);
        return new TableViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TableAdapter.TableViewHolder holder, int position) {
        TableItem table = tableList.get(position);
        if (table == null) return;

        // --- 1. SET DỮ LIỆU CƠ BẢN ---
        holder.tvTableNumber.setText("Bàn " + table.getTableNumber());
        holder.tvCapacity.setText(table.getCapacity() > 0 ? ("Sức chứa: " + table.getCapacity()) : "");
        holder.tvStatus.setText(table.getStatusDisplay());

        // --- 2. XỬ LÝ MÀU SẮC (LOGIC MỚI) ---

        // Luôn đặt nền Card là màu trắng
        holder.cardView.setCardBackgroundColor(Color.WHITE);

        if (table.getStatus() == TableItem.Status.AVAILABLE) {

            holder.viewStatusStrip.setBackgroundColor(Color.parseColor("#000000"));

            // Text màu xám để làm chìm
            holder.tvTableNumber.setTextColor(Color.parseColor("#757575"));
            holder.tvStatus.setTextColor(Color.parseColor("#757575"));
            holder.tvCapacity.setTextColor(Color.parseColor("#757575"));

            // Giảm độ nổi (Elevation)
            holder.cardView.setCardElevation(2f);

        } else {
            // --- TRẠNG THÁI: CÓ KHÁCH (Active) ---

            // Xác định màu cho Strip dựa trên chi tiết trạng thái
            int stripColor;
            switch (table.getStatus()) {
                case PENDING_PAYMENT:
                case RESERVED:
                    stripColor = Color.parseColor("#F57F17"); // Màu Cam
                    break;
                case AVAILABLE:
                    stripColor = Color.parseColor("#000000");
                    break;
                case FINISH_SERVE:
                    stripColor = Color.parseColor("#D32F2F"); // Màu Đỏ đậm hơn chút hoặc giữ nguyên
                    break;
                case OCCUPIED:
                default:
                    stripColor = Color.parseColor("#AA0000"); // Màu Đỏ chủ đạo
                    break;
            }
            holder.viewStatusStrip.setBackgroundColor(stripColor);

            // Tên bàn màu Đỏ thương hiệu
            holder.tvTableNumber.setTextColor(Color.parseColor("#AA0000"));

            // Các text thông tin khác màu đậm cho dễ đọc
            holder.tvStatus.setTextColor(Color.parseColor("#333333"));
            holder.tvStatus.setTypeface(null, Typeface.BOLD);
            holder.tvCapacity.setTextColor(Color.parseColor("#757575"));

            // Tăng độ nổi để nhấn mạnh bàn đang hoạt động
            holder.cardView.setCardElevation(8f);
        }

        // --- 3. SỰ KIỆN CLICK ---
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onTableClick(v, table);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onTableLongClick(v, table);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return tableList == null ? 0 : tableList.size();
    }

    public void updateList(List<TableItem> newList) {
        this.tableList = newList != null ? newList : new ArrayList<>();
        notifyDataSetChanged();
    }

    // --- ViewHolder Cập Nhật ---
    public static class TableViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView tvTableNumber, tvCapacity, tvStatus;
        View viewStatusStrip; // <--- Cần thêm biến này

        public TableViewHolder(@NonNull View itemView) {
            super(itemView);
            // Ánh xạ các View theo ID trong file item_table.xml mới
            cardView = itemView.findViewById(R.id.card_table);
            tvTableNumber = itemView.findViewById(R.id.tv_table_name);
            tvCapacity = itemView.findViewById(R.id.tv_table_capacity);
            tvStatus = itemView.findViewById(R.id.tv_table_status);

            // Ánh xạ thanh Strip mới
            viewStatusStrip = itemView.findViewById(R.id.view_status_strip);
        }
    }
}