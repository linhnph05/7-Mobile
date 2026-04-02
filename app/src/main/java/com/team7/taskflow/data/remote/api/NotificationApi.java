package com.team7.taskflow.data.remote.api;

import com.team7.taskflow.domain.model.Notification;
import com.team7.taskflow.domain.model.Project;
import com.team7.taskflow.domain.model.Task;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.Query;

public interface NotificationApi {

    @GET("notifications")
    Call<List<Notification>> getNotifications(
            @Query("user_id") String userIdFilter,
            @Query("select") String select,
            @Query("order") String order);

    @GET("notifications")
    Call<List<Notification>> getNotificationsByType(
            @Query("user_id") String userIdFilter,
            @Query("type") String typeFilter,
            @Query("select") String select,
            @Query("order") String order);

    @PATCH("notifications")
    Call<Void> markAsRead(
            @Query("notification_id") String notificationIdFilter,
            @Body Map<String, Object> body);

    @PATCH("notifications")
    Call<Void> markAllAsRead(
            @Query("user_id") String userIdFilter,
            @Query("is_read") String isReadFilter,
            @Body Map<String, Object> body);

    // ✅ Xóa notification sau khi Accept/Decline invite
    @DELETE("notifications")
    Call<Void> deleteNotification(
            @Query("notification_id") String notificationIdFilter,
            @retrofit2.http.Header("Prefer") String prefer);

    @GET("projects")
    Call<List<Project>> getProjectById(
            @Query("project_id") String projectIdFilter,
            @Query("select") String select);

    @GET("tasks")
    Call<List<Task>> getTaskById(
            @Query("task_id") String taskIdFilter,
            @Query("select") String select);

    @GET("projects")
    Call<List<Project>> getProjectsByIds(
            @Query("project_id") String projectIdFilter,
            @Query("select") String select);

    @GET("tasks")
    Call<List<Task>> getTasksByIds(
            @Query("task_id") String taskIdFilter,
            @Query("select") String select);
}