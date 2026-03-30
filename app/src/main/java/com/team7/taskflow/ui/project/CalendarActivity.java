package com.team7.taskflow.ui.project;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.gridlayout.widget.GridLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.utils.SessionManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CalendarActivity extends BaseActivity {
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private String selectedDateStr;
    private List<Task> allProjectTasks = new ArrayList<>();
    private GridLayout glCalendar;
    private Calendar currentCalendar = Calendar.getInstance();

    private RecyclerView rvTasks;
    private TaskAdapter adapter;
    private TaskRepository taskRepository;
    private long projectId;
    private boolean isMyTasksMode = false;
    private String currentUserId;
    private ImageView imgUserAvatar;
    private ActivityResultLauncher<Intent> taskLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        // 0. Register launcher để reload sau khi Edit Task xong
        taskLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        loadTasks(); // Tải lại task để phản ánh thay đổi
                    }
                });

        // 1. Khởi tạo Repository
        taskRepository = TaskRepository.getInstance();
        projectId = getIntent().getLongExtra("project_id", -1);
        isMyTasksMode = getIntent().getBooleanExtra("is_my_tasks", false);
        
        com.team7.taskflow.utils.SessionManager.init(this);
        currentUserId = com.team7.taskflow.utils.SessionManager.getUserId();

        // 2. Ánh xạ các View chính
        glCalendar = findViewById(R.id.glCalendar);
        rvTasks = findViewById(R.id.rvTasks);
        imgUserAvatar = findViewById(R.id.imgUserAvatar);
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            if (isMyTasksMode) {
                btnBack.setVisibility(View.INVISIBLE);
            } else {
                btnBack.setOnClickListener(v -> finish());
            }
        }
        
        loadUserInfo();

        // 3. Thiết lập RecyclerView
        if (rvTasks != null) {
            setupRecyclerView();
            loadTasks();
        }

        // 4. Khởi tạo giao diện lịch ban đầu
        if (glCalendar != null) {
            updateUI(); // Hàm này sẽ lo việc hiện "October 2023" và các con số
        }

        setupTabs();

        // 6. Nút Today (Về ngày hiện tại)
        View btnToday = findViewById(R.id.btnToday);
        if (btnToday != null) {
            btnToday.setOnClickListener(v -> {
                currentCalendar = Calendar.getInstance();
                selectedDateStr = dateFormat.format(currentCalendar.getTime()); // Reset về ngày hôm nay
                updateUI();
                filterTasksBySelectedDate();
            });
        }

        // 7. Nút chuyển tháng
        View btnPrev = findViewById(R.id.btnPrevMonth);
        if (btnPrev != null) {
            btnPrev.setOnClickListener(v -> {
                currentCalendar.add(Calendar.MONTH, -1);
                updateUI();
            });
        }

        View btnNext = findViewById(R.id.btnNextMonth);
        if (btnNext != null) {
            btnNext.setOnClickListener(v -> {
                currentCalendar.add(Calendar.MONTH, 1);
                updateUI();
            });
        }

        // 8. Nút thêm task
        View btnNewTask = findViewById(R.id.btnNewTask);
        if (btnNewTask != null) {
            if (isMyTasksMode) {
                btnNewTask.setVisibility(View.GONE);
            } else {
                btnNewTask.setOnClickListener(v -> {
                    if (projectId == -1) {
                        Toast.makeText(this, "Select a project first", Toast.LENGTH_SHORT).show();
                    } else {
                        android.content.Intent intent = new android.content.Intent(this, com.team7.taskflow.ui.ai.AiCreateActivity.class);
                        intent.putExtra("project_id", projectId);
                        startActivity(intent);
                    }
                });
            }
        }
        selectedDateStr = dateFormat.format(Calendar.getInstance().getTime());

        taskRepository = TaskRepository.getInstance();
        projectId = getIntent().getLongExtra("project_id", -1);
        updateUI();
    }

    private void setupTabs() {
        TextView tabTimeline = findViewById(R.id.tabTimeline);
        TextView tabBoard = findViewById(R.id.tabBoard);
        
        if (tabTimeline != null) {
            tabTimeline.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(this, com.team7.taskflow.ui.timeline.TimelineActivity.class);
                intent.putExtra("project_id", projectId);
                intent.putExtra("project_name", getIntent().getStringExtra("project_name"));
                intent.putExtra("is_my_tasks", isMyTasksMode);
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                overridePendingTransition(0, 0);
                finish();
            });
        }
        
        if (tabBoard != null) {
            tabBoard.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(this, com.team7.taskflow.ui.project.ProjectBoardActivity.class);
                intent.putExtra("project_id", projectId);
                intent.putExtra("project_name", getIntent().getStringExtra("project_name"));
                intent.putExtra("is_my_tasks", isMyTasksMode);
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                overridePendingTransition(0, 0);
                finish();
            });
        }
    }

    private void updateUI() {
        // Cập nhật tiêu đề Tháng Năm (ở Top App Bar)
        TextView tvMonth = findViewById(R.id.tvMonth);
        if (tvMonth != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault());
            tvMonth.setText(sdf.format(currentCalendar.getTime()));
        }

        // Cập nhật tiêu đề Tháng Năm (ở Month Navigator bên trong nội dung Calendar)
        TextView tvMonthYear = findViewById(R.id.tvMonthYear);
        if (tvMonthYear != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault());
            tvMonthYear.setText(sdf.format(currentCalendar.getTime()));
        }
        
        TextView tvProjectName = findViewById(R.id.tvProjectName);
        if (tvProjectName != null) {
            if (isMyTasksMode) {
                tvProjectName.setText("My Assigned Tasks");
            } else {
                String pName = getIntent().getStringExtra("project_name");
                tvProjectName.setText(pName != null ? pName : "Calendar");
            }
        }

        // Vẽ lại các con số ngày
        if (glCalendar != null) {
            renderCalendar();
        }
    }


    private void renderCalendar() {
        // 1. Xóa các con số cũ (nhưng giữ lại 7 cái tiêu đề thứ)
        int childCount = glCalendar.getChildCount();
        if (childCount > 7) {
            glCalendar.removeViews(7, childCount - 7);
        }

        Calendar cal = (Calendar) currentCalendar.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int emptySlots = cal.get(Calendar.DAY_OF_WEEK) - 1;

        // 2. Thêm các ô trống
        for (int i = 0; i < emptySlots; i++) {
            View space = new View(this);
            glCalendar.addView(space, new androidx.gridlayout.widget.GridLayout.LayoutParams(
                    androidx.gridlayout.widget.GridLayout.spec(androidx.gridlayout.widget.GridLayout.UNDEFINED, 1f),
                    androidx.gridlayout.widget.GridLayout.spec(androidx.gridlayout.widget.GridLayout.UNDEFINED, 1f)
            ));
        }

        // 3. Thêm các ô ngày
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int day = 1; day <= daysInMonth; day++) {
            TextView tv = new TextView(this);
            tv.setText(String.valueOf(day));
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setPadding(0, 20, 0, 20);

            // Tính toán ngày thực tế của ô này
            Calendar cellCal = (Calendar) cal.clone();
            cellCal.set(Calendar.DAY_OF_MONTH, day);
            String cellDateStr = dateFormat.format(cellCal.getTime());

            // KIỂM TRA NẾU LÀ NGÀY ĐANG ĐƯỢC CHỌN -> TÔ MÀU XANH
            if (cellDateStr.equals(selectedDateStr)) {
                tv.setTextColor(android.graphics.Color.WHITE);
                tv.setTypeface(null, android.graphics.Typeface.BOLD);
                tv.setBackgroundResource(R.drawable.bg_tag); // Dùng bg bo góc của bạn
                tv.getBackground().setTint(ContextCompat.getColor(this, R.color.primary));
            } else {
                tv.setTextColor(ContextCompat.getColor(this, R.color.slate_700));
                tv.setBackground(null);
            }

            // SỰ KIỆN CLICK VÀO NGÀY
            tv.setOnClickListener(v -> {
                selectedDateStr = cellDateStr; // Cập nhật ngày đã chọn
                renderCalendar();            // Vẽ lại lịch để cập nhật màu highlight
                filterTasksBySelectedDate(); // Lọc task hiển thị ở dưới
            });

            androidx.gridlayout.widget.GridLayout.LayoutParams params = new androidx.gridlayout.widget.GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = androidx.gridlayout.widget.GridLayout.spec(androidx.gridlayout.widget.GridLayout.UNDEFINED, 1f);
            glCalendar.addView(tv, params);
        }
    }

    private void setupRecyclerView() {
        adapter = new TaskAdapter();
        rvTasks.setLayoutManager(new LinearLayoutManager(this));
        rvTasks.setAdapter(adapter);

        // Gắn listener nút 3 chấm cho từng task trong calendar
        adapter.setOnTaskClickListener(new TaskAdapter.OnTaskClickListener() {
            @Override
            public void onTaskClick(Task task) {
                // Nhấn vào thẻ task: không làm gì
            }

            @Override
            public void onTaskMenuClick(Task task, View view) {
                showTaskMenu(task, view);
            }
        });
    }

    private void loadTasks() {
        TaskRepository.TaskCallback<List<Task>> callback = new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> result) {
                allProjectTasks = result;
                filterTasksBySelectedDate();
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(CalendarActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show());
            }
        };

        if (isMyTasksMode) {
            taskRepository.getMyTasksWithProjectName(currentUserId, callback);
        } else if (projectId != -1) {
            taskRepository.getTasksByProject(projectId, callback);
        }
    }

    private void loadUserInfo() {
        if (currentUserId == null || currentUserId.isEmpty()) return;
        
        com.team7.taskflow.data.remote.SupabaseClient.getInstance()
            .getService(com.team7.taskflow.data.remote.api.UserApi.class)
            .getUserById("eq." + currentUserId, "*")
            .enqueue(new retrofit2.Callback<List<com.team7.taskflow.domain.model.User>>() {
                @Override
                public void onResponse(@androidx.annotation.NonNull retrofit2.Call<List<com.team7.taskflow.domain.model.User>> call, @androidx.annotation.NonNull retrofit2.Response<List<com.team7.taskflow.domain.model.User>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        com.team7.taskflow.domain.model.User user = response.body().get(0);
                        runOnUiThread(() -> {
                            if (imgUserAvatar != null && user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                                com.bumptech.glide.Glide.with(CalendarActivity.this)
                                    .load(user.getAvatarUrl())
                                    .circleCrop()
                                    .placeholder(R.drawable.bg_avatar_bordered)
                                    .error(R.drawable.bg_avatar_bordered)
                                    .into(imgUserAvatar);
                            }
                        });
                    }
                }
                @Override
                public void onFailure(@androidx.annotation.NonNull retrofit2.Call<List<com.team7.taskflow.domain.model.User>> call, @androidx.annotation.NonNull Throwable t) {
                    Log.e("Calendar", "Load user failed: " + t.getMessage());
                }
            });
    }

    private void filterTasksBySelectedDate() {
        if (selectedDateStr == null) return;

        List<Task> filteredList = new ArrayList<>();

        for (Task t : allProjectTasks) {
            String start = t.getStartDate() != null ? t.getStartDate().substring(0, 10) : null;
            String due = t.getDueDate();

            if (start != null && !start.isEmpty() && due != null && !due.isEmpty()) {
                boolean isAfterOrEqualStart = selectedDateStr.compareTo(start) >= 0;
                boolean isBeforeOrEqualDue = selectedDateStr.compareTo(due) <= 0;

                if (isAfterOrEqualStart && isBeforeOrEqualDue) {
                    filteredList.add(t);
                }
            }
            else if (due != null && !due.isEmpty()) {
                if (selectedDateStr.equals(due)) {
                    filteredList.add(t);
                }
            }
            else if (start != null && !start.isEmpty()) {
                if (selectedDateStr.equals(start)) {
                    filteredList.add(t);
                }
            }
        }

        runOnUiThread(() -> {
            if (adapter != null) {
                adapter.setTasks(filteredList);
            }
        });
    }

    private void showQuickAddSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View view = getLayoutInflater().inflate(R.layout.layout_add_task_bottom_sheet, null);
        dialog.setContentView(view);

        EditText etTitle = view.findViewById(R.id.etTaskTitle);
        Button btnCreate = view.findViewById(R.id.btnCreateTask);

        btnCreate.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            if (title.isEmpty()) {
                etTitle.setError("Title is required");
                return;
            }

            Task task = new Task(projectId, title);
            task.setAssigneeId(SessionManager.getUserId());

            taskRepository.createTask(task, new TaskRepository.TaskCallback<Task>() {
                @Override
                public void onSuccess(Task result) {
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        loadTasks();
                        Toast.makeText(CalendarActivity.this, "Task created!", Toast.LENGTH_SHORT).show();
                    });
                }
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> Toast.makeText(CalendarActivity.this, "Failed: " + error, Toast.LENGTH_SHORT).show());
                }
            });
        });
        dialog.show();
    }

    private void showTaskMenu(Task task, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "Edit Task");
        popup.getMenu().add(0, 2, 1, "Delete Task");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: // Edit
                    Intent intent = new Intent(CalendarActivity.this, CreateTaskActivity.class);
                    intent.putExtra("project_id", task.getProjectId());
                    intent.putExtra("task_id", task.getId());
                    taskLauncher.launch(intent);
                    return true;
                case 2: // Delete
                    deleteTask(task);
                    return true;
                default:
                    return false;
            }
        });
        popup.show();
    }

    private void deleteTask(Task task) {
        taskRepository.deleteTask(task.getId(), new TaskRepository.TaskCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    Toast.makeText(CalendarActivity.this, "Task deleted", Toast.LENGTH_SHORT).show();
                    loadTasks();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(CalendarActivity.this, error, Toast.LENGTH_SHORT).show());
            }
        });
    }
}