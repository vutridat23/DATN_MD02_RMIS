package com.ph48845.datn_qlnh_rmis.data.model;

import com.google.gson.annotations.SerializedName;

public class PeakHour {
    @SerializedName("hour")
    private int hour;

    @SerializedName("revenue")
    private double revenue;

    @SerializedName("orders")
    private int orders;

    // Constructors
    public PeakHour() {
    }

    public PeakHour(int hour, double revenue, int orders) {
        this.hour = hour;
        this.revenue = revenue;
        this.orders = orders;
    }

    // Getters and Setters
    public int getHour() {
        return hour;
    }

    public void setHour(int hour) {
        this.hour = hour;
    }

    public double getRevenue() {
        return revenue;
    }

    public void setRevenue(double revenue) {
        this.revenue = revenue;
    }

    public int getOrders() {
        return orders;
    }

    public void setOrders(int orders) {
        this.orders = orders;
    }

    // Helper method
    public String getHourText() {
        return String.format("%02d:00 - %02d:00", hour, (hour + 1) % 24);
    }
}
