package com.ph48845.datn_qlnh_rmis.ui.bep;


import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;


import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * BepViewModel with socket support and ItemWithOrder list for adapter.
 */
public class BepViewModel extends ViewModel {

    private final OrderRepository orderRepository;
    private final MutableLiveData<List<ItemWithOrder>> itemsLive = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loadingLive = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorLive = new MutableLiveData<>(null);

    private final SocketManager socketManager = SocketManager.getInstance();
    private List<Order> allOrders = new ArrayList<>();

    public BepViewModel() {
        this.orderRepository = new OrderRepository();
    }

    public LiveData<List<ItemWithOrder>> getItemsLive() { return itemsLive; }
    public LiveData<Boolean> getLoadingLive() { return loadingLive; }
    public LiveData<String> getErrorLive() { return errorLive; }

    /**
     * Fetch all orders and flatten to ItemWithOrder (parent order + item).
     */
    public void fetchOrders() {
        loadingLive.postValue(true);
        orderRepository.getOrdersByTableNumber(null, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> result) {
                loadingLive.postValue(false);
                allOrders = result != null ? result : new ArrayList<>();
                flattenAndPost(allOrders);
            }

            @Override
            public void onError(String message) {
                loadingLive.postValue(false);
                allOrders = new ArrayList<>();
                itemsLive.postValue(new ArrayList<>());
                errorLive.postValue(message != null ? message : "Unknown error");
            }
        });
    }

    private void flattenAndPost(List<Order> orders) {
        List<ItemWithOrder> out = new ArrayList<>();
        if (orders != null) {
            for (Order o : orders) {
                if (o == null) continue;
                try { o.normalizeItems(); } catch (Exception ignored) {}
                List<Order.OrderItem> its = o.getItems();
                if (its == null) continue;
                for (Order.OrderItem it : its) {
                    if (it == null) continue;
                    String s = it.getStatus() == null ? "" : it.getStatus().trim().toLowerCase();
                    if ("pending".equals(s) || "preparing".equals(s) || "processing".equals(s)) {
                        out.add(new ItemWithOrder(o, it));
                    }
                }
            }
        }
        itemsLive.postValue(out);
    }

    /**
     * Start realtime socket connection.
     * socketBaseUrl example: "http://192.168.1.84:3000"
     */
    public void startRealtime(@NonNull String socketBaseUrl) {
        socketManager.init(socketBaseUrl);
        socketManager.setOnEventListener(new SocketManager.OnEventListener() {
            @Override
            public void onOrderCreated(JSONObject payload) {
                // refresh when server signals a new order
                fetchOrders();
            }

            @Override
            public void onOrderUpdated(JSONObject payload) {
                fetchOrders();
            }

            @Override
            public void onConnect() { /* optional */ }

            @Override
            public void onDisconnect() { /* optional */ }

            @Override
            public void onError(Exception e) { errorLive.postValue("Socket error: " + (e != null ? e.getMessage() : "")); }
        });
        socketManager.connect();
    }

    public void stopRealtime() {
        socketManager.disconnect();
    }

    /**
     * Request to change item status (call update API on repository).
     * After success, update local list and repost.
     */
    public void changeItemStatus(ItemWithOrder wrapper, String newStatus) {
        if (wrapper == null || newStatus == null || newStatus.trim().isEmpty()) return;
        Order order = wrapper.getOrder();
        Order.OrderItem item = wrapper.getItem();
        if (order == null || item == null) return;
        String orderId = order.getId();
        String itemId = item.getMenuItemId();
        if (orderId == null || orderId.trim().isEmpty() || itemId == null || itemId.trim().isEmpty()) {
            errorLive.postValue("Invalid ids");
            return;
        }

        // call repo endpoint that returns Call<Void>
        orderRepository.updateOrderItemStatus(orderId, itemId, newStatus).enqueue(new retrofit2.Callback<Void>() {
            @Override
            public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                if (response.isSuccessful()) {
                    // update local in-memory object and repost
                    item.setStatus(newStatus);
                    flattenAndPost(allOrders);
                } else {
                    errorLive.postValue("Update failed: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                errorLive.postValue("Update error: " + (t.getMessage() != null ? t.getMessage() : "Network"));
            }
        });
    }

    @Override
    protected void onCleared() {
        stopRealtime();
        super.onCleared();
    }
}