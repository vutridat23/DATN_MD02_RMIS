package com.ph48845.datn_qlnh_rmis.data.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/**
 * Model tương ứng với schema "tables" trên server.
 * - statusRaw: giữ nguyên chuỗi status trả về từ server (ví dụ "available", "occupied", "reserved")
 * - status: enum dùng cho UI (EMPTY / OCCUPIED / RESERVED / PENDING_PAYMENT / FINISH_SERVE)
 *
 * Sau khi Gson deserialize, gọi normalize() để đảm bảo enum được set đúng từ statusRaw.
 *
 * Mở rộng: thêm các trường reservationName/reservationPhone/reservationAt để client có thể lưu
 * thông tin đặt trước (nếu backend chấp nhận các trường này khi gọi PUT /tables/{id}).
 */
public class TableItem implements Serializable {

    @SerializedName("_id")
    private String id;

    @SerializedName("tableNumber")
    private Integer tableNumber;

    @SerializedName("capacity")
    private Integer capacity;

    // raw status từ server
    @SerializedName("status")
    private String statusRaw;

    // transient enum dùng cho UI (không serialize)
    private transient Status status;

    @SerializedName("currentOrder")
    private String currentOrder;

    @SerializedName("location")
    private String location;

    @SerializedName("createdAt")
    private String createdAt;

    @SerializedName("updatedAt")
    private String updatedAt;

    // NEW: reservation info nếu backend hỗ trợ (client sẽ gửi các field này khi đặt trước)
    @SerializedName("reservationName")
    private String reservationName;

    @SerializedName("reservationPhone")
    private String reservationPhone;

    @SerializedName("reservationAt")
    private String reservationAt; // ISO datetime or "yyyy-MM-dd HH:mm"



    public enum Status {
        EMPTY,
        AVAILABLE,
        OCCUPIED,
        RESERVED,
        PENDING_PAYMENT,
        FINISH_SERVE;

        public static Status fromString(String value) {
            if (value == null) return EMPTY;
            String v = value.trim().toLowerCase();
            switch (v) {
                case "occupied": return OCCUPIED;
                case "reserved": return RESERVED;
                case "available": return AVAILABLE;
                case "pending_payment":
                case "pendingpayment":
                case "pending-payment":
                case "pending": return PENDING_PAYMENT;
                case "finish_serve":
                case "finishserve":
                case "finished":
                case "served":
                case "finish": return FINISH_SERVE;
                default:
                    if (v.contains("occup")) return OCCUPIED;
                    if (v.contains("reserv")) return RESERVED;
                    if (v.contains("payment") || v.contains("thanh toán")) return PENDING_PAYMENT;
                    if (v.contains("phục vụ") || v.contains("đã phục")) return FINISH_SERVE;
                    return EMPTY;
            }
        }
    }

    public TableItem() {}

    // convenience constructor
    public TableItem(Integer tableNumber, Integer capacity, String statusRaw, String currentOrder, String location) {
        this.tableNumber = tableNumber;
        this.capacity = capacity;
        this.statusRaw = statusRaw;
        this.currentOrder = currentOrder;
        this.location = location;
        this.status = Status.fromString(statusRaw);
    }

    /**
     * After object is deserialized by Gson call this method to ensure
     * the transient enum field is set from the raw string.
     */
    public void normalize() {
        this.status = Status.fromString(this.statusRaw);
        if (this.tableNumber == null) this.tableNumber = 0;
        if (this.capacity == null) this.capacity = 0;
    }

    // Getters / Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Integer getTableNumber() { return tableNumber == null ? 0 : tableNumber; }
    public void setTableNumber(Integer tableNumber) { this.tableNumber = tableNumber; }

    public Integer getCapacity() { return capacity == null ? 0 : capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public String getStatusRaw() { return statusRaw; }
    public void setStatusRaw(String statusRaw) {
        this.statusRaw = statusRaw;
        this.status = Status.fromString(statusRaw);
    }

    public Status getStatus() {
        if (status == null) status = Status.fromString(statusRaw);
        return status;
    }
    public void setStatus(Status status) { this.status = status; }

    public String getCurrentOrder() { return currentOrder; }
    public void setCurrentOrder(String currentOrder) { this.currentOrder = currentOrder; }

    public String getLocation() { return location == null ? "" : location; }
    public void setLocation(String location) { this.location = location; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    // NEW: reservation getters/setters
    public String getReservationName() { return reservationName == null ? "" : reservationName; }
    public void setReservationName(String reservationName) { this.reservationName = reservationName; }

    public String getReservationPhone() { return reservationPhone == null ? "" : reservationPhone; }
    public void setReservationPhone(String reservationPhone) { this.reservationPhone = reservationPhone; }

    public String getReservationAt() { return reservationAt == null ? "" : reservationAt; }
    public void setReservationAt(String reservationAt) { this.reservationAt = reservationAt; }

    // UI display helper
    public String getStatusDisplay() {
        switch (getStatus()) {
            case OCCUPIED: return "Đã có khách";
            case AVAILABLE: return "Khả dụng";
            case RESERVED: return "Đã được đặt trước";
            case PENDING_PAYMENT: return "Chờ thanh toán";
            case FINISH_SERVE: return "Đã phục vụ";
            default: return "";
        }
    }

    @Override
    public String toString() {
        return "TableItem{" +
                "id='" + id + '\'' +
                ", tableNumber=" + tableNumber +
                ", capacity=" + capacity +
                ", statusRaw='" + statusRaw + '\'' +
                ", status=" + (status != null ? status.name() : "null") +
                ", currentOrder='" + currentOrder + '\'' +
                ", location='" + location + '\'' +
                ", reservationName='" + reservationName + '\'' +
                ", reservationPhone='" + reservationPhone + '\'' +
                ", reservationAt='" + reservationAt + '\'' +
                ", createdAt='" + createdAt + '\'' +
                ", updatedAt='" + updatedAt + '\'' +
                '}';
    }
}