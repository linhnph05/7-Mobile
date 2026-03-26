package com.team7.taskflow.ui.timeline;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.team7.taskflow.R;
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
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.activity.EdgeToEdge;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class TimelineActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_timeline);

        // Handle system window insets per-component (works on all devices)
        View rootLayout = findViewById(R.id.rootLayout);
        View bottomBar = findViewById(R.id.bottomBar);

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Top padding = status bar height (keeps app bar below status bar)
            v.setPadding(0, systemBars.top, 0, 0);
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(bottomBar, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Bottom padding = navigation bar height (keeps bottom bar above nav bar)
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(),
                    v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        // Back button
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // 3-dot menu → open Project Settings bottom sheet
        findViewById(R.id.btnMoreOptions).setOnClickListener(v -> showProjectSettingsPanel());

        // AI Task creation context button starts new Activity
        View fabAddAI = findViewById(R.id.fabAddAI);
        if (fabAddAI != null) {
            fabAddAI.setOnClickListener(v -> {
                android.content.Intent aiIntent = new android.content.Intent(this, com.team7.taskflow.ui.ai.AiCreateActivity.class);
                startActivity(aiIntent);
            });
        }

        // New Task button (Manual creation sheet)
        findViewById(R.id.btnNewTask).setOnClickListener(v -> {
            // TODO: open add-task sheet
        });

        // Day / Week / Month tab switching
        TextView tabDay = findViewById(R.id.tabDay);
        TextView tabWeek = findViewById(R.id.tabWeek);
        TextView tabMonth = findViewById(R.id.tabMonth);

        View.OnClickListener tabClick = v -> {
            tabDay.setBackgroundResource(R.drawable.bg_tab_inactive);
            tabWeek.setBackgroundResource(R.drawable.bg_tab_inactive);
            tabMonth.setBackgroundResource(R.drawable.bg_tab_inactive);
            tabDay.setTextColor(ContextCompat.getColor(this, R.color.slate_600));
            tabWeek.setTextColor(ContextCompat.getColor(this, R.color.slate_600));
            tabMonth.setTextColor(ContextCompat.getColor(this, R.color.slate_600));

            v.setBackgroundResource(R.drawable.bg_tab_active);
            ((TextView) v).setTextColor(ContextCompat.getColor(this, R.color.white));
        };

        tabDay.setOnClickListener(tabClick);
        tabWeek.setOnClickListener(tabClick);
        tabMonth.setOnClickListener(tabClick);

        // Calendar Button
        ImageButton btnCalendar = findViewById(R.id.btnCalendar);
        if (btnCalendar != null) {
            btnCalendar.setOnClickListener(v -> {
                Intent intent = new Intent(TimelineActivity.this, CalendarActivity.class);
                // Truyền project_id hiện tại (nếu có)
                long projectId = getIntent().getLongExtra("project_id", -1);
                intent.putExtra("project_id", projectId);
                startActivity(intent);
            });
        }
        ImageButton btnKanban = findViewById(R.id.btnKanban);

        if (btnKanban != null) {
            btnKanban.setOnClickListener(v -> {
                // Lấy project_id hiện tại từ Intent của TimelineActivity
                long projectId = getIntent().getLongExtra("project_id", -1);
                String projectName = getIntent().getStringExtra("project_name");

                // Chuyển sang ProjectBoardActivity (Kanban Board)
                Intent intent = new Intent(TimelineActivity.this, ProjectBoardActivity.class);
                intent.putExtra("project_id", projectId);
                intent.putExtra("project_name", projectName);
                startActivity(intent);

                // (Tùy chọn) Đóng TimelineActivity nếu bạn không muốn quay lại bằng nút Back
                // finish();
            });
        }
    }

    /**
     * Show Project Settings as a BottomSheetDialog using
     * layout_project_settings_panel.xml.
     */
    private void showProjectSettingsPanel() {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this, R.style.Theme_TaskFlow_BottomSheet);
        View sheetView = getLayoutInflater().inflate(R.layout.layout_project_settings_panel, null);
        bottomSheet.setContentView(sheetView);

        // Force the bottom sheet to expand fully so "Delete Project" is visible
        android.widget.FrameLayout bottomSheetLayout = bottomSheet
                .findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheetLayout != null) {
            com.google.android.material.bottomsheet.BottomSheetBehavior<android.widget.FrameLayout> behavior = com.google.android.material.bottomsheet.BottomSheetBehavior
                    .from(bottomSheetLayout);
            behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
        }

        // Get project info from Intent (passed from Dashboard)
        long currentProjectId = getIntent().getLongExtra("project_id", -1);
        String currentProjectName = getIntent().getStringExtra("project_name");
        String currentProjectKey = getIntent().getStringExtra("project_key");
        String currentProjectDesc = getIntent().getStringExtra("project_desc");

        android.widget.EditText etProjectName = sheetView.findViewById(R.id.etProjectName);
        android.widget.EditText etProjectDesc = sheetView.findViewById(R.id.etProjectDesc);
        TextView tvProjectKey = sheetView.findViewById(R.id.tvProjectKey);
        android.widget.ImageView btnSaveProject = sheetView.findViewById(R.id.btnSaveProject);

        if (etProjectName != null && currentProjectName != null) etProjectName.setText(currentProjectName);
        if (etProjectDesc != null && currentProjectDesc != null) etProjectDesc.setText(currentProjectDesc);
        if (tvProjectKey != null) {
            tvProjectKey.setText(currentProjectKey != null ? "KEY: " + currentProjectKey : "N/A");
        }

        if (btnSaveProject != null) {
            btnSaveProject.setOnClickListener(v -> {
                if (currentProjectId == -1) return;
                String newName = etProjectName.getText().toString().trim();
                String newDesc = etProjectDesc.getText().toString().trim();
                if (newName.isEmpty()) {
                    android.widget.Toast.makeText(this, "Tên dự án không được bỏ trống!", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }

                com.team7.taskflow.domain.model.Project updateP = new com.team7.taskflow.domain.model.Project();
                updateP.setName(newName);
                updateP.setDescription(newDesc);

                com.team7.taskflow.data.repository.ProjectRepository.getInstance().updateProject(
                        currentProjectId, updateP, new com.team7.taskflow.data.repository.ProjectRepository.ProjectCallback<com.team7.taskflow.domain.model.Project>() {
                    @Override
                    public void onSuccess(com.team7.taskflow.domain.model.Project result) {
                        runOnUiThread(() -> {
                            getIntent().putExtra("project_name", newName);
                            getIntent().putExtra("project_desc", newDesc);
                            android.widget.Toast.makeText(TimelineActivity.this, "Cập nhật dự án thành công!", android.widget.Toast.LENGTH_SHORT).show();
                            bottomSheet.dismiss();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> android.widget.Toast.makeText(TimelineActivity.this, error, android.widget.Toast.LENGTH_SHORT).show());
                    }
                });
            });
        }

        View btnCollapse = sheetView.findViewById(R.id.btnCollapse);
        if (btnCollapse != null) {
            btnCollapse.setOnClickListener(v -> bottomSheet.dismiss());
        }

        View btnDeleteProject = sheetView.findViewById(R.id.btnDeleteProject);
        if (btnDeleteProject != null) {
            btnDeleteProject.setOnClickListener(v -> {
                bottomSheet.dismiss();
                android.widget.Toast.makeText(this, "Delete project tapped", android.widget.Toast.LENGTH_SHORT).show();
            });
        }

        bottomSheet.show();
    }

    // ── Custom view: vertical dashed "today" line ──────────────────────────
    public static class TodayLineView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public TodayLineView(Context ctx) {
            super(ctx);
            init();
        }

        public TodayLineView(Context ctx, AttributeSet attrs) {
            super(ctx, attrs);
            init();
        }

        public TodayLineView(Context ctx, AttributeSet attrs, int defStyle) {
            super(ctx, attrs, defStyle);
            init();
        }

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
