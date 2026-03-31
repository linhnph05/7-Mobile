package com.team7.taskflow.utils;

import android.app.Activity;
import android.content.Intent;

import com.team7.taskflow.R;

/**
 * Utility class để quản lý directional animations cho BottomNavigationView navigation
 * Giữ thứ tự: Home (0) → Tasks (1) → Assistant (2) → Settings (3)
 */
public class NavigationUtils {
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
        currentActivity.startActivity(intent);
        
        if (targetNavIndex > currentNavIndex) {
            // Moving right in nav bar → slide in from right
            currentActivity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        } else if (targetNavIndex < currentNavIndex) {
            // Moving left in nav bar → slide in from left
            currentActivity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        }
    }
}
