package com.team7.taskflow.domain.model;

import com.google.gson.annotations.SerializedName;

/**
 * Model class đại diện cho Project Member
 * Mapping với bảng project_members trong Supabase
 */
public class ProjectMember {

    @SerializedName("project_id")
    private long projectId;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("role")
    private String role; // OWNER, ADMIN, MEMBER

    @SerializedName("joined_at")
    private String joinedAt;

    // Nested project data (khi dùng select với foreign key)
    // Supabase trả về key = tên bảng "projects", không phải "project"
    @SerializedName("projects")
    private Project project;

    public ProjectMember() {
    }

    // Getters and Setters
    public long getProjectId() {
        return projectId;
    }

    public void setProjectId(long projectId) {
        this.projectId = projectId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(String joinedAt) {
        this.joinedAt = joinedAt;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    /**
     * Check if user is owner
     */
    public boolean isOwner() {
        return "OWNER".equalsIgnoreCase(role);
    }

    /**
     * Check if user is admin
     */
    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

    /**
     * Check if user can edit project
     */
    public boolean canEdit() {
        return isOwner() || isAdmin();
    }
}
