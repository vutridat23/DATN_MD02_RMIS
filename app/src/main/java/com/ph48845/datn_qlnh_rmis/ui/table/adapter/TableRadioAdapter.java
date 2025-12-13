package com.ph48845.datn_qlnh_rmis.ui.table.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter hiển thị danh sách bàn dạng radio dùng trong dialog chọn bàn đích.
 */
public class TableRadioAdapter extends RecyclerView.Adapter<TableRadioAdapter.VH> {

    public interface OnTableClickListener { void onTableClick(TableItem table); }

    private final OnTableClickListener listener;
    private final List<TableItem> items = new ArrayList<>();
    private String selectedTableId;

    public TableRadioAdapter(OnTableClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<TableItem> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public void setSelectedTableId(String id) {
        selectedTableId = id;
        notifyDataSetChanged();
    }

    public String getSelectedTableId() { return selectedTableId; }

    /**
     * Safe getter for items
     */
    public List<TableItem> getItems() {
        return new ArrayList<>(items);
    }

    /**
     * Helper: find table by id
     */
    public TableItem getTableById(String id) {
        if (id == null) return null;
        for (TableItem t : items) {
            if (t == null) continue;
            if (id.equals(t.getId())) return t;
        }
        return null;
    }

    @NonNull
    @Override
    public TableRadioAdapter.VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_table_radio, parent, false);
        return new TableRadioAdapter.VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TableRadioAdapter.VH holder, int position) {
        TableItem t = items.get(position);
        if (t == null) return;
        holder.tvLabel.setText("Bàn " + t.getTableNumber());
        String status = t.getStatus() != null ? t.getStatus().name() : "";
        holder.tvStatus.setText("Trạng thái: " + status);
        boolean checked = t.getId() != null && t.getId().equals(selectedTableId);
        holder.radio.setChecked(checked);

        holder.itemView.setOnClickListener(v -> {
            selectedTableId = t.getId();
            notifyDataSetChanged();
            if (listener != null) listener.onTableClick(t);
        });
        holder.radio.setOnClickListener(v -> {
            selectedTableId = t.getId();
            notifyDataSetChanged();
            if (listener != null) listener.onTableClick(t);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        RadioButton radio;
        TextView tvLabel, tvStatus;
        VH(@NonNull View itemView) {
            super(itemView);
            radio = itemView.findViewById(R.id.rb_table);
            tvLabel = itemView.findViewById(R.id.tv_table_label);
            tvStatus = itemView.findViewById(R.id.tv_table_status);
        }
    }
}