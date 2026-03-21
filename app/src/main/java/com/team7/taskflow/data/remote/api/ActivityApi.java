package com.team7.taskflow.data.remote.api;

import com.team7.taskflow.domain.model.TaskActivity;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * API for Task Activities / History
 */
public interface ActivityApi {
    @GET("task_activities")
    Call<List<TaskActivity>> getActivitiesByTask(@Query("task_id") String taskIdFilter, @Query("order") String order);

    @POST("task_activities")
    Call<Void> logActivity(@Body TaskActivity activity);
}
