package com.ph48845.datn_qlnh_rmis.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;


import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.ReportItem;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ViewHolder> {

    private List<ReportItem> list;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public ReportAdapter(List<ReportItem> list) {
        this.list = list;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_revenue, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        ReportItem item = list.get(position);

        if (item.getDate() != null) {
            holder.tvDate.setText(sdf.format(item.getDate()));
        } else {
            holder.tvDate.setText("-");
        }

        holder.tvInvoiceCount.setText("Hóa đơn: " + item.getTotalOrders());

        // Format tổng doanh thu
        NumberFormat formatter = NumberFormat.getInstance(Locale.US); // hoặc Locale.getDefault()
        String revenueStr = formatter.format(item.getTotalRevenue());
        holder.tvTotalRevenue.setText("Tổng doanh thu: " + revenueStr + " VND");
    }


    @Override
    public int getItemCount() {
        return list.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvInvoiceCount, tvTotalRevenue;

        public ViewHolder(View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvInvoiceCount = itemView.findViewById(R.id.tvInvoiceCount);
            tvTotalRevenue = itemView.findViewById(R.id.tvTotalRevenue);
        }
    }
}
