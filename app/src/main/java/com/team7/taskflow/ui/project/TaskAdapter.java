package com.team7.taskflow.ui.project;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.team7.taskflow.R;
import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.remote.api.UserApi;
import com.team7.taskflow.data.repository.TaskRepository;
import com.team7.taskflow.domain.model.Comment;
import com.team7.taskflow.domain.model.Task;
import com.team7.taskflow.domain.model.User;
import com.team7.taskflow.ui.common.AvatarUiUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private List<Task> tasks = new ArrayList<>();
    private OnTaskClickListener listener;
    private OnTaskLongPressListener longPressListener;
    private final Map<Long, Boolean> expandedComments = new HashMap<>();
    private final Map<Long, List<Comment>> commentsCache = new HashMap<>();
    private final Map<Long, TaskCommentAdapter> inlineCommentAdapters = new HashMap<>();
    private final Set<Long> loadingComments = new HashSet<>();
    private final Map<String, String> assigneeAvatarUrlMap = new HashMap<>();
    private final Map<String, String> assigneeDisplayNameMap = new HashMap<>();
    private final Set<String> pendingAssigneeIds = new HashSet<>();
    private final TaskRepository taskRepository = TaskRepository.getInstance();
    private boolean inlineCommentsEnabled = false;
    private String inlineCommentUserId;

    public interface OnTaskClickListener {
        void onTaskClick(Task task);
        void onTaskMenuClick(Task task, View view);
    }

    public interface OnTaskLongPressListener {
        void onTaskLongPress(Task task, View itemView);
    }

    public void setOnTaskClickListener(OnTaskClickListener listener) {
        this.listener = listener;
    }

    public void setOnTaskLongPressListener(OnTaskLongPressListener longPressListener) {
        this.longPressListener = longPressListener;
    }

    public void setInlineCommentsEnabled(boolean enabled, String currentUserId) {
        inlineCommentsEnabled = enabled;
        inlineCommentUserId = currentUserId;
        notifyDataSetChanged();
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
        notifyDataSetChanged();
        preloadAssigneeProfiles(tasks);
    }

    public List<Task> getTasks() {
        return tasks != null ? tasks : new ArrayList<>();
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        holder.bind(tasks.get(position), listener, longPressListener);
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDescription, tvPriority, tvDueDate, tvStatus, tvProjectBadge;
        ImageView btnMenu, ivAssignee;
        ImageView btnToggleComments, btnSendInlineComment;
        TextView tvInlineCommentLabel;
        EditText etInlineComment;
        RecyclerView rvInlineComments;
        View layoutCommentToggle, layoutInlineComments;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvPriority = itemView.findViewById(R.id.tvPriority);
            tvDueDate = itemView.findViewById(R.id.tvDueDate);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvProjectBadge = itemView.findViewById(R.id.tvProjectBadge);
            btnMenu = itemView.findViewById(R.id.btnMenu);
            ivAssignee = itemView.findViewById(R.id.ivAssignee);
            btnToggleComments = itemView.findViewById(R.id.btnToggleComments);
            btnSendInlineComment = itemView.findViewById(R.id.btnSendInlineComment);
            tvInlineCommentLabel = itemView.findViewById(R.id.tvInlineCommentLabel);
            etInlineComment = itemView.findViewById(R.id.etInlineComment);
            rvInlineComments = itemView.findViewById(R.id.rvInlineComments);
            layoutCommentToggle = itemView.findViewById(R.id.layoutCommentToggle);
            layoutInlineComments = itemView.findViewById(R.id.layoutInlineComments);
        }

        public void bind(Task task, OnTaskClickListener listener, OnTaskLongPressListener longPressListener) {
            tvTitle.setText(task.getTitle());
            tvDescription.setText(task.getDescription());

            // Project name badge
            String projectName = task.getProjectName();
            if (projectName != null && !projectName.isEmpty()) {
                tvProjectBadge.setVisibility(View.VISIBLE);
                tvProjectBadge.setText(projectName);
            } else {
                tvProjectBadge.setVisibility(View.GONE);
            }

            // Format date: yyyy-MM-dd -> MMM dd, yyyy
            String formattedDate = formatDate(task.getDueDate());
            tvDueDate.setText(formattedDate);

            if (ivAssignee != null) {
                String assigneeId = task.getAssigneeId();
                String assigneeAvatarUrl = assigneeAvatarUrlMap.get(assigneeId);
                String assigneeName = assigneeDisplayNameMap.get(assigneeId);
                AvatarUiUtils.bindAvatarOrFallback(
                        ivAssignee,
                        null,
                    assigneeAvatarUrl,
                    assigneeName != null ? assigneeName : assigneeId);
            }

            itemView.setOnClickListener(v -> { if (listener != null) listener.onTaskClick(task); });
            btnMenu.setOnClickListener(v -> { if (listener != null) listener.onTaskMenuClick(task, v); });
            itemView.setOnLongClickListener(v -> {
                if (longPressListener != null) {
                    longPressListener.onTaskLongPress(task, v);
                    return true;
                }
                return false;
            });

            bindInlineComments(task);

            // Priority Colors
            String priority = task.getPriority() != null ? task.getPriority().toUpperCase() : "LOW";
            switch (priority) {
                case "HIGH":
                    tvPriority.setBackgroundResource(R.drawable.bg_badge_red);
                    tvPriority.setTextColor(0xFFEF4444); // Bright Red
                    break;
                case "MEDIUM":
                    tvPriority.setBackgroundResource(R.drawable.bg_badge_orange);
                    tvPriority.setTextColor(0xFFF59E0B); // Amber/Orange
                    break;
                default: 
                    tvPriority.setBackgroundResource(R.drawable.bg_badge_blue);
                    tvPriority.setTextColor(0xFF3B82F6); // Blue
                    break;
            }

            // Status Colors
            String status = task.getStatus() != null ? task.getStatus().toUpperCase() : "TODO";
            tvStatus.setText(status);
            Drawable statusBg = tvStatus.getBackground().mutate();
            
            if (status.contains("DONE")) {
                statusBg.setTint(0x332ECC71); // 20% Emerald
                tvStatus.setTextColor(0xFF2ECC71);
            } else if (status.contains("DOING") || status.contains("PROGRESS")) {
                statusBg.setTint(0x333498DB); // 20% Sky Blue
                tvStatus.setTextColor(0xFF3498DB);
            } else {
                statusBg.setTint(0x3394A3B8); // 20% Slate
                tvStatus.setTextColor(0xFF94A3B8);
            }
        }

        private void bindInlineComments(Task task) {
            if (layoutCommentToggle == null || layoutInlineComments == null || btnToggleComments == null
                    || rvInlineComments == null || etInlineComment == null || btnSendInlineComment == null
                    || task.getId() == null) {
                return;
            }

            if (!inlineCommentsEnabled) {
                layoutCommentToggle.setVisibility(View.GONE);
                layoutInlineComments.setVisibility(View.GONE);
                return;
            }

            layoutCommentToggle.setVisibility(View.VISIBLE);
            long taskId = task.getId();
            boolean expanded = expandedComments.getOrDefault(taskId, false);
            layoutInlineComments.setVisibility(expanded ? View.VISIBLE : View.GONE);
            btnToggleComments.setRotation(expanded ? 180f : 0f);

            if (rvInlineComments.getLayoutManager() == null) {
                rvInlineComments.setLayoutManager(new LinearLayoutManager(itemView.getContext()));
            }

            TaskCommentAdapter commentAdapter = inlineCommentAdapters.get(taskId);
            if (commentAdapter == null) {
                commentAdapter = new TaskCommentAdapter(inlineCommentUserId, new TaskCommentAdapter.Listener() {
                    @Override
                    public void onEdit(Comment comment) {
                        // Inline panel is optimized for quick chat flow.
                    }

                    @Override
                    public void onDelete(Comment comment) {
                        // Inline panel is optimized for quick chat flow.
                    }

                    @Override
                    public void onReact(Comment comment, String reactionType) {
                        if (comment == null || comment.getId() == null || TextUtils.isEmpty(inlineCommentUserId)) {
                            return;
                        }

                        // Optimistic UI update: update count/state immediately on screen.
                        TaskCommentAdapter localAdapter = inlineCommentAdapters.get(taskId);
                        if (localAdapter != null) {
                            localAdapter.applyLocalReactionToggle(comment.getId(), reactionType);
                        }

                        taskRepository.toggleCommentReaction(comment.getId(), inlineCommentUserId, reactionType,
                                new TaskRepository.TaskCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void result) {
                                        // Keep optimistic state; no full reload needed on success.
                                    }

                                    @Override
                                    public void onError(String error) {
                                        // Reload only when backend failed to restore correct state.
                                        itemView.post(() -> loadComments(taskId));
                                    }
                                });
                    }
                });
                commentAdapter.setAllowManageActions(false);
                inlineCommentAdapters.put(taskId, commentAdapter);
            }
            rvInlineComments.setAdapter(commentAdapter);

            if (expanded) {
                renderInlineComments(taskId);
                if (!commentsCache.containsKey(taskId) && !loadingComments.contains(taskId)) {
                    loadComments(taskId);
                }
            }

            btnToggleComments.setOnClickListener(v -> {
                boolean nextExpanded = !expandedComments.getOrDefault(taskId, false);
                expandedComments.put(taskId, nextExpanded);
                notifyItemChanged(getBindingAdapterPosition());
            });

            btnSendInlineComment.setOnClickListener(v -> {
                if (TextUtils.isEmpty(inlineCommentUserId)) {
                    return;
                }
                String content = etInlineComment.getText() != null ? etInlineComment.getText().toString().trim() : "";
                if (content.isEmpty()) {
                    return;
                }
                btnSendInlineComment.setEnabled(false);
                taskRepository.createTaskComment(taskId, inlineCommentUserId, content,
                        new TaskRepository.TaskCallback<Comment>() {
                            @Override
                            public void onSuccess(Comment result) {
                                itemView.post(() -> {
                                    etInlineComment.setText("");
                                    btnSendInlineComment.setEnabled(true);
                                    loadComments(taskId);
                                });
                            }

                            @Override
                            public void onError(String error) {
                                itemView.post(() -> btnSendInlineComment.setEnabled(true));
                            }
                        });
            });
        }

        private void loadComments(long taskId) {
            loadingComments.add(taskId);
            taskRepository.getTaskComments(taskId, new TaskRepository.TaskCallback<List<Comment>>() {
                @Override
                public void onSuccess(List<Comment> result) {
                    itemView.post(() -> {
                        loadingComments.remove(taskId);
                        commentsCache.put(taskId, result != null ? result : new ArrayList<>());
                        renderInlineComments(taskId);
                    });
                }

                @Override
                public void onError(String error) {
                    itemView.post(() -> {
                        loadingComments.remove(taskId);
                        renderInlineComments(taskId);
                    });
                }
            });
        }

        private void renderInlineComments(long taskId) {
            List<Comment> comments = commentsCache.get(taskId);
            if (loadingComments.contains(taskId)) {
                TaskCommentAdapter adapter = inlineCommentAdapters.get(taskId);
                if (adapter != null) adapter.setComments(new ArrayList<>());
                if (tvInlineCommentLabel != null) tvInlineCommentLabel.setText("Bình luận (đang tải...)");
                return;
            }
            if (comments == null || comments.isEmpty()) {
                TaskCommentAdapter adapter = inlineCommentAdapters.get(taskId);
                if (adapter != null) adapter.setComments(new ArrayList<>());
                if (tvInlineCommentLabel != null) tvInlineCommentLabel.setText("Bình luận (0)");
                return;
            }

            TaskCommentAdapter adapter = inlineCommentAdapters.get(taskId);
            if (adapter != null) {
                adapter.setComments(comments);
            }
            if (tvInlineCommentLabel != null) tvInlineCommentLabel.setText("Bình luận (" + comments.size() + ")");
        }

        private String formatDate(String rawDate) {
            if (rawDate == null || rawDate.isEmpty()) return "No date";
            try {
                SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                SimpleDateFormat formatter = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                Date date = parser.parse(rawDate.substring(0, 10)); // Handle full ISO dates
                return date != null ? formatter.format(date) : rawDate;
            } catch (Exception e) {
                return rawDate;
            }
        }
    }

    private void preloadAssigneeProfiles(List<Task> taskList) {
        if (taskList == null || taskList.isEmpty()) {
            return;
        }

        Set<String> idsToLoad = new HashSet<>();
        for (Task task : taskList) {
            if (task == null) {
                continue;
            }
            String assigneeId = task.getAssigneeId();
            if (TextUtils.isEmpty(assigneeId)) {
                continue;
            }
            if (!assigneeAvatarUrlMap.containsKey(assigneeId)
                    && !assigneeDisplayNameMap.containsKey(assigneeId)
                    && !pendingAssigneeIds.contains(assigneeId)) {
                idsToLoad.add(assigneeId);
            }
        }

        if (idsToLoad.isEmpty()) {
            return;
        }

        pendingAssigneeIds.addAll(idsToLoad);
        String idsFilter = "in.(" + TextUtils.join(",", idsToLoad) + ")";

        SupabaseClient.getInstance()
                .getService(UserApi.class)
                .getUsersByIds(idsFilter, "user_id,display_name,avatar_url")
                .enqueue(new Callback<List<User>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<User>> call, @NonNull Response<List<User>> response) {
                        pendingAssigneeIds.removeAll(idsToLoad);
                        if (response.isSuccessful() && response.body() != null) {
                            for (User user : response.body()) {
                                if (user == null || TextUtils.isEmpty(user.getUserId())) {
                                    continue;
                                }
                                if (!TextUtils.isEmpty(user.getAvatarUrl())) {
                                    assigneeAvatarUrlMap.put(user.getUserId(), user.getAvatarUrl());
                                }
                                if (!TextUtils.isEmpty(user.getDisplayName())) {
                                    assigneeDisplayNameMap.put(user.getUserId(), user.getDisplayName());
                                }
                            }
                            notifyDataSetChanged();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<User>> call, @NonNull Throwable t) {
                        pendingAssigneeIds.removeAll(idsToLoad);
                    }
                });
    }
}