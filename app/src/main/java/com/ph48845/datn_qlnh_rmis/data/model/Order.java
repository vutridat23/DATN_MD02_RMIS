package com.ph48845.datn_qlnh_rmis.data.model;

import java.util.List;

public class Order {
    private String orderId;
	private String clientOrderId;
    private String tableId;
    private String waiterId;
    private long createdAt;
    private boolean paid;
	// totalAmount tính theo đơn vị nhỏ nhất (minor units), ví dụ: VND -> đồng
	private long totalAmountMinor;
    private List<OrderItem> items;

    public Order() {
    }

    // Getters & Setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

	public String getClientOrderId() { return clientOrderId; }
	public void setClientOrderId(String clientOrderId) { this.clientOrderId = clientOrderId; }

    public String getTableId() { return tableId; }
    public void setTableId(String tableId) { this.tableId = tableId; }

    public String getWaiterId() { return waiterId; }
    public void setWaiterId(String waiterId) { this.waiterId = waiterId; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }

	public long getTotalAmountMinor() { return totalAmountMinor; }
	public void setTotalAmountMinor(long totalAmountMinor) { this.totalAmountMinor = totalAmountMinor; }

    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }

    // Nested model for order items
    public static class OrderItem {
		public enum OrderItemStatus {
			PENDING, PREPARING, READY, SERVED, CANCELED
		}

		private String menuItemId;
		private String menuItemName;
		// price theo minor units
		private long priceMinor;
		private int quantity;
		private OrderItemStatus status;

        public OrderItem() {}

		public OrderItem(String menuItemId, String menuItemName, long priceMinor, int quantity, OrderItemStatus status) {
            this.menuItemId = menuItemId;
            this.menuItemName = menuItemName;
			this.priceMinor = priceMinor;
            this.quantity = quantity;
            this.status = status;
        }

        public String getMenuItemId() { return menuItemId; }
        public void setMenuItemId(String menuItemId) { this.menuItemId = menuItemId; }

        public String getMenuItemName() { return menuItemName; }
        public void setMenuItemName(String menuItemName) { this.menuItemName = menuItemName; }

		public long getPriceMinor() { return priceMinor; }
		public void setPriceMinor(long priceMinor) { this.priceMinor = priceMinor; }

        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }

		public OrderItemStatus getStatus() { return status; }
		public void setStatus(OrderItemStatus status) { this.status = status; }
    }
}
