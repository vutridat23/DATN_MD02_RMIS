package com.ph48845.datn_qlnh_rmis.domain.usecase;

import android.util.Log;

import com.ph48845.datn_qlnh_rmis.core.utils.DateUtils;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.respository.OrderRepository;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UseCase để tạo đơn hàng mới
 * Xử lý business logic: validate, generate ID, tính toán tổng tiền, v.v.
 */
public class CreateOrderUseCase {
    private final OrderRepository orderRepository;
    private final ExecutorService executorService;

    public CreateOrderUseCase(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    /**
     * Interface để callback kết quả
     */
    public interface Callback {
        void onSuccess(Order order);
        void onError(String error);
    }

    /**
     * Interface cho OrderItem - sẽ được map sang Order model khi Order được implement
     */
    public interface OrderItem {
        String getMenuItemId();
        String getMenuItemName();
        double getPrice();
        int getQuantity();
        String getStatus();
    }

    /**
     * Tạo đơn hàng mới
     * 
     * @param tableId ID của bàn
     * @param waiterId ID của nhân viên phục vụ
     * @param items Danh sách món ăn
     * @param callback Callback để nhận kết quả
     */
    public void execute(String tableId, String waiterId, 
                       List<OrderItem> items, 
                       Callback callback) {
        executorService.execute(() -> {
            try {
                // 1. Validate input
                ValidationResult validation = validateInput(tableId, waiterId, items);
                if (!validation.isValid) {
                    if (callback != null) {
                        callback.onError(validation.errorMessage);
                    }
                    return;
                }

                // 2. Tạo Order object với business logic
                Order order = createOrderObject(tableId, waiterId, items);

                // 3. Tính toán tổng tiền
                double totalAmount = calculateTotalAmount(items);
                order.setTotalAmount(totalAmount);

                // 4. Lưu vào Repository (Repository sẽ handle Room + API sync)
                // Giả định OrderRepository có method createOrder(Order, Callback)
                saveOrder(order, callback);

            } catch (Exception e) {
                Log.e("CreateOrderUseCase", "❌ Lỗi tạo đơn hàng: " + e.getMessage());
                if (callback != null) {
                    callback.onError("Lỗi khi tạo đơn hàng: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Validate input data
     */
    private ValidationResult validateInput(String tableId, String waiterId, 
                                          List<OrderItem> items) {
        if (tableId == null || tableId.trim().isEmpty()) {
            return new ValidationResult(false, "Mã bàn không được để trống");
        }

        if (waiterId == null || waiterId.trim().isEmpty()) {
            return new ValidationResult(false, "Mã nhân viên không được để trống");
        }

        if (items == null || items.isEmpty()) {
            return new ValidationResult(false, "Đơn hàng phải có ít nhất 1 món");
        }

        // Validate từng item
        for (OrderItem item : items) {
            if (item.getMenuItemId() == null || item.getMenuItemId().trim().isEmpty()) {
                return new ValidationResult(false, "Mã món ăn không hợp lệ");
            }
            if (item.getQuantity() <= 0) {
                return new ValidationResult(false, "Số lượng món phải lớn hơn 0");
            }
            if (item.getPrice() < 0) {
                return new ValidationResult(false, "Giá món không hợp lệ");
            }
        }

        return new ValidationResult(true, null);
    }

    /**
     * Tạo Order object với các thông tin cần thiết
     */
    private Order createOrderObject(String tableId, String waiterId, 
                                    List<OrderItem> items) {
        // Generate unique order ID
        String orderId = generateOrderId();
        
        // Tạo Order object
        // Note: Order model sẽ được implement sau, tạm thời tạo object rỗng
        // Khi Order model được implement, sẽ map OrderItem sang Order.OrderItem
        Order order = new Order();
        // TODO: Khi Order model được implement, uncomment các dòng sau:
         order.setOrderId(orderId);
         order.setTableId(tableId);
         order.setWaiterId(waiterId);
         //order.setItems(convertToOrderItems(items));
         order.setCreatedAt(DateUtils.getCurrentTimestamp());
         order.setPaid(false);

        return order;
    }

    /**
     * Generate unique order ID
     * Format: ORDER_YYYYMMDD_HHMMSS_UUID
     */
    private String generateOrderId() {
        String timestamp = DateUtils.formatCustom(
            DateUtils.getCurrentTimestamp(), 
            "yyyyMMdd_HHmmss"
        );
        String uuid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "ORDER_" + timestamp + "_" + uuid;
    }

    /**
     * Tính tổng tiền của đơn hàng
     */
    private double calculateTotalAmount(List<OrderItem> items) {
        double total = 0;
        for (OrderItem item : items) {
            total += item.getPrice() * item.getQuantity();
        }
        return total;
    }

    /**
     * Convert OrderItem interface sang Order.OrderItem (sẽ được implement khi Order model có)
     */
    // private List<Order.OrderItem> convertToOrderItems(List<OrderItem> items) {
    //     // TODO: Implement khi Order model được hoàn thiện
    //     return null;
    // }

    /**
     * Lưu đơn hàng thông qua Repository
     * Note: Giả định OrderRepository sẽ có method này
     */
    private void saveOrder(Order order, Callback callback) {
        // TODO: Khi OrderRepository được implement, sẽ gọi:
        // orderRepository.createOrder(order, new OrderRepository.Callback() {
        //     @Override
        //     public void onSuccess() {
        //         if (callback != null) callback.onSuccess(order);
        //     }
        //     @Override
        //     public void onError(String error) {
        //         if (callback != null) callback.onError(error);
        //     }
        // });
        
        // Tạm thời: giả định thành công
        Log.d("CreateOrderUseCase", "✅ Đã tạo đơn hàng: " + order.getOrderId());
        if (callback != null) {
            callback.onSuccess(order);
        }
    }

    /**
     * Inner class để chứa kết quả validation
     */
    private static class ValidationResult {
        boolean isValid;
        String errorMessage;

        ValidationResult(boolean isValid, String errorMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }
    }

    /**
     * Cleanup executor khi không cần dùng nữa
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
}
