package com.team7.taskflow.ui.project;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.ui.base.BaseActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class FocusModeActivity extends BaseActivity {

    private static final long FOCUS_DURATION_MS = 25L * 60L * 1000L;
    private static final int FOCUS_DURATION_SECONDS = (int) (FOCUS_DURATION_MS / 1000L);
    private static final String WORKLOG_PREFS = "task_worklog";
    private static final String WORKLOG_TOTAL_PREFIX = "task_total_";
    private static final String WORKLOG_LOGS_PREFIX = "task_logs_";

    private TextView tvTaskTitle;
    private TextView tvTaskDescription;
    private TextView tvTimer;
    private TextView tvFocusState;
    private CircularProgressIndicator progressTimer;
    private TextView btnStopFocus;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = this::tick;

    private TaskRepository taskRepository;
    private long taskId = -1L;
    private String taskTitle;
    private String taskDescription;
    private long sessionStartedAtMs;
    private long timerStartedElapsedMs;
    private long remainingMs = FOCUS_DURATION_MS;
    private boolean running = false;
    private boolean resultPromptShown = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_focus_mode);

        taskRepository = TaskRepository.getInstance();

        taskId = getIntent().getLongExtra("task_id", -1L);
        taskTitle = getIntent().getStringExtra("task_title");
        taskDescription = getIntent().getStringExtra("task_description");
        if (taskId <= 0) {
            Toast.makeText(this, getString(R.string.task_not_found), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (taskTitle == null || taskTitle.trim().isEmpty()) {
            taskTitle = "Task #" + taskId;
        }

        initViews();
        startFocusSession();
    }

    private void initViews() {
        tvTaskTitle = findViewById(R.id.tvFocusTaskTitle);
        tvTaskDescription = findViewById(R.id.tvFocusTaskDescription);
        tvTimer = findViewById(R.id.tvFocusTimer);
        tvFocusState = findViewById(R.id.tvFocusState);
        progressTimer = findViewById(R.id.progressFocusTimer);
        btnStopFocus = findViewById(R.id.btnStopFocus);

        tvTaskTitle.setText(taskTitle);
        if (tvTaskDescription != null) {
            String safeDescription = taskDescription != null ? taskDescription.trim() : "";
            if (safeDescription.isEmpty()) {
                tvTaskDescription.setVisibility(View.GONE);
            } else {
                tvTaskDescription.setVisibility(View.VISIBLE);
                tvTaskDescription.setText(safeDescription);
            }
        }
        progressTimer.setMax(FOCUS_DURATION_SECONDS);
        progressTimer.setProgress(0);
        btnStopFocus.setOnClickListener(v -> promptSessionResult(false));

        updateTimerUi();
    }

    private void startFocusSession() {
        sessionStartedAtMs = System.currentTimeMillis();
        timerStartedElapsedMs = SystemClock.elapsedRealtime();
        remainingMs = FOCUS_DURATION_MS;
        running = true;
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    private void tick() {
        if (!running) {
            return;
        }

        long elapsedMs = SystemClock.elapsedRealtime() - timerStartedElapsedMs;
        remainingMs = Math.max(0L, FOCUS_DURATION_MS - elapsedMs);
        updateTimerUi();

        if (remainingMs <= 0L) {
            promptSessionResult(true);
            return;
        }

        handler.postDelayed(ticker, 1000L);
    }

    private void updateTimerUi() {
        long totalSec = Math.max(0L, remainingMs / 1000L);
        long min = totalSec / 60L;
        long sec = totalSec % 60L;
        tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", min, sec));

        int elapsedSec = FOCUS_DURATION_SECONDS - (int) totalSec;
        progressTimer.setProgressCompat(Math.max(0, elapsedSec), true);

        if (remainingMs <= 0L) {
            tvFocusState.setText("Time is up");
        } else {
            tvFocusState.setText("Deep focus mode");
        }
    }

    private void promptSessionResult(boolean timedOut) {
        if (resultPromptShown) {
            return;
        }
        resultPromptShown = true;
        running = false;
        handler.removeCallbacks(ticker);

        long endedAtMs = System.currentTimeMillis();
        long workedMs = Math.max(0L, FOCUS_DURATION_MS - remainingMs);

        String title = timedOut ? "Pomodoro finished" : "Stop focus mode";
        String message = timedOut
                ? "25 minutes completed. Is this task done?"
                : "Is this task done?";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Yes, done", (dialog, which) -> completeTaskAsDone())
                .setNegativeButton("Not yet", (dialog, which) -> {
                    saveWorklog(taskId, sessionStartedAtMs, endedAtMs, workedMs, timedOut);
                    Intent data = new Intent();
                    data.putExtra("task_done", false);
                    data.putExtra("worklog_updated", true);
                    setResult(RESULT_OK, data);
                    finish();
                })
                .show();
    }

    private void completeTaskAsDone() {
        taskRepository.getTaskById(taskId, new TaskRepository.TaskCallback<Task>() {
            @Override
            public void onSuccess(Task task) {
                String oldStatus = task != null && task.getStatus() != null
                        ? task.getStatus()
                        : "TODO";

                if ("DONE".equalsIgnoreCase(oldStatus)) {
                    Intent data = new Intent();
                    data.putExtra("task_done", true);
                    setResult(RESULT_OK, data);
                    finish();
                    return;
                }

                taskRepository.updateTaskStatus(taskId, oldStatus, "DONE", new TaskRepository.TaskCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        runOnUiThread(() -> {
                            Toast.makeText(FocusModeActivity.this, "Task moved to Done", Toast.LENGTH_SHORT).show();
                            Intent data = new Intent();
                            data.putExtra("task_done", true);
                            setResult(RESULT_OK, data);
                            finish();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(FocusModeActivity.this, "Failed to update task: " + error, Toast.LENGTH_SHORT).show();
                            setResult(RESULT_CANCELED);
                            finish();
                        });
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(FocusModeActivity.this, "Failed to load task: " + error, Toast.LENGTH_SHORT).show();
                    setResult(RESULT_CANCELED);
                    finish();
                });
            }
        });
    }

    private void saveWorklog(long safeTaskId, long startedAt, long endedAt, long durationMs, boolean timedOut) {
        if (durationMs <= 0L) {
            return;
        }

        SharedPreferences prefs = getSharedPreferences(WORKLOG_PREFS, MODE_PRIVATE);
        long total = prefs.getLong(WORKLOG_TOTAL_PREFIX + safeTaskId, 0L) + durationMs;

        JSONArray entries;
        try {
            entries = new JSONArray(prefs.getString(WORKLOG_LOGS_PREFIX + safeTaskId, "[]"));
        } catch (Exception ignored) {
            entries = new JSONArray();
        }

        JSONObject entry = new JSONObject();
        try {
            entry.put("started_at", startedAt);
            entry.put("ended_at", endedAt);
            entry.put("duration_ms", durationMs);
            entry.put("completed", false);
            entry.put("timed_out", timedOut);
            entries.put(entry);
        } catch (Exception ignored) {
            return;
        }

        JSONArray trimmed = new JSONArray();
        int startIndex = Math.max(0, entries.length() - 20);
        for (int i = startIndex; i < entries.length(); i++) {
            JSONObject obj = entries.optJSONObject(i);
            if (obj != null) {
                trimmed.put(obj);
            }
        }

        prefs.edit()
                .putLong(WORKLOG_TOTAL_PREFIX + safeTaskId, total)
                .putString(WORKLOG_LOGS_PREFIX + safeTaskId, trimmed.toString())
                .apply();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        running = false;
        super.onDestroy();
    }
}
