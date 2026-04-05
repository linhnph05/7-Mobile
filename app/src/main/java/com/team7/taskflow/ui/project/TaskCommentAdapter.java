package com.team7.taskflow.ui.project;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.team7.taskflow.R;
import com.team7.taskflow.domain.model.Comment;
import com.team7.taskflow.ui.common.AvatarUiUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TaskCommentAdapter extends RecyclerView.Adapter<TaskCommentAdapter.CommentViewHolder> {

    public interface Listener {
        void onEdit(Comment comment);
        void onDelete(Comment comment);
        void onReact(Comment comment, String reactionType);
    }

    private final String currentUserId;
    private final Listener listener;
    private final List<Comment> comments = new ArrayList<>();
    private boolean allowManageActions = true;

    public TaskCommentAdapter(String currentUserId, Listener listener) {
        this.currentUserId = currentUserId;
        this.listener = listener;
    }

    public void setComments(List<Comment> items) {
        comments.clear();
        if (items != null) {
            comments.addAll(items);
        }
        notifyDataSetChanged();
    }

    public void applyLocalReactionToggle(long commentId, String reactionType) {
        int index = findCommentIndex(commentId);
        if (index < 0) {
            return;
        }

        Comment comment = comments.get(index);
        String normalizedReaction = reactionType != null ? reactionType.trim().toUpperCase() : "";
        if ("LIKE".equals(normalizedReaction)) {
            boolean selected = comment.isLikeSelected();
            comment.setLikeSelected(!selected);
            comment.setLikeCount(Math.max(0, comment.getLikeCount() + (selected ? -1 : 1)));
        } else if ("LOVE".equals(normalizedReaction)) {
            boolean selected = comment.isHeartSelected();
            comment.setHeartSelected(!selected);
            comment.setHeartCount(Math.max(0, comment.getHeartCount() + (selected ? -1 : 1)));
        } else if ("CELEBRATE".equals(normalizedReaction)) {
            boolean selected = comment.isCongratsSelected();
            comment.setCongratsSelected(!selected);
            comment.setCongratsCount(Math.max(0, comment.getCongratsCount() + (selected ? -1 : 1)));
        }

        notifyItemChanged(index);
    }

    private int findCommentIndex(long commentId) {
        for (int i = 0; i < comments.size(); i++) {
            Comment item = comments.get(i);
            if (item.getId() != null && item.getId().longValue() == commentId) {
                return i;
            }
        }
        return -1;
    }

    public void setAllowManageActions(boolean allowManageActions) {
        this.allowManageActions = allowManageActions;
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        holder.bind(comments.get(position));
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    class CommentViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imgAvatar;
        private final TextView tvAvatarLetter;
        private final TextView tvAuthor;
        private final TextView tvTime;
        private final TextView tvContent;
        private final TextView btnEdit;
        private final TextView btnDelete;
        private final TextView btnLike;
        private final TextView btnLove;
        private final TextView btnCelebrate;
        private final LinearLayout layoutCommentRow;
        private final MaterialCardView cardCommentBubble;
        private final LinearLayout layoutReactionRow;
        private final LinearLayout layoutManageRow;

        CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAvatar = itemView.findViewById(R.id.imgCommentAvatar);
            tvAvatarLetter = itemView.findViewById(R.id.tvCommentAvatarLetter);
            tvAuthor = itemView.findViewById(R.id.tvCommentAuthor);
            tvTime = itemView.findViewById(R.id.tvCommentTime);
            tvContent = itemView.findViewById(R.id.tvCommentContent);
            btnEdit = itemView.findViewById(R.id.btnEditComment);
            btnDelete = itemView.findViewById(R.id.btnDeleteComment);
            btnLike = itemView.findViewById(R.id.btnReactionLike);
            btnLove = itemView.findViewById(R.id.btnReactionLove);
            btnCelebrate = itemView.findViewById(R.id.btnReactionCelebrate);
            layoutCommentRow = itemView.findViewById(R.id.layoutCommentRow);
            cardCommentBubble = itemView.findViewById(R.id.cardCommentBubble);
            layoutReactionRow = itemView.findViewById(R.id.layoutReactionRow);
            layoutManageRow = itemView.findViewById(R.id.layoutManageRow);
        }

        void bind(Comment comment) {
            String displayName = "Unknown";
            if (comment.getUser() != null && comment.getUser().getDisplayNameOrEmail() != null) {
                displayName = comment.getUser().getDisplayNameOrEmail();
            } else if (comment.getUserId() != null) {
                displayName = comment.getUserId();
            }

            tvAuthor.setText(displayName);
            tvTime.setText(formatRelativeTime(comment.getCreatedAt()));
                boolean isDeleted = comment != null && comment.isDeleted();
                tvContent.setText(isDeleted
                    ? itemView.getContext().getString(R.string.comment_deleted_placeholder)
                    : comment.getContent());
            bindAvatar(comment, displayName);

            boolean isOwner = currentUserId != null && currentUserId.equals(comment.getUserId());
            applyBubbleAlignment(isOwner);
            btnEdit.setVisibility(isOwner ? View.VISIBLE : View.GONE);
            btnDelete.setVisibility(isOwner ? View.VISIBLE : View.GONE);

            if (!allowManageActions) {
                btnEdit.setVisibility(View.GONE);
                btnDelete.setVisibility(View.GONE);
            }

            if (isDeleted) {
                btnEdit.setVisibility(View.GONE);
                btnDelete.setVisibility(View.GONE);
                btnLike.setEnabled(false);
                btnLove.setEnabled(false);
                btnCelebrate.setEnabled(false);
                btnLike.setAlpha(0.5f);
                btnLove.setAlpha(0.5f);
                btnCelebrate.setAlpha(0.5f);
            } else {
                btnLike.setEnabled(true);
                btnLove.setEnabled(true);
                btnCelebrate.setEnabled(true);
            }

            btnEdit.setOnClickListener(v -> {
                if (isDeleted) return;
                if (listener != null) listener.onEdit(comment);
            });
            btnDelete.setOnClickListener(v -> {
                if (isDeleted) return;
                if (listener != null) listener.onDelete(comment);
            });

            bindReaction(btnLike, comment, "LIKE", "👍", isDeleted);
            bindReaction(btnLove, comment, "LOVE", "❤️", isDeleted);
            bindReaction(btnCelebrate, comment, "CELEBRATE", "🎉", isDeleted);
        }

        private void bindAvatar(Comment comment, String displayName) {
            String avatarUrl = null;
            if (comment != null && comment.getUser() != null) {
                avatarUrl = comment.getUser().getAvatarUrl();
            }
            imgAvatar.setVisibility(View.VISIBLE);
            AvatarUiUtils.bindAvatarOrFallback(imgAvatar, tvAvatarLetter, avatarUrl, displayName);
        }

        private void applyBubbleAlignment(boolean isOwner) {
            if (cardCommentBubble != null) {
                LinearLayout.LayoutParams bubbleLp = (LinearLayout.LayoutParams) cardCommentBubble.getLayoutParams();
                bubbleLp.gravity = Gravity.START;
                cardCommentBubble.setLayoutParams(bubbleLp);

                int bubbleColor = ContextCompat.getColor(itemView.getContext(),
                        R.color.theme_surface_variant);
                cardCommentBubble.setCardBackgroundColor(bubbleColor);
                cardCommentBubble.setStrokeColor(ContextCompat.getColor(itemView.getContext(), R.color.theme_border));
            }

            if (layoutReactionRow != null) {
                LinearLayout.LayoutParams reactionLp = (LinearLayout.LayoutParams) layoutReactionRow.getLayoutParams();
                reactionLp.gravity = Gravity.START;
                layoutReactionRow.setLayoutParams(reactionLp);
            }

            if (layoutManageRow != null) {
                LinearLayout.LayoutParams manageLp = (LinearLayout.LayoutParams) layoutManageRow.getLayoutParams();
                manageLp.gravity = Gravity.START;
                layoutManageRow.setLayoutParams(manageLp);
            }

            tvContent.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.theme_text_primary));
        }

        private void bindReaction(TextView button, Comment comment, String type, String emoji, boolean isDeleted) {
            int count;
            boolean selected;
            if ("LIKE".equalsIgnoreCase(type)) {
                count = comment.getLikeCount();
                selected = comment.isLikeSelected();
            } else if ("LOVE".equalsIgnoreCase(type)) {
                count = comment.getHeartCount();
                selected = comment.isHeartSelected();
            } else {
                count = comment.getCongratsCount();
                selected = comment.isCongratsSelected();
            }
            button.setText(emoji + " " + count);
            button.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            button.setAlpha(selected ? 1f : 0.9f);
            button.setOnClickListener(v -> {
                if (isDeleted) {
                    return;
                }
                if (listener != null) listener.onReact(comment, type);
            });
        }

        private String formatRelativeTime(String raw) {
            if (raw == null || raw.isEmpty()) return "vừa xong";
            try {
                Instant created = OffsetDateTime.parse(raw).toInstant();
                Duration duration = Duration.between(created, Instant.now());
                long seconds = Math.max(0, duration.getSeconds());
                if (seconds < 60) return "vừa xong";
                long minutes = seconds / 60;
                if (minutes < 60) return minutes + " phút trước";
                long hours = minutes / 60;
                if (hours < 24) return hours + " giờ trước";
                long days = hours / 24;
                if (days < 7) return days + " ngày trước";
                return DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())
                        .withZone(java.time.ZoneId.systemDefault())
                        .format(created);
            } catch (Exception ignored) {
                return "vừa xong";
            }
        }
    }
}
