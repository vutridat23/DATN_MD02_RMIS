package com.ph48845.datn_qlnh_rmis.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.RevenueItem;

import java.util.List;

public class RevenueAdapter extends RecyclerView.Adapter<RevenueAdapter.RevenueViewHolder> {

    private List<RevenueItem> revenueList;

    public RevenueAdapter(List<RevenueItem> revenueList) {
        this.revenueList = revenueList;
    }

    @NonNull
    @Override
    public RevenueViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_revenue, parent, false);
        return new RevenueViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RevenueViewHolder holder, int position) {
        RevenueItem item = revenueList.get(position);
        holder.tvDate.setText(item.getDate());
        holder.tvTotalRevenue.setText(String.format("%.0f VND", item.getTotalAmount()));
        holder.tvInvoiceCount.setText("Hóa đơn: " + item.getTotalOrders());
    }

    @Override
    public int getItemCount() {
        return revenueList.size();
    }

    static class RevenueViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvTotalRevenue, tvInvoiceCount;

        public RevenueViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvTotalRevenue = itemView.findViewById(R.id.tvTotalRevenue);
            tvInvoiceCount = itemView.findViewById(R.id.tvInvoiceCount);
        }
    }
}
