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
import android.util.Log;

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

    private static final String CHANNEL_ID = "taskflow_push_channel_v3";
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
        if (context == null || userId == null || userId.trim().isEmpty() || notifications == null) {
            return;
        }

        Log.d("PushTest", "Dispatcher: dispatchUnread called with " + notifications.size() + " items");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.d("PushTest", "Dispatcher: Permission status = DENIED");
                return;
            }
        }
        Log.d("PushTest", "Dispatcher: Permission status = GRANTED");

        createChannelIfNeeded(context);

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        // Quan trọng: Phân tách key theo userId để hỗ trợ đa tài khoản
        String key = KEY_LAST_PUSHED_ID_PREFIX + userId;
        long lastPushedId = prefs.getLong(key, 0L);

        Log.d("PushTest", "Dispatcher: lastPushedId from memory is " + lastPushedId);
        
        // --- CƠ CHẾ TỰ SỬA LỖI (SELF-HEALING) ---
        // Nếu Server trả về danh sách có ID lớn nhất vẫn nhỏ hơn mốc cũ (nghi vấn DB bị xóa/reset)
        // Chúng ta sẽ reset lại mốc để tránh bị "kẹt" không hiện thông báo
        long serverMaxId = 0;
        for (Notification n : notifications) {
            if (n != null && n.getNotificationId() > serverMaxId) serverMaxId = n.getNotificationId();
        }
        if (serverMaxId > 0 && serverMaxId < lastPushedId) {
            Log.w("PushTest", "Dispatcher: Server MaxID (" + serverMaxId + ") < Local LastID (" + lastPushedId + "). Resetting tracker.");
            lastPushedId = 0; // Reset để nhận lại từ đầu
        }

        List<Notification> pending = new ArrayList<>();
        long newMaxId = lastPushedId;

        for (Notification notification : notifications) {
            if (notification == null) continue;
            
            long notificationId = notification.getNotificationId();
            
            // Cập nhật ID lớn nhất ngay khi quét qua, kể cả tin bị lọc bỏ
            if (notificationId > newMaxId) {
                newMaxId = notificationId;
            }

            // --- KIỂM TRA ĐIỀU KIỆN ---
            if (notification.isRead()) {
                Log.d("PushTest", "Dispatcher: ID " + notificationId + " ignored (Already Read)");
                continue;
            }
            if (userId.equals(notification.getActorId())) {
                Log.d("PushTest", "Dispatcher: ID " + notificationId + " ignored (Self Action)");
                continue;
            }
            
            if (notification.getType() == NotificationType.PROJECT_INVITE) {
                String status = notification.getInviteStatus();
                if ("ACCEPTED".equalsIgnoreCase(status) || "DENIED".equalsIgnoreCase(status)) {
                    Log.d("PushTest", "Dispatcher: ID " + notificationId + " ignored (Processed Invite)");
                    continue;
                }
            }

            if (!PUSH_ENABLED_TYPES.contains(notification.getType())) {
                Log.d("PushTest", "Dispatcher: ID " + notificationId + " ignored (Unsupported Type: " + notification.getType() + ")");
                continue;
            }

            // Chỉ đẩy nếu ID lớn hơn mốc ID đã đẩy lần trước
            if (notificationId <= lastPushedId) {
                Log.d("PushTest", "Dispatcher: ID " + notificationId + " ignored (<= lastPushedId " + lastPushedId + ")");
                continue;
            }

            Log.d("PushTest", "Dispatcher: ID " + notificationId + " PASSED. Ready for push.");
            pending.add(notification);
        }

        if (pending.isEmpty()) {
            if (newMaxId > lastPushedId) {
                Log.d("PushTest", "Dispatcher: Updating lastPushedId to " + newMaxId);
                // prefs.edit().putLong(key, newMaxId).commit();
            }
            return;
        }

        pending.sort((a, b) -> Long.compare(a.getNotificationId(), b.getNotificationId()));

        Log.d("PushTest", "Dispatcher: FOUND " + pending.size() + " NEW ITEMS. TRIGGERING PUSH LOGIC.");
        for (Notification notification : pending) {
            Log.d("PushTest", "Dispatcher: >>> PUSHING ID " + notification.getNotificationId() + " <<<");
            showSinglePush(context, notification);
        }

        Log.d("PushTest", "Dispatcher: Updating lastPushedId to " + newMaxId);
        // prefs.edit().putLong(key, newMaxId).commit();
    }

    private static void showSinglePush(Context context, Notification notification) {
        String title = getPushTitle(context, notification.getType());
        String body = notification.getContent();
        
        if (title == null || title.trim().isEmpty()) title = "TaskFlow Update";
        if (body == null || body.trim().isEmpty()) body = "Bạn có một thông báo mới.";

        Log.d("PushTest", "showSinglePush ID: " + notification.getNotificationId() + " using Channel: " + CHANNEL_ID);
        Log.d("PushTest", "Title: [" + title + "], Body: [" + body + "]");

        Intent openIntent = new Intent(context, NotificationsActivity.class);
        openIntent.putExtra("from_push", true);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                (int) (notification.getNotificationId() % Integer.MAX_VALUE),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String timeStr = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date());

        // Sử dụng giao diện Custom Layout của bạn (Đã sửa lỗi app:tint)
        android.widget.RemoteViews remoteViews = new android.widget.RemoteViews(context.getPackageName(), R.layout.layout_push_notification);
        remoteViews.setTextViewText(R.id.tvNotifTitle, title);
        remoteViews.setTextViewText(R.id.tvNotifMessage, body);
        remoteViews.setTextViewText(R.id.tvNotifTime, timeStr);
        remoteViews.setImageViewResource(R.id.ivNotifIcon, getPushIcon(notification.getType()));

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setCustomContentView(remoteViews) 
                .setCustomBigContentView(remoteViews) 
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        int notifId = 200000 + (int) (notification.getNotificationId() % 100000);
        Log.d("PushTest", "Calling notify() for ID: " + notifId);
        NotificationManagerCompat.from(context).notify(notifId, builder.build());
    }

    private static int getPushIcon(Notification.NotificationType type) {
        if (type == null) return R.drawable.ic_notification;
        switch (type) {
            case PROJECT_INVITE: return R.drawable.ic_members;
            case TASK_ASSIGNED:
            case TASK_STATUS_CHANGED: return R.drawable.ic_nav_tasks;
            case COMMENT: return R.drawable.ic_chat;
            case DEADLINE_REMINDER: return R.drawable.ic_calendar;
            default: return R.drawable.ic_notification;
        }
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
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(context.getString(R.string.push_channel_description));
        channel.enableVibration(true);
        channel.setShowBadge(true);
        manager.createNotificationChannel(channel);
    }
}
