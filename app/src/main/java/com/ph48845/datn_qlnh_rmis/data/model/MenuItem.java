package com.ph48845.datn_qlnh_rmis.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "menu")
public class MenuItem {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String name;
    private double price;
    private String category;

    // Constructors
    public MenuItem(String name, double price, String category) {
        this.name = name;
        this.price = price;
        this.category = category;
    }

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
