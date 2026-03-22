package com.team7.taskflow.data.remote.api;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * Retrofit API interface for Supabase Storage
 */
public interface StorageApi {

    /**
     * Upload a file to a specific bucket and path
     * POST /storage/v1/object/{bucket}/{path}
     */
    @POST("storage/v1/object/{bucket}/{path}")
    Call<ResponseBody> uploadFile(
            @Path("bucket") String bucket,
            @Path(value = "path", encoded = true) String path,
            @Body RequestBody file
    );
}
