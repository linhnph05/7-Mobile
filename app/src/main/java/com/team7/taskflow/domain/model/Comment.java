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

    @SerializedName("users")
    private User user;

    @SerializedName("like")
    private Integer likeCount;

    @SerializedName("heart")
    private Integer heartCount;

    @SerializedName("congrats")
    private Integer congratsCount;

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
}
