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
        Log.d(TAG, "loadActiveTables: Starting to load tables");
        tableRepository.getAllTables(new TableRepository.RepositoryCallback<List<TableItem>>() {
            @Override
            public void onSuccess(List<TableItem> allTables) {
                Log.d(TAG, "loadActiveTables: Received " + (allTables != null ? allTables.size() : 0) + " tables from DB");
                if (allTables == null || allTables.isEmpty()) {
                    Log.w(TAG, "loadActiveTables: No tables found in DB");
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
                        Log.d(TAG, "loadActiveTables: Added table " + table.getTableNumber() + " with status " + status);
                    } else {
                        Log.d(TAG, "loadActiveTables: Skipped table " + table.getTableNumber() + " with status " + status);
                    }
                }

                Log.d(TAG, "loadActiveTables: After status filter, " + activeTables.size() + " active tables remain");
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
     * CHỈ HIỂN THỊ CÁC BÀN CÓ HÓA ĐƠN CHƯA THANH TOÁN
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
                Log.d(TAG, "loadOrdersAndDetermineServingStatus: Received " + (allOrders != null ? allOrders.size() : 0) + " orders from API");
                
                // Tạo map: tableNumber -> List<Order> (tất cả orders, không phân biệt đã thanh toán hay chưa)
                // Để đảm bảo hiển thị tất cả các bàn có orders
                Map<Integer, List<Order>> allOrdersByTable = new HashMap<>();
                if (allOrders != null) {
                    for (Order order : allOrders) {
                        if (order == null) continue;
                        int tableNum = order.getTableNumber();
                        if (!allOrdersByTable.containsKey(tableNum)) {
                            allOrdersByTable.put(tableNum, new ArrayList<>());
                        }
                        allOrdersByTable.get(tableNum).add(order);
                    }
                }
                
                // Lọc chỉ lấy các orders chưa thanh toán để kiểm tra
                List<Order> unpaidOrders = filterUnpaidOrders(allOrders);
                Log.d(TAG, "loadOrdersAndDetermineServingStatus: After filtering, " + unpaidOrders.size() + " unpaid orders remain");
                
                // Tạo map: tableNumber -> List<Order> (chỉ các đơn chưa thanh toán)
                Map<Integer, List<Order>> unpaidOrdersByTable = new HashMap<>();
                if (unpaidOrders != null) {
                    for (Order order : unpaidOrders) {
                        if (order == null) continue;
                        int tableNum = order.getTableNumber();
                        if (!unpaidOrdersByTable.containsKey(tableNum)) {
                            unpaidOrdersByTable.put(tableNum, new ArrayList<>());
                        }
                        unpaidOrdersByTable.get(tableNum).add(order);
                        Log.d(TAG, "loadOrdersAndDetermineServingStatus: Added unpaid order for table " + tableNum);
                    }
                }

                Log.d(TAG, "loadOrdersAndDetermineServingStatus: Orders found for " + allOrdersByTable.size() + " tables (all orders)");
                Log.d(TAG, "loadOrdersAndDetermineServingStatus: Unpaid orders found for " + unpaidOrdersByTable.size() + " tables");
                Log.d(TAG, "loadOrdersAndDetermineServingStatus: Checking " + activeTables.size() + " active tables");

                // Hiển thị các bàn có orders (ưu tiên các bàn có orders chưa thanh toán)
                // Nếu bàn có orders (dù đã thanh toán hay chưa) thì vẫn hiển thị
                List<TableItem> tablesWithOrders = new ArrayList<>();
                for (TableItem table : activeTables) {
                    List<Order> allTableOrders = allOrdersByTable.get(table.getTableNumber());
                    List<Order> unpaidTableOrders = unpaidOrdersByTable.get(table.getTableNumber());
                    
                    // Hiển thị bàn nếu có ít nhất một order (dù đã thanh toán hay chưa)
                    if (allTableOrders != null && !allTableOrders.isEmpty()) {
                        // Sử dụng unpaid orders để xác định trạng thái phục vụ
                        List<Order> ordersForStatus = unpaidTableOrders != null && !unpaidTableOrders.isEmpty() 
                            ? unpaidTableOrders : allTableOrders;
                        
                        boolean allServed = determineIfAllServed(ordersForStatus);
                        
                        // Nếu tất cả đã phục vụ xong, set status thành FINISH_SERVE (nếu chưa phải)
                        if (allServed && table.getStatus() != TableItem.Status.FINISH_SERVE) {
                            // Có thể cập nhật status trên server nếu cần, nhưng ở đây chỉ dùng để hiển thị
                            // Giữ nguyên status hiện tại, adapter sẽ dựa vào orders để quyết định màu
                        }
                        
                        tablesWithOrders.add(table);
                        int unpaidCount = unpaidTableOrders != null ? unpaidTableOrders.size() : 0;
                        Log.d(TAG, "loadOrdersAndDetermineServingStatus: Added table " + table.getTableNumber() + 
                              " with " + allTableOrders.size() + " total orders (" + unpaidCount + " unpaid)");
                    } else {
                        Log.d(TAG, "loadOrdersAndDetermineServingStatus: Skipped table " + table.getTableNumber() + " - no orders at all");
                    }
                }

                Log.d(TAG, "loadOrdersAndDetermineServingStatus: Final result - " + tablesWithOrders.size() + " tables with orders");
                // Phân chia theo tầng (các bàn có orders)
                splitByFloor(tablesWithOrders, callback);
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "Error loading orders: " + message + " - proceeding without serving status");
                // Nếu lỗi, không hiển thị bàn nào (vì không thể xác định được bàn nào có hóa đơn)
                splitByFloor(new ArrayList<>(), callback);
            }
        });
    }

    /**
     * Lọc bỏ các đơn đã thanh toán, chỉ giữ lại các đơn chưa thanh toán
     */
    private List<Order> filterUnpaidOrders(List<Order> allOrders) {
        List<Order> unpaidOrders = new ArrayList<>();
        if (allOrders == null || allOrders.isEmpty()) {
            Log.d(TAG, "filterUnpaidOrders: No orders to filter");
            return unpaidOrders;
        }

        for (Order order : allOrders) {
            if (order == null) continue;

            // Kiểm tra nếu đã thanh toán
            boolean isPaid = false;

            // Kiểm tra trường paid
            if (order.isPaid()) {
                isPaid = true;
                Log.d(TAG, "filterUnpaidOrders: Order " + order.getId() + " marked as paid (isPaid=true)");
            }

            // Kiểm tra paidAt
            String paidAt = order.getPaidAt();
            if (paidAt != null && !paidAt.trim().isEmpty()) {
                isPaid = true;
                Log.d(TAG, "filterUnpaidOrders: Order " + order.getId() + " marked as paid (paidAt=" + paidAt + ")");
            }

            // Kiểm tra orderStatus
            String orderStatus = order.getOrderStatus();
            if (orderStatus != null) {
                String status = orderStatus.toLowerCase().trim();
                if (status.equals("paid") || status.contains("paid") ||
                    status.equals("completed") || status.contains("completed") ||
                    status.contains("đã thanh toán") || status.contains("hoàn thành")) {
                    isPaid = true;
                    Log.d(TAG, "filterUnpaidOrders: Order " + order.getId() + " marked as paid (orderStatus=" + orderStatus + ")");
                }
            }

            // Nếu không có dấu hiệu thanh toán, thêm vào danh sách chưa thanh toán
            if (!isPaid) {
                unpaidOrders.add(order);
                Log.d(TAG, "filterUnpaidOrders: Added unpaid order " + order.getId() + " for table " + order.getTableNumber());
            }
        }

        Log.d(TAG, "filterUnpaidOrders: Returning " + unpaidOrders.size() + " unpaid orders out of " + allOrders.size() + " total");
        return unpaidOrders;
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
