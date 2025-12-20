package com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter;

import android. view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx. recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis. data.model.Order;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Adapter để hiển thị danh sách yêu cầu tạm tính - CHỈ XEM
 */
public class TempCalculationListAdapter extends RecyclerView. Adapter<TempCalculationListAdapter.ViewHolder> {

    private List<Order> orders;

    public TempCalculationListAdapter(List<Order> orders) {
        this.orders = orders;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_temp_calculation_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Order order = orders.get(position);
        holder.bind(order);
    }

    @Override
    public int getItemCount() {
        return orders != null ? orders.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTableNumber;
        TextView tvRequestTime;
        TextView tvRequestedBy;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTableNumber = itemView.findViewById(R.id. tv_table_number);
            tvRequestTime = itemView.findViewById(R.id.tv_request_time);
            tvRequestedBy = itemView.findViewById(R.id.tv_requested_by);
        }

        public void bind(Order order) {
            // Số bàn
            tvTableNumber.setText("Bàn " + order.getTableNumber());

            // Thời gian yêu cầu
            String requestedAt = order.getTempCalculationRequestedAt();
            if (requestedAt != null && !requestedAt.trim().isEmpty()) {
                try {
                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss. SSS'Z'", Locale.US);
                    inputFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                    java.util.Date date = inputFormat.parse(requestedAt);

                    SimpleDateFormat outputFormat = new SimpleDateFormat("HH: mm - dd/MM/yyyy", Locale.getDefault());
                    tvRequestTime.setText(outputFormat. format(date));
                } catch (Exception e) {
                    tvRequestTime.setText(requestedAt);
                }
            } else {
                tvRequestTime.setText("Chưa rõ thời gian");
            }

            // Người yêu cầu
            String requestedBy = order.getTempCalculationRequestedBy();
            if (requestedBy != null && !requestedBy.trim().isEmpty()) {
                tvRequestedBy.setVisibility(View.VISIBLE);
                tvRequestedBy.setText("Người yêu cầu: " + requestedBy);
            } else {
                tvRequestedBy.setVisibility(View.GONE);
            }
        }
    }
}