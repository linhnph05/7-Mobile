package com.team7.taskflow.ui.project;

import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.content.Intent;

import androidx.annotation.Nullable;

import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.ProjectRepository;
import com.team7.taskflow.domain.model.ProjectHistoryItem;
import com.team7.taskflow.ui.base.BaseActivity;

import java.util.ArrayList;
import java.util.List;

public class ProjectActivityHistoryActivity extends BaseActivity {

    private long projectId;
    private ListView listHistory;
    private TextView tvEmpty;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_activity_history);

        projectId = readLongExtraFlexible(getIntent(), "project_id", -1L);
        if (projectId <= 0) {
            projectId = readLongExtraFlexible(getIntent(), "projectId", -1L);
        }
        if (projectId <= 0) {
            projectId = readLongExtraFlexible(getIntent(), "id", -1L);
        }
        String projectName = getIntent().getStringExtra("project_name");

        TextView tvTitle = findViewById(R.id.tvTitle);
        if (tvTitle != null && projectName != null && !projectName.trim().isEmpty()) {
            tvTitle.setText("Hoạt động của " + projectName.trim());
        } else if (tvTitle != null) {
            tvTitle.setText("Hoạt động");
        }

        listHistory = findViewById(R.id.listHistory);
        tvEmpty = findViewById(R.id.tvEmpty);
        progressBar = findViewById(R.id.progressBar);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        loadActivities();
    }

    private void loadActivities() {
        if (projectId <= 0) {
            showRows(new ArrayList<>());
            return;
        }

        setLoading(true);
        ProjectRepository.getInstance().getProjectHistoryFeed(projectId,
                new ProjectRepository.ProjectCallback<List<ProjectHistoryItem>>() {
                    @Override
                    public void onSuccess(List<ProjectHistoryItem> result) {
                        runOnUiThread(() -> {
                            setLoading(false);
                            showRows(result);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            setLoading(false);
                            showRows(new ArrayList<>());
                        });
                    }
                });
    }

    private void setLoading(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void showRows(List<ProjectHistoryItem> rows) {
        if (rows == null || rows.isEmpty()) {
            if (listHistory != null) listHistory.setVisibility(View.GONE);
            if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        if (listHistory != null) {
            listHistory.setVisibility(View.VISIBLE);
            listHistory.setAdapter(new HistoryEventAdapter(this, rows));
        }
    }

    private long readLongExtraFlexible(Intent intent, String key, long defaultValue) {
        if (intent == null || key == null || key.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            Object raw = intent.getExtras() != null ? intent.getExtras().get(key) : null;
            if (raw instanceof Number) {
                return ((Number) raw).longValue();
            }
            if (raw instanceof String) {
                return Long.parseLong(((String) raw).trim());
            }
        } catch (Exception ignored) {
            // Fall through to regular getLongExtra.
        }
        return intent.getLongExtra(key, defaultValue);
    }
}