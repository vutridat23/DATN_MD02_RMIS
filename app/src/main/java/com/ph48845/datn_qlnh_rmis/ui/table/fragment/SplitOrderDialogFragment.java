package com.ph48845.datn_qlnh_rmis.ui.table.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;
import com.ph48845.datn_qlnh_rmis.ui.MainActivity;
import com.ph48845.datn_qlnh_rmis.ui.table.adapter.OrdersAdapter;
import com.ph48845.datn_qlnh_rmis.ui.table.adapter.TableRadioAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fragment-based dialog that:
 *  - shows list of orders on the table (radio list),
 *  - on confirm opens a dialog with table list (radio) to pick destination,
 *  - then updates the selected order's tableNumber (and tableId if available).
 *
 * Adjustments:
 *  - sort destination table list so AVAILABLE tables appear first, then by tableNumber asc
 *  - keep explicit "Xác nhận" button (dialog_select_table.xml) and enable it only after selection
 *
 * Note: fixed "variable must be final" issues by copying callback parameters into final locals
 * before using them in lambdas.
 */
public class SplitOrderDialogFragment extends DialogFragment {

    private static final String ARG_TABLE_ID = "arg_table_id";
    private static final String ARG_TABLE_NUMBER = "arg_table_number";

    private String tableId;
    private int tableNumber;

    private OrderRepository orderRepository;
    private TableRepository tableRepository;

    private OrdersAdapter ordersAdapter;
    private TableRadioAdapter tableAdapter;

    public static SplitOrderDialogFragment newInstance(String tableId, int tableNumber) {
        SplitOrderDialogFragment f = new SplitOrderDialogFragment();
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
        View root = LayoutInflater.from(requireContext()).inflate(R.layout.fragment_split_order_dialog, null);
        RecyclerView rvOrders = root.findViewById(R.id.rv_orders);
        Button btnConfirm = root.findViewById(R.id.btn_confirm_split);

        // disable confirm until user selects an order (adapter will enable)
        btnConfirm.setEnabled(false);

        ordersAdapter = new OrdersAdapter(order -> {
            // enable confirm when an order selected
            btnConfirm.setEnabled(order != null && (order.getId() != null || order.getOrderId() != null));
        });
        rvOrders.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvOrders.setAdapter(ordersAdapter);

        // Build dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setView(root);
        builder.setCancelable(true);
        final AlertDialog dlg = builder.create(); // declare final to be used in lambdas

        // Load orders for table
        orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orders) {
                // copy to final local to avoid reassigning parameter used in lambdas
                final List<Order> orderList = orders != null ? new ArrayList<>(orders) : new ArrayList<>();
                requireActivity().runOnUiThread(() -> {
                    // submit list
                    ordersAdapter.submitList(orderList);
                    // optional: preselect first
                    if (!orderList.isEmpty()) {
                        String firstId = orderList.get(0).getId();
                        if (firstId == null || firstId.isEmpty()) firstId = orderList.get(0).getOrderId();
                        ordersAdapter.setSelectedOrderId(firstId);
                        btnConfirm.setEnabled(true);
                    }
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
            String selectedOrderId = ordersAdapter.getSelectedOrderId();
            if (selectedOrderId == null || selectedOrderId.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng chọn một hóa đơn", Toast.LENGTH_SHORT).show();
                return;
            }
            // find selected order object via adapter getter
            Order selected = ordersAdapter.getOrderById(selectedOrderId);
            if (selected == null) {
                Toast.makeText(requireContext(), "Hóa đơn chọn không hợp lệ", Toast.LENGTH_SHORT).show();
                return;
            }
            // open table selection dialog
            showTableSelectionDialog(selected, dlg);
        });

        return dlg;
    }

    private void showTableSelectionDialog(Order order, AlertDialog parentDlg) {
        View root = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_select_table, null);
        RecyclerView rvTables = root.findViewById(R.id.rv_tables);
        Button btnConfirmTable = root.findViewById(R.id.btn_confirm_table);

        // initially disabled until a table is selected
        btnConfirmTable.setEnabled(false);

        tableAdapter = new TableRadioAdapter(table -> {
            // enable confirm button when a table is selected
            btnConfirmTable.setEnabled(table != null && table.getId() != null);
        });
        rvTables.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvTables.setAdapter(tableAdapter);

