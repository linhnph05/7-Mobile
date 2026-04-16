package com.team7.taskflow.ui.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.ui.dashboard.DashboardActivity;
import com.team7.taskflow.ui.project.TaskDetailActivity;
import com.team7.taskflow.utils.SessionManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TaskTodayWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_MARK_DONE = "com.team7.taskflow.action.WIDGET_MARK_DONE";
    public static final String ACTION_RELOAD = "com.team7.taskflow.action.WIDGET_RELOAD";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        SessionManager.init(context);
        updateWidgets(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null) return;

        if (ACTION_MARK_DONE.equals(intent.getAction())) {
            long taskId = intent.getLongExtra("task_id", -1L);
            if (taskId > 0L) {
                TaskRepository.getInstance().getTaskById(taskId, new TaskRepository.TaskCallback<Task>() {
                    @Override
                    public void onSuccess(Task task) {
                        String oldStatus = task != null && task.getStatus() != null && !task.getStatus().trim().isEmpty()
                                ? task.getStatus().trim()
                                : "TODO";
                        markTaskDone(taskId, oldStatus, context);
                    }

                    @Override
                    public void onError(String error) {
                        markTaskDone(taskId, "TODO", context);
                    }
                });
            }
            return;
        }

        if (ACTION_RELOAD.equals(intent.getAction())) {
            refreshAll(context);
        }
    }

    private void markTaskDone(long taskId, String oldStatus, Context context) {
        TaskRepository.getInstance().updateTaskStatus(taskId, oldStatus, "DONE", new TaskRepository.TaskCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                refreshAll(context);
            }

            @Override
            public void onError(String error) {
                refreshAll(context);
            }
        });
    }

    public static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, TaskTodayWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(provider);
        updateWidgets(context, manager, ids);
        TaskTodaySummaryWidgetProvider.refreshAll(context);
    }

    private static void updateWidgets(Context context, AppWidgetManager manager, int[] ids) {
        String userId = SessionManager.getUserId();
        TaskRepository.getInstance().getMyTasksWithProjectName(userId, new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> result) {
                List<Task> recentOpen = filterRecentOpenTasks(result);
                for (int appWidgetId : ids) {
                    RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_today_tasks);
                    views.setTextViewText(R.id.tvWidgetCount,
                            context.getString(R.string.widget_recent_count_format, recentOpen.size()));
                    views.setViewVisibility(R.id.btnWidgetReload, android.view.View.VISIBLE);
                    views.setViewVisibility(R.id.tvWidgetEmpty,
                            recentOpen.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
                    bindRow(context, views, recentOpen, 0, appWidgetId, R.id.rowTask1, R.id.ivState1, R.id.tvTask1, R.id.tvMeta1);
                    bindRow(context, views, recentOpen, 1, appWidgetId, R.id.rowTask2, R.id.ivState2, R.id.tvTask2, R.id.tvMeta2);
                    bindRow(context, views, recentOpen, 2, appWidgetId, R.id.rowTask3, R.id.ivState3, R.id.tvTask3, R.id.tvMeta3);

                    Intent openIntent = new Intent(context, DashboardActivity.class);
                    PendingIntent openPending = PendingIntent.getActivity(
                            context, appWidgetId, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    views.setOnClickPendingIntent(R.id.tvWidgetTitle, openPending);
                    views.setOnClickPendingIntent(R.id.widgetRoot, openPending);

                    PendingIntent reloadPending = buildReloadPendingIntent(context, appWidgetId);
                    views.setOnClickPendingIntent(R.id.btnWidgetReload, reloadPending);

                    manager.updateAppWidget(appWidgetId, views);
                }
            }

            @Override
            public void onError(String error) {
                for (int appWidgetId : ids) {
                    RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_today_tasks);
                    views.setTextViewText(R.id.tvWidgetCount,
                        context.getString(R.string.widget_recent_count_format, 0));
                    views.setViewVisibility(R.id.btnWidgetReload, android.view.View.VISIBLE);
                    views.setViewVisibility(R.id.tvWidgetEmpty, android.view.View.VISIBLE);
                    views.setTextViewText(R.id.tvWidgetEmpty, context.getString(R.string.widget_error));
                    views.setViewVisibility(R.id.rowTask2, android.view.View.GONE);
                    views.setViewVisibility(R.id.rowTask3, android.view.View.GONE);
                    views.setViewVisibility(R.id.rowTask1, android.view.View.GONE);

                    PendingIntent reloadPending = buildReloadPendingIntent(context, appWidgetId);
                    views.setOnClickPendingIntent(R.id.btnWidgetReload, reloadPending);

                    manager.updateAppWidget(appWidgetId, views);
                }
            }
        });
    }

    private static PendingIntent buildReloadPendingIntent(Context context, int appWidgetId) {
        Intent reloadIntent = new Intent(context, TaskTodayWidgetProvider.class);
        reloadIntent.setAction(ACTION_RELOAD);
        return PendingIntent.getBroadcast(
                context,
                9000 + appWidgetId,
                reloadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static List<Task> filterRecentOpenTasks(List<Task> tasks) {
        List<Task> filtered = new ArrayList<>();
        if (tasks == null) return filtered;

        for (Task task : tasks) {
            if (task == null) continue;
            String status = task.getStatus() != null ? task.getStatus() : "";
            if ("DONE".equalsIgnoreCase(status) || "TRASH".equalsIgnoreCase(status)) {
                continue;
            }
            filtered.add(task);
            if (filtered.size() >= 3) break;
        }
        return filtered;
    }

    private static boolean isTaskInTodayRange(LocalDate today, LocalDate startDate, LocalDate dueDate) {
        if (today == null) {
            return false;
        }

        if (startDate != null && dueDate != null) {
            return !today.isBefore(startDate) && !today.isAfter(dueDate);
        }

        if (dueDate != null) {
            return today.equals(dueDate);
        }

        if (startDate != null) {
            return today.equals(startDate);
        }

        return false;
    }

    private static LocalDate parseLocalDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }

        String value = raw.trim();
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
        } catch (Exception ignored) {
        }

        try {
            return LocalDateTime.parse(value).toLocalDate();
        } catch (Exception ignored) {
        }

        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ignored) {
        }

        if (value.length() >= 10) {
            try {
                return LocalDate.parse(value.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private static void bindRow(Context context,
            RemoteViews views,
            List<Task> tasks,
            int index,
            int appWidgetId,
            int rowId,
            int checkId,
            int textId,
            int metaId) {
        if (tasks.size() <= index) {
            views.setViewVisibility(rowId, android.view.View.GONE);
            return;
        }

        Task task = tasks.get(index);
        views.setViewVisibility(rowId, android.view.View.VISIBLE);
        String fallback = task.getId() != null ? "Task #" + task.getId() : "Untitled task";
        String title = task.getTitle() != null && !task.getTitle().trim().isEmpty() ? task.getTitle().trim() : fallback;
        views.setTextViewText(textId, title);
        views.setTextViewText(metaId, mapPriorityLabel(task.getPriority()));

        if (task.getId() == null || task.getId() <= 0L) {
            return;
        }

        Intent doneIntent = new Intent(context, WidgetTaskDoneConfirmActivity.class);
        doneIntent.putExtra("task_id", task.getId());
        doneIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        doneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent donePending = PendingIntent.getActivity(
            context,
            appWidgetId * 10 + index,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(checkId, donePending);

        Intent openTaskIntent = new Intent(context, TaskDetailActivity.class);
        openTaskIntent.putExtra("task_id", task.getId());
        openTaskIntent.putExtra("project_id", task.getProjectId());
        openTaskIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openTaskPending = PendingIntent.getActivity(
            context,
            appWidgetId * 100 + index,
            openTaskIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(rowId, openTaskPending);
    }

    private static String mapPriorityLabel(String rawPriority) {
        if (rawPriority == null || rawPriority.trim().isEmpty()) {
            return "MEDIUM";
        }
        String value = rawPriority.trim().toUpperCase(Locale.US);
        if ("HIGH".equals(value)) {
            return "HIGH PRIORITY";
        }
        if ("LOW".equals(value)) {
            return "LOW PRIORITY";
        }
        return "MEDIUM";
    }
}
