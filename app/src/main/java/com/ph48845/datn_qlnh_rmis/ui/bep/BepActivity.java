package com.ph48845.datn_qlnh_rmis.ui.bep;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import java.util.ArrayList;
import java.util.List;

public class BepActivity extends AppCompatActivity {
    private BepViewModel bepViewModel;
    private com.ph48845.datn_qlnh_rmis.ui.bep.adapter.OrderItemAdapter orderItemAdapter;
    private List<Order> allOrders = new ArrayList<>(); // load từ DB hoặc repo ở thực tế

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bep);

        bepViewModel = new ViewModelProvider(this).get(BepViewModel.class);

        RecyclerView recyclerView = findViewById(R.id.recyclerOrderBep);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        orderItemAdapter = new com.ph48845.datn_qlnh_rmis.ui.bep.adapter.OrderItemAdapter(new ArrayList<>(), (item, newStatus) -> {
            Order order = bepViewModel.findOrderOfItem(item);
            bepViewModel.updateOrderItemStatus(order, item, newStatus);
        });
        recyclerView.setAdapter(orderItemAdapter);

        bepViewModel.getBepItemsLiveData().observe(this, orderItemAdapter::setData);

        // TODO: Lấy allOrders từ DB/repository rồi gọi (ở đây demo sample)
        // allOrders = ...;
//        bepViewModel.loadItemsForBep(allOrders);
    }
}