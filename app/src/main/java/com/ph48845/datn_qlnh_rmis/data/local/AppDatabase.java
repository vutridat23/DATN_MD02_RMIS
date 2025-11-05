package com.ph48845.datn_qlnh_rmis.data.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;

@Database(entities = {MenuItem.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract com.ph48845.datn_qlnh_rmis.data.local.dao.MenuDao menuDao();
}
