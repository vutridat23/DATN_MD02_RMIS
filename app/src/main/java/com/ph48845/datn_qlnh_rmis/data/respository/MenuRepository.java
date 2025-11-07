package com.ph48845.datn_qlnh_rmis.data.respository;

import android.util.Log;

import com.ph48845.datn_qlnh_rmis.core.utils.Constants;
import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class MenuRepository {

    private static final String TAG = "MenuRepository";

    // Hàm kết nối SQL Server
    private Connection connect() {
        Connection connection = null;
        try {
            Class.forName("net.sourceforge.jtds.jdbc.Driver");
            String url = "jdbc:jtds:sqlserver://" +
                    Constants.DB_IP + ":" + Constants.DB_PORT + "/" + Constants.DB_NAME;
            connection = DriverManager.getConnection(url, Constants.DB_USER, Constants.DB_PASS);
            Log.d(TAG, "✅ Kết nối SQL Server thành công");
        } catch (Exception e) {
            Log.e(TAG, "❌ Lỗi kết nối SQL: " + e.getMessage(), e);
        }
        return connection;
    }

    // Lấy danh sách toàn bộ món ăn từ DB
    public List<MenuItem> getAllMenu() {
        List<MenuItem> menuList = new ArrayList<>();

        String query = "SELECT TenMon, Gia, LoaiMon FROM Menu";

        try (Connection conn = connect()) {
            if (conn == null) {
                Log.e(TAG, "⚠️ Không thể kết nối SQL, trả về danh sách rỗng");
                return menuList;
            }

            try (PreparedStatement ps = conn.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    String tenMon = rs.getString("TenMon");
                    double gia = rs.getDouble("Gia");
                    String loaiMon = rs.getString("LoaiMon");

                    menuList.add(new MenuItem(tenMon, gia, loaiMon));
                }

                Log.d(TAG, "✅ Đã tải " + menuList.size() + " món ăn từ DB");
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Lỗi khi đọc dữ liệu Menu: " + e.getMessage(), e);
        }

        return menuList;
    }
}
