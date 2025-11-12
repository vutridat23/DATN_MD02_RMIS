package com.ph48845.datn_qlnh_rmis.ui.thungan.adapter; // Đã sửa package name

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order.Item; // Đã sử dụng lớp Item lồng

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Adapter hiển thị danh sách các món ăn trong hóa đơn TĨNH trên màn hình Thu Ngân.
 */
public class ThuNganAdapter extends RecyclerView.Adapter<ThuNganAdapter.OrderItemViewHolder> {

    private List<Item> orderItemList;
    private final NumberFormat currencyFormatter;

    // Constructor chỉ cần List<Item> vì không có tương tác tăng/giảm
    public ThuNganAdapter(List<Item> orderItemList) {
        this.orderItemList = orderItemList;
        // Định dạng tiền tệ Việt Nam (VND)
        this.currencyFormatter = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        this.currencyFormatter.setMaximumFractionDigits(0);
    }

    // --- 1. ViewHolder Class ---
    public static class OrderItemViewHolder extends RecyclerView.ViewHolder {
        final TextView tvItemName;
        final TextView tvQuantity;
        final TextView tvSubtotal; // Tổng tiền của món đó (Giá * SL)

        public OrderItemViewHolder(@NonNull View itemView) {
            super(itemView);
            // Ánh xạ các ID đã thống nhất trong item_order.xml
            tvItemName = itemView.findViewById(R.id.tv_item_name);
            tvQuantity = itemView.findViewById(R.id.tv_item_quantity);
            tvSubtotal = itemView.findViewById(R.id.tv_item_subtotal);

            // Các ID không tồn tại trong item_order.xml (như tv_item_price, btn_increase, v.v.) đã được loại bỏ
        }
    }

    // --- 2. Adapter Overrides ---
    @NonNull
    @Override
    public OrderItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_order, parent, false);
        return new OrderItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderItemViewHolder holder, int position) {
        Item currentItem = orderItemList.get(position);

        // 1. Hiển thị thông tin
        // getMenuItem() được dùng để hiển thị Tên món ăn
        holder.tvItemName.setText(currentItem.getMenuItem());

        // Hiển thị số lượng kèm "x"
        holder.tvQuantity.setText("x" + currentItem.getQuantity());

        // Tính tổng tiền của món đó (Giá * Số lượng)
        double itemSubtotal = currentItem.getPrice() * currentItem.getQuantity();
        String formattedSubtotal = currencyFormatter.format(itemSubtotal);

        holder.tvSubtotal.setText(formattedSubtotal);

        // KHÔNG CÓ LOGIC XỬ LÝ CLICK/TƯƠNG TÁC nào ở đây
    }

    @Override
    public int getItemCount() {
        return orderItemList.size();
    }

    // --- 3. Hàm tiện ích để cập nhật dữ liệu ---
    public void updateList(List<Item> newList) {
        this.orderItemList = newList;
        notifyDataSetChanged();
    }
}