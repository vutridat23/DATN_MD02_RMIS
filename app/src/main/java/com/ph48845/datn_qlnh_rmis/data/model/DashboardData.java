package com.ph48845.datn_qlnh_rmis.data.model;

public class DashboardData {
    private int servingTables;
    private int waitingPayment;
    private int servingInvoices;
    private int paidToday;
    private int cookingDishes;
    private int overdueDishes;

    public DashboardData() {
    }

    public int getServingTables() {
        return servingTables;
    }

    public void setServingTables(int servingTables) {
        this.servingTables = servingTables;
    }

    public int getWaitingPayment() {
        return waitingPayment;
    }

    public void setWaitingPayment(int waitingPayment) {
        this.waitingPayment = waitingPayment;
    }

    public int getServingInvoices() {
        return servingInvoices;
    }

    public void setServingInvoices(int servingInvoices) {
        this.servingInvoices = servingInvoices;
    }

    public int getPaidToday() {
        return paidToday;
    }

    public void setPaidToday(int paidToday) {
        this.paidToday = paidToday;
    }

    public int getCookingDishes() {
        return cookingDishes;
    }

    public void setCookingDishes(int cookingDishes) {
        this.cookingDishes = cookingDishes;
    }

    public int getOverdueDishes() {
        return overdueDishes;
    }

    public void setOverdueDishes(int overdueDishes) {
        this.overdueDishes = overdueDishes;
    }
}
