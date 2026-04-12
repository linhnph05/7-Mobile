package com.team7.taskflow.ui.project;

import android.os.Bundle;
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
import com.team7.taskflow.ui.common.AvatarUiUtils;

import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Calendar;
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
    private static final String PRIORITY_HIGH = "HIGH";
    private static final String STATUS_TRASH = "TRASH";
    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_TODO = "TODO";
    private static final long DAY_MILLIS = 24L * 3600 * 1000;
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
    private View layoutTimelineContent;
    private View viewPastOverlay;
    private View viewTodayLine;
    private LinearLayout layoutEmptyState;
    private TextView tvTimelineEmptyTitle;
    private TextView tvTimelineEmptyDesc;

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
        layoutTimelineContent = view.findViewById(R.id.layoutTimelineContent);
        ganttScrollView = view.findViewById(R.id.ganttScrollView);
        viewPastOverlay = view.findViewById(R.id.viewPastOverlay);
        viewTodayLine = view.findViewById(R.id.viewTodayLine);
        layoutEmptyState = view.findViewById(R.id.layoutEmptyStateTimeline);
        tvTimelineEmptyTitle = view.findViewById(R.id.tvTimelineEmptyTitle);
        tvTimelineEmptyDesc = view.findViewById(R.id.tvTimelineEmptyDesc);
    }

    private void loadTimelineData() {
        TaskRepository.TaskCallback<List<Task>> callback = new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> tasks) {
                if (!isAdded()) return;
                List<Task> activeTasks = buildActiveTimelineTasks(tasks);
                List<Task> renderableTasks = buildRenderableTimelineTasks(activeTasks);
                if (renderableTasks.isEmpty()) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> renderTasks(activeTasks, renderableTasks));
                    }
                    return;
                }

                fetchAssigneeAvatars(renderableTasks, () -> {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> renderTasks(activeTasks, renderableTasks));
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

    private void renderTasks(List<Task> activeTasks, List<Task> renderableTasks) {
        if (containerTaskLabels == null || containerGanttBars == null) return;

        boolean hasAnyActiveTask = activeTasks != null && !activeTasks.isEmpty();
        if (renderableTasks == null || renderableTasks.isEmpty()) {
            updateTimelineEmptyMessage(hasAnyActiveTask);
            showEmptyState(true);
            clearTimelineViews();
            return;
        }

        showEmptyState(false);
        clearTimelineViews();

        float density = getResources().getDisplayMetrics().density;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        
        Calendar minCal = Calendar.getInstance();
        Calendar maxCal = Calendar.getInstance();
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0); today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0);

        Calendar earliestStart = null, latest = null;

        for (Task t : renderableTasks) {
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
        int totalDays = (int) ((maxCal.getTimeInMillis() - minTime) / DAY_MILLIS) + 1;

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
            tvDay.setTextColor(ContextCompat.getColor(requireContext(), R.color.theme_text_secondary));
            if (containerGanttDays != null) containerGanttDays.addView(tvDay);

            View grid = new View(getContext());
            LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(1, -1);
            glp.setMargins((int)(COLUMN_WIDTH_DP * density)-1, 0, 0, 0);
            grid.setLayoutParams(glp);
            grid.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.theme_border));
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
        for (Task t : renderableTasks) {
            View labelView = getLayoutInflater().inflate(R.layout.item_timeline_label, containerTaskLabels, false);
            ((TextView)labelView.findViewById(R.id.tvTaskName)).setText(t.getTitle());
            ImageView iv = labelView.findViewById(R.id.imgAssigneeAvatar);
            if (iv != null && t.getAssigneeId() != null) {
                iv.setVisibility(View.VISIBLE);
                AvatarUiUtils.bindAvatarOrFallback(iv, null, assigneeAvatarUrlMap.get(t.getAssigneeId()), t.getAssigneeId());
            } else if (iv != null) {
                iv.setVisibility(View.INVISIBLE);
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

            int offset = Math.max(0, (int)((start - minTime) / DAY_MILLIS));
            int duration = Math.max(1, (int)((due - start) / DAY_MILLIS) + 1);
            int mStart = (int)(offset * COLUMN_WIDTH_DP * density);

            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) bar.getLayoutParams();
            lp.setMarginStart(mStart);
            lp.width = (int)(duration * COLUMN_WIDTH_DP * density);
            bar.setLayoutParams(lp);

            applyTimelineBarColor(bar, t);

            containerGanttBars.addView(barView);
        }

        // Today Line
        if (viewTodayLine != null) {
            int todayMargin = (int)(((today.getTimeInMillis() - minTime) / DAY_MILLIS) * COLUMN_WIDTH_DP * density);

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
        tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.theme_text_primary));
        containerGanttMonths.addView(tv);
    }

    private void showEmptyState(boolean isEmpty) {
        if (layoutTimelineContent != null) {
            layoutTimelineContent.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
        if (ganttScrollView != null) {
            ganttScrollView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
        if (layoutEmptyState != null) {
            layoutEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
    }

    private void updateTimelineEmptyMessage(boolean hasAnyActiveTask) {
        if (tvTimelineEmptyTitle == null || tvTimelineEmptyDesc == null) {
            return;
        }
        if (hasAnyActiveTask) {
            tvTimelineEmptyTitle.setText(R.string.timeline_no_schedule_title);
            tvTimelineEmptyDesc.setText(R.string.timeline_no_schedule_desc);
        } else {
            tvTimelineEmptyTitle.setText(R.string.overview_no_tasks);
            tvTimelineEmptyDesc.setText(R.string.overview_no_tasks_desc);
        }
    }

    private void clearTimelineViews() {
        containerTaskLabels.removeAllViews();
        containerGanttBars.removeAllViews();
        if (containerGanttMonths != null) containerGanttMonths.removeAllViews();
        if (containerGanttDays != null) containerGanttDays.removeAllViews();
        if (containerGanttGrid != null) containerGanttGrid.removeAllViews();
        if (viewPastOverlay != null) viewPastOverlay.setVisibility(View.GONE);
        if (viewTodayLine != null) viewTodayLine.setVisibility(View.GONE);
    }

    private boolean isTrashTask(Task task) {
        if (task == null) {
            return true;
        }
        String status = task.getStatus() != null
                ? task.getStatus().trim().toUpperCase(Locale.US)
                : "";
        return status.contains(STATUS_TRASH) || status.contains("DELETED");
    }

    private List<Task> buildActiveTimelineTasks(List<Task> source) {
        List<Task> activeTasks = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return activeTasks;
        }
        for (Task task : source) {
            if (!isTrashTask(task)) {
                activeTasks.add(task);
            }
        }
        return activeTasks;
    }

    private List<Task> buildRenderableTimelineTasks(List<Task> source) {
        List<Task> renderable = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return renderable;
        }

        for (Task task : source) {
            if (isRenderableTimelineTask(task)) {
                renderable.add(task);
            }
        }
        return renderable;
    }

    private boolean isRenderableTimelineTask(Task task) {
        if (task == null) {
            return false;
        }

        Date startDate = parseTimelineDate(task.getStartDate());
        Date dueDate = parseTimelineDate(task.getDueDate());
        if (startDate == null || dueDate == null) {
            return false;
        }

        return !dueDate.before(startDate);
    }

    private void applyTimelineBarColor(View bar, Task task) {
        String normalizedStatus = normalizeStatus(task != null ? task.getStatus() : null);
        String normalizedPriority = normalizePriority(task != null ? task.getPriority() : null);

        if (STATUS_DONE.equals(normalizedStatus)) {
            bar.setBackgroundResource(R.drawable.bg_timeline_bar_done);
            return;
        }

        if (PRIORITY_HIGH.equals(normalizedPriority)) {
            bar.setBackgroundResource(R.drawable.bg_timeline_bar_high);
            return;
        }

        if (STATUS_IN_PROGRESS.equals(normalizedStatus)) {
            bar.setBackgroundResource(R.drawable.bg_timeline_bar_in_progress);
            return;
        }

        bar.setBackgroundResource(R.drawable.bg_timeline_bar_todo);
    }

    private String normalizeStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.trim().isEmpty()) {
            return STATUS_TODO;
        }

        String status = rawStatus.trim().toUpperCase(Locale.US)
                .replace('-', '_')
                .replace(' ', '_');

        if (status.contains("DONE") || status.contains("COMPLETE") || status.contains("FINISH")) {
            return STATUS_DONE;
        }

        if (status.contains("IN_PROGRESS") || status.contains("INPROGRESS")
                || status.contains("PROGRESS") || status.contains("DOING")
                || status.contains("ACTIVE")) {
            return STATUS_IN_PROGRESS;
        }

        if (status.contains("TODO") || status.contains("TO_DO") || status.contains("OPEN")
                || status.contains("NEW") || status.contains("PENDING") || status.contains("BACKLOG")) {
            return STATUS_TODO;
        }

        return STATUS_TODO;
    }

    private String normalizePriority(String rawPriority) {
        if (rawPriority == null || rawPriority.trim().isEmpty()) {
            return "NONE";
        }

        String priority = rawPriority.trim().toUpperCase(Locale.US)
                .replace('-', '_')
                .replace(' ', '_');

        if (priority.contains("HIGH") || priority.contains("URGENT") || priority.contains("CRITICAL")) {
            return PRIORITY_HIGH;
        }

        if (priority.contains("MEDIUM")) {
            return "MEDIUM";
        }

        if (priority.contains("LOW")) {
            return "LOW";
        }

        return "NONE";
    }

    private Date parseTimelineDate(String rawDate) {
        if (rawDate == null || rawDate.trim().isEmpty() || rawDate.length() < 10) {
            return null;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(rawDate.substring(0, 10));
        } catch (Exception ignored) {
            return null;
        }
    }
}
