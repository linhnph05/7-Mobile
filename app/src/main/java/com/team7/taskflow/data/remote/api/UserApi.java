package com.team7.taskflow.data.remote.api;

import com.team7.taskflow.domain.model.User;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.Query;

/**
 * Retrofit API interface for Users
 * Maps to Supabase REST API endpoints
 */
public interface UserApi {

    /**
     * Get user by email
     * Supabase query: /users?email=eq.{email}
     */
    @GET("users")
    Call<List<User>> getUserByEmail(
            @Query("email") String emailFilter,
            @Query("select") String select
    );

    /**
     * Get user by ID
     * Supabase query: /users?user_id=eq.{userId}
     */
    @GET("users")
    Call<List<User>> getUserById(
            @Query("user_id") String userIdFilter,
            @Query("select") String select
    );

    /**
     * Get all users (for testing)
     * Supabase query: /users?limit=1
     */
    @GET("users")
    Call<List<User>> getUsers(
            @Query("limit") int limit,
            @Query("select") String select
    );

    /**
     * Update user data
     * Supabase query: PATCH /users?user_id=eq.{userId}
     */
    @PATCH("users")
    Call<Void> updateUser(
            @Query("user_id") String userIdFilter,
            @Body Map<String, Object> updates
    );
}
