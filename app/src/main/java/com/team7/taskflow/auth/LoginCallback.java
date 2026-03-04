package com.team7.taskflow.auth;

/**
 * Callback chung cho mọi loại đăng nhập.
 */
public interface LoginCallback {
    void onSuccess(String userId, String displayName);
    void onError(String message);
}

