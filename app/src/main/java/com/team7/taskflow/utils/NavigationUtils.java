package com.team7.taskflow.utils;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import com.team7.taskflow.R;

/**
 * Utility class để quản lý directional animations cho BottomNavigationView navigation
 * Giữ thứ tự: Home (0) → Tasks (1) → Assistant (2) → Settings (3)
 */
public class NavigationUtils {
    public static final String EXTRA_NAV_FROM = "extra_nav_from";
    public static final String EXTRA_NAV_TO = "extra_nav_to";
    private static final long CONTENT_SLIDE_DURATION_MS = 240L;
    private static final long NAVIGATION_DEBOUNCE_MS = 350L;
    private static long lastNavigationAtMs = 0L;

    // Navigation item indices
    public static final int NAV_HOME = 0;
    public static final int NAV_TASKS = 1;
    public static final int NAV_ASSISTANT = 2;
    public static final int NAV_SETTINGS = 3;

    /**
     * Start activity với directional animation dựa vào current và target nav index
     */
    public static boolean startActivityWithNavAnimation(
            Activity currentActivity,
            Intent intent,
            int currentNavIndex,
            int targetNavIndex
    ) {
        if (currentActivity == null || intent == null) {
            return false;
        }

        long now = SystemClock.elapsedRealtime();
        if (now - lastNavigationAtMs < NAVIGATION_DEBOUNCE_MS) {
            return false;
        }

        if (intent.getComponent() != null
                && currentActivity.getClass().getName().equals(intent.getComponent().getClassName())) {
            return false;
        }

        lastNavigationAtMs = now;
        
        // Flag quan trọng: Không chạy animation ở mức độ hệ thống
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT 
                | Intent.FLAG_ACTIVITY_SINGLE_TOP 
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        
        intent.putExtra(EXTRA_NAV_FROM, currentNavIndex);
        intent.putExtra(EXTRA_NAV_TO, targetNavIndex);

        // Sử dụng ActivityOptions để ghi đè hiệu ứng ngay lúc khởi tạo (Mạnh nhất)
        android.app.ActivityOptions options = android.app.ActivityOptions.makeCustomAnimation(currentActivity, 0, 0);
        currentActivity.startActivity(intent, options.toBundle());

        // Vẫn giữ lại các lệnh ghi đè cũ để hỗ trợ các máy đời thấp hơn
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            currentActivity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0);
            currentActivity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0);
        } else {
            currentActivity.overridePendingTransition(0, 0);
        }
        
        return true;
    }

    public static void applyTopContentSlideAnimation(Activity activity, View contentView) {
        if (activity == null || contentView == null || activity.getIntent() == null) {
            return;
        }

        int from = activity.getIntent().getIntExtra(EXTRA_NAV_FROM, -1);
        int to = activity.getIntent().getIntExtra(EXTRA_NAV_TO, -1);
        if (from < 0 || to < 0 || from == to) {
            return;
        }

        contentView.post(() -> {
            int width = contentView.getWidth();
            // Nếu View chưa kịp đo (width = 0), dùng chiều rộng màn hình làm fallback
            if (width <= 0) {
                android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
                activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
                width = metrics.widthPixels;
            }
            
            if (width <= 0) return;

            float startTranslationX = to > from ? width : -width;
            contentView.setTranslationX(startTranslationX);
            contentView.setAlpha(0f); // Bắt đầu từ trong suốt để tránh bị "giật"
            contentView.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(CONTENT_SLIDE_DURATION_MS)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        });
    }

    /**
     * Triệt tiêu hiệu ứng chuyển cảnh mặc định của hệ thống ngay bên trong Activity.
     * Gọi hàm này trong onCreate() hoặc onNewIntent() để đảm bảo thanh điều hướng đứng yên.
     */
    public static void suppressActivityTransition(Activity activity) {
        if (activity == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0);
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0);
        } else {
            activity.overridePendingTransition(0, 0);
        }
    }
}
