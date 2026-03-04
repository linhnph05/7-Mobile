package com.team7.taskflow.ui.notification;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.team7.taskflow.R;
import com.team7.taskflow.domain.model.Notification;
import com.team7.taskflow.domain.model.Notification.NotificationType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Multi-view-type RecyclerView Adapter for Notifications.
 *
 * Maps 9 database NotificationTypes into 3 ViewTypes:
 * VIEW_TYPE_INVITE ← PROJECT_INVITE
 * VIEW_TYPE_ME ← TASK_ASSIGNED, MENTION, COMMENT, TASK_COMPLETED, REACTION,
 * ATTACHMENT_ADDED
 * VIEW_TYPE_REMINDER ← DEADLINE_REMINDER, SYSTEM_ALERT
 */
public class NotificationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_INVITE = 1;
    private static final int VIEW_TYPE_ME = 2;
    private static final int VIEW_TYPE_REMINDER = 3;

    private List<Notification> notificationList = new ArrayList<>();
    private final OnNotificationActionListener listener;

    // ── Listener interface ──────────────────────────────────────────

    public interface OnNotificationActionListener {
        void onNotificationClick(Notification notification);

        void onAcceptInvite(Notification notification);

        void onDeclineInvite(Notification notification);
    }

    public NotificationAdapter(OnNotificationActionListener listener) {
        this.listener = listener;
    }

    public void setNotifications(List<Notification> notifications) {
        this.notificationList = notifications != null ? notifications : new ArrayList<>();
        notifyDataSetChanged();
    }

    // ── ViewType mapping ────────────────────────────────────────────

    @Override
    public int getItemViewType(int position) {
        NotificationType type = notificationList.get(position).getType();
        switch (type) {
            case PROJECT_INVITE:
                return VIEW_TYPE_INVITE;
            case DEADLINE_REMINDER:
            case SYSTEM_ALERT:
                return VIEW_TYPE_REMINDER;
            default:
                return VIEW_TYPE_ME;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case VIEW_TYPE_INVITE:
                return new InviteViewHolder(
                        inflater.inflate(R.layout.item_notification_invite, parent, false));
            case VIEW_TYPE_REMINDER:
                return new ReminderViewHolder(
                        inflater.inflate(R.layout.item_notification_reminder, parent, false));
            default:
                return new MeViewHolder(
                        inflater.inflate(R.layout.item_notification_me, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Notification notification = notificationList.get(position);

        if (holder instanceof BaseNotificationViewHolder) {
            ((BaseNotificationViewHolder) holder).bind(notification);
        }

        if (holder instanceof InviteViewHolder) {
            ((InviteViewHolder) holder).bindInvite(notification);
        } else if (holder instanceof MeViewHolder) {
            ((MeViewHolder) holder).bindMe(notification);
        } else if (holder instanceof ReminderViewHolder) {
            ((ReminderViewHolder) holder).bindReminder(notification);
        }
    }

    @Override
    public int getItemCount() {
        return notificationList.size();
    }

    // ════════════════════════════════════════════════════════════════
    // BASE VIEW HOLDER
    // ════════════════════════════════════════════════════════════════

    abstract class BaseNotificationViewHolder extends RecyclerView.ViewHolder {
        protected TextView tvContent, tvTime, tvContext;
        protected View viewUnreadDot;

        public BaseNotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_content);
            tvContext = itemView.findViewById(R.id.tv_context);
            tvTime = itemView.findViewById(R.id.tv_time);
            viewUnreadDot = itemView.findViewById(R.id.view_unread_dot);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onNotificationClick(notificationList.get(pos));
                }
            });
        }

        public void bind(Notification notification) {
            // Content (supports HTML bold tags)
            String content = notification.getContent() != null ? notification.getContent() : "";
            tvContent.setText(HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY));

            // Context (e.g. Project Name)
            String context = notification.getContextText();
            if (tvContext != null) {
                if (context != null && !context.isEmpty()) {
                    tvContext.setVisibility(View.VISIBLE);
                    tvContext.setText(context);
                } else {
                    tvContext.setVisibility(View.GONE);
                }
            }

            // Relative time
            tvTime.setText(formatRelativeTime(notification.getCreatedAt()));

            // Unread dot
            viewUnreadDot.setVisibility(notification.isRead() ? View.GONE : View.VISIBLE);

            // Background: unread → brighter, read → darker
            int bgColor = ContextCompat.getColor(itemView.getContext(),
                    notification.isRead() ? R.color.notification_read_bg : R.color.notification_unread_bg);
            itemView.setBackgroundColor(bgColor);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // INVITE VIEW HOLDER
    // ════════════════════════════════════════════════════════════════

    class InviteViewHolder extends BaseNotificationViewHolder {
        ShapeableImageView ivAvatar;
        MaterialButton btnAccept, btnDecline;

        public InviteViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            btnAccept = itemView.findViewById(R.id.btn_accept);
            btnDecline = itemView.findViewById(R.id.btn_decline);
        }

        public void bindInvite(Notification notification) {
            // Avatar — use first letter of actor name as placeholder
            ivAvatar.setImageResource(R.drawable.ic_person);

            btnAccept.setOnClickListener(v -> {
                if (listener != null)
                    listener.onAcceptInvite(notification);
            });
            btnDecline.setOnClickListener(v -> {
                if (listener != null)
                    listener.onDeclineInvite(notification);
            });
        }
    }

    // ════════════════════════════════════════════════════════════════
    // ME VIEW HOLDER (social / task-related)
    // ════════════════════════════════════════════════════════════════

    class MeViewHolder extends BaseNotificationViewHolder {
        ShapeableImageView ivAvatar;
        ImageView ivBadge;

        public MeViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            ivBadge = itemView.findViewById(R.id.iv_badge_comment);
        }

        public void bindMe(Notification notification) {
            ivAvatar.setImageResource(R.drawable.ic_person);

            // Show badge only for COMMENT or MENTION
            NotificationType type = notification.getType();
            if (type == NotificationType.COMMENT || type == NotificationType.MENTION) {
                ivBadge.setVisibility(View.VISIBLE);
                ivBadge.setImageResource(
                        type == NotificationType.COMMENT ? R.drawable.ic_chat : R.drawable.ic_person);
            } else {
                ivBadge.setVisibility(View.GONE);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // REMINDER VIEW HOLDER (system / deadline)
    // ════════════════════════════════════════════════════════════════

    class ReminderViewHolder extends BaseNotificationViewHolder {
        ImageView ivSysIcon;

        public ReminderViewHolder(@NonNull View itemView) {
            super(itemView);
            ivSysIcon = itemView.findViewById(R.id.iv_sys_icon);
        }

        public void bindReminder(Notification notification) {
            NotificationType type = notification.getType();
            if (type == NotificationType.DEADLINE_REMINDER) {
                ivSysIcon.setImageResource(R.drawable.ic_timer);
                ivSysIcon.setImageTintList(
                        ContextCompat.getColorStateList(itemView.getContext(), R.color.warning));
            } else {
                ivSysIcon.setImageResource(R.drawable.ic_notification);
                ivSysIcon.setImageTintList(
                        ContextCompat.getColorStateList(itemView.getContext(), R.color.danger));
            }
        }
    }

    // ── Utility ─────────────────────────────────────────────────────

    /**
     * Format a Date into a human-readable relative time string (e.g. "2m ago", "3h
     * ago").
     */
    private String formatRelativeTime(Date date) {
        if (date == null)
            return "";
        long diffMs = System.currentTimeMillis() - date.getTime();
        if (diffMs < 0)
            diffMs = 0;

        long minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs);
        if (minutes < 1)
            return "Just now";
        if (minutes < 60)
            return minutes + "m ago";

        long hours = TimeUnit.MILLISECONDS.toHours(diffMs);
        if (hours < 24)
            return hours + "h ago";

        long days = TimeUnit.MILLISECONDS.toDays(diffMs);
        if (days < 7)
            return days + "d ago";

        // Older than a week — show date
        return new SimpleDateFormat("MMM dd", Locale.getDefault()).format(date);
    }
}
