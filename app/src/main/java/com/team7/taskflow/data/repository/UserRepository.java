package com.team7.taskflow.data.repository;

import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.remote.api.UserApi;
import com.team7.taskflow.domain.model.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserRepository {
    private final UserApi userApi;

    public UserRepository() {
        this.userApi = SupabaseClient.getInstance().getService(UserApi.class);
    }

    public void getUserById(String userId, UserCallback callback) {
        userApi.getUserById("eq." + userId, "*").enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    callback.onSuccess(response.body().get(0));
                } else {
                    callback.onError("User not found or error occurred");
                }
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void updateUserBio(String userId, String bio, UpdateCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("bio", bio);
        
        userApi.updateUser("eq." + userId, updates).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess();
                } else {
                    callback.onError("Update failed: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void updateUserProfile(String userId, String displayName, String bio, UpdateCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("display_name", displayName);
        updates.put("bio", bio);
        
        userApi.updateUser("eq." + userId, updates).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess();
                } else {
                    callback.onError("Update failed: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public interface UserCallback {
        void onSuccess(User user);
        void onError(String message);
    }

    public interface UpdateCallback {
        void onSuccess();
        void onError(String message);
    }
}
