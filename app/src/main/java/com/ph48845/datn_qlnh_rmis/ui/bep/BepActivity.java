package com.ph48845.datn_qlnh_rmis.ui.bep;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.ui.bep.adapter.BepTableAdapter;
import com.ph48845.datn_qlnh_rmis.ui.thungan.ThuNganViewModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * BepActivity - màn hình cho Bếp
 * 
 * Hiển thị danh sách các bàn đang hoạt động (có order).
 * Khi bấm vào một bàn, mở BepOrderActivity để xem danh sách món cần làm.
 */
public class BepActivity extends AppCompatActivity {

    private static final String TAG = "BepActivity";

    private RecyclerView rvKitchen;
    private ProgressBar progressBar;
    private Button btnRefresh;
    private TextView tvTitle;

    private BepTableAdapter adapter;
    private ThuNganViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bep);

        // Views (IDs must match activity_bep.xml)
        rvKitchen = findViewById(R.id.recyclerOrderBep);
        progressBar = findViewById(R.id.progress_bep);
        btnRefresh = findViewById(R.id.btn_bep_refresh);
        tvTitle = findViewById(R.id.tvTitleBep);

        tvTitle.setText("Danh sách bàn đang hoạt động");

        // RecyclerView setup with GridLayout (3 columns)
        rvKitchen.setLayoutManager(new GridLayoutManager(this, 3));
        
        // Create adapter with click listener
        BepTableAdapter.OnTableClickListener listener = table -> {
            // Open BepOrderActivity to show order items for this table
            Intent intent = new Intent(BepActivity.this, BepOrderActivity.class);
            intent.putExtra("tableNumber", table.getTableNumber());
            startActivity(intent);
        };
        
        adapter = new BepTableAdapter(new ArrayList<>(), listener);
        rvKitchen.setAdapter(adapter);

        // ViewModel - reuse ThuNganViewModel to get active tables
        viewModel = new ThuNganViewModel();

        // Refresh button -> load active tables
        btnRefresh.setOnClickListener(v -> {
            loadActiveTables();
        });

        // Initial data load
        loadActiveTables();
    }

    /**
     * Load active tables (tables with open orders)
     * Reuses the same logic as ThuNganActivity
     */
    private void loadActiveTables() {
        progressBar.setVisibility(View.VISIBLE);
        
        viewModel.loadActiveTables(new ThuNganViewModel.DataCallback() {
            @Override
            public void onTablesLoaded(List<TableItem> floor1Tables, List<TableItem> floor2Tables) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    
                    // Combine both floors
                    List<TableItem> allTables = new ArrayList<>();
                    allTables.addAll(floor1Tables);
                    allTables.addAll(floor2Tables);
                    
                    // Sort by table number
                    allTables.sort(Comparator.comparing(TableItem::getTableNumber));
                    
                    // Update adapter
                    adapter.updateList(allTables);
                    
                    if (allTables.isEmpty()) {
                        Toast.makeText(BepActivity.this, "Không có bàn nào đang hoạt động", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(BepActivity.this, "Lỗi tải danh sách bàn: " + message, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error loading tables: " + message);
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh when returning to this screen
        loadActiveTables();
    }
}