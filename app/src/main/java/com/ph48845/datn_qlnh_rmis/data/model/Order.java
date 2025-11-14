package com.ph48845.datn_qlnh_rmis.data.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


public class Order implements Serializable {

    public List<OrderItem> items;                // legacy direct access
    public String _id;                           // Mongo ObjectId
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

    private String orderId;
    private String tableId;
    private String waiterId;
    private long createdAt;
    private boolean paid;
    private double totalAmount;

    @SerializedName("_id")
    private String idAnnotated;

    @SerializedName("tableNumber")
    private Integer tableNumberAnnotated;

    @SerializedName("server")
    private String serverIdAnnotated;

    @SerializedName("cashier")
    private String cashierIdAnnotated;

    @SerializedName("items")
    private List<OrderItem> itemsAnnotated;

    @SerializedName("totalAmount")
    private Double totalAmountAnnotated;

    @SerializedName("discount")
    private Double discountAnnotated;

    @SerializedName("finalAmount")
    private Double finalAmountAnnotated;

    @SerializedName("paidAmount")
    private Double paidAmountAnnotated;

    @SerializedName("change")
    private Double changeAnnotated;

    @SerializedName("paymentMethod")
    private String paymentMethodAnnotated;

    @SerializedName("orderStatus")
    private String orderStatusAnnotated;

    @SerializedName("mergedFrom")
    private List<String> mergedFromAnnotated;

    @SerializedName("splitTo")
    private List<String> splitToAnnotated;

    @SerializedName("createdAt")
    private String createdAtAnnotated;

    @SerializedName("paidAt")
    private String paidAtAnnotated;

    // ================== Constructors ==================
    public Order() {
        // ensure lists are non-null
        if (items == null) items = new ArrayList<>();
        if (mergedFrom == null) mergedFrom = new ArrayList<>();
        if (splitTo == null) splitTo = new ArrayList<>();
    }

    public Order(int tableNumber, String serverId, List<OrderItem> items,
                 double totalAmount, double discount, double finalAmount,
                 String paymentMethod, String orderStatus) {
        this();
        this.tableNumber = tableNumber;
        this.server = serverId;
        this.items = items != null ? items : new ArrayList<>();
        this.totalAmount = totalAmount;
        this.discount = discount;
        this.finalAmount = finalAmount;
        this.paymentMethod = paymentMethod;
        this.orderStatus = orderStatus;
    }

    // ================== Normalization ==================
    public void normalizeItems() {
        List<OrderItem> list = getItems();
        for (OrderItem oi : list) {
            if (oi != null) oi.normalize();
        }
    }

    // ================== Getters / Setters (unified) ==================
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getTableId() { return tableId; }
    public void setTableId(String tableId) { this.tableId = tableId; }

    public String getWaiterId() { return waiterId; }
    public void setWaiterId(String waiterId) { this.waiterId = waiterId; }

