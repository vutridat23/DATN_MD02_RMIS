package com.ph48845.datn_qlnh_rmis.data.remote;

import com.ph48845.datn_qlnh_rmis.data.model.Order;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.Path;

public interface OrderApi {
    @GET("orders")
    Call<List<Order>> getAllOrders();

    @PATCH("orders/{orderId}/items/{itemId}/status")
    Call<Void> updateOrderItemStatus(
            @Path("orderId") String orderId,
            @Path("itemId") String itemId,
            @Body StatusUpdate status
    );

    class StatusUpdate {
        public String status;
        public StatusUpdate(String status) { this.status = status; }
    }
}
