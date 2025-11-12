package com.ph48845.datn_qlnh_rmis.ui.thungan.adapter;

import android.content.Context; // CẦN THIẾT: Thêm Context để giải quyết lỗi 2 tham số
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
// CẦN THIẾT: Import Order để sử dụng Order.OrderItem
import com.ph48845.datn_qlnh_rmis.data.model.Order;

import java.util.List;
import java.text.DecimalFormat;

// Đổi tên ViewHolder cho rõ ràng
public class ThuNganAdapter extends RecyclerView.Adapter<ThuNganAdapter.OrderItemViewHolder> {

    private Context context; // Thêm Context
    private List<Order.OrderItem> orderItems; // Đổi từ List<MenuItem> sang List<Order.OrderItem>

    // CONSTRUCTOR ĐÃ SỬA: Chấp nhận 2 tham số (Context và List<Order.OrderItem>)
    public ThuNganAdapter(Context context, List<Order.OrderItem> orderItems) {
        this.context = context;
        this.orderItems = orderItems;
    }

    // Phương thức giúp cập nhật dữ liệu mới
    public void setOrderItems(List<Order.OrderItem> newItems) {
        this.orderItems = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public OrderItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Giả sử layout item_order_item hiển thị chi tiết món trong đơn hàng
        // HOẶC bạn vẫn dùng item_menu nếu layout đủ các TextView cần thiết
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_menu, parent, false);
        return new OrderItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderItemViewHolder holder, int position) {
        Order.OrderItem item = orderItems.get(position);

        // Sử dụng DecimalFormat để định dạng giá
        DecimalFormat formatter = new DecimalFormat("#,### VNĐ");

        // Hiển thị Tên món và Số lượng
        holder.tvName.setText(item.getName() + " x" + item.getQuantity());

        // Hiển thị Tổng tiền cho món đó (Giá * Số lượng)
        double subtotal = item.getPrice() * item.getQuantity();
        holder.tvPrice.setText(formatter.format(subtotal));

        // Hiển thị trạng thái món ăn (Ví dụ: READY, PENDING)
        // Nếu layout item_menu có tvCategory, có thể dùng để hiển thị status
        if (holder.tvCategory != null) {
            holder.tvCategory.setText("Trạng thái: " + item.getStatus());
        }
    }

    @Override
    public int getItemCount() {
        return orderItems.size();
    }

    // ViewHolder đã đổi tên và cập nhật các TextView
    public static class OrderItemViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvPrice, tvCategory; // Giữ nguyên ID từ layout item_menu

        public OrderItemViewHolder(@NonNull View itemView) {
            super(itemView);
            // Các ID này phải khớp với R.layout.item_menu
            tvName = itemView.findViewById(R.id.tv_menu_name);
            tvPrice = itemView.findViewById(R.id.tv_menu_price);
            tvCategory = itemView.findViewById(R.id.tv_badge);
        }
    }
}