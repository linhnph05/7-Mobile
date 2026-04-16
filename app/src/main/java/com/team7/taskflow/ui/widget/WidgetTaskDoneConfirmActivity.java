package com.team7.taskflow.ui.widget;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.team7.taskflow.R;

public class WidgetTaskDoneConfirmActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        long taskId = getIntent().getLongExtra("task_id", -1L);
        if (taskId <= 0L) {
            finish();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.widget_confirm_done_title)
                .setMessage(R.string.widget_confirm_done_message)
                .setNegativeButton(R.string.widget_confirm_done_cancel, (dialog, which) -> {
                    dialog.dismiss();
                    finish();
                })
                .setPositiveButton(R.string.widget_confirm_done_action, (dialog, which) -> {
                    Intent doneIntent = new Intent(this, TaskTodayWidgetProvider.class);
                    doneIntent.setAction(TaskTodayWidgetProvider.ACTION_MARK_DONE);
                    doneIntent.putExtra("task_id", taskId);
                    sendBroadcast(doneIntent);
                    dialog.dismiss();
                    finish();
                })
                .setOnCancelListener(dialog -> finish())
                .show();
    }
}
