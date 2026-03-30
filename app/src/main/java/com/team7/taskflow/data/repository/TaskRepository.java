package com.team7.taskflow.data.repository;

import androidx.annotation.NonNull;

import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.remote.SupabaseConfig;
import com.team7.taskflow.data.remote.api.ActivityApi;
import com.team7.taskflow.data.remote.api.ProjectApi;
import com.team7.taskflow.data.remote.api.TaskApi;
import com.team7.taskflow.domain.model.ProjectMember;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.domain.model.TaskActivity;
import com.team7.taskflow.domain.model.User;
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
                            logActivity(created.getId(), "CREATE", null, created.getTitle());
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
                            callback.onSuccess(response.body().get(0));
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

    public void updateTaskStatus(long taskId, String oldStatus, String newStatus, TaskCallback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", newStatus);

        taskApi.updateTaskFields("eq." + taskId, updates, null).enqueue(new Callback<List<Task>>() {
            @Override
            public void onResponse(@NonNull Call<List<Task>> call, @NonNull Response<List<Task>> response) {
                if (response.isSuccessful()) {
                    logActivity(taskId, "UPDATE_STATUS", oldStatus, newStatus);
                    callback.onSuccess(null);
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
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "TRASH"); // Assuming TRASH is a valid status for soft delete

        taskApi.updateTaskFields("eq." + taskId, updates, null).enqueue(new Callback<List<Task>>() {
            @Override
            public void onResponse(@NonNull Call<List<Task>> call, @NonNull Response<List<Task>> response) {
                if (response.isSuccessful()) {
                    logActivity(taskId, "DELETE", "ACTIVE", "TRASH");
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
        taskApi.deleteTask("eq." + taskId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful())
                    callback.onSuccess(null);
                else
                    callback.onError("Delete failed: " + response.code());
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onError(t.getMessage());
            }
        });
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

    private void logActivity(long taskId, String action, String oldVal, String newVal) {
        String userId = SessionManager.getUserId();
        TaskActivity activity = new TaskActivity(taskId, userId, action, oldVal, newVal);
        activityApi.logActivity(activity).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
            }
        });
    }
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
                                if (pm.getUser() != null) {
                                    // Đảm bảo userId được lấy từ project_member nếu nested user không có
                                    User u = pm.getUser();
                                    if (u.getUserId() == null) {
                                        u.setUserId(pm.getUserId());
                                    }
                                    users.add(u);
                                } else {
                                    // Fallback: tạo User tối giản chỉ từ userId
                                    User fallback = new User();
                                    fallback.setUserId(pm.getUserId());
                                    fallback.setDisplayName(pm.getUserId());
                                    users.add(fallback);
                                }
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
        return map;
    }

    public void getTasksByProject(long projectId, TaskCallback<List<Task>> callback) {
        taskApi.getTasksByProject("eq." + projectId, "position.asc").enqueue(new Callback<List<Task>>() {
            @Override
            public void onResponse(Call<List<Task>> call, Response<List<Task>> response) {
                if (response.isSuccessful())
                    callback.onSuccess(response.body());
                else
                    callback.onError("Load failed");
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
        taskApi.getTasksByAssignee("*", "eq." + userId, "due_date.asc").enqueue(new Callback<List<Task>>() {
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
        taskApi.getTasksByAssignee("*", "eq." + userId, "due_date.asc").enqueue(new Callback<List<Task>>() {
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
}