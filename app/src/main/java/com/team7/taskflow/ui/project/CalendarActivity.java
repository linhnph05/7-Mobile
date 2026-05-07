package com.team7.taskflow.ui.project;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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
import com.team7.taskflow.ui.common.AvatarUiUtils;
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
        androidx.activity.EdgeToEdge.enable(this);
        setContentView(R.layout.activity_calendar);

        View rootLayout = findViewById(R.id.rootLayout);
        if (rootLayout != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }

        // Xử lý insets cho bottom bar: thêm padding bottom cho navigation bar
        View bottomBarContainer = findViewById(R.id.includeBottomBar);
        if (bottomBarContainer != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(bottomBarContainer, (v, insets) -> {
                androidx.core.graphics.Insets sys = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), sys.bottom);
                return insets;
            });
        }

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
                android.content.Intent intent = new android.content.Intent(this, com.team7.taskflow.ui.timeline.ProjectDetailActivity.class);
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
        glCalendar.removeAllViews();
        addWeekdayHeaders();

        Calendar cal = (Calendar) currentCalendar.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int emptySlots = cal.get(Calendar.DAY_OF_WEEK) - 1;

        // 2. Thêm các ô trống
        for (int i = 0; i < emptySlots; i++) {
            TextView space = new TextView(this);
            space.setText(" ");
            space.setMinHeight(dp(36));
            space.setGravity(android.view.Gravity.CENTER);
            androidx.gridlayout.widget.GridLayout.LayoutParams params = new androidx.gridlayout.widget.GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = androidx.gridlayout.widget.GridLayout.spec(androidx.gridlayout.widget.GridLayout.UNDEFINED, 1f);
            glCalendar.addView(space, params);
        }

        // 3. Thêm các ô ngày
        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int day = 1; day <= daysInMonth; day++) {
            TextView tv = new TextView(this);
            tv.setText(String.valueOf(day));
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setMinHeight(dp(36));
            tv.setPadding(0, dp(6), 0, dp(6));

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
                tv.setTextColor(ContextCompat.getColor(this, R.color.theme_text_primary));
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

    private void addWeekdayHeaders() {
        String[] days = {
                getString(R.string.calendar_weekday_sun),
                getString(R.string.calendar_weekday_mon),
                getString(R.string.calendar_weekday_tue),
                getString(R.string.calendar_weekday_wed),
                getString(R.string.calendar_weekday_thu),
                getString(R.string.calendar_weekday_fri),
                getString(R.string.calendar_weekday_sat)
        };
        for (String day : days) {
            TextView tv = new TextView(this);
            tv.setText(day);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setTextSize(11f);
            tv.setTextColor(ContextCompat.getColor(this, R.color.theme_text_secondary));
            tv.setMinHeight(dp(24));

            androidx.gridlayout.widget.GridLayout.LayoutParams params = new androidx.gridlayout.widget.GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = androidx.gridlayout.widget.GridLayout.spec(androidx.gridlayout.widget.GridLayout.UNDEFINED, 1f);
            glCalendar.addView(tv, params);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void setupRecyclerView() {
        adapter = new TaskAdapter();
        rvTasks.setLayoutManager(new LinearLayoutManager(this));
        rvTasks.setAdapter(adapter);

        // Gắn listener nút 3 chấm cho từng task trong calendar
        adapter.setOnTaskClickListener(new TaskAdapter.OnTaskClickListener() {
            @Override
            public void onTaskClick(Task task) {
                // Nhấn vào thẻ task: mở màn hình chi tiết/chỉnh sửa
                Intent intent = new Intent(CalendarActivity.this, com.team7.taskflow.ui.project.TaskDetailActivity.class);
                intent.putExtra("project_id", task.getProjectId());
                intent.putExtra("task_id", task.getId());
                taskLauncher.launch(intent);
            }

            @Override
            public void onTaskMenuClick(Task task, View view) {
                moveTaskToTrash(task);
            }
        });
    }

    private void loadTasks() {
        TaskRepository.TaskCallback<List<Task>> callback = new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> result) {
                allProjectTasks = result;
                if (adapter != null) {
                    adapter.setSubtaskProgressSource(allProjectTasks != null ? allProjectTasks : new ArrayList<>());
                }
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
                            if (imgUserAvatar != null) {
                                AvatarUiUtils.bindAvatarOrFallback(
                                        imgUserAvatar,
                                        null,
                                        user.getAvatarUrl(),
                                        user.getDisplayNameOrEmail());
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
            if ("TRASH".equalsIgnoreCase(t.getStatus())) continue;
            String due = null;
            if (t.getDueDate() != null && !t.getDueDate().isEmpty()) {
                due = t.getDueDate().length() >= 10
                        ? t.getDueDate().substring(0, 10)
                        : t.getDueDate();
            }

            if (due != null && !due.isEmpty() && selectedDateStr.equals(due)) {
                filteredList.add(t);
            }
        }

        filteredList.sort((left, right) -> Long.compare(parseTaskCreatedTime(right), parseTaskCreatedTime(left)));

        runOnUiThread(() -> {
            if (adapter != null) {
                adapter.setTasks(filteredList);
            }
        });
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

    private void moveTaskToTrash(Task task) {
        taskRepository.softDeleteTask(task.getId(), new TaskRepository.TaskCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                runOnUiThread(() -> {
                    Toast.makeText(CalendarActivity.this, "Đã chuyển task vào thùng rác", Toast.LENGTH_SHORT).show();
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