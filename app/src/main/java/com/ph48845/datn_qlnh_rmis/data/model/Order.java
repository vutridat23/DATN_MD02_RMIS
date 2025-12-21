package com.ph48845.datn_qlnh_rmis. data.model;

import android.util.Log;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Order model - updated to tolerate server/cashier being returned either as String or as Object.
 *
 * Changes:
 * - serverIdAnnotated and cashierIdAnnotated are now Object so Gson can deserialize either a String or an Object.
 * - getServerId() and getCashierId() resolve the best string representation (prefer _id or id, fallback to name).
 * - setServerId/setCashierId still accept String for callers that set simple ids.
 */
public class Order implements Serializable {

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

    // ✨ Thêm field mới
    public transient String cancelReasonLegacy = "";

    private String orderId;
    private String tableId;
    private String waiterId;
    private transient long createdAt;
    private boolean paid;
    private transient double totalAmount;

    @SerializedName("_id")
    private String idAnnotated;

    @SerializedName("tableNumber")
    private Integer tableNumberAnnotated;

    // Accept either String or Object for server/cashier to be tolerant of API variations
    @SerializedName("server")
    private Object serverIdAnnotated;

    @SerializedName("cashier")
    private Object cashierIdAnnotated;

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
    private Object tempCalculationRequestedByAnnotated;

    @SerializedName("tempCalculationRequestedAt")
    private String tempCalculationRequestedAtAnnotated;

    // Tương tự, nếu trường checkItems cũng trả về object thì sửa luôn
    @SerializedName("checkItemsRequestedBy")
    private Object checkItemsRequestedByAnnotated;

    @SerializedName("checkItemsRequestedAt")
    private String checkItemsRequestedAtAnnotated;

    @SerializedName("checkItemsStatus")
    private String checkItemsStatusAnnotated;

    @SerializedName("checkItemsCompletedBy")
    private Object checkItemsCompletedByAnnotated;

    @SerializedName("checkItemsCompletedAt")
    private String checkItemsCompletedAtAnnotated;

    @SerializedName("checkItemsNote")
    private String checkItemsNoteAnnotated;

    @SerializedName("cancelReason")
    private String cancelReasonAnnotated;

    public Order() {
        if (items == null) items = new ArrayList<>();
        if (mergedFromLegacy == null) mergedFromLegacy = new ArrayList<>();
        if (splitToLegacy == null) splitToLegacy = new ArrayList<>();
        if (cancelReasonLegacy == null) cancelReasonLegacy = "";
    }

    public Order(int tableNumber, String serverId, List<OrderItem> items,
                 double totalAmount, double discount, double finalAmount,
                 String paymentMethod, String orderStatus) {
        this();
        this.tableNumber = tableNumber;
        this.serverLegacy = serverId;
        this.items = items != null ? items : new ArrayList<>();
        this.totalAmount = totalAmount;
        this.discountLegacy = discount;
        this.finalAmountLegacy = finalAmount;
        this.paymentMethodLegacy = paymentMethod;
        this.orderStatusLegacy = orderStatus;

        this.tableNumberAnnotated = tableNumber;
        this.serverIdAnnotated = serverId;
        this.itemsAnnotated = items;
        this.totalAmountAnnotated = totalAmount;
        this.discountAnnotated = discount;
        this.finalAmountAnnotated = finalAmount;
        this.paymentMethodAnnotated = paymentMethod;
        this.orderStatusAnnotated = orderStatus;

        // ✨ ensure cancelReason exists
        this.cancelReasonLegacy = "";
        this.cancelReasonAnnotated = "";
    }

    public void normalizeItems() {
        List<OrderItem> list = getItems();
        for (OrderItem oi : list) {
            if (oi != null) oi.normalize();
        }
    }

    public void prepareForCreate() {
        normalizeItems();
        setItems(getItems());

        double sum = 0.0;
        for (OrderItem oi : getItems()) {
            try {
                sum += oi.getPrice() * oi.getQuantity();
            } catch (Exception ignored) {}
        }
        setTotalAmount(sum);

        if (discountAnnotated == null) discountAnnotated = 0.0;
        if (finalAmountAnnotated == null) finalAmountAnnotated = totalAmountAnnotated - discountAnnotated;
        if (createdAtAnnotated == null) createdAtAnnotated = String.valueOf(System.currentTimeMillis());

        // ✨ ensure cancelReason default
        if (cancelReasonAnnotated == null) cancelReasonAnnotated = "";
    }

