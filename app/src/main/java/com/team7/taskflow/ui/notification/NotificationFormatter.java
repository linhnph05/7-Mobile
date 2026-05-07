package com.team7.taskflow.ui.notification;

import com.team7.taskflow.domain.model.Notification;
import com.team7.taskflow.domain.model.TaskActivity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Chịu trách nhiệm duy nhất: chuyển đổi dữ liệu Notification thành chuỗi HTML hiển thị.
 *
 * Tách khỏi Notification.java (domain model) để đảm bảo SRP:
 * domain model chỉ chứa dữ liệu, formatter chứa logic trình bày.
 */
public final class NotificationFormatter {

    private NotificationFormatter() {}

    /** Tạo chuỗi HTML hiển thị cho notification. Không bao giờ trả về null. */
    public static String format(Notification n) {
        String actor = "<b>" + n.getActorName() + "</b>";
        boolean vi = isVietnamese();

        switch (n.getType()) {
            case PROJECT_INVITE:
                return actor + (vi
                        ? " đã mời bạn tham gia dự án."
                        : " invited you to join a project.");
            case TASK_ASSIGNED:
                return actor + (vi
                        ? " đã giao một công việc cho bạn."
                        : " assigned a task to you.");
            case MENTION:
                return actor + (vi
                        ? " đã nhắc đến bạn trong một công việc."
                        : " mentioned you in a task.");
            case COMMENT:
                return actor + (vi
                        ? " đã bình luận về một công việc."
                        : " commented on a task.");
            case TASK_STATUS_CHANGED:
                TaskActivity detail = n.getActivityDetail();
                if (detail != null && detail.getActionType() != null) {
                    return formatActivityContent(actor, detail);
                }
                return actor + (vi ? " đã cập nhật công việc." : " updated task.");
            case REACTION:
                return actor + (vi
                        ? " đã phản ứng với bình luận của bạn."
                        : " reacted to your comment.");
            case DELETED:
                return actor + (vi
                        ? " đã thu hồi một phản ứng."
                        : " withdrew a reaction.");
            case ATTACHMENT_ADDED:
                return actor + (vi
                        ? " đã thêm một tệp đính kèm."
                        : " added an attachment.");
            case DEADLINE_REMINDER:
                return vi ? "Một công việc sắp đến hạn!" : "A task is due soon!";
            case SYSTEM_ALERT:
                return vi ? "Cảnh báo hệ thống" : "System alert";
            default:
                return vi ? "Bạn có một thông báo mới." : "You have a new notification.";
        }
    }

