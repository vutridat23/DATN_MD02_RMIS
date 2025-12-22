package com.ph48845.datn_qlnh_rmis.ui.bep;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    // track previous "active item count" per table to detect new dishes
    private final Map<Integer, Integer> prevActiveCountByTable = new HashMap<>();

    // tables that have new dishes -> yellow + blink
    private final Set<Integer> attentionTables = new HashSet<>();

    // muted attention set: tables that were viewed => no blink but keep ordering
    private final Set<Integer> mutedAttention = new HashSet<>();

    // attention sequence to order attention tables (higher = more recent)
    private final Map<Integer, Long> attentionSeq = new HashMap<>();
    private long attentionSeqCounter = 0L;

    // last displayed order of table numbers (used to preserve relative order for non-attention tables)
    private final List<Integer> lastDisplayedOrder = new ArrayList<>();

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

        pagerAdapter = new com.ph48845.datn_qlnh_rmis.ui.bep.BepPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOffscreenPageLimit(2);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) ->
                tab.setText(position == com.ph48845.datn_qlnh_rmis.ui.bep.BepPagerAdapter.INDEX_TABLES ? "BÀN" : "MÓN")
        ).attach();

        Fragment fTables = pagerAdapter.getFragment(com.ph48845.datn_qlnh_rmis.ui.bep.BepPagerAdapter.INDEX_TABLES);
        if (fTables instanceof BepTableFragment) {
            ((BepTableFragment) fTables).setOnTableSelectedListener(this);
        }

        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_mood) showMoodDialog();
                else if (id == R.id.nav_contact) showContactDialog();
                else if (id == R.id.nav_pantry) {
                    try { startActivity(new Intent(BepActivity.this, NguyenLieuActivity.class)); }
                    catch (Exception e) {
                        Log.w(TAG, "Cannot open NguyenLieuActivity", e);
                        Toast.makeText(BepActivity.this, "Không thể mở Nguyên liệu", Toast.LENGTH_SHORT).show();
                    }
                } else if (id == R.id.nav_logout) logout();
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            });
        }

        refreshActiveTables();
    }

    private void applyNavigationViewInsets() {
        if (navigationView == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (view, insets) -> {
            int statusBar = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top;
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

                class SummaryAccumulator {
                    int qty = 0;
                    String image = "";
                    void add(int q, String img) {
                        qty += q;
                        if ((image == null || image.isEmpty()) && img != null && !img.isEmpty()) image = img;
                    }
                }

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

                            if ("pending".equals(st) || "preparing".equals(st) || "processing".equals(st) || "cancel_requested".equals(st)) {
                                int qty = it.getQuantity() <= 0 ? 1 : it.getQuantity();
                                activeItemsThisOrder += qty;
                                activeTableNumbers.add(tn);

                                List<ItemWithOrder> list = perTableItems.computeIfAbsent(tn, k -> new ArrayList<>());
                                list.add(new ItemWithOrder(o, it));

                                if (!"cancel_requested".equals(st)) {
                                    String name = (it.getMenuItemName() != null && !it.getMenuItemName().isEmpty())
                                            ? it.getMenuItemName() : it.getName();
                                    if (name == null) name = "(Không tên)";
                                    SummaryAccumulator s = acc.get(name);
                                    if (s == null) { s = new SummaryAccumulator(); acc.put(name, s); }
                                    String img = "";
                                    try { img = it.getImageUrl(); } catch (Exception ignored) {}
                                    s.add(qty, img);
                                }
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

                // detect new dishes per table (remainingCounts increased)
                final List<Integer> newlyAddedTables = new ArrayList<>();
                for (Map.Entry<Integer, Integer> e : remainingCounts.entrySet()) {
                    int tn = e.getKey();
                    int newCount = e.getValue() != null ? e.getValue() : 0;
                    int oldCount = prevActiveCountByTable.getOrDefault(tn, 0);

                    if (newCount > oldCount) {
                        // mark attention and set sequence so it becomes top-most (most recent first)
                        if (!attentionTables.contains(tn)) {
                            attentionTables.add(tn);
                        }
                        attentionSeqCounter++;
                        attentionSeq.put(tn, attentionSeqCounter);

                        // IMPORTANT: if this table was muted (user has viewed before), unmute it so it will blink again
                        mutedAttention.remove(tn);

                        if (!newlyAddedTables.contains(tn)) newlyAddedTables.add(tn);
                    }
                    prevActiveCountByTable.put(tn, newCount);
                }

                // cleanup tables that are no longer active
                List<Integer> removed = new ArrayList<>();
                for (Integer tn : new ArrayList<>(prevActiveCountByTable.keySet())) {
                    if (!remainingCounts.containsKey(tn)) removed.add(tn);
                }
                for (Integer tn : removed) {
                    prevActiveCountByTable.remove(tn);
                    attentionTables.remove(tn);
                    attentionSeq.remove(tn);
                    // also remove from muted set if table disappears
                    mutedAttention.remove(tn);
                }

                final List<SummaryEntry> globalSummaryList = new ArrayList<>();
                for (Map.Entry<String, SummaryAccumulator> e : acc.entrySet()) {
                    globalSummaryList.add(new SummaryEntry(e.getKey(), e.getValue().qty, e.getValue().image));
                }
                Collections.sort(globalSummaryList, (a, b) -> Integer.compare(b.getQty(), a.getQty()));

                // If new tables found, show popup (once per detection)
                if (!newlyAddedTables.isEmpty()) {
                    showNewOrderPopup(newlyAddedTables, perTableItems, remainingCounts);
                }

                if (activeTableNumbers.isEmpty()) {
                    runOnUiThread(() -> {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        Fragment fTables = pagerAdapter.getFragment(com.ph48845.datn_qlnh_rmis.ui.bep.BepPagerAdapter.INDEX_TABLES);
                        Fragment fSummary = pagerAdapter.getFragment(com.ph48845.datn_qlnh_rmis.ui.bep.BepPagerAdapter.INDEX_SUMMARY);

                        if (fTables instanceof BepTableFragment) {
                            ((BepTableFragment) fTables).updateTables(
                                    new ArrayList<>(), remainingCounts, earliestTs, perTableItems,
                                    new HashSet<>(attentionTables)
                            );
                            // ensure muted state sent too
                            ((BepTableFragment) fTables).setMutedAttention(new HashSet<>(mutedAttention));
                            // update lastDisplayedOrder to empty
                            lastDisplayedOrder.clear();
                        }
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

                            // SORT:
                            // - attention tables first (by attention sequence desc => most recently marked first)
                            // - non-attention tables: preserve lastDisplayedOrder if possible; otherwise by earliestTs then tableNumber
                            activeTables.sort((a, b) -> {
                                int an = a.getTableNumber();
                                int bn = b.getTableNumber();
                                boolean aAttention = attentionTables.contains(an);
                                boolean bAttention = attentionTables.contains(bn);
                                if (aAttention && bAttention) {
                                    long sa = attentionSeq.getOrDefault(an, 0L);
                                    long sb = attentionSeq.getOrDefault(bn, 0L);
                                    // larger seq = more recent => first
                                    return Long.compare(sb, sa);
                                }
                                if (aAttention && !bAttention) return -1;
                                if (!aAttention && bAttention) return 1;
                                // both not attention -> preserve previous displayed order if available
                                int ia = lastDisplayedOrder.indexOf(an);
                                int ib = lastDisplayedOrder.indexOf(bn);
                                if (ia != -1 && ib != -1) return Integer.compare(ia, ib);
                                if (ia != -1) return -1;
                                if (ib != -1) return 1;
                                long ta = earliestTs.getOrDefault(an, Long.MAX_VALUE);
                                long tb = earliestTs.getOrDefault(bn, Long.MAX_VALUE);
                                if (ta != tb) return Long.compare(ta, tb);
                                return Integer.compare(an, bn);
                            });

                            // Update fragment and remember lastDisplayedOrder (table numbers in displayed order)
                            Fragment fTables = pagerAdapter.getFragment(com.ph48845.datn_qlnh_rmis.ui.bep.BepPagerAdapter.INDEX_TABLES);
                            Fragment fSummary = pagerAdapter.getFragment(com.ph48845.datn_qlnh_rmis.ui.bep.BepPagerAdapter.INDEX_SUMMARY);

                            if (fTables instanceof BepTableFragment) {
                                ((BepTableFragment) fTables).updateTables(
                                        activeTables, remainingCounts, earliestTs, perTableItems,
                                        new HashSet<>(attentionTables)
                                );
                                // also send muted set so adapter won't blink those tables
                                ((BepTableFragment) fTables).setMutedAttention(new HashSet<>(mutedAttention));

                                // remember displayed order (for next refresh)
                                lastDisplayedOrder.clear();
                                for (TableItem t : activeTables) {
                                    if (t != null) lastDisplayedOrder.add(t.getTableNumber());
                                }
                            }
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
                            // same sorting: attention first (by seq desc), then preserve last order or tableNumber
                            placeholders.sort((a, b) -> {
                                int an = a.getTableNumber();
                                int bn = b.getTableNumber();
                                boolean aAttention = attentionTables.contains(an);
                                boolean bAttention = attentionTables.contains(bn);
                                if (aAttention && bAttention) {
                                    long sa = attentionSeq.getOrDefault(an, 0L);
                                    long sb = attentionSeq.getOrDefault(bn, 0L);
                                    return Long.compare(sb, sa);
                                }
                                if (aAttention && !bAttention) return -1;
                                if (!aAttention && bAttention) return 1;
                                int ia = lastDisplayedOrder.indexOf(an);
                                int ib = lastDisplayedOrder.indexOf(bn);
                                if (ia != -1 && ib != -1) return Integer.compare(ia, ib);
                                if (ia != -1) return -1;
                                if (ib != -1) return 1;
                                return Integer.compare(an, bn);
                            });

                            Fragment fTables = pagerAdapter.getFragment(com.ph48845.datn_qlnh_rmis.ui.bep.BepPagerAdapter.INDEX_TABLES);
                            Fragment fSummary = pagerAdapter.getFragment(com.ph48845.datn_qlnh_rmis.ui.bep.BepPagerAdapter.INDEX_SUMMARY);

                            if (fTables instanceof BepTableFragment) {
                                ((BepTableFragment) fTables).updateTables(
                                        placeholders, remainingCounts, earliestTs, perTableItems,
                                        new HashSet<>(attentionTables)
                                );
                                ((BepTableFragment) fTables).setMutedAttention(new HashSet<>(mutedAttention));

                                lastDisplayedOrder.clear();
                                for (TableItem t : placeholders) if (t != null) lastDisplayedOrder.add(t.getTableNumber());
                            }
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

    private void showNewOrderPopup(List<Integer> newTables, Map<Integer, List<ItemWithOrder>> perTableItems, Map<Integer, Integer> remainingCounts) {
        if (newTables == null || newTables.isEmpty()) return;

        runOnUiThread(() -> {
            StringBuilder sb = new StringBuilder();
            for (Integer tn : newTables) {
                int cnt = remainingCounts != null ? remainingCounts.getOrDefault(tn, 0) : 0;
                sb.append("Bàn ").append(tn).append(": ").append(cnt).append(" món mới\n");

                List<ItemWithOrder> items = perTableItems != null ? perTableItems.get(tn) : null;
                if (items != null && !items.isEmpty()) {
                    Map<String, Integer> byName = new HashMap<>();
                    for (ItemWithOrder w : items) {
                        if (w == null || w.getItem() == null) continue;
                        String name = w.getItem().getMenuItemName();
                        if (name == null || name.isEmpty()) name = w.getItem().getName();
                        int q = w.getItem().getQuantity() <= 0 ? 1 : w.getItem().getQuantity();
                        byName.put(name, byName.getOrDefault(name, 0) + q);
                    }
                    for (Map.Entry<String, Integer> e : byName.entrySet()) {
                        sb.append("  - ").append(e.getKey()).append(" x").append(e.getValue()).append("\n");
                    }
                }
                sb.append("\n");
            }

            AlertDialog.Builder b = new AlertDialog.Builder(BepActivity.this);
            b.setTitle("Đơn hàng mới xuống bếp")
                    .setMessage(sb.toString().trim())
                    .setPositiveButton("Xem", (d, w) -> {
                        try { viewPager.setCurrentItem(com.ph48845.datn_qlnh_rmis.ui.bep.BepPagerAdapter.INDEX_TABLES, true); } catch (Exception ignored) {}
                    })
                    .setNegativeButton("Đóng", (d, w) -> {})
                    .setCancelable(true)
                    .show();

            // play notification sound
            try {
                Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                if (r != null) r.play();
            } catch (Exception ignored) {}

            // vibrate briefly (if available)
            try {
                Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(180, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        //noinspection deprecation
                        vibrator.vibrate(180);
                    }
                }
            } catch (Exception ignored) {}
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

    // When user clicks a table => DO NOT change attention/order so it stays in place
    // Instead, mute blink for that table (stop visual blinking) but keep it in attentionTables
    @Override
    public void onTableSelected(TableItem table) {
        int tn = table != null ? table.getTableNumber() : -1;
        if (tn <= 0) return;

        // Add to muted set so blinking stops but ordering (attention) remains
        mutedAttention.add(tn);

        // Update fragment to reflect muted set (adapter will stop blinking that table)
        Fragment fTables = pagerAdapter.getFragment(com.ph48845.datn_qlnh_rmis.ui.bep.BepPagerAdapter.INDEX_TABLES);
        if (fTables instanceof BepTableFragment) {
            ((BepTableFragment) fTables).setMutedAttention(new HashSet<>(mutedAttention));
        }

        // Note: do NOT remove from attentionTables or attentionSeq, so ordering stays the same.
    }
}