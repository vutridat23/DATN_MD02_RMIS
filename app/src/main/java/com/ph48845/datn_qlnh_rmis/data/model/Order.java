package com.ph48845.datn_qlnh_rmis.data.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Order model - extended OrderItem to be tolerant with server formats.
 *
 * Notes:
 * - The server may return order.items[].menuItem as either a String id or an object { _id, name, price, imageUrl }.
 * - We keep a raw menuItem field (Object) and provide normalize() to extract id/name/price/imageUrl into fields used by the UI.
 * - Call order.normalizeItems() after deserialization (or call each OrderItem.normalize()) before using items in UI.
 */
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
     * Normalize all items - call this after deserialization.
     */
    public void normalizeItems() {
        if (items == null) return;
        for (OrderItem oi : items) {
            if (oi != null) oi.normalize();
        }
    }

    /**
     * Inner class representing an item in the order.
     * Tolerant with server formats: menuItem may be an id (String) or an object map.
     */
    public static class OrderItem implements Serializable {

        // server might return either "menuItem" as id or as nested object
        @SerializedName("menuItem")
        private Object menuItemRaw;

        // canonical fields used by UI
        private String menuItemId;

        @SerializedName("name")
        private String name; // snapshot of menu name at order time

        @SerializedName("quantity")
        private int quantity;

        @SerializedName("price")
        private double price;

        @SerializedName(value = "imageUrl", alternate = {"image", "thumbnail", "img"})
        private String imageUrl;

        @SerializedName("status")
        private String status; // pending, preparing, done, cancelled

        public OrderItem() {}

        public OrderItem(String menuItemId, String name, int quantity, double price) {
            this.menuItemRaw = null;
            this.menuItemId = menuItemId;
            this.name = name;
            this.quantity = quantity;
            this.price = price;
            this.status = "pending";
            this.imageUrl = "";
        }

        public OrderItem(String menuItemId, String name, int quantity, double price, String imageUrl) {
            this(menuItemId, name, quantity, price);
            this.imageUrl = imageUrl;
        }

        /**
         * Normalize the possibly-nested menuItemRaw into menuItemId/name/price/imageUrl.
         * Works if menuItemRaw is a String (id) or a Map (LinkedTreeMap from Gson).
         */
        @SuppressWarnings("unchecked")
        public void normalize() {
            // if menuItemRaw is a simple id string
            try {
                if (menuItemRaw != null) {
                    if (menuItemRaw instanceof String) {
                        if ((menuItemId == null || menuItemId.isEmpty())) {
                            menuItemId = (String) menuItemRaw;
                        }
                    } else if (menuItemRaw instanceof Map) {
                        Map<?,?> m = (Map<?,?>) menuItemRaw;
                        // id
                        Object idObj = m.get("_id");
                        if (idObj == null) idObj = m.get("id");
                        if (idObj != null) menuItemId = String.valueOf(idObj);
                        // name
                        Object n = m.get("name");
                        if ((name == null || name.trim().isEmpty()) && n != null) name = String.valueOf(n);
                        // price
                        try {
                            Object p = m.get("price");
                            if (p != null) {
                                if (p instanceof Number) price = ((Number) p).doubleValue();
                                else price = Double.parseDouble(String.valueOf(p));
                            }
                        } catch (Exception ignored) {}
                        // image
                        Object img = m.get("imageUrl");
                        if (img == null) img = m.get("image");
                        if (img == null) img = m.get("thumbnail");
                        if (img != null) imageUrl = String.valueOf(img);
                    } else {
                        // other types: try toString
                        if (menuItemId == null || menuItemId.isEmpty()) menuItemId = String.valueOf(menuItemRaw);
                    }
                }
            } catch (Exception ignored) {}

            // defensive defaults
            if (name == null) name = "";
            if (imageUrl == null) imageUrl = "";
            // menuItemId may still be null if server didn't send it
            if (menuItemId == null) menuItemId = "";
        }

        // Getters / Setters
        public Object getMenuItemRaw() { return menuItemRaw; }
        public void setMenuItemRaw(Object menuItemRaw) { this.menuItemRaw = menuItemRaw; }

        public String getMenuItemId() { return menuItemId == null ? "" : menuItemId; }
        public void setMenuItemId(String menuItemId) { this.menuItemId = menuItemId; }

        public String getName() { return name == null ? "" : name; }
        public void setName(String name) { this.name = name; }

        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }

        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }

        public String getImageUrl() { return imageUrl == null ? "" : imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

        public String getStatus() { return status == null ? "" : status; }
        public void setStatus(String status) { this.status = status; }

        @Override
        public String toString() {
            return "OrderItem{" +
                    "menuItemRaw=" + (menuItemRaw != null ? menuItemRaw.toString() : "null") +
                    ", menuItemId='" + menuItemId + '\'' +
                    ", name='" + name + '\'' +
                    ", quantity=" + quantity +
                    ", price=" + price +
                    ", imageUrl='" + imageUrl + '\'' +
                    ", status='" + status + '\'' +
                    '}';
        }
    }
}