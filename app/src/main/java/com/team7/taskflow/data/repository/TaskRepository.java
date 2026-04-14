package com.team7.taskflow.data.repository;

import androidx.annotation.NonNull;

import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.remote.SupabaseConfig;
import com.team7.taskflow.data.remote.api.ProjectApi;
import com.team7.taskflow.data.remote.api.TaskApi;
import com.team7.taskflow.domain.model.ProjectMember;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.domain.model.TaskActivity;
import com.team7.taskflow.domain.model.User;
import com.team7.taskflow.domain.model.Comment;
import com.team7.taskflow.domain.model.CommentReaction;
import com.team7.taskflow.utils.SessionManager;

import java.io.IOException;
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
    private final ProjectApi projectApi;
    private final com.team7.taskflow.data.remote.api.StorageApi storageApi;
    private final Map<Long, String> statusBeforeTrashCache = new HashMap<>();

    private TaskRepository() {
        taskApi = SupabaseClient.getInstance().getService(TaskApi.class);
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
        Task payload = buildCreatePayload(task);
        taskApi.createTask(payload, SupabaseConfig.PREFER_RETURN_REPRESENTATION)
                .enqueue(new Callback<List<Task>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Task>> call, @NonNull Response<List<Task>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            Task created = response.body().get(0);
                            callback.onSuccess(created);
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
                        } else {
                            callback.onError("Update failed: " + buildApiError("update_task", response));
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
                } else {
                    callback.onError("Failed to update status: " + buildApiError("update_task_status", response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Task>> call, @NonNull Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    /**
     * Update all subtasks of a parent task to DONE status.
     * Called when parent task is marked as DONE.
     */
    public void updateSubtasksStatus(long parentTaskId, TaskCallback<Void> callback) {
        // First get all tasks with parent_task_id = parentTaskId
        taskApi.getTasksByParentId("eq." + parentTaskId).enqueue(new Callback<List<Task>>() {
            @Override
            public void onResponse(@NonNull Call<List<Task>> call, @NonNull Response<List<Task>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    List<Task> subtasks = response.body();
                    // Update each subtask to DONE status
                    updateSubtasksRecursively(subtasks, 0, callback);
                } else {
                    // No subtasks found, success
                    callback.onSuccess(null);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Task>> call, @NonNull Throwable t) {
                // Log error but don't fail - cascading is optional
                callback.onSuccess(null);
            }
        });
    }

    private void updateSubtasksRecursively(List<Task> subtasks, int index, TaskCallback<Void> callback) {
        if (index >= subtasks.size()) {
            callback.onSuccess(null);
            return;
        }

        Task subtask = subtasks.get(index);
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "DONE");

        taskApi.updateTaskFields("eq." + subtask.getId(), updates, null).enqueue(new Callback<List<Task>>() {
            @Override
            public void onResponse(@NonNull Call<List<Task>> call, @NonNull Response<List<Task>> response) {
                // Continue to next subtask regardless of success/failure
                updateSubtasksRecursively(subtasks, index + 1, callback);
            }

            @Override
            public void onFailure(@NonNull Call<List<Task>> call, @NonNull Throwable t) {
                // Continue to next subtask
                updateSubtasksRecursively(subtasks, index + 1, callback);
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

                        // Đảm bảo Token luôn mới nhất để vượt qua Policy RLS (auth.uid() = uploader_id)
                        String currentToken = SessionManager.getAccessToken();
                        String currentUserId = SessionManager.getUserId();
                        
                        if (currentToken != null && !currentToken.isEmpty()) {
                            SupabaseClient.getInstance().setAccessToken(currentToken);
                        }

                        if (currentUserId == null || currentUserId.isEmpty()) {
                            callback.onError("Session expired. Please login again.");
                            return;
                        }

                        // Sử dụng Map thay vì Model để tránh lỗi gửi kèm "attachment_id": null
                        // Vì SupabaseClient cấu hình serializeNulls nên Model sẽ gửi cả ID=null làm DB báo lỗi.
                        java.util.Map<String, Object> attachmentData = new java.util.HashMap<>();
                        attachmentData.put("task_id", taskId);
                        attachmentData.put("uploader_id", currentUserId);
                        attachmentData.put("file_url", publicUrl);
                        attachmentData.put("file_name", fileName);
                        attachmentData.put("file_type", mimeType);

                        com.team7.taskflow.domain.model.Attachment attachment = new com.team7.taskflow.domain.model.Attachment(
                                taskId, currentUserId, publicUrl, fileName, mimeType);

                        taskApi.addAttachment(attachmentData, "return=minimal").enqueue(new Callback<Void>() {
                            @Override
                            public void onResponse(Call<Void> call, Response<Void> response) {
                                if (response.isSuccessful()) {
                                    callback.onSuccess(attachment);
                                } else {
                                    callback.onError("Database Error: " + response.code());
                                }
                            }

                            @Override
                            public void onFailure(Call<Void> call, Throwable t) {
                                callback.onError("Network error linking attachment: " + t.getMessage());
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
        taskApi.getTaskActivities("eq." + taskId, "created_at.desc").enqueue(new Callback<List<TaskActivity>>() {
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
        String select = "comment_id,task_id,user_id,content,created_at,is_deleted," 
            + "users(user_id,display_name,avatar_url)";
        taskApi.getCommentsByTask("eq." + taskId, "eq.false", select, "created_at.desc").enqueue(new Callback<List<Comment>>() {
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
                    callback.onError("Failed to load comments: " + buildApiError("comments", response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Comment>> call, @NonNull Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void getCommentById(long commentId, TaskCallback<Comment> callback) {
        taskApi.getCommentById("eq." + commentId, "comment_id,task_id,user_id,content,is_deleted")
                .enqueue(new Callback<List<Comment>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Comment>> call,
                            @NonNull Response<List<Comment>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            callback.onSuccess(response.body().get(0));
                        } else {
                            callback.onError("Comment not found");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Comment>> call, @NonNull Throwable t) {
                        callback.onError(t.getMessage());
                    }
                });
    }

    public void createTaskComment(long taskId, String userId, String content, TaskCallback<Comment> callback) {
        String effectiveUserId = userId != null && !userId.trim().isEmpty()
                ? userId.trim()
                : SessionManager.getUserId();
        if (effectiveUserId == null || effectiveUserId.trim().isEmpty()) {
            callback.onError("Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.");
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("task_id", taskId);
        body.put("user_id", effectiveUserId);
        body.put("content", content);

        taskApi.createComment(body, SupabaseConfig.PREFER_RETURN_REPRESENTATION).enqueue(new Callback<List<Comment>>() {
            @Override
            public void onResponse(@NonNull Call<List<Comment>> call, @NonNull Response<List<Comment>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Comment createdComment = response.body().get(0);
                    callback.onSuccess(createdComment);
                } else {
                    callback.onError("Failed to create comment: " + buildApiError("create_comment", response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Comment>> call, @NonNull Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void updateTaskComment(long commentId, String userId, String content, TaskCallback<Comment> callback) {
        String effectiveUserId = userId != null && !userId.trim().isEmpty()
            ? userId.trim()
            : SessionManager.getUserId();
        if (effectiveUserId == null || effectiveUserId.trim().isEmpty()) {
            callback.onError("Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.");
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("content", content);

        taskApi.updateComment(
                "eq." + commentId,
            "eq." + effectiveUserId,
                body,
                SupabaseConfig.PREFER_RETURN_REPRESENTATION).enqueue(new Callback<List<Comment>>() {
            @Override
            public void onResponse(@NonNull Call<List<Comment>> call, @NonNull Response<List<Comment>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Comment updated = response.body().get(0);
                    callback.onSuccess(updated);
                } else {
                    callback.onError("Failed to update comment: " + buildApiError("update_comment", response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Comment>> call, @NonNull Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }

    public void deleteTaskComment(long commentId, String userId, TaskCallback<Void> callback) {
        String effectiveUserId = userId != null && !userId.trim().isEmpty()
                ? userId.trim()
                : SessionManager.getUserId();
        if (effectiveUserId == null || effectiveUserId.trim().isEmpty()) {
            callback.onError("Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.");
            return;
        }

        taskApi.getCommentById("eq." + commentId, "comment_id,task_id,content,is_deleted").enqueue(new Callback<List<Comment>>() {
            @Override
            public void onResponse(@NonNull Call<List<Comment>> call, @NonNull Response<List<Comment>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Comment existing = response.body().get(0);
                    if (existing != null && existing.isDeleted()) {
                        callback.onSuccess(null);
                        return;
                    }
                }

                Map<String, Object> body = new HashMap<>();
                body.put("is_deleted", true);

                taskApi.updateComment("eq." + commentId, "eq." + effectiveUserId, body, SupabaseConfig.PREFER_RETURN_REPRESENTATION)
                        .enqueue(new Callback<List<Comment>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Comment>> call, @NonNull Response<List<Comment>> response) {
                        if (response.isSuccessful()) {
                            callback.onSuccess(null);
                        } else {
                            callback.onError("Failed to delete comment: " + buildApiError("soft_delete_comment", response));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Comment>> call, @NonNull Throwable t) {
                        callback.onError(t.getMessage());
                    }
                });
            }

            @Override
            public void onFailure(@NonNull Call<List<Comment>> call, @NonNull Throwable t) {
                Map<String, Object> body = new HashMap<>();
                body.put("is_deleted", true);

                // Fallback: still try soft-delete even if prefetch fails.
                taskApi.updateComment("eq." + commentId, "eq." + effectiveUserId, body, SupabaseConfig.PREFER_RETURN_REPRESENTATION)
                        .enqueue(new Callback<List<Comment>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Comment>> call, @NonNull Response<List<Comment>> response) {
                        if (response.isSuccessful()) {
                            callback.onSuccess(null);
                        } else {
                            callback.onError("Failed to delete comment: " + buildApiError("soft_delete_comment_fallback", response));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Comment>> call, @NonNull Throwable t) {
                        callback.onError(t.getMessage());
                    }
                });
            }
        });
    }

    public void toggleCommentReaction(long commentId, String userId, String reactionType, TaskCallback<Void> callback) {
        String effectiveUserId = userId != null && !userId.trim().isEmpty()
                ? userId.trim()
                : SessionManager.getUserId();
        if (effectiveUserId == null || effectiveUserId.trim().isEmpty()) {
            callback.onError("Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.");
            return;
        }

        String normalizedReaction = reactionType != null ? reactionType.trim().toUpperCase() : "";
        if (!"LIKE".equals(normalizedReaction) && !"LOVE".equals(normalizedReaction) && !"CELEBRATE".equals(normalizedReaction)) {
            callback.onError("Invalid reaction type");
            return;
        }

        taskApi.getCommentReactions("eq." + commentId, "eq." + effectiveUserId, null).enqueue(new Callback<List<CommentReaction>>() {
            @Override
            public void onResponse(@NonNull Call<List<CommentReaction>> call, @NonNull Response<List<CommentReaction>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    CommentReaction existing = response.body().get(0);
                    if (existing.getId() == null) {
                        callback.onError("Reaction not found");
                        return;
                    }

                    String existingType = existing.getReactionType() != null
                            ? existing.getReactionType().trim().toUpperCase()
                            : "";

                    Map<String, Object> updateBody = new HashMap<>();
                    if (normalizedReaction.equals(existingType)) {
                        updateBody.put("reaction_type", "DELETED");
                    } else {
                        updateBody.put("reaction_type", normalizedReaction);
                    }

                    taskApi.updateCommentReaction(
                            "eq." + existing.getId(),
                            updateBody,
                            SupabaseConfig.PREFER_RETURN_REPRESENTATION)
                            .enqueue(new Callback<List<CommentReaction>>() {
                                @Override
                                public void onResponse(@NonNull Call<List<CommentReaction>> call,
                                        @NonNull Response<List<CommentReaction>> response) {
                                    if (response.isSuccessful()) {
                                        callback.onSuccess(null);
                                    } else {
                                        callback.onError("Failed to update reaction: "
                                                + buildApiError("update_reaction", response));
                                    }
                                }

                                @Override
                                public void onFailure(@NonNull Call<List<CommentReaction>> call,
                                        @NonNull Throwable t) {
                                    callback.onError(t.getMessage());
                                }
                            });
                    return;
                }

                Map<String, Object> body = new HashMap<>();
                body.put("comment_id", commentId);
                body.put("user_id", effectiveUserId);
                body.put("reaction_type", normalizedReaction);

                taskApi.createCommentReaction(body, SupabaseConfig.PREFER_RETURN_REPRESENTATION)
                        .enqueue(new Callback<List<CommentReaction>>() {
                            @Override
                            public void onResponse(@NonNull Call<List<CommentReaction>> call, @NonNull Response<List<CommentReaction>> response) {
                                if (response.isSuccessful()) {
                                    callback.onSuccess(null);
                                } else {
                                    callback.onError("Failed to save reaction: " + buildApiError("create_reaction", response));
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
        map.put("due_date", normalizeTimestamp(task.getDueDate()));
        map.put("start_date", normalizeTimestamp(task.getStartDate()));
        map.put("assignee_id", normalizeUuid(task.getAssigneeId()));
        map.put("tag", normalizeNullableText(task.getTag()));
        map.put("parent_task_id", task.getParentTaskId());
        return map;
    }

    private Task buildCreatePayload(Task source) {
        Task payload = new Task(source.getProjectId(), source.getTitle());
        payload.setDescription(normalizeNullableText(source.getDescription()));
        payload.setStatus(normalizeNullableText(source.getStatus()));
        payload.setPriority(normalizeNullableText(source.getPriority()));
        payload.setPosition(source.getPosition());
        payload.setDueDate(normalizeTimestamp(source.getDueDate()));
        payload.setStartDate(normalizeTimestamp(source.getStartDate()));
        payload.setAssigneeId(normalizeUuid(source.getAssigneeId()));
        payload.setParentTaskId(source.getParentTaskId());
        payload.setTag(normalizeNullableText(source.getTag()));
        return payload;
    }

    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeUuid(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        // Keep valid UUID-like values only; invalid user input should not be sent to DB.
        return trimmed.matches("^[0-9a-fA-F-]{36}$") ? trimmed : null;
    }

    private String normalizeTimestamp(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        // Accept already-ISO timestamps as-is.
        if (trimmed.contains("T")) {
            return trimmed;
        }

        // Convert UI format "yyyy-MM-dd HH:mm" to ISO-compatible format.
        if (trimmed.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$")) {
            return trimmed.replace(" ", "T") + ":00";
        }

        // Convert plain date "yyyy-MM-dd" to start-of-day timestamp.
        if (trimmed.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            return trimmed + "T00:00:00";
        }

        return trimmed;
    }

    private String buildApiError(String operation, Response<?> response) {
        if (response == null) {
            return operation + " failed: empty response";
        }

        String bodyText = "";
        try {
            if (response.errorBody() != null) {
                bodyText = response.errorBody().string();
            }
        } catch (IOException ignored) {
            bodyText = "";
        }

        if (bodyText != null) {
            bodyText = bodyText.trim();
        }

        if (bodyText == null || bodyText.isEmpty()) {
            return operation + " failed with HTTP " + response.code();
        }

        return operation + " failed with HTTP " + response.code() + ": " + bodyText;
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

    public void addWorkLog(long taskId, String userId, long startTime, long durationMs, String note, TaskCallback<Void> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("task_id", taskId);
        body.put("user_id", userId);
        
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
        format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        body.put("start_time", format.format(new java.util.Date(startTime)));
        body.put("end_time", format.format(new java.util.Date(startTime + durationMs)));
        body.put("duration_minutes", (int)(durationMs / 60000));
        body.put("note", note);

        taskApi.addWorkLog(body, "return=minimal")
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            callback.onSuccess(null);
                        } else {
                            callback.onError("Failed to add work log: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        callback.onError(t.getMessage());
                    }
                });
    }
}