package com.team7.taskflow.data.remote.api;

import com.team7.taskflow.domain.model.ProjectActivity;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ProjectActivityApi {

    @GET("project_activities")
    Call<List<ProjectActivity>> getActivitiesByProject(
            @Query("project_id") String projectIdFilter,
            @Query("select") String select,
            @Query("order") String order);

    @POST("project_activities")
    Call<Void> logActivity(@Body ProjectActivity activity);
}