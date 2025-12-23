package com.ph48845.datn_qlnh_rmis.data.remote;

import com.google.gson.JsonObject;
import com.ph48845.datn_qlnh_rmis.data.model.HistoryItem;
import com.ph48845.datn_qlnh_rmis.data.model.Ingredient;
import com.ph48845.datn_qlnh_rmis.data.model.LoginResponse;
import com.ph48845.datn_qlnh_rmis.data.model.MenuItem;
import com.ph48845.datn_qlnh_rmis.data.model.Order;
import com.ph48845.datn_qlnh_rmis.data.model.ReportItem;
import com.ph48845.datn_qlnh_rmis.data.model.ReportDetail;
import com.ph48845.datn_qlnh_rmis.data.model.HourlyRevenue;
import com.ph48845.datn_qlnh_rmis.data.model.PeakHour;
import com.ph48845.datn_qlnh_rmis.data.model.Shift;
import com.ph48845.datn_qlnh_rmis.data.model.TableItem;
import com.ph48845.datn_qlnh_rmis.data.model.User;
import com.ph48845.datn_qlnh_rmis.data.model.Voucher;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

/**
 * ApiService: Interface định nghĩa tất cả endpoint API
 *
 * ✅ ĐÃ SỬA:
 * - Loại bỏ import trùng lặp
 * - Thêm method createCardPayment() cho thanh toán thẻ VNPay
 * - Cập nhật PATCH method cho check items
 * - Thêm đầy đủ các endpoint cần thiết
 */
public interface ApiService {

        // ===========================
        // --- USER ENDPOINTS ---
        // ===========================

        @GET("users")
        Call<ApiResponse<List<User>>> getAllUsers();

        @POST("/users/login")
        Call<LoginResponse> login(@Body User user);

        @POST("users")
        Call<ApiResponse<User>> createUser(@Body User user);

        @PUT("users/{id}")
        Call<ApiResponse<User>> updateUser(@Path("id") String userId, @Body User user);

        // ===========================
        // --- MENU ITEM ENDPOINTS ---
        // ===========================

        @GET("menu")
        Call<ApiResponse<List<MenuItem>>> getAllMenuItems();

        @POST("menu")
        Call<ApiResponse<MenuItem>> createMenuItem(@Body MenuItem menuItem);

        @DELETE("menu/{id}")
        Call<ApiResponse<Void>> deleteMenuItem(@Path("id") String itemId);

        @PUT("menu/{id}")
        Call<ApiResponse<MenuItem>> updateMenuItem(@Path("id") String itemId, @Body MenuItem menuItem);

        @GET("menu/{id}")
        Call<ApiResponse<MenuItem>> getMenuItemById(@Path("id") String itemId);

        // ===========================
        // --- ORDER ENDPOINTS ---
        // ===========================

        /**
         * Lấy tất cả orders
         */
        @GET("orders")
        Call<ApiResponse<List<Order>>> getAllOrders();

        /**
         * Lấy orders theo bàn và status
         */
        @GET("orders")
        Call<ApiResponse<List<Order>>> getOrdersByTable(
                        @Query("tableNumber") Integer tableNumber,
                        @Query("status") String status);

        /**
         * Tạo order mới
         */
        @POST("orders")
        Call<ApiResponse<Order>> createOrder(@Body Order order);

        /**
         * Lấy chi tiết order theo ID
         */
        @GET("orders/{id}")
        Call<ApiResponse<Order>> getOrderById(@Path("id") String orderId);

        /**
         * ✅ PUT - Cập nhật toàn bộ order
         * Route backend: PUT /orders/: id
         * Dùng cho các trường hợp update nhiều field
         */
        @PUT("orders/{id}")
        Call<ApiResponse<Order>> updateOrder(
                        @Path("id") String orderId,
                        @Body Map<String, Object> updates);

        /**
         * ✅✅✅ PATCH - Cập nhật một phần order
         * Route backend: PATCH /orders/:orderId
         *
         * QUAN TRỌNG: Method này khớp với backend route
         * Dùng cho:
         * - Confirm check items (checkItemsStatus, checkItemsCompletedBy,
         * checkItemsCompletedAt, checkItemsNote)
         * - Update các field riêng lẻ khác
         *
         * @param orderId ID của order cần cập nhật
         * @param updates Map chứa các field cần update
         * @return Call trả về Order đã được cập nhật
         */
        @PATCH("orders/{orderId}")
        Call<ApiResponse<Order>> updateOrderPatch(
                        @Path("orderId") String orderId,
                        @Body Map<String, Object> updates);

