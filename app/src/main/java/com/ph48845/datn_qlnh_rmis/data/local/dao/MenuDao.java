package com.ph48845.datn_qlnh_rmis.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.lifecycle.LiveData;

import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;

import java.util.List;

@Dao
public interface MenuDao {
    @Query("SELECT * FROM menu")
    LiveData<List<MenuItem>> getAllMenus();

    @Query("SELECT * FROM menu WHERE id = :id LIMIT 1")
    MenuItem getMenuById(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<MenuItem> menus);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(MenuItem menu);

    @Query("DELETE FROM menu")
    void clearAll();
}
