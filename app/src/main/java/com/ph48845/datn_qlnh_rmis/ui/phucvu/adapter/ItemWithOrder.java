package com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter;



import com.ph48845.datn_qlnh_rmis.data.model.Order;

public class ItemWithOrder {
    private final Order order;
    private final Order.OrderItem item;

    public ItemWithOrder(Order order, Order.OrderItem item) {
        this.order = order;
        this.item = item;
    }

    public Order getOrder() { return order; }
    public Order.OrderItem getItem() { return item; }
}
