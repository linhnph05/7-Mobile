package com.team7.taskflow.ui.system;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.team7.taskflow.data.repository.TaskRepository;

public class StickyTaskActionReceiver extends BroadcastReceiver {

    public static final String ACTION_DONE = "com.team7.taskflow.action.STICKY_DONE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_DONE.equals(intent.getAction())) return;

        long taskId = intent.getLongExtra("task_id", -1L);
        if (taskId <= 0L) return;

        TaskRepository.getInstance().updateTaskStatus(taskId, "DOING", "DONE", new TaskRepository.TaskCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Intent refresh = new Intent(context, StickyTaskService.class);
                context.startForegroundService(refresh);
            }

            @Override
            public void onError(String error) {
            }
        });
    }
}
