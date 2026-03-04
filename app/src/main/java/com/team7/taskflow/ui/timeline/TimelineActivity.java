package com.team7.taskflow.ui.timeline;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.team7.taskflow.R;
import com.team7.taskflow.ui.base.BaseActivity;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
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

        // New Task button
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
        String projectName = getIntent().getStringExtra("project_name");
        if (projectName != null) {
            TextView tvProjectName = sheetView.findViewById(R.id.tvProjectName);
            if (tvProjectName != null) {
                tvProjectName.setText(projectName);
            }
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
