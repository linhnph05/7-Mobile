package com.team7.taskflow.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.ui.auth.LoginActivity;

/**
 * Quản lý session người dùng (Token, ID, Email, Name)
 */
public class SessionManager {

    private static final String TAG = "SessionManager";
    private static final String PREF_NAME = "SupabaseSession";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_EXPIRES_AT = "expires_at";

    private static SharedPreferences prefs;
    private static Context appContext;
    private static boolean redirectingToLogin;

    public static void init(Context context) {
        if (prefs == null) {
            appContext = context.getApplicationContext();
            prefs = appContext
                    .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            
            String token = getAccessToken();
            if (!token.isEmpty()) {
                SupabaseClient.getInstance().setAccessToken(token);
            }
        }
    }

    public static void saveSession(String accessToken, String refreshToken, String userId, String email, String displayName) {
        ensureInit();
        redirectingToLogin = false;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_ACCESS_TOKEN, accessToken);
        editor.putString(KEY_REFRESH_TOKEN, refreshToken);
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_USER_EMAIL, email);
        if (displayName != null) {
            editor.putString(KEY_DISPLAY_NAME, displayName);
        }
        editor.putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + 3600 * 1000);
        editor.apply();
        
        SupabaseClient.getInstance().setAccessToken(accessToken);
        Log.d(TAG, "Session saved for email=" + email);
    }

    public static String getAccessToken() {
        ensureInit();
        if (isExpired()) {
            handleExpiredSession();
            return "";
        }
        return prefs.getString(KEY_ACCESS_TOKEN, "");
    }

    public static String getRefreshToken() {
        ensureInit();
        return prefs.getString(KEY_REFRESH_TOKEN, "");
    }

    public static String getUserId() {
        ensureInit();
        if (isExpired()) {
            handleExpiredSession();
            return "";
        }
        return prefs.getString(KEY_USER_ID, "");
    }

    public static String getUserEmail() {
        ensureInit();
        if (isExpired()) {
            handleExpiredSession();
            return "";
        }
        return prefs.getString(KEY_USER_EMAIL, "");
    }

    public static String getDisplayName() {
        ensureInit();
        if (isExpired()) {
            handleExpiredSession();
            return "";
        }
        return prefs.getString(KEY_DISPLAY_NAME, "");
    }

    public static boolean isLoggedIn() {
        ensureInit();
        
        String userId = getUserId();
        if (userId.isEmpty()) {
            return false;
        }

        // Kiểm tra Token đã hết hạn chưa (Supabase Token sống 1 tiếng)
        if (isExpired()) {
            Log.w(TAG, "Access token expired! Auto-clearing session...");
            handleExpiredSession();
            return false;
        }

        return true;
    }

    public static void clearSession() {
        ensureInit();
        redirectingToLogin = false;
        prefs.edit().clear().apply();
        SupabaseClient.getInstance().clearAccessToken();
    }

    private static boolean isExpired() {
        if (prefs == null) {
            return false;
        }
        long expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0);
        return expiresAt > 0 && System.currentTimeMillis() >= expiresAt;
    }

    private static void handleExpiredSession() {
        if (redirectingToLogin) {
            return;
        }
        redirectingToLogin = true;
        clearSession();

        if (appContext == null) {
            return;
        }

        Intent intent = new Intent(appContext, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        appContext.startActivity(intent);
    }

    private static void ensureInit() {
        if (prefs == null) throw new IllegalStateException("SessionManager not initialized");
    }
}
