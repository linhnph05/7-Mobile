package com.team7.taskflow.domain.model;

import com.google.gson.annotations.SerializedName;

public class ProjectActivity {

    @SerializedName("activity_id")
    private Long id;

    @SerializedName("project_id")
    private long projectId;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("action_type")
    private String actionType;

    @SerializedName("entity_type")
    private String entityType;

    @SerializedName("entity_id")
    private Long entityId;

    @SerializedName("old_value")
    private String oldValue;

    @SerializedName("new_value")
    private String newValue;

    @SerializedName("created_at")
    private String createdAt;

    public ProjectActivity() {
    }

    public ProjectActivity(long projectId, String userId, String actionType, String entityType, Long entityId,
            String oldValue, String newValue) {
        this.projectId = projectId;
        this.userId = userId;
        this.actionType = actionType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}