package com.ph48845.datn_qlnh_rmis.ui.bep;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.core.base.BaseMenuActivity;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.socket.SocketManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

/**
 * BepActivity: TabLayout + ViewPager2 hosting two fragments:
 * - INDEX_TABLES (BepTableFragment): list of active tables and per-table detail in the left fragment.
 * - INDEX_SUMMARY (BepSummaryFragment): aggregated totals for all menus that need to be cooked.
 *
 * Clicking a table opens the detail in the left fragment only (no automatic tab switch).
 */
public class BepActivity extends BaseMenuActivity implements BepTableFragment.OnTableSelectedListener {

    private static final String TAG = "BepActivity";
    private static final String SOCKET_URL = "http://192.168.25.2:3000";

    private ProgressBar progressBar;
    private ProgressBar progressOverlay;

    private TableRepository tableRepository;
    private OrderRepository orderRepository;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageView navIcon;
    private Toolbar toolbar;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private com.ph48845.datn_qlnh_rmis.ui.bep.BepPagerAdapter pagerAdapter;

    private final SocketManager socketManager = SocketManager.getInstance();

    // Polling fallback
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final long POLL_INTERVAL_MS = 10000L;
    private boolean pollingActive = false;

    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            try { refreshActiveTables(); } finally {
                if (pollingActive) pollHandler.postDelayed(this, POLL_INTERVAL_MS);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bep);

        progressBar = findViewById(R.id.progress_bep);
        progressOverlay = findViewById(R.id.progress_bar_loading);

        toolbar = findViewById(R.id.toolbar);
        drawerLayout = findViewById(R.id.drawerLayout_bep);
        navigationView = findViewById(R.id.navigationView_bep);
        navIcon = findViewById(R.id.nav_icon);
        tabLayout = findViewById(R.id.tab_layout_bep);
        viewPager = findViewById(R.id.viewpager_bep);

        applyNavigationViewInsets();

        if (navIcon != null && drawerLayout != null) navIcon.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        if (toolbar != null && drawerLayout != null) toolbar.setNavigationOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        tableRepository = new TableRepository();
        orderRepository = new OrderRepository();

        // ViewPager + adapter
        pagerAdapter = new com.ph48845.datn_qlnh_rmis.ui.bep.BepPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(2);

