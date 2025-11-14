package com.ph48845.datn_qlnh_rmis.ui.thungan.adapter;

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

/**
 * Adapter cho danh sách bàn đang hoạt động trong hệ thống thu ngân.
 * Hiển thị trạng thái phục vụ: "Đang phục vụ lên món" (xanh) hoặc "Đã phục vụ đủ món" (đỏ).
 */
public class ThuNganAdapter extends RecyclerView.Adapter<ThuNganAdapter.TableViewHolder> {

    private final Context context;
    private List<TableItem> tableList;
    private OnTableClickListener listener;

    public interface OnTableClickListener {
        void onTableClick(TableItem table);
    }

    public ThuNganAdapter(Context context, List<TableItem> tableList, OnTableClickListener listener) {
        this.context = context;
        this.tableList = tableList != null ? tableList : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public TableViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_table_active, parent, false);
        return new TableViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TableViewHolder holder, int position) {
        TableItem table = tableList.get(position);
        if (table == null) return;

        // Set tên bàn
        holder.tvTableNumber.setText("Bàn " + table.getTableNumber());
        
        // Set trạng thái "Đã có khách"
        holder.tvStatus.setText("Đã có khách");

        // Xác định trạng thái phục vụ và màu sắc
        ServingStatus servingStatus = getServingStatus(table);
        holder.tvServingStatus.setText(servingStatus.text);
        
        // Đặt màu nền theo trạng thái phục vụ
        int bgColor;
        if (servingStatus.isServing) {
            // Xanh lá cây: Đang phục vụ lên món
            bgColor = Color.parseColor("#2BB673");
        } else {
            // Đỏ: Đã phục vụ đủ món
            bgColor = Color.parseColor("#D2544C");
        }
        holder.cardView.setCardBackgroundColor(bgColor);

        // Text màu trắng
        holder.tvTableNumber.setTextColor(Color.WHITE);
        holder.tvStatus.setTextColor(Color.WHITE);
        holder.tvServingStatus.setTextColor(Color.WHITE);

        // Click listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onTableClick(table);
        });
    }

    /**
     * Xác định trạng thái phục vụ dựa trên table.
     * - Nếu table có status FINISH_SERVE -> "Đã phục vụ đủ món" (đỏ)
     * - Nếu table có status PENDING_PAYMENT -> "Chờ thanh toán" (có thể xanh hoặc vàng)
     * - Ngược lại (OCCUPIED) -> "Đang phục vụ lên món" (xanh)
     */
    private ServingStatus getServingStatus(TableItem table) {
        TableItem.Status status = table.getStatus();
        if (status == TableItem.Status.FINISH_SERVE) {
            return new ServingStatus("- Đã phục vụ đủ món", false);
        }
        // Mặc định: đang phục vụ (OCCUPIED hoặc PENDING_PAYMENT)
        return new ServingStatus("- Đang phục vụ lên món", true);
    }

    @Override
    public int getItemCount() {
        return tableList == null ? 0 : tableList.size();
    }

    public void updateList(List<TableItem> newList) {
        this.tableList = newList != null ? new ArrayList<>(newList) : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Helper class để lưu trạng thái phục vụ
     */
    private static class ServingStatus {
        String text;
        boolean isServing;

        ServingStatus(String text, boolean isServing) {
            this.text = text;
            this.isServing = isServing;
        }
    }

    /**
     * ViewHolder
     */
    public static class TableViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView tvTableNumber;
        TextView tvStatus;
        TextView tvServingStatus;

        public TableViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_table);
            tvTableNumber = itemView.findViewById(R.id.tv_table_name);
            tvStatus = itemView.findViewById(R.id.tv_table_status);
            tvServingStatus = itemView.findViewById(R.id.tv_serving_status);
        }
    }
}
