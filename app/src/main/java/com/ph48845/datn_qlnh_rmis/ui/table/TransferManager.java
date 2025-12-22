package com.ph48845.datn_qlnh_rmis.ui.table;

import android.app.AlertDialog;
import android. app.DatePickerDialog;
import android. app.TimePickerDialog;
import android. view.View;
import android.widget.ArrayAdapter;
import android. widget.EditText;
import android.view.inputmethod.InputMethodManager;
import android.widget. ProgressBar;
import android.widget.Toast;
import android.view.LayoutInflater;

import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis. data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;
import com.ph48845.datn_qlnh_rmis. R;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util. Locale;
import java.util. Map;

/**
 * TransferManager:  chứa logic chuyển bàn (move orders + cập nhật trạng thái) và dialog nhập đặt trước khi cần.
 * ✅ CẬP NHẬT: Thêm dialog chọn hóa đơn trước khi chuyển bàn
 * 🔧 FIX:  Sửa lỗi IllegalFormatPrecisionException khi format giá tiền
 */
public class TransferManager {

    private final android.app.Activity host;
    private final TableRepository tableRepository;
    private final OrderRepository orderRepository;
    private final ProgressBar progressBar;

    public TransferManager(android.app. Activity host, TableRepository tableRepository, OrderRepository orderRepository, ProgressBar progressBar) {
        this.host = host;
        this.tableRepository = tableRepository;
        this.orderRepository = orderRepository;
        this.progressBar = progressBar;
    }