    // ======================================================================
    // BASIC GETTERS/SETTERS
    // ======================================================================

    public String getCancelReason() {
        return cancelReasonAnnotated != null ? cancelReasonAnnotated : cancelReasonLegacy;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReasonLegacy = cancelReason;
        this.cancelReasonAnnotated = cancelReason;
    }

    // ======================================================================

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
        if (serverIdAnnotated == null) return serverLegacy;
        if (serverIdAnnotated instanceof String) {
            String s = (String) serverIdAnnotated;
            if (s != null && !s.isEmpty()) return s;
            return serverLegacy;
        }
        try {
            if (serverIdAnnotated instanceof Map) {
                Map<?,?> map = (Map<?,?>) serverIdAnnotated;
                Object idObj = map.get("_id");
                if (idObj == null) idObj = map.get("id");
                if (idObj != null) return String.valueOf(idObj);
                Object nameObj = map.get("name");
                if (nameObj != null) return String.valueOf(nameObj);
            } else {
                String s = String.valueOf(serverIdAnnotated);
                if (s != null && !s.isEmpty()) return s;
            }
        } catch (Exception ignored) {}
        return serverLegacy;
    }
    public void setServerId(String serverId) {
        this.serverLegacy = serverId;
        this.serverIdAnnotated = serverId;
    }

    /**
     * Resolve cashier id similarly to server.
     */
    public String getCashierId() {
        if (cashierIdAnnotated == null) return cashierLegacy;
        if (cashierIdAnnotated instanceof String) {
            String s = (String) cashierIdAnnotated;
            if (s != null && !s.isEmpty()) return s;
            return cashierLegacy;
        }
        try {
            if (cashierIdAnnotated instanceof Map) {
                Map<?,?> map = (Map<?,?>) cashierIdAnnotated;
                Object idObj = map.get("_id");
                if (idObj == null) idObj = map.get("id");
                if (idObj != null) return String.valueOf(idObj);
                Object nameObj = map.get("name");
                if (nameObj != null) return String.valueOf(nameObj);
            } else {
                String s = String.valueOf(cashierIdAnnotated);
                if (s != null && !s.isEmpty()) return s;
            }
        } catch (Exception ignored) {}
        return cashierLegacy;
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
        this.createdAt = 0;
    }

    public String getPaidAt() {
        return paidAtAnnotated != null ? paidAtAnnotated : paidAtLegacy;
    }
    public void setPaidAt(String paidAt) {
        this.paidAtLegacy = paidAt;
        this.paidAtAnnotated = paidAt;
    }

