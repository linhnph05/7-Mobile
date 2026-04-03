package com.team7.taskflow.ui.dashboard;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.team7.taskflow.R;
import com.team7.taskflow.domain.model.Project;
import com.team7.taskflow.domain.model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter cho RecyclerView hiển thị danh sách projects trên Dashboard
 */
public class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder> {

    private List<Project> projects = new ArrayList<>();
    private OnProjectClickListener listener;

    public interface OnProjectClickListener {
        void onProjectClick(Project project);
    }

    public void setOnProjectClickListener(OnProjectClickListener listener) {
        this.listener = listener;
    }

    public void setProjects(List<Project> projects) {
        this.projects = projects;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_project, parent, false);
        return new ProjectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProjectViewHolder holder, int position) {
        Project project = projects.get(position);
        holder.bind(project);
    }

    @Override
    public int getItemCount() {
        return projects.size();
    }

    class ProjectViewHolder extends RecyclerView.ViewHolder {
        private static final int MAX_PREVIEW_MEMBERS = 3;
        private static final int AVATAR_SIZE_DP = 24;
        private static final int AVATAR_OFFSET_DP = 14;

        private final TextView tvProjectName;
        private final TextView tvProjectDesc;
        private final TextView tvProgress;
        private final TextView tvTaskCount;
        private final TextView tvCommentCount;
        private final FrameLayout layoutAvatars;
        private final ProgressBar progressBar;
        private final CardView cardView;

        public ProjectViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProjectName = itemView.findViewById(R.id.tvProjectName);
            tvProjectDesc = itemView.findViewById(R.id.tvProjectDesc);
            tvProgress = itemView.findViewById(R.id.tvProgress);
            tvTaskCount = itemView.findViewById(R.id.tvDaysLeft);
            tvCommentCount = itemView.findViewById(R.id.tvCommentCount);
            layoutAvatars = itemView.findViewById(R.id.layoutAvatars);
            progressBar = itemView.findViewById(R.id.progressBar);
            cardView = itemView.findViewById(R.id.cardProject);

            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onProjectClick(projects.get(position));
                }
            });
        }

        public void bind(Project project) {
            tvProjectName.setText(project.getName());

            int progressPercent = project.getProgressPercent();
            if (progressBar != null) {
                progressBar.setProgress(progressPercent);
            }
            if (tvProgress != null) {
                tvProgress.setText(progressPercent + "%");
            }

            int activityCount = Math.max(0, project.getNewActivitiesCount());
            tvProjectDesc.setText(itemView.getContext().getString(R.string.project_activity_count_format, activityCount));
            tvTaskCount.setText(String.valueOf(Math.max(0, project.getTaskActivitiesToday())));
            tvCommentCount.setText(String.valueOf(Math.max(0, project.getCommentActivitiesToday())));

            renderMemberAvatars(project.getMemberPreviews());
        }

        private void renderMemberAvatars(List<User> members) {
            if (layoutAvatars == null) {
                return;
            }
            layoutAvatars.removeAllViews();

            if (members == null || members.isEmpty()) {
                layoutAvatars.addView(createFallbackAvatar("?", 0));
                return;
            }

            int limit = Math.min(MAX_PREVIEW_MEMBERS, members.size());
            for (int i = 0; i < limit; i++) {
                User member = members.get(i);
                if (member != null && !TextUtils.isEmpty(member.getAvatarUrl())) {
                    layoutAvatars.addView(createAvatarImage(member.getAvatarUrl(), i));
                } else {
                    layoutAvatars.addView(createFallbackAvatar(resolveInitial(member), i));
                }
            }
        }

        private View createAvatarImage(String avatarUrl, int index) {
            Context context = itemView.getContext();
            AppCompatImageView avatar = new AppCompatImageView(context);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dpToPx(AVATAR_SIZE_DP), dpToPx(AVATAR_SIZE_DP));
            params.setMarginStart(dpToPx(index * AVATAR_OFFSET_DP));
            avatar.setLayoutParams(params);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.setBackgroundResource(R.drawable.bg_circle_outline);
            Glide.with(context)
                    .load(avatarUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(avatar);
            return avatar;
        }

        private View createFallbackAvatar(String initial, int index) {
            Context context = itemView.getContext();
            TextView avatar = new TextView(context);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dpToPx(AVATAR_SIZE_DP), dpToPx(AVATAR_SIZE_DP));
            params.setMarginStart(dpToPx(index * AVATAR_OFFSET_DP));
            avatar.setLayoutParams(params);
            avatar.setGravity(android.view.Gravity.CENTER);
            avatar.setText(initial);
            avatar.setTextSize(10f);
            avatar.setTypeface(Typeface.DEFAULT_BOLD);
            avatar.setTextColor(ContextCompat.getColor(context, R.color.theme_text_primary));

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(ContextCompat.getColor(context, R.color.theme_border));
            avatar.setBackground(bg);
            return avatar;
        }

        private String resolveInitial(User member) {
            if (member == null) {
                return "?";
            }
            String raw = member.getDisplayNameOrEmail();
            if (TextUtils.isEmpty(raw)) {
                return "?";
            }
            String[] parts = raw.trim().split("\\s+");
            if (parts.length >= 2) {
                char first = Character.toUpperCase(parts[0].charAt(0));
                char second = Character.toUpperCase(parts[1].charAt(0));
                return String.valueOf(first) + second;
            }
            return String.valueOf(Character.toUpperCase(raw.trim().charAt(0)));
        }

        private int dpToPx(int dp) {
            float density = itemView.getResources().getDisplayMetrics().density;
            return Math.round(dp * density);
        }
    }
}
