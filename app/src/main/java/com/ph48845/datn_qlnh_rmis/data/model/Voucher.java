package com.ph48845.datn_qlnh_rmis.data.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/**
 * Voucher model
 */
public class Voucher implements Serializable {

    @SerializedName("_id")
    private String id;

    @SerializedName("code")
    private String code;

    @SerializedName("name")
    private String name;

    @SerializedName("discountType")
    private String discountType; // "percentage" hoặc "fixed"

    @SerializedName("discountValue")
    private Double discountValue; // Giá trị giảm giá

    @SerializedName(value = "minOrderAmount", alternate = {"minOrderValue"})
    private Double minOrderAmount; // Số tiền tối thiểu để áp dụng

    @SerializedName(value = "maxDiscountAmount", alternate = {"maxDiscount"})
    private Double maxDiscountAmount; // Số tiền giảm tối đa (cho percentage)

    @SerializedName(value = "status", alternate = {"isActive"})
    private String status; // "active", "inactive", "expired" hoặc "true"/"false" nếu dùng isActive

    @SerializedName("startDate")
    private String startDate;

    @SerializedName("endDate")
    private String endDate;

    @SerializedName("usageLimit")
    private Integer usageLimit; // Số lần sử dụng tối đa

    @SerializedName("usedCount")
    private Integer usedCount; // Số lần đã sử dụng

    @SerializedName("createdAt")
    private String createdAt;

    @SerializedName("updatedAt")
    private String updatedAt;

    public Voucher() {}

    // Getters / Setters
    public String getId() { return id == null ? "" : id; }
    public void setId(String id) { this.id = id; }

    public String getCode() { return code == null ? "" : code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name == null ? "" : name; }
    public void setName(String name) { this.name = name; }

    public String getDiscountType() { return discountType == null ? "" : discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }

    public double getDiscountValue() { return discountValue == null ? 0.0 : discountValue; }
    public void setDiscountValue(Double discountValue) { this.discountValue = discountValue; }

    public double getMinOrderAmount() { return minOrderAmount == null ? 0.0 : minOrderAmount; }
    public void setMinOrderAmount(Double minOrderAmount) { this.minOrderAmount = minOrderAmount; }

    public double getMaxDiscountAmount() { return maxDiscountAmount == null ? 0.0 : maxDiscountAmount; }
    public void setMaxDiscountAmount(Double maxDiscountAmount) { this.maxDiscountAmount = maxDiscountAmount; }

    public String getStatus() { 
        // Nếu status là "true" hoặc "false" (từ isActive), convert sang "active"/"inactive"
        if (status != null) {
            if ("true".equalsIgnoreCase(status.trim())) return "active";
            if ("false".equalsIgnoreCase(status.trim())) return "inactive";
        }
        return status == null ? "" : status; 
    }
    public void setStatus(String status) { this.status = status; }

    public String getStartDate() { return startDate == null ? "" : startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate == null ? "" : endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public int getUsageLimit() { return usageLimit == null ? 0 : usageLimit; }
    public void setUsageLimit(Integer usageLimit) { this.usageLimit = usageLimit; }

    public int getUsedCount() { return usedCount == null ? 0 : usedCount; }
    public void setUsedCount(Integer usedCount) { this.usedCount = usedCount; }

    public String getCreatedAt() { return createdAt == null ? "" : createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt == null ? "" : updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    /**
     * Tính số tiền giảm giá dựa trên tổng tiền đơn hàng
     */
    public double calculateDiscount(double orderTotal) {
        if (orderTotal < getMinOrderAmount()) {
            return 0.0;
        }

        if ("percentage".equalsIgnoreCase(getDiscountType())) {
            double discount = orderTotal * getDiscountValue() / 100.0;
            if (getMaxDiscountAmount() > 0 && discount > getMaxDiscountAmount()) {
                return getMaxDiscountAmount();
            }
            return discount;
        } else if ("fixed".equalsIgnoreCase(getDiscountType())) {
            return getDiscountValue();
        }

        return 0.0;
    }

    /**
     * Kiểm tra voucher có hợp lệ không
     */
    public boolean isValid() {
        // Nếu status rỗng hoặc null, coi như hợp lệ (có thể database chưa set status)
        String status = getStatus();
        if (status != null && !status.trim().isEmpty()) {
            // Nếu có status, chỉ chấp nhận "active"
            if (!"active".equalsIgnoreCase(status.trim())) {
                return false;
            }
        }

        // Kiểm tra usage limit
        if (getUsageLimit() > 0 && getUsedCount() >= getUsageLimit()) {
            return false;
        }

        // Có thể thêm kiểm tra ngày hết hạn nếu cần
        return true;
    }
    
    /**
     * Kiểm tra voucher có thể áp dụng được không (không kiểm tra status quá strict)
     */
    public boolean canApply() {
        // Chỉ kiểm tra usage limit, không kiểm tra status
        if (getUsageLimit() > 0 && getUsedCount() >= getUsageLimit()) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "Voucher{" +
                "id='" + getId() + '\'' +
                ", code='" + getCode() + '\'' +
                ", name='" + getName() + '\'' +
                ", discountType='" + getDiscountType() + '\'' +
                ", discountValue=" + getDiscountValue() +
                ", minOrderAmount=" + getMinOrderAmount() +
                ", status='" + getStatus() + '\'' +
                '}';
    }

    private Voucher appliedVoucher;

    public Voucher getAppliedVoucher() {
        return appliedVoucher;
    }

    public void setAppliedVoucher(Voucher appliedVoucher) {
        this.appliedVoucher = appliedVoucher;
    }
}

