package com.ph48845.datn_qlnh_rmis.data.respository;

import android.util.Log;
import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;
import com.ph48845.datn_qlnh_rmis.core.utils.Constants;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class MenuRepository {

    private Connection connect() {
        try {
            Class.forName("net.sourceforge.jtds.jdbc.Driver");
            String url = "jdbc:jtds:sqlserver://" +
                    Constants.DB_IP + ":" + Constants.DB_PORT + "/" + Constants.DB_NAME;
            return DriverManager.getConnection(url, Constants.DB_USER, Constants.DB_PASS);
        } catch (Exception e) {
            Log.e("MenuRepo", "❌ Lỗi kết nối SQL: " + e.getMessage());
            return null;
        }
    }

    public List<MenuItem> getAllMenu() {
        List<MenuItem> list = new ArrayList<>();
        String query = "SELECT * FROM Menu";

        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(new MenuItem(
                        rs.getString("TenMon"),
                        rs.getDouble("Gia"),
                        rs.getString("LoaiMon"),
                        rs.getString("status")
                ));
            }

        } catch (Exception e) {
            Log.e("MenuRepo", "❌ Lỗi đọc dữ liệu: " + e.getMessage());
        }
        return list;
    }
}
