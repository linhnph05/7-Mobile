package com.example.project_mobile;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists and retrieves the Supabase auth session using SharedPreferences.
 * Call SessionManager.init(context) once in Application or MainActivity.
 */
public final class SessionManager {

    private static final String PREFS_NAME    = "supabase_session";
    private static final String KEY_ACCESS    = "access_token";
    private static final String KEY_REFRESH   = "refresh_token";
    private static final String KEY_USER_ID   = "user_id";

    private static SharedPreferences prefs;

    public static void init(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void saveSession(String accessToken, String refreshToken, String userId) {
        prefs.edit()
                .putString(KEY_ACCESS,  accessToken)
                .putString(KEY_REFRESH, refreshToken)
                .putString(KEY_USER_ID, userId)
                .apply();
    }

    public static String getAccessToken()  { return prefs.getString(KEY_ACCESS,  ""); }
    public static String getRefreshToken() { return prefs.getString(KEY_REFRESH, ""); }
    public static String getUserId()       { return prefs.getString(KEY_USER_ID, ""); }

    public static boolean isLoggedIn() {
        return !getAccessToken().isEmpty();
    }

    public static void clearSession() {
        prefs.edit().clear().apply();
    }

    private SessionManager() {}
}
