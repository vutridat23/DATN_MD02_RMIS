//package com.ph48845.datn_qlnh_rmis.ui.bep;
//
//import androidx.lifecycle.LiveData;
//import androidx.lifecycle.MutableLiveData;
//import androidx.lifecycle.ViewModel;
//
//import com.ph48845.datn_qlnh_rmis.data.model.Order;
//
//import java.util.ArrayList;
//import java.util.List;
//
//public class BepViewModel extends ViewModel {
//    private final MutableLiveData<List<Order.OrderItem>> bepItemsLiveData = new MutableLiveData<>(new ArrayList<>());
//    private List<Order> allOrders = new ArrayList<>(); // Lưu tất cả đơn để tìm kiếm
//
//    public void loadItemsForBep(List<Order> orders) {
//        if (orders != null) allOrders = orders;
//        List<Order.OrderItem> result = new ArrayList<>();
//        if (allOrders != null) {
//            for (Order order : allOrders) {
//                if (order != null && order.getItems() != null) {
//                    for (Order.OrderItem item : order.getItems()) {
//                        if (item != null && item.getStatus() != null &&
//                                (item.getStatus().equals("PENDING") || item.getStatus().equals("PREPARING"))) {
//                            result.add(item);
//                        }
//                    }
//                }
//            }
//        }
//        bepItemsLiveData.setValue(result);
//    }
//
//    public LiveData<List<Order.OrderItem>> getBepItemsLiveData() {
//        return bepItemsLiveData;
//    }
//
//    // Hàm đã hoàn chỉnh để tìm order cha dựa vào item con
//    public Order findOrderOfItem(Order.OrderItem item) {
//        if (item == null || allOrders == null) return null;
//        for (Order order : allOrders) {
//            if (order != null && order.getItems() != null && order.getItems().contains(item)) {
//                return order;
//            }
//        }
//        return null;
//    }
//
//    // Khi bếp cập nhật trạng thái món
//    public void updateOrderItemStatus(Order order, Order.OrderItem item, String newStatus) {
//        if (order == null || item == null || newStatus == null) return;
//        item.setStatus(newStatus);
//        // TODO: Lưu lại order vào database/repository nếu có
//        loadItemsForBep(allOrders); // làm mới lại danh sách sau khi cập nhật trạng thái
//    }
//}

package com.ph48845.datn_qlnh_rmis.ui.bep;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.remote.OrderApi;
import com.ph48845.datn_qlnh_rmis.data.respository.OrderRepository;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class BepViewModel extends ViewModel {
    private final OrderRepository orderRepository;
    private final MutableLiveData<List<Order.OrderItem>> bepItemsLiveData = new MutableLiveData<>(new ArrayList<>());
    private List<Order> allOrders = new ArrayList<>();

    public BepViewModel() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://192.168.25.2:3000/") // Đổi thành backend API của bạn
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        orderRepository = new OrderRepository(retrofit.create(OrderApi.class));
    }

    public void fetchOrders() {
        orderRepository.getAllOrders().enqueue(new Callback<List<Order>>() {
            public void onResponse(Call<List<Order>> call, Response<List<Order>> res) {
                allOrders = res.body();
                loadItemsForBep(allOrders);
            }
            public void onFailure(Call<List<Order>> call, Throwable t) {}
        });
    }

    public void loadItemsForBep(List<Order> orders) {
        List<Order.OrderItem> result = new ArrayList<>();
        if (orders != null) {
            for (Order order : orders) {
                if (order != null && order.items != null) {
                    for (Order.OrderItem item : order.items) {
                        if (item != null && item.status != null &&
                                (item.status.equals("pending") || item.status.equals("preparing"))) {
                            result.add(item);
                        }
                    }
                }
            }
        }
        bepItemsLiveData.setValue(result);
    }

    public LiveData<List<Order.OrderItem>> getBepItemsLiveData() {
        return bepItemsLiveData;
    }

    public void updateOrderItemStatus(Order order, Order.OrderItem item, String newStatus) {
        if (order == null || item == null || newStatus == null) return;
        orderRepository.updateOrderItemStatus(order._id, item.menuItem, newStatus)
                .enqueue(new Callback<Void>() {
                    public void onResponse(Call<Void> call, Response<Void> res) {
                        item.status = newStatus;
                        loadItemsForBep(allOrders);
                    }
                    public void onFailure(Call<Void> call, Throwable t) {}
                });
    }
}