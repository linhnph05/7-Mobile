package com.team7.taskflow.data.repository;

import androidx.annotation.NonNull;

import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.remote.SupabaseConfig;
import com.team7.taskflow.data.remote.api.ActivityApi;
import com.team7.taskflow.data.remote.api.ProjectApi;
import com.team7.taskflow.data.remote.api.TaskApi;
import com.team7.taskflow.domain.model.ProjectMember;
import com.team7.taskflow.domain.model.ProjectActivity;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.domain.model.TaskActivity;
import com.team7.taskflow.domain.model.User;
import com.team7.taskflow.domain.model.Comment;
import com.team7.taskflow.domain.model.CommentReaction;
import com.team7.taskflow.utils.SessionManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repository for Task data operations
 * Handles communication with Supabase API
 */
public class TaskRepository {

    private static TaskRepository instance;
    private final TaskApi taskApi;
    private final ActivityApi activityApi;
    private final ProjectApi projectApi;
    private final com.team7.taskflow.data.remote.api.StorageApi storageApi;
    private final Map<Long, String> statusBeforeTrashCache = new HashMap<>();

    private TaskRepository() {
        taskApi = SupabaseClient.getInstance().getService(TaskApi.class);
        activityApi = SupabaseClient.getInstance().getService(ActivityApi.class);
        projectApi = SupabaseClient.getInstance().getService(ProjectApi.class);
        storageApi = SupabaseClient.getInstance()
                .getStorageService(com.team7.taskflow.data.remote.api.StorageApi.class);
    }

    public static synchronized TaskRepository getInstance() {
        if (instance == null) {
            instance = new TaskRepository();
        }
        return instance;
    }

    public interface TaskCallback<T> {
        void onSuccess(T result);

        void onError(String error);
    }

    // ── Create ──────────────────────────────────────────────────────────

