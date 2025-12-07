package com.ph48845.datn_qlnh_rmis.ui.table;

import android.app.AlertDialog;
import android.widget.Toast;
import android.widget.ProgressBar;

import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;
import com.ph48845.datn_qlnh_rmis.ui.MainActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MergeManager: xử lý luồng gộp nhiều bàn vào 1 bàn đích.
 * Thay đổi: khi gộp bàn thành công thì KHÔNG xóa các order cũ — các order cũ vẫn giữ nguyên.
 */
public class MergeManager {

    private final android.app.Activity host;
    private final TableRepository tableRepository;
    private final OrderRepository orderRepository;
    private final ProgressBar progressBar;

    public MergeManager(android.app.Activity host, TableRepository tableRepository, OrderRepository orderRepository, ProgressBar progressBar) {
        this.host = host;
        this.tableRepository = tableRepository;
        this.orderRepository = orderRepository;
        this.progressBar = progressBar;
    }

    // Hiển thị dialog chọn bàn để gộp
    public void showMergeDialog(TableItem targetTable) {
        tableRepository.getAllTables(new TableRepository.RepositoryCallback<List<TableItem>>() {
            @Override
            public void onSuccess(List<TableItem> result) {
                host.runOnUiThread(() -> {
                    List<TableItem> candidates = new ArrayList<>();
                    for (TableItem t : result) {
                        if (t == null) continue;
                        if (targetTable.getId() != null && targetTable.getId().equals(t.getId())) continue;

                        TableItem.Status st = null;
                        try { st = t.getStatus(); } catch (Exception ignored) {}
                        if (st == TableItem.Status.OCCUPIED || st == TableItem.Status.PENDING_PAYMENT) {
                            candidates.add(t);
                        }
                    }

                    if (candidates.isEmpty()) {
                        Toast.makeText(host, "Không có bàn có khách để gộp.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Collections.sort(candidates, (a, b) -> Integer.compare(a.getTableNumber(), b.getTableNumber()));

                    List<String> labels = new ArrayList<>();
                    for (TableItem t : candidates) {
                        labels.add("Bàn " + t.getTableNumber() + " - " + safeStatusLabel(t));
                    }

                    boolean[] checked = new boolean[candidates.size()];

                    AlertDialog.Builder builder = new AlertDialog.Builder(host);
                    builder.setTitle("Chọn bàn để gộp vào bàn " + targetTable.getTableNumber());
                    builder.setMultiChoiceItems(labels.toArray(new String[0]), checked, (dialog, which, isChecked) -> checked[which] = isChecked);
                    builder.setPositiveButton("Gộp", (dialog, which) -> {
                        List<TableItem> selected = new ArrayList<>();
                        for (int i = 0; i < checked.length; i++) if (checked[i]) selected.add(candidates.get(i));

                        if (selected.isEmpty()) {
                            Toast.makeText(host, "Vui lòng chọn ít nhất 1 bàn", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        performMergeMultipleIntoTarget(targetTable, selected);
                    });
                    builder.setNegativeButton("Hủy", null);
                    builder.show();
                });
            }

            @Override
            public void onError(String message) {
                host.runOnUiThread(() -> Toast.makeText(host, "Lỗi tải bàn: " + message, Toast.LENGTH_LONG).show());
            }
        });
    }

    private String safeStatusLabel(TableItem t) {
        if (t == null) return "Trống";
        try {
            String disp = t.getStatusDisplay();
            if (disp != null && !disp.trim().isEmpty()) return disp.trim();
        } catch (Exception ignored) {}
        try {
            TableItem.Status st = t.getStatus();
            if (st != null) {
                switch (st) {
                    case OCCUPIED: return "Đã có khách";
                    case RESERVED: return "Đã được đặt trước";
                    case PENDING_PAYMENT: return "Chờ thanh toán";
                    case FINISH_SERVE: return "Đã phục vụ";
                    case AVAILABLE:
                    case EMPTY:
                    default: return "Trống";
                }
            }
        } catch (Exception ignored) {}
        return "Trống";
    }

    public void performMergeMultipleIntoTarget(final TableItem targetTable, final List<TableItem> sources) {
        if (targetTable == null || sources == null || sources.isEmpty()) return;

        if (progressBar != null) progressBar.setVisibility(android.view.View.VISIBLE);

        final int total = sources.size();
        final int[] finished = {0};

        for (TableItem src : sources) {
            orderRepository.moveOrdersForTable(src.getTableNumber(), targetTable.getTableNumber(), new OrderRepository.RepositoryCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    synchronized (finished) {
                        finished[0]++;
                        if (finished[0] >= total) consolidateOrders(targetTable, sources);
                    }
                }

                @Override
                public void onError(String message) {
                    synchronized (finished) {
                        finished[0]++;
                        if (finished[0] >= total) consolidateOrders(targetTable, sources);
                    }
                }
            });
        }
    }

    private void consolidateOrders(final TableItem targetTable, final List<TableItem> sources) {
        orderRepository.getOrdersByTableNumber(targetTable.getTableNumber(), null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> orders) {
                host.runOnUiThread(() -> {
                    if (orders == null || orders.isEmpty()) {
                        updateTableStatuses(targetTable, sources, false);
                        return;
                    }

                    final Order primary = orders.get(0);
                    Map<String, Order.OrderItem> mergedMap = new HashMap<>();

                    for (Order o : orders) {
                        if (o == null || o.getItems() == null) continue;
                        for (Order.OrderItem oi : o.getItems()) {
                            if (oi == null) continue;

                            String key = (oi.getMenuItemId() != null && !oi.getMenuItemId().isEmpty())
                                    ? oi.getMenuItemId()
                                    : String.valueOf(oi.getMenuItemRaw());

                            Order.OrderItem existing = mergedMap.get(key);
                            if (existing != null) {
                                existing.setQuantity(existing.getQuantity() + oi.getQuantity());
                            } else {
                                mergedMap.put(key, oi);
                            }
                        }
                    }

                    List<Order.OrderItem> mergedItems = new ArrayList<>(mergedMap.values());
                    double totalAmount = 0;
                    for (Order.OrderItem i : mergedItems) totalAmount += i.getPrice() * i.getQuantity();

                    Map<String, Object> updates = new HashMap<>();
                    updates.put("items", mergedItems);
                    updates.put("totalAmount", totalAmount);
                    updates.put("finalAmount", totalAmount);

                    orderRepository.updateOrder(primary.getId(), updates, new OrderRepository.RepositoryCallback<Order>() {
                        @Override
                        public void onSuccess(Order result) {
                            // NEW BEHAVIOR: do NOT delete the other orders. Keep original orders unchanged.
                            // Just update table statuses to reflect there are orders on the table.
                            updateTableStatuses(targetTable, sources, true);
                        }

                        @Override
                        public void onError(String message) {
                            updateTableStatuses(targetTable, sources, false);
                        }
                    });
                });
            }

            @Override
            public void onError(String message) {
                updateTableStatuses(targetTable, sources, false);
            }
        });
    }

    private void updateTableStatuses(final TableItem target, final List<TableItem> sources, boolean hasOrders) {
        String targetStatus = hasOrders ? "occupied" : "available";
        tableRepository.updateTableStatus(target.getId(), targetStatus, new TableRepository.RepositoryCallback<TableItem>() {
            @Override
            public void onSuccess(TableItem result) {
                if (sources == null) return;
                for (TableItem src : sources) tableRepository.updateTableStatus(src.getId(), "available", null);
                finalizeResult();
            }

            @Override
            public void onError(String message) { finalizeResult(); }
        });
    }

    private void finalizeResult() {
        host.runOnUiThread(() -> {
            if (progressBar != null) progressBar.setVisibility(android.view.View.GONE);
            Toast.makeText(host, "Gộp bàn thành công", Toast.LENGTH_SHORT).show();
            if (host instanceof MainActivity) ((MainActivity) host).fetchTablesFromServer();
        });
    }
}