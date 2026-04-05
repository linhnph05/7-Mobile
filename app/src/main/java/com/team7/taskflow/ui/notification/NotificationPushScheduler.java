package com.team7.taskflow.ui.notification;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/**
 * Schedules periodic background sync for push notifications.
 */
public class NotificationPushScheduler {

    private static final String UNIQUE_WORK_NAME = "taskflow_notification_push_sync";

    private NotificationPushScheduler() {
    }

    public static void ensureScheduled(Context context) {
        if (context == null) {
            return;
        }

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                NotificationPushWorker.class,
                15,
                TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(
                        UNIQUE_WORK_NAME,
                        ExistingPeriodicWorkPolicy.KEEP,
                        workRequest
                );
    }

    public static void cancel(Context context) {
        if (context == null) {
            return;
        }

        WorkManager.getInstance(context.getApplicationContext())
                .cancelUniqueWork(UNIQUE_WORK_NAME);
    }
}
