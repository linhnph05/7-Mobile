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

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
            tvName.setText(task.getTitle() != null ? task.getTitle() : itemView.getContext().getString(R.string.trash_untitled_task));

            // Set category (project name or default)
            if (task.getProjectInfo() != null && task.getProjectInfo().getName() != null) {
                tvCategory.setText(task.getProjectInfo().getName().toUpperCase());
            } else {
                tvCategory.setText(R.string.trash_item_category_task);
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
                return itemView.getContext().getString(R.string.trash_recently_deleted);
            }

            try {
                // Try parsing as OffsetDateTime/Instant-aware ISO string, fallback gracefully
                OffsetDateTime odt;
                try {
                    odt = OffsetDateTime.parse(updatedAtStr);
                } catch (Exception ex) {
                    // If no offset present, try parsing as instant
                    Instant instant = Instant.parse(updatedAtStr);
                    odt = instant.atOffset(ZoneId.of("UTC").getRules().getOffset(instant));
                }

                // Convert to Vietnam timezone
                ZoneId vnZone = ZoneId.of("Asia/Ho_Chi_Minh");
                DateTimeFormatter vnFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", new Locale("vi","VN"));
                String formatted = odt.atZoneSameInstant(vnZone).format(vnFormatter);

                return formatted;
            } catch (Exception e) {
                // Fallback: try to parse roughly with existing SimpleDateFormat patterns
                try {
                    // Attempt naive parse as yyyy-MM-dd'T'HH:mm:ss
                    java.text.SimpleDateFormat isoFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                    Date deletedDate = isoFormat.parse(updatedAtStr);
                    if (deletedDate == null) return itemView.getContext().getString(R.string.trash_recently_deleted);
                    // Format in VN locale
                    java.text.SimpleDateFormat vn = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi","VN"));
                    vn.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
                    return vn.format(deletedDate);
                } catch (Exception ex2) {
                    return itemView.getContext().getString(R.string.trash_recently_deleted);
                }
            }
        }
    }
}