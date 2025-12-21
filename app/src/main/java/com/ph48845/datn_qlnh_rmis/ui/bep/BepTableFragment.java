package com.ph48845.datn_qlnh_rmis.ui.bep;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.ui.bep.adapter.BepAdapter;
import com.ph48845.datn_qlnh_rmis.ui.bep.adapter.OrderItemAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BepTableFragment extends Fragment {

    private static final String TAG = "BepTableFragment";
    private static final int CANCEL_TIMEOUT_MS = 10000;

    private RecyclerView rvTables;
    private BepAdapter tableAdapter;

    private View layoutDetail;
    private TextView tvDetailTitle;
    private ImageButton btnBackDetail;
    private RecyclerView rvDetailOrders;
    private OrderItemAdapter orderItemAdapter;

    private List<TableItem> currentTables = new ArrayList<>();
    private Map<Integer, Integer> currentRemaining;
    private Map<Integer, Long> currentEarliest;
    private Map<Integer, List<ItemWithOrder>> perTableItems;

    // NEW: attention tables (yellow + blink)
    private Set<Integer> attentionTables = new HashSet<>();

    private OnTableSelectedListener listener;
    private OrderRepository orderRepository;

    private final Map<String, AlertDialog> activeCancelDialogs = new HashMap<>();
    private final Map<String, Handler> cancelTimeoutHandlers = new HashMap<>();

    public interface OnTableSelectedListener {
        void onTableSelected(TableItem table);
    }

    public void setOnTableSelectedListener(OnTableSelectedListener l) {
        this.listener = l;
    }

    public BepTableFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bep_table, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        rvTables = view.findViewById(R.id.rv_bep_tables);
        rvTables.setLayoutManager(new GridLayoutManager(requireContext(), 3));

        tableAdapter = new BepAdapter(requireContext(), table -> {
            showTableDetail(table);
            if (listener != null) listener.onTableSelected(table);
        });
        rvTables.setAdapter(tableAdapter);

        layoutDetail = view.findViewById(R.id.layout_table_detail);
        tvDetailTitle = view.findViewById(R.id.tv_detail_title);
        btnBackDetail = view.findViewById(R.id.btn_back_detail);
        rvDetailOrders = view.findViewById(R.id.rv_table_orders);

        orderRepository = new OrderRepository();

        orderItemAdapter = new OrderItemAdapter(requireContext(), this::handleStatusChange);
        rvDetailOrders.setLayoutManager(new GridLayoutManager(requireContext(), 1));
        rvDetailOrders.setAdapter(orderItemAdapter);

        btnBackDetail.setOnClickListener(v -> hideTableDetail());
        hideTableDetail();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (orderItemAdapter != null) orderItemAdapter.stopTimer();
        dismissAllCancelDialogs();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        dismissAllCancelDialogs();
    }

    public void updateTables(List<TableItem> tables,
                             Map<Integer, Integer> remainingCounts,
                             Map<Integer, Long> earliestTs,
                             Map<Integer, List<ItemWithOrder>> perTableItems,
                             Set<Integer> attentionTables) {

        this.currentTables = tables != null ? tables : new ArrayList<>();
        this.currentRemaining = remainingCounts;
        this.currentEarliest = earliestTs;
        this.perTableItems = perTableItems;
        this.attentionTables = attentionTables != null ? attentionTables : new HashSet<>();

        tableAdapter.updateList(this.currentTables, this.currentRemaining, this.currentEarliest, this.attentionTables);

        checkForCancelRequests(perTableItems);

        if (layoutDetail.getVisibility() == View.VISIBLE) {
            String title = tvDetailTitle.getText() != null ? tvDetailTitle.getText().toString() : "";
            int shownTableNumber = parseTableNumberFromTitle(title);
            if (shownTableNumber > 0 && perTableItems != null && perTableItems.containsKey(shownTableNumber)) {
                List<ItemWithOrder> items = perTableItems.get(shownTableNumber);
                orderItemAdapter.setItems(items != null ? items : new ArrayList<>());
            } else {
                hideTableDetail();
            }
        }
    }

    // allow Activity to update attention set only
    public void setAttentionTables(Set<Integer> attentionTables) {
        this.attentionTables = attentionTables != null ? attentionTables : new HashSet<>();
        if (tableAdapter != null) tableAdapter.setAttentionTables(this.attentionTables);
    }

    private void handleStatusChange(ItemWithOrder wrapper, String newStatus) {
        // giữ nguyên logic của bạn (mình không thay đổi phần này)
        if (wrapper == null || newStatus == null) return;
        Order order = wrapper.getOrder();
        Order.OrderItem item = wrapper.getItem();
        if (order == null || item == null) return;

        String orderId = order.getId();
        String itemId = item.getMenuItemId();
        if (orderId == null || orderId.trim().isEmpty() || itemId == null || itemId.trim().isEmpty()) {
            if (getActivity() != null) Toast.makeText(getActivity(), "Không thể xác định order/item id", Toast.LENGTH_SHORT).show();
            return;
        }

        if (getActivity() != null) Toast.makeText(getActivity(), "Đang gửi yêu cầu cập nhật trạng thái...", Toast.LENGTH_SHORT).show();

        if ("preparing".equals(newStatus)) {
            double qty = item.getQuantity() <= 0 ? 1.0 : item.getQuantity();
            Call<ApiResponse<Void>> consumeCall = orderRepository.consumeRecipeCall(item.getMenuItemId(), qty, orderId);

            if (getActivity() != null) getActivity().runOnUiThread(() ->
                    Toast.makeText(getActivity(), "Đang kiểm tra và trừ nguyên liệu...", Toast.LENGTH_SHORT).show());

            consumeCall.enqueue(new Callback<ApiResponse<Void>>() {
                @Override
                public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                    try {
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            orderRepository.updateOrderItemStatus(orderId, itemId, newStatus).enqueue(new retrofit2.Callback<Void>() {
                                @Override
                                public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                                    if (getActivity() == null) return;
                                    if (response.isSuccessful()) {
                                        item.setStatus(newStatus);
                                        String ns = newStatus == null ? "" : newStatus.trim().toLowerCase();
                                        if ("ready".equals(ns) || "soldout".equals(ns) || "done".equals(ns) || "completed".equals(ns)) {
                                            orderItemAdapter.clearStartTimeForItem(order, item);
                                        }
                                        getActivity().runOnUiThread(() -> {
                                            orderItemAdapter.notifyDataSetChanged();
                                            Toast.makeText(getActivity(), "Bắt đầu làm món và trừ nguyên liệu thành công", Toast.LENGTH_SHORT).show();
                                        });
                                    } else {
                                        getActivity().runOnUiThread(() ->
                                                Toast.makeText(getActivity(), "Cập nhật trạng thái thất bại: HTTP " + response.code(), Toast.LENGTH_LONG).show());
                                    }
                                }

                                @Override
                                public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                                    if (getActivity() == null) return;
                                    getActivity().runOnUiThread(() ->
                                            Toast.makeText(getActivity(), "Lỗi mạng khi cập nhật trạng thái: " + (t.getMessage() != null ? t.getMessage() : ""), Toast.LENGTH_LONG).show());
                                }
                            });
                            return;
                        }

                        String errBody = null;
                        if (response.errorBody() != null) {
                            try { errBody = response.errorBody().string(); } catch (Exception ignored) { errBody = null; }
                        } else if (response.body() != null && response.body().getMessage() != null) {
                            showShortageDialog("Không thể trừ nguyên liệu", response.body().getMessage());
                            return;
                        }

                        if (errBody != null && !errBody.isEmpty()) parseAndShowShortages(errBody);
                        else showShortageDialog("Không thể trừ nguyên liệu", "Server trả về lỗi khi trừ nguyên liệu.");
                    } catch (Exception ex) {
                        Log.e(TAG, "consumeRecipe onResponse exception", ex);
                        showShortageDialog("Lỗi", "Lỗi xử lý phản hồi từ server: " + ex.getMessage());
                    }
                }

                @Override
                public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                    Log.e(TAG, "consumeRecipe onFailure", t);
                    if (getActivity() != null) getActivity().runOnUiThread(() ->
                            showShortageDialog("Lỗi kết nối", "Không thể kết nối tới server: " + (t.getMessage() != null ? t.getMessage() : "")));
                }
            });
        } else {
            orderRepository.updateOrderItemStatus(orderId, itemId, newStatus).enqueue(new retrofit2.Callback<Void>() {
                @Override
                public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                    if (getActivity() == null) return;
                    if (response.isSuccessful()) {
                        item.setStatus(newStatus);
                        String ns = newStatus == null ? "" : newStatus.trim().toLowerCase();
                        if ("ready".equals(ns) || "soldout".equals(ns) || "done".equals(ns) || "completed".equals(ns)) {
                            orderItemAdapter.clearStartTimeForItem(order, item);
                        }
                        getActivity().runOnUiThread(() -> {
                            orderItemAdapter.notifyDataSetChanged();
                            Toast.makeText(getActivity(), "Cập nhật trạng thái thành công", Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        getActivity().runOnUiThread(() ->
                                Toast.makeText(getActivity(), "Cập nhật thất bại: HTTP " + response.code(), Toast.LENGTH_LONG).show());
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getActivity(), "Lỗi mạng: " + (t.getMessage() != null ? t.getMessage() : ""), Toast.LENGTH_LONG).show());
                }
            });
        }
    }

    private void checkForCancelRequests(Map<Integer, List<ItemWithOrder>> perTableItems) {
        if (perTableItems == null || getActivity() == null) return;

        for (Map.Entry<Integer, List<ItemWithOrder>> entry : perTableItems.entrySet()) {
            List<ItemWithOrder> items = entry.getValue();
            if (items == null) continue;

            for (ItemWithOrder wrapper : items) {
                if (wrapper == null) continue;
                Order.OrderItem item = wrapper.getItem();
                if (item == null) continue;

                String status = item.getStatus();
                if (status != null && "cancel_requested".equalsIgnoreCase(status.trim())) {
                    showCancelRequestDialog(wrapper);
                }
            }
        }
    }

    private void showCancelRequestDialog(ItemWithOrder wrapper) {
        if (getActivity() == null) return;

        Order order = wrapper.getOrder();
        Order.OrderItem item = wrapper.getItem();
        if (order == null || item == null) return;

        String orderId = order.getId();
        String itemId = item.getMenuItemId();
        String key = orderId + ":" + itemId;

        if (activeCancelDialogs.containsKey(key)) return;

        String dishName = (item.getMenuItemName() != null && !item.getMenuItemName().isEmpty())
                ? item.getMenuItemName() : item.getName();

        getActivity().runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Yêu cầu hủy món")
                    .setMessage("Phục vụ yêu cầu hủy món \"" + dishName + "\". Xác nhận hủy?\n\n(Tự động từ chối sau 10 giây)")
                    .setCancelable(false)
                    .setPositiveButton("Xác nhận hủy", (dialog, which) -> {
                        cancelTimeoutHandler(key);
                        updateItemStatusToSoldout(wrapper);
                        activeCancelDialogs.remove(key);
                    })
                    .setNegativeButton("Từ chối", (dialog, which) -> {
                        cancelTimeoutHandler(key);
                        updateItemStatusToPending(wrapper);
                        activeCancelDialogs.remove(key);
                    });

            AlertDialog dialog = builder.create();
            activeCancelDialogs.put(key, dialog);
            dialog.show();

            Handler timeoutHandler = new Handler(Looper.getMainLooper());
            timeoutHandler.postDelayed(() -> {
                if (activeCancelDialogs.containsKey(key)) {
                    AlertDialog d = activeCancelDialogs.get(key);
                    if (d != null && d.isShowing()) d.dismiss();
                    activeCancelDialogs.remove(key);
                    updateItemStatusToPending(wrapper);
                }
                cancelTimeoutHandlers.remove(key);
            }, CANCEL_TIMEOUT_MS);

            cancelTimeoutHandlers.put(key, timeoutHandler);
        });
    }

    private void updateItemStatusToSoldout(ItemWithOrder wrapper) {
        if (wrapper == null) return;
        Order order = wrapper.getOrder();
        Order.OrderItem item = wrapper.getItem();
        if (order == null || item == null) return;

        String orderId = order.getId();
        String itemId = item.getMenuItemId();

        if (getActivity() != null) Toast.makeText(getActivity(), "Đang cập nhật trạng thái hủy món...", Toast.LENGTH_SHORT).show();

        orderRepository.updateOrderItemStatus(orderId, itemId, "soldout").enqueue(new retrofit2.Callback<Void>() {
            @Override
            public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                if (getActivity() == null) return;
                if (response.isSuccessful()) {
                    item.setStatus("soldout");
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getActivity(), "Đã xác nhận hủy món", Toast.LENGTH_SHORT).show();
                        refreshView();
                    });
                } else {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getActivity(), "Lỗi cập nhật: HTTP " + response.code(), Toast.LENGTH_LONG).show());
                }
            }

            @Override
            public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() ->
                        Toast.makeText(getActivity(), "Lỗi mạng: " + (t.getMessage() != null ? t.getMessage() : ""), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void updateItemStatusToPending(ItemWithOrder wrapper) {
        if (wrapper == null) return;
        Order order = wrapper.getOrder();
        Order.OrderItem item = wrapper.getItem();
        if (order == null || item == null) return;

        String orderId = order.getId();
        String itemId = item.getMenuItemId();

        if (getActivity() != null) Toast.makeText(getActivity(), "Đang từ chối hủy món...", Toast.LENGTH_SHORT).show();

        orderRepository.updateOrderItemStatus(orderId, itemId, "pending").enqueue(new retrofit2.Callback<Void>() {
            @Override
            public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                if (getActivity() == null) return;
                if (response.isSuccessful()) {
                    item.setStatus("pending");
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getActivity(), "Đã từ chối hủy món", Toast.LENGTH_SHORT).show();
                        refreshView();
                    });
                } else {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getActivity(), "Lỗi cập nhật: HTTP " + response.code(), Toast.LENGTH_LONG).show());
                }
            }

            @Override
            public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() ->
                        Toast.makeText(getActivity(), "Lỗi mạng: " + (t.getMessage() != null ? t.getMessage() : ""), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void cancelTimeoutHandler(String key) {
        Handler h = cancelTimeoutHandlers.remove(key);
        if (h != null) h.removeCallbacksAndMessages(null);
    }

    private void dismissAllCancelDialogs() {
        for (AlertDialog dialog : activeCancelDialogs.values()) {
            if (dialog != null && dialog.isShowing()) {
                try { dialog.dismiss(); } catch (Exception ignored) {}
            }
        }
        activeCancelDialogs.clear();

        for (Handler handler : cancelTimeoutHandlers.values()) {
            if (handler != null) handler.removeCallbacksAndMessages(null);
        }
        cancelTimeoutHandlers.clear();
    }

    private void refreshView() {
        if (getActivity() instanceof BepActivity) {
            try { ((BepActivity) getActivity()).refreshActiveTables(); } catch (Exception ignored) {}
        }
    }

    private int parseTableNumberFromTitle(String title) {
        if (title == null) return -1;
        String digits = title.replaceAll("[^0-9]", "");
        try { return digits.isEmpty() ? -1 : Integer.parseInt(digits); } catch (Exception e) { return -1; }
    }

    private void showTableDetail(TableItem table) {
        if (table == null) return;
        int tn = table.getTableNumber();
        tvDetailTitle.setText("Bàn " + tn + " - Danh sách món cần làm");

        List<ItemWithOrder> items = perTableItems != null ? perTableItems.get(tn) : null;
        if (items == null) items = new ArrayList<>();
        orderItemAdapter.setItems(items);

        layoutDetail.setVisibility(View.VISIBLE);
        rvTables.setVisibility(View.GONE);
        orderItemAdapter.startTimer();
    }

    private void hideTableDetail() {
        layoutDetail.setVisibility(View.GONE);
        rvTables.setVisibility(View.VISIBLE);
        orderItemAdapter.stopTimer();
    }

    private void parseAndShowShortages(String errBody) {
        if (getActivity() == null) return;
        try {
            JSONObject root = new JSONObject(errBody);
            String message = root.optString("message", "Không đủ nguyên liệu");
            JSONObject data = root.optJSONObject("data");
            JSONArray shortages = data != null ? data.optJSONArray("shortages") : null;

            StringBuilder details = new StringBuilder();
            if (shortages != null && shortages.length() > 0) {
                for (int i = 0; i < shortages.length(); i++) {
                    JSONObject s = shortages.optJSONObject(i);
                    if (s == null) continue;
                    String name = s.optString("ingredientName", s.optString("ingredientId", "Unknown"));
                    double required = s.optDouble("required", -1);
                    double available = s.optDouble("available", -1);
                    String unit = s.optString("unit", "");
                    details.append(name)
                            .append(" — cần: ").append(formatNumber(required)).append(" ").append(unit)
                            .append(", còn: ").append(formatNumber(available)).append(" ").append(unit);
                    if (i < shortages.length() - 1) details.append("\n");
                }
            } else {
                details.append(message);
            }

            showShortageDialog("Không thể trừ nguyên liệu", details.toString());
        } catch (JSONException e) {
            Log.w(TAG, "parseAndShowShortages JSON error", e);
            showShortageDialog("Không thể trừ nguyên liệu", errBody);
        }
    }

    private void showShortageDialog(String title, String body) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            try {
                AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
                b.setTitle(title);
                b.setMessage(body != null && !body.isEmpty() ? body : "Vui lòng kiểm tra kho");
                b.setPositiveButton("Mở kho", (d, w) -> {
                    try { startActivity(new Intent(getActivity(), NguyenLieuActivity.class)); } catch (Exception ignored) {}
                });
                b.setNegativeButton("Đóng", (d, w) -> {});
                b.show();
            } catch (Exception e) {
                Toast.makeText(getActivity(), title + ": " + (body != null ? body : ""), Toast.LENGTH_LONG).show();
            }
        });
    }

    private String formatNumber(double v) {
        if (v == (long) v) return String.valueOf((long) v);
        DecimalFormat df = new DecimalFormat("#.##");
        return df.format(v);
    }
}