package com.team7.taskflow.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Quản lý các tùy chọn dự án được lưu trữ cục bộ, chẳng hạn như trạng thái ghim.
 */
public class ProjectPrefsManager {
    private static final String PREF_NAME = "project_prefs";
    private static final String KEY_PINNED_PROJECTS = "pinned_projects";
    
    private final SharedPreferences prefs;

    public ProjectPrefsManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Ghim hoặc bỏ ghim một dự án.
     */
    public void setProjectPinned(long projectId, boolean pinned) {
        Set<String> pinnedIds = new HashSet<>(prefs.getStringSet(KEY_PINNED_PROJECTS, new HashSet<>()));
        String idStr = String.valueOf(projectId);
        
        if (pinned) {
            pinnedIds.add(idStr);
        } else {
            pinnedIds.remove(idStr);
        }
        
        prefs.edit().putStringSet(KEY_PINNED_PROJECTS, pinnedIds).apply();
    }

    /**
     * Kiểm tra xem một dự án có được ghim hay không.
     */
    public boolean isProjectPinned(long projectId) {
        Set<String> pinnedIds = prefs.getStringSet(KEY_PINNED_PROJECTS, new HashSet<>());
        return pinnedIds.contains(String.valueOf(projectId));
    }

    /**
     * Lấy danh sách ID các dự án được ghim.
     */
    public Set<String> getPinnedProjectIds() {
        return prefs.getStringSet(KEY_PINNED_PROJECTS, new HashSet<>());
    }
}
