package com.ph48845.datn_qlnh_rmis.ui.bep.adapter;

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
 * Adapter cho danh sách bàn đang hoạt động trong màn hình Bếp.
 * Hiển thị các bàn đang có order để bếp biết bàn nào cần chuẩn bị món.
 */
public class BepTableAdapter extends RecyclerView.Adapter<BepTableAdapter.TableViewHolder> {

    private List<TableItem> tableList;
    private OnTableClickListener listener;

    public interface OnTableClickListener {
        void onTableClick(TableItem table);
    }

    public BepTableAdapter(List<TableItem> tableList, OnTableClickListener listener) {
        this.tableList = tableList != null ? tableList : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public TableViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_table_active, parent, false);
        return new TableViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TableViewHolder holder, int position) {
        TableItem table = tableList.get(position);
        if (table == null) return;

        // Set table name
        holder.tvTableNumber.setText("Bàn " + table.getTableNumber());
        
        // Set status
        holder.tvStatus.setText("Có món cần làm");

        // Simple status indicator
        TableItem.Status status = table.getStatus();
        String servingStatusText = "- Đang phục vụ";
        
        // Color: Green for kitchen view (indicating active orders)
        int bgColor = Color.parseColor("#4CAF50"); // Green - has orders to prepare
        
        holder.tvServingStatus.setText(servingStatusText);
        holder.cardView.setCardBackgroundColor(bgColor);

        // White text
        holder.tvTableNumber.setTextColor(Color.WHITE);
        holder.tvStatus.setTextColor(Color.WHITE);
        holder.tvServingStatus.setTextColor(Color.WHITE);

        // Click listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onTableClick(table);
        });
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
