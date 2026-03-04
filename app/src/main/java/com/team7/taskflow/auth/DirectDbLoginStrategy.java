package com.team7.taskflow.auth;

import android.util.Log;

import androidx.annotation.NonNull;

import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.remote.api.UserApi;
import com.team7.taskflow.domain.model.User;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Đăng nhập bằng cách query trực tiếp bảng public.users.
 *
 * So sánh email + password_hash (plain text) trong DB.
 * Đơn giản, dùng cho giai đoạn phát triển / demo.
 */
public class DirectDbLoginStrategy implements LoginStrategy {

    private static final String TAG = "DirectDbLogin";

    @Override
    public void login(String email, String password, LoginCallback callback) {
        UserApi userApi = SupabaseClient.getInstance().getService(UserApi.class);

        // SELECT * FROM users WHERE email = ? AND password_hash = ?
        userApi.getUserByEmailAndPassword(
                "eq." + email,
                "eq." + password,
                "*"
        ).enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(@NonNull Call<List<User>> call,
                                   @NonNull Response<List<User>> response) {
                if (response.isSuccessful()
                        && response.body() != null
                        && !response.body().isEmpty()) {

                    User user = response.body().get(0);
                    Log.d(TAG, "Login OK: " + user.getEmail());
                    callback.onSuccess(user.getUserId(),
                            user.getDisplayNameOrEmail());
                } else {
                    Log.d(TAG, "No match, code=" + response.code());
                    callback.onError("Email hoặc mật khẩu không đúng");
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<User>> call,
                                  @NonNull Throwable t) {
                Log.e(TAG, "Network error: " + t.getMessage(), t);
                callback.onError("Lỗi kết nối: " + t.getMessage());
            }
        });
    }
}

