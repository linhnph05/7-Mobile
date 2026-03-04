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
    private final Retrofit retrofit;
    private final OkHttpClient okHttpClient;

    // Store current user's access token
    private String accessToken;

    private SupabaseClient() {
        // Logging interceptor for debugging
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        // Build OkHttpClient with interceptors
        okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new SupabaseHeaderInterceptor())
                .addInterceptor(loggingInterceptor)
                .build();

        // Build Retrofit instance
        retrofit = new Retrofit.Builder()
                .baseUrl(SupabaseConfig.REST_URL)
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

    /**
     * Get API service interface
     */
    public <T> T getService(Class<T> serviceClass) {
        return retrofit.create(serviceClass);
    }

    /**
     * Set access token after user login
     */
    public void setAccessToken(String token) {
        this.accessToken = token;
    }

    /**
     * Get current access token
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * Clear access token on logout
     */
    public void clearAccessToken() {
        this.accessToken = null;
    }

    /**
     * Check if user is logged in
     */
    public boolean isLoggedIn() {
        return accessToken != null && !accessToken.isEmpty();
    }

    /**
     * Interceptor to add Supabase headers to all requests
     */
    private class SupabaseHeaderInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            Request original = chain.request();

            Request.Builder builder = original.newBuilder()
                    .header(SupabaseConfig.HEADER_API_KEY, SupabaseConfig.SUPABASE_KEY)
                    .header(SupabaseConfig.HEADER_CONTENT_TYPE, SupabaseConfig.CONTENT_TYPE_JSON);

            // Add Authorization header if user is logged in
            if (accessToken != null && !accessToken.isEmpty()) {
                builder.header(SupabaseConfig.HEADER_AUTHORIZATION, "Bearer " + accessToken);
            }

            Request request = builder.build();
            return chain.proceed(request);
        }
    }
}

