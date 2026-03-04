package com.team7.taskflow.auth;

/**
 * Strategy interface — mọi cách đăng nhập đều implement interface này.
 *
 * Muốn thêm cách login mới (Firebase, JWT tự build, v.v.)
 * chỉ cần tạo class mới implement LoginStrategy.
 */
public interface LoginStrategy {

    /**
     * Thực hiện đăng nhập bằng email + password.
     * Callback được gọi trên background thread — caller tự runOnUiThread.
     */
    void login(String email, String password, LoginCallback callback);
}

