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
    private Details details; // wrapper chứa items và totalAmount

    public HistoryItem() { }

    public String getId() {
        return id;
    }

    public int getTableNumber() {
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
        if (details == null || details.items == null) return 0;
        int sum = 0;
        for (Details.Item item : details.items) {
            sum += item.getQuantity();
        }
        return sum;
    }

    // Tổng tiền
    public double getTotalAmount() {
        return details != null ? details.totalAmount : 0;
    }

    // Inner class Details
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

        // Inner class Item
        public static class Item {

            @SerializedName("menuItemName")
            private String menuItemName;

            @SerializedName("quantity")
            private int quantity;

            @SerializedName("price")
            private double price;

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
    }
}
