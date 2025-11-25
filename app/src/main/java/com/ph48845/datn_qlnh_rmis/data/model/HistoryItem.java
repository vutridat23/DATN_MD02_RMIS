package com.ph48845.datn_qlnh_rmis.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class HistoryItem {
    @SerializedName("_id")
    private String id;
    private int tableNumber;
    private List<Item> items;
    private double totalAmount;
    private String createdAt;


    public HistoryItem(String id, int tableNumber, List<Item> items, double totalAmount) {
        this.id = id;
        this.tableNumber = tableNumber;
        this.items = items;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;

    }

    public String getId() {
        return id;
    }

    public int getTableNumber() {
        return tableNumber;
    }

    public List<Item> getItems() {
        return items;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public int getTotalItems() {
        int sum = 0;
        for (Item item : items) {
            sum += item.getQuantity();
        }
        return sum;
    }

    public static class Item {
        private String menuItemName;
        private int quantity;
        private double price;

        public Item(String menuItemName, int quantity, double price) {
            this.menuItemName = menuItemName;
            this.quantity = quantity;
            this.price = price;
        }

        public String getMenuItemName() {
            return menuItemName;
        }

        public int getQuantity() {
            return quantity;
        }

        public double getPrice() {
            return price;
        }
    }
    public String getCreatedAt() {
        return createdAt;
    }
}
