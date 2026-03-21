package com.team7.taskflow.ui.project;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
        return tasks;
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
        TextView tvTitle, tvDescription, tvPriority, tvDueDate;
        ImageView btnMenu, ivAssignee;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvPriority = itemView.findViewById(R.id.tvPriority);
            tvDueDate = itemView.findViewById(R.id.tvDueDate);
            btnMenu = itemView.findViewById(R.id.btnMenu);
            ivAssignee = itemView.findViewById(R.id.ivAssignee);
        }

        public void bind(Task task, OnTaskClickListener listener) {
            tvTitle.setText(task.getTitle());
            tvDescription.setText(task.getDescription());
            tvPriority.setText(task.getPriority());
            tvDueDate.setText(task.getDueDate() != null ? task.getDueDate() : "No date");

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onTaskClick(task);
            });

            btnMenu.setOnClickListener(v -> {
                if (listener != null) listener.onTaskMenuClick(task, v);
            });
            
            // Priority coloring
            if ("HIGH".equals(task.getPriority())) {
                tvPriority.setBackgroundResource(R.drawable.bg_badge_red);
                tvPriority.setTextColor(0xFFDC2626);
            } else if ("MEDIUM".equals(task.getPriority())) {
                tvPriority.setBackgroundResource(R.drawable.bg_badge_orange);
                tvPriority.setTextColor(0xFFC2410C);
            } else {
                tvPriority.setBackgroundResource(R.drawable.bg_badge_blue);
                tvPriority.setTextColor(0xFF0369A1);
            }
        }
    }
}
