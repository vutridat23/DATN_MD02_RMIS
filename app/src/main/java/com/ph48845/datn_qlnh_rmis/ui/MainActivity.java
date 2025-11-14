package com.ph48845.datn_qlnh_rmis.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ph48845.datn_qlnh_rmis.R;
import com.ph48845.datn_qlnh_rmis.adapter.TableAdapter;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.OrderActivity;
import com.ph48845.datn_qlnh_rmis.ui.thungan.ThuNganActivity;
import com.ph48845.datn_qlnh_rmis.ui.revenue.RevenueActivity;
import android.widget.ImageView;
import android.widget.PopupMenu;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MainActivity: hiển thị danh sách bàn và đồng bộ trạng thái bàn với orders trên API.
 *
 * Sửa lại:
 *  - Sắp xếp bảng hiển thị (floor + số bàn) ổn định.
 *  - Fix danh sách ứng viên chuyển bàn (showTransferDialog) -- sắp xếp hợp lý theo tầng -> số bàn,
 *    hiển thị trạng thái đúng (không để "Bàn 10 - " trống).
 *
 * Ghi chú: TableAdapter phải có updateList(List<TableItem>) như trước.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivityHome";
    private ProgressBar progressBar;

    private RecyclerView rvFloor1;
    private RecyclerView rvFloor2;
    private TableAdapter adapterFloor1;
    private TableAdapter adapterFloor2;
    private TableRepository tableRepository;
    private OrderRepository orderRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // views
        progressBar = findViewById(R.id.progress_bar_loading);
        rvFloor1 = findViewById(R.id.recycler_floor1);
        rvFloor2 = findViewById(R.id.recycler_floor2);

        // Grid with 3 columns
        rvFloor1.setLayoutManager(new GridLayoutManager(this, 3));
        rvFloor2.setLayoutManager(new GridLayoutManager(this, 3));

        rvFloor1.setNestedScrollingEnabled(false);
        rvFloor2.setNestedScrollingEnabled(false);

        tableRepository = new TableRepository();
        orderRepository = new OrderRepository();

        // create adapter with listener
        TableAdapter.OnTableClickListener listener = new TableAdapter.OnTableClickListener() {
            @Override
            public void onTableClick(View v, TableItem table) {
                if (table == null) return;
                Intent intent = new Intent(MainActivity.this, OrderActivity.class);
                intent.putExtra("tableId", table.getId());
                intent.putExtra("tableNumber", table.getTableNumber());
                startActivity(intent);
            }

            @Override
            public void onTableLongClick(View v, TableItem table) {
                if (table == null) return;
                showTableActionsMenu(v, table);
            }
        };

        adapterFloor1 = new TableAdapter(this, new ArrayList<>(), listener);
        adapterFloor2 = new TableAdapter(this, new ArrayList<>(), listener);
        rvFloor1.setAdapter(adapterFloor1);
        rvFloor2.setAdapter(adapterFloor2);

        // Setup menu/avatar click to open ThuNganActivity
        ImageView avatar = findViewById(R.id.avatar);
        if (avatar != null) {
            avatar.setOnClickListener(v -> showMenu(v));
        }

        // initial load
        fetchTablesFromServer();
    }

    /**
     * Hiển thị menu khi click vào avatar
     */
    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "Thu Ngân");
        popup.getMenu().add(0, 2, 1, "Thống kê doanh thu");
        
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                // Mở màn hình Thu Ngân
                Intent intent = new Intent(MainActivity.this, ThuNganActivity.class);
                startActivity(intent);
                return true;
            } else if (id == 2) {
                // Mở màn hình Thống kê doanh thu
                Intent intent = new Intent(MainActivity.this, RevenueActivity.class);
                startActivity(intent);
                return true;
            }
            return false;
        });
        
        popup.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // refresh on resume to reflect any changes
        fetchTablesFromServer();
    }

    /**
     * Fetch tables from server and update adapters, then sync status with orders.
     *
     * Improvements:
     * - Determine floor by parsing first number in location string.
     * - Sort tables within each floor by tableNumber (numeric) to keep stable order.
     */
    public void fetchTablesFromServer() {
        progressBar.setVisibility(View.VISIBLE);

        tableRepository.getAllTables(new TableRepository.RepositoryCallback<List<TableItem>>() {
            @Override
            public void onSuccess(List<TableItem> result) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (result == null || result.isEmpty()) {
                        Toast.makeText(MainActivity.this, "Không có bàn", Toast.LENGTH_SHORT).show();
                        adapterFloor1.updateList(new ArrayList<>());
                        adapterFloor2.updateList(new ArrayList<>());
                        return;
                    }

                    // defensive: ensure location non-null
                    for (TableItem t : result) {
                        if (t == null) continue;
                        if (t.getLocation() == null) t.setLocation("");
                    }

                    // phân chia theo floor dựa trên số tầng (first captured number in location)
                    List<TableItem> floor1 = new ArrayList<>();
                    List<TableItem> floor2 = new ArrayList<>();
                    for (TableItem t : result) {
                        int floor = parseFloorFromLocation(t.getLocation());
                        if (floor == 2) {
                            floor2.add(t);
                        } else {
                            // default to floor 1 for any non-2 floors (you can expand to handle other floors)
                            floor1.add(t);
                        }
                    }

                    // sắp xếp từng floor theo tableNumber (numeric) để thứ tự ổn định
                    Comparator<TableItem> byNumber = (a, b) -> {
                        if (a == null && b == null) return 0;
                        if (a == null) return 1;
                        if (b == null) return -1;
                        try {
                            return Integer.compare(a.getTableNumber(), b.getTableNumber());
                        } catch (Exception e) {
                            // fallback string
                            return String.valueOf(a.getTableNumber()).compareTo(String.valueOf(b.getTableNumber()));
                        }
                    };
                    Collections.sort(floor1, byNumber);
                    Collections.sort(floor2, byNumber);

                    adapterFloor1.updateList(floor1);
                    adapterFloor2.updateList(floor2);

                    Log.d(TAG, "Loaded tables from server: total=" + result.size() + " floor1=" + floor1.size() + " floor2=" + floor2.size());

                    // Sau khi cập nhật adapter, đồng bộ trạng thái từng bàn với orders trên API
                    List<TableItem> all = new ArrayList<>();
                    all.addAll(floor1);
                    all.addAll(floor2);
                    syncTableStatusesWithOrders(all);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Lỗi tải danh sách bàn: " + message, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Error fetching tables: " + message);
                });
            }
        });
    }

    /**
     * Parse floor number from location string.
     * - Looks for first integer in the string. If found returns it.
     * - If none found returns 1 (default).
     */
    private int parseFloorFromLocation(String location) {
        if (location == null) return 1;
        try {
            String lower = location.toLowerCase(Locale.getDefault());
            Pattern p = Pattern.compile("(\\d+)");
            Matcher m = p.matcher(lower);
            if (m.find()) {
                String num = m.group(1);
                return Integer.parseInt(num);
            }
        } catch (Exception ignored) {}
        return 1;
    }

    /**
     * Đồng bộ trạng thái toàn bộ bàn theo orders.
     */
    private void syncTableStatusesWithOrders(List<TableItem> tables) {
        if (tables == null || tables.isEmpty()) return;

        // Lấy tất cả orders (không truyền tableNumber) 1 lần
        orderRepository.getOrdersByTableNumber(null, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orders) {
                // Build set of tableNumbers that have orders
                final Set<Integer> occupiedTableNumbers = new HashSet<>();
                if (orders != null) {
                    for (Order o : orders) {
                        if (o == null) continue;
                        try {
                            occupiedTableNumbers.add(o.getTableNumber());
                        } catch (Exception ignored) {}
                    }
                }

                // Determine which tables need update
                List<TableItem> toUpdate = new ArrayList<>();
                final List<String> desiredStatuses = new ArrayList<>(); // parallel list same size as toUpdate

                for (TableItem t : tables) {
                    if (t == null) continue;
                    String currentStatusLower = "";
                    try {
                        if (t.getStatus() != null) currentStatusLower = t.getStatus().name().toLowerCase();
                    } catch (Exception ex) {
                        try { currentStatusLower = String.valueOf(t.getStatus()).toLowerCase(); } catch (Exception ignored) {}
                    }

                    // If table is RESERVED, keep reserved (don't overwrite)
                    boolean isReserved = false;
                    try {
                        isReserved = t.getStatus() == TableItem.Status.RESERVED;
                    } catch (Exception ignored) {}

                    String desired;
                    if (isReserved) {
                        // skip updating reserved tables
                        continue;
                    } else {
                        if (occupiedTableNumbers.contains(t.getTableNumber())) {
                            desired = "occupied";
                        } else {
                            desired = "available"; // empty
                        }
                    }

                    if (!currentStatusLower.equals(desired)) {
                        toUpdate.add(t);
                        desiredStatuses.add(desired);
                    }
                }

                if (toUpdate.isEmpty()) {
                    Log.d(TAG, "No table status updates needed after sync with orders.");
                    return;
                }

                // perform updates and call fetchTablesFromServer once when all done
                final int total = toUpdate.size();
                final int[] finished = {0};

                for (int i = 0; i < toUpdate.size(); i++) {
                    TableItem ti = toUpdate.get(i);
                    String desiredStatus = desiredStatuses.get(i);
                    tableRepository.updateTableStatus(ti.getId(), desiredStatus, new TableRepository.RepositoryCallback<TableItem>() {
                        @Override
                        public void onSuccess(TableItem updated) {
                            Log.d(TAG, "Updated table " + ti.getTableNumber() + " -> " + desiredStatus);
                            finished[0]++;
                            if (finished[0] >= total) {
                                // refresh once after all updates done
                                runOnUiThread(() -> fetchTablesFromServer());
                            }
                        }

                        @Override
                        public void onError(String message) {
                            Log.w(TAG, "Failed to update table " + ti.getTableNumber() + " -> " + desiredStatus + " : " + message);
                            finished[0]++;
                            if (finished[0] >= total) {
                                runOnUiThread(() -> fetchTablesFromServer());
                            }
                        }
                    });
                }
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Failed to fetch orders for sync: " + message);
                // nếu không lấy được orders, không thay đổi trạng thái; giữ nguyên
            }
        });
    }

    /**
     * Hiện PopupMenu anchored tại view bàn với các hành động: Chuyển, Gộp, Hủy đặt trước (nếu reserved),
     * và Đặt trước (nếu bàn trống hoặc available).
     */
    public void showTableActionsMenu(View anchor, TableItem table) {
        Log.d(TAG, "showTableActionsMenu: tableId=" + table.getId() + " tableNumber=" + table.getTableNumber() + " status=" + table.getStatus());

        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "Chuyển bàn");
        popup.getMenu().add(0, 2, 1, "Gộp bàn");

        try {
            if (table.getStatus() == TableItem.Status.RESERVED) {
                popup.getMenu().add(0, 3, 2, "Hủy đặt trước");
            }
        } catch (Exception ignored) {}

        try {
            if (table.getStatus() == TableItem.Status.EMPTY || table.getStatus() == TableItem.Status.AVAILABLE) {
                popup.getMenu().add(0, 4, 3, "Đặt trước");
            }
        } catch (Exception ignored) {}

        popup.setOnMenuItemClickListener((MenuItem item) -> {
            int id = item.getItemId();
            switch (id) {
                case 1:
                    showTransferDialog(table);
                    return true;
                case 2:
                    showMergeDialog(table);
                    return true;
                case 3:
                    cancelReservation(table);
                    return true;
                case 4:
                    showReservationDialogWithPickers(table);
                    return true;
                default:
                    return false;
            }
        });

        popup.show();
    }

    /**
     * SHOW TRANSFER DIALOG
     *
     * Sửa:
     *  - Lọc ứng viên theo rule (giữ nguyên logic của bạn)
     *  - Sắp xếp ứng viên theo tầng -> số bàn (numeric)
     *  - Build label với trạng thái hiển thị an toàn (không để trống)
     */
    private void showTransferDialog(TableItem fromTable) {
        tableRepository.getAllTables(new TableRepository.RepositoryCallback<List<TableItem>>() {
            @Override
            public void onSuccess(List<TableItem> result) {
                runOnUiThread(() -> {
                    List<TableItem> candidates = new ArrayList<>();
                    // Determine allowed target statuses depending on fromTable status
                    TableItem.Status fromStatus = TableItem.Status.EMPTY;
                    try { fromStatus = fromTable.getStatus(); } catch (Exception ignored) {}

                    for (TableItem t : result) {
                        if (t == null) continue;
                        if (fromTable.getId() != null && fromTable.getId().equals(t.getId())) continue;

                        // Filter logic:
                        TableItem.Status targetStatus = t.getStatus();
                        boolean allowed = true;
                        if (fromStatus == TableItem.Status.RESERVED) {
                            if (!(targetStatus == TableItem.Status.AVAILABLE || targetStatus == TableItem.Status.EMPTY)) {
                                allowed = false;
                            }
                        } else if (fromStatus == TableItem.Status.OCCUPIED) {
                            if (targetStatus == TableItem.Status.RESERVED) {
                                allowed = false;
                            }
                        }

                        if (!allowed) continue;

                        candidates.add(t);
                    }

                    if (candidates.isEmpty()) {
                        if (fromTable.getStatus() == TableItem.Status.RESERVED) {
                            Toast.makeText(MainActivity.this, "Không có bàn trống để chuyển đặt trước.", Toast.LENGTH_SHORT).show();
                        } else if (fromTable.getStatus() == TableItem.Status.OCCUPIED) {
                            Toast.makeText(MainActivity.this, "Không có bàn phù hợp để chuyển (không thể chuyển vào bàn đã đặt trước).", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Không có bàn để chuyển.", Toast.LENGTH_SHORT).show();
                        }
                        return;
                    }

                    // Sort candidates by floor then numeric tableNumber for predictable order
                    Collections.sort(candidates, (a, b) -> {
                        int fa = parseFloorFromLocation(a.getLocation());
                        int fb = parseFloorFromLocation(b.getLocation());
                        if (fa != fb) return Integer.compare(fa, fb);
                        try {
                            return Integer.compare(a.getTableNumber(), b.getTableNumber());
                        } catch (Exception e) {
                            return String.valueOf(a.getTableNumber()).compareTo(String.valueOf(b.getTableNumber()));
                        }
                    });

                    // Build readable labels safely
                    List<String> labels = new ArrayList<>();
                    for (TableItem t : candidates) {
                        String statusLabel = getStatusLabel(t);
                        labels.add("Bàn " + t.getTableNumber() + " - " + statusLabel);
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1, labels);
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Chọn bàn để chuyển khách từ Bàn " + fromTable.getTableNumber())
                            .setAdapter(adapter, (DialogInterface dialog, int which) -> {
                                TableItem target = candidates.get(which);
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("Xác nhận chuyển")
                                        .setMessage("Chuyển khách từ Bàn " + fromTable.getTableNumber() + " → Bàn " + target.getTableNumber() + "?")
                                        .setPositiveButton("Chuyển", (d2, w2) -> performTransfer(fromTable, target))
                                        .setNegativeButton("Hủy", null)
                                        .show();
                                dialog.dismiss();
                            })
                            .setNegativeButton("Hủy", null)
                            .show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Lỗi khi tải danh sách bàn: " + message, Toast.LENGTH_LONG).show());
            }
        });
    }

    /**
     * Helper to return human-friendly status label for a table.
     * Tries getStatusDisplay() if available; otherwise falls back to enum name or simple mapping.
     */
    private String getStatusLabel(TableItem t) {
        if (t == null) return "";
        try {
            // try method getStatusDisplay()
            try {
                java.lang.reflect.Method m = t.getClass().getMethod("getStatusDisplay");
                Object res = m.invoke(t);
                if (res != null) {
                    String s = String.valueOf(res).trim();
                    if (!s.isEmpty()) return s;
                }
            } catch (NoSuchMethodException ignored) {}

            // fallback to TableItem.Status enum name or numeric mapping
            TableItem.Status st = t.getStatus();
            if (st != null) {
                String name = st.name().toLowerCase(Locale.getDefault());
                switch (name) {
                    case "reserved": return "Đã được đặt trước";
                    case "occupied": return "Đã có khách";
                    case "available":
                    case "empty":
                        return "Trống";
                    default:
                        return capitalize(name);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "getStatusLabel error: " + e.getMessage());
        }
        return "";
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void performTransfer(final TableItem originalFromTable, final TableItem targetTable) {
        if (originalFromTable == null || targetTable == null) return;
        progressBar.setVisibility(View.VISIBLE);

        // Refresh the fromTable from server to ensure we have latest status/reservation metadata
        tableRepository.getTableById(originalFromTable.getId(), new TableRepository.RepositoryCallback<TableItem>() {
            @Override
            public void onSuccess(TableItem fromTable) {
                runOnUiThread(() -> {
                    // Final validation rules before performing transfer:
                    TableItem.Status fromStatus = fromTable.getStatus();
                    TableItem.Status toStatus = targetTable.getStatus();

                    // Rule A: reserved -> can only go to available/empty
                    if (fromStatus == TableItem.Status.RESERVED) {
                        if (!(toStatus == TableItem.Status.AVAILABLE || toStatus == TableItem.Status.EMPTY)) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(MainActivity.this, "Không thể chuyển: bàn đặt trước chỉ được chuyển sang bàn trống/available.", Toast.LENGTH_LONG).show();
                            fetchTablesFromServer(); // refresh UI
                            return;
                        }
                    }

                    // Rule B: occupied -> cannot move into reserved
                    if (fromStatus == TableItem.Status.OCCUPIED) {
                        if (toStatus == TableItem.Status.RESERVED) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(MainActivity.this, "Không thể chuyển: bàn đang có khách không được chuyển vào bàn đã đặt trước.", Toast.LENGTH_LONG).show();
                            fetchTablesFromServer();
                            return;
                        }
                    }

                    // If source is RESERVED we do reservation-transfer behavior (preserve reservation on target)
                    if (fromStatus == TableItem.Status.RESERVED) {
                        String rName = fromTable.getReservationName();
                        String rPhone = fromTable.getReservationPhone();
                        String rAt = fromTable.getReservationAt();

                        if ((rName == null || rName.trim().isEmpty()) &&
                                (rPhone == null || rPhone.trim().isEmpty()) &&
                                (rAt == null || rAt.trim().isEmpty())) {
                            // Ask staff to enter reservation details (fallback)
                            progressBar.setVisibility(View.GONE);
                            showTransferReservationDialog(fromTable, targetTable);
                            return;
                        }

                        // perform reservation transfer: set target -> reserved with metadata, clear source
                        Map<String, Object> targetBody = new HashMap<>();
                        targetBody.put("status", "reserved");
                        targetBody.put("reservationName", rName);
                        targetBody.put("reservationPhone", rPhone);
                        targetBody.put("reservationAt", rAt);

                        tableRepository.updateTable(targetTable.getId(), targetBody, new TableRepository.RepositoryCallback<TableItem>() {
                            @Override
                            public void onSuccess(TableItem updatedTarget) {
                                // clear source
                                Map<String, Object> clearSource = new HashMap<>();
                                clearSource.put("status", "available");
                                clearSource.put("reservationName", "");
                                clearSource.put("reservationPhone", "");
                                clearSource.put("reservationAt", "");
                                tableRepository.updateTable(originalFromTable.getId(), clearSource, new TableRepository.RepositoryCallback<TableItem>() {
                                    @Override
                                    public void onSuccess(TableItem updatedSource) {
                                        runOnUiThread(() -> {
                                            progressBar.setVisibility(View.GONE);
                                            Toast.makeText(MainActivity.this, "Chuyển đặt trước thành công", Toast.LENGTH_SHORT).show();
                                            fetchTablesFromServer();
                                        });
                                    }
                                    @Override
                                    public void onError(String message) {
                                        // rollback target -> available
                                        Map<String, Object> rollback = new HashMap<>();
                                        rollback.put("status", "available");
                                        tableRepository.updateTable(targetTable.getId(), rollback, new TableRepository.RepositoryCallback<TableItem>() {
                                            @Override
                                            public void onSuccess(TableItem rollbackTable) {
                                                runOnUiThread(() -> {
                                                    progressBar.setVisibility(View.GONE);
                                                    Toast.makeText(MainActivity.this, "Không thể chuyển đặt trước: " + message + " (đã rollback)", Toast.LENGTH_LONG).show();
                                                    fetchTablesFromServer();
                                                });
                                            }
                                            @Override
                                            public void onError(String rbMsg) {
                                                runOnUiThread(() -> {
                                                    progressBar.setVisibility(View.GONE);
                                                    Toast.makeText(MainActivity.this, "Không thể chuyển đặt trước: " + message + " ; rollback thất bại: " + rbMsg, Toast.LENGTH_LONG).show();
                                                    fetchTablesFromServer();
                                                });
                                            }
                                        });
                                    }
                                });
                            }

                            @Override
                            public void onError(String message) {
                                runOnUiThread(() -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(MainActivity.this, "Không thể đặt bàn đích là reserved: " + message, Toast.LENGTH_LONG).show();
                                    fetchTablesFromServer();
                                });
                            }
                        });

                        return;
                    }

                    // Otherwise (not reserved) - regular move orders then update statuses
                    final int fromNumber = originalFromTable.getTableNumber();
                    final int toNumber = targetTable.getTableNumber();

                    orderRepository.moveOrdersForTable(fromNumber, toNumber, new OrderRepository.RepositoryCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            // update statuses: target -> occupied, source -> available
                            updateTablesAfterMove(originalFromTable, targetTable, true);
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Không thể chuyển order: " + message + " — sẽ vẫn cập nhật trạng thái bàn", Toast.LENGTH_LONG).show());
                            updateTablesAfterMove(originalFromTable, targetTable, false);
                        }
                    });
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Không thể lấy thông tin bàn nguồn: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void updateTablesAfterMove(final TableItem fromTable, final TableItem targetTable, final boolean ordersMoved) {
        // set target -> occupied
        tableRepository.updateTableStatus(targetTable.getId(), "occupied", new TableRepository.RepositoryCallback<TableItem>() {
            @Override
            public void onSuccess(TableItem updatedTarget) {
                // set source -> available
                tableRepository.updateTableStatus(fromTable.getId(), "available", new TableRepository.RepositoryCallback<TableItem>() {
                    @Override
                    public void onSuccess(TableItem updatedSource) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            if (ordersMoved) {
                                Toast.makeText(MainActivity.this, "Chuyển bàn và đơn hàng thành công", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(MainActivity.this, "Chuyển bàn thành công (đơn hàng chưa chuyển)", Toast.LENGTH_LONG).show();
                            }
                            fetchTablesFromServer();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        // rollback target -> available
                        tableRepository.updateTableStatus(targetTable.getId(), "available", new TableRepository.RepositoryCallback<TableItem>() {
                            @Override
                            public void onSuccess(TableItem rollbackTable) {
                                runOnUiThread(() -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(MainActivity.this, "Không thể chuyển: " + message + " (đã rollback)", Toast.LENGTH_LONG).show();
                                    fetchTablesFromServer();
                                });
                            }
                            @Override
                            public void onError(String rbMsg) {
                                runOnUiThread(() -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(MainActivity.this, "Không thể chuyển: " + message + " ; rollback thất bại: " + rbMsg, Toast.LENGTH_LONG).show();
                                    fetchTablesFromServer();
                                });
                            }
                        });
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Không thể đặt bàn đích là occupied: " + message, Toast.LENGTH_LONG).show();
                    fetchTablesFromServer();
                });
            }
        });
    }

    private void showTransferReservationDialog(final TableItem fromTable, final TableItem targetTable) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        final EditText etName = new EditText(this);
        etName.setHint("Tên khách");
        layout.addView(etName, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final EditText etPhone = new EditText(this);
        etPhone.setHint("Số điện thoại");
        etPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        layout.addView(etPhone, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final EditText etDate = new EditText(this);
        etDate.setHint("Chọn ngày (yyyy-MM-dd)");
        etDate.setFocusable(false);
        etDate.setClickable(true);
        layout.addView(etDate, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final EditText etTime = new EditText(this);
        etTime.setHint("Chọn giờ (HH:mm)");
        etTime.setFocusable(false);
        etTime.setClickable(true);
        layout.addView(etTime, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final Calendar selectedCal = Calendar.getInstance();
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        etDate.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            int year = selectedCal.get(Calendar.YEAR);
            int month = selectedCal.get(Calendar.MONTH);
            int day = selectedCal.get(Calendar.DAY_OF_MONTH);
            DatePickerDialog dpd = new DatePickerDialog(MainActivity.this, (view, y, m, d) -> {
                selectedCal.set(Calendar.YEAR, y);
                selectedCal.set(Calendar.MONTH, m);
                selectedCal.set(Calendar.DAY_OF_MONTH, d);
                etDate.setText(dateFormat.format(selectedCal.getTime()));
            }, year, month, day);
            dpd.show();
        });

        etTime.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            int hour = selectedCal.get(Calendar.HOUR_OF_DAY);
            int minute = selectedCal.get(Calendar.MINUTE);
            TimePickerDialog tpd = new TimePickerDialog(MainActivity.this, (view, h, m) -> {
                selectedCal.set(Calendar.HOUR_OF_DAY, h);
                selectedCal.set(Calendar.MINUTE, m);
                selectedCal.set(Calendar.SECOND, 0);
                etTime.setText(timeFormat.format(selectedCal.getTime()));
            }, hour, minute, true);
            tpd.show();
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Nhập thông tin đặt trước để chuyển")
                .setView(layout)
                .setPositiveButton("Chuyển", null)
                .setNegativeButton("Hủy", (d, w) -> d.dismiss())
                .create();

        dialog.setOnShowListener(dlg -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etName.getText().toString().trim();
                String phone = etPhone.getText().toString().trim();
                String date = etDate.getText().toString().trim();
                String time = etTime.getText().toString().trim();
                if (name.isEmpty() || phone.isEmpty() || date.isEmpty() || time.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Vui lòng nhập đầy đủ: tên, điện thoại, ngày và giờ", Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                progressBar.setVisibility(View.VISIBLE);

                String reservationAt = date + " " + time; // yyyy-MM-dd HH:mm
                Map<String,Object> targetBody = new HashMap<>();
                targetBody.put("status", "reserved");
                targetBody.put("reservationName", name);
                targetBody.put("reservationPhone", phone);
                targetBody.put("reservationAt", reservationAt);

                // set target -> reserved + metadata
                tableRepository.updateTable(targetTable.getId(), targetBody, new TableRepository.RepositoryCallback<TableItem>() {
                    @Override
                    public void onSuccess(TableItem updatedTarget) {
                        // clear source
                        Map<String,Object> clearSource = new HashMap<>();
                        clearSource.put("status", "available");
                        clearSource.put("reservationName", "");
                        clearSource.put("reservationPhone", "");
                        clearSource.put("reservationAt", "");
                        tableRepository.updateTable(originalFromTableIdOrFallback(fromTable), clearSource, new TableRepository.RepositoryCallback<TableItem>() {
                            @Override
                            public void onSuccess(TableItem updatedSource) {
                                runOnUiThread(() -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(MainActivity.this, "Chuyển đặt trước thành công", Toast.LENGTH_SHORT).show();
                                    dialog.dismiss();
                                    fetchTablesFromServer();
                                });
                            }

                            @Override
                            public void onError(String message) {
                                Map<String,Object> rollback = new HashMap<>();
                                rollback.put("status", "available");
                                tableRepository.updateTable(targetTable.getId(), rollback, new TableRepository.RepositoryCallback<TableItem>() {
                                    @Override
                                    public void onSuccess(TableItem rollbackTable) {
                                        runOnUiThread(() -> {
                                            progressBar.setVisibility(View.GONE);
                                            Toast.makeText(MainActivity.this, "Không thể chuyển đặt trước: " + message + " (đã rollback)", Toast.LENGTH_LONG).show();
                                            dialog.dismiss();
                                            fetchTablesFromServer();
                                        });
                                    }

                                    @Override
                                    public void onError(String rbMsg) {
                                        runOnUiThread(() -> {
                                            progressBar.setVisibility(View.GONE);
                                            Toast.makeText(MainActivity.this, "Không thể chuyển đặt trước: " + message + " ; rollback thất bại: " + rbMsg, Toast.LENGTH_LONG).show();
                                            dialog.dismiss();
                                            fetchTablesFromServer();
                                        });
                                    }
                                });
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            Toast.makeText(MainActivity.this, "Không thể đặt bàn đích: " + message, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            });
        });

        dialog.show();
    }

    // helper: return id of fromTable (defensive) - avoids capturing fromTable variable in deep anonymous scopes
    private String originalFromTableIdOrFallback(TableItem fromTable) {
        if (fromTable == null) return "";
        String id = fromTable.getId();
        return id == null ? "" : id;
    }

    /* Merge helpers (unchanged) */
    private void showMergeDialog(TableItem fromTable) {
        tableRepository.getAllTables(new TableRepository.RepositoryCallback<List<TableItem>>() {
            @Override
            public void onSuccess(List<TableItem> result) {
                runOnUiThread(() -> {
                    List<String> labels = new ArrayList<>();
                    List<TableItem> candidates = new ArrayList<>();
                    for (TableItem t : result) {
                        if (t == null) continue;
                        if (fromTable.getId() != null && fromTable.getId().equals(t.getId())) continue;
                        labels.add("Bàn " + t.getTableNumber() + " - " + getStatusLabel(t));
                        candidates.add(t);
                    }
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_list_item_1, labels);
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Chọn bàn để gộp với Bàn " + fromTable.getTableNumber())
                            .setAdapter(adapter, (DialogInterface dialog, int which) -> {
                                TableItem target = candidates.get(which);
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("Xác nhận gộp")
                                        .setMessage("Gộp Bàn " + fromTable.getTableNumber() + " vào Bàn " + target.getTableNumber() + "?")
                                        .setPositiveButton("Gộp", (d2, w2) -> performMerge(fromTable, target))
                                        .setNegativeButton("Hủy", null)
                                        .show();
                                dialog.dismiss();
                            })
                            .setNegativeButton("Hủy", null)
                            .show();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Lỗi khi tải bàn: " + message, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void performMerge(TableItem fromTable, TableItem targetTable) {
        if (fromTable == null || targetTable == null) return;
        progressBar.setVisibility(View.VISIBLE);
        tableRepository.mergeTables(fromTable.getId(), targetTable.getId(), new TableRepository.RepositoryCallback<TableItem>() {
            @Override
            public void onSuccess(TableItem mergedTarget) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Gộp bàn thành công", Toast.LENGTH_SHORT).show();
                    fetchTablesFromServer();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Gộp bàn thất bại: " + message, Toast.LENGTH_LONG).show();
                    fetchTablesFromServer();
                });
            }
        });
    }

    /**
     * showReservationDialogWithPickers: đặt trước bàn (ghi reservation metadata nếu server hỗ trợ)
     */
    private void showReservationDialogWithPickers(TableItem table) {
        if (table == null) return;
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        final EditText etName = new EditText(this);
        etName.setHint("Tên khách");
        layout.addView(etName, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final EditText etPhone = new EditText(this);
        etPhone.setHint("Số điện thoại");
        etPhone.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        layout.addView(etPhone, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final EditText etDate = new EditText(this);
        etDate.setHint("Chọn ngày (yyyy-MM-dd)");
        etDate.setFocusable(false);
        etDate.setClickable(true);
        layout.addView(etDate, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final EditText etTime = new EditText(this);
        etTime.setHint("Chọn giờ (HH:mm)");
        etTime.setFocusable(false);
        etTime.setClickable(true);
        layout.addView(etTime, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final Calendar selectedCal = Calendar.getInstance();
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        etDate.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            int year = selectedCal.get(Calendar.YEAR);
            int month = selectedCal.get(Calendar.MONTH);
            int day = selectedCal.get(Calendar.DAY_OF_MONTH);
            DatePickerDialog dpd = new DatePickerDialog(MainActivity.this, (view, y, m, d) -> {
                selectedCal.set(Calendar.YEAR, y);
                selectedCal.set(Calendar.MONTH, m);
                selectedCal.set(Calendar.DAY_OF_MONTH, d);
                etDate.setText(dateFormat.format(selectedCal.getTime()));
            }, year, month, day);
            dpd.show();
        });

        etTime.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            int hour = selectedCal.get(Calendar.HOUR_OF_DAY);
            int minute = selectedCal.get(Calendar.MINUTE);
            TimePickerDialog tpd = new TimePickerDialog(MainActivity.this, (view, h, m) -> {
                selectedCal.set(Calendar.HOUR_OF_DAY, h);
                selectedCal.set(Calendar.MINUTE, m);
                selectedCal.set(Calendar.SECOND, 0);
                etTime.setText(timeFormat.format(selectedCal.getTime()));
            }, hour, minute, true);
            tpd.show();
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Đặt trước - Bàn " + table.getTableNumber())
                .setView(layout)
                .setPositiveButton("Đặt", null)
                .setNegativeButton("Hủy", (d, which) -> d.dismiss())
                .create();

        dialog.setOnShowListener(dlg -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etName.getText().toString().trim();
                String phone = etPhone.getText().toString().trim();
                String dateStr = etDate.getText().toString().trim();
                String timeStr = etTime.getText().toString().trim();
                if (name.isEmpty() || phone.isEmpty() || dateStr.isEmpty() || timeStr.isEmpty()) {
                    Toast.makeText(MainActivity.this, "Vui lòng nhập đầy đủ: tên, điện thoại, ngày và giờ", Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                progressBar.setVisibility(View.VISIBLE);

                // Build reservation payload and send it to update table so reservation metadata stored on server
                String reservationAt = dateStr + " " + timeStr; // you can change to ISO if server expects
                Map<String, Object> body = new HashMap<>();
                body.put("status", "reserved");
                body.put("reservationName", name);
                body.put("reservationPhone", phone);
                body.put("reservationAt", reservationAt);

                tableRepository.updateTable(table.getId(), body, new TableRepository.RepositoryCallback<TableItem>() {
                    @Override
                    public void onSuccess(TableItem updatedTable) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(MainActivity.this, "Đặt trước thành công", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            fetchTablesFromServer();
                        });
                    }
                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            Toast.makeText(MainActivity.this, "Không thể đặt trước: " + message, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            });
        });

        dialog.show();
    }

    private void cancelReservation(TableItem table) {
        if (table == null || table.getId() == null || table.getId().trim().isEmpty()) {
            Toast.makeText(this, "Bàn không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        tableRepository.updateTable(table.getId(), new HashMap<String, Object>() {{
            put("status", "available");
            put("reservationName", "");
            put("reservationPhone", "");
            put("reservationAt", "");
        }}, new TableRepository.RepositoryCallback<TableItem>() {
            @Override
            public void onSuccess(TableItem result) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Hủy đặt trước thành công", Toast.LENGTH_SHORT).show();
                    fetchTablesFromServer();
                });
            }
            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Không thể hủy đặt trước: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}