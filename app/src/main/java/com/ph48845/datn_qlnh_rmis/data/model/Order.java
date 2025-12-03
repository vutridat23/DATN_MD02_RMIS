package com.ph48845.datn_qlnh_rmis.data.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


public class Order implements Serializable {

    // SỬA LỖI GSON CONFLICT: Đánh dấu tất cả các trường "Legacy" hoặc không có chú thích
    // tương ứng với trường @SerializedName là transient.
    public transient String _id;
    public transient int tableNumber;
    public transient String serverLegacy;
    public transient String cashierLegacy;
    public transient List<OrderItem> items;
    public transient double discountLegacy;
    public transient double finalAmountLegacy;
    public transient double paidAmountLegacy;
    public transient double changeLegacy;
    public transient String paymentMethodLegacy;
    public transient String orderStatusLegacy;
    public transient List<String> mergedFromLegacy;
    public transient List<String> splitToLegacy;
    public transient String paidAtLegacy;

    // Các trường khác không liên quan trực tiếp đến JSON keys nhưng cần giữ lại
    private String orderId;
    private String tableId;
    private String waiterId;
    private transient long createdAt; // Đã sửa thành transient ở lần trước do conflict
    private boolean paid;
    private transient double totalAmount; // <--- ĐÃ THÊM TRANSIENT VÀO ĐÂY ĐỂ GIẢI QUYẾT LỖI XUNG ĐỘT

    // ================== Annotated Fields (Gson uses these) ==================
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

    @SerializedName("tempCalculationRequestedBy")
    private String tempCalculationRequestedByAnnotated;

    @SerializedName("tempCalculationRequestedAt")
    private String tempCalculationRequestedAtAnnotated;

    @SerializedName("checkItemsRequestedBy")
    private String checkItemsRequestedByAnnotated;

    @SerializedName("checkItemsRequestedAt")
    private String checkItemsRequestedAtAnnotated;

    // ================== Constructors ==================
    public Order() {
        if (items == null) items = new ArrayList<>();
        if (mergedFromLegacy == null) mergedFromLegacy = new ArrayList<>();
        if (splitToLegacy == null) splitToLegacy = new ArrayList<>();
    }

