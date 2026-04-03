package com.team7.taskflow.data.repository;

import android.util.Log;

import androidx.annotation.NonNull;

import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.remote.api.NotificationApi;
import com.team7.taskflow.domain.model.Notification;
import com.team7.taskflow.domain.model.Project;
import com.team7.taskflow.domain.model.Task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationRepository {

    private static final String TAG = "NotificationRepo";
    private static final String SELECT_WITH_ACTOR =
            "*,actor:users!notifications_actor_id_fkey(display_name,avatar_url)";

    private static NotificationRepository instance;
    private final NotificationApi api;

    private NotificationRepository() {
        api = SupabaseClient.getInstance().getService(NotificationApi.class);
    }

    public static synchronized NotificationRepository getInstance() {
        if (instance == null) instance = new NotificationRepository();
        return instance;
    }

    public interface NotificationCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    public void getNotifications(String userId, NotificationCallback<List<Notification>> callback) {
        api.getNotifications("eq." + userId, SELECT_WITH_ACTOR, "created_at.desc")
                .enqueue(new Callback<List<Notification>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Notification>> call,
                                           @NonNull Response<List<Notification>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            enrichAndReturn(response.body(), callback);
                        } else {
                            Log.w(TAG, "Select with actor failed, fallback to basic select. Code=" + response.code());
                            fetchNotificationsFallback(userId, callback);
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<List<Notification>> call, @NonNull Throwable t) {
                        Log.w(TAG, "Select with actor failed, fallback to basic select: " + t.getMessage());
                        fetchNotificationsFallback(userId, callback);
                    }
                });
    }

    private void fetchNotificationsFallback(String userId, NotificationCallback<List<Notification>> callback) {
        api.getNotifications("eq." + userId, "*", "created_at.desc")
                .enqueue(new Callback<List<Notification>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Notification>> call,
                                           @NonNull Response<List<Notification>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            enrichAndReturn(response.body(), callback);
                        } else {
                            callback.onError("Failed to load notifications: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Notification>> call, @NonNull Throwable t) {
                        callback.onError("Network error: " + t.getMessage());
                    }
                });
    }

    public void markAsRead(long notificationId, NotificationCallback<Void> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("is_read", true);
        api.markAsRead("eq." + notificationId, body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) callback.onSuccess(null);
                else callback.onError("Failed to mark as read: " + response.code());
            }
            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    public void markAllAsRead(String userId, NotificationCallback<Void> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("is_read", true);
        api.markAllAsRead("eq." + userId, "eq.false", body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) callback.onSuccess(null);
                else callback.onError("Failed to mark all as read: " + response.code());
            }
            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    // ✅ Xóa notification khỏi database (dùng sau Accept/Decline invite)
    public void deleteNotification(long notificationId, NotificationCallback<Void> callback) {
        api.deleteNotification("eq." + notificationId, "return=minimal")
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                        if (response.isSuccessful()) callback.onSuccess(null);
                        else callback.onError("Failed to delete notification: " + response.code());
                    }
                    @Override
                    public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                        callback.onError("Network error: " + t.getMessage());
                    }
                });
    }

    // ── Enrichment ───────────────────────────────────────────────────────────

    private void enrichAndReturn(List<Notification> notifications,
                                 NotificationCallback<List<Notification>> callback) {
        Set<Long> projectIds = new HashSet<>();
        Set<Long> taskIds    = new HashSet<>();

        for (Notification n : notifications) {
            if (n.getReferenceId() == null) continue;
            long refId = n.getReferenceId();
            switch (n.getType()) {
                case PROJECT_INVITE:
                    projectIds.add(refId); break;
                case TASK_ASSIGNED:
                case MENTION:
                case COMMENT:
                case TASK_COMPLETED:
                case REACTION:
                case ATTACHMENT_ADDED:
                case DEADLINE_REMINDER:
                    taskIds.add(refId); break;
                default: break;
            }
        }

        if (projectIds.isEmpty() && taskIds.isEmpty()) {
            for (Notification n : notifications) n.buildDisplayContent();
            callback.onSuccess(notifications);
            return;
        }

        final int[] pendingCalls = {0};
        final Map<Long, String> projectNameMap = new HashMap<>();
        final Map<Long, String> taskTitleMap   = new HashMap<>();

        if (!projectIds.isEmpty()) pendingCalls[0]++;
        if (!taskIds.isEmpty())    pendingCalls[0]++;

        Runnable onBatchDone = () -> {
            pendingCalls[0]--;
            if (pendingCalls[0] <= 0) {
                for (Notification n : notifications) {
                    if (n.getReferenceId() != null) {
                        long refId = n.getReferenceId();
                        String name = projectNameMap.get(refId);
                        if (name == null) name = taskTitleMap.get(refId);
                        if (name != null) n.setReferenceName(name);
                    }
                    n.buildDisplayContent();
                }
                callback.onSuccess(notifications);
            }
        };

        if (!projectIds.isEmpty()) {
            api.getProjectsByIds("in.(" + joinIds(projectIds) + ")", "project_id,project_name")
                    .enqueue(new Callback<List<Project>>() {
                        @Override
                        public void onResponse(@NonNull Call<List<Project>> call,
                                               @NonNull Response<List<Project>> response) {
                            if (response.isSuccessful() && response.body() != null)
                                for (Project p : response.body())
                                    projectNameMap.put(p.getId(), p.getName());
                            onBatchDone.run();
                        }
                        @Override
                        public void onFailure(@NonNull Call<List<Project>> call, @NonNull Throwable t) {
                            Log.e(TAG, "Failed to fetch projects: " + t.getMessage());
                            onBatchDone.run();
                        }
                    });
        }

        if (!taskIds.isEmpty()) {
            api.getTasksByIds("in.(" + joinIds(taskIds) + ")", "task_id,title")
                    .enqueue(new Callback<List<Task>>() {
                        @Override
                        public void onResponse(@NonNull Call<List<Task>> call,
                                               @NonNull Response<List<Task>> response) {
                            if (response.isSuccessful() && response.body() != null)
                                for (Task t : response.body())
                                    taskTitleMap.put(t.getId(), t.getTitle());
                            onBatchDone.run();
                        }
                        @Override
                        public void onFailure(@NonNull Call<List<Task>> call, @NonNull Throwable t) {
                            Log.e(TAG, "Failed to fetch tasks: " + t.getMessage());
                            onBatchDone.run();
                        }
                    });
        }
    }

    private String joinIds(Set<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (sb.length() > 0) sb.append(",");
            sb.append(id);
        }
        return sb.toString();
    }
}