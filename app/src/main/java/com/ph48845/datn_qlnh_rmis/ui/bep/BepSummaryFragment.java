package com.ph48845.datn_qlnh_rmis.ui.bep;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.ui.bep.adapter.BepSummaryAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Right fragment: aggregated totals (all tables).
 * Each summary row has a "ĐÃ XONG" button which marks ALL items with that menu name as ready.
 */
public class BepSummaryFragment extends Fragment {

    private RecyclerView rv;
    private BepSummaryAdapter adapter;
    private OrderRepository orderRepository;

    public BepSummaryFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bep_summary, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        rv = view.findViewById(R.id.recycler_summary);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        orderRepository = new OrderRepository();

        adapter = new BepSummaryAdapter(new ArrayList<>(), entry -> {
            // user clicked ĐÃ XONG on one summary row: mark all matching items across all orders as ready
            if (entry == null) return;
            final String targetName = entry.getName();
            Toast.makeText(requireContext(), "Đang đánh dấu \"" + targetName + "\" là đã xong...", Toast.LENGTH_SHORT).show();

            // Fetch all orders and update matching items
            orderRepository.getOrdersByTableNumber(null, null, new OrderRepository.RepositoryCallback<List<Order>>() {
                @Override
                public void onSuccess(List<Order> orders) {
                    if (orders == null || orders.isEmpty()) {
                        requireActivity().runOnUiThread(() -> {
                            adapter.setProcessing(targetName, false);
                            Toast.makeText(requireContext(), "Không có đơn hàng.", Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }

                    // collect and update all matching items
                    final AtomicInteger totalToUpdate = new AtomicInteger(0);
                    final AtomicInteger finished = new AtomicInteger(0);
                    final AtomicInteger errors = new AtomicInteger(0);

                    for (Order o : orders) {
                        if (o == null) continue;
                        try { o.normalizeItems(); } catch (Exception ignored) {}
                        if (o.getItems() == null) continue;
                        for (Order.OrderItem it : o.getItems()) {
                            if (it == null) continue;
                            String name = (it.getMenuItemName() != null && !it.getMenuItemName().isEmpty()) ? it.getMenuItemName() : it.getName();
                            if (name == null) name = "";
                            String st = it.getStatus() == null ? "" : it.getStatus().trim().toLowerCase();
                            // match by name (case-sensitive kept as-is; could use .equalsIgnoreCase if preferred)
                            if (targetName.equals(name) && ("pending".equals(st) || "preparing".equals(st) || "processing".equals(st))) {
                                totalToUpdate.incrementAndGet();
                                Call<Void> call = orderRepository.updateOrderItemStatus(o.getId(), it.getMenuItemId(), "ready");
                                call.enqueue(new Callback<Void>() {
                                    @Override
                                    public void onResponse(Call<Void> call, Response<Void> response) {
                                        if (!response.isSuccessful()) errors.incrementAndGet();
                                        finished.incrementAndGet();
                                        checkFinish();
                                    }

                                    @Override
                                    public void onFailure(Call<Void> call, Throwable t) {
                                        errors.incrementAndGet();
                                        finished.incrementAndGet();
                                        checkFinish();
                                    }

                                    private void checkFinish() {
                                        if (finished.get() >= totalToUpdate.get()) {
                                            requireActivity().runOnUiThread(() -> {
                                                adapter.setProcessing(targetName, false);
                                                if (errors.get() == 0) {
                                                    Toast.makeText(requireContext(), "Đã đánh dấu tất cả \"" + targetName + "\" là đã xong.", Toast.LENGTH_SHORT).show();
                                                } else {
                                                    Toast.makeText(requireContext(), "Hoàn tất với " + errors.get() + " lỗi.", Toast.LENGTH_LONG).show();
                                                }
                                                // refresh activity lists if possible
                                                if (getActivity() instanceof BepActivity) {
                                                    try { ((BepActivity) getActivity()).refreshActiveTables(); } catch (Exception ignored) {}
                                                }
                                            });
                                        }
                                    }
                                });
                            }
                        }
                    }

                    if (totalToUpdate.get() == 0) {
                        requireActivity().runOnUiThread(() -> {
                            adapter.setProcessing(targetName, false);
                            Toast.makeText(requireContext(), "Không tìm thấy món \"" + targetName + "\" đang cần làm.", Toast.LENGTH_SHORT).show();
                        });
                    }
                }

                @Override
                public void onError(String message) {
                    requireActivity().runOnUiThread(() -> {
                        adapter.setProcessing(targetName, false);
                        Toast.makeText(requireContext(), "Lỗi khi lấy đơn hàng: " + message, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        rv.setAdapter(adapter);
    }

    /**
     * Update summary list. Accepts a list of SummaryEntry (name, qty, imageUrl).
     * We ensure list is sorted descending by qty before showing.
     */
    public void updateSummary(List<SummaryEntry> summary) {
        if (summary == null) summary = new ArrayList<>();
        // sort descending by qty just in case caller didn't
        summary.sort((a, b) -> Integer.compare(b.getQty(), a.getQty()));
        adapter.setItems(summary);
    }
}