package com.ph48845.datn_qlnh_rmis.ui.table.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;
import com.ph48845.datn_qlnh_rmis.ui.MainActivity;
import com.ph48845.datn_qlnh_rmis.ui.table.adapter.OrderGroupAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DialogFragment hiển thị toàn bộ món của các hóa đơn trên bàn, theo nhóm hóa đơn.
 * Người dùng tích chọn món, bấm Xác nhận -> tạo order mới (cùng bàn) với các món đã chọn,
 * và cập nhật các order gốc để loại bỏ các món đã tách.
 */
public class SplitItemsDialogFragment extends DialogFragment {

    private static final String ARG_TABLE_ID = "arg_table_id";
    private static final String ARG_TABLE_NUMBER = "arg_table_number";

    private String tableId;
    private int tableNumber;

    private OrderRepository orderRepository;
    private TableRepository tableRepository;

    private OrderGroupAdapter groupAdapter;

    public static SplitItemsDialogFragment newInstance(String tableId, int tableNumber) {
        SplitItemsDialogFragment f = new SplitItemsDialogFragment();
        Bundle b = new Bundle();
        b.putString(ARG_TABLE_ID, tableId);
        b.putInt(ARG_TABLE_NUMBER, tableNumber);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        orderRepository = new OrderRepository();
        tableRepository = new TableRepository();
        if (getArguments() != null) {
            tableId = getArguments().getString(ARG_TABLE_ID);
            tableNumber = getArguments().getInt(ARG_TABLE_NUMBER, 0);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View root = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_split_items, null);
        RecyclerView rvGroups = root.findViewById(R.id.rv_order_groups);
        Button btnConfirm = root.findViewById(R.id.btn_confirm_split_items);

        btnConfirm.setEnabled(false);

        groupAdapter = new OrderGroupAdapter(selectedCount -> {
            // enable confirm when at least one item selected
            btnConfirm.setEnabled(selectedCount > 0);
        });
        rvGroups.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvGroups.setAdapter(groupAdapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setView(root);
        builder.setCancelable(true);
        final AlertDialog dlg = builder.create();

        // load orders for table
        orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orders) {
                requireActivity().runOnUiThread(() -> {
                    groupAdapter.submitList(orders != null ? orders : new ArrayList<>());
                });
            }

            @Override
            public void onError(String message) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Lỗi tải hóa đơn: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });

        btnConfirm.setOnClickListener(v -> {
            // collect selected items grouped by source order
            Map<String, List<Order.OrderItem>> selectedByOrder = groupAdapter.getSelectedItemsGroupedByOrderId();
            if (selectedByOrder.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng chọn món để tách", Toast.LENGTH_SHORT).show();
                return;
            }

            // Build new order items (copies)
            List<Order.OrderItem> itemsForNewOrder = new ArrayList<>();
            for (List<Order.OrderItem> list : selectedByOrder.values()) {
                for (Order.OrderItem oi : list) {
                    Order.OrderItem copy = new Order.OrderItem();
                    copy.setMenuItemId(oi.getMenuItemId());
                    copy.setMenuItemName(oi.getMenuItemName());
                    copy.setName(oi.getName());
                    copy.setQuantity(oi.getQuantity());
                    copy.setPrice(oi.getPrice());
                    copy.setStatus(oi.getStatus());
                    copy.setNote(oi.getNote());
                    copy.setImageUrl(oi.getImageUrl());
                    copy.setCancelReason(oi.getCancelReason());
                    itemsForNewOrder.add(copy);
                }
            }

            if (itemsForNewOrder.isEmpty()) {
                Toast.makeText(requireContext(), "Không có món hợp lệ để tách", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create new order on same table
            Order newOrder = new Order();
            newOrder.setTableNumber(tableNumber);
            newOrder.setItems(itemsForNewOrder);

            // --- IMPORTANT: set required fields expected by server ---
            try {
                SharedPreferences prefs = requireActivity().getSharedPreferences("RestaurantPrefs", Context.MODE_PRIVATE);
                String uid = prefs.getString("userId", null);
                String cashierId = prefs.getString("cashierId", uid); // fallback to userId
                if (uid != null && !uid.isEmpty()) {
                    // existing codebase uses setServerId / setCashierId in other places
                    try { newOrder.setServerId(uid); } catch (Exception ignored) {}
                }
                if (cashierId != null && !cashierId.isEmpty()) {
                    try { newOrder.setCashierId(cashierId); } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}

            // set a default payment method required by server (adjust value if your backend expects different)
            try { newOrder.setPaymentMethod("cash"); } catch (Exception ignored) {}

            // prepare and create
            try {
                newOrder.prepareForCreate();
            } catch (Exception ignored) {}

            // Disable button to prevent double submit
            btnConfirm.setEnabled(false);

            // Create new order then update original orders to remove selected items
            orderRepository.createOrder(newOrder, new OrderRepository.RepositoryCallback<Order>() {
                @Override
                public void onSuccess(Order created) {
                    // after created, update each source order
                    final List<String> sourceOrderIds = new ArrayList<>(selectedByOrder.keySet());
                    final int total = sourceOrderIds.size();
                    final int[] finished = {0};
                    for (String srcId : sourceOrderIds) {
                        // find order in adapter to build remaining items
                        Order srcOrder = groupAdapter.getOrderById(srcId);
                        if (srcOrder == null) { finished[0]++; if (finished[0] >= total) finish(); continue; }

                        List<Order.OrderItem> remaining = new ArrayList<>();
                        List<Order.OrderItem> toRemove = selectedByOrder.getOrDefault(srcId, new ArrayList<>());
                        for (Order.OrderItem oi : srcOrder.getItems()) {
                            boolean remove = false;
                            for (Order.OrderItem rem : toRemove) {
                                String a = safeString(oi.getId());
                                String b = safeString(rem.getId());
                                if (!a.isEmpty() && !b.isEmpty() && a.equals(b)) { remove = true; break; }
                                if (safeString(oi.getMenuItemId()).equals(safeString(rem.getMenuItemId()))
                                        && oi.getQuantity() == rem.getQuantity()
                                        && Double.compare(oi.getPrice(), rem.getPrice()) == 0) { remove = true; break; }
                            }
                            if (!remove) remaining.add(oi);
                        }

                        Map<String, Object> updates = new HashMap<>();
                        List<Map<String,Object>> remainingMaps = new ArrayList<>();
                        for (Order.OrderItem r : remaining) {
                            try { remainingMaps.add(r.toMap()); } catch (Exception e) { /* fallback: ignore */ }
                        }
                        updates.put("items", remainingMaps);

                        String orderIdToUpdate = srcOrder.getId();
                        if (orderIdToUpdate == null || orderIdToUpdate.isEmpty()) orderIdToUpdate = srcOrder.getOrderId();

                        if (orderIdToUpdate == null || orderIdToUpdate.isEmpty()) {
                            finished[0]++;
                            if (finished[0] >= total) finish();
                            continue;
                        }

                        orderRepository.updateOrder(orderIdToUpdate, updates, new OrderRepository.RepositoryCallback<Order>() {
                            @Override
                            public void onSuccess(Order result) {
                                synchronized (finished) { finished[0]++; if (finished[0] >= total) finish(); }
                            }
                            @Override
                            public void onError(String message) {
                                synchronized (finished) { finished[0]++; if (finished[0] >= total) finish(); }
                            }
                        });
                    }

                    if (sourceOrderIds.isEmpty()) finish();
                }

                @Override
                public void onError(String message) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Tạo đơn mới thất bại: " + message, Toast.LENGTH_LONG).show();
                        btnConfirm.setEnabled(true);
                    });
                }

                private void finish() {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Tách hóa đơn thành công", Toast.LENGTH_SHORT).show();
                        if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).fetchTablesFromServer();
                        dlg.dismiss();
                    });
                }
            });
        });

        dlg.show();
        return dlg;
    }

    // small helper
    private String safeString(Object o) {
        if (o == null) return "";
        try { return String.valueOf(o).trim(); } catch (Exception e) { return ""; }
    }
}