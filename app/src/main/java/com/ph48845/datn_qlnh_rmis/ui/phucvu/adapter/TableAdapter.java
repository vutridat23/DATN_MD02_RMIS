package com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter;



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
 * Adapter for a grid of tables. Each cell is a square card whose color depends on table status.
 */
public class TableAdapter extends RecyclerView.Adapter<TableAdapter.TableViewHolder> {

    private final Context context;
    private List<TableItem> tableList;
    private OnTableClickListener listener;

    // Interface cho click listener (gửi kèm View để có thể anchor PopupMenu)
    public interface OnTableClickListener {
        void onTableClick(View v, TableItem table);
        void onTableLongClick(View v, TableItem table);
    }

    // Constructor
    public TableAdapter(Context context, List<TableItem> tableList, OnTableClickListener listener) {
        this.context = context;
        this.tableList = tableList != null ? tableList : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public TableViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_table, parent, false);
        return new TableViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TableViewHolder holder, int position) {
        TableItem table = tableList.get(position);

        // Set dữ liệu
        holder.tvTableNumber.setText("Bàn " + table.getTableNumber());
        holder.tvCapacity.setText(table.getCapacity() > 0 ? ("Sức chứa: " + table.getCapacity() + " người") : "");
        holder.tvStatus.setText(table.getStatusDisplay());
        holder.tvLocation.setText(table.getLocation());

        // Đổi màu theo trạng thái
        int bgColor;
        switch (table.getStatus()) {
            case OCCUPIED:
                bgColor = Color.parseColor("#2BB673"); // green
                break;
            case PENDING_PAYMENT:
            case RESERVED:
                bgColor = Color.parseColor("#F2B01E"); // yellow/orange
                break;
            case FINISH_SERVE:
                bgColor = Color.parseColor("#D2544C"); // red
                break;
            case EMPTY:
            default:
                bgColor = Color.parseColor("#DADADA"); // light gray
                break;
        }
        holder.cardView.setCardBackgroundColor(bgColor);

        // text color: if gray, show muted dark text; otherwise white
        if (table.getStatus() == TableItem.Status.EMPTY) {
            holder.tvTableNumber.setTextColor(Color.parseColor("#7A7A7A"));
            holder.tvCapacity.setTextColor(Color.parseColor("#7A7A7A"));
            holder.tvStatus.setTextColor(Color.parseColor("#7A7A7A"));
            holder.tvLocation.setTextColor(Color.parseColor("#7A7A7A"));
        } else {
            holder.tvTableNumber.setTextColor(Color.WHITE);
            holder.tvCapacity.setTextColor(Color.WHITE);
            holder.tvStatus.setTextColor(Color.WHITE);
            holder.tvLocation.setTextColor(Color.WHITE);
        }

        // Click listeners (gửi kèm view)
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

    // Cập nhật danh sách
    public void updateList(List<TableItem> newList) {
        this.tableList = newList != null ? newList : new ArrayList<>();
        notifyDataSetChanged();
    }

    // Thêm/xóa/cập nhật bàn (giữ nguyên API bạn đã có)
    public void addTable(TableItem table) {
        tableList.add(table);
        notifyItemInserted(tableList.size() - 1);
    }

    public void removeTable(int position) {
        if (position >= 0 && position < tableList.size()) {
            tableList.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void updateTable(int position, TableItem table) {
        if (position >= 0 && position < tableList.size()) {
            tableList.set(position, table);
            notifyItemChanged(position);
        }
    }

    // ViewHolder
    public static class TableViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView tvTableNumber, tvCapacity, tvStatus, tvLocation;

        public TableViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_table);
            tvTableNumber = itemView.findViewById(R.id.tv_table_name);
            tvCapacity = itemView.findViewById(R.id.tv_table_capacity);
            tvStatus = itemView.findViewById(R.id.tv_table_status);
            tvLocation = itemView.findViewById(R.id.tv_table_sub);
        }
    }
}