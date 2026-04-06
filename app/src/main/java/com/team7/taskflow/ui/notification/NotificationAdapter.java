package com.team7.taskflow.ui.notification;

import android.content.Context;
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
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;
import com.team7.taskflow.R;
import com.team7.taskflow.domain.model.Notification;
import com.team7.taskflow.domain.model.Notification.NotificationType;
import com.team7.taskflow.ui.common.AvatarUiUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Multi-view-type RecyclerView Adapter for Notifications.
 *
 * Maps 9 database NotificationTypes into 3 ViewTypes:
 * VIEW_TYPE_INVITE ← PROJECT_INVITE
 * VIEW_TYPE_ME ← TASK_ASSIGNED, MENTION, COMMENT, TASK_STATUS_CHANGED, REACTION,
 * ATTACHMENT_ADDED
 * VIEW_TYPE_REMINDER ← DEADLINE_REMINDER, SYSTEM_ALERT
 */
public class NotificationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_INVITE = 1;
    private static final int VIEW_TYPE_ME = 2;
    private static final int VIEW_TYPE_REMINDER = 3;

    private enum InviteDecision {
        NONE,
        ACCEPTED,
        DECLINED
    }

    private List<Notification> notificationList = new ArrayList<>();
    private final OnNotificationActionListener listener;
    private final Map<Long, InviteDecision> inviteDecisions = new HashMap<>();
    private Context appContext;

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

    public void markInviteDecision(long notificationId, boolean accepted) {
        inviteDecisions.put(notificationId, accepted ? InviteDecision.ACCEPTED : InviteDecision.DECLINED);
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
        if (appContext == null) {
            appContext = parent.getContext().getApplicationContext();
        }
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
        protected MaterialCardView cardNotificationItem;

        public BaseNotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_content);
            tvContext = itemView.findViewById(R.id.tv_context);
            tvTime = itemView.findViewById(R.id.tv_time);
            viewUnreadDot = itemView.findViewById(R.id.view_unread_dot);
            cardNotificationItem = itemView.findViewById(R.id.cardNotificationItem);

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

            // Vietnam datetime format
            tvTime.setText(formatRelativeTime(notification.getCreatedAt()));

            // Unread dot
            viewUnreadDot.setVisibility(notification.isRead() ? View.GONE : View.VISIBLE);

            applyCardState(notification.isRead());
        }

        private void applyCardState(boolean isRead) {
            if (cardNotificationItem == null) {
                return;
            }
            int bgColor = ContextCompat.getColor(itemView.getContext(),
                    isRead ? R.color.notification_read_bg : R.color.notification_unread_bg);
            cardNotificationItem.setCardBackgroundColor(bgColor);
                cardNotificationItem.setStrokeWidth(0);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // INVITE VIEW HOLDER
    // ════════════════════════════════════════════════════════════════

    class InviteViewHolder extends BaseNotificationViewHolder {
        ShapeableImageView ivAvatar;
        TextView tvAvatarLetter;
        TextView tvInviteStatus;
        MaterialButton btnAccept, btnDecline;

        public InviteViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            tvAvatarLetter = itemView.findViewById(R.id.tv_avatar_letter);
            tvInviteStatus = itemView.findViewById(R.id.tv_invite_status);
            btnAccept = itemView.findViewById(R.id.btn_accept);
            btnDecline = itemView.findViewById(R.id.btn_decline);
        }

        public void bindInvite(Notification notification) {
            bindActorAvatar(ivAvatar, tvAvatarLetter, notification);

            InviteDecision decision = resolveInviteDecision(notification);
            boolean canRespond = decision == InviteDecision.NONE;
            btnAccept.setVisibility(canRespond ? View.VISIBLE : View.GONE);
            btnDecline.setVisibility(canRespond ? View.VISIBLE : View.GONE);

            if (tvInviteStatus != null) {
                if (decision == InviteDecision.ACCEPTED) {
                    tvInviteStatus.setVisibility(View.VISIBLE);
                    tvInviteStatus.setText(R.string.notification_invite_status_joined);
                } else if (decision == InviteDecision.DECLINED) {
                    tvInviteStatus.setVisibility(View.VISIBLE);
                    tvInviteStatus.setText(R.string.notification_invite_status_declined);
                } else {
                    tvInviteStatus.setVisibility(View.GONE);
                }
            }

            btnAccept.setOnClickListener(v -> {
                if (!canRespond) return;
                if (listener != null)
                    listener.onAcceptInvite(notification);
            });
            btnDecline.setOnClickListener(v -> {
                if (!canRespond) return;
                if (listener != null)
                    listener.onDeclineInvite(notification);
            });
        }

        private InviteDecision resolveInviteDecision(Notification notification) {
            InviteDecision localDecision = inviteDecisions.getOrDefault(
                    notification.getNotificationId(),
                    InviteDecision.NONE);
            if (localDecision != InviteDecision.NONE) {
                return localDecision;
            }

            String inviteStatus = notification.getInviteStatus();
            if (inviteStatus == null || inviteStatus.trim().isEmpty()) {
                return InviteDecision.NONE;
            }

            String normalized = inviteStatus.trim().toUpperCase(Locale.US);
            if ("ACCEPTED".equals(normalized)) {
                return InviteDecision.ACCEPTED;
            }
            if ("DENIED".equals(normalized)) {
                return InviteDecision.DECLINED;
            }
            return InviteDecision.NONE;
        }
    }

    // ════════════════════════════════════════════════════════════════
    // ME VIEW HOLDER (social / task-related)
    // ════════════════════════════════════════════════════════════════

    class MeViewHolder extends BaseNotificationViewHolder {
        ShapeableImageView ivAvatar;
        TextView tvAvatarLetter;
        ImageView ivBadge;

        public MeViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            tvAvatarLetter = itemView.findViewById(R.id.tv_avatar_letter);
            ivBadge = itemView.findViewById(R.id.iv_badge_comment);
        }

        public void bindMe(Notification notification) {
            bindActorAvatar(ivAvatar, tvAvatarLetter, notification);

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

        private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
        private static final DateTimeFormatter VIETNAM_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.getDefault());

    /**
     * Relative Vietnamese time like project history: just now, minutes, hours, yesterday, days.
     */
    private String formatRelativeTime(Date date) {
        if (date == null) {
            return "";
        }
        try {
            Instant created = date.toInstant();
            Instant now = Instant.now();
            if (created.isAfter(now)) {
                return appContext.getString(R.string.notification_time_just_now);
            }

            Duration duration = Duration.between(created, now);
            long minutes = duration.toMinutes();
            if (minutes < 1) {
                return appContext.getString(R.string.notification_time_just_now);
            }

            LocalDate createdDate = created.atZone(VIETNAM_ZONE).toLocalDate();
            LocalDate currentDate = now.atZone(VIETNAM_ZONE).toLocalDate();

            if (createdDate.equals(currentDate)) {
                if (minutes < 60) {
                    return appContext.getResources().getQuantityString(
                            R.plurals.notification_time_minutes_ago,
                            (int) minutes,
                            minutes);
                }
                long hours = duration.toHours();
                return appContext.getResources().getQuantityString(
                        R.plurals.notification_time_hours_ago,
                        (int) hours,
                        hours);
            }

            if (createdDate.equals(currentDate.minusDays(1))) {
                return appContext.getString(R.string.notification_time_yesterday);
            }

            long days = java.time.temporal.ChronoUnit.DAYS.between(createdDate, currentDate);
            if (days > 0 && days <= 7) {
                return appContext.getResources().getQuantityString(
                        R.plurals.notification_time_days_ago,
                        (int) days,
                        days);
            }

            ZonedDateTime dateTime = created.atZone(VIETNAM_ZONE);
            return dateTime.format(VIETNAM_DATE_TIME_FORMATTER);
        } catch (Exception ignored) {
            ZonedDateTime dateTime = date.toInstant().atZone(VIETNAM_ZONE);
            return dateTime.format(VIETNAM_DATE_TIME_FORMATTER);
        }
    }

    private void bindActorAvatar(ShapeableImageView avatarView,
            TextView letterView,
            Notification notification) {
        String actorName = notification != null ? notification.getActorName() : null;
        String avatarUrl = notification != null ? notification.getActorAvatarUrl() : null;
        AvatarUiUtils.bindAvatarOrFallback(avatarView, letterView, avatarUrl, actorName);
    }
}
