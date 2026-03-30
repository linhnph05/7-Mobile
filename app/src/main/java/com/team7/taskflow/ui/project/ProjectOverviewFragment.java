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
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;
import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.MemberRepository;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.ProjectMember;
import com.team7.taskflow.domain.model.Task;
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

    private long projectId;
    private TaskRepository taskRepository;
    private MemberRepository memberRepository;
    private TaskAdapter taskAdapter;

    private NestedScrollView nestedScrollView;
    private TextView tvTotalTasks, tvDoneTasks, tvOverdueTasks;
    private PieChart pieChartStatus;
    private BarChart barChartProductivity;
    private RecyclerView rvUpcomingTasks;
    private LinearLayout layoutEmptyState, layoutCharts;
    
    private Map<String, String> memberNames = new HashMap<>();

    public static ProjectOverviewFragment newInstance(long projectId) {
        ProjectOverviewFragment fragment = new ProjectOverviewFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_PROJECT_ID, projectId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            projectId = getArguments().getLong(ARG_PROJECT_ID);
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
        rvUpcomingTasks = view.findViewById(R.id.rvUpcomingTasks);
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState);
        layoutCharts = view.findViewById(R.id.layoutCharts);

        setupPieChart();
        setupBarChart();
    }

    private void setupRecyclerView() {
        taskAdapter = new TaskAdapter();
        rvUpcomingTasks.setLayoutManager(new LinearLayoutManager(getContext()));
        rvUpcomingTasks.setAdapter(taskAdapter);
        rvUpcomingTasks.setNestedScrollingEnabled(false);
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
        barChartProductivity.getAxisRight().setEnabled(false);
        barChartProductivity.getLegend().setEnabled(false);
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
        taskRepository.getTasksByProject(projectId, new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> tasks) {
                if (tasks == null || tasks.isEmpty()) {
                    showEmptyState(true);
                    return;
                }
                showEmptyState(false);
                processTaskStats(tasks);
                updateUpcomingTasks(tasks);
            }

            @Override
            public void onError(String error) {
                if (isAdded()) Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
            }
        });
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
        int todo = 0, in_progress = 0, done = 0, overdue = 0;
        
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String todayStr = sdf.format(cal.getTime());

        for (Task task : tasks) {
            String status = task.getStatus() != null ? task.getStatus().toUpperCase() : "TODO";
            if (status.contains("DONE")) {
                done++;
            } else if (status.contains("IN_PROGRESS") || status.contains("PROGRESS") || status.contains("DOING")) {
                in_progress++;
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

        updatePieChart(todo, in_progress, done);
        updateBarChart(tasks);
    }

    private void updatePieChart(int todo, int in_progress, int done) {
        pieChartStatus.setBackgroundColor(Color.TRANSPARENT);
        ArrayList<PieEntry> entries = new ArrayList<>();
        ArrayList<Integer> colors = new ArrayList<>();

        if (todo > 0) {
            entries.add(new PieEntry(todo, getString(R.string.task_status_todo)));
            colors.add(0xFF94A3B8); // Slate 400
        }
        if (in_progress > 0) {
            entries.add(new PieEntry(in_progress, getString(R.string.task_status_in_progress)));
            colors.add(0xFF6366F1); // Indigo 500
        }
        if (done > 0) {
            entries.add(new PieEntry(done, getString(R.string.task_status_done)));
            colors.add(0xFF10B981); // Emerald 500
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setSliceSpace(4f);
        dataSet.setDrawValues(false);

        PieData data = new PieData(dataSet);
        pieChartStatus.setData(data);

        int total = todo + in_progress + done;
        int percent = total > 0 ? (done * 100 / total) : 0;
        
        SpannableString centerText = new SpannableString(percent + "%\n" + getString(R.string.overview_done).toUpperCase());
        centerText.setSpan(new RelativeSizeSpan(2.0f), 0, centerText.length() - 4, 0);
        centerText.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, centerText.length(), 0);
        
        pieChartStatus.setCenterText(centerText);
        pieChartStatus.setCenterTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface));
        pieChartStatus.invalidate();
    }

    private void updateBarChart(List<Task> tasks) {
        Map<String, Integer> stats = new HashMap<>();
        // Group tasks by assignee
        for (Task t : tasks) {
            String assigneeId = t.getAssigneeId();
            String name = (assigneeId == null || !memberNames.containsKey(assigneeId)) 
                ? "Chưa phân công" : memberNames.get(assigneeId);
            stats.put(name, stats.getOrDefault(name, 0) + 1);
        }

        barChartProductivity.setBackgroundColor(Color.TRANSPARENT);
        ArrayList<BarEntry> entries = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        
        int i = 0;
        for (Map.Entry<String, Integer> entry : stats.entrySet()) {
            entries.add(new BarEntry(i, entry.getValue()));
            // Rút gọn tên nếu quá dài (ví dụ: "Nguyễn Văn A" -> "Nguyễn V. A")
            String label = entry.getKey();
            if (label.length() > 10) label = label.substring(0, 8) + "..";
            labels.add(label);
            i++;
        }

        ArrayList<Integer> colors = new ArrayList<>();
        int[] palette = {0xFF6366F1, 0xFF10B981, 0xFFF59E0B, 0xFFF43F5E, 0xFF8B5CF6, 0xFFEC4899};
        for (int j = 0; j < entries.size(); j++) {
            colors.add(palette[j % palette.length]);
        }

        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setColors(colors);
        dataSet.setDrawValues(true);
        dataSet.setValueTextColor(getThemeColor(com.google.android.material.R.attr.colorOnSurface));
        dataSet.setValueTextSize(11f);
        // Định dạng số nguyên cho giá trị trên đỉnh cột
        dataSet.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.5f);
        barChartProductivity.setData(data);

        XAxis xAxis = barChartProductivity.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setLabelCount(labels.size());
        xAxis.setGranularity(1f);
        
        barChartProductivity.getAxisLeft().setGranularity(1f); // Trục Y hiển thị số nguyên
        barChartProductivity.getAxisRight().setEnabled(false);
        barChartProductivity.invalidate();
    }

    private void updateUpcomingTasks(List<Task> allTasks) {
        List<Task> upcoming = new ArrayList<>();
        for (Task t : allTasks) {
            if (!"DONE".equalsIgnoreCase(t.getStatus())) upcoming.add(t);
        }
        Collections.sort(upcoming, (t1, t2) -> {
             if (t1.getDueDate() == null) return 1;
             if (t2.getDueDate() == null) return -1;
             return t1.getDueDate().compareTo(t2.getDueDate());
        });
        int limit = Math.min(upcoming.size(), 5);
        taskAdapter.setTasks(upcoming.subList(0, limit));
    }
}
