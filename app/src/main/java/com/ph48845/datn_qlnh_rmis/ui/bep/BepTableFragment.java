package com.ph48845.datn_qlnh_rmis.ui.bep;

import android.os.Bundle;
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
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.ui.bep.adapter.BepAdapter;
import com.ph48845.datn_qlnh_rmis.ui.bep.adapter.OrderItemAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Left fragment: table list + per-table detail (in-place).
 */
public class BepTableFragment extends Fragment {

    private RecyclerView rvTables;
    private BepAdapter tableAdapter;

    // Detail views
    private View layoutDetail;
    private TextView tvDetailTitle;
    private ImageButton btnBackDetail;
    private RecyclerView rvDetailOrders;
    private OrderItemAdapter orderItemAdapter;

    // Data holders
    private List<TableItem> currentTables = new ArrayList<>();
    private Map<Integer, Integer> currentRemaining;
    private Map<Integer, Long> currentEarliest;
    private Map<Integer, List<ItemWithOrder>> perTableItems;

    private OnTableSelectedListener listener;

    private OrderRepository orderRepository;

    public interface OnTableSelectedListener {
        void onTableSelected(TableItem table);
    }

    public void setOnTableSelectedListener(OnTableSelectedListener l) {
        this.listener = l;
    }

    public BepTableFragment() { /* empty */ }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bep_table, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        rvTables = view.findViewById(R.id.rv_bep_tables);
        rvTables.setLayoutManager(new GridLayoutManager(requireContext(), 1));
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

        // IMPORTANT: create adapter with real update behavior (call API immediately)
        orderItemAdapter = new OrderItemAdapter(requireContext(), (wrapper, newStatus) -> {
            // gọi API cập nhật trạng thái ngay khi bấm
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

            orderRepository.updateOrderItemStatus(orderId, itemId, newStatus).enqueue(new retrofit2.Callback<Void>() {
                @Override
                public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                    if (getActivity() == null) return;
                    if (response.isSuccessful()) {
                        // Cập nhật local model ngay để UI phản hồi tức thì
                        item.setStatus(newStatus);
                        // Nếu là trạng thái hoàn tất/hết thì xóa start time của timer để UI phản hồi
                        String ns = newStatus == null ? "" : newStatus.trim().toLowerCase();
                        if ("ready".equals(ns) || "soldout".equals(ns) || "done".equals(ns) || "completed".equals(ns)) {
                            orderItemAdapter.clearStartTimeForItem(order, item);
                        }
                        // refresh adapter UI
                        getActivity().runOnUiThread(() -> {
                            orderItemAdapter.notifyDataSetChanged();
                            Toast.makeText(getActivity(), "Cập nhật trạng thái thành công", Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        getActivity().runOnUiThread(() -> Toast.makeText(getActivity(), "Cập nhật thất bại: HTTP " + response.code(), Toast.LENGTH_LONG).show());
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                    if (getActivity() == null) return;
                    getActivity().runOnUiThread(() -> Toast.makeText(getActivity(), "Lỗi mạng: " + (t.getMessage() != null ? t.getMessage() : ""), Toast.LENGTH_LONG).show());
                }
            });
        });

        rvDetailOrders.setLayoutManager(new GridLayoutManager(requireContext(), 1));
        rvDetailOrders.setAdapter(orderItemAdapter);

        btnBackDetail.setOnClickListener(v -> hideTableDetail());

        hideTableDetail();
    }

    @Override
    public void onStop() {
        super.onStop();
        // ensure timer stopped when fragment no longer visible
        if (orderItemAdapter != null) orderItemAdapter.stopTimer();
    }

    /**
     * Update tables (called by Activity).
     */
    public void updateTables(List<TableItem> tables, Map<Integer, Integer> remainingCounts, Map<Integer, Long> earliestTs, Map<Integer, List<ItemWithOrder>> perTableItems) {
        this.currentTables = tables != null ? tables : new ArrayList<>();
        this.currentRemaining = remainingCounts;
        this.currentEarliest = earliestTs;
        this.perTableItems = perTableItems;
        tableAdapter.updateList(this.currentTables, this.currentRemaining, this.currentEarliest);

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

        // Start per-item timer when showing detail so countdown runs continuously (independent of socket)
        orderItemAdapter.startTimer();
    }

    private void hideTableDetail() {
        layoutDetail.setVisibility(View.GONE);
        rvTables.setVisibility(View.VISIBLE);

        // Stop timer to avoid leaking handler ticks when detail hidden
        orderItemAdapter.stopTimer();
    }
}