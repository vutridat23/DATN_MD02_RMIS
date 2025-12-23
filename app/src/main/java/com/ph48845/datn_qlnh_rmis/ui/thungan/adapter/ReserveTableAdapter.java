
package com.ph48845.datn_qlnh_rmis.ui.thungan.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;

import java.util.List;

public class ReserveTableAdapter extends RecyclerView.Adapter<ReserveTableAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(TableItem table);
    }

    private List<TableItem> list;
    private final OnItemClickListener listener;

    public ReserveTableAdapter(List<TableItem> list, OnItemClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    public void updateList(List<TableItem> newList) {
        this.list = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReserveTableAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_reserve_table, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ReserveTableAdapter.ViewHolder holder, int position) {
        TableItem t = list.get(position);
        holder.bind(t, listener);
    }

    @Override
    public int getItemCount() {
        return list != null ? list.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvLine;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLine = itemView.findViewById(R.id.tv_table_line);
        }

        void bind(final TableItem t, final OnItemClickListener listener) {
            // Show single-line: "Bàn X - Khả dụng"
            String text = "Bàn " + t.getTableNumber() + " - Khả dụng";
            tvLine.setText(text);
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(t);
            });
        }
    }
}