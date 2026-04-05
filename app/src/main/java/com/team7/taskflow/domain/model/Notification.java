package com.team7.taskflow.domain.model;

import com.google.gson.annotations.SerializedName;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
        TASK_STATUS_CHANGED,
        REACTION,
        DELETED,
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

    @SerializedName("task_activity_id")
    private Long taskActivityId; // FK to task_activities.activity_id (for TASK_STATUS_CHANGED)

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

    /** Task activity details (fetched from task_activities table for TASK_STATUS_CHANGED) */
    private transient TaskActivity activityDetail;

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

    public Long getTaskActivityId() {
        return taskActivityId;
    }

    public void setTaskActivityId(Long taskActivityId) {
        this.taskActivityId = taskActivityId;
    }

    public ActorInfo getActor() {
        return actor;
    }

    public void setActor(ActorInfo actor) {
        this.actor = actor;
    }

    public TaskActivity getActivityDetail() {
        return activityDetail;
    }

    public void setActivityDetail(TaskActivity activityDetail) {
        this.activityDetail = activityDetail;
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
            if ("TASK_COMPLETED".equals(typeRaw)) {
                typeParsed = NotificationType.TASK_STATUS_CHANGED;
                return typeParsed;
            }
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
            case TASK_STATUS_CHANGED:
            case REACTION:
            case DELETED:
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
     * Build a human-readable HTML content string based on type, actor, and reference.
     * For TASK_STATUS_CHANGED, uses activityDetail (fetched from task_activities by Repository).
     * Routes to different content builders based on actionType.
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
            case TASK_STATUS_CHANGED:
                // Route to specific builders based on activity details
                if (activityDetail != null && activityDetail.getActionType() != null) {
                    displayContent = buildActivityContent(actor, activityDetail);
                } else {
                    displayContent = actor + " updated task.";
                }
                break;
            case REACTION:
                displayContent = actor + " reacted to your comment.";
                break;
            case DELETED:
                displayContent = actor + " withdrew a reaction.";
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

    /**
     * Route to specific content builder based on actionType from TaskActivity.
     * Handles: UPDATE_STATUS, UPDATE_DUE_DATE, UPDATE_PRIORITY, UPDATE_START_DATE, UPDATE_TITLE, UPDATE_ASSIGNEE, UPDATE_TAG, etc.
     */
    private String buildActivityContent(String actor, TaskActivity activity) {
        String actionType = activity.getActionType();
        if (actionType == null) {
            return actor + " updated task.";
        }

        actionType = actionType.toUpperCase().trim();

        switch (actionType) {
            case "UPDATE_STATUS":
                return buildStatusChangeContent(actor, activity);
            case "UPDATE_DUE_DATE":
                return buildDueDateChangeContent(actor, activity);
            case "UPDATE_PRIORITY":
                return buildPriorityChangeContent(actor, activity);
            case "UPDATE_START_DATE":
                return buildStartDateChangeContent(actor, activity);
            case "UPDATE_TITLE":
                return buildTitleChangeContent(actor, activity);
            case "UPDATE_ASSIGNEE":
                return buildAssigneeChangeContent(actor, activity);
            case "UPDATE_TAG":
                return buildTagChangeContent(actor, activity);
            case "UPDATE_DESCRIPTION":
                return buildDescriptionChangeContent(actor, activity);
            case "DELETE":
            case "CREATE":
                return actor + " updated task.";
            default:
                return actor + " updated task.";
        }
    }

    private String buildStatusChangeContent(String actor, TaskActivity activity) {
        String oldVal = activity.getOldValue();
        String newVal = activity.getNewValue();

        if (oldVal != null && newVal != null) {
            return actor + " changed status from <b>" + oldVal + "</b> to <b>" + newVal + "</b>.";
        }
        return actor + " changed task status to <b>" + (newVal != null ? newVal : "?") + "</b>.";
    }

    private String buildDueDateChangeContent(String actor, TaskActivity activity) {
        String oldVal = activity.getOldValue();
        String newVal = activity.getNewValue();

        if (newVal != null && !newVal.isEmpty()) {
            return actor + " changed due date to <b>" + formatDateDisplay(newVal) + "</b>.";
        } else if (oldVal != null && !oldVal.isEmpty()) {
            return actor + " removed the due date.";
        }
        return actor + " changed the due date.";
    }

    private String buildPriorityChangeContent(String actor, TaskActivity activity) {
        String oldVal = activity.getOldValue();
        String newVal = activity.getNewValue();

        if (newVal != null && !newVal.isEmpty() && (oldVal == null || oldVal.isEmpty())) {
            return actor + " added priority <b>" + newVal + "</b>.";
        } else if ((newVal == null || newVal.isEmpty()) && oldVal != null && !oldVal.isEmpty()) {
            return actor + " removed priority <b>" + oldVal + "</b>.";
        } else if (newVal != null && !newVal.isEmpty()) {
            return actor + " changed priority to <b>" + newVal + "</b>.";
        }
        return actor + " changed task priority.";
    }

    private String buildStartDateChangeContent(String actor, TaskActivity activity) {
        String oldVal = activity.getOldValue();
        String newVal = activity.getNewValue();

        if (newVal != null && !newVal.isEmpty()) {
            return actor + " changed start date to <b>" + formatDateDisplay(newVal) + "</b>.";
        } else if (oldVal != null && !oldVal.isEmpty()) {
            return actor + " removed the start date.";
        }
        return actor + " changed the start date.";
    }

    private String buildTitleChangeContent(String actor, TaskActivity activity) {
        String newVal = activity.getNewValue();

        if (newVal != null && !newVal.isEmpty()) {
            return actor + " changed task title to <b>" + escapeHtml(newVal) + "</b>.";
        }
        return actor + " changed task title.";
    }

    private String buildAssigneeChangeContent(String actor, TaskActivity activity) {
        String newVal = activity.getNewValue();
        String oldVal = activity.getOldValue();

        if (newVal != null && !newVal.isEmpty()) {
            return actor + " assigned task to <b>" + newVal + "</b>.";
        } else if (oldVal != null && !oldVal.isEmpty()) {
            return actor + " removed task assignment.";
        }
        return actor + " changed task assignee.";
    }

    private String buildTagChangeContent(String actor, TaskActivity activity) {
        String newVal = activity.getNewValue();
        String oldVal = activity.getOldValue();

        if (newVal != null && !newVal.isEmpty()) {
            return actor + " added tag <b>" + newVal + "</b>.";
        } else if (oldVal != null && !oldVal.isEmpty()) {
            return actor + " removed tag <b>" + oldVal + "</b>.";
        }
        return actor + " changed task tag.";
    }

    private String buildDescriptionChangeContent(String actor, TaskActivity activity) {
        return actor + " changed task description.";
    }

    /**
     * Escape HTML special characters to prevent injection
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * Format date string for display in Vietnam timezone.
     * - Date only: dd/MM/yyyy
     * - Date-time: HH:mm dd/MM/yyyy
     */
    private String formatDateDisplay(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return dateStr;
        }

        String value = dateStr.trim();
        ZoneId vietnamZone = ZoneId.of("Asia/Ho_Chi_Minh");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

        try {
            if (value.contains("T")) {
                OffsetDateTime offsetDateTime = OffsetDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
                return offsetDateTime.atZoneSameInstant(vietnamZone).format(dateTimeFormatter);
            }

            if (value.contains(":")) {
                DateTimeFormatter inputDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                LocalDateTime localDateTime = LocalDateTime.parse(value, inputDateTime);
                return localDateTime.atZone(vietnamZone).format(dateTimeFormatter);
            }

            LocalDate localDate = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
            return localDate.format(dateFormatter);
        } catch (Exception e) {
            return value;
        }
    }
}
