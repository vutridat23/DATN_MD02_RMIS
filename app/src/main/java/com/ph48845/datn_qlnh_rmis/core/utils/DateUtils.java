package com.ph48845.datn_qlnh_rmis.core.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utility class để xử lý định dạng ngày tháng
 */
public class DateUtils {

    private static final String DATE_FORMAT_DEFAULT = "dd/MM/yyyy";
    private static final String DATE_TIME_FORMAT = "dd/MM/yyyy HH:mm:ss";
    private static final String TIME_FORMAT = "HH:mm";

    /**
     * Format timestamp thành chuỗi ngày tháng
     */
    public static String formatDate(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_DEFAULT, Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    /**
     * Format timestamp thành chuỗi ngày tháng + giờ
     */
    public static String formatDateTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_TIME_FORMAT, Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    /**
     * Format timestamp thành chuỗi giờ
     */
    public static String formatTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat(TIME_FORMAT, Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    /**
     * Lấy timestamp hiện tại
     */
    public static long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * Format timestamp với pattern tùy chỉnh
     */
    public static String formatCustom(long timestamp, String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
}

