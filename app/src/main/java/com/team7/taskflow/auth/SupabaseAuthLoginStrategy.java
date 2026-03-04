package com.team7.taskflow.auth;

import android.util.Log;

import com.team7.taskflow.ui.auth.AuthRepository;

/**
 * Đăng nhập qua Supabase Auth REST API (/auth/v1/token).
 *
 * Supabase tự hash password, tự quản lý session token.
 * Dùng khi production hoặc cần bảo mật cao.
 */
public class SupabaseAuthLoginStrategy implements LoginStrategy {

    private static final String TAG = "SupabaseAuthLogin";

    @Override
    public void login(String email, String password, LoginCallback callback) {
        AuthRepository.signIn(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String userId) {
                Log.d(TAG, "Supabase Auth login OK: " + userId);
                // Session đã được lưu bên trong AuthRepository.signIn()
                callback.onSuccess(userId, email);
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Supabase Auth error: " + message);
                callback.onError(message);
            }
        });
    }
}
