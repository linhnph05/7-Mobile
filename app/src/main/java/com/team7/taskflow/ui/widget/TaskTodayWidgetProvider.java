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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskTodayWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_MARK_DONE = "com.team7.taskflow.action.WIDGET_MARK_DONE";

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
                TaskRepository.getInstance().updateTaskStatus(taskId, "TODO", "DONE", new TaskRepository.TaskCallback<Void>() {
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
        }
    }

    private void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, TaskTodayWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(provider);
        updateWidgets(context, manager, ids);
    }

    private void updateWidgets(Context context, AppWidgetManager manager, int[] ids) {
        String userId = SessionManager.getUserId();
        TaskRepository.getInstance().getMyTasksWithProjectName(userId, new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> result) {
                List<Task> today = filterToday(result);
                for (int appWidgetId : ids) {
                    RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_today_tasks);
                    bindRow(context, views, today, 0, R.id.rowTask1, R.id.tvTask1, R.id.btnDone1);
                    bindRow(context, views, today, 1, R.id.rowTask2, R.id.tvTask2, R.id.btnDone2);
                    bindRow(context, views, today, 2, R.id.rowTask3, R.id.tvTask3, R.id.btnDone3);

                            Intent openIntent = new Intent(context, DashboardActivity.class);
                    PendingIntent openPending = PendingIntent.getActivity(
                            context, appWidgetId, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    views.setOnClickPendingIntent(R.id.tvWidgetTitle, openPending);

                    manager.updateAppWidget(appWidgetId, views);
                }
            }

            @Override
            public void onError(String error) {
                for (int appWidgetId : ids) {
                    RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_today_tasks);
                    views.setTextViewText(R.id.tvTask1, context.getString(R.string.widget_error));
                    views.setViewVisibility(R.id.rowTask2, android.view.View.GONE);
                    views.setViewVisibility(R.id.rowTask3, android.view.View.GONE);
                    manager.updateAppWidget(appWidgetId, views);
                }
            }
        });
    }

    private List<Task> filterToday(List<Task> tasks) {
        List<Task> filtered = new ArrayList<>();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (tasks == null) return filtered;

        for (Task task : tasks) {
            if (task == null) continue;
            String due = task.getDueDate();
            String status = task.getStatus() != null ? task.getStatus() : "";
            if (due != null && due.length() >= 10 && today.equals(due.substring(0, 10))
                    && !"DONE".equalsIgnoreCase(status)
                    && !"TRASH".equalsIgnoreCase(status)) {
                filtered.add(task);
            }
            if (filtered.size() >= 3) break;
        }
        return filtered;
    }

    private void bindRow(Context context, RemoteViews views, List<Task> tasks, int index, int rowId, int textId, int doneBtnId) {
        if (tasks.size() <= index) {
            views.setViewVisibility(rowId, android.view.View.GONE);
            return;
        }

        Task task = tasks.get(index);
        views.setViewVisibility(rowId, android.view.View.VISIBLE);
        views.setTextViewText(textId, task.getTitle());

        Intent doneIntent = new Intent(context, TaskTodayWidgetProvider.class);
        doneIntent.setAction(ACTION_MARK_DONE);
        doneIntent.putExtra("task_id", task.getId());
        PendingIntent donePending = PendingIntent.getBroadcast(
                context,
                500 + index,
                doneIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(doneBtnId, donePending);
    }
}
