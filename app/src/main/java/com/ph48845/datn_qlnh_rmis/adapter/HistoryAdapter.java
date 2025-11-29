package com.ph48845.datn_qlnh_rmis.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.HistoryItem;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private List<HistoryItem> historyList;

    public HistoryAdapter(List<HistoryItem> historyList) {
        this.historyList = historyList;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        HistoryItem item = historyList.get(position);

        // Hiển thị ID hóa đơn
        holder.tvOrderId.setText(item.getId() != null ? item.getId() : "N/A");

        // Số bàn
        holder.tvTableNumber.setText("Bàn " + item.getTableNumber());

        // Ngày tạo hóa đơn
        holder.tvOrderDate.setText(formatDate(item.getCreatedAt()));

        holder.tvItemCount.setText("Số món: " + item.getTotalItems());
        holder.tvTotal.setText("Tổng: " + NumberFormat.getInstance(new Locale("vi","VN")).format(item.getTotalAmount()) + " VND");

    }

    @Override
    public int getItemCount() {
        return historyList != null ? historyList.size() : 0;
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView tvOrderId, tvTableNumber, tvOrderDate, tvItemCount, tvTotal;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderId = itemView.findViewById(R.id.tvOrderId);
            tvTableNumber = itemView.findViewById(R.id.tvTableNumber);
            tvOrderDate = itemView.findViewById(R.id.tvOrderDate);
            tvItemCount = itemView.findViewById(R.id.tvItemCount);
            tvTotal = itemView.findViewById(R.id.tvTotal);
        }
    }

    // Chuyển từ ISO 8601 sang định dạng dd/MM/yyyy HH:mm
    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "N/A";
        try {
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = isoFormat.parse(isoDate);
            SimpleDateFormat outFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            return outFormat.format(date);
        } catch (Exception e) {
            e.printStackTrace();
            return isoDate;
        }
    }
}
