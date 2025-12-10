package com.ph48845.datn_qlnh_rmis.ui.table;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.view.inputmethod.InputMethodManager;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.content.Context;
import android.view.LayoutInflater;

import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;
import com.ph48845.datn_qlnh_rmis.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TransferManager: chứa logic chuyển bàn (move orders + cập nhật trạng thái) và dialog nhập đặt trước khi cần.
 */
public class TransferManager {

    private final android.app.Activity host;
    private final TableRepository tableRepository;
    private final OrderRepository orderRepository;
    private final ProgressBar progressBar;

    public TransferManager(android.app.Activity host, TableRepository tableRepository, OrderRepository orderRepository, ProgressBar progressBar) {
        this.host = host;
        this.tableRepository = tableRepository;
        this.orderRepository = orderRepository;
        this.progressBar = progressBar;
    }

    public void showTransferDialog(TableItem fromTable) {
        tableRepository.getAllTables(new TableRepository.RepositoryCallback<List<TableItem>>() {
            @Override
            public void onSuccess(List<TableItem> result) {
                host.runOnUiThread(() -> {
                    List<TableItem> candidates = new ArrayList<>();
                    TableItem.Status fromStatus = TableItem.Status.EMPTY;
                    try { fromStatus = fromTable.getStatus(); } catch (Exception ignored) {}

                    for (TableItem t : result) {
                        if (t == null) continue;
                        if (fromTable.getId() != null && fromTable.getId().equals(t.getId())) continue;
                        TableItem.Status ts = t.getStatus();
                        boolean allowed = true;
                        if (fromStatus == TableItem.Status.RESERVED) {
                            if (!(ts == TableItem.Status.AVAILABLE || ts == TableItem.Status.EMPTY)) allowed = false;
                        } else if (fromStatus == TableItem.Status.OCCUPIED) {
                            if (ts == TableItem.Status.RESERVED) allowed = false;
                        }
                        if (allowed) candidates.add(t);
                    }

                    if (candidates.isEmpty()) {
                        Toast.makeText(host, "Không có bàn để chuyển.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Collections.sort(candidates, (a, b) -> {
                        int fa = parseFloor(a.getLocation()), fb = parseFloor(b.getLocation());
                        if (fa != fb) return Integer.compare(fa, fb);
                        return Integer.compare(a.getTableNumber(), b.getTableNumber());
                    });

                    List<String> labels = new ArrayList<>();
                    for (TableItem t : candidates) labels.add("Bàn " + t.getTableNumber() + " - " + safeStatusLabel(t));

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(host, android.R.layout.simple_list_item_1, labels);
                    new AlertDialog.Builder(host)
                            .setTitle("Chọn bàn để chuyển khách từ Bàn " + fromTable.getTableNumber())
                            .setAdapter(adapter, (dialog, which) -> {
                                TableItem target = candidates.get(which);
                                new AlertDialog.Builder(host)
                                        .setTitle("Xác nhận chuyển")
                                        .setMessage("Chuyển khách từ Bàn " + fromTable.getTableNumber() + " → Bàn " + target.getTableNumber() + "?")
                                        .setPositiveButton("Chuyển", (d2, w2) -> performTransfer(fromTable, target))
                                        .setNegativeButton("Hủy", null)
                                        .show();
                            })
                            .setNegativeButton("Hủy", null)
                            .show();
                });
            }

            @Override
            public void onError(String message) {
                host.runOnUiThread(() -> Toast.makeText(host, "Lỗi tải bàn: " + message, Toast.LENGTH_LONG).show());
            }
        });
    }

    private int parseFloor(String loc) { if (loc == null) return 1; try { java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(loc); if (m.find()) return Integer.parseInt(m.group(1)); } catch (Exception ignored) {} return 1; }

    private String safeStatusLabel(TableItem t) {
        try {
            String s = t.getStatusDisplay();
            if (s != null && !s.isEmpty()) return s;
        } catch (Exception ignored) {}
        if (t.getStatus() == TableItem.Status.AVAILABLE) return "Khả dụng";
        if (t.getStatus() == TableItem.Status.OCCUPIED) return "Đã có khách";
        if (t.getStatus() == TableItem.Status.RESERVED) return "Đã được đặt trước";
        return "Khả dụng";
    }

