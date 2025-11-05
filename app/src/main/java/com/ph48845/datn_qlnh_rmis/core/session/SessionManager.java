package com.ph48845.datn_qlnh_rmis.core.session;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "app_session";
    private static final String KEY_ROLE = "user_role";
    private static final String KEY_USER = "user_name";

    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    public boolean login(String username, String password) {
        if (username.equals("admin") && password.equals("123")) {
            saveSession(username, "ADMIN");
            return true;
        } else if (username.equals("bep") && password.equals("123")) {
            saveSession(username, "BEP");
            return true;
        } else if (username.equals("phucvu") && password.equals("123")) {
            saveSession(username, "PHUCVU");
            return true;
        } else if (username.equals("thungan") && password.equals("123")) {
            saveSession(username, "THUNGAN");
            return true;
        }
        return false;
    }

    public void saveSession(String username, String role) {
        editor.putString(KEY_USER, username);
        editor.putString(KEY_ROLE, role);
        editor.apply();
    }

    public String getUser() {
        return prefs.getString(KEY_USER, null);
    }

    public String getRole() {
        return prefs.getString(KEY_ROLE, null);
    }

    public void logout() {
        editor.clear().apply();
    }
}
