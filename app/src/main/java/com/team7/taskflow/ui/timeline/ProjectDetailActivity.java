package com.team7.taskflow.ui.timeline;

import android.content.Intent;
import android.text.TextUtils;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import com.team7.taskflow.R;
import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.ui.dashboard.DashboardActivity;
import com.team7.taskflow.ui.profile.ProfileActivity;
import com.team7.taskflow.data.repository.ProjectRepository;
import com.team7.taskflow.domain.model.ProjectActivity;
import com.team7.taskflow.ui.project.BoardFragment;
import com.team7.taskflow.ui.project.CalendarFragment;
import com.team7.taskflow.ui.project.ProjectOverviewFragment;
import com.team7.taskflow.ui.project.TaskListFragment;
import com.team7.taskflow.ui.project.TimelineFragment;
import com.team7.taskflow.ui.system.ShortcutRouterActivity;
import com.team7.taskflow.utils.NavigationUtils;
import com.team7.taskflow.utils.ProjectColorUtils;
import com.team7.taskflow.utils.SessionManager;

import java.util.List;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public class ProjectDetailActivity extends BaseActivity {

    public static final String EXTRA_INITIAL_TAB = "initial_tab";
    public static final String EXTRA_OPEN_TASK_ID = "open_task_id";
    public static final int INITIAL_TAB_TIMELINE = 3;

    private static final int TAB_OVERVIEW = 0;
    private static final int TAB_BOARD    = 1;
    private static final int TAB_LIST     = 2;
    private static final int TAB_TIMELINE = 3;
    private static final int TAB_CALENDAR = 4;

    private long projectId;
    private String projectName;
    private String projectKey;
    private String projectDesc;
    private String projectColor;
    private String currentUserId;
    private boolean isMyTasksMode;
    private boolean isViewer = false;
    private boolean isOwner = false;

    private LinearLayout tabOverview, tabBoard, tabList, tabTimeline, tabCalendar;
    private TextView tvProjectName, tvMonth;
    private BottomNavigationView bottomNavigationView;
    private View btnMore;
    private View btnProjectActivity;
    private TextView tvProjectActivityBadge;
    private boolean isBottomNavNavigating = false;
    private int currentTabIndex = -1;
    private int requestedInitialTab = TAB_OVERVIEW;
    private Long pendingOpenTaskId;
    private boolean openAiCreateFromShortcut;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_project_detail);

        projectId     = readLongExtraFlexible(getIntent(), "project_id", -1L);
        if (projectId <= 0) {
            projectId = readLongExtraFlexible(getIntent(), "projectId", -1L);
        }
        if (projectId <= 0) {
            projectId = readLongExtraFlexible(getIntent(), "id", -1L);
        }
        projectName   = getIntent().getStringExtra("project_name");
        projectKey    = getIntent().getStringExtra("project_key");
        projectDesc   = getIntent().getStringExtra("project_desc");
        projectColor  = getIntent().getStringExtra("project_color");
        isMyTasksMode = getIntent().getBooleanExtra("is_my_tasks", false);
        requestedInitialTab = getIntent().getIntExtra(EXTRA_INITIAL_TAB, TAB_OVERVIEW);
        if (getIntent().hasExtra(EXTRA_OPEN_TASK_ID)) {
            long taskId = getIntent().getLongExtra(EXTRA_OPEN_TASK_ID, -1);
            if (taskId > 0) {
                pendingOpenTaskId = taskId;
            }
        }
        openAiCreateFromShortcut = getIntent().getBooleanExtra(ShortcutRouterActivity.EXTRA_OPEN_AI_CREATE, false);

        SessionManager.init(this);
        currentUserId = SessionManager.getUserId();

        initViews();
        applyHeaderTint();
        setupNavigation();
        setupBottomNavigation();
        loadUserInfo();
        loadProjectTheme();
        refreshProjectActivityBadge();

        View header = findViewById(R.id.layoutHeader);
        if (header != null) {
            ViewCompat.setOnApplyWindowInsetsListener(header, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), sys.top, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }

        // Xử lý insets cho bottom bar: thêm padding bottom cho navigation bar
        View bottomBarContainer = findViewById(R.id.includeBottomBar);
        if (bottomBarContainer != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomBarContainer, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), sys.bottom);
                return insets;
            });
        }

        openTab(resolveInitialTab());
        triggerAiCreateIfRequested();
    }

    private void triggerAiCreateIfRequested() {
        if (!openAiCreateFromShortcut || projectId <= 0) {
            return;
        }
        openAiCreateFromShortcut = false;
        getIntent().removeExtra(ShortcutRouterActivity.EXTRA_OPEN_AI_CREATE);

        View container = findViewById(R.id.fragment_container);
        Runnable openTaskCreator = () -> {
            Intent intent = new Intent(ProjectDetailActivity.this, com.team7.taskflow.ui.ai.AiCreateActivity.class);
            intent.putExtra("project_id", projectId);
            intent.putExtra("project_name", projectName);
            intent.putExtra("project_key", projectKey);
            String prefillTitle = getIntent().getStringExtra("prefill_title");
            String prefillDescription = getIntent().getStringExtra("prefill_description");
            if (prefillTitle != null && !prefillTitle.trim().isEmpty()) {
                intent.putExtra("prefill_title", prefillTitle);
            }
            if (prefillDescription != null && !prefillDescription.trim().isEmpty()) {
                intent.putExtra("prefill_description", prefillDescription);
            }
            startActivity(intent);
        };

        if (container != null) {
            container.postDelayed(openTaskCreator, 180L);
        } else {
            openTaskCreator.run();
        }
    }

    private int resolveInitialTab() {
        if (requestedInitialTab == INITIAL_TAB_TIMELINE) {
            return TAB_TIMELINE;
        }
        return TAB_OVERVIEW;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // initViews
    // ─────────────────────────────────────────────────────────────────────────
    private void initViews() {
        tvProjectName = findViewById(R.id.tvProjectName);
        tvMonth       = findViewById(R.id.tvMonth);

        if (tvProjectName != null) {
            tvProjectName.setText(isMyTasksMode ? "My Assigned Tasks"
                    : (projectName != null ? projectName : "Project"));
        }
        if (tvMonth != null) {
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault());
            tvMonth.setText(sdf.format(java.util.Calendar.getInstance().getTime()));
        }

        tabOverview          = findViewById(R.id.tabOverview);
        tabBoard             = findViewById(R.id.tabBoard);
        tabList              = findViewById(R.id.tabList);
        tabTimeline          = findViewById(R.id.tabTimeline);
        tabCalendar          = findViewById(R.id.tabCalendar);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        btnProjectActivity   = findViewById(R.id.btnProjectActivity);
        tvProjectActivityBadge = findViewById(R.id.tvProjectActivityBadge);
        btnMore              = findViewById(R.id.btnMoreOptions);

        if (tvProjectActivityBadge != null) {
            tvProjectActivityBadge.setVisibility(View.GONE);
        }

        View fragmentContainer = findViewById(R.id.fragment_container);
        View bottomBar         = findViewById(R.id.includeBottomBar);
        if (bottomBar != null) {
            bottomBar.setVisibility(isMyTasksMode ? View.VISIBLE : View.GONE);
        }
        if (fragmentContainer != null && isMyTasksMode) {
            fragmentContainer.post(() -> {
                int bi = bottomBar != null ? bottomBar.getHeight() : 0;
                fragmentContainer.setPadding(
                        fragmentContainer.getPaddingLeft(),
                        fragmentContainer.getPaddingTop(),
                        fragmentContainer.getPaddingRight(), bi);
            });
        }

        View btnBack = findViewById(R.id.btnBack);
        positionCreateTaskButton();
        if (isMyTasksMode) {
            if (btnBack != null) btnBack.setVisibility(View.INVISIBLE);
            if (btnMore != null) btnMore.setVisibility(View.GONE);
        } else {
            if (btnBack != null) btnBack.setOnClickListener(v -> finish());
            if (btnMore != null) btnMore.setOnClickListener(v -> showProjectSettingsPanel());
        }

        if (btnProjectActivity != null) {
            btnProjectActivity.setVisibility(isMyTasksMode ? View.GONE : View.VISIBLE);
            btnProjectActivity.setEnabled(true);
            btnProjectActivity.setClickable(true);
            btnProjectActivity.setFocusable(true);
            btnProjectActivity.bringToFront();
            btnProjectActivity.setOnClickListener(this::onProjectActivityClick);
        }

        updateHeaderActionsForTab(TAB_OVERVIEW);
    }

    private void positionCreateTaskButton() {
        View fabAddAI = findViewById(R.id.fabAddAI);
        if (fabAddAI == null) {
            return;
        }

        View root = findViewById(R.id.rootLayout);
        if (root == null) {
            return;
        }

        root.post(() -> {
            int rootHeight = root.getHeight();
            if (rootHeight <= 0) {
                return;
            }
            View bottomBar = findViewById(R.id.includeBottomBar);
            int spacing = getResources().getDimensionPixelSize(R.dimen.spacing_lg);
            int bottomBarHeight = 0;
            if (bottomBar != null && bottomBar.getVisibility() == View.VISIBLE) {
                bottomBarHeight = bottomBar.getHeight();
            }

            int bottomInset = 0;
            WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(root);
            if (rootInsets != null) {
                bottomInset = rootInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            }

            ViewGroup.LayoutParams rawParams = fabAddAI.getLayoutParams();
            if (!(rawParams instanceof FrameLayout.LayoutParams)) {
                return;
            }

            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) rawParams;
            params.gravity = Gravity.BOTTOM | Gravity.END;
            int alignedBottomMargin = getResources().getDimensionPixelSize(R.dimen.spacing_xl) * 11;
            params.bottomMargin = Math.max((bottomBarHeight > 0 ? bottomBarHeight : bottomInset) + spacing,
                    alignedBottomMargin);
            params.setMarginEnd(spacing);
            fabAddAI.setLayoutParams(params);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navigation
    // ─────────────────────────────────────────────────────────────────────────
    private void setupNavigation() {
        if (tabOverview != null) tabOverview.setOnClickListener(v -> openTab(TAB_OVERVIEW));
        if (tabBoard    != null) tabBoard.setOnClickListener(v    -> openTab(TAB_BOARD));
        if (tabList     != null) tabList.setOnClickListener(v     -> openTab(TAB_LIST));
        if (tabTimeline != null) tabTimeline.setOnClickListener(v -> openTab(TAB_TIMELINE));
        if (tabCalendar != null) tabCalendar.setOnClickListener(v -> openTab(TAB_CALENDAR));

        View fabAddAI = findViewById(R.id.fabAddAI);
        if (fabAddAI != null) {
            fabAddAI.setVisibility(isMyTasksMode ? View.GONE : View.VISIBLE);
            fabAddAI.setOnClickListener(v -> {
                Intent i = new Intent(this, com.team7.taskflow.ui.ai.AiCreateActivity.class);
                i.putExtra("project_id", projectId);
                startActivity(i);
            });
        }
    }

    private void setupBottomNavigation() {
        if (!isMyTasksMode || bottomNavigationView == null) return;
        bottomNavigationView.getMenu().findItem(R.id.nav_tasks).setChecked(true);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (isBottomNavNavigating) return true;
            if (id == R.id.nav_tasks) return true;
            if (id == R.id.nav_home) {
                isBottomNavNavigating = true;
                    Intent i = new Intent(this, DashboardActivity.class);
                boolean started = NavigationUtils.startActivityWithNavAnimation(
                        this, i, NavigationUtils.NAV_TASKS, NavigationUtils.NAV_HOME);
                if (!started) {
                    isBottomNavNavigating = false;
                }
                return true;
            }
            if (id == R.id.nav_settings) {
                isBottomNavNavigating = true;
                    Intent i = new Intent(this, ProfileActivity.class);
                boolean started = NavigationUtils.startActivityWithNavAnimation(
                        this, i, NavigationUtils.NAV_TASKS, NavigationUtils.NAV_SETTINGS);
                if (!started) {
                    isBottomNavNavigating = false;
                }
                return true;
            }
            return id == R.id.nav_assistant;
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Role loading
    // ─────────────────────────────────────────────────────────────────────────
    private void loadUserInfo() {
        if (currentUserId == null || currentUserId.isEmpty()) return;

        com.team7.taskflow.data.remote.SupabaseClient.getInstance()
                .getService(com.team7.taskflow.data.remote.api.MemberApiService.class)
                .getMembers("eq." + projectId, "user_id,role")
                .enqueue(new retrofit2.Callback<List<com.team7.taskflow.domain.model.ProjectMember>>() {
                    @Override
                    public void onResponse(
                            @NonNull retrofit2.Call<List<com.team7.taskflow.domain.model.ProjectMember>> call,
                            @NonNull retrofit2.Response<List<com.team7.taskflow.domain.model.ProjectMember>> r) {
                        if (!r.isSuccessful() || r.body() == null) return;
                        for (com.team7.taskflow.domain.model.ProjectMember m : r.body()) {
                            if (currentUserId.equals(m.getUserId())) {
                                isViewer = m.isViewer();
                                isOwner = m.isOwner();
                                runOnUiThread(ProjectDetailActivity.this::applyRoleRestrictions);
                                break;
                            }
                        }
                    }

                    @Override
                    public void onFailure(
                            @NonNull retrofit2.Call<List<com.team7.taskflow.domain.model.ProjectMember>> call,
                            @NonNull Throwable t) {
                        Log.e("ProjectDetail", "Load role failed: " + t.getMessage());
                    }
                });
    }

    private void applyRoleRestrictions() {
        if (!isViewer) return;
        View fabAddAI = findViewById(R.id.fabAddAI);
        if (fabAddAI != null) fabAddAI.setVisibility(View.GONE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tab management
    // ─────────────────────────────────────────────────────────────────────────
    private void openTab(int idx) {
        if (idx == TAB_OVERVIEW && isMyTasksMode && tabOverview == null) return;

        Fragment fragment;
        String tag;
        LinearLayout activeTab;

        switch (idx) {
            case TAB_OVERVIEW:
                fragment  = ProjectOverviewFragment.newInstance(projectId, isMyTasksMode, currentUserId);
                tag       = "OVERVIEW"; activeTab = tabOverview; break;
            case TAB_BOARD:
                fragment  = BoardFragment.newInstance(projectId, isMyTasksMode, currentUserId);
                tag       = "BOARD";    activeTab = tabBoard;    break;
            case TAB_LIST:
                fragment  = TaskListFragment.newInstance(projectId, isMyTasksMode, currentUserId);
                tag       = "LIST";     activeTab = tabList;     break;
            case TAB_TIMELINE:
                fragment  = TimelineFragment.newInstance(projectId, isMyTasksMode, currentUserId);
                tag       = "TIMELINE"; activeTab = tabTimeline; break;
            case TAB_CALENDAR:
                fragment  = CalendarFragment.newInstance(projectId, isMyTasksMode, currentUserId);
                tag       = "CALENDAR"; activeTab = tabCalendar; break;
            default: return;
        }

        if (idx == currentTabIndex) {
            updateTabUI(activeTab);
            updateHeaderActionsForTab(idx);
            return;
        }
        switchFragment(fragment, tag, idx);
        updateTabUI(activeTab);
        updateHeaderActionsForTab(idx);
    }

    private void updateHeaderActionsForTab(int tabIndex) {
        if (btnProjectActivity != null)
            btnProjectActivity.setVisibility(isMyTasksMode ? View.GONE : View.VISIBLE);
        if (btnMore != null)
            btnMore.setVisibility(isMyTasksMode ? View.GONE : View.VISIBLE);
    }

    private void switchFragment(Fragment fragment, String tag, int idx) {
        androidx.fragment.app.FragmentTransaction tx =
                getSupportFragmentManager().beginTransaction();
        if (currentTabIndex != -1) {
            if (idx > currentTabIndex)
                tx.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left);
            else
                tx.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right);
        }
        tx.replace(R.id.fragment_container, fragment, tag);
        if (idx == TAB_TIMELINE && pendingOpenTaskId != null) {
            tx.runOnCommit(this::openPendingTaskDetailAfterTimeline);
        }
        tx.commit();
        currentTabIndex = idx;
    }

    private void openPendingTaskDetailAfterTimeline() {
        if (pendingOpenTaskId == null || pendingOpenTaskId <= 0) {
            return;
        }
        final long taskId = pendingOpenTaskId;
        pendingOpenTaskId = null;

        View container = findViewById(R.id.fragment_container);
        Runnable openTask = () -> {
            Intent intent = new Intent(ProjectDetailActivity.this, com.team7.taskflow.ui.project.TaskDetailActivity.class);
            intent.putExtra("project_id", projectId);
            intent.putExtra("task_id", taskId);
            startActivity(intent);
        };

        if (container != null) {
            container.postDelayed(openTask, 180L);
        } else {
            openTask.run();
        }
    }

    private void updateTabUI(LinearLayout activeTab) {
        resetTab(tabOverview); resetTab(tabBoard); resetTab(tabList);
        resetTab(tabTimeline); resetTab(tabCalendar);
        if (activeTab == null) return;
        ImageView icon = (ImageView) activeTab.getChildAt(0);
        TextView  text = (TextView)  activeTab.getChildAt(1);
        icon.setColorFilter(ContextCompat.getColor(this, R.color.primary));
        text.setTextColor(ContextCompat.getColor(this, R.color.primary));
        text.setTypeface(null, android.graphics.Typeface.BOLD);
        if (activeTab.getChildCount() > 2) activeTab.getChildAt(2).setVisibility(View.VISIBLE);
    }

    private void resetTab(LinearLayout tab) {
        if (tab == null) return;
        ImageView icon = (ImageView) tab.getChildAt(0);
        TextView  text = (TextView)  tab.getChildAt(1);
        int color = ContextCompat.getColor(this, R.color.theme_text_secondary);
        icon.setColorFilter(color);
        text.setTextColor(color);
        text.setTypeface(null, android.graphics.Typeface.NORMAL);
        if (tab.getChildCount() > 2) tab.getChildAt(2).setVisibility(View.GONE);
    }

    private void loadProjectTheme() {
        if (projectId <= 0) {
            return;
        }
        ProjectRepository.getInstance().getProjectById(projectId,
                new ProjectRepository.ProjectCallback<com.team7.taskflow.domain.model.Project>() {
                    @Override
                    public void onSuccess(com.team7.taskflow.domain.model.Project result) {
                        if (result == null) {
                            return;
                        }
                        runOnUiThread(() -> {
                            projectColor = result.getColor();
                            applyHeaderTint();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        // Keep fallback from intent/default color when project color cannot be loaded.
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshProjectActivityBadge();
    }

    private void refreshProjectActivityBadge() {
        if (tvProjectActivityBadge == null || projectId <= 0 || isMyTasksMode) {
            updateProjectActivityBadge(0);
            return;
        }

        ProjectRepository.getInstance().getProjectHistoryFeed(projectId,
                new ProjectRepository.ProjectCallback<List<com.team7.taskflow.domain.model.ProjectHistoryItem>>() {
                    @Override
                    public void onSuccess(List<com.team7.taskflow.domain.model.ProjectHistoryItem> result) {
                        runOnUiThread(() -> updateProjectActivityBadge(countTodayHistoryItems(result)));
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> updateProjectActivityBadge(0));
                    }
                });
    }

    private int countTodayHistoryItems(List<com.team7.taskflow.domain.model.ProjectHistoryItem> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        int count = 0;
        for (com.team7.taskflow.domain.model.ProjectHistoryItem item : items) {
            if (item == null) {
                continue;
            }
            LocalDate createdDate = extractHistoryDate(item.getCreatedAt());
            if (today.equals(createdDate)) {
                count++;
            }
        }
        return count;
    }

    private LocalDate extractHistoryDate(String createdAt) {
        if (TextUtils.isEmpty(createdAt)) {
            return null;
        }

        String normalized = createdAt.trim();
        try {
            return OffsetDateTime.parse(normalized)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDate();
        } catch (Exception ignored) {
        }

        try {
            return java.time.LocalDateTime.parse(normalized).toLocalDate();
        } catch (Exception ignored) {
        }

        try {
            if (normalized.length() >= 10) {
                return LocalDate.parse(normalized.substring(0, 10));
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private void updateProjectActivityBadge(int count) {
        if (tvProjectActivityBadge == null) {
            return;
        }

        if (count <= 0) {
            tvProjectActivityBadge.setVisibility(View.GONE);
            tvProjectActivityBadge.setText("");
            return;
        }

        tvProjectActivityBadge.setVisibility(View.VISIBLE);
        tvProjectActivityBadge.setText(count > 99 ? "99+" : String.valueOf(count));
    }

    private void applyHeaderTint() {
        View header = findViewById(R.id.layoutHeader);
        int baseColor = ProjectColorUtils.resolveBaseColor(this, projectColor);
        int headerTint = ProjectColorUtils.resolveHeaderTintColor(this, baseColor);
        int contentTint = ProjectColorUtils.resolveContentTintColor(this, baseColor);

        if (header != null) {
            header.setBackgroundColor(headerTint);
        }

        View fragmentContainer = findViewById(R.id.fragment_container);
        if (fragmentContainer != null) {
            fragmentContainer.setBackgroundColor(contentTint);
        }

        View rootLayout = findViewById(R.id.rootLayout);
        if (rootLayout != null) {
            rootLayout.setBackgroundColor(contentTint);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Project Settings Panel
    // ─────────────────────────────────────────────────────────────────────────
    private void showProjectSettingsPanel() {
        BottomSheetDialog bottomSheet =
                new BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View sheetView = getLayoutInflater()
                .inflate(R.layout.layout_project_settings_panel, null);
        bottomSheet.setContentView(sheetView);

        android.widget.FrameLayout bsl = bottomSheet
                .findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bsl != null) {
            com.google.android.material.bottomsheet.BottomSheetBehavior
                    .from(bsl)
                    .setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            com.google.android.material.bottomsheet.BottomSheetBehavior
                    .from(bsl).setSkipCollapsed(true);
        }

        android.widget.EditText etName  = sheetView.findViewById(R.id.etProjectName);
        android.widget.EditText etDesc  = sheetView.findViewById(R.id.etProjectDesc);
        TextView                tvKey   = sheetView.findViewById(R.id.tvProjectKey);
        android.widget.ImageView btnSave = sheetView.findViewById(R.id.btnSaveProject);

        if (etName != null && projectName != null) etName.setText(projectName);
        if (etDesc != null && projectDesc  != null) etDesc.setText(projectDesc);
        if (tvKey  != null) tvKey.setText(projectKey != null ? "KEY: " + projectKey : "N/A");

        if (btnSave != null) {
            btnSave.setOnClickListener(v -> {
                if (projectId == -1) return;
                String newName = etName.getText().toString().trim();
                String newDesc = etDesc.getText().toString().trim();
                if (newName.isEmpty()) {
                    Toast.makeText(this, "Tên dự án không được bỏ trống!", Toast.LENGTH_SHORT).show();
                    return;
                }
                com.team7.taskflow.domain.model.Project p =
                        new com.team7.taskflow.domain.model.Project();
                p.setName(newName);
                p.setDescription(newDesc);
                com.team7.taskflow.data.repository.ProjectRepository.getInstance()
                        .updateProject(projectId, p,
                                new com.team7.taskflow.data.repository.ProjectRepository
                                        .ProjectCallback<com.team7.taskflow.domain.model.Project>() {
                                    @Override
                                    public void onSuccess(com.team7.taskflow.domain.model.Project r) {
                                        runOnUiThread(() -> {
                                            projectName = newName;
                                            projectDesc = newDesc;
                                            tvProjectName.setText(newName);
                                            Toast.makeText(ProjectDetailActivity.this,
                                                    "Cập nhật dự án thành công!", Toast.LENGTH_SHORT).show();
                                            bottomSheet.dismiss();
                                        });
                                    }
                                    @Override
                                    public void onError(String error) {
                                        runOnUiThread(() -> Toast.makeText(
                                                ProjectDetailActivity.this, error, Toast.LENGTH_SHORT).show());
                                    }
                                });
            });
        }

        View btnMembers = sheetView.findViewById(R.id.btnManageMembers);
        if (btnMembers != null) {
            btnMembers.setOnClickListener(v -> {
                bottomSheet.dismiss();
                com.team7.taskflow.ui.member.MemberListBottomSheet sheet =
                        com.team7.taskflow.ui.member.MemberListBottomSheet.newInstance(projectId);
                sheet.show(getSupportFragmentManager(), "members");
            });
        }

        View btnProjectActivityHistory = sheetView.findViewById(R.id.btnProjectActivityHistory);
        if (btnProjectActivityHistory != null) {
            btnProjectActivityHistory.setOnClickListener(v -> {
                bottomSheet.dismiss();
                Intent intent = new Intent(ProjectDetailActivity.this,
                        com.team7.taskflow.ui.project.ProjectActivityHistoryActivity.class);
                intent.putExtra("project_id", projectId);
                intent.putExtra("project_name", projectName);
                startActivity(intent);
            });
        }

        View btnViewArchived = sheetView.findViewById(R.id.btnViewArchived);
        if (btnViewArchived != null) {
            btnViewArchived.setOnClickListener(v -> {
                bottomSheet.dismiss();
                Intent intent = new Intent(ProjectDetailActivity.this, com.team7.taskflow.ui.project.TrashActivity.class);
                intent.putExtra("project_id", projectId);
                intent.putExtra("project_name", projectName);
                intent.putExtra("is_my_tasks", isMyTasksMode);
                startActivity(intent);
            });
        }

        View btnDeleteProject = sheetView.findViewById(R.id.btnDeleteProject);
        if (btnDeleteProject != null) {
            btnDeleteProject.setVisibility(isOwner ? View.VISIBLE : View.GONE);
            btnDeleteProject.setOnClickListener(v -> {
                if (!isOwner) {
                    Toast.makeText(this, getString(R.string.project_delete_owner_only), Toast.LENGTH_SHORT).show();
                    return;
                }

                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(getString(R.string.project_delete_confirm_title))
                        .setMessage(getString(R.string.project_delete_confirm_message))
                        .setNegativeButton(getString(R.string.cancel), null)
                        .setPositiveButton(getString(R.string.delete), (dialog, which) -> {
                            com.team7.taskflow.data.repository.ProjectRepository.getInstance()
                                    .deleteProject(projectId,
                                            new com.team7.taskflow.data.repository.ProjectRepository.ProjectCallback<Void>() {
                                                @Override
                                                public void onSuccess(Void result) {
                                                    runOnUiThread(() -> {
                                                        Toast.makeText(ProjectDetailActivity.this,
                                                                getString(R.string.project_deleted_success),
                                                                Toast.LENGTH_SHORT).show();
                                                        bottomSheet.dismiss();
                                                        Intent intent = new Intent(ProjectDetailActivity.this,
                                                                DashboardActivity.class);
                                                        boolean started = NavigationUtils.startActivityWithNavAnimation(
                                                                ProjectDetailActivity.this,
                                                                intent,
                                                                NavigationUtils.NAV_TASKS,
                                                                NavigationUtils.NAV_HOME);
                                                        if (started) {
                                                            finish();
                                                        } else {
                                                            startActivity(intent);
                                                            finish();
                                                        }
                                                    });
                                                }

                                                @Override
                                                public void onError(String error) {
                                                    runOnUiThread(() -> Toast.makeText(
                                                            ProjectDetailActivity.this,
                                                            error,
                                                            Toast.LENGTH_SHORT).show());
                                                }
                                            });
                        })
                        .show();
            });
        }

        View btnCollapse = sheetView.findViewById(R.id.btnCollapse);
        if (btnCollapse != null) btnCollapse.setOnClickListener(v -> bottomSheet.dismiss());

        bottomSheet.show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Project Activity History (của đồng đội)
    // ─────────────────────────────────────────────────────────────────────────
    private void onProjectActivityClick(View view) {
        openProjectActivityHistory();
    }

    private void openProjectActivityHistory() {
        long resolvedProjectId = resolveProjectIdForHistory();
        if (resolvedProjectId <= 0) {
            Toast.makeText(this, "Không tìm thấy project id", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this,
                com.team7.taskflow.ui.project.ProjectActivityHistoryActivity.class);
        intent.putExtra("project_id", resolvedProjectId);
        intent.putExtra("project_name", projectName);
        startActivity(intent);
    }

    private long resolveProjectIdForHistory() {
        if (projectId > 0) {
            return projectId;
        }

        long fromIntent = readLongExtraFlexible(getIntent(), "project_id", -1L);
        if (fromIntent > 0) {
            projectId = fromIntent;
            return fromIntent;
        }

        fromIntent = readLongExtraFlexible(getIntent(), "projectId", -1L);
        if (fromIntent > 0) {
            projectId = fromIntent;
            return fromIntent;
        }

        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (current != null && current.getArguments() != null) {
            long fromFragment = current.getArguments().getLong("project_id", -1L);
            if (fromFragment > 0) {
                projectId = fromFragment;
                return fromFragment;
            }
        }

        return -1L;
    }

    private long readLongExtraFlexible(Intent intent, String key, long defaultValue) {
        if (intent == null || key == null || key.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            Object raw = intent.getExtras() != null ? intent.getExtras().get(key) : null;
            if (raw instanceof Number) {
                return ((Number) raw).longValue();
            }
            if (raw instanceof String) {
                return Long.parseLong(((String) raw).trim());
            }
        } catch (Exception ignored) {
            // Fall through to regular getLongExtra.
        }
        return intent.getLongExtra(key, defaultValue);
    }
}