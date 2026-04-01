package com.team7.taskflow.ui.system;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.team7.taskflow.data.repository.ProjectRepository;
import com.team7.taskflow.domain.model.Project;
import com.team7.taskflow.ui.auth.LoginActivity;
import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.ui.dashboard.DashboardActivity;
import com.team7.taskflow.ui.project.CreateTaskActivity;
import com.team7.taskflow.ui.timeline.ProjectDetailActivity;
import com.team7.taskflow.utils.SessionManager;

import java.util.List;

public class ShortcutRouterActivity extends BaseActivity {

    public static final String ACTION_SHORTCUT_ADD_TASK = "com.team7.taskflow.action.SHORTCUT_ADD_TASK";
    public static final String ACTION_SHORTCUT_VIEW_CALENDAR = "com.team7.taskflow.action.SHORTCUT_VIEW_CALENDAR";
    public static final String ACTION_SHORTCUT_SEARCH = "com.team7.taskflow.action.SHORTCUT_SEARCH";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionManager.init(this);

        if (!SessionManager.isLoggedIn()) {
            Intent loginIntent = new Intent(this, LoginActivity.class);
            loginIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(loginIntent);
            finish();
            return;
        }

        String action = getIntent() != null ? getIntent().getAction() : null;
        if (ACTION_SHORTCUT_ADD_TASK.equals(action)) {
            openCreateTaskFromFirstProject();
            return;
        }

        if (ACTION_SHORTCUT_VIEW_CALENDAR.equals(action)) {
            Intent intent = new Intent(this, ProjectDetailActivity.class);
            intent.putExtra("is_my_tasks", true);
            intent.putExtra("project_name", "My Assigned Tasks");
            startActivity(intent);
            finish();
            return;
        }

        Intent dashboardIntent = new Intent(this, DashboardActivity.class);
        startActivity(dashboardIntent);
        finish();
    }

    private void openCreateTaskFromFirstProject() {
        String userId = SessionManager.getUserId();
        ProjectRepository.getInstance().getAllUserProjects(userId, new ProjectRepository.ProjectCallback<List<Project>>() {
            @Override
            public void onSuccess(List<Project> result) {
                runOnUiThread(() -> {
                    if (result == null || result.isEmpty()) {
                        Toast.makeText(ShortcutRouterActivity.this, "No project available", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(ShortcutRouterActivity.this, DashboardActivity.class));
                        finish();
                        return;
                    }

                    Project firstProject = result.get(0);
                    Intent intent = new Intent(ShortcutRouterActivity.this, CreateTaskActivity.class);
                    intent.putExtra("project_id", firstProject.getId());
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ShortcutRouterActivity.this, error, Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(ShortcutRouterActivity.this, DashboardActivity.class));
                    finish();
                });
            }
        });
    }
}
