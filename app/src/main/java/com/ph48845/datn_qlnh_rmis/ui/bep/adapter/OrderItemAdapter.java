package com.ph48845.datn_qlnh_rmis. ui. bep. adapter;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget. RecyclerView;
import com.bumptech.glide. Glide;
import com. ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.ui.bep.ItemWithOrder;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util. Locale;
import java.util. Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adapter hiển thị các món trong detail view.
 * - Dimming when item is finished (ready/done/completed)
 * - Timer per item persisted to SharedPreferences
 *
 * Thay đổi:
 * - Tự động start timer khi adapter được gắn vào RecyclerView (onAttachedToRecyclerView)
 *   và dừng khi rời RecyclerView (onDetachedFromRecyclerView) -> đảm bảo countdown cập nhật liên tục
 *   mỗi giây khi list đang hiển thị.
 * - Thêm setter để cấu hình MAX_COOK_MS nếu cần.
 * - Thêm dialog xác nhận khi bấm nút hủy món
 */
public class OrderItemAdapter extends RecyclerView.Adapter<OrderItemAdapter.VH> {

    private static final String TAG = "OrderItemAdapter";
    private static final String PREFS_NAME = "cook_timers_prefs";
    private static final String PREF_KEY_PREFIX = "cook_start_";
    private static final long DEFAULT_MAX_COOK_MS = 1L * 60L * 1000L; // default = 1 phút
    private static final String TIMER_PAYLOAD = "payload_timer";

    public interface OnActionListener {
        void onChangeStatus(ItemWithOrder wrapper, String newStatus);
    }

    private final List<ItemWithOrder> items = new ArrayList<>();
    private final OnActionListener listener;
    private final Context context;
    private final DecimalFormat priceFmt = new DecimalFormat("#,###");

    private final Map<String, Long> startTimes = new HashMap<>();
    private RecyclerView attachedRecyclerView;

