package com.ph48845.datn_qlnh_rmis.data.repository;

import com.ph48845.datn_qlnh_rmis.data.model.Order;

import java.util.ArrayList;
import java.util.List;

public class RevenueRepository {

    private static final List<Order> revenueList = new ArrayList<>();

    // Callback để xử lý async
    public interface RepositoryCallback<T> {
        void onSuccess(T result);
        void onError(String message);
    }

    public void addRevenue(Order order) {
        if (order != null) {
            revenueList.add(order);
        }
    }

    public List<Order> getRevenue() {
        return revenueList;
    }

    public double getTotalRevenue() {
        double total = 0;
        for (Order o : revenueList) {
            total += o.getFinalAmount();
        }
        return total;
    }

    // Giả lập async thêm revenue (trong dự án thật có thể gọi API server)
    public void createRevenueFromOrder(Order order, RepositoryCallback<Void> callback) {
        if (order == null) {
            callback.onError("Order null");
            return;
        }

        try {
            addRevenue(order); // lưu local
            callback.onSuccess(null);
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }
}
