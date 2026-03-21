package com.team7.taskflow.domain.model;

import com.google.gson.annotations.SerializedName;

public class TaskActivity {
    @SerializedName("activity_id")
    private long id;

    @SerializedName("task_id")
    private long taskId;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("action_type")
    private String actionType; // CREATE, UPDATE_STATUS, UPDATE_TITLE, DELETE, etc.

    @SerializedName("old_value")
    private String oldValue;

    @SerializedName("new_value")
    private String newValue;

    @SerializedName("created_at")
    private String createdAt;

    public TaskActivity() {}

    public TaskActivity(long taskId, String userId, String actionType, String oldValue, String newValue) {
        this.taskId = taskId;
        this.userId = userId;
        this.actionType = actionType;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    // Getters and Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getTaskId() { return taskId; }
    public void setTaskId(long taskId) { this.taskId = taskId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
