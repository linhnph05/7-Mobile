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
import com.team7.taskflow.utils.SessionManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class TaskTodaySummaryWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        SessionManager.init(context);
        updateWidgets(context, appWidgetManager, appWidgetIds);
    }

    public static void refreshAll(Context context) {
        SessionManager.init(context);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, TaskTodaySummaryWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(provider);
        if (ids != null && ids.length > 0) {
            new TaskTodaySummaryWidgetProvider().updateWidgets(context, manager, ids);
        }
    }

    private void updateWidgets(Context context, AppWidgetManager manager, int[] ids) {
        String userId = SessionManager.getUserId();
        TaskRepository.getInstance().getMyTasksWithProjectName(userId, new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> result) {
                List<Task> todayTasks = filterTodayScope(result);
                int total = todayTasks.size();
                int done = 0;
                for (Task task : todayTasks) {
                    if (task != null && "DONE".equalsIgnoreCase(task.getStatus())) {
                        done++;
                    }
                }
                int remaining = Math.max(total - done, 0);
                int percent = total == 0 ? 0 : (int) Math.round((done * 100.0) / total);

                for (int appWidgetId : ids) {
                    RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_today_summary_2x2);
                    views.setTextViewText(R.id.tvSummaryPercent,
                        context.getString(R.string.widget_summary_percent_format, percent));
                    views.setTextViewText(R.id.tvSummaryCount,
                        context.getString(R.string.widget_summary_count_format, remaining));
                    views.setProgressBar(R.id.pbSummaryProgress, 100, percent, false);

                    Intent openIntent = new Intent(context, DashboardActivity.class);
                    PendingIntent openPending = PendingIntent.getActivity(
                            context,
                            appWidgetId,
                            openIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    views.setOnClickPendingIntent(R.id.widgetSummaryRoot, openPending);

                    manager.updateAppWidget(appWidgetId, views);
                }
            }

            @Override
            public void onError(String error) {
                for (int appWidgetId : ids) {
                    RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_today_summary_2x2);
                    views.setTextViewText(R.id.tvSummaryPercent,
                            context.getString(R.string.widget_summary_percent_format, 0));
                    views.setTextViewText(R.id.tvSummaryCount,
                            context.getString(R.string.widget_summary_count_format, 0));
                    views.setProgressBar(R.id.pbSummaryProgress, 100, 0, false);
                    manager.updateAppWidget(appWidgetId, views);
                }
            }
        });
    }

    private List<Task> filterTodayScope(List<Task> tasks) {
        List<Task> filtered = new ArrayList<>();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (tasks == null) {
            return filtered;
        }

        for (Task task : tasks) {
            if (task == null) {
                continue;
            }

            String status = task.getStatus() != null ? task.getStatus() : "";
            if ("TRASH".equalsIgnoreCase(status)) {
                continue;
            }

            LocalDate dueDate = parseLocalDate(task.getDueDate());
            LocalDate startDate = parseLocalDate(task.getStartDate());
            if (isTaskInTodayRange(today, startDate, dueDate)) {
                filtered.add(task);
            }
        }

        return filtered;
    }

    private boolean isTaskInTodayRange(LocalDate today, LocalDate startDate, LocalDate dueDate) {
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

    private LocalDate parseLocalDate(String raw) {
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
}