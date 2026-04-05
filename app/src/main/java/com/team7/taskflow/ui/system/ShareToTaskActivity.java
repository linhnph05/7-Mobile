package com.team7.taskflow.ui.system;

import android.content.Intent;
import android.net.Uri;
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
import com.team7.taskflow.ui.timeline.ProjectDetailActivity;
import com.team7.taskflow.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

public class ShareToTaskActivity extends BaseActivity {

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

        handleShareIntent();
    }

    private void handleShareIntent() {
        Intent source = getIntent();
        if (source == null) {
            finish();
            return;
        }

        String action = source.getAction();
        String type = source.getType();

        String prefillTitle = "Shared item";
        String prefillDescription = "";

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                String text = source.getStringExtra(Intent.EXTRA_TEXT);
                if (text != null && !text.trim().isEmpty()) {
                    prefillDescription = text.trim();
                    prefillTitle = "Task from shared link";
                }
            } else if (type.startsWith("image/")) {
                Uri imageUri = source.getParcelableExtra(Intent.EXTRA_STREAM);
                if (imageUri != null) {
                    prefillDescription = imageUri.toString();
                    prefillTitle = "Task from shared image";
                }
            }
        }

        final String finalPrefillTitle = prefillTitle;
        final String finalPrefillDescription = prefillDescription;

        ProjectRepository.getInstance().getAllUserProjects(SessionManager.getUserId(),
                new ProjectRepository.ProjectCallback<List<Project>>() {
                    @Override
                    public void onSuccess(List<Project> result) {
                        runOnUiThread(() -> {
                            if (result == null || result.isEmpty()) {
                                Toast.makeText(ShareToTaskActivity.this, "No project available", Toast.LENGTH_SHORT).show();
                                finish();
                                return;
                            }

                            List<Project> recentProjects = pickTopRecentProjects(result, 3);
                            if (recentProjects.isEmpty()) {
                                Toast.makeText(ShareToTaskActivity.this, "No project available", Toast.LENGTH_SHORT).show();
                                finish();
                                return;
                            }

                            if (recentProjects.size() == 1) {
                                openTaskCreatorForProject(recentProjects.get(0), finalPrefillTitle, finalPrefillDescription);
                                return;
                            }

                            showRecentProjectPicker(recentProjects, finalPrefillTitle, finalPrefillDescription);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            Toast.makeText(ShareToTaskActivity.this, error, Toast.LENGTH_SHORT).show();
                            finish();
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

    private void showRecentProjectPicker(List<Project> projects, String prefillTitle, String prefillDescription) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.layout_shortcut_project_picker, null, false);
        bottomSheet.setContentView(sheetView);

        TextView tvTitle = sheetView.findViewById(R.id.tvPickerTitle);
        TextView tvSubtitle = sheetView.findViewById(R.id.tvPickerSubtitle);
        LinearLayout container = sheetView.findViewById(R.id.containerProjectPickerItems);
        TextView btnCancel = sheetView.findViewById(R.id.btnPickerCancel);

        if (tvTitle != null) {
            tvTitle.setText(getString(R.string.share_picker_title));
        }
        if (tvSubtitle != null) {
            tvSubtitle.setText(getString(R.string.share_picker_subtitle));
        }

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
            tvHint.setText(getString(R.string.share_picker_item_hint));

            itemView.setOnClickListener(v -> {
                bottomSheet.dismiss();
                openTaskCreatorForProject(project, prefillTitle, prefillDescription);
            });
            container.addView(itemView);
        }

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> {
                bottomSheet.dismiss();
                finish();
            });
        }

        bottomSheet.setOnCancelListener(dialog -> finish());
        bottomSheet.show();
    }

    private void openTaskCreatorForProject(Project project, String prefillTitle, String prefillDescription) {
        Intent intent = new Intent(this, ProjectDetailActivity.class);
        intent.putExtra("project_id", project.getId());
        intent.putExtra("project_name", project.getName());
        intent.putExtra("project_key", project.getProjectKey());
        intent.putExtra("project_desc", project.getDescription());
        intent.putExtra(ShortcutRouterActivity.EXTRA_OPEN_AI_CREATE, true);
        intent.putExtra("prefill_title", prefillTitle);
        intent.putExtra("prefill_description", prefillDescription);
        startActivity(intent);
        finish();
    }
}
