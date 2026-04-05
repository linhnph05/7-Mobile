package com.team7.taskflow.ui.notification;

import android.content.Context;

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
        Context appContext = getApplicationContext();
        SessionManager.init(appContext);

        if (!SessionManager.isLoggedIn()) {
            return Result.success();
        }

        String userId = SessionManager.getUserId();
        if (userId == null || userId.trim().isEmpty()) {
            return Result.success();
        }

        NotificationRepository repository = NotificationRepository.getInstance();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<Notification>> dataRef = new AtomicReference<>();
        AtomicReference<String> errorRef = new AtomicReference<>();

        repository.getNotifications(userId, new NotificationRepository.NotificationCallback<List<Notification>>() {
            @Override
            public void onSuccess(List<Notification> result) {
                dataRef.set(result);
                latch.countDown();
            }

            @Override
            public void onError(String error) {
                errorRef.set(error);
                latch.countDown();
            }
        });

        try {
            boolean completed = latch.await(25, TimeUnit.SECONDS);
            if (!completed) {
                return Result.retry();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.retry();
        }

        if (errorRef.get() != null) {
            return Result.retry();
        }

        NotificationPushDispatcher.dispatchUnread(appContext, userId, dataRef.get());
        return Result.success();
    }
}
