package com.team7.taskflow.ui.timeline;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
import com.team7.taskflow.ui.project.BoardFragment;
import com.team7.taskflow.ui.project.CalendarFragment;
import com.team7.taskflow.ui.project.ProjectOverviewFragment;
import com.team7.taskflow.ui.project.TaskListFragment;
import com.team7.taskflow.ui.project.TimelineFragment;
import com.team7.taskflow.utils.SessionManager;
import com.team7.taskflow.utils.NavigationUtils;

import java.util.List;

public class ProjectDetailActivity extends BaseActivity {

    private static final int TAB_OVERVIEW  = 0;
    private static final int TAB_BOARD     = 1;
    private static final int TAB_LIST      = 2;
    private static final int TAB_TIMELINE  = 3;
    private static final int TAB_CALENDAR  = 4;

    private long projectId;
    private String projectName;
    private String projectKey;
    private String projectDesc;
    private String currentUserId;
    private boolean isMyTasksMode;

    // ✅ FIX: Thêm field lưu role của user hiện tại
    private boolean isViewer = false;

    private LinearLayout tabOverview, tabBoard, tabList, tabTimeline, tabCalendar;
    private TextView tvProjectName, tvMonth;
    private BottomNavigationView bottomNavigationView;
    private View btnTrash;
    private View btnMore;
    private View btnProjectActivity; // ✅ Thêm của đồng đội
    private int currentTabIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_project_detail);

        projectId = getIntent().getLongExtra("project_id", -1);
        projectName = getIntent().getStringExtra("project_name");
        projectKey  = getIntent().getStringExtra("project_key");
        projectDesc = getIntent().getStringExtra("project_desc");
        isMyTasksMode = getIntent().getBooleanExtra("is_my_tasks", false);

        SessionManager.init(this);
        currentUserId = SessionManager.getUserId();

        initViews();
        setupNavigation();
        setupBottomNavigation();
        loadUserInfo(); // ✅ Load role → ẩn/hiện FAB

        View header = findViewById(R.id.layoutHeader);
        if (header != null) {
            ViewCompat.setOnApplyWindowInsetsListener(header, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }

        openTab(TAB_OVERVIEW);
    }

    private void initViews() {
        tvProjectName = findViewById(R.id.tvProjectName);
        tvMonth       = findViewById(R.id.tvMonth);

        if (tvProjectName != null) {
            tvProjectName.setText(isMyTasksMode ? "My Assigned Tasks"
                    : (projectName != null ? projectName : "Project"));
        }

        if (tvMonth != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "MMMM yyyy", java.util.Locale.getDefault());
            tvMonth.setText(sdf.format(java.util.Calendar.getInstance().getTime()));
        }

        tabOverview        = findViewById(R.id.tabOverview);
        tabBoard           = findViewById(R.id.tabBoard);
        tabList            = findViewById(R.id.tabList);
        tabTimeline        = findViewById(R.id.tabTimeline);
        tabCalendar        = findViewById(R.id.tabCalendar);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        btnTrash           = findViewById(R.id.btnTrash);
        btnProjectActivity = findViewById(R.id.btnProjectActivity); // ✅ Thêm của đồng đội
        btnMore            = findViewById(R.id.btnMoreOptions);

        View fragmentContainer = findViewById(R.id.fragment_container);
        View bottomBar = findViewById(R.id.includeBottomBar);
        if (bottomBar != null) {
            bottomBar.setVisibility(isMyTasksMode ? View.VISIBLE : View.GONE);
        }

        if (fragmentContainer != null && isMyTasksMode) {
            fragmentContainer.post(() -> {
                int bottomInset = bottomBar != null ? bottomBar.getHeight() : 0;
                fragmentContainer.setPadding(
                        fragmentContainer.getPaddingLeft(),
                        fragmentContainer.getPaddingTop(),
                        fragmentContainer.getPaddingRight(),
                        bottomInset);
            });
        }

        View btnBack = findViewById(R.id.btnBack);
        if (isMyTasksMode) {
            if (btnBack != null) btnBack.setVisibility(View.INVISIBLE);
            if (btnMore != null) btnMore.setVisibility(View.GONE);
        } else {
            if (btnBack != null) btnBack.setOnClickListener(v -> finish());
            if (btnMore != null) btnMore.setOnClickListener(v -> showProjectSettingsPanel());
        }

        if (btnTrash != null) {
            btnTrash.setOnClickListener(v -> {
                Fragment listFragment = getSupportFragmentManager().findFragmentByTag("LIST");
                if (listFragment instanceof TaskListFragment) {
                    ((TaskListFragment) listFragment).openTrashFromHeader();
                } else {
                    openTab(TAB_LIST);
                    findViewById(R.id.fragment_container).post(() -> {
                        Fragment readyFragment = getSupportFragmentManager().findFragmentByTag("LIST");
                        if (readyFragment instanceof TaskListFragment) {
                            ((TaskListFragment) readyFragment).openTrashFromHeader();
                        }
                    });
                }
            });
        }

        updateHeaderActionsForTab(TAB_OVERVIEW);
    }

    private void setupNavigation() {
        if (tabOverview  != null) tabOverview.setOnClickListener(v  -> openTab(TAB_OVERVIEW));
        if (tabBoard     != null) tabBoard.setOnClickListener(v     -> openTab(TAB_BOARD));
        if (tabList      != null) tabList.setOnClickListener(v      -> openTab(TAB_LIST));
        if (tabTimeline  != null) tabTimeline.setOnClickListener(v  -> openTab(TAB_TIMELINE));
        if (tabCalendar  != null) tabCalendar.setOnClickListener(v  -> openTab(TAB_CALENDAR));

        View fabAddAI = findViewById(R.id.fabAddAI);
        if (fabAddAI != null) {
            // ✅ Mặc định ẩn nếu isMyTasksMode, sẽ được cập nhật lại sau khi loadUserInfo() xong
            fabAddAI.setVisibility(isMyTasksMode ? View.GONE : View.VISIBLE);
            fabAddAI.setOnClickListener(v -> {
                Intent aiIntent = new Intent(this, com.team7.taskflow.ui.ai.AiCreateActivity.class);
                aiIntent.putExtra("project_id", projectId);
                startActivity(aiIntent);
            });
        }
    }

    private void setupBottomNavigation() {
        if (!isMyTasksMode || bottomNavigationView == null) return;
        bottomNavigationView.setItemIconTintList(null);
        bottomNavigationView.setSelectedItemId(R.id.nav_tasks);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_tasks)  return true;
            if (id == R.id.nav_home) {
                Intent intent = new Intent(this, DashboardActivity.class);
                NavigationUtils.startActivityWithNavAnimation(this, intent,
                        NavigationUtils.NAV_TASKS, NavigationUtils.NAV_HOME);
                finish();
                return true;
            }
            if (id == R.id.nav_settings) {
                Intent intent = new Intent(this, ProfileActivity.class);
                NavigationUtils.startActivityWithNavAnimation(this, intent,
                        NavigationUtils.NAV_TASKS, NavigationUtils.NAV_SETTINGS);
                finish();
                return true;
            }
            return id == R.id.nav_assistant;
        });
    }

    private void loadUserInfo() {
        if (currentUserId == null || currentUserId.isEmpty()) return;

        // ✅ FIX: Query role của user hiện tại trong project này
        com.team7.taskflow.data.remote.SupabaseClient.getInstance()
                .getService(com.team7.taskflow.data.remote.api.MemberApiService.class)
                .getMembers("eq." + projectId, "user_id,role")
                .enqueue(new retrofit2.Callback<List<com.team7.taskflow.domain.model.ProjectMember>>() {
                    @Override
                    public void onResponse(
                            @NonNull retrofit2.Call<List<com.team7.taskflow.domain.model.ProjectMember>> call,
                            @NonNull retrofit2.Response<List<com.team7.taskflow.domain.model.ProjectMember>> response) {

                        if (!response.isSuccessful() || response.body() == null) return;

                        for (com.team7.taskflow.domain.model.ProjectMember m : response.body()) {
                            if (currentUserId.equals(m.getUserId())) {
                                isViewer = m.isViewer();
                                runOnUiThread(() -> applyRoleRestrictions());
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

    // ✅ FIX: Ẩn FAB nếu là Viewer
    private void applyRoleRestrictions() {
        if (!isViewer) return; // OWNER / ADMIN / MEMBER → không đụng gì

        // Viewer không được tạo task → ẩn FAB
        View fabAddAI = findViewById(R.id.fabAddAI);
        if (fabAddAI != null) {
            fabAddAI.setVisibility(View.GONE);
        }

        // Viewer không được xóa task → ẩn nút thùng rác
        if (btnTrash != null) {
            btnTrash.setVisibility(View.GONE);
        }
    }

    private void openTab(int targetTabIndex) {
        if (targetTabIndex == TAB_OVERVIEW && isMyTasksMode && tabOverview == null) return;

        Fragment fragment;
        String tag;
        LinearLayout activeTab;

        switch (targetTabIndex) {
            case TAB_OVERVIEW:
                fragment  = ProjectOverviewFragment.newInstance(projectId, isMyTasksMode, currentUserId);
                tag       = "OVERVIEW";
                activeTab = tabOverview;
                break;
            case TAB_BOARD:
                fragment  = BoardFragment.newInstance(projectId, isMyTasksMode, currentUserId);
                tag       = "BOARD";
                activeTab = tabBoard;
                break;
            case TAB_LIST:
                fragment  = TaskListFragment.newInstance(projectId, isMyTasksMode, currentUserId);
                tag       = "LIST";
                activeTab = tabList;
                break;
            case TAB_TIMELINE:
                fragment  = TimelineFragment.newInstance(projectId, isMyTasksMode, currentUserId);
                tag       = "TIMELINE";
                activeTab = tabTimeline;
                break;
            case TAB_CALENDAR:
                fragment  = CalendarFragment.newInstance(projectId, isMyTasksMode, currentUserId);
                tag       = "CALENDAR";
                activeTab = tabCalendar;
                break;
            default:
                return;
        }

        if (targetTabIndex == currentTabIndex) {
            updateTabUI(activeTab);
            updateHeaderActionsForTab(targetTabIndex);
            return;
        }

        switchFragment(fragment, tag, targetTabIndex);
        updateTabUI(activeTab);
        updateHeaderActionsForTab(targetTabIndex);
    }

    private void updateHeaderActionsForTab(int tabIndex) {
        if (btnTrash != null) {
            // ✅ Viewer không thấy nút thùng rác
            btnTrash.setVisibility(isViewer ? View.GONE : View.VISIBLE);
        }
        if (btnProjectActivity != null) {
            // ✅ Thêm của đồng đội
            btnProjectActivity.setVisibility(isMyTasksMode ? View.GONE : View.VISIBLE);
        }
        if (btnMore != null) {
            btnMore.setVisibility(isMyTasksMode ? View.GONE : View.VISIBLE);
        }
    }

    private void switchFragment(Fragment fragment, String tag, int targetTabIndex) {
        androidx.fragment.app.FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        if (currentTabIndex != -1) {
            if (targetTabIndex > currentTabIndex) {
                tx.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left);
            } else {
                tx.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right);
            }
        }
        tx.replace(R.id.fragment_container, fragment, tag).commit();
        currentTabIndex = targetTabIndex;
    }

    private void updateTabUI(LinearLayout activeTab) {
        resetTab(tabOverview);
        resetTab(tabBoard);
        resetTab(tabList);
        resetTab(tabTimeline);
        resetTab(tabCalendar);

        if (activeTab == null) return;

        ImageView icon = (ImageView) activeTab.getChildAt(0);
        TextView  text = (TextView)  activeTab.getChildAt(1);
        icon.setColorFilter(ContextCompat.getColor(this, R.color.primary));
        text.setTextColor(ContextCompat.getColor(this, R.color.primary));
        text.setTypeface(null, android.graphics.Typeface.BOLD);
        if (activeTab.getChildCount() > 2) {
            activeTab.getChildAt(2).setVisibility(View.VISIBLE);
        }
    }

    private void resetTab(LinearLayout tab) {
        if (tab == null) return;
        ImageView icon = (ImageView) tab.getChildAt(0);
        TextView  text = (TextView)  tab.getChildAt(1);
        icon.setColorFilter(Color.parseColor("#888888"));
        text.setTextColor(Color.parseColor("#888888"));
        text.setTypeface(null, android.graphics.Typeface.NORMAL);
        if (tab.getChildCount() > 2) {
            tab.getChildAt(2).setVisibility(View.GONE);
        }
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

        android.widget.EditText etProjectName = sheetView.findViewById(R.id.etProjectName);
        android.widget.EditText etProjectDesc = sheetView.findViewById(R.id.etProjectDesc);
        TextView tvProjectKey                 = sheetView.findViewById(R.id.tvProjectKey);
        android.widget.ImageView btnSaveProject = sheetView.findViewById(R.id.btnSaveProject);

        if (etProjectName != null && projectName != null) etProjectName.setText(projectName);
        if (etProjectDesc != null && projectDesc  != null) etProjectDesc.setText(projectDesc);
        if (tvProjectKey  != null) tvProjectKey.setText(projectKey != null ? "KEY: " + projectKey : "N/A");

        if (btnSaveProject != null) {
            btnSaveProject.setOnClickListener(v -> {
                if (projectId == -1) return;
                String newName = etProjectName.getText().toString().trim();
                String newDesc = etProjectDesc.getText().toString().trim();
                if (newName.isEmpty()) {
                    Toast.makeText(this, "Tên dự án không được bỏ trống!", Toast.LENGTH_SHORT).show();
                    return;
                }
                com.team7.taskflow.domain.model.Project updateP = new com.team7.taskflow.domain.model.Project();
                updateP.setName(newName);
                updateP.setDescription(newDesc);

                com.team7.taskflow.data.repository.ProjectRepository.getInstance().updateProject(
                        projectId, updateP,
                        new com.team7.taskflow.data.repository.ProjectRepository.ProjectCallback<
                                com.team7.taskflow.domain.model.Project>() {
                            @Override
                            public void onSuccess(com.team7.taskflow.domain.model.Project result) {
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

        View btnManageMembers = sheetView.findViewById(R.id.btnManageMembers);
        if (btnManageMembers != null) {
            btnManageMembers.setOnClickListener(v -> {
                bottomSheet.dismiss();
                com.team7.taskflow.ui.member.MemberListBottomSheet sheet =
                        com.team7.taskflow.ui.member.MemberListBottomSheet.newInstance(projectId);
                sheet.show(getSupportFragmentManager(), "members");
            });
        }

        // ✅ Thêm của đồng đội
        private void openProjectActivityHistory() {
            if (projectId <= 0) {
                Toast.makeText(this, "Project not found", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this,
                    com.team7.taskflow.ui.project.ProjectActivityHistoryActivity.class);
            intent.putExtra("project_id", projectId);
            intent.putExtra("project_name", projectName);
            startActivity(intent);
        }
    }