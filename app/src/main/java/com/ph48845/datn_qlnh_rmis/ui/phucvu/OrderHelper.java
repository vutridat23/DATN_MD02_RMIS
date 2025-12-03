package com.ph48845.datn_qlnh_rmis.ui.phucvu;



import android.app.AlertDialog;
import android.content.Context;
import android.widget.Toast;

import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.Order.OrderItem;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.ui.phucvu.adapter.MenuAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Static helpers for merging/creating orders and showing confirmation dialog.
 */
public class OrderHelper {

    public interface OrderCallback {
        void onSuccess();
        void onError(String message);
    }

    public interface ConfirmCallback {
        void onResult(boolean confirmed);
    }

    public static void showConfirmationDialog(Context ctx, Map<String,Integer> addQtyMap, Map<String,String> notesMap, MenuAdapter menuAdapter, ConfirmCallback cb) {
        if (addQtyMap == null || addQtyMap.isEmpty()) {
            Toast.makeText(ctx, "Chưa chọn món để thêm", Toast.LENGTH_SHORT).show();
            cb.onResult(false);
            return;
        }
        StringBuilder sb = new StringBuilder();
        double total = 0.0;
        for (Map.Entry<String, Integer> e : addQtyMap.entrySet()) {
            String id = e.getKey();
            int qty = e.getValue();
            MenuItem mi = menuAdapter != null ? menuAdapter.findById(id) : null;
            String name = mi != null ? mi.getName() : ("(id:" + id + ")");
            double price = mi != null ? mi.getPrice() : 0.0;
            String note = notesMap != null ? notesMap.get(id) : null;
            sb.append(name).append(" x").append(qty);
            if (price > 0) sb.append(" - ").append(String.format("%,.0f VND", price)).append(" each");
            if (note != null && !note.isEmpty()) sb.append("\nGhi chú: ").append(note);
            sb.append("\n\n");
            total += price * qty;
        }
        sb.append("Tổng: ").append(String.format("%,.0f VND", total)).append("\n\n");
        sb.append("Bạn có muốn xác nhận lên đơn?");

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle("Xác nhận lên đơn");
        builder.setMessage(sb.toString());
        builder.setPositiveButton("Xác nhận", (d, w) -> cb.onResult(true));
        builder.setNegativeButton("Hủy", (d, w) -> cb.onResult(false));
        builder.show();
    }

    public static Order pickTargetOrderForMerge(List<Order> orders, int tableNumber) {
        if (orders == null || orders.isEmpty()) return null;
        Order targetOrder = null;
        for (Order o : orders) {
            if (o == null) continue;
            if (o.getTableNumber() != tableNumber) continue;
            String s = o.getOrderStatus() != null ? o.getOrderStatus().toLowerCase() : "";
            if (s.contains("pending") || s.contains("preparing") || s.contains("open") || s.contains("unpaid") || s.isEmpty()) {
                targetOrder = o;
                break;
            }
        }
        if (targetOrder == null) {
            Order newest = null;
            for (Order o : orders) {
                if (o == null) continue;
                if (o.getTableNumber() != tableNumber) continue;
                if (newest == null) newest = o;
                else {
                    String a = newest.getCreatedAt() != null ? newest.getCreatedAt() : "";
                    String b = o.getCreatedAt() != null ? o.getCreatedAt() : "";
                    if (b.compareTo(a) > 0) newest = o;
                }
            }
            targetOrder = newest;
        }
        return targetOrder;
    }

