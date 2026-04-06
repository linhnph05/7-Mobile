package com.team7.taskflow.ui.project;

import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.StackedValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.MemberRepository;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.ProjectMember;
import com.team7.taskflow.domain.model.Task;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fragment hiển thị tổng quan tiến độ dự án với biểu đồ Pie và Bar.
 * Đã được nâng cấp giao diện theo phong cách Premium.
 */
public class ProjectOverviewFragment extends Fragment {

    private static final String ARG_PROJECT_ID = "project_id";
    private static final String ARG_IS_MY_TASKS = "is_my_tasks";
    private static final String ARG_USER_ID = "user_id";

    private long projectId;
    private boolean isMyTasksMode;
    private String currentUserId;
    private TaskRepository taskRepository;
    private MemberRepository memberRepository;
    private TaskAdapter taskAdapter;

    private NestedScrollView nestedScrollView;
    private TextView tvTotalTasks, tvDoneTasks, tvOverdueTasks;
    private PieChart pieChartStatus;
    private BarChart barChartProductivity;
    private RecyclerView rvUpcomingTasks;
    private LinearLayout layoutEmptyState, layoutCharts;
    private View cardBarChart;
    
    private Map<String, String> memberNames = new HashMap<>();
    private int todoCount;
    private int inProgressCount;
    private int doneCount;

    private static final String STATUS_TODO_KEY = "TODO";
    private static final String STATUS_IN_PROGRESS_KEY = "IN_PROGRESS";
    private static final String STATUS_DONE_KEY = "DONE";

