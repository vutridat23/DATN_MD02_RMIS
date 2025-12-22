package com.ph48845.datn_qlnh_rmis. ui.phucvu.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com. ph48845.datn_qlnh_rmis.data. model.Order;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Adapter hiển thị danh sách yêu cầu kiểm tra bàn
 */
public class CheckItemsListAdapter extends RecyclerView.Adapter<CheckItemsListAdapter.ViewHolder> {

    private List<Order> orders;
    private OnCheckItemClickListener listener;

    public interface OnCheckItemClickListener {
        void onCheckItemClick(Order order);
    }

    public CheckItemsListAdapter(List<Order> orders, OnCheckItemClickListener listener) {
        this.orders = orders;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_check_items_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Order order = orders.get(position);
        holder.bind(order, listener);
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
            tvTableNumber = itemView.findViewById(R. id.tv_table_number);
            tvRequestTime = itemView.findViewById(R.id.tv_request_time);
            tvRequestedBy = itemView.findViewById(R.id.tv_requested_by);
        }

        public void bind(Order order, OnCheckItemClickListener listener) {
            // Hiển thị số bàn
            tvTableNumber. setText("Bàn " + order.getTableNumber());

            // Hiển thị thời gian yêu cầu
            String requestedAt = order.getCheckItemsRequestedAt();
            if (requestedAt != null && !requestedAt.trim().isEmpty()) {
                try {
                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss. SSS'Z'", Locale.US);
                    inputFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC+7"));
                    java.util.Date date = inputFormat.parse(requestedAt);

                    SimpleDateFormat outputFormat = new SimpleDateFormat("HH: mm - dd/MM/yyyy", Locale.getDefault());
                    tvRequestTime.setText(outputFormat. format(date));
                } catch (Exception e) {
                    tvRequestTime.setText(requestedAt);
                }
            } else {
                tvRequestTime.setText("Chưa rõ thời gian");
            }

            // Hiển thị người yêu cầu (thu ngân)
            String requestedBy = order.getCheckItemsRequestedBy();
            if (requestedBy != null && !requestedBy.trim().isEmpty()) {
                tvRequestedBy.setVisibility(View.VISIBLE);
                tvRequestedBy.setText("Người yêu cầu:  " + requestedBy);
            } else {
                tvRequestedBy.setVisibility(View. GONE);
            }

            // Handle click
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onCheckItemClick(order);
                    }
                }
            });
        }
    }
}