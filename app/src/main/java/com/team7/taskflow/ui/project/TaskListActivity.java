package com.team7.taskflow.ui.project;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.team7.taskflow.R;
import com.team7.taskflow.ui.dashboard.DashboardActivity;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.ui.profile.ProfileActivity;
import com.team7.taskflow.utils.NavigationUtils;
import com.team7.taskflow.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

public class TaskListActivity extends BaseActivity {

    private RecyclerView rvTasks;
    private TaskAdapter adapter;
    private TaskRepository taskRepository;
    private SwipeRefreshLayout swipeRefresh;
    private boolean isBottomNavNavigating = false;
    
    private TextView tabToDo, tabDoing, tabDone;
    private String currentStatus = "TODO";
    private List<Task> allLoadedTasks = new ArrayList<>();
    
    private long projectId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_task_list);

        // Xử lý insets cho bottom bar: thêm padding bottom cho navigation bar
        View bottomBarContainer = findViewById(R.id.includeBottomBar);
        if (bottomBarContainer != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(bottomBarContainer, (v, insets) -> {
                androidx.core.graphics.Insets sys = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), sys.bottom);
                return insets;
            });
        }

        projectId = getIntent().getLongExtra("project_id", -1);
        taskRepository = TaskRepository.getInstance();

        initViews();
        setupRecyclerView();
        setupTabs();
        setupBottomNavigation();

        findViewById(R.id.fabAdd).setOnClickListener(v -> {
            Intent intent = new Intent(this, TaskDetailActivity.class);
            intent.putExtra("project_id", projectId != -1 ? projectId : 1L);
            startActivity(intent);
        });
        
        swipeRefresh.setOnRefreshListener(this::loadTasks);
        loadTasks();
    }

    private void initViews() {
        rvTasks = findViewById(R.id.rvTasks);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        tabToDo = findViewById(R.id.tabToDo);
        tabDoing = findViewById(R.id.tabDoing);
        tabDone = findViewById(R.id.tabDone);
    }

    private void setupRecyclerView() {
        adapter = new TaskAdapter();
        rvTasks.setLayoutManager(new LinearLayoutManager(this));
        rvTasks.setAdapter(adapter);

        adapter.setOnTaskClickListener(new TaskAdapter.OnTaskClickListener() {
            @Override
            public void onTaskClick(Task task) {
                // Mở màn hình chi tiết/chỉnh sửa khi nhấn vào task
                Intent intent = new Intent(TaskListActivity.this, TaskDetailActivity.class);
                intent.putExtra("project_id", task.getProjectId());
                intent.putExtra("task_id", task.getId());
                startActivity(intent);
            }

            @Override
            public void onTaskMenuClick(Task task, View view) {
                moveTaskToTrash(task);
            }
        });
    }

    private void setupTabs() {
        View.OnClickListener tabListener = v -> {
            if (v.getId() == R.id.tabToDo) currentStatus = "TODO";
            else if (v.getId() == R.id.tabDoing) currentStatus = "DOING";
            else if (v.getId() == R.id.tabDone) currentStatus = "DONE";
            
            updateTabUI();
            filterTasks();
        };

        tabToDo.setOnClickListener(tabListener);
        tabDoing.setOnClickListener(tabListener);
        tabDone.setOnClickListener(tabListener);
    }

    private void updateTabUI() {
        tabToDo.setTextColor(currentStatus.equals("TODO") ? 
                ContextCompat.getColor(this, R.color.primary) : ContextCompat.getColor(this, R.color.slate_400));
        tabDoing.setTextColor(currentStatus.equals("DOING") ? 
                ContextCompat.getColor(this, R.color.primary) : ContextCompat.getColor(this, R.color.slate_400));
        tabDone.setTextColor(currentStatus.equals("DONE") ? 
                ContextCompat.getColor(this, R.color.primary) : ContextCompat.getColor(this, R.color.slate_400));
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigationView);
        if (bottomNav != null) {
            bottomNav.setItemIconTintList(null);
            bottomNav.getMenu().findItem(R.id.nav_tasks).setChecked(true);
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (isBottomNavNavigating) {
                    return true;
                }
                if (id == R.id.nav_home) {
                    isBottomNavNavigating = true;
                    Intent intent = new Intent(this, DashboardActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    NavigationUtils.startActivityWithNavAnimation(this, intent,
                            NavigationUtils.NAV_TASKS, NavigationUtils.NAV_HOME);
                    finish();
                    return true;
                } else if (id == R.id.nav_settings) {
                    isBottomNavNavigating = true;
                    Intent intent = new Intent(this, ProfileActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    NavigationUtils.startActivityWithNavAnimation(this, intent,
                            NavigationUtils.NAV_TASKS, NavigationUtils.NAV_SETTINGS);
                    finish();
                    return true;
                }
                return id == R.id.nav_tasks;
            });
        }
    }

    private void loadTasks() {
        swipeRefresh.setRefreshing(true);
        TaskRepository.TaskCallback<List<Task>> callback = new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> result) {
                runOnUiThread(() -> {
                    allLoadedTasks = result != null ? result : new ArrayList<>();
                    adapter.setSubtaskProgressSource(allLoadedTasks);
                    filterTasks();
                    swipeRefresh.setRefreshing(false);
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(TaskListActivity.this, error, Toast.LENGTH_SHORT).show();
                });
            }
        };

        if (projectId != -1) {
            taskRepository.getTasksByProject(projectId, callback);
        } else {
            taskRepository.getMyTasks(SessionManager.getUserId(), callback);
        }
    }

    private void filterTasks() {
        List<Task> filtered = new ArrayList<>();
        for (Task t : allLoadedTasks) {
            if (t.getStatus() != null && t.getStatus().equals(currentStatus)) {
                filtered.add(t);
            }
        }
        filtered.sort((left, right) -> Long.compare(parseTaskCreatedTime(right), parseTaskCreatedTime(left)));
        adapter.setTasks(filtered);
    }

    private long parseTaskCreatedTime(Task task) {
        if (task == null || task.getCreatedAt() == null || task.getCreatedAt().trim().isEmpty()) {
            return 0L;
        }
        try {
            return java.time.OffsetDateTime.parse(task.getCreatedAt()).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void moveTaskToTrash(Task task) {
        taskRepository.softDeleteTask(task.getId(), new TaskRepository.TaskCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    Toast.makeText(TaskListActivity.this, "Đã chuyển task vào thùng rác", Toast.LENGTH_SHORT).show();
                    loadTasks();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(TaskListActivity.this, error, Toast.LENGTH_SHORT).show());
            }
        });
    }
}
