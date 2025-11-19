package com.ph48845.datn_qlnh_rmis.ui.thungan;

import android.util.Log;

import androidx.lifecycle.ViewModel;

import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.repository.OrderRepository;
import com.ph48845.datn_qlnh_rmis.data.repository.TableRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ViewModel cho màn hình Thu Ngân.
 * Quản lý việc lấy danh sách bàn đang hoạt động và xác định trạng thái phục vụ.
 */
public class ThuNganViewModel extends ViewModel {

    private static final String TAG = "ThuNganViewModel";
    private TableRepository tableRepository;
    private OrderRepository orderRepository;

    public ThuNganViewModel() {
        tableRepository = new TableRepository();
        orderRepository = new OrderRepository();
    }

    /**
     * Callback interface để trả về kết quả
     */
    public interface DataCallback {
        void onTablesLoaded(List<TableItem> floor1Tables, List<TableItem> floor2Tables);
        void onError(String message);
    }

    /**
     * Lấy danh sách bàn đang hoạt động và xác định trạng thái phục vụ.
     * Chỉ trả về các bàn có status: OCCUPIED, PENDING_PAYMENT, FINISH_SERVE.
     */
    public void loadActiveTables(DataCallback callback) {
        tableRepository.getAllTables(new TableRepository.RepositoryCallback<List<TableItem>>() {
            @Override
            public void onSuccess(List<TableItem> allTables) {
                if (allTables == null || allTables.isEmpty()) {
                    callback.onTablesLoaded(new ArrayList<>(), new ArrayList<>());
                    return;
                }

                // Lọc chỉ bàn đang hoạt động
                List<TableItem> activeTables = new ArrayList<>();
                for (TableItem table : allTables) {
                    if (table == null) continue;
                    TableItem.Status status = table.getStatus();
                    if (status == TableItem.Status.OCCUPIED ||
                        status == TableItem.Status.PENDING_PAYMENT ||
                        status == TableItem.Status.FINISH_SERVE) {
                        activeTables.add(table);
                    }
                }

                // Lấy orders để xác định trạng thái phục vụ
                loadOrdersAndDetermineServingStatus(activeTables, callback);
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Error loading tables: " + message);
                callback.onError(message);
            }
        });
    }

    /**
     * Lấy orders và xác định trạng thái phục vụ cho từng bàn.
     * Nếu tất cả items trong order có status "done" -> "Đã phục vụ đủ món"
     * Ngược lại -> "Đang phục vụ lên món"
     */
    private void loadOrdersAndDetermineServingStatus(List<TableItem> activeTables, DataCallback callback) {
        if (activeTables.isEmpty()) {
            // Phân chia theo tầng
            splitByFloor(activeTables, callback);
            return;
        }

        // Lấy tất cả orders (không filter theo tableNumber)
        orderRepository.getOrdersByTableNumber(null, null, new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> allOrders) {
                // Tạo map: tableNumber -> List<Order>
                Map<Integer, List<Order>> ordersByTable = new HashMap<>();
                if (allOrders != null) {
                    for (Order order : allOrders) {
                        if (order == null) continue;
                        int tableNum = order.getTableNumber();
                        if (!ordersByTable.containsKey(tableNum)) {
                            ordersByTable.put(tableNum, new ArrayList<>());
                        }
                        ordersByTable.get(tableNum).add(order);
                    }
                }

                // Xác định trạng thái phục vụ cho từng bàn
                for (TableItem table : activeTables) {
                    List<Order> tableOrders = ordersByTable.get(table.getTableNumber());
                    boolean allServed = determineIfAllServed(tableOrders);
                    
                    // Nếu tất cả đã phục vụ xong, set status thành FINISH_SERVE (nếu chưa phải)
                    if (allServed && table.getStatus() != TableItem.Status.FINISH_SERVE) {
                        // Có thể cập nhật status trên server nếu cần, nhưng ở đây chỉ dùng để hiển thị
                        // Giữ nguyên status hiện tại, adapter sẽ dựa vào orders để quyết định màu
                    }
                }

                // Phân chia theo tầng
                splitByFloor(activeTables, callback);
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Error loading orders: " + message + " - proceeding without serving status");
                // Vẫn hiển thị bàn nhưng không có thông tin trạng thái phục vụ chi tiết
                splitByFloor(activeTables, callback);
            }
        });
    }

    /**
     * Xác định xem tất cả món đã được phục vụ chưa.
     * Trả về true nếu tất cả items trong orders có status "done" hoặc không có orders.
     */
    private boolean determineIfAllServed(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return false; // Không có order thì chưa phục vụ
        }

        for (Order order : orders) {
            if (order == null || order.getItems() == null) continue;
            
            // Normalize items trước khi kiểm tra
            order.normalizeItems();
            
            for (Order.OrderItem item : order.getItems()) {
                if (item == null) continue;
                String status = item.getStatus();
                // Nếu có item chưa "done" thì chưa phục vụ xong
                if (status == null || !status.toLowerCase().contains("done")) {
                    return false;
                }
            }
        }
        return true; // Tất cả items đều "done"
    }

    /**
     * Phân chia danh sách bàn theo tầng dựa trên location.
     */
    private void splitByFloor(List<TableItem> tables, DataCallback callback) {
        List<TableItem> floor1 = new ArrayList<>();
        List<TableItem> floor2 = new ArrayList<>();

        for (TableItem table : tables) {
            if (table == null) continue;
            int floor = parseFloorFromLocation(table.getLocation());
            if (floor == 2) {
                floor2.add(table);
            } else {
                floor1.add(table);
            }
        }

        // Sắp xếp theo số bàn
        floor1.sort((a, b) -> Integer.compare(a.getTableNumber(), b.getTableNumber()));
        floor2.sort((a, b) -> Integer.compare(a.getTableNumber(), b.getTableNumber()));

        callback.onTablesLoaded(floor1, floor2);
    }

    /**
     * Parse số tầng từ location string.
     * Tìm số đầu tiên trong chuỗi, nếu không tìm thấy trả về 1.
     */
    private int parseFloorFromLocation(String location) {
        if (location == null || location.trim().isEmpty()) return 1;
        try {
            String lower = location.toLowerCase();
            // Tìm số đầu tiên
            for (int i = 0; i < lower.length(); i++) {
                char c = lower.charAt(i);
                if (Character.isDigit(c)) {
                    int num = Character.getNumericValue(c);
                    if (num == 2) return 2;
                    if (num > 0) return num;
                }
            }
        } catch (Exception ignored) {}
        return 1; // Mặc định tầng 1
    }
}
