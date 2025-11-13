package com.ph48845.datn_qlnh_rmis.ui.bep;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.remote.OrderApi;
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
        OrderApi api = RetrofitClient.getInstance().getOrderApi();
        orderRepository = new OrderRepository(api);
    }

    public void fetchOrders() {
        orderRepository.getAllOrders().enqueue(new Callback<List<Order>>() {
            @Override
            public void onResponse(Call<List<Order>> call, Response<List<Order>> res) {
                if (res.isSuccessful() && res.body() != null) {
                    allOrders = res.body();
                } else {
                    allOrders = new ArrayList<>();
                    errorLiveData.setValue("Fetch orders failed: HTTP " + res.code());
                }
                loadItemsForBep(allOrders);
            }

            @Override
            public void onFailure(Call<List<Order>> call, Throwable t) {
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
                    if (item == null || item.status == null) continue;
                    if (item.status.equalsIgnoreCase("pending") || item.status.equalsIgnoreCase("preparing")) {
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
        orderRepository.updateOrderItemStatus(order._id, item.menuItem, newStatus)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> res) {
                        if (res.isSuccessful()) {
                            item.status = newStatus;
                            loadItemsForBep(allOrders);
                        } else {
                            errorLiveData.setValue("Update item status failed HTTP " + res.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        errorLiveData.setValue("Update item status error: " + (t.getMessage() != null ? t.getMessage() : "Network"));
                    }
                });
    }
}