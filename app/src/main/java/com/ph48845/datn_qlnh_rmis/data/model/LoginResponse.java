package com.ph48845.datn_qlnh_rmis.data.model;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class LoginResponse implements Serializable {

    // Khớp với key 'message' của Server
    @SerializedName("message")
    private String message;

    // Khớp với key 'user' của Server, chứa toàn bộ User object
    @SerializedName("user")
    private User user;

    // Khớp với key 'token'
    @SerializedName("token")
    private String token;

    // Getter
    public User getUser() { return user; }
    public String getToken() { return token; }
    // ... (Thêm getter cho message nếu cần)
}