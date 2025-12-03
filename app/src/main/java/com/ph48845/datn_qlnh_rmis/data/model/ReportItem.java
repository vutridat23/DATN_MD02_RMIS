package com.ph48845.datn_qlnh_rmis.data.model;
import java.util.Date;
import java.util.Map;

public class ReportItem {
    private String id; // _id từ MongoDB
    private String reportType;
    private Date date;
    private String timeFrame;
    private double totalRevenue;
    private int totalOrders;
    private double totalDiscountGiven;
    private double averageOrderValue;
    private Map<String, Object> details; // chi tiết có thể là Map hoặc List
    private Date generatedAt;

    // Constructor rỗng (bắt buộc cho Firebase / Gson / Retrofit)
    public ReportItem() {}

    // Constructor đầy đủ
    public ReportItem(String reportType, Date date, String timeFrame, double totalRevenue,
                      int totalOrders, double totalDiscountGiven, double averageOrderValue,
                      Map<String, Object> details, Date generatedAt) {
        this.reportType = reportType;
        this.date = date;
        this.timeFrame = timeFrame;
        this.totalRevenue = totalRevenue;
        this.totalOrders = totalOrders;
        this.totalDiscountGiven = totalDiscountGiven;
        this.averageOrderValue = averageOrderValue;
        this.details = details;
        this.generatedAt = generatedAt;
    }

    // Getter và Setter
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }

    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }

    public String getTimeFrame() { return timeFrame; }
    public void setTimeFrame(String timeFrame) { this.timeFrame = timeFrame; }

    public double getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(double totalRevenue) { this.totalRevenue = totalRevenue; }

    public int getTotalOrders() { return totalOrders; }
    public void setTotalOrders(int totalOrders) { this.totalOrders = totalOrders; }

    public double getTotalDiscountGiven() { return totalDiscountGiven; }
    public void setTotalDiscountGiven(double totalDiscountGiven) { this.totalDiscountGiven = totalDiscountGiven; }

    public double getAverageOrderValue() { return averageOrderValue; }
    public void setAverageOrderValue(double averageOrderValue) { this.averageOrderValue = averageOrderValue; }

    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> details) { this.details = details; }

    public Date getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Date generatedAt) { this.generatedAt = generatedAt; }
}
