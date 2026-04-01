package com.team7.taskflow.ui.system;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.ui.timeline.ProjectDetailActivity;
import com.team7.taskflow.utils.SessionManager;

import java.util.List;

public class StickyTaskService extends Service {

    public static final String CHANNEL_ID = "taskflow_sticky_channel";
    public static final int NOTIFICATION_ID = 1001;
    public static final String ACTION_REFRESH = "com.team7.taskflow.action.STICKY_REFRESH";

    @Override
    public void onCreate() {
        super.onCreate();
        SessionManager.init(this);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        loadAndShowTopTask();
        return START_STICKY;
    }

    private void loadAndShowTopTask() {
        String userId = SessionManager.getUserId();
        TaskRepository.getInstance().getMyTasksWithProjectName(userId, new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> result) {
                Task topTask = pickTopTask(result);
                Notification notification = buildNotification(topTask);
                startForeground(NOTIFICATION_ID, notification);
            }

            @Override
            public void onError(String error) {
                Notification notification = buildNotification(null);
                startForeground(NOTIFICATION_ID, notification);
            }
        });
    }

    private Task pickTopTask(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) return null;

        for (Task task : tasks) {
            if (task.getStatus() != null && "DOING".equalsIgnoreCase(task.getStatus())) {
                return task;
            }
        }

        for (Task task : tasks) {
            String status = task.getStatus() != null ? task.getStatus() : "";
            if (!"DONE".equalsIgnoreCase(status) && !"TRASH".equalsIgnoreCase(status)) {
                return task;
            }
        }
        return null;
    }

    private Notification buildNotification(@Nullable Task task) {
        Intent openIntent = new Intent(this, ProjectDetailActivity.class);
        openIntent.putExtra("is_my_tasks", true);
        openIntent.putExtra("project_name", "My Assigned Tasks");
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this, 11, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.sticky_title))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(openPendingIntent);

        if (task == null || task.getId() == null) {
            builder.setContentText(getString(R.string.sticky_no_task));
            return builder.build();
        }

        builder.setContentText(task.getTitle());

        Intent doneIntent = new Intent(this, StickyTaskActionReceiver.class);
        doneIntent.setAction(StickyTaskActionReceiver.ACTION_DONE);
        doneIntent.putExtra("task_id", task.getId());
        PendingIntent donePendingIntent = PendingIntent.getBroadcast(
                this,
                12,
                doneIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        builder.addAction(R.drawable.ic_check, getString(R.string.sticky_done_action), donePendingIntent);
        return builder.build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.sticky_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.sticky_channel_desc));
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