    public static void mergeIntoExistingOrderAndUpdate(Order existing,
                                                       Map<String,Integer> addQtyMap,
                                                       Map<String,String> notesMap,
                                                       MenuAdapter menuAdapter,
                                                       OrderRepository orderRepository,
                                                       OrderCallback cb) {
        if (existing == null) {
            cb.onError("Existing order is null");
            return;
        }
        Map<String, OrderItem> mergedMap = new HashMap<>();
        if (existing.getItems() != null) {
            for (OrderItem oi : existing.getItems()) {
                if (oi == null) continue;
                String mid = oi.getMenuItemId();
                if (mid == null) continue;
                try {
                    OrderItem copy = new OrderItem(mid, oi.getMenuItemName() != null && !oi.getMenuItemName().isEmpty() ? oi.getMenuItemName() : oi.getName(), oi.getQuantity(), oi.getPrice());
                    copy.setMenuItemId(mid);
                    copy.setMenuItemRaw(mid);
                    copy.setImageUrl(oi.getImageUrl());
                    copy.setStatus(oi.getStatus());
                    try {
                        String serverNote = oi.getNote();
                        if (serverNote != null && !serverNote.isEmpty()) copy.setNote(serverNote);
                    } catch (Exception ignored) {}
                    mergedMap.put(mid, copy);
                } catch (Exception ex) {
                    mergedMap.put(mid, oi);
                }
            }
        }

        for (Map.Entry<String, Integer> e : addQtyMap.entrySet()) {
            String menuId = e.getKey();
            int addQty = e.getValue();
            if (menuId == null || addQty <= 0) continue;

            OrderItem existingOi = mergedMap.get(menuId);
            if (existingOi != null) {
                existingOi.setQuantity(existingOi.getQuantity() + addQty);
                String note = notesMap.get(menuId);
                if (note != null && !note.isEmpty()) existingOi.setNote(note);
            } else {
                MenuItem mi = menuAdapter != null ? menuAdapter.findById(menuId) : null;
                String name = mi != null ? mi.getName() : "";
                double price = mi != null ? mi.getPrice() : 0.0;
                String imageUrl = mi != null ? mi.getImageUrl() : "";

                OrderItem newOi;
                try {
                    newOi = new OrderItem(menuId, name, addQty, price);
                } catch (Exception ex) {
                    newOi = new OrderItem();
                    newOi.setMenuItemId(menuId);
                    newOi.setName(name);
                    newOi.setQuantity(addQty);
                    newOi.setPrice(price);
                }
                newOi.setMenuItemRaw(menuId);
                newOi.setMenuItemId(menuId);
                newOi.setMenuItemName(name);
                newOi.setImageUrl(imageUrl);
                newOi.setStatus("pending");
                String note = notesMap.get(menuId);
                if (note != null && !note.isEmpty()) newOi.setNote(note);
                mergedMap.put(menuId, newOi);
            }
        }

        List<OrderItem> mergedList = new ArrayList<>(mergedMap.values());
        double total = 0.0;
        for (OrderItem oi : mergedList) total += oi.getPrice() * oi.getQuantity();

        Map<String, Object> updates = new HashMap<>();
        updates.put("items", mergedList);
        updates.put("totalAmount", total);
        updates.put("finalAmount", total);

        orderRepository.updateOrder(existing.getId(), updates, new OrderRepository.RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order result) {
                cb.onSuccess();
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }

    public static void createNewOrderFromAddMap(Map<String,Integer> addQtyMap,
                                                Map<String,String> notesMap,
                                                MenuAdapter menuAdapter,
                                                int tableNumber,
                                                String fakeServerId,
                                                String fakeCashierId,
                                                OrderRepository orderRepository,
                                                OrderCallback cb) {
        List<OrderItem> items = new ArrayList<>();
        double total = 0.0;
        for (Map.Entry<String, Integer> e : addQtyMap.entrySet()) {
            String menuId = e.getKey();
            int qty = e.getValue();
            MenuItem mi = menuAdapter != null ? menuAdapter.findById(menuId) : null;
            String name = mi != null ? mi.getName() : "";
            double price = mi != null ? mi.getPrice() : 0.0;
            String imageUrl = mi != null ? mi.getImageUrl() : "";

            OrderItem oi;
            try {
                oi = new OrderItem(menuId, name, qty, price);
            } catch (Exception ex) {
                oi = new OrderItem();
                oi.setMenuItemId(menuId);
                oi.setName(name);
                oi.setQuantity(qty);
                oi.setPrice(price);
            }
            oi.setMenuItemRaw(menuId);
            oi.setMenuItemId(menuId);
            oi.setMenuItemName(name);
            oi.setImageUrl(imageUrl);
            oi.setStatus("pending");

            String note = notesMap.get(menuId);
            if (note != null && !note.isEmpty()) oi.setNote(note);

            items.add(oi);
            total += price * qty;
        }

        Order order = new Order();
        order.setTableNumber(tableNumber);
        order.setItems(items);
        order.setTotalAmount(total);
        order.setDiscount(0);
        order.setFinalAmount(total);
        order.setPaidAmount(0);
        order.setChange(0);
        order.setServerId(fakeServerId);
        order.setCashierId(fakeCashierId);
        order.setPaymentMethod("Tiền mặt");
        order.setOrderStatus("pending");

        orderRepository.createOrder(order, new OrderRepository.RepositoryCallback<Order>() {
            @Override
            public void onSuccess(Order result) {
                cb.onSuccess();
            }

            @Override
            public void onError(String message) {
                cb.onError(message);
            }
        });
    }
}