package com.ph48845.datn_qlnh_rmis.ui.bep;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.ui.bep.adapter.OrderItemAdapter;

import java.util.ArrayList;

/**
 * BepActivity - màn cho Bếp
 *
 * Sửa để khớp với activity_bep.xml bạn gửi:
 * - recyclerOrderBep -> RecyclerView id
 * - progress_bep -> ProgressBar id
 * - btn_bep_realtime / btn_bep_refresh -> controls
 *
 * Giữ nguyên logic: dùng BepViewModel để fetchOrders(), start/stop realtime,
 * và changeItemStatus() khi bấm nút trong adapter.
 */
public class BepActivity extends AppCompatActivity implements OrderItemAdapter.OnActionListener {

    private static final String TAG = "BepActivity";
    private static final String SOCKET_URL = "http://192.168.1.84:3000"; // đổi theo server của bạn

    private RecyclerView rvKitchen;
    private ProgressBar progressBar;
    private Button btnRealtime;
    private Button btnRefresh;
    private TextView tvTitle;

    private BepViewModel viewModel;
    private OrderItemAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bep); // tương ứng với XML bạn gửi

        // Views (IDs must match activity_bep.xml)
        rvKitchen = findViewById(R.id.recyclerOrderBep);
        progressBar = findViewById(R.id.progress_bep);
        btnRealtime = findViewById(R.id.btn_bep_realtime);
        btnRefresh = findViewById(R.id.btn_bep_refresh);
        tvTitle = findViewById(R.id.tvTitleBep);

        // RecyclerView setup
        rvKitchen.setLayoutManager(new LinearLayoutManager(this));
        adapter = new OrderItemAdapter(this);
        adapter.setItems(new ArrayList<>());
        rvKitchen.setAdapter(adapter);

        // ViewModel
        viewModel = new ViewModelProvider(this).get(BepViewModel.class);

        // Observe items
        viewModel.getItemsLive().observe(this, items -> {
            if (items == null || items.isEmpty()) {
                adapter.setItems(new ArrayList<>());
            } else {
                adapter.setItems(items);
            }
        });

        // Observe loading
        viewModel.getLoadingLive().observe(this, loading -> {
            if (progressBar != null) progressBar.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE);
        });

        // Observe errors
        viewModel.getErrorLive().observe(this, err -> {
            if (err != null && !err.trim().isEmpty()) {
                Toast.makeText(BepActivity.this, err, Toast.LENGTH_LONG).show();
                Log.w(TAG, "Error: " + err);
            }
        });

        // Refresh button -> fetch orders
        btnRefresh.setOnClickListener(v -> {
            try {
                viewModel.fetchOrders();
            } catch (Exception e) {
                Log.e(TAG, "refresh failed: " + e.getMessage(), e);
                Toast.makeText(this, "Làm mới thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Realtime toggle button
        btnRealtime.setOnClickListener(v -> {
            String txt = btnRealtime.getText() != null ? btnRealtime.getText().toString() : "";
            if (txt.toLowerCase().contains("off")) {
                // start realtime
                try {
                    viewModel.startRealtime(SOCKET_URL);
                    btnRealtime.setText("Realtime ON");
                } catch (Exception e) {
                    Log.w(TAG, "startRealtime failed: " + e.getMessage(), e);
                    Toast.makeText(this, "Không thể bật realtime: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                // stop realtime
                try {
                    viewModel.stopRealtime();
                    btnRealtime.setText("Realtime OFF");
                } catch (Exception e) {
                    Log.w(TAG, "stopRealtime failed: " + e.getMessage(), e);
                    Toast.makeText(this, "Không thể tắt realtime: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Initial data load (do not forcibly start realtime)
        viewModel.fetchOrders();
    }

    @Override
    protected void onDestroy() {
        try {
            viewModel.stopRealtime();
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    /**
     * OrderItemAdapter.OnActionListener callback
     * Called when Bếp bấm 1 trong 4 nút (received/preparing/ready/soldout).
     * Gọi ViewModel để cập nhật trạng thái (ViewModel sẽ gọi API và cập nhật local).
     */
    @Override
    public void onChangeStatus(ItemWithOrder wrapper, String newStatus) {
        if (wrapper == null || newStatus == null) return;
        try {
            viewModel.changeItemStatus(wrapper, newStatus);
        } catch (Exception e) {
            Log.e(TAG, "changeItemStatus failed: " + e.getMessage(), e);
            Toast.makeText(this, "Không thể thay đổi trạng thái: " + (e.getMessage() != null ? e.getMessage() : ""), Toast.LENGTH_LONG).show();
        }
    }
}