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
import java.util.ArrayList;
import java.util.List;

public class BepViewModel extends ViewModel {
    private final MutableLiveData<List<Order.OrderItem>> bepItemsLiveData = new MutableLiveData<>(new ArrayList<>());
    private List<Order> allOrders = new ArrayList<>();

    public BepViewModel() {
        // FAKE DATA mẫu sử dụng setter để test UI bếp

        // Đơn 1
        List<Order.OrderItem> items1 = new ArrayList<>();

        Order.OrderItem item1 = new Order.OrderItem();
        item1.setMenuItemName("Cơm chiên trứng");
        item1.setStatus("PENDING");
        items1.add(item1);

        Order.OrderItem item2 = new Order.OrderItem();
        item2.setMenuItemName("Mì xào bò");
        item2.setStatus("PREPARING");
        items1.add(item2);

        Order.OrderItem item3 = new Order.OrderItem();
        item3.setMenuItemName("Súp cua");
        item3.setStatus("PENDING");
        items1.add(item3);

        // Đơn 2
        List<Order.OrderItem> items2 = new ArrayList<>();

        Order.OrderItem item4 = new Order.OrderItem();
        item4.setMenuItemName("Phở bò");
        item4.setStatus("PENDING");
        items2.add(item4);

        Order.OrderItem item5 = new Order.OrderItem();
        item5.setMenuItemName("Bún chả");
        item5.setStatus("PREPARING");
        items2.add(item5);

        Order order1 = new Order();
        order1.setOrderId("Order01");
        order1.setItems(items1);

        Order order2 = new Order();
        order2.setOrderId("Order02");
        order2.setItems(items2);

        allOrders.add(order1);
        allOrders.add(order2);

        loadItemsForBep(allOrders);
    }

    public void loadItemsForBep(List<Order> orders) {
        if (orders != null) allOrders = orders;
        List<Order.OrderItem> result = new ArrayList<>();
        if (allOrders != null) {
            for (Order order : allOrders) {
                if (order != null && order.getItems() != null) {
                    for (Order.OrderItem item : order.getItems()) {
                        if (item != null && item.getStatus() != null &&
                                (item.getStatus().equals("PENDING") || item.getStatus().equals("PREPARING"))) {
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

    public Order findOrderOfItem(Order.OrderItem item) {
        if (item == null || allOrders == null) return null;
        for (Order order : allOrders) {
            if (order != null && order.getItems() != null && order.getItems().contains(item)) {
                return order;
            }
        }
        return null;
    }

    public void updateOrderItemStatus(Order order, Order.OrderItem item, String newStatus) {
        if (order == null || item == null || newStatus == null) return;
        item.setStatus(newStatus);
        loadItemsForBep(allOrders); // reload lại danh sách món đang chờ bếp làm
    }
}