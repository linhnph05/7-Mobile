package com.team7.taskflow.data.remote.api;

import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.Header;
import retrofit2.http.Query;
import retrofit2.http.POST;

/**
 * API endpoints for device tokens (user_devices table)
 */
public interface DeviceApi {

    @POST("user_devices")
    Call<Void> upsertDevice(
            @Body Map<String, Object> body,
            @Header("Prefer") String prefer
    );

        @DELETE("user_devices")
        Call<Void> deleteDevice(
            @Query("user_id") String userIdFilter,
            @Query("device_token") String deviceTokenFilter,
            @Header("Prefer") String prefer
        );
}
