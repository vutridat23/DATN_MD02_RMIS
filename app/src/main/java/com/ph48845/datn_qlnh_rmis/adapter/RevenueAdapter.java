package com.ph48845.datn_qlnh_rmis.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.RevenueItem;

import java.util.ArrayList;
import java.util.List;

public class RevenueAdapter extends RecyclerView.Adapter<RevenueAdapter.ViewHolder> {

    private List<RevenueItem> revenueList = new ArrayList<>();

    public RevenueAdapter() { }

    public RevenueAdapter(List<RevenueItem> revenueList) {
        if (revenueList != null) {
            this.revenueList = revenueList;
        }
    }

    // Cập nhật dữ liệu mới
    public void setRevenueList(List<RevenueItem> revenueList) {
        if (revenueList != null) {
            this.revenueList = revenueList;
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_revenue, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RevenueItem item = revenueList.get(position);
        if (item != null) {
            holder.tvDate.setText("Ngày: " + (item.getDate() != null ? item.getDate() : "-"));
            holder.tvTotalRevenue.setText("Doanh thu: " + String.format("%,.0f₫", item.getTotalRevenue()));
            holder.tvInvoiceCount.setText("Số hóa đơn: " + item.getInvoiceCount());
        }
    }

    @Override
    public int getItemCount() {
        return revenueList != null ? revenueList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvTotalRevenue, tvInvoiceCount;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvTotalRevenue = itemView.findViewById(R.id.tvTotalRevenue);
            tvInvoiceCount = itemView.findViewById(R.id.tvInvoiceCount);
        }
    }
}
