package com.ph48845.datn_qlnh_rmis.data.model;



import android.graphics.Bitmap;
import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class MenuItem implements Serializable {

    @SerializedName("_id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("price")
    private double price;

    @SerializedName("category")
    private String category;

    @SerializedName("status")
    private String status; // e.g., "available", "unavailable"

    @SerializedName("createdAt")
    private String createdAt;

    // New: store image as a URL (recommended) returned by your backend or storage service
    @SerializedName("imageUrl")
    private String imageUrl;

    // Optional: local-only Bitmap for UI use (not serialized). Mark transient so it won't be serialized.
    private transient Bitmap imageBitmap;

    public MenuItem() {}

    public MenuItem(String name, double price, String category, String status) {
        this.name = name;
        this.price = price;
        this.category = category;
        this.status = status;
    }

    public MenuItem(String id, String name, double price, String category, String status, String createdAt) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.category = category;
        this.status = status;
        this.createdAt = createdAt;
    }

    // New constructors including imageUrl
    public MenuItem(String name, double price, String category, String status, String imageUrl) {
        this.name = name;
        this.price = price;
        this.category = category;
        this.status = status;
        this.imageUrl = imageUrl;
    }

    public MenuItem(String id, String name, double price, String category, String status, String createdAt, String imageUrl) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.category = category;
        this.status = status;
        this.createdAt = createdAt;
        this.imageUrl = imageUrl;
    }

    // Getters / Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public Bitmap getImageBitmap() { return imageBitmap; }
    public void setImageBitmap(Bitmap imageBitmap) { this.imageBitmap = imageBitmap; }

    @Override
    public String toString() {
        return "MenuItem{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", price=" + price +
                ", category='" + category + '\'' +
                ", status='" + status + '\'' +
                ", createdAt='" + createdAt + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                '}';
    }
}