    public void showTransferReservationDialog(final TableItem fromTable, final TableItem targetTable) {
        // Inflate dialog_reservation.xml (reusing same layout as ReservationHelper)
        LayoutInflater inflater = LayoutInflater.from(host);
        View layout = inflater.inflate(R.layout.dialog_reservation, null);

        final EditText etName = layout.findViewById(R.id.et_res_name);
        final EditText etPhone = layout.findViewById(R.id.et_res_phone);
        final EditText etDate = layout.findViewById(R.id.et_res_date);
        final EditText etTime = layout.findViewById(R.id.et_res_time);

        final Calendar selectedCal = Calendar.getInstance();
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        etDate.setFocusable(false);
        etDate.setClickable(true);
        etDate.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) host.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            new DatePickerDialog(host, (view, y, m, d) -> {
                selectedCal.set(Calendar.YEAR, y); selectedCal.set(Calendar.MONTH, m); selectedCal.set(Calendar.DAY_OF_MONTH, d);
                etDate.setText(dateFormat.format(selectedCal.getTime()));
            }, selectedCal.get(Calendar.YEAR), selectedCal.get(Calendar.MONTH), selectedCal.get(Calendar.DAY_OF_MONTH)).show();
        });

        etTime.setFocusable(false);
        etTime.setClickable(true);
        etTime.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) host.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            new TimePickerDialog(host, (view, h, m) -> {
                selectedCal.set(Calendar.HOUR_OF_DAY, h); selectedCal.set(Calendar.MINUTE, m); selectedCal.set(Calendar.SECOND, 0);
                etTime.setText(timeFormat.format(selectedCal.getTime()));
            }, selectedCal.get(Calendar.HOUR_OF_DAY), selectedCal.get(Calendar.MINUTE), true).show();
        });

        AlertDialog dialog = new AlertDialog.Builder(host)
                .setTitle("Nhập thông tin đặt trước để chuyển")
                .setView(layout)
                .setPositiveButton("Chuyển", null)
                .setNegativeButton("Hủy", (d,w) -> d.dismiss())
                .create();

        dialog.setOnShowListener(dlg -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etName.getText().toString().trim();
                String phone = etPhone.getText().toString().trim();
                String date = etDate.getText().toString().trim();
                String time = etTime.getText().toString().trim();
                if (name.isEmpty() || phone.isEmpty() || date.isEmpty() || time.isEmpty()) {
                    Toast.makeText(host, "Vui lòng nhập đầy đủ", Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                progressBar.setVisibility(View.VISIBLE);
                String reservationAt = date + " " + time;
                Map<String,Object> targetBody = new HashMap<>();
                targetBody.put("status","reserved");
                targetBody.put("reservationName", name);
                targetBody.put("reservationPhone", phone);
                targetBody.put("reservationAt", reservationAt);

                tableRepository.updateTable(targetTable.getId(), targetBody, new TableRepository.RepositoryCallback<TableItem>() {
                    @Override
                    public void onSuccess(TableItem updatedTarget) {
                        Map<String,Object> clearSource = new HashMap<>();
                        clearSource.put("status","available");
                        clearSource.put("reservationName","");
                        clearSource.put("reservationPhone","");
                        clearSource.put("reservationAt","");
                        tableRepository.updateTable(fromTable.getId(), clearSource, new TableRepository.RepositoryCallback<TableItem>() {
                            @Override
                            public void onSuccess(TableItem updatedSource) {
                                host.runOnUiThread(() -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(host, "Chuyển đặt trước thành công", Toast.LENGTH_SHORT).show();
                                    dialog.dismiss();
                                    if (host instanceof com.ph48845.datn_qlnh_rmis.ui.MainActivity) ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) host).fetchTablesFromServer();
                                });
                            }
                            @Override
                            public void onError(String message) {
                                Map<String,Object> rollback = new HashMap<>();
                                rollback.put("status","available");
                                tableRepository.updateTable(targetTable.getId(), rollback, new TableRepository.RepositoryCallback<TableItem>() {
                                    @Override
                                    public void onSuccess(TableItem rollbackTable) {
                                        host.runOnUiThread(() -> {
                                            progressBar.setVisibility(View.GONE);
                                            Toast.makeText(host, "Không thể chuyển đặt trước: " + message + " (đã rollback)", Toast.LENGTH_LONG).show();
                                            dialog.dismiss();
                                            if (host instanceof com.ph48845.datn_qlnh_rmis.ui.MainActivity) ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) host).fetchTablesFromServer();
                                        });
                                    }
                                    @Override
                                    public void onError(String rbMsg) {
                                        host.runOnUiThread(() -> {
                                            progressBar.setVisibility(View.GONE);
                                            Toast.makeText(host, "Không thể chuyển đặt trước, rollback thất bại: " + rbMsg, Toast.LENGTH_LONG).show();
                                            dialog.dismiss();
                                            if (host instanceof com.ph48845.datn_qlnh_rmis.ui.MainActivity) ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) host).fetchTablesFromServer();
                                        });
                                    }
                                });
                            }
                        });
                    }
                    @Override
                    public void onError(String message) {
                        host.runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            Toast.makeText(host, "Không thể đặt bàn đích: " + message, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            });
        });
        dialog.show();
    }

    public void performTransfer(final TableItem originalFromTable, final TableItem targetTable) {
        if (originalFromTable == null || targetTable == null) return;
        progressBar.setVisibility(View.VISIBLE);
        tableRepository.getTableById(originalFromTable.getId(), new TableRepository.RepositoryCallback<TableItem>() {
            @Override
            public void onSuccess(TableItem fromTable) {
                host.runOnUiThread(() -> {
                    TableItem.Status fromStatus = fromTable.getStatus();
                    TableItem.Status toStatus = targetTable.getStatus();
                    if (fromStatus == TableItem.Status.RESERVED) {
                        if (!(toStatus == TableItem.Status.AVAILABLE || toStatus == TableItem.Status.EMPTY)) {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(host, "Không thể chuyển: bàn đặt trước chỉ được chuyển sang bàn trống/available.", Toast.LENGTH_LONG).show();
                            if (host instanceof com.ph48845.datn_qlnh_rmis.ui.MainActivity) ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) host).fetchTablesFromServer();
                            return;
                        }
                    }
                    if (fromStatus == TableItem.Status.OCCUPIED && toStatus == TableItem.Status.RESERVED) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(host, "Không thể chuyển: bàn đang có khách không được chuyển vào bàn đã đặt trước.", Toast.LENGTH_LONG).show();
                        if (host instanceof com.ph48845.datn_qlnh_rmis.ui.MainActivity) ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) host).fetchTablesFromServer();
                        return;
                    }
                    if (fromStatus == TableItem.Status.RESERVED) {
                        String rName = fromTable.getReservationName();
                        String rPhone = fromTable.getReservationPhone();
                        String rAt = fromTable.getReservationAt();
                        if ((rName == null || rName.trim().isEmpty()) && (rPhone == null || rPhone.trim().isEmpty()) && (rAt == null || rAt.trim().isEmpty())) {
                            progressBar.setVisibility(View.GONE);
                            showTransferReservationDialog(fromTable, targetTable);
                            return;
                        }
                        Map<String,Object> targetBody = new HashMap<>();
                        targetBody.put("status","reserved");
                        targetBody.put("reservationName", rName);
                        targetBody.put("reservationPhone", rPhone);
                        targetBody.put("reservationAt", rAt);
                        tableRepository.updateTable(targetTable.getId(), targetBody, new TableRepository.RepositoryCallback<TableItem>() {
                            @Override
                            public void onSuccess(TableItem updatedTarget) {
                                Map<String,Object> clearSource = new HashMap<>();
                                clearSource.put("status","available");
                                clearSource.put("reservationName","");
                                clearSource.put("reservationPhone","");
                                clearSource.put("reservationAt","");
                                tableRepository.updateTable(originalFromTable.getId(), clearSource, new TableRepository.RepositoryCallback<TableItem>() {
                                    @Override
                                    public void onSuccess(TableItem updatedSource) {
                                        host.runOnUiThread(() -> {
                                            progressBar.setVisibility(View.GONE);
                                            Toast.makeText(host, "Chuyển đặt trước thành công", Toast.LENGTH_SHORT).show();
                                            if (host instanceof com.ph48845.datn_qlnh_rmis.ui.MainActivity) ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) host).fetchTablesFromServer();
                                        });
                                    }
                                    @Override
                                    public void onError(String message) {
                                        Map<String,Object> rollback = new HashMap<>();
                                        rollback.put("status","available");
                                        tableRepository.updateTable(targetTable.getId(), rollback, new TableRepository.RepositoryCallback<TableItem>() {
                                            @Override
                                            public void onSuccess(TableItem rollbackTable) {
                                                host.runOnUiThread(() -> {
                                                    progressBar.setVisibility(View.GONE);
                                                    Toast.makeText(host, "Không thể chuyển đặt trước: " + message + " (đã rollback)", Toast.LENGTH_LONG).show();
                                                    if (host instanceof com.ph48845.datn_qlnh_rmis.ui.MainActivity) ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) host).fetchTablesFromServer();
                                                });
                                            }
                                            @Override
                                            public void onError(String rbMsg) {
                                                host.runOnUiThread(() -> {
                                                    progressBar.setVisibility(View.GONE);
                                                    Toast.makeText(host, "Không thể chuyển đặt trước: " + message + " ; rollback thất bại: " + rbMsg, Toast.LENGTH_LONG).show();
                                                    if (host instanceof com.ph48845.datn_qlnh_rmis.ui.MainActivity) ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) host).fetchTablesFromServer();
                                                });
                                            }
                                        });
                                    }
                                });
                            }
                            @Override
                            public void onError(String message) {
                                host.runOnUiThread(() -> {
                                    progressBar.setVisibility(View.GONE);
                                    Toast.makeText(host, "Không thể đặt bàn đích là reserved: " + message, Toast.LENGTH_LONG).show();
                                    if (host instanceof com.ph48845.datn_qlnh_rmis.ui.MainActivity) ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) host).fetchTablesFromServer();
                                });
                            }
                        });
                        return;
                    }

                    final int fromNumber = originalFromTable.getTableNumber();
                    final int toNumber = targetTable.getTableNumber();
                    orderRepository.moveOrdersForTable(fromNumber, toNumber, new OrderRepository.RepositoryCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            updateTablesAfterMove(originalFromTable, targetTable, true);
                        }
                        @Override
                        public void onError(String message) {
                            host.runOnUiThread(() -> Toast.makeText(host, "Không thể chuyển order: " + message + " — sẽ vẫn cập nhật trạng thái bàn", Toast.LENGTH_LONG).show());
                            updateTablesAfterMove(originalFromTable, targetTable, false);
                        }
                    });
                });
            }
            @Override
            public void onError(String message) {
                host.runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(host, "Không thể lấy thông tin bàn nguồn: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void updateTablesAfterMove(final TableItem fromTable, final TableItem targetTable, final boolean ordersMoved) {
        tableRepository.updateTableStatus(targetTable.getId(), "occupied", new TableRepository.RepositoryCallback<TableItem>() {
            @Override
            public void onSuccess(TableItem updatedTarget) {
                tableRepository.updateTableStatus(fromTable.getId(), "available", new TableRepository.RepositoryCallback<TableItem>() {
                    @Override
                    public void onSuccess(TableItem updatedSource) {
                        host.runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(host, ordersMoved ? "Chuyển bàn và đơn hàng thành công" : "Chuyển bàn thành công (đơn hàng chưa chuyển)", Toast.LENGTH_SHORT).show();
                            if (host instanceof com.ph48845.datn_qlnh_rmis.ui.MainActivity) ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) host).fetchTablesFromServer();
                        });
                    }
                    @Override
                    public void onError(String message) {
                        table_repository_updateTargetRollback(message, targetTable);
                    }
                });
            }
            @Override
            public void onError(String message) {
                host.runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(host, "Không thể đặt bàn đích là occupied: " + message, Toast.LENGTH_LONG).show();
                    if (host instanceof com.ph48845.datn_qlnh_rmis.ui.MainActivity) ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) host).fetchTablesFromServer();
                });
            }
        });
    }

    // extracted small helper to keep previous behavior (rollback target->available if source update fails)
    private void table_repository_updateTargetRollback(String message, TableItem targetTable) {
        tableRepository.updateTableStatus(targetTable.getId(), "available", new TableRepository.RepositoryCallback<TableItem>() {
            @Override
            public void onSuccess(TableItem rollbackTable) {
                host.runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(host, "Không thể chuyển: " + message + " (đã rollback)", Toast.LENGTH_LONG).show();
                    if (host instanceof com.ph48845.datn_qlnh_rmis.ui.MainActivity) ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) host).fetchTablesFromServer();
                });
            }
            @Override
            public void onError(String rbMsg) {
                host.runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(host, "Không thể chuyển: " + message + " ; rollback thất bại: " + rbMsg, Toast.LENGTH_LONG).show();
                    if (host instanceof com.ph48845.datn_qlnh_rmis.ui.MainActivity) ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) host).fetchTablesFromServer();
                });
            }
        });
    }
}