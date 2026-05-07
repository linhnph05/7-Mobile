package com.team7.taskflow.domain.model;

import com.google.gson.annotations.SerializedName;
import com.team7.taskflow.ui.notification.NotificationFormatter;

import java.util.Date;
import java.util.Locale;

/**
 * Domain model đại diện cho một Notification từ database.
 *
 * Chỉ chứa dữ liệu và getter/setter.
 * Mọi logic hiển thị (HTML, i18n, date format) được xử lý trong NotificationFormatter.
 *
 * Schema DB: notification_id, user_id, actor_id, type, reference_id, is_read, created_at
 * Supabase join: actor:users!notifications_actor_id_fkey(display_name, avatar_url)
 */
public class Notification {

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

    // ── Database columns ────────────────────────────────────────────────

    @SerializedName("notification_id")
    private long notificationId;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("actor_id")
    private String actorId;

    @SerializedName("type")
    private String typeRaw;

    @SerializedName("reference_id")
    private Long referenceId;

    @SerializedName("task_activity_id")
    private Long taskActivityId;

    @SerializedName("is_read")
    private boolean isRead;

    @SerializedName("created_at")
    private Date createdAt;

    // ── Nested join: actor từ bảng users ───────────────────────────────

    @SerializedName("actor")
    private ActorInfo actor;

    public static class ActorInfo {
        @SerializedName("display_name")
        private String displayName;

        @SerializedName("avatar_url")
        private String avatarUrl;

        public String getDisplayName() { return displayName; }
        public String getAvatarUrl()   { return avatarUrl; }
    }

    // ── Client-side enriched fields (set bởi Repository sau khi fetch) ─

    private transient String actorName;
    private transient String referenceName;
    private transient NotificationType typeParsed;
    private transient String displayContent;
    private transient TaskActivity activityDetail;
    private transient String inviteStatus;

    // ── Constructors ────────────────────────────────────────────────────

    public Notification() {}

    // ── Getters / Setters ───────────────────────────────────────────────

    public long getNotificationId()                   { return notificationId; }
    public void setNotificationId(long v)             { this.notificationId = v; }

    public String getUserId()                         { return userId; }
    public void setUserId(String v)                   { this.userId = v; }

    public String getActorId()                        { return actorId; }
    public void setActorId(String v)                  { this.actorId = v; }

    public String getTypeRaw()                        { return typeRaw; }
    public void setTypeRaw(String v)                  { this.typeRaw = v; }

    public Long getReferenceId()                      { return referenceId; }
    public void setReferenceId(Long v)                { this.referenceId = v; }

    public Long getTaskActivityId()                   { return taskActivityId; }
    public void setTaskActivityId(Long v)             { this.taskActivityId = v; }

    public boolean isRead()                           { return isRead; }
    public void setRead(boolean v)                    { this.isRead = v; }

    public Date getCreatedAt()                        { return createdAt; }
    public void setCreatedAt(Date v)                  { this.createdAt = v; }

    public ActorInfo getActor()                       { return actor; }
    public void setActor(ActorInfo v)                 { this.actor = v; }

    public TaskActivity getActivityDetail()           { return activityDetail; }
    public void setActivityDetail(TaskActivity v)     { this.activityDetail = v; }

    public String getInviteStatus()                   { return inviteStatus; }
    public void setInviteStatus(String v)             { this.inviteStatus = v; }

    // ── Enriched field accessors ────────────────────────────────────────

    /** Tên actor hiển thị; fallback về "Someone" nếu chưa có dữ liệu. */
    public String getActorName() {
        if (actorName != null) return actorName;
        if (actor != null && actor.getDisplayName() != null) return actor.getDisplayName();
        return "Someone";
    }
    public void setActorName(String v) { this.actorName = v; }

    public String getActorAvatarUrl() {
        return actor != null ? actor.getAvatarUrl() : null;
    }

    public String getReferenceName()        { return referenceName; }
    public void setReferenceName(String v)  { this.referenceName = v; }

    /**
     * Parse và cache NotificationType enum từ chuỗi DB.
     * "TASK_COMPLETED" được map về TASK_STATUS_CHANGED để tương thích ngược.
     */
    public NotificationType getType() {
        if (typeParsed != null) return typeParsed;
        if (typeRaw == null) return NotificationType.SYSTEM_ALERT;
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
    public void setType(NotificationType v) { this.typeParsed = v; }

    // ── Display content (delegate sang NotificationFormatter) ───────────

    /**
     * Lấy nội dung HTML để hiển thị.
     * Gọi buildDisplayContent() nếu chưa có cache.
     */
    public String getContent() {
        if (displayContent != null) return displayContent;
        return buildDisplayContent();
    }

    /** Build và cache nội dung hiển thị qua NotificationFormatter. */
    public String buildDisplayContent() {
        displayContent = NotificationFormatter.format(this);
        return displayContent;
    }

    public void setDisplayContent(String v) { this.displayContent = v; }

    /** Text ngữ cảnh (tên project / task) hiển thị bên dưới nội dung. */
    public String getContextText() {
        return NotificationFormatter.formatContextText(this);
    }
}
