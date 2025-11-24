package com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter;

import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * OrderAdapter: hiển thị Order.OrderItem với ảnh, tên, số lượng, giá, trạng thái.
 * Phiên bản này CHỈ sử dụng oi.getImageUrl() (image đã được gán từ menuItem trong deserializer).
 * Đã bổ sung phần rất nhẹ để hiển thị trường note (nếu tồn tại) trong tv_item_note.
 */
public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.VH> {

    private static final String TAG = "OrderAdapter";
    private List<Order.OrderItem> items = new ArrayList<>();
    public interface OnItemClick { void onItemClick(Order.OrderItem item); }
    private OnItemClick listener;
    private final DecimalFormat priceFmt = new DecimalFormat("#,###");

    // Fallback base (nếu server trả đường dẫn relative)
    private static final String FALLBACK_BASE = "http://192.168.1.84:3000";

    public OrderAdapter(List<Order.OrderItem> items, OnItemClick listener) {
        this.items = items != null ? items : new ArrayList<>();
        this.listener = listener;
    }

    public synchronized void setItems(List<Order.OrderItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Trả về copy của danh sách items hiện tại (an toàn cho thread).
     */
    public synchronized List<Order.OrderItem> getItems() {
        return new ArrayList<>(items);
    }

    /**
     * Cập nhật trạng thái (status) của 1 item theo menuId.
     * Trả về true nếu đã cập nhật được (item tồn tại trên UI).
     */
    public synchronized boolean updateItemStatus(String menuId, String status) {
        if (menuId == null || menuId.isEmpty()) return false;
        String st = status == null ? "" : status;

        // 1) match theo menuItemId
        for (int i = 0; i < items.size(); i++) {
            Order.OrderItem oi = items.get(i);
            if (oi == null) continue;
            String mid = safeString(oi.getMenuItemId());
            if (!mid.isEmpty() && mid.equals(menuId)) {
                oi.setStatus(st);
                notifyItemChanged(i);
                Log.d(TAG, "updateItemStatus by menuItemId: menuId=" + menuId + " -> status=" + st + " at pos=" + i);
                return true;
            }
        }

        // 2) match theo menuItemRaw (Object -> String)
        for (int i = 0; i < items.size(); i++) {
            Order.OrderItem oi = items.get(i);
            if (oi == null) continue;
            String raw = safeString(oi.getMenuItemRaw());
            if (!raw.isEmpty() && raw.equals(menuId)) {
                oi.setStatus(st);
                notifyItemChanged(i);
                Log.d(TAG, "updateItemStatus by menuItemRaw: menuId=" + menuId + " -> status=" + st + " at pos=" + i);
                return true;
            }
        }

        // 3) match theo tên (fallback)
        for (int i = 0; i < items.size(); i++) {
            Order.OrderItem oi = items.get(i);
            if (oi == null) continue;
            String name = safeString(oi.getMenuItemName());
            if (name.isEmpty()) name = safeString(oi.getName());
            if (!name.isEmpty() && name.equalsIgnoreCase(menuId)) {
                oi.setStatus(st);
                notifyItemChanged(i);
                Log.d(TAG, "updateItemStatus by name match (menuId used as name): menuId=" + menuId + " -> status=" + st + " at pos=" + i);
                return true;
            }
        }

        // 4) match theo imageUrl (fallback)
        for (int i = 0; i < items.size(); i++) {
            Order.OrderItem oi = items.get(i);
            if (oi == null) continue;
            String img = safeString(oi.getImageUrl());
            if (!img.isEmpty() && img.equals(menuId)) {
                oi.setStatus(st);
                notifyItemChanged(i);
                Log.d(TAG, "updateItemStatus by imageUrl: image=" + menuId + " -> status=" + st + " at pos=" + i);
                return true;
            }
        }

        Log.d(TAG, "updateItemStatus: no local item matched for menuId=" + menuId + " status=" + st);
        return false;
    }

    /**
     * Update / merge list items (dùng khi nhận payload toàn bộ items của order).
     * Cố gắng cập nhật từng phần để có animation (notifyItemChanged/Inserted).
     */
    public synchronized void updateOrReplaceItems(List<Order.OrderItem> newItems) {
        if (newItems == null) {
            setItems(null);
            return;
        }

        if (items.isEmpty()) {
            setItems(newItems);
            return;
        }

        List<Order.OrderItem> toAdd = new ArrayList<>();
        for (Order.OrderItem newOi : newItems) {
            if (newOi == null) continue;
            String mid = safeString(newOi.getMenuItemId());
            boolean found = false;
            for (int i = 0; i < items.size(); i++) {
                Order.OrderItem cur = items.get(i);
                if (cur == null) continue;
                String curMid = safeString(cur.getMenuItemId());
                if (!curMid.isEmpty() && curMid.equals(mid)) {
                    // update fields
                    cur.setQuantity(newOi.getQuantity());
                    cur.setPrice(newOi.getPrice());
                    cur.setMenuItemName(safeString(newOi.getMenuItemName()));
                    cur.setName(safeString(newOi.getName()));
                    cur.setImageUrl(safeString(newOi.getImageUrl()));
                    cur.setStatus(safeString(newOi.getStatus()));
                    notifyItemChanged(i);
                    found = true;
                    break;
                }
            }
            if (!found) toAdd.add(newOi);
        }

        if (!toAdd.isEmpty()) {
            int start = items.size();
            items.addAll(toAdd);
            notifyItemRangeInserted(start, toAdd.size());
        }
    }

    private String safeString(Object o) {
        if (o == null) return "";
        if (o instanceof String) return ((String) o).trim();
        try { return String.valueOf(o).trim(); } catch (Exception e) { return ""; }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Order.OrderItem oi = items.get(position);
        if (oi == null) return;

        try { oi.normalize(); } catch (Exception e) { Log.w(TAG, "normalize failed: " + e.getMessage()); }

        Log.d(TAG, "Binding item pos=" + position + " -> name=\"" + oi.getMenuItemName() + "\" imageUrl=\"" + oi.getImageUrl() + "\" price=" + oi.getPrice() + " qty=" + oi.getQuantity());

        String displayName = oi.getMenuItemName();
        if (displayName == null || displayName.trim().isEmpty()) displayName = oi.getName();
        if (displayName == null || displayName.trim().isEmpty()) displayName = "(Không tên)";
        holder.tvName.setText(displayName);

        int qty = oi.getQuantity() <= 0 ? 1 : oi.getQuantity();
        double unitPrice = oi.getPrice();
        double total = unitPrice * qty;
        holder.tvQty.setText("Số lượng: " + qty);
        holder.tvPrice.setText(priceFmt.format(total) + " VND");

        // CHỈ LẤY ẢNH TỪ oi.getImageUrl() (deserializer đã gán từ menuItem)
        String rawUrl = oi.getImageUrl();
        String imgUrl = normalizeImageUrl(rawUrl);

        if (imgUrl != null && !imgUrl.isEmpty()) {
            Glide.with(holder.ivThumb.getContext())
                    .load(imgUrl)
                    .centerCrop()
                    .placeholder(R.drawable.ic_menu_placeholder)
                    .error(R.drawable.ic_menu_placeholder)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.w(TAG, "Glide load failed. model=" + model + ", err=" + (e != null ? e.getMessage() : "null"));
                            return false;
                        }
                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    })
                    .into(holder.ivThumb);
        } else {
            holder.ivThumb.setImageResource(R.drawable.ic_menu_placeholder);
        }

        // ---- MINIMAL: read note and show it (if exists) ----
        String note = "";
        try {
            java.lang.reflect.Method gm = oi.getClass().getMethod("getNote");
            Object v = gm.invoke(oi);
            if (v != null) note = String.valueOf(v).trim();
        } catch (NoSuchMethodException nsme) {
            // fallback to read field "note"
            try {
                java.lang.reflect.Field f = oi.getClass().getDeclaredField("note");
                f.setAccessible(true);
                Object v = f.get(oi);
                if (v != null) note = String.valueOf(v).trim();
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}

        Log.d(TAG, "OrderAdapter: note for pos=" + position + " => \"" + note + "\"");

        if (note != null && !note.isEmpty()) {
            holder.tvNote.setVisibility(View.VISIBLE);
            holder.tvNote.setText("Ghi chú: " + note);
        } else {
            holder.tvNote.setVisibility(View.GONE);
        }
        // ---- end note handling ----

        String s = oi.getStatus() != null ? oi.getStatus().toLowerCase() : "preparing";
        if (s.contains("done") || s.contains("xong") || s.contains("completed") || s.contains("served") || s.contains("ready")) {
            holder.tvStatus.setText("Đã xong");
            holder.tvStatus.setBackgroundResource(R.drawable.badge_green_bg);
        } else if (s.contains("out") || s.contains("het") || s.contains("sold") || s.contains("unavailable")) {
            holder.tvStatus.setText("Đã hết");
            holder.tvStatus.setBackgroundResource(R.drawable.badge_red_bg);
        } else if (s.contains("cancel") || s.contains("huy")) {
            holder.tvStatus.setText("Đã huỷ");
            holder.tvStatus.setBackgroundResource(R.drawable.badge_red_bg);
        } else {
            holder.tvStatus.setText("Đang nấu");
            holder.tvStatus.setBackgroundResource(R.drawable.badge_yellow_bg);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(oi);
        });
    }

    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }

    private String normalizeImageUrl(String imageUrl) {
        if (imageUrl == null) return null;
        imageUrl = imageUrl.trim();
        if (imageUrl.isEmpty()) return null;
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) return imageUrl;
        if (!imageUrl.startsWith("/")) imageUrl = "/" + imageUrl;
        return FALLBACK_BASE + imageUrl;
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        TextView tvName, tvQty, tvPrice, tvStatus, tvNote;
        VH(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.iv_item_thumb);
            tvName = itemView.findViewById(R.id.tv_item_name);
            tvQty = itemView.findViewById(R.id.tv_item_qty);
            tvPrice = itemView.findViewById(R.id.tv_item_price);
            tvStatus = itemView.findViewById(R.id.tv_item_status);
            tvNote = itemView.findViewById(R.id.tv_item_note);
        }
    }
}