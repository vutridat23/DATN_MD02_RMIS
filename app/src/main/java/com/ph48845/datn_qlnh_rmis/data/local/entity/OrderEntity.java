package com.ph48845.datn_qlnh_rmis.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

@Entity(tableName = "orders")
public class OrderEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "order_id")
    private String orderId;

    @ColumnInfo(name = "table_id")
    private String tableId;

    @ColumnInfo(name = "waiter_id")
    private String waiterId;

    @ColumnInfo(name = "created_at")
    private long createdAt; // store as epoch millis

    @ColumnInfo(name = "paid")
    private boolean paid;

    @ColumnInfo(name = "items_json")
    private String itemsJson; // store order items as JSON (simpler)

    public OrderEntity(@NonNull String orderId, String tableId, String waiterId, long createdAt, boolean paid, String itemsJson) {
        this.orderId = orderId;
        this.tableId = tableId;
        this.waiterId = waiterId;
        this.createdAt = createdAt;
        this.paid = paid;
        this.itemsJson = itemsJson;
    }

    @NonNull public String getOrderId() { return orderId; }
    public void setOrderId(@NonNull String orderId) { this.orderId = orderId; }
    public String getTableId() { return tableId; }
    public void setTableId(String tableId) { this.tableId = tableId; }
    public String getWaiterId() { return waiterId; }
    public void setWaiterId(String waiterId) { this.waiterId = waiterId; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }
    public String getItemsJson() { return itemsJson; }
    public void setItemsJson(String itemsJson) { this.itemsJson = itemsJson; }
}
