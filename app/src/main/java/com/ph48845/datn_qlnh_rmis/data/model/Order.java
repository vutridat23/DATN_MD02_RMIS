package com.ph48845.datn_qlnh_rmis.data.model;

import java.util.List;

public class Order {
    private String _id;
    private int tableNumber;
    private String server; // ObjectId dạng chuỗi
    private String cashier;
    private List<Item> items;
    private double totalAmount;
    private double discount;
    private double finalAmount;
    private double paidAmount;
    private double change;
    private String paymentMethod;
    private String orderStatus;

    public Order(int tableNumber, String server, String cashier, List<Item> items,
                 double totalAmount, double discount, double finalAmount,
                 double paidAmount, double change, String paymentMethod, String orderStatus) {
        this.tableNumber = tableNumber;
        this.server = server;
        this.cashier = cashier;
        this.items = items;
        this.totalAmount = totalAmount;
        this.discount = discount;
        this.finalAmount = finalAmount;
        this.paidAmount = paidAmount;
        this.change = change;
        this.paymentMethod = paymentMethod;
        this.orderStatus = orderStatus;
    }

    public static class Item {
        private String menuItem;
        private int quantity;
        private double price;

        public Item(String menuItem, int quantity, double price) {
            this.menuItem = menuItem;
            this.quantity = quantity;
            this.price = price;
        }


        public String getMenuItem() {
            return menuItem;
        }

        public void setMenuItem(String menuItem) {
            this.menuItem = menuItem;
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
    }

    public String get_id() { return _id; }
    public int getTableNumber() { return tableNumber; }
    public double getFinalAmount() { return finalAmount; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getOrderStatus() { return orderStatus; }
}
