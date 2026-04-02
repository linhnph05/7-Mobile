package com.team7.taskflow.ui.project;

import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.ProjectRepository;
import com.team7.taskflow.domain.model.ProjectActivity;
import com.team7.taskflow.ui.base.BaseActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProjectActivityHistoryActivity extends BaseActivity {

    private long projectId;
    private ListView listHistory;
    private TextView tvEmpty;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_activity_history);

        projectId = getIntent().getLongExtra("project_id", -1);
        String projectName = getIntent().getStringExtra("project_name");

        TextView tvTitle = findViewById(R.id.tvTitle);
        if (tvTitle != null && projectName != null && !projectName.trim().isEmpty()) {
            tvTitle.setText(projectName + " Activity");
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
        ProjectRepository.getInstance().getProjectActivities(projectId,
                new ProjectRepository.ProjectCallback<List<ProjectActivity>>() {
                    @Override
                    public void onSuccess(List<ProjectActivity> result) {
                        runOnUiThread(() -> {
                            setLoading(false);
                            showRows(formatRows(result));
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

    private List<String> formatRows(List<ProjectActivity> activities) {
        List<String> rows = new ArrayList<>();
        if (activities == null) {
            return rows;
        }

        for (ProjectActivity activity : activities) {
            String action = activity.getActionType() != null ? activity.getActionType() : "UPDATE";
            String oldValue = activity.getOldValue() != null ? activity.getOldValue() : "";
            String newValue = activity.getNewValue() != null ? activity.getNewValue() : "";
            String entity = activity.getEntityType() != null ? activity.getEntityType() : "PROJECT";
            String detail = entity + ": " + oldValue + " -> " + newValue;
            rows.add(formatTime(activity.getCreatedAt()) + " - " + action + " (" + detail + ")");
        }
        return rows;
    }

    private String formatTime(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "Vừa xong";
        }
        try {
            Date date = Date.from(java.time.OffsetDateTime.parse(raw).toInstant());
            return new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(date);
        } catch (Exception e) {
            return raw;
        }
    }

    private void setLoading(boolean loading) {
        if (progressBar != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private void showRows(List<String> rows) {
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
}