        /**
         * Cập nhật status của order
         */
        @PUT("orders/{id}/status")
        Call<ApiResponse<Order>> updateOrderStatus(
                        @Path("id") String orderId,
                        @Body Map<String, Object> newStatusBody);

        /**
         * Xóa order
         */
        @DELETE("orders/{id}")
        Call<ApiResponse<Void>> deleteOrder(@Path("id") String orderId);

        /**
         * Cập nhật trạng thái món trong order
         */
        @PATCH("orders/{orderId}/items/{itemId}/status")
        Call<Void> updateOrderItemStatus(
                        @Path("orderId") String orderId,
                        @Path("itemId") String itemId,
                        @Body StatusUpdate statusUpdate);

        /**
         * Yêu cầu hủy món
         */
        @POST("orders/{orderId}/items/{itemId}/request-cancel")
        Call<ApiResponse<Order>> requestCancelItem(
                        @Path("orderId") String orderId,
                        @Path("itemId") String itemId,
                        @Body Map<String, Object> body);

        /**
         * Yêu cầu tạm tính
         * POST /orders/{orderId}/request-temp-calculation
         * Body: { requestedBy, requestedByName }
         */
        @POST("orders/{orderId}/request-temp-calculation")
        Call<ApiResponse<Order>> requestTempCalculation(
                        @Path("orderId") String orderId,
                        @Body Map<String, Object> body);

        /**
         * Di chuyển tất cả orders sang bàn khác
         * POST /orders/move-to-table
         * Body: { fromTableNumber, toTableNumber, movedBy }
         */
        @POST("orders/move-to-table")
        Call<ApiResponse<Map<String, Object>>> moveOrdersToTable(@Body Map<String, Object> body);

        /**
         * Thanh toán order
         */
        @POST("orders/pay")
        Call<ApiResponse<Order>> payOrder(@Body Map<String, Object> body);

        @POST("payment/create-card")
        Call<Map<String, Object>> createCardPayment(@Body Map<String, Object> body);

        /**
         * ✅ THÊM MỚI - Thanh toán bằng thẻ (VNPay)
         * POST /payment/card/create
         * Body: { orderId, orderIds, amount }
         * Response: { paymentUrl }
         */

        /**
         * Xác nhận kiểm tra bàn (dùng JsonObject)
         * Route backend: PATCH /orders/:orderId/confirm-check-items
         */
        @PATCH("orders/{orderId}/confirm-check-items")
        Call<ApiResponse<Order>> confirmCheckItems(
                        @Path("orderId") String orderId,
                        @Body JsonObject body);

        /**
         * Hoàn thành kiểm tra bàn
         * Route backend: PATCH /orders/:orderId/complete-check-items
         */
        @PATCH("orders/{orderId}/complete-check-items")
        Call<ApiResponse<Order>> completeCheckItems(
                        @Path("orderId") String orderId,
                        @Body Map<String, Object> body);

        // ===========================
        // --- TABLE ENDPOINTS ---
        // ===========================

        /**
         * Lấy tất cả bàn
         */
        @GET("tables")
        Call<ApiResponse<List<TableItem>>> getAllTables();

        /**
         * Lấy chi tiết bàn theo ID
         */
        @GET("tables/{id}")
        Call<ApiResponse<TableItem>> getTableById(@Path("id") String tableId);

        /**
         * Cập nhật thông tin bàn
         */
        @PUT("tables/{id}")
        Call<TableItem> updateTable(
                        @Path("id") String tableId,
                        @Body Map<String, Object> updates);

        /**
         * Gộp bàn
         */
        @POST("tables/{id}/merge")
        Call<TableItem> mergeTable(
                        @Path("id") String targetTableId,
                        @Body Map<String, String> body);

        /**
         * Đặt trước bàn
         */
        @POST("tables/{id}/reserve")
        Call<TableItem> reserveTable(
                        @Path("id") String id,
                        @Body Map<String, Object> body);

