package com.team7.taskflow.ui.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.ProjectRepository;
import com.team7.taskflow.domain.model.Project;
import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

public class ProjectTrashActivity extends BaseActivity {

    private ImageView btnBack;
    private RecyclerView rvDeletedProjects;
    private View emptyState;
    private TextView tvTrashCount;

    private final List<Project> deletedProjects = new ArrayList<>();
    private ProjectTrashAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_trash);

        SessionManager.init(this);

        initViews();
        setupRecycler();
        setupListeners();
        loadDeletedProjects();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        rvDeletedProjects = findViewById(R.id.rvDeletedProjects);
        emptyState = findViewById(R.id.emptyState);
        tvTrashCount = findViewById(R.id.tvTrashCount);
    }

    private void setupRecycler() {
        adapter = new ProjectTrashAdapter(deletedProjects, this::confirmRestore);
        rvDeletedProjects.setLayoutManager(new LinearLayoutManager(this));
        rvDeletedProjects.setAdapter(adapter);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
    }

    private void loadDeletedProjects() {
        String ownerId = SessionManager.getUserId();
        if (ownerId == null || ownerId.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.error_unknown), Toast.LENGTH_SHORT).show();
            return;
        }

        ProjectRepository.getInstance().getDeletedOwnedProjects(ownerId, new ProjectRepository.ProjectCallback<List<Project>>() {
            @Override
            public void onSuccess(List<Project> result) {
                runOnUiThread(() -> {
                    deletedProjects.clear();
                    if (result != null) {
                        deletedProjects.addAll(result);
                    }
                    adapter.notifyDataSetChanged();
                    updateState();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ProjectTrashActivity.this, error, Toast.LENGTH_SHORT).show();
                    updateState();
                });
            }
        });
    }

    private void updateState() {
        tvTrashCount.setText(getString(R.string.project_trash_count_format, deletedProjects.size()));
        boolean hasData = !deletedProjects.isEmpty();
        rvDeletedProjects.setVisibility(hasData ? View.VISIBLE : View.GONE);
        emptyState.setVisibility(hasData ? View.GONE : View.VISIBLE);
    }

    private void confirmRestore(Project project) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.project_restore_confirm_title))
                .setMessage(getString(R.string.project_restore_confirm_message, safeProjectName(project)))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.project_restore_action), (dialog, which) -> doRestore(project))
                .show();
    }

    private void doRestore(Project project) {
        ProjectRepository.getInstance().restoreProject(project.getId(), new ProjectRepository.ProjectCallback<Project>() {
            @Override
            public void onSuccess(Project result) {
                runOnUiThread(() -> {
                    deletedProjects.remove(project);
                    adapter.notifyDataSetChanged();
                    updateState();
                    Toast.makeText(ProjectTrashActivity.this, getString(R.string.project_restore_success), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(ProjectTrashActivity.this, error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private String safeProjectName(Project project) {
        if (project == null || project.getName() == null || project.getName().trim().isEmpty()) {
            return getString(R.string.project_unnamed);
        }
        return project.getName();
    }
}
