package com.team7.taskflow.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Manages the Supabase auth session using SharedPreferences.
 * Persists access tokens, refresh tokens, user IDs, and display names.
 */
public class SessionManager {

    private static final String TAG = "SessionManager";
    private static final String PREF_NAME = "SupabaseSession";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_EXPIRES_AT = "expires_at";

    private static SharedPreferences prefs;

    /**
     * Must be called once (e.g. in Application.onCreate or first Activity).
     */
    public static void init(Context context) {
        if (prefs == null) {
            prefs = context.getApplicationContext()
                    .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
    }

    // ── Save ────────────────────────────────────────────────────────

    public static void saveSession(String accessToken, String refreshToken, String userId) {
        saveSession(accessToken, refreshToken, userId, null);
    }

    public static void saveSession(String accessToken, String refreshToken,
            String userId, String displayName) {
        ensureInit();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_ACCESS_TOKEN, accessToken);
        editor.putString(KEY_REFRESH_TOKEN, refreshToken);
        editor.putString(KEY_USER_ID, userId);
        if (displayName != null) {
            editor.putString(KEY_DISPLAY_NAME, displayName);
        }
        // Set expiry to 1 hour from now (Supabase default JWT lifetime)
        editor.putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + 3600 * 1000);
        editor.apply();
        Log.d(TAG, "Session saved for userId=" + userId);
    }

    // ── Read ─────────────────────────────────────────────────────────

    public static String getAccessToken() {
        ensureInit();
        return prefs.getString(KEY_ACCESS_TOKEN, "");
    }

    public static String getRefreshToken() {
        ensureInit();
        return prefs.getString(KEY_REFRESH_TOKEN, "");
    }

    public static String getUserId() {
        ensureInit();
        return prefs.getString(KEY_USER_ID, "");
    }

    public static String getDisplayName() {
        ensureInit();
        return prefs.getString(KEY_DISPLAY_NAME, "");
    }

    // ── Status ───────────────────────────────────────────────────────

    public static boolean isLoggedIn() {
        ensureInit();
        String userId = prefs.getString(KEY_USER_ID, "");
        return userId != null && !userId.isEmpty();
    }

    public static boolean isTokenExpired() {
        ensureInit();
        long expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0);
        return System.currentTimeMillis() >= expiresAt;
    }

    // ── Clear ────────────────────────────────────────────────────────

    public static void clearSession() {
        ensureInit();
        prefs.edit().clear().apply();
        Log.d(TAG, "Session cleared");
    }

    private static void ensureInit() {
        if (prefs == null) {
            throw new IllegalStateException(
                    "SessionManager.init(context) must be called before using SessionManager");
        }
    }
}
