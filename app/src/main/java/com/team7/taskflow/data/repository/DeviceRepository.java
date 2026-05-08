package com.team7.taskflow.data.repository;

import android.util.Log;

import androidx.annotation.NonNull;

import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.remote.api.DeviceApi;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository to manage device tokens (user_devices table)
 * Follows repository pattern used elsewhere in the project.
 */
public class DeviceRepository extends BaseRepository {

    private static final String TAG = "DeviceRepository";
    private static DeviceRepository instance;
    private final DeviceApi api;

    private DeviceRepository() {
        api = SupabaseClient.getInstance().getService(DeviceApi.class);
    }

    public static synchronized DeviceRepository getInstance() {
        if (instance == null) instance = new DeviceRepository();
        return instance;
    }

    public interface ResultCallback {
        void onSuccess();
        void onError(String message);
    }

    /**
     * Upsert device token for a user. Uses Prefer: resolution=merge-duplicates to upsert.
     */
    public void upsertDeviceToken(String userId, String deviceToken, ResultCallback callback) {
        if (userId == null || userId.trim().isEmpty() || deviceToken == null || deviceToken.trim().isEmpty()) {
            if (callback != null) callback.onError("Invalid input");
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("user_id", userId);
        body.put("device_token", deviceToken);
        body.put("platform", "android");

        api.upsertDevice(body, "resolution=merge-duplicates")
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        if (response.isSuccessful()) {
                            if (callback != null) callback.onSuccess();
                        } else {
                            String msg = "Failed to upsert device: " + response.code();
                            Log.w(TAG, msg);
                            if (callback != null) callback.onError(msg);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        String msg = "Network error: " + getErrorMessage(t);
                        Log.w(TAG, msg);
                        if (callback != null) callback.onError(msg);
                    }
                });
    }

    public void deleteDeviceToken(String userId, String deviceToken, ResultCallback callback) {
        if (userId == null || userId.trim().isEmpty() || deviceToken == null || deviceToken.trim().isEmpty()) {
            if (callback != null) callback.onError("Invalid input");
            return;
        }

        api.deleteDevice("eq." + userId, "eq." + deviceToken, "return=minimal")
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        if (response.isSuccessful() || response.code() == 404) {
                            if (callback != null) callback.onSuccess();
                        } else {
                            String msg = "Failed to delete device: " + response.code();
                            Log.w(TAG, msg);
                            if (callback != null) callback.onError(msg);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        String msg = "Network error: " + getErrorMessage(t);
                        Log.w(TAG, msg);
                        if (callback != null) callback.onError(msg);
                    }
                });
    }
}
