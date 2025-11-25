package com.ph48845.datn_qlnh_rmis.data.model;

import com.google.gson.annotations.SerializedName;

public class RevenueItem {

    @SerializedName("id")
    private String id; // _id của order

    @SerializedName("date")
    private String date; // yyyy-MM-dd

    @SerializedName("totalAmount")
    private double totalAmount;

    @SerializedName("totalOrders")
    private int totalOrders;

    public RevenueItem() {
    }

    public RevenueItem(String id, String date, double totalAmount, int totalOrders) {
        this.id = id;
        this.date = date;
        this.totalAmount = totalAmount;
        this.totalOrders = totalOrders;
    }

    public String getId() {
        return id;
    }

    public String getDate() {
        return date;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public int getTotalOrders() {
        return totalOrders;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public void setTotalOrders(int totalOrders) {
        this.totalOrders = totalOrders;
    }
}
