package com.ph48845.datn_qlnh_rmis.data.model;

import com.google.gson.annotations.SerializedName;

public class RevenueItem {

    @SerializedName("_id")
    private String date; // Ngày doanh thu
    private double totalRevenue; // Tổng doanh thu ngày đó
    @SerializedName("countOrders")
    private int invoiceCount; // Số lượng hóa đơn

    public RevenueItem(String date, double totalRevenue, int invoiceCount) {
        this.date = date;
        this.totalRevenue = totalRevenue;
        this.invoiceCount = invoiceCount;
    }

    public String getDate() {
        return date;
    }

    public double getTotalRevenue() {
        return totalRevenue;
    }

    public int getInvoiceCount() {
        return invoiceCount;
    }
}
