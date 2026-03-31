package com.team7.taskflow.domain.model;

import com.google.gson.annotations.SerializedName;

public class CommentReaction {

    @SerializedName("reaction_id")
    private Long id;

    @SerializedName("comment_id")
    private Long commentId;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("reaction_type")
    private String reactionType;

    @SerializedName("created_at")
    private String createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCommentId() {
        return commentId;
    }

    public void setCommentId(Long commentId) {
        this.commentId = commentId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getReactionType() {
        return reactionType;
    }

    public void setReactionType(String reactionType) {
        this.reactionType = reactionType;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
