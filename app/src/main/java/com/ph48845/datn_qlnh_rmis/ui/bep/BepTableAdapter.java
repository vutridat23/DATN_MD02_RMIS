package com.ph48845.datn_qlnh_rmis.ui.bep;

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
 * Adapter cho danh sách bàn đang hoạt động trong màn hình bếp.
 * Hiển thị các bàn có order đang mở để bếp có thể xem và chuẩn bị món.
 */
public class BepTableAdapter extends RecyclerView.Adapter<BepTableAdapter.TableViewHolder> {

    private final Context context;
    private List<TableItem> tableList;
    private OnTableClickListener listener;

    public interface OnTableClickListener {
        void onTableClick(TableItem table);
    }

    public BepTableAdapter(Context context, List<TableItem> tableList, OnTableClickListener listener) {
        this.context = context;
        this.tableList = tableList != null ? tableList : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public TableViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_bep_table, parent, false);
        return new TableViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TableViewHolder holder, int position) {
        TableItem table = tableList.get(position);
        if (table == null) return;

        // Set tên bàn
        holder.tvTableNumber.setText("Bàn " + table.getTableNumber());
        
        // Set trạng thái "Có order"
        holder.tvStatus.setText("Có order");

        // Màu xanh lá cây cho bàn có order
        int bgColor = Color.parseColor("#4CAF50");
        holder.cardView.setCardBackgroundColor(bgColor);

        // Text màu trắng
        holder.tvTableNumber.setTextColor(Color.WHITE);
        holder.tvStatus.setTextColor(Color.WHITE);

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

        public TableViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_bep_table);
            tvTableNumber = itemView.findViewById(R.id.tv_bep_table_name);
            tvStatus = itemView.findViewById(R.id.tv_bep_table_status);
        }
    }
}
