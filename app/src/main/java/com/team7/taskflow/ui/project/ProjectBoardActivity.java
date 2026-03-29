package com.team7.taskflow.ui.project;

import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.ui.base.BaseActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ProjectBoardActivity extends BaseActivity {

    private TaskRepository taskRepository;
    private long projectId;

    private TextView tvProjectName, tvCountTodo, tvCountDoing, tvCountDone;
    private ImageView btnBack;
    private RecyclerView rvTodo, rvDoing, rvDone;
    private TaskAdapter adapterTodo, adapterDoing, adapterDone;
    private View fabAddTask;

    private ActivityResultLauncher<Intent> taskLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_project_board);

        taskRepository = TaskRepository.getInstance();
        projectId = getIntent().getLongExtra("project_id", -1);
        String projectName = getIntent().getStringExtra("project_name");

        initViews();

        if (tvProjectName != null) {
            tvProjectName.setText(projectName != null ? projectName : "Project Board");
        }

        taskLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> loadTaskCounts()
        );

        setupListeners();
        setupBoards();

        loadTaskCounts();
    }

    private void setupBoards() {
        // 1. Khởi tạo 3 Adapter cho 3 cột
        adapterTodo = new TaskAdapter();
        adapterDoing = new TaskAdapter();
        adapterDone = new TaskAdapter();

        // 2. Thiết lập RecyclerView cho cột To Do
        rvTodo.setLayoutManager(new LinearLayoutManager(this));
        rvTodo.setAdapter(adapterTodo);

        // 3. Thiết lập RecyclerView cho cột Doing
        rvDoing.setLayoutManager(new LinearLayoutManager(this));
        rvDoing.setAdapter(adapterDoing);

        // 4. Thiết lập RecyclerView cho cột Done
        rvDone.setLayoutManager(new LinearLayoutManager(this));
        rvDone.setAdapter(adapterDone);

        // ── ĐÂY LÀ KHÚC BẠN ĐANG THIẾU ──
        // Tạo Listener chung cho cả 3 bảng
        TaskAdapter.OnTaskClickListener listener = new TaskAdapter.OnTaskClickListener() {
            @Override
            public void onTaskClick(Task task) {
                // Khi nhấn vào cả cái thẻ Task -> Cũng mở trang Edit
                Intent intent = new Intent(ProjectBoardActivity.this, CreateTaskActivity.class);
                intent.putExtra("project_id", projectId);
                intent.putExtra("task_id", task.getId());
                taskLauncher.launch(intent);
            }

            @Override
            public void onTaskMenuClick(Task task, View view) {
                // KHI NHẤN VÀO NÚT 3 CHẤM -> Gọi hàm hiện Popup Menu bạn đã viết
                showTaskMenu(task, view);
            }
        };

        // Gán listener vào từng adapter
        adapterTodo.setOnTaskClickListener(listener);
        adapterDoing.setOnTaskClickListener(listener);
        adapterDone.setOnTaskClickListener(listener);

        // 5. THIẾT LẬP KÉO THẢ (Vuốt để chuyển status)
        setupDragAndDrop();
        // Ngăn HorizontalScrollView chặn sự kiện vuốt của RecyclerView
        View.OnTouchListener interceptTouchListener = (v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        };

        rvTodo.setOnTouchListener(interceptTouchListener);
        rvDoing.setOnTouchListener(interceptTouchListener);
        rvDone.setOnTouchListener(interceptTouchListener);
    }

    private void setupDragAndDrop() {
        // Hỗ trợ vuốt cả TRÁI và PHẢI
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                return false; // Không dùng kéo lên xuống
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                TaskAdapter currentAdapter = (TaskAdapter) viewHolder.getBindingAdapter();
                if (currentAdapter == null) return;

                Task task = currentAdapter.getTasks().get(position);
                String currentStatus = task.getStatus().toUpperCase();
                String nextStatus = currentStatus;

                if (direction == ItemTouchHelper.RIGHT) {
                    // Vuốt PHẢI: Tiến tới
                    if (currentStatus.equals("TODO")) nextStatus = "DOING";
                    else if (currentStatus.equals("DOING")) nextStatus = "DONE";
                } else if (direction == ItemTouchHelper.LEFT) {
                    // Vuốt TRÁI: Quay lui
                    if (currentStatus.equals("DONE")) nextStatus = "DOING";
                    else if (currentStatus.equals("DOING")) nextStatus = "TODO";
                }

                if (!nextStatus.equals(currentStatus)) {
                    // Nếu có sự thay đổi trạng thái -> Gọi API cập nhật
                    updateTaskStatusOnServer(task, nextStatus);
                } else {
                    // Nếu không thể chuyển thêm (VD: Done mà vẫn vuốt phải) -> Trả icon về chỗ cũ
                    currentAdapter.notifyItemChanged(position);
                }
            }
        };

        // Gán vào cả 3 cột
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(rvTodo);
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(rvDoing);
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(rvDone);
    }
    private void initViews() {
        tvProjectName = findViewById(R.id.tvProjectName);
        tvCountTodo = findViewById(R.id.tvCountTodo);
        tvCountDoing = findViewById(R.id.tvCountDoing);
        tvCountDone = findViewById(R.id.tvCountDone);
        btnBack = findViewById(R.id.btnBack);
        fabAddTask = findViewById(R.id.fabAddTask);

        rvTodo = findViewById(R.id.rvTodo);
        rvDoing = findViewById(R.id.rvDoing);
        rvDone = findViewById(R.id.rvDone);
    }

    private void setupListeners() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (fabAddTask != null) {
            fabAddTask.setOnClickListener(v -> {
                if (projectId == -1) {
                    Toast.makeText(this, "Project not found", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(this, CreateTaskActivity.class);
                intent.putExtra("project_id", projectId);
                taskLauncher.launch(intent);
            });
        }
    }

    private void loadTaskCounts() {
        if (projectId == -1) return;

        taskRepository.getTasksByProject(projectId, new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> result) {
                List<Task> todoList = new ArrayList<>();
                List<Task> doingList = new ArrayList<>();
                List<Task> doneList = new ArrayList<>();

                for (Task t : result) {
                    if (t.getStatus() == null) continue;
                    String status = t.getStatus().toUpperCase();
                    switch (status) {
                        case "TODO": todoList.add(t); break;
                        case "DOING": doingList.add(t); break;
                        case "DONE": doneList.add(t); break;
                    }
                }

                runOnUiThread(() -> {
                    if (tvCountTodo != null) tvCountTodo.setText(String.valueOf(todoList.size()));
                    if (tvCountDoing != null) tvCountDoing.setText(String.valueOf(doingList.size()));
                    if (tvCountDone != null) tvCountDone.setText(String.valueOf(doneList.size()));

                    adapterTodo.setTasks(todoList);
                    adapterDoing.setTasks(doingList);
                    adapterDone.setTasks(doneList);
                });
            }

            @Override
            public void onError(String error) {
                Log.e("ProjectBoard", "Load failed: " + error);
            }
        });
    }
    private void updateTaskStatusOnServer(Task task, String newStatus) {
        // Hiển thị loading nhẹ hoặc Toast
        taskRepository.updateTaskStatus(task.getId(), task.getStatus(), newStatus, new TaskRepository.TaskCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    Toast.makeText(ProjectBoardActivity.this, "Moved to " + newStatus, Toast.LENGTH_SHORT).show();
                    // Tải lại toàn bộ để các con số và danh sách được đồng bộ
                    loadTaskCounts();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(ProjectBoardActivity.this, "Failed: " + error, Toast.LENGTH_SHORT).show();
                    loadTaskCounts(); // Tải lại để đưa Task về vị trí cũ nếu lỗi
                });
            }
        });
    }
    private void showTaskMenu(Task task, View view) {
        PopupMenu popup = new PopupMenu(this, view);
        // Thêm các lựa chọn vào menu
        popup.getMenu().add(0, 1, 0, "Edit Task");
        popup.getMenu().add(0, 2, 1, "Delete Task");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: // Edit Task
                    Intent intent = new Intent(ProjectBoardActivity.this, CreateTaskActivity.class);
                    intent.putExtra("project_id", projectId);
                    intent.putExtra("task_id", task.getId()); // Truyền ID để trang Create biết là đang Edit
                    taskLauncher.launch(intent);
                    return true;
                case 2: // Delete Task
                    deleteTask(task);
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    // Hàm bổ trợ xóa task
    private void deleteTask(Task task) {
        taskRepository.deleteTask(task.getId(), new TaskRepository.TaskCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    Toast.makeText(ProjectBoardActivity.this, "Task deleted", Toast.LENGTH_SHORT).show();
                    loadTaskCounts(); // Load lại danh sách
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(ProjectBoardActivity.this, error, Toast.LENGTH_SHORT).show());
            }
        });
    }
}