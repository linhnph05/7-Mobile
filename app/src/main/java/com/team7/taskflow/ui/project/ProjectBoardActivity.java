package com.team7.taskflow.ui.project;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.ui.base.BaseActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ProjectBoardActivity extends BaseActivity {

    private RecyclerView rvTasks;
    private TaskAdapter adapter;
    private TaskRepository taskRepository;
    private long projectId;
    private String currentStatus = "TODO";

    private LinearLayout tabToDo, tabDoing, tabDone;
    private TextView tvProjectTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_project_board);

        projectId = getIntent().getLongExtra("project_id", -1);
        String projectName = getIntent().getStringExtra("project_name");

        taskRepository = TaskRepository.getInstance();

        initViews();
        tvProjectTitle.setText(projectName != null ? projectName : "Project Board");

        setupRecyclerView();
        setupTabs();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.fabAdd).setOnClickListener(v -> showAddTaskSheet());

        loadTasks();
    }

    private void initViews() {
        rvTasks = findViewById(R.id.projectRecyclerView); // In the layout, this is the ID inside ScrollView, but better replace ScrollView with RV
        tabToDo = findViewById(R.id.tabToDo);
        tabDoing = findViewById(R.id.tabDoing);
        tabDone = findViewById(R.id.tabDone);
        tvProjectTitle = findViewById(R.id.tvProjectTitle);
    }

    private void setupRecyclerView() {
        adapter = new TaskAdapter();
        rvTasks.setLayoutManager(new LinearLayoutManager(this));
        rvTasks.setAdapter(adapter);

        adapter.setOnTaskClickListener(new TaskAdapter.OnTaskClickListener() {
            @Override
            public void onTaskClick(Task task) {
                showEditTaskSheet(task);
            }

            @Override
            public void onTaskMenuClick(Task task, View view) {
                showTaskMenu(task, view);
            }
        });

        // Drag & Drop Implementation
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                Task task = adapter.getTasks().get(position);
                String nextStatus = currentStatus;

                if (direction == ItemTouchHelper.RIGHT) {
                    if (currentStatus.equals("TODO")) nextStatus = "DOING";
                    else if (currentStatus.equals("DOING")) nextStatus = "DONE";
                } else {
                    if (currentStatus.equals("DONE")) nextStatus = "DOING";
                    else if (currentStatus.equals("DOING")) nextStatus = "TODO";
                }

                if (!nextStatus.equals(currentStatus)) {
                    updateStatus(task, nextStatus);
                } else {
                    adapter.notifyItemChanged(position);
                }
            }
        });
        itemTouchHelper.attachToRecyclerView(rvTasks);
    }

    private void setupTabs() {
        View.OnClickListener tabClick = v -> {
            if (v.getId() == R.id.tabToDo) currentStatus = "TODO";
            else if (v.getId() == R.id.tabDoing) currentStatus = "DOING";
            else if (v.getId() == R.id.tabDone) currentStatus = "DONE";
            
            updateTabVisuals();
            loadTasks();
        };

        tabToDo.setOnClickListener(tabClick);
        tabDoing.setOnClickListener(tabClick);
        tabDone.setOnClickListener(tabClick);
        updateTabVisuals();
    }

    private void updateTabVisuals() {
        // Simple visual feedback for active tab
        tabToDo.setAlpha(currentStatus.equals("TODO") ? 1.0f : 0.5f);
        tabDoing.setAlpha(currentStatus.equals("DOING") ? 1.0f : 0.5f);
        tabDone.setAlpha(currentStatus.equals("DONE") ? 1.0f : 0.5f);
    }

    private void loadTasks() {
        taskRepository.getTasksByProject(projectId, new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> result) {
                List<Task> filtered = new ArrayList<>();
                for (Task t : result) {
                    if (t.getStatus().equals(currentStatus)) filtered.add(t);
                }
                runOnUiThread(() -> adapter.setTasks(filtered));
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(ProjectBoardActivity.this, error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showAddTaskSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View view = getLayoutInflater().inflate(R.layout.layout_add_task_bottom_sheet, null);
        dialog.setContentView(view);

        EditText etTitle = view.findViewById(R.id.etTaskTitle);
        EditText etDesc = view.findViewById(R.id.etTaskDescription);
        Button btnCreate = view.findViewById(R.id.btnCreateTask);

        btnCreate.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            if (title.isEmpty()) return;

            Task task = new Task(projectId, title);
            task.setDescription(etDesc.getText().toString().trim());
            task.setStatus(currentStatus);

            taskRepository.createTask(task, new TaskRepository.TaskCallback<Task>() {
                @Override
                public void onSuccess(Task result) {
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        loadTasks();
                    });
                }
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> Toast.makeText(ProjectBoardActivity.this, error, Toast.LENGTH_SHORT).show());
                }
            });
        });

        dialog.show();
    }

    private void showEditTaskSheet(Task task) {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View view = getLayoutInflater().inflate(R.layout.layout_add_task_bottom_sheet, null);
        dialog.setContentView(view);

        ((TextView)view.findViewById(android.R.id.title)).setText("Edit Task");
        EditText etTitle = view.findViewById(R.id.etTaskTitle);
        EditText etDesc = view.findViewById(R.id.etTaskDescription);
        Button btnSave = view.findViewById(R.id.btnCreateTask);
        btnSave.setText("Save Changes");

        etTitle.setText(task.getTitle());
        etDesc.setText(task.getDescription());

        btnSave.setOnClickListener(v -> {
            task.setTitle(etTitle.getText().toString().trim());
            task.setDescription(etDesc.getText().toString().trim());

            taskRepository.updateTask(task.getId(), task, new TaskRepository.TaskCallback<Task>() {
                @Override
                public void onSuccess(Task result) {
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        loadTasks();
                    });
                }
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> Toast.makeText(ProjectBoardActivity.this, error, Toast.LENGTH_SHORT).show());
                }
            });
        });

        dialog.show();
    }

    private void showTaskMenu(Task task, View view) {
        PopupMenu popup = new PopupMenu(this, view);
        popup.getMenu().add("Delete (Trash)");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getTitle().equals("Delete (Trash)")) {
                taskRepository.softDeleteTask(task.getId(), new TaskRepository.TaskCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        runOnUiThread(() -> loadTasks());
                    }
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> Toast.makeText(ProjectBoardActivity.this, error, Toast.LENGTH_SHORT).show());
                    }
                });
            }
            return true;
        });
        popup.show();
    }

    private void updateStatus(Task task, String nextStatus) {
        String oldStatus = task.getStatus();
        taskRepository.updateTaskStatus(task.getId(), oldStatus, nextStatus, new TaskRepository.TaskCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> loadTasks());
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ProjectBoardActivity.this, error, Toast.LENGTH_SHORT).show();
                    loadTasks();
                });
            }
        });
    }
}
