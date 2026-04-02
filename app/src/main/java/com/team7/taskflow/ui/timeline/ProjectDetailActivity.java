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

    private static final int TAB_OVERVIEW = 0;
    private static final int TAB_BOARD = 1;
    private static final int TAB_LIST = 2;
    private static final int TAB_TIMELINE = 3;
    private static final int TAB_CALENDAR = 4;

    private long projectId;
    private String projectName;
    private String projectKey;
    private String projectDesc;
    private String currentUserId;
    private boolean isMyTasksMode;

    private LinearLayout tabOverview, tabBoard, tabList, tabTimeline, tabCalendar;
    private TextView tvProjectName, tvMonth;
    private BottomNavigationView bottomNavigationView;
    private View btnProjectActivity;
    private View btnMore;
    private int currentTabIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_project_detail);

        projectId = getIntent().getLongExtra("project_id", -1);
        projectName = getIntent().getStringExtra("project_name");
        projectKey = getIntent().getStringExtra("project_key");
        projectDesc = getIntent().getStringExtra("project_desc");
        isMyTasksMode = getIntent().getBooleanExtra("is_my_tasks", false);

        SessionManager.init(this);
        currentUserId = SessionManager.getUserId();

        initViews();
        setupNavigation();
        setupBottomNavigation();
        loadUserInfo();

        // Handle insets for status bar extension
        View header = findViewById(R.id.layoutHeader);
        if (header != null) {
            ViewCompat.setOnApplyWindowInsetsListener(header, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }

        // Always open Overview tab first (both for projects and My Assigned Tasks)
        openTab(TAB_OVERVIEW);
    }

    private void initViews() {
        tvProjectName = findViewById(R.id.tvProjectName);
        tvMonth = findViewById(R.id.tvMonth);

        if (tvProjectName != null) {
            if (isMyTasksMode) {
                tvProjectName.setText("My Assigned Tasks");
            } else {
                tvProjectName.setText(projectName != null ? projectName : "Project");
            }
        }
        
        if (tvMonth != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault());
            tvMonth.setText(sdf.format(java.util.Calendar.getInstance().getTime()));
        }

        tabOverview = findViewById(R.id.tabOverview);
        tabBoard = findViewById(R.id.tabBoard);
        tabList = findViewById(R.id.tabList);
        tabTimeline = findViewById(R.id.tabTimeline);
        tabCalendar = findViewById(R.id.tabCalendar);
        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        btnProjectActivity = findViewById(R.id.btnProjectActivity);
        btnMore = findViewById(R.id.btnMoreOptions);

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
            if (btnProjectActivity != null) btnProjectActivity.setVisibility(View.GONE);
            if (btnMore != null) btnMore.setVisibility(View.GONE);
        } else {
            if (btnBack != null) btnBack.setOnClickListener(v -> finish());
            if (btnProjectActivity != null) {
                btnProjectActivity.setOnClickListener(v -> openProjectActivityHistory());
            }
            if (btnMore != null) btnMore.setOnClickListener(v -> showProjectSettingsPanel());
        }

        updateHeaderActionsForTab(TAB_OVERVIEW);
    }

    private void setupNavigation() {
        if (tabOverview != null) {
            tabOverview.setOnClickListener(v -> openTab(TAB_OVERVIEW));
        }

        if (tabBoard != null) {
            tabBoard.setOnClickListener(v -> openTab(TAB_BOARD));
        }

        if (tabList != null) {
            tabList.setOnClickListener(v -> openTab(TAB_LIST));
        }

        if (tabTimeline != null) {
            tabTimeline.setOnClickListener(v -> openTab(TAB_TIMELINE));
        }

        if (tabCalendar != null) {
            tabCalendar.setOnClickListener(v -> openTab(TAB_CALENDAR));
        }

        View fabAddAI = findViewById(R.id.fabAddAI);
        if (fabAddAI != null) {
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
            if (id == R.id.nav_tasks) {
                return true;
            }
            if (id == R.id.nav_home) {
                Intent intent = new Intent(this, DashboardActivity.class);
                NavigationUtils.startActivityWithNavAnimation(this, intent, NavigationUtils.NAV_TASKS, NavigationUtils.NAV_HOME);
                finish();
                return true;
            }
            if (id == R.id.nav_settings) {
                Intent intent = new Intent(this, ProfileActivity.class);
                NavigationUtils.startActivityWithNavAnimation(this, intent, NavigationUtils.NAV_TASKS, NavigationUtils.NAV_SETTINGS);
                finish();
                return true;
            }
            return id == R.id.nav_assistant;
        });
    }

    private void loadUserInfo() {
        if (currentUserId == null || currentUserId.isEmpty()) return;
        
        com.team7.taskflow.data.remote.SupabaseClient.getInstance()
            .getService(com.team7.taskflow.data.remote.api.UserApi.class)
            .getUserById("eq." + currentUserId, "*")
            .enqueue(new retrofit2.Callback<List<com.team7.taskflow.domain.model.User>>() {
                @Override
                public void onResponse(@NonNull retrofit2.Call<List<com.team7.taskflow.domain.model.User>> call, @NonNull retrofit2.Response<List<com.team7.taskflow.domain.model.User>> response) {
                    if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                        com.team7.taskflow.domain.model.User user = response.body().get(0);
                        // User info loaded but no longer needed for top bar avatar
                    }
                }
                @Override
                public void onFailure(@NonNull retrofit2.Call<List<com.team7.taskflow.domain.model.User>> call, @NonNull Throwable t) {
                    Log.e("ProjectDetail", "Load user failed: " + t.getMessage());
                }
            });
    }

    private void openTab(int targetTabIndex) {
        if (targetTabIndex == TAB_OVERVIEW && isMyTasksMode && tabOverview == null) {
            return;
        }

        Fragment fragment;
        String tag;
        LinearLayout activeTab;

        switch (targetTabIndex) {
            case TAB_OVERVIEW:
                fragment = ProjectOverviewFragment.newInstance(projectId, isMyTasksMode, currentUserId);
                tag = "OVERVIEW";
                activeTab = tabOverview;
                break;
            case TAB_BOARD:
                fragment = BoardFragment.newInstance(projectId, isMyTasksMode, currentUserId);
                tag = "BOARD";
                activeTab = tabBoard;
                break;
            case TAB_LIST:
                fragment = TaskListFragment.newInstance(projectId, isMyTasksMode, currentUserId);
                tag = "LIST";
                activeTab = tabList;
                break;
            case TAB_TIMELINE:
                fragment = TimelineFragment.newInstance(projectId, isMyTasksMode, currentUserId);
                tag = "TIMELINE";
                activeTab = tabTimeline;
                break;
            case TAB_CALENDAR:
                fragment = CalendarFragment.newInstance(projectId, isMyTasksMode, currentUserId);
                tag = "CALENDAR";
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
        if (btnProjectActivity != null) {
            btnProjectActivity.setVisibility(isMyTasksMode ? View.GONE : View.VISIBLE);
        }
        if (btnMore != null) {
            if (isMyTasksMode) {
                btnMore.setVisibility(View.GONE);
            } else {
                btnMore.setVisibility(View.VISIBLE);
            }
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
        TextView text = (TextView) activeTab.getChildAt(1);
        
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
        TextView text = (TextView) tab.getChildAt(1);
        
        icon.setColorFilter(Color.parseColor("#888888"));
        text.setTextColor(Color.parseColor("#888888"));
        text.setTypeface(null, android.graphics.Typeface.NORMAL);
        
        if (tab.getChildCount() > 2) {
            tab.getChildAt(2).setVisibility(View.GONE);
        }
    }

    private void showProjectSettingsPanel() {
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
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
        TextView tvProjectKey = sheetView.findViewById(R.id.tvProjectKey);
        android.widget.ImageView btnSaveProject = sheetView.findViewById(R.id.btnSaveProject);

        if (etProjectName != null && projectName != null)
            etProjectName.setText(projectName);
        if (etProjectDesc != null && projectDesc != null)
            etProjectDesc.setText(projectDesc);
        if (tvProjectKey != null)
            tvProjectKey.setText(projectKey != null ? "KEY: " + projectKey : "N/A");

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
                        new com.team7.taskflow.data.repository.ProjectRepository.ProjectCallback<com.team7.taskflow.domain.model.Project>() {
                    @Override
                    public void onSuccess(com.team7.taskflow.domain.model.Project result) {
                        runOnUiThread(() -> {
                            projectName = newName;
                            projectDesc = newDesc;
                            tvProjectName.setText(newName);
                            Toast.makeText(ProjectDetailActivity.this, "Cập nhật dự án thành công!", Toast.LENGTH_SHORT).show();
                            bottomSheet.dismiss();
                        });
                    }
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> Toast.makeText(ProjectDetailActivity.this, error, Toast.LENGTH_SHORT).show());
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

        View btnDeleteProject = sheetView.findViewById(R.id.btnDeleteProject);
        if (btnDeleteProject != null) {
            btnDeleteProject.setOnClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Delete Project")
                        .setMessage("Are you sure you want to delete this project?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            com.team7.taskflow.data.repository.ProjectRepository.getInstance().deleteProject(
                                    projectId,
                                    new com.team7.taskflow.data.repository.ProjectRepository.ProjectCallback<Void>() {
                                        @Override
                                        public void onSuccess(Void result) {
                                            runOnUiThread(() -> {
                                                Toast.makeText(ProjectDetailActivity.this, "Project deleted", Toast.LENGTH_SHORT).show();
                                                bottomSheet.dismiss();
                                                finish();
                                            });
                                        }

                                        @Override
                                        public void onError(String error) {
                                            runOnUiThread(() -> Toast.makeText(ProjectDetailActivity.this, error, Toast.LENGTH_SHORT).show());
                                        }
                                    });
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .show();
            });
        }

        View btnViewArchived = sheetView.findViewById(R.id.btnViewArchived);
        if (btnViewArchived != null) {
            btnViewArchived.setOnClickListener(v -> {
                bottomSheet.dismiss();
                Intent intent = new Intent(this, com.team7.taskflow.ui.project.TrashActivity.class);
                startActivity(intent);
            });
        }

        View btnCollapse = sheetView.findViewById(R.id.btnCollapse);
        if (btnCollapse != null) {
            btnCollapse.setOnClickListener(v -> bottomSheet.dismiss());
        }

        bottomSheet.show();
    }

    private void openProjectActivityHistory() {
        if (projectId <= 0) {
            Toast.makeText(this, "Project not found", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, com.team7.taskflow.ui.project.ProjectActivityHistoryActivity.class);
        intent.putExtra("project_id", projectId);
        intent.putExtra("project_name", projectName);
        startActivity(intent);
    }
}
