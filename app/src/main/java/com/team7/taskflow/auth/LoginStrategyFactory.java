package com.team7.taskflow.auth;

import com.team7.taskflow.utils.AppConfig;

/**
 * Factory trả về LoginStrategy phù hợp dựa trên AppConfig.AUTH_MODE.
 *
 * Cách dùng:
 *   LoginStrategy strategy = LoginStrategyFactory.create();
 *   strategy.login(email, password, callback);
 */
public final class LoginStrategyFactory {

    private LoginStrategyFactory() {}

    public static LoginStrategy create() {
        switch (AppConfig.AUTH_MODE) {
            case SUPABASE_AUTH:
                return new SupabaseAuthLoginStrategy();

            case DIRECT_DB:
            default:
                return new DirectDbLoginStrategy();
        }
    }
}

