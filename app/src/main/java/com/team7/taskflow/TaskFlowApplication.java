package com.team7.taskflow;

import android.app.Application;

import com.team7.taskflow.utils.LanguageManager;
import com.team7.taskflow.ui.profile.ProfileActivity;

/**
 * TaskFlowApplication — Class chạy đầu tiên khi app khởi động.
 * Dùng để thiết lập theme toàn cục và các cấu hình hệ thống.
 */
public class TaskFlowApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        LanguageManager.applySavedLanguage(this);

        // Áp dụng theme (Sáng/Tối) ngay khi app vừa khởi động
        // Điều này giúp tránh hiện tượng "văng" ra màn hình chính hoặc reset theme khi chuyển màn hình
        ProfileActivity.applySavedTheme(this);
    }
}
