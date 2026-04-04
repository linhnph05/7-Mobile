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
     * - Nếu target > current: item ở phía phải → activity slide in từ phải (slide_in_right, slide_out_left)
     * - Nếu target < current: item ở phía trái → activity slide in từ trái (slide_in_left, slide_out_right)
     */
    public static void startActivityWithNavAnimation(
            Activity currentActivity,
            Intent intent,
            int currentNavIndex,
            int targetNavIndex
    ) {
        if (currentActivity == null || intent == null) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (now - lastNavigationAtMs < NAVIGATION_DEBOUNCE_MS) {
            return;
        }

        if (intent.getComponent() != null
                && currentActivity.getClass().getName().equals(intent.getComponent().getClassName())) {
            return;
        }

        lastNavigationAtMs = now;
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(EXTRA_NAV_FROM, currentNavIndex);
        intent.putExtra(EXTRA_NAV_TO, targetNavIndex);
        currentActivity.startActivity(intent);
        // Disable whole-activity transition so bottom bar does not slide.
        currentActivity.overridePendingTransition(0, 0);
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
            if (width <= 0) {
                return;
            }

            float startTranslationX = to > from ? width : -width;
            contentView.setTranslationX(startTranslationX);
            contentView.setAlpha(0.92f);
            contentView.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(CONTENT_SLIDE_DURATION_MS)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        });
    }
}