// ===================== GETTER & SETTER CHO CÁC TRƯỜNG DYNAMIC (OBJECT) =====================

    // 1. TempCalculationRequestedBy
    public String getTempCalculationRequestedBy() {
        if (tempCalculationRequestedByAnnotated == null) return ""; // Trả về rỗng để an toàn hiển thị

        // Trường hợp A: Server trả về Object JSON (Map)
        if (tempCalculationRequestedByAnnotated instanceof Map) {
            try {
                Map<?, ?> map = (Map<?, ?>) tempCalculationRequestedByAnnotated;
                // Ưu tiên 1: Lấy tên hiển thị ("name")
                Object name = map.get("name");
                if (name != null) return String.valueOf(name);

                // Ưu tiên 2: Lấy username
                Object username = map.get("username");
                if (username != null) return String.valueOf(username);

                // Ưu tiên 3: Lấy _id
                Object id = map.get("_id");
                return id != null ? String.valueOf(id) : "";
            } catch (Exception e) {
                return "";
            }
        }

        // Trường hợp B: Server trả về String (chỉ có ID) hoặc các kiểu khác
        if (tempCalculationRequestedByAnnotated instanceof String) {
            return (String) tempCalculationRequestedByAnnotated;
        }

        // Trường hợp còn lại: ép kiểu về String
        return String.valueOf(tempCalculationRequestedByAnnotated);
    }

    /**
     * Lấy ID từ tempCalculationRequestedBy (luôn trả về ID, không phải name/username)
     * Dùng để map ID -> name trong UI
     */
    public String getTempCalculationRequestedById() {
        if (tempCalculationRequestedByAnnotated == null) return "";

        if (tempCalculationRequestedByAnnotated instanceof Map) {
            try {
                Map<?, ?> map = (Map<?, ?>) tempCalculationRequestedByAnnotated;
                // Luôn ưu tiên lấy _id
                Object id = map.get("_id");
                if (id != null) return String.valueOf(id);
                // Fallback: nếu không có _id, có thể là ID nằm ở key khác
                return "";
            } catch (Exception e) {
                return "";
            }
        }

        if (tempCalculationRequestedByAnnotated instanceof String) {
            return (String) tempCalculationRequestedByAnnotated;
        }

        return String.valueOf(tempCalculationRequestedByAnnotated);
    }

    public void setTempCalculationRequestedBy(String tempCalculationRequestedBy) {
        // Khi set từ App, ta gán String ID vào biến Object
        this.tempCalculationRequestedByAnnotated = tempCalculationRequestedBy;
    }

    public String getTempCalculationRequestedAt() {
        return tempCalculationRequestedAtAnnotated;
    }

    public void setTempCalculationRequestedAt(String tempCalculationRequestedAt) {
        this.tempCalculationRequestedAtAnnotated = tempCalculationRequestedAt;
    }

    // ======================================================================
    // CHECK ITEMS REQUEST FIELDS
    // ======================================================================

    public String getCheckItemsRequestedBy() {
        if (checkItemsRequestedByAnnotated == null) return "";

        if (checkItemsRequestedByAnnotated instanceof Map) {
            try {
                Map<?, ?> map = (Map<?, ?>) checkItemsRequestedByAnnotated;
                Object fullName = map.get("fullName");
                if (fullName != null && !String.valueOf(fullName).trim().isEmpty()) {
                    return String.valueOf(fullName).trim();
                }

                Object name = map.get("name");
                if (name != null) return String.valueOf(name);

                Object username = map.get("username");
                if (username != null) return String.valueOf(username);

                Object id = map.get("_id");
                return id != null ? String.valueOf(id) : "";
            } catch (Exception e) {
                return "";
            }
        }

        if (checkItemsRequestedByAnnotated instanceof String) {
            return (String) checkItemsRequestedByAnnotated;
        }

        return String.valueOf(checkItemsRequestedByAnnotated);
    }

    public void setCheckItemsRequestedBy(String checkItemsRequestedBy) {
        this.checkItemsRequestedByAnnotated = checkItemsRequestedBy;
    }

    // Các trường thời gian (At) vẫn giữ nguyên là String vì Server trả về chuỗi ngày tháng
    public String getTempCalculationRequestedAt() {
        return tempCalculationRequestedAtAnnotated;
    }

    public void setTempCalculationRequestedAt(String tempCalculationRequestedAt) {
        this.tempCalculationRequestedAtAnnotated = tempCalculationRequestedAt;
    }

    public String getCheckItemsRequestedAt() {
        return checkItemsRequestedAtAnnotated;
    }

    public void setCheckItemsRequestedAt(String checkItemsRequestedAt) {
        this.checkItemsRequestedAtAnnotated = checkItemsRequestedAt;
    }

    // ======================================================================
    // CHECK ITEMS COMPLETION FIELDS
    // ======================================================================

    public String getCheckItemsStatus() {
        return checkItemsStatusAnnotated != null ? checkItemsStatusAnnotated : "";
    }

    public void setCheckItemsStatus(String status) {
        this.checkItemsStatusAnnotated = status;
    }

    public String getCheckItemsCompletedBy() {
        if (checkItemsCompletedByAnnotated == null) return "";

        if (checkItemsCompletedByAnnotated instanceof Map) {
            try {
                Map<?, ?> map = (Map<?, ?>) checkItemsCompletedByAnnotated;

                Object fullName = map.get("fullName");
                if (fullName != null && !String. valueOf(fullName).trim().isEmpty()) {
                    return String. valueOf(fullName).trim();
                }

                Object name = map.get("name");
                if (name != null && !String.valueOf(name).trim().isEmpty()) {
                    return String.valueOf(name).trim();
                }

                Object username = map.get("username");
                if (username != null && !String.valueOf(username).trim().isEmpty()) {
                    return String. valueOf(username).trim();
                }

                Object id = map. get("_id");
                return id != null ? String.valueOf(id) : "";
            } catch (Exception e) {
                Log.w("Order", "Failed to extract checkItemsCompletedBy", e);
                return "";
            }
        }

        if (checkItemsCompletedByAnnotated instanceof String) {
            return (String) checkItemsCompletedByAnnotated;
        }

        return String.valueOf(checkItemsCompletedByAnnotated);
    }

    public void setCheckItemsCompletedBy(String userId) {
        this.checkItemsCompletedByAnnotated = userId;
    }

    public String getCheckItemsCompletedAt() {
        return checkItemsCompletedAtAnnotated;
    }

    public void setCheckItemsCompletedAt(String timestamp) {
        this.checkItemsCompletedAtAnnotated = timestamp;
    }

    public String getCheckItemsNote() {
        return checkItemsNoteAnnotated != null ? checkItemsNoteAnnotated : "";
    }

    public void setCheckItemsNote(String note) {
        this.checkItemsNoteAnnotated = note;
    }

    // ======================================================================
    // HELPER METHODS
    // ======================================================================

    private String createdAtEpochToString() {
        return createdAtAnnotated != null ? createdAtAnnotated : (createdAt > 0 ? String.valueOf(createdAt) : null);
    }

    // ===================== UPDATED toString() =====================
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
                ", cancelReason='" + getCancelReason() + '\'' +
                ", checkItemsStatus='" + getCheckItemsStatus() + '\'' +
                '}';
    }

    public Map<String, Object> toMapPayload() {
        Map<String, Object> m = new HashMap<>();
        if (tableNumberAnnotated != null) m.put("tableNumber", tableNumberAnnotated);
        else m.put("tableNumber", tableNumber);
        if (serverIdAnnotated != null) m.put("server", serverIdAnnotated);
        if (cashierIdAnnotated != null) m.put("cashier", cashierIdAnnotated);

        List<Map<String, Object>> itemsList = new ArrayList<>();
        for (OrderItem oi : getItems()) {
            itemsList.add(oi.toMap());
        }
        m. put("items", itemsList);

        m.put("totalAmount", getTotalAmount());
        m.put("discount", getDiscount());
        m.put("finalAmount", getFinalAmount());
        if (paymentMethodAnnotated != null) m.put("paymentMethod", paymentMethodAnnotated);
        if (orderStatusAnnotated != null) m.put("orderStatus", orderStatusAnnotated);
        if (createdAtAnnotated != null) m.put("createdAt", createdAtAnnotated);
        if (tempCalculationRequestedByAnnotated != null) m.put("tempCalculationRequestedBy", tempCalculationRequestedByAnnotated);
        if (tempCalculationRequestedAtAnnotated != null) m.put("tempCalculationRequestedAt", tempCalculationRequestedAtAnnotated);
        if (checkItemsRequestedByAnnotated != null) m.put("checkItemsRequestedBy", checkItemsRequestedByAnnotated);
        if (checkItemsRequestedAtAnnotated != null) m.put("checkItemsRequestedAt", checkItemsRequestedAtAnnotated);

        if (checkItemsStatusAnnotated != null) m.put("checkItemsStatus", checkItemsStatusAnnotated);
        if (checkItemsCompletedByAnnotated != null) m.put("checkItemsCompletedBy", checkItemsCompletedByAnnotated);
        if (checkItemsCompletedAtAnnotated != null) m.put("checkItemsCompletedAt", checkItemsCompletedAtAnnotated);
        if (checkItemsNoteAnnotated != null) m.put("checkItemsNote", checkItemsNoteAnnotated);

        m.put("cancelReason", getCancelReason());

        return m;
    }

    // ======================================================================
    // ORDER ITEM CLASS
    // ======================================================================



    // ===================== OrderItem unchanged except cancelReason additions =====================
    // (Nguyên vẹn để tránh xung đột)
    // ---------------------------------------------------------------
    public static class OrderItem implements Serializable {
        @SerializedName("_id")
        private String idAnnotated;

        @SerializedName("menuItem")
        private Object menuItemRaw;

        @SerializedName("menuItemId")
        private String menuItemId;

        @SerializedName("status")
        private String status;
        @SerializedName("note")
        private String note;
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

        // ✨ cancelReason for individual item (added)
        @SerializedName("cancelReason")
        private String cancelReason;

        private transient String parentOrderId;

        public OrderItem() {}

        public OrderItem(String menuItemId, String name, int quantity, double price, String status) {
            this.menuItemId = menuItemId;
            this.name = name;
            this.menuItemName = name;
            this.quantity = quantity;
            this.price = price;
            this.status = status;
            this.cancelReason = this.cancelReason == null ? "" : this.cancelReason;
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
                        try {
                            Object subId = map.get("_id");
                            if (subId == null) subId = map.get("id");
                            if (subId != null && (idAnnotated == null || idAnnotated.isEmpty())) {
                                idAnnotated = String.valueOf(subId);
                            }
                        } catch (Exception ignored) {}
                        // also try to read cancelReason if present in nested menuItemRaw map
                        try {
                            Object cr = map.get("cancelReason");
                            if (cr != null && (cancelReason == null || cancelReason.isEmpty())) cancelReason = String.valueOf(cr);
                        } catch (Exception ignored) {}
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
        }

        public String getId() {
            return idAnnotated != null ? idAnnotated : null;
        }
        public void setId(String id) {
            this.idAnnotated = id;
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

        @SuppressWarnings("unchecked")
        public String getImageUrl() {
            if (imageUrl != null && !imageUrl.trim().isEmpty()) return imageUrl.trim();

            try {
                if (menuItemRaw != null) {
                    if (menuItemRaw instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) menuItemRaw;
                        Object imgObj = map.get("imageUrl");
                        if (imgObj == null) imgObj = map.get("image");
                        if (imgObj == null) imgObj = map.get("thumbnail");
                        if (imgObj == null) imgObj = map.get("img");
                        if (imgObj != null) {
                            String s = String.valueOf(imgObj);
                            return s != null ? s.trim() : "";
                        }
                    } else {
                        String raw = String.valueOf(menuItemRaw);
                        if (raw != null && raw.startsWith("http")) return raw.trim();
                    }
                }
            } catch (Exception ignored) {}

            return "";
        }

        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

        public String getStatus() { return status == null ? "" : status; }
        public void setStatus(String status) { this.status = status; }

        public String getNote() { return note == null ? "" : note; }
        public void setNote(String note) { this.note = note; }

        // ✨ cancelReason getter/setter for item
        public String getCancelReason() {
            return cancelReason == null ? "" : cancelReason;
        }
        public void setCancelReason(String cancelReason) {
            this.cancelReason = cancelReason == null ? "" : cancelReason;
        }

        public String getParentOrderId() { return parentOrderId; }
        public void setParentOrderId(String parentOrderId) { this.parentOrderId = parentOrderId; }

        @Override
        public String toString() {
            return "OrderItem{" +
                    "id='" + getId() + '\'' +
                    ", menuItemId='" + menuItemId + '\'' +
                    ", name='" + name + '\'' +
                    ", quantity=" + quantity +
                    ", price=" + price +
                    ", status='" + status + '\'' +
                    ", note='" + note + '\'' +
                    ", cancelReason='" + getCancelReason() + '\'' +
                    ", imageUrl='" + imageUrl + '\'' +
                    '}';
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            if (menuItemId != null && !menuItemId.isEmpty()) m.put("menuItemId", menuItemId);
            m.put("menuItemName", getMenuItemName());
            m.put("name", getName());
            m.put("quantity", quantity);
            m.put("price", price);
            m.put("status", getStatus());
            if (note != null) m.put("note", getNote());
            if (imageUrl != null) m.put("imageUrl", getImageUrl());
            if (menuItemId != null && !menuItemId.isEmpty()) m.put("menuItem", menuItemId);
            // ✨ include cancelReason if present
            if (cancelReason != null && !cancelReason.isEmpty()) m.put("cancelReason", cancelReason);
            return m;
        }
    }

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
            if (id.equals(oi.getMenuItemId()) || id.equals(oi.getMenuItem()) || id.equals(oi.getId())) return oi;
        }
        return null;
    }
}