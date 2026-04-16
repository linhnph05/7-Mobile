package com.team7.taskflow.ui.notification;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.team7.taskflow.data.repository.NotificationRepository;
import com.team7.taskflow.domain.model.Notification;
import com.team7.taskflow.utils.SessionManager;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Periodic background worker to deliver local push notifications
 * from unread DB notifications.
 */
public class NotificationPushWorker extends Worker {

    public NotificationPushWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d("PushTest", "Worker started");
        Context appContext = getApplicationContext();
        SessionManager.init(appContext);

        if (!SessionManager.isLoggedIn()) {
            Log.d("PushTest", "Worker stopped: User not logged in");
            return Result.success();
        }

        String userId = SessionManager.getUserId();
        Log.d("PushTest", "Processing for userId: " + userId);
        
        if (userId == null || userId.trim().isEmpty()) {
            return Result.success();
        }

        NotificationRepository repository = NotificationRepository.getInstance();
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<Notification>> notificationsRef = new AtomicReference<>();
        
        // 1. Quét thông báo hệ thống (Unread)
        Log.d("PushTest", "Worker: Fetching notifications...");
        repository.getNotifications(userId, new NotificationRepository.NotificationCallback<List<Notification>>() {
            @Override
            public void onSuccess(List<Notification> result) {
                String userEmail = SessionManager.getUserEmail();
                repository.hydrateInviteStatuses(result, userEmail, () -> {
                    notificationsRef.set(result);
                    latch.countDown();
                });
            }
            @Override
            public void onError(String error) {
                latch.countDown();
            }
        });

        try {
            latch.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 2. Đẩy thông báo hệ thống (Unread, Invites, etc.)
        List<Notification> notifs = notificationsRef.get();
        if (notifs != null && !notifs.isEmpty()) {
            NotificationPushDispatcher.dispatchUnread(appContext, userId, notifs);
        }
        
        return Result.success();
    }
}
