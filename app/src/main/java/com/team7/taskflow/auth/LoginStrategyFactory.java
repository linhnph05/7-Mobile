package com.team7.taskflow.auth;

/**
 * Factory trả về LoginStrategy đăng nhập.
 *
 * Cách dùng:
 *   LoginStrategy strategy = LoginStrategyFactory.create();
 *   strategy.login(email, password, callback);
 */
public final class LoginStrategyFactory {

    private LoginStrategyFactory() {}

    public static LoginStrategy create() {
        return new SupabaseAuthLoginStrategy();
    }
}

