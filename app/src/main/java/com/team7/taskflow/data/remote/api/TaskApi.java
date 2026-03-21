package com.team7.taskflow.data.remote.api;

import com.team7.taskflow.domain.model.Task;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Retrofit API interface for Tasks
 * Maps to Supabase REST API endpoints
 */
public interface TaskApi {

    /**
     * Get all tasks for a project
     */
    @GET("tasks")
    Call<List<Task>> getTasksByProject(
            @Query("project_id") String projectIdFilter,
            @Query("order") String order
    );

    /**
     * Get tasks by status
     */
    @GET("tasks")
    Call<List<Task>> getTasksByStatus(
            @Query("project_id") String projectIdFilter,
            @Query("status") String statusFilter,
            @Query("order") String order
    );

    /**
     * Get task by ID
     */
    @GET("tasks")
    Call<List<Task>> getTaskById(
            @Query("task_id") String taskIdFilter
    );

    /**
     * Get tasks assigned to a user
     */
    @GET("tasks")
    Call<List<Task>> getTasksByAssignee(
            @Query("assignee_id") String assigneeIdFilter,
            @Query("order") String order
    );

    /**
     * Create a new task
     */
    @POST("tasks")
    Call<List<Task>> createTask(
            @Body Task task,
            @Header("Prefer") String prefer
    );

    /**
     * Update task fields (using Map for dynamic updates like soft delete or drag-drop position)
     */
    @PATCH("tasks")
    Call<List<Task>> updateTaskFields(
            @Query("task_id") String taskIdFilter,
            @Body Map<String, Object> updates,
            @Header("Prefer") String prefer
    );

    /**
     * Update task status (for drag & drop in Kanban)
     */
    @PATCH("tasks")
    Call<Void> updateTaskStatus(
            @Query("task_id") String taskIdFilter,
            @Body StatusBody body
    );

    /**
     * Delete task (hard delete)
     */
    @DELETE("tasks")
    Call<Void> deleteTask(
            @Query("task_id") String taskIdFilter
    );

    /**
     * Body for status update
     */
    class StatusBody {
        private String status;

        public StatusBody(String status) {
            this.status = status;
        }
    }
}
