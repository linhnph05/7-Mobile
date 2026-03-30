package com.team7.taskflow.domain.model;

import com.google.gson.annotations.SerializedName;

public class ProjectMember {

    @SerializedName("project_id")
    private long projectId;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("role")
    private String role; // OWNER, ADMIN, MEMBER, VIEWER

    @SerializedName("joined_at")
    private String joinedAt;

    @SerializedName("projects")
    private Project project;

    @SerializedName("users")
    private UserInfo userInfo;

    public static class UserInfo {
        @SerializedName("user_id")
        public String userId;

        @SerializedName("display_name")
        public String displayName;

        @SerializedName("email")
        public String email;

        @SerializedName("avatar_url")
        public String avatarUrl;

        // Tương thích với AiCreateActivity
        public String getDisplayNameOrEmail() {
            if (displayName != null && !displayName.isEmpty()) return displayName;
            if (email != null && !email.isEmpty()) return email;
            return "Unknown";
        }

        // Tương thích với code dùng getUserId() trên UserInfo
        public String getUserId() { return userId; }

        // Tương thích với code dùng setUserId() trên UserInfo
        public void setUserId(String userId) { this.userId = userId; }
    }

    public ProjectMember() {}

    // ── Getters & Setters ──────────────────────────────────────
    public long getProjectId() { return projectId; }
    public void setProjectId(long projectId) { this.projectId = projectId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getJoinedAt() { return joinedAt; }
    public void setJoinedAt(String joinedAt) { this.joinedAt = joinedAt; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public UserInfo getUserInfo() { return userInfo; }
    public void setUserInfo(UserInfo userInfo) { this.userInfo = userInfo; }

    // Tương thích với code đồng đội dùng getUser()
    public UserInfo getUser() { return userInfo; }

    // ── Helper methods ─────────────────────────────────────────
    public String getDisplayName() {
        if (userInfo != null && userInfo.displayName != null) return userInfo.displayName;
        if (userInfo != null && userInfo.email != null) return userInfo.email;
        return "Unknown";
    }

    public String getEmail() {
        if (userInfo != null) return userInfo.email;
        return "";
    }

    public String getAvatarUrl() {
        if (userInfo != null) return userInfo.avatarUrl;
        return null;
    }

    public boolean isOwner() { return "OWNER".equalsIgnoreCase(role); }
    public boolean isAdmin() { return "ADMIN".equalsIgnoreCase(role); }
    public boolean isMember() { return "MEMBER".equalsIgnoreCase(role); }
    public boolean isViewer() { return "VIEWER".equalsIgnoreCase(role); }
    public boolean canEdit() { return isOwner() || isAdmin(); }
}