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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import android.view.Gravity;
import android.view.ViewGroup;

public class TimelineActivity extends BaseActivity {

    private LinearLayout containerTaskLabels;
    private LinearLayout containerGanttBars;
    private LinearLayout containerGanttMonths;
    private LinearLayout containerGanttDays;
    private LinearLayout containerGanttGrid;
    private HorizontalScrollView ganttScrollView;
    private TaskRepository taskRepository;
    private long projectId;
    private final int COLUMN_WIDTH_DP = 40; // Dense column width

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_timeline);

        // 1. Khởi tạo
        taskRepository = TaskRepository.getInstance();
        projectId = getIntent().getLongExtra("project_id", -1);
        String projectName = getIntent().getStringExtra("project_name");

        initViews();

        if (projectName != null) {
            TextView tvProjectName = findViewById(R.id.tvProjectName);
            if (tvProjectName != null) tvProjectName.setText(projectName);
            
            TextView tvMonth = findViewById(R.id.tvMonth);
            if (tvMonth != null) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault());
                tvMonth.setText(sdf.format(java.util.Calendar.getInstance().getTime()));
            }
        }

        setupWindowInsets();
        setupClickListeners();

        // 2. Load dữ liệu ngay lập tức
        if (projectId != -1) {
            loadTimelineData();
        }
    }

    private void initViews() {
        containerTaskLabels = findViewById(R.id.containerTaskLabels);
        containerGanttBars = findViewById(R.id.containerGanttBars);
        containerGanttMonths = findViewById(R.id.containerGanttMonths);
        containerGanttDays = findViewById(R.id.containerGanttDays);
        containerGanttGrid = findViewById(R.id.containerGanttGrid);
        ganttScrollView = findViewById(R.id.ganttScrollView);
    }

    private void loadTimelineData() {
        taskRepository.getTasksByProject(projectId, new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> tasks) {
                runOnUiThread(() -> renderTasks(tasks));
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(TimelineActivity.this, "Lỗi: " + error, Toast.LENGTH_SHORT).show());
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

        // Find date range
        for (Task t : tasks) {
            try {
                if (t.getStartDate() != null && t.getStartDate().length() >= 10) {
                    Date d = sdf.parse(t.getStartDate().substring(0, 10));
                    if (earliestTaskDate == null || d.before(earliestTaskDate.getTime())) { earliestTaskDate = Calendar.getInstance(); earliestTaskDate.setTime(d); }
                    if (latestTaskDate == null || d.after(latestTaskDate.getTime())) { latestTaskDate = Calendar.getInstance(); latestTaskDate.setTime(d); }
                }
                if (t.getDueDate() != null && t.getDueDate().length() >= 10) {
                    Date d = sdf.parse(t.getDueDate().substring(0, 10));
                    if (earliestTaskDate == null || d.before(earliestTaskDate.getTime())) { earliestTaskDate = Calendar.getInstance(); earliestTaskDate.setTime(d); }
                    if (latestTaskDate == null || d.after(latestTaskDate.getTime())) { latestTaskDate = Calendar.getInstance(); latestTaskDate.setTime(d); }
                }
            } catch (Exception ignored) {}
        }
        
        if (earliestTaskDate == null) {
            // Không có task nào có ngày
            minCal.setTime(today.getTime());
            maxCal.setTime(today.getTime());
        } else {
            // Nếu có task trước ngày hiện tại thì lấy ngày task đó, nếu không thì lấy ngày hiện tại
            if (earliestTaskDate.before(today)) {
                minCal.setTime(earliestTaskDate.getTime());
            } else {
                minCal.setTime(today.getTime());
            }
            
            // Tương tự cho maxCal
            if (latestTaskDate.after(today)) {
                maxCal.setTime(latestTaskDate.getTime());
            } else {
                maxCal.setTime(today.getTime());
            }
        }
        
        minCal.add(Calendar.DAY_OF_YEAR, -2); // padding trước 2 ngày
        maxCal.add(Calendar.DAY_OF_YEAR, 10); // padding sau 10 ngày
        
        // Zero out time
        minCal.set(Calendar.HOUR_OF_DAY, 0); minCal.set(Calendar.MINUTE, 0); minCal.set(Calendar.SECOND, 0); minCal.set(Calendar.MILLISECOND, 0);
        maxCal.set(Calendar.HOUR_OF_DAY, 0); maxCal.set(Calendar.MINUTE, 0); maxCal.set(Calendar.SECOND, 0); maxCal.set(Calendar.MILLISECOND, 0);
        
        long minTime = minCal.getTimeInMillis();
        int totalDays = Math.max(1, (int) ((maxCal.getTimeInMillis() - minTime + 12L*3600*1000) / (24L*3600*1000)) + 1);

        // Render Headers & Grid
        Calendar iterCal = (Calendar) minCal.clone();
        String currentMonth = "";
        int currentMonthDays = 0;
        
        for (int i = 0; i < totalDays; i++) {
            // Day header
            TextView tvDay = new TextView(this);
            tvDay.setText(String.valueOf(iterCal.get(Calendar.DAY_OF_MONTH)));
            tvDay.setLayoutParams(new LinearLayout.LayoutParams((int)(COLUMN_WIDTH_DP * density), ViewGroup.LayoutParams.WRAP_CONTENT));
            tvDay.setGravity(Gravity.CENTER);
            tvDay.setTextSize(12f);
            tvDay.setTextColor(ContextCompat.getColor(this, R.color.slate_500));
            if (containerGanttDays != null) containerGanttDays.addView(tvDay);
            
            // Grid line
            View gridLine = new View(this);
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT);
            glp.setMargins((int)(COLUMN_WIDTH_DP * density) - 1, 0, 0, 0);
            gridLine.setLayoutParams(glp);
            gridLine.setBackgroundColor(ContextCompat.getColor(this, R.color.slate_200));
            if (containerGanttGrid != null) containerGanttGrid.addView(gridLine);
            
            // Month grouping
            SimpleDateFormat monthFormat = new SimpleDateFormat("MMM", Locale.US);
            String mName = monthFormat.format(iterCal.getTime());
            if (currentMonth.equals(mName)) {
                currentMonthDays++;
            } else {
                if (currentMonthDays > 0) {
                    TextView tvMonth = new TextView(this);
                    tvMonth.setText(currentMonth);
                    tvMonth.setLayoutParams(new LinearLayout.LayoutParams((int)(currentMonthDays * COLUMN_WIDTH_DP * density), ViewGroup.LayoutParams.WRAP_CONTENT));
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
        
        // Add final month
        if (currentMonthDays > 0 && containerGanttMonths != null) {
            TextView tvMonth = new TextView(this);
            tvMonth.setText(currentMonth);
            tvMonth.setLayoutParams(new LinearLayout.LayoutParams((int)(currentMonthDays * COLUMN_WIDTH_DP * density), ViewGroup.LayoutParams.WRAP_CONTENT));
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
            tvName.setText(task.getTitle());
            containerTaskLabels.addView(labelView);
            
            View barView = getLayoutInflater().inflate(R.layout.item_timeline_bar, containerGanttBars, false);
            View taskBar = barView.findViewById(R.id.taskBar);
            TextView tvBarLabel = barView.findViewById(R.id.tvBarLabel);
            tvBarLabel.setText(task.getStatus() + " - " + task.getTitle());
            
            // Calc offset & width
            long startT = minTime; 
            long dueT = minTime;
            try {
                if (task.getStartDate() != null && task.getStartDate().length() >= 10) {
                    Date d = sdf.parse(task.getStartDate().substring(0, 10));
                    startT = d.getTime();
                }
                if (task.getDueDate() != null && task.getDueDate().length() >= 10) {
                    Date d = sdf.parse(task.getDueDate().substring(0, 10));
                    dueT = d.getTime();
                } else {
                    dueT = startT; 
                }
                if (startT > dueT) dueT = startT; // fallback
            } catch (Exception ignored) {}
            
            int offsetDays = Math.max(0, (int) ((startT - minTime + 12L*3600*1000) / (24L*3600*1000)));
            int durationDays = Math.max(1, (int) ((dueT - startT + 12L*3600*1000) / (24L*3600*1000)) + 1);
            
            int marginStart = (int)(offsetDays * COLUMN_WIDTH_DP * density);
            int barWidth = (int)(durationDays * COLUMN_WIDTH_DP * density);
            
            if (marginStart < minMarginStart && marginStart > 0) minMarginStart = marginStart;
            
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) taskBar.getLayoutParams();
            lp.setMarginStart(marginStart);
            lp.width = barWidth;
            taskBar.setLayoutParams(lp);

            if ("HIGH".equals(task.getPriority())) {
                taskBar.setBackgroundColor(ContextCompat.getColor(this, R.color.red_500));
            } else if ("TODO".equals(task.getStatus())) {
                taskBar.setBackgroundColor(ContextCompat.getColor(this, R.color.slate_400));
            }
            
            containerGanttBars.addView(barView);
        }
        
        if (minMarginStart != Integer.MAX_VALUE && minMarginStart > 0 && ganttScrollView != null) {
            final int finalScroll = Math.max(0, minMarginStart - (int)(1 * COLUMN_WIDTH_DP * density)); 
            ganttScrollView.post(() -> ganttScrollView.scrollTo(finalScroll, 0));
        }
    }

    private void setupClickListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnMoreOptions).setOnClickListener(v -> showProjectSettingsPanel());

        View fabAddAI = findViewById(R.id.fabAddAI);
        if (fabAddAI != null) {
            fabAddAI.setOnClickListener(v -> {
                Intent intent = new Intent(TimelineActivity.this, com.team7.taskflow.ui.ai.AiCreateActivity.class);
                intent.putExtra("project_id", projectId);
                startActivity(intent);
            });
        }

        setupTabs();
    }

    private void showProjectSettingsPanel() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View sheetView = getLayoutInflater().inflate(R.layout.layout_project_settings_panel, null);
        bottomSheet.setContentView(sheetView);

        // SỬA LỖI R: Sử dụng ID trực tiếp
        View bottomSheetLayout = bottomSheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheetLayout != null) {
            com.google.android.material.bottomsheet.BottomSheetBehavior behavior =
                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheetLayout);
            behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        }

        bottomSheet.show();
    }

    // Các hàm setupWindowInsets, setupTabs, TodayLineView giữ nguyên như cũ...
    private void setupWindowInsets() {
        View rootLayout = findViewById(R.id.rootLayout);
        View bottomBar = findViewById(R.id.bottomBar);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, systemBars.top, 0, 0);
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });
    }

    private void setupTabs() {
        TextView tabBoard = findViewById(R.id.tabBoard);
        TextView tabCalendar = findViewById(R.id.tabCalendar);
        
        if (tabBoard != null) {
            tabBoard.setOnClickListener(v -> {
                Intent intent = new Intent(this, ProjectBoardActivity.class);
                intent.putExtra("project_id", projectId);
                intent.putExtra("project_name", getIntent().getStringExtra("project_name"));
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
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                overridePendingTransition(0, 0);
                finish();
            });
        }
    }

    public static class TodayLineView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public TodayLineView(Context ctx) { super(ctx); init(); }
        public TodayLineView(Context ctx, AttributeSet attrs) { super(ctx, attrs); init(); }
        private void init() {
            paint.setColor(0xFF136DEC);
            paint.setStrokeWidth(2f * getResources().getDisplayMetrics().density);
            paint.setStyle(Paint.Style.STROKE);
            float dash = 8f * getResources().getDisplayMetrics().density;
            paint.setPathEffect(new DashPathEffect(new float[] { dash, dash }, 0));
        }
        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            canvas.drawLine(cx, 0, cx, getHeight(), paint);
        }
    }
}