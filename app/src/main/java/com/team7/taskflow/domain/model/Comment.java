package com.team7.taskflow.domain.model;

import com.google.gson.annotations.SerializedName;

public class Comment {

    @SerializedName("comment_id")
    private Long id;

    @SerializedName("task_id")
    private Long taskId;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("content")
    private String content;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("is_deleted")
    private Boolean isDeleted;

    @SerializedName("users")
    private User user;

    private Integer likeCount;

    private Integer heartCount;

    private Integer congratsCount;

    private boolean likeSelected;

    private boolean heartSelected;

    private boolean congratsSelected;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public boolean isDeleted() {
        return isDeleted != null && isDeleted;
    }

    public void setDeleted(Boolean deleted) {
        isDeleted = deleted;
    }

    public int getLikeCount() {
        return likeCount != null ? likeCount : 0;
    }

    public void setLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
    }

    public int getHeartCount() {
        return heartCount != null ? heartCount : 0;
    }

    public void setHeartCount(Integer heartCount) {
        this.heartCount = heartCount;
    }

    public int getCongratsCount() {
        return congratsCount != null ? congratsCount : 0;
    }

    public void setCongratsCount(Integer congratsCount) {
        this.congratsCount = congratsCount;
    }

    public boolean isLikeSelected() {
        return likeSelected;
    }

    public void setLikeSelected(boolean likeSelected) {
        this.likeSelected = likeSelected;
    }

    public boolean isHeartSelected() {
        return heartSelected;
    }

    public void setHeartSelected(boolean heartSelected) {
        this.heartSelected = heartSelected;
    }

    public boolean isCongratsSelected() {
        return congratsSelected;
    }

    public void setCongratsSelected(boolean congratsSelected) {
        this.congratsSelected = congratsSelected;
    }
}
