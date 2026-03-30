package com.team7.taskflow.data.remote.api;

import com.team7.taskflow.domain.model.User;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Retrofit API interface for Users
 * Maps to Supabase REST API endpoints
 */
public interface UserApi {

    /**
     * Get user by email
     */
    @GET("users")
    Call<List<User>> getUserByEmail(
            @Query("email") String emailFilter,
            @Query("select") String select
    );

    /**
     * Get user by ID
     */
    @GET("users")
    Call<List<User>> getUserById(
            @Query("user_id") String userIdFilter,
            @Query("select") String select
    );

    /**
     * Get users by a list of IDs, ex: in.(uuid1,uuid2)
     */
    @GET("users")
    Call<List<User>> getUsersByIds(
            @Query("user_id") String userIdsFilter,
            @Query("select") String select
    );
    /**
     * Get user by Project
     */
    @GET("profiles")
    Call<List<User>> getUsersByProject(
            @Query("project_id") String projectId
    );


    /**
     * Update user data
     */
    @PATCH("users")
    Call<Void> updateUser(
            @Query("user_id") String userIdFilter,
            @Body Map<String, Object> updates
    );

    /**
     * Upsert user profile (Insert or Update)
     * Header Prefer: resolution=merge-duplicates makes it an upsert
     */
    @POST("users")
    Call<Void> upsertUser(
            @Body Map<String, Object> userData,
            @Header("Prefer") String prefer
    );
}
