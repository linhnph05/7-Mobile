package com.team7.taskflow.data.remote.api;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * Retrofit interface for Supabase Storage API
 */
public interface StorageApi {

    // Gửi trực tiếp RequestBody thay vì Multipart để tránh lỗi 400
    @POST("object/{bucket}/{path}")
    Call<ResponseBody> uploadFile(
            @Path("bucket") String bucket,
            @Path(value = "path", encoded = true) String path,
            @Body RequestBody file
    );
}