    public void createTask(Task task, TaskCallback<Task> callback) {
        taskApi.createTask(task, SupabaseConfig.PREFER_RETURN_REPRESENTATION)
                .enqueue(new Callback<List<Task>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Task>> call, @NonNull Response<List<Task>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            Task created = response.body().get(0);
                            callback.onSuccess(created);
                            String actorId = SessionManager.getUserId();
                            logTaskActivity(
                                    created.getId(),
                                actorId,
                                    "CREATE",
                                    null,
                                    created.getStatus() != null ? created.getStatus() : "TODO");
                            logProjectActivity(
                                    created.getProjectId(),
                                actorId,
                                    "TASK",
                                    created.getId(),
                                    "CREATE",
                                    null,
                                    created.getTitle());
                        } else {
                            callback.onError("Failed to create task: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Task>> call, @NonNull Throwable t) {
                        callback.onError(t.getMessage());
                    }
                });
    }

    // ── Update ──────────────────────────────────────────────────────────

    public void updateTask(long taskId, Task task, TaskCallback<Task> callback) {
        taskApi.updateTaskFields("eq." + taskId, getTaskMap(task), SupabaseConfig.PREFER_RETURN_REPRESENTATION)
                .enqueue(new Callback<List<Task>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Task>> call, @NonNull Response<List<Task>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            Task updated = response.body().get(0);
                            callback.onSuccess(updated);
                            logProjectActivity(
                                    updated.getProjectId(),
                                    SessionManager.getUserId(),
                                    "TASK",
                                    updated.getId(),
                                    "UPDATE",
                                    null,
                                    updated.getTitle());
                        } else {
                            callback.onError("Update failed");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Task>> call, @NonNull Throwable t) {
                        callback.onError(t.getMessage());
                    }
                });
    }

    public void getTaskById(long taskId, TaskCallback<Task> callback) {
        taskApi.getTaskById("eq." + taskId).enqueue(new Callback<List<Task>>() {
            @Override
            public void onResponse(@NonNull Call<List<Task>> call, @NonNull Response<List<Task>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    callback.onSuccess(response.body().get(0));
                } else {
                    callback.onError("Task not found");
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Task>> call, @NonNull Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void updateTaskStatus(long taskId, String oldStatus, String newStatus, TaskCallback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", newStatus);

        taskApi.updateTaskFields("eq." + taskId, updates, null).enqueue(new Callback<List<Task>>() {
            @Override
            public void onResponse(@NonNull Call<List<Task>> call, @NonNull Response<List<Task>> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(null);
                    logTaskActivity(taskId, SessionManager.getUserId(), "UPDATE_STATUS", oldStatus, newStatus);
                    logProjectActivityByTaskId(taskId, userIdOrSession(userIdFromStatuses(oldStatus, newStatus)), "TASK", taskId,
                            "UPDATE_STATUS", oldStatus, newStatus);
                } else {
                    callback.onError("Failed to update status");
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Task>> call, @NonNull Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    // ── Delete ──────────────────────────────────────────────────────────

    public void softDeleteTask(long taskId, TaskCallback<Void> callback) {
        getTaskById(taskId, new TaskCallback<Task>() {
            @Override
            public void onSuccess(Task task) {
                String previousStatus = task != null && task.getStatus() != null
                        ? task.getStatus().trim()
                        : "TODO";
                performSoftDelete(taskId, previousStatus, callback);
            }

            @Override
            public void onError(String error) {
                performSoftDelete(taskId, "TODO", callback);
            }
        });
    }

    private void performSoftDelete(long taskId, String previousStatus, TaskCallback<Void> callback) {
        statusBeforeTrashCache.put(taskId, previousStatus);
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "TRASH");

        taskApi.updateTaskFields("eq." + taskId, updates, null).enqueue(new Callback<List<Task>>() {
            @Override
            public void onResponse(@NonNull Call<List<Task>> call, @NonNull Response<List<Task>> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(null);
                    logTaskActivity(taskId, SessionManager.getUserId(), "DELETE", previousStatus, "TRASH");
                    logProjectActivityByTaskId(taskId, SessionManager.getUserId(), "TASK", taskId,
                            "DELETE", previousStatus, "TRASH");
                } else {
                    callback.onError("Failed to delete task");
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Task>> call, @NonNull Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void deleteTask(long taskId, TaskCallback<Void> callback) {
        getTaskById(taskId, new TaskCallback<Task>() {
            @Override
            public void onSuccess(Task taskBeforeDelete) {
                taskApi.deleteTask("eq." + taskId).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            logTaskActivity(taskId, SessionManager.getUserId(), "HARD_DELETE", "TRASH", "DELETED");
                            if (taskBeforeDelete != null && taskBeforeDelete.getProjectId() > 0) {
                                logProjectActivity(
                                        taskBeforeDelete.getProjectId(),
                                        SessionManager.getUserId(),
                                        "TASK",
                                        taskId,
                                        "HARD_DELETE",
                                        "TRASH",
                                        "DELETED");
                            }
                            callback.onSuccess(null);
                        } else {
                            callback.onError("Delete failed: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        callback.onError(t.getMessage());
                    }
                });
            }

            @Override
            public void onError(String error) {
                taskApi.deleteTask("eq." + taskId).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            logTaskActivity(taskId, SessionManager.getUserId(), "HARD_DELETE", "TRASH", "DELETED");
                            callback.onSuccess(null);
                        } else {
                            callback.onError("Delete failed: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        callback.onError(t.getMessage());
                    }
                });
            }
        });
    }

    public void restoreTask(long taskId, TaskCallback<Void> callback) {
        getTaskHistory(taskId, new TaskCallback<List<TaskActivity>>() {
            @Override
            public void onSuccess(List<TaskActivity> history) {
                String restoreStatus = statusBeforeTrashCache.getOrDefault(taskId, "TODO");
                if (history != null) {
                    for (TaskActivity activity : history) {
                        String action = activity.getActionType() != null ? activity.getActionType().trim().toUpperCase() : "";
                        String newValueUpper = activity.getNewValue() != null ? activity.getNewValue().trim().toUpperCase() : "";
                        String oldValueRaw = activity.getOldValue() != null ? activity.getOldValue().trim() : "";
                        String oldValueUpper = oldValueRaw.toUpperCase();
                        if (("DELETE".equals(action) || "UPDATE_STATUS".equals(action))
                                && "TRASH".equals(newValueUpper)
                                && !oldValueRaw.isEmpty()
                                && !"TRASH".equals(oldValueUpper)
                                && !"DELETED".equals(oldValueUpper)) {
                            restoreStatus = oldValueRaw;
                            break;
                        }
                    }
                }
                applyRestore(taskId, restoreStatus, callback);
            }

            @Override
            public void onError(String error) {
                applyRestore(taskId, "TODO", callback);
            }
        });
    }

    private void applyRestore(long taskId, String restoreStatus, TaskCallback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", restoreStatus);

        taskApi.updateTaskFields("eq." + taskId, updates, null).enqueue(new Callback<List<Task>>() {
            @Override
            public void onResponse(@NonNull Call<List<Task>> call, @NonNull Response<List<Task>> response) {
                if (response.isSuccessful()) {
                    statusBeforeTrashCache.remove(taskId);
                    callback.onSuccess(null);
                    logTaskActivity(taskId, SessionManager.getUserId(), "RESTORE", "TRASH", restoreStatus);
                    logProjectActivityByTaskId(taskId, SessionManager.getUserId(), "TASK", taskId,
                            "RESTORE", "TRASH", restoreStatus);
                } else {
                    callback.onError("Failed to restore task");
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Task>> call, @NonNull Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void permanentlyDeleteTask(long taskId, TaskCallback<Void> callback) {
        deleteTask(taskId, callback);
    }

    // ── Attachments ─────────────────────────────────────────────────────

    public void uploadTaskAttachment(long taskId, android.net.Uri fileUri, String fileName, String mimeType,
            android.content.ContentResolver contentResolver,
            TaskCallback<com.team7.taskflow.domain.model.Attachment> callback) {
        try {
            java.io.InputStream inputStream = contentResolver.openInputStream(fileUri);
            if (inputStream == null) {
                callback.onError("Could not open file");
                return;
            }

            // Read bytes
            java.io.ByteArrayOutputStream byteBuffer = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }
            byte[] bytes = byteBuffer.toByteArray();

            // Prepare for upload
            String bucket = "task_attachments";
            String userId = SessionManager.getUserId();
            long timestamp = System.currentTimeMillis();
            
            // Format: filename_timestamp.extension
            String finalFileName = fileName;
            int dotIndex = fileName.lastIndexOf(".");
            if (dotIndex != -1) {
                finalFileName = fileName.substring(0, dotIndex) + "_" + timestamp + fileName.substring(dotIndex);
            } else {
                finalFileName = fileName + "_" + timestamp;
            }

            // Path includes userId for RLS compliance
            String path = userId + "/" + taskId + "/" + finalFileName;
            okhttp3.RequestBody requestBody = okhttp3.RequestBody.create(bytes,
                    okhttp3.MediaType.parse(mimeType != null ? mimeType : "application/octet-stream"));

            // Upload to storage with x-upsert=true
            storageApi.uploadFile(bucket, path, requestBody, "true").enqueue(new Callback<okhttp3.ResponseBody>() {
                @Override
                public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                    if (response.isSuccessful()) {
                        String publicUrl = SupabaseConfig.SUPABASE_URL + "/storage/v1/object/public/" + bucket + "/"
                                + path;

                        // Insert into attachments table with resolution=merge-duplicates to avoid 409
                        com.team7.taskflow.domain.model.Attachment attachment = new com.team7.taskflow.domain.model.Attachment(
                                taskId, SessionManager.getUserId(), publicUrl, fileName, mimeType);

                        taskApi.addAttachment(attachment, "resolution=merge-duplicates").enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(Call<Void> call, Response<Void> response) {
                                if (response.isSuccessful())
                                    callback.onSuccess(attachment);
                                else
                                    callback.onError("Failed to link attachment to task: " + response.code());
                            }

                            @Override
                            public void onFailure(Call<Void> call, Throwable t) {
                                callback.onError(t.getMessage());
                            }
                        });
                    } else {
                        callback.onError("Upload failed. Make sure bucket '" + bucket + "' exists. " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                    callback.onError("Network error: " + t.getMessage());
                }
            });

        } catch (java.io.IOException e) {
            callback.onError(e.getMessage());
        }
    }

    public void getTaskAttachments(long taskId, TaskCallback<List<com.team7.taskflow.domain.model.Attachment>> callback) {
        taskApi.getAttachmentsByTask("eq." + taskId).enqueue(new Callback<List<com.team7.taskflow.domain.model.Attachment>>() {
            @Override
            public void onResponse(Call<List<com.team7.taskflow.domain.model.Attachment>> call, Response<List<com.team7.taskflow.domain.model.Attachment>> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Failed to load attachments: " + response.code());
                }
            }
            @Override
            public void onFailure(Call<List<com.team7.taskflow.domain.model.Attachment>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void deleteTaskAttachment(long attachmentId, TaskCallback<Void> callback) {
        taskApi.deleteAttachment("eq." + attachmentId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(null);
                } else {
                    callback.onError("Failed to delete attachment: " + response.code());
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    // ── History ─────────────────────────────────────────────────────────
    public void getProjectMembers(long projectId, TaskCallback<List<User>> callback) {
        // Query: /project_members?project_id=eq.{id}&select=*,users(*)
        projectApi.getProjectMembers("eq." + projectId, "*,users(*)")
                .enqueue(new retrofit2.Callback<List<ProjectMember>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<ProjectMember>> call,
                                           @NonNull retrofit2.Response<List<ProjectMember>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<User> users = new java.util.ArrayList<>();
                            for (ProjectMember pm : response.body()) {
                                if (pm == null || pm.isRemoved()) {
                                    continue;
                                }
                                User u = new User();
                                if (pm.getUserInfo() != null) {
                                    ProjectMember.UserInfo info = pm.getUserInfo();
                                    u.setUserId(info.userId != null ? info.userId : pm.getUserId());
                                    u.setDisplayName(info.displayName);
                                    u.setEmail(info.email);
                                    u.setAvatarUrl(info.avatarUrl);
                                } else {
                                    // Fallback: tạo User tối giản chỉ từ userId
                                    u.setUserId(pm.getUserId());
                                    u.setDisplayName(pm.getUserId());
                                }
                                users.add(u);
                            }
                            callback.onSuccess(users);
                        } else {
                            callback.onError("Failed to load members: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<ProjectMember>> call, @NonNull Throwable t) {
                        callback.onError(t.getMessage());
                    }
                });
    }
    public void getTaskHistory(long taskId, TaskCallback<List<TaskActivity>> callback) {
        activityApi.getActivitiesByTask("eq." + taskId, "created_at.desc").enqueue(new Callback<List<TaskActivity>>() {
            @Override
            public void onResponse(Call<List<TaskActivity>> call, Response<List<TaskActivity>> response) {
                if (response.isSuccessful())
                    callback.onSuccess(response.body());
                else
                    callback.onError("Failed to load history");
            }

            @Override
            public void onFailure(Call<List<TaskActivity>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void getTaskComments(long taskId, TaskCallback<List<Comment>> callback) {
        String select = "comment_id,task_id,user_id,content,created_at," 
            + "users(user_id,display_name,avatar_url)";
        taskApi.getCommentsByTask("eq." + taskId, select, "created_at.desc").enqueue(new Callback<List<Comment>>() {
            @Override
            public void onResponse(@NonNull Call<List<Comment>> call, @NonNull Response<List<Comment>> response) {
                if (response.isSuccessful()) {
                    List<Comment> comments = response.body();
                    loadCommentReactionCounts(comments != null ? comments : new java.util.ArrayList<>(), new TaskCallback<List<Comment>>() {
                        @Override
                        public void onSuccess(List<Comment> result) {
                            callback.onSuccess(result);
                        }

                        @Override
                        public void onError(String error) {
                            callback.onError(error);
                        }
                    });
                } else {
                    callback.onError("Failed to load comments: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Comment>> call, @NonNull Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void createTaskComment(long taskId, String userId, String content, TaskCallback<Comment> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("task_id", taskId);
        body.put("user_id", userId);
        body.put("content", content);

        taskApi.createComment(body, SupabaseConfig.PREFER_RETURN_REPRESENTATION).enqueue(new Callback<List<Comment>>() {
            @Override
            public void onResponse(@NonNull Call<List<Comment>> call, @NonNull Response<List<Comment>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Comment createdComment = response.body().get(0);
                    callback.onSuccess(createdComment);
                        logProjectActivityByTaskId(taskId, userId, "TASK", taskId,
                            "COMMENT_CREATE", null, content);
                } else {
                    callback.onError("Failed to create comment: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Comment>> call, @NonNull Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void updateTaskComment(long commentId, String userId, String content, TaskCallback<Comment> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);

        taskApi.updateComment(
                "eq." + commentId,
                "eq." + userId,
                body,
                SupabaseConfig.PREFER_RETURN_REPRESENTATION).enqueue(new Callback<List<Comment>>() {
            @Override
            public void onResponse(@NonNull Call<List<Comment>> call, @NonNull Response<List<Comment>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Comment updated = response.body().get(0);
                    callback.onSuccess(updated);
                    logProjectActivityByTaskId(
                            updated.getTaskId() != null ? updated.getTaskId() : -1,
                            userId,
                            "TASK",
                            updated.getTaskId() != null ? updated.getTaskId() : -1,
                            "COMMENT_UPDATE",
                            null,
                            content);
                } else {
                    callback.onError("Failed to update comment: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Comment>> call, @NonNull Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void deleteTaskComment(long commentId, String userId, TaskCallback<Void> callback) {
        taskApi.getCommentById("eq." + commentId, "comment_id,task_id,content").enqueue(new Callback<List<Comment>>() {
            @Override
            public void onResponse(@NonNull Call<List<Comment>> call, @NonNull Response<List<Comment>> response) {
                Comment existing = null;
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    existing = response.body().get(0);
                }
                final Comment finalExisting = existing;

                taskApi.deleteComment("eq." + commentId, "eq." + userId).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        if (response.isSuccessful()) {
                            callback.onSuccess(null);
                            long taskId = finalExisting != null && finalExisting.getTaskId() != null ? finalExisting.getTaskId() : -1;
                            logProjectActivityByTaskId(
                                    taskId,
                                    userId,
                                    "TASK",
                                    taskId,
                                    "COMMENT_DELETE",
                                    finalExisting != null ? finalExisting.getContent() : null,
                                    null);
                        } else {
                            callback.onError("Failed to delete comment: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        callback.onError(t.getMessage());
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<List<Comment>> call, @NonNull Throwable t) {
                // Fallback: still try delete even if prefetch fails.
                taskApi.deleteComment("eq." + commentId, "eq." + userId).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        if (response.isSuccessful()) {
                            callback.onSuccess(null);
                            logProjectActivityByTaskId(-1L, userId, "TASK", -1L, "COMMENT_DELETE", null, null);
                        } else {
                            callback.onError("Failed to delete comment: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        callback.onError(t.getMessage());
                    }
                });
            }
        });
    }

    public void toggleCommentReaction(long commentId, String userId, String reactionType, TaskCallback<Void> callback) {
        String normalizedReaction = reactionType != null ? reactionType.trim().toUpperCase() : "";
        if (!"LIKE".equals(normalizedReaction) && !"LOVE".equals(normalizedReaction) && !"CELEBRATE".equals(normalizedReaction)) {
            callback.onError("Invalid reaction type");
            return;
        }

        taskApi.getCommentReactions("eq." + commentId, "eq." + userId, "eq." + normalizedReaction).enqueue(new Callback<List<CommentReaction>>() {
            @Override
            public void onResponse(@NonNull Call<List<CommentReaction>> call, @NonNull Response<List<CommentReaction>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    CommentReaction existing = response.body().get(0);
                    if (existing.getId() == null) {
                        callback.onError("Reaction not found");
                        return;
                    }
                    taskApi.deleteCommentReaction("eq." + existing.getId()).enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                            if (response.isSuccessful()) {
                                callback.onSuccess(null);
                                logReactionProjectActivity(commentId, userId, normalizedReaction, "REMOVE_REACTION");
                            } else {
                                callback.onError("Failed to remove reaction: " + response.code());
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                            callback.onError(t.getMessage());
                        }
                    });
                    return;
                }

                Map<String, Object> body = new HashMap<>();
                body.put("comment_id", commentId);
                body.put("user_id", userId);
                body.put("reaction_type", normalizedReaction);

                taskApi.createCommentReaction(body, SupabaseConfig.PREFER_RETURN_REPRESENTATION)
                        .enqueue(new Callback<List<CommentReaction>>() {
                            @Override
                            public void onResponse(@NonNull Call<List<CommentReaction>> call, @NonNull Response<List<CommentReaction>> response) {
                                if (response.isSuccessful()) {
                                    callback.onSuccess(null);
                                    logReactionProjectActivity(commentId, userId, normalizedReaction, "ADD_REACTION");
                                } else {
                                    callback.onError("Failed to save reaction: " + response.code());
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<List<CommentReaction>> call, @NonNull Throwable t) {
                                callback.onError(t.getMessage());
                            }
                        });
            }

            @Override
            public void onFailure(@NonNull Call<List<CommentReaction>> call, @NonNull Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    private void logReactionProjectActivity(long commentId, String userId, String reactionType, String actionType) {
        taskApi.getCommentById("eq." + commentId, "comment_id,task_id").enqueue(new Callback<List<Comment>>() {
            @Override
            public void onResponse(@NonNull Call<List<Comment>> call, @NonNull Response<List<Comment>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Comment comment = response.body().get(0);
                    long taskId = comment.getTaskId() != null ? comment.getTaskId() : -1;
                    logProjectActivityByTaskId(
                            taskId,
                            userId,
                            "TASK",
                            taskId,
                            actionType,
                            null,
                            reactionType);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Comment>> call, @NonNull Throwable t) {
                // Ignore logging failures
            }
        });
    }

    private void loadCommentReactionCounts(List<Comment> comments, TaskCallback<List<Comment>> callback) {
        if (comments == null || comments.isEmpty()) {
            callback.onSuccess(comments);
            return;
        }

        final int[] remaining = {comments.size()};
        final String currentUserId = SessionManager.getUserId();

        for (Comment comment : comments) {
            Long commentId = comment.getId();
            if (commentId == null) {
                remaining[0]--;
                if (remaining[0] == 0) {
                    callback.onSuccess(comments);
                }
                continue;
            }

            taskApi.getCommentReactions("eq." + commentId, null, null).enqueue(new Callback<List<CommentReaction>>() {
                @Override
                public void onResponse(@NonNull Call<List<CommentReaction>> call, @NonNull Response<List<CommentReaction>> response) {
                    applyReactionCounts(comment, response.body(), currentUserId);
                    if (--remaining[0] == 0) {
                        callback.onSuccess(comments);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<List<CommentReaction>> call, @NonNull Throwable t) {
                    applyReactionCounts(comment, null, currentUserId);
                    if (--remaining[0] == 0) {
                        callback.onSuccess(comments);
                    }
                }
            });
        }
    }

    private void applyReactionCounts(Comment comment, List<CommentReaction> reactions, String currentUserId) {
        int like = 0;
        int heart = 0;
        int congrats = 0;
        boolean likeSelected = false;
        boolean heartSelected = false;
        boolean congratsSelected = false;

        if (reactions != null) {
            for (CommentReaction reaction : reactions) {
                String type = reaction.getReactionType() != null ? reaction.getReactionType().toUpperCase() : "";
                boolean isCurrentUser = currentUserId != null && currentUserId.equals(reaction.getUserId());

                if ("LIKE".equals(type)) {
                    like++;
                    if (isCurrentUser) {
                        likeSelected = true;
                    }
                } else if ("LOVE".equals(type)) {
                    heart++;
                    if (isCurrentUser) {
                        heartSelected = true;
                    }
                } else if ("CELEBRATE".equals(type)) {
                    congrats++;
                    if (isCurrentUser) {
                        congratsSelected = true;
                    }
                }
            }
        }

        comment.setLikeCount(like);
        comment.setHeartCount(heart);
        comment.setCongratsCount(congrats);
        comment.setLikeSelected(likeSelected);
        comment.setHeartSelected(heartSelected);
        comment.setCongratsSelected(congratsSelected);
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private Map<String, Object> getTaskMap(Task task) {
        Map<String, Object> map = new HashMap<>();
        if (task.getTitle() != null)
            map.put("title", task.getTitle());
        if (task.getDescription() != null)
            map.put("description", task.getDescription());
        if (task.getStatus() != null)
            map.put("status", task.getStatus());
        if (task.getPriority() != null)
            map.put("priority", task.getPriority());
        if (task.getPosition() != null)
            map.put("position", task.getPosition());
        // Optional fields used by detail/edit screens – always send them so
        // clearing values (null) is reflected in Supabase
        map.put("due_date", task.getDueDate());
        map.put("start_date", task.getStartDate());
        map.put("assignee_id", task.getAssigneeId());
        map.put("tag", task.getTag());
        map.put("parent_task_id", task.getParentTaskId());
        return map;
    }

    public void getTasksByProject(long projectId, TaskCallback<List<Task>> callback) {
        taskApi.getTasksByProject("eq." + projectId, "position.asc").enqueue(new Callback<List<Task>>() {
            @Override
            public void onResponse(Call<List<Task>> call, Response<List<Task>> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Load failed");
                }
            }

            @Override
            public void onFailure(Call<List<Task>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void getTasksByProjectAndStatus(long projectId, String status, TaskCallback<List<Task>> callback) {
        taskApi.getTasksByStatus("eq." + projectId, "eq." + status, "created_at.desc").enqueue(new Callback<List<Task>>() {
            @Override
            public void onResponse(Call<List<Task>> call, Response<List<Task>> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Load tasks by status failed: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<Task>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    private void logProjectActivity(long projectId, String userId, String entityType, Long entityId,
            String actionType, String oldValue, String newValue) {
        if (projectId <= 0) {
            return;
        }
        ProjectActivity activity = new ProjectActivity(
                projectId,
                userIdOrSession(userId),
                actionType,
                entityType,
                entityId,
                oldValue,
                newValue);
        projectApi.createProjectActivity(activity).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                // Fire-and-forget for dashboard counters.
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                // Ignore logging failures to avoid blocking task actions.
            }
        });
    }

    private void logTaskActivity(long taskId, String userId, String actionType, String oldValue, String newValue) {
        if (taskId <= 0) {
            return;
        }
        TaskActivity activity = new TaskActivity(
                taskId,
                userIdOrSession(userId),
                actionType,
                oldValue,
                newValue);
        activityApi.logActivity(activity).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                // Fire-and-forget
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                // Ignore activity logging failures to avoid blocking main flow.
            }
        });
    }

    private void logProjectActivityByTaskId(long taskId, String userId, String entityType, Long entityId,
            String actionType, String oldValue, String newValue) {
        if (taskId <= 0) {
            return;
        }
        getTaskById(taskId, new TaskCallback<Task>() {
            @Override
            public void onSuccess(Task task) {
                if (task == null) {
                    return;
                }
                logProjectActivity(task.getProjectId(), userId, entityType, entityId, actionType, oldValue, newValue);
            }

            @Override
            public void onError(String error) {
                // Ignore logging fallback errors.
            }
        });
    }

    private String userIdOrSession(String userId) {
        if (userId != null && !userId.trim().isEmpty()) {
            return userId;
        }
        return SessionManager.getUserId();
    }

    private String userIdFromStatuses(String oldStatus, String newStatus) {
        return SessionManager.getUserId();
    }

    /**
     * Get tasks assigned to a specific user
     */
    public void getMyTasks(String userId, TaskCallback<List<Task>> callback) {
        taskApi.getTasksByAssignee("*", "eq." + userId, "created_at.desc").enqueue(new Callback<List<Task>>() {
            @Override
            public void onResponse(Call<List<Task>> call, Response<List<Task>> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Load my tasks failed: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<Task>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    /**
     * Get tasks assigned to a specific user, joining with project to load project name
     */
    public void getMyTasksWithProjectName(String userId, TaskCallback<List<Task>> callback) {
        // Use Supabase PostgREST syntax: *,projects(*) to include related project data
        taskApi.getTasksByAssignee("*,projects(*)", "eq." + userId, "created_at.desc").enqueue(new Callback<List<Task>>() {
            @Override
            public void onResponse(Call<List<Task>> call, Response<List<Task>> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(response.body());
                } else {
                    callback.onError("Load my tasks failed: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<Task>> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void getMyTasksWithProjectNameByStatus(String userId, String status, TaskCallback<List<Task>> callback) {
        taskApi.getTasksByAssigneeAndStatus(
                "*,projects(*)",
                "eq." + userId,
                "eq." + status,
                "created_at.desc").enqueue(new Callback<List<Task>>() {
                    @Override
                    public void onResponse(Call<List<Task>> call, Response<List<Task>> response) {
                        if (response.isSuccessful()) {
                            callback.onSuccess(response.body());
                        } else {
                            callback.onError("Load my tasks by status failed: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Task>> call, Throwable t) {
                        callback.onError(t.getMessage());
                    }
                });
    }
}