    private final android.os.Handler timerHandler = new android.os. Handler(android.os.Looper. getMainLooper());
    private final AtomicBoolean timerRunning = new AtomicBoolean(false);
    private long maxCookMs = DEFAULT_MAX_COOK_MS;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (attachedRecyclerView != null && attachedRecyclerView.getChildCount() > 0) {
                    int childCount = attachedRecyclerView.getChildCount();
                    for (int i = 0; i < childCount; i++) {
                        View child = attachedRecyclerView.getChildAt(i);
                        int pos = attachedRecyclerView.getChildAdapterPosition(child);
                        if (pos != RecyclerView.NO_POSITION) {
                            notifyItemChanged(pos, TIMER_PAYLOAD);
                        }
                    }
                } else {
                    // fallback:  notify full dataset so when view appears it's up-to-date
                    notifyDataSetChanged();
                }
            } catch (Exception e) {
                Log.w(TAG, "Timer tick error", e);
            } finally {
                if (timerRunning.get()) timerHandler.postDelayed(this, 1000L);
            }
        }
    };

    public OrderItemAdapter(Context ctx, OnActionListener listener) {
        if (ctx == null) throw new IllegalArgumentException("Context is required");
        this.context = ctx. getApplicationContext();
        this.listener = listener;
    }

    /**
     * Cấu hình thời gian tối đa cho phép (ms)
     */
    public void setMaxCookMs(long ms) {
        if (ms > 0) this.maxCookMs = ms;
    }

    public void startTimer() {
        if (timerRunning.compareAndSet(false, true)) timerHandler.postDelayed(timerRunnable, 1000L);
    }

    public void stopTimer() {
        if (timerRunning.compareAndSet(true, false)) timerHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.attachedRecyclerView = recyclerView;
        // Auto-start timer khi adapter gắn vào RecyclerView để countdown chạy liên tục
        startTimer();
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        if (this.attachedRecyclerView == recyclerView) this.attachedRecyclerView = null;
        // Stop timer khi rời RecyclerView để tránh leak
        stopTimer();
    }

    public void setItems(List<ItemWithOrder> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();

        for (ItemWithOrder w : items) {
            if (w == null) continue;
            Order.OrderItem oi = w.getItem();
            Order order = w.getOrder();
            String status = oi != null && oi.getStatus() != null ? oi.getStatus().trim().toLowerCase() : "";
            String key = buildItemKey(order, oi);

            if (prefs.contains(PREF_KEY_PREFIX + key)) {
                long persisted = prefs.getLong(PREF_KEY_PREFIX + key, -1L);
                if (persisted > 0) {
                    startTimes.put(key, persisted);
                    continue;
                }
            }

            if ("pending".equals(status) || "preparing".equals(status) || "processing".equals(status)) {
                if (! startTimes.containsKey(key)) {
                    startTimes.put(key, now);
                    prefs.edit().putLong(PREF_KEY_PREFIX + key, now).apply();
                }
            } else {
                if (startTimes.containsKey(key)) startTimes.remove(key);
                if (prefs.contains(PREF_KEY_PREFIX + key)) prefs.edit().remove(PREF_KEY_PREFIX + key).apply();
            }
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_bep_order, parent, false);

        if (v. getLayoutParams() instanceof ViewGroup. MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            int m = dpToPx(8);
            lp.setMargins(m, m, m, m);
            v.setLayoutParams(lp);
        }

        try {
            if (v instanceof ViewGroup && ((ViewGroup) v).getChildCount() > 0) {
                View inner = ((ViewGroup) v).getChildAt(0);
                GradientDrawable gd = new GradientDrawable();
                gd.setColor(Color. WHITE);
                gd.setCornerRadius(dpToPx(8));
                gd.setStroke(dpToPx(1), Color.parseColor("#CCCCCC"));
                inner.setBackground(gd);
                int pad = dpToPx(10);
                inner.setPadding(pad, pad, pad, pad);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to apply boxed background programmatically:  " + e.getMessage());
        }

        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        bindFull(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position, @NonNull List<Object> payloads) {
        if (payloads != null && payloads. size() == 1 && TIMER_PAYLOAD.equals(payloads.get(0))) {
            updateTimerUI(holder, position);
        } else {
            bindFull(holder, position);
        }
    }

    private void bindFull(@NonNull VH holder, int position) {
        ItemWithOrder wrapper = items.get(position);
        Order order = wrapper.getOrder();
        Order.OrderItem oi = wrapper.getItem();

        String name = oi.getMenuItemName() != null && !oi.getMenuItemName().isEmpty() ? oi.getMenuItemName() : oi.getName();
        holder.txtTenMon.setText(name);

        if (holder.txtTableInfo != null) {
            String tableText = "";
            if (order != null) {
                try {
                    int tn = order.getTableNumber();
                    if (tn > 0) tableText = "Bàn " + tn;
                } catch (Exception ignored) {}
            }
            holder.txtTableInfo.setVisibility(tableText.isEmpty() ? View.GONE : View.VISIBLE);
            holder.txtTableInfo.setText(tableText);
        }

        String note = oi.getNote() != null ? oi.getNote().trim() : "";
        if (!note.isEmpty()) {
            if (holder.txtNote != null) {
                holder.txtNote. setVisibility(View.VISIBLE);
                holder.txtNote. setText(note);
            }
        } else {
            if (holder.txtNote != null) {
                holder.txtNote.setVisibility(View.GONE);
                holder.txtNote.setText("");
            }
        }

        if (holder.txtQty != null) holder.txtQty.setText("Số lượng: " + oi.getQuantity());
        if (holder.txtTrangThai != null) holder.txtTrangThai.setText("Trạng thái: " + (oi.getStatus() == null ? "" : oi.getStatus()));

        String img = oi.getImageUrl();
        if (!TextUtils.isEmpty(img)) {
            Glide.with(holder.imgThumb.getContext())
                    .load(img)
                    .centerCrop()
                    .placeholder(R.drawable.ic_menu_placeholder)
                    .error(R.drawable.ic_menu_placeholder)
                    .into(holder.imgThumb);
        } else {
            holder.imgThumb.setImageResource(R.drawable.ic_menu_placeholder);
        }

        updateTimerUI(holder, position);

        holder.setButtonsEnabled(true);

        holder.btnDangLam.setOnClickListener(v -> {
            if (listener != null) listener.onChangeStatus(wrapper, "preparing");
        });
        holder.btnXongMon.setOnClickListener(v -> {
            if (listener != null) listener.onChangeStatus(wrapper, "ready");
            clearStartTimeForItem(order, oi);
        });
        holder.btnHetMon.setOnClickListener(v -> {
            // Hiển thị dialog xác nhận trước khi hủy món
            showCancelConfirmDialog(holder.itemView. getContext(), wrapper, order, oi);
        });
    }

    /**
     * Hiển thị dialog xác nhận khi bấm nút hủy món
     */
    private void showCancelConfirmDialog(Context ctx, ItemWithOrder wrapper, Order order, Order.OrderItem oi) {
        String itemName = oi.getMenuItemName() != null && !oi.getMenuItemName().isEmpty()
                ? oi.getMenuItemName()
                : oi.getName();

        new AlertDialog.Builder(ctx)
                .setTitle("Xác nhận hủy món")
                .setMessage("Bạn có chắc chắn muốn hủy món \"" + itemName + "\" không?")
                .setPositiveButton("Xác nhận", (dialog, which) -> {
                    // Thực hiện hủy món
                    if (listener != null) {
                        listener.onChangeStatus(wrapper, "soldout");
                    }
                    clearStartTimeForItem(order, oi);
                    dialog.dismiss();
                })
                .setNegativeButton("Hủy bỏ", (dialog, which) -> {
                    // Đóng dialog, không làm gì
                    dialog.dismiss();
                })
                .setCancelable(true)
                .show();
    }

    private void updateTimerUI(@NonNull VH holder, int position) {
        if (position < 0 || position >= items.size()) return;
        ItemWithOrder wrapper = items.get(position);
        Order order = wrapper.getOrder();
        Order.OrderItem oi = wrapper.getItem();
        String statusBase = oi. getStatus() == null ? "" : oi.getStatus();
        String lowerStatus = statusBase.trim().toLowerCase();
        String key = buildItemKey(order, oi);
        long now = System.currentTimeMillis();
        long start = -1L;

        if (startTimes.containsKey(key)) start = startTimes.get(key);
        else {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            if (prefs.contains(PREF_KEY_PREFIX + key)) {
                long persisted = prefs.getLong(PREF_KEY_PREFIX + key, -1L);
                if (persisted > 0) start = persisted;
            }
        }

        String timerText = "";
        boolean shouldWarn = false;
        if ("pending".equals(lowerStatus) || "preparing".equals(lowerStatus) || "processing".equals(lowerStatus)) {
            if (start <= 0) start = now;
            long elapsed = Math.max(0L, now - start);
            long remaining = maxCookMs - elapsed; // dùng maxCookMs có thể cấu hình
            if (remaining >= 0) {
                timerText = " - Thời gian còn:  " + formatDuration(remaining);
            } else {
                timerText = " - Quá giờ:  " + formatDuration(-remaining);
                shouldWarn = true;
            }
        } else {
            if (startTimes.containsKey(key)) startTimes.remove(key);
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            if (prefs.contains(PREF_KEY_PREFIX + key)) prefs.edit().remove(PREF_KEY_PREFIX + key).apply();
        }

        // Map status to Vietnamese display then show with timer text
        String statusDisplay = mapStatus(statusBase);
        if (statusDisplay == null) statusDisplay = statusBase;
        holder.txtTrangThai.setText("Trạng thái: " + statusDisplay + timerText);

        View rootInner = null;
        try {
            if (holder.itemView instanceof ViewGroup && ((ViewGroup) holder.itemView).getChildCount() > 0) {
                rootInner = ((ViewGroup) holder.itemView).getChildAt(0);
            }
        } catch (Exception ignored) {}

        if (shouldWarn) {
            if (rootInner != null) rootInner.setBackground(createRoundedDrawable(Color.parseColor("#FFF3E0"), Color.parseColor("#FF7043")));
            else holder.itemView.setBackgroundColor(Color.parseColor("#FFF3E0"));
        } else {
            if (rootInner != null) rootInner.setBackground(createRoundedDrawable(Color. WHITE, Color.parseColor("#CCCCCC")));
            else holder.itemView.setBackgroundColor(Color. TRANSPARENT);
        }

        boolean isFinished = false;
        if (lowerStatus.contains("ready") || lowerStatus.contains("done") || lowerStatus.contains("completed")) {
            isFinished = true;
        }

        float alpha = isFinished ? 0.45f : 1f;
        if (rootInner != null) rootInner.setAlpha(alpha); else holder.itemView.setAlpha(alpha);

        holder.setButtonsEnabled(! isFinished);
    }

    private GradientDrawable createRoundedDrawable(int fillColor, int strokeColor) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fillColor);
        gd.setCornerRadius(dpToPx(8));
        gd.setStroke(dpToPx(1), strokeColor);
        return gd;
    }

    /**
     * Make clearStartTimeForItem public so callers (e.g. fragment) can clear timer when item finished.
     */
    public void clearStartTimeForItem(Order order, Order.OrderItem oi) {
        String key = buildItemKey(order, oi);
        startTimes.remove(key);
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(PREF_KEY_PREFIX + key).apply();
    }

    @Override
    public int getItemCount() { return items.size(); }

    private static String buildItemKey(Order order, Order. OrderItem oi) {
        String orderId = (order != null && order.getId() != null) ? order.getId() : "o? ";
        String itemId = (oi != null && oi. getMenuItemId() != null && !oi.getMenuItemId().isEmpty()) ? oi.getMenuItemId() : ("i?" + (oi != null ? oi.getName() : "unknown"));
        return orderId + ":" + itemId;
    }

    private static String formatDuration(long ms) {
        long totalSec = Math.max(0L, ms / 1000L);
        long minutes = totalSec / 60L;
        long seconds = totalSec % 60L;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    /**
     * Map common english/raw statuses to Vietnamese display text.
     */
    private String mapStatus(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase();
        switch (s) {
            case "pending":
            case "wait":
            case "waiting":
                return "ĐANG CHỜ";
            case "preparing":
            case "in_progress":
            case "processing":
                return "ĐANG LÀM";
            case "ready":
            case "done":
            case "completed":
                return "HOÀN TẤT";
            case "soldout":
            case "out_of_stock":
                return "HẾT MÓN";
            case "canceled":
            case "cancelled":
                return "ĐÃ HỦY";
            default:
                String token = s.split("[_\\- ]+")[0];
                return token != null ? token. toUpperCase() : s. toUpperCase();
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView imgThumb;
        TextView txtTenMon, txtTableInfo, txtNote, txtQty, txtPrice, txtTrangThai;
        Button btnDangLam, btnXongMon, btnHetMon;

        VH(@NonNull View itemView) {
            super(itemView);
            imgThumb = itemView. findViewById(R.id.imgThumb);
            txtTenMon = itemView.findViewById(R.id.txtTenMon);

            txtNote = itemView.findViewById(R. id.txtNote);
            txtQty = itemView.findViewById(R.id.txtQty);
            txtPrice = itemView. findViewById(R.id.txtPrice);
            txtTrangThai = itemView.findViewById(R. id.txtTrangThai);

            btnDangLam = itemView.findViewById(R.id. btnDangLam);
            btnXongMon = itemView.findViewById(R.id.btnXongMon);
            btnHetMon = itemView. findViewById(R.id.btnHetMon);
        }

        void setButtonsEnabled(boolean enabled) {
            btnDangLam.setEnabled(enabled);
            btnXongMon. setEnabled(enabled);
            btnHetMon.setEnabled(enabled);
            float alpha = enabled ? 1f :  0.5f;
            btnDangLam.setAlpha(alpha);
            btnXongMon.setAlpha(alpha);
            btnHetMon.setAlpha(alpha);
        }
    }
}