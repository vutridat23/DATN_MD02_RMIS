package com.ph48845.datn_qlnh_rmis.ui.bep;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;
import com.ph48845.datn_qlnh_rmis.ui.thungan.adapter.ThuNganAdapter;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * BepActivity (kitchen)
 *
 * - Derive active tables from orders (server source-of-truth for "active").
 * - Realtime: refreshActiveTables() when socket order events arrive.
 * - Fallback: small periodic polling while activity visible to catch deletes/updates
 *   when server doesn't emit a matching realtime event.
 */
public class BepActivity extends AppCompatActivity {

    private static final String TAG = "BepActivity";
    private static final String SOCKET_URL = "http://192.168.1.84:3000"; // cập nhật nếu cần

    private RecyclerView rvTables;
    private ProgressBar progressBar;
    private Button btnRefresh;
    private TextView tvTitle;

    private TableRepository tableRepository;
    private OrderRepository orderRepository;
    private ThuNganAdapter adapter;

    private final SocketManager socketManager = SocketManager.getInstance();

    // Polling handler: chạy fallback refresh mỗi POLL_INTERVAL_MS khi activity visible
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final long POLL_INTERVAL_MS = 5000L; // 5 giây (tùy chỉnh)
    private boolean pollingActive = false;

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                // gọi refreshActiveTables để đảm bảo danh sách cập nhật
                refreshActiveTables();
            } finally {
                // nếu còn active -> tiếp tục postDelayed
                if (pollingActive) {
                    pollHandler.postDelayed(this, POLL_INTERVAL_MS);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bep);

        rvTables = findViewById(R.id.recyclerOrderBep);
        progressBar = findViewById(R.id.progress_bep);
        btnRefresh = findViewById(R.id.btn_bep_refresh);
        tvTitle = findViewById(R.id.tvTitleBep);

        tvTitle.setText("Danh sách bàn (Bếp)");

        tableRepository = new TableRepository();
        orderRepository = new OrderRepository();

        rvTables.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new ThuNganAdapter(this, new ArrayList<>(), table -> {
            if (table == null) return;
            Intent it = new Intent(BepActivity.this, BepOrderActivity.class);
            it.putExtra("tableId", table.getId());
            it.putExtra("tableNumber", table.getTableNumber());
            startActivity(it);
        });
        rvTables.setAdapter(adapter);

        btnRefresh.setOnClickListener(v -> refreshActiveTables());

        // initial load
        refreshActiveTables();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // connect socket and start polling fallback
        startRealtime();
        startPolling();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // disconnect socket and stop polling when not visible
        stopRealtime();
        stopPolling();
    }

    /**
     * Refresh UI: derive active tables from orders on server and display those tables only.
     */
    private void refreshActiveTables() {
        progressBar.setVisibility(View.VISIBLE);

        // 1) Get all orders (server is source-of-truth for active tables)
        orderRepository.getOrdersByTableNumber(null, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> allOrders) {
                // Build set of table numbers that have orders
                final Set<Integer> activeTableNumbers = new HashSet<>();
                if (allOrders != null) {
                    for (Order o : allOrders) {
                        if (o == null) continue;
                        try {
                            int tn = o.getTableNumber();
                            if (tn > 0) activeTableNumbers.add(tn);
                        } catch (Exception ignored) {}
                    }
                }

                // If no active tables found, update UI and finish
                if (activeTableNumbers.isEmpty()) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        adapter.updateList(new ArrayList<>());
                    });
                    return;
                }

                // 2) Fetch all tables to get details and filter by activeTableNumbers
                tableRepository.getAllTables(new TableRepository.RepositoryCallback<List<TableItem>>() {
                    @Override
                    public void onSuccess(List<TableItem> allTables) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            List<TableItem> activeTables = new ArrayList<>();

                            // Map returned tables by tableNumber for quick lookup
                            if (allTables != null) {
                                for (TableItem t : allTables) {
                                    if (t == null) continue;
                                    if (activeTableNumbers.contains(t.getTableNumber())) {
                                        try { t.normalize(); } catch (Exception ignored) {}
                                        activeTables.add(t);
                                    }
                                }
                            }

                            // For any tableNumber present in orders but not returned in allTables,
                            // create a minimal placeholder TableItem so kitchen still sees the table.
                            for (Integer tn : activeTableNumbers) {
                                boolean found = false;
                                for (TableItem tt : activeTables) {
                                    if (tt != null && tt.getTableNumber() == tn) {
                                        found = true; break;
                                    }
                                }
                                if (!found) {
                                    TableItem placeholder = new TableItem();
                                    placeholder.setTableNumber(tn);
                                    placeholder.setStatusRaw("occupied");
                                    placeholder.normalize();
                                    activeTables.add(placeholder);
                                }
                            }

                            // Optional: sort by tableNumber for stable UI order
                            activeTables.sort((a, b) -> Integer.compare(a.getTableNumber(), b.getTableNumber()));

                            adapter.updateList(activeTables);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        // If fetching tables failed, fall back to showing minimal placeholders derived from orders
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            List<TableItem> placeholders = new ArrayList<>();
                            for (Integer tn : activeTableNumbers) {
                                TableItem p = new TableItem();
                                p.setTableNumber(tn);
                                p.setStatusRaw("occupied");
                                p.normalize();
                                placeholders.add(p);
                            }
                            placeholders.sort((a, b) -> Integer.compare(a.getTableNumber(), b.getTableNumber()));
                            adapter.updateList(placeholders);
                        });
                    }
                });
            }

            @Override
            public void onError(String message) {
                // If order fetch failed, show nothing (or optionally fall back to tableRepository)
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Log.w(TAG, "Cannot fetch orders to determine active tables: " + message);
                    adapter.updateList(new ArrayList<>());
                    Toast.makeText(BepActivity.this, "Không thể tải thông tin đơn hàng: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Start socket connection and set event listener to auto-refresh when orders or tables change.
     */
    private void startRealtime() {
        try {
            socketManager.init(SOCKET_URL);
            socketManager.setOnEventListener(new SocketManager.OnEventListener() {
                @Override
                public void onOrderCreated(JSONObject payload) {
                    Log.d(TAG, "Socket onOrderCreated: " + (payload != null ? payload.toString() : "null"));
                    runOnUiThread(() -> refreshActiveTables());
                }

                @Override
                public void onOrderUpdated(JSONObject payload) {
                    Log.d(TAG, "Socket onOrderUpdated: " + (payload != null ? payload.toString() : "null"));
                    runOnUiThread(() -> refreshActiveTables());
                }

                @Override
                public void onConnect() {
                    Log.d(TAG, "Socket connected (BepActivity).");
                    try {
                        socketManager.emitJoinRoom("bep");
                        socketManager.emitJoinRoom("phucvu");
                    } catch (Exception ignored) {}
                }

                @Override
                public void onDisconnect() {
                    Log.d(TAG, "Socket disconnected (BepActivity).");
                }

                @Override
                public void onError(Exception e) {
                    Log.w(TAG, "Socket error (BepActivity): " + (e != null ? e.getMessage() : ""));
                }
            });
            socketManager.connect();
        } catch (Exception e) {
            Log.w(TAG, "startRealtime failed: " + e.getMessage(), e);
        }
    }

    private void stopRealtime() {
        try {
            socketManager.disconnect();
        } catch (Exception ignored) {}
    }

    /**
     * Start/Stop simple polling fallback while activity visible.
     */
    private void startPolling() {
        if (!pollingActive) {
            pollingActive = true;
            pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        }
    }

    private void stopPolling() {
        pollingActive = false;
        pollHandler.removeCallbacksAndMessages(null);
    }
}