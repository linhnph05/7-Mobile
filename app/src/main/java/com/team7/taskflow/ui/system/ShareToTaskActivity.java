package com.team7.taskflow.ui.system;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.team7.taskflow.data.repository.ProjectRepository;
import com.team7.taskflow.domain.model.Project;
import com.team7.taskflow.ui.auth.LoginActivity;
import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.ui.project.CreateTaskActivity;
import com.team7.taskflow.utils.SessionManager;

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

                            Intent createIntent = new Intent(ShareToTaskActivity.this, CreateTaskActivity.class);
                            createIntent.putExtra("project_id", result.get(0).getId());
                            createIntent.putExtra("prefill_title", finalPrefillTitle);
                            createIntent.putExtra("prefill_description", finalPrefillDescription);
                            startActivity(createIntent);
                            finish();
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
}
