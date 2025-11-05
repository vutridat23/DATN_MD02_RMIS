package com.ph48845.datn_qlnh_rmis.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.lifecycle.LiveData;

import com.ph48845.datn_qlnh_rmis.data.local.entity.OrderEntity;

import java.util.List;

@Dao
public interface OrderDao {
    @Query("SELECT * FROM orders ORDER BY created_at DESC")
    LiveData<List<OrderEntity>> getAllOrders();

    @Query("SELECT * FROM orders WHERE order_id = :id LIMIT 1")
    OrderEntity getOrderById(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(OrderEntity order);

    @Query("DELETE FROM orders")
    void clearAll();
}
