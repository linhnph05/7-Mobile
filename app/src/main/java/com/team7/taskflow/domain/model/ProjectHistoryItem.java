package com.team7.taskflow.domain.model;

public class ProjectHistoryItem {

    public static final String SOURCE_PROJECT_ACTIVITY = "PROJECT_ACTIVITY";
    public static final String SOURCE_TASK_ACTIVITY = "TASK_ACTIVITY";
    public static final String SOURCE_COMMENT = "COMMENT";

    private String source;
    private String actorId;
    private String actorName;
    private String avatarUrl;
    private String actionLabel;
    private String taskTitle;
    private String detail;
    private String commentContent;
    private String createdAt;
    
    private String rawActionType;
    private String oldValue;
    private String newValue;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getActionLabel() {
        return actionLabel;
    }

    public void setActionLabel(String actionLabel) {
        this.actionLabel = actionLabel;
    }

    public String getTaskTitle() {
        return taskTitle;
    }

    public void setTaskTitle(String taskTitle) {
        this.taskTitle = taskTitle;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getCommentContent() {
        return commentContent;
    }

    public void setCommentContent(String commentContent) {
        this.commentContent = commentContent;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getRawActionType() {
        return rawActionType;
    }

    public void setRawActionType(String rawActionType) {
        this.rawActionType = rawActionType;
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
}