        // Tab labels
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(position == com.ph48845.datn_qlnh_rmis.ui.bep.BepPagerAdapter.INDEX_TABLES ? "BÀN" : "MÓN");
        }).attach();

        // wire left fragment selection callback
        Fragment fTables = pagerAdapter.getFragment(com.ph48845.datn_qlnh_rmis.ui.bep.BepPagerAdapter.INDEX_TABLES);
        if (fTables instanceof BepTableFragment) {
            ((BepTableFragment) fTables).setOnTableSelectedListener(this);
        }

        // navigation view minimal wiring
        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_mood) showMoodDialog();
                else if (id == R.id.nav_contact) showContactDialog();
                else if (id == R.id.nav_pantry) {
                    try { startActivity(new Intent(BepActivity.this, NguyenLieuActivity.class)); }
                    catch (Exception e) { Log.w(TAG, "Cannot open NguyenLieuActivity", e); Toast.makeText(BepActivity.this, "Không thể mở Nguyên liệu", Toast.LENGTH_SHORT).show(); }
                } else if (id == R.id.nav_logout) logout();
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            });
        }

        // initial load
        refreshActiveTables();
    }

    private void applyNavigationViewInsets() {
        if (navigationView == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (view, insets) -> {
            int statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            View header = navigationView.getHeaderView(0);
            if (header != null) header.setPadding(header.getPaddingLeft(), statusBar, header.getPaddingRight(), header.getPaddingBottom());
            return insets;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        startRealtime();
        startPolling();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopRealtime();
        stopPolling();
    }

    /**
     * Build active table list and per-table items + global summary, then update fragments.
     * Only tables with at least one active item (pending/preparing/processing) are considered active.
     *
     * NOTE: Made public to allow BepSummaryFragment to request a refresh after batch updates.
     */
    public void refreshActiveTables() {
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        orderRepository.getOrdersByTableNumber(null, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> allOrders) {
                final Map<Integer, Integer> remainingCounts = new HashMap<>();
                final Map<Integer, Long> earliestTs = new HashMap<>();
                final Map<Integer, List<ItemWithOrder>> perTableItems = new HashMap<>();
                final Set<Integer> activeTableNumbers = new HashSet<>();
                final long now = System.currentTimeMillis();

                // local accumulator class must be declared BEFORE we use it
                class SummaryAccumulator {
                    int qty = 0;
                    String image = "";
                    void add(int q, String img) {
                        qty += q;
                        if ((image == null || image.isEmpty()) && img != null && !img.isEmpty()) image = img;
                    }
                }

                // accumulator map: name -> (qty + sample image)
                final Map<String, SummaryAccumulator> acc = new HashMap<>();

                if (allOrders != null) {
                    for (Order o : allOrders) {
                        if (o == null) continue;
                        try { o.normalizeItems(); } catch (Exception ignored) {}
                        int tn = o.getTableNumber();
                        if (tn <= 0) continue;

                        long created = now;
                        try {
                            String c = o.getCreatedAt();
                            if (c != null && !c.isEmpty()) created = Long.parseLong(c);
                        } catch (Exception ignored) {}

                        if (o.getItems() == null) continue;
                        int activeItemsThisOrder = 0;
                        for (Order.OrderItem it : o.getItems()) {
                            if (it == null) continue;
                            String st = it.getStatus() == null ? "" : it.getStatus().trim().toLowerCase();
                            if ("soldout".equals(st) || "out_of_stock".equals(st)) continue;
                            if ("pending".equals(st) || "preparing".equals(st) || "processing".equals(st)) {
                                int qty = it.getQuantity() <= 0 ? 1 : it.getQuantity();
                                activeItemsThisOrder += qty;
                                activeTableNumbers.add(tn);

                                List<ItemWithOrder> list = perTableItems.computeIfAbsent(tn, k -> new ArrayList<>());
                                list.add(new ItemWithOrder(o, it));

                                String name = it.getMenuItemName() != null && !it.getMenuItemName().isEmpty() ? it.getMenuItemName() : it.getName();
                                if (name == null) name = "(Không tên)";
                                SummaryAccumulator s = acc.get(name);
                                if (s == null) { s = new SummaryAccumulator(); acc.put(name, s); }
                                String img = "";
                                try { img = it.getImageUrl(); } catch (Exception ignored) {}
                                s.add(qty, img);
                            }
                        }
                        if (activeItemsThisOrder > 0) {
                            int prev = remainingCounts.getOrDefault(tn, 0);
                            remainingCounts.put(tn, prev + activeItemsThisOrder);
                            Long prevTs = earliestTs.get(tn);
                            if (prevTs == null || created < prevTs) earliestTs.put(tn, created);
                        }
                    }
                }

                // build list of SummaryEntry from accumulator
                final List<SummaryEntry> globalSummaryList = new ArrayList<>();
                for (Map.Entry<String, SummaryAccumulator> e : acc.entrySet()) {
                    globalSummaryList.add(new SummaryEntry(e.getKey(), e.getValue().qty, e.getValue().image));
                }

                // Sort descending by qty (món cần làm nhiều lên đầu)
                Collections.sort(globalSummaryList, (a, b) -> Integer.compare(b.getQty(), a.getQty()));

                if (activeTableNumbers.isEmpty()) {
                    runOnUiThread(() -> {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        Fragment fTables = pagerAdapter.getFragment(com.ph48845.datn_qlnh_rmis.ui.bep.BepPagerAdapter.INDEX_TABLES);
                        Fragment fSummary = pagerAdapter.getFragment(com.ph48845.datn_qlnh_rmis.ui.bep.BepPagerAdapter.INDEX_SUMMARY);
                        if (fTables instanceof BepTableFragment) ((BepTableFragment) fTables).updateTables(new ArrayList<>(), remainingCounts, earliestTs, perTableItems);
                        if (fSummary instanceof BepSummaryFragment) ((BepSummaryFragment) fSummary).updateSummary(globalSummaryList);
                    });
                    return;
                }

                tableRepository.getAllTables(new TableRepository.RepositoryCallback<List<TableItem>>() {
                    @Override
                    public void onSuccess(List<TableItem> allTables) {
                        runOnUiThread(() -> {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            List<TableItem> activeTables = new ArrayList<>();
                            if (allTables != null) {
                                for (TableItem t : allTables) {
                                    if (t == null) continue;
                                    if (activeTableNumbers.contains(t.getTableNumber())) {
                                        try { t.normalize(); } catch (Exception ignored) {}
                                        activeTables.add(t);
                                    }
                                }
                            }
                            for (Integer tn : activeTableNumbers) {
                                boolean found = false;
                                for (TableItem tt : activeTables) {
                                    if (tt != null && tt.getTableNumber() == tn) { found = true; break; }
                                }
                                if (!found) {
                                    TableItem p = new TableItem();
                                    p.setTableNumber(tn);
                                    p.setStatusRaw("occupied");
                                    p.normalize();
                                    activeTables.add(p);
                                }
                            }

                            // sort by earliestTs ascending (older orders first)
                            activeTables.sort((a,b) -> {
                                long ta = earliestTs.getOrDefault(a.getTableNumber(), Long.MAX_VALUE);
                                long tb = earliestTs.getOrDefault(b.getTableNumber(), Long.MAX_VALUE);
                                return Long.compare(ta, tb);
                            });

                            Fragment fTables = pagerAdapter.getFragment(com.ph48845.datn_qlnh_rmis.ui.bep.BepPagerAdapter.INDEX_TABLES);
                            Fragment fSummary = pagerAdapter.getFragment(com.ph48845.datn_qlnh_rmis.ui.bep.BepPagerAdapter.INDEX_SUMMARY);

                            if (fTables instanceof BepTableFragment) ((BepTableFragment) fTables).updateTables(activeTables, remainingCounts, earliestTs, perTableItems);
                            if (fSummary instanceof BepSummaryFragment) ((BepSummaryFragment) fSummary).updateSummary(globalSummaryList);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            List<TableItem> placeholders = new ArrayList<>();
                            for (Integer tn : activeTableNumbers) {
                                TableItem p = new TableItem();
                                p.setTableNumber(tn);
                                p.setStatusRaw("occupied");
                                p.normalize();
                                placeholders.add(p);
                            }
                            placeholders.sort((a,b) -> Integer.compare(a.getTableNumber(), b.getTableNumber()));

                            Fragment fTables = pagerAdapter.getFragment(com.ph48845.datn_qlnh_rmis.ui.bep.BepPagerAdapter.INDEX_TABLES);
                            Fragment fSummary = pagerAdapter.getFragment(com.ph48845.datn_qlnh_rmis.ui.bep.BepPagerAdapter.INDEX_SUMMARY);

                            if (fTables instanceof BepTableFragment) ((BepTableFragment) fTables).updateTables(placeholders, remainingCounts, earliestTs, perTableItems);
                            if (fSummary instanceof BepSummaryFragment) ((BepSummaryFragment) fSummary).updateSummary(globalSummaryList);
                        });
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    Log.w(TAG, "Cannot fetch orders: " + message);
                    Toast.makeText(BepActivity.this, "Không thể tải thông tin đơn hàng: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void startRealtime() {
        try {
            socketManager.init(SOCKET_URL);
            socketManager.setOnEventListener(new SocketManager.OnEventListener() {
                @Override public void onOrderCreated(JSONObject payload) { runOnUiThread(() -> refreshActiveTables()); }
                @Override public void onOrderUpdated(JSONObject payload) { runOnUiThread(() -> refreshActiveTables()); }
                @Override public void onConnect() { try { socketManager.emitEvent("join_room", "bep"); } catch (Exception ignored) {} }
                @Override public void onDisconnect() {}
                @Override public void onError(Exception e) { Log.w(TAG, "Socket error: " + (e != null ? e.getMessage() : "")); }
            });
            socketManager.connect();
        } catch (Exception e) {
            Log.w(TAG, "startRealtime failed: " + e.getMessage(), e);
        }
    }

    private void stopRealtime() { try { socketManager.disconnect(); } catch (Exception ignored) {} }

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

    // Called when a table in the left fragment is selected.
    // We do NOT switch to the "MÓN" tab here — detail is shown inside BepTableFragment itself.
    @Override
    public void onTableSelected(TableItem table) {
        Log.d(TAG, "Table selected (Activity no-op): " + (table != null ? table.getTableNumber() : "null"));
    }
}