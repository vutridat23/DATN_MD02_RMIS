package com.ph48845.datn_qlnh_rmis.ui.table;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * DialogFragment hiển thị danh sách order của một bàn, cho phép chọn 1 order và xác nhận yêu cầu tạm tính.
 *
 * Lưu ý: fragment gọi trực tiếp Retrofit ApiService.
 */
public class TemporaryBillDialogFragment extends DialogFragment implements OrdersAdapter.OnOrderClickListener {

    public interface Listener {
        void onTemporaryBillRequested(Order order);
    }

    private static final String ARG_TABLE = "arg_table";

    private TableItem table;
    private OrdersAdapter adapter;
    private RecyclerView rvOrders;
    private Button btnConfirm;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private Order selectedOrder;
    private Listener listener;

    public TemporaryBillDialogFragment() {}

    public static TemporaryBillDialogFragment newInstance(TableItem table, Listener listener) {
        TemporaryBillDialogFragment f = new TemporaryBillDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_TABLE, table);
        f.setArguments(args);
        f.listener = listener;
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCancelable(true);
        if (getArguments() != null) {
            Object o = getArguments().getSerializable(ARG_TABLE);
            if (o instanceof TableItem) table = (TableItem) o;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.layout_tam_tinh, container, false);
        rvOrders = v.findViewById(R.id.rvOrders);
        btnConfirm = v.findViewById(R.id.btnConfirm);
        progressBar = v.findViewById(R.id.progressBar);
        tvEmpty = v.findViewById(R.id.tvEmpty);

        adapter = new OrdersAdapter(this);
        rvOrders.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvOrders.setAdapter(adapter);

        btnConfirm.setEnabled(false);
        btnConfirm.setOnClickListener(view -> onConfirmClicked());

        loadOrders();

        return v;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog d = getDialog();
        if (d != null) {
            Window w = d.getWindow();
            if (w != null) {
                int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
                w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        }
    }

    private void loadOrders() {
        if (table == null) {
            showEmpty("Bàn không hợp lệ");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        rvOrders.setVisibility(View.GONE);

        // Gọi API lấy orders của bàn (dùng Retrofit trực tiếp)
        ApiService api = RetrofitClient.getInstance().getApiService();
        Call<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<List<Order>>> call = api.getOrdersByTable(table.getTableNumber(), null);
        call.enqueue(new Callback<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<List<Order>>>() {
            @Override
            public void onResponse(Call<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<List<Order>>> call, Response<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<List<Order>>> response) {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<Order> orders = response.body().getData();
                    if (orders == null || orders.isEmpty()) {
                        showEmpty("Không có order");
                    } else {
                        adapter.submitList(orders);
                        rvOrders.setVisibility(View.VISIBLE);
                    }
                } else {
                    showEmpty("Không thể tải order");
                }
            }

            @Override
            public void onFailure(Call<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<List<Order>>> call, Throwable t) {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);
                showEmpty("Lỗi tải order: " + (t != null ? t.getMessage() : "unknown"));
            }
        });
    }

    private void showEmpty(String message) {
        rvOrders.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.VISIBLE);
        tvEmpty.setText(message);
    }

    private void onConfirmClicked() {
        if (selectedOrder == null) {
            Toast.makeText(requireContext(), "Vui lòng chọn order để yêu cầu tạm tính", Toast.LENGTH_SHORT).show();
            return;
        }

        btnConfirm.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        // Lấy user id (nếu lưu trong prefs), có thể null
        String requestedBy = null;
        try {
            requestedBy = requireActivity().getSharedPreferences("RestaurantPrefs", Context.MODE_PRIVATE).getString("userId", null);
        } catch (Exception ignored) {}

        Map<String, Object> body = new HashMap<>();
        if (requestedBy != null) body.put("requestedBy", requestedBy);

        ApiService api = RetrofitClient.getInstance().getApiService();
        Call<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<Order>> call = api.requestTempCalculation(selectedOrder.getId(), body);
        call.enqueue(new Callback<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<Order>>() {
            @Override
            public void onResponse(Call<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<Order>> call, Response<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<Order>> response) {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Order updated = response.body().getData();
                    Toast.makeText(requireContext(), "Đã gửi yêu cầu tạm tính", Toast.LENGTH_SHORT).show();
                    if (listener != null) listener.onTemporaryBillRequested(updated);
                    // dispatch local UI refresh via activity if needed
                    try {
                        if (getActivity() instanceof com.ph48845.datn_qlnh_rmis.ui.MainActivity) {
                            ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) getActivity()).fetchTablesFromServer();
                        }
                    } catch (Exception ignored) {}
                    dismiss();
                } else {
                    String msg = "Gửi yêu cầu thất bại";
                    if (response != null && response.errorBody() != null) {
                        try { msg += ": " + response.errorBody().string(); } catch (Exception ignored) {}
                    }
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                    btnConfirm.setEnabled(true);
                }
            }

            @Override
            public void onFailure(Call<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<Order>> call, Throwable t) {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);
                Toast.makeText(requireContext(), "Gửi yêu cầu thất bại: " + (t != null ? t.getMessage() : "unknown"), Toast.LENGTH_LONG).show();
                btnConfirm.setEnabled(true);
            }
        });
    }

    @Override
    public void onOrderClick(Order order) {
        this.selectedOrder = order;
        adapter.setSelectedOrderId(order != null ? order.getId() : null);
        btnConfirm.setEnabled(order != null);
    }
}