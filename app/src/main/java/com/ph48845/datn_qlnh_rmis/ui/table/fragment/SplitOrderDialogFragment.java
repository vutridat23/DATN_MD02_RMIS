package com.ph48845.datn_qlnh_rmis.ui.table.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
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
import com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;
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

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * TÁCH BÀN – KHÔNG TÁCH HÓA ĐƠN
 * → Chia sẻ 1 orderId cho nhiều bàn
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

    private Order currentOrder; // 🔥 ORDER ĐANG ACTIVE

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
        View root = LayoutInflater.from(requireContext())
                .inflate(R.layout.fragment_split_order_dialog, null);

        RecyclerView rvOrders = root.findViewById(R.id.rv_orders);
        Button btnConfirm = root.findViewById(R.id.btn_confirm_split);
        btnConfirm.setEnabled(false);

        ordersAdapter = new OrdersAdapter(order -> {});
        rvOrders.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvOrders.setAdapter(ordersAdapter);

        AlertDialog dlg = new AlertDialog.Builder(requireContext())
                .setView(root)
                .setCancelable(true)
                .create();

        // 🔥 LOAD ORDER ĐANG ACTIVE (LẤY 1 ORDER)
        orderRepository.getOrdersByTableNumber(tableNumber, null,
                new OrderRepository.RepositoryCallback<List<Order>>() {
                    @Override
                    public void onSuccess(List<Order> orders) {
                        if (orders != null && !orders.isEmpty()) {
                            currentOrder = orders.get(0); // 🔥 CHỈ 1 ORDER
                            ordersAdapter.submitList(orders);
                            btnConfirm.setEnabled(true);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(requireContext(),
                                "Lỗi tải hóa đơn: " + message,
                                Toast.LENGTH_LONG).show();
                    }
                });

        btnConfirm.setOnClickListener(v -> showTableSelectionDialog(dlg));

        return dlg;
    }

    private void showTableSelectionDialog(AlertDialog parentDlg) {
        View root = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_select_table, null);

        RecyclerView rvTables = root.findViewById(R.id.rv_tables);
        Button btnConfirmTable = root.findViewById(R.id.btn_confirm_table);
        btnConfirmTable.setEnabled(false);

        tableAdapter = new TableRadioAdapter(table -> {
            btnConfirmTable.setEnabled(table != null);
        });

        rvTables.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvTables.setAdapter(tableAdapter);

        AlertDialog dlg = new AlertDialog.Builder(requireContext())
                .setView(root)
                .setCancelable(true)
                .create();

        tableRepository.getAllTables(new TableRepository.RepositoryCallback<List<TableItem>>() {
            @Override
            public void onSuccess(List<TableItem> tables) {
                Collections.sort(tables, Comparator.comparingInt(TableItem::getTableNumber));
                List<TableItem> filtered = new ArrayList<>();
                for (TableItem t : tables) {
                    if (t.getTableNumber() != tableNumber) {
                        filtered.add(t);
                    }
                }
                tableAdapter.submitList(filtered);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(requireContext(),
                        "Lỗi tải bàn: " + message,
                        Toast.LENGTH_LONG).show();
            }
        });

        btnConfirmTable.setOnClickListener(v -> {
            String selectedTableId = tableAdapter.getSelectedTableId();
            if (selectedTableId == null || selectedTableId.isEmpty() || currentOrder == null) {
                Toast.makeText(requireContext(), "Vui lòng chọn bàn đích", Toast.LENGTH_SHORT).show();
                return;
            }

            TableItem chosen = tableAdapter.getTableById(selectedTableId);
            if (chosen == null) {
                Toast.makeText(requireContext(), "Bàn đích không hợp lệ", Toast.LENGTH_SHORT).show();
                return;
            }


            new AlertDialog.Builder(requireContext())
                    .setTitle("Xác nhận tách bàn")
                    .setMessage("Chia sẻ hóa đơn sang Bàn " + chosen.getTableNumber())
                    .setPositiveButton("Xác nhận", (d, w) -> {
                        performShareOrder(chosen.getTableNumber(), dlg, parentDlg);
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });

        dlg.show();
    }

    /**
     * 🔥 SHARE ORDER → GỌI API ĐÚNG
     */
    private void performShareOrder(int toTableNumber,
                                   AlertDialog tableDialog,
                                   AlertDialog parentDialog) {

        Map<String, Object> body = new HashMap<>();
        body.put("orderId", currentOrder.getId());
        body.put("toTableNumber", toTableNumber);

        ApiService api = RetrofitClient.getInstance().getApiService();
        api.moveOrdersToTable(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {

                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {

                    Toast.makeText(requireContext(),
                            "Đã chia sẻ hóa đơn sang bàn " + toTableNumber,
                            Toast.LENGTH_SHORT).show();

                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).fetchTablesFromServer();
                    }

                    tableDialog.dismiss();
                    parentDialog.dismiss();
                } else {
                    Toast.makeText(requireContext(),
                            "Tách bàn thất bại",
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                Toast.makeText(requireContext(),
                        "Lỗi mạng: " + t.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }
}
