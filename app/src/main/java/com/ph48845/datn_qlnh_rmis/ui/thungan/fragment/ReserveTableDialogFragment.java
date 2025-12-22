package com.ph48845.datn_qlnh_rmis.ui.thungan.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;
import com.ph48845.datn_qlnh_rmis.ui.thungan.adapter.ReserveTableAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * DialogFragment hiển thị danh sách bàn trống để chọn đặt trước.
 * Danh sách được sắp xếp theo số bàn (tăng dần).
 */
public class ReserveTableDialogFragment extends DialogFragment implements ReserveTableAdapter.OnItemClickListener {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private ReserveTableAdapter adapter;
    private TableRepository tableRepository;
    private OnTablePickedListener callback;

    public interface OnTablePickedListener {
        void onTablePicked(TableItem table);
    }

    public static ReserveTableDialogFragment newInstance() {
        ReserveTableDialogFragment f = new ReserveTableDialogFragment();
        f.setCancelable(true);
        return f;
    }

    public void setOnTablePickedListener(OnTablePickedListener cb) {
        this.callback = cb;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Remove default title / background handling
        Dialog d = getDialog();
        if (d != null) {
            Window w = d.getWindow();
            if (w != null) {
                w.setBackgroundDrawableResource(android.R.color.transparent);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reserve_table, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        recyclerView = v.findViewById(R.id.rv_reserve_tables);
        progressBar = v.findViewById(R.id.progress_loading_tables);
        tvEmpty = v.findViewById(R.id.tv_no_tables);

        adapter = new ReserveTableAdapter(new ArrayList<>(), this);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        tableRepository = new TableRepository();
        loadAvailableTables();
    }

    private void loadAvailableTables() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        tableRepository.getAllTables(new TableRepository.RepositoryCallback<List<TableItem>>() {
            @Override
            public void onSuccess(List<TableItem> result) {
                if (getActivity() == null) return;
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    List<TableItem> emptyTables = new ArrayList<>();
                    if (result != null) {
                        for (TableItem t : result) {
                            if (t == null) continue;
                            boolean isAvailable = false;
                            try {
                                TableItem.Status st = t.getStatus();
                                if (st == TableItem.Status.AVAILABLE) isAvailable = true;
                            } catch (Exception ignored) {}
                            // fallback: if status name equals "available"
                            if (!isAvailable) {
                                try {
                                    String s = t.getStatus() != null ? t.getStatus().name() : "";
                                    if ("available".equalsIgnoreCase(s)) isAvailable = true;
                                } catch (Exception ignored) {}
                            }
                            if (isAvailable) emptyTables.add(t);
                        }
                    }

                    // SẮP XẾP: theo tableNumber tăng dần (an toàn khi tableNumber null/không hợp lệ)
                    Collections.sort(emptyTables, new Comparator<TableItem>() {
                        @Override
                        public int compare(TableItem a, TableItem b) {
                            if (a == null && b == null) return 0;
                            if (a == null) return 1;
                            if (b == null) return -1;
                            try {
                                return Integer.compare(a.getTableNumber(), b.getTableNumber());
                            } catch (Exception e) {
                                // fallback: so sánh chuỗi
                                String sa = String.valueOf(a.getTableNumber());
                                String sb = String.valueOf(b.getTableNumber());
                                return sa.compareTo(sb);
                            }
                        }
                    });

                    if (emptyTables.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        adapter.updateList(new ArrayList<>());
                    } else {
                        tvEmpty.setVisibility(View.GONE);
                        adapter.updateList(emptyTables);
                    }
                });
            }

            @Override
            public void onError(String message) {
                if (getActivity() == null) return;
                requireActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                    Toast.makeText(requireContext(), "Không thể tải danh sách bàn: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Override
    public void onItemClick(TableItem table) {
        // Gọi callback trả về Activity để xử lý đặt bàn thực tế
        if (callback != null) {
            callback.onTablePicked(table);
        } else {
            // fallback: hiển thị toast
            Toast.makeText(requireContext(), "Chọn bàn " + table.getTableNumber(), Toast.LENGTH_SHORT).show();
        }
        dismiss();
    }
}