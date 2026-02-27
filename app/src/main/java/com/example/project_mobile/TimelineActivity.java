package com.example.project_mobile;

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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class TimelineActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_timeline);

        // Back / menu button
        findViewById(R.id.btnMenu).setOnClickListener(v -> finish());

        // Shift the bottom bar above the system navigation bar
        View bottomBar = findViewById(R.id.bottomBar);
        ViewCompat.setOnApplyWindowInsetsListener(bottomBar, (v, insets) -> {
            Insets navInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                v.getPaddingLeft(),
                v.getPaddingTop(),
                v.getPaddingRight(),
                navInsets.bottom + (int) (12 * getResources().getDisplayMetrics().density)
            );
            return insets;
        });

        // Shift the top app bar below the status bar
        View topAppBar = findViewById(R.id.topAppBar);
        ViewCompat.setOnApplyWindowInsetsListener(topAppBar, (v, insets) -> {
            Insets statusInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                v.getPaddingLeft(),
                statusInsets.top,
                v.getPaddingRight(),
                v.getPaddingBottom()
            );
            return insets;
        });

        // New Task button
        findViewById(R.id.btnNewTask).setOnClickListener(v -> {
            // TODO: open add-task sheet
        });

        // Day / Week / Month tab switching
        TextView tabDay   = findViewById(R.id.tabDay);
        TextView tabWeek  = findViewById(R.id.tabWeek);
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

    // ── Custom view: vertical dashed "today" line ──────────────────────────
    public static class TodayLineView extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public TodayLineView(Context ctx) { super(ctx); init(); }
        public TodayLineView(Context ctx, AttributeSet attrs) { super(ctx, attrs); init(); }
        public TodayLineView(Context ctx, AttributeSet attrs, int defStyle) {
            super(ctx, attrs, defStyle);
            init();
        }

        private void init() {
            paint.setColor(0xFF136DEC);          // primary blue
            paint.setStrokeWidth(2f * getResources().getDisplayMetrics().density);
            paint.setStyle(Paint.Style.STROKE);
            float dash = 8f * getResources().getDisplayMetrics().density;
            paint.setPathEffect(new DashPathEffect(new float[]{dash, dash}, 0));
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            canvas.drawLine(cx, 0, cx, getHeight(), paint);
        }
    }
}
