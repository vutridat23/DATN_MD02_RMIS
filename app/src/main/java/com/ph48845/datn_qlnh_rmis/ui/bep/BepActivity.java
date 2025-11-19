package com.ph48845.datn_qlnh_rmis.ui.bep;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.RecyclerView;
import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.ui.bep.adapter.OrderItemAdapter;
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
    }
}