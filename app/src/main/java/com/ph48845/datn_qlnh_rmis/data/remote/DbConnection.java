package com.ph48845.datn_qlnh_rmis.data.remote;

import android.util.Log;

import java.sql.Connection;
import java.sql.DriverManager;

public class DbConnection {

        private static Connection conn;

        public static Connection getConnection() {
            try {
                if (conn == null || conn.isClosed()) {
                    // ⚠️ NHỚ: Thay IP bằng IP máy SQL thật của bạn!
                    String ip = "192.168.1.103";
                    String port = "1433";
                    String db = "QuanLyNhaHang";
                    String user = "sa";
                    String pass = "123456";

                    Class.forName("net.sourceforge.jtds.jdbc.Driver");
                    String url = "jdbc:jtds:sqlserver://" + ip + ":" + port + "/" + db;

                    conn = DriverManager.getConnection(url, user, pass);
                    Log.d("DB", "✅ Kết nối SQL Server thành công!");
                }
            } catch (Exception e) {
                Log.e("DB", "❌ Lỗi kết nối SQL Server: " + e.getMessage());
                e.printStackTrace();
            }

            return conn;
        }
    }


