package com.ph48845.datn_qlnh_rmis. ui.phucvu.fragment;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget. ProgressBar;
import android.widget. TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation. Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com. ph48845.datn_qlnh_rmis.ui. phucvu.adapter. TemporaryBillAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util. Comparator;
import java.util.List;

/**
 * Fragment hiển thị danh sách hóa đơn có trạng thái yêu cầu tạm tính
 */
public class TemporaryBillListFragment extends Fragment {

    private static final String TAG = "TempBillListFragment";

    private RecyclerView recyclerView;
    private TemporaryBillAdapter adapter;
    private ProgressBar progressBar;
    private TextView tvEmptyState;
    private OrderRepository orderRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_temporary_bill_list, container, false);

        // Initialize views
        recyclerView = view. findViewById(R.id.rv_temporary_bills);
        progressBar = view. findViewById(R.id.progress_bar);
        tvEmptyState = view.findViewById(R. id.tv_empty_state);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // ✅ Sửa lỗi: Khởi tạo ArrayList đúng cách
        List<Order> emptyList = new ArrayList<>();
        adapter = new TemporaryBillAdapter(emptyList, new TemporaryBillAdapter. OnOrderClickListener() {
            @Override
            public void onOrderClick(Order order) {
                // Handle click on order item - Mở InvoiceActivity
                if (order != null) {
                    try {
                        Intent intent = new Intent(getContext(), com.ph48845.datn_qlnh_rmis.ui.thungan.InvoiceActivity.class);
                        intent.putExtra("tableNumber", order.getTableNumber());
                        intent.putExtra("orderId", order.getId());
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to open InvoiceActivity", e);
                        Toast.makeText(getContext(), "Không thể mở hóa đơn:  " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        recyclerView.setAdapter(adapter);

        // Initialize repository
        orderRepository = new OrderRepository();

        // Load data
        loadTemporaryBills();

        return view;
    }

    private void loadTemporaryBills() {
        progressBar.setVisibility(View. VISIBLE);
        tvEmptyState.setVisibility(View. GONE);
        recyclerView.setVisibility(View.GONE);

        Log.d(TAG, "Loading temporary bills...");

        // ✅ Sử dụng method getTemporaryBillOrders từ OrderRepository
        orderRepository. getTemporaryBillOrders(new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> tempBillOrders) {
                if (getActivity() == null) return;

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View. GONE);

                        Log.d(TAG, "Found " + (tempBillOrders != null ?  tempBillOrders.size() : 0) + " temporary bills");

                        if (tempBillOrders == null || tempBillOrders. isEmpty()) {
                            showEmptyState("Không có hóa đơn tạm tính nào");
                        } else {
                            // Sắp xếp theo thời gian yêu cầu (mới nhất trước)
                            Collections.sort(tempBillOrders, new Comparator<Order>() {
                                @Override
                                public int compare(Order o1, Order o2) {
                                    String time1 = o1.getTempCalculationRequestedAt();
                                    String time2 = o2.getTempCalculationRequestedAt();
                                    if (time1 == null) return 1;
                                    if (time2 == null) return -1;
                                    return time2.compareTo(time1); // Mới nhất trước
                                }
                            });

                            showOrders(tempBillOrders);
                        }
                    }
                });
            }

            @Override
            public void onError(String message) {
                if (getActivity() == null) return;

                Log.e(TAG, "Failed to load temporary bills: " + message);

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.GONE);
                        showEmptyState("Lỗi:  " + message);
                        Toast.makeText(getContext(), "Lỗi tải dữ liệu:  " + message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void showEmptyState(String message) {
        tvEmptyState.setVisibility(View.VISIBLE);
        tvEmptyState.setText(message);
        recyclerView.setVisibility(View.GONE);
    }

    private void showOrders(List<Order> orders) {
        tvEmptyState.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        adapter.updateList(orders);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh data khi quay lại fragment
        Log.d(TAG, "onResume - reloading temporary bills");
        loadTemporaryBills();
    }
}