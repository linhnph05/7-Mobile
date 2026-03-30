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

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.team7.taskflow.R;
import com.team7.taskflow.ui.base.BaseActivity;
import com.team7.taskflow.ui.project.BoardFragment;
import com.team7.taskflow.ui.project.ProjectOverviewFragment;
import com.team7.taskflow.ui.project.TimelineFragment;
import com.team7.taskflow.utils.SessionManager;

import java.util.List;

public class TimelineActivity extends BaseActivity {

    private long projectId;
    private String projectName;
    private String projectKey;
    private String projectDesc;
    private String currentUserId;

    private LinearLayout tabOverview, tabBoard, tabTimeline, tabCalendar;
    private TextView tvProjectName, tvMonth;
    private ImageView imgUserAvatar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_timeline);

        projectId = getIntent().getLongExtra("project_id", -1);
        projectName = getIntent().getStringExtra("project_name");
        projectKey = getIntent().getStringExtra("project_key");
        projectDesc = getIntent().getStringExtra("project_desc");

        SessionManager.init(this);
        currentUserId = SessionManager.getUserId();

        initViews();
        setupNavigation();
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

        // Mặc định mở Overview
        switchFragment(ProjectOverviewFragment.newInstance(projectId), "OVERVIEW");
        updateTabUI(tabOverview);
    }

    private void initViews() {
        tvProjectName = findViewById(R.id.tvProjectName);
        tvMonth = findViewById(R.id.tvMonth);
        imgUserAvatar = findViewById(R.id.imgUserAvatar);

        if (tvProjectName != null) tvProjectName.setText(projectName != null ? projectName : "Project");
        
        if (tvMonth != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault());
            tvMonth.setText(sdf.format(java.util.Calendar.getInstance().getTime()));
        }

        tabOverview = findViewById(R.id.tabOverview);
        tabBoard = findViewById(R.id.tabBoard);
        tabTimeline = findViewById(R.id.tabTimeline);
        tabCalendar = findViewById(R.id.tabCalendar);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnMoreOptions).setOnClickListener(v -> showProjectSettingsPanel());
    }

    private void setupNavigation() {
        if (tabOverview != null) {
            tabOverview.setOnClickListener(v -> {
                switchFragment(ProjectOverviewFragment.newInstance(projectId), "OVERVIEW");
                updateTabUI(tabOverview);
            });
        }

        if (tabBoard != null) {
            tabBoard.setOnClickListener(v -> {
                switchFragment(BoardFragment.newInstance(projectId), "BOARD");
                updateTabUI(tabBoard);
            });
        }

        if (tabTimeline != null) {
            tabTimeline.setOnClickListener(v -> {
                switchFragment(TimelineFragment.newInstance(projectId), "TIMELINE");
                updateTabUI(tabTimeline);
            });
        }

        if (tabCalendar != null) {
            tabCalendar.setOnClickListener(v -> {
                Toast.makeText(this, "Calendar coming soon", Toast.LENGTH_SHORT).show();
                updateTabUI(tabCalendar);
            });
        }

        View fabAddAI = findViewById(R.id.fabAddAI);
        if (fabAddAI != null) {
            fabAddAI.setOnClickListener(v -> {
                Intent aiIntent = new Intent(this, com.team7.taskflow.ui.ai.AiCreateActivity.class);
                aiIntent.putExtra("project_id", projectId);
                startActivity(aiIntent);
            });
        }
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
                public void onFailure(@NonNull retrofit2.Call<List<com.team7.taskflow.domain.model.User>> call, @NonNull Throwable t) {
                    Log.e("Timeline", "Load user failed: " + t.getMessage());
                }
            });
    }

    private void switchFragment(Fragment fragment, String tag) {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.fragment_container, fragment, tag)
                .commit();
    }

    private void updateTabUI(LinearLayout activeTab) {
        resetTab(tabOverview);
        resetTab(tabBoard);
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
                            Toast.makeText(TimelineActivity.this, "Cập nhật dự án thành công!", Toast.LENGTH_SHORT).show();
                            bottomSheet.dismiss();
                        });
                    }
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> Toast.makeText(TimelineActivity.this, error, Toast.LENGTH_SHORT).show());
                    }
                });
            });
        }

        View btnManageMembers = sheetView.findViewById(R.id.btnManageMembers);
        if (btnManageMembers != null) {
            btnManageMembers.setOnClickListener(v -> {
                bottomSheet.dismiss();
                com.team7.taskflow.ui.member.MemberListBottomSheet sheet =
                        new com.team7.taskflow.ui.member.MemberListBottomSheet(projectId);
                sheet.show(getSupportFragmentManager(), "members");
            });
        }

        View btnCollapse = sheetView.findViewById(R.id.btnCollapse);
        if (btnCollapse != null) {
            btnCollapse.setOnClickListener(v -> bottomSheet.dismiss());
        }

        bottomSheet.show();
    }
}
