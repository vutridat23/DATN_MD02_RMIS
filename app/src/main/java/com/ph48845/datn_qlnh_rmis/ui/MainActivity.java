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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * MainActivity: hiển thị danh sách bàn và đồng bộ trạng thái bàn với orders trên API.
 *
 * Sửa:
 * - Lấy tất cả orders 1 lần và build set tableNumbers có orders
 * - Duyệt bàn, cập nhật chỉ những bàn cần đổi trạng thái
 * - Giữ trạng thái RESERVED nếu backend đã đặt trước
 * - Chỉ gọi fetchTablesFromServer một lần sau khi tất cả updates hoàn tất
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

        // initial load
        fetchTablesFromServer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchTablesFromServer();
    }

    /**
     * Fetch tables from server and update adapters, then sync status with orders.
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

                    // phân chia theo location -> floor 1 / floor 2
                    List<TableItem> floor1 = new ArrayList<>();
                    List<TableItem> floor2 = new ArrayList<>();
                    for (TableItem t : result) {
                        String loc = t.getLocation() != null ? t.getLocation().toLowerCase(Locale.getDefault()) : "";
                        if (loc.contains("tầng 2") || loc.contains("tang 2") || loc.contains("floor 2") || loc.contains("2")) {
                            floor2.add(t);
                        } else {
                            floor1.add(t);
                        }
                    }

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
     * Đồng bộ trạng thái toàn bộ bàn theo orders.
     * Cách làm:
     *  - gọi API 1 lần để lấy tất cả orders
     *  - build set numbers của bàn có orders
     *  - duyệt tất cả table: nếu tableNumber in set => desired = occupied
     *      else desired = available (trừ khi table đang RESERVED thì giữ nguyên)
     *  - chỉ update những table có desired khác current
     *  - gọi fetchTablesFromServer() 1 lần sau khi tất cả update hoàn tất
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

        if (table.getStatus() == TableItem.Status.EMPTY || table.getStatus() == TableItem.Status.AVAILABLE) {
            popup.getMenu().add(0, 4, 3, "Đặt trước");
        }

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

    // ---------- Phần còn lại (transfer/merge/reservation) giữ nguyên như trước ----------
    private void showTransferDialog(TableItem fromTable) {
        tableRepository.getAllTables(new TableRepository.RepositoryCallback<List<TableItem>>() {
            @Override
            public void onSuccess(List<TableItem> result) {
                runOnUiThread(() -> {
                    List<String> labels = new ArrayList<>();
                    List<TableItem> candidates = new ArrayList<>();
                    for (TableItem t : result) {
                        if (t == null) continue;
                        if (fromTable.getId() != null && fromTable.getId().equals(t.getId())) continue;
                        labels.add("Bàn " + t.getTableNumber() + " - " + t.getStatusDisplay());
                        candidates.add(t);
                    }
                    if (labels.isEmpty()) {
                        Toast.makeText(MainActivity.this, "Không có bàn để chuyển.", Toast.LENGTH_SHORT).show();
                        return;
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
     * Updated performTransfer:
     * - First try to move orders from source tableNumber -> target tableNumber (orderRepository.moveOrdersForTable)
     * - Then update table statuses (target -> occupied, source -> available)
     * - Show appropriate toasts and refresh table list once done
     */
    private void performTransfer(TableItem fromTable, TableItem targetTable) {
        if (fromTable == null || targetTable == null) return;
        progressBar.setVisibility(View.VISIBLE);

        final int fromNumber = fromTable.getTableNumber();
        final int toNumber = targetTable.getTableNumber();

        // 1) Attempt to move orders first
        orderRepository.moveOrdersForTable(fromNumber, toNumber, new OrderRepository.RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Log.d(TAG, "Orders moved from " + fromNumber + " to " + toNumber + " successfully.");
                // 2) Now update table statuses
                updateTablesAfterMove(true);
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Failed to move orders: " + message);
                // Still attempt to update table statuses (so seating changes), but inform user orders were not moved
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Không thể chuyển order: " + message + " — sẽ vẫn cập nhật trạng thái bàn", Toast.LENGTH_LONG).show());
                updateTablesAfterMove(false);
            }

            // helper to update table statuses (performed after attempting moveOrders)
            private void updateTablesAfterMove(final boolean ordersMoved) {
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
                                // Attempt rollback: try to set target back to available
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
        });
    }

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
                        labels.add("Bàn " + t.getTableNumber() + " - " + t.getStatusDisplay());
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
                tableRepository.updateTableStatus(table.getId(), "reserved", new TableRepository.RepositoryCallback<TableItem>() {
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
        tableRepository.updateTableStatus(table.getId(), "available", new TableRepository.RepositoryCallback<TableItem>() {
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