        /**
         * Tách bàn không tách hóa đơn
         */
        @POST("tables/{sourceTableId}/split-table-only")
        Call<ApiResponse<Void>> splitTableOnly(
                        @Path("sourceTableId") String sourceTableId,
                        @Body Map<String, Object> body);

        /**
         * Yêu cầu kiểm tra bàn
         */
        @POST("tables/request-check")
        Call<ApiResponse<Void>> requestTableCheck(@Body Map<String, Object> body);

        // ===========================
        // --- INGREDIENT ENDPOINTS ---
        // ===========================

        /**
         * Lấy danh sách nguyên liệu
         */
        @GET("ingredients")
        Call<ApiResponse<List<Ingredient>>> getAllIngredients(
                        @Query("status") String status,
                        @Query("tag") String tag);

        /**
         * Lấy nguyên liệu theo ID
         */
        @GET("ingredients/{id}")
        Call<ApiResponse<Ingredient>> getIngredientById(@Path("id") String ingredientId);

        /**
         * Tạo nguyên liệu mới
         */
        @POST("ingredients")
        Call<ApiResponse<Ingredient>> createIngredient(@Body Ingredient ingredient);

        /**
         * Cập nhật nguyên liệu
         */
        @PUT("ingredients/{id}")
        Call<ApiResponse<Ingredient>> updateIngredient(
                        @Path("id") String ingredientId,
                        @Body Ingredient ingredient);

        /**
         * Xóa nguyên liệu
         */
        @DELETE("ingredients/{id}")
        Call<ApiResponse<Void>> deleteIngredient(@Path("id") String ingredientId);

        /**
         * Lấy nguyên liệu
         */
        @POST("ingredients/{id}/take")
        Call<ApiResponse<Ingredient>> takeIngredient(
                        @Path("id") String ingredientId,
                        @Body Map<String, Object> body);

        /**
         * Nhập thêm nguyên liệu
         */
        @POST("ingredients/{id}/restock")
        Call<ApiResponse<Ingredient>> restockIngredient(
                        @Path("id") String ingredientId,
                        @Body Map<String, Object> body);

        /**
         * Lấy danh sách nguyên liệu cảnh báo
         */
        @GET("ingredients/warnings")
        Call<ApiResponse<List<Ingredient>>> getWarningIngredients();

        /**
         * Tiêu thụ nguyên liệu theo công thức
         */
        @POST("recipes/consume")
        Call<ApiResponse<Void>> consumeRecipe(@Body Map<String, Object> body);

        // ===========================
        // --- HISTORY ENDPOINTS ---
        // ===========================

        /**
         * Lấy tất cả lịch sử
         */
        @GET("history")
        Call<ApiResponse<List<HistoryItem>>> getAllHistory(@QueryMap Map<String, String> filters);

        /**
         * Lấy chi tiết lịch sử theo ID
         */
        @GET("history/{id}")
        Call<ApiResponse<HistoryItem>> getHistoryById(@Path("id") String historyId);

        /**
         * Lấy lịch sử orders
         */
        @GET("orders/historyod")
        Call<ApiResponse<List<HistoryItem>>> getAllOrdersHistory();

        // ===========================
        // --- REPORT ENDPOINTS ---
        // ===========================

        /**
         * Lấy tất cả báo cáo
         */
        @GET("reports")
        Call<ApiResponse<List<ReportItem>>> getAllReports();

        /**
         * Lấy báo cáo theo ngày
         */
        @GET("reports/byDate")
        Call<ApiResponse<List<ReportItem>>> getReportsByDate(@QueryMap Map<String, String> params);

        /**
         * Lấy chi tiết báo cáo theo ID
         */
        @GET("reports/{id}")
        Call<ApiResponse<ReportItem>> getReportById(@Path("id") String reportId);

        /**
         * Lấy báo cáo chi tiết
         */
        @GET("reports/detailed")
        Call<ApiResponse<ReportDetail>> getDetailedReport(@QueryMap Map<String, String> params);

        /**
         * Lấy doanh thu theo giờ
         */
        @GET("reports/hourly")
        Call<ApiResponse<List<HourlyRevenue>>> getHourlyRevenue(@Query("date") String date);

