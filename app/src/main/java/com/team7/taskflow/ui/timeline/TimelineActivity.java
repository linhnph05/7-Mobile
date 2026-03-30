package com.team7.taskflow.ui.timeline;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.ui.project.CalendarActivity;
import com.team7.taskflow.ui.project.ProjectBoardActivity;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Gravity;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class TimelineActivity extends BaseActivity {

    private LinearLayout containerTaskLabels;
    private LinearLayout containerGanttBars;
    private LinearLayout containerGanttMonths;
    private LinearLayout containerGanttDays;
    private LinearLayout containerGanttGrid;
    private HorizontalScrollView ganttScrollView;
    private TaskRepository taskRepository;
    private long projectId;
    private boolean isMyTasksMode = false;
    private String currentUserId;
    private ImageView imgUserAvatar;
    private final Map<String, String> assigneeAvatarUrlMap = new HashMap<>();
    private final int COLUMN_WIDTH_DP = 40; 

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_timeline);

        // Xử lý viền màn hình (WindowInsets)
        View rootLayout = findViewById(R.id.rootLayout);
        View bottomBar = findViewById(R.id.bottomBar);

        if (rootLayout != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(0, systemBars.top, 0, 0);
                return insets;
            });
        }

        if (bottomBar != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomBar, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                        v.getPaddingRight(), systemBars.bottom);
                return insets;
            });
        }

        // Khởi tạo dữ liệu
        taskRepository = TaskRepository.getInstance();
        projectId = getIntent().getLongExtra("project_id", -1);
        String projectName = getIntent().getStringExtra("project_name");
        isMyTasksMode = getIntent().getBooleanExtra("is_my_tasks", false);

        com.team7.taskflow.utils.SessionManager.init(this);
        currentUserId = com.team7.taskflow.utils.SessionManager.getUserId();

        initViews();

        if (isMyTasksMode) {
            TextView tvProjectName = findViewById(R.id.tvProjectName);
            if (tvProjectName != null) tvProjectName.setText("My Assigned Tasks");

            View btnBack = findViewById(R.id.btnBack);
            if (btnBack != null) btnBack.setVisibility(View.INVISIBLE);
            
            View layoutRightIcons = findViewById(R.id.layoutRightIcons);
            if (layoutRightIcons != null) layoutRightIcons.setVisibility(View.INVISIBLE);
            
            View fabAddAI = findViewById(R.id.fabAddAI);
            if (fabAddAI != null) fabAddAI.setVisibility(View.GONE);
        } else if (projectName != null) {
            TextView tvProjectName = findViewById(R.id.tvProjectName);
            if (tvProjectName != null) tvProjectName.setText(projectName);
            
            TextView tvMonth = findViewById(R.id.tvMonth);
            if (tvMonth != null) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault());
                tvMonth.setText(sdf.format(java.util.Calendar.getInstance().getTime()));
            }
        }

        setupClickListeners();

        // Load dữ liệu
        loadTimelineData();
    }

    private void initViews() {
        containerTaskLabels = findViewById(R.id.containerTaskLabels);
        containerGanttBars = findViewById(R.id.containerGanttBars);
        containerGanttMonths = findViewById(R.id.containerGanttMonths);
        containerGanttDays = findViewById(R.id.containerGanttDays);
        containerGanttGrid = findViewById(R.id.containerGanttGrid);
        ganttScrollView = findViewById(R.id.ganttScrollView);
        imgUserAvatar = findViewById(R.id.imgUserAvatar);
        
        loadUserInfo();
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
                                com.bumptech.glide.Glide.with(TimelineActivity.this)
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
                    Log.e("Timeline", "Load user failed: " + t.getMessage());
                }
            });
    }

    private void loadTimelineData() {
        TaskRepository.TaskCallback<List<Task>> callback = new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> tasks) {
                fetchAssigneeAvatars(tasks, () -> runOnUiThread(() -> renderTasks(tasks)));
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(TimelineActivity.this, "Lỗi: " + error, Toast.LENGTH_SHORT).show());
            }
        };

        if (isMyTasksMode) {
            taskRepository.getMyTasksWithProjectName(currentUserId, callback);
        } else if (projectId != -1) {
            taskRepository.getTasksByProject(projectId, callback);
        }
    }

    private void fetchAssigneeAvatars(List<Task> tasks, Runnable onDone) {
        assigneeAvatarUrlMap.clear();
        if (tasks == null || tasks.isEmpty()) {
            onDone.run();
            return;
        }

        Set<String> assigneeIds = new HashSet<>();
        for (Task task : tasks) {
            if (task == null) continue;
            String assigneeId = task.getAssigneeId();
            if (assigneeId != null && !assigneeId.trim().isEmpty()) {
                assigneeIds.add(assigneeId);
            }
        }

        if (assigneeIds.isEmpty()) {
            onDone.run();
            return;
        }

        String idsFilter = "in.(" + android.text.TextUtils.join(",", assigneeIds) + ")";

        com.team7.taskflow.data.remote.SupabaseClient.getInstance()
                .getService(com.team7.taskflow.data.remote.api.UserApi.class)
                .getUsersByIds(idsFilter, "user_id,avatar_url")
                .enqueue(new retrofit2.Callback<List<com.team7.taskflow.domain.model.User>>() {
                    @Override
                    public void onResponse(@androidx.annotation.NonNull retrofit2.Call<List<com.team7.taskflow.domain.model.User>> call,
                                           @androidx.annotation.NonNull retrofit2.Response<List<com.team7.taskflow.domain.model.User>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            for (com.team7.taskflow.domain.model.User user : response.body()) {
                                if (user.getUserId() != null && user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
                                    assigneeAvatarUrlMap.put(user.getUserId(), user.getAvatarUrl());
                                }
                            }
                        }
                        onDone.run();
                    }

                    @Override
                    public void onFailure(@androidx.annotation.NonNull retrofit2.Call<List<com.team7.taskflow.domain.model.User>> call,
                                          @androidx.annotation.NonNull Throwable t) {
                        Log.e("Timeline", "Load assignee avatars failed: " + t.getMessage());
                        onDone.run();
                    }
                });
    }

    private void renderTasks(List<Task> tasks) {
        if (containerTaskLabels == null || containerGanttBars == null) return;

        containerTaskLabels.removeAllViews();
        containerGanttBars.removeAllViews();
        if (containerGanttMonths != null) containerGanttMonths.removeAllViews();
        if (containerGanttDays != null) containerGanttDays.removeAllViews();
        if (containerGanttGrid != null) containerGanttGrid.removeAllViews();

        float density = getResources().getDisplayMetrics().density;
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar minCal = Calendar.getInstance();
        Calendar maxCal = Calendar.getInstance();

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0); today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0);

        Calendar earliestTaskDate = null;
        Calendar latestTaskDate = null;

        if (tasks == null) {
            tasks = java.util.Collections.emptyList();
        }

        // Find date range from task start/due dates.
        for (Task t : tasks) {
            try {
                if (t.getStartDate() != null && t.getStartDate().length() >= 10) {
                    Date d = sdf.parse(t.getStartDate().substring(0, 10));
                    if (d != null) {
                        if (earliestTaskDate == null || d.before(earliestTaskDate.getTime())) {
                            earliestTaskDate = Calendar.getInstance();
                            earliestTaskDate.setTime(d);
                        }
                        if (latestTaskDate == null || d.after(latestTaskDate.getTime())) {
                            latestTaskDate = Calendar.getInstance();
                            latestTaskDate.setTime(d);
                        }
                    }
                }
                if (t.getDueDate() != null && t.getDueDate().length() >= 10) {
                    Date d = sdf.parse(t.getDueDate().substring(0, 10));
                    if (d != null) {
                        if (earliestTaskDate == null || d.before(earliestTaskDate.getTime())) {
                            earliestTaskDate = Calendar.getInstance();
                            earliestTaskDate.setTime(d);
                        }
                        if (latestTaskDate == null || d.after(latestTaskDate.getTime())) {
                            latestTaskDate = Calendar.getInstance();
                            latestTaskDate.setTime(d);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (earliestTaskDate == null) {
            minCal.setTime(today.getTime());
            maxCal.setTime(today.getTime());
        } else {
            if (earliestTaskDate.before(today)) {
                minCal.setTime(earliestTaskDate.getTime());
            } else {
                minCal.setTime(today.getTime());
            }

            if (latestTaskDate != null && latestTaskDate.after(today)) {
                maxCal.setTime(latestTaskDate.getTime());
            } else {
                maxCal.setTime(today.getTime());
            }
        }

        minCal.add(Calendar.DAY_OF_YEAR, -2);
        maxCal.add(Calendar.DAY_OF_YEAR, 10);

        minCal.set(Calendar.HOUR_OF_DAY, 0);
        minCal.set(Calendar.MINUTE, 0);
        minCal.set(Calendar.SECOND, 0);
        minCal.set(Calendar.MILLISECOND, 0);
        maxCal.set(Calendar.HOUR_OF_DAY, 0);
        maxCal.set(Calendar.MINUTE, 0);
        maxCal.set(Calendar.SECOND, 0);
        maxCal.set(Calendar.MILLISECOND, 0);

        long minTime = minCal.getTimeInMillis();
        int totalDays = Math.max(1,
                (int) ((maxCal.getTimeInMillis() - minTime + 12L * 3600 * 1000) / (24L * 3600 * 1000)) + 1);

        // Render day headers, month headers and vertical grid lines.
        Calendar iterCal = (Calendar) minCal.clone();
        String currentMonth = "";
        int currentMonthDays = 0;

        for (int i = 0; i < totalDays; i++) {
            TextView tvDay = new TextView(this);
            tvDay.setText(String.valueOf(iterCal.get(Calendar.DAY_OF_MONTH)));
            tvDay.setLayoutParams(new LinearLayout.LayoutParams((int) (COLUMN_WIDTH_DP * density), ViewGroup.LayoutParams.WRAP_CONTENT));
            tvDay.setGravity(Gravity.CENTER);
            tvDay.setTextSize(12f);
            tvDay.setTextColor(ContextCompat.getColor(this, R.color.slate_500));
            if (containerGanttDays != null) containerGanttDays.addView(tvDay);

            View gridLine = new View(this);
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT);
            glp.setMargins((int) (COLUMN_WIDTH_DP * density) - 1, 0, 0, 0);
            gridLine.setLayoutParams(glp);
            gridLine.setBackgroundColor(ContextCompat.getColor(this, R.color.slate_200));
            if (containerGanttGrid != null) containerGanttGrid.addView(gridLine);

            String mName = new SimpleDateFormat("MMM", Locale.US).format(iterCal.getTime());
            if (currentMonth.equals(mName)) {
                currentMonthDays++;
            } else {
                if (currentMonthDays > 0) {
                    TextView tvMonth = new TextView(this);
                    tvMonth.setText(currentMonth);
                    tvMonth.setLayoutParams(new LinearLayout.LayoutParams((int) (currentMonthDays * COLUMN_WIDTH_DP * density), ViewGroup.LayoutParams.WRAP_CONTENT));
                    tvMonth.setPadding(32, 0, 0, 0);
                    tvMonth.setTextSize(13f);
                    tvMonth.setTypeface(null, android.graphics.Typeface.BOLD);
                    tvMonth.setTextColor(ContextCompat.getColor(this, R.color.slate_900));
                    if (containerGanttMonths != null) containerGanttMonths.addView(tvMonth);
                }
                currentMonth = mName;
                currentMonthDays = 1;
            }

            iterCal.add(Calendar.DAY_OF_YEAR, 1);
        }

        if (currentMonthDays > 0 && containerGanttMonths != null) {
            TextView tvMonth = new TextView(this);
            tvMonth.setText(currentMonth);
            tvMonth.setLayoutParams(new LinearLayout.LayoutParams((int) (currentMonthDays * COLUMN_WIDTH_DP * density), ViewGroup.LayoutParams.WRAP_CONTENT));
            tvMonth.setPadding(32, 0, 0, 0);
            tvMonth.setTextSize(13f);
            tvMonth.setTypeface(null, android.graphics.Typeface.BOLD);
            tvMonth.setTextColor(ContextCompat.getColor(this, R.color.slate_900));
            containerGanttMonths.addView(tvMonth);
        }

        // Render Tasks
        int minMarginStart = Integer.MAX_VALUE;

        for (Task task : tasks) {
            View labelView = getLayoutInflater().inflate(R.layout.item_timeline_label, containerTaskLabels, false);
            TextView tvName = labelView.findViewById(R.id.tvTaskName);
            ImageView imgAssigneeAvatar = labelView.findViewById(R.id.imgAssigneeAvatar);
            
            String labelText = task.getTitle();
            if (isMyTasksMode) {
                String pName = task.getProjectName();
                if (pName != null) {
                    labelText = "[" + pName + "] " + labelText;
                }
            }
            tvName.setText(labelText);

            String assigneeId = task.getAssigneeId();
            String assigneeAvatarUrl = assigneeId != null ? assigneeAvatarUrlMap.get(assigneeId) : null;
            if (imgAssigneeAvatar != null) {
                if (assigneeAvatarUrl != null && !assigneeAvatarUrl.isEmpty()) {
                    imgAssigneeAvatar.setVisibility(View.VISIBLE);
                    com.bumptech.glide.Glide.with(TimelineActivity.this)
                            .load(assigneeAvatarUrl)
                            .circleCrop()
                            .placeholder(R.drawable.bg_avatar_bordered)
                            .error(R.drawable.bg_avatar_bordered)
                            .into(imgAssigneeAvatar);
                } else {
                    imgAssigneeAvatar.setVisibility(View.INVISIBLE);
                }
            }

            containerTaskLabels.addView(labelView);
            
            View barView = getLayoutInflater().inflate(R.layout.item_timeline_bar, containerGanttBars, false);
            View taskBar = barView.findViewById(R.id.taskBar);
            TextView tvBarLabel = barView.findViewById(R.id.tvBarLabel);
            
            String barLabelText = (task.getStatus() != null ? task.getStatus() : "TODO") + " - " + task.getTitle();
            if (isMyTasksMode) {
                String pName = task.getProjectName();
                if (pName != null) {
                    barLabelText = "[" + pName + "] " + barLabelText;
                }
            }
            tvBarLabel.setText(barLabelText);
            
            // Tính toán offset & độ dài của bar
            long startT = minTime; 
            long dueT = minTime;
            try {
                if (task.getStartDate() != null && task.getStartDate().length() >= 10) {
                    Date d = sdf.parse(task.getStartDate().substring(0, 10));
                    if (d != null) startT = d.getTime();
                }
                if (task.getDueDate() != null && task.getDueDate().length() >= 10) {
                    Date d = sdf.parse(task.getDueDate().substring(0, 10));
                    if (d != null) dueT = d.getTime();
                } else {
                    dueT = startT; 
                }
                if (startT > dueT) dueT = startT; 
            } catch (Exception ignored) {}
            
            int offsetDays = Math.max(0, (int) ((startT - minTime + 12L*3600*1000) / (24L*3600*1000)));
            int durationDays = Math.max(1, (int) ((dueT - startT + 12L*3600*1000) / (24L*3600*1000)) + 1);
            
            int marginStart = (int)(offsetDays * COLUMN_WIDTH_DP * density);
            int barWidth = (int)(durationDays * COLUMN_WIDTH_DP * density);
            
            if (marginStart < minMarginStart) minMarginStart = marginStart;
            
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) taskBar.getLayoutParams();
            lp.setMarginStart(marginStart);
            lp.width = barWidth;
            taskBar.setLayoutParams(lp);

            if ("HIGH".equals(task.getPriority())) {
                taskBar.setBackgroundResource(R.drawable.bg_timeline_bar_high);
            } else if ("DONE".equals(task.getStatus())) {
                taskBar.setBackgroundResource(R.drawable.bg_timeline_bar_done);
            } else {
                taskBar.setBackgroundResource(R.drawable.bg_timeline_bar_default);
            }
            
            containerGanttBars.addView(barView);
        }

        // Vị trí Today Line
        View viewTodayLine = findViewById(R.id.viewTodayLine);
        if (viewTodayLine != null) {
            long todayOffsetDays = (today.getTimeInMillis() - minTime + 12L*3600*1000) / (24L*3600*1000);
            int todayMarginStart = (int) (todayOffsetDays * COLUMN_WIDTH_DP * density);
            
            FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                (int)(2 * density), ViewGroup.LayoutParams.MATCH_PARENT);
            tlp.setMarginStart(todayMarginStart);
            viewTodayLine.setLayoutParams(tlp);
            viewTodayLine.setVisibility(View.VISIBLE);
            
            // Tự động cuộn đến task gần nhất hoặc ngày hiện tại
            int scrollTarget = todayMarginStart - (int)(1 * COLUMN_WIDTH_DP * density);
            if (minMarginStart != Integer.MAX_VALUE && minMarginStart < todayMarginStart) {
                scrollTarget = minMarginStart - (int)(1 * COLUMN_WIDTH_DP * density);
            }
            final int finalScroll = Math.max(0, scrollTarget);
            ganttScrollView.post(() -> ganttScrollView.scrollTo(finalScroll, 0));
        } else if (minMarginStart != Integer.MAX_VALUE && ganttScrollView != null) {
            final int finalScroll = Math.max(0, minMarginStart - (int)(1 * COLUMN_WIDTH_DP * density)); 
            ganttScrollView.post(() -> ganttScrollView.scrollTo(finalScroll, 0));
        }
    }

    private void setupClickListeners() {
        // Xử lý nút Back 
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null && !isMyTasksMode) {
            btnBack.setOnClickListener(v -> finish());
        }

        View btnMoreOptions = findViewById(R.id.btnMoreOptions);
        if (btnMoreOptions != null) {
            btnMoreOptions.setOnClickListener(v -> showProjectSettingsPanel());
        }

        // Mở màn hình tạo Task Assistant
        View fabAddAI = findViewById(R.id.fabAddAI);
        if (fabAddAI != null) {
            fabAddAI.setOnClickListener(v -> {
                Intent aiIntent = new Intent(this, com.team7.taskflow.ui.ai.AiCreateActivity.class);
                startActivity(aiIntent);
            });
        }

        // Tab switching
        TextView tabTimeline = findViewById(R.id.tabTimeline);
        TextView tabBoard    = findViewById(R.id.tabBoard);
        TextView tabCalendar = findViewById(R.id.tabCalendar);

        if (tabTimeline != null && tabBoard != null && tabCalendar != null) {
            View.OnClickListener tabClick = v -> {
                tabTimeline.setBackgroundResource(R.drawable.bg_tab_inactive);
                tabBoard.setBackgroundResource(R.drawable.bg_tab_inactive);
                tabCalendar.setBackgroundResource(R.drawable.bg_tab_inactive);
                tabTimeline.setTextColor(ContextCompat.getColor(this, R.color.slate_600));
                tabBoard.setTextColor(ContextCompat.getColor(this, R.color.slate_600));
                tabCalendar.setTextColor(ContextCompat.getColor(this, R.color.slate_600));
                v.setBackgroundResource(R.drawable.bg_tab_active);
                ((TextView) v).setTextColor(ContextCompat.getColor(this, R.color.white));
            };
            tabTimeline.setOnClickListener(tabClick);
            tabBoard.setOnClickListener(tabClick);
            tabCalendar.setOnClickListener(tabClick);
        }
        
        setupTabs(); 
    }

    private void showProjectSettingsPanel() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View sheetView = getLayoutInflater().inflate(R.layout.layout_project_settings_panel, null);
        bottomSheet.setContentView(sheetView);

        android.widget.FrameLayout bottomSheetLayout = bottomSheet
                .findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheetLayout != null) {
            com.google.android.material.bottomsheet.BottomSheetBehavior<android.widget.FrameLayout> behavior =
                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheetLayout);
            behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
        }

        long currentProjectId = getIntent().getLongExtra("project_id", -1);
        String currentProjectName = getIntent().getStringExtra("project_name");
        String currentProjectKey  = getIntent().getStringExtra("project_key");
        String currentProjectDesc = getIntent().getStringExtra("project_desc");

        android.widget.EditText etProjectName = sheetView.findViewById(R.id.etProjectName);
        android.widget.EditText etProjectDesc = sheetView.findViewById(R.id.etProjectDesc);
        TextView tvProjectKey = sheetView.findViewById(R.id.tvProjectKey);
        android.widget.ImageView btnSaveProject = sheetView.findViewById(R.id.btnSaveProject);

        if (etProjectName != null && currentProjectName != null)
            etProjectName.setText(currentProjectName);
        if (etProjectDesc != null && currentProjectDesc != null)
            etProjectDesc.setText(currentProjectDesc);
        if (tvProjectKey != null)
            tvProjectKey.setText(currentProjectKey != null ? "KEY: " + currentProjectKey : "N/A");

        if (btnSaveProject != null) {
            btnSaveProject.setOnClickListener(v -> {
                if (currentProjectId == -1) return;
                String newName = etProjectName.getText().toString().trim();
                String newDesc = etProjectDesc.getText().toString().trim();
                if (newName.isEmpty()) {
                    android.widget.Toast.makeText(this,
                            "Tên dự án không được bỏ trống!", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                com.team7.taskflow.domain.model.Project updateP =
                        new com.team7.taskflow.domain.model.Project();
                updateP.setName(newName);
                updateP.setDescription(newDesc);

                com.team7.taskflow.data.repository.ProjectRepository.getInstance().updateProject(
                        currentProjectId, updateP,
                        new com.team7.taskflow.data.repository.ProjectRepository.ProjectCallback<
                                com.team7.taskflow.domain.model.Project>() {
                    @Override
                    public void onSuccess(com.team7.taskflow.domain.model.Project result) {
                        runOnUiThread(() -> {
                            getIntent().putExtra("project_name", newName);
                            getIntent().putExtra("project_desc", newDesc);
                            android.widget.Toast.makeText(TimelineActivity.this,
                                    "Cập nhật dự án thành công!",
                                    android.widget.Toast.LENGTH_SHORT).show();
                            bottomSheet.dismiss();
                        });
                    }
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> android.widget.Toast.makeText(
                                TimelineActivity.this, error,
                                android.widget.Toast.LENGTH_SHORT).show());
                    }
                });
            });
        }

        // Các nút chức năng quản lý
        View btnManageMembers = sheetView.findViewById(R.id.btnManageMembers);
        if (btnManageMembers != null) {
            btnManageMembers.setOnClickListener(v -> {
                bottomSheet.dismiss();
                com.team7.taskflow.ui.member.MemberListBottomSheet sheet =
                        new com.team7.taskflow.ui.member.MemberListBottomSheet(currentProjectId);
                sheet.show(getSupportFragmentManager(), "members");
            });
        }

        View btnCollapse = sheetView.findViewById(R.id.btnCollapse);
        if (btnCollapse != null) {
            btnCollapse.setOnClickListener(v -> bottomSheet.dismiss());
        }

        View btnDeleteProject = sheetView.findViewById(R.id.btnDeleteProject);
        if (btnDeleteProject != null) {
            btnDeleteProject.setOnClickListener(v -> {
                bottomSheet.dismiss();
                android.widget.Toast.makeText(this,
                        "Delete project tapped", android.widget.Toast.LENGTH_SHORT).show();
            });
        }

        bottomSheet.show();
    }

    private void setupTabs() {
        TextView tabBoard = findViewById(R.id.tabBoard);
        TextView tabCalendar = findViewById(R.id.tabCalendar);
        
        if (tabBoard != null) {
            tabBoard.setOnClickListener(v -> {
                Intent intent = new Intent(this, ProjectBoardActivity.class);
                intent.putExtra("project_id", projectId);
                intent.putExtra("project_name", getIntent().getStringExtra("project_name"));
                intent.putExtra("is_my_tasks", isMyTasksMode);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                overridePendingTransition(0, 0);
                finish();
            });
        }
        
        if (tabCalendar != null) {
            tabCalendar.setOnClickListener(v -> {
                Intent intent = new Intent(this, CalendarActivity.class);
                intent.putExtra("project_id", projectId);
                intent.putExtra("project_name", getIntent().getStringExtra("project_name"));
                intent.putExtra("is_my_tasks", isMyTasksMode);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                overridePendingTransition(0, 0);
                finish();
            });
        }
    }

    // Custom view: vertical dashed "today" line 
    public static class TodayLineView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public TodayLineView(Context ctx) {
            super(ctx);
            init();
        }

        public TodayLineView(Context ctx, AttributeSet attrs) {
            super(ctx, attrs);
            init();
        }

        public TodayLineView(Context ctx, AttributeSet attrs, int defStyle) {
            super(ctx, attrs, defStyle);
            init();
        }

        private void init() {
            paint.setColor(0xFF136DEC);
            paint.setStrokeWidth(2f * getResources().getDisplayMetrics().density);
            paint.setStyle(Paint.Style.STROKE);
            float dash = 8f * getResources().getDisplayMetrics().density;
            paint.setPathEffect(new DashPathEffect(new float[]{dash, dash}, 0));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            canvas.drawLine(cx, 0, cx, getHeight(), paint);
        }
    }
}