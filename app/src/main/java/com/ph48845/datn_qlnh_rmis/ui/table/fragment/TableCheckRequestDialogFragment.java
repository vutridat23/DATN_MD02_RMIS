package com.ph48845.datn_qlnh_rmis.ui.table.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation. Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget. LinearLayoutManager;
import androidx. recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis. data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;
import com. ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis. ui.MainActivity;
import com. ph48845.datn_qlnh_rmis.ui. table.adapter.OrdersAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Dialog yêu cầu kiểm tra bàn:
 * - Chọn order từ bàn
 * - Gửi yêu cầu lên server để thu ngân xác nhận
 * - Body: { tableNumber, orderId, requestedBy, requestedByName }
 */
public class TableCheckRequestDialogFragment extends DialogFragment {

    private static final String ARG_TABLE_ID = "arg_table_id";
    private static final String ARG_TABLE_NUMBER = "arg_table_number";

    private String tableId;
    private int tableNumber;

    private OrderRepository orderRepository;
    private OrdersAdapter adapter;

    public static TableCheckRequestDialogFragment newInstance(String tableId, int tableNumber) {
        TableCheckRequestDialogFragment f = new TableCheckRequestDialogFragment();
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
        if (getArguments() != null) {
            tableId = getArguments().getString(ARG_TABLE_ID);
            tableNumber = getArguments().getInt(ARG_TABLE_NUMBER, 0);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View root = LayoutInflater.from(requireContext()).inflate(R.layout.layout_tam_tinh, null);
        RecyclerView rvOrders = root.findViewById(R.id. rvOrders);
        Button btnConfirm = root.findViewById(R.id.btnConfirm);
        btnConfirm.setText("Gửi yêu cầu");

        btnConfirm.setEnabled(false);

        adapter = new OrdersAdapter(order -> {
            btnConfirm.setEnabled(order != null && (order.getId() != null || order.getOrderId() != null));
        });
        rvOrders.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvOrders.setAdapter(adapter);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Yêu cầu kiểm tra bàn " + tableNumber);
        builder.setView(root);
        builder.setCancelable(true);
        final AlertDialog dlg = builder.create();

        // Load orders
        orderRepository.getOrdersByTableNumber(tableNumber, null, new OrderRepository. RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orders) {
                final List<Order> orderList = orders != null ? new ArrayList<>(orders) : new ArrayList<>();
                requireActivity().runOnUiThread(() -> {
                    adapter.submitList(orderList);
                    if (!orderList.isEmpty()) {
                        String firstId = orderList.get(0).getId();
                        if (firstId == null || firstId.isEmpty()) firstId = orderList.get(0).getOrderId();
                        adapter.setSelectedOrderId(firstId);
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
            String selectedOrderId = adapter.getSelectedOrderId();
            if (selectedOrderId == null || selectedOrderId. isEmpty()) {
                Toast.makeText(requireContext(), "Vui lòng chọn hóa đơn", Toast. LENGTH_SHORT).show();
                return;
            }

            btnConfirm.setEnabled(false);

            // Lấy thông tin user
            String requestedBy = null;
            String requestedByName = null;
            try {
                Context ctx = requireActivity();
                requestedBy = ctx.getSharedPreferences("RestaurantPrefs", Context.MODE_PRIVATE).getString("userId", null);
                requestedByName = ctx.getSharedPreferences("RestaurantPrefs", Context.MODE_PRIVATE).getString("fullName", null);
            } catch (Exception ignored) {}

            Map<String, Object> body = new HashMap<>();
            body.put("tableNumber", tableNumber);
            body.put("orderId", selectedOrderId);
            if (requestedBy != null) body.put("requestedBy", requestedBy);
            if (requestedByName != null) body.put("requestedByName", requestedByName);

            ApiService api = RetrofitClient.getInstance().getApiService();
            Call<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<Void>> call = api.requestTableCheck(body);
            call.enqueue(new Callback<com.ph48845.datn_qlnh_rmis. data.remote.ApiResponse<Void>>() {
                @Override
                public void onResponse(Call<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<Void>> call, Response<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<Void>> response) {
                    requireActivity().runOnUiThread(() -> {
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            Toast. makeText(requireContext(), "Đã gửi yêu cầu kiểm tra bàn", Toast.LENGTH_SHORT).show();
                            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).fetchTablesFromServer();
                            dlg.dismiss();
                        } else {
                            String msg = "Gửi yêu cầu thất bại";
                            if (response.errorBody() != null) {
                                try { msg += ": " + response.errorBody().string(); } catch (Exception ignored) {}
                            }
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                            btnConfirm.setEnabled(true);
                        }
                    });
                }

                @Override
                public void onFailure(Call<com.ph48845.datn_qlnh_rmis.data.remote.ApiResponse<Void>> call, Throwable t) {
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "Lỗi:  " + (t != null ? t.getMessage() : "unknown"), Toast.LENGTH_LONG).show();
                        btnConfirm.setEnabled(true);
                    });
                }
            });
        });

        return dlg;
    }
}