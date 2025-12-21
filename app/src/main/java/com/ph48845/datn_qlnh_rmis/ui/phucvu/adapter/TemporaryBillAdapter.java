package com.ph48845.datn_qlnh_rmis. ui.phucvu.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android. widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis. R;
import com.ph48845.datn_qlnh_rmis.data.model. Order;

import java.text. NumberFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util. Locale;

/**
 * Adapter để hiển thị danh sách hóa đơn tạm tính
 */
public class TemporaryBillAdapter extends RecyclerView. Adapter<TemporaryBillAdapter.ViewHolder> {

    private List<Order> orders;
    private OnOrderClickListener listener;

    public interface OnOrderClickListener {
        void onOrderClick(Order order);
    }

    public TemporaryBillAdapter(List<Order> orders, OnOrderClickListener listener) {
        this.orders = orders;
        this. listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_temporary_bill, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Order order = orders.get(position);
        holder.bind(order, listener);
    }

    @Override
    public int getItemCount() {
        return orders != null ?  orders.size() : 0;
    }

    public void updateList(List<Order> newOrders) {
        this.orders = newOrders;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvOrderId;
        TextView tvTableNumber;
        TextView tvTotalAmount;
        TextView tvCreatedAt;
        TextView tvStatus;
        TextView tvRequestedBy;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderId = itemView.findViewById(R.id. tv_order_id);
            tvTableNumber = itemView.findViewById(R.id.tv_table_number);
            tvTotalAmount = itemView.findViewById(R.id.tv_total_amount);
            tvCreatedAt = itemView.findViewById(R.id.tv_created_at);
            tvStatus = itemView.findViewById(R.id.tv_status);
            tvRequestedBy = itemView.findViewById(R.id.tv_requested_by);
        }

        public void bind(Order order, OnOrderClickListener listener) {
            // Format order ID
            String orderId = order.getId();
            if (orderId != null && orderId.length() > 8) {
                orderId = orderId.substring(orderId.length() - 8); // Lấy 8 ký tự cuối
            }
            tvOrderId.setText("Mã HĐ: #" + (orderId != null ? orderId :  "N/A"));

            // Format table number
            tvTableNumber.setText("Bàn: " + order.getTableNumber());

            // Format total amount
            NumberFormat formatter = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
            double amount = order.getFinalAmount() > 0 ? order.getFinalAmount() : order.getTotalAmount();
            tvTotalAmount. setText(formatter.format(amount));

            // Format requested time
            String requestedAt = order.getTempCalculationRequestedAt();
            if (requestedAt != null && ! requestedAt.trim().isEmpty()) {
                try {
                    // Parse ISO 8601 format:  2025-12-19T09:30:00.000Z
                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                    inputFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                    java.util.Date date = inputFormat.parse(requestedAt);

                    // Format hiển thị: dd/MM/yyyy HH:mm
                    SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                    tvCreatedAt.setText(outputFormat.format(date));
                } catch (Exception e) {
                    tvCreatedAt. setText(requestedAt);
                }
            } else {
                tvCreatedAt.setText("N/A");
            }

            // Display requested by
            String requestedBy = order.getTempCalculationRequestedBy();
            if (requestedBy != null && !requestedBy.trim().isEmpty()) {
                tvRequestedBy.setVisibility(View.VISIBLE);
                tvRequestedBy.setText("Người yêu cầu: " + requestedBy);
            } else {
                tvRequestedBy.setVisibility(View. GONE);
            }

            // Display status
            tvStatus.setText("Yêu cầu tạm tính");
            tvStatus.setBackgroundResource(R.drawable.bg_status_temporary_bill);

            // Handle click
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onOrderClick(order);
                }
            });
        }
    }
}