    public Order(int tableNumber, String serverId, List<OrderItem> items,
                 double totalAmount, double discount, double finalAmount,
                 String paymentMethod, String orderStatus) {
        this();
        this.tableNumber = tableNumber;
        this.serverLegacy = serverId;
        this.items = items != null ? items : new ArrayList<>();
        this.totalAmount = totalAmount; // Giá trị này chỉ dùng cho constructor và sẽ không được Gson parse
        this.discountLegacy = discount;
        this.finalAmountLegacy = finalAmount;
        this.paymentMethodLegacy = paymentMethod;
        this.orderStatusLegacy = orderStatus;

        // Cập nhật các trường Annotated trong constructor để đồng bộ (nếu cần)
        this.tableNumberAnnotated = tableNumber;
        this.serverIdAnnotated = serverId;
        this.itemsAnnotated = items;
        this.totalAmountAnnotated = totalAmount;
        this.discountAnnotated = discount;
        this.finalAmountAnnotated = finalAmount;
        this.paymentMethodAnnotated = paymentMethod;
        this.orderStatusAnnotated = orderStatus;
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
        // Trả về trường Annotated, nếu null thì dùng trường transient (chỉ có giá trị nếu được set thủ công)
        return totalAmountAnnotated != null ? totalAmountAnnotated : totalAmount;
    }
    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
        this.totalAmountAnnotated = totalAmount;
    }

    public List<OrderItem> getItems() {
        if (itemsAnnotated != null && !itemsAnnotated.isEmpty()) return itemsAnnotated;
        if (items == null) items = new ArrayList<>();
        return items;
    }
    public void setItems(List<OrderItem> items) {
        this.items = items;
        this.itemsAnnotated = items;
    }

    public String getId() {
        return idAnnotated != null ? idAnnotated : _id;
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
        return serverIdAnnotated != null ? serverIdAnnotated : serverLegacy;
    }
    public void setServerId(String serverId) {
        this.serverLegacy = serverId;
        this.serverIdAnnotated = serverId;
    }

    public String getCashierId() {
        return cashierIdAnnotated != null ? cashierIdAnnotated : cashierLegacy;
    }
    public void setCashierId(String cashierId) {
        this.cashierLegacy = cashierId;
        this.cashierIdAnnotated = cashierId;
    }

    public double getDiscount() {
        return discountAnnotated != null ? discountAnnotated : discountLegacy;
    }
    public void setDiscount(double discount) {
        this.discountLegacy = discount;
        this.discountAnnotated = discount;
    }

    public double getFinalAmount() {
        return finalAmountAnnotated != null ? finalAmountAnnotated : finalAmountLegacy;
    }
    public void setFinalAmount(double finalAmount) {
        this.finalAmountLegacy = finalAmount;
        this.finalAmountAnnotated = finalAmount;
    }

    public double getPaidAmount() {
        return paidAmountAnnotated != null ? paidAmountAnnotated : paidAmountLegacy;
    }
    public void setPaidAmount(double paidAmount) {
        this.paidAmountLegacy = paidAmount;
        this.paidAmountAnnotated = paidAmount;
    }

    public double getChange() {
        return changeAnnotated != null ? changeAnnotated : changeLegacy;
    }
    public void setChange(double change) {
        this.changeLegacy = change;
        this.changeAnnotated = change;
    }

    public String getPaymentMethod() {
        return paymentMethodAnnotated != null ? paymentMethodAnnotated : paymentMethodLegacy;
    }
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethodLegacy = paymentMethod;
        this.paymentMethodAnnotated = paymentMethod;
    }

    public String getOrderStatus() {
        return orderStatusAnnotated != null ? orderStatusAnnotated : orderStatusLegacy;
    }
    public void setOrderStatus(String orderStatus) {
        this.orderStatusLegacy = orderStatus;
        this.orderStatusAnnotated = orderStatus;
    }

    public List<String> getMergedFrom() {
        if (mergedFromAnnotated != null && !mergedFromAnnotated.isEmpty()) return mergedFromAnnotated;
        if (mergedFromLegacy == null) mergedFromLegacy = new ArrayList<>();
        return mergedFromLegacy;
    }
    public void setMergedFrom(List<String> mergedFrom) {
        this.mergedFromLegacy = mergedFrom;
        this.mergedFromAnnotated = mergedFrom;
    }

    public List<String> getSplitTo() {
        if (splitToAnnotated != null && !splitToAnnotated.isEmpty()) return splitToAnnotated;
        if (splitToLegacy == null) splitToLegacy = new ArrayList<>();
        return splitToLegacy;
    }
    public void setSplitTo(List<String> splitTo) {
        this.splitToLegacy = splitTo;
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
        return paidAtAnnotated != null ? paidAtAnnotated : paidAtLegacy;
    }
    public void setPaidAt(String paidAt) {
        this.paidAtLegacy = paidAt;
        this.paidAtAnnotated = paidAt;
    }

    public String getTempCalculationRequestedBy() {
        return tempCalculationRequestedByAnnotated;
    }
    public void setTempCalculationRequestedBy(String tempCalculationRequestedBy) {
        this.tempCalculationRequestedByAnnotated = tempCalculationRequestedBy;
    }

    public String getTempCalculationRequestedAt() {
        return tempCalculationRequestedAtAnnotated;
    }
    public void setTempCalculationRequestedAt(String tempCalculationRequestedAt) {
        this.tempCalculationRequestedAtAnnotated = tempCalculationRequestedAt;
    }

    public String getCheckItemsRequestedBy() {
        return checkItemsRequestedByAnnotated;
    }
    public void setCheckItemsRequestedBy(String checkItemsRequestedBy) {
        this.checkItemsRequestedByAnnotated = checkItemsRequestedBy;
    }

    public String getCheckItemsRequestedAt() {
        return checkItemsRequestedAtAnnotated;
    }
    public void setCheckItemsRequestedAt(String checkItemsRequestedAt) {
        this.checkItemsRequestedAtAnnotated = checkItemsRequestedAt;
    }

    private String createdAtEpochToString() {
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

        @SerializedName("_id")
        private String id;

        @SerializedName("menuItem")
        private Object menuItemRaw;

        @SerializedName("menuItemId") // Đặt tên JSON rõ ràng để gửi đi
        private String menuItemId;

        @SerializedName("status")
        private String status;
        @SerializedName("note")
        private String note;
        @SerializedName("cancelReason")
        private String cancelReason;
        @SerializedName("cancelRequestedBy")
        private String cancelRequestedBy;
        @SerializedName("parentOrderId")
        private String parentOrderId;
        @SerializedName("menuItemName")
        private String menuItemName;

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
                    } else if (menuItemRaw instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) menuItemRaw;
                        Object idObj = map.get("_id");
                        if (idObj == null) idObj = map.get("id");
                        if (idObj != null) {
                            String idStr = String.valueOf(idObj);
                            if (menuItemId == null || menuItemId.isEmpty()) menuItemId = idStr;
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
                        String rawStr = String.valueOf(menuItemRaw);
                        if (menuItemId == null || menuItemId.isEmpty()) menuItemId = rawStr;
                    }
                }
            } catch (Exception ignored) {}

            if (name == null) name = "";
            if (menuItemName == null || menuItemName.isEmpty()) menuItemName = name;
            if (imageUrl == null) imageUrl = "";
            if (status == null) status = "";
            if (note == null) note = "";
            if (cancelReason == null) cancelReason = "";
            if (cancelRequestedBy == null) cancelRequestedBy = "";
            if (parentOrderId == null) parentOrderId = "";
        }

        // ---------- Getters / Setters ----------

        public String getId() {
            return id == null ? "" : id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Object getMenuItemRaw() { return menuItemRaw; }
        public void setMenuItemRaw(Object menuItemRaw) { this.menuItemRaw = menuItemRaw; }

        public String getMenuItemId() {
            return menuItemId == null ? "" : menuItemId;
        }

        public void setMenuItemId(String menuItemId) {
            this.menuItemId = menuItemId;
        }

        public String getMenuItem() {
            return menuItemId == null ? "" : menuItemId;
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

        public String getCancelReason() { return cancelReason == null ? "" : cancelReason; }
        public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

        public String getCancelRequestedBy() { return cancelRequestedBy == null ? "" : cancelRequestedBy; }
        public void setCancelRequestedBy(String cancelRequestedBy) { this.cancelRequestedBy = cancelRequestedBy; }

        public String getParentOrderId() { return parentOrderId == null ? "" : parentOrderId; }
        public void setParentOrderId(String parentOrderId) { this.parentOrderId = parentOrderId; }

        @Override
        public String toString() {
            return "OrderItem{" +
                    "menuItemId='" + menuItemId + '\'' +
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