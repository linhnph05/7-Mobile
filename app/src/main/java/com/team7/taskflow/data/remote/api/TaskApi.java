package com.team7.taskflow.data.remote.api;

import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.domain.model.Comment;
import com.team7.taskflow.domain.model.CommentReaction;
import com.team7.taskflow.domain.model.TaskActivity;

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

    @GET("tasks")
    Call<List<Task>> getTasksByProjectWithSelect(
            @Query("project_id") String projectIdFilter,
            @Query("select") String select,
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
            @Query("select") String select,
            @Query("assignee_id") String assigneeIdFilter,
            @Query("order") String order
    );

    @GET("tasks")
    Call<List<Task>> getTasksByAssigneeAndStatus(
            @Query("select") String select,
            @Query("assignee_id") String assigneeIdFilter,
            @Query("status") String statusFilter,
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
     * Add attachment to task
     */
    @POST("attachments")
    Call<Void> addAttachment(
            @Body com.team7.taskflow.domain.model.Attachment attachment,
            @Header("Prefer") String prefer
    );

    /**
     * Get attachments by task ID
     */
    @GET("attachments")
    Call<List<com.team7.taskflow.domain.model.Attachment>> getAttachmentsByTask(
            @Query("task_id") String taskIdFilter
    );

    /**
     * Delete attachment by ID
     */
    @DELETE("attachments")
    Call<Void> deleteAttachment(
            @Query("attachment_id") String attachmentIdFilter
    );

    @GET("comments")
    Call<List<Comment>> getCommentsByTask(
            @Query("task_id") String taskIdFilter,
            @Query("is_deleted") String isDeletedFilter,
            @Query("select") String select,
            @Query("order") String order
    );

    @POST("comments")
    Call<List<Comment>> createComment(
            @Body Map<String, Object> body,
            @Header("Prefer") String prefer
    );

    @PATCH("comments")
    Call<List<Comment>> updateComment(
            @Query("comment_id") String commentIdFilter,
            @Query("user_id") String userIdFilter,
            @Body Map<String, Object> body,
            @Header("Prefer") String prefer
    );

    @GET("comments")
    Call<List<Comment>> getCommentById(
            @Query("comment_id") String commentIdFilter,
            @Query("select") String select
    );

    @PATCH("comments")
    Call<List<Comment>> updateCommentById(
            @Query("comment_id") String commentIdFilter,
            @Body Map<String, Object> body,
            @Header("Prefer") String prefer
    );

    @DELETE("comments")
    Call<Void> deleteComment(
            @Query("comment_id") String commentIdFilter,
            @Query("user_id") String userIdFilter
    );

    @GET("comment_reactions")
    Call<List<CommentReaction>> getCommentReactions(
            @Query("comment_id") String commentIdFilter,
            @Query("user_id") String userIdFilter,
            @Query("reaction_type") String reactionTypeFilter
    );

    @POST("comment_reactions")
    Call<List<CommentReaction>> createCommentReaction(
            @Body Map<String, Object> body,
            @Header("Prefer") String prefer
    );

    @DELETE("comment_reactions")
    Call<Void> deleteCommentReaction(
            @Query("reaction_id") String reactionIdFilter
    );

    @PATCH("comment_reactions")
    Call<List<CommentReaction>> updateCommentReaction(
            @Query("reaction_id") String reactionIdFilter,
            @Body Map<String, Object> body,
            @Header("Prefer") String prefer
    );

    @GET("task_activities")
    Call<List<TaskActivity>> getTaskActivities(
            @Query("task_id") String taskIdFilter,
            @Query("order") String order
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
