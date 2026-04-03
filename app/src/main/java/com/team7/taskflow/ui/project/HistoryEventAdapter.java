package com.team7.taskflow.ui.project;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.team7.taskflow.R;
import com.team7.taskflow.domain.model.ProjectHistoryItem;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
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
            holder.bindAvatar(null, "?");
            holder.viewAccent.setBackgroundColor(Color.parseColor("#475569"));
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

        int accentColor = resolveAccentColor(historyItem.getSource(), historyItem.getActionLabel(), detail);
        holder.viewAccent.setBackgroundColor(accentColor);
        holder.tvActor.setText(actor);
        holder.bindAvatar(historyItem.getAvatarUrl(), resolveAvatarLetter(actor));
        holder.tvAction.setText(buildActionLine(historyItem.getActionLabel(), taskTitle));
        holder.tvAction.setTextColor(accentColor);
        holder.tvMeta.setText(formatRelativeTime(historyItem.getCreatedAt()));

        String comment = historyItem.getCommentContent() != null ? historyItem.getCommentContent().trim() : "";
        if (comment.isEmpty()) {
            holder.tvComment.setVisibility(View.GONE);
        } else {
            holder.tvComment.setVisibility(View.VISIBLE);
            holder.tvComment.setText(comment);
        }

        if (detail.isEmpty() || "Noi dung binh luan".equalsIgnoreCase(detail)) {
            holder.tvDetail.setVisibility(View.GONE);
        } else {
            holder.tvDetail.setVisibility(View.VISIBLE);
            holder.tvDetail.setText(detail);
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
        holder.viewAccent.setBackgroundColor(accentColor);
        holder.bindAvatar(null, "H");
        holder.tvActor.setText("History");
        holder.tvAction.setText(prettyActionLabel(action));
        holder.tvAction.setTextColor(accentColor);
        holder.tvMeta.setText(time);
        holder.tvComment.setVisibility(View.GONE);

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
                : "da cap nhat";
        if (taskTitle == null || taskTitle.isEmpty()) {
            return action;
        }
        return action + " trong " + taskTitle;
    }

    private String resolveAvatarLetter(String actor) {
        if (actor == null || actor.trim().isEmpty()) {
            return "?";
        }
        return actor.trim().substring(0, 1).toUpperCase(Locale.US);
    }

    private int resolveAccentColor(String source, String actionRaw, String detailRaw) {
        String action = actionRaw != null ? actionRaw.toUpperCase(Locale.US) : "";
        String detail = detailRaw != null ? detailRaw.toUpperCase(Locale.US) : "";
        String normalizedSource = source != null ? source.toUpperCase(Locale.US) : "";

        if (ProjectHistoryItem.SOURCE_COMMENT.equals(normalizedSource)
                || action.contains("BINH LUAN")
                || action.contains("COMMENT")) {
            return ContextCompat.getColor(inflater.getContext(), R.color.indigo_600);
        }

        if (action.contains("DOI TRANG THAI")) {
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

        if (action.contains("XOA") || action.contains("TRASH")) {
            return ContextCompat.getColor(inflater.getContext(), R.color.text_red_600);
        }

        if (action.contains("MEMBER_REMOVED") || action.contains("MEMBER_LEFT") || action.contains("XOA THANH VIEN")) {
            return ContextCompat.getColor(inflater.getContext(), R.color.text_red_600);
        }

        if (action.contains("MEMBER") || action.contains("THANH VIEN") || action.contains("JOIN")) {
            return ContextCompat.getColor(inflater.getContext(), R.color.success);
        }

        if (action.contains("TAO") || action.contains("CREATE")) {
            return ContextCompat.getColor(inflater.getContext(), R.color.success);
        }

        if (action.contains("CHINH SUA") || action.contains("CAP NHAT") || action.contains("UPDATE")) {
            return ContextCompat.getColor(inflater.getContext(), R.color.slate_700);
        }

        return Color.parseColor("#475569");
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
            return ContextCompat.getColor(inflater.getContext(), R.color.slate_700);
        }

        return Color.parseColor("#475569");
    }

    private String formatRelativeTime(String rawTime) {
        if (rawTime == null || rawTime.trim().isEmpty()) {
            return "Vua xong";
        }

        try {
            Instant created = OffsetDateTime.parse(rawTime).toInstant();
            Duration duration = Duration.between(created, Instant.now());
            long minutes = duration.toMinutes();
            if (minutes < 1) {
                return "Vua xong";
            }
            if (minutes < 60) {
                return minutes + " phut truoc";
            }
            long hours = duration.toHours();
            if (hours < 24) {
                return hours + " gio truoc";
            }
            if (hours < 48) {
                return "Hom qua";
            }
            Date date = Date.from(created);
            return new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(date);
        } catch (Exception ignored) {
            try {
                return DateTimeFormatter.ofPattern("dd/MM HH:mm").format(OffsetDateTime.parse(rawTime));
            } catch (Exception secondIgnored) {
                return rawTime;
            }
        }
    }

    private static class ViewHolder {
        final View viewAccent;
        final ImageView imgAvatar;
        final TextView tvAvatarLetter;
        final TextView tvActor;
        final TextView tvAction;
        final TextView tvMeta;
        final TextView tvComment;
        final TextView tvDetail;

        ViewHolder(View itemView) {
            viewAccent = itemView.findViewById(R.id.viewHistoryAccent);
            imgAvatar = itemView.findViewById(R.id.imgHistoryAvatar);
            tvAvatarLetter = itemView.findViewById(R.id.tvHistoryAvatarLetter);
            tvActor = itemView.findViewById(R.id.tvHistoryActor);
            tvAction = itemView.findViewById(R.id.tvHistoryAction);
            tvMeta = itemView.findViewById(R.id.tvHistoryMeta);
            tvComment = itemView.findViewById(R.id.tvHistoryComment);
            tvDetail = itemView.findViewById(R.id.tvHistoryDetail);
        }

        void bindAvatar(String avatarUrl, String fallbackLetter) {
            String letter = fallbackLetter != null && !fallbackLetter.trim().isEmpty() ? fallbackLetter.trim() : "?";
            if (avatarUrl != null && !avatarUrl.trim().isEmpty() && imgAvatar != null) {
                imgAvatar.setVisibility(View.VISIBLE);
                tvAvatarLetter.setVisibility(View.GONE);
                Glide.with(imgAvatar)
                        .load(avatarUrl)
                        .circleCrop()
                        .into(imgAvatar);
            } else {
                if (imgAvatar != null) {
                    imgAvatar.setImageDrawable(null);
                    imgAvatar.setVisibility(View.VISIBLE);
                }
                tvAvatarLetter.setVisibility(View.VISIBLE);
                tvAvatarLetter.setText(letter);
            }
        }
    }
}
