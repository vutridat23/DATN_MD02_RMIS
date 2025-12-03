package com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter;

import android.content.Context;
import android.graphics.Color;
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

/**
 * Adapter hiển thị danh sách bàn (với layout item_table.xml)
 *
 * Kept a backward-compatible constructor that accepts (Context, List<TableItem>, listener).
 */
public class TableAdapter extends RecyclerView.Adapter<TableAdapter.TableViewHolder> {

    public interface OnTableClickListener {
        void onTableClick(View v, TableItem table);
        void onTableLongClick(View v, TableItem table);
    }

    private final OnTableClickListener listener;
    private List<TableItem> tableList = new ArrayList<>();

    // Constructor (listener first)
    public TableAdapter(OnTableClickListener listener, List<TableItem> tables) {
        this.listener = listener;
        this.tableList = tables != null ? tables : new ArrayList<>();
    }

    // Overloaded constructor used in code: (Context, List<TableItem>, listener)
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

        holder.tvTableNumber.setText("Bàn " + table.getTableNumber());
        holder.tvCapacity.setText(table.getCapacity() > 0 ? ("Sức chứa: " + table.getCapacity() + " người") : "");
        holder.tvStatus.setText(table.getStatusDisplay());

        int bgColor;
        try {
            switch (table.getStatus()) {
                case OCCUPIED:
                    bgColor = Color.parseColor("#2BB673");
                    break;
                case PENDING_PAYMENT:
                case RESERVED:
                    bgColor = Color.parseColor("#F2B01E");
                    break;
                case FINISH_SERVE:
                    bgColor = Color.parseColor("#D2544C");
                    break;
                case EMPTY:
                default:
                    bgColor = Color.parseColor("#DADADA");
                    break;
            }
        } catch (Exception e) {
            bgColor = Color.parseColor("#DADADA");
        }
        holder.cardView.setCardBackgroundColor(bgColor);

        if (table.getStatus() == TableItem.Status.EMPTY) {
            holder.tvTableNumber.setTextColor(Color.parseColor("#7A7A7A"));
            holder.tvCapacity.setTextColor(Color.parseColor("#7A7A7A"));
            holder.tvStatus.setTextColor(Color.parseColor("#7A7A7A"));
        } else {
            holder.tvTableNumber.setTextColor(Color.WHITE);
            holder.tvCapacity.setTextColor(Color.WHITE);
            holder.tvStatus.setTextColor(Color.WHITE);
        }

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

    public static class TableViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        TextView tvTableNumber, tvCapacity, tvStatus;

        public TableViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_table);
            tvTableNumber = itemView.findViewById(R.id.tv_table_name);
            tvCapacity = itemView.findViewById(R.id.tv_table_capacity);
            tvStatus = itemView.findViewById(R.id.tv_table_status);
        }
    }
}