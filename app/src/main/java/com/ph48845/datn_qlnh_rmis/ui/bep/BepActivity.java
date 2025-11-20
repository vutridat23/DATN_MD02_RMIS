package com.ph48845.datn_qlnh_rmis.ui.bep;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.ui.bep.adapter.OrderItemAdapter;


/**
 * Activity for kitchen. Shows flattened items with parent order context and 4 status buttons.
 * Fixed lambda parameter count for adapter listener.
 */
public class BepActivity extends AppCompatActivity {

    private BepViewModel viewModel;
    private RecyclerView recyclerView;
    private OrderItemAdapter adapter;
    private ProgressBar progress;
    private Button btnRealtimeToggle;
    private Button btnRefresh;
    private boolean realtimeOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bep);

        viewModel = new ViewModelProvider(this).get(BepViewModel.class);

        recyclerView = findViewById(R.id.recyclerOrderBep);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        progress = findViewById(R.id.progress_bep);
        btnRealtimeToggle = findViewById(R.id.btn_bep_realtime);
        btnRefresh = findViewById(R.id.btn_bep_refresh);

        // Create adapter with a listener that accepts two parameters: (ItemWithOrder, newStatus)
        adapter = new OrderItemAdapter((wrapper, newStatus) -> {
            // call ViewModel to change status
            viewModel.changeItemStatus(wrapper, newStatus);
        });

        recyclerView.setAdapter(adapter);

        // Observe data
        viewModel.getItemsLive().observe(this, items -> {
            adapter.setItems(items);
        });

        viewModel.getLoadingLive().observe(this, loading -> {
            if (progress != null) progress.setVisibility(loading != null && loading ? View.VISIBLE : View.GONE);
        });

        viewModel.getErrorLive().observe(this, err -> {
            if (err != null && !err.isEmpty()) {
                // you can show Toast or Snackbar here
            }
        });

        // Button: refresh
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> viewModel.fetchOrders());
        }

        // Button: realtime toggle
        if (btnRealtimeToggle != null) {
            btnRealtimeToggle.setOnClickListener(v -> {
                realtimeOn = !realtimeOn;
                if (realtimeOn) {
                    viewModel.startRealtime("http://192.168.1.84:3000"); // change to your socket URL
                    btnRealtimeToggle.setText("Realtime ON");
                } else {
                    viewModel.stopRealtime();
                    btnRealtimeToggle.setText("Realtime OFF");
                }
            });
        }

        // initial load
        viewModel.fetchOrders();
    }

    @Override
    protected void onDestroy() {
        viewModel.stopRealtime();
        super.onDestroy();
    }
}