    /** Tạo text ngữ cảnh (tên project hoặc task) để hiển thị bên dưới content. */
    public static String formatContextText(Notification n) {
        String ref = (n.getReferenceName() != null && !n.getReferenceName().isEmpty())
                ? n.getReferenceName() : "";
        boolean vi = isVietnamese();
        String projectPrefix = vi ? "Dự án: " : "Project: ";
        String taskPrefix    = vi ? "Công việc: " : "Task: ";

        switch (n.getType()) {
            case PROJECT_INVITE:
                return projectPrefix + ref;
            case TASK_ASSIGNED:
            case MENTION:
            case COMMENT:
            case TASK_STATUS_CHANGED:
            case REACTION:
            case DELETED:
            case ATTACHMENT_ADDED:
            case DEADLINE_REMINDER:
                return taskPrefix + ref;
            default:
                return "";
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private static String formatActivityContent(String actor, TaskActivity activity) {
        String actionType = activity.getActionType().toUpperCase(Locale.US).trim();
        switch (actionType) {
            case "UPDATE_STATUS":
                return formatStatusChange(actor, activity);
            case "UPDATE_DUE_DATE":
                return formatDueDateChange(actor, activity);
            case "UPDATE_TIME":
            case "UPDATE_DATETIME":
            case "UPDATE_DATE_TIME":
            case "UPDATE_START_AND_DUE_DATE":
            case "UPDATE_TIME_RANGE":
                return formatDateTimeChange(actor, activity);
            case "UPDATE_PRIORITY":
                return formatPriorityChange(actor, activity);
            case "UPDATE_START_DATE":
                return formatStartDateChange(actor, activity);
            case "UPDATE_TITLE":
                return formatTitleChange(actor, activity);
            case "UPDATE_ASSIGNEE":
                return formatAssigneeChange(actor, activity);
            case "UPDATE_TAG":
                return formatTagChange(actor, activity);
            case "UPDATE_DESCRIPTION":
                return isVietnamese()
                        ? actor + " đã thay đổi mô tả công việc."
                        : actor + " changed task description.";
            default:
                return isVietnamese()
                        ? actor + " đã cập nhật công việc."
                        : actor + " updated task.";
        }
    }

    private static String formatStatusChange(String actor, TaskActivity a) {
        String oldVal = a.getOldValue();
        String newVal = a.getNewValue();
        boolean vi = isVietnamese();
        if (oldVal != null && newVal != null) {
            return vi
                    ? actor + " đã đổi trạng thái từ <b>" + escapeHtml(oldVal) + "</b> sang <b>" + escapeHtml(newVal) + "</b>."
                    : actor + " changed status from <b>" + escapeHtml(oldVal) + "</b> to <b>" + escapeHtml(newVal) + "</b>.";
        }
        String val = newVal != null ? escapeHtml(newVal) : "?";
        return vi
                ? actor + " đã đổi trạng thái công việc thành <b>" + val + "</b>."
                : actor + " changed task status to <b>" + val + "</b>.";
    }

    private static String formatDueDateChange(String actor, TaskActivity a) {
        String oldVal = a.getOldValue();
        String newVal = a.getNewValue();
        boolean vi = isVietnamese();
        if (newVal != null && !newVal.isEmpty()) {
            return vi
                    ? actor + " đã đổi hạn chót thành <b>" + formatDate(newVal) + "</b>."
                    : actor + " changed due date to <b>" + formatDate(newVal) + "</b>.";
        }
        if (oldVal != null && !oldVal.isEmpty()) {
            return vi ? actor + " đã xóa hạn chót." : actor + " removed the due date.";
        }
        return vi ? actor + " đã thay đổi hạn chót." : actor + " changed the due date.";
    }

    private static String formatStartDateChange(String actor, TaskActivity a) {
        String oldVal = a.getOldValue();
        String newVal = a.getNewValue();
        boolean vi = isVietnamese();
        if (newVal != null && !newVal.isEmpty()) {
            return vi
                    ? actor + " đã đổi ngày bắt đầu thành <b>" + formatDate(newVal) + "</b>."
                    : actor + " changed start date to <b>" + formatDate(newVal) + "</b>.";
        }
        if (oldVal != null && !oldVal.isEmpty()) {
            return vi ? actor + " đã xóa ngày bắt đầu." : actor + " removed the start date.";
        }
        return vi ? actor + " đã thay đổi ngày bắt đầu." : actor + " changed the start date.";
    }

    private static String formatDateTimeChange(String actor, TaskActivity a) {
        String oldVal = a.getOldValue();
        String newVal = a.getNewValue();
        boolean vi = isVietnamese();
        if (newVal != null && !newVal.isEmpty()) {
            return vi
                    ? actor + " đã đổi thời gian công việc thành <b>" + formatDate(newVal) + "</b>."
                    : actor + " changed task time to <b>" + formatDate(newVal) + "</b>.";
        }
        if (oldVal != null && !oldVal.isEmpty()) {
            return vi ? actor + " đã xóa thời gian công việc." : actor + " removed task time.";
        }
        return vi ? actor + " đã thay đổi thời gian công việc." : actor + " changed task time.";
    }

    private static String formatPriorityChange(String actor, TaskActivity a) {
        String oldVal = a.getOldValue();
        String newVal = a.getNewValue();
        boolean vi = isVietnamese();
        boolean hasNew = newVal != null && !newVal.isEmpty();
        boolean hasOld = oldVal != null && !oldVal.isEmpty();
        if (hasNew && !hasOld) {
            return vi
                    ? actor + " đã thêm mức ưu tiên <b>" + escapeHtml(newVal) + "</b>."
                    : actor + " added priority <b>" + escapeHtml(newVal) + "</b>.";
        }
        if (!hasNew && hasOld) {
            return vi
                    ? actor + " đã bỏ mức ưu tiên <b>" + escapeHtml(oldVal) + "</b>."
                    : actor + " removed priority <b>" + escapeHtml(oldVal) + "</b>.";
        }
        if (hasNew) {
            return vi
                    ? actor + " đã đổi mức ưu tiên thành <b>" + escapeHtml(newVal) + "</b>."
                    : actor + " changed priority to <b>" + escapeHtml(newVal) + "</b>.";
        }
        return vi ? actor + " đã thay đổi mức ưu tiên công việc." : actor + " changed task priority.";
    }

    private static String formatTitleChange(String actor, TaskActivity a) {
        String newVal = a.getNewValue();
        boolean vi = isVietnamese();
        if (newVal != null && !newVal.isEmpty()) {
            return vi
                    ? actor + " đã đổi tiêu đề công việc thành <b>" + escapeHtml(newVal) + "</b>."
                    : actor + " changed task title to <b>" + escapeHtml(newVal) + "</b>.";
        }
        return vi ? actor + " đã thay đổi tiêu đề công việc." : actor + " changed task title.";
    }

    private static String formatAssigneeChange(String actor, TaskActivity a) {
        String oldVal = a.getOldValue();
        String newVal = a.getNewValue();
        boolean vi = isVietnamese();
        if (newVal != null && !newVal.isEmpty()) {
            return vi
                    ? actor + " đã giao công việc cho <b>" + escapeHtml(newVal) + "</b>."
                    : actor + " assigned task to <b>" + escapeHtml(newVal) + "</b>.";
        }
        if (oldVal != null && !oldVal.isEmpty()) {
            return vi ? actor + " đã gỡ người được giao công việc." : actor + " removed task assignment.";
        }
        return vi ? actor + " đã thay đổi người được giao công việc." : actor + " changed task assignee.";
    }

    private static String formatTagChange(String actor, TaskActivity a) {
        String oldVal = a.getOldValue();
        String newVal = a.getNewValue();
        boolean vi = isVietnamese();
        if (newVal != null && !newVal.isEmpty()) {
            return vi
                    ? actor + " đã thêm nhãn <b>" + escapeHtml(newVal) + "</b>."
                    : actor + " added tag <b>" + escapeHtml(newVal) + "</b>.";
        }
        if (oldVal != null && !oldVal.isEmpty()) {
            return vi
                    ? actor + " đã gỡ nhãn <b>" + escapeHtml(oldVal) + "</b>."
                    : actor + " removed tag <b>" + escapeHtml(oldVal) + "</b>.";
        }
        return vi ? actor + " đã thay đổi nhãn công việc." : actor + " changed task tag.";
    }

    // ── Date formatting ──────────────────────────────────────────────────

    /** Chuyển chuỗi ISO date/datetime thành "dd/MM/yyyy [HH:mm]" để hiển thị. */
    static String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return dateStr != null ? dateStr : "";
        String normalized = dateStr.trim().replace('T', ' ');
        String datePart = extractDatePart(normalized);
        if (datePart == null) return normalized;
        String formatted = reformatDatePart(datePart);
        String timePart = extractTimePart(normalized);
        return timePart == null ? formatted : formatted + " " + timePart;
    }

    private static String extractDatePart(String value) {
        if (value == null || value.length() < 10) return null;
        try {
            String candidate = value.substring(0, 10);
            LocalDate.parse(candidate, DateTimeFormatter.ISO_LOCAL_DATE);
            return candidate;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String reformatDatePart(String datePart) {
        try {
            return LocalDate.parse(datePart, DateTimeFormatter.ISO_LOCAL_DATE)
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception ignored) {
            return datePart;
        }
    }

    private static String extractTimePart(String value) {
        if (value == null || value.length() <= 10) return null;
        try {
            String raw = value.substring(11).trim();
            if (raw.isEmpty()) return null;
            return raw.length() >= 5 ? raw.substring(0, 5) : raw;
        } catch (Exception ignored) {
            return null;
        }
    }

    // ── Security ─────────────────────────────────────────────────────────

    /** Escape HTML để tránh XSS khi render bằng Html.fromHtml(). */
    static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    // ── Locale ───────────────────────────────────────────────────────────

    private static boolean isVietnamese() {
        Locale locale = Locale.getDefault();
        if (locale == null) return false;
        String lang = locale.getLanguage();
        return lang != null && lang.toLowerCase(Locale.US).startsWith("vi");
    }
}
