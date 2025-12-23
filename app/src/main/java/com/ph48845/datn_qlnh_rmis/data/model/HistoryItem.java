package com.ph48845.datn_qlnh_rmis.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class HistoryItem {

    @SerializedName("_id")
    private String id;

    @SerializedName("tableNumber")
    private int tableNumber;

    @SerializedName("createdAt")
    private String createdAt;

    @SerializedName("details")
    private Details details;

    public HistoryItem() {
    }

    // ====================
    // GETTER
    // ====================
    public String getId() {
        return id;
    }

    public Integer getTableNumber() {
        return tableNumber;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public Details getDetails() {
        return details;
    }

    // Tổng số món
    public int getTotalItems() {
        if (details == null || details.items == null)
            return 0;
        int sum = 0;
        for (Details.Item item : details.items) {
            sum += item.getQuantity();
        }
        return sum;
    }

    // Tổng tiền
    public Double getTotalAmount() {
        return details != null ? details.totalAmount : 0;
    }

    // Helper method để lấy danh sách items
    public List<OrderItemDetail> getItems() {
        if (details == null || details.items == null)
            return null;

        List<OrderItemDetail> orderItems = new java.util.ArrayList<>();
        for (Details.Item item : details.items) {
            OrderItemDetail orderItem = new OrderItemDetail();
            orderItem.setDishName(item.getMenuItemName());
            orderItem.setQuantity(item.getQuantity());
            orderItem.setPrice(item.getPrice());
            orderItem.setStatus(item.getStatus());
            orderItems.add(orderItem);
        }
        return orderItems;
    }

    // Inner class để adapter dễ sử dụng
    public static class OrderItemDetail {
        private String dishName;
        private int quantity;
        private double price;
        private String status;

        public String getDishName() {
            return dishName;
        }

        public void setDishName(String dishName) {
            this.dishName = dishName;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public double getPrice() {
            return price;
        }

        public void setPrice(double price) {
            this.price = price;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    // ====================
    // SETTER
    // ====================
    public void setId(String id) {
        this.id = id;
    }

    public void setTableNumber(int tableNumber) {
        this.tableNumber = tableNumber;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public void setDetails(Details details) {
        this.details = details;
    }

    // ====================
    // CLASS DETAILS
    // ====================
    public static class Details {

        @SerializedName("items")
        private List<Item> items;

        @SerializedName("totalAmount")
        private double totalAmount;

        @SerializedName("finalAmount")
        private double finalAmount;

        @SerializedName("paymentMethod")
        private String paymentMethod;

        @SerializedName("paidAt")
        private String paidAt;

        // GETTER
        public List<Item> getItems() {
            return items;
        }

        public double getTotalAmount() {
            return totalAmount;
        }

        public double getFinalAmount() {
            return finalAmount;
        }

        public String getPaymentMethod() {
            return paymentMethod;
        }

        public String getPaidAt() {
            return paidAt;
        }

        // SETTER
        public void setItems(List<Item> items) {
            this.items = items;
        }

        public void setTotalAmount(double totalAmount) {
            this.totalAmount = totalAmount;
        }

        public void setFinalAmount(double finalAmount) {
            this.finalAmount = finalAmount;
        }

        public void setPaymentMethod(String paymentMethod) {
            this.paymentMethod = paymentMethod;
        }

        public void setPaidAt(String paidAt) {
            this.paidAt = paidAt;
        }

        // ====================
        // CLASS ITEM
        // ====================
        public static class Item {

            @SerializedName("menuItemName")
            private String menuItemName;

            @SerializedName("quantity")
            private int quantity;

            @SerializedName("price")
            private double price;

            @SerializedName("status")
            private String status;

            // GETTER
            public String getMenuItemName() {
                return menuItemName;
            }

            public int getQuantity() {
                return quantity;
            }

            public double getPrice() {
                return price;
            }

            public String getStatus() {
                return status;
            }

            // SETTER
            public void setMenuItemName(String menuItemName) {
                this.menuItemName = menuItemName;
            }

            public void setQuantity(int quantity) {
                this.quantity = quantity;
            }

            public void setPrice(double price) {
                this.price = price;
            }

            public void setStatus(String status) {
                this.status = status;
            }
        }
    }
}
