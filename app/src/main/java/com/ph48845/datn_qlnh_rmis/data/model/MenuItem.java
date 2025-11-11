package com.ph48845.datn_qlnh_rmis.data.model;




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

    @Override
    public String toString() {
        return "MenuItem{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", price=" + price +
                ", category='" + category + '\'' +
                ", status='" + status + '\'' +
                ", createdAt='" + createdAt + '\'' +
                '}';
    }
}