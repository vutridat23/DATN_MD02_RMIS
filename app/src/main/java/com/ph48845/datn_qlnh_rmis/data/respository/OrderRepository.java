package com.ph48845.datn_qlnh_rmis.data.respository;

import androidx.annotation.NonNull;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.ph48845.datn_qlnh_rmis.data.model.Order;

public class OrderRepository {

    private final DatabaseReference ordersRef;

    public interface OnOrderUpdateCallback {
        void onResult(boolean success);
    }

    public OrderRepository() {
        ordersRef = FirebaseDatabase.getInstance().getReference("orders");
    }

    /**
     * Cập nhật trạng thái thanh toán của đơn hàng
     * @param orderId ID đơn hàng
     * @param paid true nếu đã thanh toán
     * @param callback callback trả về kết quả
     */
    public void updateOrderStatus(@NonNull String orderId, boolean paid, @NonNull OnOrderUpdateCallback callback) {
        ordersRef.child(orderId).child("paid").setValue(paid)
                .addOnSuccessListener(unused -> callback.onResult(true))
                .addOnFailureListener(e -> callback.onResult(false));
    }

    /**
     * (Tùy chọn) Cập nhật toàn bộ đơn hàng sau khi tính toán lại hoặc sửa đổi.
     */
    public void updateFullOrder(@NonNull Order order, @NonNull OnOrderUpdateCallback callback) {
        ordersRef.child(order.getOrderId()).setValue(order)
                .addOnSuccessListener(unused -> callback.onResult(true))
                .addOnFailureListener(e -> callback.onResult(false));
    }
}
