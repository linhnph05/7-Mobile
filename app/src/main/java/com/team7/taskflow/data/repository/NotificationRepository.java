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

/**
 * Repository for Notification data operations.
 *
 * Flow:
 * 1. Fetch notifications with actor join (gets actor name in one query).
 * 2. Collect all reference_ids grouped by type category (project vs task).
 * 3. Batch-fetch project names and task titles.
 * 4. Enrich each Notification with the resolved reference name.
 * 5. Call buildDisplayContent() to generate the display string.
 */
public class NotificationRepository {

    private static final String TAG = "NotificationRepo";

    /** Supabase select clause: all columns + actor join */
    private static final String SELECT_WITH_ACTOR = "*,actor:users!notifications_actor_id_fkey(display_name,avatar_url)";

    private static NotificationRepository instance;
    private final NotificationApi api;

    private NotificationRepository() {
        api = SupabaseClient.getInstance().getService(NotificationApi.class);
    }

    public static synchronized NotificationRepository getInstance() {
        if (instance == null) {
            instance = new NotificationRepository();
        }
        return instance;
    }

    // ── Callback ────────────────────────────────────────────────────

    public interface NotificationCallback<T> {
        void onSuccess(T result);

        void onError(String error);
    }

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Load all notifications for a user, fully enriched with actor + reference
     * names.
     */
    public void getNotifications(String userId, NotificationCallback<List<Notification>> callback) {
        api.getNotifications(
                "eq." + userId,
                SELECT_WITH_ACTOR,
                "created_at.desc").enqueue(new Callback<List<Notification>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Notification>> call,
                            @NonNull Response<List<Notification>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<Notification> notifications = response.body();
                            enrichAndReturn(notifications, callback);
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

    /**
     * Mark a single notification as read.
     */
    public void markAsRead(long notificationId, NotificationCallback<Void> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("is_read", true);

        api.markAsRead("eq." + notificationId, body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(null);
                } else {
                    callback.onError("Failed to mark as read: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Mark all unread notifications as read for a user.
     */
    public void markAllAsRead(String userId, NotificationCallback<Void> callback) {
        Map<String, Object> body = new HashMap<>();
        body.put("is_read", true);

        api.markAllAsRead("eq." + userId, "eq.false", body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(null);
                } else {
                    callback.onError("Failed to mark all as read: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    // ── Enrichment Logic ────────────────────────────────────────────

    /**
     * Enrich notifications with reference names (project name / task title),
     * then build display content and return via callback.
     */
    private void enrichAndReturn(List<Notification> notifications,
            NotificationCallback<List<Notification>> callback) {
        // Separate reference_ids by category
        Set<Long> projectIds = new HashSet<>();
        Set<Long> taskIds = new HashSet<>();

        for (Notification n : notifications) {
            if (n.getReferenceId() == null)
                continue;
            long refId = n.getReferenceId();

            switch (n.getType()) {
                case PROJECT_INVITE:
                    projectIds.add(refId);
                    break;
                case TASK_ASSIGNED:
                case MENTION:
                case COMMENT:
                case TASK_COMPLETED:
                case REACTION:
                case ATTACHMENT_ADDED:
                case DEADLINE_REMINDER:
                    taskIds.add(refId);
                    break;
                default:
                    break;
            }
        }

        // If nothing to resolve, just build content and return
        if (projectIds.isEmpty() && taskIds.isEmpty()) {
            for (Notification n : notifications)
                n.buildDisplayContent();
            callback.onSuccess(notifications);
            return;
        }

        // Track completion of async calls
        final int[] pendingCalls = { 0 };
        final Map<Long, String> projectNameMap = new HashMap<>();
        final Map<Long, String> taskTitleMap = new HashMap<>();

        if (!projectIds.isEmpty())
            pendingCalls[0]++;
        if (!taskIds.isEmpty())
            pendingCalls[0]++;

        Runnable onBatchDone = () -> {
            pendingCalls[0]--;
            if (pendingCalls[0] <= 0) {
                // All batches done — enrich and return
                for (Notification n : notifications) {
                    if (n.getReferenceId() != null) {
                        long refId = n.getReferenceId();
                        String name = projectNameMap.get(refId);
                        if (name == null)
                            name = taskTitleMap.get(refId);
                        if (name != null)
                            n.setReferenceName(name);
                    }
                    n.buildDisplayContent();
                }
                callback.onSuccess(notifications);
            }
        };

        // Batch fetch projects
        if (!projectIds.isEmpty()) {
            String inFilter = "in.(" + joinIds(projectIds) + ")";
            api.getProjectsByIds(inFilter, "project_id,project_name")
                    .enqueue(new Callback<List<Project>>() {
                        @Override
                        public void onResponse(@NonNull Call<List<Project>> call,
                                @NonNull Response<List<Project>> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                for (Project p : response.body()) {
                                    projectNameMap.put(p.getId(), p.getName());
                                }
                            }
                            onBatchDone.run();
                        }

                        @Override
                        public void onFailure(@NonNull Call<List<Project>> call, @NonNull Throwable t) {
                            Log.e(TAG, "Failed to fetch projects: " + t.getMessage());
                            onBatchDone.run();
                        }
                    });
        }

        // Batch fetch tasks
        if (!taskIds.isEmpty()) {
            String inFilter = "in.(" + joinIds(taskIds) + ")";
            api.getTasksByIds(inFilter, "task_id,title")
                    .enqueue(new Callback<List<Task>>() {
                        @Override
                        public void onResponse(@NonNull Call<List<Task>> call,
                                @NonNull Response<List<Task>> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                for (Task t : response.body()) {
                                    taskTitleMap.put(t.getId(), t.getTitle());
                                }
                            }
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

    /** Join a set of IDs into a comma-separated string for Supabase in.() filter */
    private String joinIds(Set<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (sb.length() > 0)
                sb.append(",");
            sb.append(id);
        }
        return sb.toString();
    }
}
