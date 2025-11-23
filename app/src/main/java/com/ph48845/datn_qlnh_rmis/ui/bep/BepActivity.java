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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;

import java.util.ArrayList;
import java.util.List;

/**
 * BepActivity - màn cho Bếp
 * 
 * Đã được cập nhật để hiển thị danh sách bàn đang hoạt động (có order đang mở).
 * Khi bấm vào một bàn, mở BepOrderActivity để xem danh sách món cần làm (read-only).
 */
public class BepActivity extends AppCompatActivity {

    private static final String TAG = "BepActivity";

    private RecyclerView rvBepTables;
    private ProgressBar progressBar;
    private Button btnRefresh;
    private TextView tvTitle;
    private TextView tvEmptyMessage;

    private BepViewModel viewModel;
    private BepTableAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bep);

        // Views
        rvBepTables = findViewById(R.id.recycler_bep_tables);
        progressBar = findViewById(R.id.progress_bep);
        btnRefresh = findViewById(R.id.btn_bep_refresh);
        tvTitle = findViewById(R.id.tvTitleBep);
        tvEmptyMessage = findViewById(R.id.tv_bep_empty_message);

        // RecyclerView setup - Grid với 3 cột như ThuNganActivity
        rvBepTables.setLayoutManager(new GridLayoutManager(this, 3));
        
        // Adapter với click listener
        BepTableAdapter.OnTableClickListener listener = table -> {
            // Chuyển sang màn hình xem order items của bàn
            Intent intent = new Intent(BepActivity.this, BepOrderActivity.class);
            intent.putExtra("tableNumber", table.getTableNumber());
            intent.putExtra("tableName", "Bàn " + table.getTableNumber());
            startActivity(intent);
        };
        
        adapter = new BepTableAdapter(this, new ArrayList<>(), listener);
        rvBepTables.setAdapter(adapter);

        // ViewModel
        viewModel = new ViewModelProvider(this).get(BepViewModel.class);

        // Refresh button -> load active tables
        btnRefresh.setOnClickListener(v -> {
            loadActiveTables();
        });

        // Initial data load
        loadActiveTables();
    }

    /**
     * Load danh sách bàn đang hoạt động (có order đang mở).
     */
    private void loadActiveTables() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmptyMessage.setVisibility(View.GONE);

        viewModel.loadActiveTables(new BepViewModel.ActiveTablesCallback() {
            @Override
            public void onTablesLoaded(List<TableItem> activeTables) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    
                    if (activeTables == null || activeTables.isEmpty()) {
                        tvEmptyMessage.setVisibility(View.VISIBLE);
                        tvEmptyMessage.setText("Không có bàn nào đang hoạt động");
                        adapter.updateList(new ArrayList<>());
                    } else {
                        tvEmptyMessage.setVisibility(View.GONE);
                        adapter.updateList(activeTables);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvEmptyMessage.setVisibility(View.VISIBLE);
                    tvEmptyMessage.setText("Lỗi tải danh sách bàn");
                    Toast.makeText(BepActivity.this, "Lỗi: " + message, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error loading tables: " + message);
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh when returning to this activity
        loadActiveTables();
    }
}