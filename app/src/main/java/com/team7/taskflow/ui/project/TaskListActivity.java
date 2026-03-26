package com.team7.taskflow.ui.project;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.ui.dashboard.DashboardActivity;
import com.team7.taskflow.ui.profile.ProfileActivity;
import com.team7.taskflow.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

public class TaskListActivity extends BaseActivity {

    private RecyclerView rvTasks;
    private TaskAdapter adapter;
    private TaskRepository taskRepository;
    private SwipeRefreshLayout swipeRefresh;
    
    private TextView tabToDo, tabDoing, tabDone;
    private String currentStatus = "TODO";
    private List<Task> allLoadedTasks = new ArrayList<>();
    
    private long projectId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_task_list);

        projectId = getIntent().getLongExtra("project_id", -1);
        taskRepository = TaskRepository.getInstance();

        initViews();
        setupRecyclerView();
        setupTabs();
        setupBottomNavigation();

        findViewById(R.id.fabAdd).setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateTaskActivity.class);
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
                // Future: showEditTaskSheet(task);
            }

            @Override
            public void onTaskMenuClick(Task task, View view) {
                showTaskMenu(task, view);
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
            bottomNav.setSelectedItemId(R.id.nav_tasks);
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    startActivity(new Intent(this, DashboardActivity.class));
                    finish();
                    return true;
                } else if (id == R.id.nav_settings) {
                    startActivity(new Intent(this, ProfileActivity.class));
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
                    allLoadedTasks = result;
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
        adapter.setTasks(filtered);
    }

    private void showTaskMenu(Task task, View view) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenu().add("Move to Trash");
        popup.setOnMenuItemClickListener(item -> {
            taskRepository.softDeleteTask(task.getId(), new TaskRepository.TaskCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    runOnUiThread(() -> loadTasks());
                }
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> Toast.makeText(TaskListActivity.this, error, Toast.LENGTH_SHORT).show());
                }
            });
            return true;
        });
        popup.show();
    }
}
