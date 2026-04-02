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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TrashItemAdapter extends RecyclerView.Adapter<TrashItemAdapter.ViewHolder> {

    private final List<Task> items;
    private final OnRestoreClickListener onRestoreClick;
    private final OnDeleteClickListener onDeleteClick;

    public interface OnRestoreClickListener {
        void onRestoreClick(Task task);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(Task task);
    }

    public TrashItemAdapter(List<Task> items, OnRestoreClickListener onRestoreClick, OnDeleteClickListener onDeleteClick) {
        this.items = items;
        this.onRestoreClick = onRestoreClick;
        this.onDeleteClick = onDeleteClick;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trash, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Task task = items.get(position);
        holder.bind(task, onRestoreClick, onDeleteClick);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivIcon;
        private final TextView tvName;
        private final TextView tvCategory;
        private final TextView tvDeletedDate;
        private final ImageView btnRestore;
        private final ImageView btnDelete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.ivTrashItemIcon);
            tvName = itemView.findViewById(R.id.tvTrashItemName);
            tvCategory = itemView.findViewById(R.id.tvTrashItemCategory);
            tvDeletedDate = itemView.findViewById(R.id.tvTrashItemDeletedDate);
            btnRestore = itemView.findViewById(R.id.btnRestore);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }

        public void bind(Task task, OnRestoreClickListener onRestoreClick, OnDeleteClickListener onDeleteClick) {
            tvName.setText(task.getTitle() != null ? task.getTitle() : "Untitled Task");

            // Set category (project name or default)
            if (task.getProjectInfo() != null && task.getProjectInfo().getName() != null) {
                tvCategory.setText(task.getProjectInfo().getName().toUpperCase());
            } else {
                tvCategory.setText("TASK");
            }

            // Set deleted date
            String deletedDate = getRelativeDeleteDate(task.getCreatedAt());
            tvDeletedDate.setText(deletedDate);

            // Set icon based on task type
            ivIcon.setImageResource(R.drawable.ic_task_alt);

            // Set click listeners
            btnRestore.setOnClickListener(v -> {
                if (onRestoreClick != null) {
                    onRestoreClick.onRestoreClick(task);
                }
            });

            btnDelete.setOnClickListener(v -> {
                if (onDeleteClick != null) {
                    onDeleteClick.onDeleteClick(task);
                }
            });
        }

        private String getRelativeDeleteDate(String updatedAtStr) {
            if (updatedAtStr == null || updatedAtStr.isEmpty()) {
                return "Recently deleted";
            }

            try {
                // Parse ISO format timestamp
                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                Date deletedDate = isoFormat.parse(updatedAtStr);
                if (deletedDate == null) return "Recently deleted";

                long diffMs = System.currentTimeMillis() - deletedDate.getTime();
                long diffDays = diffMs / (1000 * 60 * 60 * 24);

                if (diffDays == 0) {
                    long diffHours = diffMs / (1000 * 60 * 60);
                    if (diffHours == 0) {
                        return "Deleted just now";
                    }
                    return "Deleted " + diffHours + " hour" + (diffHours > 1 ? "s" : "") + " ago";
                } else if (diffDays == 1) {
                    return "Deleted yesterday";
                } else {
                    return "Deleted " + diffDays + " days ago";
                }
            } catch (Exception e) {
                return "Recently deleted";
            }
        }
    }
}