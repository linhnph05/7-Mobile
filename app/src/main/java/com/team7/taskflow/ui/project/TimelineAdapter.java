package com.team7.taskflow.ui.project;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.team7.taskflow.R;
import com.team7.taskflow.domain.model.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TimelineAdapter extends RecyclerView.Adapter<TimelineAdapter.TimelineViewHolder> {

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

    @NonNull
    @Override
    public TimelineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_timeline_row, parent, false);
        return new TimelineViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TimelineViewHolder holder, int position) {
        holder.bind(tasks.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    public static class TimelineViewHolder extends RecyclerView.ViewHolder {
        TextView tvTaskName, tvBarTitle;
        CardView cardGanttBar;

        public TimelineViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTaskName = itemView.findViewById(R.id.tvTaskName);
            tvBarTitle = itemView.findViewById(R.id.tvBarTitle);
            cardGanttBar = itemView.findViewById(R.id.cardGanttBar);
        }

        public void bind(Task task, OnTaskClickListener listener) {
            tvTaskName.setText(task.getTitle());
            tvBarTitle.setText(task.getTitle());

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onTaskClick(task);
            });

            // Get LayoutParams and cast to FrameLayout.LayoutParams since parent is FrameLayout
            ViewGroup.LayoutParams params = cardGanttBar.getLayoutParams();
            if (params instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) params;
                int randomMargin = new Random().nextInt(200); 
                lp.leftMargin = randomMargin + 20; 
                cardGanttBar.setLayoutParams(lp);
            }
            
            // Priority colors
            if ("HIGH".equals(task.getPriority())) {
                cardGanttBar.setCardBackgroundColor(0xFFEF4444);
            } else if ("DONE".equals(task.getStatus())) {
                cardGanttBar.setCardBackgroundColor(0xFF22C55E);
            } else {
                cardGanttBar.setCardBackgroundColor(0xFF136DEC);
            }
        }
    }
}
