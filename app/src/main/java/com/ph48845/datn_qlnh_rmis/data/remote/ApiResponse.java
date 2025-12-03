package com.ph48845.datn_qlnh_rmis.data.remote;

import android.util.Log;
import com.google.gson.annotations.SerializedName;

public class ApiResponse<T> {

    private static final String TAG = "ApiResponse";

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
        Log.d(TAG, "setData: " + (data != null ? data.toString() : "null"));
        this.data = data;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        Log.d(TAG, "setSuccess: " + success);
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        Log.d(TAG, "setMessage: " + message);
        this.message = message;
    }
}
