package com.ph48845.datn_qlnh_rmis.ui.bep;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.SocketManager;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.ui.bep.adapter.OrderItemAdapter;

import org.json.JSONObject;

import java.util.List;

public class BepActivity extends AppCompatActivity {
    private BepViewModel bepViewModel;
    private RecyclerView recyclerView;
    private OrderItemAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bep);

        bepViewModel = new BepViewModel();
        recyclerView = findViewById(R.id.recyclerOrderBep);
        adapter = new OrderItemAdapter((order, item, newStatus) ->
                bepViewModel.updateOrderItemStatus(order, item, newStatus)
        );
        recyclerView.setAdapter(adapter);

        bepViewModel.getBepItemsLiveData().observe(this, items ->
                adapter.setItems(items)
        );

        bepViewModel.fetchOrders();

        // --- Socket init: nhận realtime order từ server ---
        // Nếu test trên emulator: serverUrl = "http://10.0.2.2:3000"
        // Nếu test trên thiết bị thật: serverUrl = "http://<IP-máy-dev>:3000"
        String serverUrl = "http://10.0.2.2:3000";
        String restaurantId = "r1"; // thay giá trị thật
        String stationId = "grill"; // thay theo station bếp tương ứng

        SocketManager.init(serverUrl, restaurantId, stationId);

        // Đăng ký listener để cập nhật UI khi có order mới
        SocketManager.setOnNewOrderListener(new SocketManager.OnNewOrderListener() {
            @Override
            public void onNewOrder(JSONObject orderJson) {
                // Ở đây mình làm đơn giản: gọi fetchOrders để reload danh sách từ server.
                // Bạn có thể parse orderJson rồi thêm/update list cục bộ nếu muốn performance tốt hơn.
                runOnUiThread(() -> {
                    bepViewModel.fetchOrders();
                    Toast.makeText(BepActivity.this, "Đã nhận order mới", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Nếu muốn giữ kết nối giữa các màn, bỏ dòng này; ở demo ta disconnect khi Activity đóng
        SocketManager.removeOnNewOrderListener();
        SocketManager.disconnect();
    }
}