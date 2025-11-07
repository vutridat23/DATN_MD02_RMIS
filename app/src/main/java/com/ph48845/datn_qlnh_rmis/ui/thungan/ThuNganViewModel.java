package com.ph48845.datn_qlnh_rmis.ui.thungan;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ph48845.datn_qlnh_rmis.data.model.Order;

/**
 * ViewModel cho màn hình Thu ngân — quản lý hóa đơn, tính tiền, giảm giá và cập nhật giao diện.
 */
public class ThuNganViewModel extends ViewModel {

    private final MutableLiveData<Order> orderLiveData = new MutableLiveData<>();

    // ✅ Trả về LiveData để Activity/Fragment có thể quan sát
    public LiveData<Order> getOrder() {
        return orderLiveData;
    }

    // ✅ Cập nhật đơn hàng hiện tại
    public void setOrder(Order order) {
        orderLiveData.setValue(order);
    }

    // ✅ Tính số tiền giảm giá theo phần trăm (vd: 10%)
    public double calculateDiscount(double total, double percent) {
        if (percent <= 0) return 0;
        return total * percent / 100.0;
    }

    // ✅ Tính thành tiền sau khi giảm
    public double calculateFinal(double total, double discount) {
        double finalAmount = total - discount;
        return Math.max(finalAmount, 0); // không âm
    }

    // ✅ Hàm cập nhật tổng tiền vào LiveData
    public void updateTotalAmount(double total) {
        Order order = orderLiveData.getValue();
        if (order != null) {
            order.setTotalAmount(total);
            orderLiveData.setValue(order);
        }
    }

    // ✅ Lấy tổng tiền hiện tại của hóa đơn
    public double getCurrentTotal() {
        Order order = orderLiveData.getValue();
        return (order != null) ? order.getTotalAmount() : 0;
    }
}