        AlertDialog.Builder b = new AlertDialog.Builder(requireContext());
        b.setView(root);
        b.setCancelable(true);
        final AlertDialog dlg = b.create(); // final here too

        // load tables
        tableRepository.getAllTables(new TableRepository.RepositoryCallback<List<TableItem>>() {
            @Override
            public void onSuccess(List<TableItem> tablesParam) {
                // copy to final local list to avoid reassigning parameter used in lambdas
                final List<TableItem> tables = tablesParam != null ? new ArrayList<>(tablesParam) : new ArrayList<>();
                requireActivity().runOnUiThread(() -> {
                    // Sort tables: first AVAILABLE then by tableNumber asc
                    Collections.sort(tables, new Comparator<TableItem>() {
                        @Override
                        public int compare(TableItem a, TableItem b) {
                            if (a == null && b == null) return 0;
                            if (a == null) return 1;
                            if (b == null) return -1;
                            // put AVAILABLE first
                            boolean aAvail = a.getStatus() == TableItem.Status.AVAILABLE;
                            boolean bAvail = b.getStatus() == TableItem.Status.AVAILABLE;
                            if (aAvail && !bAvail) return -1;
                            if (!aAvail && bAvail) return 1;
                            // else sort by tableNumber ascending (safely)
                            try {
                                return Integer.compare(a.getTableNumber(), b.getTableNumber());
                            } catch (Exception e) {
                                return String.valueOf(a.getTableNumber()).compareTo(String.valueOf(b.getTableNumber()));
                            }
                        }
                    });

                    // present sorted tables
                    tableAdapter.submitList(tables);
                    // preselect current table if present
                    for (TableItem t : tables) {
                        if (t != null && t.getTableNumber() == tableNumber) {
                            tableAdapter.setSelectedTableId(t.getId());
                            btnConfirmTable.setEnabled(true);
                            break;
                        }
                    }
                });
            }
            @Override
            public void onError(String message) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Lỗi tải bàn: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });

        btnConfirmTable.setOnClickListener(v -> {
            String selTableId = tableAdapter.getSelectedTableId();
            if (selTableId == null || selTableId.isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng chọn bàn đích", Toast.LENGTH_SHORT).show();
                return;
            }
            // find chosen TableItem using adapter getter
            TableItem chosen = tableAdapter.getTableById(selTableId);
            if (chosen == null) {
                Toast.makeText(requireContext(), "Bàn đích không hợp lệ", Toast.LENGTH_SHORT).show();
                return;
            }

            // Optional: confirm with user before performing move
            new AlertDialog.Builder(requireContext())
                    .setTitle("Xác nhận tách")
                    .setMessage("Bạn có chắc muốn tách hóa đơn này sang Bàn " + chosen.getTableNumber() + "?")
                    .setPositiveButton("Xác nhận", (confirmDlg, which) -> {
                        // perform update: change order's tableNumber (and tableId)
                        final Map<String, Object> updates = new HashMap<>();
                        updates.put("tableNumber", chosen.getTableNumber());
                        if (chosen.getId() != null) updates.put("tableId", chosen.getId());

                        String orderId = order.getId();
                        if (orderId == null || orderId.isEmpty()) orderId = order.getOrderId();
                        if (orderId == null || orderId.isEmpty()) {
                            Toast.makeText(requireContext(), "Không xác định được id hóa đơn", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        orderRepository.updateOrder(orderId, updates, new OrderRepository.RepositoryCallback<Order>() {
                            @Override
                            public void onSuccess(Order result) {
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(requireContext(), "Tách bàn thành công", Toast.LENGTH_SHORT).show();
                                    if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).fetchTablesFromServer();
                                    dlg.dismiss();
                                    // close parent dialog as well
                                    if (getDialog() != null) getDialog().dismiss();
                                });
                            }
                            @Override
                            public void onError(String message) {
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(requireContext(), "Cập nhật thất bại: " + message, Toast.LENGTH_LONG).show();
                                });
                            }
                        });
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });

        // Show dialog and limit its height so confirm button is always visible
        dlg.show();
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.75);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight);
        }
    }
}