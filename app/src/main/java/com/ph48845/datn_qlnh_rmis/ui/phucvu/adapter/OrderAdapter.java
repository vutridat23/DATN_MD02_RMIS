package com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter;


import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
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
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.MenuLongPressHandler;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * OrderAdapter: hiển thị Order.OrderItem với ảnh, tên, số lượng, giá, trạng thái.
 *
 * Những thay đổi:
 * - Khi lưu/đọc lý do hủy vào NoteStore, dùng key composite có parentOrderId để tránh "lan" giữa bàn.
 * - Chỉ hiển thị lí do hủy khi status chỉ rõ là hủy/yêu cầu hủy hoặc cancelReason khớp với noteStore của cùng order.
 */
public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.VH> {

    private static final String TAG = "OrderAdapter";
    private List<Order.OrderItem> items = new ArrayList<>();
    public interface OnItemClick { void onItemClick(Order.OrderItem item); }
    private OnItemClick listener;
    private final DecimalFormat priceFmt = new DecimalFormat("#,###");

    // Note store (optional) — dùng để prefill và lưu lại lý do hủy theo menuId
    private final MenuLongPressHandler.NoteStore noteStore;

    // Fallback base (nếu server trả đường dẫn relative)
    private static final String FALLBACK_BASE = "http://192.168.1.84:3000";

    // Constructor updated: thêm noteStore (có thể truyền null nếu không cần)
    public OrderAdapter(List<Order.OrderItem> items, OnItemClick listener, MenuLongPressHandler.NoteStore noteStore) {
        this.items = items != null ? items : new ArrayList<>();
        this.listener = listener;
        this.noteStore = noteStore;
    }

    public synchronized void setItems(List<Order.OrderItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    public synchronized List<Order.OrderItem> getItems() {
        return new ArrayList<>(items);
    }

    /**
     * Cập nhật trạng thái item theo menuId hoặc itemSubId.
     */
    public synchronized boolean updateItemStatus(String menuId, String status) {
        if (menuId == null || menuId.isEmpty()) return false;
        String st = status == null ? "" : status;
        for (int i = 0; i < items.size(); i++) {
            Order.OrderItem oi = items.get(i);
            if (oi == null) continue;
            String mid = safeString(oi.getMenuItemId());
            String sid = safeString(oi.getId());
            if ((!mid.isEmpty() && mid.equals(menuId)) || (!sid.isEmpty() && sid.equals(menuId))) {
                oi.setStatus(st);
                notifyItemChanged(i);
                Log.d(TAG, "updateItemStatus matched: " + menuId + " -> " + st + " at pos=" + i);
                return true;
            }
        }
        return false;
    }

    /**
     * Áp dụng dữ liệu order trả về từ server. Hàm này sẽ tìm item tương ứng trong adapter và cập nhật
     * cancelReason, note, status (nếu server trả). Gọi phương thức này khi bạn tải lại order từ server
     * (ví dụ khi mở chi tiết order).
     */
    public synchronized void applyServerOrder(Order updatedOrder) {
        if (updatedOrder == null || updatedOrder.getItems() == null) return;
        for (Order.OrderItem serverItem : updatedOrder.getItems()) {
            if (serverItem == null) continue;
            String srvId = safeString(serverItem.getId());
            String srvMenuId = safeString(serverItem.getMenuItemId());
            for (int i = 0; i < items.size(); i++) {
                Order.OrderItem local = items.get(i);
                if (local == null) continue;
                String locId = safeString(local.getId());
                String locMenuId = safeString(local.getMenuItemId());
                boolean match = (!srvId.isEmpty() && srvId.equals(locId)) || (!srvMenuId.isEmpty() && srvMenuId.equals(locMenuId));
                if (match) {
                    boolean changed = false;
                    try {
                        if (serverItem.getCancelReason() != null && !serverItem.getCancelReason().trim().isEmpty()) {
                            local.setCancelReason(serverItem.getCancelReason().trim());
                            changed = true;
                        }
                    } catch (Exception ignored) {}
                    try {
                        if (serverItem.getNote() != null && !serverItem.getNote().trim().isEmpty()) {
                            local.setNote(serverItem.getNote().trim());
                            changed = true;
                        }
                    } catch (Exception ignored) {}
                    try {
                        if (serverItem.getStatus() != null && !serverItem.getStatus().trim().isEmpty()) {
                            local.setStatus(serverItem.getStatus().trim());
                            changed = true;
                        }
                    } catch (Exception ignored) {}
                    if (changed) notifyItemChanged(i);
                    break;
                }
            }
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
        // restore inflation with parent,false to keep layout params
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final VH holder, int position) {
        final Order.OrderItem oi = items.get(position);
        if (oi == null) return;

        try { oi.normalize(); } catch (Exception e) { Log.w(TAG, "normalize failed: " + e.getMessage()); }

        final String displayName;
        {
            String dn = oi.getMenuItemName();
            if (dn == null || dn.trim().isEmpty()) dn = oi.getName();
            if (dn == null || dn.trim().isEmpty()) dn = "(Không tên)";
            displayName = dn;
        }

        holder.tvName.setText(displayName);
        int qty = oi.getQuantity() <= 0 ? 1 : oi.getQuantity();
        holder.tvQty.setText("Số lượng: " + qty);
        holder.tvPrice.setText(priceFmt.format(oi.getPrice() * qty) + " VND");

        String imgUrl = normalizeImageUrl(oi.getImageUrl());
        if (imgUrl != null && !imgUrl.isEmpty()) {
            Glide.with(holder.ivThumb.getContext())
                    .load(imgUrl)
                    .centerCrop()
                    .placeholder(R.drawable.ic_menu_placeholder)
                    .error(R.drawable.ic_menu_placeholder)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.w(TAG, "Glide load failed: " + (e != null ? e.getMessage() : "null"));
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

        // Show note (original) and cancelReason (from API) separately
        String cancelReason = "";
        try {
            cancelReason = oi.getCancelReason() != null ? oi.getCancelReason().trim() : "";
        } catch (Exception ignored) {}
        String note = oi.getNote() != null ? oi.getNote().trim() : "";

        if (note != null && !note.isEmpty()) {
            holder.tvNote.setVisibility(View.VISIBLE);
            holder.tvNote.setText("Ghi chú: " + note);
        } else {
            holder.tvNote.setVisibility(View.GONE);
        }

        // Determine status first
        String s = oi.getStatus() != null ? oi.getStatus().toLowerCase() : "preparing";
        if (s.contains("done") || s.contains("ready") || s.contains("completed")) {
            holder.tvStatus.setText("Đã xong");
            holder.tvStatus.setBackgroundResource(R.drawable.badge_green_bg);
        } else if (s.contains("out") || s.contains("het") || s.contains("sold")) {
            holder.tvStatus.setText("Đã hết");
            holder.tvStatus.setBackgroundResource(R.drawable.badge_red_bg);
        } else if (s.contains("cancel") || s.contains("huy") || s.contains("cancel_requested")) {
            holder.tvStatus.setText("Yêu cầu hủy");
            holder.tvStatus.setBackgroundResource(R.drawable.badge_red_bg);
        } else {
            holder.tvStatus.setText("Đang nấu");
            holder.tvStatus.setBackgroundResource(R.drawable.badge_yellow_bg);
        }

        final String itemSubId = safeString(oi.getId());
        final String fallbackMenuItemId = safeString(oi.getMenuItemId());
        final String resolvedItemId = !itemSubId.isEmpty() ? itemSubId : fallbackMenuItemId;
        final String parentOrderId = safeString(oi.getParentOrderId());
        final Context ctx = holder.itemView.getContext();

        // Make composite key for noteStore: prefer parentOrderId if available to avoid cross-order leakage
        String compositeKey = makeCancelNoteKey(parentOrderId, fallbackMenuItemId, resolvedItemId);

        // Only display cancel reason if item status indicates cancellation/request OR the noteStore has the same saved reason for this order+item
        boolean showCancelReason = false;
        if (cancelReason != null && !cancelReason.isEmpty() && isCancelStatus(s)) {
            showCancelReason = true;
        } else {
            try {
                if (!compositeKey.isEmpty() && noteStore != null) {
                    String saved = noteStore.getNoteForMenu(compositeKey);
                    if (saved != null && !saved.isEmpty() && saved.equals(cancelReason) && !cancelReason.isEmpty()) {
                        showCancelReason = true;
                    }
                }
            } catch (Exception ignored) {}
        }

        if (showCancelReason) {
            holder.tvNote2.setVisibility(View.VISIBLE);
            holder.tvNote2.setText("Lí do hủy: " + cancelReason);
        } else {
            holder.tvNote2.setVisibility(View.GONE);
        }

        holder.itemView.setOnLongClickListener(view -> {
            PopupMenu popup = new PopupMenu(ctx, view);
            popup.getMenu().add(0, 1, 0, "Yêu cầu hủy món");
            popup.setOnMenuItemClickListener((MenuItem menuItem) -> {
                if (menuItem.getItemId() == 1) {
                    // Inflate dialog_cancel_reason.xml instead of programmatic EditText
                    View vInput = LayoutInflater.from(ctx).inflate(R.layout.dialog_cancel_reason, null);
                    final EditText input = vInput.findViewById(R.id.et_cancel_reason);
                    input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                    input.setHint("Nhập lý do hủy (tuỳ chọn)");

                    // Prefill từ noteStore nếu có (dùng composite key gồm parentOrderId nếu có)
                    String noteKeyForRead = makeCancelNoteKey(parentOrderId, fallbackMenuItemId, resolvedItemId);
                    try {
                        if (noteStore != null && noteKeyForRead != null && !noteKeyForRead.isEmpty()) {
                            String prev = noteStore.getNoteForMenu(noteKeyForRead);
                            if (prev != null && !prev.isEmpty()) input.setText(prev);
                        }
                    } catch (Exception ignored) {}

                    new AlertDialog.Builder(ctx)
                            .setTitle("Lý do hủy món")
                            .setView(vInput)
                            .setPositiveButton("Xác nhận", (d1, w1) -> {
                                String reason = input.getText() != null ? input.getText().toString().trim() : "";
                                new AlertDialog.Builder(ctx)
                                        .setTitle("Xác nhận gửi yêu cầu")
                                        .setMessage("Bạn có chắc muốn gửi yêu cầu hủy món \"" + displayName + "\" không?")
                                        .setPositiveButton("Có", (d2, w2) -> {
                                            if (parentOrderId == null || parentOrderId.trim().isEmpty()) {
                                                new AlertDialog.Builder(ctx)
                                                        .setTitle("Không thể gửi")
                                                        .setMessage("Không xác định được order chứa món này. Vui lòng vào chi tiết order.")
                                                        .setPositiveButton("OK", null)
                                                        .show();
                                                return;
                                            }
                                            if (resolvedItemId == null || resolvedItemId.trim().isEmpty()) {
                                                new AlertDialog.Builder(ctx)
                                                        .setTitle("Không thể gửi")
                                                        .setMessage("Không xác định được id món (không thể gửi yêu cầu).")
                                                        .setPositiveButton("OK", null)
                                                        .show();
                                                return;
                                            }

                                            holder.itemView.setEnabled(false);
                                            ApiService api = RetrofitClient.getInstance().getApiService();

                                            // Build body expected by server.requestCancelDish: { requestedBy, reason }
                                            Map<String, Object> body = new HashMap<>();
                                            try {
                                                String uid = ctx.getSharedPreferences("RestaurantPrefs", Context.MODE_PRIVATE).getString("userId", null);
                                                if (uid != null) body.put("requestedBy", uid);
                                            } catch (Exception ignored) {}
                                            body.put("reason", reason);

                                            Call<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<Order>> call =
                                                    api.requestCancelItem(parentOrderId, resolvedItemId, body);
                                            call.enqueue(new Callback<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<Order>>() {
                                                @Override
                                                public void onResponse(Call<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<Order>> call, Response<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<Order>> response) {
                                                    holder.itemView.setEnabled(true);
                                                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                                                        // Try to extract updated item from server response and update local oi
                                                        try {
                                                            Order updated = response.body().getData();
                                                            boolean applied = false;
                                                            if (updated != null && updated.getItems() != null) {
                                                                for (Order.OrderItem si : updated.getItems()) {
                                                                    String siId = si.getId();
                                                                    String siMenuId = si.getMenuItemId();
                                                                    if ((siId != null && !siId.isEmpty() && siId.equals(resolvedItemId))
                                                                            || (siMenuId != null && !siMenuId.isEmpty() && siMenuId.equals(resolvedItemId))) {
                                                                        // update local item fields from server truth
                                                                        try {
                                                                            if (si.getCancelReason() != null && !si.getCancelReason().isEmpty()) oi.setCancelReason(si.getCancelReason());
                                                                        } catch (Exception ignored) {}
                                                                        try {
                                                                            if (si.getNote() != null && !si.getNote().isEmpty()) oi.setNote(si.getNote());
                                                                        } catch (Exception ignored) {}
                                                                        try {
                                                                            if (si.getStatus() != null && !si.getStatus().isEmpty()) oi.setStatus(si.getStatus());
                                                                        } catch (Exception ignored) {}
                                                                        applied = true;
                                                                        break;
                                                                    }
                                                                }
                                                            }
                                                            // fallback: if server didn't return item, still set local cancelReason to entered reason so user sees it immediately
                                                            if (!applied) {
                                                                if (reason != null && !reason.isEmpty()) oi.setCancelReason(reason);
                                                                oi.setStatus("cancel_requested");
                                                            }

                                                            // Lưu lại lý do hủy vào noteStore dưới composite key (parentOrderId + item/menu id)
                                                            try {
                                                                if (noteStore != null) {
                                                                    String nk = makeCancelNoteKey(parentOrderId, fallbackMenuItemId, resolvedItemId);
                                                                    if (nk != null && !nk.isEmpty()) {
                                                                        noteStore.putNoteForMenu(nk, reason != null ? reason : "");
                                                                    }
                                                                }
                                                            } catch (Exception ignored) {}

                                                        } catch (Exception e) {
                                                            // fallback: set local reason
                                                            if (reason != null && !reason.isEmpty()) oi.setCancelReason(reason);
                                                            oi.setStatus("cancel_requested");
                                                            // still try to save into noteStore
                                                            try {
                                                                if (noteStore != null) {
                                                                    String nk = makeCancelNoteKey(parentOrderId, fallbackMenuItemId, resolvedItemId);
                                                                    if (nk != null && !nk.isEmpty()) {
                                                                        noteStore.putNoteForMenu(nk, reason != null ? reason : "");
                                                                    }
                                                                }
                                                            } catch (Exception ignored) {}
                                                        }

                                                        int pos = holder.getAdapterPosition();
                                                        if (pos >= 0) notifyItemChanged(pos);
                                                        new AlertDialog.Builder(ctx).setMessage("Đã gửi yêu cầu hủy món").setPositiveButton("OK", null).show();
                                                    } else {
                                                        String msg = "Gửi yêu cầu thất bại";
                                                        if (response != null && response.errorBody() != null) {
                                                            try { msg += ": " + response.errorBody().string(); } catch (Exception ignored) {}
                                                        }
                                                        new AlertDialog.Builder(ctx).setTitle("Lỗi").setMessage(msg).setPositiveButton("OK", null).show();
                                                    }
                                                }

                                                @Override
                                                public void onFailure(Call<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<Order>> call, Throwable t) {
                                                    holder.itemView.setEnabled(true);
                                                    new AlertDialog.Builder(ctx).setTitle("Lỗi").setMessage("Gửi yêu cầu thất bại: " + (t != null ? t.getMessage() : "unknown")).setPositiveButton("OK", null).show();
                                                }
                                            });
                                        })
                                        .setNegativeButton("Không", null)
                                        .show();
                            })
                            .setNegativeButton("Hủy", null)
                            .show();
                }
                return true;
            });
            popup.show();
            return true;
        });

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

    // Helper to determine whether a status string indicates cancellation/request-cancel
    private boolean isCancelStatus(String status) {
        if (status == null) return false;
        String l = status.toLowerCase();
        return l.contains("cancel") || l.contains("huy") || l.contains("cancel_requested") || l.contains("requested_cancel");
    }

    // Create composite cancel key: prefer parentOrderId if available to avoid cross-order leakage
    private String makeCancelNoteKey(String parentOrderId, String menuItemId, String resolvedItemId) {
        String id = (menuItemId != null && !menuItemId.trim().isEmpty()) ? menuItemId.trim() : (resolvedItemId != null ? resolvedItemId.trim() : "");
        if (id.isEmpty()) return "";
        if (parentOrderId != null && !parentOrderId.trim().isEmpty()) {
            return "cancel:" + parentOrderId.trim() + ":" + id;
        } else {
            return "cancel:" + id;
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView ivThumb;
        TextView tvName, tvQty, tvPrice, tvStatus, tvNote, tvNote2;

        VH(@NonNull View itemView) {
            super(itemView);
            ivThumb = itemView.findViewById(R.id.iv_item_thumb);
            tvName = itemView.findViewById(R.id.tv_item_name);
            tvQty = itemView.findViewById(R.id.tv_item_qty);
            tvPrice = itemView.findViewById(R.id.tv_item_price);
            tvStatus = itemView.findViewById(R.id.tv_item_status);
            tvNote = itemView.findViewById(R.id.tv_item_note);
            tvNote2 = itemView.findViewById(R.id.tv_item_huy);
        }
    }
}