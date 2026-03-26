package com.team7.taskflow.ui.project;

import android.graphics.drawable.Drawable;
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

import java.util.ArrayList;
import java.util.List;

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
        Task task = tasks.get(position);
        holder.bind(task, listener);
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDescription, tvPriority, tvDueDate, tvStatus;
        ImageView btnMenu, ivAssignee;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvPriority = itemView.findViewById(R.id.tvPriority);
            tvDueDate = itemView.findViewById(R.id.tvDueDate);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            btnMenu = itemView.findViewById(R.id.btnMenu);
            ivAssignee = itemView.findViewById(R.id.ivAssignee);
        }

        public void bind(Task task, OnTaskClickListener listener) {
            // 1. Gán nội dung văn bản
            tvTitle.setText(task.getTitle());
            tvDescription.setText(task.getDescription());
            tvPriority.setText(task.getPriority() != null ? task.getPriority() : "LOW");

            // 2. Logic dải ngày (Start Date - Due Date)

            String start = task.getStartDate();
            String due = task.getDueDate();
            if (start != null && !start.isEmpty() && due != null && !due.isEmpty()) {
                tvDueDate.setText(start + " - " + due);
            } else {
                tvDueDate.setText(due != null && !due.isEmpty() ? due : "No date");
            }

            // 3. Sự kiện Click
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onTaskClick(task);
            });

            btnMenu.setOnClickListener(v -> {
                if (listener != null) listener.onTaskMenuClick(task, v);
            });

            // 4. Đổ màu cho thẻ Priority
            String priority = task.getPriority() != null ? task.getPriority().toUpperCase() : "LOW";
            switch (priority) {
                case "HIGH":
                    tvPriority.setBackgroundResource(R.drawable.bg_badge_red);
                    tvPriority.setTextColor(0xFFDC2626);
                    break;
                case "MEDIUM":
                    tvPriority.setBackgroundResource(R.drawable.bg_badge_orange);
                    tvPriority.setTextColor(0xFFC2410C);
                    break;
                default: // LOW
                    tvPriority.setBackgroundResource(R.drawable.bg_badge_blue);
                    tvPriority.setTextColor(0xFF0369A1);
                    break;
            }

            // 5. Đổ màu cho thẻ Status
            if (task.getStatus() != null) {
                String status = task.getStatus().toUpperCase();
                tvStatus.setText(status);

                Drawable statusBg = tvStatus.getBackground().mutate();

                switch (status) {
                    case "TODO":
                        statusBg.setTint(0xFFF1F5F9); // Xám nhạt
                        tvStatus.setTextColor(0xFF64748B); // Chữ xám đậm
                        break;
                    case "DOING":
                        statusBg.setTint(0xFFDBEAFE); // Xanh dương nhạt
                        tvStatus.setTextColor(0xFF1E40AF); // Chữ xanh dương đậm
                        break;
                    case "DONE":
                        statusBg.setTint(0xFFDCFCE7); // Xanh lá nhạt
                        tvStatus.setTextColor(0xFF166534); // Chữ xanh lá đậm
                        break;
                }
            }
        }
    }
}