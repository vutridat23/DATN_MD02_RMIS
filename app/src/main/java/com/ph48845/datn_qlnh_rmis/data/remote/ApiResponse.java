package com.ph48845.datn_qlnh_rmis.data.remote;





import com.google.gson.annotations.SerializedName;

/**
 * Generic response wrapper để map JSON có dạng:
 * { "data": ..., "success": true, "message": "..." }
 */
public class ApiResponse<T> {

    @SerializedName("data")
    private T data;

    @SerializedName("success")
    private boolean success;

    @SerializedName("message")
    private String message;

    public ApiResponse() {}

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
