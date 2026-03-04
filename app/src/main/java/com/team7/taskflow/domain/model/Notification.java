package com.team7.taskflow.domain.model;

import com.google.gson.annotations.SerializedName;

import java.util.Date;

/**
 * Model class representing a Notification from the database.
 *
 * Database schema:
 * notification_id, user_id, actor_id, type, reference_id, is_read, created_at
 *
 * Supabase select query joins:
 * actor:users!notifications_actor_id_fkey(display_name, avatar_url)
 *
 * Content is NOT stored in DB — it is built dynamically on the client
 * based on type + actor name + referenced entity name.
 */
public class Notification {

    /**
     * Maps to the "type" column in the notifications table.
     */
    public enum NotificationType {
        PROJECT_INVITE,
        TASK_ASSIGNED,
        MENTION,
        COMMENT,
        TASK_COMPLETED,
        REACTION,
        ATTACHMENT_ADDED,
        DEADLINE_REMINDER,
        SYSTEM_ALERT
    }

    // ── Database columns ────────────────────────────────────────────

    @SerializedName("notification_id")
    private long notificationId;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("actor_id")
    private String actorId;

    @SerializedName("type")
    private String typeRaw; // raw string from DB, e.g. "PROJECT_INVITE"

    @SerializedName("reference_id")
    private Long referenceId; // FK to projects.project_id or tasks.task_id

    @SerializedName("is_read")
    private boolean isRead;

    @SerializedName("created_at")
    private Date createdAt;

    // ── Nested join objects from Supabase select ────────────────────

    /** Joined from users table via actor_id */
    @SerializedName("actor")
    private ActorInfo actor;

    // ── Client-side enriched fields (set after fetching) ───────────

    /** Resolved display name of the actor */
    private transient String actorName;

    /** Resolved name of the referenced entity (project name or task title) */
    private transient String referenceName;

    /** Parsed enum type (converted from typeRaw) */
    private transient NotificationType typeParsed;

    /** Pre-built HTML content string for display */
    private transient String displayContent;

    // ── Constructors ────────────────────────────────────────────────

    public Notification() {
    }

    // ── Nested class for actor join ─────────────────────────────────

    public static class ActorInfo {
        @SerializedName("display_name")
        private String displayName;

        @SerializedName("avatar_url")
        private String avatarUrl;

        public String getDisplayName() {
            return displayName;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }
    }

    // ── Getters / Setters ───────────────────────────────────────────

    public long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(long notificationId) {
        this.notificationId = notificationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getTypeRaw() {
        return typeRaw;
    }

    public void setTypeRaw(String typeRaw) {
        this.typeRaw = typeRaw;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(Long referenceId) {
        this.referenceId = referenceId;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public ActorInfo getActor() {
        return actor;
    }

    public void setActor(ActorInfo actor) {
        this.actor = actor;
    }

    // ── Client-enriched field accessors ──────────────────────────────

    public String getActorName() {
        if (actorName != null)
            return actorName;
        if (actor != null && actor.getDisplayName() != null)
            return actor.getDisplayName();
        return "Someone";
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public String getActorAvatarUrl() {
        return actor != null ? actor.getAvatarUrl() : null;
    }

    public String getReferenceName() {
        return referenceName;
    }

    public void setReferenceName(String referenceName) {
        this.referenceName = referenceName;
    }

    /**
     * Parse and cache the NotificationType enum from the raw DB string.
     */
    public NotificationType getType() {
        if (typeParsed != null)
            return typeParsed;
        if (typeRaw == null)
            return NotificationType.SYSTEM_ALERT;
        try {
            typeParsed = NotificationType.valueOf(typeRaw);
        } catch (IllegalArgumentException e) {
            typeParsed = NotificationType.SYSTEM_ALERT;
        }
        return typeParsed;
    }

    public void setType(NotificationType type) {
        this.typeParsed = type;
    }

    /**
     * Get the pre-built display content for this notification.
     * Must call buildDisplayContent() or setDisplayContent() first.
     */
    public String getContent() {
        if (displayContent != null)
            return displayContent;
        return buildDisplayContent();
    }

    public String getContextText() {
        String ref = (referenceName != null && !referenceName.isEmpty()) ? referenceName : "";
        switch (getType()) {
            case PROJECT_INVITE:
                return "Project: " + ref;
            case TASK_ASSIGNED:
            case MENTION:
            case COMMENT:
            case TASK_COMPLETED:
            case REACTION:
            case ATTACHMENT_ADDED:
            case DEADLINE_REMINDER:
                return "Task: " + ref;
            default:
                return "";
        }
    }

    public void setDisplayContent(String displayContent) {
        this.displayContent = displayContent;
    }

    /**
     * Build a human-readable HTML content string based on type, actor, and
     * reference.
     */
    public String buildDisplayContent() {
        String actor = "<b>" + getActorName() + "</b>";

        switch (getType()) {
            case PROJECT_INVITE:
                displayContent = actor + " invited you to join a project.";
                break;
            case TASK_ASSIGNED:
                displayContent = actor + " assigned a task to you.";
                break;
            case MENTION:
                displayContent = actor + " mentioned you in a task.";
                break;
            case COMMENT:
                displayContent = actor + " commented on a task.";
                break;
            case TASK_COMPLETED:
                displayContent = actor + " completed a task.";
                break;
            case REACTION:
                displayContent = actor + " reacted to your comment.";
                break;
            case ATTACHMENT_ADDED:
                displayContent = actor + " added an attachment.";
                break;
            case DEADLINE_REMINDER:
                displayContent = "A task is due soon!";
                break;
            case SYSTEM_ALERT:
                displayContent = "System alert";
                break;
            default:
                displayContent = "You have a new notification.";
        }
        return displayContent;
    }
}