    public long getCreatedAtEpoch() { return createdAt; }
    public void setCreatedAtEpoch(long createdAt) { this.createdAt = createdAt; }

    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }

    public double getTotalAmount() {
        return totalAmountAnnotated != null ? totalAmountAnnotated : totalAmount;
    }
    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
        this.totalAmountAnnotated = totalAmount;
    }

    public List<OrderItem> getItems() {
        // prefer annotated list if present
        if (itemsAnnotated != null && !itemsAnnotated.isEmpty()) return itemsAnnotated;
        if (items == null) items = new ArrayList<>();
        return items;
    }
    public void setItems(List<OrderItem> items) {
        this.items = items;
        this.itemsAnnotated = items;
    }

    public String getId() {
        return _id != null ? _id : idAnnotated;
    }
    public void setId(String id) {
        this._id = id;
        this.idAnnotated = id;
    }

    public int getTableNumber() {
        return tableNumberAnnotated != null ? tableNumberAnnotated : tableNumber;
    }
    public void setTableNumber(int tableNumber) {
        this.tableNumber = tableNumber;
        this.tableNumberAnnotated = tableNumber;
    }

    public String getServerId() {
        return serverIdAnnotated != null ? serverIdAnnotated : server;
    }
    public void setServerId(String serverId) {
        this.server = serverId;
        this.serverIdAnnotated = serverId;
    }

    public String getCashierId() {
        return cashierIdAnnotated != null ? cashierIdAnnotated : cashier;
    }
    public void setCashierId(String cashierId) {
        this.cashier = cashierId;
        this.cashierIdAnnotated = cashierId;
    }

    public double getDiscount() {
        return discountAnnotated != null ? discountAnnotated : discount;
    }
    public void setDiscount(double discount) {
        this.discount = discount;
        this.discountAnnotated = discount;
    }

    public double getFinalAmount() {
        return finalAmountAnnotated != null ? finalAmountAnnotated : finalAmount;
    }
    public void setFinalAmount(double finalAmount) {
        this.finalAmount = finalAmount;
        this.finalAmountAnnotated = finalAmount;
    }

    public double getPaidAmount() {
        return paidAmountAnnotated != null ? paidAmountAnnotated : paidAmount;
    }
    public void setPaidAmount(double paidAmount) {
        this.paidAmount = paidAmount;
        this.paidAmountAnnotated = paidAmount;
    }

    public double getChange() {
        return changeAnnotated != null ? changeAnnotated : change;
    }
    public void setChange(double change) {
        this.change = change;
        this.changeAnnotated = change;
    }

    public String getPaymentMethod() {
        return paymentMethodAnnotated != null ? paymentMethodAnnotated : paymentMethod;
    }
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
        this.paymentMethodAnnotated = paymentMethod;
    }

    public String getOrderStatus() {
        return orderStatusAnnotated != null ? orderStatusAnnotated : orderStatus;
    }
    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
        this.orderStatusAnnotated = orderStatus;
    }

    public List<String> getMergedFrom() {
        if (mergedFromAnnotated != null && !mergedFromAnnotated.isEmpty()) return mergedFromAnnotated;
        if (mergedFrom == null) mergedFrom = new ArrayList<>();
        return mergedFrom;
    }
    public void setMergedFrom(List<String> mergedFrom) {
        this.mergedFrom = mergedFrom;
        this.mergedFromAnnotated = mergedFrom;
    }

    public List<String> getSplitTo() {
        if (splitToAnnotated != null && !splitToAnnotated.isEmpty()) return splitToAnnotated;
        if (splitTo == null) splitTo = new ArrayList<>();
        return splitTo;
    }
    public void setSplitTo(List<String> splitTo) {
        this.splitTo = splitTo;
        this.splitToAnnotated = splitTo;
    }

    public String getCreatedAt() {
        return createdAtAnnotated != null ? createdAtAnnotated : createdAtEpochToString();
    }
    public void setCreatedAt(String createdAt) {
        this.createdAtAnnotated = createdAt;
        this.createdAt = 0; // epoch not known
    }

    public String getPaidAt() {
        return paidAtAnnotated != null ? paidAtAnnotated : paidAt;
    }
    public void setPaidAt(String paidAt) {
        this.paidAt = paidAt;
        this.paidAtAnnotated = paidAt;
    }

    private String createdAtEpochToString() {
        // Nếu bạn muốn convert epoch thành ISO string, thực hiện tại đây (placeholder)
        return createdAtAnnotated != null ? createdAtAnnotated : (createdAt > 0 ? String.valueOf(createdAt) : null);
    }

    @Override
    public String toString() {
        return "Order{" +
                "id='" + getId() + '\'' +
                ", tableNumber=" + getTableNumber() +
                ", serverId='" + getServerId() + '\'' +
                ", cashierId='" + getCashierId() + '\'' +
                ", items=" + getItems() +
                ", totalAmount=" + getTotalAmount() +
                ", discount=" + getDiscount() +
                ", finalAmount=" + getFinalAmount() +
                ", paidAmount=" + getPaidAmount() +
                ", change=" + getChange() +
                ", paymentMethod='" + getPaymentMethod() + '\'' +
                ", orderStatus='" + getOrderStatus() + '\'' +
                ", mergedFrom=" + getMergedFrom() +
                ", splitTo=" + getSplitTo() +
                ", createdAt='" + getCreatedAt() + '\'' +
                ", paidAt='" + getPaidAt() + '\'' +
                '}';
    }

    // ================== Inner OrderItem ==================
    public static class OrderItem implements Serializable {

        // Raw menuItem from backend (String id or Map object)
        @SerializedName("menuItem")
        private Object menuItemRaw;

        // Legacy fields (public) kept for backward compatibility
        public String menuItem;         // direct id (legacy)
        public String status;           // pending / preparing / ready / soldout / etc
        public String note;             // optional note
        public String menuItemName;     // legacy name field (may mirror 'name')

        // Unified canonical fields
        private String menuItemId;
        @SerializedName("name")
        private String name;
        @SerializedName("quantity")
        private int quantity;
        @SerializedName("price")
        private double price;
        @SerializedName(value = "imageUrl", alternate = {"image", "thumbnail", "img"})
        private String imageUrl;

        public OrderItem() {}

        public OrderItem(String menuItemId, String name, int quantity, double price, String status) {
            this.menuItemId = menuItemId;
            this.name = name;
            this.menuItemName = name;
            this.quantity = quantity;
            this.price = price;
            this.status = status;
            this.menuItem = menuItemId;
        }

        public OrderItem(String menuItemId, String name, int quantity, double price) {
            this(menuItemId, name, quantity, price, "pending");
        }

        @SuppressWarnings("unchecked")
        public void normalize() {
            try {
                if (menuItemRaw != null) {
                    if (menuItemRaw instanceof String) {
                        String rawId = (String) menuItemRaw;
                        if (menuItemId == null || menuItemId.isEmpty()) menuItemId = rawId;
                        if (menuItem == null || menuItem.isEmpty()) menuItem = rawId;
                    } else if (menuItemRaw instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) menuItemRaw;
                        Object idObj = map.get("_id");
                        if (idObj == null) idObj = map.get("id");
                        if (idObj != null) {
                            String idStr = String.valueOf(idObj);
                            if (menuItemId == null || menuItemId.isEmpty()) menuItemId = idStr;
                            if (menuItem == null || menuItem.isEmpty()) menuItem = idStr;
                        }
                        Object nObj = map.get("name");
                        if (nObj != null && (name == null || name.isEmpty())) {
                            name = String.valueOf(nObj);
                            if (menuItemName == null || menuItemName.isEmpty()) menuItemName = name;
                        }
                        Object pObj = map.get("price");
                        if (pObj != null) {
                            try {
                                if (pObj instanceof Number) price = ((Number) pObj).doubleValue();
                                else price = Double.parseDouble(String.valueOf(pObj));
                            } catch (Exception ignored) {}
                        }
                        Object imgObj = map.get("imageUrl");
                        if (imgObj == null) imgObj = map.get("image");
                        if (imgObj == null) imgObj = map.get("thumbnail");
                        if (imgObj != null && (imageUrl == null || imageUrl.isEmpty())) {
                            imageUrl = String.valueOf(imgObj);
                        }
                    } else {
                        // Fallback: toString
                        String rawStr = String.valueOf(menuItemRaw);
                        if (menuItemId == null || menuItemId.isEmpty()) menuItemId = rawStr;
                        if (menuItem == null || menuItem.isEmpty()) menuItem = rawStr;
                    }
                }
            } catch (Exception ignored) {}

            // Defensive defaults
            if (menuItemId == null) menuItemId = menuItem != null ? menuItem : "";
            if (menuItem == null) menuItem = menuItemId != null ? menuItemId : "";
            if (name == null) name = "";
            if (menuItemName == null || menuItemName.isEmpty()) menuItemName = name;
            if (imageUrl == null) imageUrl = "";
            if (status == null) status = "";
            if (note == null) note = "";
        }

        // ---------- Getters / Setters ----------
        public Object getMenuItemRaw() { return menuItemRaw; }
        public void setMenuItemRaw(Object menuItemRaw) { this.menuItemRaw = menuItemRaw; }

        public String getMenuItemId() { return menuItemId == null ? "" : menuItemId; }
        public void setMenuItemId(String menuItemId) {
            this.menuItemId = menuItemId;
            if (this.menuItem == null || this.menuItem.isEmpty()) this.menuItem = menuItemId;
        }

        public String getMenuItem() { return menuItem == null ? "" : menuItem; }
        public void setMenuItem(String menuItem) {
            this.menuItem = menuItem;
            if (this.menuItemId == null || this.menuItemId.isEmpty()) this.menuItemId = menuItem;
        }

        public String getName() { return name == null ? "" : name; }
        public void setName(String name) {
            this.name = name;
            if (this.menuItemName == null || this.menuItemName.isEmpty()) this.menuItemName = name;
        }

        public String getMenuItemName() { return menuItemName == null ? "" : menuItemName; }
        public void setMenuItemName(String menuItemName) { this.menuItemName = menuItemName; }

        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }

        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }

        public String getImageUrl() { return imageUrl == null ? "" : imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

        public String getStatus() { return status == null ? "" : status; }
        public void setStatus(String status) { this.status = status; }

        public String getNote() { return note == null ? "" : note; }
        public void setNote(String note) { this.note = note; }

        @Override
        public String toString() {
            return "OrderItem{" +
                    "menuItemId='" + menuItemId + '\'' +
                    ", menuItem='" + menuItem + '\'' +
                    ", name='" + name + '\'' +
                    ", quantity=" + quantity +
                    ", price=" + price +
                    ", status='" + status + '\'' +
                    ", note='" + note + '\'' +
                    ", imageUrl='" + imageUrl + '\'' +
                    '}';
        }
    }

    // ================== Convenience helpers ==================
    public boolean hasItems() {
        return !getItems().isEmpty();
    }

    public List<OrderItem> safeItems() {
        return Collections.unmodifiableList(getItems());
    }

    public OrderItem findItemById(String id) {
        if (id == null) return null;
        for (OrderItem oi : getItems()) {
            if (oi == null) continue;
            if (id.equals(oi.getMenuItemId()) || id.equals(oi.getMenuItem())) return oi;
        }
        return null;
    }
}