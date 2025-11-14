package com.ph48845.datn_qlnh_rmis.data.remote;

import com.ph48845.datn_qlnh_rmis.data.model.Order;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * OrderApi: định nghĩa các endpoint thao tác với Order.
 * CHÚ Ý:
 *  - Điều chỉnh base path nếu server khác (ví dụ thêm "api/").
 *  - Nếu backend không dùng PATCH cho cập nhật order, đổi @PATCH -> @PUT hay @POST phù hợp.
 *  - Nếu getOrdersByTable trả về List<Order> trực tiếp, đổi kiểu trả về.
 */
public interface OrderApi {

    // Tạo mới một order
    @POST("orders")
    Call<Order> createOrder(@Body Order order);

    // Cập nhật một phần order (tableNumber, discount, v.v.)
    @PATCH("orders/{orderId}")
    Call<Order> updateOrder(@Path("orderId") String orderId,
                            @Body Map<String, Object> updates);

    // Xóa order theo id
    @DELETE("orders/{orderId}")
    Call<Void> deleteOrder(@Path("orderId") String orderId);

    // Lấy orders theo số bàn + optional status
    // Nếu backend trả về List<Order> thay vì ApiResponse<List<Order>> thì đổi kiểu.
    @GET("orders/table")
    Call<ApiResponse<List<Order>>> getOrdersByTable(@Query("tableNumber") Integer tableNumber,
                                                    @Query("status") String status);

    // Lấy tất cả orders (dùng cho BepViewModel)
    @GET("orders")
    Call<List<Order>> getAllOrders();

    // Cập nhật trạng thái một item trong order.
    // itemId: nếu bạn đang dùng menuItem (ObjectId món) làm định danh thì truyền nó.
    // Body: { "status": "pending"/"preparing"/"ready"/"soldout" }
    @PATCH("orders/{orderId}/items/{itemId}/status")
    Call<Void> updateOrderItemStatus(@Path("orderId") String orderId,
                                     @Path("itemId") String itemId,
                                     @Body StatusUpdate body);

    // DTO body cho cập nhật trạng thái item
    class StatusUpdate {
        public String status;
        public StatusUpdate(String status) { this.status = status; }
    }

}