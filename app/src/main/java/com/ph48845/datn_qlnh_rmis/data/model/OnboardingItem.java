package com.ph48845.datn_qlnh_rmis.data.model;

public class OnboardingItem {
    private int image;       // ID của hình ảnh (R.drawable.xxx)
    private String title;    // Tiêu đề (ví dụ: Gọi món)
    private String description; // Mô tả chi tiết

    public OnboardingItem(int image, String title, String description) {
        this.image = image;
        this.title = title;
        this.description = description;
    }

    public int getImage() { return image; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
}