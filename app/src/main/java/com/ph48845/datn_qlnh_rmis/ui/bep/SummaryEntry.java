package com.ph48845.datn_qlnh_rmis.ui.bep;

/**
 * Simple DTO for summary list (aggregated by menu name).
 */
public class SummaryEntry {
    private final String name;
    private final int qty;
    private final String imageUrl;

    public SummaryEntry(String name, int qty, String imageUrl) {
        this.name = name == null ? "(Không tên)" : name;
        this.qty = qty;
        this.imageUrl = imageUrl == null ? "" : imageUrl;
    }

    public String getName() {
        return name;
    }

    public int getQty() {
        return qty;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}