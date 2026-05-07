package com.team7.taskflow.ui.foryou;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.team7.taskflow.R;
import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.remote.api.UserApi;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.domain.model.User;
import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.ui.common.AvatarUiUtils;
import com.team7.taskflow.ui.dashboard.DashboardActivity;
import com.team7.taskflow.ui.profile.ProfileActivity;
import com.team7.taskflow.ui.project.CreateProjectActivity;
import com.team7.taskflow.ui.project.TaskAdapter;
import com.team7.taskflow.ui.timeline.ProjectDetailActivity;
import com.team7.taskflow.utils.NavigationUtils;
import com.team7.taskflow.utils.SessionManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ForYouActivity extends BaseActivity {

    private static final long TASKS_CACHE_TTL_MS = 20_000L;
    private static List<Task> cachedMyTasks = new ArrayList<>();
    private static long cachedMyTasksAtMs = 0L;
    private static String cachedMyTasksUserId;

    private enum TaskFilter {
        ALL,
        TODAY,
        UPCOMING
    }

    private TextView tvGreeting;
    private TextView tvTaskCount;
    private TextView tvProgressPercent;
    private TextView tvDoneCount;
    private TextView tvRemainingCount;
    private com.google.android.material.button.MaterialButton chipAll;
    private com.google.android.material.button.MaterialButton chipToday;
    private com.google.android.material.button.MaterialButton chipUpcoming;
    private ProgressBar pbOverallProgress;
    private RecyclerView rvMyTasks;
    private ImageView ivProfilePic;
    private TextView tvProfileAvatarLetter;
    private BottomNavigationView bottomNavigationView;
    private FloatingActionButton fabAdd;
    private boolean isBottomNavNavigating = false;

    private TaskAdapter taskAdapter;
    private TaskRepository taskRepository;
    private TaskFilter activeFilter = TaskFilter.ALL;
    private List<Task> allTasks = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        NavigationUtils.suppressActivityTransition(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_for_you);
        
        SessionManager.init(this);
        taskRepository = TaskRepository.getInstance();

        bindViews();
        applyNavTransitionIfNeeded();
        setupRecycler();
        setupActions();
        setupBottomNavigation();
        renderGreeting();
        loadTasks();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        NavigationUtils.suppressActivityTransition(this);
        super.onNewIntent(intent);
        setIntent(intent);
        applyNavTransitionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isBottomNavNavigating = false;
        applyNavTransitionIfNeeded();
        // Update bottom navigation selected item to ensure icon highlights correctly
        if (bottomNavigationView != null) {
            bottomNavigationView.setItemIconTintList(null);
            bottomNavigationView.getMenu().findItem(R.id.nav_tasks).setChecked(true);
        }
        loadTasks();
    }

    private void applyNavTransitionIfNeeded() {
        Intent intent = getIntent();
        if (intent == null) {
            return;
        }
        if (!intent.hasExtra(NavigationUtils.EXTRA_NAV_FROM)
                || !intent.hasExtra(NavigationUtils.EXTRA_NAV_TO)) {
            return;
        }

        NavigationUtils.applyTopContentSlideAnimation(this, findViewById(R.id.contentScrollView));

        intent.removeExtra(NavigationUtils.EXTRA_NAV_FROM);
        intent.removeExtra(NavigationUtils.EXTRA_NAV_TO);
    }

    private void bindViews() {
        tvGreeting = findViewById(R.id.tvGreeting);
        tvTaskCount = findViewById(R.id.tvTaskCount);
        tvProgressPercent = findViewById(R.id.tvProgressPercent);
        tvDoneCount = findViewById(R.id.tvDoneCount);
        tvRemainingCount = findViewById(R.id.tvRemainingCount);
        chipAll = findViewById(R.id.chipAll);
        chipToday = findViewById(R.id.chipToday);
        chipUpcoming = findViewById(R.id.chipUpcoming);
        pbOverallProgress = findViewById(R.id.pbOverallProgress);
        rvMyTasks = findViewById(R.id.rvMyTasks);
        ivProfilePic = findViewById(R.id.ivProfilePic);
        tvProfileAvatarLetter = findViewById(R.id.tvProfileAvatarLetter);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        if (bottomNavigationView != null) {
            bottomNavigationView.setItemIconTintList(null);
        }
        fabAdd = findViewById(R.id.fabAdd);
    }

    private void setupRecycler() {
        taskAdapter = new TaskAdapter();
        taskAdapter.setInlineCommentsEnabled(true, SessionManager.getUserId());
        taskAdapter.setOnTaskClickListener(new TaskAdapter.OnTaskClickListener() {
            @Override
            public void onTaskClick(Task task) {
                Intent intent = new Intent(ForYouActivity.this, ProjectDetailActivity.class);
                intent.putExtra("project_id", task.getProjectId());
                intent.putExtra("project_name", task.getProjectName());
                intent.putExtra(ProjectDetailActivity.EXTRA_INITIAL_TAB, ProjectDetailActivity.INITIAL_TAB_TIMELINE);
                if (task.getId() != null) {
                    intent.putExtra(ProjectDetailActivity.EXTRA_OPEN_TASK_ID, task.getId());
                }
                if (task.getProjectInfo() != null) {
                    intent.putExtra("user_role", task.getProjectInfo().getUserRole());
                    intent.putExtra("project_color", task.getProjectInfo().getColor());
                }
                startActivity(intent);
            }

            @Override
            public void onTaskMenuClick(Task task, android.view.View view) {
                // No contextual menu in ForYou.
            }
        });

        rvMyTasks.setLayoutManager(new LinearLayoutManager(this));
        rvMyTasks.setAdapter(taskAdapter);
        rvMyTasks.setNestedScrollingEnabled(false);
    }

    private void setupActions() {
        chipAll.setOnClickListener(v -> applyFilter(TaskFilter.ALL));
        chipToday.setOnClickListener(v -> applyFilter(TaskFilter.TODAY));
        chipUpcoming.setOnClickListener(v -> applyFilter(TaskFilter.UPCOMING));
        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> startActivity(new Intent(this, CreateProjectActivity.class)));
        }
    }

    private void setupBottomNavigation() {
        if (bottomNavigationView == null) {
            return;
        }
        bottomNavigationView.getMenu().findItem(R.id.nav_tasks).setChecked(true);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (isBottomNavNavigating) {
                return true;
            }
            if (id == R.id.nav_tasks) {
                return true;
            }
            if (id == R.id.nav_home) {
                isBottomNavNavigating = true;
                Intent intent = new Intent(this, DashboardActivity.class);
                boolean started = NavigationUtils.startActivityWithNavAnimation(
                        this, intent, NavigationUtils.NAV_TASKS, NavigationUtils.NAV_HOME);
                if (!started) {
                    isBottomNavNavigating = false;
                }
                return true;
            }
            if (id == R.id.nav_settings) {
                isBottomNavNavigating = true;
                Intent intent = new Intent(this, ProfileActivity.class);
                boolean started = NavigationUtils.startActivityWithNavAnimation(
                        this, intent, NavigationUtils.NAV_TASKS, NavigationUtils.NAV_SETTINGS);
                if (!started) {
                    isBottomNavNavigating = false;
                }
                return true;
            }
            return id == R.id.nav_assistant;
        });
    }

    private void renderGreeting() {
        String displayName = SessionManager.getDisplayName();
        if (TextUtils.isEmpty(displayName)) {
            displayName = getString(R.string.for_you_default_name);
        }
        tvGreeting.setText(getString(R.string.for_you_greeting_format, displayName));

        AvatarUiUtils.bindAvatarOrFallback(ivProfilePic, tvProfileAvatarLetter, null, displayName);
        loadUserAvatar();
    }

    private void loadUserAvatar() {
        String currentUserId = SessionManager.getUserId();
        if (TextUtils.isEmpty(currentUserId)) {
            return;
        }

        UserApi userApi = SupabaseClient.getInstance().getService(UserApi.class);
        userApi.getUserById("eq." + currentUserId, "*").enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {
                if (!response.isSuccessful() || response.body() == null || response.body().isEmpty()) {
                    return;
                }
                User user = response.body().get(0);
                String displayName = user.getDisplayName();
                if (!TextUtils.isEmpty(displayName)) {
                    runOnUiThread(() -> tvGreeting.setText(getString(R.string.for_you_greeting_format, displayName)));
                }
                if (user.getAvatarUrl() == null || user.getAvatarUrl().isEmpty()) {
                    return;
                }
                runOnUiThread(() -> AvatarUiUtils.bindAvatarOrFallback(
                        ivProfilePic,
                        tvProfileAvatarLetter,
                        user.getAvatarUrl(),
                        user.getDisplayNameOrEmail()));
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                // Keep default avatar.
            }
        });
    }

    private void loadTasks() {
        String currentUserId = SessionManager.getUserId();
        if (TextUtils.isEmpty(currentUserId)) {
            Toast.makeText(this, getString(R.string.error_unknown), Toast.LENGTH_SHORT).show();
            return;
        }

        if (canUseTaskCache(currentUserId)) {
            allTasks = new ArrayList<>();
            for (Task t : cachedMyTasks) {
                if (!"TRASH".equalsIgnoreCase(t.getStatus())) allTasks.add(t);
            }
            taskAdapter.setSubtaskProgressSource(allTasks);
            updateOverview(allTasks);
            applyFilter(activeFilter);
            return;
        }

        taskRepository.getMyTasksWithProjectName(currentUserId, new TaskRepository.TaskCallback<List<Task>>() {
            @Override
            public void onSuccess(List<Task> result) {
                runOnUiThread(() -> {
                    allTasks = result != null ? result : new ArrayList<>();
                    cachedMyTasks = new ArrayList<>(allTasks);
                    cachedMyTasksAtMs = System.currentTimeMillis();
                    cachedMyTasksUserId = currentUserId;
                    taskAdapter.setSubtaskProgressSource(allTasks);
                    updateOverview(allTasks);
                    applyFilter(activeFilter);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(ForYouActivity.this, error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private boolean canUseTaskCache(String userId) {
        if (TextUtils.isEmpty(userId)) {
            return false;
        }
        if (TextUtils.isEmpty(cachedMyTasksUserId) || !userId.equals(cachedMyTasksUserId)) {
            return false;
        }
        long ageMs = System.currentTimeMillis() - cachedMyTasksAtMs;
        return ageMs >= 0L && ageMs <= TASKS_CACHE_TTL_MS;
    }

    private void updateOverview(List<Task> tasks) {
        int total = tasks.size();
        int doneCount = 0;

        for (Task task : tasks) {
            if (isDone(task)) {
                doneCount++;
            }
        }

        int remainingCount = Math.max(total - doneCount, 0);
        int percent = total == 0 ? 0 : (int) Math.round((doneCount * 100.0) / total);

        pbOverallProgress.setProgress(percent);
        tvProgressPercent.setText(getString(R.string.for_you_progress_percent, percent));
        tvDoneCount.setText(getString(R.string.for_you_progress_done, doneCount));
        tvRemainingCount.setText(getString(R.string.for_you_progress_remaining, remainingCount));

        int todayCount = countTasksForToday(tasks);
        tvTaskCount.setText(getString(R.string.for_you_task_count_today, todayCount));
    }

    private int countTasksForToday(List<Task> tasks) {
        int count = 0;
        for (Task task : tasks) {
            if (isDueToday(task)) {
                count++;
            }
        }
        return count;
    }

    private void applyFilter(TaskFilter filter) {
        if (filter == null) {
            return;
        }

        // Nếu nhấn lại filter cũ, chỉ bộ đồng bộ lại UI (tránh bị uncheck do toggle)
        activeFilter = filter;
        updateFilterUi();

        // Kích hoạt animation cho danh sách công việc
        if (rvMyTasks != null) {
            rvMyTasks.setLayoutAnimation(android.view.animation.AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_fall_down));
            rvMyTasks.scheduleLayoutAnimation();
        }

        List<Task> filtered = new ArrayList<>();
        for (Task task : allTasks) {
            if (matchesFilter(task, filter)) {
                filtered.add(task);
            }
        }
        filtered.sort(this::compareTasksForDisplayOrder);
        taskAdapter.setTasks(filtered);
    }

    private int compareTasksForDisplayOrder(Task left, Task right) {
        int leftStatusRank = getStatusRank(left);
        int rightStatusRank = getStatusRank(right);
        if (leftStatusRank != rightStatusRank) {
            return Integer.compare(leftStatusRank, rightStatusRank);
        }

        int leftPriorityRank = getPriorityRank(left);
        int rightPriorityRank = getPriorityRank(right);
        if (leftPriorityRank != rightPriorityRank) {
            return Integer.compare(leftPriorityRank, rightPriorityRank);
        }

        LocalDate leftDueDate = parseIsoDate(left != null ? left.getDueDate() : null);
        LocalDate rightDueDate = parseIsoDate(right != null ? right.getDueDate() : null);
        if (leftDueDate != null && rightDueDate != null) {
            int byDueDate = leftDueDate.compareTo(rightDueDate);
            if (byDueDate != 0) {
                return byDueDate;
            }
        } else if (leftDueDate != null) {
            return -1;
        } else if (rightDueDate != null) {
            return 1;
        }

        return Long.compare(parseTaskCreatedTime(right), parseTaskCreatedTime(left));
    }

    private int getStatusRank(Task task) {
        if (task == null || task.getStatus() == null) {
            return 1;
        }
        String status = task.getStatus().trim().toUpperCase(Locale.US);
        if (status.contains("DOING") || status.contains("IN_PROGRESS") || status.contains("PROGRESS")) {
            return 0;
        }
        if (status.contains("DONE") || "COMPLETED".equals(status)) {
            return 2;
        }
        return 1;
    }

    private int getPriorityRank(Task task) {
        if (task == null || task.getPriority() == null) {
            return 3;
        }
        String priority = task.getPriority().trim().toUpperCase(Locale.US);
        switch (priority) {
            case "HIGH":
                return 0;
            case "MEDIUM":
                return 1;
            case "LOW":
                return 2;
            default:
                return 3;
        }
    }

    private long parseTaskCreatedTime(Task task) {
        if (task == null || TextUtils.isEmpty(task.getCreatedAt())) {
            return 0L;
        }
        try {
            return OffsetDateTime.parse(task.getCreatedAt()).toInstant().toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void updateFilterUi() {
        if (chipAll == null || chipToday == null || chipUpcoming == null) {
            return;
        }
        chipAll.setChecked(activeFilter == TaskFilter.ALL);
        chipToday.setChecked(activeFilter == TaskFilter.TODAY);
        chipUpcoming.setChecked(activeFilter == TaskFilter.UPCOMING);
    }

    private boolean matchesFilter(Task task, TaskFilter filter) {
        switch (filter) {
            case TODAY:
                return isDueToday(task);
            case UPCOMING:
                return isUpcoming(task);
            case ALL:
            default:
                return true;
        }
    }

    private boolean isDone(Task task) {
        String status = task.getStatus();
        if (status == null) {
            return false;
        }
        String normalized = status.toUpperCase(Locale.US);
        return normalized.contains("DONE") || "COMPLETED".equals(normalized);
    }

    private boolean isDueToday(Task task) {
        LocalDate dueDate = parseIsoDate(task.getDueDate());
        return dueDate != null && dueDate.equals(LocalDate.now());
    }

    private boolean isUpcoming(Task task) {
        LocalDate dueDate = parseIsoDate(task.getDueDate());
        return dueDate != null && dueDate.isAfter(LocalDate.now());
    }

    private LocalDate parseIsoDate(String value) {
        if (TextUtils.isEmpty(value)) {
            return null;
        }

        try {
            if (value.length() >= 10) {
                return LocalDate.parse(value.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
            }
        } catch (Exception ignored) {
        }

        try {
            return OffsetDateTime.parse(value).toLocalDate();
        } catch (Exception ignored) {
            return null;
        }
    }
}
