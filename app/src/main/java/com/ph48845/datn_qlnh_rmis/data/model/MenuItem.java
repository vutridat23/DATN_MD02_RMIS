package com.ph48845.datn_qlnh_rmis.data.model;

public class MenuItem {
	private String id;
    private String name;
	// price theo minor units
	private long priceMinor;
    private String category;

    // Constructors
	public MenuItem(String name, long priceMinor, String category) {
        this.name = name;
		this.priceMinor = priceMinor;
        this.category = category;
    }

    // Getters & Setters
	public String getId() { return id; }
	public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
	public long getPriceMinor() { return priceMinor; }
	public void setPriceMinor(long priceMinor) { this.priceMinor = priceMinor; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
