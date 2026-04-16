package com.team7.taskflow.ui.project;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

public class TrashActivity extends BaseActivity {

    private static final String TAG = "TrashActivity";

    private ImageView btnBack;
    private TextView btnEmptyTrash;
    private TextView tvTotalItems;
    private TextView tvAutoCleanup;
    private RecyclerView rvTrashItems;
    private LinearLayout emptyState;
    private TrashItemAdapter adapter;
    private List<Task> trashedTasks = new ArrayList<>();
    private long projectId = -1L;
    private boolean isMyTasksMode = true;
    private String projectName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_trash);

        // Handle window insets
        View scrollView = findViewById(R.id.rvTrashItems);
        ViewCompat.setOnApplyWindowInsetsListener(scrollView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        SessionManager.init(this);
        projectId = getIntent().getLongExtra("project_id", -1L);
        isMyTasksMode = getIntent().getBooleanExtra("is_my_tasks", false);
        projectName = getIntent().getStringExtra("project_name");

        initViews();
        setupRecyclerView();
        setupListeners();
        loadTrashedTasks();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnEmptyTrash = findViewById(R.id.btnEmptyTrash);
        tvTotalItems = findViewById(R.id.tvTotalItems);
        tvAutoCleanup = findViewById(R.id.tvAutoCleanup);
        rvTrashItems = findViewById(R.id.rvTrashItems);
        emptyState = findViewById(R.id.emptyState);
    }

    private void setupRecyclerView() {
        adapter = new TrashItemAdapter(trashedTasks, task -> {
            // Restore action
            restoreTask(task);
        }, task -> {
            // Delete permanently action with confirmation
            confirmDeleteTask(task);
        });
        rvTrashItems.setLayoutManager(new LinearLayoutManager(this));
        rvTrashItems.setAdapter(adapter);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnEmptyTrash.setOnClickListener(v -> emptyAllTrash());
    }

    private void loadTrashedTasks() {
        String userId = SessionManager.getUserId();
        if (userId == null || userId.isEmpty()) {
            Log.e(TAG, "No userId");
            return;
        }

        TaskRepository.TaskCallback<List<Task>> callback = new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> result) {
                trashedTasks.clear();
                if (result != null) {
                    if (projectId > 0 && projectName != null && !projectName.trim().isEmpty()) {
                        for (Task task : result) {
                            if (task.getProjectInfo() == null) {
                                com.team7.taskflow.domain.model.Project project = new com.team7.taskflow.domain.model.Project();
                                project.setName(projectName);
                                task.setProjectInfo(project);
                            }
                        }
                    }
                    trashedTasks.addAll(result);
                }
                adapter.notifyDataSetChanged();
                updateUI();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error loading trashed tasks: " + error);
                updateUI();
            }
        };

        if (projectId > 0 && !isMyTasksMode) {
            TaskRepository.getInstance().getTasksByProjectAndStatus(projectId, "TRASH", callback);
        } else {
            TaskRepository.getInstance().getMyTasksWithProjectNameByStatus(userId, "TRASH", callback);
        }
    }

    private void updateUI() {
        tvTotalItems.setText(String.valueOf(trashedTasks.size()));
        if (tvAutoCleanup != null) {
            tvAutoCleanup.setText(getString(R.string.trash_auto_cleanup_demo));
        }

        if (trashedTasks.isEmpty()) {
            rvTrashItems.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            rvTrashItems.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
        }
    }

    private void restoreTask(Task task) {
        if (task == null || task.getId() == 0) return;

        TaskRepository.getInstance().restoreTask(task.getId(), new TaskRepository.TaskCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Log.d(TAG, "Task restored: " + task.getId());
                Toast.makeText(TrashActivity.this, getString(R.string.trash_restore_success), Toast.LENGTH_SHORT).show();
                trashedTasks.remove(task);
                adapter.notifyDataSetChanged();
                updateUI();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error restoring task: " + error);
            }
        });
    }

    private void deleteTaskPermanently(Task task) {
        if (task == null || task.getId() == 0) return;

        TaskRepository.getInstance().permanentlyDeleteTask(task.getId(), new TaskRepository.TaskCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Log.d(TAG, "Task deleted permanently: " + task.getId());
                Toast.makeText(TrashActivity.this, getString(R.string.trash_delete_success), Toast.LENGTH_SHORT).show();
                trashedTasks.remove(task);
                adapter.notifyDataSetChanged();
                updateUI();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error deleting task: " + error);
            }
        });
    }

    private void confirmDeleteTask(Task task) {
        if (task == null || task.getId() == 0) return;

        String title = task.getTitle() != null && !task.getTitle().trim().isEmpty()
                ? task.getTitle().trim()
                : getString(R.string.trash_untitled_task);

        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TaskFlow_MaterialAlertDialog)
                .setTitle(R.string.trash_delete_confirm_title)
                .setMessage(getString(R.string.trash_delete_confirm_message_format, title))
                .setPositiveButton(R.string.trash_delete_confirm_action, (dialog, which) -> {
                    deleteTaskPermanently(task);
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void emptyAllTrash() {
        // Show confirmation dialog
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_TaskFlow_MaterialAlertDialog)
                .setTitle(R.string.trash_empty_confirm_title)
                .setMessage(R.string.trash_empty_confirm_message)
                .setPositiveButton(R.string.trash_empty_confirm_action, (dialog, which) -> {
                    performEmptyTrash();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    dialog.dismiss();
                })
                .show();
    }

    private void performEmptyTrash() {
        List<Task> toDelete = new ArrayList<>(trashedTasks);
        int[] deletedCount = {0};

        for (Task task : toDelete) {
            TaskRepository.getInstance().permanentlyDeleteTask(task.getId(), new TaskRepository.TaskCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    deletedCount[0]++;
                    if (deletedCount[0] == toDelete.size()) {
                        trashedTasks.clear();
                        adapter.notifyDataSetChanged();
                        updateUI();
                        Toast.makeText(TrashActivity.this, getString(R.string.trash_empty_success), Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "All trash emptied");
                    }
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Error emptying trash: " + error);
                }
            });
        }
    }
}