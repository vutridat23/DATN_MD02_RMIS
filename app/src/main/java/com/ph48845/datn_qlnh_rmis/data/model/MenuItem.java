package com.ph48845.datn_qlnh_rmis.data.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/**
 * MenuItem model with tolerant deserialization:
 * - Accepts alternate field names for name, price and image.
 * - Uses boxed Double for price so we can detect missing values.
 */
public class MenuItem implements Serializable {

    @SerializedName("_id")
    private String id;

    @SerializedName(value = "name", alternate = {"title", "menuName"})
    private String name;

    // Accept multiple possible field names for price
    @SerializedName(value = "price", alternate = {"cost", "unitPrice"})
    private Double price;

    @SerializedName(value = "category", alternate = {"type"})
    private String category;

    @SerializedName(value = "status", alternate = {"availability"})
    private String status; // e.g., "available", "unavailable"

    @SerializedName("createdAt")
    private String createdAt;

    // Accept multiple possible image fields
    @SerializedName(value = "imageUrl", alternate = {"image", "thumbnail", "img"})
    private String imageUrl;

    // transient local-only bitmap (not serialized)
    private transient android.graphics.Bitmap imageBitmap;

    public MenuItem() {}

    public MenuItem(String name, Double price, String category, String status) {
        this.name = name;
        this.price = price;
        this.category = category;
        this.status = status;
    }

    public MenuItem(String id, String name, Double price, String category, String status, String createdAt, String imageUrl) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.category = category;
        this.status = status;
        this.createdAt = createdAt;
        this.imageUrl = imageUrl;
    }

    // Getters / Setters (defensive: never return nulls for strings)
    public String getId() { return id == null ? "" : id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name == null ? "" : name; }
    public void setName(String name) { this.name = name; }

    public double getPrice() { return price == null ? 0.0 : price; }
    public void setPrice(Double price) { this.price = price; }

    public String getCategory() { return category == null ? "" : category; }
    public void setCategory(String category) { this.category = category; }

    public String getStatus() { return status == null ? "" : status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt == null ? "" : createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getImageUrl() { return imageUrl == null ? "" : imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public android.graphics.Bitmap getImageBitmap() { return imageBitmap; }
    public void setImageBitmap(android.graphics.Bitmap imageBitmap) { this.imageBitmap = imageBitmap; }

    @Override
    public String toString() {
        return "MenuItem{" +
                "id='" + id + '\'' +
                ", name='" + getName() + '\'' +
                ", price=" + getPrice() +
                ", category='" + getCategory() + '\'' +
                ", status='" + getStatus() + '\'' +
                ", createdAt='" + getCreatedAt() + '\'' +
                ", imageUrl='" + getImageUrl() + '\'' +
                '}';
    }
}