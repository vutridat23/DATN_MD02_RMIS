package com.ph48845.datn_qlnh_rmis.ui. table. fragment;

import android.app. AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android. widget.Button;
import android. widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation. Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis. R;
import com.ph48845.datn_qlnh_rmis.data.model. TableItem;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;
import com. ph48845.datn_qlnh_rmis.data.repository.TableRepository;
import com.ph48845.datn_qlnh_rmis.ui.MainActivity;
import com.ph48845.datn_qlnh_rmis.ui.table.adapter.TableRadioAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util. Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * CASE 1: Tách bàn không tách hóa đơn
 * - Chọn bàn đích (available)
 * - Gọi API để 2 bàn trỏ về cùng 1 order
 * - Server sẽ xử lý logic:  cập nhật tableId/tableNumber trong order hoặc tạo reference
 */
public class SplitTableOnlyDialogFragment extends DialogFragment {

    private static final String ARG_TABLE_ID = "arg_table_id";
    private static final String ARG_TABLE_NUMBER = "arg_table_number";

    private String tableId;
    private int tableNumber;

    private TableRepository tableRepository;
    private TableRadioAdapter tableAdapter;

    public static SplitTableOnlyDialogFragment newInstance(String tableId, int tableNumber) {
        SplitTableOnlyDialogFragment f = new SplitTableOnlyDialogFragment();
        Bundle b = new Bundle();
        b.putString(ARG_TABLE_ID, tableId);
        b.putInt(ARG_TABLE_NUMBER, tableNumber);
        f.setArguments(b);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tableRepository = new TableRepository();
        if (getArguments() != null) {
            tableId = getArguments().getString(ARG_TABLE_ID);
            tableNumber = getArguments().getInt(ARG_TABLE_NUMBER, 0);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View root = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_select_table, null);
        RecyclerView rvTables = root.findViewById(R. id.rv_tables);
        Button btnConfirmTable = root.findViewById(R.id.btn_confirm_table);

        btnConfirmTable.setEnabled(false);

        tableAdapter = new TableRadioAdapter(table -> {
            btnConfirmTable.setEnabled(table != null && table.getId() != null);
        });
        rvTables.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvTables.setAdapter(tableAdapter);

        AlertDialog.Builder b = new AlertDialog.Builder(requireContext());
        b.setTitle("Tách bàn (không tách hóa đơn)");
        b.setView(root);
        b.setCancelable(true);
        final AlertDialog dlg = b.create();

        // Load tables (chỉ available)
        tableRepository.getAllTables(new TableRepository. RepositoryCallback<List<TableItem>>() {
            @Override
            public void onSuccess(List<TableItem> tablesParam) {
                final List<TableItem> tables = tablesParam != null ? new ArrayList<>(tablesParam) : new ArrayList<>();
                requireActivity().runOnUiThread(() -> {
                    // Filter:  chỉ available, không phải bàn hiện tại
                    List<TableItem> availableTables = new ArrayList<>();
                    for (TableItem t : tables) {
                        if (t == null) continue;
                        if (t.getTableNumber() == tableNumber) continue;
                        if (t. getStatus() == TableItem.Status.AVAILABLE) {
                            availableTables.add(t);
                        }
                    }

                    // Sort by tableNumber asc
                    Collections.sort(availableTables, new Comparator<TableItem>() {
                        @Override
                        public int compare(TableItem a, TableItem b) {
                            if (a == null && b == null) return 0;
                            if (a == null) return 1;
                            if (b == null) return -1;
                            try {
                                return Integer.compare(a.getTableNumber(), b.getTableNumber());
                            } catch (Exception e) {
                                return 0;
                            }
                        }
                    });

                    tableAdapter. submitList(availableTables);
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

            TableItem chosen = tableAdapter.getTableById(selTableId);
            if (chosen == null) {
                Toast.makeText(requireContext(), "Bàn đích không hợp lệ", Toast. LENGTH_SHORT).show();
                return;
            }

            // Confirm dialog
            new AlertDialog.Builder(requireContext())
                    .setTitle("Xác nhận tách bàn")
                    .setMessage("Tách bàn " + tableNumber + " sang bàn " + chosen.getTableNumber() + " (không tách hóa đơn)?")
                    .setPositiveButton("Xác nhận", (confirmDlg, which) -> {
                        performSplitTableOnly(chosen);
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        });

        dlg.show();
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.75);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight);
        }
        return dlg;
    }

    private void performSplitTableOnly(TableItem targetTable) {
        Map<String, Object> body = new HashMap<>();
        body.put("targetTableId", targetTable.getId());

        ApiService api = RetrofitClient.getInstance().getApiService();
        Call<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<Void>> call = api.splitTableOnly(tableId, body);
        call.enqueue(new Callback<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<Void>> call, Response<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<Void>> response) {
                requireActivity().runOnUiThread(() -> {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        Toast.makeText(requireContext(), "Tách bàn thành công", Toast. LENGTH_SHORT).show();
                        if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).fetchTablesFromServer();
                        dismiss();
                    } else {
                        String msg = "Tách bàn thất bại";
                        if (response.errorBody() != null) {
                            try { msg += ": " + response.errorBody().string(); } catch (Exception ignored) {}
                        }
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onFailure(Call<com.ph48845.datn_qlnh_rmis.data.remote. ApiResponse<Void>> call, Throwable t) {
                requireActivity().runOnUiThread(() -> {
                    Toast. makeText(requireContext(), "Lỗi:  " + (t != null ? t.getMessage() : "unknown"), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}