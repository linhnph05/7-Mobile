package com.team7.taskflow.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.team7.taskflow.R;
import com.team7.taskflow.domain.model.Project;

import java.util.List;

public class ProjectTrashAdapter extends RecyclerView.Adapter<ProjectTrashAdapter.ProjectTrashViewHolder> {

    public interface OnRestoreClickListener {
        void onRestore(Project project);
    }

    private final List<Project> projects;
    private final OnRestoreClickListener onRestoreClickListener;

    public ProjectTrashAdapter(List<Project> projects, OnRestoreClickListener onRestoreClickListener) {
        this.projects = projects;
        this.onRestoreClickListener = onRestoreClickListener;
    }

    @NonNull
    @Override
    public ProjectTrashViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_project_trash, parent, false);
        return new ProjectTrashViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProjectTrashViewHolder holder, int position) {
        Project project = projects.get(position);
        holder.tvProjectName.setText(project.getName() == null || project.getName().trim().isEmpty()
                ? holder.itemView.getContext().getString(R.string.project_unnamed)
                : project.getName());

        String description = project.getDescription();
        if (description == null || description.trim().isEmpty()) {
            holder.tvProjectDescription.setText(R.string.project_no_description);
        } else {
            holder.tvProjectDescription.setText(description);
        }

        String deletedAt = project.getDeletedAt();
        if (deletedAt == null || deletedAt.trim().isEmpty()) {
            holder.tvDeletedAt.setText(holder.itemView.getContext().getString(R.string.project_deleted_at_unknown));
        } else {
            holder.tvDeletedAt.setText(holder.itemView.getContext().getString(
                    R.string.project_deleted_at_format,
                    deletedAt.replace("T", " ").replace("Z", "")));
        }

        holder.btnRestore.setOnClickListener(v -> {
            if (onRestoreClickListener != null) {
                onRestoreClickListener.onRestore(project);
            }
        });
    }

    @Override
    public int getItemCount() {
        return projects != null ? projects.size() : 0;
    }

    static class ProjectTrashViewHolder extends RecyclerView.ViewHolder {
        TextView tvProjectName;
        TextView tvProjectDescription;
        TextView tvDeletedAt;
        View btnRestore;

        ProjectTrashViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProjectName = itemView.findViewById(R.id.tvProjectName);
            tvProjectDescription = itemView.findViewById(R.id.tvProjectDescription);
            tvDeletedAt = itemView.findViewById(R.id.tvDeletedAt);
            btnRestore = itemView.findViewById(R.id.btnRestore);
        }
    }
}
