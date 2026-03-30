package com.team7.taskflow.ui.project;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.team7.taskflow.R;
import com.team7.taskflow.domain.model.Task;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private List<Task> tasks = new ArrayList<>();
    private OnTaskClickListener listener;

    public interface OnTaskClickListener {
        void onTaskClick(Task task);
        void onTaskMenuClick(Task task, View view);
    }

    public void setOnTaskClickListener(OnTaskClickListener listener) {
        this.listener = listener;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
        notifyDataSetChanged();
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
        holder.bind(tasks.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDescription, tvPriority, tvDueDate, tvStatus, tvProjectBadge;
        ImageView btnMenu, ivAssignee;

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
        }

        public void bind(Task task, OnTaskClickListener listener) {
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

            itemView.setOnClickListener(v -> { if (listener != null) listener.onTaskClick(task); });
            btnMenu.setOnClickListener(v -> { if (listener != null) listener.onTaskMenuClick(task, v); });

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
}