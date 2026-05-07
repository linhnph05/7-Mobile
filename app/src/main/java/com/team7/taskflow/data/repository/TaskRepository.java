package com.team7.taskflow.data.repository;

import androidx.annotation.NonNull;

import android.content.Context;

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
import com.team7.taskflow.ui.widget.TaskTodaySummaryWidgetProvider;
import com.team7.taskflow.ui.widget.TaskTodayWidgetProvider;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TaskRepository extends BaseRepository {

    private static TaskRepository instance;
    private final TaskApi taskApi;
    private final ProjectApi projectApi;
    private final com.team7.taskflow.data.remote.api.StorageApi storageApi;

    // Cache trạng thái trước khi xóa vào thùng rác, để restore về đúng trạng thái
    private final Map<Long, String> statusBeforeTrashCache = new HashMap<>();

    // ── In-Memory Cache ────────────────────────────────────────────────
    private final Map<Long, Task> taskCache = new HashMap<>();
    private final Map<Long, List<Task>> projectTasksCache = new HashMap<>();

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
        Map<String, Object> payload = getTaskMap(task);
        payload.put("project_id", task.getProjectId());

        taskApi.createTask(payload, SupabaseConfig.PREFER_RETURN_REPRESENTATION)
                .enqueue(new Callback<List<Task>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Task>> call, @NonNull Response<List<Task>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            Task created = response.body().get(0);
                            taskCache.put(created.getId(), created);
                            refreshTaskWidgets();
                            callback.onSuccess(created);
                        } else {
                            callback.onError(buildApiError("create_task", response));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Task>> call, @NonNull Throwable t) {
                        callback.onError(getErrorMessage(t));
                    }
                });
    }

    // ── Read ────────────────────────────────────────────────────────────

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
                callback.onError(getErrorMessage(t));
            }
        });
    }

    public void getTasksByProject(long projectId, TaskCallback<List<Task>> callback) {
        taskApi.getTasksByProject("eq." + projectId, "position.asc").enqueue(new Callback<List<Task>>() {
            @Override
            public void onResponse(Call<List<Task>> call, Response<List<Task>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Task> tasks = response.body();
                    projectTasksCache.put(projectId, tasks);
                    for (Task t : tasks) {
                        taskCache.put(t.getId(), t);
                    }
                    callback.onSuccess(tasks);
                } else {
                    callback.onError("Load failed");
                }
            }

            @Override
            public void onFailure(Call<List<Task>> call, Throwable t) {
                callback.onError(getErrorMessage(t));
            }
        });
    }

    public void getTasksByProjectAndStatus(long projectId, String status, TaskCallback<List<Task>> callback) {
        taskApi.getTasksByStatus("eq." + projectId, "eq." + status, "created_at.desc")
                .enqueue(new Callback<List<Task>>() {
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
                        callback.onError(getErrorMessage(t));
                    }
                });
    }

    /** Lấy tất cả task được giao cho user — không bao gồm task trong thùng rác. */
    public void getMyTasks(String userId, TaskCallback<List<Task>> callback) {
        taskApi.getTasksByAssignee("*", "eq." + userId, "neq.TRASH", "created_at.desc")
                .enqueue(new Callback<List<Task>>() {
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
                        callback.onError(getErrorMessage(t));
                    }
                });
    }

    /** Lấy task của user kèm thông tin project — không bao gồm task trong thùng rác. */
    public void getMyTasksWithProjectName(String userId, TaskCallback<List<Task>> callback) {
        taskApi.getTasksByAssignee("*,projects(*)", "eq." + userId, "neq.TRASH", "created_at.desc")
                .enqueue(new Callback<List<Task>>() {
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
                        callback.onError(getErrorMessage(t));
                    }
                });
    }

    public void getMyTasksWithProjectNameByStatus(String userId, String status, TaskCallback<List<Task>> callback) {
        taskApi.getTasksByAssigneeAndStatus(
                "*,projects(*)", "eq." + userId, "eq." + status, "created_at.desc")
                .enqueue(new Callback<List<Task>>() {
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
                        callback.onError(getErrorMessage(t));
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
                            taskCache.put(updated.getId(), updated);
                            refreshTaskWidgets();
                            callback.onSuccess(updated);
                        } else {
                            callback.onError(buildApiError("update_task", response));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Task>> call, @NonNull Throwable t) {
                        callback.onError(getErrorMessage(t));
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
                    refreshTaskWidgets();
                    callback.onSuccess(null);
                } else {
                    callback.onError(buildApiError("update_task_status", response));
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Task>> call, @NonNull Throwable t) {
                callback.onError(getErrorMessage(t));
            }
        });
    }

    /** Cập nhật tất cả subtask về DONE khi parent task được đánh dấu hoàn thành. */
    public void updateSubtasksStatus(long parentTaskId, TaskCallback<Void> callback) {
        taskApi.getTasksByParentId("eq." + parentTaskId).enqueue(new Callback<List<Task>>() {
            @Override
            public void onResponse(@NonNull Call<List<Task>> call, @NonNull Response<List<Task>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    List<Task> subtasks = response.body();
                    for (Task sub : subtasks) {
                        taskCache.put(sub.getId(), sub);
                    }
                    updateSubtasksRecursively(subtasks, 0, callback);
                } else {
                    callback.onSuccess(null);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Task>> call, @NonNull Throwable t) {
                // Cascading update là optional — không fail toàn bộ operation
                callback.onSuccess(null);
            }
        });
    }

    private void updateSubtasksRecursively(List<Task> subtasks, int index, TaskCallback<Void> callback) {
        if (index >= subtasks.size()) {
            refreshTaskWidgets();
            callback.onSuccess(null);
            return;
        }
        Task subtask = subtasks.get(index);
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "DONE");

        taskApi.updateTaskFields("eq." + subtask.getId(), updates, null).enqueue(new Callback<List<Task>>() {
            @Override
            public void onResponse(@NonNull Call<List<Task>> call, @NonNull Response<List<Task>> response) {
                updateSubtasksRecursively(subtasks, index + 1, callback);
            }

            @Override
            public void onFailure(@NonNull Call<List<Task>> call, @NonNull Throwable t) {
                updateSubtasksRecursively(subtasks, index + 1, callback);
            }
        });
    }

    // ── Delete ──────────────────────────────────────────────────────────

    public void softDeleteTask(long taskId, TaskCallback<Void> callback) {
        getTaskById(taskId, new TaskCallback<Task>() {
            @Override
            public void onSuccess(Task task) {
                String previousStatus = (task != null && task.getStatus() != null)
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
                    refreshTaskWidgets();
                    callback.onSuccess(null);
                } else {
                    callback.onError("Failed to delete task");
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Task>> call, @NonNull Throwable t) {
                callback.onError(getErrorMessage(t));
            }
        });
    }

    /**
     * Xóa vĩnh viễn task khỏi DB — không cần pre-fetch, gọi API xóa trực tiếp.
     * (DRY: loại bỏ duplicate code path cũ trong deleteTask)
     */
    public void deleteTask(long taskId, TaskCallback<Void> callback) {
        taskApi.deleteTask("eq." + taskId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    taskCache.remove(taskId);
                    refreshTaskWidgets();
                    callback.onSuccess(null);
                } else {
                    callback.onError("Delete failed: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onError(getErrorMessage(t));
            }
        });
    }

    public void permanentlyDeleteTask(long taskId, TaskCallback<Void> callback) {
        deleteTask(taskId, callback);
    }

    public void restoreTask(long taskId, TaskCallback<Void> callback) {
        getTaskHistory(taskId, new TaskCallback<List<TaskActivity>>() {
            @Override
            public void onSuccess(List<TaskActivity> history) {
                String restoreStatus = statusBeforeTrashCache.getOrDefault(taskId, "TODO");
                if (history != null) {
                    for (TaskActivity activity : history) {
                        String action = activity.getActionType() != null
                                ? activity.getActionType().trim().toUpperCase() : "";
                        String newValueUpper = activity.getNewValue() != null
                                ? activity.getNewValue().trim().toUpperCase() : "";
                        String oldValueRaw = activity.getOldValue() != null
                                ? activity.getOldValue().trim() : "";
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
                    refreshTaskWidgets();
                    statusBeforeTrashCache.remove(taskId);
                    callback.onSuccess(null);
                } else {
                    callback.onError("Failed to restore task");
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<Task>> call, @NonNull Throwable t) {
                callback.onError(getErrorMessage(t));
            }
        });
    }

    // ── Cache Accessors ────────────────────────────────────────────────

    public Task getCachedTask(long taskId) {
        return taskCache.get(taskId);
    }

    public List<Task> getCachedTasksByProject(long projectId) {
        return projectTasksCache.get(projectId);
    }

    public void clearCache() {
        taskCache.clear();
        projectTasksCache.clear();
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

            byte[] bytes;
            try {
                java.io.ByteArrayOutputStream byteBuffer = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    byteBuffer.write(buffer, 0, len);
                }
                bytes = byteBuffer.toByteArray();
            } finally {
                inputStream.close();
            }

            String bucket = "task_attachments";
            String userId = SessionManager.getUserId();
            long timestamp = System.currentTimeMillis();

            String finalFileName = fileName;
            int dotIndex = fileName.lastIndexOf(".");
            if (dotIndex != -1) {
                finalFileName = fileName.substring(0, dotIndex) + "_" + timestamp + fileName.substring(dotIndex);
            } else {
                finalFileName = fileName + "_" + timestamp;
            }

            String path = userId + "/" + taskId + "/" + finalFileName;
            okhttp3.RequestBody requestBody = okhttp3.RequestBody.create(bytes,
                    okhttp3.MediaType.parse(mimeType != null ? mimeType : "application/octet-stream"));

            storageApi.uploadFile(bucket, path, requestBody, "true").enqueue(new Callback<okhttp3.ResponseBody>() {
                @Override
                public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                    if (!response.isSuccessful()) {
                        callback.onError("Upload failed. Make sure bucket '" + bucket + "' exists. " + response.code());
                        return;
                    }

                    String currentToken = SessionManager.getAccessToken();
                    String currentUserId = SessionManager.getUserId();
                    if (currentUserId == null || currentUserId.isEmpty()) {
                        callback.onError("Session expired. Please login again.");
                        return;
                    }
                    if (currentToken != null && !currentToken.isEmpty()) {
                        SupabaseClient.getInstance().setAccessToken(currentToken);
                    }

                    String publicUrl = SupabaseConfig.SUPABASE_URL
                            + "/storage/v1/object/public/" + bucket + "/" + path;

                    // Dùng Map để tránh gửi attachment_id=null (Gson serializeNulls)
                    java.util.Map<String, Object> attachmentData = new java.util.HashMap<>();
                    attachmentData.put("task_id", taskId);
                    attachmentData.put("uploader_id", currentUserId);
                    attachmentData.put("file_url", publicUrl);
                    attachmentData.put("file_name", fileName);
                    attachmentData.put("file_type", mimeType);

                    taskApi.addAttachment(attachmentData, SupabaseConfig.PREFER_RETURN_REPRESENTATION)
                            .enqueue(new Callback<List<com.team7.taskflow.domain.model.Attachment>>() {
                                @Override
                                public void onResponse(Call<List<com.team7.taskflow.domain.model.Attachment>> call,
                                        Response<List<com.team7.taskflow.domain.model.Attachment>> response) {
                                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                                        // Trả về attachment có ID từ DB
                                        callback.onSuccess(response.body().get(0));
                                    } else {
                                        callback.onError("Database Error: " + response.code());
                                    }
                                }

                                @Override
                                public void onFailure(Call<List<com.team7.taskflow.domain.model.Attachment>> call,
                                        Throwable t) {
                                    callback.onError("Network error linking attachment: " + getErrorMessage(t));
                                }
                            });
                }

                @Override
                public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                    callback.onError("Network error: " + getErrorMessage(t));
                }
            });

        } catch (java.io.IOException e) {
            callback.onError(e.getMessage() != null ? e.getMessage() : "IO error reading file");
        }
    }

    public void getTaskAttachments(long taskId,
            TaskCallback<List<com.team7.taskflow.domain.model.Attachment>> callback) {
        taskApi.getAttachmentsByTask("eq." + taskId)
                .enqueue(new Callback<List<com.team7.taskflow.domain.model.Attachment>>() {
                    @Override
                    public void onResponse(Call<List<com.team7.taskflow.domain.model.Attachment>> call,
                            Response<List<com.team7.taskflow.domain.model.Attachment>> response) {
                        if (response.isSuccessful()) {
                            callback.onSuccess(response.body());
                        } else {
                            callback.onError("Failed to load attachments: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<List<com.team7.taskflow.domain.model.Attachment>> call, Throwable t) {
                        callback.onError(getErrorMessage(t));
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
                callback.onError(getErrorMessage(t));
            }
        });
    }

    // ── History ─────────────────────────────────────────────────────────

    public void getProjectMembers(long projectId, TaskCallback<List<User>> callback) {
        ProjectRepository.getInstance().getProjectMembers(projectId,
                new ProjectRepository.ProjectCallback<List<User>>() {
                    @Override
                    public void onSuccess(List<User> result) { callback.onSuccess(result); }

                    @Override
                    public void onError(String error) { callback.onError(error); }
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
                callback.onError(getErrorMessage(t));
            }
        });
    }

    // ── Comments ─────────────────────────────────────────────────────────

    public void getTaskComments(long taskId, TaskCallback<List<Comment>> callback) {
        String select = "comment_id,task_id,user_id,content,created_at,is_deleted,"
                + "users(user_id,display_name,avatar_url)";
        taskApi.getCommentsByTask("eq." + taskId, "eq.false", select, "created_at.desc")
                .enqueue(new Callback<List<Comment>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Comment>> call, @NonNull Response<List<Comment>> response) {
                        if (response.isSuccessful()) {
                            List<Comment> comments = response.body();
                            loadCommentReactionCounts(
                                    comments != null ? comments : new java.util.ArrayList<>(), callback);
                        } else {
                            callback.onError(buildApiError("comments", response));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Comment>> call, @NonNull Throwable t) {
                        callback.onError(getErrorMessage(t));
                    }
                });
    }

    public void getCommentById(long commentId, TaskCallback<Comment> callback) {
        taskApi.getCommentById("eq." + commentId, "comment_id,task_id,user_id,content,is_deleted")
                .enqueue(new Callback<List<Comment>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Comment>> call, @NonNull Response<List<Comment>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            callback.onSuccess(response.body().get(0));
                        } else {
                            callback.onError("Comment not found");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Comment>> call, @NonNull Throwable t) {
                        callback.onError(getErrorMessage(t));
                    }
                });
    }

    public void createTaskComment(long taskId, String userId, String content, TaskCallback<Comment> callback) {
        String effectiveUserId = resolveUserId(userId);
        if (effectiveUserId == null) {
            callback.onError("Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.");
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("task_id", taskId);
        body.put("user_id", effectiveUserId);
        body.put("content", content);

        taskApi.createComment(body, SupabaseConfig.PREFER_RETURN_REPRESENTATION)
                .enqueue(new Callback<List<Comment>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Comment>> call, @NonNull Response<List<Comment>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            callback.onSuccess(response.body().get(0));
                        } else {
                            callback.onError(buildApiError("create_comment", response));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Comment>> call, @NonNull Throwable t) {
                        callback.onError(getErrorMessage(t));
                    }
                });
    }

    public void updateTaskComment(long commentId, String userId, String content, TaskCallback<Comment> callback) {
        String effectiveUserId = resolveUserId(userId);
        if (effectiveUserId == null) {
            callback.onError("Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.");
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("content", content);

        taskApi.updateComment("eq." + commentId, "eq." + effectiveUserId, body,
                SupabaseConfig.PREFER_RETURN_REPRESENTATION)
                .enqueue(new Callback<List<Comment>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Comment>> call, @NonNull Response<List<Comment>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            callback.onSuccess(response.body().get(0));
                        } else {
                            callback.onError(buildApiError("update_comment", response));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Comment>> call, @NonNull Throwable t) {
                        callback.onError(getErrorMessage(t));
                    }
                });
    }

    public void deleteTaskComment(long commentId, String userId, TaskCallback<Void> callback) {
        String effectiveUserId = resolveUserId(userId);
        if (effectiveUserId == null) {
            callback.onError("Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.");
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("is_deleted", true);

        // Kiểm tra trước xem comment đã bị xóa chưa; nếu rồi thì coi như thành công.
        getCommentById(commentId, new TaskCallback<Comment>() {
            @Override
            public void onSuccess(Comment existing) {
                if (existing != null && existing.isDeleted()) {
                    callback.onSuccess(null);
                    return;
                }
                softDeleteComment(commentId, effectiveUserId, body, callback);
            }

            @Override
            public void onError(String error) {
                // Pre-fetch lỗi nhưng vẫn thử soft-delete (fallback)
                softDeleteComment(commentId, effectiveUserId, body, callback);
            }
        });
    }

    private void softDeleteComment(long commentId, String userId,
            Map<String, Object> body, TaskCallback<Void> callback) {
        taskApi.updateComment("eq." + commentId, "eq." + userId, body,
                SupabaseConfig.PREFER_RETURN_REPRESENTATION)
                .enqueue(new Callback<List<Comment>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Comment>> call, @NonNull Response<List<Comment>> response) {
                        if (response.isSuccessful()) {
                            callback.onSuccess(null);
                        } else {
                            callback.onError(buildApiError("soft_delete_comment", response));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Comment>> call, @NonNull Throwable t) {
                        callback.onError(getErrorMessage(t));
                    }
                });
    }

    public void toggleCommentReaction(long commentId, String userId, String reactionType,
            TaskCallback<Void> callback) {
        String effectiveUserId = resolveUserId(userId);
        if (effectiveUserId == null) {
            callback.onError("Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại.");
            return;
        }

        String normalizedReaction = reactionType != null ? reactionType.trim().toUpperCase() : "";
        if (!"LIKE".equals(normalizedReaction) && !"LOVE".equals(normalizedReaction)
                && !"CELEBRATE".equals(normalizedReaction)) {
            callback.onError("Invalid reaction type");
            return;
        }

        taskApi.getCommentReactions("eq." + commentId, "eq." + effectiveUserId, null)
                .enqueue(new Callback<List<CommentReaction>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<CommentReaction>> call,
                            @NonNull Response<List<CommentReaction>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            CommentReaction existing = response.body().get(0);
                            if (existing.getId() == null) {
                                callback.onError("Reaction not found");
                                return;
                            }
                            String existingType = existing.getReactionType() != null
                                    ? existing.getReactionType().trim().toUpperCase() : "";
                            String newType = normalizedReaction.equals(existingType)
                                    ? "DELETED" : normalizedReaction;

                            Map<String, Object> updateBody = new HashMap<>();
                            updateBody.put("reaction_type", newType);

                            taskApi.updateCommentReaction("eq." + existing.getId(), updateBody,
                                    SupabaseConfig.PREFER_RETURN_REPRESENTATION)
                                    .enqueue(new Callback<List<CommentReaction>>() {
                                        @Override
                                        public void onResponse(@NonNull Call<List<CommentReaction>> call,
                                                @NonNull Response<List<CommentReaction>> response) {
                                            if (response.isSuccessful()) callback.onSuccess(null);
                                            else callback.onError(buildApiError("update_reaction", response));
                                        }

                                        @Override
                                        public void onFailure(@NonNull Call<List<CommentReaction>> call,
                                                @NonNull Throwable t) {
                                            callback.onError(getErrorMessage(t));
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
                                    public void onResponse(@NonNull Call<List<CommentReaction>> call,
                                            @NonNull Response<List<CommentReaction>> response) {
                                        if (response.isSuccessful()) callback.onSuccess(null);
                                        else callback.onError(buildApiError("create_reaction", response));
                                    }

                                    @Override
                                    public void onFailure(@NonNull Call<List<CommentReaction>> call,
                                            @NonNull Throwable t) {
                                        callback.onError(getErrorMessage(t));
                                    }
                                });
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<CommentReaction>> call, @NonNull Throwable t) {
                        callback.onError(getErrorMessage(t));
                    }
                });
    }

    // ── WorkLog ──────────────────────────────────────────────────────────

    public void addWorkLog(long taskId, String userId, long startTime, long durationMs,
            long remainingMs, String note, TaskCallback<Void> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("task_id", taskId);
        body.put("user_id", userId);

        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US);
        format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        body.put("start_time", format.format(new java.util.Date(startTime)));
        body.put("end_time", format.format(new java.util.Date(startTime + durationMs)));
        body.put("duration_minutes", (int) (durationMs / 60000));
        body.put("remaining_minutes", (int) (remainingMs / 60000));
        body.put("note", note);

        taskApi.addWorkLog(body, "return=minimal").enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) callback.onSuccess(null);
                else callback.onError("Failed to add work log: " + response.code());
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onError(getErrorMessage(t));
            }
        });
    }

    // ── Private helpers ─────────────────────────────────────────────────

    /**
     * Lấy userId hiệu dụng: ưu tiên tham số truyền vào, fallback về session.
     * Trả về null nếu không xác định được (session hết hạn hoặc chưa đăng nhập).
     */
    private static String resolveUserId(String userId) {
        if (userId != null && !userId.trim().isEmpty()) return userId.trim();
        String sessionId = SessionManager.getUserId();
        return (sessionId != null && !sessionId.trim().isEmpty()) ? sessionId.trim() : null;
    }

    private Map<String, Object> getTaskMap(Task task) {
        Map<String, Object> map = new HashMap<>();
        if (task.getTitle() != null)
            map.put("title", task.getTitle());
        map.put("description", normalizeNullableText(task.getDescription()));
        map.put("status", normalizeNullableText(task.getStatus()));
        map.put("priority", normalizeNullableText(task.getPriority()));
        if (task.getPosition() != null)
            map.put("position", task.getPosition());
        map.put("due_date", normalizeTimestamp(task.getDueDate()));
        map.put("start_date", normalizeTimestamp(task.getStartDate()));
        map.put("assignee_id", normalizeUuid(task.getAssigneeId()));
        map.put("tag", normalizeNullableText(task.getTag()));
        map.put("parent_task_id", task.getParentTaskId());
        return map;
    }

    private String normalizeNullableText(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeUuid(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        // Validate UUID format: 8-4-4-4-12
        return trimmed.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
                ? trimmed : null;
    }

    private String normalizeTimestamp(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.contains("T")) return trimmed;
        if (trimmed.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$"))
            return trimmed.replace(" ", "T") + ":00";
        if (trimmed.matches("^\\d{4}-\\d{2}-\\d{2}$"))
            return trimmed + "T00:00:00";
        return trimmed;
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
                if (--remaining[0] == 0) callback.onSuccess(comments);
                continue;
            }

            taskApi.getCommentReactions("eq." + commentId, null, null)
                    .enqueue(new Callback<List<CommentReaction>>() {
                        @Override
                        public void onResponse(@NonNull Call<List<CommentReaction>> call,
                                @NonNull Response<List<CommentReaction>> response) {
                            applyReactionCounts(comment, response.body(), currentUserId);
                            if (--remaining[0] == 0) callback.onSuccess(comments);
                        }

                        @Override
                        public void onFailure(@NonNull Call<List<CommentReaction>> call, @NonNull Throwable t) {
                            applyReactionCounts(comment, null, currentUserId);
                            if (--remaining[0] == 0) callback.onSuccess(comments);
                        }
                    });
        }
    }

    private void applyReactionCounts(Comment comment, List<CommentReaction> reactions, String currentUserId) {
        int like = 0, heart = 0, congrats = 0;
        boolean likeSelected = false, heartSelected = false, congratsSelected = false;

        if (reactions != null) {
            for (CommentReaction reaction : reactions) {
                String type = reaction.getReactionType() != null
                        ? reaction.getReactionType().toUpperCase() : "";
                boolean isCurrentUser = currentUserId != null && currentUserId.equals(reaction.getUserId());
                switch (type) {
                    case "LIKE":
                        like++;
                        if (isCurrentUser) likeSelected = true;
                        break;
                    case "LOVE":
                        heart++;
                        if (isCurrentUser) heartSelected = true;
                        break;
                    case "CELEBRATE":
                        congrats++;
                        if (isCurrentUser) congratsSelected = true;
                        break;
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

    private void refreshTaskWidgets() {
        Context appContext = SessionManager.getAppContext();
        if (appContext == null) return;
        TaskTodayWidgetProvider.refreshAll(appContext);
        TaskTodaySummaryWidgetProvider.refreshAll(appContext);
    }
}
