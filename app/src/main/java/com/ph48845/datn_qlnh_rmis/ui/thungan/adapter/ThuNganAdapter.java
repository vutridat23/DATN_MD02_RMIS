package com.ph48845.datn_qlnh_rmis.ui.thungan.adapter;



import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;

import java.util.List;
import java.text.DecimalFormat;

public class ThuNganAdapter extends RecyclerView.Adapter<ThuNganAdapter.MenuViewHolder> {

    private List<MenuItem> menuItems;

    public ThuNganAdapter(List<MenuItem> menuItems) {
        this.menuItems = menuItems;
    }

    // Phương thức giúp cập nhật dữ liệu mới sau khi gọi API
    public void setMenuItems(List<MenuItem> newItems) {
        this.menuItems = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MenuViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_menu, parent, false);
        return new MenuViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MenuViewHolder holder, int position) {
        MenuItem item = menuItems.get(position);

        // Sử dụng DecimalFormat để định dạng giá cho dễ đọc
        DecimalFormat formatter = new DecimalFormat("#,### VNĐ");

        holder.tvName.setText(item.getName());
        holder.tvPrice.setText("Giá: " + formatter.format(item.getPrice()));
        holder.tvCategory.setText("Danh mục: " + item.getCategory());
        // TODO: Xử lý hiển thị ảnh món ăn nếu có trường ảnh
    }

    @Override
    public int getItemCount() {
        return menuItems.size();
    }

    // ViewHolder: Giữ các view để tái sử dụng
    public static class MenuViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvPrice, tvCategory;

        public MenuViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_menu_item_name);
            tvPrice = itemView.findViewById(R.id.tv_menu_item_price);
            tvCategory = itemView.findViewById(R.id.tv_menu_item_category);
        }
    }
}
