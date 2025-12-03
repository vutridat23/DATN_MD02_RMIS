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
 * Có thể ẩn dòng trạng thái phục vụ theo từng màn hình sử dụng.
 */
public class ThuNganAdapter extends RecyclerView.Adapter<ThuNganAdapter.TableViewHolder> {

    private final Context context;
    private List<TableItem> tableList;
    private OnTableClickListener listener;

    // New flag: Ẩn/hiện dòng trạng thái phục vụ
    private boolean showServingStatus = true;

    public void setShowServingStatus(boolean showServingStatus) {
        this.showServingStatus = showServingStatus;
    }

    // Nếu bạn muốn ẩn cả "Đã có khách", giữ nguyên flag sau:
    private boolean showDaCoKhach = true;
    public void setShowDaCoKhach(boolean showDaCoKhach) {
        this.showDaCoKhach = showDaCoKhach;
    }

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
        holder.tvTableNumber.setTextColor(Color.WHITE);

        // Xác định trạng thái phục vụ để luôn có màu nền cho bàn
        ServingStatus servingStatus = getServingStatus(table);
        int bgColor = servingStatus.isServing ? Color.parseColor("#2BB673") : Color.parseColor("#D2544C");
        holder.cardView.setCardBackgroundColor(bgColor);

        // Ẩn/hiện "Đã có khách" linh hoạt (nếu cần)
        if (showDaCoKhach) {
            holder.tvStatus.setText("Đã có khách");
            holder.tvStatus.setVisibility(View.VISIBLE);
            holder.tvStatus.setTextColor(Color.WHITE);
        } else {
            holder.tvStatus.setVisibility(View.GONE);
        }

        // Hiện hoặc ẩn trạng thái phục vụ
        if (showServingStatus) {
            holder.tvServingStatus.setText(servingStatus.text);
            holder.tvServingStatus.setVisibility(View.VISIBLE);
            holder.tvServingStatus.setTextColor(Color.WHITE);
        } else {
            holder.tvServingStatus.setVisibility(View.GONE);
        }

        // Bắt sự kiện bấm chọn bàn
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onTableClick(table);
        });
    }

    // Helper xác định trạng thái phục vụ (không thay đổi)
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

    // Helper class để lưu trạng thái phục vụ
    private static class ServingStatus {
        String text;
        boolean isServing;

        ServingStatus(String text, boolean isServing) {
            this.text = text;
            this.isServing = isServing;
        }
    }

    // ViewHolder (layout của bạn cần 3 TextView: tv_table_name, tv_table_status, tv_serving_status)
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