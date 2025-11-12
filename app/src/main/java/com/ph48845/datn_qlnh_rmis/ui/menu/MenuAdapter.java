package com.ph48845.datn_qlnh_rmis.ui.menu;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.MenuItem; // Sử dụng lớp MenuItem đã định nghĩa

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Adapter cho RecyclerView hiển thị danh sách các món ăn trong MENU.
 * Xử lý sự kiện click để thêm món vào hóa đơn (thực hiện trong Activity/Fragment).
 */
public class MenuAdapter extends RecyclerView.Adapter<MenuAdapter.MenuItemViewHolder> {

    private List<MenuItem> menuItems;
    private OnMenuItemClickListener clickListener;
    private final NumberFormat currencyFormatter;

    // --- Interface xử lý sự kiện click (Activity/Fragment phải implement) ---
    public interface OnMenuItemClickListener {
        /**
         * Được gọi khi người dùng click vào một món ăn trong danh sách Menu.
         * @param item Đối tượng MenuItem đã được chọn.
         */
        void onMenuItemClick(MenuItem item);
    }

    // Constructor yêu cầu danh sách MenuItem và đối tượng lắng nghe
    public MenuAdapter(List<MenuItem> menuItems, OnMenuItemClickListener listener) {
        this.menuItems = menuItems;
        this.clickListener = listener;
        // Định dạng tiền tệ Việt Nam (VND)
        this.currencyFormatter = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        this.currencyFormatter.setMaximumFractionDigits(0);
    }

    // --- 1. ViewHolder Class ---
    // Giả định layout menu sử dụng ID: tv_menu_item_name, tv_menu_item_price, tv_menu_item_category
    public static class MenuItemViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvPrice;
        final TextView tvCategory;

        public MenuItemViewHolder(@NonNull View itemView) {
            super(itemView);
            // Ánh xạ ID từ layout Menu (Giả sử R.layout.item_menu)
            tvName = itemView.findViewById(R.id.tv_menu_item_name);
            tvPrice = itemView.findViewById(R.id.tv_menu_item_price);
            tvCategory = itemView.findViewById(R.id.tv_menu_item_category);
        }
    }

    // --- 2. Adapter Overrides ---
    @NonNull
    @Override
    public MenuItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Giả định layout file là item_menu.xml
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_menu, parent, false);
        return new MenuItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MenuItemViewHolder holder, int position) {
        MenuItem currentItem = menuItems.get(position);

        String formattedPrice = currencyFormatter.format(currentItem.getPrice());

        // Sử dụng Getters từ lớp MenuItem
        holder.tvName.setText(currentItem.getName());
        holder.tvPrice.setText("Giá: " + formattedPrice + " VNĐ");
        holder.tvCategory.setText("Danh mục: " + currentItem.getCategory());

        // Xử lý sự kiện click
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onMenuItemClick(currentItem);
            }
        });
    }

    @Override
    public int getItemCount() {
        return menuItems.size();
    }

    // --- 3. Hàm tiện ích để cập nhật dữ liệu (Giúp sửa lỗi 'setMenuItems') ---
    /**
     * Cập nhật danh sách MenuItem và làm mới RecyclerView.
     * @param newList Danh sách MenuItem mới.
     */
    public void updateList(List<MenuItem> newList) {
        this.menuItems = newList;
        notifyDataSetChanged();
    }
}