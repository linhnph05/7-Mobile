package com.team7.taskflow.ui.notification;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.team7.taskflow.R;
import com.team7.taskflow.domain.model.Notification;
import com.team7.taskflow.domain.model.Notification.NotificationType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Dispatches local push notifications for selected in-app notification types.
 * This keeps UI push behavior consistent and avoids duplicate delivery.
 */
public class NotificationPushDispatcher {

    private static final String CHANNEL_ID = "taskflow_updates";
    private static final String PREF_NAME = "taskflow_push_state";
    private static final String KEY_LAST_PUSHED_ID_PREFIX = "last_pushed_notification_id_";

    private static final Set<NotificationType> PUSH_ENABLED_TYPES = EnumSet.of(
            NotificationType.PROJECT_INVITE,
            NotificationType.TASK_ASSIGNED,
            NotificationType.TASK_STATUS_CHANGED,
            NotificationType.COMMENT,
            NotificationType.ATTACHMENT_ADDED,
            NotificationType.DEADLINE_REMINDER
    );

    private NotificationPushDispatcher() {
    }

    public static void dispatchUnread(Context context, String userId, List<Notification> notifications) {
        if (context == null || userId == null || userId.trim().isEmpty() || notifications == null || notifications.isEmpty()) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        createChannelIfNeeded(context);

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String key = KEY_LAST_PUSHED_ID_PREFIX + userId;
        long lastPushedId = prefs.getLong(key, 0L);

        List<Notification> pending = new ArrayList<>();
        long newMaxId = lastPushedId;
        for (Notification notification : notifications) {
            if (notification == null || notification.isRead()) {
                continue;
            }

            if (!PUSH_ENABLED_TYPES.contains(notification.getType())) {
                continue;
            }

            long notificationId = notification.getNotificationId();
            if (notificationId <= lastPushedId) {
                continue;
            }

            pending.add(notification);
            if (notificationId > newMaxId) {
                newMaxId = notificationId;
            }
        }

        if (pending.isEmpty()) {
            return;
        }

        // Oldest first for natural delivery order.
        pending.sort((a, b) -> Long.compare(a.getNotificationId(), b.getNotificationId()));
        for (Notification notification : pending) {
            showSinglePush(context, notification);
        }

        prefs.edit().putLong(key, newMaxId).apply();
    }

    private static void showSinglePush(Context context, Notification notification) {
        Intent openIntent = new Intent(context, NotificationsActivity.class);
        openIntent.putExtra("from_push", true);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                (int) (notification.getNotificationId() % Integer.MAX_VALUE),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = getPushTitle(context, notification.getType());
        String body = notification.getContent();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentIntent)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE);

        NotificationManagerCompat.from(context).notify(
                200000 + (int) (notification.getNotificationId() % 100000),
                builder.build()
        );
    }

    private static String getPushTitle(Context context, NotificationType type) {
        if (type == null) {
            return context.getString(R.string.push_title_general);
        }

        switch (type) {
            case PROJECT_INVITE:
                return context.getString(R.string.push_title_project_invite);
            case TASK_ASSIGNED:
                return context.getString(R.string.push_title_task_assigned);
            case TASK_STATUS_CHANGED:
                return context.getString(R.string.push_title_task_status_changed);
            case COMMENT:
                return context.getString(R.string.push_title_comment);
            case ATTACHMENT_ADDED:
                return context.getString(R.string.push_title_attachment_added);
            case DEADLINE_REMINDER:
                return context.getString(R.string.push_title_deadline_reminder);
            default:
                return context.getString(R.string.push_title_general);
        }
    }

    private static void createChannelIfNeeded(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }

        NotificationChannel existing = manager.getNotificationChannel(CHANNEL_ID);
        if (existing != null) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.push_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription(context.getString(R.string.push_channel_description));
        channel.enableVibration(true);
        channel.setShowBadge(true);
        manager.createNotificationChannel(channel);
    }
}