    public static ProjectOverviewFragment newInstance(long projectId, boolean isMyTasksMode, String userId) {
        ProjectOverviewFragment fragment = new ProjectOverviewFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_PROJECT_ID, projectId);
        args.putBoolean(ARG_IS_MY_TASKS, isMyTasksMode);
        args.putString(ARG_USER_ID, userId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            projectId = getArguments().getLong(ARG_PROJECT_ID);
            isMyTasksMode = getArguments().getBoolean(ARG_IS_MY_TASKS, false);
            currentUserId = getArguments().getString(ARG_USER_ID);
        }
        taskRepository = TaskRepository.getInstance();
        memberRepository = new MemberRepository();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_project_overview, container, false);
        initViews(view);
        setupRecyclerView();
        loadProjectData();
        return view;
    }

    private void initViews(View view) {
        nestedScrollView = (NestedScrollView) view;
        tvTotalTasks = view.findViewById(R.id.tvTotalTasks);
        tvDoneTasks = view.findViewById(R.id.tvDoneTasks);
        tvOverdueTasks = view.findViewById(R.id.tvOverdueTasks);
        pieChartStatus = view.findViewById(R.id.pieChartStatus);
        barChartProductivity = view.findViewById(R.id.barChartProductivity);
        cardBarChart = view.findViewById(R.id.cardBarChart);
        rvUpcomingTasks = view.findViewById(R.id.rvUpcomingTasks);
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState);
        layoutCharts = view.findViewById(R.id.layoutCharts);

        if (isMyTasksMode && cardBarChart != null) {
            cardBarChart.setVisibility(View.GONE);
        }

        setupPieChart();
        if (!isMyTasksMode) {
            setupBarChart();
        }
    }

    private void setupRecyclerView() {
        taskAdapter = new TaskAdapter();
        taskAdapter.setInlineCommentsEnabled(true, currentUserId);
        rvUpcomingTasks.setLayoutManager(new LinearLayoutManager(getContext()));
        rvUpcomingTasks.setAdapter(taskAdapter);
        rvUpcomingTasks.setNestedScrollingEnabled(false);

        taskAdapter.setOnTaskClickListener(new TaskAdapter.OnTaskClickListener() {
            @Override
            public void onTaskClick(Task task) {
                if (getContext() == null) return;
                android.content.Intent intent = new android.content.Intent(getContext(), TaskDetailActivity.class);
                intent.putExtra("project_id", task.getProjectId());
                intent.putExtra("task_id", task.getId());
                startActivity(intent);
            }

            @Override
            public void onTaskMenuClick(Task task, View view) {
                // No menu on overview upcoming list for now
            }
        });
    }

    private void setupPieChart() {
        pieChartStatus.setDrawHoleEnabled(true);
        pieChartStatus.setHoleColor(Color.TRANSPARENT);
        pieChartStatus.setTransparentCircleRadius(62f);
        pieChartStatus.setHoleRadius(58f);

        pieChartStatus.setDrawCenterText(true);
        pieChartStatus.setRotationEnabled(true);
        pieChartStatus.setHighlightPerTapEnabled(true);

        pieChartStatus.getDescription().setEnabled(false);
        pieChartStatus.setDrawEntryLabels(false);

        Legend l = pieChartStatus.getLegend();
        l.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        l.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        l.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        l.setDrawInside(false);
        l.setTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface));
        l.setForm(Legend.LegendForm.CIRCLE);
        l.setXEntrySpace(15f);
        l.setYOffset(10f);

        pieChartStatus.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                PieEntry pe = (PieEntry) e;
                if (getString(R.string.task_status_done).equalsIgnoreCase(pe.getLabel())) {
                    nestedScrollView.smoothScrollTo(0, rvUpcomingTasks.getTop());
                }
            }
            @Override
            public void onNothingSelected() {}
        });
    }

    private void setupBarChart() {
        int textColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface);
        barChartProductivity.getDescription().setEnabled(false);
        barChartProductivity.setDrawGridBackground(false);
        barChartProductivity.setDrawBarShadow(false);

        XAxis xAxis = barChartProductivity.getXAxis();
        xAxis.setDrawGridLines(false);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(textColor);
        xAxis.setGranularity(1f);

        barChartProductivity.getAxisLeft().setDrawGridLines(false);
        barChartProductivity.getAxisLeft().setTextColor(textColor);
        barChartProductivity.getAxisLeft().setGranularity(1f);
        barChartProductivity.getAxisRight().setEnabled(false);

        Legend legend = barChartProductivity.getLegend();
        legend.setEnabled(true);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setTextColor(textColor);
        legend.setForm(Legend.LegendForm.SQUARE);
        legend.setXEntrySpace(12f);
        legend.setYOffset(24f);

        // Add extra bottom room so legend does not sit too close to x-axis labels.
        barChartProductivity.setExtraBottomOffset(8f);
    }

    private int getThemeColor(int attr) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        if (getContext() != null) {
            getContext().getTheme().resolveAttribute(attr, typedValue, true);
            return typedValue.data;
        }
        return Color.GRAY;
    }

    private void loadProjectData() {
        if (isMyTasksMode) {
            loadTasks();
            return;
        }
        memberRepository.getMembers(projectId, new MemberRepository.ResultCallback<List<ProjectMember>>() {
            @Override
            public void onSuccess(List<ProjectMember> members) {
                memberNames.clear();
                for (ProjectMember pm : members) {
                    memberNames.put(pm.getUserId(), pm.getDisplayName());
                }
                loadTasks();
            }

            @Override
            public void onError(String message) {
                loadTasks();
            }
        });
    }

    private void loadTasks() {
        TaskRepository.TaskCallback<List<Task>> callback = new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> tasks) {
                List<Task> activeTasks = filterOutTrashTasks(tasks);
                if (activeTasks.isEmpty()) {
                    showEmptyState(true);
                    return;
                }
                showEmptyState(false);
                processTaskStats(activeTasks);
                updateUpcomingTasks(activeTasks);
            }

            @Override
            public void onError(String error) {
                if (isAdded()) Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
            }
        };

        if (isMyTasksMode) {
            taskRepository.getMyTasksWithProjectName(currentUserId, callback);
        } else {
            taskRepository.getTasksByProject(projectId, callback);
        }
    }

    private List<Task> filterOutTrashTasks(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return new ArrayList<>();
        }
        List<Task> activeTasks = new ArrayList<>();
        for (Task task : tasks) {
            String status = task != null && task.getStatus() != null
                    ? task.getStatus().trim().toUpperCase(Locale.US)
                    : "";
            if (!"TRASH".equals(status)) {
                activeTasks.add(task);
            }
        }
        return activeTasks;
    }

    private void showEmptyState(boolean isEmpty) {
        layoutEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        layoutCharts.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        if (isEmpty) {
            tvTotalTasks.setText("0");
            tvDoneTasks.setText("0");
            tvOverdueTasks.setText("0");
        }
    }

    private void processTaskStats(List<Task> tasks) {
        int todo = 0, inProgress = 0, done = 0, overdue = 0;
        
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String todayStr = sdf.format(cal.getTime());

        for (Task task : tasks) {
            if (!hasCompleteScheduleRange(task)) {
                continue;
            }
            String status = task.getStatus() != null ? task.getStatus().toUpperCase() : "TODO";
            if (status.contains("DONE")) {
                done++;
            } else if (status.contains("IN_PROGRESS") || status.contains("PROGRESS") || status.contains("DOING")) {
                inProgress++;
            } else {
                todo++;
            }
            
            if (task.getDueDate() != null && task.getDueDate().compareTo(todayStr) < 0 && !status.contains("DONE")) {
                overdue++;
            }
        }

        tvTotalTasks.setText(String.valueOf(tasks.size()));
        tvDoneTasks.setText(String.valueOf(done));
        tvOverdueTasks.setText(String.valueOf(overdue));

        todoCount = todo;
        inProgressCount = inProgress;
        doneCount = done;

        updatePieChart(todo, inProgress, done);
        if (!isMyTasksMode) {
            updateBarChart(tasks);
        }
    }

    private boolean hasCompleteScheduleRange(Task task) {
        if (task == null) {
            return false;
        }
        String startDate = task.getStartDate();
        String dueDate = task.getDueDate();
        return startDate != null && !startDate.trim().isEmpty()
                && dueDate != null && !dueDate.trim().isEmpty();
    }

    private void updatePieChart(int todo, int inProgress, int done) {
        pieChartStatus.setBackgroundColor(Color.TRANSPARENT);
        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();

        if (todo > 0) {
            entries.add(new PieEntry(todo, getString(R.string.overview_status_todo_count, todo)));
            colors.add(ContextCompat.getColor(requireContext(), R.color.slate_400));
        }
        if (inProgress > 0) {
            entries.add(new PieEntry(inProgress, getString(R.string.overview_status_in_progress_count, inProgress)));
            colors.add(ContextCompat.getColor(requireContext(), R.color.primary));
        }
        if (done > 0) {
            entries.add(new PieEntry(done, getString(R.string.overview_status_done_count, done)));
            colors.add(ContextCompat.getColor(requireContext(), R.color.green_500));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(4f);
        dataSet.setDrawValues(false);

        PieData data = new PieData(dataSet);
        pieChartStatus.setData(data);

        int total = todo + inProgress + done;
        int percent = total > 0 ? (done * 100 / total) : 0;
        
        SpannableString centerText = new SpannableString(percent + "%\n" + getString(R.string.overview_done).toUpperCase());
        centerText.setSpan(new RelativeSizeSpan(2.0f), 0, centerText.length() - 4, 0);
        centerText.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, centerText.length(), 0);
        
        pieChartStatus.setCenterText(centerText);
        pieChartStatus.setCenterTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface));
        pieChartStatus.invalidate();
    }

    private void updateBarChart(List<Task> tasks) {
        barChartProductivity.setBackgroundColor(Color.TRANSPARENT);
        ArrayList<BarEntry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();

        Map<String, int[]> stats = new HashMap<>();
        String unassignedLabel = getString(R.string.overview_unassigned_label);

        for (Task t : tasks) {
            if (!hasCompleteScheduleRange(t)) {
                continue;
            }
            String assigneeId = t.getAssigneeId();
            String name = (assigneeId == null || !memberNames.containsKey(assigneeId))
                    ? unassignedLabel
                    : memberNames.get(assigneeId);

            int[] buckets = stats.computeIfAbsent(name, k -> new int[] {0, 0, 0});
            String status = t.getStatus() != null ? t.getStatus().toUpperCase(Locale.US) : STATUS_TODO_KEY;
            if ("TRASH".equals(status)) {
                continue;
            }

            if (status.contains("DONE")) {
                buckets[2]++;
            } else if (status.contains("IN_PROGRESS") || status.contains("PROGRESS") || status.contains("DOING")) {
                buckets[1]++;
            } else {
                buckets[0]++;
            }
        }

        List<String> orderedNames = new ArrayList<>(stats.keySet());
        orderedNames.sort((left, right) -> {
            if (left.equals(unassignedLabel)) return 1;
            if (right.equals(unassignedLabel)) return -1;
            return left.compareToIgnoreCase(right);
        });

        int i = 0;
        for (String name : orderedNames) {
            int[] buckets = stats.get(name);
            if (buckets == null) {
                continue;
            }
            entries.add(new BarEntry(i, new float[] {buckets[0], buckets[1], buckets[2]}));
            String label = name;
            if (label.length() > 10) label = label.substring(0, 8) + "..";
            labels.add(label);
            i++;
        }

        if (entries.isEmpty()) {
            barChartProductivity.clear();
            return;
        }

        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setColors(new int[] {
                ContextCompat.getColor(requireContext(), R.color.slate_400),
                ContextCompat.getColor(requireContext(), R.color.primary),
                ContextCompat.getColor(requireContext(), R.color.green_500)
        });
        dataSet.setStackLabels(new String[] {
            "Cần làm",
            "Đang làm",
            "Đã xong"
        });
        dataSet.setDrawValues(true);
        dataSet.setValueTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface));
        dataSet.setValueTextSize(12f);
        dataSet.setValueFormatter(new StackedValueFormatter(false, "", 0));

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.5f);
        barChartProductivity.setData(data);

        XAxis xAxis = barChartProductivity.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setLabelCount(labels.size());
        xAxis.setGranularity(1f);
        barChartProductivity.getAxisLeft().setGranularity(1f);
        barChartProductivity.getAxisRight().setEnabled(false);
        barChartProductivity.invalidate();
    }

    private void updateUpcomingTasks(List<Task> allTasks) {
        List<Task> upcoming = new ArrayList<>();
        for (Task t : allTasks) {
            String status = t.getStatus() != null ? t.getStatus() : "";
            if (!"DONE".equalsIgnoreCase(status) && !"TRASH".equalsIgnoreCase(status)) {
                upcoming.add(t);
            }
        }
        Collections.sort(upcoming, (t1, t2) -> {
            return Long.compare(parseTaskCreatedTime(t2), parseTaskCreatedTime(t1));
        });
        int limit = Math.min(upcoming.size(), 5);
        taskAdapter.setTasks(upcoming.subList(0, limit));
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
}
