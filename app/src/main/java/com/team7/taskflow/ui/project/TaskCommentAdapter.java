package com.team7.taskflow.ui.project;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.team7.taskflow.R;
import com.team7.taskflow.domain.model.Comment;

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
            tvContent.setText(comment.getContent());

            boolean isOwner = currentUserId != null && currentUserId.equals(comment.getUserId());
            applyBubbleAlignment(isOwner);
            btnEdit.setVisibility(isOwner ? View.VISIBLE : View.GONE);
            btnDelete.setVisibility(isOwner ? View.VISIBLE : View.GONE);

            if (!allowManageActions) {
                btnEdit.setVisibility(View.GONE);
                btnDelete.setVisibility(View.GONE);
            }

            btnEdit.setOnClickListener(v -> {
                if (listener != null) listener.onEdit(comment);
            });
            btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDelete(comment);
            });

            bindReaction(btnLike, comment, "LIKE", "👍");
            bindReaction(btnLove, comment, "LOVE", "❤️");
            bindReaction(btnCelebrate, comment, "CELEBRATE", "🎉");
        }

        private void applyBubbleAlignment(boolean isOwner) {
            if (cardCommentBubble != null) {
                LinearLayout.LayoutParams bubbleLp = (LinearLayout.LayoutParams) cardCommentBubble.getLayoutParams();
                bubbleLp.gravity = isOwner ? Gravity.END : Gravity.START;
                cardCommentBubble.setLayoutParams(bubbleLp);

                int bubbleColor = ContextCompat.getColor(itemView.getContext(),
                        isOwner ? R.color.indigo_50 : R.color.theme_card);
                cardCommentBubble.setCardBackgroundColor(bubbleColor);
                cardCommentBubble.setStrokeColor(ContextCompat.getColor(itemView.getContext(), R.color.theme_border));
            }

            if (layoutReactionRow != null) {
                LinearLayout.LayoutParams reactionLp = (LinearLayout.LayoutParams) layoutReactionRow.getLayoutParams();
                reactionLp.gravity = isOwner ? Gravity.END : Gravity.START;
                layoutReactionRow.setLayoutParams(reactionLp);
            }

            if (layoutManageRow != null) {
                LinearLayout.LayoutParams manageLp = (LinearLayout.LayoutParams) layoutManageRow.getLayoutParams();
                manageLp.gravity = isOwner ? Gravity.END : Gravity.START;
                layoutManageRow.setLayoutParams(manageLp);
            }

            tvAuthor.setVisibility(isOwner ? View.GONE : View.VISIBLE);
            tvContent.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.theme_text_primary));
        }

        private void bindReaction(TextView button, Comment comment, String type, String emoji) {
            int count;
            if ("LIKE".equalsIgnoreCase(type)) {
                count = comment.getLikeCount();
            } else if ("LOVE".equalsIgnoreCase(type)) {
                count = comment.getHeartCount();
            } else {
                count = comment.getCongratsCount();
            }
            button.setText(emoji + " " + count);
            button.setTypeface(null, Typeface.NORMAL);
            button.setAlpha(0.9f);
            button.setOnClickListener(v -> {
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
