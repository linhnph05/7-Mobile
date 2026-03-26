package com.team7.taskflow.data.repository;

import androidx.annotation.NonNull;

import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.remote.SupabaseConfig;
import com.team7.taskflow.data.remote.api.ActivityApi;
import com.team7.taskflow.data.remote.api.TaskApi;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.domain.model.TaskActivity;
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

    private TaskRepository() {
        taskApi = SupabaseClient.getInstance().getService(TaskApi.class);
        activityApi = SupabaseClient.getInstance().getService(ActivityApi.class);
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
                if (response.isSuccessful()) callback.onSuccess(null);
                else callback.onError("Delete failed: " + response.code());
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) { callback.onError(t.getMessage()); }
        });
    }

    // ── History ─────────────────────────────────────────────────────────

    private void logActivity(long taskId, String action, String oldVal, String newVal) {
        String userId = SessionManager.getUserId();
        TaskActivity activity = new TaskActivity(taskId, userId, action, oldVal, newVal);
        activityApi.logActivity(activity).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> call, Response<Void> response) {}
            @Override public void onFailure(Call<Void> call, Throwable t) {}
        });
    }

    public void getTaskHistory(long taskId, TaskCallback<List<TaskActivity>> callback) {
        activityApi.getActivitiesByTask("eq." + taskId, "created_at.desc").enqueue(new Callback<List<TaskActivity>>() {
            @Override
            public void onResponse(Call<List<TaskActivity>> call, Response<List<TaskActivity>> response) {
                if (response.isSuccessful()) callback.onSuccess(response.body());
                else callback.onError("Failed to load history");
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
        if (task.getTitle() != null) map.put("title", task.getTitle());
        if (task.getDescription() != null) map.put("description", task.getDescription());
        if (task.getStatus() != null) map.put("status", task.getStatus());
        if (task.getPriority() != null) map.put("priority", task.getPriority());
        if (task.getPosition() != null) map.put("position", task.getPosition());
        return map;
    }

    public void getTasksByProject(long projectId, TaskCallback<List<Task>> callback) {
        taskApi.getTasksByProject("eq." + projectId, "position.asc").enqueue(new Callback<List<Task>>() {
            @Override
            public void onResponse(Call<List<Task>> call, Response<List<Task>> response) {
                if (response.isSuccessful()) callback.onSuccess(response.body());
                else callback.onError("Load failed");
            }
            @Override public void onFailure(Call<List<Task>> call, Throwable t) { callback.onError(t.getMessage()); }
        });
    }

    /**
     * Get tasks assigned to a specific user
     */
    public void getMyTasks(String userId, TaskCallback<List<Task>> callback) {
        taskApi.getTasksByAssignee("eq." + userId, "due_date.asc").enqueue(new Callback<List<Task>>() {
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