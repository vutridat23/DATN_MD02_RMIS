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
                int occupiedCount = 0;
                int pendingPaymentCount = 0;
                int finishServeCount = 0;
                int otherStatusCount = 0;
                
                for (TableItem table : allTables) {
                    if (table == null) continue;
                    TableItem.Status status = table.getStatus();
                    if (status == TableItem.Status.OCCUPIED ||
                        status == TableItem.Status.PENDING_PAYMENT ||
                        status == TableItem.Status.FINISH_SERVE) {
                        activeTables.add(table);
                        if (status == TableItem.Status.OCCUPIED) occupiedCount++;
                        else if (status == TableItem.Status.PENDING_PAYMENT) pendingPaymentCount++;
                        else if (status == TableItem.Status.FINISH_SERVE) finishServeCount++;
                        Log.d(TAG, "loadActiveTables: ✅ Added table " + table.getTableNumber() + " with status " + status);
                    } else {
                        otherStatusCount++;
                        if (otherStatusCount <= 5) { // Chỉ log 5 bàn đầu để tránh spam
                            Log.d(TAG, "loadActiveTables: ⚠️ Skipped table " + table.getTableNumber() + " with status " + status);
                        }
                    }
                }

                Log.d(TAG, "loadActiveTables: Summary - Total from DB: " + allTables.size() + 
                      ", OCCUPIED: " + occupiedCount + 
                      ", PENDING_PAYMENT: " + pendingPaymentCount + 
                      ", FINISH_SERVE: " + finishServeCount + 
                      ", Other status: " + otherStatusCount);
                Log.d(TAG, "loadActiveTables: After status filter, " + activeTables.size() + " active tables remain");
                // Lấy orders để xác định trạng thái phục vụ
                // Truyền TẤT CẢ bàn từ DB (không chỉ activeTables) để có thể tìm bàn có orders
                loadOrdersAndDetermineServingStatus(allTables, callback);
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
     * HIỂN THỊ TẤT CẢ CÁC BÀN CÓ HÓA ĐƠN CHƯA THANH TOÁN (không quan tâm status của bàn trong DB)
     * @param allTablesFromDB Tất cả bàn từ DB (có thể có status AVAILABLE, OCCUPIED, etc.)
     */
    private void loadOrdersAndDetermineServingStatus(List<TableItem> allTablesFromDB, DataCallback callback) {
        // KHÔNG return sớm nếu allTablesFromDB rỗng - vẫn cần load orders để tìm các bàn có orders
        Log.d(TAG, "loadOrdersAndDetermineServingStatus: Starting with " + allTablesFromDB.size() + " tables from DB");

        // Lấy TẤT CẢ orders từ server (dùng getAllOrders để đảm bảo lấy đủ dữ liệu)
        orderRepository.getAllOrders(new OrderRepository.RepositoryCallback<List<Order>>() {
            @Override
            public void onSuccess(List<Order> allOrders) {
                Log.d(TAG, "loadOrdersAndDetermineServingStatus: Received " + (allOrders != null ? allOrders.size() : 0) + " orders from API (all tables)");
                
                // Log chi tiết để debug
                if (allOrders != null && !allOrders.isEmpty()) {
                    Log.d(TAG, "loadOrdersAndDetermineServingStatus: Sample orders:");
                    int count = 0;
                    for (Order order : allOrders) {
                        if (order != null && count < 5) {
                            Log.d(TAG, "  Order " + order.getId() + " (Bàn " + order.getTableNumber() + 
                                  "): status = " + order.getOrderStatus() + 
                                  ", paid = " + order.isPaid());
                            count++;
                        }
                    }
                }
                
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
                Log.d(TAG, "loadOrdersAndDetermineServingStatus: Checking " + allTablesFromDB.size() + " tables from DB");

                // Tạo map để tránh duplicate
                Map<Integer, TableItem> tablesToDisplayMap = new HashMap<>();
                
                // Tạo map nhanh: tableNumber -> TableItem từ DB
                Map<Integer, TableItem> tablesFromDBMap = new HashMap<>();
                for (TableItem table : allTablesFromDB) {
                    if (table != null) {
                        tablesFromDBMap.put(table.getTableNumber(), table);
                    }
                }
                
                // BƯỚC 1: Thêm TẤT CẢ các bàn có status OCCUPIED, PENDING_PAYMENT, FINISH_SERVE từ DB
                // (Đây là các bàn đang hoạt động theo status trong DB, không phụ thuộc vào orders)
                for (TableItem table : allTablesFromDB) {
                    if (table == null) continue;
                    TableItem.Status status = table.getStatus();
                    if (status == TableItem.Status.OCCUPIED ||
                        status == TableItem.Status.PENDING_PAYMENT ||
                        status == TableItem.Status.FINISH_SERVE) {
                        tablesToDisplayMap.put(table.getTableNumber(), table);
                        Log.d(TAG, "loadOrdersAndDetermineServingStatus: ✅ Added table " + table.getTableNumber() + 
                              " from DB (status: " + status + ")");
                    }
                }
                
                // BƯỚC 2: Thêm các bàn có orders CHƯA THANH TOÁN (nếu chưa có trong danh sách)
                // (Để đảm bảo hiển thị các bàn có hóa đơn chưa thanh toán, dù status trong DB là AVAILABLE)
                for (Integer tableNum : unpaidOrdersByTable.keySet()) {
                    List<Order> unpaidTableOrders = unpaidOrdersByTable.get(tableNum);
                    
                    if (unpaidTableOrders != null && !unpaidTableOrders.isEmpty()) {
                        // Nếu bàn này chưa có trong danh sách, thêm vào
                        if (!tablesToDisplayMap.containsKey(tableNum)) {
                            TableItem tableFromDB = tablesFromDBMap.get(tableNum);
                            
                            TableItem tableToAdd;
                            if (tableFromDB != null) {
                                // Dùng bàn từ DB (giữ nguyên status, location, capacity)
                                tableToAdd = tableFromDB;
                                Log.d(TAG, "loadOrdersAndDetermineServingStatus: ✅ Added table " + tableNum + 
                                      " from DB (status: " + tableFromDB.getStatus() + ") with " + 
                                      unpaidTableOrders.size() + " unpaid orders");
                            } else {
                                // Tạo TableItem mới cho bàn này (nếu không có trong DB)
                                tableToAdd = new TableItem();
                                tableToAdd.setTableNumber(tableNum);
                                tableToAdd.setStatus(TableItem.Status.OCCUPIED); // Mặc định
                                tableToAdd.setLocation(""); // Mặc định tầng 1
                                tableToAdd.setCapacity(4); // Mặc định
                                Log.d(TAG, "loadOrdersAndDetermineServingStatus: ✅ Created new table " + tableNum + 
                                      " (not in DB) with " + unpaidTableOrders.size() + " unpaid orders");
                            }
                            
                            tablesToDisplayMap.put(tableNum, tableToAdd);
                        } else {
                            Log.d(TAG, "loadOrdersAndDetermineServingStatus: Table " + tableNum + 
                                  " already in list (from DB status), has " + unpaidTableOrders.size() + " unpaid orders");
                        }
                    }
                }

                List<TableItem> tablesToDisplay = new ArrayList<>(tablesToDisplayMap.values());
                Log.d(TAG, "loadOrdersAndDetermineServingStatus: Final result - " + tablesToDisplay.size() + 
                      " tables to display (from DB status + unpaid orders)");
                
                // Phân chia theo tầng
                splitByFloor(tablesToDisplay, callback);
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
     * Chỉ tìm số tầng từ từ khóa "tầng" hoặc "floor", tránh nhầm với số bàn.
     * Ví dụ: "Tầng 1" -> 1, "Tầng 2" -> 2, "Floor 2" -> 2, "Bàn 20" -> 1 (mặc định)
     */
    private int parseFloorFromLocation(String location) {
        if (location == null || location.trim().isEmpty()) return 1;
        try {
            String lower = location.toLowerCase().trim();
            
            // Tìm từ khóa "tầng" hoặc "floor" và lấy số sau đó
            // Pattern: "tầng" hoặc "floor" theo sau bởi số
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(tầng|floor)\\s*(\\d+)");
            java.util.regex.Matcher matcher = pattern.matcher(lower);
            
            if (matcher.find()) {
                String floorNum = matcher.group(2);
                int floor = Integer.parseInt(floorNum);
                // Chỉ chấp nhận tầng 1 hoặc 2
                if (floor == 2) return 2;
                if (floor == 1) return 1;
            }
        } catch (Exception ignored) {}
        return 1; // Mặc định tầng 1
    }
}
