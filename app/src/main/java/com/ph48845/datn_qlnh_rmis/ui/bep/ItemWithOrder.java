package com.ph48845.datn_qlnh_rmis.ui.bep;


import com.ph48845.datn_qlnh_rmis.data.model.Order;

/**
 * Wrapper that holds both parent Order and its OrderItem for adapter convenience.
 */
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