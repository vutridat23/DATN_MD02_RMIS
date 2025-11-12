package com.ph48845.datn_qlnh_rmis.data.model;





import com.google.gson.annotations.SerializedName;
import java.io.Serializable;


public class User implements Serializable {
    @SerializedName("_id")
    private String id;

    @SerializedName("username")
    private String username;

    // Usually not returned in list endpoints, but needed when creating/updating user
    @SerializedName("password")
    private String password;

    @SerializedName("role")
    private String role;

    // Backend uses "name" — map it to fullName to keep previous UI code unchanged
    @SerializedName("name")
    private String fullName;

    @SerializedName("phoneNumber")
    private String phoneNumber;

    @SerializedName("email")
    private String email;

    @SerializedName("isActive")
    private boolean isActive;

    @SerializedName("createdAt")
    private String createdAt;

    public User() {}

    // Convenience constructor (without id/createdAt)
    public User(String username, String password, String role, String fullName,
                String phoneNumber, String email, boolean isActive) {
        this.username = username;
        this.password = password;
        this.role = role;
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.isActive = isActive;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", username='" + username + '\'' +
                ", role='" + role + '\'' +
                ", fullName='" + fullName + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", email='" + email + '\'' +
                ", isActive=" + isActive +
                ", createdAt='" + createdAt + '\'' +
                '}';
    }
}