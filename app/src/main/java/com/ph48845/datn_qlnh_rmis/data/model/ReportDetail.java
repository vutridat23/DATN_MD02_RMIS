package com.ph48845.datn_qlnh_rmis.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ReportDetail {
    @SerializedName("summary")
    private Summary summary;

    @SerializedName("dailyRevenue")
    private List<DailyRevenue> dailyRevenue;

    @SerializedName("hourlyRevenue")
    private List<HourlyRevenue> hourlyRevenue;

    @SerializedName("topDishes")
    private List<TopDish> topDishes;

    @SerializedName("peakHours")
    private List<PeakHour> peakHours;

    // Getters and Setters
    public Summary getSummary() {
        return summary;
    }

    public void setSummary(Summary summary) {
        this.summary = summary;
    }

    public List<DailyRevenue> getDailyRevenue() {
        return dailyRevenue;
    }

    public void setDailyRevenue(List<DailyRevenue> dailyRevenue) {
        this.dailyRevenue = dailyRevenue;
    }

    public List<HourlyRevenue> getHourlyRevenue() {
        return hourlyRevenue;
    }

    public void setHourlyRevenue(List<HourlyRevenue> hourlyRevenue) {
        this.hourlyRevenue = hourlyRevenue;
    }

    public List<TopDish> getTopDishes() {
        return topDishes;
    }

    public void setTopDishes(List<TopDish> topDishes) {
        this.topDishes = topDishes;
    }

    public List<PeakHour> getPeakHours() {
        return peakHours;
    }

    public void setPeakHours(List<PeakHour> peakHours) {
        this.peakHours = peakHours;
    }

    // Inner class for Summary
    public static class Summary {
        @SerializedName("totalRevenue")
        private double totalRevenue;

        @SerializedName("totalOrders")
        private int totalOrders;

        @SerializedName("averageOrderValue")
        private double averageOrderValue;

        @SerializedName("totalCustomers")
        private int totalCustomers;

        // Getters and Setters
        public double getTotalRevenue() {
            return totalRevenue;
        }

        public void setTotalRevenue(double totalRevenue) {
            this.totalRevenue = totalRevenue;
        }

        public int getTotalOrders() {
            return totalOrders;
        }

        public void setTotalOrders(int totalOrders) {
            this.totalOrders = totalOrders;
        }

        public double getAverageOrderValue() {
            return averageOrderValue;
        }

        public void setAverageOrderValue(double averageOrderValue) {
            this.averageOrderValue = averageOrderValue;
        }

        public int getTotalCustomers() {
            return totalCustomers;
        }

        public void setTotalCustomers(int totalCustomers) {
            this.totalCustomers = totalCustomers;
        }
    }

    // Inner class for DailyRevenue
    public static class DailyRevenue {
        @SerializedName("date")
        private String date;

        @SerializedName("revenue")
        private double revenue;

        @SerializedName("orders")
        private int orders;

        // Getters and Setters
        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
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
    }

    // Inner class for TopDish
    public static class TopDish {
        @SerializedName("dishName")
        private String dishName;

        @SerializedName("quantity")
        private int quantity;

        @SerializedName("revenue")
        private double revenue;

        // Getters and Setters
        public String getDishName() {
            return dishName;
        }

        public void setDishName(String dishName) {
            this.dishName = dishName;
        }

        public int getQuantity() {
            return quantity;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        public double getRevenue() {
            return revenue;
        }

        public void setRevenue(double revenue) {
            this.revenue = revenue;
        }
    }
}