    /**
     * ✅ BƯỚC 1: Hiển thị dialog chọn hóa đơn từ bàn nguồn
     */
    public void showTransferDialog(TableItem fromTable) {
        if (fromTable == null) {
            Toast.makeText(host, "Bàn không hợp lệ", Toast. LENGTH_SHORT).show();
            return;
        }

        // Kiểm tra trạng thái bàn
        TableItem. Status fromStatus = TableItem.Status.AVAILABLE;
        try {
            fromStatus = fromTable.getStatus();
        } catch (Exception ignored) {}

        // Nếu bàn RESERVED → chuyển đặt trước (không cần chọn hóa đơn)
        if (fromStatus == TableItem.Status.RESERVED) {
            showTableSelectionForReservation(fromTable);
            return;
        }

        // Nếu bàn OCCUPIED → load hóa đơn và cho chọn
        if (fromStatus == TableItem.Status.OCCUPIED || fromStatus == TableItem.Status. PENDING_PAYMENT) {
            progressBar.setVisibility(View. VISIBLE);
            orderRepository.getOrdersByTableNumber(fromTable.getTableNumber(), null, new OrderRepository. RepositoryCallback<List<Order>>() {
                @Override
                public void onSuccess(List<Order> orders) {
                    host.runOnUiThread(() -> {
                        progressBar.setVisibility(View. GONE);

                        if (orders == null || orders.isEmpty()) {
                            Toast.makeText(host, "Bàn không có hóa đơn để chuyển", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Hiển thị dialog chọn hóa đơn
                        showOrderSelectionDialog(fromTable, orders);
                    });
                }

                @Override
                public void onError(String message) {
                    host.runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(host, "Lỗi tải hóa đơn: " + message, Toast.LENGTH_LONG).show();
                    });
                }
            });
        } else {
            Toast.makeText(host, "Bàn không có khách để chuyển", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * ✅ BƯỚC 2: Dialog chọn hóa đơn (MultiChoice)
     * 🔧 FIX: Dùng DecimalFormat thay vì String.format để tránh crash
     */
    private void showOrderSelectionDialog(TableItem fromTable, List<Order> orders) {
        DecimalFormat priceFormat = new DecimalFormat("#,###");

        List<String> orderLabels = new ArrayList<>();
        for (Order o : orders) {
            String orderId = o.getId();
            if (orderId == null || orderId.isEmpty()) orderId = o.getOrderId();

            // Hiển thị 6 ký tự cuối của orderId
            String displayId = orderId != null && orderId.length() > 6
                    ?  orderId.substring(orderId. length() - 6)
                    : (orderId != null ?  orderId : "N/A");

            int itemCount = o.getItems() != null ? o.getItems().size() : 0;

            // ✅ FIX:  Safe handling cho totalAmount
            double total = 0;
            try {
                total = o. getTotalAmount();
                if (Double.isNaN(total) || Double.isInfinite(total)) total = 0;
            } catch (Exception ignored) {}

            // ✅ FIX:  Dùng DecimalFormat thay vì String.format
            String totalStr = priceFormat. format(total);

            orderLabels.add(String. format("HĐ #%s - %d món - %s VND", displayId, itemCount, totalStr));
        }

        boolean[] selectedOrders = new boolean[orders.size()];

        AlertDialog.Builder builder = new AlertDialog.Builder(host);
        builder.setTitle("Chọn hóa đơn cần chuyển từ Bàn " + fromTable. getTableNumber());
        builder.setMultiChoiceItems(orderLabels.toArray(new String[0]), selectedOrders,
                (dialog, which, isChecked) -> selectedOrders[which] = isChecked);

        builder.setPositiveButton("Tiếp tục", (dialog, which) -> {
            List<Order> selected = new ArrayList<>();
            for (int i = 0; i < selectedOrders.length; i++) {
                if (selectedOrders[i]) selected.add(orders. get(i));
            }

            if (selected.isEmpty()) {
                Toast.makeText(host, "Vui lòng chọn ít nhất 1 hóa đơn", Toast.LENGTH_SHORT).show();
                return;
            }

            // Chuyển sang bước 3: chọn bàn đích
            showTableSelectionDialog(fromTable, selected);
        });

        builder. setNegativeButton("Hủy", null);
        builder.show();
    }

    /**
     * ✅ BƯỚC 3: Dialog chọn bàn đích
     */
    private void showTableSelectionDialog(TableItem fromTable, List<Order> selectedOrders) {
        tableRepository.getAllTables(new TableRepository. RepositoryCallback<List<TableItem>>() {
            @Override
            public void onSuccess(List<TableItem> result) {
                host.runOnUiThread(() -> {
                    List<TableItem> candidates = new ArrayList<>();
                    TableItem.Status fromStatus = TableItem.Status.AVAILABLE;
                    try { fromStatus = fromTable.getStatus(); } catch (Exception ignored) {}

                    for (TableItem t : result) {
                        if (t == null) continue;
                        if (fromTable.getId() != null && fromTable.getId().equals(t.getId())) continue;

                        TableItem.Status ts = t.getStatus();
                        boolean allowed = true;

                        // Bàn có khách không được chuyển vào bàn đặt trước
                        if (fromStatus == TableItem.Status. OCCUPIED && ts == TableItem.Status. RESERVED) {
                            allowed = false;
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
                    for (TableItem t : candidates) {
                        labels.add("Bàn " + t.getTableNumber() + " - " + safeStatusLabel(t));
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(host, android.R.layout.simple_list_item_1, labels);
                    new AlertDialog.Builder(host)
                            .setTitle("Chọn bàn đích (" + selectedOrders.size() + " hóa đơn)")
                            .setAdapter(adapter, (dialog, which) -> {
                                TableItem target = candidates.get(which);

                                // Xác nhận trước khi chuyển
                                String orderSummary = selectedOrders.size() == 1
                                        ? "1 hóa đơn"
                                        : selectedOrders.size() + " hóa đơn";

                                new AlertDialog.Builder(host)
                                        .setTitle("Xác nhận chuyển")
                                        .setMessage("Chuyển " + orderSummary + " từ Bàn " +
                                                fromTable.getTableNumber() + " → Bàn " +
                                                target.getTableNumber() + "?")
                                        .setPositiveButton("Chuyển", (d2, w2) ->
                                                performTransferSelectedOrders(fromTable, target, selectedOrders))
                                        .setNegativeButton("Hủy", null)
                                        .show();
                            })
                            . setNegativeButton("Hủy", null)
                            . show();
                });
            }

            @Override
            public void onError(String message) {
                host.runOnUiThread(() -> Toast.makeText(host, "Lỗi tải bàn: " + message, Toast. LENGTH_LONG).show());
            }
        });
    }

    /**
     * ✅ BƯỚC 4: Thực hiện chuyển các hóa đơn đã chọn
     */
    private void performTransferSelectedOrders(TableItem fromTable, TableItem targetTable, List<Order> selectedOrders) {
        if (selectedOrders == null || selectedOrders.isEmpty()) {
            Toast.makeText(host, "Không có hóa đơn để chuyển", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        final int total = selectedOrders.size();
        final int[] finished = {0};
        final int[] errors = {0};

        for (Order order : selectedOrders) {
            String orderId = order.getId();
            if (orderId == null || orderId.isEmpty()) orderId = order.getOrderId();

            if (orderId == null || orderId.isEmpty()) {
                finished[0]++;
                errors[0]++;
                if (finished[0] >= total) finalizeTransfer(fromTable, targetTable, errors[0]);
                continue;
            }

            Map<String, Object> updates = new HashMap<>();
            updates. put("tableNumber", targetTable.getTableNumber());
            if (targetTable.getId() != null) updates.put("tableId", targetTable.getId());

            orderRepository.updateOrder(orderId, updates, new OrderRepository.RepositoryCallback<Order>() {
                @Override
                public void onSuccess(Order result) {
                    synchronized (finished) {
                        finished[0]++;
                        if (finished[0] >= total) {
                            finalizeTransfer(fromTable, targetTable, errors[0]);
                        }
                    }
                }

                @Override
                public void onError(String message) {
                    synchronized (finished) {
                        finished[0]++;
                        errors[0]++;
                        if (finished[0] >= total) {
                            finalizeTransfer(fromTable, targetTable, errors[0]);
                        }
                    }
                }
            });
        }
    }

    /**
     * ✅ BƯỚC 5: Cập nhật trạng thái bàn sau khi chuyển
     */
    private void finalizeTransfer(TableItem fromTable, TableItem targetTable, int errorCount) {
        host.runOnUiThread(() -> {
            // Cập nhật bàn đích thành occupied
            tableRepository.updateTableStatus(targetTable.getId(), "occupied", new TableRepository.RepositoryCallback<TableItem>() {
                @Override
                public void onSuccess(TableItem updatedTarget) {
                    // Kiểm tra xem bàn nguồn còn hóa đơn không
                    orderRepository.getOrdersByTableNumber(fromTable.getTableNumber(), null, new OrderRepository.RepositoryCallback<List<Order>>() {
                        @Override
                        public void onSuccess(List<Order> remainingOrders) {
                            String newStatus = (remainingOrders == null || remainingOrders.isEmpty())
                                    ? "available"
                                    : "occupied";

                            tableRepository.updateTableStatus(fromTable.getId(), newStatus, new TableRepository.RepositoryCallback<TableItem>() {
                                @Override
                                public void onSuccess(TableItem updatedSource) {
                                    host.runOnUiThread(() -> {
                                        progressBar.setVisibility(View. GONE);

                                        String msg = errorCount == 0
                                                ?  "Chuyển hóa đơn thành công"
                                                : "Chuyển hoàn tất (có " + errorCount + " lỗi)";

                                        Toast.makeText(host, msg, Toast.LENGTH_SHORT).show();

                                        if (host instanceof com.ph48845.datn_qlnh_rmis.ui.MainActivity) {
                                            ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) host).fetchTablesFromServer();
                                        }
                                    });
                                }

                                @Override
                                public void onError(String message) {
                                    host.runOnUiThread(() -> {
                                        progressBar.setVisibility(View.GONE);
                                        Toast.makeText(host, "Chuyển thành công nhưng không cập nhật được bàn nguồn", Toast.LENGTH_SHORT).show();

                                        if (host instanceof com.ph48845.datn_qlnh_rmis. ui.MainActivity) {
                                            ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) host).fetchTablesFromServer();
                                        }
                                    });
                                }
                            });
                        }

                        @Override
                        public void onError(String message) {
                            // Fallback:  set bàn nguồn về available
                            tableRepository.updateTableStatus(fromTable.getId(), "available", null);

                            host.runOnUiThread(() -> {
                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(host, "Chuyển thành công", Toast.LENGTH_SHORT).show();

                                if (host instanceof com.ph48845.datn_qlnh_rmis.ui.MainActivity) {
                                    ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) host).fetchTablesFromServer();
                                }
                            });
                        }
                    });
                }

                @Override
                public void onError(String message) {
                    host.runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(host, "Không thể cập nhật bàn đích:  " + message, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }

    /**
     * ✅ Chuyển bàn đặt trước (không cần chọn hóa đơn)
     */
    private void showTableSelectionForReservation(TableItem fromTable) {
        tableRepository.getAllTables(new TableRepository.RepositoryCallback<List<TableItem>>() {
            @Override
            public void onSuccess(List<TableItem> result) {
                host.runOnUiThread(() -> {
                    List<TableItem> candidates = new ArrayList<>();

                    for (TableItem t : result) {
                        if (t == null) continue;
                        if (fromTable.getId() != null && fromTable.getId().equals(t.getId())) continue;

                        // Bàn đặt trước chỉ chuyển sang bàn AVAILABLE
                        if (t.getStatus() == TableItem.Status. AVAILABLE) {
                            candidates.add(t);
                        }
                    }

                    if (candidates.isEmpty()) {
                        Toast.makeText(host, "Không có bàn trống để chuyển đặt trước.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Collections.sort(candidates, (a, b) -> Integer.compare(a.getTableNumber(), b.getTableNumber()));

                    List<String> labels = new ArrayList<>();
                    for (TableItem t : candidates) {
                        labels.add("Bàn " + t.getTableNumber() + " - " + safeStatusLabel(t));
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(host, android.R.layout.simple_list_item_1, labels);
                    new AlertDialog.Builder(host)
                            .setTitle("Chọn bàn để chuyển đặt trước")
                            .setAdapter(adapter, (dialog, which) -> {
                                TableItem target = candidates.get(which);
                                performTransferReservation(fromTable, target);
                            })
                            . setNegativeButton("Hủy", null)
                            .show();
                });
            }

            @Override
            public void onError(String message) {
                host.runOnUiThread(() -> Toast.makeText(host, "Lỗi tải bàn: " + message, Toast.LENGTH_LONG).show());
            }
        });
    }

    /**
     * ✅ Thực hiện chuyển đặt trước
     */
    private void performTransferReservation(TableItem fromTable, TableItem targetTable) {
        String rName = fromTable.getReservationName();
        String rPhone = fromTable.getReservationPhone();
        String rAt = fromTable.getReservationAt();

        if ((rName == null || rName.trim().isEmpty()) &&
                (rPhone == null || rPhone.trim().isEmpty()) &&
                (rAt == null || rAt.trim().isEmpty())) {
            showTransferReservationDialog(fromTable, targetTable);
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        Map<String, Object> targetBody = new HashMap<>();
        targetBody.put("status", "reserved");
        targetBody.put("reservationName", rName);
        targetBody.put("reservationPhone", rPhone);
        targetBody.put("reservationAt", rAt);

        tableRepository.updateTable(targetTable.getId(), targetBody, new TableRepository.RepositoryCallback<TableItem>() {
            @Override
            public void onSuccess(TableItem updatedTarget) {
                Map<String, Object> clearSource = new HashMap<>();
                clearSource.put("status", "available");
                clearSource.put("reservationName", "");
                clearSource.put("reservationPhone", "");
                clearSource.put("reservationAt", "");

                tableRepository.updateTable(fromTable.getId(), clearSource, new TableRepository.RepositoryCallback<TableItem>() {
                    @Override
                    public void onSuccess(TableItem updatedSource) {
                        host.runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(host, "Chuyển đặt trước thành công", Toast.LENGTH_SHORT).show();

                            if (host instanceof com.ph48845.datn_qlnh_rmis.ui.MainActivity) {
                                ((com.ph48845.datn_qlnh_rmis.ui.MainActivity) host).fetchTablesFromServer();
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        // Rollback
                        Map<String, Object> rollback = new HashMap<>();
                        rollback.put("status", "available");
                        tableRepository.updateTable(targetTable.getId(), rollback, null);

                        host.runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(host, "Không thể chuyển đặt trước:  " + message, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            }

            @Override
            public void onError(String message) {
                host.runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(host, "Không thể đặt bàn đích: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // ===== HELPER METHODS =====

    private int parseFloor(String loc) {
        if (loc == null) return 1;
        try {
            java.util.regex. Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(loc);
            if (m. find()) return Integer.parseInt(m.group(1));
        } catch (Exception ignored) {}
        return 1;
    }

    private String safeStatusLabel(TableItem t) {
        try {
            String s = t.getStatusDisplay();
            if (s != null && !s.isEmpty()) return s;
        } catch (Exception ignored) {}
        if (t.getStatus() == TableItem.Status.AVAILABLE) return "Khả dụng";
        if (t.getStatus() == TableItem.Status.OCCUPIED) return "Đã có khách";
        if (t.getStatus() == TableItem.Status.RESERVED) return "Đã được đặt trước";
        return "";
    }

    public void showTransferReservationDialog(final TableItem fromTable, final TableItem targetTable) {
        LayoutInflater inflater = LayoutInflater.from(host);
        View layout = inflater.inflate(R. layout.dialog_reservation, null);

        final EditText etName = layout.findViewById(R. id.et_res_name);
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
                selectedCal.set(Calendar.YEAR, y);
                selectedCal.set(Calendar.MONTH, m);
                selectedCal.set(Calendar.DAY_OF_MONTH, d);
                etDate.setText(dateFormat.format(selectedCal.getTime()));
            }, selectedCal.get(Calendar.YEAR), selectedCal.get(Calendar.MONTH), selectedCal.get(Calendar. DAY_OF_MONTH)).show();
        });

        etTime.setFocusable(false);
        etTime.setClickable(true);
        etTime.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) host.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            new TimePickerDialog(host, (view, h, m) -> {
                selectedCal. set(Calendar.HOUR_OF_DAY, h);
                selectedCal.set(Calendar.MINUTE, m);
                selectedCal.set(Calendar. SECOND, 0);
                etTime.setText(timeFormat.format(selectedCal.getTime()));
            }, selectedCal.get(Calendar.HOUR_OF_DAY), selectedCal.get(Calendar.MINUTE), true).show();
        });

        AlertDialog dialog = new AlertDialog. Builder(host)
                .setTitle("Nhập thông tin đặt trước để chuyển")
                .setView(layout)
                .setPositiveButton("Chuyển", null)
                .setNegativeButton("Hủy", (d, w) -> d.dismiss())
                .create();

        dialog.setOnShowListener(dlg -> {
            dialog.getButton(AlertDialog. BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = etName.getText().toString().trim();
                String phone = etPhone.getText().toString().trim();
                String date = etDate.getText().toString().trim();
                String time = etTime.getText().toString().trim();

                if (name.isEmpty() || phone.isEmpty() || date.isEmpty() || time.isEmpty()) {
                    Toast.makeText(host, "Vui lòng nhập đầy đủ", Toast.LENGTH_SHORT).show();
                    return;
                }

                dialog.dismiss();
                performTransferReservation(fromTable, targetTable);
            });
        });

        dialog.show();
    }
}