package com.ph48845.datn_qlnh_rmis.data.model;

import java.util.List;

public class Order {
    private String orderId;
    private String tableId;
    private String waiterId;
    private long createdAt;
    private boolean paid;
    private double totalAmount;
    private List<OrderItem> items;
    public String _id; // ObjectId từ MongoDB
    public int tableNumber;
    public String server;
    public String cashier;
    public double discount;
    public double finalAmount;
    public double paidAmount;
    public double change;
    public String paymentMethod;
    public String orderStatus;
    public List<String> mergedFrom;
    public List<String> splitTo;
    public String paidAt;

    public Order() {
    }

    // Getters & Setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getTableId() { return tableId; }
    public void setTableId(String tableId) { this.tableId = tableId; }

    public String getWaiterId() { return waiterId; }
    public void setWaiterId(String waiterId) { this.waiterId = waiterId; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    // Nested model for order items
    public static class OrderItem {
        private String menuItemId;
        private String menuItemName;
        private double price;
        private int quantity;

        public String menuItem; // ObjectId của món (MongoDB)
        public String status; // "pending", "preparing", "ready", "soldout"
        public String note;

        public OrderItem() {}

        public OrderItem(String menuItemId, String menuItemName, double price, int quantity, String status) {
            this.menuItemId = menuItemId;
            this.menuItemName = menuItemName;
            this.price = price;
            this.quantity = quantity;
            this.status = status;
        }

        public String getMenuItemId() { return menuItemId; }
        public void setMenuItemId(String menuItemId) { this.menuItemId = menuItemId; }

        public String getMenuItemName() { return menuItemName; }
        public void setMenuItemName(String menuItemName) { this.menuItemName = menuItemName; }

        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }

        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}

