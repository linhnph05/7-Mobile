package com.team7.taskflow.domain.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class Project {

    @SerializedName("project_id")
    private long id;

    @SerializedName("owner_id")
    private String ownerId;

    @SerializedName("project_name")
    private String name;

    @SerializedName("description")
    private String description;

    @SerializedName("project_key")
    private String projectKey;

    @SerializedName("background_color")
    private String color;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("is_deleted")
    private boolean isDeleted;

    @SerializedName("deleted_at")
    private String deletedAt;

    @SerializedName("is_private")
    private boolean isPrivate;

    private transient int totalTasks;
    private transient int completedTasks;
    private transient int newActivitiesCount;
    private transient int taskActivitiesToday;
    private transient int commentActivitiesToday;
    private transient List<User> memberPreviews = new ArrayList<>();
    private transient String template;
    private transient String userRole; // OWNER, ADMIN, MEMBER - vai trò của user trong project

    public Project() {}

    public Project(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public String getDeletedAt() { return deletedAt; }
    public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }

    public int getTotalTasks() { return totalTasks; }
    public void setTotalTasks(int totalTasks) { this.totalTasks = totalTasks; }

    public int getCompletedTasks() { return completedTasks; }
    public void setCompletedTasks(int completedTasks) { this.completedTasks = completedTasks; }

    public int getNewActivitiesCount() { return newActivitiesCount; }
    public void setNewActivitiesCount(int newActivitiesCount) { this.newActivitiesCount = newActivitiesCount; }

    public int getTaskActivitiesToday() { return taskActivitiesToday; }
    public void setTaskActivitiesToday(int taskActivitiesToday) { this.taskActivitiesToday = taskActivitiesToday; }

    public int getCommentActivitiesToday() { return commentActivitiesToday; }
    public void setCommentActivitiesToday(int commentActivitiesToday) { this.commentActivitiesToday = commentActivitiesToday; }

    public List<User> getMemberPreviews() { return memberPreviews; }
    public void setMemberPreviews(List<User> memberPreviews) {
        this.memberPreviews = memberPreviews != null ? memberPreviews : new ArrayList<>();
    }

    public boolean isPrivate() { return isPrivate; }
    public void setPrivate(boolean isPrivate) { this.isPrivate = isPrivate; }

    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }

    public String getUserRole() { return userRole; }
    public void setUserRole(String userRole) { this.userRole = userRole; }

    /**
     * Check if current user is owner of this project
     */
    public boolean isOwner() {
        return "OWNER".equalsIgnoreCase(userRole);
    }

    /**
     * Check if current user can edit this project
     */
    public boolean canEdit() {
        return "OWNER".equalsIgnoreCase(userRole) || "ADMIN".equalsIgnoreCase(userRole);
    }

    public int getProgressPercent() {
        if (totalTasks == 0) return 0;
        return (int) ((completedTasks * 100.0) / totalTasks);
    }
}
