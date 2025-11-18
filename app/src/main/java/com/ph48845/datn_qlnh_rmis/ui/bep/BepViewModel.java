package com.ph48845.datn_qlnh_rmis.ui.bep;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.remote.ApiService;
import com.ph48845.datn_qlnh_rmis.data.remote.RetrofitClient;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BepViewModel extends ViewModel {

    private final OrderRepository orderRepository;
    private final MutableLiveData<List<Order.OrderItem>> bepItemsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>(null);

    private List<Order> allOrders = new ArrayList<>();

    public BepViewModel() {
        ApiService api = RetrofitClient.getInstance().getApiService();
        orderRepository = new OrderRepository(api);
    }

    public void fetchOrders() {
        orderRepository.getAllOrders().enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<Order>> call, @NonNull Response<List<Order>> res) {
                if (res.isSuccessful() && res.body() != null) {
                    allOrders = res.body();
                } else {
                    allOrders = new ArrayList<>();
                    errorLiveData.setValue("Fetch orders failed: HTTP " + res.code());
                }
                loadItemsForBep(allOrders);
            }

            @Override
            public void onFailure(@NonNull Call<List<Order>> call, @NonNull Throwable t) {
                allOrders = new ArrayList<>();
                errorLiveData.setValue("Fetch orders error: " + (t.getMessage() != null ? t.getMessage() : "Network"));
                loadItemsForBep(allOrders);
            }
        });
    }

    public void loadItemsForBep(List<Order> orders) {
        List<Order.OrderItem> result = new ArrayList<>();
        if (orders != null) {
            for (Order order : orders) {
                if (order == null || order.items == null) continue;
                for (Order.OrderItem item : order.items) {
                    if (item == null || item.getStatus() == null) continue;
                    if (item.getStatus().equalsIgnoreCase("pending") || item.getStatus().equalsIgnoreCase("preparing")) {
                        result.add(item);
                    }
                }
            }
        }
        bepItemsLiveData.setValue(result);
    }

    public LiveData<List<Order.OrderItem>> getBepItemsLiveData() {
        return bepItemsLiveData;
    }

    public LiveData<String> getErrorLiveData() { return errorLiveData; }

    public void updateOrderItemStatus(Order order, Order.OrderItem item, String newStatus) {
        if (order == null || item == null || newStatus == null || newStatus.trim().isEmpty()) return;
        // Dùng order._id & item.menuItem (theo model hiện tại)
        orderRepository.updateOrderItemStatus(order._id, item.getMenuItemId(), newStatus)
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> res) {
                        if (res.isSuccessful()) {
                            item.setStatus(newStatus);
                            loadItemsForBep(allOrders);
                        } else {
                            errorLiveData.setValue("Update item status failed HTTP " + res.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        errorLiveData.setValue("Update item status error: " + (t.getMessage() != null ? t.getMessage() : "Network"));
                    }
                });
    }
}