        /**
         * Lấy giờ cao điểm
         */
        @GET("reports/peak-hours")
        Call<ApiResponse<List<PeakHour>>> getPeakHours(@QueryMap Map<String, String> params);

        /**
         * Tạo báo cáo ngày
         */
        @POST("reports/daily")
        Call<ApiResponse<ReportItem>> createDailyReport(@Body Map<String, String> body);

        /**
         * Tạo báo cáo tuần
         */
        @POST("reports/weekly")
        Call<ApiResponse<ReportItem>> createWeeklyReport(@Body Map<String, String> body);

        // ===========================
        // --- SHIFT ENDPOINTS ---
        // ===========================

        /**
         * Lấy tất cả ca làm việc
         */
        @GET("shifts")
        Call<ApiResponse<List<Shift>>> getAllShifts(@QueryMap Map<String, String> params);

        /**
         * Lấy chi tiết ca làm việc theo ID
         */
        @GET("shifts/{id}")
        Call<ApiResponse<Shift>> getShiftById(@Path("id") String shiftId);

        /**
         * Lấy lịch sử ca làm việc của nhân viên
         */
        @GET("shifts/employee/{employeeId}")
        Call<ApiResponse<List<Shift>>> getEmployeeShiftHistory(
                        @Path("employeeId") String employeeId,
                        @QueryMap Map<String, String> params);

        /**
         * Tạo ca làm việc mới
         */
        @POST("shifts")
        Call<ApiResponse<Shift>> createShift(@Body Shift shift);

        /**
         * Cập nhật ca làm việc
         */
        @PUT("shifts/{id}")
        Call<ApiResponse<Shift>> updateShift(@Path("id") String shiftId, @Body Shift shift);

        /**
         * Xóa ca làm việc
         */
        @DELETE("shifts/{id}")
        Call<ApiResponse<Void>> deleteShift(@Path("id") String shiftId);

        /**
         * Check-in ca làm việc
         */
        @POST("shifts/{id}/checkin")
        Call<ApiResponse<Shift>> checkinShift(
                        @Path("id") String shiftId,
                        @Body Map<String, String> body);

        /**
         * Check-out ca làm việc
         */
        @POST("shifts/{id}/checkout")
        Call<ApiResponse<Shift>> checkoutShift(
                        @Path("id") String shiftId,
                        @Body Map<String, String> body);

        // ===========================
        // --- VOUCHER ENDPOINTS ---
        // ===========================

        /**
         * Lấy tất cả voucher
         */
        @GET("vouchers")
        Call<ApiResponse<List<Voucher>>> getAllVouchers(@Query("status") String status);

        /**
         * Lấy chi tiết voucher theo ID
         */
        @GET("vouchers/{id}")
        Call<ApiResponse<Voucher>> getVoucherById(@Path("id") String voucherId);

        /**
         * Lấy voucher theo code
         */
        @GET("vouchers/code/{code}")
        Call<ApiResponse<Voucher>> getVoucherByCode(@Path("code") String code);

        /**
         * Tạo voucher mới
         */
        @POST("vouchers")
        Call<ApiResponse<Voucher>> createVoucher(@Body Voucher voucher);

        /**
         * Cập nhật voucher
         */
        @PUT("vouchers/{id}")
        Call<ApiResponse<Voucher>> updateVoucher(
                        @Path("id") String voucherId,
                        @Body Voucher voucher);

        /**
         * Xóa voucher
         */
        @DELETE("vouchers/{id}")
        Call<ApiResponse<Void>> deleteVoucher(@Path("id") String voucherId);

        // ===========================
        // --- DASHBOARD ENDPOINTS ---
        // ===========================

        /**
         * Lấy thông tin dashboard tổng hợp
         */
        @GET("service/dashboard")
        Call<ApiResponse<com.ph48845.datn_qlnh_rmis.data.model.DashboardData>> getServiceDashboard();

        // ===========================
        // --- HELPER CLASSES ---
        // ===========================

        /**
         * Class helper cho cập nhật status
         */
        class StatusUpdate {
                public String status;

                public StatusUpdate(String status) {
                        this.status = status;
                }
        }
}