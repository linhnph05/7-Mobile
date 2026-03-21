package com.team7.taskflow.data.remote;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Singleton class to manage Supabase API client
 */
public class SupabaseClient {

    private static SupabaseClient instance;
    private final Retrofit restRetrofit;
    private final Retrofit storageRetrofit;
    private final OkHttpClient okHttpClient;

    private String accessToken;

    private SupabaseClient() {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new SupabaseHeaderInterceptor())
                .addInterceptor(loggingInterceptor)
                .build();

        restRetrofit = new Retrofit.Builder()
                .baseUrl(SupabaseConfig.REST_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        storageRetrofit = new Retrofit.Builder()
                .baseUrl(SupabaseConfig.STORAGE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    public static synchronized SupabaseClient getInstance() {
        if (instance == null) {
            instance = new SupabaseClient();
        }
        return instance;
    }

    public <T> T getService(Class<T> serviceClass) {
        return restRetrofit.create(serviceClass);
    }

    public <T> T getStorageService(Class<T> serviceClass) {
        return storageRetrofit.create(serviceClass);
    }

    public void setAccessToken(String token) {
        this.accessToken = token;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void clearAccessToken() {
        this.accessToken = null;
    }

    private class SupabaseHeaderInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request original = chain.request();

            Request.Builder builder = original.newBuilder()
                    .header(SupabaseConfig.HEADER_API_KEY, SupabaseConfig.SUPABASE_KEY);

            // QUAN TRỌNG: Thêm Content-Type cho các API REST (ngoại trừ Storage upload)
            if (!original.url().toString().contains("/storage/v1/object/")) {
                builder.header(SupabaseConfig.HEADER_CONTENT_TYPE, SupabaseConfig.CONTENT_TYPE_JSON);
            }

            if (accessToken != null && !accessToken.isEmpty()) {
                builder.header(SupabaseConfig.HEADER_AUTHORIZATION, "Bearer " + accessToken);
            }

            return chain.proceed(builder.build());
        }
    }
}
