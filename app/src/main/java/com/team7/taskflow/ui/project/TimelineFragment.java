package com.team7.taskflow.ui.project;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.team7.taskflow.R;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.Task;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fragment hiển thị biểu đồ Gantt (Timeline).
 * Được nâng cấp từ logic cũ của TimelineActivity.
 */
public class TimelineFragment extends Fragment {

    private static final String ARG_PROJECT_ID = "project_id";
    private static final String ARG_IS_MY_TASKS = "is_my_tasks";
    private static final String ARG_USER_ID = "user_id";
    private long projectId;
    private TaskRepository taskRepository;
    private boolean isMyTasksMode;
    private String currentUserId;

    private LinearLayout containerTaskLabels;
    private LinearLayout containerGanttBars;
    private LinearLayout containerGanttMonths;
    private LinearLayout containerGanttDays;
    private LinearLayout containerGanttGrid;
    private HorizontalScrollView ganttScrollView;
    private View viewPastOverlay;
    private View viewTodayLine;

    private final Map<String, String> assigneeAvatarUrlMap = new HashMap<>();
    private final int COLUMN_WIDTH_DP = 40;

    public static TimelineFragment newInstance(long projectId, boolean isMyTasksMode, String userId) {
        TimelineFragment fragment = new TimelineFragment();
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
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.view_timeline_content, container, false);
        initViews(view);
        loadTimelineData();
        return view;
    }

    private void initViews(View view) {
        containerTaskLabels = view.findViewById(R.id.containerTaskLabels);
        containerGanttBars = view.findViewById(R.id.containerGanttBars);
        containerGanttMonths = view.findViewById(R.id.containerGanttMonths);
        containerGanttDays = view.findViewById(R.id.containerGanttDays);
        containerGanttGrid = view.findViewById(R.id.containerGanttGrid);
        ganttScrollView = view.findViewById(R.id.ganttScrollView);
        viewPastOverlay = view.findViewById(R.id.viewPastOverlay);
        viewTodayLine = view.findViewById(R.id.viewTodayLine);
    }

    private void loadTimelineData() {
        TaskRepository.TaskCallback<List<Task>> callback = new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> tasks) {
                if (!isAdded()) return;
                fetchAssigneeAvatars(tasks, () -> {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> renderTasks(tasks));
                    }
                });
            }

            @Override
            public void onError(String error) {
                if (isAdded()) {
                    Toast.makeText(getContext(), "Lỗi: " + error, Toast.LENGTH_SHORT).show();
                }
            }
        };

        if (isMyTasksMode) {
            taskRepository.getMyTasksWithProjectName(currentUserId, callback);
        } else {
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
            if (task != null && task.getAssigneeId() != null && !task.getAssigneeId().trim().isEmpty()) {
                assigneeIds.add(task.getAssigneeId());
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
                    public void onResponse(@NonNull retrofit2.Call<List<com.team7.taskflow.domain.model.User>> call,
                                           @NonNull retrofit2.Response<List<com.team7.taskflow.domain.model.User>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            for (com.team7.taskflow.domain.model.User user : response.body()) {
                                if (user.getUserId() != null && user.getAvatarUrl() != null) {
                                    assigneeAvatarUrlMap.put(user.getUserId(), user.getAvatarUrl());
                                }
                            }
                        }
                        onDone.run();
                    }

                    @Override
                    public void onFailure(@NonNull retrofit2.Call<List<com.team7.taskflow.domain.model.User>> call, @NonNull Throwable t) {
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

        Calendar earliestStart = null, latest = null;

        for (Task t : tasks) {
            try {
                if (t.getStartDate() != null && t.getStartDate().length() >= 10) {
                    Date d = sdf.parse(t.getStartDate().substring(0, 10));
                    if (earliestStart == null || d.before(earliestStart.getTime())) { earliestStart = Calendar.getInstance(); earliestStart.setTime(d); }
                    if (latest == null || d.after(latest.getTime())) { latest = Calendar.getInstance(); latest.setTime(d); }
                }
                if (t.getDueDate() != null && t.getDueDate().length() >= 10) {
                    Date d = sdf.parse(t.getDueDate().substring(0, 10));
                    if (earliestStart == null) { earliestStart = Calendar.getInstance(); earliestStart.setTime(d); }
                    if (latest == null || d.after(latest.getTime())) { latest = Calendar.getInstance(); latest.setTime(d); }
                }
            } catch (Exception ignored) {}
        }

        if (earliestStart == null) {
            minCal.setTime(today.getTime());
            maxCal.setTime(today.getTime());
        } else {
            minCal.setTime(earliestStart.before(today) ? earliestStart.getTime() : today.getTime());
            maxCal.setTime(latest != null && latest.after(today) ? latest.getTime() : today.getTime());
        }

        minCal.add(Calendar.DAY_OF_YEAR, -2);
        maxCal.add(Calendar.DAY_OF_YEAR, 15);
        minCal.set(Calendar.HOUR_OF_DAY, 0); minCal.set(Calendar.MINUTE, 0); minCal.set(Calendar.SECOND, 0);
        maxCal.set(Calendar.HOUR_OF_DAY, 0); maxCal.set(Calendar.MINUTE, 0); maxCal.set(Calendar.SECOND, 0);

        long minTime = minCal.getTimeInMillis();
        int totalDays = (int) ((maxCal.getTimeInMillis() - minTime) / (24L * 3600 * 1000)) + 1;

        // Headers
        Calendar iter = (Calendar) minCal.clone();
        String currentMonth = "";
        int monthDays = 0;

        for (int i = 0; i < totalDays; i++) {
            TextView tvDay = new TextView(getContext());
            tvDay.setText(String.valueOf(iter.get(Calendar.DAY_OF_MONTH)));
            tvDay.setLayoutParams(new LinearLayout.LayoutParams((int)(COLUMN_WIDTH_DP * density), -2));
            tvDay.setGravity(Gravity.CENTER);
            tvDay.setTextSize(11f);
            tvDay.setTextColor(0xFF64748B);
            if (containerGanttDays != null) containerGanttDays.addView(tvDay);

            View grid = new View(getContext());
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(1, -1);
            glp.setMargins((int)(COLUMN_WIDTH_DP * density)-1, 0, 0, 0);
            grid.setLayoutParams(glp);
            grid.setBackgroundColor(0xFFE2E8F0);
            if (containerGanttGrid != null) containerGanttGrid.addView(grid);

            String m = new SimpleDateFormat("MMM", Locale.US).format(iter.getTime());
            if (currentMonth.equals(m)) monthDays++;
            else {
                if (monthDays > 0) addMonthHeader(currentMonth, monthDays, density);
                currentMonth = m; monthDays = 1;
            }
            iter.add(Calendar.DAY_OF_YEAR, 1);
        }
        addMonthHeader(currentMonth, monthDays, density);

        // Bars
        for (Task t : tasks) {
            View labelView = getLayoutInflater().inflate(R.layout.item_timeline_label, containerTaskLabels, false);
            ((TextView)labelView.findViewById(R.id.tvTaskName)).setText(t.getTitle());
            ImageView iv = labelView.findViewById(R.id.imgAssigneeAvatar);
            if (iv != null && t.getAssigneeId() != null && assigneeAvatarUrlMap.containsKey(t.getAssigneeId())) {
                com.bumptech.glide.Glide.with(this).load(assigneeAvatarUrlMap.get(t.getAssigneeId())).circleCrop().into(iv);
            }
            containerTaskLabels.addView(labelView);

            View barView = getLayoutInflater().inflate(R.layout.item_timeline_bar, containerGanttBars, false);
            View bar = barView.findViewById(R.id.taskBar);
            ((TextView)barView.findViewById(R.id.tvBarLabel)).setText(t.getTitle());

            long start = minTime, due = minTime;
            try {
                if (t.getStartDate() != null) start = sdf.parse(t.getStartDate().substring(0,10)).getTime();
                if (t.getDueDate() != null) due = sdf.parse(t.getDueDate().substring(0,10)).getTime();
                else due = start;
            } catch (Exception ignored) {}

            int offset = Math.max(0, (int)((start - minTime)/(24L*3600*1000)));
            int duration = Math.max(1, (int)((due - start)/(24L*3600*1000)) + 1);
            int mStart = (int)(offset * COLUMN_WIDTH_DP * density);

            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) bar.getLayoutParams();
            lp.setMarginStart(mStart);
            lp.width = (int)(duration * COLUMN_WIDTH_DP * density);
            bar.setLayoutParams(lp);

            if ("HIGH".equals(t.getPriority())) bar.setBackgroundResource(R.drawable.bg_timeline_bar_high);
            else if ("DONE".equals(t.getStatus())) bar.setBackgroundResource(R.drawable.bg_timeline_bar_done);
            else bar.setBackgroundResource(R.drawable.bg_timeline_bar_default);

            containerGanttBars.addView(barView);
        }

        // Today Line
        if (viewTodayLine != null) {
            int todayMargin = (int)(((today.getTimeInMillis() - minTime)/(24L*3600*1000)) * COLUMN_WIDTH_DP * density);

            if (viewPastOverlay != null) {
                FrameLayout.LayoutParams pastLp = (FrameLayout.LayoutParams) viewPastOverlay.getLayoutParams();
                pastLp.width = Math.max(0, todayMargin);
                pastLp.setMarginStart(0);
                viewPastOverlay.setLayoutParams(pastLp);
                viewPastOverlay.setVisibility(todayMargin > 0 ? View.VISIBLE : View.GONE);
            }

            FrameLayout.LayoutParams tlp = (FrameLayout.LayoutParams) viewTodayLine.getLayoutParams();
            tlp.setMarginStart(todayMargin);
            viewTodayLine.setLayoutParams(tlp);
            viewTodayLine.setVisibility(View.VISIBLE);
            ganttScrollView.post(() -> ganttScrollView.scrollTo(0, 0));
        }
    }

    private void addMonthHeader(String name, int days, float density) {
        if (days <= 0 || containerGanttMonths == null) return;
        TextView tv = new TextView(getContext());
        tv.setText(name);
        tv.setLayoutParams(new LinearLayout.LayoutParams((int)(days * COLUMN_WIDTH_DP * density), -2));
        tv.setPadding(32, 0, 0, 0);
        tv.setTextSize(12f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setTextColor(0xFF1E293B);
        containerGanttMonths.addView(tv);
    }
}
