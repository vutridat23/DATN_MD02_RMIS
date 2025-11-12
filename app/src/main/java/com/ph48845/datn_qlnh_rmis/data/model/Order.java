package com.ph48845.datn_qlnh_rmis.data.model;

import java.util.List;

public class Order {
    private String _id;
    private int tableNumber;
    private String server; // ObjectId dạng chuỗi
    private String cashier;
    private List<Item> items;
    private double totalAmount;
    private double discount;
    private double finalAmount;
    private double paidAmount;
    private double change;
    private String paymentMethod;
    private String orderStatus;

    // 1. ✅ THÊM HÀM TẠO RỖNG (Quan trọng để ViewModelProvider và thư viện tạo Order dễ dàng)
    public Order() {
        // Hàm tạo mặc định/rỗng
    }

    // 2. Hàm tạo đầy đủ (giữ lại)
    public Order(int tableNumber, String server, String cashier, List<Item> items,
                 double totalAmount, double discount, double finalAmount,
                 double paidAmount, double change, String paymentMethod, String orderStatus) {
        this.tableNumber = tableNumber;
        this.server = server;
        this.cashier = cashier;
        this.items = items;
        this.totalAmount = totalAmount;
        this.discount = discount;
        this.finalAmount = finalAmount;
        this.paidAmount = paidAmount;
        this.change = change;
        this.paymentMethod = paymentMethod;
        this.orderStatus = orderStatus;
    }

    public static class Item {
        private String menuItem;
        private int quantity;
        private double price;

        public Item(String menuItem, int quantity, double price) {
            this.menuItem = menuItem;
            this.quantity = quantity;
            this.price = price;
        }

        // Giữ lại tất cả các getter và setter cho Item
        public String getMenuItem() { return menuItem; }
        public void setMenuItem(String menuItem) { this.menuItem = menuItem; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
    }

    // --- Getter & Setter cần thiết cho ThuNganActivity và ViewModel ---

    // 3. ✅ Getter & Setter cho items (Lỗi 'setItems' trong ThuNganActivity đã được sửa)
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    // 4. ✅ Getter & Setter cho totalAmount
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    // 5. Các Getter khác (đã có, giữ lại và bổ sung thêm)
    public String get_id() { return _id; }
    public int getTableNumber() { return tableNumber; }
    public double getFinalAmount() { return finalAmount; }
    public double getDiscount() { return discount; } // Cần thiết cho ViewModel
    public double getPaidAmount() { return paidAmount; } // Có thể cần nếu xử lý tiền thừa
    public double getChange() { return change; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getOrderStatus() { return orderStatus; }

    // Bạn có thể thêm các setter khác nếu cần thay đổi các trường này (ví dụ: setDiscount, setFinalAmount)
    // Nhưng totalAmount và items là 2 trường quan trọng nhất cho việc cập nhật hóa đơn.
}