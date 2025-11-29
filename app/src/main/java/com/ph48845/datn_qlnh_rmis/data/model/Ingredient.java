package com.ph48845.datn_qlnh_rmis.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Model mapping cho nguyên liệu (ingredient)
 * - Đã loại bỏ tag và unit theo yêu cầu, sử dụng image để hiển thị ảnh
 */
public class Ingredient {

    @SerializedName("_id")
    private String id;

    private String name;

    private double quantity;
    private double minQuantity;
    private String status;
    private String image;
    private String description;
    private String supplier;

    @SerializedName("lastRestocked")
    private String lastRestocked;

    @SerializedName("createdAt")
    private String createdAt;

    @SerializedName("updatedAt")
    private String updatedAt;

    public Ingredient() {}

    // getters & setters (chỉ những field còn dùng)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public double getMinQuantity() { return minQuantity; }
    public void setMinQuantity(double minQuantity) { this.minQuantity = minQuantity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }

    public String getLastRestocked() { return lastRestocked; }
    public void setLastRestocked(String lastRestocked) { this.lastRestocked = lastRestocked; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}