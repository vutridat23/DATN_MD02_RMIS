package com.ph48845.datn_qlnh_rmis.data.model;




import androidx.annotation.DrawableRes;

public class InAppNotification {

    public enum Type {
        INFO,
        SUCCESS,
        WARNING,
        ERROR,
        ORDER_NEW,
        ORDER_READY,
        ORDER_UPDATED,
        TABLE_UPDATED,
        CHECK_ITEMS,
        TEMP_CALC,
        MENU_UPDATED,
        INGREDIENT_LOW
    }

    private String id;
    private Type type;
    private String title;
    private String message;
    private int iconRes;
    private long timestamp;
    private String actionData;
    private int duration;

    public InAppNotification(Type type, String title, String message) {
        this.id = String.valueOf(System.currentTimeMillis());
        this.type = type;
        this.title = title;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
        this.duration = 5000;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public int getIconRes() { return iconRes; }
    public void setIconRes(@DrawableRes int iconRes) { this.iconRes = iconRes; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getActionData() { return actionData; }
    public void setActionData(String actionData) { this.actionData = actionData; }

    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }

    public static class Builder {
        private InAppNotification notification;

        public Builder(Type type, String title, String message) {
            notification = new InAppNotification(type, title, message);
        }

        public Builder icon(@DrawableRes int iconRes) {
            notification.iconRes = iconRes;
            return this;
        }

        public Builder actionData(String data) {
            notification.actionData = data;
            return this;
        }

        public Builder duration(int ms) {
            notification.duration = ms;
            return this;
        }

        public InAppNotification build() {
            return notification;
        }
    }
}