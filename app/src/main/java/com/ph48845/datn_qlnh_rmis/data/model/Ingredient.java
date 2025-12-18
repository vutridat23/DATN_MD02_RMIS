package com.ph48845.datn_qlnh_rmis.data.model;

import com.google.gson.annotations.SerializedName;

/**
 * Model mapping cho nguyên liệu (ingredient)
 */
public class Ingredient {

    @SerializedName("_id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("unit")
    private String unit;

    @SerializedName("category")
    private String category; // tuoi, kho, bia, ruou, gia_vi, do_uong, khac

    @SerializedName("tag")
    private String tag;

    @SerializedName("quantity")
    private double quantity;

    @SerializedName("minQuantity")
    private double minQuantity;

    @SerializedName("minThreshold")
    private double minThreshold;

    @SerializedName("importPrice")
    private double importPrice;

    @SerializedName("supplier")
    private String supplier;

    @SerializedName("image")
    private String image;

    @SerializedName("description")
    private String description;

    @SerializedName("status")
    private String status; // available, low_stock, out_of_stock

    @SerializedName("lastRestocked")
    private String lastRestocked;

    @SerializedName("expirationDate")
    private String expirationDate;

    @SerializedName("createdAt")
    private String createdAt;

    @SerializedName("updatedAt")
    private String updatedAt;

    public Ingredient() {
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public double getMinQuantity() {
        return minQuantity;
    }

    public void setMinQuantity(double minQuantity) {
        this.minQuantity = minQuantity;
    }

    public double getMinThreshold() {
        return minThreshold;
    }

    public void setMinThreshold(double minThreshold) {
        this.minThreshold = minThreshold;
    }

    public double getImportPrice() {
        return importPrice;
    }

    public void setImportPrice(double importPrice) {
        this.importPrice = importPrice;
    }

    public String getSupplier() {
        return supplier;
    }

    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLastRestocked() {
        return lastRestocked;
    }

    public void setLastRestocked(String lastRestocked) {
        this.lastRestocked = lastRestocked;
    }

    public String getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(String expirationDate) {
        this.expirationDate = expirationDate;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Helper methods
    public boolean isLowStock() {
        return "low_stock".equals(status);
    }

    public boolean isOutOfStock() {
        return "out_of_stock".equals(status);
    }

    public String getStatusText() {
        if (status == null)
            return "";
        switch (status) {
            case "available":
                return "Còn hàng";
            case "low_stock":
                return "Sắp hết";
            case "out_of_stock":
                return "Hết hàng";
            default:
                return status;
        }
    }

    public String getCategoryText() {
        if (category == null)
            return "Khác";
        switch (category) {
            case "tuoi":
                return "Tươi sống";
            case "kho":
                return "Khô";
            case "bia":
                return "Bia";
            case "ruou":
                return "Rượu";
            case "gia_vi":
                return "Gia vị";
            case "do_uong":
                return "Đồ uống";
            case "khac":
                return "Khác";
            default:
                return category;
        }
    }
}
