package com.ph48845.datn_qlnh_rmis.core.session;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Simple SessionManager using SharedPreferences.
 * For production, consider EncryptedSharedPreferences.
 */
public class SessionManager {
    private static final String PREFS_NAME = "com_ph48845_session";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String KEY_USER_ID = "user_id";
    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveLogin(String userId, String role) {
        prefs.edit()
                .putBoolean(KEY_IS_LOGGED_IN, true)
                .putString(KEY_USER_ROLE, role)
                .putString(KEY_USER_ID, userId)
                .apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public String getUserRole() {
        return prefs.getString(KEY_USER_ROLE, "");
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, "");
    }

    public void logout() {
        prefs.edit().clear().apply();
    }

    // Optional convenience
    public boolean isRole(String role) {
        return role != null && role.equalsIgnoreCase(getUserRole());
    }
}
