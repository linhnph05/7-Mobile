package com.team7.taskflow.ui.project;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.team7.taskflow.R;
import com.team7.taskflow.domain.model.ProjectHistoryItem;
import com.team7.taskflow.ui.common.AvatarUiUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;

public class HistoryEventAdapter extends BaseAdapter {

    private final LayoutInflater inflater;
    private final List<?> rows;

    public HistoryEventAdapter(Context context, List<?> rows) {
        this.inflater = LayoutInflater.from(context);
        this.rows = rows;
    }

    @Override
    public int getCount() {
        return rows != null ? rows.size() : 0;
    }

    @Override
    public Object getItem(int position) {
        if (rows == null || position < 0 || position >= rows.size()) {
            return null;
        }
        return rows.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_history_event, parent, false);
            holder = new ViewHolder(convertView);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Object item = rows.get(position);
        bindRow(holder, item);
        return convertView;
    }

    private void bindRow(ViewHolder holder, Object item) {
        if (item == null) {
            holder.tvActor.setText("Unknown");
            holder.tvAction.setText("Cap nhat");
            holder.tvMeta.setText("Vua xong");
            holder.tvComment.setVisibility(View.GONE);
            holder.tvDetail.setVisibility(View.GONE);
            holder.layoutStatusTransition.setVisibility(View.GONE);
            holder.bindAvatar(null, "?");
            holder.viewAccent.setCardBackgroundColor(
                    ContextCompat.getColor(inflater.getContext(), R.color.theme_text_secondary));
            return;
        }

        if (item instanceof String) {
            bindLegacyRow(holder, (String) item);
            return;
        }

        if (!(item instanceof ProjectHistoryItem)) {
            bindLegacyRow(holder, String.valueOf(item));
            return;
        }

        ProjectHistoryItem historyItem = (ProjectHistoryItem) item;

        String actor = historyItem.getActorName() != null && !historyItem.getActorName().trim().isEmpty()
                ? historyItem.getActorName().trim()
                : "Unknown";
        String taskTitle = historyItem.getTaskTitle() != null ? historyItem.getTaskTitle().trim() : "";
        String detail = historyItem.getDetail() != null ? historyItem.getDetail().trim() : "";

        String rawActionType = historyItem.getRawActionType() != null
                ? historyItem.getRawActionType().trim().toUpperCase(Locale.US)
                : "";
        String actionLabel = historyItem.getActionLabel();
        if ("UPDATE_STATUS".equals(rawActionType) && historyItem.getNewValue() != null &&
                ("TRASH".equalsIgnoreCase(historyItem.getNewValue())
                        || "DELETED".equalsIgnoreCase(historyItem.getNewValue()))) {
            actionLabel = "đã xóa công việc";
        }

        int accentColor = resolveAccentColor(historyItem.getSource(), historyItem.getRawActionType(), detail);
        holder.viewAccent.setCardBackgroundColor(accentColor);
        holder.tvActor.setText(actor);
        holder.bindAvatar(historyItem.getAvatarUrl(), resolveAvatarLetter(actor));
        holder.tvAction.setText(buildActionLine(actionLabel, taskTitle));

        String source = historyItem.getSource();
        if (ProjectHistoryItem.SOURCE_TASK_ACTIVITY.equals(source)) {
            holder.tvMeta.setText(formatTaskHistoryTime(historyItem.getCreatedAt()));
        } else {
            holder.tvMeta.setText(formatRelativeTime(historyItem.getCreatedAt()));
        }

        String comment = historyItem.getCommentContent() != null ? historyItem.getCommentContent().trim() : "";
        if (comment.isEmpty()) {
            holder.tvComment.setVisibility(View.GONE);
        } else {
            holder.tvComment.setVisibility(View.VISIBLE);
            holder.tvComment.setText(comment);
        }

        boolean hideDetail = detail.isEmpty()
                || "Noi dung binh luan".equalsIgnoreCase(detail)
                || shouldHideTaskUpdateDetail(historyItem);

        boolean isStatusChange = ("UPDATE_STATUS".equals(rawActionType) || "RESTORE".equals(rawActionType)
                || "DELETE".equals(rawActionType) || "TRASH".equals(rawActionType));

        if (isStatusChange && historyItem.getNewValue() != null && ("TRASH".equalsIgnoreCase(historyItem.getNewValue())
                || "DELETED".equalsIgnoreCase(historyItem.getNewValue()))) {
            isStatusChange = false; // Hide status badge if moving to trash
        }

        if (isStatusChange && historyItem.getOldValue() != null && historyItem.getNewValue() != null) {
            holder.tvDetail.setVisibility(View.GONE);
            holder.layoutStatusTransition.setVisibility(View.VISIBLE);
            bindStatusBadge(holder.tvOldStatus, historyItem.getOldValue());
            bindStatusBadge(holder.tvNewStatus, historyItem.getNewValue());
        } else {
            holder.layoutStatusTransition.setVisibility(View.GONE);
            if (hideDetail) {
                holder.tvDetail.setVisibility(View.GONE);
            } else {
                holder.tvDetail.setVisibility(View.VISIBLE);
                holder.tvDetail.setText(detail);
            }
        }
    }

    private void bindLegacyRow(ViewHolder holder, String raw) {
        String time = "Vua xong";
        String action = raw;
        String detail = "";

        String[] splitDash = raw.split(" - ", 2);
        if (splitDash.length == 2) {
            time = splitDash[0].trim();
            String right = splitDash[1].trim();

            int open = right.indexOf('(');
            int close = right.lastIndexOf(')');
            if (open > 0 && close > open) {
                action = right.substring(0, open).trim();
                detail = right.substring(open + 1, close).trim();
            } else {
                action = right;
            }
        }

        int accentColor = resolveLegacyAccentColor(action, detail);
        holder.viewAccent.setCardBackgroundColor(accentColor);
        holder.bindAvatar(null, "H");
        holder.tvActor.setText("History");
        holder.tvAction.setText(prettyActionLabel(action));
        holder.tvMeta.setText(time);
        holder.tvComment.setVisibility(View.GONE);

        holder.layoutStatusTransition.setVisibility(View.GONE);
        if (detail.isEmpty()) {
            holder.tvDetail.setVisibility(View.GONE);
        } else {
            holder.tvDetail.setVisibility(View.VISIBLE);
            holder.tvDetail.setText(detail);
        }
    }

    private String prettyActionLabel(String actionRaw) {
        if (actionRaw == null || actionRaw.trim().isEmpty()) {
            return "Cap nhat";
        }

        String normalized = actionRaw.trim().toUpperCase(Locale.US);
        if (normalized.contains("STATUS_CHANGED")) {
            return "Doi trang thai";
        }
        if (normalized.contains("COMMENT")) {
            return "Binh luan";
        }
        if (normalized.contains("DELETE") || normalized.contains("TRASH")) {
            return "Xoa / Thung rac";
        }
        if (normalized.contains("CREATE")) {
            return "Tao moi";
        }
        if (normalized.contains("UPDATE") || normalized.contains("EDIT")) {
            return "Chinh sua";
        }
        return normalized.replace('_', ' ');
    }

    private String buildActionLine(String actionLabel, String taskTitle) {
        String action = actionLabel != null && !actionLabel.trim().isEmpty()
                ? actionLabel.trim()
                : inflater.getContext().getString(R.string.task_history_action_updated);
        if (taskTitle == null || taskTitle.isEmpty()) {
            return action;
        }
        return inflater.getContext().getString(R.string.task_history_action_in_task_format, action, taskTitle);
    }

    private String resolveAvatarLetter(String actor) {
        if (actor == null || actor.trim().isEmpty()) {
            return "?";
        }
        return actor.trim().substring(0, 1).toUpperCase(Locale.US);
    }

    private int resolveAccentColor(String source, String rawActionType, String detailRaw) {
        String action = rawActionType != null ? rawActionType.toUpperCase(Locale.US) : "";
        String detail = detailRaw != null ? detailRaw.toUpperCase(Locale.US) : "";
        String normalizedSource = source != null ? source.toUpperCase(Locale.US) : "";

        if (ProjectHistoryItem.SOURCE_COMMENT.equals(normalizedSource)
                || action.contains("COMMENT")) {
            return ContextCompat.getColor(inflater.getContext(), R.color.indigo_600);
        }

        if (action.contains("UPDATE_STATUS") || action.contains("RESTORE")) {
            if (detail.contains("-> TRASH")) {
                return ContextCompat.getColor(inflater.getContext(), R.color.text_red_600);
            }
            if (detail.contains("TRASH ->") || action.contains("RESTORE")) {
                return ContextCompat.getColor(inflater.getContext(), R.color.success);
            }
            if (detail.contains("-> DONE")) {
                return ContextCompat.getColor(inflater.getContext(), R.color.success);
            }
            if (detail.contains("-> IN_PROGRESS") || detail.contains("-> DOING")) {
                return ContextCompat.getColor(inflater.getContext(), R.color.primary);
            }
            return ContextCompat.getColor(inflater.getContext(), R.color.warning);
        }

        if (action.contains("DELETE") || action.contains("TRASH")) {
            return ContextCompat.getColor(inflater.getContext(), R.color.text_red_600);
        }

        if (action.contains("MEMBER_REMOVED") || action.contains("MEMBER_LEFT")) {
            return ContextCompat.getColor(inflater.getContext(), R.color.text_red_600);
        }

        if (action.contains("MEMBER_ADD") || action.contains("MEMBER_JOIN")) {
            return ContextCompat.getColor(inflater.getContext(), R.color.success);
        }

        if (action.contains("CREATE")) {
            return ContextCompat.getColor(inflater.getContext(), R.color.success);
        }

        if (action.contains("UPDATE")) {
            if (isNightMode()) {
                return ContextCompat.getColor(inflater.getContext(), R.color.theme_text_primary);
            }
            return ContextCompat.getColor(inflater.getContext(), R.color.theme_text_secondary);
        }

        if (isNightMode()) {
            return ContextCompat.getColor(inflater.getContext(), R.color.theme_text_secondary);
        }
        return ContextCompat.getColor(inflater.getContext(), R.color.theme_text_secondary);
    }

    private int resolveLegacyAccentColor(String actionRaw, String detailRaw) {
        String action = actionRaw != null ? actionRaw.toUpperCase(Locale.US) : "";
        String detail = detailRaw != null ? detailRaw.toUpperCase(Locale.US) : "";

        if (action.contains("STATUS_CHANGED")) {
            if (detail.contains("-> TRASH")) {
                return ContextCompat.getColor(inflater.getContext(), R.color.text_red_600);
            }
            if (detail.contains("TRASH ->")) {
                return ContextCompat.getColor(inflater.getContext(), R.color.success);
            }
            if (detail.contains("-> DONE")) {
                return ContextCompat.getColor(inflater.getContext(), R.color.success);
            }
            if (detail.contains("-> IN_PROGRESS") || detail.contains("-> DOING")) {
                return ContextCompat.getColor(inflater.getContext(), R.color.indigo_600);
            }
            return ContextCompat.getColor(inflater.getContext(), R.color.warning);
        }

        if (action.contains("COMMENT")) {
            return ContextCompat.getColor(inflater.getContext(), R.color.indigo_600);
        }

        if (action.contains("DELETE") || action.contains("TRASH")) {
            return ContextCompat.getColor(inflater.getContext(), R.color.text_red_600);
        }

        if (action.contains("CREATE")) {
            return ContextCompat.getColor(inflater.getContext(), R.color.success);
        }

        if (action.contains("UPDATE") || action.contains("EDIT")) {
            if (isNightMode()) {
                return ContextCompat.getColor(inflater.getContext(), R.color.theme_text_primary);
            }
            return ContextCompat.getColor(inflater.getContext(), R.color.theme_text_secondary);
        }

        if (isNightMode()) {
            return ContextCompat.getColor(inflater.getContext(), R.color.theme_text_secondary);
        }
        return ContextCompat.getColor(inflater.getContext(), R.color.theme_text_secondary);
    }

    private boolean isNightMode() {
        int nightMode = inflater.getContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    private String formatRelativeTime(String rawTime) {
        if (rawTime == null || rawTime.trim().isEmpty()) {
            return "Vua xong";
        }

        try {
            Instant created;
            if (rawTime.contains("T")) {
                created = OffsetDateTime.parse(rawTime).toInstant();
            } else {
                created = Instant.ofEpochMilli(Long.parseLong(rawTime));
            }
            Instant now = Instant.now();
            if (created.isAfter(now)) {
                return "Vua xong";
            }

            Duration duration = Duration.between(created, now);
            long minutes = duration.toMinutes();

            ZoneId zone = ZoneId.systemDefault();
            java.time.LocalDate createdDate = created.atZone(zone).toLocalDate();
            java.time.LocalDate currentDate = now.atZone(zone).toLocalDate();

            if (createdDate.equals(currentDate)) {
                if (minutes < 1) {
                    return "Vua xong";
                }
                if (minutes < 60) {
                    return minutes + " phut truoc";
                }
                long hours = duration.toHours();
                return hours + " gio truoc";
            }

            if (createdDate.equals(currentDate.minusDays(1))) {
                return "Hom qua";
            }

            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                    .ofPattern("dd/MM/yyyy HH:mm");
            return created.atZone(zone).format(formatter);
        } catch (Exception ignored) {
            return rawTime;
        }
    }

    private String formatTaskHistoryTime(String rawTime) {
        if (rawTime == null || rawTime.trim().isEmpty()) {
            return inflater.getContext().getString(R.string.task_history_time_just_now);
        }

        try {
            Instant created = OffsetDateTime.parse(rawTime).toInstant();
            Instant now = Instant.now();
            if (created.isAfter(now)) {
                return inflater.getContext().getString(R.string.task_history_time_just_now);
            }

            Duration duration = Duration.between(created, now);
            long minutes = duration.toMinutes();

            ZoneId zone = ZoneId.systemDefault();
            java.time.LocalDate createdDate = created.atZone(zone).toLocalDate();
            java.time.LocalDate currentDate = now.atZone(zone).toLocalDate();

            if (createdDate.equals(currentDate)) {
                if (minutes < 1) {
                    return inflater.getContext().getString(R.string.task_history_time_just_now);
                }
                if (minutes < 60) {
                    return inflater.getContext().getString(R.string.task_history_time_minutes_ago, minutes);
                }
                long hours = duration.toHours();
                return inflater.getContext().getString(R.string.task_history_time_hours_ago, hours);
            }

            if (createdDate.equals(currentDate.minusDays(1))) {
                return inflater.getContext().getString(R.string.task_history_time_yesterday);
            }

            long days = ChronoUnit.DAYS.between(createdDate, currentDate);
            if (days <= 0) {
                return inflater.getContext().getString(R.string.task_history_time_just_now);
            }
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                    .ofPattern("dd/MM/yyyy HH:mm");
            return created.atZone(zone).format(formatter);
        } catch (Exception ignored) {
            return formatRelativeTime(rawTime);
        }
    }

    private boolean shouldHideTaskUpdateDetail(ProjectHistoryItem historyItem) {
        if (historyItem == null
                || !ProjectHistoryItem.SOURCE_TASK_ACTIVITY.equals(historyItem.getSource())) {
            return false;
        }

        String action = historyItem.getActionLabel();
        if (action == null || action.trim().isEmpty()) {
            return false;
        }

        String normalized = action.trim().toUpperCase(Locale.US);
        return normalized.contains("CHINH SUA") || normalized.contains("UPDATE") || normalized.contains("CAP NHAT");
    }

    private void bindStatusBadge(TextView tv, String status) {
        if (status == null)
            status = "TODO";
        String normalized = status.trim().toUpperCase(Locale.US);
        if ("DONE".equals(normalized)) {
            tv.setText(inflater.getContext().getString(R.string.task_status_done));
            tv.setBackgroundResource(R.drawable.bg_badge_green);
            tv.setTextColor(ContextCompat.getColor(inflater.getContext(), R.color.success));
        } else if ("IN_PROGRESS".equals(normalized) || "DOING".equals(normalized)) {
            tv.setText(inflater.getContext().getString(R.string.task_status_in_progress));
            tv.setBackgroundResource(R.drawable.bg_badge_blue);
            tv.setTextColor(ContextCompat.getColor(inflater.getContext(), R.color.primary));
        } else if ("TRASH".equals(normalized) || "DELETED".equals(normalized)) {
            tv.setText("Trash");
            tv.setBackgroundResource(R.drawable.bg_badge_red);
            tv.setTextColor(ContextCompat.getColor(inflater.getContext(), R.color.text_red_600));
        } else {
            tv.setText(inflater.getContext().getString(R.string.task_status_todo));
            tv.setBackgroundResource(R.drawable.bg_badge_neutral);
            tv.setTextColor(ContextCompat.getColor(inflater.getContext(), R.color.slate_700));
            if (isNightMode()) {
                tv.setTextColor(ContextCompat.getColor(inflater.getContext(), R.color.slate_300));
            }
        }
    }

    private static class ViewHolder {
        final com.google.android.material.card.MaterialCardView viewAccent;
        final ImageView imgAvatar;
        final TextView tvAvatarLetter;
        final TextView tvActor;
        final TextView tvAction;
        final TextView tvMeta;
        final TextView tvComment;
        final TextView tvDetail;
        final View layoutStatusTransition;
        final TextView tvOldStatus;
        final TextView tvNewStatus;

        ViewHolder(View itemView) {
            viewAccent = itemView.findViewById(R.id.viewHistoryAccent);
            imgAvatar = itemView.findViewById(R.id.imgHistoryAvatar);
            tvAvatarLetter = itemView.findViewById(R.id.tvHistoryAvatarLetter);
            tvActor = itemView.findViewById(R.id.tvHistoryActor);
            tvAction = itemView.findViewById(R.id.tvHistoryAction);
            tvMeta = itemView.findViewById(R.id.tvHistoryMeta);
            tvComment = itemView.findViewById(R.id.tvHistoryComment);
            tvDetail = itemView.findViewById(R.id.tvHistoryDetail);
            layoutStatusTransition = itemView.findViewById(R.id.layoutStatusTransition);
            tvOldStatus = itemView.findViewById(R.id.tvOldStatus);
            tvNewStatus = itemView.findViewById(R.id.tvNewStatus);
        }

        void bindAvatar(String avatarUrl, String fallbackLetter) {
            if (imgAvatar == null) {
                return;
            }
            imgAvatar.setVisibility(View.VISIBLE);
            AvatarUiUtils.bindAvatarOrFallback(imgAvatar, tvAvatarLetter, avatarUrl, fallbackLetter);
        }
    }
}
