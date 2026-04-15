package com.team7.taskflow.ui.system;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.ProjectRepository;
import com.team7.taskflow.domain.model.Project;
import com.team7.taskflow.ui.auth.LoginActivity;
import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.ui.dashboard.DashboardActivity;
import com.team7.taskflow.ui.foryou.ForYouActivity;
import com.team7.taskflow.ui.timeline.ProjectDetailActivity;
import com.team7.taskflow.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

public class ShortcutRouterActivity extends BaseActivity {

    public static final String ACTION_SHORTCUT_ADD_TASK = "com.team7.taskflow.action.SHORTCUT_ADD_TASK";
    public static final String ACTION_SHORTCUT_VIEW_CALENDAR = "com.team7.taskflow.action.SHORTCUT_VIEW_CALENDAR";
    public static final String ACTION_SHORTCUT_SEARCH = "com.team7.taskflow.action.SHORTCUT_SEARCH";
    public static final String EXTRA_OPEN_AI_CREATE = "extra_open_ai_create";

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
            Intent intent = new Intent(this, ForYouActivity.class);
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
                        openDashboardAndFinish();
                        return;
                    }

                    List<Project> recentProjects = pickTopRecentProjects(result, 3);
                    if (recentProjects.isEmpty()) {
                        openDashboardAndFinish();
                        return;
                    }

                    if (recentProjects.size() == 1) {
                        openTaskCreatorForProject(recentProjects.get(0));
                        return;
                    }

                    showRecentProjectPicker(recentProjects);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ShortcutRouterActivity.this, error, Toast.LENGTH_SHORT).show();
                    openDashboardAndFinish();
                });
            }
        });
    }

    private List<Project> pickTopRecentProjects(List<Project> projects, int limit) {
        List<Project> top = new ArrayList<>();
        if (projects == null || projects.isEmpty() || limit <= 0) {
            return top;
        }

        for (Project project : projects) {
            if (project == null || project.getId() <= 0) {
                continue;
            }
            top.add(project);
            if (top.size() >= limit) {
                break;
            }
        }
        return top;
    }

    private void showRecentProjectPicker(List<Project> projects) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.layout_shortcut_project_picker, null, false);
        bottomSheet.setContentView(sheetView);

        LinearLayout container = sheetView.findViewById(R.id.containerProjectPickerItems);
        TextView btnCancel = sheetView.findViewById(R.id.btnPickerCancel);

        for (Project project : projects) {
            if (project == null || project.getId() <= 0) {
                continue;
            }

            View itemView = LayoutInflater.from(this).inflate(R.layout.item_shortcut_project_picker, container, false);
            TextView tvName = itemView.findViewById(R.id.tvProjectPickerName);
            TextView tvHint = itemView.findViewById(R.id.tvProjectPickerHint);

            String projectName = project.getName() != null && !project.getName().trim().isEmpty()
                    ? project.getName().trim()
                    : getString(R.string.shortcut_picker_project_fallback, project.getId());
            tvName.setText(projectName);
            tvHint.setText(getString(R.string.shortcut_picker_item_hint));

            itemView.setOnClickListener(v -> {
                bottomSheet.dismiss();
                openTaskCreatorForProject(project);
            });
            container.addView(itemView);
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                bottomSheet.dismiss();
                openDashboardAndFinish();
            });
        }

        bottomSheet.setOnCancelListener(dialog -> openDashboardAndFinish());
        bottomSheet.show();
    }

    private void openTaskCreatorForProject(Project project) {
        Intent intent = new Intent(this, ProjectDetailActivity.class);
        intent.putExtra("project_id", project.getId());
        intent.putExtra("project_name", project.getName());
        intent.putExtra("project_key", project.getProjectKey());
        intent.putExtra("user_role", project.getUserRole());
        intent.putExtra(EXTRA_OPEN_AI_CREATE, true);
        startActivity(intent);
        finish();
    }

    private void openDashboardAndFinish() {
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }
}
