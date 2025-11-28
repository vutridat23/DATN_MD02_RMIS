package com.ph48845.datn_qlnh_rmis.data.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class LoginResponse implements Serializable {

    @SerializedName("message")
    private String message;

    @SerializedName("user")
    private User user;

    @SerializedName("token")
    private String token;

    // Getter
    public User getUser() { return user; }
    public String getToken() { return token; }
    // ... (Thêm getter cho message nếu cần)
}