package com.ph48845.datn_qlnh_rmis.data.model;




import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


public class Order implements Serializable {

    @SerializedName("_id")
    private String id;

    @SerializedName("tableNumber")
    private int tableNumber;

    // store references as String ids (ObjectId)
    @SerializedName("server")
    private String serverId;

    @SerializedName("cashier")
    private String cashierId;

    @SerializedName("items")
    private List<OrderItem> items = new ArrayList<>();

    @SerializedName("totalAmount")
    private double totalAmount;

    @SerializedName("discount")
    private double discount;

    @SerializedName("finalAmount")
    private double finalAmount;

    @SerializedName("paidAmount")
    private double paidAmount;

    @SerializedName("change")
    private double change;

    @SerializedName("paymentMethod")
    private String paymentMethod;

    @SerializedName("orderStatus")
    private String orderStatus;


    @SerializedName("mergedFrom")
    private List<String> mergedFrom = new ArrayList<>();

    @SerializedName("splitTo")
    private List<String> splitTo = new ArrayList<>();

    @SerializedName("createdAt")
    private String createdAt;

    @SerializedName("paidAt")
    private String paidAt;

    public Order() {}


    public Order(int tableNumber, String serverId, List<OrderItem> items,
                 double totalAmount, double discount, double finalAmount,
                 String paymentMethod, String orderStatus) {
        this.tableNumber = tableNumber;
        this.serverId = serverId;
        this.items = items;
        this.totalAmount = totalAmount;
        this.discount = discount;
        this.finalAmount = finalAmount;
        this.paymentMethod = paymentMethod;
        this.orderStatus = orderStatus;
    }

    // Getters / Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getTableNumber() { return tableNumber; }
    public void setTableNumber(int tableNumber) { this.tableNumber = tableNumber; }

    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }

    public String getCashierId() { return cashierId; }
    public void setCashierId(String cashierId) { this.cashierId = cashierId; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public double getDiscount() { return discount; }
    public void setDiscount(double discount) { this.discount = discount; }

    public double getFinalAmount() { return finalAmount; }
    public void setFinalAmount(double finalAmount) { this.finalAmount = finalAmount; }

    public double getPaidAmount() { return paidAmount; }
    public void setPaidAmount(double paidAmount) { this.paidAmount = paidAmount; }

    public double getChange() { return change; }
    public void setChange(double change) { this.change = change; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }

    public List<String> getMergedFrom() { return mergedFrom; }
    public void setMergedFrom(List<String> mergedFrom) { this.mergedFrom = mergedFrom; }

    public List<String> getSplitTo() { return splitTo; }
    public void setSplitTo(List<String> splitTo) { this.splitTo = splitTo; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getPaidAt() { return paidAt; }
    public void setPaidAt(String paidAt) { this.paidAt = paidAt; }

    @Override
    public String toString() {
        return "Order{" +
                "id='" + id + '\'' +
                ", tableNumber=" + tableNumber +
                ", serverId='" + serverId + '\'' +
                ", cashierId='" + cashierId + '\'' +
                ", items=" + items +
                ", totalAmount=" + totalAmount +
                ", discount=" + discount +
                ", finalAmount=" + finalAmount +
                ", paidAmount=" + paidAmount +
                ", change=" + change +
                ", paymentMethod='" + paymentMethod + '\'' +
                ", orderStatus='" + orderStatus + '\'' +
                ", mergedFrom=" + mergedFrom +
                ", splitTo=" + splitTo +
                ", createdAt='" + createdAt + '\'' +
                ", paidAt='" + paidAt + '\'' +
                '}';
    }

    /**
     * Inner class representing an item in the order.
     */
    public static class OrderItem implements Serializable {

        @SerializedName("menuItem")
        private String menuItemId; // ObjectId reference to menuModel

        @SerializedName("name")
        private String name; // snapshot of menu name at order time

        @SerializedName("quantity")
        private int quantity;

        @SerializedName("price")
        private double price;

        @SerializedName("status")
        private String status; // pending, preparing, done, cancelled

        public OrderItem() {}

        public OrderItem(String menuItemId, String name, int quantity, double price) {
            this.menuItemId = menuItemId;
            this.name = name;
            this.quantity = quantity;
            this.price = price;
            this.status = "pending";
        }

        // Getters / Setters
        public String getMenuItemId() { return menuItemId; }
        public void setMenuItemId(String menuItemId) { this.menuItemId = menuItemId; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }

        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        @Override
        public String toString() {
            return "OrderItem{" +
                    "menuItemId='" + menuItemId + '\'' +
                    ", name='" + name + '\'' +
                    ", quantity=" + quantity +
                    ", price=" + price +
                    ", status='" + status + '\'' +
                    '}';
        }
    }
}
