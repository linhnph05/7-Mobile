package com.team7.taskflow.ui.project;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.team7.taskflow.R;
import com.team7.taskflow.data.remote.SupabaseClient;
import com.team7.taskflow.data.remote.api.UserApi;
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
    private final Map<String, String> assigneeAvatarUrlMap = new HashMap<>();
    private final Map<String, String> assigneeDisplayNameMap = new HashMap<>();
    private final Set<String> pendingAssigneeIds = new HashSet<>();
    private final Map<Long, SubtaskProgress> subtaskProgressByParentId = new HashMap<>();
    private List<Task> subtaskProgressSource = new ArrayList<>();

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
        // Kept for API compatibility with existing call sites.
    }

    public void setSubtaskProgressSource(List<Task> tasksSource) {
        subtaskProgressSource = tasksSource != null ? new ArrayList<>(tasksSource) : new ArrayList<>();
        rebuildSubtaskProgressMap();
        notifyDataSetChanged();
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks != null ? tasks : new ArrayList<>();
        rebuildSubtaskProgressMap();
        notifyDataSetChanged();
        preloadAssigneeProfiles(this.tasks);
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
        TextView tvSubtaskProgress;
        ImageView btnMenu, ivAssignee;
        ProgressBar pbSubtaskProgress;
        View layoutSubtaskProgress;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvPriority = itemView.findViewById(R.id.tvPriority);
            tvDueDate = itemView.findViewById(R.id.tvDueDate);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvProjectBadge = itemView.findViewById(R.id.tvProjectBadge);
            tvSubtaskProgress = itemView.findViewById(R.id.tvSubtaskProgress);
            btnMenu = itemView.findViewById(R.id.btnMenu);
            ivAssignee = itemView.findViewById(R.id.ivAssignee);
            pbSubtaskProgress = itemView.findViewById(R.id.pbSubtaskProgress);
            layoutSubtaskProgress = itemView.findViewById(R.id.layoutSubtaskProgress);
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

            bindTaskProgress(task);

            // Priority text + badge
            String priority = task.getPriority() != null
                    ? task.getPriority().trim().toUpperCase(Locale.US)
                    : "NONE";
            switch (priority) {
                case "HIGH":
                    tvPriority.setText("HIGH");
                    tvPriority.setBackgroundResource(R.drawable.bg_badge_red);
                    tvPriority.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.priority_high));
                    break;
                case "MEDIUM":
                    tvPriority.setText("MEDIUM");
                    tvPriority.setBackgroundResource(R.drawable.bg_badge_orange);
                    tvPriority.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.priority_medium));
                    break;
                case "LOW":
                    tvPriority.setText("LOW");
                    tvPriority.setBackgroundResource(R.drawable.bg_badge_blue);
                    tvPriority.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.priority_low));
                    break;
                default:
                    tvPriority.setText("NONE");
                    tvPriority.setBackgroundResource(R.drawable.bg_chip_neutral);
                    tvPriority.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.theme_text_secondary));
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

        private void bindTaskProgress(Task task) {
            if (layoutSubtaskProgress == null || pbSubtaskProgress == null || tvSubtaskProgress == null) {
                return;
            }
            if (task == null) {
                layoutSubtaskProgress.setVisibility(View.GONE);
                return;
            }

            SubtaskProgress progress = task.getId() != null ? subtaskProgressByParentId.get(task.getId()) : null;
            if (progress == null || progress.total <= 0) {
                layoutSubtaskProgress.setVisibility(View.GONE);
                return;
            }

            layoutSubtaskProgress.setVisibility(View.VISIBLE);
            pbSubtaskProgress.setProgress(progress.percent);
            tvSubtaskProgress.setText(itemView.getContext().getString(
                    R.string.task_subtask_progress_format,
                    progress.done,
                    progress.total,
                    progress.percent));
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

    private void rebuildSubtaskProgressMap() {
        subtaskProgressByParentId.clear();
        List<Task> source = (subtaskProgressSource != null && !subtaskProgressSource.isEmpty())
                ? subtaskProgressSource
                : tasks;
        if (source == null || source.isEmpty()) {
            return;
        }

        for (Task task : source) {
            if (task == null || task.getParentTaskId() == null) {
                continue;
            }
            Long parentId = task.getParentTaskId();
            if (parentId == null) {
                continue;
            }

            SubtaskProgress progress = subtaskProgressByParentId.get(parentId);
            if (progress == null) {
                progress = new SubtaskProgress();
                subtaskProgressByParentId.put(parentId, progress);
            }

            progress.total++;
            if (isDoneStatus(task.getStatus())) {
                progress.done++;
            }
        }

        for (SubtaskProgress progress : subtaskProgressByParentId.values()) {
            progress.percent = progress.total == 0
                    ? 0
                    : (int) Math.round((progress.done * 100.0) / progress.total);
        }
    }

    private boolean isDoneStatus(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.toUpperCase(Locale.US);
        return normalized.contains("DONE") || "COMPLETED".equals(normalized);
    }

    private static class SubtaskProgress {
        int total;
        int done;
        int percent;
    }
}