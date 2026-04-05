package com.team7.taskflow.data.repository;

import android.util.Log;

import androidx.annotation.NonNull;

import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.remote.api.NotificationApi;
import com.team7.taskflow.domain.model.Comment;
import com.team7.taskflow.domain.model.Notification;
import com.team7.taskflow.domain.model.Project;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.domain.model.TaskActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationRepository {

    private static final String TAG = "NotificationRepo";
    private static final String EXCLUDE_PROJECT_INVITE_FILTER = "neq.PROJECT_INVITE";
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
        api.markAllAsRead("eq." + userId, "eq.false", EXCLUDE_PROJECT_INVITE_FILTER, body)
                .enqueue(new Callback<Void>() {
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
        Set<Long> commentIds = new HashSet<>();
        Set<Long> taskActivityIds = new HashSet<>(); // For TASK_STATUS_CHANGED

        for (Notification n : notifications) {
            if (n.getReferenceId() == null) continue;
            long refId = n.getReferenceId();
            switch (n.getType()) {
                case PROJECT_INVITE:
                    projectIds.add(refId); break;
                case TASK_ASSIGNED:
                case MENTION:
                case TASK_STATUS_CHANGED:
                case ATTACHMENT_ADDED:
                case DEADLINE_REMINDER:
                    taskIds.add(refId);
                    if (n.getType() == Notification.NotificationType.TASK_STATUS_CHANGED
                            && n.getTaskActivityId() != null) {
                        taskActivityIds.add(n.getTaskActivityId());
                    }
                    break;
                case COMMENT:
                case REACTION:
                case DELETED:
                    commentIds.add(refId); break;
                default: break;
            }
        }

        if (projectIds.isEmpty() && taskIds.isEmpty() && commentIds.isEmpty()) {
            for (Notification n : notifications) n.buildDisplayContent();
            callback.onSuccess(notifications);
            return;
        }

        final Map<Long, String> projectNameMap = new HashMap<>();
        final Map<Long, String> taskTitleMap   = new HashMap<>();
        final Map<Long, Long> commentTaskMap   = new HashMap<>();
        final Map<Long, TaskActivity> taskActivitiesMap = new HashMap<>();

        AtomicInteger pendingPrimaryCalls = new AtomicInteger(0);
        if (!projectIds.isEmpty()) pendingPrimaryCalls.incrementAndGet();
        if (!commentIds.isEmpty()) pendingPrimaryCalls.incrementAndGet();
        if (!taskActivityIds.isEmpty()) pendingPrimaryCalls.incrementAndGet();

        Runnable onPrimaryDone = () -> {
            if (pendingPrimaryCalls.decrementAndGet() == 0) {
                Set<Long> resolvedTaskIds = new HashSet<>(taskIds);
                resolvedTaskIds.addAll(commentTaskMap.values());

                fetchTaskTitles(resolvedTaskIds, taskTitleMap, new NotificationCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        finalizeNotifications(notifications, projectNameMap, taskTitleMap, commentTaskMap, taskActivitiesMap, callback);
                    }

                    @Override
                    public void onError(String error) {
                        finalizeNotifications(notifications, projectNameMap, taskTitleMap, commentTaskMap, taskActivitiesMap, callback);
                    }
                });
            }
        };

        if (pendingPrimaryCalls.get() == 0) {
            fetchTaskTitles(taskIds, taskTitleMap, new NotificationCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    finalizeNotifications(notifications, projectNameMap, taskTitleMap, commentTaskMap, taskActivitiesMap, callback);
                }

                @Override
                public void onError(String error) {
                    finalizeNotifications(notifications, projectNameMap, taskTitleMap, commentTaskMap, taskActivitiesMap, callback);
                }
            });
            return;
        }

        if (!projectIds.isEmpty()) {
            api.getProjectsByIds("in.(" + joinIds(projectIds) + ")", "project_id,project_name")
                    .enqueue(new Callback<List<Project>>() {
                        @Override
                        public void onResponse(@NonNull Call<List<Project>> call,
                                               @NonNull Response<List<Project>> response) {
                            if (response.isSuccessful() && response.body() != null)
                                for (Project p : response.body())
                                    projectNameMap.put(p.getId(), p.getName());
                            onPrimaryDone.run();
                        }
                        @Override
                        public void onFailure(@NonNull Call<List<Project>> call, @NonNull Throwable t) {
                            Log.e(TAG, "Failed to fetch projects: " + t.getMessage());
                            onPrimaryDone.run();
                        }
                    });
        }

        if (!commentIds.isEmpty()) {
            api.getCommentsByIds("in.(" + joinIds(commentIds) + ")", "comment_id,task_id")
                    .enqueue(new Callback<List<Comment>>() {
                        @Override
                        public void onResponse(@NonNull Call<List<Comment>> call,
                                               @NonNull Response<List<Comment>> response) {
                            if (response.isSuccessful() && response.body() != null)
                                for (Comment c : response.body()) {
                                    if (c == null || c.getId() == null || c.getTaskId() == null) {
                                        continue;
                                    }
                                    commentTaskMap.put(c.getId(), c.getTaskId());
                                }
                            onPrimaryDone.run();
                        }
                        @Override
                        public void onFailure(@NonNull Call<List<Comment>> call, @NonNull Throwable t) {
                            Log.e(TAG, "Failed to fetch comments: " + t.getMessage());
                            onPrimaryDone.run();
                        }
                    });
        }

        if (!taskActivityIds.isEmpty()) {
            fetchTaskActivitiesForNotifications(taskActivityIds, taskActivitiesMap, new NotificationCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    onPrimaryDone.run();
                }

                @Override
                public void onError(String error) {
                    Log.w(TAG, "Failed to fetch task activities: " + error);
                    onPrimaryDone.run();
                }
            });
        }
    }

    private void fetchTaskTitles(Set<Long> taskIds,
                                 Map<Long, String> taskTitleMap,
                                 NotificationCallback<Void> callback) {
        if (taskIds == null || taskIds.isEmpty()) {
            callback.onSuccess(null);
            return;
        }

        api.getTasksByIds("in.(" + joinIds(taskIds) + ")", "task_id,title")
                .enqueue(new Callback<List<Task>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Task>> call,
                                           @NonNull Response<List<Task>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            for (Task t : response.body()) {
                                taskTitleMap.put(t.getId(), t.getTitle());
                            }
                        }
                        callback.onSuccess(null);
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Task>> call, @NonNull Throwable t) {
                        Log.e(TAG, "Failed to fetch tasks: " + t.getMessage());
                        callback.onError(t.getMessage());
                    }
                });
    }

    private void finalizeNotifications(List<Notification> notifications,
                                       Map<Long, String> projectNameMap,
                                       Map<Long, String> taskTitleMap,
                                       Map<Long, Long> commentTaskMap,
                                       Map<Long, TaskActivity> taskActivitiesMap,
                                       NotificationCallback<List<Notification>> callback) {
        for (Notification n : notifications) {
            if (n.getReferenceId() != null) {
                long refId = n.getReferenceId();
                String referenceName = resolveReferenceName(n, refId, projectNameMap, taskTitleMap, commentTaskMap);
                if (referenceName != null) {
                    n.setReferenceName(referenceName);
                }
            }

            // Set activity detail for TASK_STATUS_CHANGED notifications
            if (n.getType() == Notification.NotificationType.TASK_STATUS_CHANGED
                    && n.getTaskActivityId() != null) {
                TaskActivity activity = taskActivitiesMap.get(n.getTaskActivityId());
                if (activity != null) {
                    n.setActivityDetail(activity);
                }
            }

            n.buildDisplayContent();
        }
        callback.onSuccess(notifications);
    }

    private String resolveReferenceName(Notification notification,
                                        long referenceId,
                                        Map<Long, String> projectNameMap,
                                        Map<Long, String> taskTitleMap,
                                        Map<Long, Long> commentTaskMap) {
        if (notification.getType() == Notification.NotificationType.PROJECT_INVITE) {
            return projectNameMap.get(referenceId);
        }

        if (isCommentReferenceType(notification.getType())) {
            Long taskId = commentTaskMap.get(referenceId);
            return taskId != null ? taskTitleMap.get(taskId) : null;
        }

        return taskTitleMap.get(referenceId);
    }

    private boolean isCommentReferenceType(Notification.NotificationType type) {
        return type == Notification.NotificationType.COMMENT
                || type == Notification.NotificationType.REACTION
                || type == Notification.NotificationType.DELETED;
    }

    /**
     * Fetch task activities for the given task IDs.
     * Used to enrich TASK_STATUS_CHANGED notifications with activity details.
     */
    private void fetchTaskActivitiesForNotifications(Set<Long> activityIds,
                                                     Map<Long, TaskActivity> activitiesMap,
                                                     NotificationCallback<Void> callback) {
        if (activityIds == null || activityIds.isEmpty()) {
            callback.onSuccess(null);
            return;
        }

        api.getTaskActivitiesByIds("in.(" + joinIds(activityIds) + ")",
                        "activity_id,task_id,user_id,action_type,old_value,new_value,created_at")
                .enqueue(new Callback<List<TaskActivity>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<TaskActivity>> call,
                                           @NonNull Response<List<TaskActivity>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            for (TaskActivity activity : response.body()) {
                                if (activity != null && activity.getId() > 0) {
                                    activitiesMap.put(activity.getId(), activity);
                                }
                            }
                        }
                        callback.onSuccess(null);
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<TaskActivity>> call, @NonNull Throwable t) {
                        Log.w(TAG, "Failed to fetch task activities by ids: " + t.getMessage());
                        callback.onError(t.getMessage());
                    }
                });
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