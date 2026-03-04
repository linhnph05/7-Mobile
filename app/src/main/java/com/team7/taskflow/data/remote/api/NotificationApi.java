package com.team7.taskflow.data.remote.api;

import com.team7.taskflow.domain.model.Notification;
import com.team7.taskflow.domain.model.Project;
import com.team7.taskflow.domain.model.Task;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.Query;

/**
 * Retrofit API interface for Notifications.
 * Maps to Supabase REST API endpoint: /notifications
 */
public interface NotificationApi {

    /**
     * Get notifications for the current user, newest first.
     * Joins actor info (display_name, avatar_url) via the FK on actor_id.
     *
     * Example Supabase call:
     * GET /notifications?user_id=eq.{userId}
     * &select=*,actor:users!notifications_actor_id_fkey(display_name,avatar_url)
     * &order=created_at.desc
     */
    @GET("notifications")
    Call<List<Notification>> getNotifications(
            @Query("user_id") String userIdFilter,
            @Query("select") String select,
            @Query("order") String order);

    /**
     * Get notifications filtered by type.
     */
    @GET("notifications")
    Call<List<Notification>> getNotificationsByType(
            @Query("user_id") String userIdFilter,
            @Query("type") String typeFilter,
            @Query("select") String select,
            @Query("order") String order);

    /**
     * Mark a single notification as read.
     * PATCH /notifications?notification_id=eq.{id}
     */
    @PATCH("notifications")
    Call<Void> markAsRead(
            @Query("notification_id") String notificationIdFilter,
            @Body Map<String, Object> body);

    /**
     * Mark all notifications as read for a user.
     * PATCH /notifications?user_id=eq.{userId}&is_read=eq.false
     */
    @PATCH("notifications")
    Call<Void> markAllAsRead(
            @Query("user_id") String userIdFilter,
            @Query("is_read") String isReadFilter,
            @Body Map<String, Object> body);

    // ── Helper endpoints to resolve reference names ─────────────────

    /**
     * Get project by ID (to resolve reference_id → project_name for invite
     * notifications).
     * Reuses the projects endpoint.
     */
    @GET("projects")
    Call<List<Project>> getProjectById(
            @Query("project_id") String projectIdFilter,
            @Query("select") String select);

    /**
     * Get task by ID (to resolve reference_id → task title for task-related
     * notifications).
     * Reuses the tasks endpoint.
     */
    @GET("tasks")
    Call<List<Task>> getTaskById(
            @Query("task_id") String taskIdFilter,
            @Query("select") String select);

    /**
     * Batch-fetch projects by IDs.
     * GET /projects?project_id=in.(1,2,3)&select=project_id,project_name
     */
    @GET("projects")
    Call<List<Project>> getProjectsByIds(
            @Query("project_id") String projectIdFilter,
            @Query("select") String select);

    /**
     * Batch-fetch tasks by IDs.
     * GET /tasks?task_id=in.(1,2,3)&select=task_id,title,project_id
     */
    @GET("tasks")
    Call<List<Task>> getTasksByIds(
            @Query("task_id") String taskIdFilter,
            @Query("select") String select);
}
