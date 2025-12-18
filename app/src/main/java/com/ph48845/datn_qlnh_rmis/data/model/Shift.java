package com.ph48845.datn_qlnh_rmis.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Shift {
    @SerializedName("_id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("startTime")
    private String startTime;

    @SerializedName("endTime")
    private String endTime;

    @SerializedName("date")
    private String date;

    @SerializedName("employees")
    private List<ShiftEmployee> employees;

    @SerializedName("status")
    private String status; // scheduled, ongoing, completed, cancelled

    @SerializedName("notes")
    private String notes;

    @SerializedName("createdAt")
    private String createdAt;

    @SerializedName("updatedAt")
    private String updatedAt;

    // Constructors
    public Shift() {
    }

    public Shift(String name, String startTime, String endTime, String date, List<ShiftEmployee> employees) {
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.date = date;
        this.employees = employees;
        this.status = "scheduled";
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public List<ShiftEmployee> getEmployees() {
        return employees;
    }

    public void setEmployees(List<ShiftEmployee> employees) {
        this.employees = employees;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Helper methods
    public String getStatusText() {
        if (status == null)
            return "";
        switch (status) {
            case "scheduled":
                return "Đã lên lịch";
            case "ongoing":
                return "Đang diễn ra";
            case "completed":
                return "Hoàn thành";
            case "cancelled":
                return "Đã hủy";
            default:
                return status;
        }
    }

    public int getEmployeeCount() {
        return employees != null ? employees.size() : 0;
    }

    public int getPresentCount() {
        if (employees == null)
            return 0;
        int count = 0;
        for (ShiftEmployee emp : employees) {
            if ("present".equals(emp.getStatus()))
                count++;
        }
        return count;
    }

    // Inner class for ShiftEmployee
    public static class ShiftEmployee {
        @SerializedName("employeeId")
        private EmployeeInfo employeeId;

        @SerializedName("checkinTime")
        private String checkinTime;

        @SerializedName("checkoutTime")
        private String checkoutTime;

        @SerializedName("actualHours")
        private double actualHours;

        @SerializedName("status")
        private String status; // scheduled, present, absent, late

        @SerializedName("note")
        private String note;

        @SerializedName("_id")
        private String id;

        // Constructors
        public ShiftEmployee() {
        }

        public ShiftEmployee(String employeeId) {
            this.employeeId = new EmployeeInfo();
            this.employeeId.setId(employeeId);
            this.status = "scheduled";
        }

        // Getters and Setters
        public EmployeeInfo getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(EmployeeInfo employeeId) {
            this.employeeId = employeeId;
        }

        public String getCheckinTime() {
            return checkinTime;
        }

        public void setCheckinTime(String checkinTime) {
            this.checkinTime = checkinTime;
        }

        public String getCheckoutTime() {
            return checkoutTime;
        }

        public void setCheckoutTime(String checkoutTime) {
            this.checkoutTime = checkoutTime;
        }

        public double getActualHours() {
            return actualHours;
        }

        public void setActualHours(double actualHours) {
            this.actualHours = actualHours;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        // Helper methods
        public String getStatusText() {
            if (status == null)
                return "";
            switch (status) {
                case "scheduled":
                    return "Đã lên lịch";
                case "present":
                    return "Có mặt";
                case "absent":
                    return "Vắng mặt";
                case "late":
                    return "Đi muộn";
                default:
                    return status;
            }
        }

        public String getEmployeeName() {
            return employeeId != null ? employeeId.getName() : "";
        }

        public String getEmployeeRole() {
            return employeeId != null ? employeeId.getRole() : "";
        }
    }

    // Inner class for Employee Info
    public static class EmployeeInfo {
        @SerializedName("_id")
        private String id;

        @SerializedName("username")
        private String username;

        @SerializedName("name")
        private String name;

        @SerializedName("role")
        private String role;

        // Getters and Setters
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getRoleText() {
            if (role == null)
                return "";
            switch (role) {
                case "admin":
                    return "ADMIN";
                case "kitchen":
                    return "Bếp";
                case "cashier":
                    return "Thu ngân";
                case "waiter":
                    return "Phục vụ";
                default:
                    return "Phục vụ"; // Default to waiter for unknown roles
            }
        }
    }
}
